package com.github.dcysteine.nesql.exporter.task;

import com.github.dcysteine.nesql.exporter.source.SourceTest;
import com.github.dcysteine.nesql.exporter.source.Probe;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static com.github.dcysteine.nesql.exporter.source.Json.object;
import static com.github.dcysteine.nesql.exporter.source.Json.array;

/** Lifecycle semantics, rather than assertions about implementation names. */
public final class JobsTest {
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("nesql-jobs-");
        snapshots(root.resolve("snapshots"));
        WorkTest.run();
        EpochTest.run();
        Path checks = root.resolve("diagnostics"); Files.createDirectories(checks); ChecksTest.run(checks);
        CountDownLatch began = new CountDownLatch(1);
        CountDownLatch cleanup = new CountDownLatch(1);
        CountDownLatch cleaned = new CountDownLatch(1);
        java.util.Map<String, Integer> channels = new java.util.TreeMap<>(); channels.put("coil", 2);
        Probe expanded = new Probe(4, channels); channels.put("coil", 7);
        require(expanded.channels.get("coil") == 2, "Caller changed immutable probe parameters");
        Jobs.Request request = new Jobs.Request("once", "fixture", "full", java.util.Collections.emptyList(), java.util.Arrays.asList(expanded, Probe.defaults()), "test-copy");
        Jobs.Request reordered = Jobs.Request.parse(object("key", "once", "name", "fixture", "profile", "full", "world", "test-copy", "probes", array(Probe.defaults().json(), expanded.json())));
        request.checkWorld("test-copy");
        expect("world_changed", () -> request.checkWorld("test"));
        expect("invalid_request", () -> Jobs.Request.parse(object("key", "invalid", "name", "fixture", "profile", "data", "world", "../test")));
        java.util.concurrent.atomic.AtomicReference<java.util.concurrent.Callable<Void>> handoff = new java.util.concurrent.atomic.AtomicReference<>();
        String id;
        try (Jobs jobs = new Jobs(root.resolve("jobs"), context -> {
            handoff.set(Jobs.bind(() -> null));
            context.progress("capture", 0, 1, "Begin fixture");
            context.progress("capture", 1, 1, "Pending fixture progress");
            began.countDown();
            try { new CountDownLatch(1).await(); }
            finally {
                while (!cleanup.await(10, TimeUnit.MILLISECONDS)) { /* model a cleanup operation */ }
                cleaned.countDown();
            }
        })) {
            synchronized (jobs) {
                id = jobs.start(request).id;
                require(snapshot(root, id).get("state").getAsString().equals("queued"), "Job start was not persisted immediately");
            }
            require(began.await(5, TimeUnit.SECONDS), "Task did not start");
            require(snapshot(root, id).get("state").getAsString().equals("running"), "Running state was not persisted before the task");
            require(id.equals(jobs.start(request).id), "Retry started another export");
            require(id.equals(jobs.start(reordered).id), "Probe order changed retry identity");
            require(jobs.read(id).request.probes.get(0).equals(Probe.defaults()), "Journal did not normalize probe order");
            expect("key_conflict", () -> jobs.start(new Jobs.Request("once", "fixture", "full", request.handlers, request.probes, "test")));
            expect("key_conflict", () -> jobs.start(new Jobs.Request("once", "fixture", "full")));
            expect("key_conflict", () -> jobs.start(new Jobs.Request("once", "different", "full")));
            expect("export_busy", () -> jobs.start(new Jobs.Request("another", "fixture", "full")));
            require(jobs.cancel(id).state.equals("cancelling"), "Cancellation skipped cleanup");
            require(snapshot(root, id).get("state").getAsString().equals("cancelling")
                    && snapshot(root, id).get("completed").getAsLong() == 1, "Cancellation request did not immediately persist pending progress");
            try { handoff.get().call(); throw new AssertionError("Client handoff lost its job cancellation"); }
            catch (java.util.concurrent.CancellationException expected) { /* owning job is cancelled */ }
            Jobs.checkpoint();
            require(jobs.results(null, 20).rows.isEmpty(), "Incomplete export leaked into published datasets");
            cleanup.countDown();
            require(cleaned.await(5, TimeUnit.SECONDS), "Cleanup was interrupted");
            await(jobs, id, "cancelled");
            require(jobs.cancel(id).state.equals("cancelled"), "Cancellation was not idempotent");
        }
        require(snapshot(root, id).get("state").getAsString().equals("cancelled"), "Cancellation completion was not persisted");
        try (Jobs jobs = new Jobs(root.resolve("jobs"), context -> {
            context.publish(() -> SourceTest.fixture(root.resolve("capture"), root.resolve("datasets")));
        })) {
            require(jobs.start(request).state.equals("cancelled"), "Restart forgot the retry key");
            require(jobs.start(reordered).state.equals("cancelled"), "Restart changed normalized probe identity");
            require("test-copy".equals(jobs.read(id).request.world), "Restart lost the selected world");
            expect("key_conflict", () -> jobs.start(new Jobs.Request("once", "fixture", "full")));
            try { jobs.read(id).request.probes.add(Probe.defaults()); throw new AssertionError("Restored parameters are mutable"); }
            catch (UnsupportedOperationException expected) { /* validated immutable request */ }
            try { jobs.read(id).request.probes.get(1).channels.put("coil", 9); throw new AssertionError("Restored channels are mutable"); }
            catch (UnsupportedOperationException expected) { /* immutable channel map */ }
            Jobs.Job success = jobs.start(new Jobs.Request("second", "fixture", "full"));
            await(jobs, success.id, "succeeded");
            require(jobs.results(null, 20).rows.size() == 1, "Published result is missing");
            require(jobs.cancel(success.id).state.equals("succeeded"), "Late cancellation changed a committed result");
            require(jobs.start(success.request, () -> { throw new AssertionError("A retry must not require a loaded world"); }).id.equals(success.id),
                    "Completed retry started new game work");
            expect("world_unavailable", () -> jobs.start(new Jobs.Request("no-world", "fixture", "full"), () -> {
                throw new Jobs.Fault("world_unavailable", "No world");
            }));
        }
        Path report = root.resolve("jobs").resolve(id + ".json");
        com.google.gson.JsonObject damaged = parse(Files.readAllBytes(report));
        damaged.getAsJsonObject("request").add("probes", array(object("count", 65, "channels", object())));
        Files.write(report, damaged.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        try (Jobs ignored = new Jobs(root.resolve("jobs"), context -> {})) { throw new AssertionError("Invalid journal parameters were restored"); }
        catch (java.io.IOException expected) { require(expected.getMessage().contains("Invalid job report"), "Wrong journal error"); }
        api(Files.createTempDirectory("nesql-api-"));
        recipeScope(Files.createTempDirectory("nesql-scope-"));
    }

