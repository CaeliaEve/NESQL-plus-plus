package com.github.dcysteine.nesql.exporter.task;

import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Queue expiry, slow completion and cancellation are different lifecycle outcomes. */
final class WorkTest {
    private WorkTest() {}

    static void run() throws Exception {
        Queue<Work<?>> queue = new ConcurrentLinkedQueue<>();
        AtomicInteger calls = new AtomicInteger();
        Work<Integer> queued = new Work<>("never started", calls::incrementAndGet);
        queue.add(queued);
        try { queued.await(queue, TimeUnit.MILLISECONDS.toNanos(2), ignored -> {}); throw new AssertionError("Expected queue expiry"); }
        catch (Jobs.Fault expected) { require(expected.code.equals("client_queue_timeout"), "Wrong queue error"); }
        queued.run();
        require(calls.get() == 0 && queue.isEmpty(), "An abandoned queue entry still ran");

        CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1);
        Work<Integer> slow = new Work<>("structure 17000 capture", () -> {
            Work.phase("construct"); started.countDown(); release.await(); return 42;
        });
        Thread runner = new Thread(slow::run), timer = new Thread(() -> {
            try { release.await(5, TimeUnit.SECONDS); } catch (InterruptedException error) { Thread.currentThread().interrupt(); }
            finally { release.countDown(); }
        });
        List<JsonObject> observations = new ArrayList<>();
        runner.start();
        require(started.await(5, TimeUnit.SECONDS), "Native work did not start"); timer.start();
        try {
            require(slow.await(queue, TimeUnit.MILLISECONDS.toNanos(2), value -> {
                observations.add(value);
                if (value.get("slow").getAsBoolean() && value.getAsJsonArray("stack").size() > 0) release.countDown();
            }) == 42, "Slow native result was discarded");
        } finally { release.countDown(); runner.join(5000); timer.join(5000); }
        require(observations.stream().anyMatch(value -> value.get("state").getAsString().equals("running")
                        && value.get("phase").getAsString().equals("construct") && value.getAsJsonArray("stack").size() > 0),
                "A slow native operation did not expose its phase and stack while running");
        JsonObject completed = observations.get(observations.size() - 1);
        require(completed.get("state").getAsString().equals("done") && completed.get("slow").getAsBoolean()
                && completed.get("runMicros").getAsLong() >= 1_000_000, "Missing completed operation timing");
        Jobs.Fault original = new Jobs.Fault("fixture_native", "original native failure");
        Work<Void> broken = new Work<>("broken native call", () -> { throw original; }); broken.run();
        try { broken.await(queue, 1, ignored -> {}); throw new AssertionError("Native failure disappeared"); }
        catch (Jobs.Fault expected) { require(expected == original, "Native error was replaced by a timeout"); }

        CountDownLatch busy = new CountDownLatch(1), finish = new CountDownLatch(1), entered = new CountDownLatch(1), returned = new CountDownLatch(1);
        Work<Void> cancelled = new Work<>("cancelled after start", () -> { busy.countDown(); finish.await(); return null; });
        AtomicReference<Throwable> error = new AtomicReference<>();
        Thread game = new Thread(cancelled::run), waiting = new Thread(() -> {
            entered.countDown();
            try { cancelled.await(queue, TimeUnit.SECONDS.toNanos(1), ignored -> {}); }
            catch (Throwable failure) { error.set(failure); }
            finally { returned.countDown(); }
        });
        game.start(); require(busy.await(5, TimeUnit.SECONDS), "Cancellation fixture did not start"); waiting.start();
        try {
            require(entered.await(5, TimeUnit.SECONDS), "Waiter did not start"); waiting.interrupt();
            require(!returned.await(50, TimeUnit.MILLISECONDS), "Cancellation returned while native work was still running");
        } finally { finish.countDown(); game.join(5000); waiting.join(5000); }
        require(error.get() instanceof InterruptedException && returned.getCount() == 0, "Cancellation lost its original signal");
        AutoCloseable resource = () -> {};
        Work<AutoCloseable> factory = new Work<>("factory", () -> resource); factory.run();
        try {
            factory.await(queue, 1, ignored -> { throw new java.io.IOException("journal fixture"); });
            throw new AssertionError("Expected journal failure");
        } catch (java.io.IOException expected) { require(factory.resource() == resource, "A failed wait lost the completed resource needed for cleanup"); }
        System.out.println("Client work: queue expiry, retained slow results, running phase/stack, original failure and safe cancellation passed");
    }

    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
