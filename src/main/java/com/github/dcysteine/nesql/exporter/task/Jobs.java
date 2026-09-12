package com.github.dcysteine.nesql.exporter.task;

import com.github.dcysteine.nesql.exporter.source.Dataset;
import com.github.dcysteine.nesql.exporter.source.Probe;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** One export at a time, with durable retry keys and cooperative cancellation. */
public final class Jobs implements AutoCloseable {
    private static final Gson JSON = new GsonBuilder().disableHtmlEscaping()
            .registerTypeAdapter(Request.class, (JsonDeserializer<Request>) (value, type, context) -> Request.parse(value.getAsJsonObject())).create();
    private static final ThreadLocal<Context> CURRENT = new ThreadLocal<>();
    private static final int EVENT_LIMIT = 32;
    private static final int REPORT_LIMIT = 256 * 1024;
    private final Path directory;
    private final Task task;
    private final FileChannel lease;
    private final FileLock owner;
    private final Map<String, String> retries = new LinkedHashMap<>();
    private final NavigableMap<String, Result> results = new TreeMap<>();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(action -> {
        Thread thread = new Thread(action, "NESQL export");
        thread.setDaemon(true);
        return thread;
    });
    private Job active;
    private Job latest;
    private boolean closed;

    public interface Task {
        void run(Context context) throws Exception;
    }

    public static final class Request {
        public final String key;
        public final String name;
        public final String profile;
        public final List<String> handlers;
        public final List<Probe> probes;
        public final String world;

        public Request(String key, String name, String profile) {
            this(key, name, profile, java.util.Collections.emptyList());
        }

        public Request(String key, String name, String profile, List<String> handlers) {
            this(key, name, profile, handlers, java.util.Collections.singletonList(Probe.defaults()));
        }

        public Request(String key, String name, String profile, List<String> handlers, List<Probe> probes) {
            this(key, name, profile, handlers, probes, null);
        }

        public Request(String key, String name, String profile, List<String> handlers, List<Probe> probes, String world) {
            identifier(key, "key");
            identifier(name, "name");
            if (!Arrays.asList("full", "data", "images").contains(profile)) {
                throw new Fault("invalid_request", "Unknown export profile: " + profile);
            }
            this.key = key;
            this.name = name;
            this.profile = profile;
            if (world != null && (world.isEmpty() || world.length() > 128 || world.equals(".") || world.equals("..")
                    || world.chars().anyMatch(character -> character < 32 || character == 127 || character == '/' || character == '\\'))) {
                throw new Fault("invalid_request", "world must be a save folder name");
            }
            this.world = world;
            if (handlers == null || handlers.size() > 512) throw new Fault("invalid_request", "Invalid handler selection");
            java.util.TreeSet<String> selected = new java.util.TreeSet<>();
            for (String handler : handlers) {
                if (handler == null || !handler.matches("category_[a-f0-9]{64}") || !selected.add(handler)) {
                    throw new Fault("invalid_request", "Handler selection requires unique category ids");
                }
            }
            if (profile.equals("images") && !selected.isEmpty()) throw new Fault("invalid_request", "The images profile has no recipe handlers");
            this.handlers = java.util.Collections.unmodifiableList(new ArrayList<>(selected));
            try { this.probes = Probe.order(probes); }
            catch (IllegalArgumentException failure) { throw new Fault("invalid_request", failure.getMessage()); }
        }

        public static Request parse(JsonObject body) {
            if (body == null || !body.entrySet().stream().allMatch(entry -> Arrays.asList("key", "name", "profile", "handlers", "probes", "world").contains(entry.getKey()))) {
                throw new Fault("invalid_request", "Unknown export request field");
            }
            List<String> handlers = new ArrayList<>();
            if (body.has("handlers")) {
                if (!body.get("handlers").isJsonArray()) throw new Fault("invalid_request", "handlers must be an array");
                for (JsonElement value : body.getAsJsonArray("handlers")) handlers.add(string(value, "handler"));
            }
            List<Probe> probes = new ArrayList<>();
            if (body.has("probes")) {
                if (!body.get("probes").isJsonArray()) throw new Fault("invalid_request", "probes must be an array");
                for (JsonElement value : body.getAsJsonArray("probes")) probes.add(Probe.parse(value));
            } else probes.add(Probe.defaults());
            return new Request(string(body.get("key"), "key"), string(body.get("name"), "name"), string(body.get("profile"), "profile"), handlers, probes,
                    !body.has("world") || body.get("world").isJsonNull() ? null : string(body.get("world"), "world"));
        }

