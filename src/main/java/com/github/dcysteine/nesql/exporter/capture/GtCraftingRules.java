package com.github.dcysteine.nesql.exporter.capture;

import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import gregtech.api.util.GTShapedRecipe;
import gregtech.api.util.GTShapelessRecipe;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.crafting.IRecipe;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Exact GT declarations only. This is not a result interpreter or a superclass-output fallback. */
final class GtCraftingRules {
    private GtCraftingRules() {}

    static boolean supports(IRecipe recipe) {
        return recipe.getClass() == GTShapedRecipe.class || recipe.getClass() == GTShapelessRecipe.class;
    }

    static JsonObject definition(IRecipe recipe) {
        if (!supports(recipe)) throw new Jobs.Fault("recipe_unsupported", "Unadapted GT crafting subclass: " + recipe.getClass().getName());
        boolean shaped = recipe.getClass() == GTShapedRecipe.class;
        boolean keeping = shaped ? ((GTShapedRecipe)recipe).mKeepingNBT : ((GTShapelessRecipe)recipe).mKeepingNBT;
        boolean removable = shaped ? ((GTShapedRecipe)recipe).mRemovableByGT : ((GTShapelessRecipe)recipe).mRemovableByGT;
        boolean overwrite = !shaped && ((GTShapelessRecipe)recipe).overwriteNBT;
        Enchantment[] enchantments = (Enchantment[])field(recipe, "mEnchantmentsAdded");
        int[] levels = (int[])field(recipe, "mEnchantmentLevelsAdded");
        if ((enchantments == null) != (levels == null)
                || enchantments != null && (enchantments.length > 4096 || levels.length > 4096 || levels.length < enchantments.length))
            throw new Jobs.Fault("recipe_unsupported", "Malformed native GT crafting enchantment declaration");
        JsonArray added = array();
        if (enchantments != null) for (int index = 0; index < enchantments.length; index++) {
            Jobs.checkpoint();
            Enchantment enchantment = enchantments[index];
            if (enchantment == null) throw new Jobs.Fault("recipe_unsupported", "Null native GT crafting enchantment");
            // Retain declaration order, duplicates and signed levels. Native result arithmetic is a separate rule.
            added.add(object("id", enchantment.effectId, "class", enchantment.getClass().getName(), "level", levels[index]));
        }
        JsonArray declaredLevels = array();
        if (levels != null) for (int level : levels) declaredLevels.add(value(level));
        return object("keepingNbt", keeping, "overwriteNbt", overwrite, "removable", removable,
                "enchantments", added, "declaredLevels", declaredLevels);
    }
}