    private static void snapshots(Path root) throws Exception {
        java.util.concurrent.atomic.AtomicLong clock = new java.util.concurrent.atomic.AtomicLong();
        java.util.concurrent.atomic.AtomicReference<Jobs.Context> context = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicReference<Jobs.Observer> observer = new java.util.concurrent.atomic.AtomicReference<>();
        CountDownLatch ready = new CountDownLatch(1), finish = new CountDownLatch(1);
        Jobs.Request request = new Jobs.Request("snapshots", "fixture", "data");
        String id;
        try (Jobs jobs = new Jobs(root.resolve("jobs"), value -> {
            context.set(value); observer.set(Jobs.observer()); ready.countDown();
            finish.await();
            throw new Jobs.Fault("snapshot_fixture", "Expected fixture failure");
        }, clock::get)) {
            id = jobs.start(request).id;
            try {
                require(ready.await(5, TimeUnit.SECONDS), "Snapshot fixture did not start");
                Jobs.Context task = context.get(); Jobs.Observer updates = observer.get();
                task.progress("capture", 0, 20, "Begin capture");
                updates.update(operation("1", "queued", "call", 0));
                com.google.gson.JsonObject first = snapshot(root, id);
                require(first.get("stage").getAsString().equals("capture")
                        && first.getAsJsonObject("operation").get("state").getAsString().equals("queued"), "Initial stage/phase was not persisted");
                task.progress("capture", 1, 20, "First row");
                updates.update(operation("1", "running", "call", 3));
                require(jobs.read(id).completed == 1 && jobs.read(id).sequence == first.get("sequence").getAsLong() + 1
                        && jobs.current().operation.get("state").getAsString().equals("running"),
                        "Coalescing lost live progress or running observation");
                require(snapshot(root, id).equals(first), "Ordinary progress/observer updates replaced the snapshot inside 250 ms");

                clock.set(TimeUnit.MILLISECONDS.toNanos(249));
                updates.update(operation("1", "done", "call", 7));
                updates.update(operation("1", "done", "call", 7));
                updates.update(operation("2", "done", "call", 11));
                require(snapshot(root, id).equals(first), "Completed operation samples bypassed the snapshot gate");
                com.google.gson.JsonObject timings = task.timings();
                com.google.gson.JsonObject timing = timings.getAsJsonArray("operations").get(0).getAsJsonObject();
                require(timing.get("calls").getAsInt() == 2 && timing.get("runMicros").getAsLong() == 18
                        && timings.getAsJsonObject("phases").get("call").getAsLong() == 18
                        && jobs.current().performance.getAsJsonObject("clientRun").get("calls").getAsInt() == 2,
                        "Coalescing dropped or duplicated in-memory operation timing");
                clock.set(TimeUnit.MILLISECONDS.toNanos(250));
                task.progress("capture", 2, 20, "Second row");
                com.google.gson.JsonObject elapsed = snapshot(root, id);
                require(elapsed.get("completed").getAsLong() == 2
                        && elapsed.getAsJsonObject("operation").get("id").getAsString().equals("2"), "250 ms boundary did not flush the latest snapshot");
                clock.set(TimeUnit.MILLISECONDS.toNanos(499));
                task.progress("capture", 3, 20, "Third row");
                require(snapshot(root, id).equals(elapsed), "Successful snapshot did not restart the gate");
                clock.set(TimeUnit.MILLISECONDS.toNanos(500));
                updates.update(operation("3", "running", "call", 1));
                require(snapshot(root, id).get("completed").getAsLong() == 3, "Observer did not flush elapsed progress");

                updates.update(operation("3", "running", "encode", 2));
                com.google.gson.JsonObject phase = snapshot(root, id);
                require(phase.getAsJsonObject("operation").get("phase").getAsString().equals("encode"), "Phase change was throttled");
                task.progress("capture", 4, 20, "Fourth row");
                require(snapshot(root, id).equals(phase), "Phase snapshot did not coalesce subsequent ordinary progress");
                Path temporary = root.resolve("jobs").resolve(id + ".tmp");
                Files.createDirectory(temporary);
                try {
                    task.progress("publish", 5, 20, "Prepare publication");
                    throw new AssertionError("Stage snapshot I/O failure was swallowed");
                } catch (java.io.IOException expected) { /* retrying this boundary still needs an immediate snapshot */ }
                finally { Files.delete(temporary); }
                task.progress("publish", 5, 20, "Prepare publication");
                require(snapshot(root, id).get("stage").getAsString().equals("publish"), "Retrying a failed stage snapshot was throttled");

                Files.createDirectory(temporary);
                clock.set(TimeUnit.MILLISECONDS.toNanos(750));
                try {
                    task.progress("publish", 6, 20, "Failed snapshot");
                    throw new AssertionError("Snapshot I/O failure was swallowed");
                } catch (java.io.IOException expected) { /* a due snapshot must propagate its write failure */ }
                finally { Files.delete(temporary); }
                task.progress("publish", 7, 20, "Retry snapshot");
                require(snapshot(root, id).get("completed").getAsLong() == 7, "Failed snapshot incorrectly advanced the gate");
                task.progress("publish", 8, 20, "Final pending row");
                updates.update(operation("3", "done", "encode", 9));
                require(snapshot(root, id).get("completed").getAsLong() == 7, "Pending tail was not coalesced");
            } finally { finish.countDown(); await(jobs, id, "failed"); }
        }
        com.google.gson.JsonObject terminal = snapshot(root, id);
        require(terminal.get("state").getAsString().equals("failed") && terminal.get("completed").getAsLong() == 8
                && terminal.getAsJsonObject("operation").get("state").getAsString().equals("done")
                && terminal.getAsJsonObject("performance").getAsJsonObject("clientRun").get("micros").getAsLong() == 27,
                "Terminal snapshot lost pending progress, operation or timing");
        String next;
        try (Jobs jobs = new Jobs(root.resolve("jobs"), value -> { throw new Jobs.Fault("next_fixture", "Expected next failure"); }, clock::get)) {
            require(jobs.start(request).id.equals(id) && jobs.current().completed == 8, "Restart lost coalesced terminal state or retry identity");
            next = jobs.start(new Jobs.Request("next", "fixture", "data")).id;
            await(jobs, next, "failed");
        }
        require(snapshot(root, next).get("state").getAsString().equals("failed"), "New job inherited the previous snapshot gate");
        System.out.println("Job snapshots: 250 ms coalescing, live timing, stage/phase flush, I/O retry, terminal state and restart passed");
    }

