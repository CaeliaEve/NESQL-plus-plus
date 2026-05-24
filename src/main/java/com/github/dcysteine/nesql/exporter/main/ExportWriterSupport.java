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
import com.github.dcysteine.nesql.exporter.local.RawExportV3SidecarWriter;
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
            boolean includeRenderAssets,
            boolean failOnError) throws Exception {
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
            if (failOnError) {
                throw e;
            }
        }
        return java.util.Collections.emptyList();
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

    public static List<CanonicalRenderAsset> collectRenderAssets(File repositoryDirectory) throws Exception {
        Logger.chatMessage(EnumChatFormatting.AQUA + "Collecting NESQL++ render assets from exported files...");
        try {
            List<CanonicalRenderAsset> assets =
                    new CanonicalRenderAssetCollector(null, repositoryDirectory).collectAll();
            Logger.MOD.info(
                    "Collected {} render assets from exported files for render-contract stages.",
                    assets.size());
            Logger.chatMessage(
                    EnumChatFormatting.GREEN
                            + "Collected "
                            + assets.size()
                            + " render assets for atlas/manifest stages.");
            return assets;
        } catch (Exception e) {
            Logger.MOD.error("Failed to collect NESQL++ render assets from exported files", e);
            Logger.chatMessage(
                    EnumChatFormatting.RED + "Failed to collect NESQL++ render assets: " + e.getMessage());
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

    public static void writeRawExportV3Sidecar(
            EntityManager entityManager,
            File repositoryDirectory,
            ExportContext exportContext,
            List<CanonicalRenderAsset> precollectedAssets) throws Exception {
        Logger.chatMessage(EnumChatFormatting.AQUA + "Writing NESQL++ raw-export v3 sidecar...");
        try {
            new RawExportV3SidecarWriter(entityManager, repositoryDirectory, exportContext, precollectedAssets).export();
        } catch (Exception e) {
            Logger.MOD.error("Failed to write NESQL++ raw-export v3 sidecar", e);
            Logger.chatMessage(
                    EnumChatFormatting.RED + "Failed to write raw-export v3 sidecar: " + e.getMessage());
            throw e;
        }
    }

    public static void syncRawExportV3FinalReports(File repositoryDirectory) {
        try {
            RawExportV3SidecarWriter.syncFinalReports(repositoryDirectory);
        } catch (Exception e) {
            Logger.MOD.warn("Failed to sync raw-export v3 final reports", e);
        }
    }
}
