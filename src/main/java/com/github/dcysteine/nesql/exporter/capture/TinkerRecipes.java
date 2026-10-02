package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** TConstruct 1.13.57 registries. Native layouts receive owned projections only. */
final class TinkerRecipes implements RegistryRecipes {
    private static final String HANDLERS = "tconstruct.plugins.nei.";
    private static final String CRAFTING = "tconstruct.library.crafting.";
    private final TemplateRecipeHandler handler;
    private final List<Object> recipes = new ArrayList<>();

    static boolean supports(ICraftingHandler handler) {
        String name = handler.getClass().getName();
        return name.equals(HANDLERS + "RecipeHandlerAlloying") || name.equals(HANDLERS + "RecipeHandlerMelting");
    }
    private static boolean alloying(TemplateRecipeHandler handler) { return handler.getClass().getName().equals(HANDLERS + "RecipeHandlerAlloying"); }
    TinkerRecipes(TemplateRecipeHandler handler) {
        version("TConstruct", "1.13.57-GTNH");
        version("Mantle", "0.5.1");
        this.handler = handler;
        if (alloying(handler)) recipes.addAll((List<?>) invoke(type(CRAFTING + "Smeltery"), null, "getAlloyList", new Class<?>[0]));
        else for (Object key : ((Map<?, ?>) invoke(type(CRAFTING + "Smeltery"), null, "getSmeltingList", new Class<?>[0])).keySet()) {
            Jobs.checkpoint();
            if (!key.getClass().getName().equals("mantle.utils.ItemMetaWrapper")) throw fault("Unadapted melting key: " + key.getClass().getName());
            recipes.add(new ItemStack((Item) field(key, "item"), 1, (Integer) field(key, "meta")));
        }
        if (recipes.size() > 262144) throw fault("TConstruct registry exceeds its budget");
    }
    public int size() { return recipes.size(); }
    public boolean capture(int index, RecipeRow row) { return capture(handler, recipes.get(index), row); }

    static boolean capture(TemplateRecipeHandler handler, Object recipe, RecipeRow row) {
        if (!supports(handler)) throw fault("Unknown TConstruct recipe handler");
        if (alloying(handler)) return alloy(handler, recipe, row);
        if (!(recipe instanceof ItemStack)) throw fault("Invalid TConstruct melting source");
        melting(handler, (ItemStack) recipe, row); return true;
    }

    private static boolean alloy(TemplateRecipeHandler handler, Object recipe, RecipeRow row) {
        if (recipe == null || !recipe.getClass().getName().equals(CRAFTING + "AlloyMix"))
            throw fault("Unadapted alloy implementation: " + (recipe == null ? "null" : recipe.getClass().getName()));
        List<?> mixers = (List<?>) field(recipe, "mixers");
        if (mixers.isEmpty()) return false; // The native NEI handler excludes these; mix() has no minimum batch.
        if (mixers.size() > 4096) throw fault("Alloy ingredients exceed their budget");
        List<FluidStack> inputs = new ArrayList<>();
        for (Object raw : mixers) {
            Jobs.checkpoint();
            FluidStack input = fluid(raw);
            for (FluidStack prior : inputs) if (prior.isFluidEqual(input))
                throw fault("Repeated alloy fluid cannot be matched by the smeltery's merged tank");
            inputs.add(input);
        }
        FluidStack output = fluid(field(recipe, "result"));
        List<FluidStack> copies = new ArrayList<>();
        for (FluidStack input : inputs) copies.add(input.copy());
        Object projection = construct(type(CRAFTING + "AlloyMix"), new Class<?>[] {FluidStack.class, List.class}, output.copy(), copies);
        TemplateRecipeHandler.CachedRecipe cached = (TemplateRecipeHandler.CachedRecipe) construct(type(HANDLERS + "RecipeHandlerAlloying$CachedAlloyingRecipe"),
                new Class<?>[] {handler.getClass(), projection.getClass()}, handler, projection);
        for (int slot = 0; slot < inputs.size(); slot++) row.fluidInput(null, slot, inputs.get(slot), false);
        row.fluidOutput(null, 0, output);
        row.property("tconstruct:batch", "Batch policy", "Maximum whole multiples of the listed fluid quantities");
        replace(handler, cached); return true;
    }

    private static void melting(TemplateRecipeHandler handler, ItemStack source, RecipeRow row) {
        if (source.getItem() == null) throw fault("Invalid melting item");
        ItemStack input = source.copy(); input.stackSize = 1; input.setTagCompound(null);
        Class<?> registry = type(CRAFTING + "Smeltery");
        FluidStack output = fluid(invoke(registry, null, "getSmelteryResult", new Class<?>[] {ItemStack.class}, input.copy()));
        int temperature = (Integer) invoke(registry, null, "getLiquifyTemperature", new Class<?>[] {ItemStack.class}, input.copy());
        TemplateRecipeHandler.CachedRecipe cached = (TemplateRecipeHandler.CachedRecipe) construct(type(HANDLERS + "RecipeHandlerMelting$CachedMeltingRecipe"),
                new Class<?>[] {handler.getClass(), ItemStack.class}, handler, input.copy());
        PositionedStack display = cached.getIngredient();
        // Mantle's ItemMetaWrapper uses exact metadata, including 32767; it does not implement wildcard lookup.
        // Replace NEI's generic wildcard expansion with this exact registered input.
        display.items = new ItemStack[] {input.copy()}; display.item = display.items[0];
        row.itemInput(display, 0, Collections.singletonList(new RecipeRow.Ingredient(input, 1, false,
                object("kind", "wildcard", "meta", false, "nbt", true))), false);
        row.fluidOutput(null, 0, output);
        row.property("tconstruct:temperature", "Melting temperature (C)", temperature);
        // Heating time depends on fuel/active temperature; do not invent a constant duration or EU cost.
        replace(handler, cached);
    }

    private static void replace(TemplateRecipeHandler handler, TemplateRecipeHandler.CachedRecipe cached) {
        handler.arecipes.clear(); handler.arecipes.add(cached);
    }
    private static FluidStack fluid(Object raw) {
        if (!(raw instanceof FluidStack)) throw fault("Missing native TConstruct fluid");
        FluidStack fluid = (FluidStack) raw;
        if (fluid.getFluid() == null || fluid.amount <= 0) throw fault("Invalid native TConstruct fluid amount");
        return fluid.copy();
    }
    private static Object construct(Class<?> owner, Class<?>[] parameters, Object... arguments) {
        try { return owner.getConstructor(parameters).newInstance(arguments); }
        catch (InvocationTargetException error) {
            Throwable cause = error.getCause();
            if (cause instanceof Error) throw (Error) cause;
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            Jobs.Fault failure = fault("Native TConstruct constructor failed: " + owner.getName()); failure.initCause(cause); throw failure;
        } catch (ReflectiveOperationException error) {
            Jobs.Fault failure = fault("Missing native TConstruct constructor: " + owner.getName()); failure.initCause(error); throw failure;
        }
    }
    private static Jobs.Fault fault(String message) { return new Jobs.Fault("recipe_unsupported", message); }
}
