package com.github.dcysteine.nesql.exporter.plugin.gregtech;

import com.github.dcysteine.nesql.exporter.nativeui.NativeNeiFrameExportRegistry;
import com.github.dcysteine.nesql.exporter.main.Logger;
import com.github.dcysteine.nesql.exporter.plugin.PluginExporter;
import com.github.dcysteine.nesql.exporter.plugin.PluginHelper;
import com.github.dcysteine.nesql.exporter.plugin.base.factory.RecipeBuilder;
import com.github.dcysteine.nesql.exporter.plugin.gregtech.util.GTRecipeMap;
import com.github.dcysteine.nesql.exporter.plugin.gregtech.util.GregTechRecipeTypeHandler;
import com.github.dcysteine.nesql.exporter.plugin.gregtech.util.GregTechUtil;
import com.github.dcysteine.nesql.exporter.plugin.gregtech.util.Voltage;
import com.github.dcysteine.nesql.sql.base.recipe.Recipe;
import com.github.dcysteine.nesql.sql.base.recipe.RecipeType;
import gregtech.nei.GTNEIDefaultHandler;
import gregtech.api.util.GTRecipe;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class GregTechRecipeProcessor extends PluginHelper {
    private final GregTechRecipeTypeHandler recipeTypeHandler;
    private int nativeFrameQueueCount = 0;

    public GregTechRecipeProcessor(
            PluginExporter exporter, GregTechRecipeTypeHandler recipeTypeHandler) {
        super(exporter);
        this.recipeTypeHandler = recipeTypeHandler;
    }

    public void process() {
        int recipeMapTotal = GTRecipeMap.allNEIRecipeMaps.values().size();
        logger.info("Processing {} GregTech recipe maps...", recipeMapTotal);

        GregTechRecipeFactory gregTechRecipeFactory = new GregTechRecipeFactory(exporter);
        int recipeMapCount = 0;
        for (GTRecipeMap GTRecipeMap : GTRecipeMap.allNEIRecipeMaps.values()) {
            logger.info("Processing recipe map: " + GTRecipeMap.getName());
            recipeMapCount++;

            Collection<GTRecipe> recipes = GTRecipeMap.getRecipeMap().getAllRecipes();
            int total = recipes.size();
            logger.info("Processing {} GregTech recipes...", total);

            int count = 0;
            for (GTRecipe recipe : recipes) {
                count++;

                try {
                    int voltage = recipe.mEUt / GTRecipeMap.getAmperage();
                    Voltage voltageTier = Voltage.convertVoltage(voltage);
                    RecipeType recipeType = recipeTypeHandler.getRecipeType(GTRecipeMap, voltageTier);
                    RecipeBuilder builder = new RecipeBuilder(exporter, recipeType);
                    // TODO if we want to avoid skipping slots, esp. output slots, add null checks.
                    for (ItemStack input : recipe.mInputs) {
                        builder.addItemGroupInput(GregTechUtil.reverseUnify(input));
                    }
                    for (FluidStack input : recipe.mFluidInputs) {
                        builder.addFluidInput(input);
                    }
                    for (int i = 0; i < recipe.mOutputs.length; i++) {
                        ItemStack output = recipe.mOutputs[i];
                        int chance = recipe.getOutputChance(i);
                        if (chance == 100_00) {
                            builder.addItemOutput(output);
                        } else {
                            builder.addItemOutput(output, chance / 100_00d);
                        }
                    }
                    for (FluidStack output : recipe.mFluidOutputs) {
                        builder.addFluidOutput(output);
                    }

                    List<ItemStack> specialItems = new ArrayList<>();
                    if (recipe.mSpecialItems != null) {
                        specialItems = GregTechUtil.reverseUnify(recipe.mSpecialItems);
                    }

                    Recipe recipeEntity = builder.build();
                    gregTechRecipeFactory.get(
                            recipeEntity, GTRecipeMap, recipe, voltageTier, voltage, specialItems);
                    registerNativeGtFrame(recipeEntity, GTRecipeMap, recipe);
                } catch (Exception e) {
                    if (NativeNeiFrameExportRegistry.isEnabled()) {
                        throw new IllegalStateException(
                                "Native GregTech NEI frame export failed for recipe map "
                                        + GTRecipeMap.getName(),
                                e);
                    }
                    // This try-catch is sadly necessary. There's a few weird exceptions that get
                    // thrown. There's even some that lack a stack trace!
                    logger.error("Caught exception processing GregTech recipe!", e);
                }

                if (Logger.intermittentLog(count)) {
                    logger.info(
                            "Processed GregTech {} recipe {} of {}",
                            GTRecipeMap.getName(), count, total);
                }
            }

            exporterState.flushEntityManager();
            NativeNeiFrameExportRegistry.awaitPendingFrames();
            logger.info("Processed GregTech recipe map {} of {}", recipeMapCount, recipeMapTotal);
        }

        NativeNeiFrameExportRegistry.awaitPendingFrames();
        logger.info("Finished processing GregTech recipe maps!");
    }

    private void registerNativeGtFrame(Recipe recipeEntity, GTRecipeMap recipeMap, GTRecipe gtRecipe) {
        if (recipeEntity == null || recipeMap == null || gtRecipe == null
                || !NativeNeiFrameExportRegistry.isEnabled()) {
            return;
        }
        try {
            GTNEIDefaultHandler handler =
                    new GTNEIDefaultHandler(recipeMap.getRecipeMap().getDefaultRecipeCategory());
            handler.arecipes.add(handler.new CachedDefaultRecipe(gtRecipe));
            Map<String, Object> nativeFrame = NativeNeiFrameExportRegistry.captureRecipeFrame(
                    recipeEntity.getId(),
                    handler,
                    0);
            if (nativeFrame == null) {
                throw new IllegalStateException(
                        "Native GregTech NEI frame ABI was not produced for recipe "
                                + recipeEntity.getId());
            }
            Map<String, Object> data = new LinkedHashMap<String, Object>();
            data.put("nativeFrame", nativeFrame);
            com.github.dcysteine.nesql.exporter.util.SpecialRecipeMetadataRegistry.registerMetadata(
                    recipeEntity.getId(),
                    new com.github.dcysteine.nesql.exporter.util.SpecialRecipeMetadataRegistry.SpecialRecipeMetadata(
                            "NativeNEI_Frame", data));
            nativeFrameQueueCount++;
            if (nativeFrameQueueCount % 64 == 0) {
                NativeNeiFrameExportRegistry.awaitPendingFrames();
            }
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to queue native GregTech NEI frame for recipe " + recipeEntity.getId(),
                    e);
        }
    }

}
