package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.ShapedRecipeHandler;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonArray;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.*;
import net.minecraftforge.oredict.OreDictionary;
import net.minecraftforge.oredict.ShapedOreRecipe;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** The native shaped loader with source bindings retained; never identify recipes by their picture. */
final class Crafting {
    private final ShapedRecipeHandler handler;
    private final List<IRecipe> sources = new ArrayList<>();
    private final List<ShapedRecipeHandler.CachedShapedRecipe> caches = new ArrayList<>();

    Crafting(ShapedRecipeHandler handler, Iterable<?> recipes) {
        this.handler = handler;
        if (handler.getClass() != ShapedRecipeHandler.class || !handler.arecipes.isEmpty())
            throw new Jobs.Fault("handler_changed", "Shaped source binding requires a fresh native handler");
        for (Object value : recipes) {
            Jobs.checkpoint();
            IRecipe source = (IRecipe) value;
            ShapedRecipeHandler.CachedShapedRecipe cached = source instanceof ShapedRecipes
                    ? handler.new CachedShapedRecipe((ShapedRecipes) source)
                    : source instanceof ShapedOreRecipe ? handler.forgeShapedRecipe((ShapedOreRecipe) source) : null;
            if (cached == null) continue; // Same omission rule as the pinned native loader.
            cached.computeVisuals();
            sources.add(source); caches.add(cached); handler.arecipes.add(cached);
        }
    }

    String sourceType(int index) { return sources.get(index).getClass().getName(); }

    boolean capture(int index, RecipeRow row) {
        if (handler.arecipes.size() != caches.size() || handler.arecipes.get(index) != caches.get(index))
            throw new Jobs.Fault("slot_changed", "Shaped cache/source binding changed");
        IRecipe source = sources.get(index);
        if (!(source instanceof RecipesMapExtending)) return false;
        if (source.getClass() != RecipesMapExtending.class)
            throw new Jobs.Fault("recipe_unsupported", "Map extension overrides native behavior: " + source.getClass().getName());
        RecipesMapExtending map = (RecipesMapExtending) source;
        if (map.recipeWidth != 3 || map.recipeHeight != 3 || map.recipeItems.length != 9)
            throw new Jobs.Fault("slot_changed", "Map extension grid changed");
        PositionedStack[] cells = new PositionedStack[9];
        for (PositionedStack display : caches.get(index).ingredients) {
            int x = display.relx - 25, y = display.rely - 6;
            if (x < 0 || y < 0 || x > 36 || y > 36 || x % 18 != 0 || y % 18 != 0 || cells[y / 18 * 3 + x / 18] != null)
                throw new Jobs.Fault("slot_changed", "Map extension native layout changed");
            cells[y / 18 * 3 + x / 18] = display;
        }
        JsonArray grid = new JsonArray();
        List<ItemStack> centers = new ArrayList<>();
        for (int slot = 0; slot < 9; slot++) {
            ItemStack template = map.recipeItems[slot];
            boolean center = slot == 4;
            if (template == null || template.getItem() != (center ? Items.filled_map : Items.paper)
                    || template.getItemDamage() != (center ? OreDictionary.WILDCARD_VALUE : 0)
                    || cells[slot] == null || cells[slot].items.length == 0)
                throw new Jobs.Fault("slot_changed", "Map extension ingredient changed at " + slot);
            for (ItemStack choice : cells[slot].items) {
                if (choice == null || choice.getItem() != template.getItem()
                        || choice.getItemDamage() == OreDictionary.WILDCARD_VALUE || !center && choice.getItemDamage() != 0)
                    throw new Jobs.Fault("slot_changed", "Map extension displayed candidate differs from native ingredient");
                if (center) { ItemStack copy = choice.copy(); copy.stackSize = 1; centers.add(copy); }
            }
            row.itemInput(cells[slot], slot, 1, false, true, object("kind", "wildcard", "meta", center, "nbt", true));
            grid.add(value(slot));
        }
        // These are pre-onCreated observations, not promises about the eventual world map ID.
        Products output = Products.observe(centers, object("kind", "mapScaling"), input -> pending(map, input), row.facts, 4);
        row.itemOutput(caches.get(index).result, 0, output.output, 10000);
        output.attach(row);
        row.record.add("grid", object("width", 3, "height", 3, "cells", grid, "mirror", true));
        row.record.add("process", object("kind", "mapScaling"));
        return true;
    }

    static ItemStack pending(RecipesMapExtending recipe, ItemStack center) {
        InventoryCrafting inventory = new InventoryCrafting(new Container() {
            @Override public boolean canInteractWith(EntityPlayer player) { return false; }
        }, 3, 3);
        for (int slot = 0; slot < 9; slot++) inventory.setInventorySlotContents(slot,
                slot == 4 ? center.copy() : new ItemStack(Items.paper));
        // matches/getMapData/onCreated can allocate IDs and modify world data. Never call them here.
        return recipe.getCraftingResult(inventory);
    }
}
