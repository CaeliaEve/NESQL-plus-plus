package com.github.dcysteine.nesql.exporter.plugin.thaumcraft;

import com.github.dcysteine.nesql.exporter.plugin.PluginExporter;
import com.github.dcysteine.nesql.exporter.plugin.PluginHelper;
import com.github.dcysteine.nesql.exporter.plugin.base.factory.ItemFactory;
import com.github.dcysteine.nesql.exporter.plugin.base.factory.RecipeTypeFactory;
import com.github.dcysteine.nesql.exporter.util.ItemUtil;
import com.github.dcysteine.nesql.sql.base.item.Item;
import com.github.dcysteine.nesql.sql.base.recipe.RecipeType;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;

/**
 * Handles setup of Thaumcraft recipe types.
 */
public class ThaumcraftRecipeTypeHandler extends PluginHelper {
    private final PluginExporter exporter;

    private RecipeType infusionCrafting;
    private RecipeType arcaneWorkbench;
    private RecipeType crucible;
    private RecipeType aspectCombination;

    public ThaumcraftRecipeTypeHandler(PluginExporter exporter) {
        super(exporter);
        this.exporter = exporter;
    }

    public void initialize() {
        ItemFactory itemFactory = new ItemFactory(exporter);
        RecipeTypeFactory recipeTypeFactory = new RecipeTypeFactory(exporter);

        // Use crafting table as default icon
        ItemStack craftingTableStack = ItemUtil.getItemStack(Blocks.crafting_table).get();
        Item defaultIcon = itemFactory.get(craftingTableStack);

        // Infusion Crafting (注魔祭坛)
        infusionCrafting = recipeTypeFactory.newBuilder()
                .setId("thaumcraft", "infusion")
                .setCategory("Thaumcraft")
                .setType("Infusion Crafting")
                .setIcon(defaultIcon)
                .setShapeless(true)
                .setItemInputDimension(1, 9)
                .setItemOutputDimension(1, 1)
                .build();

        // Arcane Workbench (奥数工作台)
        arcaneWorkbench = recipeTypeFactory.newBuilder()
                .setId("thaumcraft", "arcane_workbench")
                .setCategory("Thaumcraft")
                .setType("Arcane Workbench")
                .setIcon(defaultIcon)
                .setShapeless(false)
                .setItemInputDimension(3, 3)
                .setItemOutputDimension(1, 1)
                .build();

        // Crucible (坩埚)
        crucible = recipeTypeFactory.newBuilder()
                .setId("thaumcraft", "crucible")
                .setCategory("Thaumcraft")
                .setType("Crucible")
                .setIcon(defaultIcon)
                .setShapeless(true)
                .setItemInputDimension(1, 1)
                .setItemOutputDimension(1, 1)
                .build();

        // Aspect Combination (源质合成)
        aspectCombination = recipeTypeFactory.newBuilder()
                .setId("thaumcraft", "aspect_combination")
                .setCategory("Thaumcraft")
                .setType("Aspect Combination")
                .setIcon(defaultIcon)
                .setShapeless(true)
                .setItemInputDimension(1, 1)
                .setItemOutputDimension(1, 1)
                .build();
    }

    public RecipeType getInfusionCrafting() {
        return infusionCrafting;
    }

    public RecipeType getArcaneWorkbench() {
        return arcaneWorkbench;
    }

    public RecipeType getCrucible() {
        return crucible;
    }

    public RecipeType getAspectCombination() {
        return aspectCombination;
    }
}
