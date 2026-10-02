package com.github.dcysteine.nesql.exporter.capture;

import com.google.gson.JsonObject;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;
import cpw.mods.fml.relauncher.ReflectionHelper;
import gregtech.api.objects.ItemData;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.api.util.GTRecipe;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemDoor;
import net.minecraft.item.ItemStack;
import java.util.Map;
import java.util.function.Function;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Evidence for obsolete Malisis originals which registered items cannot match. */
final class Replaced {
    static JsonObject inspect(GTRecipe recipe, String category) {
        if (!category.equals("gt.recipe.fluidextractor/gt.recipe.category.fluid_extractor_recycling")
                && !category.equals("gt.recipe.macerator/gt.recipe.category.macerator_recycling")
                && !category.equals("gt.recipe.arcfurnace/gt.recipe.category.arc_furnace_recycling")) return null;
        if (candidate(recipe) == null) return null;
        Map<String, ModContainer> mods = Loader.instance().getIndexedModList();
        if (!version(mods.get("malisisdoors"), "1.18.2-GTNH") || !version(mods.get("gregtech"), "5.09.51.482")) return null;
        Class<?> tool = MagicApi.type("net.malisis.core.util.replacement.ReplacementTool");
        return inspect(recipe, item -> (Item) MagicApi.invoke(tool, null, "originalItem", new Class<?>[]{Item.class}, item));
    }

    @SuppressWarnings("unchecked")
    static JsonObject inspect(GTRecipe recipe, Function<Item, Item> original) {
        ItemStack input = candidate(recipe);
        if (input == null) return null;
        Item old = input.getItem();
        String replacement = null;
        for (String name : new String[]{"minecraft:wooden_door", "minecraft:iron_door"}) {
            Item registered = (Item) Item.itemRegistry.getObject(name);
            if (registered != null && original.apply(registered) == old) {
                if (replacement != null) return null;
                replacement = name;
            }
        }
        if (replacement == null) return null;

        // Do not construct RecipeItemInput: normalization can mutate the global target's tags.
        if (GTOreDictUnificator.getAssociation(input) != null) return null;
        GTRecipe.RecipeItemInput[] cached = ReflectionHelper.getPrivateValue(GTRecipe.class, recipe, "mergedInputCache");
        if (cached != null) {
            ItemStack[] atCache = ReflectionHelper.getPrivateValue(GTRecipe.class, recipe, "inputsAtCacheTime");
            if (atCache != recipe.mInputs || cached.length != 1 || cached[0] == null
                    || !target(cached[0].unifiedStack, old) || cached[0].inputAmount <= 0) return null;
        }

        int registeredCount = 0;
        for (Object registered : Item.itemRegistry) {
            if (registered == old) return null;
            registeredCount++;
        }
        Map<String, ItemStack> names = ReflectionHelper.getPrivateValue(GTOreDictUnificator.class, null, "sName2StackMap");
        Map<ItemStack, ItemData> associations = ReflectionHelper.getPrivateValue(GTOreDictUnificator.class, null, "sItemStack2DataMap");
        // Include targets from unregistered/blacklisted associations too. This deliberately
        // over-approximates reachable inputs, including lazily resolved named targets.
        for (ItemStack stack : names.values()) if (target(stack, old)) return null;
        for (ItemData data : associations.values()) if (data != null && target(data.mUnificationTarget, old)) return null;
        return object("reason", "replaced_input_unreachable", "scope", "registered_items",
                "replacement", replacement, "sourceClass", old.getClass().getName(),
                "meta", Items.feather.getDamage(input), "amount", Integer.toString(input.stackSize),
                "registeredItems", registeredCount, "namedTargets", names.size(), "associations", associations.size(),
                "cachedInput", cached != null);
    }

    private static ItemStack candidate(GTRecipe recipe) {
        if (recipe == null || recipe.getClass() != GTRecipe.class || recipe.mFakeRecipe || recipe.mSpecialItems != null
                || recipe.mInputs == null || recipe.mInputs.length != 1) return null;
        ItemStack input = recipe.mInputs[0];
        return input != null && input.stackSize > 0 && input.getItem() != null && input.getItem().getClass() == ItemDoor.class
                && Item.itemRegistry.getNameForObject(input.getItem()) == null ? input : null;
    }

    private static boolean target(ItemStack stack, Item item) { return stack != null && stack.getItem() == item; }
    private static boolean version(ModContainer mod, String expected) { return mod != null && expected.equals(mod.getVersion()); }
}
