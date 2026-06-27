package com.github.dcysteine.nesql.exporter.local;

import com.github.dcysteine.nesql.exporter.canonical.CanonicalRenderAsset;
import com.github.dcysteine.nesql.exporter.main.ExportContext;
import com.github.dcysteine.nesql.exporter.main.ExportStage;
import com.github.dcysteine.nesql.exporter.main.Logger;
import com.github.dcysteine.nesql.exporter.semantic.SemanticRulePack;
import jakarta.persistence.EntityManager;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.common.ForgeVersion;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Writes the first raw-export sidecar without replacing the current canonical
 * export structure.
 *
 * <p>This is intentionally conservative: the sidecar records counts, source
 * profile, selected stages, and references to the existing canonical outputs.
 * Later rebuild phases can replace each placeholder JSONL file with true raw
 * fact streams while NeoNEI continues to consume the current export layout.</p>
 */
public final class RawExportSidecarWriter {
    private static final String OUTPUT_DIRECTORY = "raw-export";
    private static final String SCHEMA_VERSION = "nesqlpp/raw-export/alpha1";

    private final EntityManager entityManager;
    private final File repositoryDirectory;
    private final ExportContext exportContext;
    private final List<CanonicalRenderAsset> renderAssets;

    public RawExportSidecarWriter(
            EntityManager entityManager,
            File repositoryDirectory,
            ExportContext exportContext,
            List<CanonicalRenderAsset> renderAssets) {
        this.entityManager = entityManager;
        this.repositoryDirectory = repositoryDirectory;
        this.exportContext = exportContext;
        this.renderAssets = renderAssets == null
                ? java.util.Collections.<CanonicalRenderAsset>emptyList()
                : renderAssets;
    }

    public void export() throws IOException {
        File rawDir = new File(repositoryDirectory, OUTPUT_DIRECTORY);
        ensureDirectory(rawDir);
        purgeLegacyRawExportOutputs(rawDir);

        RawFactCounts factCounts = writeRawFactStreams(rawDir);
        SemanticRulePack.RuntimeMetadata semanticRuleRuntime = buildSemanticRuleRuntimeMetadata();
        SemanticRulePack.writeBundledCopy(new File(rawDir, "facts/semantic/rule-pack.json"), semanticRuleRuntime);
        SemanticItemIdentityDiagnosticsWriter.SemanticAuditSummary semanticAudit =
                new SemanticItemIdentityDiagnosticsWriter(entityManager, rawDir).write();

        RawExportReport report = buildReport();
        RawExportReportAssembler.apply(report, factCounts, semanticRuleRuntime, semanticAudit);
        RawExportValidationSupport.apply(report);
        String generatedAt = utcNow();
        RawExportManifest manifest = RawExportManifestBuilder.build(SCHEMA_VERSION, generatedAt, exportContext, report);

        RawExportReportWriter.write(SCHEMA_VERSION, generatedAt, rawDir, manifest, report);

        Logger.MOD.info(
                "Semantic item identity audit written: totalItems={}, taggedItems={}, classifiedTaggedItems={}, semanticItems={}, variants={}, payloads={}",
                semanticAudit.totalItems,
                semanticAudit.taggedItems,
                semanticAudit.classifiedTaggedItems,
                semanticAudit.semanticItems,
                semanticAudit.variants,
                semanticAudit.payloads);
        Logger.chatMessage(EnumChatFormatting.GREEN + "Raw-export sidecar written:");
        Logger.chatMessage(EnumChatFormatting.YELLOW + "  " + rawDir.getAbsolutePath());
    }

