package com.github.dcysteine.nesql.exporter.task;

import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.source.Dataset;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Diagnostic requests and reports never become source datasets. */
public final class Checks {
    public static final int REPORT_LIMIT = 16 * 1024 * 1024;
    private Checks() {}

    public static final class Selection {
        public final String domain;
        public final List<Integer> controllers;
        public final int offset, limit;

        private Selection(String domain, List<Integer> controllers, int offset, int limit) {
            if (!Arrays.asList("structures", "recipes").contains(domain)) throw invalid("Choose structures or recipes");
            if (controllers.size() > 512 || new TreeSet<>(controllers).size() != controllers.size()) throw invalid("Controller ids must be unique, with at most 512 targets");
            if (domain.equals("recipes") && !controllers.isEmpty()) throw invalid("Recipe checks do not accept controller ids");
            this.domain = domain;
            this.controllers = Collections.unmodifiableList(new ArrayList<>(new TreeSet<>(controllers)));
            this.offset = offset; this.limit = limit;
        }

        public static Selection parse(JsonObject body) {
            fields(body, "domain", "controllers", "offset", "limit");
            JsonElement domain = body.get("domain");
            if (domain == null || !domain.isJsonPrimitive() || !domain.getAsJsonPrimitive().isString()) throw invalid("domain must be a string");
            List<Integer> controllers = new ArrayList<>();
            if (body.has("controllers")) {
                if (!body.get("controllers").isJsonArray()) throw invalid("controllers must be an array");
                for (JsonElement id : body.getAsJsonArray("controllers")) controllers.add(integer(id, "controller", 0, 32767));
            }
            int offset = body.has("offset") ? integer(body.get("offset"), "offset", 0, 1_000_000) : 0;
            int limit = body.has("limit") ? integer(body.get("limit"), "limit", 1, 4096) : 128;
            if (domain.getAsString().equals("structures") && (offset != 0 || limit != 128)) throw invalid("Recipe ranges do not apply to structures");
            return new Selection(domain.getAsString(), controllers, offset, limit);
        }

        @Override public boolean equals(Object value) {
            if (!(value instanceof Selection)) return false;
            Selection other = (Selection) value;
            return domain.equals(other.domain) && controllers.equals(other.controllers) && offset == other.offset && limit == other.limit;
        }
        @Override public int hashCode() { return java.util.Objects.hash(domain, controllers, offset, limit); }
    }

    public static Jobs.Request request(JsonObject body) {
        fields(body, "key", "world", "domain", "controllers", "handlers", "probes", "offset", "limit");
        JsonObject request = object("name", "check", "profile", "data");
        for (String field : new String[] {"key", "world", "handlers", "probes"}) if (body.has(field)) request.add(field, body.get(field));
        JsonObject selection = new JsonObject();
        for (String field : new String[] {"domain", "controllers", "offset", "limit"}) if (body.has(field)) selection.add(field, body.get(field));
        request.add("check", selection);
        return Jobs.Request.parse(request);
    }

    public static final class Summary {
        public String path, sha256, status;
        public long bytes;
        public int total, passed, failed, unsupported, partial, pending;
    }

    public static final class Report {
        private final Path path;
        private final JsonObject record;
        private final JsonArray rows;
        private final Jobs.Context context;

        public Report(Path directory, Jobs.Context context, JsonObject environment, JsonArray rows) throws IOException {
            Dataset.directory(directory);
            this.path = directory.resolve(context.id() + ".json");
            if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Diagnostic report already exists");
            this.context = context; this.rows = rows;
            this.record = object("format", "nesql.check", "job", context.id(), "created", Instant.now().toString(),
                    "request", new com.google.gson.Gson().toJsonTree(context.request()), "environment", environment,
                    "status", "running", "rows", rows);
            save();
        }

        public JsonObject row(int index) { return rows.get(index).getAsJsonObject(); }
        public int size() { return rows.size(); }
        public void inventory(JsonArray handlers) { record.add("handlers", handlers); }
        public void planning(JsonObject timings) { record.add("planning", timings); }

        public Summary save() throws IOException {
            boolean interrupted = Thread.interrupted();
            try { return write(); }
            finally { if (interrupted) Thread.currentThread().interrupt(); }
        }

        private Summary write() throws IOException {
            Summary summary = new Summary();
            summary.status = record.get("status").getAsString(); summary.total = rows.size();
            for (JsonElement element : rows) {
                String status = element.getAsJsonObject().get("status").getAsString();
                switch (status) {
                    case "passed": summary.passed++; break;
                    case "failed": summary.failed++; break;
                    case "unsupported": summary.unsupported++; break;
                    case "partial": summary.partial++; break;
                    default: summary.pending++;
                }
            }
            byte[] bytes = CanonicalJson.bytes(record);
            if (bytes.length > REPORT_LIMIT) throw new IOException("Diagnostic report exceeds 16 MiB");
            Path temporary = path.resolveSibling(context.id() + ".tmp");
            Dataset.plain(path.getParent());
            if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) Dataset.plain(path);
            if (Files.exists(temporary, LinkOption.NOFOLLOW_LINKS)) Dataset.plain(temporary);
            Files.write(temporary, bytes);
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            summary.path = path.toString(); summary.sha256 = CanonicalJson.digest(bytes); summary.bytes = bytes.length;
            context.report(summary);
            return summary;
        }

