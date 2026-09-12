package com.github.dcysteine.nesql.exporter.task;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.client.Minecraft;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Bounded handoff of game API work to the client tick. File and HTTP work stay off-thread. */
public final class ClientThread implements net.minecraft.client.resources.IResourceManagerReloadListener {
    private final ArrayBlockingQueue<Work<?>> queue = new ArrayBlockingQueue<>(128);
    private volatile Thread thread;
    private volatile long lastTick;
    private volatile boolean ready;
    private volatile String reason = "The client has not ticked yet";
    private volatile long longestMicros;
    private long resources;

    public boolean ready() { return ready && System.nanoTime() - lastTick < TimeUnit.SECONDS.toNanos(5); }
    public String reason() { return ready() ? null : ready ? "The client is not responding" : reason; }
    public long longestMicros() { return longestMicros; }
    public int queued() { return queue.size(); }

    public Session session() throws Exception { return call(() -> new Session(Minecraft.getMinecraft())); }

    /** Pins all work to the same world and player, including after the user closes a world. */
    public final class Session {
        private final Object world;
        private final Object player;
        private final long generation;
        private Session(Minecraft game) { world = game.theWorld; player = game.thePlayer; generation = resources; }
        public <T> T call(Callable<T> action) throws Exception {
            return ClientThread.this.call(() -> {
                Minecraft game = Minecraft.getMinecraft();
                if (game.theWorld != world || game.thePlayer != player) throw new Jobs.Fault("world_changed", "The export's world or player changed");
                if (generation != resources) throw new Jobs.Fault("resources_changed", "Game resources were reloaded during export");
                return action.call();
            });
        }
    }

    @Override public void onResourceManagerReload(net.minecraft.client.resources.IResourceManager manager) { resources++; }

    public void requireWorld() {
        if (!ready()) throw new Jobs.Fault("world_unavailable", reason());
    }

    public <T> T call(Callable<T> action) throws Exception {
        return call(action, true);
    }

    public void cleanup(Runnable action) throws Exception {
        Jobs.cleanup(() -> call(() -> { action.run(); return null; }, false));
    }

    private <T> T call(Callable<T> action, boolean worldRequired) throws Exception {
        Jobs.checkpoint();
        if (Thread.currentThread() == thread) { if (worldRequired) requireWorld(); return action.call(); }
        Work<T> work = new Work<>(Jobs.bind(() -> {
            if (worldRequired) requireWorld();
            return action.call();
        }));
        if (!queue.offer(work)) throw new Jobs.Fault("client_busy", "The client work queue is full");
        try {
            return work.result.get(15, TimeUnit.SECONDS);
        } catch (InterruptedException | TimeoutException error) {
            // A started game operation must finish before its owning job can report cancellation.
            synchronized (work) {
                if (!work.started) { work.abandoned = true; queue.remove(work); }
            }
            if (work.started) {
                boolean interrupted = false;
                while (true) {
                    try { work.result.get(); break; }
                    catch (InterruptedException ignored) { interrupted = true; }
                    catch (ExecutionException ignored) { break; }
                }
                if (interrupted) Thread.currentThread().interrupt();
            }
            throw error;
        } catch (ExecutionException error) {
            Throwable cause = error.getCause();
            if (cause instanceof Exception) throw (Exception) cause;
            if (cause instanceof Error) throw (Error) cause;
            throw new IllegalStateException(cause);
        }
    }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        thread = Thread.currentThread();
        lastTick = System.nanoTime();
        Minecraft game = Minecraft.getMinecraft();
        ready = game.theWorld != null && game.thePlayer != null && game.isSingleplayer();
        reason = game.theWorld == null || game.thePlayer == null
                ? "Load a single-player world before exporting" : "Only local single-player export is supported";
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(4);
        Work<?> work;
        while (System.nanoTime() < deadline && (work = queue.poll()) != null) {
            long started = System.nanoTime();
            work.run();
            longestMicros = Math.max(longestMicros, (System.nanoTime() - started) / 1000);
        }
    }

    private static final class Work<T> {
        final Callable<T> action;
        final CompletableFuture<T> result = new CompletableFuture<>();
        volatile boolean started;
        boolean abandoned;

        Work(Callable<T> action) { this.action = action; }

        void run() {
            synchronized (this) {
                if (abandoned) return;
                started = true;
            }
            try { result.complete(action.call()); }
            catch (Throwable error) { result.completeExceptionally(error); }
        }
    }
}
