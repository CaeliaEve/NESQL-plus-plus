package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** ProjectBlue's NEI handler materializes concrete control-panel recipes through its own callbacks. */
final class ProjectBlueRecipes implements RegistryRecipes {
    private static final String HANDLER = "gcewing.projectblue.nei.NEIRecipeHandler";
    private final TemplateRecipeHandler handler;
    private final List<TemplateRecipeHandler.CachedRecipe> entries = new ArrayList<>();

    static boolean supports(ICraftingHandler value) { return supportsClass(value.getClass().getName()); }
    static boolean supportsClass(String name) { return HANDLER.equals(name); }

    ProjectBlueRecipes(TemplateRecipeHandler handler) {
        if (!supports(handler)) throw fault("Unknown ProjectBlue handler");
        this.handler = handler;
        Set<String> seen = new HashSet<>();
        for (Object value : Item.itemRegistry) {
            Jobs.checkpoint();
            if (!(value instanceof Item)) continue;
            Item item = (Item) value;
            List<ItemStack> variants = new ArrayList<>();
            item.getSubItems(item, null, variants);
            if (variants.isEmpty()) variants.add(new ItemStack(item, 1, 0));
            if (variants.size() > 4096) throw fault("ProjectBlue item has too many variants");
            for (ItemStack variant : variants) {
                if (variant == null || variant.getItem() == null) continue;
                handler.arecipes.clear();
                handler.loadCraftingRecipes(variant.copy());
                for (Object cached : handler.arecipes) {
                    if (!(cached instanceof TemplateRecipeHandler.CachedRecipe)) throw fault("ProjectBlue NEI cache type changed");
                    TemplateRecipeHandler.CachedRecipe recipe = (TemplateRecipeHandler.CachedRecipe) cached;
                    String key = signature(recipe);
                    if (seen.add(key)) entries.add(recipe);
                    if (entries.size() > 65536) throw fault("ProjectBlue recipe budget exceeded");
                }
            }
        }
        handler.arecipes.clear();
        if (entries.isEmpty()) throw fault("ProjectBlue NEI registry has no concrete recipes");
    }

    public int size() { return entries.size(); }

    public boolean capture(int index, RecipeRow row) {
        TemplateRecipeHandler.CachedRecipe cached = entries.get(index);
        List<PositionedStack> inputs = cached.getIngredients();
        if (inputs == null || inputs.isEmpty() || inputs.size() > 9) throw fault("ProjectBlue recipe has invalid inputs");
        int slot = 0;
        boolean[] occupied = new boolean[9];
        for (PositionedStack input : inputs) {
            if (input == null || input.item == null || input.items == null || input.items.length == 0) throw fault("ProjectBlue input is empty");
            int nativeSlot = slot(input);
            if (occupied[nativeSlot]) throw fault("ProjectBlue native layout repeats slot " + nativeSlot);
            occupied[nativeSlot] = true;
            boolean wildcard = input.items.length != 1;
            for (ItemStack choice : input.items) if (choice == null || choice.getItemDamage() == Short.MAX_VALUE) wildcard = true;
            row.itemInput(input, nativeSlot, Math.max(1, input.item.stackSize), false, true,
                    object("kind", wildcard ? "wildcard" : "exact", "meta", wildcard, "nbt", true));
            slot++;
        }
        PositionedStack output = cached.getResult();
        if (output == null || output.item == null || output.items == null || output.items.length == 0) throw fault("ProjectBlue output is empty");
        row.itemOutput(output, 0, output.item, 10000);
        handler.arecipes.clear(); handler.arecipes.add(cached);
        return true;
    }

    private static int slot(PositionedStack stack) {
        int x = stack.relx - 25, y = stack.rely - 6;
        if (x < 0 || y < 0 || x % 18 != 0 || y % 18 != 0 || x > 36 || y > 36) throw fault("ProjectBlue native layout is not a 3x3 grid");
        return (y / 18) * 3 + (x / 18);
    }

    private static String signature(TemplateRecipeHandler.CachedRecipe recipe) {
        StringBuilder key = new StringBuilder();
        PositionedStack result = recipe.getResult();
        key.append(stack(result == null ? null : result.item));
        List<PositionedStack> inputs = recipe.getIngredients();
        if (inputs != null) for (PositionedStack input : inputs) {
            key.append('|').append(input == null ? "null" : input.relx).append(':').append(input == null ? "null" : input.rely);
            if (input != null && input.items != null) for (ItemStack choice : input.items) key.append(':').append(stack(choice));
        }
        return CanonicalJson.digest(object("signature", key.toString()));
    }

    private static String stack(ItemStack item) {
        if (item == null || item.getItem() == null) return "null";
        return String.valueOf(Item.itemRegistry.getNameForObject(item.getItem())) + "#" + item.getItemDamage() + "x" + item.stackSize + "#" + TypedNbt.encode(item.getTagCompound());
    }

    private static Jobs.Fault fault(String message) { return new Jobs.Fault("recipe_unsupported", message); }
}