        private static String string(JsonElement value, String name) {
            if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw new Fault("invalid_request", "Expected " + name + " string");
            return value.getAsString();
        }

        private boolean matches(Request other) {
            return key.equals(other.key) && name.equals(other.name) && profile.equals(other.profile) && handlers.equals(other.handlers)
                    && probes.equals(other.probes) && java.util.Objects.equals(world, other.world);
        }

        public void checkWorld(String folder) {
            if (world != null && !world.equals(folder)) throw new Fault("world_changed", "Expected save " + world + "; loaded " + folder);
        }
    }

    public static final class Result {
        public final String id;
        public final String path;

        public Result(String id, Path path) {
            if (id == null || !id.matches("[a-f0-9]{64}")) {
                throw new IllegalArgumentException("Result id must be a SHA-256 digest");
            }
            this.id = id;
            this.path = path.toAbsolutePath().normalize().toString();
        }
    }

    public static final class Page {
        public final List<Result> rows;
        public final String next;

        private Page(List<Result> rows, String next) { this.rows = rows; this.next = next; }
    }

    public static final class Fault extends RuntimeException {
        private static final long serialVersionUID = 1L;
        public final String code;

        public Fault(String code, String message) {
            super(message);
            this.code = code;
        }
    }

    public static final class Event {
        public final long sequence;
        public final String time;
        public final String stage;
        public final String message;

        private Event(long sequence, String stage, String message) {
            this.sequence = sequence;
            this.time = Instant.now().toString();
            this.stage = stage;
            this.message = message.length() <= 384 ? message : message.substring(0, 384);
        }
    }

    public static final class Job {
        public String id;
        public Request request;
        public String state;
        public String stage;
        public String created;
        public String finished;
        public long completed;
        public long total;
        public long sequence;
        public List<Event> events = new ArrayList<>();
        public Result result;
        public Map<String, String> error;
        private transient Thread thread;
        private transient boolean cancelled;

        private boolean terminal() {
            return "succeeded".equals(state) || "failed".equals(state) || "cancelled".equals(state);
        }
    }

    public final class Context {
        private final Job job;

        private Context(Job job) {
            this.job = job;
        }

        public Request request() { return job.request; }
        public String id() { return job.id; }

        public void check() {
            synchronized (Jobs.this) {
                if (job.cancelled || Thread.currentThread().isInterrupted()) {
                    throw new CancellationException("Export cancelled");
                }
            }
        }

        public void progress(String stage, long completed, long total, String message) throws IOException {
            check();
            if (completed < 0 || total < 0 || completed > total) {
                throw new IllegalArgumentException("Invalid export progress");
            }
            synchronized (Jobs.this) {
                job.completed = completed;
                job.total = total;
                event(job, stage, message);
                save(job, false);
            }
        }

        /** Prepare and validate files first; only the final atomic publication belongs here. */
        public void publish(Callable<Result> publication) throws Exception {
            synchronized (Jobs.this) {
                check();
                if (job.terminal()) throw new IllegalStateException("Job already completed");
                event(job, "publish", "Publishing validated source dataset");
                job.result = publication.call();
                if (job.result == null) throw new IllegalStateException("Publication returned no dataset");
                job.state = "succeeded";
                job.finished = Instant.now().toString();
                event(job, "complete", "Source dataset published");
                save(job);
            }
        }
    }

