package com.github.dcysteine.nesql.exporter.plugin.botania;

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
 * Handles setup of Botania recipe types.
 */
public class BotaniaRecipeTypeHandler extends PluginHelper {
    private final PluginExporter exporter;

    private RecipeType manaPool;
    private RecipeType pureDaisy;
    private RecipeType petalApothecary;
    private RecipeType runeAltar;
    private RecipeType elvenTrade;
    private RecipeType brewRecipe;
    private RecipeType terraPlate;

    public BotaniaRecipeTypeHandler(PluginExporter exporter) {
        super(exporter);
        this.exporter = exporter;
    }

    public void initialize() {
        ItemFactory itemFactory = new ItemFactory(exporter);
        RecipeTypeFactory recipeTypeFactory = new RecipeTypeFactory(exporter);

        // Use crafting table as default icon
        ItemStack craftingTableStack = ItemUtil.getItemStack(Blocks.crafting_table).get();
        Item defaultIcon = itemFactory.get(craftingTableStack);

        // Mana Pool Recipes
        manaPool = recipeTypeFactory.newBuilder()
                .setId("botania", "mana_pool")
                .setCategory("Botania")
                .setType("Mana Pool")
                .setIcon(defaultIcon)
                .setShapeless(true)
                .setItemInputDimension(1, 1)
                .setItemOutputDimension(1, 1)
                .build();

        // Pure Daisy (Conversion)
        pureDaisy = recipeTypeFactory.newBuilder()
                .setId("botania", "pure_daisy")
                .setCategory("Botania")
                .setType("Pure Daisy")
                .setIcon(defaultIcon)
                .setShapeless(true)
                .setItemInputDimension(1, 1)
                .setItemOutputDimension(1, 1)
                .build();

        // Petal Apothecary
        petalApothecary = recipeTypeFactory.newBuilder()
                .setId("botania", "petal_apothecary")
                .setCategory("Botania")
                .setType("Petal Apothecary")
                .setIcon(defaultIcon)
                .setShapeless(true)
                .setItemInputDimension(1, 9)
                .setItemOutputDimension(1, 1)
                .build();

        // Rune Altar
        runeAltar = recipeTypeFactory.newBuilder()
                .setId("botania", "rune_altar")
                .setCategory("Botania")
                .setType("Rune Altar")
                .setIcon(defaultIcon)
                .setShapeless(false)
                .setItemInputDimension(3, 3)
                .setItemOutputDimension(1, 1)
                .build();

        // Elven Trade
        elvenTrade = recipeTypeFactory.newBuilder()
                .setId("botania", "elven_trade")
                .setCategory("Botania")
                .setType("Elven Trade")
                .setIcon(defaultIcon)
                .setShapeless(true)
                .setItemInputDimension(1, 9)
                .setItemOutputDimension(1, 6)
                .build();

        // Brew Recipe
        brewRecipe = recipeTypeFactory.newBuilder()
                .setId("botania", "brew")
                .setCategory("Botania")
                .setType("Brew Recipe")
                .setIcon(defaultIcon)
                .setShapeless(true)
                .setItemInputDimension(1, 9)
                .setItemOutputDimension(0, 0)
                .build();

        // Terra Plate
        terraPlate = recipeTypeFactory.newBuilder()
                .setId("botania", "terra_plate")
                .setCategory("Botania")
                .setType("Terra Plate")
                .setIcon(defaultIcon)
                .setShapeless(false)
                .setItemInputDimension(3, 3)
                .setItemOutputDimension(1, 1)
                .build();
    }

    public RecipeType getManaPool() {
        return manaPool;
    }

    public RecipeType getPureDaisy() {
        return pureDaisy;
    }

    public RecipeType getPetalApothecary() {
        return petalApothecary;
    }

    public RecipeType getRuneAltar() {
        return runeAltar;
    }

    public RecipeType getElvenTrade() {
        return elvenTrade;
    }

    public RecipeType getBrewRecipe() {
        return brewRecipe;
    }

    public RecipeType getTerraPlate() {
        return terraPlate;
    }
}
