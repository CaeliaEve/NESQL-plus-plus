package com.github.dcysteine.nesql.exporter.capture;

import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import cpw.mods.fml.relauncher.ReflectionHelper;
import gregtech.api.recipe.NEIRecipeProperties;
import gregtech.api.recipe.RecipeCategory;
import gregtech.api.recipe.RecipeCategorySetting;
import gregtech.api.recipe.RecipeMetadataKey;
import gregtech.api.util.GTRecipe;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntSupplier;

import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Pinned GT category selection and owned signatures, without constructing a native view/cache. */
final class GtRecipeSelection implements AutoCloseable {
    private final RecipeCategory category;
    private final NEIRecipeProperties properties;
    private final IntSupplier reloadVersion;
    private final int version;
    private final int amperage;
    private final List<GTRecipe> recipes;
    private final List<String> signatures = new ArrayList<>();
    private final Map<RecipeCategory, RecipeCategorySetting> settings = new IdentityHashMap<>();
    private final Map<RecipeCategory, Integer> sizes = new IdentityHashMap<>();
    private boolean closed;

    GtRecipeSelection(RecipeCategory category, IntSupplier reloadVersion) {
        this.category = category;
        this.reloadVersion = reloadVersion;
        this.version = reloadVersion.getAsInt();
        this.amperage = category.recipeMap.getAmperage();
        properties = category.recipeMap.getFrontend().getNEIProperties();
        remember(category);
        for (RecipeCategory candidate : category.recipeMap.getBackend().getRecipeCategoryMap().keySet())
            remember(candidate);
        recipes = read(category, properties);
        for (GTRecipe recipe : recipes) {
            Jobs.checkpoint();
            signatures.add(signature(recipe));
        }
        verify();
    }

    private void remember(RecipeCategory candidate) {
        settings.put(candidate, setting(candidate));
        sizes.put(candidate, category.recipeMap.getBackend().getRecipesByCategory(candidate).size());
    }

    static List<GTRecipe> read(RecipeCategory category, NEIRecipeProperties properties) {
        List<GTRecipe> selected = new ArrayList<>();
        RecipeCategory primary = category.recipeMap.getDefaultRecipeCategory();
        if (category == primary) {
            for (Map.Entry<RecipeCategory, Collection<GTRecipe>> entry :
                    category.recipeMap.getBackend().getRecipeCategoryMap().entrySet()) {
                if (setting(entry.getKey()) == RecipeCategorySetting.MERGE)
                    append(selected, entry.getValue());
            }
            append(selected, category.recipeMap.getBackend().getRecipesByCategory(primary));
        } else if (setting(category) == RecipeCategorySetting.ENABLE) {
            append(selected, category.recipeMap.getBackend().getRecipesByCategory(category));
        }
        selected.sort(properties.comparator); // Stable: exactly the native category/cache order.
        return selected;
    }

    private static void append(List<GTRecipe> selected, Collection<GTRecipe> entries) {
        if (entries.size() > 262144 - selected.size())
            throw new Jobs.Fault("recipe_limit", "GT native category selection exceeds 262144 entries");
        for (GTRecipe recipe : entries) {
            Jobs.checkpoint();
            if (recipe == null) throw new Jobs.Fault("recipe_invalid", "GT category contains a null recipe");
            if (!recipe.mHidden) selected.add(recipe);
        }
    }

    private static RecipeCategorySetting setting(RecipeCategory category) {
        RecipeCategorySetting setting = category.settingSupplier.get();
        if (setting == null) throw new Jobs.Fault("recipe_unready", "GT category setting is unavailable");
        return setting;
    }

    int size() { requireOpen(); return recipes.size(); }

    GTRecipe recipe(int index) {
        requireOpen();
        GTRecipe recipe = recipes.get(index);
        if (!signatures.get(index).equals(signature(recipe)))
            throw new Jobs.Fault("recipe_changed", "GT native recipe changed at index " + index);
        return recipe;
    }

    void verify() {
        requireOpen();
        if (reloadVersion.getAsInt() != version
                || category.recipeMap.getAmperage() != amperage
                || category.recipeMap.getFrontend().getNEIProperties() != properties)
            throw new Jobs.Fault("recipe_changed", "GT native reload or semantic callbacks changed");
        Map<RecipeCategory, Collection<GTRecipe>> current = category.recipeMap.getBackend().getRecipeCategoryMap();
        for (RecipeCategory key : current.keySet()) {
            if (!settings.containsKey(key))
                throw new Jobs.Fault("recipe_changed", "GT native category membership changed");
        }
        for (RecipeCategory key : settings.keySet()) {
            if (setting(key) != settings.get(key)
                    || category.recipeMap.getBackend().getRecipesByCategory(key).size() != sizes.get(key))
                throw new Jobs.Fault("recipe_changed", "GT native category settings or recipe count changed");
        }
    }

    void finishVerification() {
        verify();
        List<GTRecipe> current = read(category, properties);
        if (current.size() != recipes.size())
            throw new Jobs.Fault("recipe_changed", "GT native filtered recipe count changed");
        for (int index = 0; index < current.size(); index++) {
            Jobs.checkpoint();
            if (current.get(index) != recipes.get(index))
                throw new Jobs.Fault("recipe_changed", "GT native recipe membership or order changed");
            recipe(index);
        }
    }

