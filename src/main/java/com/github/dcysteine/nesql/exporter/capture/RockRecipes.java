package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** The pinned GTNH rock crusher exits before processing, insertion or GUI use. */
final class RockRecipes implements RegistryRecipes {
    private static final String HANDLER = "tonius.neiintegration.mods.railcraft.RecipeHandlerRockCrusher";
    private static final String GAME = "mods.railcraft.common.util.misc.Game";
    private final List<String> types = new ArrayList<>();
    static boolean supports(ICraftingHandler handler) {
        return handler.getClass().getName().equals(HANDLER) && disabled();
    }
    private static boolean disabled() { return (Boolean) field(type(GAME), null, "isGTNH"); }
    RockRecipes(TemplateRecipeHandler handler) { this(handler, registry()); }
    private static List<?> registry() {
        version("Railcraft", "9.16.33"); version("neiintegration", "1.5.0");
        Object manager = field(type("mods.railcraft.api.crafting.RailcraftCraftingManager"), null, "rockCrusher");
        if (manager == null || !manager.getClass().getName().equals("mods.railcraft.common.util.crafting.RockCrusherCraftingManager"))
            throw new Jobs.Fault("recipe_unsupported", "Unadapted rock crusher registry");
        return new ArrayList<>((List<?>) invoke(manager.getClass(), manager, "getRecipes", new Class<?>[0]));
    }
    RockRecipes(TemplateRecipeHandler handler, List<?> registry) {
        if (!handler.getClass().getName().equals(HANDLER) || registry.size() > 4096)
            throw new Jobs.Fault("recipe_unsupported", "Invalid disabled rock crusher identity or proof budget");
        requireDisabled();
        // Native loadAllRecipes skips nulls. No getters or random-output methods run here.
        for (Object recipe : registry) if (recipe != null) types.add(recipe.getClass().getName());
    }
    public int size() { return types.size(); }
    public boolean capture(int index, RecipeRow row) { types.get(index); requireDisabled(); return false; }
    public JsonObject exclusion(int index) {
        requireDisabled();
        return object("reason", "native_machine_disabled", "scope", "current_environment", "mod", "Railcraft", "version", "9.16.33",
                "condition", GAME + ".isGTNH", "value", true, "machine", "mods.railcraft.common.blocks.machine.alpha.TileRockCrusher",
                "sourceClass", types.get(index), "nativeIndex", index);
    }
    private static void requireDisabled() {
        if (!disabled()) throw new Jobs.Fault("environment_changed", "Rock crusher isGTNH disable guard changed; no exclusion is valid");
    }
}
