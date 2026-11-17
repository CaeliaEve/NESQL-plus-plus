package com.github.dcysteine.nesql.exporter.capture;

import com.github.dcysteine.nesql.exporter.source.Identity;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import cpw.mods.fml.relauncher.ReflectionHelper;
import gregtech.api.recipe.*;
import gregtech.api.util.GTRecipe;
import gregtech.nei.GTNEIDefaultHandler;
import java.lang.reflect.Field;
import java.util.*;
import java.util.function.Function;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.*;

import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Actual installed API with owned native registry records; no game/window/cache is constructed. */
final class NativeGtSemanticTest {
    static void run() throws Exception {
        boolean ready = codechicken.nei.ItemList.loadFinished;
        List<ItemStack> panel = codechicken.nei.ItemList.items;
        try {
            codechicken.nei.ItemList.loadFinished = true;
            codechicken.nei.ItemList.items = new ArrayList<>(Arrays.asList(new ItemStack(Items.paper),new ItemStack(Items.diamond)));
            runOwned();
        } finally { codechicken.nei.ItemList.loadFinished = ready; codechicken.nei.ItemList.items = panel; }
    }
    private static void runOwned() throws Exception {
        if (!GTRecipe.class.getProtectionDomain().getCodeSource().getLocation().toString().contains("/native-tests/gregtech-space.jar!"))
            throw new AssertionError("Pinned installed GT JAR required");
        RecipeMapBackend backend = allocate(RecipeMapBackend.class);
        RecipeMapFrontend frontend = allocate(RecipeMapFrontend.class);
        NEIRecipeProperties properties = allocate(NEIRecipeProperties.class);
        set(properties, "comparator", Comparator.comparingInt((GTRecipe recipe) -> recipe.mDuration));
        set(properties, "itemInputsGetter", (Function<GTRecipe, ItemStack[]>) recipe -> recipe.mInputs);
        set(properties, "itemOutputsGetter", (Function<GTRecipe, ItemStack[]>) recipe -> recipe.mOutputs);
        set(properties, "fluidInputsGetter", (Function<GTRecipe, FluidStack[]>) recipe -> recipe.mFluidInputs);
        set(properties, "fluidOutputsGetter", (Function<GTRecipe, FluidStack[]>) recipe -> recipe.mFluidOutputs);
        BasicUIProperties ui = allocate(BasicUIProperties.class);
        set(ui, "amperage", 2);
        // No native slots or geometry exist. These UI capacity values must not hide semantics.
        set(ui, "maxItemInputs", 0); set(ui, "maxItemOutputs", 0);
        set(ui, "maxFluidInputs", 0); set(ui, "maxFluidOutputs", 0);
        set(frontend, "uiProperties", ui); set(frontend, "neiProperties", properties);
        RecipeMap<?> map = allocate(RecipeMap.class);
        set(map, "backend", backend); set(map, "frontend", frontend); set(map, "unlocalizedName", "fixture.gt.semantic");
        RecipeCategory primary = category(map, "primary", RecipeCategorySetting.ENABLE);
        RecipeCategory merged = category(map, "merged", RecipeCategorySetting.MERGE);
        RecipeCategory hidden = category(map, "hidden", RecipeCategorySetting.HIDE);
        GTNEIDefaultHandler registrationOne=allocate(GTNEIDefaultHandler.class),registrationTwo=allocate(GTNEIDefaultHandler.class);
        set(registrationOne,"recipeCategory",primary);set(registrationTwo,"recipeCategory",primary);
        require(RegistrationBindings.read(registrationOne).same(RegistrationBindings.read(registrationTwo)),
                "Same native GT category lost its shared registration proof");
        set(registrationTwo,"recipeCategory",merged);
        require(!RegistrationBindings.read(registrationOne).same(RegistrationBindings.read(registrationTwo)),
                "Different native GT categories were declared equivalent");
        set(map, "defaultRecipeCategory", primary);
        Map<RecipeCategory, Collection<GTRecipe>> categories = new LinkedHashMap<>();
        GTRecipe base = recipe(20), mergedRecipe = recipe(10), hiddenRecipe = recipe(1), notShown = recipe(2);
        notShown.mHidden = true;
        categories.put(primary, new ArrayList<>(Arrays.asList(base, notShown)));
        categories.put(merged, new ArrayList<>(Collections.singletonList(mergedRecipe)));
        categories.put(hidden, new ArrayList<>(Collections.singletonList(hiddenRecipe)));
        set(backend, "recipesByCategory", categories);
        int[] reload = {0};
        GtRecipeSelection selection = new GtRecipeSelection(primary, () -> reload[0]);
        require(selection.size() == 2 && selection.recipe(0) == mergedRecipe && selection.recipe(1) == base,
                "Native merge/hidden filtering or stable comparator order changed");
        selection.finishVerification();
        base.mOutputs[2].stackSize = 13;
        reject("recipe_changed", () -> selection.recipe(1));
        reject("recipe_changed", selection::finishVerification);
        base.mOutputs[2].stackSize = 9;
        reload[0] = 1; reject("recipe_changed", selection::verify); reload[0] = 0;
        set(merged, "settingSupplier", (java.util.function.Supplier<RecipeCategorySetting>) () -> RecipeCategorySetting.HIDE);
        reject("recipe_changed", selection::verify);
        set(merged, "settingSupplier", (java.util.function.Supplier<RecipeCategorySetting>) () -> RecipeCategorySetting.MERGE);
        selection.finishVerification(); selection.close(); reject("recipe_closed", selection::size);
        GtRecipeSelection empty = new GtRecipeSelection(hidden, () -> reload[0]);
        require(empty.size() == 0, "Ready hidden category gained a fabricated recipe");
        empty.finishVerification(); empty.close();

        GTNEIDefaultHandler handler = allocate(GTNEIDefaultHandler.class);
        set(handler, "recipeMap", map); set(handler, "recipeCategory", primary);
        require(ReflectionHelper.getPrivateValue(GTNEIDefaultHandler.class, handler, "modularWindow") == null,
                "Fixture unexpectedly owns a GUI");
        Facts facts = new Facts("en_US");
        Set<String> known = ReflectionHelper.getPrivateValue(Facts.class, facts, "items");
        for (net.minecraft.item.Item item : Arrays.asList(Items.paper, Items.diamond, Items.stick))
            known.add(Identity.item(net.minecraft.item.Item.itemRegistry.getNameForObject(item), 0, null));
        GtRecipes reader = new GtRecipes(handler, () -> reload[0]);
        codechicken.nei.ItemList.loadFinished = false;
        reject("recipe_changed", reader::verify);
        reject("recipe_unready", () -> new GtRecipes(handler, () -> reload[0]));
        codechicken.nei.ItemList.loadFinished = true;
        RecipeRow row = new RecipeRow(facts, object("owner", "fixture", "handler", "gt", "key", "fixture.gt.semantic/primary"), "fixture", 1);
        require(reader.captureRows(1, row).size() == 1 && row.inputs.size() == 2 && row.outputs.size() == 3,
                "Sparse/over-capacity semantic inputs or outputs disappeared");
        require(row.outputs.get(1).getAsJsonObject().get("slot").getAsInt() == 2
                && row.outputs.get(1).getAsJsonObject().get("amount").getAsString().equals("9")
                && row.outputs.get(2).getAsJsonObject().get("amount").getAsString().equals("125")
                && row.inputs.get(1).getAsJsonObject().get("slot").getAsInt() == 65535,
                "Native indices, quantities or special-slot ownership changed");
        reader.finishVerification(); reader.close();
        GTRecipe cost = recipe(10);
        cost.mInputs = new ItemStack[0]; cost.mSpecialItems = null;
        cost.mFluidInputs = new FluidStack[] {new FluidStack(FluidRegistry.WATER, 0)};
        require(cost.maxParallelCalculatedByInputs(4, new FluidStack[0], new ItemStack[0]) == 4
                && cost.maxParallelCalculatedByInputs(4, null, new ItemStack[0]) == 0,
                "Pinned GT zero-cost calculation changed its empty/null distinction");
        cost.mFluidInputs[0].amount = 3;
        cost.mFluidInputs[0].tag = new net.minecraft.nbt.NBTTagCompound(); cost.mFluidInputs[0].tag.setInteger("batch", 7);
        require(cost.maxParallelCalculatedByInputs(4, new FluidStack[] {new FluidStack(FluidRegistry.WATER, 3)}, new ItemStack[0]) == 1,
                "Pinned GT fluid cost unexpectedly requires NBT identity");
        cost.mFluidInputs[0].amount = 0;
        categories.put(primary, new ArrayList<>(Collections.singletonList(cost)));
        categories.put(merged, new ArrayList<>());
        for (String mapName : Arrays.asList("fixture.gt.semantic", "gtpp.recipe.multiblockrockbreaker")) {
            set(map, "unlocalizedName", mapName);
            if (mapName.startsWith("gtpp")) set(properties, "fluidInputsGetter", (Function<GTRecipe, FluidStack[]>) recipe ->
                    new FluidStack[] {new FluidStack(FluidRegistry.WATER, 0),new FluidStack(FluidRegistry.LAVA, 0)});
            GtRecipes zeroReader = new GtRecipes(handler, () -> reload[0]);
            RecipeRow zeroRow = new RecipeRow(facts,
                    object("owner", "gregtech", "handler", GTNEIDefaultHandler.class.getName(), "key", mapName + "/primary"), "fixture", 0);
            zeroReader.captureRows(0, zeroRow);
            zeroRow.record.addProperty("category", Identity.origin("category", zeroRow.record.getAsJsonObject("source")));
            require(zeroRow.inputs.get(0).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject().get("amount").getAsString().equals("0"),
                    "GT native zero input was invented as one mB");
            NativeContractFixture.writeOwned(mapName.startsWith("gtpp") ? "gt-presence-fluid" : "gt-zero-fluid", zeroRow,
                    Arrays.asList(new ItemStack(Items.paper),new ItemStack(Items.diamond)), cost.mFluidInputs[0], cost.mFluidOutputs[0],
                    new FluidStack(FluidRegistry.WATER, 0),new FluidStack(FluidRegistry.LAVA, 0));
            zeroReader.finishVerification(); zeroReader.close();
        }
        System.out.println("GT semantic: native category merge/order/hidden, no GUI/cache, sparse full slots, special inputs, reload/count/definition drift and closure passed");
    }

