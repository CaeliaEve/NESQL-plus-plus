package com.github.dcysteine.nesql.exporter.capture;

import codechicken.lib.gui.GuiDraw;
import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.source.Chance;
import com.github.dcysteine.nesql.exporter.source.Identity;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import forestry.api.recipes.ICentrifugeRecipe;
import forestry.api.recipes.IStillRecipe;
import forestry.api.recipes.RecipeManagers;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Item;
import net.minecraft.init.Items;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.oredict.OreDictionary;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Forestry 4.10.17 factories: machine consumption is distinct from NEI's display quantities. */
final class ForestryRecipes implements RegistryRecipes {
    private static final String PREFIX = "forestry.factory.recipes.";
    private final TemplateRecipeHandler handler;
    private final List<?> recipes;

    static boolean supports(ICraftingHandler handler) {
        String name = handler.getClass().getName();
        return name.equals(PREFIX + "nei.NEIHandlerCentrifuge") || name.equals(PREFIX + "nei.NEIHandlerStill");
    }
    private static boolean centrifuge(TemplateRecipeHandler handler) { return handler.getClass().getName().equals(PREFIX + "nei.NEIHandlerCentrifuge"); }
    ForestryRecipes(TemplateRecipeHandler handler) {
        version("Forestry", "4.10.17");
        this.handler = handler;
        Collection<?> source = centrifuge(handler) ? RecipeManagers.centrifugeManager.recipes() : RecipeManagers.stillManager.recipes();
        if (source.size() > 262144) throw fault("Factory registry exceeds its budget");
        recipes = new ArrayList<>(source);
    }
    public int size() { return recipes.size(); }
    public boolean capture(int index, RecipeRow row) { capture(handler, recipes.get(index), row); return true; }

    static void capture(TemplateRecipeHandler handler, Object source, RecipeRow row) {
        boolean centrifuge = centrifuge(handler);
        if (!supports(handler) || source == null || !source.getClass().getName().equals(PREFIX + (centrifuge ? "CentrifugeRecipe" : "StillRecipe"))) {
            throw fault("Unadapted Forestry recipe implementation: " + (source == null ? "null" : source.getClass().getName()));
        }
        if (centrifuge) centrifuge(handler, (ICentrifugeRecipe) source, row);
        else still(handler, (IStillRecipe) source, row);
    }

    private static void centrifuge(TemplateRecipeHandler handler, ICentrifugeRecipe source, RecipeRow row) {
        int time = source.getProcessingTime(); requirePositive(time, "centrifuge time");
        ItemStack input = item(source.getInput()); input.stackSize = 1; // TileCentrifuge.workCycle decrements one.
        List<Product> products = new ArrayList<>();
        Map<ItemStack, Float> nativeProducts = source.getAllProducts();
        if (nativeProducts.isEmpty() || nativeProducts.size() > 4096) throw fault("Invalid centrifuge product count");
        for (Map.Entry<ItemStack, Float> entry : nativeProducts.entrySet()) {
            Jobs.checkpoint();
            if (entry.getValue() == null) throw fault("Missing centrifuge product probability");
            products.add(new Product(item(entry.getKey()), entry.getValue()));
        }
        // Native maps may use identity hashing. Order facts independently of their iteration order,
        // retaining separate identical entries because each gets its own native probability roll.
        products.sort(Comparator.comparing((Product product) -> product.key)
                .thenComparingInt(product -> product.item.stackSize).thenComparingDouble(product -> product.rate));
        Object projection = construct(PREFIX + "CentrifugeRecipe", new Class<?>[] {int.class, ItemStack.class, Map.class}, time, input.copy(), copyProducts(products));
        TemplateRecipeHandler.CachedRecipe cached = cached(handler, "Centrifuge", ICentrifugeRecipe.class, projection);
        PositionedStack display = (PositionedStack) field(cached, "inputs");
        List<RecipeRow.Ingredient> ingredients = new ArrayList<>();
        boolean wildcard = input.getItemDamage() == OreDictionary.WILDCARD_VALUE;
        boolean ignoreNbt = input.getTagCompound() == null || input.getTagCompound().hasNoTags();
        for (ItemStack variant : display.items) {
            if (variant.getItem() != input.getItem() || !wildcard && variant.getItemDamage() != input.getItemDamage()) throw new Jobs.Fault("slot_changed", "Forestry input expansion changed its item");
            ItemStack fact = variant.copy(); fact.setTagCompound(input.getTagCompound() == null ? null : (net.minecraft.nbt.NBTTagCompound) input.getTagCompound().copy());
            ingredients.add(new RecipeRow.Ingredient(fact, 1, false, wildcard || ignoreNbt
                    ? object("kind", "wildcard", "meta", wildcard, "nbt", ignoreNbt) : object("kind", "exact")));
        }
        row.itemInput(display, 0, ingredients, false);
        List<Product> visible = new ArrayList<>(products);
        visible.sort((left, right) -> Float.compare(right.rate, left.rate));
        Map<Product, PositionedStack> positions = new IdentityHashMap<>();
        for (PositionedStack position : cached.getOtherStacks()) {
            Product match = null;
            for (Product product : visible) if (ItemStack.areItemStacksEqual(position.items[0], product.item)) { match = product; break; }
            if (match == null) throw new Jobs.Fault("slot_changed", "Forestry UI contains an unknown centrifuge product");
            visible.remove(match); positions.put(match, position);
        }
        int slot = 0;
        for (Product product : products) {
            row.itemOutput(positions.get(product), slot++, product.item, 10000);
            float rate = product.rate; // Native getProducts: >=1 always; NaN/<=0 never; otherwise nextFloat < rate.
            row.outputs.get(row.outputs.size() - 1).getAsJsonObject().add("chance", Chance.nextFloat(rate, false));
        }
        requirements(row, time, 160);
        handler.arecipes.clear(); handler.arecipes.add(cached);
    }

