package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.common.base.Optional;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Registered AE2 recipes. Name presses preempt this registry; see the Inscriber process contract. */
final class InscriberRecipes implements RegistryRecipes {
    private static final String HANDLER = "appeng.integration.modules.NEIHelpers.NEIInscriberRecipeHandler";
    private static final String RECIPE = "appeng.core.features.registries.entries.InscriberRecipe";
    private final TemplateRecipeHandler handler;
    private final List<Entry> entries = new ArrayList<>();
    private final ItemStack namePress;

    static boolean supports(ICraftingHandler handler) { return handler.getClass().getName().equals(HANDLER); }
    InscriberRecipes(TemplateRecipeHandler handler) { this(handler, api()); }
    private InscriberRecipes(TemplateRecipeHandler handler, Object api) {
        this(handler, registry(api), namePress(api));
    }
    private static Object api() {
        version("appliedenergistics2", "rv3-beta-695-GTNH");
        return invoke(type("appeng.api.AEApi"), null, "instance", new Class<?>[0]);
    }
    private static Collection<?> registry(Object api) {
        Object registries = call(api, "registries"), registry = call(registries, "inscriber");
        return (Collection<?>) call(registry, "getRecipes");
    }
    private static ItemStack namePress(Object api) {
        Object definition = call(call(call(api, "definitions"), "materials"), "namePress");
        if (!definition.getClass().getName().equals("appeng.core.features.DamagedItemDefinition"))
            throw fault("Unknown name press identity matcher");
        return (ItemStack) ((Optional<?>) invoke(definition.getClass(), definition, "maybeStack", new Class<?>[]{int.class}, 1)).orNull();
    }
    InscriberRecipes(TemplateRecipeHandler handler, Collection<?> recipes, ItemStack namePress) {
        if (!supports(handler) || recipes == null || recipes.size() > 262144) throw fault("Invalid inscriber registry");
        this.handler = handler;
        this.namePress = namePress == null ? null : copy(namePress, true);
        for (Object recipe : recipes) {
            Jobs.checkpoint();
            if (!type(RECIPE).isInstance(recipe)) throw fault("Unknown inscriber recipe getters");
            entries.add(new Entry(recipe));
        }
    }
    public int size() { return entries.size(); }
    public boolean capture(int index, RecipeRow row) {
        Entry entry = entries.get(index);
        // Every matching plate arrangement is claimed by the preceding rename branch.
        if (isName(entry.top) && (entry.bottom == null || isName(entry.bottom))) return false;
        row.record.add("process", object("kind", "inscriber", "mode", entry.mode,
                "top", entry.top == null ? null : row.facts.item(entry.top),
                "bottom", entry.bottom == null ? null : row.facts.item(entry.bottom),
                "namePress", namePress == null ? null : row.facts.item(namePress)));
        boolean keep = entry.mode.equals("inscribe");
        if (entry.top != null) {
            input(row, 0, Collections.singletonList(entry.top), 40, 5, keep);
            if (entry.bottom != null) input(row, 1, Collections.singletonList(entry.bottom), 40, 51, keep);
        }
        // Native top-absent precedence ignores the declared bottom and requires an empty plate slot.
        input(row, 2, entry.inputs, 58, 28, false);
        row.itemOutput(new PositionedStack(entry.output.copy(), 108, 29, false), 0, entry.output.copy(), 10000);
        Object projection = TinkerRecipes.construct(type(RECIPE),
                new Class<?>[]{Collection.class, ItemStack.class, ItemStack.class, ItemStack.class, type("appeng.api.features.InscriberProcessType")},
                copies(entry.inputs), entry.output.copy(), entry.top == null ? null : entry.top.copy(),
                entry.bottom == null ? null : entry.bottom.copy(), entry.nativeMode);
        TemplateRecipeHandler.CachedRecipe cache;
        try {
            java.lang.reflect.Constructor<?> constructor = type(HANDLER + "$CachedInscriberRecipe").getDeclaredConstructor(handler.getClass(), type("appeng.api.features.IInscriberRecipe"));
            constructor.setAccessible(true);
            cache = (TemplateRecipeHandler.CachedRecipe) constructor.newInstance(handler, projection);
        } catch (ReflectiveOperationException error) {
            Jobs.Fault failure = fault("Cannot build native inscriber view"); failure.initCause(error); throw failure;
        }
        handler.arecipes.clear(); handler.arecipes.add(cache);
        return true;
    }
    private boolean isName(ItemStack item) {
        return item != null && namePress != null && item.getItem() == namePress.getItem() && item.getItemDamage() == namePress.getItemDamage();
    }
    private static void input(RecipeRow row, int slot, List<ItemStack> items, int x, int y, boolean keep) {
        List<RecipeRow.Ingredient> choices = new ArrayList<>();
        for (ItemStack item : items) choices.add(new RecipeRow.Ingredient(item.copy(), 1, keep, object("kind", "ae")));
        row.itemInput(new PositionedStack(copies(items), x, y, false), slot, choices, false);
    }
    private static List<ItemStack> copies(List<ItemStack> items) {
        List<ItemStack> result = new ArrayList<>(); for (ItemStack item : items) result.add(item.copy()); return result;
    }
    private static final class Entry {
        final List<ItemStack> inputs = new ArrayList<>();
        final ItemStack top, bottom, output;
        final Object nativeMode;
        final String mode;
        Entry(Object recipe) {
            Collection<?> raw = (Collection<?>) call(recipe, "getInputs");
            if (raw == null || raw.isEmpty() || raw.size() > 65536) throw fault("Invalid inscriber alternatives");
            for (Object item : raw) inputs.add(copy((ItemStack) item, true));
            ItemStack a = (ItemStack) ((Optional<?>) call(recipe, "getTopOptional")).orNull();
            ItemStack b = (ItemStack) ((Optional<?>) call(recipe, "getBottomOptional")).orNull();
            top = a == null ? null : copy(a, true); bottom = b == null ? null : copy(b, true);
            output = copy((ItemStack) call(recipe, "getOutput"), false);
            nativeMode = call(recipe, "getProcessType");
            if (!(nativeMode instanceof Enum)) throw fault("Missing inscriber process mode");
            mode = ((Enum<?>) nativeMode).name().toLowerCase(Locale.ROOT);
            if (!mode.equals("press") && !mode.equals("inscribe")) throw fault("Unknown inscriber process mode");
        }
    }
    private static ItemStack copy(ItemStack item, boolean predicate) {
        if (item == null || item.getItem() == null || !predicate && item.stackSize <= 0) throw fault("Invalid inscriber item");
        if (predicate) {
            try {
                if (item.getItem().getClass().getMethod("getDamage", ItemStack.class).getDeclaringClass() != Item.class)
                    throw fault("Inscriber predicate has a custom metadata getter");
            } catch (NoSuchMethodException error) { throw fault("Missing native metadata getter"); }
            if (item.getTagCompound() != null && item.getTagCompound().getClass() != NBTTagCompound.class)
                throw fault("Inscriber predicate uses identity-dependent NBT");
            rejectNaN(TypedNbt.encode(item.getTagCompound()));
        }
        ItemStack copy = item.copy(); if (predicate) copy.stackSize = 1; return copy;
    }
    private static void rejectNaN(JsonElement value) {
        if (value == null || value.isJsonNull()) return;
        JsonObject object = value.getAsJsonObject();
        String kind = object.get("type").getAsString();
        if (kind.equals("float") && Float.isNaN(Float.intBitsToFloat((int) Long.parseUnsignedLong(object.get("value").getAsString(), 16)))
                || kind.equals("double") && Double.isNaN(Double.longBitsToDouble(Long.parseUnsignedLong(object.get("value").getAsString(), 16))))
            throw fault("Inscriber NaN predicate depends on object identity");
        if (kind.equals("list")) for (JsonElement child : object.getAsJsonArray("value")) rejectNaN(child);
        if (kind.equals("compound")) for (Map.Entry<String, JsonElement> child : object.getAsJsonObject("value").entrySet()) rejectNaN(child.getValue());
    }
    private static Object call(Object target, String name) { return invoke(target.getClass(), target, name, new Class<?>[0]); }
    private static Jobs.Fault fault(String message) { return new Jobs.Fault("recipe_unsupported", message); }
}
