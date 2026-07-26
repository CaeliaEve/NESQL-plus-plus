package com.github.dcysteine.nesql.exporter.local;

import com.github.dcysteine.nesql.exporter.canonical.ResourceAuthorityContract;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;

/** Writes the authoritative physical-frame contract consumed by animation compilers. */
final class RawExportAnimationFrameMaterializationWriter {
    private static final String SCHEMA_VERSION =
            "nesqlpp/raw-export/alpha1/animation-frame-materialization";
    private static final Gson GSON = new GsonBuilder().serializeNulls().create();

    RawAnimationFrameMaterializationCounts write(JsonArray assetRows, File out)
            throws IOException {
        RawAnimationFrameMaterializationCounts counts =
                new RawAnimationFrameMaterializationCounts();
        try (OutputStreamWriter writer = RawExportSidecarFileOps.createUtf8JsonlWriter(out)) {
            if (assetRows == null) {
                return counts;
            }
            for (JsonElement element : assetRows) {
                if (element == null || !element.isJsonObject()) {
                    continue;
                }
                JsonObject asset = element.getAsJsonObject();
                if (!hasMaterializationContract(asset)) {
                    continue;
                }
                JsonObject row = toRow(asset);
                String status = row.get("materializationStatus").getAsString();
                GSON.toJson(row, writer);
                writer.write('\n');
                counts.total++;
                if (ResourceAuthorityContract.ANIMATION_MATERIALIZED.equals(status)) {
                    counts.materialized++;
                } else if (ResourceAuthorityContract.ANIMATION_STATIC.equals(status)) {
                    counts.staticFrames++;
                } else {
                    counts.unavailable++;
                }
            }
        }
        return counts;
    }

    private static boolean hasMaterializationContract(JsonObject asset) {
        return asset.has("materializationStatus")
                || asset.has("materializedFrameCount")
                || asset.has("distinctFrameCount");
    }

    private static JsonObject toRow(JsonObject asset) throws IOException {
        String assetId = requiredString(asset, "assetId");
        int runtimeFrameCount = nonNegativeInt(asset, "runtimeFrameCount");
        int declaredFrameCount = nonNegativeInt(asset, "declaredFrameCount");
        int materializedFrameCount = nonNegativeInt(asset, "materializedFrameCount");
        int distinctFrameCount = nonNegativeInt(asset, "distinctFrameCount");
        String expectedStatus;
        try {
            expectedStatus = ResourceAuthorityContract.animationStatus(
                    materializedFrameCount,
                    distinctFrameCount);
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid animation frame materialization counts", e);
        }
        String status = requiredString(asset, "materializationStatus");
        if (!expectedStatus.equals(status)) {
            throw new IOException(
                    "Animation frame materialization status does not match counts for "
                            + stringValue(asset, "assetId"));
        }
        String reason = requiredString(asset, "materializationReason");
        JsonArray frames = arrayValue(asset, "materializedFrames");
        if (frames.size() != materializedFrameCount) {
            throw new IOException(
                    "Materialized frame descriptor count does not match materializedFrameCount for "
                            + stringValue(asset, "assetId"));
        }

        JsonObject row = new JsonObject();
        row.addProperty("schemaVersion", SCHEMA_VERSION);
        row.addProperty("assetId", assetId);
        copy(row, asset, "variantKey");
        copy(row, asset, "iconName");
        row.addProperty("runtimeFrameCount", runtimeFrameCount);
        row.addProperty("declaredFrameCount", declaredFrameCount);
        row.addProperty("materializedFrameCount", materializedFrameCount);
        row.addProperty("distinctFrameCount", distinctFrameCount);
        row.addProperty("materializationStatus", status);
        row.addProperty("materializationReason", reason);
        JsonElement atlasFile = asset.get("nativeSpriteAtlasFile");
        if (atlasFile == null || atlasFile.isJsonNull()) {
            atlasFile = asset.get("atlasFile");
        }
        if (atlasFile != null && !atlasFile.isJsonNull()) {
            row.add("atlasFile", atlasFile);
        }
        row.add("frames", frames);
        return row;
    }

    private static int nonNegativeInt(JsonObject object, String key) throws IOException {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            return 0;
        }
        try {
            int value = element.getAsInt();
            if (value < 0) {
                throw new IOException(key + " must be non-negative");
            }
            return value;
        } catch (RuntimeException e) {
            throw new IOException(key + " must be an integer", e);
        }
    }

    private static String requiredString(JsonObject object, String key) throws IOException {
        String value = stringValue(object, key);
        if (value == null || value.trim().isEmpty()) {
            throw new IOException(key + " must be non-empty");
        }
        return value;
    }

    private static String stringValue(JsonObject object, String key) {
        try {
            JsonElement element = object.get(key);
            return element == null || element.isJsonNull() ? null : element.getAsString();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static JsonArray arrayValue(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonArray()
                ? value.getAsJsonArray()
                : new JsonArray();
    }

    private static void copy(JsonObject target, JsonObject source, String key) {
        JsonElement value = source.get(key);
        if (value != null && !value.isJsonNull()) {
            target.add(key, value);
        }
    }
}
