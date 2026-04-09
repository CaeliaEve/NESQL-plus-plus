package com.github.dcysteine.nesql.exporter.plugin.avaritia;

import com.github.dcysteine.nesql.exporter.plugin.PluginExporter;
import com.github.dcysteine.nesql.exporter.plugin.PluginHelper;
import com.github.dcysteine.nesql.exporter.plugin.base.factory.ItemFactory;
import com.github.dcysteine.nesql.exporter.plugin.base.factory.RecipeTypeFactory;
import com.github.dcysteine.nesql.exporter.util.ItemUtil;
import com.github.dcysteine.nesql.sql.base.item.Item;
import com.github.dcysteine.nesql.sql.base.recipe.RecipeType;
import morphclock.api.ExtremeCraftingManager;
import morphclock.api.IRecipe;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;

import java.util.Map;

/**
 * Handles setup of Avaritia recipe types.
 */
public class AvaritiaRecipeTypeHandler extends PluginHelper {
    private final PluginExporter exporter;

    private RecipeType extremeCrafting;

    public AvaritiaRecipeTypeHandler(PluginExporter exporter) {
        super(exporter);
        this.exporter = exporter;
    }

    public void initialize() {
        ItemFactory itemFactory = new ItemFactory(exporter);
        RecipeTypeFactory recipeTypeFactory = new RecipeTypeFactory(exporter);

        // Try to get the Extreme Crafting crystal as icon
        Item icon = null;
        try {
            Map<Object, IRecipe> recipes = ExtremeCraftingManager.getInstance().getRecipes();
            if (recipes != null && !recipes.isEmpty()) {
                ItemStack firstStack = recipes.values().iterator().next().getRecipeOutput();
                if (firstStack != null) {
                    icon = itemFactory.get(firstStack);
                }
            }
        } catch (Exception e) {
            // Ignore errors, icon will remain null
        }

        // Fallback to a generic item if icon is null
        if (icon == null) {
            try {
                ItemStack craftingTable = ItemUtil.getItemStack(net.minecraft.init.Blocks.crafting_table).get();
                icon = itemFactory.get(craftingTable);
            } catch (Exception e) {
                // Ignore
            }
        }

        // Extreme Crafting (9x9)
        extremeCrafting = recipeTypeFactory.newBuilder()
                .setId("avaritia", "extreme_crafting")
                .setCategory("Avaritia")
                .setType("Extreme Crafting")
                .setIcon(icon != null ? icon : itemFactory.get(ItemUtil.getItemStack(Blocks.crafting_table).get()))
                .setShapeless(false)
                .setItemInputDimension(9, 9)
                .setItemOutputDimension(1, 1)
                .build();
    }

    public RecipeType getExtremeCrafting() {
        return extremeCrafting;
    }
}
