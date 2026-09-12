package com.github.dcysteine.nesql.exporter.capture;

import com.github.dcysteine.nesql.exporter.source.Identity;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import net.minecraft.item.ItemStack;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.common.lib.crafting.ThaumcraftCraftingManager;

import java.util.Map;
import java.util.TreeMap;
import java.util.function.BiConsumer;

import static com.github.dcysteine.nesql.exporter.source.Json.object;

/** Reads native aspect lists without exporting their empty-array sentinel. */
final class Aspects {
    private Aspects() {}

    static JsonElement item(ItemStack item) {
        ItemStack copy = item.copy();
        AspectList base = ThaumcraftCraftingManager.getObjectTags(copy);
        AspectList result = ThaumcraftCraftingManager.getBonusTags(copy, base == null ? null : base.copy());
        return result == null ? JsonNull.INSTANCE : amounts(result);
    }

    static JsonArray amounts(AspectList list) {
        TreeMap<String, Integer> values = new TreeMap<>();
        each(list, (aspect, amount) -> {
            if (amount < 0) throw new Jobs.Fault("aspect_amount", "Negative aspect quantity for '" + aspect.getTag() + "': " + amount);
            if (values.putIfAbsent(id(aspect), amount) != null) throw new Jobs.Fault("aspect_duplicate", "Duplicate aspect identity: " + aspect.getTag());
        });
        JsonArray rows = new JsonArray();
        for (Map.Entry<String, Integer> entry : values.entrySet()) rows.add(object("aspect", entry.getKey(), "amount", Integer.toString(entry.getValue())));
        return rows;
    }

    static JsonObject knowledge(AspectList list) {
        JsonObject result = new JsonObject();
        each(list, (aspect, amount) -> result.addProperty(aspect.getTag(), amount));
        return result;
    }

    private static void each(AspectList list, BiConsumer<Aspect, Integer> action) {
        if (list == null) return;
        for (Aspect aspect : list.getAspects()) {
            int amount = list.getAmount(aspect);
            // TC4 returns [null] for an empty list. Its copy/add/merge methods can
            // materialize that sentinel as a real null -> 0 map entry, even beside
            // valid aspects. Keep registered zero quantities and reject unknown costs.
            if (aspect == null && amount == 0) continue;
            if (aspect == null) throw new Jobs.Fault("aspect_reference", "Null aspect has a non-zero quantity: " + amount);
            action.accept(aspect, amount);
        }
    }

    static String id(Aspect aspect) {
        if (aspect == null) throw new Jobs.Fault("aspect_reference", "Null aspect reference");
        String tag = aspect.getTag();
        Aspect registered = Aspect.getAspect(tag);
        if (registered != aspect) throw new Jobs.Fault("aspect_reference", "Aspect '" + tag + "' from " + aspect.getClass().getName()
                + (registered == null ? " is not registered" : " differs from the registered " + registered.getClass().getName()));
        return Identity.origin("aspect", source(aspect));
    }

    static JsonObject source(Aspect aspect) {
        return object("owner", "Thaumcraft", "handler", Aspect.class.getName(), "key", aspect.getTag());
    }
}
