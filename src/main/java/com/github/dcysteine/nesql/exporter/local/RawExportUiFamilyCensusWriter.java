package com.github.dcysteine.nesql.exporter.local;

import com.github.dcysteine.nesql.exporter.plugin.nei.metadata.NeiHandlerMetadataEntry;
import com.github.dcysteine.nesql.exporter.plugin.nei.metadata.NeiHandlerMetadataRepository;
import com.github.dcysteine.nesql.exporter.plugin.nei.metadata.NeiUiFamilyClassifier;
import com.github.dcysteine.nesql.exporter.plugin.nei.metadata.NeiUiTemplateLayoutSpecs;
import com.github.dcysteine.nesql.exporter.main.Logger;
import com.github.dcysteine.nesql.exporter.nativeui.NativeUiExportAbi;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.util.EnumChatFormatting;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Writes the first NEI UI family census for the raw-export sidecar.
 */
public final class RawExportUiFamilyCensusWriter {
    private static final String OUTPUT_DIRECTORY = "raw-export";
    private static final String OUTPUT_FILE = NativeUiExportAbi.UI_FAMILY_CENSUS_FILE;
    private static final String SCHEMA_VERSION = NativeUiExportAbi.UI_FAMILY_CENSUS_SCHEMA;

    private final File repositoryDirectory;

    public RawExportUiFamilyCensusWriter(File repositoryDirectory) {
        this.repositoryDirectory = repositoryDirectory;
    }

    public void export() throws IOException {
        File rawDir = new File(repositoryDirectory, OUTPUT_DIRECTORY);
        RawExportSidecarFileOps.ensureDirectory(rawDir);
        File validationDir = new File(rawDir, "validation");
        RawExportSidecarFileOps.ensureDirectory(validationDir);

        UiFamilyCensusReport report = buildReport();

        Gson gson = new GsonBuilder().setPrettyPrinting().serializeNulls().create();
        File outputFile = new File(rawDir, OUTPUT_FILE);
        RawExportSidecarFileOps.writeJson(gson, outputFile, report);

        Logger.chatMessage(EnumChatFormatting.GREEN + "NEI UI family census written:");
        Logger.chatMessage(EnumChatFormatting.YELLOW + "  " + outputFile.getAbsolutePath());
        Logger.chatMessage(EnumChatFormatting.GRAY
                + "  handlers=" + report.summary.handlerCount
                + ", families=" + report.summary.familyCount
                + ", layoutKinds=" + report.summary.layoutKindCount
                + ", mods=" + report.summary.modCount);
    }