    public Jobs(Path directory, Task task) throws IOException {
        this.directory = directory.toAbsolutePath().normalize();
        this.task = task;
        Dataset.directory(this.directory);
        Path lock = this.directory.resolve(".lock");
        if (Files.exists(lock, LinkOption.NOFOLLOW_LINKS)) Dataset.plain(lock);
        lease = FileChannel.open(lock, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        try {
            owner = lease.tryLock();
            if (owner == null) throw new IOException("Another exporter owns this job directory");
            try (DirectoryStream<Path> paths = Files.newDirectoryStream(this.directory, "*.json")) {
                for (Path path : paths) {
                    Job job = load(path);
                    if (!job.terminal()) {
                        fail(job, "game_stopped", "The game stopped before this export finished");
                        save(job);
                    } else track(job);
                }
            }
        } catch (IOException | RuntimeException error) {
            lease.close();
            worker.shutdownNow();
            throw error;
        }
    }

    public synchronized Job start(Request request) throws IOException {
        return start(request, () -> {});
    }

    public synchronized Job start(Request request, Runnable available) throws IOException {
        if (closed) throw new Fault("game_stopped", "The exporter is stopping");
        String previous = retries.get(request.key);
        if (previous != null) {
            Job job = read(previous);
            if (!job.request.matches(request)) throw new Fault("key_conflict", "This key belongs to different export arguments");
            return job;
        }
        if (active != null) throw new Fault("export_busy", "Export " + active.id + " is still running");
        available.run();
        Job job = new Job();
        job.id = UUID.randomUUID().toString();
        job.request = request;
        job.state = "queued";
        job.created = Instant.now().toString();
        event(job, "queued", "Export queued");
        save(job);
        active = job;
        latest = job;
        worker.execute(() -> execute(job));
        return copy(job);
    }

    public synchronized Job read(String id) throws IOException {
        return copy(require(id));
    }

    public synchronized Job current() { return active != null ? copy(active) : latest == null ? null : copy(latest); }

    public synchronized Job cancel(String id) throws IOException {
        Job job = require(id);
        if (!job.terminal() && !job.cancelled) {
            job.cancelled = true;
            job.state = "cancelling";
            event(job, job.stage, "Cancellation requested; waiting for cleanup");
            try { save(job); }
            finally { if (job.thread != null) job.thread.interrupt(); }
        }
        return copy(job);
    }

    public synchronized Page results(String after, int limit) {
        if (after != null && !after.matches("[a-f0-9]{64}")) throw new Fault("invalid_request", "Invalid export cursor");
        if (limit < 1 || limit > 100) throw new Fault("invalid_request", "Export limit must be between 1 and 100");
        List<Result> rows = new ArrayList<>();
        Iterable<Result> values = after == null ? results.values() : results.tailMap(after, false).values();
        for (Result result : values) {
            if (rows.size() == limit) return new Page(rows, rows.get(rows.size() - 1).id);
            rows.add(result);
        }
        return new Page(rows, null);
    }

    public synchronized Result result(String id) {
        if (id == null || !id.matches("[a-f0-9]{64}")) throw new Fault("invalid_request", "Invalid export id");
        Result result = results.get(id);
        if (result == null) throw new Fault("export_missing", "Unknown completed export");
        return result;
    }

    public static void checkpoint() {
        Context context = CURRENT.get();
        if (context != null) context.check();
        else if (Thread.currentThread().isInterrupted()) throw new CancellationException("Export interrupted");
    }

    /** Keeps cancellation attached to the job when game work crosses a thread boundary. */
    public static <T> Callable<T> bind(Callable<T> action) {
        Context owner = CURRENT.get();
        return () -> {
            Context previous = CURRENT.get();
            if (owner == null) CURRENT.remove();
            else CURRENT.set(owner);
            try {
                checkpoint();
                T value = action.call();
                checkpoint();
                return value;
            } finally {
                if (previous == null) CURRENT.remove();
                else CURRENT.set(previous);
            }
        };
    }

    /** Only resource disposal uses this scope; it cannot collect or publish more game data. */
    static <T> T cleanup(Callable<T> action) throws Exception {
        Context previous = CURRENT.get();
        boolean interrupted = Thread.interrupted();
        CURRENT.remove();
        try { return action.call(); }
        finally {
            if (previous != null) CURRENT.set(previous);
            if (interrupted) Thread.currentThread().interrupt();
        }
    }

    private void execute(Job job) {
        Context context = new Context(job);
        CURRENT.set(context);
        try {
            synchronized (this) {
                job.thread = Thread.currentThread();
                context.check();
                job.state = "running";
                save(job);
            }
            task.run(context);
            synchronized (this) {
                if (!job.terminal()) throw new IllegalStateException("Export returned without publishing a dataset");
            }
        } catch (Exception error) {
            synchronized (this) {
                if (!job.terminal()) {
                    if (job.cancelled || error instanceof InterruptedException || error instanceof CancellationException) {
                        job.state = "cancelled";
                        job.finished = Instant.now().toString();
                        event(job, "cancelled", "Export stopped and temporary files closed");
                    } else {
                        fail(job, error instanceof Fault ? ((Fault) error).code : "export_failed", error.toString());
                    }
                }
            }
        } finally {
            try {
                synchronized (this) {
                    if (!job.terminal()) fail(job, "export_failed", "Export terminated unexpectedly");
                    try { save(job); }
                    catch (IOException error) { throw new IllegalStateException("Cannot persist export result", error); }
                    finally { job.thread = null; active = null; }
                }
            } finally {
                CURRENT.remove();
                Thread.interrupted();
            }
        }
    }

    private Job require(String id) throws IOException {
        identifier(id, "job id");
        if (active != null && active.id.equals(id)) return active;
        if (latest != null && latest.id.equals(id)) return latest;
        Path path = directory.resolve(id + ".json");
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) throw new Fault("job_missing", "Unknown export job: " + id);
        return load(path);
    }

