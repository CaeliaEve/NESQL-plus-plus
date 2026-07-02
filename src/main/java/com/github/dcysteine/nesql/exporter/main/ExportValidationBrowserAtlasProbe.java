package com.github.dcysteine.nesql.exporter.main;

import com.github.dcysteine.nesql.exporter.local.RawExportFileCatalog;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Owns browser atlas residency and layout coverage evidence collection. */
final class ExportValidationBrowserAtlasProbe {
    private ExportValidationBrowserAtlasProbe() {}

    static void inspect(File rawDir, ExportValidationReportWriter.ValidationReport report) {
        File browserAtlasFile = RawExportFileCatalog.rawExportFile(
                rawDir,
                RawExportFileCatalog.BROWSER_ATLAS_INDEX_FILE);
        report.browserAtlasPresent = browserAtlasFile.exists();
        if (!browserAtlasFile.exists()) {
            return;
        }

        try (FileInputStream atlasFis = new FileInputStream(browserAtlasFile);
             InputStreamReader atlasReader = new InputStreamReader(atlasFis, StandardCharsets.UTF_8)) {
            JsonObject atlasObject = new JsonParser().parse(atlasReader).getAsJsonObject();
            report.browserAtlasItems = ExportValidationJsonSupport.readIntMember(atlasObject, "itemCount");
            report.browserAtlasAnimatedItems = ExportValidationJsonSupport.readIntMember(atlasObject, "animatedItemCount");
            report.browserAtlasMissingAtlasCount = ExportValidationJsonSupport.readIntMember(atlasObject, "missingAtlasCount");

            Set<String> drawableAtlasItemIds = new HashSet<String>();
            if (atlasObject.has("items") && atlasObject.get("items").isJsonArray()) {
                if (report.browserAtlasItems == 0) {
                    report.browserAtlasItems = atlasObject.get("items").getAsJsonArray().size();
                }
                for (JsonElement element : atlasObject.get("items").getAsJsonArray()) {
                    if (!element.isJsonObject()) {
                        continue;
                    }
                    JsonObject item = element.getAsJsonObject();
                    String itemId = ExportValidationJsonSupport.readStringMember(item, "itemId");
                    if (itemId != null && hasDrawableAtlas(item)) {
                        drawableAtlasItemIds.add(itemId);
                    }
                }
            }
            report.browserAtlasDrawableItems = drawableAtlasItemIds.size();

            report.browserAtlasLayoutItemCount = report.browserAtlasItems;
            report.browserAtlasLayoutCoveredItems = report.browserAtlasDrawableItems;
            report.browserAtlasLayoutMissingItems = Math.max(0, report.browserAtlasItems - report.browserAtlasDrawableItems);
            report.browserAtlasLayoutCoverageRatio = ExportValidationJsonSupport.ratio(
                    report.browserAtlasLayoutCoveredItems,
                    report.browserAtlasLayoutItemCount);
        } catch (Exception e) {
            Logger.MOD.warn("Failed to inspect browser atlas coverage", e);
        }
    }

    private static void inspectBrowserLayoutCoverage(
            File browserLayoutFile,
            Set<String> drawableAtlasItemIds,
            ExportValidationReportWriter.ValidationReport report) {
        try (FileInputStream layoutFis = new FileInputStream(browserLayoutFile);
             InputStreamReader layoutReader = new InputStreamReader(layoutFis, StandardCharsets.UTF_8)) {
            JsonObject layoutObject = new JsonParser().parse(layoutReader).getAsJsonObject();
            Set<String> layoutItemIds = new HashSet<String>();
            collectLayoutItemIds(layoutObject, "items", false, layoutItemIds);
            collectLayoutItemIds(layoutObject, "defaultEntries", true, layoutItemIds);

            report.browserAtlasLayoutItemCount = layoutItemIds.size();
            for (String itemId : layoutItemIds) {
                if (hasDrawableAtlasForItem(drawableAtlasItemIds, itemId)) {
                    report.browserAtlasLayoutCoveredItems++;
                } else {
                    report.browserAtlasLayoutMissingItems++;
                    ExportValidationJsonSupport.addSample(report.browserAtlasLayoutMissingSamples, itemId);
                }
            }
            report.browserAtlasLayoutCoverageRatio = ExportValidationJsonSupport.ratio(
                    report.browserAtlasLayoutCoveredItems,
                    report.browserAtlasLayoutItemCount);
        } catch (Exception e) {
            Logger.MOD.warn("Failed to inspect browser layout atlas coverage", e);
        }
    }

    private static void collectLayoutItemIds(
            JsonObject layoutObject,
            String memberName,
            boolean preferRepresentative,
            Set<String> layoutItemIds) {
        if (!layoutObject.has(memberName) || !layoutObject.get(memberName).isJsonArray()) {
            return;
        }
        for (JsonElement element : layoutObject.get(memberName).getAsJsonArray()) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject item = element.getAsJsonObject();
            String itemId = preferRepresentative
                    ? ExportValidationJsonSupport.firstNonEmpty(
                            ExportValidationJsonSupport.readStringMember(item, "representativeItemId"),
                            ExportValidationJsonSupport.readStringMember(item, "itemId"),
                            null)
                    : ExportValidationJsonSupport.readStringMember(item, "itemId");
            if (itemId != null && !itemId.isEmpty()) {
                layoutItemIds.add(itemId);
            }
        }
    }

    private static boolean hasDrawableAtlas(JsonObject item) {
        return hasAtlasFile(item, "staticAtlas") || hasAtlasFile(item, "animatedAtlas");
    }

    private static boolean hasAtlasFile(JsonObject item, String memberName) {
        if (!item.has(memberName) || !item.get(memberName).isJsonObject()) {
            return false;
        }
        String atlasFile = ExportValidationJsonSupport.readStringMember(item.get(memberName).getAsJsonObject(), "atlasFile");
        return atlasFile != null && !atlasFile.isEmpty();
    }

    private static boolean hasDrawableAtlasForItem(Set<String> drawableAtlasItemIds, String itemId) {
        if (drawableAtlasItemIds.contains(itemId)) {
            return true;
        }
        for (String alias : getItemIdAliases(itemId)) {
            if (drawableAtlasItemIds.contains(alias)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> getItemIdAliases(String itemId) {
        List<String> aliases = new ArrayList<String>();
        if (itemId == null) {
            return aliases;
        }
        String normalized = itemId.trim();
        if (normalized.isEmpty()) {
            return aliases;
        }
        String[] parts = normalized.split("~");
        if (parts.length >= 4 && "i".equals(parts[0])) {
            String compact = parts[0] + "~" + parts[1] + "~" + parts[2] + "~" + parts[3];
            String metaZero = parts[0] + "~" + parts[1] + "~" + parts[2] + "~0";
            if (!compact.equals(normalized)) {
                aliases.add(compact);
            }
            if (!metaZero.equals(normalized) && !aliases.contains(metaZero)) {
                aliases.add(metaZero);
            }
        }
        return aliases;
    }
}