    private void requireOpen() {
        if (closed) throw new Jobs.Fault("recipe_closed", "GT semantic reader is closed");
    }

    @Override public void close() {
        closed = true;
        recipes.clear(); signatures.clear(); settings.clear(); sizes.clear();
    }

    static String signature(GTRecipe recipe) {
        long slots = count(recipe.mInputs) + count(recipe.mOutputs) + count(recipe.mFluidInputs) + count(recipe.mFluidOutputs);
        if (recipe instanceof GTRecipe.GTRecipe_WithAlt) {
            net.minecraft.item.ItemStack[][] alternatives = ((GTRecipe.GTRecipe_WithAlt) recipe).mOreDictAlt;
            slots += alternatives == null ? 0 : count(alternatives);
            if (alternatives != null) for (net.minecraft.item.ItemStack[] choices : alternatives) {
                Jobs.checkpoint();
                if (choices != null) slots += count(choices);
                if (slots > 65536) throw new Jobs.Fault("recipe_limit", "GT total slot/alternative signature exceeds 65536 entries");
            }
        }
        if (slots > 65536) throw new Jobs.Fault("recipe_limit", "GT total semantic slot signature exceeds 65536 entries");
        JsonObject metadata = object();
        if (recipe.getMetadataStorage() != null) {
            if (recipe.getMetadataStorage().getEntries().size() > 4096)
                throw new Jobs.Fault("metadata_limit", "GT recipe exceeds 4096 metadata entries");
            for (Map.Entry<RecipeMetadataKey<?>, Object> entry : recipe.getMetadataStorage().getEntries()) {
                Jobs.checkpoint();
                String name = ReflectionHelper.getPrivateValue(RecipeMetadataKey.class, entry.getKey(), "identifier");
                Class<?> type = ReflectionHelper.getPrivateValue(RecipeMetadataKey.class, entry.getKey(), "clazz");
                metadata.add(type.getName() + "/" + name, Values.snapshot(entry.getValue()));
            }
        }
        Object special = recipe.mSpecialItems;
        JsonObject encodedSpecial = special instanceof tectech.recipe.EyeOfHarmonyRecipe
                ? Harmony.snapshot((tectech.recipe.EyeOfHarmonyRecipe) special)
                : special == null ? null : Values.snapshot(special);
        JsonObject row = object("type", recipe.getClass().getName(), "inputs", array(recipe.mInputs),
                "outputs", array(recipe.mOutputs), "fluidInputs", array(recipe.mFluidInputs),
                "fluidOutputs", array(recipe.mFluidOutputs), "special", encodedSpecial,
                "duration", recipe.mDuration, "energy", recipe.mEUt, "specialValue", recipe.mSpecialValue,
                "enabled", recipe.mEnabled, "hidden", recipe.mHidden, "fake", recipe.mFakeRecipe,
                "nbtSensitive", recipe.isNBTSensitive,
                "inputChances", GtProbability.chances(recipe.mInputChances),
                "outputChances", GtProbability.chances(recipe.mOutputChances),
                "fluidInputChances", GtProbability.chances(recipe.mFluidInputChances),
                "fluidOutputChances", GtProbability.chances(recipe.mFluidOutputChances),
                "fluidAlternatives", GtProbability.alternatives(recipe.mAltFluidInputs, Values::snapshot),
                "probabilityPolicy", "gt-native-5.09.54.265-v1",
                "description", recipe.getNeiDesc() == null ? null : Values.snapshot(recipe.getNeiDesc()),
                "metadata", metadata);
        if (recipe instanceof GTRecipe.GTRecipe_WithAlt) {
            ItemAlternatives.add(row, ((GTRecipe.GTRecipe_WithAlt) recipe).mOreDictAlt);
        }
        byte[] bytes = CanonicalJson.bytes(row);
        if (bytes.length > 4 * 1024 * 1024)
            throw new Jobs.Fault("recipe_limit", "GT native recipe signature exceeds 4 MiB");
        return CanonicalJson.digest(bytes);
    }

    private static JsonArray array(Object[] values) {
        if (values == null || values.length > 65536)
            throw new Jobs.Fault("recipe_limit", "GT native slot array is null or exceeds 65536");
        JsonArray result = new JsonArray();
        for (Object element : values) {
            Jobs.checkpoint();
            result.add(element == null ? value(null) : Values.snapshot(element));
        }
        return result;
    }

    private static long count(Object[] values) {
        if (values == null || values.length > 65536)
            throw new Jobs.Fault("recipe_limit", "GT native semantic array is null or exceeds 65536 entries");
        return values.length;
    }

    private static final class ItemAlternatives {
        static void add(JsonObject row, net.minecraft.item.ItemStack[][] alternatives) {
            if (alternatives == null) { row.add("alternatives", value(null)); return; }
            if (alternatives.length > 65536)
                throw new Jobs.Fault("recipe_limit", "GT alternatives exceed 65536 slots");
            JsonArray encoded = new JsonArray();
            for (net.minecraft.item.ItemStack[] items : alternatives)
                encoded.add(items == null ? value(null) : array(items));
            row.add("alternatives", encoded);
        }
    }
}
