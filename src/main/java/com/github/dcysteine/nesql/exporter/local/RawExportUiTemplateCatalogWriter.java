package com.github.dcysteine.nesql.exporter.local;

import com.github.dcysteine.nesql.exporter.main.Logger;
import com.github.dcysteine.nesql.exporter.nativeui.NativeUiExportAbi;
import com.github.dcysteine.nesql.exporter.plugin.nei.metadata.NeiUiTemplateLayoutSpecs;
import com.github.dcysteine.nesql.exporter.plugin.nei.metadata.NeiUiTemplateLayoutSpecs.UiTemplateSlot;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.util.EnumChatFormatting;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;

/**
 * Writes the first deterministic NEI UI template catalog derived from the census report.
 */
public final class RawExportUiTemplateCatalogWriter {
    private static final String OUTPUT_DIRECTORY = "raw-export";
    private static final String OUTPUT_FILE = NativeUiExportAbi.UI_TEMPLATE_CATALOG_FILE;
    private static final String SCHEMA_VERSION = NativeUiExportAbi.UI_TEMPLATE_CATALOG_SCHEMA;
    private static final String CENSUS_FILE = NativeUiExportAbi.UI_FAMILY_CENSUS_FILE;

    private final File repositoryDirectory;

    public RawExportUiTemplateCatalogWriter(File repositoryDirectory) {
        this.repositoryDirectory = repositoryDirectory;
    }

    public void export() throws IOException {
        File rawDir = new File(repositoryDirectory, OUTPUT_DIRECTORY);
        ensureDirectory(rawDir);
        File validationDir = new File(rawDir, "validation");
        ensureDirectory(validationDir);

        UiTemplateCatalogReport report = buildReport(rawDir);

        Gson gson = new GsonBuilder().setPrettyPrinting().serializeNulls().create();
        File outputFile = new File(rawDir, OUTPUT_FILE);
        try (FileOutputStream fos = new FileOutputStream(outputFile);
             OutputStreamWriter writer = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
            gson.toJson(report, writer);
        }

        Logger.chatMessage(EnumChatFormatting.GREEN + "NEI UI template catalog written:");
        Logger.chatMessage(EnumChatFormatting.YELLOW + "  " + outputFile.getAbsolutePath());
        Logger.chatMessage(EnumChatFormatting.GRAY
                + "  handlers=" + report.summary.handlerCount
                + ", templates=" + report.summary.templateCount
                + ", families=" + report.summary.familyCount
                + ", layoutKinds=" + report.summary.layoutKindCount
                + ", slots=" + report.summary.slotCount);
    }

