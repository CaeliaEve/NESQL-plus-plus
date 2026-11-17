package com.github.dcysteine.nesql.exporter.capture;

import com.github.dcysteine.nesql.exporter.source.Chance;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import gregtech.api.util.GTRecipe;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import java.util.function.Function;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Native facts, not independent per-unit rolls or a complete machine execution policy. */
final class GtProbability {
    static final String ARCHIVE = "b969e11625a0298416a75b6e39703da795233edb71cb66f25fb9bbf654d75528";
    private GtProbability() {}

    static boolean referenceOnly(GTRecipe recipe, boolean specialized) {
        return !recipe.mEnabled || recipe.mFakeRecipe && !specialized;
    }

    static JsonObject capture(GTRecipe recipe, Facts facts, boolean specialized) {
        return object("archiveSha256", ARCHIVE, "nativeRecipeClass", recipe.getClass().getName(),
                "nbtSensitive", recipe.isNBTSensitive, "fake", recipe.mFakeRecipe, "enabled", recipe.mEnabled,
                "itemInputs", stacks(recipe.mInputs, item -> item(item, facts)),
                "itemOutputs", stacks(recipe.mOutputs, item -> item(item, facts)),
                "fluidInputs", stacks(recipe.mFluidInputs, fluid -> fluid(fluid, facts)),
                "fluidOutputs", stacks(recipe.mFluidOutputs, fluid -> fluid(fluid, facts)),
                "itemAlternatives", recipe instanceof GTRecipe.GTRecipe_WithAlt
                        ? alternatives(((GTRecipe.GTRecipe_WithAlt) recipe).mOreDictAlt, item -> item(item, facts)) : null,
                "fluidAlternatives", alternatives(recipe.mAltFluidInputs, fluid -> fluid(fluid, facts)),
                "inputChances", chances(recipe.mInputChances), "outputChances", chances(recipe.mOutputChances),
                "fluidInputChances", chances(recipe.mFluidInputChances),
                "fluidOutputChances", chances(recipe.mFluidOutputChances),
                "policy", object("itemConsumption", "nativeMatchingGroups", "fluidConsumption", "nativeSlots",
                        "sampler", "gtIntegralChancedMultiplier",
                        "execution", referenceOnly(recipe, specialized) ? "referenceOnly" : "unresolvedMachineContext",
                        "projection", specialized ? "specializedProcess" : "nativeSlots"));
    }

    static JsonElement chances(int[] values) {
        if (values == null) return value(null);
        budget(values.length);
        JsonArray result = array();
        for (int chance : values) result.add(value(chance));
        return result;
    }

    static <T> JsonElement alternatives(T[][] values, Function<T, JsonElement> encode) {
        if (values == null) return value(null);
        budget(values.length);
        JsonArray result = array();
        int total = values.length;
        for (T[] slot : values) {
            Jobs.checkpoint();
            if (slot != null) {
                total += slot.length;
                budget(total);
            }
            result.add(slot == null ? value(null) : stacks(slot, encode));
        }
        return result;
    }

    private static <T> JsonArray stacks(T[] values, Function<T, JsonElement> encode) {
        if (values == null) throw new Jobs.Fault("recipe_invalid", "GT native quantity array is null");
        budget(values.length);
        JsonArray result = array();
        for (T stack : values) {
            Jobs.checkpoint();
            result.add(stack == null ? value(null) : encode.apply(stack));
        }
        return result;
    }

    private static JsonObject item(ItemStack item, Facts facts) {
        return object("id", facts.item(item), "amount", Integer.toString(item.stackSize));
    }

    private static JsonObject fluid(FluidStack fluid, Facts facts) {
        if (fluid.getFluid() == null) throw new Jobs.Fault("recipe_invalid", "GT native fluid has no registry identity");
        return object("id", facts.fluid(fluid), "amount", Integer.toString(fluid.amount));
    }

    static void validateProjection(GTRecipe recipe) {
        for (int[] values : new int[][] {recipe.mInputChances, recipe.mOutputChances,
                recipe.mFluidInputChances, recipe.mFluidOutputChances}) {
            if (values == null) continue;
            budget(values.length);
            for (int chance : values)
                if (chance < 0 || chance > 10000)
                    throw new Jobs.Fault("invalid_chance", "Malformed raw GT chance cannot form an executable projection: " + chance);
        }
    }

    static void inputChance(JsonObject input, int chance) {
        for (JsonElement value : input.getAsJsonArray("choices")) {
            JsonObject choice = value.getAsJsonObject();
            // Native zero quantities already carry their explicit presence/cost policy.
            if (!"keep".equals(choice.getAsJsonObject("consume").get("kind").getAsString()))
                choice.add("consume", object("kind", "chance", "chance", Chance.of(chance, 10000)));
        }
    }

    private static void budget(int size) {
        if (size > 65536) throw new Jobs.Fault("recipe_limit", "GT native facts exceed 65536 entries");
    }
}
