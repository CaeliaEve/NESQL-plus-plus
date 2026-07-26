package com.github.dcysteine.nesql.exporter.main;

import com.github.dcysteine.nesql.exporter.canonical.CanonicalRenderAsset;
import com.github.dcysteine.nesql.exporter.local.CanonicalRepositorySnapshotWriter;
import com.github.dcysteine.nesql.exporter.local.CanonicalRenderAssetManifestWriter;
import com.github.dcysteine.nesql.exporter.local.CanonicalAtlasPackWriter;
import com.github.dcysteine.nesql.exporter.local.CanonicalAnimationManifestWriter;
import com.github.dcysteine.nesql.exporter.local.CanonicalAnimatedAtlasPackWriter;
import com.github.dcysteine.nesql.exporter.local.CanonicalRenderIndexWriter;
import com.github.dcysteine.nesql.exporter.local.CanonicalAtlasRegistryWriter;
import com.github.dcysteine.nesql.exporter.local.CanonicalBrowserAtlasIndexWriter;
import com.github.dcysteine.nesql.exporter.local.CanonicalBrowserLayoutIndexWriter;
import com.github.dcysteine.nesql.exporter.local.CanonicalRenderAssetCollector;
import com.github.dcysteine.nesql.exporter.local.RawExportSidecarWriter;
import com.github.dcysteine.nesql.exporter.local.RawExportUiFamilyCensusWriter;
import com.github.dcysteine.nesql.exporter.local.RawExportUiTemplateCatalogWriter;
import com.github.dcysteine.nesql.exporter.local.ModBasedItemExporter;
import com.github.dcysteine.nesql.exporter.local.ModBasedRecipeExporter;
import net.minecraft.util.EnumChatFormatting;

import jakarta.persistence.EntityManager;
import java.io.File;
import java.util.List;

/**
 * Shared writer dispatch support for NESQL export entrypoints.
 */
public final class ExportWriterSupport {

    private ExportWriterSupport() {}

    public static void writeModBasedItems(EntityManager entityManager, File repositoryDirectory) throws Exception {
        Logger.MOD.info("============================================================");
        Logger.MOD.info("=== v1.04 Step 1/2: Starting item export ===");
        Logger.MOD.info("============================================================");
        Logger.chatMessage(EnumChatFormatting.AQUA + "=== Step 1/2: Exporting Items ===");
        Logger.chatMessage(EnumChatFormatting.YELLOW + "Structure: items/{modId}/items.json.gz");
        try {
            Logger.MOD.info("Creating ModBasedItemExporter...");
            ModBasedItemExporter itemExporter = new ModBasedItemExporter(entityManager, repositoryDirectory);
            Logger.MOD.info("ModBasedItemExporter created");
            Logger.MOD.info("Calling exportItems()...");
            itemExporter.exportItems();
            Logger.MOD.info("exportItems() completed successfully");
            Logger.chatMessage(EnumChatFormatting.GREEN + "Items exported!");
        } catch (Exception e) {
            Logger.MOD.error("Failed to export items to local JSON", e);
            Logger.chatMessage(EnumChatFormatting.RED + "Failed to export items JSON: " + e.getMessage());
            throw e;
        }
    }

    public static void writeModBasedRecipes(EntityManager entityManager, File repositoryDirectory) throws Exception {
        writeModBasedRecipes(entityManager, repositoryDirectory, java.util.Collections.emptySet());
    }

    public static void writeModBasedRecipes(
            EntityManager entityManager,
            File repositoryDirectory,
            java.util.Set<String> modFilter) throws Exception {
        Logger.MOD.info("============================================================");
        Logger.MOD.info("=== v1.04 Step 2/2: Starting recipe export ===");
        Logger.MOD.info("============================================================");
        Logger.chatMessage(EnumChatFormatting.AQUA + "=== Step 2/2: Exporting Recipes ===");
        Logger.chatMessage(EnumChatFormatting.YELLOW + "Structure: recipes/crafting/{modId}/recipes.json.gz");
        Logger.chatMessage(EnumChatFormatting.YELLOW + "Usage links are derived by NeoNEI backend");
        try {
            Logger.MOD.info("Creating ModBasedRecipeExporter...");
            ModBasedRecipeExporter recipeExporter = new ModBasedRecipeExporter(entityManager, repositoryDirectory, modFilter);
            Logger.MOD.info("ModBasedRecipeExporter created");
            Logger.MOD.info("Calling exportRecipes()...");
            recipeExporter.exportRecipes();
            Logger.MOD.info("exportRecipes() completed successfully");
            Logger.chatMessage(EnumChatFormatting.GREEN + "Recipes exported!");
        } catch (Exception e) {
            Logger.MOD.error("Failed to export recipes to local JSON", e);
            Logger.chatMessage(EnumChatFormatting.RED + "Failed to export recipes JSON: " + e.getMessage());
            throw e;
        }
    }

