package com.github.dcysteine.nesql.exporter.task;

import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.source.Dataset;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/** Authenticated loopback job API used only by the separate MCP process. */
public final class GameServer implements AutoCloseable {
    private static final Gson JSON = new GsonBuilder().disableHtmlEscaping().serializeNulls().create();
    private static final int RESPONSE_LIMIT = 1024 * 1024;
    private final HttpServer server;
    private final Jobs jobs;
    private final Supplier<JsonObject> inspect;
    private final Runnable requireWorld;
    private final Path directory;
    private final String token;
    private final String session = UUID.randomUUID().toString();
    private final ThreadPoolExecutor requests;

    public GameServer(Path directory, Jobs jobs, Supplier<JsonObject> inspect, Runnable requireWorld) throws IOException {
        this.directory = directory.toAbsolutePath().normalize();
        this.jobs = jobs;
        this.inspect = inspect;
        this.requireWorld = requireWorld;
        byte[] secret = new byte[32];
        new SecureRandom().nextBytes(secret);
        token = CanonicalJson.hex(secret);
        server = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 8);
        requests = new ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(16), action -> {
            Thread thread = new Thread(action, "NESQL local API");
            thread.setDaemon(true);
            return thread;
        });
        server.setExecutor(requests);
        server.createContext("/", this::handle);
        try {
            Dataset.directory(this.directory);
            JsonObject connection = new JsonObject();
            connection.addProperty("protocol", 1);
            connection.addProperty("port", server.getAddress().getPort());
            connection.addProperty("token", token);
            connection.addProperty("session", session);
            Path temporary = this.directory.resolve("connection.tmp");
            Path target = this.directory.resolve("connection.json");
            if (Files.exists(temporary, LinkOption.NOFOLLOW_LINKS)) Dataset.plain(temporary);
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) Dataset.plain(target);
            Files.write(temporary, CanonicalJson.bytes(connection));
            restrict(temporary);
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            server.start();
        } catch (IOException | RuntimeException error) {
            server.stop(0);
            requests.shutdownNow();
            throw error;
        }
    }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            exchange.getResponseHeaders().set("X-NESQL-Session", session);
            String authorization = exchange.getRequestHeaders().getFirst("Authorization");
            if (authorization == null || !MessageDigest.isEqual(
                    ("Bearer " + token).getBytes(StandardCharsets.US_ASCII), authorization.getBytes(StandardCharsets.US_ASCII))) {
                reply(exchange, 401, error("unauthorized", "A valid local token is required"));
                return;
            }
            if (!session.equals(exchange.getRequestHeaders().getFirst("X-NESQL-Session"))) {
                reply(exchange, 409, error("game_changed", "Rediscover the current game session"));
                return;
            }
            if (exchange.getRequestHeaders().containsKey("Origin")
                    || !("127.0.0.1:" + server.getAddress().getPort()).equals(exchange.getRequestHeaders().getFirst("Host"))) {
                reply(exchange, 403, error("invalid_origin", "Only the local MCP bridge may use this endpoint"));
                return;
            }
            String path = exchange.getRequestURI().getRawPath();
            String method = exchange.getRequestMethod();
            Map<String, String> query = query(exchange);
            if (!query.isEmpty() && !(method.equals("GET") && path.equals("/exports"))) {
                throw new Jobs.Fault("invalid_request", "Only export listings accept query parameters");
            }
            Object response;
            int status = 200;
            if (method.equals("GET") && path.equals("/game")) response = inspect.get();
            else if (method.equals("POST") && path.equals("/jobs")) {
                Jobs.Request request = Jobs.Request.parse(body(exchange));
                response = jobs.start(request, requireWorld);
                status = 202;
            } else if (method.equals("GET") && path.equals("/jobs")) {
                response = java.util.Collections.singletonMap("job", jobs.current());
            } else if (method.equals("GET") && path.matches("/jobs/[a-zA-Z0-9_-]{1,80}")) {
                response = java.util.Collections.singletonMap("job", jobs.read(path.substring("/jobs/".length())));
            } else if (method.equals("POST") && path.matches("/jobs/[a-zA-Z0-9_-]{1,80}/cancel")) {
                if (!body(exchange).entrySet().isEmpty()) throw new Jobs.Fault("invalid_request", "Cancellation has no body fields");
                response = jobs.cancel(path.substring("/jobs/".length(), path.length() - "/cancel".length()));
            } else if (method.equals("GET") && path.equals("/exports")) {
                response = jobs.results(query.get("after"), query.containsKey("limit") ? Integer.parseInt(query.get("limit")) : 20);
            } else if (method.equals("GET") && path.matches("/exports/[a-f0-9]{64}")) {
                response = exported(path.substring("/exports/".length()));
            } else {
                reply(exchange, 404, error("route_missing", "Unknown local game endpoint"));
                return;
            }
            reply(exchange, status, response);
        } catch (Jobs.Fault error) {
            int status = error.code.endsWith("missing") ? 404 : error.code.equals("invalid_request") ? 400 : 409;
            reply(exchange, status, error(error.code, error.getMessage()));
        } catch (IllegalArgumentException error) {
            reply(exchange, 400, error("invalid_request", error.getMessage()));
        } catch (Exception error) {
            reply(exchange, 500, error("game_error", error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage()));
        } finally {
            exchange.close();
        }
    }

    private JsonObject exported(String id) throws IOException {
        Jobs.Result completed = jobs.result(id);
        Path root = directory.resolve("datasets").resolve(id);
        if (!root.toString().equals(completed.path)) throw new IOException("Export is outside the dataset directory");
        Path manifestPath = root.resolve("manifest.json");
        Dataset.plain(manifestPath);
        if (!Files.isRegularFile(manifestPath, LinkOption.NOFOLLOW_LINKS) || Files.size(manifestPath) > 64L * 1024 * 1024) {
            throw new IOException("Invalid source manifest file");
        }
        JsonObject manifest = new JsonParser().parse(new String(Files.readAllBytes(manifestPath), StandardCharsets.UTF_8)).getAsJsonObject();
        if (!Dataset.FORMAT.equals(string(manifest, "format")) || !id.equals(string(manifest, "id"))
                || count(manifest, "revision") != Dataset.REVISION) throw new IOException("Invalid source manifest");
        manifest.remove("id");
        if (!CanonicalJson.digest(manifest).equals(id)) throw new IOException("Source manifest digest mismatch");
        manifest.addProperty("id", id);
        if (!string(manifest, "environment").matches("[a-f0-9]{64}") || !manifest.get("producer").isJsonObject()
                || !manifest.get("scope").isJsonObject() || !manifest.get("files").isJsonArray()) throw new IOException("Invalid source metadata");
        JsonObject result = new JsonObject();
        result.addProperty("path", root.toString());
        result.addProperty("manifestPath", manifestPath.toString());
        for (String field : Arrays.asList("id", "format", "revision", "producer", "environment", "scope")) {
            result.add(field, manifest.get(field));
        }
        JsonObject counts = new JsonObject();
        long bytes = 0;
        long rows = 0;
        Set<String> paths = new HashSet<>();
        for (JsonElement file : manifest.getAsJsonArray("files")) {
            JsonObject descriptor = file.getAsJsonObject();
            String path = string(descriptor, "path");
            if (!path.matches("[a-zA-Z0-9._/-]+") || path.startsWith("/") || Arrays.asList(path.split("/", -1)).stream()
                    .anyMatch(part -> part.isEmpty() || part.equals(".") || part.equals("..")) || !paths.add(path)
                    || !string(descriptor, "sha256").matches("[a-f0-9]{64}")) throw new IOException("Invalid source file declaration");
            bytes = Math.addExact(bytes, count(descriptor, "bytes"));
            rows = Math.addExact(rows, count(descriptor, "rows"));
        }
        counts.addProperty("files", manifest.getAsJsonArray("files").size());
        counts.addProperty("bytes", Long.toString(bytes));
        counts.addProperty("rows", Long.toString(rows));
        result.add("counts", counts);
        return result;
    }

    private static long count(JsonObject object, String key) throws IOException {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()
                || !value.getAsString().matches("0|[1-9][0-9]{0,18}")) throw new IOException("Invalid source count: " + key);
        try { return Long.parseLong(value.getAsString()); }
        catch (NumberFormatException error) { throw new IOException("Source count exceeds its limit: " + key, error); }
    }

    private static Map<String, String> query(HttpExchange exchange) {
        Map<String, String> values = new LinkedHashMap<>();
        String raw = exchange.getRequestURI().getRawQuery();
        if (raw == null) return values;
        if (raw.length() > 100) throw new Jobs.Fault("invalid_request", "Query exceeds size limit");
        for (String field : raw.split("&", -1)) {
            String[] pair = field.split("=", -1);
            if (pair.length != 2 || !(pair[0].equals("after") && pair[1].matches("[a-f0-9]{64}")
                    || pair[0].equals("limit") && pair[1].matches("[1-9][0-9]{0,2}"))
                    || values.putIfAbsent(pair[0], pair[1]) != null) throw new Jobs.Fault("invalid_request", "Invalid export query");
        }
        return values;
    }

    private static String string(JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw new Jobs.Fault("invalid_request", "Expected string: " + key);
        return value.getAsString();
    }

    private static JsonObject body(HttpExchange exchange) throws IOException {
        String type = exchange.getRequestHeaders().getFirst("Content-Type");
        if (type == null || !type.split(";", 2)[0].trim().equalsIgnoreCase("application/json")) throw new Jobs.Fault("invalid_request", "Content-Type must be application/json");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (InputStream input = exchange.getRequestBody()) {
            byte[] buffer = new byte[1024];
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (bytes.size() + count > 131072) throw new Jobs.Fault("invalid_request", "Request body exceeds 128 KiB");
                bytes.write(buffer, 0, count);
            }
        }
        try {
            JsonElement value = new JsonParser().parse(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
            if (!value.isJsonObject()) throw new IllegalArgumentException();
            return value.getAsJsonObject();
        } catch (RuntimeException error) { throw new Jobs.Fault("invalid_request", "Expected a JSON object"); }
    }

    private static Map<String, Object> error(String code, String message) {
        Map<String, String> details = new LinkedHashMap<>();
        details.put("code", code);
        details.put("message", message);
        return java.util.Collections.singletonMap("error", details);
    }

    private static void reply(HttpExchange exchange, int status, Object result) throws IOException {
        byte[] bytes = JSON.toJson(result).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > RESPONSE_LIMIT) {
            status = 500;
            bytes = JSON.toJson(error("response_limit", "Game response exceeds 1 MiB; narrow the request")).getBytes(StandardCharsets.UTF_8);
        }
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) { output.write(bytes); }
    }

    private static void restrict(Path path) throws IOException {
        java.nio.file.attribute.PosixFileAttributeView posix = Files.getFileAttributeView(path, java.nio.file.attribute.PosixFileAttributeView.class);
        if (posix != null) posix.setPermissions(java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
        java.nio.file.attribute.AclFileAttributeView acl = Files.getFileAttributeView(path, java.nio.file.attribute.AclFileAttributeView.class);
        if (acl != null) {
            java.nio.file.attribute.AclEntry entry = java.nio.file.attribute.AclEntry.newBuilder()
                    .setType(java.nio.file.attribute.AclEntryType.ALLOW).setPrincipal(Files.getOwner(path))
                    .setPermissions(java.util.EnumSet.allOf(java.nio.file.attribute.AclEntryPermission.class)).build();
            acl.setAcl(java.util.Collections.singletonList(entry));
        }
    }

    @Override
    public void close() throws IOException {
        server.stop(0);
        requests.shutdownNow();
        jobs.close();
        Path connection = directory.resolve("connection.json");
        if (Files.exists(connection)) {
            Dataset.plain(connection);
            if (Files.size(connection) > 4096) throw new IOException("Invalid game connection file");
            JsonObject value = new JsonParser().parse(new String(Files.readAllBytes(connection), StandardCharsets.UTF_8)).getAsJsonObject();
            if (session.equals(string(value, "session"))) Files.delete(connection);
        }
    }
}
