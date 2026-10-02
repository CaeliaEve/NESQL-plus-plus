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
        if (wireless()) {
            Class<?> nativeHandler = Class.forName("net.p455w0rd.wirelesscraftingterminal.integration.modules.NEIHelpers.NEIAEShapedRecipeHandler");
            if (!nativeHandler.getProtectionDomain().getCodeSource().getLocation().toString().contains("/native-tests/wireless.jar!"))
                throw new AssertionError("Wireless test must use the pinned derived jar");
            if (Recipes.adapter((ICraftingHandler)nativeHandler.newInstance()) == null)
                throw new AssertionError("Wireless crafting terminal has no production adapter");
        }
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
        ItemStack output = new ItemStack(Items.diamond, 3);
        for (String kind : wireless() ? new String[] {"Shaped", "Shapeless", "Wireless"} : new String[] {"Shaped", "Shapeless"}) {
            boolean wct = kind.equals("Wireless"), shaped = !kind.equals("Shapeless");
            String root = "net.p455w0rd.wirelesscraftingterminal.";
            Class<?> api = Class.forName(wct ? root + "api.recipes.IIngredient" : "appeng.api.recipes.IIngredient");
            Object apples = ingredient(api, apple, carrot), papers = ingredient(api, paper);
            Class<?> type = Class.forName(wct ? root + "api.recipes.game.ShapedRecipe" : "appeng.recipes.game." + kind + "Recipe");
            Object[] recipeArgs = shaped ? new Object[] {new String[] {"AP", " A"}, 'A', apples, 'P', papers}
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
            inventory.setInventorySlotContents(shaped ? 4 : 2, new ItemStack(Items.apple));
            if (!recipe.matches(inventory, null)) throw new AssertionError("Pinned native recipe should ignore offered NBT/count");
            if (recipe.getCraftingResult(inventory).stackSize != 3) throw new AssertionError("Native result count changed");
            TemplateRecipeHandler handler = (TemplateRecipeHandler) Class.forName(wct ? root + "integration.modules.NEIHelpers.NEIAEShapedRecipeHandler"
                    : "appeng.integration.modules.NEIHelpers.NEIAE" + kind + "RecipeHandler").newInstance();
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
            if (shaped) {
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
            if (shaped) boundaries(type, api, handler, facts, known, output, disabled);
        }
        System.out.println("Native AE2" + (wireless() ? " and Wireless terminal" : "") + ": enabled handlers, shaped holes/mirroring, shapeless inputs, NBT rules, native count and immutable source stacks passed");
    }

    private static boolean wireless() { return "wireless".equals(System.getProperty("nesql.nativeFamily")); }

    private static void boundaries(Class<?> type, Class<?> api, TemplateRecipeHandler handler, Facts facts,
                                   Set<String> known, ItemStack output, IRecipe disabled) throws Exception {
        try {
            AeRecipes.capture(disabled, handler, new RecipeRow(facts, object("owner","ae","handler","test","key","test"), "category_test", 2));
            throw new AssertionError("A disabled recipe became exportable");
        } catch (com.github.dcysteine.nesql.exporter.task.Jobs.Fault expected) {
            if (!expected.code.equals("slot_changed")) throw expected;
        }
        ItemStack wildcard = new ItemStack(Items.apple, 17, 32767), milk = new ItemStack(Items.milk_bucket);
        for (ItemStack stack : new ItemStack[]{wildcard, milk, new ItemStack(Items.bucket)})
            known.add(Identity.item(Item.itemRegistry.getNameForObject(stack.getItem()), stack.getItemDamage(), null));
        IRecipe recipe = (IRecipe)type.getConstructor(ItemStack.class,Object[].class).newInstance(output,
                new Object[]{new String[]{"WB"}, 'W', ingredient(api,wildcard), 'B', ingredient(api,milk)});
        InventoryCrafting grid = new InventoryCrafting(new Container() {
            @Override public boolean canInteractWith(EntityPlayer player) { return true; }
        },3,3);
        ItemStack offered = new ItemStack(Items.apple,1,19); offered.setTagInfo("owner",new net.minecraft.nbt.NBTTagString("ignored"));
        grid.setInventorySlotContents(0,offered); grid.setInventorySlotContents(1,milk.copy());
        if (!recipe.matches(grid,null)) throw new AssertionError("Native wildcard did not ignore metadata and NBT");
        grid.setInventorySlotContents(2,new ItemStack(Items.paper));
        if (recipe.matches(grid,null)) throw new AssertionError("Native shaped recipe accepted an extra occupied cell");
        RecipeRow row = new RecipeRow(facts,object("owner","ae","handler","test","key","wildcard"),"category_test",3);
        AeRecipes.capture(recipe,handler,row);
        com.google.gson.JsonObject first = row.inputs.get(0).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject();
        com.google.gson.JsonObject second = row.inputs.get(1).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject();
        if (!first.getAsJsonObject("rule").get("meta").getAsBoolean() || !first.getAsJsonObject("rule").get("nbt").getAsBoolean()
                || second.getAsJsonArray("returns").size()!=1 || !second.getAsJsonArray("returns").get(0).getAsJsonObject().get("id").getAsString()
                .equals(Identity.item("minecraft:bucket",0,null))) throw new AssertionError("Wildcard or native crafting container return lost");
        if (wildcard.stackSize!=17 || milk.stackSize!=1) throw new AssertionError("Boundary projection mutated native sources");
        if (wireless() && type.getName().contains("wirelesscraftingterminal")) {
            TemplateRecipeHandler foreign=(TemplateRecipeHandler)Class.forName("appeng.integration.modules.NEIHelpers.NEIAEShapedRecipeHandler").newInstance();
            try { AeRecipes.capture(recipe,foreign,row); throw new AssertionError("Foreign native API silently accepted"); }
            catch (com.github.dcysteine.nesql.exporter.task.Jobs.Fault expected) { if (!expected.code.equals("recipe_unsupported")) throw expected; }
        }
    }
    private static Object ingredient(Class<?> api, ItemStack... stacks) throws Exception {
        ItemStack stack = stacks[0];
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
