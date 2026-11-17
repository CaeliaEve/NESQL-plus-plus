package com.github.dcysteine.nesql.exporter.capture;

import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Map;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.ShapedRecipes;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Vanilla's LAST tagged offered cell, not GT's first tagged input.
 * Native per-input projections are linear in candidate count; they are not matching tuples.
 * The audited parent result callback copies only the base and the last tag compound.
 */
final class NativeCraftingNbt {
    private NativeCraftingNbt() {}

    static void attach(ShapedRecipes recipe, RecipeRow row, Map<String, ItemStack> specimens) {
        attachResult(recipe,row,specimens);
        row.record.add("process", object("kind", "craftingNbt"));
    }
    /** Same native result transfer inside a machine with its own selection/power process. */
    static void attachResult(ShapedRecipes recipe, RecipeRow row, Map<String, ItemStack> specimens) {
        ItemStack base = result(recipe, null);
        JsonObject baseStack = stack(row, base);
        JsonArray samples = array(); samples.add(baseStack);
        for (JsonElement input : row.inputs) for (JsonElement choice : input.getAsJsonObject().getAsJsonArray("choices")) {
            Jobs.checkpoint();
            if (samples.size() >= 65537) throw new Jobs.Fault("recipe_limit", "Native NBT projections exceed their budget");
            String id = choice.getAsJsonObject().get("id").getAsString();
            ItemStack specimen = specimens.get(id);
            if (specimen == null) throw new Jobs.Fault("slot_changed", "Native NBT projection lost its input specimen");
            samples.add(stack(row, result(recipe, specimen)));
        }
        row.itemOutput(0, base, 10000);
        row.outputs.get(0).getAsJsonObject().add("change", object("input", 0,
                "action", object("kind", "craftingNbt", "base", baseStack), "samples", samples));
    }

    private static JsonObject stack(RecipeRow row, ItemStack stack) {
        if (stack == null || stack.getItem() == null || stack.stackSize <= 0)
            throw new Jobs.Fault("recipe_unsupported", "Native NBT crafting produced no positive result");
        return object("id", row.facts.item(stack), "amount", Integer.toString(stack.stackSize));
    }

    private static ItemStack result(ShapedRecipes recipe, ItemStack offered) {
        InventoryCrafting inventory = new InventoryCrafting(new Container() {
            @Override public boolean canInteractWith(EntityPlayer player) { return false; }
        }, 3, 3);
        if (offered != null) inventory.setInventorySlotContents(0, offered.copy());
        // Deliberately do not call matches. These are isolated tag-transfer projections,
        // not declarations that a one-cell inventory matches the full recipe.
        return recipe.getCraftingResult(inventory);
    }
}
