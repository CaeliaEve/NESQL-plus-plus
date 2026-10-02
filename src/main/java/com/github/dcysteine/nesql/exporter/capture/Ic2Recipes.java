package com.github.dcysteine.nesql.exporter.capture;

import codechicken.lib.gui.GuiDraw;
import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.oredict.OreDictionary;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import java.lang.reflect.Proxy;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** IC2 2.2.828: registry predicates/results supply facts; a private native cache supplies layout. */
final class Ic2Recipes implements RegistryRecipes {
    private static final String HANDLERS = "ic2.neiIntegration.core.recipehandler.";
    private static final String API = "ic2.api.recipe.";
    private enum Machine {
        MACERATOR("MaceratorRecipeHandler", 300, 2), EXTRACTOR("ExtractorRecipeHandler", 300, 2),
        COMPRESSOR("CompressorRecipeHandler", 300, 2), CUTTING("MetalFormerRecipeHandlerCutting", 200, 10),
        ROLLING("MetalFormerRecipeHandlerRolling", 200, 10), EXTRUDING("MetalFormerRecipeHandlerExtruding", 200, 10),
        CENTRIFUGE("CentrifugeRecipeHandler", 500, 48), WASHING("OreWashingRecipeHandler", 500, 16);
        final String handler;
        final int ticks, energy;
        Machine(String handler, int ticks, int energy) { this.handler = HANDLERS + handler; this.ticks = ticks; this.energy = energy; }
    }
    private static Machine machine(ICraftingHandler handler) {
        for (Machine value : Machine.values()) if (value.handler.equals(handler.getClass().getName())) return value;
        return null;
    }
    static boolean supports(ICraftingHandler handler) { return machine(handler) != null; }
    private final TemplateRecipeHandler handler;
    private final List<Map.Entry<?, ?>> recipes = new ArrayList<>();

    Ic2Recipes(TemplateRecipeHandler handler) {
        version("IC2", "2.2.828-experimental");
        this.handler = handler;
        Object registry = invoke(handler.getClass(), handler, "getRecipeList", new Class<?>[0]);
        if (!(registry instanceof Map<?, ?>)) throw fault("IC2 machine has no recipe registry");
        for (Map.Entry<?, ?> recipe : ((Map<?, ?>) registry).entrySet()) {
            Jobs.checkpoint();
            recipes.add(new AbstractMap.SimpleImmutableEntry<>(recipe));
        }
    }
    public int size() { return recipes.size(); }
    public boolean capture(int index, RecipeRow row) {
        Map.Entry<?, ?> recipe = recipes.get(index);
        capture(handler, recipe.getKey(), recipe.getValue(), row);
        return true;
    }

    static int[] progressBar(TemplateRecipeHandler handler) {
        Machine machine = machine(handler);
        if (machine == null) throw fault("Unverified IC2 progress layout");
        if (machine == Machine.CENTRIFUGE) return new int[] {84, 10, 176, 50, 5, 30, 20, 3};
        if (machine == Machine.WASHING) return new int[] {114, 24, 176, 117, 20, 19, 20, 0};
        if (machine == Machine.CUTTING || machine == Machine.ROLLING || machine == Machine.EXTRUDING) {
            return new int[] {46, 30, 177, 14, 51, 12, 20, 0};
        }
        return new int[] {74, 23, 176, 14, 25, 16, 20, 0};
    }

    static void scene(TemplateRecipeHandler handler, Runnable draw) {
        // At tick 20 native energy/heat indicators are full and the cycling progress is empty.
        // Capture that native foreground, then animate only its progress texture in the catalog.
        Class<?> owner = type(HANDLERS + "MachineRecipeHandler");
        try {
            java.lang.reflect.Field clock = owner.getDeclaredField("ticks"); clock.setAccessible(true);
            int previous = clock.getInt(handler);
            try { clock.setInt(handler, 20); draw.run(); }
            finally { clock.setInt(handler, previous); }
        } catch (ReflectiveOperationException error) {
            Jobs.Fault failure = fault("Cannot preserve native IC2 UI clock"); failure.initCause(error); throw failure;
        }
    }

    static void draw(TemplateRecipeHandler handler, int index) {
        handler.drawBackground(index);
        if (machine(handler) != Machine.WASHING) { handler.drawForeground(index); return; }
        // Retain the native labels and tank; the native mouse tooltip dereferences the live GuiRecipe
        // and is not part of an offscreen scene. Progress is captured as its separate native track.
        Object water = invoke(handler.getClass(), handler, "getreqWater", new Class<?>[] {int.class}, index);
        GuiDraw.drawStringC("require: ", 35, 20, 0, false);
        GuiDraw.drawStringC("water", 35, 33, 0, false);
        GuiDraw.drawStringC(water + "mb", 35, 46, 0, false);
    }