    private Job load(Path path) throws IOException {
        Dataset.plain(path);
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.size(path) > REPORT_LIMIT) {
            throw new IOException("Invalid job report file: " + path);
        }
        try {
            Job job = JSON.fromJson(new String(Files.readAllBytes(path), StandardCharsets.UTF_8), Job.class);
            if (job == null || job.request == null || job.id == null || !path.getFileName().toString().equals(job.id + ".json")) {
                throw new IllegalArgumentException("Missing report identity");
            }
            identifier(job.id, "job id");
            Instant.parse(job.created);
            if (!Arrays.asList("queued", "running", "cancelling", "succeeded", "failed", "cancelled").contains(job.state)
                    || job.events == null || job.events.size() > EVENT_LIMIT || job.completed < 0 || job.completed > job.total) {
                throw new IllegalArgumentException("Invalid report state");
            }
            if (job.terminal()) Instant.parse(job.finished);
            if ("succeeded".equals(job.state)) {
                if (job.result == null) throw new IllegalArgumentException("Missing published result");
                Result result = new Result(job.result.id, java.nio.file.Paths.get(job.result.path));
                if (!result.path.equals(directory.getParent().resolve("datasets").resolve(result.id).toString())) {
                    throw new IllegalArgumentException("Result is outside the dataset directory");
                }
            } else if (job.result != null) throw new IllegalArgumentException("Unpublished job has a result");
            return job;
        } catch (RuntimeException error) { throw new IOException("Invalid job report: " + path, error); }
    }

    private void track(Job job) throws IOException {
        String previous = retries.putIfAbsent(job.request.key, job.id);
        if (previous != null && !previous.equals(job.id)) throw new IOException("Duplicate export retry key: " + job.request.key);
        if ("succeeded".equals(job.state)) results.put(job.result.id, job.result);
        if (latest == null || latest.id.equals(job.id) || job.created.compareTo(latest.created) > 0
                || (job.created.equals(latest.created) && job.id.compareTo(latest.id) > 0)) latest = job;
    }

    private static Job copy(Job job) {
        return JSON.fromJson(JSON.toJson(job), Job.class);
    }

    private static void identifier(String value, String label) {
        if (value == null || !value.matches("[a-zA-Z0-9_-]{1,80}")) {
            throw new Fault("invalid_request", "Invalid " + label);
        }
    }

    private static void event(Job job, String stage, String message) {
        job.stage = stage;
        job.events.add(new Event(++job.sequence, stage, message));
        if (job.events.size() > EVENT_LIMIT) job.events.remove(0);
    }

    private static void fail(Job job, String code, String message) {
        job.state = "failed";
        job.finished = Instant.now().toString();
        job.error = new LinkedHashMap<>();
        job.error.put("code", code);
        job.error.put("message", message.length() <= 2000 ? message : message.substring(0, 2000));
        event(job, "failed", message);
    }

    private void save(Job job) throws IOException {
        save(job, true);
    }

    private void save(Job job, boolean durable) throws IOException {
        Dataset.plain(directory);
        Path temporary = directory.resolve(job.id + ".tmp");
        Path target = directory.resolve(job.id + ".json");
        if (Files.exists(temporary, LinkOption.NOFOLLOW_LINKS)) Dataset.plain(temporary);
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) Dataset.plain(target);
        byte[] bytes = JSON.toJson(job).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > REPORT_LIMIT) throw new IOException("Job report exceeds 128 KiB");
        try (FileChannel file = FileChannel.open(temporary, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) file.write(buffer);
            if (durable) file.force(true);
        }
        Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        track(job);
    }

    @Override
    public void close() throws IOException {
        IOException failure = null;
        synchronized (this) {
            if (!lease.isOpen()) return;
            closed = true;
            try { if (active != null) cancel(active.id); }
            catch (IOException error) { failure = error; }
            finally { worker.shutdown(); }
        }
        try {
            if (!worker.awaitTermination(5, TimeUnit.SECONDS)) throw new IOException("Export cleanup is still running; the journal remains locked");
            synchronized (this) {
                if (lease.isOpen()) {
                    try { owner.release(); }
                    finally { lease.close(); }
                }
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            IOException interrupted = new IOException("Interrupted while waiting for export cleanup", error);
            if (failure != null) interrupted.addSuppressed(failure);
            throw interrupted;
        } catch (IOException error) {
            if (failure != null) error.addSuppressed(failure);
            throw error;
        }
        if (failure != null) throw failure;
    }
}
