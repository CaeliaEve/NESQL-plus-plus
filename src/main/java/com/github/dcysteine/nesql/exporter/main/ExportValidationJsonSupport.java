package com.github.dcysteine.nesql.exporter.main;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.GZIPInputStream;

/** Shared low-level JSON, counting, and ratio helpers for validation probes. */
final class ExportValidationJsonSupport {
    private ExportValidationJsonSupport() {}

    static JsonObject readJsonObject(File file) {
        if (file == null || !file.exists()) {
            return null;
        }
        try (FileInputStream fis = new FileInputStream(file);
             InputStreamReader reader = new InputStreamReader(fis, StandardCharsets.UTF_8)) {
            JsonElement element = new JsonParser().parse(reader);
            return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
        } catch (Exception e) {
            Logger.MOD.warn("Failed to read JSON object from {}", file.getAbsolutePath(), e);
            return null;
        }
    }

    static JsonObject readCountsObject(File reportFile) {
        JsonObject root = readJsonObject(reportFile);
        if (root == null || !root.has("counts") || !root.get("counts").isJsonObject()) {
            return null;
        }
        return root.getAsJsonObject("counts");
    }

    static long readLongMember(JsonObject object, String memberName) {
        if (object != null
                && object.has(memberName)
                && object.get(memberName).isJsonPrimitive()
                && object.get(memberName).getAsJsonPrimitive().isNumber()) {
            return object.get(memberName).getAsLong();
        }
        return 0L;
    }

    static int readIntMember(JsonObject object, String memberName) {
        if (object != null
                && object.has(memberName)
                && object.get(memberName).isJsonPrimitive()
                && object.get(memberName).getAsJsonPrimitive().isNumber()) {
            return object.get(memberName).getAsInt();
        }
        return 0;
    }

    static String readStringMember(JsonObject object, String memberName) {
        if (object != null
                && object.has(memberName)
                && object.get(memberName).isJsonPrimitive()) {
            String value = object.get(memberName).getAsString();
            return value == null ? null : value.trim();
        }
        return null;
    }

    static JsonArray copyArray(JsonObject object, String memberName, int limit) {
        JsonArray out = new JsonArray();
        if (object == null || !object.has(memberName) || !object.get(memberName).isJsonArray()) {
            return out;
        }
        JsonArray source = object.get(memberName).getAsJsonArray();
        for (int i = 0; i < source.size() && i < limit; i++) {
            out.add(source.get(i));
        }
        return out;
    }

    static int arraySize(JsonObject object, String memberName) {
        if (object != null && object.has(memberName) && object.get(memberName).isJsonArray()) {
            return object.get(memberName).getAsJsonArray().size();
        }
        return 0;
    }

    static int safeInt(long value) {
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) Math.max(0L, value);
    }

    static Double ratio(long numerator, long denominator) {
        if (denominator <= 0L) {
            return null;
        }
        return Math.round((numerator / (double) denominator) * 10000.0) / 10000.0;
    }

    static Double ratio(int numerator, int denominator) {
        if (denominator <= 0) {
            return null;
        }
        return Math.round((numerator / (double) denominator) * 10000.0) / 10000.0;
    }

    static int countFiles(File root, String suffix) {
        if (root == null || !root.exists()) {
            return 0;
        }
        Counter counter = new Counter();
        countFiles(root, suffix, counter);
        return counter.value;
    }

    static long countGzipJsonl(File file) {
        if (file == null || !file.exists() || !file.isFile()) {
            return 0L;
        }
        long count = 0L;
        try (BufferedReader reader = openMaybeGzipUtf8(file)) {
            while (reader.readLine() != null) {
                count++;
            }
        } catch (Exception e) {
            Logger.MOD.warn("Failed to count JSONL stream {}", file.getAbsolutePath(), e);
        }
        return count;
    }

    static BufferedReader openMaybeGzipUtf8(File file) throws Exception {
        FileInputStream fis = new FileInputStream(file);
        if (file.getName().endsWith(".gz")) {
            return new BufferedReader(new InputStreamReader(new GZIPInputStream(fis), StandardCharsets.UTF_8));
        }
        return new BufferedReader(new InputStreamReader(fis, StandardCharsets.UTF_8));
    }

    static void addSample(List<String> samples, String value) {
        if (value != null && !value.isEmpty() && samples.size() < 100 && !samples.contains(value)) {
            samples.add(value);
        }
    }

    static String firstNonEmpty(String first, String second, String third) {
        if (first != null && !first.isEmpty()) {
            return first;
        }
        if (second != null && !second.isEmpty()) {
            return second;
        }
        if (third != null && !third.isEmpty()) {
            return third;
        }
        return null;
    }

    private static void countFiles(File file, String suffix, Counter counter) {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isFile()) {
            if (file.getName().endsWith(suffix)) {
                counter.value++;
            }
            return;
        }

        File[] children = file.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            countFiles(child, suffix, counter);
        }
    }

    private static final class Counter {
        int value;
    }
}