    private UiTemplateCatalogReport buildReport(File rawDir) throws IOException {
        File censusFile = new File(rawDir, CENSUS_FILE);
        if (!censusFile.exists()) {
            throw new IOException("Missing UI family census file: " + censusFile.getAbsolutePath());
        }

        RawExportUiFamilyCensusWriter.UiFamilyCensusReport census;
        Gson gson = new Gson();
        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(censusFile), StandardCharsets.UTF_8)) {
            census = gson.fromJson(reader, RawExportUiFamilyCensusWriter.UiFamilyCensusReport.class);
        } catch (Exception e) {
            throw new IOException("Failed to read UI family census: " + censusFile.getAbsolutePath(), e);
        }
        if (census == null) {
            throw new IOException("Empty UI family census file: " + censusFile.getAbsolutePath());
        }

        List<UiTemplateBucket> templates = new ArrayList<UiTemplateBucket>();
        Set<String> layoutKinds = new LinkedHashSet<String>();
        long handlerCount = 0L;
        long slotCount = 0L;
        List<RawExportUiFamilyCensusWriter.UiFamilyBucket> families = census.families == null
                ? Collections.<RawExportUiFamilyCensusWriter.UiFamilyBucket>emptyList()
                : census.families;
        for (RawExportUiFamilyCensusWriter.UiFamilyBucket family : families) {
            UiTemplateBucket template = buildTemplateBucket(family);
            templates.add(template);
            layoutKinds.add(template.layoutKind);
            handlerCount += template.handlerCount;
            slotCount += template.slotCount;
        }

        Collections.sort(templates, (left, right) -> left.templateKey.compareTo(right.templateKey));

        UiTemplateCatalogReport report = new UiTemplateCatalogReport();
        report.schemaVersion = SCHEMA_VERSION;
        report.generatedAt = Long.toString(System.currentTimeMillis());
        report.source = new UiTemplateCatalogSource();
        report.source.kind = "ui-family-census";
        report.source.resource = CENSUS_FILE;
        report.source.censusSchemaVersion = census.schemaVersion;
        report.source.censusFamilyCount = census.summary.familyCount;
        report.source.censusHandlerCount = census.summary.handlerCount;
        report.source.layoutSpecProvider = "NeiUiTemplateLayoutSpecs";
        report.summary = new UiTemplateCatalogSummary();
        report.summary.handlerCount = handlerCount;
        report.summary.templateCount = templates.size();
        report.summary.familyCount = templates.size();
        report.summary.layoutKindCount = layoutKinds.size();
        report.summary.slotCount = slotCount;
        report.summary.overlayCount = 0L;
        report.templates = templates;
        return report;
    }

    private UiTemplateBucket buildTemplateBucket(RawExportUiFamilyCensusWriter.UiFamilyBucket family) {
        UiTemplateBucket template = new UiTemplateBucket();
        template.familyKey = family.familyKey;
        template.canonicalMachineFamily = family.canonicalMachineFamily;
        template.layoutKind = family.layoutKind;
        template.coordinateSpace = NativeUiExportAbi.COORDINATE_SPACE;
        template.scaleMode = NativeUiExportAbi.SCALE_MODE;
        template.anchor = NativeUiExportAbi.ANCHOR;
        template.width = family.width;
        template.height = family.height;
        template.yShift = family.yShift;
        template.maxRecipesPerPage = family.maxRecipesPerPage;
        template.imageResource = family.imageResource;
        template.nativeBackground = family.nativeBackground;
        template.slots = NeiUiTemplateLayoutSpecs.defaultLayoutSlots(family.layoutKind);
        template.slotCount = template.slots.size();
        template.textOverlays = new ArrayList<UiTemplateTextOverlay>();
        template.hotspots = new ArrayList<UiTemplateRect>();
        template.viewports = new ArrayList<UiTemplateRect>();
        List<RawExportUiFamilyCensusWriter.UiFamilyMember> members = family.members == null
                ? Collections.<RawExportUiFamilyCensusWriter.UiFamilyMember>emptyList()
                : family.members;
        template.handlerCount = members.size();
        template.handlerIds = new ArrayList<String>();
        template.modIds = new ArrayList<String>();
        template.handlerClasses = new ArrayList<String>();
        Set<String> uniqueModIds = new LinkedHashSet<String>();
        Set<String> uniqueHandlers = new LinkedHashSet<String>();
        for (RawExportUiFamilyCensusWriter.UiFamilyMember member : members) {
            if (member == null) {
                continue;
            }
            if (!member.handler.isEmpty() && uniqueHandlers.add(member.handler)) {
                template.handlerIds.add(member.handler);
            }
            if (!member.modId.isEmpty() && uniqueModIds.add(member.modId)) {
                template.modIds.add(member.modId);
            }
            if (!member.handler.isEmpty()) {
                template.handlerClasses.add(member.handler);
            }
        }
        template.templateSignature = computeTemplateSignature(template);
        template.templateKey = "ui-template/" + template.templateSignature.substring(0, 16);
        return template;
    }

    private static String computeTemplateSignature(UiTemplateBucket template) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            StringBuilder canonical = new StringBuilder();
            canonical.append(nullToEmpty(template.familyKey)).append('\n');
            canonical.append(nullToEmpty(template.canonicalMachineFamily)).append('\n');
            canonical.append(nullToEmpty(template.layoutKind)).append('\n');
            canonical.append(nullToEmpty(template.coordinateSpace)).append(':')
                    .append(nullToEmpty(template.scaleMode)).append(':')
                    .append(nullToEmpty(template.anchor)).append('\n');
            canonical.append(template.width).append('x').append(template.height).append('@').append(template.yShift).append('\n');
            canonical.append(template.maxRecipesPerPage).append('\n');
            canonical.append(nullToEmpty(template.imageResource)).append('\n');
            if (template.nativeBackground != null) {
                canonical.append(nullToEmpty(template.nativeBackground.status)).append(':')
                        .append(nullToEmpty(template.nativeBackground.kind)).append(':')
                        .append(nullToEmpty(template.nativeBackground.assetRef)).append(':')
                        .append(nullToEmpty(template.nativeBackground.resource)).append(':')
                        .append(nullToEmpty(template.nativeBackground.drawable)).append(':')
                        .append(nullToEmpty(template.nativeBackground.scaling));
                if (template.nativeBackground.texture != null) {
                    canonical.append(':')
                            .append(template.nativeBackground.texture.width).append('x')
                            .append(template.nativeBackground.texture.height).append('@')
                            .append(template.nativeBackground.texture.borderU).append('x')
                            .append(template.nativeBackground.texture.borderV);
                }
                canonical.append('\n');
            }
            for (UiTemplateSlot slot : template.slots) {
                canonical.append(nullToEmpty(slot.role))
                        .append(':').append(slot.startIndex)
                        .append(':').append(slot.columns)
                        .append(':').append(slot.rows)
                        .append(':').append(slot.x)
                        .append(':').append(slot.y)
                        .append('\n');
            }
            byte[] bytes = digest.digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < bytes.length; i++) {
                hex.append(String.format(Locale.ROOT, "%02x", bytes[i] & 0xff));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute template signature", e);
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static void ensureDirectory(File directory) throws IOException {
        if (directory.exists()) {
            return;
        }
        if (!directory.mkdirs() && !directory.exists()) {
            throw new IOException("Failed to create directory: " + directory.getAbsolutePath());
        }
    }

    static final class UiTemplateCatalogReport {
        String schemaVersion;
        String generatedAt;
        UiTemplateCatalogSource source;
        UiTemplateCatalogSummary summary;
        List<UiTemplateBucket> templates = new ArrayList<UiTemplateBucket>();
    }

    static final class UiTemplateCatalogSource {
        String kind;
        String resource;
        String censusSchemaVersion;
        long censusFamilyCount;
        long censusHandlerCount;
        String layoutSpecProvider;
    }

    static final class UiTemplateCatalogSummary {
        long handlerCount;
        long templateCount;
        long familyCount;
        long layoutKindCount;
        long slotCount;
        long overlayCount;
    }

    static final class UiTemplateBucket {
        String templateKey;
        String templateSignature;
        String familyKey;
        String canonicalMachineFamily;
        String layoutKind;
        String coordinateSpace;
        String scaleMode;
        String anchor;
        int width;
        int height;
        int yShift;
        int maxRecipesPerPage;
        String imageResource;
        RawExportUiFamilyCensusWriter.UiNativeBackground nativeBackground;
        long handlerCount;
        long slotCount;
        List<String> handlerIds = new ArrayList<String>();
        List<String> handlerClasses = new ArrayList<String>();
        List<String> modIds = new ArrayList<String>();
        List<UiTemplateSlot> slots = new ArrayList<UiTemplateSlot>();
        List<UiTemplateTextOverlay> textOverlays = new ArrayList<UiTemplateTextOverlay>();
        List<UiTemplateRect> hotspots = new ArrayList<UiTemplateRect>();
        List<UiTemplateRect> viewports = new ArrayList<UiTemplateRect>();
    }

    static final class UiTemplateTextOverlay {
        String text;
        int x;
        int y;
        int width;
        int height;
    }

    static final class UiTemplateRect {
        String id;
        String kind;
        String role;
        String label;
        String tooltip;
        String action;
        String itemId;
        String payloadKey;
        int x;
        int y;
        int width;
        int height;
    }
}
