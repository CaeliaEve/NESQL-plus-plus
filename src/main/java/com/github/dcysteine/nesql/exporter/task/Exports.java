package com.github.dcysteine.nesql.exporter.task;

import com.github.dcysteine.nesql.exporter.capture.Capture;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Owns the local export lifecycle; GUI actions and journal reads also stay off the game thread. */
public final class Exports implements AutoCloseable {
    public final ClientThread client = new ClientThread();
    private final Capture capture;
    private final Jobs jobs;
    private final GameServer server;
    private final ThreadPoolExecutor controls = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(16), action -> {
        Thread thread = new Thread(action, "NESQL controls");
        thread.setDaemon(true);
        return thread;
    });

    public Exports(Path instance) throws IOException {
        capture = new Capture(instance, client);
        Path directory = instance.resolve("nesql");
        jobs = new Jobs(directory.resolve("jobs"), capture);
        try { server = new GameServer(directory, jobs, capture::inspect, client::requireWorld); }
        catch (IOException | RuntimeException error) {
            try { jobs.close(); } catch (IOException close) { error.addSuppressed(close); }
            controls.shutdownNow();
            throw error;
        }
    }

    public CompletableFuture<JsonObject> inspect() { return submit(capture::inspect); }
    public CompletableFuture<Jobs.Job> current() { return submit(jobs::current); }
    public CompletableFuture<Jobs.Job> start(Jobs.Request request) { return submit(() -> jobs.start(request, client::requireWorld)); }
    public CompletableFuture<Jobs.Job> read(String id) { return submit(() -> jobs.read(id)); }
    public CompletableFuture<Jobs.Job> cancel(String id) { return submit(() -> jobs.cancel(id)); }

    private <T> CompletableFuture<T> submit(Callable<T> action) {
        Control<T> control = new Control<>(action);
        control.future.whenComplete((value, error) -> { if (control.future.isCancelled()) controls.remove(control); });
        try { controls.execute(control); }
        catch (RejectedExecutionException error) {
            control.future.completeExceptionally(new Jobs.Fault(controls.isShutdown() ? "game_stopped" : "controls_busy",
                    controls.isShutdown() ? "The exporter is stopping" : "The export control queue is full"));
        }
        return control.future;
    }

    @Override public void close() throws IOException {
        for (Runnable action : controls.shutdownNow()) {
            ((Control<?>) action).future.completeExceptionally(new Jobs.Fault("game_stopped", "The exporter is stopping"));
        }
        server.close();
    }

    private static final class Control<T> implements Runnable {
        final CompletableFuture<T> future = new CompletableFuture<>();
        final Callable<T> action;
        Control(Callable<T> action) { this.action = action; }
        @Override public void run() {
            if (future.isDone()) return;
            try { future.complete(action.call()); }
            catch (Throwable error) {
                future.completeExceptionally(error);
                if (error instanceof Error) throw (Error) error;
            }
        }
    }
}
