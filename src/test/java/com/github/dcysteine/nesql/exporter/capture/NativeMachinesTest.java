package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.source.Identity;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.oredict.OreDictionary;
import java.lang.reflect.*;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Small conformance suite against the actual, locally remapped IC2 jar. No game is launched. */
public final class NativeMachinesTest {
    public static void main(String[] args) throws Exception { GameTest.run(); }
    public static void run() throws Exception {
        String family = System.getProperty("nesql.nativeFamily", "machines");
        if (family.equals("forestry-work")) { NativeSqueezerWorkTest.run(); return; }
        if (family.equals("canner")) { NativeCannerTest.run(); return; }
        if (family.equals("blast")) { NativeBlastTest.run(); return; }
        if (family.equals("integration")) { NativeIntegrationTest.run(); return; }
        if (family.equals("compression")) { NativeCompressionTest.run(); return; }
        if (family.equals("buildcraft")) { NativeBuildcraftTest.run(); NativeRefiningTest.run(); return; }
        if (family.equals("soul")) { NativeSoulTest.run(); return; }
        if (family.equals("ender-machines")) { NativeEnderMachinesTest.run(); return; }
        if (family.equals("vat")) { NativeVatTest.run(); return; }
        if (family.equals("enchanter")) { NativeEnchanterTest.run(); return; }
        if (family.equals("scrapbox")) { NativeScrapboxTest.run(); return; }
        if (family.equals("inscriber")) { NativeInscriberTest.run(); return; }
        if (family.equals("wireless")) { NativeAeTest.run(); return; }
        if (family.equals("replaced")) { NativeReplacedTest.run(); return; }
        if (family.equals("runic")) { NativeRunicTest.run(); return; }
        if (family.equals("harmony")) { NativeHarmonyTest.run(); return; }
        if (family.equals("solar")) { NativeSolarTest.run(); return; }
        if (family.equals("railcraft")) { NativeRailTest.run(); return; }
        if (family.equals("space")) { NativeSpaceTest.run(); return; }
        if (family.equals("circuits")) { NativeCircuitsTest.run(); return; }
        if (!family.equals("machines")) { NativeFactoriesTest.run(family); return; }
        String prefix = "ic2.neiIntegration.core.recipehandler.";
        require(Recipes.adapter((TemplateRecipeHandler) Class.forName(prefix + "BlockCutterRecipeHandler").newInstance()) != null,
                "Block cutter has no native adapter");
        for (String kind : new String[] {"Macerator", "Extractor", "Compressor", "MetalFormerRecipeHandlerCutting",
                "MetalFormerRecipeHandlerRolling", "MetalFormerRecipeHandlerExtruding", "Centrifuge", "OreWashing", "BlockCutter"}) {
            String name = kind.contains("RecipeHandler") ? kind : kind + "RecipeHandler";
            TemplateRecipeHandler handler = (TemplateRecipeHandler) Class.forName(prefix + name).newInstance();
            require(Recipes.adapter(handler) != null, "Native IC2 handler has no adapter: " + kind);
            int[][] bars = Ic2Recipes.progressBars(handler);
            require(bars.length == (kind.equals("BlockCutter") ? 0 : 1), "IC2 native progress presence changed");
            for (int[] bar : bars) require(Ui.progress(bar[4], bar[5], bar[6], bar[7]).size() > 1, "IC2 native progress was exported as a still image");
            Field clock = Class.forName(prefix + "MachineRecipeHandler").getDeclaredField("ticks"); clock.setAccessible(true);
            clock.setInt(handler, 7);
            Method scene = Ic2Recipes.class.getDeclaredMethod("scene", TemplateRecipeHandler.class, Runnable.class); scene.setAccessible(true);
            try {
                scene.invoke(null, handler, (Runnable) () -> {
                    require(((Integer) MagicApi.field(handler, "ticks")) == 20, "IC2 foreground did not use the native steady-state clock");
                    throw new IllegalStateException("draw interrupted");
                });
                throw new AssertionError("Scene swallowed a draw failure");
            } catch (InvocationTargetException expected) { require(expected.getCause() instanceof IllegalStateException, "Scene changed draw failure"); }
            require(clock.getInt(handler) == 7, "IC2 capture did not restore its owned clock after a failure");
        }
        // Blast-furnace inputs include air; it must not inherit a plain one-input adapter.
        require(Recipes.adapter((TemplateRecipeHandler) Class.forName(prefix + "BlastFurnaceRecipeHandler").newInstance()) != Recipes.Adapter.IC2,
                "Blast furnace was incorrectly treated as a plain item machine");
        Class<?> inputType = Class.forName("ic2.api.recipe.RecipeInputItemStack");
        Class<?> oreType = Class.forName("ic2.api.recipe.RecipeInputOreDict");
        Class<?> outputType = Class.forName("ic2.api.recipe.RecipeOutput");
        ItemStack iron = new ItemStack(Items.iron_ingot, 17), gold = new ItemStack(Items.gold_ingot, 9);
        ItemStack output = new ItemStack(Items.diamond, 3), byproduct = new ItemStack(Items.coal, 2);
        Object input = inputType.getConstructor(ItemStack.class, int.class).newInstance(iron, 4);
        ItemStack tagged = iron.copy(); tagged.setTagInfo("owner", new net.minecraft.nbt.NBTTagString("arbitrary"));
        require((Boolean) inputType.getMethod("matches", ItemStack.class).invoke(input, tagged), "Native IC2 must ignore input NBT");
        NBTTagCompound metadata = new NBTTagCompound(); metadata.setInteger("minHeat", 1500);
        Object products = outputType.getConstructor(NBTTagCompound.class, ItemStack[].class)
                .newInstance(metadata, new ItemStack[] {output, byproduct});
        TemplateRecipeHandler handler = (TemplateRecipeHandler) Class.forName(prefix + "CentrifugeRecipeHandler").newInstance();
        RecipeRow row = row(iron, gold, output, byproduct);
        capture(handler, input, products, row);
        require(row.inputs.size() == 1 && row.outputs.size() == 2, "Native multi-output recipe lost slots");
        com.google.gson.JsonObject choice = row.inputs.get(0).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject();
        require(choice.get("amount").getAsString().equals("4") && choice.getAsJsonObject("rule").get("nbt").getAsBoolean(), "IC2 amount or NBT rule changed");
        require(row.outputs.get(0).getAsJsonObject().get("amount").getAsString().equals("3")
                && row.outputs.get(1).getAsJsonObject().get("amount").getAsString().equals("2"), "Output counts collapsed");
        require(row.record.get("duration").getAsString().equals("500") && row.record.get("energy").getAsString().equals("48")
                && row.properties.has("ic2:minHeat"), "Centrifuge requirements disappeared");
        OreDictionary.registerOre("nesqlMachineInputs", iron); OreDictionary.registerOre("nesqlMachineInputs", gold);
        Object ore = oreType.getConstructor(String.class, int.class).newInstance("nesqlMachineInputs", 6);
        RecipeRow alternatives = row(iron, gold, output, byproduct);
        capture(handler, ore, products, alternatives);
        require(alternatives.inputs.get(0).getAsJsonObject().getAsJsonArray("choices").size() == 2, "Ore alternatives collapsed");
        require(iron.stackSize == 17 && gold.stackSize == 9 && output.stackSize == 3 && metadata.getInteger("minHeat") == 1500,
                "Native layout mutated source stacks or metadata");
        Object unknown = Proxy.newProxyInstance(inputType.getClassLoader(), new Class<?>[] {Class.forName("ic2.api.recipe.IRecipeInput")},
                (proxy, method, args) -> { throw new AssertionError("Unknown predicate should never execute"); });
        try { capture(handler, unknown, products, row(iron, output)); throw new AssertionError("Unknown native predicate was accepted"); }
        catch (Jobs.Fault expected) { require(expected.code.equals("recipe_unsupported"), "Wrong failure for unknown predicate"); }
        TemplateRecipeHandler washing = (TemplateRecipeHandler) Class.forName(prefix + "OreWashingRecipeHandler").newInstance();
        NBTTagCompound water = new NBTTagCompound(); water.setInteger("amount", 1000);
        Object washingOutput = outputType.getConstructor(NBTTagCompound.class, ItemStack[].class).newInstance(water, new ItemStack[] {output, byproduct});
        RecipeRow washed = row(iron, output, byproduct); capture(washing, input, washingOutput, washed);
        com.google.gson.JsonObject fluidChoice = washed.inputs.get(1).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject();
        require(washed.inputs.get(1).getAsJsonObject().get("kind").getAsString().equals("fluid")
                && fluidChoice.get("id").getAsString().equals(Identity.fluid("water", null))
                && fluidChoice.get("amount").getAsString().equals("1000") && fluidChoice.getAsJsonObject("rule").get("nbt").getAsBoolean(),
                "Ore washing lost native water consumption or required water NBT");
        require(washed.record.get("duration").getAsString().equals("500") && washed.record.get("energy").getAsString().equals("16"), "Ore washing lost native power/time");
        require(water.getInteger("amount") == 1000, "Ore washing mutated metadata");
        water.setInteger("amount", 0);
        RecipeRow dry = row(iron, output, byproduct); capture(washing, input, washingOutput, dry);
        require(dry.inputs.size() == 1, "Zero-water recipe gained a fake keep-one fluid input");
        cutter(input, output, byproduct);
        System.out.println("Native IC2: nine handlers, native matching/counts, alternatives, multi-output, water and blade requirements, immutable projection, unknown predicate rejection passed");
        furnaces();
        extreme();
    }
    private static void cutter(Object input, ItemStack output, ItemStack byproduct) throws Exception {
        TemplateRecipeHandler handler = (TemplateRecipeHandler) Class.forName("ic2.neiIntegration.core.recipehandler.BlockCutterRecipeHandler").newInstance();
        Class<?> outputType = Class.forName("ic2.api.recipe.RecipeOutput");
        ItemStack ingredient = new ItemStack(Items.iron_ingot);
        for (int hardness : new int[] {-1, 0, 3, 6, 9}) {
            NBTTagCompound metadata = new NBTTagCompound(); metadata.setInteger("hardness", hardness);
            Object products = outputType.getConstructor(NBTTagCompound.class, ItemStack[].class).newInstance(metadata, new ItemStack[] {output, byproduct});
            RecipeRow row = row(ingredient, output, byproduct);
            capture(handler, input, products, row);
            require(row.record.get("duration").getAsInt() == 900 && row.record.get("energy").getAsInt() == 48,
                    "Block cutter base time/power changed");
            require(row.inputs.size() == 1 && row.outputs.size() == 2
                    && row.properties.getAsJsonObject("ic2:bladeHardness").getAsJsonObject("value").get("value").getAsInt() == hardness,
                    "Block cutter blade attachment became consumption or lost its native threshold");
            require(metadata.getInteger("hardness") == hardness, "Block cutter display changed source metadata");
        }
        Object absent = outputType.getConstructor(NBTTagCompound.class, ItemStack[].class).newInstance(null, new ItemStack[] {output});
        try { capture(handler, input, absent, row(ingredient, output)); throw new AssertionError("Metadata-free block cutter recipe accepted"); }
        catch (Jobs.Fault expected) { require(expected.code.equals("recipe_unsupported"), "Wrong missing cutter metadata failure"); }
        NBTTagCompound empty = new NBTTagCompound();
        Object zero = outputType.getConstructor(NBTTagCompound.class, ItemStack[].class).newInstance(empty, new ItemStack[] {output});
        RecipeRow defaulted = row(ingredient, output); capture(handler, input, zero, defaulted);
        require(defaulted.properties.getAsJsonObject("ic2:bladeHardness").getAsJsonObject("value").get("value").getAsInt() == 0,
                "Native default-zero hardness changed");
        Class<?> machineType = Class.forName("ic2.core.block.machine.tileentity.TileEntityBlockCutter");
        machineType.getMethod("init").invoke(null);
        Object registry = Class.forName("ic2.api.recipe.Recipes").getField("blockcutter").get(null);
        Method add = registry.getClass().getMethod("addRecipe", Class.forName("ic2.api.recipe.IRecipeInput"), NBTTagCompound.class, ItemStack[].class);
        NBTTagCompound hardness = new NBTTagCompound(); hardness.setInteger("hardness", 6);
        add.invoke(registry, input, hardness, new ItemStack[] {output});
        Object machine = machineType.newInstance();
        Object inputSlot = MagicApi.field(machine, "inputSlot"), cutterSlot = MagicApi.field(machine, "cutterSlot");
        inputSlot.getClass().getMethod("put", ItemStack.class).invoke(inputSlot, new ItemStack(Items.iron_ingot, 4));
        require(machineType.getMethod("getOutput").invoke(machine) == null, "Native cutter works without a blade");
        // Own a native blade without ItemIC2's global item/network registration. Keep its real getter.
        Class<?> bladeType = Class.forName("ic2.core.item.resources.ItemBlockCuttingBlade");
        Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); unsafeField.setAccessible(true);
        Item blade = (Item) ((sun.misc.Unsafe) unsafeField.get(null)).allocateInstance(bladeType);
        Field delegate = Item.class.getField("delegate"); delegate.setAccessible(true);
        delegate.set(blade, new cpw.mods.fml.common.registry.RegistryDelegate.Delegate<>(blade, Item.class));
        Field strength = bladeType.getDeclaredField("hardness"); strength.setAccessible(true);
        cutterSlot.getClass().getMethod("put", ItemStack.class).invoke(cutterSlot, new ItemStack(blade));
        for (int rating : new int[] {3, 6, 9}) {
            strength.setInt(blade, rating);
            require((machineType.getMethod("getOutput").invoke(machine) != null) == (rating >= 6), "Native blade threshold disagrees with exported requirement");
        }
    }
    private static void extreme() throws Exception {
        String prefix = "fox.spiteful.avaritia.";
        for (String kind : new String[] {"Shaped", "Shapeless"}) {
            TemplateRecipeHandler handler = (TemplateRecipeHandler) Class.forName(prefix + "compat.nei.Extreme" + kind + "RecipeHandler").newInstance();
            require(Recipes.adapter(handler) != null, "Avaritia " + kind + " has no native adapter");
            ItemStack tagged = new ItemStack(Items.apple, 9); tagged.setTagInfo("owner", new net.minecraft.nbt.NBTTagString("required"));
            ItemStack paper = new ItemStack(Items.paper, 6), output = new ItemStack(Items.diamond, 4);
            Class<?> type = Class.forName(prefix + "crafting.Extreme" + kind + "Recipe");
            net.minecraft.item.crafting.IRecipe recipe = (net.minecraft.item.crafting.IRecipe) (kind.equals("Shaped")
                    ? type.getConstructor(int.class, int.class, ItemStack[].class, ItemStack.class).newInstance(2, 2, new ItemStack[] {tagged, null, paper, tagged}, output)
                    : type.getConstructor(ItemStack.class, List.class).newInstance(output, Arrays.asList(tagged, paper, tagged)));
            net.minecraft.inventory.InventoryCrafting inventory = new net.minecraft.inventory.InventoryCrafting(new net.minecraft.inventory.Container() {
                public boolean canInteractWith(net.minecraft.entity.player.EntityPlayer player) { return true; }
            }, 9, 9);
            inventory.setInventorySlotContents(0, new ItemStack(Items.apple)); inventory.setInventorySlotContents(kind.equals("Shaped") ? 9 : 1, paper.copy());
            inventory.setInventorySlotContents(kind.equals("Shaped") ? 10 : 2, tagged.copy());
            require(!recipe.matches(inventory, null), "Native tagged recipe accepted missing NBT");
            inventory.setInventorySlotContents(0, tagged.copy()); require(recipe.matches(inventory, null), "Native recipe fixture does not match");
            Method capture = Class.forName("com.github.dcysteine.nesql.exporter.capture.ExtremeRecipes")
                    .getDeclaredMethod("capture", net.minecraft.item.crafting.IRecipe.class, TemplateRecipeHandler.class, RecipeRow.class);
            capture.setAccessible(true); RecipeRow row = row(tagged, paper, output);
            require((Boolean) capture.invoke(null, recipe, handler, row), "Valid native recipe was excluded");
            require(row.inputs.size() == 3 && row.outputs.get(0).getAsJsonObject().get("amount").getAsString().equals("4"), "Avaritia lost cells or output count");
            com.google.gson.JsonObject choice = row.inputs.get(0).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject();
            require(choice.get("amount").getAsString().equals("1") && choice.getAsJsonObject("rule").get("kind").getAsString().equals("exact"), "Avaritia must preserve required NBT with an exact rule and consume one item");
            if (kind.equals("Shaped")) require(row.record.getAsJsonObject("grid").getAsJsonArray("cells").get(1).isJsonNull()
                    && row.record.getAsJsonObject("grid").get("mirror").getAsBoolean(), "Avaritia grid holes/mirroring lost");
            require(tagged.stackSize == 9 && paper.stackSize == 6, "Avaritia mutated native source quantities");
            if (kind.equals("Shaped")) {
                Class<?> ore = Class.forName(prefix + "crafting.ExtremeShapedOreRecipe");
                Object mixed = ore.getConstructor(ItemStack.class, Object[].class, int.class, int.class)
                        .newInstance(output, new Object[] {new ArrayList<>(Arrays.asList(tagged, paper)), tagged, null, paper}, 2, 2);
                ore.getMethod("setMirrored", boolean.class).invoke(mixed, false);
                RecipeRow mixedRow = row(tagged, paper, output); capture.invoke(null, mixed, handler, mixedRow);
                require(mixedRow.inputs.get(0).getAsJsonObject().getAsJsonArray("choices").size() == 2
                        && mixedRow.inputs.get(0).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject().getAsJsonObject("rule").get("nbt").getAsBoolean()
                        && mixedRow.inputs.get(1).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject().getAsJsonObject("rule").get("kind").getAsString().equals("exact")
                        && !mixedRow.record.getAsJsonObject("grid").get("mirror").getAsBoolean(), "Extreme ore groups must ignore tags while direct stacks keep required NBT");
                Object empty = ore.getConstructor(ItemStack.class, Object[].class, int.class, int.class)
                        .newInstance(output, new Object[] {new ArrayList<>()}, 1, 1);
                require(!(Boolean) capture.invoke(null, empty, handler, row(output)), "Impossible ore predicate was exported as a recipe");
            } else {
                net.minecraftforge.oredict.ShapelessOreRecipe ore = new net.minecraftforge.oredict.ShapelessOreRecipe(output, tagged, paper);
                RecipeRow mixedRow = row(tagged, paper, output); capture.invoke(null, ore, handler, mixedRow);
                require(mixedRow.inputs.get(0).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject().getAsJsonObject("rule").get("nbt").getAsBoolean(), "Forge shapeless input unexpectedly requires NBT");
            }
        }
        System.out.println("Native Avaritia: shaped/shapeless NBT matching, grid holes, mirroring, unit consumption, output count and source ownership passed");
    }
    @SuppressWarnings("unchecked")
    private static void furnaces() throws Exception {
        java.io.File root = new java.io.File("build/native-tests/etfuturum-config").getAbsoluteFile();
        root.mkdirs();
        cpw.mods.fml.relauncher.ReflectionHelper.setPrivateValue(cpw.mods.fml.relauncher.FMLInjectionData.class, null, root, "minecraftHome");
        net.minecraft.launchwrapper.Launch.minecraftHome = root;
        for (String kind : new String[] {"Smoker", "BlastFurnace"}) {
            TemplateRecipeHandler handler = (TemplateRecipeHandler) Class.forName("ganymedes01.etfuturum.compat.nei." + kind + "RecipeHandler").newInstance();
            require(Recipes.adapter(handler) != null, "Et Futurum " + kind + " has no adapter");
            Object registry = Class.forName("ganymedes01.etfuturum.recipes." + kind + "Recipes").getMethod("smelting").invoke(null);
            Method add = registry.getClass().getMethod("addRecipe", ItemStack.class, ItemStack.class, float.class);
            ItemStack apple = new ItemStack(Items.apple, 11), carrot = new ItemStack(Items.carrot, 7);
            ItemStack result = new ItemStack(Items.diamond, 3);
            add.invoke(registry, apple, result, 0.5f); add.invoke(registry, carrot, result, 0.5f);
            registry.getClass().getMethod("removeRecipe", ItemStack.class).invoke(registry, carrot);
            Class<?> adapter = Class.forName("com.github.dcysteine.nesql.exporter.capture.SmeltingRecipes");
            Method enumerate = adapter.getDeclaredMethod("enumerate", Object.class, Map.class); enumerate.setAccessible(true);
            Map<ItemStack, ItemStack> inherited = new LinkedHashMap<>(); inherited.put(apple, new ItemStack(Items.bread));
            // Exercise the native filter directly so missing test-only bootstrap dependencies retain their cause.
            registry.getClass().getMethod("canAdd", ItemStack.class, ItemStack.class).invoke(registry, apple, inherited.get(apple));
            List<?> recipes = (List<?>) enumerate.invoke(null, registry, inherited);
            require(recipes.size() == 1, "Et Futurum override/blacklist produced extra recipes");
            RecipeRow row = row(apple, result);
            Method capture = adapter.getDeclaredMethod("capture", codechicken.nei.recipe.FurnaceRecipeHandler.class, Map.Entry.class, Object.class, RecipeRow.class);
            capture.setAccessible(true); capture.invoke(null, handler, recipes.get(0), registry, row);
            require(row.inputs.size() == 1 && row.outputs.size() == 1 && row.record.get("duration").getAsString().equals("100"), "Et Futurum captured fuel as output or lost native duration");
            require(row.outputs.get(0).getAsJsonObject().get("amount").getAsString().equals("3"), "Et Futurum override result/count was lost");
            require(apple.stackSize == 11 && result.stackSize == 3, "Smelting layout mutated registered recipe stacks");
            ((Map<?, ?>) registry.getClass().getField("smeltingList").get(registry)).clear();
            // Native wildcard lookup precedes exact entries; a specific blacklist still wins.
            ItemStack any = new ItemStack(Items.paper, 1, OreDictionary.WILDCARD_VALUE);
            ItemStack first = new ItemStack(Items.paper, 1, 0), second = new ItemStack(Items.paper, 1, 1);
            List<ItemStack> previous = new ArrayList<>(codechicken.nei.ItemList.itemMap.get(Items.paper));
            try {
                codechicken.nei.ItemList.itemMap.replaceValues(Items.paper, Arrays.asList(first, second));
                add.invoke(registry, any, result, 0.5f);
                add.invoke(registry, second, new ItemStack(Items.gold_ingot), 0.5f);
                registry.getClass().getMethod("removeRecipe", ItemStack.class).invoke(registry, first);
                List<Map.Entry<ItemStack, ItemStack>> expanded = (List<Map.Entry<ItemStack, ItemStack>>) enumerate.invoke(null, registry, Collections.emptyMap());
                require(expanded.size() == 1 && expanded.get(0).getKey().getItemDamage() == 1,
                        "Wildcard smelting retained a blacklisted subtype or duplicated a native key");
                ItemStack nativeOutput = (ItemStack) registry.getClass().getMethod("getSmeltingResult", ItemStack.class).invoke(registry, second.copy());
                require(ItemStack.areItemStacksEqual(nativeOutput, expanded.get(0).getValue()), "Smelting disagrees with native wildcard precedence");
                RecipeRow exact = row(second, result); capture.invoke(null, handler, expanded.get(0), registry, exact);
                require(!exact.inputs.get(0).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject().getAsJsonObject("rule").get("meta").getAsBoolean(),
                        "Filtered smelting candidate must not regain wildcard metadata matching");
            } finally {
                codechicken.nei.ItemList.itemMap.replaceValues(Items.paper, previous);
                ((Map<?, ?>) registry.getClass().getField("smeltingList").get(registry)).clear();
            }
        }
        System.out.println("Native Et Futurum: overrides, blacklist, exact output counts, duration, immutable layout passed");
    }
    private static void capture(TemplateRecipeHandler handler, Object input, Object output, RecipeRow row) throws Exception {
        Method method = Class.forName("com.github.dcysteine.nesql.exporter.capture.Ic2Recipes")
                .getDeclaredMethod("capture", TemplateRecipeHandler.class, Object.class, Object.class, RecipeRow.class);
        method.setAccessible(true);
        try { method.invoke(null, handler, input, output, row); }
        catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof Error) throw (Error) failure.getCause();
            throw (Exception) failure.getCause();
        }
    }
    @SuppressWarnings("unchecked")
    private static RecipeRow row(ItemStack... stacks) {
        Facts facts = new Facts("en_US");
        Set<String> known = (Set<String>) MagicApi.field(facts, "items");
        for (ItemStack stack : stacks) known.add(Identity.item(Item.itemRegistry.getNameForObject(stack.getItem()), stack.getItemDamage(), TypedNbt.encode(stack.getTagCompound())));
        return new RecipeRow(facts, object("owner", "IC2", "handler", "native", "key", "native"), "category_test", 0);
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
