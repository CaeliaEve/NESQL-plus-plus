package com.github.dcysteine.nesql.exporter.task;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/** Reuses the job suite; no world, network, file writes or game launch. */
final class ServerSnapshotsTest {
    static void run() throws Exception {
        ServerSnapshots snapshots = new ServerSnapshots();
        Object server = new Object();
        AtomicReference<Object> result = new AtomicReference<>();
        Thread caller =
                new Thread(
                        () -> {
                            try {
                                result.set(
                                        snapshots.call(
                                                server,
                                                () -> Thread.currentThread(),
                                                ignored -> {}));
                            } catch (Throwable failure) {
                                result.set(failure);
                            }
                        });
        caller.start();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (snapshots.queued() == 0 && caller.isAlive() && System.nanoTime() < deadline)
            Thread.yield();
        snapshots.drain(server);
        caller.join(2000);
        if (caller.isAlive() || result.get() != Thread.currentThread())
            throw new AssertionError("Snapshot did not execute on owning server thread");
        result.set(null);
        Thread drifted =
                new Thread(
                        () -> {
                            try {
                                snapshots.call(
                                        server,
                                        () -> {
                                            throw new AssertionError(
                                                    "Stale world callback executed");
                                        },
                                        ignored -> {});
                            } catch (Throwable failure) {
                                result.set(failure);
                            }
                        });
        drifted.start();
        deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (snapshots.queued() == 0 && drifted.isAlive() && System.nanoTime() < deadline)
            Thread.yield();
        snapshots.drain(new Object());
        drifted.join(2000);
        if (drifted.isAlive()
                || !(result.get() instanceof Jobs.Fault)
                || !((Jobs.Fault) result.get()).code.equals("world_changed"))
            throw new AssertionError("Server identity drift was hidden");
        System.out.println("Server snapshots: owning tick thread and world replacement passed");
    }
}
