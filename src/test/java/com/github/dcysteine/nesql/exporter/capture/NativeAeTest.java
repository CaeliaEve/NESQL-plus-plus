package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.source.Identity;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.inventory.Container;
import net.minecraft.entity.player.EntityPlayer;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Installed AE2 handler capability regression; further semantic checks run after capability registration. */
public final class NativeAeTest {
    private NativeAeTest() {}
    public static void main(String[] args) throws Exception { GameTest.run(); }
    public static void run() throws Exception {
        for (String kind : new String[] {"Shaped", "Shapeless"}) {
            ICraftingHandler handler = (ICraftingHandler) Class.forName("appeng.integration.modules.NEIHelpers.NEIAE" + kind + "RecipeHandler").newInstance();
            if (Recipes.adapter(handler) == null) throw new AssertionError("Pinned AE2 " + kind + " handler has no production adapter");
        }
        Class<?> config = Class.forName("appeng.core.AEConfig");
        java.lang.reflect.Field unsafe = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); unsafe.setAccessible(true);
        Object oldConfig = config.getField("instance").get(null);
        config.getField("instance").set(null, ((sun.misc.Unsafe) unsafe.get(null)).allocateInstance(config));
        // The colored-cable preference initializes AE2's complete block/API registry;
        // that branch belongs to live-game coverage, not this isolated fixture.
        try { semantics(); } finally { config.getField("instance").set(null, oldConfig); }
    }

    @SuppressWarnings("unchecked")
    private static void semantics() throws Exception {
        ItemStack apple = new ItemStack(Items.apple, 13, 0), paper = new ItemStack(Items.paper, 1, 0);
        ItemStack carrot = new ItemStack(Items.carrot, 7, 0);
        ItemStack tagged = apple.copy(); tagged.setTagInfo("owner", new net.minecraft.nbt.NBTTagString("any"));
        Object apples = ingredient(apple, carrot), papers = ingredient(paper);
        ItemStack output = new ItemStack(Items.diamond, 3);
        for (String kind : new String[] {"Shaped", "Shapeless"}) {
            Class<?> type = Class.forName("appeng.recipes.game." + kind + "Recipe");
            Object[] recipeArgs = kind.equals("Shaped") ? new Object[] {new String[] {"AP", " A"}, 'A', apples, 'P', papers}
                    : new Object[] {apples, papers, apples};
            IRecipe recipe = (IRecipe) type.getConstructor(ItemStack.class, Object[].class).newInstance(output, recipeArgs);
            IRecipe disabled = (IRecipe) type.getConstructor(ItemStack.class, Object[].class).newInstance(output, recipeArgs);
            java.lang.reflect.Field disabledFlag = type.getDeclaredField("disable"); disabledFlag.setAccessible(true); disabledFlag.setBoolean(disabled, true);
            if (!AeRecipes.enumerate(type, Arrays.asList(disabled, recipe, new Object())).equals(Collections.singletonList(recipe))) {
                throw new AssertionError("AE2 enumeration must retain native order and enabled state");
            }
            InventoryCrafting inventory = new InventoryCrafting(new Container() {
                @Override public boolean canInteractWith(EntityPlayer player) { return true; }
            }, 3, 3);
            inventory.setInventorySlotContents(0, tagged.copy()); inventory.setInventorySlotContents(1, paper.copy());
            inventory.setInventorySlotContents(kind.equals("Shaped") ? 4 : 2, new ItemStack(Items.apple));
            if (!recipe.matches(inventory, null)) throw new AssertionError("Pinned native recipe should ignore offered NBT/count");
            if (recipe.getCraftingResult(inventory).stackSize != 3) throw new AssertionError("Native result count changed");
            TemplateRecipeHandler handler = (TemplateRecipeHandler) Class.forName("appeng.integration.modules.NEIHelpers.NEIAE" + kind + "RecipeHandler").newInstance();
            Facts facts = new Facts("en_US");
            Set<String> known = (Set<String>) MagicApi.field(facts, "items");
            for (ItemStack stack : new ItemStack[] {apple, carrot, paper, output}) known.add(Identity.item(Item.itemRegistry.getNameForObject(stack.getItem()),
                    stack.getItemDamage(), TypedNbt.encode(stack.getTagCompound())));
            RecipeRow row = new RecipeRow(facts, object("owner", "ae2", "handler", kind, "key", kind), "category_test", 0);
            AeRecipes.capture(recipe, handler, row);
            if (row.inputs.size() != 3 || row.outputs.size() != 1 || !"3".equals(row.outputs.get(0).getAsJsonObject().get("amount").getAsString())) {
                throw new AssertionError("AE2 projection lost inputs or native output quantity");
            }
            for (com.google.gson.JsonElement value : row.inputs) {
                com.google.gson.JsonObject choice = value.getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject();
                if (!"1".equals(choice.get("amount").getAsString()) || !choice.getAsJsonObject("rule").get("nbt").getAsBoolean()) {
                    throw new AssertionError("AE2 consumes one item per cell and ignores NBT");
                }
            }
            if (row.inputs.get(0).getAsJsonObject().getAsJsonArray("choices").size() != 2) {
                throw new AssertionError("AE2 alternative inputs must not collapse to their first display stack");
            }
            if (kind.equals("Shaped")) {
                com.google.gson.JsonObject grid = row.record.getAsJsonObject("grid");
                if (grid.get("width").getAsInt() != 2 || !grid.getAsJsonArray("cells").get(2).isJsonNull() || !grid.get("mirror").getAsBoolean()) {
                    throw new AssertionError("AE2 shaped grid holes or mirroring were lost");
                }
                java.lang.reflect.Field mirror = type.getDeclaredField("mirrored"); mirror.setAccessible(true); mirror.setBoolean(recipe, false);
                RecipeRow unmirrored = new RecipeRow(facts, object("owner", "ae2", "handler", kind, "key", kind), "category_test", 1);
                AeRecipes.capture(recipe, handler, unmirrored);
                if (unmirrored.record.getAsJsonObject("grid").get("mirror").getAsBoolean()) throw new AssertionError("AE2 disabled mirroring was lost");
            } else if (!row.properties.has("minecraft:shapeless")) throw new AssertionError("Shapeless semantics were lost");
            if (apple.stackSize != 13 || carrot.stackSize != 7 || apple.hasTagCompound() || output.stackSize != 3) throw new AssertionError("Projection mutated registry stacks");
        }
        System.out.println("Native AE2: enabled handlers, shaped holes/mirroring, shapeless inputs, NBT rules, native count and immutable source stacks passed");
    }

    private static Object ingredient(ItemStack... stacks) throws Exception {
        ItemStack stack = stacks[0];
        Class<?> api = Class.forName("appeng.api.recipes.IIngredient");
        return java.lang.reflect.Proxy.newProxyInstance(api.getClassLoader(), new Class<?>[] {api}, (proxy, method, args) -> {
            switch (method.getName()) {
                case "equals": return proxy == args[0];
                case "hashCode": return System.identityHashCode(proxy);
                case "getItemStackSet": return stacks;
                case "getItemStack": return stack;
                case "isAir": return false;
                case "getQty": return stack.stackSize;
                case "getDamageValue": return stack.getItemDamage();
                case "getNameSpace": return "minecraft";
                case "getItemName": return "test";
                case "bake": return null;
                default: throw new AssertionError("Unexpected ingredient API: " + method.getName());
            }
        });
    }
}
