package com.github.dcysteine.nesql.exporter.task;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import net.minecraft.server.MinecraftServer;

/** Finite read-only world snapshots execute on the owning integrated-server tick. */
public final class ServerSnapshots {
    public static final ServerSnapshots INSTANCE = new ServerSnapshots();
    private final ArrayBlockingQueue<Work<?>> queue = new ArrayBlockingQueue<>(8);
    private volatile Object observedServer;
    private volatile Thread thread;

    public <T> T call(Object server, Callable<T> snapshot) throws Exception {
        return call(server, snapshot, Jobs.observer());
    }

    <T> T call(Object server, Callable<T> snapshot, Jobs.Observer observer) throws Exception {
        if (server == null)
            throw new Jobs.Fault(
                    "handler_not_ready", "Integrated server is unavailable for a world snapshot");
        Jobs.checkpoint();
        if (Thread.currentThread() == thread) {
            check(server);
            return snapshot.call();
        }
        Work<T> work =
                new Work<>(
                        "EnderStorage read-only snapshot",
                        Jobs.bind(
                                () -> {
                                    check(server);
                                    return snapshot.call();
                                },
                                false),
                        "server_queue_timeout",
                        "Server did not begin the read-only snapshot before the queue deadline");
        if (!queue.offer(work)) throw new Jobs.Fault("server_busy", "World snapshot queue is full");
        try {
            return work.await(queue, TimeUnit.SECONDS.toNanos(10), observer);
        } catch (Exception | Error failure) {
            AutoCloseable resource = work.resource();
            if (resource != null) {
                try {
                    resource.close();
                } catch (Exception | Error closing) {
                    failure.addSuppressed(closing);
                }
            }
            throw failure;
        }
    }

    private void check(Object server) {
        if (observedServer != server)
            throw new Jobs.Fault(
                    "world_changed", "The snapshot's owning integrated server changed");
    }

    int queued() {
        return queue.size();
    }

    void drain(Object server) {
        thread = Thread.currentThread();
        observedServer = server;
        // One bounded snapshot per tick; queue waits retain the existing safe-cancellation
        // behavior.
        Work<?> work = queue.poll();
        if (work != null) work.run();
    }

    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) drain(MinecraftServer.getServer());
    }
}
