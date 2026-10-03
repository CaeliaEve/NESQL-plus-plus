package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonObject;
import net.minecraftforge.oredict.OreDictionary;
import java.util.ArrayList;
import java.util.List;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Current-environment native disable proofs, never an active-machine recipe adapter. */
final class DisabledRecipes implements RegistryRecipes {
    private enum Family {
        ROCK("tonius.neiintegration.mods.railcraft.RecipeHandlerRockCrusher", "Railcraft", "9.16.33",
                "mods.railcraft.common.util.misc.Game", "isGTNH", "mods.railcraft.common.blocks.machine.alpha.TileRockCrusher", "native_machine_disabled"),
        COMPRESSION("fox.spiteful.avaritia.compat.nei.CompressionHandler", "Avaritia", "1.77",
                "fox.spiteful.avaritia.Avaritia", "isDreamCraftLoaded", "fox.spiteful.avaritia.tile.TileEntityCompressor", "native_recipe_lookup_disabled");
        final String handler, mod, version, owner, flag, machine, reason;
        Family(String handler, String mod, String version, String owner, String flag, String machine, String reason) {
            this.handler=handler; this.mod=mod; this.version=version; this.owner=owner; this.flag=flag; this.machine=machine; this.reason=reason;
        }
        boolean disabled() { return (Boolean) field(type(owner), null, flag); }
    }
    private final Family family;
    private final List<String> types = new ArrayList<>();
    private final List<Integer> indices = new ArrayList<>();
    private static Family family(ICraftingHandler handler) {
        for (Family family : Family.values()) if (handler.getClass().getName().equals(family.handler)) return family;
        return null;
    }
    static boolean supports(ICraftingHandler handler) { Family family=family(handler); return family!=null && family.disabled(); }
    DisabledRecipes(TemplateRecipeHandler handler) { this(handler, registry(handler)); }
    private static List<?> registry(TemplateRecipeHandler handler) {
        Family family=family(handler);
        if (family==null) throw new Jobs.Fault("recipe_unsupported", "Unknown disabled handler");
        version(family.mod,family.version);
        if (family==Family.COMPRESSION) return (List<?>) invoke(type("fox.spiteful.avaritia.crafting.CompressorManager"),null,"getRecipes",new Class<?>[0]);
        version("neiintegration", "1.5.0");
        Object manager = field(type("mods.railcraft.api.crafting.RailcraftCraftingManager"), null, "rockCrusher");
        if (manager == null || !manager.getClass().getName().equals("mods.railcraft.common.util.crafting.RockCrusherCraftingManager"))
            throw new Jobs.Fault("recipe_unsupported", "Unadapted rock crusher registry");
        return (List<?>) invoke(manager.getClass(), manager, "getRecipes", new Class<?>[0]);
    }
    DisabledRecipes(TemplateRecipeHandler handler, List<?> registry) {
        family=family(handler);
        if (family==null || registry==null || registry.size()>4096) throw new Jobs.Fault("recipe_unsupported", "Invalid disabled identity or proof budget");
        requireDisabled();
        for (int index=0;index<registry.size();index++) {
            Jobs.checkpoint();
            Object recipe=registry.get(index);
            if (family==Family.ROCK && recipe==null) continue; // Native loadAllRecipes skips nulls.
            if (recipe==null) throw new Jobs.Fault("recipe_unsupported", "Null disabled compressor recipe");
            if (family==Family.COMPRESSION) {
                String name=recipe.getClass().getName();
                if (name.equals("fox.spiteful.avaritia.crafting.CompressOreRecipe")) {
                    // Native safeOre skips empty ore lists. Read its pinned field; no virtual
                    // recipe getter, cost balancing, output computation or machine tick runs.
                    int ore=(Integer)field(recipe,"oreID");
                    if (OreDictionary.getOres(OreDictionary.getOreName(ore)).isEmpty()) continue;
                } else if (!name.equals("fox.spiteful.avaritia.crafting.CompressorRecipe")) {
                    throw new Jobs.Fault("recipe_unsupported", "Unknown compressor enumeration semantics: "+name);
                }
            }
            types.add(recipe.getClass().getName()); indices.add(index);
        }
    }
    public int size() { requireDisabled(); return types.size(); }
    public boolean capture(int index, RecipeRow row) { types.get(index); requireDisabled(); return false; }
    public JsonObject handlerExclusion() {
        requireDisabled();
        return object("reason",family.reason,"scope","current_environment","mod",family.mod,"version",family.version,
                "condition",family.owner+"."+family.flag,"value",true,"machine",family.machine);
    }
    public JsonObject exclusion(int index) {
        JsonObject proof=handlerExclusion();
        proof.addProperty("sourceClass",types.get(index)); proof.addProperty("nativeIndex",indices.get(index));
        return proof;
    }
    private void requireDisabled() {
        if (!family.disabled()) throw new Jobs.Fault("environment_changed",family.mod+" native disable guard changed; no exclusion is valid");
    }
}
