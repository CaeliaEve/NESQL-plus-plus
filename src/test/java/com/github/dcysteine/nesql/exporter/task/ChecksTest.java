package com.github.dcysteine.nesql.exporter.task;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** The production sweep must collect independent failures without becoming an export. */
final class ChecksTest {
    private ChecksTest() {}

    static void run(Path root) throws Exception {
        Jobs.Request request = Checks.request(object("key", "sweep", "world", "test-copy", "domain", "structures", "controllers", array(4, 3, 2, 1)));
        require(request.check.controllers.equals(java.util.Arrays.asList(1, 2, 3, 4)), "Check targets are not canonical");
        for (JsonObject invalid : java.util.Arrays.asList(
                object("key", "bad", "domain", "structures"),
                object("key", "bad", "world", "test", "domain", "code"),
                object("key", "bad", "world", "test", "domain", "structures", "controllers", array(-1)),
                object("key", "bad", "world", "test", "domain", "structures", "controllers", array(1, 1)),
                object("key", "bad", "world", "test", "domain", "recipes", "controllers", array(1)),
                object("key", "bad", "world", "test", "domain", "recipes", "limit", 0))) {
            try { Checks.request(invalid); throw new AssertionError("Accepted invalid diagnostic request"); }
            catch (Jobs.Fault expected) { require(expected.code.equals("invalid_request"), "Wrong request error"); }
        }
        AtomicInteger attempted = new AtomicInteger(), guarded = new AtomicInteger(), publications = new AtomicInteger();
        String jobId;
        try (Jobs jobs = new Jobs(root.resolve("jobs"), context -> {
            JsonArray rows = array();
            for (int id : context.request().check.controllers) rows.add(object("controller", id, "status", "pending"));
            Checks.Report report = new Checks.Report(root.resolve("checks"), context, object("fixture", true), rows);
            Checks.sweep(context, report, guarded::incrementAndGet, (index, row) -> {
                attempted.incrementAndGet();
                if (index == 0 || index == 2) throw new Jobs.Fault("fixture_failure", "independent failure " + index);
                row.addProperty("status", index == 3 ? "unsupported" : "passed");
            });
            try { context.publish(() -> { publications.incrementAndGet(); return null; }); throw new AssertionError("Check could publish"); }
            catch (Jobs.Fault expected) { require(expected.code.equals("check_only"), "Wrong publication guard"); }
            context.checked(report.finish("complete", null));
        }); GameServer server = new GameServer(root, jobs, () -> object("ready", true), () -> {})) {
            JsonObject connection = new com.google.gson.JsonParser().parse(new String(Files.readAllBytes(root.resolve("connection.json")), StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject body = object("key", "sweep", "world", "test-copy", "domain", "structures", "controllers", array(4, 3, 2, 1));
            jobId = JobsTest.http(connection, "POST", "/checks", body, null).get("id").getAsString();
            Jobs.Job done = await(jobs, jobId);
            require(done.state.equals("checked") && done.result == null && done.report.failed == 2 && done.report.passed == 1
                    && done.report.unsupported == 1 && done.report.pending == 0 && attempted.get() == 4 && guarded.get() == 8,
                    "Sweep stopped at the first error or mislabeled coverage");
            require(publications.get() == 0 && jobs.results(null, 100).rows.isEmpty(), "Diagnostic leaked into export listings");
            require(JobsTest.http(connection, "GET", "/jobs/" + jobId, null, null).getAsJsonObject("job").get("state").getAsString().equals("checked"), "HTTP omitted the checked state");
            JobsTest.http(connection, "POST", "/jobs", new com.google.gson.Gson().toJsonTree(request).getAsJsonObject(), "invalid_request");
            JobsTest.http(connection, "POST", "/checks", object("key", "wrong", "world", "test-copy", "domain", "structures", "handlers", array("category_" + String.join("", java.util.Collections.nCopies(64, "a")))), "invalid_request");
            JsonObject report = new com.google.gson.JsonParser().parse(new String(Files.readAllBytes(root.resolve("checks").resolve(jobId + ".json")), StandardCharsets.UTF_8)).getAsJsonObject();
            require(report.getAsJsonArray("rows").get(2).getAsJsonObject().getAsJsonObject("error").get("message").getAsString().contains("2"), "Report lost the later failure");
        }
        try (Jobs jobs = new Jobs(root.resolve("jobs"), context -> { throw new AssertionError("A historical check reran"); })) {
            require(jobs.read(jobId).state.equals("checked") && jobs.start(request).id.equals(jobId) && jobs.results(null, 100).rows.isEmpty(),
                    "Restart lost diagnostic identity or published its report");
        }
        attempted.set(0);
        Jobs.Request stopped = Checks.request(object("key", "stop", "world", "test-copy", "domain", "structures", "controllers", array(1, 2, 3)));
        try (Jobs jobs = new Jobs(root.resolve("jobs"), context -> {
            Checks.Report report = new Checks.Report(root.resolve("checks"), context, object(), array(
                    object("controller", 1, "status", "pending"), object("controller", 2, "status", "pending"), object("controller", 3, "status", "pending")));
            try {
                Checks.sweep(context, report, () -> {}, (index, row) -> {
                    attempted.incrementAndGet();
                    if (index == 1) throw new Jobs.Fault("check_cleanup", "Owned preview did not close");
                    row.addProperty("status", "passed");
                });
            } catch (Exception failure) { report.finish("stopped", failure); throw failure; }
        })) {
            Jobs.Job done = await(jobs, jobs.start(stopped).id);
            require(done.state.equals("failed") && done.report.pending == 1 && attempted.get() == 2
                    && done.error.get("code").equals("check_cleanup"), "Unsafe cleanup allowed the sweep to continue");
        }
        java.util.concurrent.CountDownLatch running = new java.util.concurrent.CountDownLatch(1);
        Jobs.Request cancelled = Checks.request(object("key", "cancel-check", "world", "test-copy", "domain", "structures", "controllers", array(1, 2)));
        try (Jobs jobs = new Jobs(root.resolve("jobs"), context -> {
            Checks.Report report = new Checks.Report(root.resolve("checks"), context, object(), array(
                    object("controller", 1, "status", "pending"), object("controller", 2, "status", "pending")));
            try {
                Checks.sweep(context, report, () -> {}, (index, row) -> { running.countDown(); new java.util.concurrent.CountDownLatch(1).await(); });
            } catch (Exception failure) { report.finish("cancelled", failure); throw failure; }
        })) {
            String id = jobs.start(cancelled).id;
            require(running.await(5, TimeUnit.SECONDS), "Diagnostic cancellation fixture did not start");
            jobs.cancel(id);
            Jobs.Job done = await(jobs, id);
            require(done.state.equals("cancelled") && done.report.status.equals("cancelled") && done.report.pending == 1 && done.result == null,
                    "Cancelled check lost its durable report or executed later targets");
        }
        require(Checks.fatal(new Jobs.Fault("environment_changed", "fixture")), "Environment changes must stop checks");
        Jobs.Fault wrapped = new Jobs.Fault("structure_capture", "wrapper"); wrapped.initCause(new Jobs.Fault("preview_cleanup", "fixture"));
        require(Checks.fatal(wrapped), "Wrapped preview cleanup failures were treated as independent failures");
        System.out.println("Diagnostic tasks: request limits, multiple failures, unexecuted targets, cleanup stop, journal restart and publication separation passed");
    }

    private static Jobs.Job await(Jobs jobs, String id) throws Exception {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < end) {
            Jobs.Job job = jobs.read(id);
            if (java.util.Arrays.asList("checked", "failed", "cancelled").contains(job.state)) return job;
            Thread.sleep(5);
        }
        throw new AssertionError("Diagnostic did not reach a terminal state");
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