    public static void syncFinalReports(File repositoryDirectory) throws IOException {
        File rawDir = new File(repositoryDirectory, OUTPUT_DIRECTORY);
        ensureDirectory(rawDir);
        File rawValidationDir = new File(rawDir, "validation");
        ensureDirectory(rawValidationDir);

        copyIfPresent(
                new File(rawValidationDir, "export_stage_timings.json"),
                new File(rawDir, "export_stage_timings.json"));
        copyIfPresent(
                new File(rawValidationDir, "stage_checkpoint.json"),
                new File(rawDir, "stage_checkpoint.json"));
        copyIfPresent(
                new File(rawValidationDir, "stage_checksums.json"),
                new File(rawDir, "stage_checksums.json"));
        copyIfPresent(
                new File(rawValidationDir, "export_manifest.json"),
                new File(rawDir, "export_manifest.json"));
        copyIfPresent(
                new File(rawValidationDir, "export-health-report.json"),
                new File(rawDir, "validation_report.json"));
    }

    private SemanticRulePack.RuntimeMetadata buildSemanticRuleRuntimeMetadata() {
        SemanticRulePack.RuntimeMetadata metadata = new SemanticRulePack.RuntimeMetadata();
        metadata.repositoryName = exportContext.paths.repositoryName;
        metadata.exportProfile = exportContext.profile.profileId;
        metadata.exportSelection = exportContext.selection.describe();
        metadata.javaVersion = System.getProperty("java.version", "");
        metadata.minecraftVersion = safeMinecraftVersion();
        metadata.forgeVersion = safeForgeVersion();
        for (String modId : semanticFingerprintModIds()) {
            String version = safeModVersion(modId);
            if (version != null && !version.trim().isEmpty()) {
                metadata.modVersions.put(modId, version);
            }
        }
        metadata.gtnhFingerprint = semanticFingerprint(metadata.modVersions);
        return metadata;
    }

    private static List<String> semanticFingerprintModIds() {
        ArrayList<String> ids = new ArrayList<String>();
        Collections.addAll(ids,
                "gregtech",
                "NotEnoughItems",
                "angelica",
                "dreamcraft",
                "Thaumcraft",
                "appliedenergistics2",
                "Avaritia",
                "EnderIO",
                "BuildCraft|Core",
                "Forestry",
                "TConstruct",
                "ExtraUtilities",
                "OpenBlocks",
                "GalacticraftCore");
        return ids;
    }

