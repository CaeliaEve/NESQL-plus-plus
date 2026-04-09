package com.github.dcysteine.nesql.exporter.local;

import com.github.dcysteine.nesql.exporter.main.Logger;
import com.github.dcysteine.nesql.sql.base.recipe.Recipe;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import jakarta.persistence.EntityManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * V14 mod-organized recipe exporter.
 *
 * <p>Output structure: {@code recipes/{type}/{modId}/recipes.json.gz}</p>
 */
public class ModBasedRecipeExporter {

    private final EntityManager entityManager;
    private final File exportDirectory;
    private final ModBasedRecipeGroupingBuilder groupingBuilder;

    public ModBasedRecipeExporter(EntityManager entityManager, File exportDirectory) {
        this.entityManager = entityManager;
        this.exportDirectory = exportDirectory;
        this.groupingBuilder = new ModBasedRecipeGroupingBuilder(new ModBasedRecipeDtoAssembler(entityManager));
    }

    public void exportRecipes() throws IOException {
        Logger.MOD.info("============================================================");
        Logger.MOD.info("=== Starting V14 Recipe Export ===");
        Logger.MOD.info("============================================================");
        Logger.MOD.info("Export directory: {}", exportDirectory.getAbsolutePath());
        Logger.MOD.info("Organization: recipes/{type}/{modId}/recipes.json.gz");

        try {
            exportRecipesByType("crafting", "Crafting Recipes");

            Logger.MOD.info("============================================================");
            Logger.MOD.info("=== V14 Recipe Export Complete ===");
            Logger.MOD.info("============================================================");
        } catch (Exception e) {
            Logger.MOD.error("V14 recipe export failed", e);
            throw e;
        }
    }

    private void exportRecipesByType(String recipeType, String typeName) throws IOException {
        Logger.MOD.info("--- Exporting {} recipes ---", typeName);
        Logger.MOD.info("Recipe type: {}", recipeType);

        try {
            ModBasedRecipeDataset dataset = ModBasedRecipeDatasetLoader.load(entityManager, recipeType);
            if (dataset.recipes.isEmpty()) {
                Logger.MOD.warn("No recipes found in database!");
                return;
            }

            Logger.MOD.info("Grouping recipes by output mod...");
            Map<String, List<RecipeDTO>> recipesByMod = groupingBuilder.groupByOutputMod(dataset, recipeType);
            Logger.MOD.info("Found {} unique mods", recipesByMod.size());

            File recipesDir = new File(exportDirectory, "recipes/" + recipeType);
            Logger.MOD.info("Creating recipes directory: {}", recipesDir.getAbsolutePath());
            if (!recipesDir.exists()) {
                recipesDir.mkdirs();
                Logger.MOD.info("Directory created");
            }

            Gson gson = new GsonBuilder().serializeNulls().create();

            int modCount = 0;
            int totalRecipes = 0;
            Logger.MOD.info("Starting per-mod export...");

            for (Map.Entry<String, List<RecipeDTO>> entry : recipesByMod.entrySet()) {
                String modId = entry.getKey();
                List<RecipeDTO> modRecipes = entry.getValue();

                modCount++;
                totalRecipes += modRecipes.size();

                Logger.MOD.info("[{}/{}] Exporting mod: {} ({} recipes)",
                        modCount, recipesByMod.size(), modId, modRecipes.size());
                Logger.chatMessage(String.format(
                        "[%d/%d] Exporting mod: %s (%d recipes)...",
                        modCount,
                        recipesByMod.size(),
                        modId,
                        modRecipes.size()));

                String safeModId = ModBasedRecipeFileSupport.sanitizeModId(modId);
                File modDir = new File(recipesDir, safeModId);
                if (!modDir.exists()) {
                    modDir.mkdirs();
                }

                File modRecipesFile = new File(modDir, "recipes.json");
                Logger.MOD.info("Writing JSON to: {}", modRecipesFile.getAbsolutePath());
                try (FileOutputStream fos = new FileOutputStream(modRecipesFile);
                     OutputStreamWriter writer = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
                    gson.toJson(modRecipes, writer);
                }
                Logger.MOD.info("JSON written successfully");

                Logger.MOD.info("Compressing JSON file...");
                File compressedFile = ModBasedRecipeFileSupport.compressFile(modRecipesFile);
                long originalSize = modRecipesFile.length();
                long compressedSize = compressedFile.length();
                double ratio = (1.0 - (double) compressedSize / originalSize) * 100;

                Logger.MOD.info("Compression complete: {} -> {} (saved {}%)",
                        ModBasedRecipeFileSupport.formatSize(originalSize),
                        ModBasedRecipeFileSupport.formatSize(compressedSize),
                        String.format("%.1f", ratio));
                Logger.chatMessage(String.format(
                        "  %.2f MB -> %.2f MB (saved %.1f%%)",
                        originalSize / 1024.0 / 1024.0,
                        compressedSize / 1024.0 / 1024.0,
                        ratio));

                Logger.MOD.info("Deleting uncompressed JSON...");
                modRecipesFile.delete();

                if (modCount % 10 == 0) {
                    Logger.MOD.info("Progress: {}/{} mods completed", modCount, recipesByMod.size());
                    Logger.chatMessage(String.format("Progress: %d/%d mods completed",
                            modCount, recipesByMod.size()));
                }
            }

            Logger.MOD.info("=== Per-mod export complete ===");
            Logger.MOD.info("Total mods: {}", recipesByMod.size());
            Logger.MOD.info("Total recipes: {}", totalRecipes);
            Logger.chatMessage(String.format("%s export complete: %d mods, %d recipes",
                    typeName, recipesByMod.size(), totalRecipes));
        } catch (Exception e) {
            Logger.MOD.error(String.format("Failed to export %s", recipeType), e);
            throw e;
        }
    }

    public static class RecipeDTO {
        public String id;
        public String recipeType;
        public List<ItemStackDTO> outputs = new ArrayList<>();
        public List<ItemGroupDTO> inputs = new ArrayList<>();
        public List<FluidGroupDTO> fluidInputs = new ArrayList<>();
        public List<FluidStackDTO> fluidOutputs = new ArrayList<>();
        public MachineInfoDTO machineInfo;
        public RecipeMetadataDTO metadata;
    }

    public static class ItemStackDTO {
        public ItemDTO item;
        public int stackSize;
        public double probability;
    }

    public static class ItemGroupDTO {
        public int slotIndex;
        public List<ItemStackDTO> items;
        public boolean isOreDictionary;
        public String oreDictName;
    }

    public static class MachineInfoDTO {
        public String machineId;
        public String category;
        public String machineType;
        public String iconInfo;
        public boolean shapeless;
        public String parsedVoltageTier;
        public Integer parsedVoltage;
        public ItemDTO machineIcon;
    }

    public static class ItemDTO {
        public String itemId;
        public String modId;
        public String localizedName;
        public String renderAssetRef;
        public String imageFileName;
    }

    public static class FluidDTO {
        public String fluidId;
        public String modId;
        public String internalName;
        public String localizedName;
        public String renderAssetRef;
        public int temperature;
    }

    public static class FluidStackDTO {
        public FluidDTO fluid;
        public int amount;
        public double probability;
    }

    public static class FluidGroupDTO {
        public int slotIndex;
        public List<FluidStackDTO> fluids;
    }

    public static class RecipeMetadataDTO {
        public String voltageTier;
        public Integer voltage;
        public Integer amperage;
        public Integer duration;
        public Long totalEU;
        public Boolean requiresCleanroom;
        public Boolean requiresLowGravity;
        public String additionalInfo;
        public List<ItemDTO> specialItems;
    }
}
