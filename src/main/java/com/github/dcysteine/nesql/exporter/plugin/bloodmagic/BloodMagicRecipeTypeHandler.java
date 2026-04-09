package com.github.dcysteine.nesql.exporter.plugin.bloodmagic;

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
 * Handles setup of Blood Magic recipe types.
 */
public class BloodMagicRecipeTypeHandler extends PluginHelper {
    private final PluginExporter exporter;

    private RecipeType altar;
    private RecipeType alchemyArray;
    private RecipeType sacrificial;
    private RecipeType tartarForge;

    public BloodMagicRecipeTypeHandler(PluginExporter exporter) {
        super(exporter);
        this.exporter = exporter;
    }

    public void initialize() {
        ItemFactory itemFactory = new ItemFactory(exporter);
        RecipeTypeFactory recipeTypeFactory = new RecipeTypeFactory(exporter);

        // Use crafting table as default icon
        ItemStack craftingTableStack = ItemUtil.getItemStack(Blocks.crafting_table).get();
        Item defaultIcon = itemFactory.get(craftingTableStack);

        // Blood Altar
        altar = recipeTypeFactory.newBuilder()
                .setId("bloodmagic", "altar")
                .setCategory("Blood Magic")
                .setType("Blood Altar")
                .setIcon(defaultIcon)
                .setShapeless(true)
                .setItemInputDimension(1, 1)
                .setItemOutputDimension(1, 1)
                .build();

        // Alchemy Array
        alchemyArray = recipeTypeFactory.newBuilder()
                .setId("bloodmagic", "alchemy_array")
                .setCategory("Blood Magic")
                .setType("Alchemy Array")
                .setIcon(defaultIcon)
                .setShapeless(true)
                .setItemInputDimension(1, 1)
                .setItemOutputDimension(1, 1)
                .build();

        // Sacrificial
        sacrificial = recipeTypeFactory.newBuilder()
                .setId("bloodmagic", "sacrificial")
                .setCategory("Blood Magic")
                .setType("Sacrificial")
                .setIcon(defaultIcon)
                .setShapeless(true)
                .setItemInputDimension(1, 1)
                .setItemOutputDimension(1, 1)
                .build();

        // Tartaric Forge
        tartarForge = recipeTypeFactory.newBuilder()
                .setId("bloodmagic", "tartar_forge")
                .setCategory("Blood Magic")
                .setType("Tartaric Forge")
                .setIcon(defaultIcon)
                .setShapeless(true)
                .setItemInputDimension(1, 2)
                .setItemOutputDimension(1, 1)
                .build();
    }

    public RecipeType getAltar() {
        return altar;
    }

    public RecipeType getAlchemyArray() {
        return alchemyArray;
    }

    public RecipeType getSacrificial() {
        return sacrificial;
    }

    public RecipeType getTartarForge() {
        return tartarForge;
    }
}