    private static String safeMinecraftVersion() {
        try {
            ModContainer minecraft = Loader.instance().getMinecraftModContainer();
            return minecraft == null ? "" : minecraft.getVersion();
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static String safeForgeVersion() {
        try {
            return ForgeVersion.getVersion();
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static String safeModVersion(String modId) {
        try {
            ModContainer container = Loader.instance().getIndexedModList().get(modId);
            if (container == null) {
                return "";
            }
            String version = container.getVersion();
            return version == null ? "" : version;
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static String semanticFingerprint(Map<String, String> modVersions) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            if (modVersions != null) {
                for (Map.Entry<String, String> entry : modVersions.entrySet()) {
                    String line = entry.getKey() + "=" + entry.getValue() + "\n";
                    digest.update(line.getBytes(StandardCharsets.UTF_8));
                }
            }
            byte[] bytes = digest.digest();
            StringBuilder builder = new StringBuilder();
            for (int index = 0; index < Math.min(12, bytes.length); index++) {
                builder.append(String.format("%02x", bytes[index] & 0xff));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ignored) {
            return "";
        }
    }

    private RawExportReport buildReport() {
        RawExportReport report = new RawExportReport();
        report.schemaVersion = SCHEMA_VERSION + "/report";
        report.generatedAt = utcNow();
        report.profile = exportContext.profile.profileId;
        report.selection = exportContext.selection.describe();

        RawExportCounts counts = new RawExportCounts();
        counts.items = countQuery("SELECT COUNT(i) FROM Item i");
        counts.fluids = countQuery("SELECT COUNT(f) FROM Fluid f");
        counts.recipes = countQuery("SELECT COUNT(r) FROM Recipe r");
        counts.recipeTypes = countQuery("SELECT COUNT(rt) FROM RecipeType rt");
        counts.renderAssets = renderAssets.size();
        counts.itemModFiles = countFiles(new File(repositoryDirectory, "items"), "items.json.gz");
        counts.recipeModFiles = countFiles(new File(repositoryDirectory, "recipes"), "recipes.json.gz");
        counts.canonicalFiles = 0L;
        report.counts = counts;

        report.validation.missingTextureCount = 0;
        report.validation.missingAnimationMetadataCount = 0;
        report.validation.missingGroupOrOrderCount = 0;
        report.validation.failedStages = new ArrayList<String>();
        report.validation.gates = new ArrayList<RawValidationGate>();
        report.validation.status = "not-yet-enforced";
        report.validation.readinessStatus = "blocked";
        return report;
    }

    private RawFactCounts writeRawFactStreams(File rawDir) throws IOException {
        RawFactCounts counts = new RawFactCounts();
        RawRepositoryFactStreamResult repository = streamRepositoryFacts(rawDir);
        counts.items = repository.items;
        counts.fluids = repository.fluids;
        counts.recipes = repository.recipes;

        RawNeiFactCounts nei = new RawExportNeiFactWriter(repositoryDirectory, rawDir, SCHEMA_VERSION).write();
        counts.groups = nei.groups;
        counts.neiOrderEntries = nei.neiOrderEntries;
        counts.neiBrowserContract = nei.neiBrowserContract;
        counts.neiRuntimePanelItems = nei.neiRuntimePanelItems;
        counts.neiExportOnlyItems = nei.neiExportOnlyItems;
        counts.neiBrowserItems = nei.neiBrowserItems;
        counts.neiDefaultEntries = nei.neiDefaultEntries;
        counts.neiFallbackGroups = nei.neiFallbackGroups;
        counts.neiNativeGroups = nei.neiNativeGroups;
        counts.neiSyntheticGroups = nei.neiSyntheticGroups;
        counts.neiGuidFilterRules = nei.neiGuidFilterRules;
        counts.neiHiddenItemRules = nei.neiHiddenItemRules;
        counts.neiHiddenItems = nei.neiHiddenItems;
        counts.neiRepresentativeMismatches = nei.neiRepresentativeMismatches;
        counts.neiHandlers = nei.neiHandlers;
        counts.neiHandlerLayouts = nei.neiHandlerLayouts;
        counts.uiFamilyCensusHandlers = nei.uiFamilyCensusHandlers;
        counts.uiFamilyCensusFamilies = nei.uiFamilyCensusFamilies;
        counts.uiTemplateCatalogHandlers = nei.uiTemplateCatalogHandlers;
        counts.uiTemplateCatalogTemplates = nei.uiTemplateCatalogTemplates;
        counts.uiTemplateCatalogFamilies = nei.uiTemplateCatalogFamilies;

        RawRenderAssetCatalogCounts renderAssetCatalog =
                new RawExportRenderAssetCatalogWriter(repositoryDirectory, rawDir, renderAssets).write();
        counts.textures = renderAssetCatalog.textures;
        counts.animations = renderAssetCatalog.animations;
        counts.browserAtlasAssets = renderAssetCatalog.browserAtlasAssets;

        RawExportEmptyJsonlWriter.write(new File(rawDir, "models/multiblocks/index.jsonl.gz"));
        counts.entities = new RawExportEntityModelWriter(repositoryDirectory, rawDir, SCHEMA_VERSION).write();
        AngelicaRenderFactsWriter.Counts renderCounts =
                new AngelicaRenderFactsWriter(entityManager, rawDir, renderAssets).write();
        counts.renderBackendFacts = renderCounts.backendFacts;
        counts.renderBackendAngelica = "angelica".equals(renderCounts.backend) ? 1L : 0L;
        counts.renderTextureSprites = renderCounts.textureSprites;
        counts.renderTextureSpritesMissingTiming = renderCounts.textureSpritesMissingTiming;
        counts.renderItemRenderers = renderCounts.itemRenderers;
        counts.renderShaderItems = renderCounts.shaderItems;
        counts.renderShaderItemsRequiringCapture = renderCounts.shaderItemsRequiringCapture;
        counts.renderShaderItemsMissingCapture = renderCounts.shaderItemsMissingCapture;
        counts.renderShaderItemsMissingCaptureSamples = new ArrayList<String>(renderCounts.shaderItemsMissingCaptureSamples);
        counts.renderUnknownSpecialRenderers = renderCounts.unknownSpecialRenderers;
        counts.renderFramebufferCaptures = renderCounts.framebufferCaptures;
        counts.renderFramebufferCapturesWithoutFrames = renderCounts.framebufferCapturesWithoutFrames;
        counts.renderFramebufferCapturesWithoutFramesSamples = new ArrayList<String>(renderCounts.framebufferCapturesWithoutFramesSamples);
        return counts;
    }

    private long countQuery(String query) {
        if (entityManager == null) {
            return -1L;
        }
        try {
            Object value = entityManager.createQuery(query).getSingleResult();
            if (value instanceof Number) {
                return ((Number) value).longValue();
            }
        } catch (Exception e) {
            Logger.MOD.warn("Failed to calculate raw-export count for query: " + query, e);
        }
        return -1L;
    }

    private static RawExportFileRef fileRef(String logicalName, String path, String kind) {
        RawExportFileRef ref = new RawExportFileRef();
        ref.logicalName = logicalName;
        ref.path = path;
        ref.kind = kind;
        return ref;
    }

    private RawRepositoryFactStreamResult streamRepositoryFacts(File rawDir) throws IOException {
        return new RawExportRepositoryFactStreamer(entityManager, rawDir, SCHEMA_VERSION).write();
    }

    private static long countFiles(File root, String requiredName) {
        if (root == null || !root.exists()) {
            return 0L;
        }
        if (root.isFile()) {
            return requiredName == null || requiredName.equals(root.getName()) ? 1L : 0L;
        }
        long count = 0L;
        File[] children = root.listFiles();
        if (children == null) {
            return 0L;
        }
        for (File child : children) {
            count += countFiles(child, requiredName);
        }
        return count;
    }

    private static void ensureDirectory(File directory) throws IOException {
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("Failed to create directory: " + directory.getAbsolutePath());
        }
    }

    private static void copyIfPresent(File source, File target) throws IOException {
        if (source == null || !source.exists() || !source.isFile()) {
            return;
        }
        File parent = target.getParentFile();
        if (parent != null) {
            ensureDirectory(parent);
        }
        byte[] buffer = new byte[1024 * 1024];
        try (FileInputStream in = new FileInputStream(source);
             FileOutputStream out = new FileOutputStream(target)) {
            int read;
            while ((read = in.read(buffer)) >= 0) {
                if (read > 0) {
                    out.write(buffer, 0, read);
                }
            }
        }
    }

    private static void purgeLegacyRawExportOutputs(File rawDir) throws IOException {
        deleteIfExists(new File(rawDir, "recipes.jsonl"));
        deleteIfExists(new File(rawDir, "items.jsonl"));
        deleteIfExists(new File(rawDir, "fluids.jsonl"));
        deleteIfExists(new File(rawDir, "entities.jsonl"));
        deleteIfExists(new File(rawDir, "facts/items.jsonl"));
        deleteIfExists(new File(rawDir, "facts/fluids.jsonl"));
        deleteIfExists(new File(rawDir, "facts/recipes/all.jsonl"));
        for (String domainId : RawExportRepositoryFactStreamer.specialDomainIds()) {
            deleteIfExists(new File(rawDir, "special/" + domainId + "/recipes.jsonl"));
        }
    }

    private static void deleteIfExists(File file) throws IOException {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteIfExists(child);
                }
            }
        }
        if (!file.delete() && file.exists()) {
            throw new IOException("Failed to delete legacy raw-export output: " + file.getAbsolutePath());
        }
    }

    private static String utcNow() {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(new Date());
    }








}
