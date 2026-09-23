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
        java.util.Map<Integer, java.util.List<JsonObject>> measured = new java.util.TreeMap<>();
        java.util.List<JsonObject> inventory = new java.util.ArrayList<>();
        String jobId;
        try (Jobs jobs = new Jobs(root.resolve("jobs"), context -> {
            JsonArray rows = array();
            for (int id : context.request().check.controllers) rows.add(object("controller", id, "status", "pending"));
            Checks.Report report = new Checks.Report(root.resolve("checks"), context, object("fixture", true), rows);
            // Unlike an empty synthetic sweep, native calls always populate the
            // timing accumulator before planning and before every row is saved.
            observed("structure inventory", "call", () -> {}, inventory);
            JsonObject planning = context.timings();
            timing(planning, inventory);
            report.planning(planning); report.save();
            JsonObject cleared = context.timings();
            require(cleared.getAsJsonArray("operations").size() == 0 && cleared.getAsJsonObject("phases").entrySet().isEmpty()
                    && cleared.getAsJsonArray("slow").size() == 0, "Planning timings were not drained");
            Checks.sweep(context, report, guarded::incrementAndGet, (index, row) -> {
                attempted.incrementAndGet();
                java.util.List<JsonObject> samples = new java.util.ArrayList<>(); measured.put(index, samples);
                observed("structure capture", "construct", () -> {}, samples);
                observed("structure capture", "serialize", () -> {
                    if (index == 0 || index == 2) throw new Jobs.Fault("fixture_failure", "independent failure " + index);
                }, samples);
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
                    "Sweep stopped at the first error or mislabeled coverage: " + done.error);
            require(publications.get() == 0 && jobs.results(null, 100).rows.isEmpty(), "Diagnostic leaked into export listings");
            require(JobsTest.http(connection, "GET", "/jobs/" + jobId, null, null).getAsJsonObject("job").get("state").getAsString().equals("checked"), "HTTP omitted the checked state");
            JobsTest.http(connection, "POST", "/jobs", new com.google.gson.Gson().toJsonTree(request).getAsJsonObject(), "invalid_request");
            JobsTest.http(connection, "POST", "/checks", object("key", "wrong", "world", "test-copy", "domain", "structures", "handlers", array("category_" + String.join("", java.util.Collections.nCopies(64, "a")))), "invalid_request");
            JsonObject report = new com.google.gson.JsonParser().parse(new String(Files.readAllBytes(root.resolve("checks").resolve(jobId + ".json")), StandardCharsets.UTF_8)).getAsJsonObject();
            timing(report.getAsJsonObject("planning"), inventory);
            require(report.getAsJsonArray("rows").get(2).getAsJsonObject().getAsJsonObject("error").get("message").getAsString().contains("2"), "Report lost the later failure");
            for (int index = 0; index < 4; index++) timing(report.getAsJsonArray("rows").get(index).getAsJsonObject().getAsJsonObject("timings"), measured.get(index));
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
                    observed("structure release", "release", () -> {
                        if (index == 1) throw new Jobs.Fault("check_cleanup", "Owned preview did not close");
                    }, new java.util.ArrayList<>());
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
                Checks.sweep(context, report, () -> {}, (index, row) -> {
                    observed("structure open", "initialize", () -> {}, new java.util.ArrayList<>());
                    running.countDown(); new java.util.concurrent.CountDownLatch(1).await();
                });
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
        recipes(root);
        System.out.println("Diagnostic tasks: observed work through timing reports, decimal quantities, failure/cancel reports, retry identity and publication separation passed");
    }

    private static void recipes(Path root) throws Exception {
        Path scope = root.resolve("recipes");
        java.util.Map<String, java.util.List<Integer>> attempts = new java.util.HashMap<>();
        try (Jobs jobs = new Jobs(scope.resolve("jobs"), context -> {
            String key = context.request().key;
            java.util.List<Integer> visited = new java.util.ArrayList<>(); attempts.put(key, visited);
            JsonObject row = object("handler", "fixture", "status", "pending");
            Checks.Report report = new Checks.Report(scope.resolve("checks"), context, object(), array(row));
            try {
                Checks.sweep(context, report, () -> {}, (target, result) ->
                        Checks.recipes(context, report, () -> {}, result, key.equals("recipe-stop") ? 483 : 20, index -> {
                            visited.add(index);
                            if (key.equals("recipe-stop") && index == 11) throw new Jobs.Fault("slot_changed", "Candidate content changed");
                            if (key.equals("recipe-range") && (index == 7 || index == 9)) throw new Jobs.Fault("recipe_capture", "Independent recipe failure");
                            return !key.equals("recipe-range") || index != 10;
                        }));
                context.checked(report.finish("complete", null));
            } catch (Exception error) { report.finish("stopped", error); throw error; }
        })) {
            Jobs.Request stopped = Checks.request(object("key", "recipe-stop", "world", "test-copy", "domain", "recipes", "limit", 483));
            Jobs.Job failed = await(jobs, jobs.start(stopped).id);
            JsonObject row = readReport(scope, failed.id);
            require(failed.state.equals("failed") && failed.error.get("code").equals("slot_changed")
                    && attempts.get("recipe-stop").size() == 12 && row.get("checkedRecipes").getAsInt() == 12
                    && row.get("unexamined").getAsInt() == 471 && row.get("end").getAsInt() == 483
                    && row.getAsJsonArray("failedRecipes").size() == 1 && row.getAsJsonArray("failedRecipes").get(0).getAsInt() == 11,
                    "Fatal recipe mismatch continued or the report claimed the unvisited tail was checked");
            Jobs.Request range = Checks.request(object("key", "recipe-range", "world", "test-copy", "domain", "recipes", "offset", 5, "limit", 8));
            Jobs.Job checked = await(jobs, jobs.start(range).id);
            row = readReport(scope, checked.id);
            require(checked.state.equals("checked") && checked.report.failed == 1 && attempts.get("recipe-range").size() == 8
                    && row.get("checkedRecipes").getAsInt() == 8 && row.get("unexamined").getAsInt() == 12
                    && row.getAsJsonArray("failedRecipes").size() == 2 && row.getAsJsonArray("excludedRecipes").size() == 1,
                    "Bounded diagnostic ranges lost independent failures, excluded recipes or actual coverage");
        }
        System.out.println("Recipe diagnostics: fatal index 11 leaves 471 unexamined, bounded ranges retain independent failures and exclusions");
    }

    private static JsonObject readReport(Path root, String id) throws Exception {
        return new com.google.gson.JsonParser().parse(new String(Files.readAllBytes(root.resolve("checks").resolve(id + ".json")),
                StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("rows").get(0).getAsJsonObject();
    }

    private static void observed(String name, String phase, Runnable action, java.util.List<JsonObject> samples) throws Exception {
        Jobs.Observer observer = Jobs.observer();
        Work<Void> work = new Work<>(name, Jobs.bind(() -> { Work.phase(phase); action.run(); return null; }));
        observer.update(work.snapshot());
        Thread runner = new Thread(work::run, "diagnostic fixture client");
        runner.start();
        try { work.await(new java.util.concurrent.ConcurrentLinkedQueue<>(), TimeUnit.SECONDS.toNanos(5), observer); }
        finally {
            runner.join(5000);
            observer.update(work.snapshot()); // Repeated delivery must not double count the same call.
            samples.add(work.snapshot());
        }
    }

    private static void timing(JsonObject result, java.util.List<JsonObject> samples) {
        require(result.getAsJsonArray("operations").size() == 1, "Timing group was lost or split");
        JsonObject timing = result.getAsJsonArray("operations").get(0).getAsJsonObject();
        for (String field : new String[] {"calls", "queueMicros", "runMicros", "maxMicros"}) {
            require(timing.get(field).getAsJsonPrimitive().isString(), "Exact timing field is not a decimal string: " + field);
        }
        require(timing.get("calls").getAsString().equals(Integer.toString(samples.size())), "Calls were dropped or double counted");
        long queue = 0, run = 0, maximum = 0;
        java.util.Map<String, Long> phases = new java.util.TreeMap<>();
        for (JsonObject sample : samples) {
            queue += sample.get("queueMicros").getAsLong(); run += sample.get("runMicros").getAsLong();
            maximum = Math.max(maximum, sample.get("runMicros").getAsLong());
            sample.getAsJsonObject("phases").entrySet().forEach(entry -> phases.merge(entry.getKey(), entry.getValue().getAsLong(), Long::sum));
        }
        require(timing.get("queueMicros").getAsString().equals(Long.toString(queue))
                && timing.get("runMicros").getAsString().equals(Long.toString(run))
                && timing.get("maxMicros").getAsString().equals(Long.toString(maximum)), "Report lost observed timing values");
        phases.forEach((name, micros) -> require(result.getAsJsonObject("phases").get(name).getAsString().equals(Long.toString(micros)), "Phase timing changed"));
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
