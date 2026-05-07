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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Mod-organized recipe exporter for the NESQL++ v1.04 export chain.
 *
 * <p>Output structure: {@code recipes/{type}/{modId}/recipes.json.gz}</p>
 */
public class ModBasedRecipeExporter {

    private final EntityManager entityManager;
    private final File exportDirectory;
    private final ModBasedRecipeGroupingBuilder groupingBuilder;
    private final Set<String> modFilter;

    public ModBasedRecipeExporter(EntityManager entityManager, File exportDirectory) {
        this(entityManager, exportDirectory, Collections.emptySet());
    }

    public ModBasedRecipeExporter(EntityManager entityManager, File exportDirectory, Set<String> modFilter) {
        this.entityManager = entityManager;
        this.exportDirectory = exportDirectory;
        this.groupingBuilder = new ModBasedRecipeGroupingBuilder(new ModBasedRecipeDtoAssembler(entityManager));
        this.modFilter = modFilter == null ? Collections.emptySet() : modFilter;
    }

    public void exportRecipes() throws IOException {
        Logger.MOD.info("============================================================");
        Logger.MOD.info("=== Starting v1.04 Recipe Export ===");
        Logger.MOD.info("============================================================");
        Logger.MOD.info("Export directory: {}", exportDirectory.getAbsolutePath());
        Logger.MOD.info("Organization: recipes/{type}/{modId}/recipes.json.gz");

        try {
            exportRecipesByType("crafting", "Crafting Recipes");

            Logger.MOD.info("============================================================");
            Logger.MOD.info("=== v1.04 Recipe Export Complete ===");
            Logger.MOD.info("============================================================");
        } catch (Exception e) {
            Logger.MOD.error("v1.04 recipe export failed", e);
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
            if (!modFilter.isEmpty()) {
                recipesByMod.entrySet().removeIf(entry -> !matchesModFilter(entry.getKey()));
                Logger.MOD.info("Applying mod filter: {} -> {} matching mods", modFilter, recipesByMod.size());
                Logger.chatMessage(String.format("Safe merge: writing only %d target mod recipe files", recipesByMod.size()));
            }
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

                File compressedFile = ModBasedRecipeFileSupport.jsonGzipFile(modDir, "recipes.json");
                Logger.MOD.info("Writing compressed JSON to: {}", compressedFile.getAbsolutePath());
                long compressedSize = ModBasedRecipeFileSupport.writeCompressedJson(gson, modRecipes, compressedFile);
                Logger.MOD.info("Compressed JSON written successfully: {}",
                        ModBasedRecipeFileSupport.formatSize(compressedSize));
                Logger.chatMessage("  Wrote " + ModBasedRecipeFileSupport.formatSize(compressedSize) + " compressed");

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

    private boolean matchesModFilter(String modId) {
        if (modFilter.isEmpty()) {
            return true;
        }
        for (String candidate : modFilter) {
            if (candidate != null && candidate.equalsIgnoreCase(modId)) {
                return true;
            }
        }
        return false;
    }

    public static class RecipeDTO {
        public String id;
        public String recipeType;
        public List<ItemStackDTO> outputs = new ArrayList<>();
        public List<ItemGroupDTO> inputs = new ArrayList<>();
        public List<FluidGroupDTO> fluidInputs = new ArrayList<>();
        public List<FluidStackDTO> fluidOutputs = new ArrayList<>();
        public MachineInfoDTO machineInfo;
        public java.util.Map<String, Object> layout;
        public java.util.Map<String, Object> additionalData;
        public RecipeMetadataDTO metadata;
    }

    public static class ItemStackDTO {
        public Integer slotIndex;
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
        public String layoutClass;
        public boolean shapeless;
        public String parsedVoltageTier;
        public Integer parsedVoltage;
        public Integer itemInputWidth;
        public Integer itemInputHeight;
        public Integer itemOutputWidth;
        public Integer itemOutputHeight;
        public Integer fluidInputWidth;
        public Integer fluidInputHeight;
        public Integer fluidOutputWidth;
        public Integer fluidOutputHeight;
        public Boolean supportsFluids;
        public Boolean supportsSpecialItems;
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
        public Integer slotIndex;
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
        public java.util.Map<String, Integer> aspects;
        public String specialRecipeType;
        public String correctedMachineType;
        public String research;
        public String centralItemId;
        public Integer centerInputSlotIndex;
        public Integer instability;
        public List<Integer> componentSlotOrder;
        public Integer tier;
        public Integer bloodCost;
        public Integer lpCost;
        public Integer consumptionRate;
        public Integer drainRate;
        public Integer tartaricCost;
        public Boolean isWeakActivation;
    }
}
