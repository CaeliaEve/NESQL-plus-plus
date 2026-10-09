package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import net.minecraft.item.ItemStack;
import java.util.ArrayList;
import java.util.List;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.field;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.type;
import static com.github.dcysteine.nesql.exporter.source.Json.object;

/** Blood Magic alchemy table: five greedy item slots plus a reusable tiered orb. */
final class BloodAlchemyRecipes implements RegistryRecipes {
    private static final String HANDLER = "WayofTime.alchemicalWizardry.client.nei.NEIAlchemyRecipeHandler";
    private static final String RECIPE = "WayofTime.alchemicalWizardry.api.alchemy.AlchemyRecipe";
    private final TemplateRecipeHandler handler;
    private final List<Object> recipes;

    static boolean supports(ICraftingHandler value) { return supportsClass(value.getClass().getName()); }
    static boolean supportsClass(String name) { return HANDLER.equals(name); }

    BloodAlchemyRecipes(TemplateRecipeHandler handler) {
        if (!supports(handler)) throw fault("Unknown Blood Magic alchemy handler");
        this.handler = handler;
        Object raw = field(type("WayofTime.alchemicalWizardry.api.alchemy.AlchemyRecipeRegistry"), null, "recipes");
        if (!(raw instanceof List) || ((List<?>) raw).size() > 65536) throw fault("Invalid Blood Magic alchemy registry");
        recipes = new ArrayList<>();
        for (Object value : (List<?>) raw) {
            if (value == null) continue;
            if (!RECIPE.equals(value.getClass().getName())) throw fault("Unknown Blood Magic alchemy recipe");
            ItemStack output = (ItemStack) field(value, "output");
            ItemStack[] input = (ItemStack[]) field(value, "recipe");
            int amount = ((Number) field(value, "amountNeeded")).intValue();
            int level = ((Number) field(value, "bloodOrbLevel")).intValue();
            if (output == null || output.getItem() == null || input == null || input.length > 5 || amount <= 0 || level < 0)
                throw fault("Invalid Blood Magic alchemy recipe fields");
            for (ItemStack item : input) if (item != null && (item.getItem() == null || item.stackSize <= 0)) throw fault("Invalid Blood Magic alchemy input");
            recipes.add(value);
        }
        if (recipes.isEmpty()) throw fault("Blood Magic alchemy registry is empty");
    }

    public int size() { return recipes.size(); }

    public boolean capture(int index, RecipeRow row) {
        Object recipe = recipes.get(index);
        ItemStack[] input = ((ItemStack[]) field(recipe, "recipe"));
        ItemStack output = ((ItemStack) field(recipe, "output")).copy();
        int amount = ((Number) field(recipe, "amountNeeded")).intValue();
        int level = ((Number) field(recipe, "bloodOrbLevel")).intValue();
        for (int slot = 0; slot < input.length; slot++) {
            ItemStack item = input[slot];
            if (item == null) continue;
            PositionedStack display = new PositionedStack(item.copy(), slot == 0 ? 76 : slot == 1 ? 51 : slot == 2 ? 101 : slot == 3 ? 64 : 88,
                    slot < 1 ? 3 : slot < 3 ? 19 : 47, false);
            row.itemInput(display, slot, 1, false, false, object("kind", "exact", "meta", item.getItemDamage() == Short.MAX_VALUE, "nbt", false));
        }
        List<ItemStack> candidates = new ArrayList<>();
        for (net.minecraft.item.Item orbItem : BloodOrbShapedRecipes.orbs()) {
            if (BloodOrbShapedRecipes.orbLevel(orbItem) >= level) candidates.add(new ItemStack(orbItem));
        }
        if (candidates.isEmpty()) throw fault("Blood Magic alchemy has no registered orb meeting tier " + level);
        PositionedStack orb = new PositionedStack(candidates, 136, 47, false);
        row.itemInput(orb, 5, 1, true, false, object("kind", "wildcard", "meta", true, "nbt", true));
        row.itemOutput(new PositionedStack(output.copy(), 76, 25, false), 0, output, 10000);
        row.property("bloodmagic:lpCost", "Life points consumed from the soul network", ((long) amount) * 100L);
        row.property("bloodmagic:orb", "Blood Orb is reusable and requires this minimum tier", level);
        return true;
    }

    private static Jobs.Fault fault(String message) { return new Jobs.Fault("recipe_unsupported", message); }
}