    private static com.google.gson.JsonObject operation(String id, String state, String phase, long running) {
        return object("id", id, "name", "capture", "phase", phase, "state", state, "created", "2026-10-05T00:00:00Z",
                "queueMicros", "2", "runMicros", Long.toString(running), "slow", false,
                "phases", object(phase, Long.toString(running)), "stack", array(), "framesOmitted", 0);
    }

    private static com.google.gson.JsonObject snapshot(Path root, String id) throws Exception {
        return parse(Files.readAllBytes(root.resolve("jobs").resolve(id + ".json")));
    }

    private static void recipeScope(Path root) throws Exception {
        com.google.gson.JsonObject body = object("key", "scope", "name", "recipes", "profile", "full", "world", "test-copy",
                "scope", "recipes", "handlers", array("category_" + String.join("", java.util.Collections.nCopies(64, "a"))));
        Jobs.Request request = Jobs.Request.parse(body);
        require(request.recipeScope() && !request.complete(), "Recipe scope masqueraded as a complete export");
        require(new Jobs.Request("default", "all", "full").complete(), "Default export lost its complete scope");
        String id;
        try (Jobs jobs = new Jobs(root.resolve("jobs"), context -> { throw new Jobs.Fault("fixture", "No game capture in this lifecycle test"); })) {
            id = jobs.start(request).id;
            long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (!jobs.read(id).state.equals("failed") && System.nanoTime() < end) Thread.sleep(5);
            require(jobs.read(id).state.equals("failed"), "Fixture task did not stop");
        }
        try (Jobs jobs = new Jobs(root.resolve("jobs"), context -> { throw new AssertionError("Scope retry executed game work"); })) {
            require(jobs.read(id).request.recipeScope() && jobs.start(request).id.equals(id), "Restart lost explicit scope or retry identity");
            com.google.gson.JsonObject changed = parse(body.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)); changed.remove("scope");
            expect("key_conflict", () -> jobs.start(Jobs.Request.parse(changed)));
        }
        for (String field : new String[]{"handlers", "world", "profile", "scope", "check"}) {
            com.google.gson.JsonObject invalid = parse(body.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            if (field.equals("handlers")) invalid.add(field, array());
            else if (field.equals("world")) invalid.remove(field);
            else if (field.equals("profile")) invalid.addProperty(field, "images");
            else if (field.equals("scope")) invalid.addProperty(field, "anything");
            else { invalid.addProperty("name", "check"); invalid.addProperty("profile", "data"); invalid.add("check", object("domain", "recipes")); }
            expect("invalid_request", () -> Jobs.Request.parse(invalid));
        }
    }

