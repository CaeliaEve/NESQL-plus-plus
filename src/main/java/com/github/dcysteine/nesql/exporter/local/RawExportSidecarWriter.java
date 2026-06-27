package com.github.dcysteine.nesql.exporter.local;

import com.github.dcysteine.nesql.exporter.canonical.CanonicalRenderAsset;
import com.github.dcysteine.nesql.exporter.main.ExportContext;
import com.github.dcysteine.nesql.exporter.main.Logger;
import com.github.dcysteine.nesql.exporter.semantic.SemanticRulePack;
import jakarta.persistence.EntityManager;
import net.minecraft.util.EnumChatFormatting;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

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
        RawExportSidecarFileOps.ensureDirectory(rawDir);
        RawExportSidecarFileOps.purgeLegacyRawExportOutputs(rawDir);

        RawFactCounts factCounts = writeRawFactStreams(rawDir);
        SemanticRulePack.RuntimeMetadata semanticRuleRuntime = new RawExportSemanticRuntimeBuilder(exportContext).build();
        SemanticRulePack.writeBundledCopy(new File(rawDir, "facts/semantic/rule-pack.json"), semanticRuleRuntime);
        SemanticItemIdentityDiagnosticsWriter.SemanticAuditSummary semanticAudit =
                new SemanticItemIdentityDiagnosticsWriter(entityManager, rawDir).write();

        RawExportReport report = new RawExportReportFactory(
                entityManager, repositoryDirectory, exportContext, renderAssets, SCHEMA_VERSION).build();
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
        RawExportSidecarFileOps.ensureDirectory(rawDir);
        File rawValidationDir = new File(rawDir, "validation");
        RawExportSidecarFileOps.ensureDirectory(rawValidationDir);

        RawExportSidecarFileOps.copyIfPresent(
                new File(rawValidationDir, "export_stage_timings.json"),
                new File(rawDir, "export_stage_timings.json"));
        RawExportSidecarFileOps.copyIfPresent(
                new File(rawValidationDir, "stage_checkpoint.json"),
                new File(rawDir, "stage_checkpoint.json"));
        RawExportSidecarFileOps.copyIfPresent(
                new File(rawValidationDir, "stage_checksums.json"),
                new File(rawDir, "stage_checksums.json"));
        RawExportSidecarFileOps.copyIfPresent(
                new File(rawValidationDir, "export_manifest.json"),
                new File(rawDir, "export_manifest.json"));
        RawExportSidecarFileOps.copyIfPresent(
                new File(rawValidationDir, "export-health-report.json"),
                new File(rawDir, "validation_report.json"));
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

    private RawRepositoryFactStreamResult streamRepositoryFacts(File rawDir) throws IOException {
        return new RawExportRepositoryFactStreamer(entityManager, rawDir, SCHEMA_VERSION).write();
    }

    private static String utcNow() {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(new Date());
    }








}
