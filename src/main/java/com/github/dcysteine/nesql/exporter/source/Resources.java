package com.github.dcysteine.nesql.exporter.source;

import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import static com.github.dcysteine.nesql.exporter.source.Json.object;

/** Exact native resource bytes; this evidence makes no claim about item rendering. */
public final class Resources {
    private Resources() {}

    public static String path(String location) {
        if (location == null || location.getBytes(StandardCharsets.UTF_8).length > 4096) throw new IllegalArgumentException("Invalid resource location");
        int colon = location.indexOf(':');
        if (colon < 1 || !location.substring(0, colon).matches("[a-z0-9_.-]+")) throw new IllegalArgumentException("A canonical resource namespace is required");
        if (location.substring(0, colon).equals(".") || location.substring(0, colon).equals("..")) throw new IllegalArgumentException("Invalid resource namespace");
        String path = location.substring(colon + 1);
        if (path.isEmpty() || path.chars().anyMatch(c -> c < 32 || c == 127 || c == ':' || c == '\\')) throw new IllegalArgumentException("Invalid resource path");
        for (String part : path.split("/", -1)) if (part.isEmpty() || part.equals(".") || part.equals("..")) throw new IllegalArgumentException("Invalid resource path segment");
        if (!(path.startsWith("textures/") && (path.endsWith(".png") || path.endsWith(".png.mcmeta")))
                && !(path.startsWith("lang/") && (path.endsWith(".lang") || path.endsWith(".json")))) {
            throw new IllegalArgumentException("Only texture and language resources can be checked");
        }
        return "assets/" + location.substring(0, colon) + "/" + path;
    }

    public static JsonObject read(String location, InputStream stream) throws IOException {
        try (InputStream input = stream) {
            String path = path(location);
            MessageDigest digest = CanonicalJson.sha256();
            byte[] buffer = new byte[65536]; long size = 0;
            while (true) {
                Jobs.checkpoint();
                int count = input.read(buffer);
                if (count < 0) break;
                if (count == 0) {
                    int next = input.read(); if (next < 0) break;
                    buffer[0] = (byte) next; count = 1;
                }
                size += count;
                if (size > 64L * 1024 * 1024) throw new Jobs.Fault("resource_limit", "Native resource exceeds 64 MiB: " + location);
                digest.update(buffer, 0, count);
            }
            return object("resource", location, "path", path, "bytes", Long.toString(size), "sha256", CanonicalJson.hex(digest.digest()));
        }
    }
}