    private static void api(Path root) throws Exception {
        try (Jobs jobs = new Jobs(root.resolve("jobs"), context -> context.publish(() ->
                SourceTest.fixture(root.resolve(context.id()), root.resolve("datasets"), context.request().name)));
             GameServer server = new GameServer(root, jobs, () -> object("ready", true), () -> {})) {
            require(jobs.current() == null, "An empty journal reported a current job");
            com.google.gson.JsonObject connection = parse(Files.readAllBytes(root.resolve("connection.json")));
            require(http(connection, "GET", "/jobs", null, null).get("job").isJsonNull(), "An empty API journal is not explicit");
            for (int index = 0; index < 3; index++) {
                com.google.gson.JsonObject body = object("key", "api-" + index, "name", "fixture-" + index, "profile", "full");
                if (index == 2) {
                    com.google.gson.JsonArray handlers = array(), probes = array();
                    for (int handler = 0; handler < 512; handler++) handlers.add(new com.google.gson.JsonPrimitive(String.format("category_%064x", handler)));
                    com.google.gson.JsonObject channels = object();
                    for (int channel = 0; channel < 32; channel++) channels.addProperty(String.format("channel_%056d", channel), 65535);
                    for (int count = 1; count <= 16; count++) probes.add(object("count", count, "channels", channels));
                    body.add("handlers", handlers); body.add("probes", probes);
                    require(body.toString().length() > 65536, "The request budget test did not reach the old body limit");
                }
                String id = http(connection, "POST", "/jobs", body, null)
                        .get("id").getAsString();
                await(jobs, id, "succeeded");
                require(jobs.current().id.equals(id), "Current job did not follow API submissions");
            }
            Jobs.Page first = jobs.results(null, 2);
            require(first.rows.size() == 2 && first.next != null, "Export page has no continuation");
            Jobs.Page last = jobs.results(first.next, 2);
            require(last.rows.size() == 1 && last.next == null && last.rows.get(0).id.compareTo(first.next) > 0, "Export cursor duplicated or lost a result");
            com.google.gson.JsonObject page = http(connection, "GET", "/exports?limit=2&after=" + first.next, null, null);
            require(page.getAsJsonArray("rows").size() == 1 && page.get("next").isJsonNull(), "API pagination differs from the journal");
            String result = first.rows.get(0).id;
            com.google.gson.JsonObject exported = http(connection, "GET", "/exports/" + result, null, null);
            require(exported.get("id").getAsString().equals(result) && exported.getAsJsonObject("counts").get("rows").getAsLong() > 0,
                    "Published manifest summary lost its declared counts");
            require(http(connection, "GET", "/exports?limit=2&limit=3", null, "invalid_request").has("error"), "Duplicate query was accepted");
            http(connection, "GET", "/exports?limit=101", null, "invalid_request");
            http(connection, "GET", "/game?limit=2", null, "invalid_request");
            com.google.gson.JsonObject stale = parse(connection.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            stale.addProperty("session", java.util.UUID.randomUUID().toString());
            http(stale, "GET", "/game", null, "game_changed");
            stale.addProperty("token", "invalid");
            http(stale, "GET", "/game", null, "unauthorized");
            http(connection, "POST", "/jobs", object("key", "removed", "name", "fixture", "profile", "ui"), "invalid_request");
            for (com.google.gson.JsonObject invalid : java.util.Arrays.asList(
                    object("count", 0), object("count", 65), object("count", new com.google.gson.JsonPrimitive(1.5)), object("count", "1"),
                    object("count", 1, "channels", object("Coil", 2)), object("count", 1, "channels", object("coil", 0)),
                    object("count", 1, "channels", object("coil", 65536)), object("count", 1, "channels", object("coil", "2")),
                    object("count", 1, "channels", object("coil", new com.google.gson.JsonPrimitive(2.5))), object("count", 1, "extra", true))) {
                http(connection, "POST", "/jobs", object("key", "invalid", "name", "fixture", "profile", "full", "probes", array(invalid)), "invalid_request");
            }
            http(connection, "POST", "/jobs", object("key", "invalid", "name", "fixture", "profile", "full", "probes", array()), "invalid_request");
            http(connection, "POST", "/jobs", object("key", "invalid", "name", "fixture", "profile", "full", "probes", array(Probe.defaults().json(), Probe.defaults().json())), "invalid_request");
            Path manifest = root.resolve("datasets").resolve(result).resolve("manifest.json");
            com.google.gson.JsonObject changed = parse(Files.readAllBytes(manifest));
            changed.addProperty("environment", String.join("", java.util.Collections.nCopies(64, "0")));
            Files.write(manifest, changed.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            http(connection, "GET", "/exports/" + result, null, "game_error");
        }
        require(!Files.exists(root.resolve("connection.json")), "API close left live discovery behind");
        try (Jobs jobs = new Jobs(root.resolve("jobs"), context -> { throw new AssertionError("Restart must not rerun exports"); })) {
            require(jobs.current() != null && jobs.results(null, 100).rows.size() == 3, "Restart lost the journal indexes");
            require(jobs.start(new Jobs.Request("api-0", "fixture-0", "full")).state.equals("succeeded"), "Restart lost a historical retry key");
            require(jobs.current().request.probes.size() == 16 && jobs.current().request.handlers.size() == 512, "Restart lost the largest valid request");
        }
    }

    static com.google.gson.JsonObject http(com.google.gson.JsonObject connection, String method, String endpoint,
                                                   com.google.gson.JsonObject body, String expectedError) throws Exception {
        java.net.HttpURLConnection request = (java.net.HttpURLConnection) new java.net.URL(
                "http://127.0.0.1:" + connection.get("port").getAsInt() + endpoint).openConnection();
        try {
            request.setConnectTimeout(2000); request.setReadTimeout(5000);
            request.setRequestMethod(method);
            request.setRequestProperty("Authorization", "Bearer " + connection.get("token").getAsString());
            request.setRequestProperty("X-NESQL-Session", connection.get("session").getAsString());
            if (body != null) {
                request.setDoOutput(true); request.setRequestProperty("Content-Type", "application/json");
                try (java.io.OutputStream output = request.getOutputStream()) { output.write(body.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
            }
            int status = request.getResponseCode();
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            try (java.io.InputStream input = status < 400 ? request.getInputStream() : request.getErrorStream()) {
                byte[] buffer = new byte[4096]; int count;
                while ((count = input.read(buffer)) != -1) bytes.write(buffer, 0, count);
            }
            com.google.gson.JsonObject result = parse(bytes.toByteArray());
            if (expectedError == null) require(status < 400, "API request failed: " + result);
            else require(status >= 400 && result.getAsJsonObject("error").get("code").getAsString().equals(expectedError), "Unexpected API error: " + result);
            return result;
        } finally { request.disconnect(); }
    }

    private static com.google.gson.JsonObject parse(byte[] bytes) {
        return new com.google.gson.JsonParser().parse(new String(bytes, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static void await(Jobs jobs, String id, String state) throws Exception {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < end) {
            Jobs.Job job = jobs.read(id);
            if (job.state.equals(state)) return;
            if (job.state.equals("failed")) throw new AssertionError(job.error);
            Thread.sleep(5);
        }
        throw new AssertionError("Job did not reach " + state);
    }

    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void expect(String code, Checked action) throws Exception {
        try { action.run(); }
        catch (Jobs.Fault expected) { require(expected.code.equals(code), "Wrong fault code"); return; }
        throw new AssertionError("Expected " + code);
    }
    private interface Checked { void run() throws Exception; }
}
