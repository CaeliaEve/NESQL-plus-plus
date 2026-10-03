package com.github.dcysteine.nesql.exporter.capture;

import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.google.gson.*;
import forestry.api.recipes.IStillRecipe;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.*;
import java.nio.file.*;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.capture.NativeSqueezerTest.fluidJson;
import static com.github.dcysteine.nesql.exporter.capture.NativeSqueezerWorkTest.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Calls native hasWork/workCycle without construction, ticking, players or a world. */
final class NativeStillWorkTest {
    private static final JsonArray cases = new JsonArray();

    @SuppressWarnings("unchecked")
    static void run() throws Exception {
        Set<IStillRecipe> registry = (Set<IStillRecipe>) field(type("forestry.factory.recipes.StillRecipeManager"), null, "recipes");
        Set<IStillRecipe> saved = new HashSet<>(registry);
        try {
            IStillRecipe base = recipe(3, 10, 3);
            registry.clear(); registry.add(base);
            check("whole-batch", registry, base, water(100), lava(10), null, true);
            check("partial-space", registry, base, water(100), lava(9995), null, true);
            check("full-tank-still-reserves", registry, base, water(100), lava(10000), null, true);
            check("wrong-product-still-reserves", registry, base, water(100), water(100), null, true);
            check("input-short-for-batch", registry, base, water(29), null, null, true);
            check("exact-input-drained", registry, base, water(30), null, null, true);
            check("buffer-survives-blocked-space", registry, base, null, lava(10000), water(30), true);
            check("buffer-completes-with-empty-input", registry, base, null, null, water(30), true);
            check("buffer-priority-over-input", registry, base, lava(80), null, water(30), true);
            check("input-missing", registry, base, null, null, null, true);
            check("no-current-selects-registry", registry, null, water(100), null, null, true);
            check("stale-filter-blocks-but-reserves", registry, base, water(100), null, null, false);
            FluidStack emptyTag = water(100); emptyTag.tag = new NBTTagCompound();
            check("null-empty-nbt-do-not-match", registry, base, emptyTag, null, null, true);
            IStillRecipe competing = recipe(7, 10, 11); registry.add(competing);
            check("retained-wins-registry", registry, competing, water(100), null, null, true);
            check("native-hashset-fresh-priority", registry, null, water(100), null, null, true);
            for (int[] values : new int[][] {{0,10,3},{-1,10,3},{Integer.MAX_VALUE,10,3},{3,0,3},{3,10,0},{3,10,-1},{3,10,Integer.MAX_VALUE}}) {
                IStillRecipe edge = recipe(values[0], values[1], values[2]);
                registry.clear(); registry.add(edge);
                check("native-arithmetic-"+Arrays.toString(values), registry, edge, water(100), null, null, true);
            }
        } finally { registry.clear(); registry.addAll(saved); }
        Files.write(Paths.get("build/native-tests/forestry-still-observations.json"), CanonicalJson.bytes(object("native", "Forestry 4.10.17", "cases", cases)));
        System.out.println("Native Forestry still: " + cases.size() + " selection/buffer/tank observations recorded");
    }

    private static void check(String name, Set<IStillRecipe> registry, IStillRecipe retained, FluidStack resource, FluidStack product, FluidStack buffer, boolean allowOutput) throws Exception {
        Object tile = allocate(type("forestry.factory.tiles.TileStill"));
        set(tile, "errorHandler", type("forestry.core.errors.ErrorLogic").newInstance());
        Class<?> filtered = type("forestry.core.fluids.tanks.FilteredTank");
        FluidTank input = (FluidTank) filtered.getConstructor(int.class, Collection.class).newInstance(10000, Arrays.asList(FluidRegistry.WATER));
        List<Fluid> allowed = allowOutput ? Arrays.asList(FluidRegistry.LAVA) : Collections.<Fluid>emptyList();
        FluidTank output = (FluidTank) filtered.getConstructor(int.class, Collection.class).newInstance(10000, allowed);
        input.setFluid(copy(resource)); output.setFluid(copy(product));
        set(tile, "resourceTank", input); set(tile, "productTank", output);
        set(tile, "currentRecipe", retained); set(tile, "bufferedLiquid", copy(buffer));
        JsonArray rules = new JsonArray(), filters = new JsonArray();
        for (IStillRecipe rule : registry) rules.add(json(rule));
        for (Fluid fluid : allowed) filters.add(new JsonPrimitive(fluid.getName()));
        JsonObject before = object("retained", json(retained), "resource", fluidJson(resource), "product", fluidJson(product), "buffer", fluidJson(buffer), "filters", filters);
        boolean ready = (Boolean) invoke(tile.getClass(), tile, "hasWork", new Class<?>[0]);
        JsonElement reserved = fluidJson((FluidStack) field(tile, "bufferedLiquid"));
        boolean completed = ready && (Boolean) invoke(tile.getClass(), tile, "workCycle", new Class<?>[0]);
        cases.add(object("name", name, "recipes", rules, "state", before, "after", object("selected", json((IStillRecipe) field(tile, "currentRecipe")),
                "hasWork", ready, "completed", completed, "reserved", reserved, "resource", fluidJson(input.getFluid()),
                "product", fluidJson(output.getFluid()), "buffer", fluidJson((FluidStack) field(tile, "bufferedLiquid")))));
    }

    private static JsonElement json(IStillRecipe recipe) { return recipe == null ? JsonNull.INSTANCE : object("cycles", recipe.getCyclesPerUnit(), "input", fluidJson(recipe.getInput()), "output", fluidJson(recipe.getOutput())); }
    private static IStillRecipe recipe(int cycles, int input, int output) throws Exception { return (IStillRecipe) type("forestry.factory.recipes.StillRecipe").getConstructor(int.class, FluidStack.class, FluidStack.class).newInstance(cycles, water(input), lava(output)); }
    private static FluidStack water(int amount) { return new FluidStack(FluidRegistry.WATER, amount); }
    private static FluidStack lava(int amount) { return new FluidStack(FluidRegistry.LAVA, amount); }
    private static FluidStack copy(FluidStack value) { return value == null ? null : value.copy(); }
}
