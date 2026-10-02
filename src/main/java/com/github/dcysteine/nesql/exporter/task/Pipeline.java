package com.github.dcysteine.nesql.exporter.task;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.ArrayDeque;

/** Ordered output with bounded background CPU work; native objects never enter this queue. */
public final class Pipeline implements AutoCloseable {
    public interface Commit { void accept(byte[] bytes) throws Exception; }
    private final ThreadPoolExecutor worker = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(8), action -> {
        Thread thread = new Thread(action, "NESQL PNG encoding"); thread.setDaemon(true); return thread;
    });
    private final ArrayDeque<Pending> pending = new ArrayDeque<>();
    private long bytes;
    public void submit(long weight, Callable<byte[]> work, Commit commit) throws Exception {
        Jobs.checkpoint();
        if (weight < 0) throw new IllegalArgumentException("Negative pipeline weight");
        while (!pending.isEmpty() && (pending.size() >= 8 || weight > 16 * 1024 * 1024L - bytes)) drain();
        if (weight > 16 * 1024 * 1024L) { commit.accept(work.call()); return; }
        pending.add(new Pending(weight, worker.submit(Jobs.bind(work)), commit)); bytes += weight;
    }
    public void flush() throws Exception { while (!pending.isEmpty()) drain(); }
    private void drain() throws Exception {
        Jobs.checkpoint();
        Pending next = pending.removeFirst(); bytes -= next.weight;
        byte[] result;
        try { result = next.result.get(); }
        catch (ExecutionException failed) {
            Throwable cause = failed.getCause();
            if (cause instanceof Exception) throw (Exception) cause;
            if (cause instanceof Error) throw (Error) cause;
            throw new IllegalStateException(cause);
        }
        Jobs.checkpoint(); next.commit.accept(result);
    }
    @Override public void close() {
        for (Pending next : pending) next.result.cancel(true);
        pending.clear(); worker.shutdownNow();
        boolean interrupted = Thread.interrupted();
        try {
            while (!worker.isTerminated()) try { worker.awaitTermination(250, TimeUnit.MILLISECONDS); }
            catch (InterruptedException ignored) { interrupted = true; }
        } finally { if (interrupted) Thread.currentThread().interrupt(); }
    }
    private static final class Pending {
        final long weight; final Future<byte[]> result; final Commit commit;
        Pending(long weight, Future<byte[]> result, Commit commit) { this.weight = weight; this.result = result; this.commit = commit; }
    }
}
