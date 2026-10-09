package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import net.minecraft.item.ItemStack;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.field;
import static com.github.dcysteine.nesql.exporter.source.Json.object;

/** Blood Magic Alchemic Calcinator reagent conversion. */
final class BloodCalcinatorRecipes implements RegistryRecipes {
    private static final String HANDLER = "WayofTime.alchemicalWizardry.client.nei.NEICalcinatorHandler";
    private static final String KEY = "alchemicalwizardry.calcinator";
    private final TemplateRecipeHandler handler;
    private final List<Integer> recipes;

    static boolean supports(ICraftingHandler value) { return supportsClass(value.getClass().getName()); }
    static boolean supportsClass(String name) { return HANDLER.equals(name); }

    BloodCalcinatorRecipes(TemplateRecipeHandler handler) {
        if (!supports(handler)) throw fault("Unknown Blood Magic calcinator handler");
        Map<?, ?> registry = (Map<?, ?>) field(MagicApi.type("WayofTime.alchemicalWizardry.api.alchemy.energy.ReagentRegistry"), null, "itemToReagentMap");
        int count = registry == null ? 0 : registry.size();
        if (count <= 0 || count > 4096) throw fault("Blood Magic calcinator registry is empty or excessive");
        this.handler = handler;
        this.recipes = new ArrayList<>();
        for (int i = 0; i < count; i++) recipes.add(i);
    }

    public int size() { return recipes.size(); }

    public boolean capture(int index, RecipeRow row) {
        if (index < 0 || index >= recipes.size()) throw fault("Blood Magic calcinator recipe index outside native registry");
        Map<?, ?> registry = (Map<?, ?>) field(MagicApi.type("WayofTime.alchemicalWizardry.api.alchemy.energy.ReagentRegistry"), null, "itemToReagentMap");
        if (registry == null || index >= registry.size()) throw fault("Blood Magic calcinator registry changed during capture");
        ItemStack source = null; Object reagentStack = null; int cursor = 0;
        for (Map.Entry<?, ?> entry : registry.entrySet()) { if (cursor++ == index) { source = ((ItemStack) entry.getKey()).copy(); reagentStack = entry.getValue(); break; } }
        if (source == null || source.getItem() == null || reagentStack == null) throw fault("Blood Magic calcinator registry entry is invalid");
        int amount = ((Number) field(reagentStack, "amount")).intValue();
        if (amount <= 0) throw fault("Blood Magic calcinator reagent amount is nonpositive");
        Object reagent = field(reagentStack, "reagent");
        ItemStack output = (ItemStack) MagicApi.invoke(MagicApi.type("WayofTime.alchemicalWizardry.api.alchemy.energy.ReagentRegistry"), null, "getItemForReagent", new Class<?>[] {MagicApi.type("WayofTime.alchemicalWizardry.api.alchemy.energy.Reagent")}, reagent);
        if (output == null || output.getItem() == null) throw fault("Blood Magic calcinator reagent has no output item");
        List<ItemStack> orbs = new ArrayList<>(); for (net.minecraft.item.Item orb : BloodOrbShapedRecipes.orbs()) orbs.add(new ItemStack(orb));
        if (orbs.isEmpty()) throw fault("Blood Magic calcinator has no registered blood orb candidates");
        row.itemInput(new PositionedStack(orbs, 32, 33), 0, 1, true, false, object("kind", "wildcard", "meta", true, "nbt", true));
        output = output.copy(); output.stackSize = 1;
        row.itemOutput(new PositionedStack(output.copy(), 0, 0), 0, output, 10000);
        row.property("bloodmagic:reagentAmount", "Native reagent amount produced per calcinator cycle", amount);
        row.property("bloodmagic:orb", "Blood Orb is reusable catalyst", true);
        return true;
    }

    private static Jobs.Fault fault(String message) { return new Jobs.Fault("recipe_unsupported", message); }
}