        public Summary finish(String status, Throwable error) throws IOException {
            record.addProperty("status", status); record.addProperty("finished", Instant.now().toString());
            if (error != null) record.add("error", failure(error));
            return save();
        }
    }

    public interface Guard { void check() throws Exception; }
    public interface Step { void run(int index, JsonObject row) throws Exception; }

    /** The same failure-isolation loop serves native checks and lifecycle regressions. */
    public static void sweep(Jobs.Context context, Report report, Guard guard, Step step) throws Exception {
        for (int index = 0; index < report.size(); index++) {
            context.check(); guard.check();
            JsonObject row = report.row(index); row.addProperty("status", "running"); report.save();
            String target = row.has("controller") ? "controller " + row.get("controller").getAsInt() : row.get("handler").getAsString();
            context.progress("check_" + context.request().check.domain, index, report.size(), "Checking " + target);
            long began = System.nanoTime();
            try {
                step.run(index, row);
                if (!Arrays.asList("passed", "failed", "partial", "unsupported").contains(row.get("status").getAsString())) {
                    throw new Jobs.Fault("check_incomplete", "The check did not record a target outcome");
                }
            } catch (Exception error) {
                row.addProperty("status", "failed"); row.add("error", failure(error));
                if (fatal(error)) throw error;
            } catch (Error error) {
                row.addProperty("status", "failed"); row.add("error", failure(error));
                throw error;
            } finally {
                row.addProperty("elapsedMicros", Long.toString((System.nanoTime() - began) / 1000));
                row.add("timings", context.timings()); report.save();
            }
            guard.check();
            context.progress("check_" + context.request().check.domain, index + 1, report.size(), "Checked " + target + ": " + row.get("status").getAsString());
        }
    }

    public static boolean fatal(Exception error) {
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Throwable cause = error; cause != null && seen.add(cause); cause = cause.getCause()) {
            if (cause instanceof java.util.concurrent.CancellationException || cause instanceof InterruptedException
                    || cause instanceof IOException || cause instanceof Error || cause.getSuppressed().length != 0) return true;
            if (cause instanceof Jobs.Fault) {
                String code = ((Jobs.Fault) cause).code;
                if (code.equals("check_cleanup") || code.equals("preview_cleanup") || code.startsWith("client_")
                        || code.endsWith("_changed") || code.equals("world_unavailable")) return true;
            }
        }
        return false;
    }

    public static JsonObject failure(Throwable error) {
        return failure(error, Collections.newSetFromMap(new IdentityHashMap<>()), new int[] {0}, 0);
    }

    private static JsonObject failure(Throwable error, Set<Throwable> seen, int[] count, int depth) {
        if (error == null) return null;
        if (depth >= 6 || ++count[0] > 32 || !seen.add(error)) return object("type", error.getClass().getName(), "omitted", true);
        JsonArray stack = new JsonArray(), suppressed = new JsonArray();
        StackTraceElement[] trace = error.getStackTrace();
        for (int index = 0; index < Math.min(24, trace.length); index++) stack.add(value(shorten(trace[index].toString(), 512)));
        for (int index = 0; index < Math.min(4, error.getSuppressed().length); index++) suppressed.add(failure(error.getSuppressed()[index], seen, count, depth + 1));
        return object("type", error.getClass().getName(), "code", error instanceof Jobs.Fault ? ((Jobs.Fault) error).code : "check_failed",
                "message", shorten(error.getMessage(), 2000), "stack", stack, "framesOmitted", Math.max(0, trace.length - 24),
                "cause", failure(error.getCause(), seen, count, depth + 1), "suppressed", suppressed,
                "suppressedOmitted", Math.max(0, error.getSuppressed().length - 4));
    }

    static String shorten(String value, int length) { return value == null || value.length() <= length ? value : value.substring(0, length); }
    private static int integer(JsonElement value, String label, int minimum, int maximum) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber() || !value.getAsString().matches("0|[1-9][0-9]{0,6}")) throw invalid("Invalid " + label);
        int result = value.getAsInt();
        if (result < minimum || result > maximum) throw invalid("Invalid " + label);
        return result;
    }
    private static void fields(JsonObject body, String... allowed) {
        if (body == null || body.entrySet().stream().anyMatch(entry -> !Arrays.asList(allowed).contains(entry.getKey()))) throw invalid("Unknown check request field");
    }
    private static Jobs.Fault invalid(String message) { return new Jobs.Fault("invalid_request", message); }
}
