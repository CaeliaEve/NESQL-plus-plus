package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.source.Identity;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.*;
import net.minecraft.init.Items;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagInt;
import net.minecraftforge.oredict.OreDictionary;
import java.lang.reflect.*;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Actual native matcher checks: ore membership, exact NBT and guarded HashMap alias behavior. */
final class NativeImbuingTest {
    static void run() throws Exception {
        Class<?> adapter;
        try { adapter = Class.forName("com.github.dcysteine.nesql.exporter.capture.ImbuingRecipes"); }
        catch (ClassNotFoundException missing) { throw new AssertionError("Imbuing lacks proven native ingredient allocation", missing); }
        Class<?> recipeType = Class.forName("lumien.randomthings.Handler.ImbuingStation.ImbuingRecipe");
        Constructor<?> nativeCtor = recipeType.getConstructor(ItemStack.class, ItemStack.class, ItemStack[].class);
        ItemStack base = new ItemStack(Items.potionitem, 21, 0); base.setTagInfo("native", new NBTTagInt(8));
        ItemStack iron = new ItemStack(Items.iron_ingot, 8), gold = new ItemStack(Items.gold_ingot, 9), diamond = new ItemStack(Items.diamond, 7);
        ItemStack emerald = new ItemStack(Items.emerald, 3); emerald.setTagInfo("native", new NBTTagInt(19));
        OreDictionary.registerOre("nesqlImbuingMetal", iron); OreDictionary.registerOre("nesqlImbuingMetal", gold);
        Object recipe = nativeCtor.newInstance(base, emerald, new ItemStack[]{iron, diamond});
        TemplateRecipeHandler handler = (TemplateRecipeHandler) Class.forName("lumien.randomthings.Handler.ModCompability.NEI.ImbuingStationRecipeHandler").newInstance();
        Constructor<?> ctor = adapter.getDeclaredConstructor(TemplateRecipeHandler.class, List.class, float.class); ctor.setAccessible(true);
        List<Object> registry = new ArrayList<>(Collections.singletonList(recipe));
        RegistryRecipes capture = (RegistryRecipes) ctor.newInstance(handler, registry, 2.25f);
        require(capture.size() == 1, "Imbuing registry enumeration changed");
        Facts facts = new Facts("en_US");
        @SuppressWarnings("unchecked") Set<String> ids = (Set<String>) MagicApi.field(facts, "items");
        Map<String, ItemStack> known = new HashMap<>();
        for (ItemStack stack : Arrays.asList(base, iron, gold, diamond, emerald)) { ids.add(id(stack)); known.put(id(stack), stack); }
        RecipeRow row = new RecipeRow(facts, object("owner", "RandomThings", "handler", handler.getClass().getName(), "key", "imbuing"), "category_test", 0);
        require(capture.capture(0, row), "Native imbuing recipe was omitted");
        require(row.inputs.size() == 3 && row.record.get("duration").getAsString().equals("3"), "Native float threshold or recipe slots changed");
        require(row.outputs.get(0).getAsJsonObject().get("amount").getAsString().equals("3"), "Imbuing output quantity changed");
        for (JsonElement raw : row.inputs) for (JsonElement choice : raw.getAsJsonObject().getAsJsonArray("choices")) {
            require(choice.getAsJsonObject().get("amount").getAsString().equals("1") && choice.getAsJsonObject().getAsJsonArray("returns").size() == 0,
                    "Imbuing honors template count or container callbacks instead of consuming unit slots");
        }
        for (ItemStack metal : Arrays.asList(iron, gold)) for (boolean tagged : new boolean[]{false, true}) {
            ItemStack offered = metal.copy(); offered.stackSize = 1; if (tagged) offered.setTagInfo("different", new NBTTagInt(3));
            InventoryBasic inv = new InventoryBasic("test", true, 5);
            inv.setInventorySlotContents(0, diamond.copy()); inv.setInventorySlotContents(2, offered); inv.setInventorySlotContents(3, base.copy());
            require((Boolean) recipeType.getMethod("matchesInventory", net.minecraft.inventory.IInventory.class).invoke(recipe, inv), "Native shared-ore ingredient fixture rejected");
            boolean captured = false;
            for (JsonElement raw : row.inputs.get(0).getAsJsonObject().getAsJsonArray("choices")) {
                JsonObject choice = raw.getAsJsonObject();
                captured |= NativeRailTest.matches(choice.getAsJsonObject("rule"), known.get(choice.get("id").getAsString()), offered, known);
            }
            require(captured, "Captured shared ore alternatives are narrower than native matching");
            ItemStack wrongBase = base.copy(); wrongBase.setTagInfo("native", new NBTTagInt(9)); inv.setInventorySlotContents(3, wrongBase);
            require(!(Boolean) recipeType.getMethod("matchesInventory", net.minecraft.inventory.IInventory.class).invoke(recipe, inv), "Native direct base NBT must be exact");
            JsonObject baseChoice = row.inputs.get(2).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject();
            require(baseChoice.getAsJsonObject("rule").get("kind").getAsString().equals("exact"), "Non-ore base lost its exact NBT predicate");
        }
        Object overlap = nativeCtor.newInstance(base, emerald, new ItemStack[]{iron, gold});
        expectUnsupported(ctor, handler, Collections.singletonList(overlap), "Overlapping native HashMap ingredient requirements were approximated as independent slots");
        Object competing = nativeCtor.newInstance(base, new ItemStack(Items.apple), new ItemStack[]{gold, diamond});
        expectUnsupported(ctor, handler, Arrays.asList(recipe, competing), "Competing native first-match recipes were flattened");
        capture.verify(); emerald.stackSize = 4;
        try { capture.verify(); throw new AssertionError("Imbuing registry output drift was ignored"); }
        catch (Jobs.Fault expected) { require(expected.code.equals("recipe_changed"), "Wrong imbuing drift fault"); }
        emerald.stackSize = 3;
        require(base.stackSize == 21 && iron.stackSize == 8 && emerald.getTagCompound().getInteger("native") == 19, "Imbuing mutated caller stacks");
        Class<?> tileType = Class.forName("lumien.randomthings.TileEntities.TileEntityImbuingStation");
        Object tile = tileType.newInstance();
        Method set = tileType.getMethod("setInventorySlotContents", int.class, ItemStack.class);
        set.invoke(tile, 0, iron.copy()); set.invoke(tile, 1, diamond.copy()); set.invoke(tile, 3, base.copy());
        Field pending = tileType.getDeclaredField("currentOutput"); pending.setAccessible(true); pending.set(tile, emerald);
        Method finish = tileType.getDeclaredMethod("imbue"); finish.setAccessible(true); finish.invoke(tile);
        Method get = tileType.getMethod("getStackInSlot", int.class);
        require(((ItemStack) get.invoke(tile, 0)).stackSize == 7 && ((ItemStack) get.invoke(tile, 1)).stackSize == 6
                && ((ItemStack) get.invoke(tile, 3)).stackSize == 20 && ItemStack.areItemStacksEqual((ItemStack) get.invoke(tile, 4), emerald),
                "Native initially-empty completion differs from unit-consumption/output contract");
        emerald.stackSize = 70;
        Object cappedTile = tileType.newInstance(); pending.set(cappedTile, emerald); finish.invoke(cappedTile);
        require(((ItemStack) get.invoke(cappedTile, 4)).stackSize == 64, "Native empty-output clamp fixture changed");
        RegistryRecipes capped = (RegistryRecipes) ctor.newInstance(handler, registry, 2.25f);
        RecipeRow cappedRow = new RecipeRow(facts, object("owner", "RandomThings", "handler", handler.getClass().getName(), "key", "imbuing"), "category_test", 0);
        capped.capture(0, cappedRow);
        require(cappedRow.outputs.get(0).getAsJsonObject().get("amount").getAsString().equals("64"), "Initially-empty imbuing output omitted native InventoryBasic stack limit");
        emerald.stackSize = 3;
        Item many = new Item().setHasSubtypes(true);
        Item.itemRegistry.addObject(31031, "fixture:imbuing_many", many);
        for (int meta = 0; meta < 1500; meta++) OreDictionary.registerOre("nesqlImbuingProofBudget", new ItemStack(many, 1, meta));
        Object expensive = nativeCtor.newInstance(new ItemStack(many), emerald, new ItemStack[0]);
        expectUnsupported(ctor, handler, Collections.singletonList(expensive), "Imbuing proof accepted an unbounded alternative-normalization workload");
        row.finish(); java.nio.file.Files.write(java.nio.file.Paths.get("build/native-tests/imbuing-record.json"), CanonicalJson.bytes(row.record));
        System.out.println("Native imbuing: native ore/NBT matching, disjoint ingredient proof, first-match conflict guard, unit consumption and float duration passed");
    }
    private static void expectUnsupported(Constructor<?> ctor, TemplateRecipeHandler handler, List<?> registry, String message) throws Exception {
        try { ctor.newInstance(handler, registry, 2.25f); throw new AssertionError(message); }
        catch (InvocationTargetException expected) { require(expected.getCause() instanceof Jobs.Fault && ((Jobs.Fault) expected.getCause()).code.equals("recipe_unsupported"), "Wrong imbuing allocation guard"); }
    }
    private static String id(ItemStack stack) { return Identity.item(Item.itemRegistry.getNameForObject(stack.getItem()), Items.feather.getDamage(stack), TypedNbt.encode(stack.getTagCompound())); }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