    public static List<CanonicalRenderAsset> writeCanonicalSnapshot(
            EntityManager entityManager,
            File repositoryDirectory,
            String profileId,
            boolean includeRenderAssets) throws Exception {
        Logger.chatMessage(EnumChatFormatting.AQUA + "Exporting NESQL++ canonical snapshot...");
        try {
            List<CanonicalRenderAsset> renderAssets =
                    new CanonicalRepositorySnapshotWriter(entityManager, repositoryDirectory, profileId, includeRenderAssets)
                            .export();
            new GregTechCircuitProgressionWriter(repositoryDirectory).export();
            new GregTechMaterialPartsWriter(repositoryDirectory).export();
            new ForestryGeneticsWriter(repositoryDirectory).export();
            return renderAssets;
        } catch (Exception e) {
            Logger.MOD.error("Failed to export NESQL++ canonical snapshot", e);
            Logger.chatMessage(
                    EnumChatFormatting.RED + "Failed to export NESQL++ canonical snapshot: " + e.getMessage());
            throw e;
        }
    }

    public static void writeGregTechMultiblocks(String repositoryName) throws Exception {
        Logger.chatMessage(EnumChatFormatting.AQUA + "Exporting GregTech multiblock blueprints...");
        try {
            new GregTechMultiblockExporter(repositoryName).export();
        } catch (Exception e) {
            Logger.MOD.error("Failed to export GregTech multiblock blueprints", e);
            Logger.chatMessage(
                    EnumChatFormatting.RED + "Failed to export GregTech multiblock blueprints: " + e.getMessage());
            throw e;
        }
    }

    public static void writeBlockFaceMetadata(String repositoryName) throws Exception {
        Logger.chatMessage(EnumChatFormatting.AQUA + "Exporting block face metadata...");
        try {
            new BlockFaceExporter(repositoryName).export();
        } catch (Exception e) {
            Logger.MOD.error("Failed to export block face metadata", e);
            Logger.chatMessage(
                    EnumChatFormatting.RED + "Failed to export block face metadata: " + e.getMessage());
            throw e;
        }
    }

    public static List<CanonicalRenderAsset> collectRenderAssets(
            EntityManager entityManager,
            File repositoryDirectory) throws Exception {
        if (entityManager == null) {
            throw new IllegalArgumentException("Render asset collection requires a live EntityManager");
        }
        Logger.chatMessage(EnumChatFormatting.AQUA + "Collecting NESQL++ render assets from the export database...");
        try {
            List<CanonicalRenderAsset> assets =
                    new CanonicalRenderAssetCollector(entityManager, repositoryDirectory).collectAll();
            Logger.MOD.info(
                    "Collected {} render assets from the export database for render-contract stages.",
                    assets.size());
            Logger.chatMessage(
                    EnumChatFormatting.GREEN
                            + "Collected "
                            + assets.size()
                            + " render assets for atlas/manifest stages.");
            return assets;
        } catch (Exception e) {
            Logger.MOD.error("Failed to collect NESQL++ render assets from the export database", e);
            Logger.chatMessage(
                    EnumChatFormatting.RED + "Failed to collect NESQL++ render assets: " + e.getMessage());
            throw e;
        }
    }

    public static List<CanonicalRenderAsset> collectRenderAssetsFromFiles(File repositoryDirectory) throws Exception {
        Logger.chatMessage(EnumChatFormatting.AQUA + "Collecting NESQL++ render assets from exported files...");
        try {
            List<CanonicalRenderAsset> assets =
                    CanonicalRenderAssetCollector.collectFromExportedFiles(repositoryDirectory);
            Logger.MOD.info(
                    "Collected {} render assets from exported files for offline render-contract rebuild.",
                    assets.size());
            return assets;
        } catch (Exception e) {
            Logger.MOD.error("Failed to collect NESQL++ render assets from exported files", e);
            throw e;
        }
    }

    public static void writeRenderAssetManifest(EntityManager entityManager, File repositoryDirectory) throws Exception {
        writeRenderAssetManifest(entityManager, repositoryDirectory, null);
    }