    private static GTRecipe recipe(int duration) throws Exception {
        GTRecipe recipe = allocate(GTRecipe.class);
        recipe.mInputs = new ItemStack[] {new ItemStack(Items.paper, 3)};
        recipe.mOutputs = new ItemStack[] {new ItemStack(Items.paper, 3), null, new ItemStack(Items.diamond, 9)};
        recipe.mFluidInputs = new FluidStack[0]; recipe.mFluidOutputs = new FluidStack[] {new FluidStack(FluidRegistry.WATER, 125)};
        recipe.mSpecialItems = new ItemStack(Items.stick, 1); recipe.mDuration = duration; recipe.mEUt = 8; recipe.mEnabled = true;
        return recipe;
    }
    private static RecipeCategory category(RecipeMap<?> map, String name, RecipeCategorySetting setting) throws Exception {
        RecipeCategory result = allocate(RecipeCategory.class);
        set(result, "recipeMap", map); set(result, "unlocalizedName", name);
        set(result, "settingSupplier", (java.util.function.Supplier<RecipeCategorySetting>) () -> setting);
        return result;
    }
    private static <T> T allocate(Class<T> type) throws Exception {
        Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); field.setAccessible(true);
        return type.cast(((sun.misc.Unsafe) field.get(null)).allocateInstance(type));
    }
    private static void set(Object target, String name, Object value) throws Exception {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try { Field field = type.getDeclaredField(name); field.setAccessible(true); field.set(target, value); return; }
            catch (NoSuchFieldException ignored) {}
        }
        throw new NoSuchFieldException(name);
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void reject(String code, Runnable action) {
        try { action.run(); throw new AssertionError("Expected " + code); }
        catch (Jobs.Fault expected) { require(code.equals(expected.code), "Wrong failure: " + expected.code); }
    }
}