    @SuppressWarnings("unchecked")
    static void capture(TemplateRecipeHandler handler, Object input, Object output, RecipeRow row) {
        Machine machine = machine(handler);
        if (machine == null) throw fault("Unverified IC2 machine " + handler.getClass().getName());
        List<RecipeRow.Ingredient> ingredients = ingredients(input);
        if (output == null || !output.getClass().getName().equals(API + "RecipeOutput")) throw fault("Unknown IC2 output type");
        List<?> raw = (List<?>) field(output, "items");
        if (raw.isEmpty() || raw.size() > 4096) throw fault("Invalid IC2 output count");
        List<ItemStack> products = new ArrayList<>();
        for (Object item : raw) {
            Jobs.checkpoint();
            if (!(item instanceof ItemStack) || ((ItemStack) item).getItem() == null || ((ItemStack) item).stackSize <= 0) {
                throw fault("IC2 machine has no fixed positive output");
            }
            products.add(((ItemStack) item).copy());
        }
        NBTTagCompound metadata = (NBTTagCompound) field(output, "metadata");
        int water = 0;
        if (machine == Machine.CENTRIFUGE) {
            if (metadata == null || !metadata.hasKey("minHeat", 3) || metadata.getInteger("minHeat") < 0) {
                throw fault("IC2 centrifuge has no nonnegative native heat requirement");
            }
            row.property("ic2:minHeat", "Minimum heat", metadata.getInteger("minHeat"));
        }
        if (machine == Machine.WASHING) {
            if (metadata == null) throw fault("IC2 washing recipe has no metadata and cannot operate");
            // Native getInteger defaults absent fields to zero; drain(0) consumes no fluid.
            // Water admission checks the fluid ID only, not its tags.
            water = Math.max(0, metadata.getInteger("amount"));
        }
        if (metadata != null) for (Object key : metadata.func_150296_c()) {
            if (!(machine == Machine.CENTRIFUGE && key.equals("minHeat") || machine == Machine.WASHING && key.equals("amount")))
                throw fault("Unadapted IC2 recipe metadata: " + key);
        }
        row.record.addProperty("duration", Integer.toString(machine.ticks));
        row.record.addProperty("energy", Integer.toString(machine.energy));
        Class<?> api = type(API + "IRecipeInput");
        // The layout receives only owned copies. In particular, ore-dictionary stacks are shared by IC2.
        Object projection = Proxy.newProxyInstance(api.getClassLoader(), new Class<?>[] {api}, (proxy, method, args) -> {
            if (method.getName().equals("getAmount")) return (int) ingredients.get(0).amount;
            if (method.getName().equals("getInputs")) {
                List<ItemStack> copies = new ArrayList<>();
                for (RecipeRow.Ingredient ingredient : ingredients) copies.add(ingredient.item.copy());
                return copies;
            }
            throw fault("Unexpected IC2 layout predicate call: " + method.getName());
        });
        TemplateRecipeHandler.CachedRecipe cached;
        try {
            Class<?> resultType = type(API + "RecipeOutput");
            Object result = resultType.getConstructor(NBTTagCompound.class, List.class)
                    .newInstance(metadata == null ? null : metadata.copy(), products);
            cached = (TemplateRecipeHandler.CachedRecipe) type(HANDLERS + "MachineRecipeHandler$CachedIORecipe")
                    .getConstructor(type(HANDLERS + "MachineRecipeHandler"), api, resultType).newInstance(handler, projection, result);
        } catch (ReflectiveOperationException error) {
            Jobs.Fault failure = fault("Cannot construct native IC2 layout"); failure.initCause(error); throw failure;
        }
        List<PositionedStack> inputs = (List<PositionedStack>) field(cached, "ingredients");
        if (inputs.size() != 1) throw new Jobs.Fault("slot_changed", "IC2 layout changed its input count");
        row.itemInput(inputs.get(0), 0, ingredients, false);
        if (water > 0) row.fluidInput(null, 0, new FluidStack(FluidRegistry.WATER, water), false,
                object("kind", "wildcard", "meta", false, "nbt", true));
        List<PositionedStack> outputs = new ArrayList<>(); outputs.add(cached.getResult()); outputs.addAll(cached.getOtherStacks());
        if (outputs.size() != products.size()) throw new Jobs.Fault("slot_missing", "IC2 layout omitted an output");
        for (int slot = 0; slot < products.size(); slot++) row.itemOutput(outputs.get(slot), slot, products.get(slot), 10000);
        handler.arecipes.clear(); handler.arecipes.add(cached);
    }

    private static List<RecipeRow.Ingredient> ingredients(Object input) {
        if (input == null) throw fault("Missing IC2 input predicate");
        String name = input.getClass().getName();
        boolean ore = name.equals(API + "RecipeInputOreDict");
        if (!ore && !name.equals(API + "RecipeInputItemStack")) throw fault("Unadapted IC2 input predicate: " + name);
        int amount = (Integer) invoke(input.getClass(), input, "getAmount", new Class<?>[0]);
        if (amount <= 0) throw fault("IC2 input amount must be positive");
        List<?> offered = (List<?>) invoke(input.getClass(), input, "getInputs", new Class<?>[0]);
        if (offered.isEmpty() || offered.size() > 65536) throw fault("IC2 predicate has no finite input set");
        Integer override = ore ? (Integer) field(input, "meta") : null;
        List<RecipeRow.Ingredient> result = new ArrayList<>();
        for (Object value : offered) {
            Jobs.checkpoint();
            if (!(value instanceof ItemStack) || ((ItemStack) value).getItem() == null) throw fault("IC2 predicate contains an invalid item");
            ItemStack item = ((ItemStack) value).copy(); item.stackSize = amount;
            if (override != null) item.setItemDamage(override);
            boolean wildcard = item.getItemDamage() == OreDictionary.WILDCARD_VALUE;
            ItemStack[] variants = wildcard ? new PositionedStack(item, 0, 0, true).items : new ItemStack[] {item};
            if (variants.length == 0 || variants.length + result.size() > 65536) throw fault("IC2 input expansion exceeds its budget");
            for (ItemStack variant : variants) {
                if (!(Boolean) invoke(input.getClass(), input, "matches", new Class<?>[] {ItemStack.class}, variant.copy())) {
                    throw new Jobs.Fault("slot_changed", "IC2 displayed candidate does not satisfy its native predicate");
                }
                result.add(new RecipeRow.Ingredient(variant.copy(), amount, false, object("kind", "wildcard", "meta", wildcard, "nbt", true)));
            }
        }
        return result;
    }
    private static Jobs.Fault fault(String message) { return new Jobs.Fault("recipe_unsupported", message); }
}
