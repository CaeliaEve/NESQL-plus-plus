package com.github.dcysteine.nesql.exporter.local;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

final class RawExportEntityModelWriter {
    private final File repositoryDirectory;
    private final File rawDir;
    private final String schemaVersion;

    RawExportEntityModelWriter(File repositoryDirectory, File rawDir, String schemaVersion) {
        this.repositoryDirectory = repositoryDirectory;
        this.rawDir = rawDir;
        this.schemaVersion = schemaVersion;
    }

    long write() throws IOException {
        JsonObject previews = readObject(new File(repositoryDirectory, "canonical/entity-previews.json"));
        JsonObject models = readObject(new File(repositoryDirectory, "canonical/entity-models.json"));
        JsonArray previewEntries = previews == null ? null : previews.getAsJsonArray("entries");
        JsonArray modelEntries = models == null ? null : models.getAsJsonArray("entries");

        Map<String, JsonObject> byMobName = new LinkedHashMap<String, JsonObject>();
        if (previewEntries != null) {
            for (JsonElement element : previewEntries) {
                if (element == null || !element.isJsonObject()) {
                    continue;
                }
                JsonObject preview = element.getAsJsonObject();
                String mobName = stringAt(preview, "mobName");
                if (mobName == null || mobName.trim().length() == 0) {
                    continue;
                }
                JsonObject row = entityRow(byMobName, mobName.trim());
                copyElement(row, "preview", preview, "");
                copyString(row, "entityId", preview, "mobName");
                copyString(row, "displayName", preview, "localizedName");
                copyString(row, "modId", preview, "modId");
                copyString(row, "previewImage", preview, "relativeGifPath");
                copyFirstNumber(row, "frameCount", preview, "frameCount");
                copyFirstNumber(row, "frameDurationMs", preview, "frameDurationMs");
                copyFirstNumber(row, "width", preview, "width");
                copyFirstNumber(row, "height", preview, "height");
                copyString(row, "previewRenderMode", preview, "renderMode");
            }
        }
        if (modelEntries != null) {
            for (JsonElement element : modelEntries) {
                if (element == null || !element.isJsonObject()) {
                    continue;
                }
                JsonObject model = element.getAsJsonObject();
                String mobName = stringAt(model, "mobName");
                if (mobName == null || mobName.trim().length() == 0) {
                    continue;
                }
                JsonObject row = entityRow(byMobName, mobName.trim());
                copyElement(row, "model", model, "");
                copyString(row, "entityId", model, "mobName");
                copyString(row, "displayName", model, "localizedName");
                copyString(row, "modId", model, "modId");
                copyString(row, "modelPath", model, "relativeModelPath");
                copyFirstNumber(row, "componentCount", model, "componentCount");
                copyString(row, "modelRenderMode", model, "renderMode");
            }
        }

        JsonArray rows = new JsonArray();
        for (JsonObject row : byMobName.values()) {
            row.addProperty("schemaVersion", schemaVersion + "/entity-model");
            rows.add(row);
        }
        return writeArrayAsJsonl(rows, new File(rawDir, "models/entities/index.jsonl.gz"));
    }

    private static JsonObject entityRow(Map<String, JsonObject> rows, String mobName) {
        JsonObject row = rows.get(mobName);
        if (row == null) {
            row = new JsonObject();
            row.addProperty("entityId", mobName);
            row.addProperty("mobName", mobName);
            rows.put(mobName, row);
        }
        return row;
    }

    private static void copyString(JsonObject target, String to, JsonObject source, String dottedPath) {
        String value = stringAt(source, dottedPath);
        if (value != null && value.trim().length() > 0) {
            target.addProperty(to, value.trim());
        }
    }

    private static void copyElement(JsonObject target, String to, JsonObject source, String dottedPath) {
        JsonElement value = elementAt(source, dottedPath);
        if (value != null && !value.isJsonNull()) {
            target.add(to, cloneJson(value));
        }
    }

    private static void copyFirstNumber(JsonObject target, String to, JsonObject source, String... dottedPaths) {
        for (String dottedPath : dottedPaths) {
            JsonElement value = elementAt(source, dottedPath);
            if (value == null || value.isJsonNull() || !value.isJsonPrimitive()) {
                continue;
            }
            try {
                target.add(to, cloneJson(value));
                return;
            } catch (Exception ignored) {
                // Try the next candidate.
            }
        }
    }

    private static JsonElement cloneJson(JsonElement value) {
        return value == null ? null : new JsonParser().parse(value.toString());
    }


    private static JsonObject readObject(File file) {
        if (file == null || !file.exists()) {
            return null;
        }
        try (FileInputStream fis = new FileInputStream(file);
             java.io.InputStreamReader reader = new java.io.InputStreamReader(fis, StandardCharsets.UTF_8)) {
            JsonElement element = new JsonParser().parse(reader);
            return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static long writeArrayAsJsonl(JsonArray array, File out) throws IOException {
        long count = 0L;
        Gson gson = new GsonBuilder().serializeNulls().create();
        File parent = out.getParentFile();
        if (parent != null) {
            ensureDirectory(parent);
        }
        try (OutputStreamWriter writer = createUtf8Writer(out)) {
            if (array != null) {
                for (JsonElement element : array) {
                    gson.toJson(element, writer);
                    writer.write('\n');
                    count++;
                }
            }
        }
        return count;
    }

    private static JsonElement elementAt(JsonObject object, String dottedPath) {
        if (dottedPath == null || dottedPath.length() == 0) {
            return object;
        }
        JsonElement current = object;
        for (String part : dottedPath.split("\\.")) {
            if (current == null || !current.isJsonObject()) {
                return null;
            }
            current = current.getAsJsonObject().get(part);
        }
        return current;
    }

    private static String stringAt(JsonObject object, String dottedPath) {
        JsonElement current = object;
        for (String part : dottedPath.split("\\.")) {
            if (current == null || !current.isJsonObject()) {
                return null;
            }
            current = current.getAsJsonObject().get(part);
        }
        if (current == null || current.isJsonNull()) {
            return null;
        }
        try {
            return current.isJsonPrimitive() ? current.getAsString() : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void ensureDirectory(File directory) throws IOException {
        if (directory != null && !directory.exists() && !directory.mkdirs()) {
            throw new IOException("Failed to create directory: " + directory.getAbsolutePath());
        }
    }

    private static OutputStreamWriter createUtf8Writer(File out) throws IOException {
        FileOutputStream fos = new FileOutputStream(out);
        if (out.getName().endsWith(".gz")) {
            return new OutputStreamWriter(new GZIPOutputStream(fos), StandardCharsets.UTF_8);
        }
        return new OutputStreamWriter(fos, StandardCharsets.UTF_8);
    }
}
