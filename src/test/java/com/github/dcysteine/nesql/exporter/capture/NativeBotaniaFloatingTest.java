package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.source.Identity;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.*;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.ShapelessRecipes;
import net.minecraft.nbt.NBTTagInt;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagString;
import java.util.*;
import java.lang.reflect.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Native evidence: NEI copies all tags, whereas the gameplay recipe extracts one string. */
final class NativeBotaniaFloatingTest {
    static void run() throws Exception {
        Class<?> blocks = Class.forName("vazkii.botania.common.block.ModBlocks");
        blocks.getField("floatingFlower").set(null, Blocks.red_flower);
        blocks.getField("specialFlower").set(null, Blocks.yellow_flower);
        blocks.getField("floatingSpecialFlower").set(null, Blocks.sapling);
        IRecipe nativeRecipe = (IRecipe) Class.forName("vazkii.botania.common.crafting.recipe.SpecialFloatingFlowerRecipe").newInstance();
        require(nativeRecipe.getRecipeSize() == 10 && nativeRecipe.getRecipeOutput() == null,
                "Floating flower native dynamic recipe marker changed");
        int accepted = exhaustive(nativeRecipe, 3) + exhaustive(nativeRecipe, 2);
        require(accepted == 18710, "Floating flower native grid coverage changed");
        InventoryCrafting inventory = grid(3);
        inventory.setInventorySlotContents(0, new ItemStack(Blocks.red_flower, 13, 32767));
        ItemStack special = new ItemStack(Blocks.yellow_flower, 27, 41);
        inventory.setInventorySlotContents(8, special);
        require(nativeRecipe.matches(inventory, null), "Untagged flower stopped matching");
        output(nativeRecipe.getCraftingResult(inventory), "");
        require(!special.hasTagCompound(), "Native extraction unexpectedly changed untagged input");
        special.setTagInfo("type", new NBTTagInt(73));
        special.setTagInfo("discard", new NBTTagString("owner payload"));
        output(nativeRecipe.getCraftingResult(inventory), "73");
        special.setTagInfo("type", new NBTTagString("arbitrary:unknown/花"));
        output(nativeRecipe.getCraftingResult(inventory), "arbitrary:unknown/花");
        require(special.stackSize == 27 && special.getItemDamage() == 41 && special.getTagCompound().getString("discard").equals("owner payload"),
                "Native recipe mutated offered count, metadata or NBT");
        inventory.setInventorySlotContents(4, new ItemStack(Blocks.stone));
        require(!nativeRecipe.matches(inventory, null), "Extra foreign item was accepted");
        inventory.setInventorySlotContents(4, null);
        priority(nativeRecipe, inventory);
        containers();
        compoundOrder(nativeRecipe, inventory);
        System.out.println("Native Botania floating evidence: 18,710 matching 2x2/3x3 grids; repeated inputs, physical last-special selection, string defaults, discarded tags and crafting priority passed");
        Class<?> adapter;
        try { adapter = Class.forName("com.github.dcysteine.nesql.exporter.capture.BotaniaFloatingRecipes"); }
        catch (ClassNotFoundException missing) { throw new AssertionError("Floating flowers lack native variable-grid and last-special string transformation", missing); }
        adapter(adapter, nativeRecipe);
    }
    private static int exhaustive(IRecipe recipe, int side) {
        InventoryCrafting inventory = grid(side); int cells = side * side, variants = 1, accepted = 0;
        for (int i = 0; i < cells; i++) variants *= 3;
        for (int encoded = 0; encoded < variants; encoded++) {
            int remaining = encoded, floating = 0, special = 0, last = -1;
            for (int slot = 0; slot < cells; slot++) {
                int kind = remaining % 3; remaining /= 3; ItemStack stack = null;
                if (kind == 1) { stack = new ItemStack(Blocks.red_flower, 7 + slot, 30 + slot); floating++; }
                if (kind == 2) {
                    stack = new ItemStack(Blocks.yellow_flower, 17 + slot, 90 + slot); special++; last = slot;
                    stack.setTagInfo("type", new NBTTagString("flower-" + slot)); stack.setTagInfo("discard", new NBTTagInt(slot));
                }
                inventory.setInventorySlotContents(slot, stack);
            }
            boolean matched = recipe.matches(inventory, null);
            require(matched == (floating > 0 && special > 0), "Native floating flower matcher differs at grid " + side + "/" + encoded);
            if (matched) { accepted++; output(recipe.getCraftingResult(inventory), "flower-" + last); }
        }
        return accepted;
    }
    @SuppressWarnings("unchecked") private static void priority(IRecipe recipe, InventoryCrafting inventory) {
        List<IRecipe> registry = CraftingManager.getInstance().getRecipeList(); List<IRecipe> saved = new ArrayList<>(registry);
        IRecipe earlier = new ShapelessRecipes(new ItemStack(Blocks.stone), Arrays.asList(
                new ItemStack(Blocks.red_flower, 1, 32767), new ItemStack(Blocks.yellow_flower, 1, 32767)));
        try {
            registry.clear(); registry.add(earlier); registry.add(recipe);
            require(CraftingManager.getInstance().findMatchingRecipe(inventory, null).getItem() == Item.getItemFromBlock(Blocks.stone),
                    "Earlier ordinary crafting recipe failed to intercept floating flowers");
            Collections.reverse(registry);
            output(CraftingManager.getInstance().findMatchingRecipe(inventory, null), "arbitrary:unknown/花");
        } finally { registry.clear(); registry.addAll(saved); }
    }
    private static InventoryCrafting grid(int side) {
        return new InventoryCrafting(new Container() {
            @Override public boolean canInteractWith(EntityPlayer player) { return false; }
        }, side, side);
    }
    private static void containers() throws Exception {
        String prefix = "vazkii.botania.common.item.block.";
        for (String name : Arrays.asList("ItemBlockWithMetadataAndName", "ItemBlockSpecialFlower", "ItemBlockFloatingSpecialFlower")) {
            Item item = (Item) Class.forName(prefix + name).getConstructor(net.minecraft.block.Block.class).newInstance(Blocks.yellow_flower);
            require(ItemCallbacks.method(item, "hasContainerItem", "hasContainerItem", ItemStack.class).getDeclaringClass() == Item.class
                    && ItemCallbacks.method(item, "hasContainerItem", "func_77634_r").getDeclaringClass() == Item.class,
                    "Native flower gained a container predicate override");
            ItemStack offered = new ItemStack(item, 31, 41); offered.setTagInfo("type", new NBTTagString("arbitrary"));
            require(!item.hasContainerItem(offered) && ItemCallbacks.container(offered, true) == null,
                    "Native flower unexpectedly returns a crafting container");
            item.setContainerItem(Items.bucket);
            require(item.hasContainerItem(offered) && ItemCallbacks.container(offered, true).getItem() == Items.bucket,
                    "Native base container field must remain visible to drift verification");
        }
    }
    private static void compoundOrder(IRecipe recipe, InventoryCrafting inventory) {
        NBTTagCompound first = new NBTTagCompound(), second = new NBTTagCompound();
        first.setInteger("Aa", 1); first.setInteger("BB", 2);
        second.setInteger("BB", 2); second.setInteger("Aa", 1);
        require(first.equals(second), "Equivalent native compound fixture differs structurally");
        require(!first.toString().equals(second.toString()), "Native collision-order fixture no longer distinguishes compound iteration");
        ItemStack a = new ItemStack(Blocks.yellow_flower), b = new ItemStack(Blocks.yellow_flower);
        a.setTagInfo("type", first); b.setTagInfo("type", second);
        require(com.github.dcysteine.nesql.exporter.source.CanonicalJson.digest(com.github.dcysteine.nesql.exporter.source.TypedNbt.encode(a.getTagCompound()))
                .equals(com.github.dcysteine.nesql.exporter.source.CanonicalJson.digest(com.github.dcysteine.nesql.exporter.source.TypedNbt.encode(b.getTagCompound()))),
                "Canonical NBT unexpectedly retains native hash-table insertion order");
        inventory.setInventorySlotContents(8, a); ItemStack outputA = recipe.getCraftingResult(inventory);
        inventory.setInventorySlotContents(8, b); ItemStack outputB = recipe.getCraftingResult(inventory);
        require(!ItemStack.areItemStackTagsEqual(outputA, outputB),
                "Native compound type stringification failed to expose distinct output identities");
        output(outputA, first.toString()); output(outputB, second.toString());
    }
    private static void adapter(Class<?> adapter, IRecipe nativeRecipe) throws Exception {
        TemplateRecipeHandler handler = (TemplateRecipeHandler) Class.forName("vazkii.botania.client.integration.nei.recipe.RecipeHandlerFloatingFlowers").newInstance();
        Constructor<?> ctor = adapter.getDeclaredConstructor(TemplateRecipeHandler.class, List.class, List.class); ctor.setAccessible(true);
        ItemStack floating = new ItemStack(Blocks.red_flower, 17, 3), special = new ItemStack(Blocks.yellow_flower, 11, 5);
        special.setTagInfo("type", new NBTTagString("sample")); special.setTagInfo("discard", new NBTTagInt(9));
        ItemStack result = new ItemStack(Blocks.sapling); result.setTagInfo("type", new NBTTagString("sample"));
        List<IRecipe> registry = new ArrayList<>(Collections.singletonList(nativeRecipe));
        List<ItemStack> catalog = new ArrayList<>(Arrays.asList(floating, special));
        RegistryRecipes capture = (RegistryRecipes) ctor.newInstance(handler, registry, catalog);
        require(capture.size() == 36, "Floating count partitions were omitted or expanded to duplicate physical arrangements");
        JsonArray records = new JsonArray(); int index = 0;
        for (int total = 2; total <= 9; total++) for (int specials = 1; specials < total; specials++) {
            Facts facts = new Facts("en_US");
            @SuppressWarnings("unchecked") Set<String> known = (Set<String>) MagicApi.field(facts, "items");
            known.add(id(floating)); known.add(id(special)); known.add(id(result));
            RecipeRow row = new RecipeRow(facts, object("owner", "Botania", "handler", handler.getClass().getName(), "key", "floating"), "category_test", index);
            require(capture.capture(index++, row), "Reachable String-type floating branch disappeared");
            require(row.inputs.size() == total && row.outputs.size() == 1 && row.record.get("grid").isJsonNull(), "Floating branch lost physical occupied-cell count");
            JsonObject process = row.record.getAsJsonObject("process"); JsonArray selected = process.getAsJsonArray("special");
            require(process.get("kind").getAsString().equals("floatingFlowers") && selected.size() == specials, "Floating physical-order selection changed");
            for (int slot = 0; slot < total; slot++) {
                JsonObject input = row.inputs.get(slot).getAsJsonObject(), choice = input.getAsJsonArray("choices").get(0).getAsJsonObject();
                require(input.get("slot").getAsInt() == slot && input.getAsJsonArray("choices").size() == 1 && choice.get("amount").getAsInt() == 1
                        && choice.getAsJsonObject("consume").get("kind").getAsString().equals("consume") && choice.getAsJsonArray("returns").size() == 0,
                        "Floating consumption changed from one per occupied cell");
                require(choice.get("id").getAsString().equals(id(slot < total - specials ? floating : special)), "Floating or special input roles changed");
                JsonObject rule = choice.getAsJsonObject("rule");
                if (slot == total - 1) require(rule.get("kind").getAsString().equals("string_tag") && rule.get("key").getAsString().equals("type"),
                        "Last special flower did not expose its supported String-or-absent type scope");
                else require(rule.get("kind").getAsString().equals("wildcard") && rule.get("meta").getAsBoolean() && rule.get("nbt").getAsBoolean(),
                        "Earlier flower input was narrowed to the example's NBT or metadata");
                if (slot >= total - specials) require(selected.get(slot - total + specials).getAsInt() == slot, "Special slots are not in physical binding order");
            }
            JsonObject output = row.outputs.get(0).getAsJsonObject(), change = output.getAsJsonObject("change");
            require(output.get("id").getAsString().equals(id(result)) && output.get("amount").getAsInt() == 1 && change.get("input").getAsInt() == total - 1
                    && change.getAsJsonObject("action").get("kind").getAsString().equals("floatingFlower")
                    && change.getAsJsonObject("action").get("base").getAsString().equals(id(result)), "Floating output became a fixed full-NBT copy");
            require(change.getAsJsonArray("samples").size() == 1 && change.getAsJsonArray("samples").get(0).getAsJsonObject().get("id").getAsString().equals(id(result)),
                    "Floating native transform sample is missing");
            require(row.properties.has("botania:floatingScope") && row.properties.has("botania:craftingBoundary"), "Floating partial input-state or dispatch scope is hidden");
            require(handler.arecipes.size() == 1, "Floating cache accumulated stale branch displays");
            List<PositionedStack> display = handler.arecipes.get(0).getIngredients();
            require(display.size() == 2 && display.get(0).relx == 25 && display.get(1).relx == 43
                    && row.elements.size() == 3, "Floating native two-input display was expanded with invented slots");
            require(row.elements.get(1).getAsJsonObject().get("slot").getAsInt() == total - 1, "Native special example binds to a nonselected logical input");
            row.finish(); records.add(row.record);
        }
        capture.verify(); special.getTagCompound().setString("type", "changed");
        changed(capture, "Floating catalog sample drift was ignored"); special.getTagCompound().setString("type", "sample");
        registry.clear(); changed(capture, "Floating native registration removal was ignored"); registry.add(nativeRecipe);
        blocksChanged(capture);
        require(floating.stackSize == 17 && special.stackSize == 11 && special.getTagCompound().getInteger("discard") == 9, "Floating capture changed catalog-owned stacks");
        java.nio.file.Files.write(java.nio.file.Paths.get("build/native-tests/botania-floating-records.json"), CanonicalJson.bytes(records));
        System.out.println("Native Botania floating adapter: all 36 count partitions, selected-slot scope, native string-only output examples, fixed consumption, native view geometry and drift passed");
    }
    private static void blocksChanged(RegistryRecipes capture) throws Exception {
        Field field = Class.forName("vazkii.botania.common.block.ModBlocks").getField("floatingFlower"); Object previous = field.get(null);
        try { field.set(null, Blocks.stone); changed(capture, "Floating native item identity drift was ignored"); }
        finally { field.set(null, previous); }
        Item item = Item.getItemFromBlock(Blocks.yellow_flower); item.setContainerItem(Items.bucket);
        try { changed(capture, "Floating container state drift was ignored"); }
        finally { item.setContainerItem(null); }
    }
    private static void changed(RegistryRecipes capture, String message) {
        try { capture.verify(); throw new AssertionError(message); }
        catch (Jobs.Fault expected) { require(expected.code.equals("recipe_changed"), "Wrong floating drift failure: " + expected.code); }
    }
    private static String id(ItemStack stack) { return Identity.item(Item.itemRegistry.getNameForObject(stack.getItem()), Items.feather.getDamage(stack), TypedNbt.encode(stack.getTagCompound())); }
    private static void output(ItemStack output, String expected) {
        require(output != null && output.getItem() == Item.getItemFromBlock(Blocks.sapling) && output.stackSize == 1 && output.getItemDamage() == 0,
                "Native floating flower output item/count/metadata changed");
        require(output.hasTagCompound() && output.getTagCompound().func_150296_c().size() == 1
                && output.getTagCompound().hasKey("type", 8) && output.getTagCompound().getString("type").equals(expected),
                "Floating output retained extra NBT or selected the wrong special flower");
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