    public static void writeRenderAssetManifest(
            EntityManager entityManager,
            File repositoryDirectory,
            List<CanonicalRenderAsset> precollectedAssets) throws Exception {
        Logger.chatMessage(EnumChatFormatting.AQUA + "Exporting NESQL++ render asset manifest...");
        try {
            new CanonicalRenderAssetManifestWriter(entityManager, repositoryDirectory, precollectedAssets).export();
        } catch (Exception e) {
            Logger.MOD.error("Failed to export NESQL++ render asset manifest", e);
            Logger.chatMessage(
                    EnumChatFormatting.RED + "Failed to export NESQL++ render asset manifest: " + e.getMessage());
            throw e;
        }
    }

    public static void writeAtlasPacks(EntityManager entityManager, File repositoryDirectory) throws Exception {
        writeAtlasPacks(entityManager, repositoryDirectory, null);
    }

    public static void writeAtlasPacks(
            EntityManager entityManager,
            File repositoryDirectory,
            List<CanonicalRenderAsset> precollectedAssets) throws Exception {
        Logger.chatMessage(EnumChatFormatting.AQUA + "Packing NESQL++ static atlases...");
        try {
            new CanonicalAtlasPackWriter(entityManager, repositoryDirectory, precollectedAssets).export();
        } catch (Exception e) {
            Logger.MOD.error("Failed to write NESQL++ atlas packs", e);
            Logger.chatMessage(
                    EnumChatFormatting.RED + "Failed to write NESQL++ atlas packs: " + e.getMessage());
            throw e;
        }
    }

    public static void writeAnimationManifest(EntityManager entityManager, File repositoryDirectory) throws Exception {
        writeAnimationManifest(entityManager, repositoryDirectory, null);
    }

    public static void writeAnimationManifest(
            EntityManager entityManager,
            File repositoryDirectory,
            List<CanonicalRenderAsset> precollectedAssets) throws Exception {
        Logger.chatMessage(EnumChatFormatting.AQUA + "Exporting NESQL++ animation manifest...");
        try {
            new CanonicalAnimationManifestWriter(entityManager, repositoryDirectory, precollectedAssets).export();
        } catch (Exception e) {
            Logger.MOD.error("Failed to write NESQL++ animation manifest", e);
            Logger.chatMessage(
                    EnumChatFormatting.RED + "Failed to write NESQL++ animation manifest: " + e.getMessage());
            throw e;
        }
    }

    public static void writeAnimatedAtlasPacks(EntityManager entityManager, File repositoryDirectory) throws Exception {
        writeAnimatedAtlasPacks(entityManager, repositoryDirectory, null);
    }

    public static void writeAnimatedAtlasPacks(
            EntityManager entityManager,
            File repositoryDirectory,
            List<CanonicalRenderAsset> precollectedAssets) throws Exception {
        Logger.chatMessage(EnumChatFormatting.AQUA + "Packing NESQL++ animated atlases...");
        try {
            new CanonicalAnimatedAtlasPackWriter(entityManager, repositoryDirectory, precollectedAssets).export();
        } catch (Exception e) {
            Logger.MOD.error("Failed to write NESQL++ animated atlas packs", e);
            Logger.chatMessage(
                    EnumChatFormatting.RED + "Failed to write NESQL++ animated atlas packs: " + e.getMessage());
            throw e;
        }
    }

    public static void writeRenderIndex(EntityManager entityManager, File repositoryDirectory) throws Exception {
        writeRenderIndex(entityManager, repositoryDirectory, null);
    }

    public static void writeRenderIndex(
            EntityManager entityManager,
            File repositoryDirectory,
            List<CanonicalRenderAsset> precollectedAssets) throws Exception {
        Logger.chatMessage(EnumChatFormatting.AQUA + "Exporting NESQL++ unified render index...");
        try {
            new CanonicalRenderIndexWriter(entityManager, repositoryDirectory, precollectedAssets).export();
        } catch (Exception e) {
            Logger.MOD.error("Failed to write NESQL++ unified render index", e);
            Logger.chatMessage(
                    EnumChatFormatting.RED + "Failed to write NESQL++ unified render index: " + e.getMessage());
            throw e;
        }
    }

    public static void writeAtlasRegistry(EntityManager entityManager, File repositoryDirectory) throws Exception {
        writeAtlasRegistry(entityManager, repositoryDirectory, null);
    }