    private static void still(TemplateRecipeHandler handler, IStillRecipe source, RecipeRow row) {
        int cycles = source.getCyclesPerUnit(); requirePositive(cycles, "still cycles");
        FluidStack input = fluid(source.getInput()), output = fluid(source.getOutput());
        try { input.amount = Math.multiplyExact(input.amount, cycles); output.amount = Math.multiplyExact(output.amount, cycles); }
        catch (ArithmeticException overflow) { throw fault("Native still batch overflows a fluid amount"); }
        // The native UI shows the nominal batch. TileStill.hasWork checks only
        // one output unit; workCycle fills the whole batch and discards overflow.
        Object projection = construct(PREFIX + "StillRecipe", new Class<?>[] {int.class, FluidStack.class, FluidStack.class}, cycles, input.copy(), output.copy());
        TemplateRecipeHandler.CachedRecipe cached = cached(handler, "Still", IStillRecipe.class, projection);
        row.fluidInput(null, 0, input, false);
        row.fluidOutput(null, 0, output, object("kind", "potential", "stat", "forestry:stillTankSpace",
                "condition", "Limited by compatible product tank space at completion; excess fluid is lost",
                "nominal", Integer.toString(output.amount)));
        requirements(row, cycles, 200);
        handler.arecipes.clear(); handler.arecipes.add(cached);
    }

    static int[] progressBar(TemplateRecipeHandler handler) {
        if (!supports(handler)) throw fault("Unknown Forestry progress layout");
        // NEI's direction 11 has the same reverse/up semantics as direction 7.
        return centrifuge(handler) ? new int[] {53, 25, 176, 0, 4, 17, 80, 3} : new int[] {79, 6, 176, 74, 4, 18, 80, 7};
    }
    static void draw(TemplateRecipeHandler handler, int index) {
        handler.drawBackground(index);
        invoke(type("forestry.core.recipes.nei.RecipeHandlerBase"), handler, "drawFluidTanks", new Class<?>[] {int.class}, index);
        invoke(type("forestry.core.recipes.nei.RecipeHandlerBase"), handler, "changeToGuiTexture", new Class<?>[0]);
        if (!centrifuge(handler)) GuiDraw.drawTexturedModalRect(77, 46, 176, 60, 14, 14);
        // Dynamic progress is captured separately; all other native foreground content is retained.
    }

    private static void requirements(RecipeRow row, int time, int rfPerTick) {
        // TilePowered advances its counter every five game ticks, then applies
        // speed, difficulty and power multipliers. A recipe time is not a fixed
        // tick duration, nor is the raw RF parameter an EU/t rate.
        row.property("forestry:workSteps", "Base work steps", time);
        row.property("forestry:stepTicks", "Game ticks per work step", 5);
        row.property("forestry:energyRF", "Energy parameter before difficulty (RF)", time * rfPerTick);
    }
    private static ItemStack item(ItemStack value) { if (value == null || value.getItem() == null || value.stackSize <= 0) throw fault("Invalid native factory item"); return value.copy(); }
    private static FluidStack fluid(FluidStack value) { if (value == null || value.getFluid() == null || value.amount <= 0) throw fault("Invalid native factory fluid"); return value.copy(); }
    private static void requirePositive(int value, String field) { if (value <= 0) throw fault("Invalid " + field); }
    private static final class Product {
        final ItemStack item;
        final float rate;
        final String key;
        Product(ItemStack item, float rate) {
            this.item = item; this.rate = rate;
            String registry = Item.itemRegistry.getNameForObject(item.getItem());
            if (registry == null) throw new Jobs.Fault("unregistered_item", "Forestry product is unregistered");
            key = Identity.item(registry, Items.feather.getDamage(item), TypedNbt.encode(item.getTagCompound()));
        }
    }
    private static Map<ItemStack, Float> copyProducts(List<Product> products) {
        Map<ItemStack, Float> result = new LinkedHashMap<>();
        for (Product product : products) result.put(product.item.copy(), product.rate);
        return result;
    }
    private static Object construct(String name, Class<?>[] parameters, Object... args) {
        try { return type(name).getConstructor(parameters).newInstance(args); }
        catch (ReflectiveOperationException error) { Jobs.Fault failure = fault("Native Forestry constructor failed: " + name); failure.initCause(error); throw failure; }
    }
    private static TemplateRecipeHandler.CachedRecipe cached(TemplateRecipeHandler handler, String kind, Class<?> api, Object recipe) {
        return (TemplateRecipeHandler.CachedRecipe) construct(handler.getClass().getName() + "$Cached" + kind + "Recipe", new Class<?>[] {handler.getClass(), api}, handler, recipe);
    }
    private static Jobs.Fault fault(String message) { return new Jobs.Fault("recipe_unsupported", message); }
}
