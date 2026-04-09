package com.github.dcysteine.nesql.exporter.plugin.witchery;

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
 * Handles setup of Witchery recipe types.
 */
public class WitcheryRecipeTypeHandler extends PluginHelper {
    private final PluginExporter exporter;

    private RecipeType spinner;
    private RecipeType distillery;
    private RecipeType altar;
    private RecipeType cauldron;

    public WitcheryRecipeTypeHandler(PluginExporter exporter) {
        super(exporter);
        this.exporter = exporter;
    }

    public void initialize() {
        ItemFactory itemFactory = new ItemFactory(exporter);
        RecipeTypeFactory recipeTypeFactory = new RecipeTypeFactory(exporter);

        // Use crafting table as default icon
        ItemStack craftingTableStack = ItemUtil.getItemStack(Blocks.crafting_table).get();
        Item defaultIcon = itemFactory.get(craftingTableStack);

        // Spinning Wheel
        spinner = recipeTypeFactory.newBuilder()
                .setId("witchery", "spinner")
                .setCategory("Witchery")
                .setType("Spinning Wheel")
                .setIcon(defaultIcon)
                .setShapeless(true)
                .setItemInputDimension(1, 1)
                .setItemOutputDimension(1, 1)
                .build();

        // Distillery
        distillery = recipeTypeFactory.newBuilder()
                .setId("witchery", "distillery")
                .setCategory("Witchery")
                .setType("Distillery")
                .setIcon(defaultIcon)
                .setShapeless(true)
                .setItemInputDimension(1, 1)
                .setItemOutputDimension(1, 1)
                .build();

        // Witches' Altar
        altar = recipeTypeFactory.newBuilder()
                .setId("witchery", "altar")
                .setCategory("Witchery")
                .setType("Witches' Altar")
                .setIcon(defaultIcon)
                .setShapeless(true)
                .setItemInputDimension(1, 9)
                .setItemOutputDimension(1, 1)
                .build();

        // Cauldron
        cauldron = recipeTypeFactory.newBuilder()
                .setId("witchery", "cauldron")
                .setCategory("Witchery")
                .setType("Cauldron")
                .setIcon(defaultIcon)
                .setShapeless(true)
                .setItemInputDimension(1, 9)
                .setItemOutputDimension(1, 1)
                .build();
    }

    public RecipeType getSpinner() {
        return spinner;
    }

    public RecipeType getDistillery() {
        return distillery;
    }

    public RecipeType getAltar() {
        return altar;
    }

    public RecipeType getCauldron() {
        return cauldron;
    }
}