    public static void writeAtlasRegistry(
            EntityManager entityManager,
            File repositoryDirectory,
            List<CanonicalRenderAsset> precollectedAssets) throws Exception {
        Logger.chatMessage(EnumChatFormatting.AQUA + "Exporting NESQL++ atlas registry...");
        try {
            new CanonicalAtlasRegistryWriter(entityManager, repositoryDirectory, precollectedAssets).export();
        } catch (Exception e) {
            Logger.MOD.error("Failed to write NESQL++ atlas registry", e);
            Logger.chatMessage(
                    EnumChatFormatting.RED + "Failed to write NESQL++ atlas registry: " + e.getMessage());
            throw e;
        }
    }

    public static void writeBrowserAtlasIndex(File repositoryDirectory) throws Exception {
        Logger.chatMessage(EnumChatFormatting.AQUA + "Exporting NESQL++ browser atlas index...");
        try {
            new CanonicalBrowserAtlasIndexWriter(repositoryDirectory).export();
        } catch (Exception e) {
            Logger.MOD.error("Failed to write NESQL++ browser atlas index", e);
            Logger.chatMessage(
                    EnumChatFormatting.RED + "Failed to write NESQL++ browser atlas index: " + e.getMessage());
            throw e;
        }
    }

    public static void writeBrowserLayoutIndex(EntityManager entityManager, File repositoryDirectory) throws Exception {
        Logger.chatMessage(EnumChatFormatting.AQUA + "Exporting NESQL++ browser layout index...");
        try {
            new CanonicalBrowserLayoutIndexWriter(entityManager, repositoryDirectory).export();
        } catch (Exception e) {
            Logger.MOD.error("Failed to write NESQL++ browser layout index", e);
            Logger.chatMessage(
                    EnumChatFormatting.RED + "Failed to write NESQL++ browser layout index: " + e.getMessage());
            throw e;
        }
    }

    public static void writeUiFamilyCensus(File rawDir) throws Exception {
        Logger.chatMessage(EnumChatFormatting.AQUA + "Exporting NESQL++ NEI UI family census...");
        try {
            new RawExportUiFamilyCensusWriter(rawDir).export();
        } catch (Exception e) {
            Logger.MOD.error("Failed to write NESQL++ NEI UI family census", e);
            Logger.chatMessage(
                    EnumChatFormatting.RED + "Failed to write NEI UI family census: " + e.getMessage());
            throw e;
        }
    }

    public static void writeUiTemplateCatalog(File rawDir) throws Exception {
        Logger.chatMessage(EnumChatFormatting.AQUA + "Exporting NESQL++ NEI UI template catalog...");
        try {
            new RawExportUiTemplateCatalogWriter(rawDir).export();
        } catch (Exception e) {
            Logger.MOD.error("Failed to write NESQL++ NEI UI template catalog", e);
            Logger.chatMessage(
                    EnumChatFormatting.RED + "Failed to write NEI UI template catalog: " + e.getMessage());
            throw e;
        }
    }

    public static void writeRawExportSidecar(
            EntityManager entityManager,
            File repositoryDirectory,
            File rawDir,
            ExportContext exportContext,
            List<CanonicalRenderAsset> precollectedAssets) throws Exception {
        Logger.chatMessage(EnumChatFormatting.AQUA + "Writing NESQL++ raw-export sidecar...");
        try {
            new RawExportSidecarWriter(
                    entityManager,
                    repositoryDirectory,
                    rawDir,
                    exportContext,
                    precollectedAssets).export();
        } catch (Exception e) {
            Logger.MOD.error("Failed to write NESQL++ raw-export sidecar", e);
            Logger.chatMessage(
                    EnumChatFormatting.RED + "Failed to write raw-export sidecar: " + e.getMessage());
            throw e;
        }
    }

    public static void syncRawExportFinalReports(File rawDir) throws Exception {
        RawExportSidecarWriter.syncFinalReports(rawDir);
    }

    public static void deleteCanonicalStagingDirectory(File repositoryDirectory) {
        File canonicalDir = new File(repositoryDirectory, "canonical");
        if (!canonicalDir.exists()) {
            return;
        }
        try {
            deleteRecursively(canonicalDir);
            Logger.chatMessage(
                    EnumChatFormatting.GREEN
                            + "[NESQL] Removed legacy canonical staging output; raw-export is authoritative.");
        } catch (Exception e) {
            Logger.MOD.warn("Failed to remove legacy canonical staging directory", e);
            Logger.chatMessage(
                    EnumChatFormatting.YELLOW
                            + "[NESQL] Could not remove canonical staging directory: "
                            + e.getMessage());
        }
    }

    private static void deleteRecursively(File file) throws java.io.IOException {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        java.nio.file.Files.deleteIfExists(file.toPath());
    }
}
