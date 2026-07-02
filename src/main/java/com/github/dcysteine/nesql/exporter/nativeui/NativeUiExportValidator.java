package com.github.dcysteine.nesql.exporter.nativeui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

/** Validates Native UI export ABI geometry before compiler/frontend consumption. */
public final class NativeUiExportValidator {
    private static final int SAMPLE_LIMIT = 50;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private NativeUiExportValidator() {}

    public static Result validate(File rawDir) throws java.io.IOException {
        Result result = new Result();
        result.schemaVersion = NativeUiExportAbi.NATIVE_UI_VALIDATION_SCHEMA;
        result.generatedAtEpochMs = System.currentTimeMillis();

        File layoutFile = new File(rawDir, NativeUiExportAbi.NEI_HANDLER_LAYOUTS_FILE.replace('/', File.separatorChar));
        if (!layoutFile.exists()) {
            result.missingSurfaceCount++;
            addSample(result.missingSurfaceSamples, "missing-layout-file:" + NativeUiExportAbi.NEI_HANDLER_LAYOUTS_FILE);
            finish(rawDir, result);
            return result;
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new GZIPInputStream(new FileInputStream(layoutFile)), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }
                JsonObject layout = parseObject(line, result);
                if (layout != null) {
                    validateLayout(layout, result);
                }
            }
        }

        finish(rawDir, result);
        return result;
    }

    private static JsonObject parseObject(String line, Result result) {
        try {
            JsonElement element = new JsonParser().parse(line);
            return element == null || !element.isJsonObject() ? null : element.getAsJsonObject();
        } catch (Exception e) {
            result.coordinateContractViolationCount++;
            addSample(result.coordinateContractSamples, "invalid-json:" + truncate(line));
            return null;
        }
    }

    private static void validateLayout(JsonObject layout, Result result) {
        result.layoutCount++;
        String handlerKey = readString(layout, "handlerKey", "unknown");
        int width = readInt(layout, "width", 0);
        int height = readInt(layout, "height", 0);
        checkSurfaceContract("layout:" + handlerKey, layout, result);
        if (width <= 0 || height <= 0) {
            result.backgroundBoundsViolationCount++;
            addSample(result.backgroundBoundsSamples, "layout-size:" + handlerKey + ":" + width + "x" + height);
        }

        JsonElement slotsElement = layout.get("slots");
        if (slotsElement != null && slotsElement.isJsonArray()) {
            int index = 0;
            for (JsonElement slotElement : slotsElement.getAsJsonArray()) {
                if (slotElement != null && slotElement.isJsonObject()) {
                    validateSlot(handlerKey, index, width, height, slotElement.getAsJsonObject(), result);
                }
                index++;
            }
        }

        JsonObject background = object(layout, "nativeBackground");
        if (background == null) {
            result.missingSurfaceCount++;
            addSample(result.missingSurfaceSamples, "missing-background:" + handlerKey);
            return;
        }
        validateBackground(handlerKey, width, height, background, result);
    }

    private static void validateSlot(
            String handlerKey,
            int index,
            int surfaceWidth,
            int surfaceHeight,
            JsonObject slot,
            Result result) {
        result.slotCount++;
        checkSlotContract("slot:" + handlerKey + ":" + index, slot, result);
        int columns = readInt(slot, "columns", 0);
        int rows = readInt(slot, "rows", 0);
        int x = readInt(slot, "x", -1);
        int y = readInt(slot, "y", -1);
        int slotWidth = readInt(slot, "slotWidth", 0);
        int slotHeight = readInt(slot, "slotHeight", 0);
        int pitchX = readInt(slot, "pitchX", 0);
        int pitchY = readInt(slot, "pitchY", 0);
        int right = x + Math.max(0, columns - 1) * pitchX + slotWidth;
        int bottom = y + Math.max(0, rows - 1) * pitchY + slotHeight;
        if (columns <= 0 || rows <= 0 || x < 0 || y < 0 || slotWidth <= 0 || slotHeight <= 0
                || pitchX <= 0 || pitchY <= 0 || right > surfaceWidth || bottom > surfaceHeight) {
            result.slotBoundsViolationCount++;
            addSample(result.slotBoundsSamples,
                    "slot-bounds:" + handlerKey + ":" + index + ":"
                            + x + "," + y + " " + columns + "x" + rows
                            + " slot=" + slotWidth + "x" + slotHeight
                            + " pitch=" + pitchX + "x" + pitchY
                            + " surface=" + surfaceWidth + "x" + surfaceHeight);
        }
    }

    private static void validateBackground(
            String handlerKey,
            int surfaceWidth,
            int surfaceHeight,
            JsonObject background,
            Result result) {
        checkSurfaceContract("background:" + handlerKey, background, result);
        String status = readString(background, "status", "");
        String kind = readString(background, "kind", "");
        if (NativeUiExportAbi.BACKGROUND_STATUS_MISSING.equals(status)
                || NativeUiExportAbi.BACKGROUND_KIND_UNKNOWN.equals(kind)
                || readBoolean(background, "captureRequired", false)) {
            result.missingSurfaceCount++;
            addSample(result.missingSurfaceSamples, "missing-background:" + handlerKey + ":" + kind);
        }

        int width = readInt(background, "width", 0);
        int height = readInt(background, "height", 0);
        if (width <= 0 || height <= 0 || width > surfaceWidth || height > surfaceHeight) {
            result.backgroundBoundsViolationCount++;
            addSample(result.backgroundBoundsSamples,
                    "background-size:" + handlerKey + ":" + width + "x" + height
                            + " surface=" + surfaceWidth + "x" + surfaceHeight);
        }
        JsonObject size = object(background, "recipeBackgroundSize");
        JsonObject offset = object(background, "recipeBackgroundOffset");
        if (size != null && offset != null) {
            int x = readInt(offset, "x", -1);
            int y = readInt(offset, "y", -1);
            int w = readInt(size, "width", 0);
            int h = readInt(size, "height", 0);
            if (x < 0 || y < 0 || w <= 0 || h <= 0 || x + w > surfaceWidth || y + h > surfaceHeight) {
                result.backgroundBoundsViolationCount++;
                addSample(result.backgroundBoundsSamples,
                        "recipe-background:" + handlerKey + ":" + x + "," + y + " " + w + "x" + h
                                + " surface=" + surfaceWidth + "x" + surfaceHeight);
            }
        }
        JsonObject region = object(background, "region");
        if (region != null) {
            int w = readInt(region, "width", 0);
            int h = readInt(region, "height", 0);
            if (w <= 0 || h <= 0) {
                result.backgroundBoundsViolationCount++;
                addSample(result.backgroundBoundsSamples, "background-region:" + handlerKey + ":" + w + "x" + h);
            }
        }
    }

    private static void checkSurfaceContract(String label, JsonObject object, Result result) {
        if (!NativeUiExportAbi.COORDINATE_SPACE.equals(readString(object, "coordinateSpace", ""))
                || !NativeUiExportAbi.SCALE_MODE.equals(readString(object, "scaleMode", ""))
                || !NativeUiExportAbi.ANCHOR.equals(readString(object, "anchor", ""))) {
            result.coordinateContractViolationCount++;
            addSample(result.coordinateContractSamples, "surface-contract:" + label);
        }
    }

    private static void checkSlotContract(String label, JsonObject object, Result result) {
        if (!NativeUiExportAbi.COORDINATE_SPACE.equals(readString(object, "coordinateSpace", ""))
                || !NativeUiExportAbi.ANCHOR.equals(readString(object, "anchor", ""))
                || readInt(object, "slotWidth", 0) != NativeUiExportAbi.SLOT_SIZE
                || readInt(object, "slotHeight", 0) != NativeUiExportAbi.SLOT_SIZE
                || readInt(object, "pitchX", 0) != NativeUiExportAbi.SLOT_PITCH
                || readInt(object, "pitchY", 0) != NativeUiExportAbi.SLOT_PITCH) {
            result.coordinateContractViolationCount++;
            addSample(result.coordinateContractSamples, "slot-contract:" + label);
        }
    }

    private static void finish(File rawDir, Result result) throws java.io.IOException {
        result.status = result.layoutCount > 0
                && result.slotCount > 0
                && result.missingSurfaceCount == 0
                && result.slotBoundsViolationCount == 0
                && result.backgroundBoundsViolationCount == 0
                && result.coordinateContractViolationCount == 0
                ? "ok"
                : "blocked";
        File out = new File(rawDir, NativeUiExportAbi.NATIVE_UI_VALIDATION_FILE.replace('/', File.separatorChar));
        File parent = out.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new java.io.IOException("Failed to create directory: " + parent.getAbsolutePath());
        }
        try (FileOutputStream fos = new FileOutputStream(out);
             OutputStreamWriter writer = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
            GSON.toJson(result, writer);
        }
    }

    private static JsonObject object(JsonObject object, String key) {
        JsonElement element = object == null ? null : object.get(key);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    private static String readString(JsonObject object, String key, String fallback) {
        try {
            JsonElement element = object == null ? null : object.get(key);
            return element == null || element.isJsonNull() ? fallback : element.getAsString();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static int readInt(JsonObject object, String key, int fallback) {
        try {
            JsonElement element = object == null ? null : object.get(key);
            return element == null || element.isJsonNull() ? fallback : element.getAsInt();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static boolean readBoolean(JsonObject object, String key, boolean fallback) {
        try {
            JsonElement element = object == null ? null : object.get(key);
            return element == null || element.isJsonNull() ? fallback : element.getAsBoolean();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static void addSample(List<String> samples, String sample) {
        if (samples.size() < SAMPLE_LIMIT) {
            samples.add(sample);
        }
    }

    private static String truncate(String value) {
        if (value == null || value.length() <= 160) {
            return value;
        }
        return value.substring(0, 160);
    }

    public static final class Result {
        public String schemaVersion;
        public long generatedAtEpochMs;
        public String status;
        public long layoutCount;
        public long slotCount;
        public long missingSurfaceCount;
        public long slotBoundsViolationCount;
        public long backgroundBoundsViolationCount;
        public long coordinateContractViolationCount;
        public List<String> missingSurfaceSamples = new ArrayList<String>();
        public List<String> slotBoundsSamples = new ArrayList<String>();
        public List<String> backgroundBoundsSamples = new ArrayList<String>();
        public List<String> coordinateContractSamples = new ArrayList<String>();
    }
}