    private UiFamilyCensusReport buildReport() {
        List<NeiHandlerMetadataEntry> entries = NeiHandlerMetadataRepository.getInstance().getEntries();
        Map<String, UiFamilyBucket> families = new LinkedHashMap<String, UiFamilyBucket>();
        Set<String> modIds = new LinkedHashSet<String>();
        Set<String> layoutKinds = new LinkedHashSet<String>();

        for (NeiHandlerMetadataEntry entry : entries) {
            if (entry == null) {
                continue;
            }
            String handler = trimToEmpty(entry.getHandler());
            String modId = trimToEmpty(entry.getModId());
            String modName = trimToEmpty(entry.getModName());
            String itemName = trimToEmpty(entry.getItemName());
            String family = NeiUiFamilyClassifier.classifyHandlerFamily(handler, itemName, modId);
            String layoutKind = NeiUiFamilyClassifier.inferLayoutKind(handler, itemName, family);
            int width = entry.getHandlerWidthInt() == null ? 166 : entry.getHandlerWidthInt().intValue();
            int height = entry.getHandlerHeightInt() == null ? 65 : entry.getHandlerHeightInt().intValue();
            width = NeiUiTemplateLayoutSpecs.boundedSurfaceWidth(layoutKind, width);
            height = NeiUiTemplateLayoutSpecs.boundedSurfaceHeight(layoutKind, height);
            int yShift = entry.getYShiftInt() == null ? 0 : entry.getYShiftInt().intValue();
            int maxRecipesPerPage = entry.getMaxRecipesPerPageInt() == null
                    ? 1
                    : entry.getMaxRecipesPerPageInt().intValue();
            String imageResource = trimToEmpty(entry.getImageResource());
            UiNativeBackground nativeBackground = buildNativeBackground(entry, family, layoutKind, width, height, yShift);

            String familyKey = buildFamilyKey(family, layoutKind, width, height, yShift, maxRecipesPerPage, imageResource);
            UiFamilyBucket bucket = families.get(familyKey);
            if (bucket == null) {
                bucket = new UiFamilyBucket();
                bucket.familyKey = familyKey;
                bucket.canonicalMachineFamily = family;
                bucket.layoutKind = layoutKind;
                bucket.width = width;
                bucket.height = height;
                bucket.yShift = yShift;
                bucket.maxRecipesPerPage = maxRecipesPerPage;
                bucket.imageResource = imageResource;
                bucket.nativeBackground = nativeBackground;
                families.put(familyKey, bucket);
            }

            UiFamilyMember member = new UiFamilyMember();
            member.handler = handler;
            member.modId = modId;
            member.modName = modName;
            member.itemName = itemName;
            member.itemNotes = trimToEmpty(entry.getItemNotes());
            member.modRequired = entry.isModRequired();
            member.excludedModId = trimToEmpty(entry.getExcludedModId());
            member.imageResource = imageResource;
            member.handlerWidth = width;
            member.handlerHeight = height;
            member.yShift = yShift;
            member.maxRecipesPerPage = maxRecipesPerPage;
            member.nativeBackground = nativeBackground;
            bucket.members.add(member);

            if (!modId.isEmpty()) {
                modIds.add(modId);
            }
            layoutKinds.add(layoutKind);
        }

        List<UiFamilyBucket> sortedFamilies = new ArrayList<UiFamilyBucket>(families.values());
        Collections.sort(sortedFamilies, new Comparator<UiFamilyBucket>() {
            @Override
            public int compare(UiFamilyBucket left, UiFamilyBucket right) {
                int bySize = Integer.compare(right.members.size(), left.members.size());
                if (bySize != 0) {
                    return bySize;
                }
                return left.familyKey.compareTo(right.familyKey);
            }
        });

        UiFamilyCensusReport report = new UiFamilyCensusReport();
        report.schemaVersion = SCHEMA_VERSION;
        report.generatedAt = Long.toString(System.currentTimeMillis());
        report.source = new UiFamilyCensusSource();
        report.source.kind = "bundled-nei-handler-metadata";
        report.source.resource = "nesql/nei/handler-metadata.json";
        report.source.entryCount = entries.size();
        report.source.classifier = "NeiUiFamilyClassifier";
        report.summary = new UiFamilyCensusSummary();
        report.summary.handlerCount = entries.size();
        report.summary.familyCount = sortedFamilies.size();
        report.summary.modCount = modIds.size();
        report.summary.layoutKindCount = layoutKinds.size();
        report.summary.nativeFamilyCount = countFamilies(sortedFamilies, "native-nei");
        report.summary.craftingFamilyCount = countFamilies(sortedFamilies, "crafting-table");
        report.summary.machineFamilyCount = countFamilies(sortedFamilies, "gregtech-machine");
        report.families = sortedFamilies;
        return report;
    }

    private static int countFamilies(List<UiFamilyBucket> families, String familyName) {
        int count = 0;
        for (UiFamilyBucket family : families) {
            if (family != null && familyName.equals(family.canonicalMachineFamily)) {
                count++;
            }
        }
        return count;
    }

    private static String buildFamilyKey(
            String family,
            String layoutKind,
            int width,
            int height,
            int yShift,
            int maxRecipesPerPage,
            String imageResource) {
        return normalizeKeyPart(family)
                + "|"
                + normalizeKeyPart(layoutKind)
                + "|"
                + width
                + "x"
                + height
                + "@"
                + yShift
                + "#"
                + maxRecipesPerPage
                + "|"
                + normalizeKeyPart(imageResource);
    }

