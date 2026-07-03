package com.github.dcysteine.nesql.exporter.main;

import com.github.dcysteine.nesql.exporter.local.RawExportFileCatalog;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

/** Owns browser atlas residency and layout coverage evidence collection. */
final class ExportValidationBrowserAtlasProbe {
    private ExportValidationBrowserAtlasProbe() {}

    static void inspect(File rawDir, ExportValidationReport report) {
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
            report.browserAtlasItems = ExportValidationJsonSupport.readIntMember(
                    atlasObject,
                    ExportValidationEvidenceCatalog.BrowserAtlas.ITEM_COUNT);
            report.browserAtlasAnimatedItems = ExportValidationJsonSupport.readIntMember(
                    atlasObject,
                    ExportValidationEvidenceCatalog.BrowserAtlas.ANIMATED_ITEM_COUNT);
            report.browserAtlasMissingAtlasCount = ExportValidationJsonSupport.readIntMember(
                    atlasObject,
                    ExportValidationEvidenceCatalog.BrowserAtlas.MISSING_ATLAS_COUNT);

            Set<String> drawableAtlasItemIds = new HashSet<String>();
            if (atlasObject.has(ExportValidationEvidenceCatalog.OBJECT_ITEMS)
                    && atlasObject.get(ExportValidationEvidenceCatalog.OBJECT_ITEMS).isJsonArray()) {
                if (report.browserAtlasItems == 0) {
                    report.browserAtlasItems = atlasObject
                            .get(ExportValidationEvidenceCatalog.OBJECT_ITEMS)
                            .getAsJsonArray()
                            .size();
                }
                for (JsonElement element : atlasObject.get(ExportValidationEvidenceCatalog.OBJECT_ITEMS).getAsJsonArray()) {
                    if (!element.isJsonObject()) {
                        continue;
                    }
                    JsonObject item = element.getAsJsonObject();
                    String itemId = ExportValidationJsonSupport.readStringMember(
                            item,
                            ExportValidationEvidenceCatalog.MEMBER_ITEM_ID);
                    if (itemId == null) {
                        continue;
                    }
                    if (hasDrawableAtlas(item)) {
                        drawableAtlasItemIds.add(itemId);
                    } else {
                        ExportValidationJsonSupport.addSample(report.browserAtlasLayoutMissingSamples, itemId);
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

    private static boolean hasDrawableAtlas(JsonObject item) {
        return hasAtlasFile(item, ExportValidationEvidenceCatalog.OBJECT_STATIC_ATLAS)
                || hasAtlasFile(item, ExportValidationEvidenceCatalog.OBJECT_ANIMATED_ATLAS);
    }

    private static boolean hasAtlasFile(JsonObject item, String memberName) {
        if (!item.has(memberName) || !item.get(memberName).isJsonObject()) {
            return false;
        }
        String atlasFile = ExportValidationJsonSupport.readStringMember(
                item.get(memberName).getAsJsonObject(),
                ExportValidationEvidenceCatalog.MEMBER_ATLAS_FILE);
        return atlasFile != null && !atlasFile.isEmpty();
    }
}
