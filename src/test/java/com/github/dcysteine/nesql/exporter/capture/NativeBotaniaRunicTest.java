package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.source.Identity;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.*;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagInt;
import net.minecraftforge.oredict.OreDictionary;
import java.lang.reflect.*;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Catches lost rune refunds, missing dropped livingrock, and greedy native matching approximations. */
final class NativeBotaniaRunicTest {
    static void run() throws Exception {
        Class<?> adapter;
        try { adapter = Class.forName("com.github.dcysteine.nesql.exporter.capture.BotaniaRunicRecipes"); }
        catch (ClassNotFoundException missing) { throw new AssertionError("Runic altar lacks native inventory, rune refund and livingrock semantics", missing); }
        Class<?> type = Class.forName("vazkii.botania.api.recipe.RecipeRuneAltar");
        Constructor<?> nativeCtor = type.getConstructor(ItemStack.class, int.class, Object[].class);
        Class<?> blocks = Class.forName("vazkii.botania.common.block.ModBlocks");
        blocks.getField("runeAltar").set(null, Blocks.gold_block);
        blocks.getField("livingrock").set(null, Blocks.stone);
        Class.forName("vazkii.botania.common.item.ModItems").getField("rune").set(null, Items.feather);
        ItemStack iron = new ItemStack(Items.iron_ingot, 19), gold = new ItemStack(Items.gold_ingot, 7);
        ItemStack rune = new ItemStack(Items.feather, 11, 4), rock = new ItemStack(Blocks.stone);
        rune.setTagInfo("templateOnly", new NBTTagInt(6));
        ItemStack output = new ItemStack(Items.diamond, 3); output.setTagInfo("native", new NBTTagInt(91));
        OreDictionary.registerOre("nesqlRunicMetal", iron); OreDictionary.registerOre("nesqlRunicMetal", gold);
        Object recipe = nativeCtor.newInstance(output, 7500, new Object[]{"nesqlRunicMetal", "nesqlRunicMetal", rune});
        List<Object> registry = new ArrayList<>(Collections.singletonList(recipe));
        TemplateRecipeHandler handler = (TemplateRecipeHandler) Class.forName("vazkii.botania.client.integration.nei.recipe.RecipeHandlerRunicAltar").newInstance();
        Constructor<?> ctor = adapter.getDeclaredConstructor(TemplateRecipeHandler.class, List.class); ctor.setAccessible(true);
        RegistryRecipes capture = (RegistryRecipes) ctor.newInstance(handler, registry);
        require(capture.size() == 1, "Runic altar registry enumeration changed");
        Map<String, ItemStack> known = new HashMap<>();
        for (ItemStack stack : Arrays.asList(iron, gold, rune, rock, output)) {
            known.put(id(stack), stack); ItemStack clean = stack.copy(); clean.setTagCompound(null); known.put(id(clean), clean);
        }
        RecipeRow row = row(handler, known);
        require(capture.capture(0, row), "Reachable native rune recipe was omitted");
        require(row.inputs.size() == 4 && row.outputs.size() == 1, "Runic altar lost completion stone or gained a consumed altar/wand");
        require(row.outputs.get(0).getAsJsonObject().get("amount").getAsString().equals("3")
                && row.outputs.get(0).getAsJsonObject().get("id").getAsString().equals(id(output)), "Native rune output amount/NBT changed");
        require(row.properties.getAsJsonObject("botania:mana").getAsJsonObject("value").get("value").getAsInt() == 7500
                && row.properties.getAsJsonObject("botania:cooldown").getAsJsonObject("value").get("value").getAsInt() == 60,
                "Runic altar mana or post-craft cooldown disappeared");
        require(row.record.get("duration").isJsonNull(), "Mana accumulation or player trigger became a fixed craft duration");
        for (int index = 0; index < 4; index++) for (JsonElement raw : row.inputs.get(index).getAsJsonObject().getAsJsonArray("choices")) {
            JsonObject choice = raw.getAsJsonObject();
            require(choice.get("amount").getAsInt() == 1 && choice.getAsJsonArray("returns").size() == 0, "Runic inputs used template quantities or crafting container callbacks");
            require(choice.getAsJsonObject("consume").get("kind").getAsString().equals(index == 2 ? "keep" : "consume"), "Runic native refund became consumed input");
            require(choice.getAsJsonObject("rule").get("nbt").getAsBoolean(), "Native rune matching stopped ignoring offered NBT");
        }
        JsonObject stone = row.inputs.get(3).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject();
        require(row.inputs.get(3).getAsJsonObject().get("slot").getAsInt() == 16
                && stone.getAsJsonObject("rule").get("meta").getAsBoolean(), "Dropped livingrock was narrowed to metadata zero or placed in altar inventory");
        Method nativeMatches = type.getMethod("matches", IInventory.class);
        for (ItemStack first : Arrays.asList(iron, gold)) for (ItemStack second : Arrays.asList(iron, gold)) {
            ItemStack offeredRune = rune.copy(); offeredRune.stackSize = 1; offeredRune.setTagInfo("offered", new NBTTagInt(42));
            ItemStack[] offered = {offeredRune, first.copy(), second.copy()};
            InventoryBasic inventory = new InventoryBasic("runic-test", true, 16);
            for (int i = 0; i < offered.length; i++) { offered[i].stackSize = 1; inventory.setInventorySlotContents(i, offered[i]); }
            require((Boolean) nativeMatches.invoke(recipe, inventory), "Native packed/repeated requirement fixture rejected");
            require(allocation(row, offered, 0, 0, known), "Exported rune predicate differs from native greedy allocation");
            offered[2] = new ItemStack(Items.apple); inventory.setInventorySlotContents(2, offered[2]);
            require(!(Boolean) nativeMatches.invoke(recipe, inventory) && !allocation(row, offered, 0, 0, known), "Unmatched packed inventory was accepted");
        }
        @SuppressWarnings("unchecked") List<PositionedStack> decorations = (List<PositionedStack>) adapter.getDeclaredMethod("ornaments", TemplateRecipeHandler.class, int.class).invoke(null, handler, 0);
        require(decorations.size() == 1 && decorations.get(0).relx == 73 && decorations.get(0).rely == 55, "Native rune altar decoration lost its center position");
        require(row.elements.size() == 4, "Hidden completion stone became an invented native display slot");
        capture.verify(); output.stackSize = 4;
        try { capture.verify(); throw new AssertionError("Rune output drift was ignored"); }
        catch (Jobs.Fault expected) { require(expected.code.equals("recipe_changed"), "Wrong rune drift fault"); }
        output.stackSize = 3;
        Object partial = nativeCtor.newInstance(output, 100, new Object[]{"nesqlRunicMetal", iron});
        unsupported(ctor, handler, Collections.singletonList(partial), "Partially overlapping greedy requirements became freely assignable slots");
        Object competing = nativeCtor.newInstance(new ItemStack(Items.apple), 20, new Object[]{"nesqlRunicMetal", "nesqlRunicMetal", rune});
        unsupported(ctor, handler, Arrays.asList(recipe, competing), "Competing runic first-match predicates were flattened");
        Object hidden = nativeCtor.newInstance(new ItemStack(Items.skull), 20, new Object[]{"nesqlRunicMetal", "nesqlRunicMetal", rune});
        unsupported(ctor, handler, Arrays.asList(hidden, recipe), "Hidden skull output stopped participating in native first-match selection");
        Object impossible = nativeCtor.newInstance(output, 0, new Object[]{iron});
        RegistryRecipes zero = (RegistryRecipes) ctor.newInstance(handler, Collections.singletonList(impossible));
        require(!zero.capture(0, row(handler, known)), "Zero-mana rune recipe was claimed reachable despite native manaToGet > 0 gate");
        require(iron.stackSize == 19 && rune.stackSize == 11 && rune.getTagCompound().getInteger("templateOnly") == 6, "Runic capture mutated registry stacks");
        row.finish(); java.nio.file.Files.write(java.nio.file.Paths.get("build/native-tests/botania-runic-record.json"), CanonicalJson.bytes(row.record));
        System.out.println("Native Botania runic: actual greedy matching, repeated equivalent groups, rune refunds, dropped livingrock, mana/cooldown, hidden-priority guards and drift passed");
    }
    private static RecipeRow row(TemplateRecipeHandler handler, Map<String, ItemStack> known) {
        Facts facts = new Facts("en_US");
        @SuppressWarnings("unchecked") Set<String> ids = (Set<String>) MagicApi.field(facts, "items"); ids.addAll(known.keySet());
        return new RecipeRow(facts, object("owner", "Botania", "handler", handler.getClass().getName(), "key", "runic"), "category_test", 0);
    }
    private static boolean allocation(RecipeRow row, ItemStack[] offered, int at, int used, Map<String, ItemStack> known) {
        if (at == offered.length) return true;
        for (int index = 0; index < offered.length; index++) if ((used & 1 << index) == 0) {
            for (JsonElement raw : row.inputs.get(at).getAsJsonObject().getAsJsonArray("choices")) {
                JsonObject choice = raw.getAsJsonObject();
                if (NativeRailTest.matches(choice.getAsJsonObject("rule"), known.get(choice.get("id").getAsString()), offered[index], known)
                        && allocation(row, offered, at + 1, used | 1 << index, known)) return true;
            }
        }
        return false;
    }
    private static void unsupported(Constructor<?> ctor, TemplateRecipeHandler handler, List<?> registry, String message) throws Exception {
        try { ctor.newInstance(handler, registry); throw new AssertionError(message); }
        catch (InvocationTargetException expected) { require(expected.getCause() instanceof Jobs.Fault && ((Jobs.Fault) expected.getCause()).code.equals("recipe_unsupported"), "Wrong rune allocation guard"); }
    }
    private static String id(ItemStack stack) { return Identity.item(Item.itemRegistry.getNameForObject(stack.getItem()), Items.feather.getDamage(stack), TypedNbt.encode(stack.getTagCompound())); }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
