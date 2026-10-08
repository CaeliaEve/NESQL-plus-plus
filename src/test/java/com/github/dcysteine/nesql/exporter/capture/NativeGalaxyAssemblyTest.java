package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.source.Identity;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.*;
import net.minecraft.init.Items;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.*;
import net.minecraft.nbt.NBTTagInt;
import net.minecraftforge.oredict.ShapelessOreRecipe;
import java.lang.reflect.*;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Catches vanilla-crafting substitution, repair-branch interception, and leaked static NEI clocks. */
final class NativeGalaxyAssemblyTest {
    static void run() throws Exception {
        Class<?> adapter;
        try { adapter = Class.forName("com.github.dcysteine.nesql.exporter.capture.GalaxyAssemblyRecipes"); }
        catch (ClassNotFoundException missing) { throw new AssertionError("GalaxySpace assembly lacks its native registry/repair priority adapter", missing); }
        Class<?> registryType = Class.forName("galaxyspace.core.recipe.AssemblyRecipes");
        @SuppressWarnings("unchecked") List<IRecipe> registry = (List<IRecipe>) registryType.getMethod("getRecipeList").invoke(null);
        registry.clear();
        ItemStack milk = new ItemStack(Items.milk_bucket, 23), coal = new ItemStack(Items.coal, 17), gold = new ItemStack(Items.gold_ingot, 11);
        ItemStack output = new ItemStack(Items.emerald, 70); output.setTagInfo("native", new NBTTagInt(31));
        registry.add(new ShapelessRecipes(new ItemStack(Items.carrot), Collections.singletonList(coal.copy())));
        registry.add(new ShapelessOreRecipe(new ItemStack(Items.diamond), coal.copy()));
        registry.add(new ShapedRecipes(1, 1, new ItemStack[]{coal.copy()}, new ItemStack(Items.apple)));
        ShapedRecipes fixed = new ShapedRecipes(2, 2, new ItemStack[]{milk.copy(), null, coal.copy(), gold.copy()}, output);
        Field copyNbt = ShapedRecipes.class.getDeclaredField("field_92101_f"); copyNbt.setAccessible(true); copyNbt.setBoolean(fixed, true);
        registry.add(fixed);
        TemplateRecipeHandler handler = (TemplateRecipeHandler) Class.forName("galaxyspace.core.nei.AssemblyMachineRecipeHandler").newInstance();
        Constructor<?> ctor = adapter.getDeclaredConstructor(TemplateRecipeHandler.class, List.class, boolean.class); ctor.setAccessible(true);
        RegistryRecipes capture = (RegistryRecipes) ctor.newInstance(handler, registry, false);
        require(capture.size() == 4, "Assembly native registry entries disappeared before reachability classification");
        Facts facts = new Facts("en_US");
        @SuppressWarnings("unchecked") Set<String> known = (Set<String>) MagicApi.field(facts, "items");
        for (ItemStack stack : Arrays.asList(milk, coal, gold, output, new ItemStack(Items.carrot), new ItemStack(Items.diamond), new ItemStack(Items.apple))) known.add(id(stack));
        RecipeRow omitted = row(facts, handler, 0);
        require(!capture.capture(0, omitted), "Native-unrecognized ShapelessRecipes was falsely exported as machine gameplay");
        RecipeRow row = row(facts, handler, 3); require(capture.capture(3, row), "Reachable assembly recipe was omitted");
        JsonObject process = row.record.getAsJsonObject("process");
        require(process.get("kind").getAsString().equals("galaxyspace-assembly") && process.getAsJsonArray("earlier").size() == 2
                && process.getAsJsonArray("earlier").get(0).getAsJsonObject().get("grid").isJsonNull(), "Assembly native first-match selectors include unreachable classes or omit native shapeless priority");
        require(row.record.get("duration").isJsonNull() && row.record.get("energy").isJsonNull(), "Supply-tier timing became a fixed machine time or energy");
        require(row.inputs.size() == 3 && row.outputs.get(0).getAsJsonObject().get("amount").getAsInt() == 70
                && row.outputs.get(0).getAsJsonObject().get("id").getAsString().equals(id(output)), "Initially-empty native output lost amount or fixed NBT");
        for (JsonElement input : row.inputs) for (JsonElement raw : input.getAsJsonObject().getAsJsonArray("choices")) {
            JsonObject choice = raw.getAsJsonObject();
            require(choice.get("amount").getAsInt() == 1 && choice.getAsJsonArray("returns").size() == 0 && choice.getAsJsonObject("rule").get("nbt").getAsBoolean(),
                    "Assembly uses recipe template counts, container callbacks or input NBT");
        }
        JsonObject grid = row.record.getAsJsonObject("grid");
        require(grid.get("width").getAsInt() == 2 && grid.get("height").getAsInt() == 2 && grid.get("mirror").getAsBoolean()
                && grid.getAsJsonArray("cells").get(1).isJsonNull(), "Assembly native translated/mirrored shaped predicate changed");
        List<PositionedStack> positions = handler.arecipes.get(0).getIngredients();
        require(positions.size() == 3 && positions.get(0).relx == 21 && positions.get(0).rely == 26
                && positions.get(1).relx == 57 && positions.get(1).rely == 26 && positions.get(2).relx == 21 && positions.get(2).rely == 44,
                "Assembly changed the native NEI projection that indexes display columns by three");
        Method find = registryType.getMethod("findMatchingRecipe", IInventory.class);
        for (boolean mirror : new boolean[]{false, true}) {
            InventoryBasic inventory = new InventoryBasic("assembly-test", true, 9);
            ItemStack offeredMilk = milk.copy(); offeredMilk.setTagInfo("offered", new NBTTagInt(99));
            inventory.setInventorySlotContents(mirror ? 1 : 0, offeredMilk);
            inventory.setInventorySlotContents(mirror ? 4 : 3, coal.copy()); inventory.setInventorySlotContents(mirror ? 3 : 4, gold.copy());
            require(ItemStack.areItemStacksEqual((ItemStack) find.invoke(null, inventory), output), "Actual assembly matching or fixed-output behavior differs from captured grid/NBT");
        }
        InventoryBasic priority = new InventoryBasic("priority", true, 9); priority.setInventorySlotContents(8, coal.copy());
        require(((ItemStack) find.invoke(null, priority)).getItem() == Items.diamond, "Native first-match fixture changed");
        cpw.mods.fml.common.ModAPIManager.INSTANCE.registerDataTableAndParseAPI(new cpw.mods.fml.common.discovery.ASMDataTable());
        Class<?> tileType = Class.forName("galaxyspace.core.tile.machine.TileEntityAssemblyMachine");
        Object tile = tileType.newInstance();
        IInventory matrix = (IInventory) tileType.getField("testCraftMatrix").get(tile);
        matrix.setInventorySlotContents(0, milk.copy()); matrix.setInventorySlotContents(3, coal.copy()); matrix.setInventorySlotContents(4, gold.copy());
        tileType.getMethod("updateInput").invoke(tile);
        Method finish = tileType.getDeclaredMethod("compressIntoSlot", int.class); finish.setAccessible(true); finish.invoke(tile, 1);
        ItemStack finished = ((IInventory) tile).getStackInSlot(1);
        require(ItemStack.areItemStacksEqual(finished, output) && finished.stackSize == 70,
                "Actual initially-empty assembly output was clamped or lost fixed NBT");
        require(matrix.getStackInSlot(0).stackSize == 22 && matrix.getStackInSlot(3).stackSize == 16 && matrix.getStackInSlot(4).stackSize == 10,
                "Native assembly completion did not decrement each occupied input by one");
        Method texture = adapter.getDeclaredMethod("texture"); texture.setAccessible(true);
        require(texture.invoke(null).equals("galaxyspace:textures/gui/assembly_machine.png") && !texture.invoke(null).equals(handler.getGuiTexture()), "Assembly retained the native getGuiTexture typo instead of the drawn atlas");
        Field clock = handler.getClass().getDeclaredField("ticksPassed"); clock.setAccessible(true); clock.setInt(null, 41);
        Method scene = adapter.getDeclaredMethod("scene", TemplateRecipeHandler.class, Runnable.class); scene.setAccessible(true);
        try {
            scene.invoke(null, handler, (Runnable) () -> {
                try { require(clock.getInt(null) == 0, "Assembly still background includes animation pixels"); clock.setInt(null, 67); }
                catch (IllegalAccessException error) { throw new AssertionError(error); }
                throw new IllegalStateException("draw failed");
            });
            throw new AssertionError("Assembly scene swallowed draw failure");
        } catch (InvocationTargetException expected) { require(expected.getCause() instanceof IllegalStateException, "Assembly changed native draw failure"); }
        require(clock.getInt(null) == 41, "Assembly leaked shared native animation clock after failure");
        Method frames = adapter.getDeclaredMethod("frames", boolean.class); frames.setAccessible(true);
        JsonArray arrow = (JsonArray) frames.invoke(null, false), flame = (JsonArray) frames.invoke(null, true);
        require(ticks(arrow) == 70 && ticks(flame) == 70 && arrow.size() == 54 && flame.size() == 2
                && flame.get(0).getAsJsonObject().get("ticks").getAsInt() == 27 && flame.get(0).getAsJsonObject().getAsJsonArray("areas").size() == 0,
                "Assembly native 70-tick/27-tick animation thresholds changed");
        capture.verify(); output.stackSize = 69;
        try { capture.verify(); throw new AssertionError("Assembly output drift was ignored"); }
        catch (Jobs.Fault expected) { require(expected.code.equals("recipe_changed"), "Wrong assembly drift failure"); }
        output.stackSize = 70;
        ShapedRecipes repair = new ShapedRecipes(2, 1, new ItemStack[]{new ItemStack(Items.iron_sword, 9, 32767), new ItemStack(Items.iron_sword, 4, 32767)}, new ItemStack(Items.apple));
        registry.clear(); registry.add(repair);
        InventoryBasic repairInputs = new InventoryBasic("repair", true, 9);
        repairInputs.setInventorySlotContents(0, new ItemStack(Items.iron_sword, 1, 200)); repairInputs.setInventorySlotContents(1, new ItemStack(Items.iron_sword, 1, 220));
        ItemStack repaired = (ItemStack) find.invoke(null, repairInputs);
        require(repaired.getItem() == Items.iron_sword && repaired.getItemDamage() == 158 && repaired.stackSize == 1, "Native repair-before-registry fixture changed");
        try { ctor.newInstance(handler, registry, false); throw new AssertionError("Assembly ordinary recipe intercepted by repair branch was flattened to a fixed output"); }
        catch (InvocationTargetException expected) { require(expected.getCause() instanceof Jobs.Fault && ((Jobs.Fault) expected.getCause()).code.equals("recipe_unsupported"), "Wrong assembly repair guard"); }
        require(milk.stackSize == 23 && coal.stackSize == 17 && output.getTagCompound().getInteger("native") == 31, "Assembly capture mutated registry stacks");
        row.finish(); java.nio.file.Files.write(java.nio.file.Paths.get("build/native-tests/galaxy-assembly-record.json"), CanonicalJson.bytes(row.record));
        System.out.println("Native Galaxy assembly: actual shaped/shapeless selection, native unrecognized types, repair-first guard, unit/no-container consumption, fixed NBT/output count, atlas, 70-tick tracks and clock cleanup passed");
    }
    private static int ticks(JsonArray frames) { int total = 0; for (JsonElement frame : frames) total += frame.getAsJsonObject().get("ticks").getAsInt(); return total; }
    private static RecipeRow row(Facts facts, TemplateRecipeHandler handler, int order) { return new RecipeRow(facts, object("owner", "GalaxySpace", "handler", handler.getClass().getName(), "key", "assembly"), "category_test", order); }
    private static String id(ItemStack stack) { return Identity.item(Item.itemRegistry.getNameForObject(stack.getItem()), Items.feather.getDamage(stack), TypedNbt.encode(stack.getTagCompound())); }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
