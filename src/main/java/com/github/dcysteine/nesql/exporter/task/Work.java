package com.github.dcysteine.nesql.exporter.task;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.time.Instant;
import java.util.Map;
import java.util.Queue;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Queue deadlines never discard a completed native call or interrupt its game thread. */
final class Work<T> {
    private static final AtomicLong IDS = new AtomicLong();
    private static final ThreadLocal<Work<?>> CURRENT = new ThreadLocal<>();
    private final Callable<T> action;
    private final CompletableFuture<T> result = new CompletableFuture<>();
    private final String id = Long.toString(IDS.incrementAndGet()), name, created = Instant.now().toString();
    private final long queued = System.nanoTime();
    private final Map<String, Long> phases = new TreeMap<>();
    private long started, ended, phaseStarted, sampled;
    private String phase = "call", state = "queued";
    private Thread runner;
    private boolean abandoned;
    private StackTraceElement[] stack = new StackTraceElement[0];

    Work(String name, Callable<T> action) { this.name = Checks.shorten(name, 256); this.action = action; }

    static void phase(String name) {
        Work<?> work = CURRENT.get();
        if (work != null) work.phaseTo(name);
    }

    private synchronized void phaseTo(String name) {
        long now = System.nanoTime();
        phases.merge(phase, now - phaseStarted, Long::sum);
        phase = phases.size() >= 32 && !phases.containsKey(name) ? "other" : Checks.shorten(name, 64);
        phaseStarted = now;
    }

    void run() {
        synchronized (this) {
            if (abandoned || started != 0) return;
            started = phaseStarted = System.nanoTime(); runner = Thread.currentThread(); state = "running";
        }
        Work<?> previous = CURRENT.get(); CURRENT.set(this);
        try {
            T value = action.call();
            finish("done"); result.complete(value);
        } catch (Throwable error) { finish("failed"); result.completeExceptionally(error); }
        finally { if (previous == null) CURRENT.remove(); else CURRENT.set(previous); }
    }

    private synchronized void finish(String state) {
        ended = System.nanoTime(); phases.merge(phase, ended - phaseStarted, Long::sum); this.state = state;
    }

    synchronized JsonObject snapshot() {
        long now = System.nanoTime(), end = ended == 0 ? now : ended;
        long waiting = (started == 0 ? end : started) - queued, running = started == 0 ? 0 : end - started;
        if (runner != null && ended == 0 && running >= TimeUnit.SECONDS.toNanos(1) && now - sampled >= TimeUnit.SECONDS.toNanos(1)) {
            stack = runner.getStackTrace(); sampled = now;
        }
        JsonArray trace = new JsonArray();
        for (int index = 0; index < Math.min(stack.length, 24); index++) trace.add(value(Checks.shorten(stack[index].toString(), 512)));
        JsonObject times = new JsonObject();
        Map<String, Long> elapsed = new TreeMap<>(phases);
        if (started != 0 && ended == 0) elapsed.merge(phase, now - phaseStarted, Long::sum);
        elapsed.forEach((key, nanos) -> times.addProperty(key, Long.toString(nanos / 1000)));
        return object("id", id, "name", name, "phase", phase, "state", state, "created", created,
                "queueMicros", Long.toString(waiting / 1000), "runMicros", Long.toString(running / 1000),
                "slow", running >= TimeUnit.SECONDS.toNanos(1), "phases", times, "stack", trace, "framesOmitted", Math.max(0, stack.length - 24));
    }

    T await(Queue<Work<?>> queue, long queueTimeout, Jobs.Observer observer) throws Exception {
        try {
            while (!result.isDone()) {
                long wait;
                synchronized (this) {
                    long remaining = queueTimeout - (System.nanoTime() - queued);
                    if (started == 0 && remaining <= 0) {
                        abandoned = true; ended = System.nanoTime(); state = "queue_timeout"; queue.remove(this);
                        throw new Jobs.Fault("client_queue_timeout", "Client did not start " + name + " before the queue deadline");
                    }
                    wait = started == 0 ? Math.max(1, Math.min(remaining, TimeUnit.MILLISECONDS.toNanos(250))) : TimeUnit.MILLISECONDS.toNanos(250);
                }
                try { result.get(wait, TimeUnit.NANOSECONDS); }
                catch (TimeoutException ignored) { observe(observer); }
                catch (ExecutionException complete) { break; }
            }
        } catch (Exception failure) {
            boolean running;
            synchronized (this) {
                running = started != 0;
                if (!running) {
                    abandoned = true; ended = System.nanoTime(); queue.remove(this);
                    if (!state.equals("queue_timeout")) state = failure instanceof InterruptedException ? "cancelled" : "abandoned";
                }
            }
            if (running) settle(observer, failure);
            try { observe(observer); } catch (java.io.IOException reporting) { failure.addSuppressed(reporting); }
            throw failure;
        }
        observe(observer);
        try { return result.get(); }
        catch (ExecutionException error) {
            Throwable cause = error.getCause();
            if (cause instanceof Exception) throw (Exception) cause;
            if (cause instanceof Error) throw (Error) cause;
            throw new IllegalStateException(cause);
        }
    }

    private void observe(Jobs.Observer observer) throws java.io.IOException {
        boolean interrupted = Thread.interrupted();
        try { observer.update(snapshot()); }
        finally { if (interrupted) Thread.currentThread().interrupt(); }
    }

    /** A cancelled wait still owns any resource returned by the completed game call. */
    AutoCloseable resource() {
        if (!result.isDone() || result.isCompletedExceptionally()) return null;
        T value = result.getNow(null);
        return value instanceof AutoCloseable ? (AutoCloseable) value : null;
    }

    private void settle(Jobs.Observer observer, Exception failure) {
        boolean interrupted = false;
        while (!result.isDone()) {
            try { result.get(250, TimeUnit.MILLISECONDS); }
            catch (InterruptedException ignored) { interrupted = true; }
            catch (ExecutionException finished) { break; }
            catch (TimeoutException waiting) {
                try { observe(observer); }
                catch (java.io.IOException reporting) { if (failure.getSuppressed().length < 4) failure.addSuppressed(reporting); }
            }
        }
        if (interrupted) Thread.currentThread().interrupt();
    }
}