    private static UiNativeBackground buildNativeBackground(
            NeiHandlerMetadataEntry entry,
            String family,
            String layoutKind,
            int width,
            int height,
            int yShift) {
        UiNativeBackground background = new UiNativeBackground();
        background.schemaVersion = NativeUiExportAbi.schema(SCHEMA_VERSION, "native-ui-background");
        background.width = width;
        background.height = height;
        background.yShift = yShift;
        background.layoutKind = layoutKind;
        background.canonicalMachineFamily = family;
        background.coordinateSpace = NativeUiExportAbi.COORDINATE_SPACE;
        background.scaleMode = NativeUiExportAbi.SCALE_MODE;
        background.anchor = NativeUiExportAbi.ANCHOR;
        if ("gregtech-machine".equals(family)) {
            background.status = NativeUiExportAbi.BACKGROUND_STATUS_CAPTURED;
            background.kind = NativeUiExportAbi.BACKGROUND_KIND_GT_MODULAR_UI;
            background.assetRef = NativeUiExportAbi.GT_NEI_BACKGROUND_ASSET_REF;
            background.resource = NativeUiExportAbi.GT_NEI_BACKGROUND_RESOURCE;
            background.source = "GTNEIDefaultHandler.drawUI(ModularWindow.getBackground)";
            background.drawable = "GTUITextures.BACKGROUND_NEI_SINGLE_RECIPE";
            applyNineSliceBackgroundGeometry(background, 3, 3, Math.max(0, width - 6), Math.max(0, height - yShift - 6));
            background.captureRequired = false;
            return background;
        }
        background.status = NativeUiExportAbi.BACKGROUND_STATUS_SEMANTIC;
        background.kind = NativeUiExportAbi.BACKGROUND_KIND_CANONICAL_NEI_TEMPLATE;
        background.source = "NeiUiTemplateLayoutSpecs.defaultLayoutSlots";
        background.drawable = "Native NEI canonical semantic panel";
        applyNineSliceBackgroundGeometry(background, 0, 0, width, height);
        background.captureRequired = false;
        return background;
    }

    private static void applyNineSliceBackgroundGeometry(
            UiNativeBackground background,
            int offsetX,
            int offsetY,
            int targetWidth,
            int targetHeight) {
        background.scaling = NativeUiExportAbi.BACKGROUND_SCALING_NINE_SLICE;
        background.texture = new UiTexture();
        background.texture.width = 64;
        background.texture.height = 64;
        background.texture.borderU = 2;
        background.texture.borderV = 2;
        background.recipeBackgroundOffset = new UiPoint();
        background.recipeBackgroundOffset.x = offsetX;
        background.recipeBackgroundOffset.y = offsetY;
        background.recipeBackgroundSize = new UiSize();
        background.recipeBackgroundSize.width = targetWidth;
        background.recipeBackgroundSize.height = targetHeight;
    }

    private static String normalizeKeyPart(String value) {
        String trimmed = trimToEmpty(value);
        if (trimmed.isEmpty()) {
            return "unknown";
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }

    private static String trimToEmpty(String value) {
        if (value == null) {
            return "";
        }
        return value.trim();
    }


    static final class UiFamilyCensusReport {
        String schemaVersion;
        String generatedAt;
        UiFamilyCensusSource source;
        UiFamilyCensusSummary summary;
        List<UiFamilyBucket> families = new ArrayList<UiFamilyBucket>();
    }

    static final class UiFamilyCensusSource {
        String kind;
        String resource;
        long entryCount;
        String classifier;
    }

    static final class UiFamilyCensusSummary {
        long handlerCount;
        long familyCount;
        long modCount;
        long layoutKindCount;
        long nativeFamilyCount;
        long craftingFamilyCount;
        long machineFamilyCount;
    }

    static final class UiFamilyBucket {
        String familyKey;
        String canonicalMachineFamily;
        String layoutKind;
        int width;
        int height;
        int yShift;
        int maxRecipesPerPage;
        String imageResource;
        UiNativeBackground nativeBackground;
        List<UiFamilyMember> members = new ArrayList<UiFamilyMember>();
    }

    static final class UiFamilyMember {
        String handler;
        String modId;
        String modName;
        String itemName;
        String itemNotes;
        boolean modRequired;
        String excludedModId;
        String imageResource;
        int handlerWidth;
        int handlerHeight;
        int yShift;
        int maxRecipesPerPage;
        UiNativeBackground nativeBackground;
    }

    static final class UiNativeBackground {
        String schemaVersion;
        String status;
        String kind;
        String coordinateSpace;
        String scaleMode;
        String anchor;
        String assetRef;
        String resource;
        String source;
        String drawable;
        String scaling;
        String canonicalMachineFamily;
        String layoutKind;
        int width;
        int height;
        int yShift;
        UiTexture texture;
        UiRect region;
        UiPoint recipeBackgroundOffset;
        UiSize recipeBackgroundSize;
        boolean captureRequired;
    }

    static final class UiTexture {
        int width;
        int height;
        int borderU;
        int borderV;
    }

    static final class UiRect {
        int x;
        int y;
        int width;
        int height;
    }

    static final class UiPoint {
        int x;
        int y;
    }

    static final class UiSize {
        int width;
        int height;
    }
}
