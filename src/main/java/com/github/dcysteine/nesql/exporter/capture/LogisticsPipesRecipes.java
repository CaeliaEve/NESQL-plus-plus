package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import com.google.gson.JsonObject;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.field;
import static com.github.dcysteine.nesql.exporter.source.Json.object;

/** LogisticsPipes soldering station. Dynamic result callbacks are rejected explicitly. */
final class LogisticsPipesRecipes implements RegistryRecipes {
    private static final String HANDLER = "logisticspipes.nei.NEISolderingStationRecipeManager";
    private static final String REGISTRY = "logisticspipes.recipes.SolderingStationRecipes";
    private static final String RECORD = REGISTRY + "$SolderingStationRecipe";
    private final TemplateRecipeHandler handler;
    private final List<Entry> recipes;

    private static final class Entry {
        final ItemStack[] source;
        final ItemStack result;
        final Object callback;
        Entry(ItemStack[] source, ItemStack result, Object callback) {
            this.source = source; this.result = result; this.callback = callback;
        }
    }

    static boolean supports(ICraftingHandler value) { return supportsClass(value.getClass().getName()); }
    static boolean supportsClass(String name) { return HANDLER.equals(name); }

    LogisticsPipesRecipes(TemplateRecipeHandler handler) {
        if (!supports(handler)) throw fault("Unknown LogisticsPipes soldering handler");
        this.handler = handler;
        Object raw = MagicApi.invoke(MagicApi.type(REGISTRY), null, "getRecipes", new Class<?>[0]);
        if (!(raw instanceof List) || ((List<?>) raw).size() > 65536) throw fault("Invalid LogisticsPipes soldering registry");
        recipes = new ArrayList<>();
        for (Object value : (List<?>) raw) {
            if (value == null || !RECORD.equals(value.getClass().getName())) throw fault("Unknown LogisticsPipes soldering recipe");
            ItemStack[] source = (ItemStack[]) field(value, "source");
            ItemStack result = (ItemStack) field(value, "result");
            Object callback = field(value, "handler");
            if (source == null || source.length != 9 || result == null || result.getItem() == null) throw fault("Invalid LogisticsPipes soldering recipe shape");
            for (ItemStack item : source) if (item != null && (item.getItem() == null || item.stackSize <= 0)) throw fault("Invalid LogisticsPipes soldering ingredient");
            recipes.add(new Entry(copy(source), result.copy(), callback));
        }
        if (recipes.isEmpty()) throw fault("LogisticsPipes soldering registry is empty");
    }

    public int size() { return recipes.size(); }

    public boolean capture(int index, RecipeRow row) {
        Entry recipe = recipes.get(index);
        if (recipe.callback != null) return false;
        ItemStack[] source = recipe.source;
        ItemStack result = recipe.result.copy();
        int slot = 0;
        for (int y = 0; y < 3; y++) for (int x = 0; x < 3; x++) {
            ItemStack item = source[y * 3 + x];
            if (item == null) continue;
            PositionedStack display = new PositionedStack(item.copy(), 39 + y * 18, 6 + x * 18, false);
            display.setMaxSize(1);
            row.itemInput(display, slot++, 1, false, false, object("kind", "exact", "meta", false, "nbt", true));
        }
        PositionedStack solder = new PositionedStack(new ItemStack(Items.iron_ingot), 102, 6, false);
        solder.setMaxSize(1);
        row.itemInput(solder, slot++, 1, false, false, object("kind", "exact", "meta", false, "nbt", true));
        row.itemOutput(new PositionedStack(result.copy(), 136, 36, false), 0, result, 10000);
        row.property("logisticspipes:heat", "Native soldering station requires heated solder", true);
        return true;
    }

    public JsonObject exclusion(int index) {
        Entry recipe = recipes.get(index);
        if (recipe.callback == null) return null;
        return object("reason", "dynamic_result_callback", "scope", "registered_entry",
                "nativeClass", RECORD, "index", index,
                "callbackClass", recipe.callback.getClass().getName(),
                "condition", "ICraftingResultHandler.handleCrafting mutates the result at runtime and is not invoked during capture");
    }

    private static ItemStack[] copy(ItemStack[] source) {
        ItemStack[] result = new ItemStack[source.length];
        for (int i = 0; i < source.length; i++) result[i] = source[i] == null ? null : source[i].copy();
        return result;
    }

    private static Jobs.Fault fault(String message) { return new Jobs.Fault("recipe_unsupported", message); }
}
