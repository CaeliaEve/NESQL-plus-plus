package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.source.Identity;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonObject;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.*;
import net.minecraft.world.World;
import net.minecraftforge.oredict.ShapedOreRecipe;
import net.minecraftforge.oredict.ShapelessOreRecipe;
import java.util.*;
import java.lang.reflect.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Real Railcraft registry lookup and native NEI cache; no world or render loop. */
final class NativeRollingTest {
    @SuppressWarnings("unchecked")
    static void run() throws Exception {
        String prefix = "tonius.neiintegration.mods.railcraft.RecipeHandlerRollingMachine";
        TemplateRecipeHandler shaped = (TemplateRecipeHandler) Class.forName(prefix + "Shaped").newInstance();
        TemplateRecipeHandler shapeless = (TemplateRecipeHandler) Class.forName(prefix + "Shapeless").newInstance();
        Object manager = Class.forName("mods.railcraft.common.util.crafting.RollingMachineCraftingManager").newInstance();
        List<IRecipe> registry = (List<IRecipe>) manager.getClass().getMethod("getRecipeList").invoke(manager);
        ItemStack milk = new ItemStack(Items.milk_bucket, 7), iron = new ItemStack(Items.iron_ingot, 19);
        iron.setTagInfo("ignored", new net.minecraft.nbt.NBTTagInt(4));
        ItemStack output = new ItemStack(Items.paper, 3);
        registry.add(new ShapedRecipes(2, 2, new ItemStack[] {milk, null, null, iron}, output));
        registry.add(new ShapelessRecipes(new ItemStack(Items.book), Arrays.asList(milk, iron)));
        registry.add(new ShapedOreRecipe(new ItemStack(Items.stick, 2), false, "I", 'I', iron));
        registry.add(new ShapelessOreRecipe(new ItemStack(Items.apple), milk, iron));
        InventoryCrafting inv = new InventoryCrafting(new Container() {
            public boolean canInteractWith(EntityPlayer player) { return false; }
        }, 3, 3);
        inv.setInventorySlotContents(1, milk.copy()); inv.setInventorySlotContents(5, iron.copy());
        Method find = manager.getClass().getMethod("findMatchingRecipe", InventoryCrafting.class, World.class);
        require(((ItemStack) find.invoke(manager, inv, null)).getItem() == Items.paper, "Native shifted shaped recipe lost registry precedence");
        inv.setInventorySlotContents(5, null); inv.setInventorySlotContents(4, iron.copy());
        require(((ItemStack) find.invoke(manager, inv, null)).getItem() == Items.book, "Native shapeless fallback changed");
        Object shapedAdapter = adapter(shaped, registry, true), shapelessAdapter = adapter(shapeless, registry, false);
        require(((RegistryRecipes) shapedAdapter).size() == 2 && ((RegistryRecipes) shapelessAdapter).size() == 2,
                "Native shaped/shapeless enumeration differs");
        for (Object candidate : new Object[] {shapedAdapter, shapelessAdapter}) {
            for (int i = 0; i < ((RegistryRecipes) candidate).size(); i++) {
                Facts facts = new Facts("en_US"); Set<String> known = (Set<String>) MagicApi.field(facts, "items");
                for (ItemStack stack : new ItemStack[] {milk, iron, output, new ItemStack(Items.book), new ItemStack(Items.stick), new ItemStack(Items.apple),
                        new ItemStack(Items.milk_bucket), new ItemStack(Items.iron_ingot)}) known.add(id(stack));
                RecipeRow row = new RecipeRow(facts, object("owner","Railcraft","handler","native","key","rolling"), "category_test", i);
                require(((RegistryRecipes) candidate).capture(i, row), "Reachable rolling recipe was omitted");
                JsonObject process = row.record.getAsJsonObject("process");
                require(process.get("kind").getAsString().equals("rolling") && process.get("powered").getAsBoolean() == (candidate == shapedAdapter),
                        "Native rolling power mode was lost");
                int sourceIndex = candidate == shapedAdapter ? i * 2 : i * 2 + 1;
                require(process.getAsJsonArray("earlier").size() == sourceIndex, "Cross-family registry precedence was dropped");
                for (com.google.gson.JsonElement raw : row.inputs) for (com.google.gson.JsonElement c : raw.getAsJsonObject().getAsJsonArray("choices")) {
                    JsonObject choice = c.getAsJsonObject();
                    require(choice.get("amount").getAsString().equals("1") && choice.getAsJsonArray("returns").size() == 0
                            && choice.getAsJsonObject("consume").get("kind").getAsString().equals("consume"),
                            "Rolling must consume one, without a crafting container return");
                    require(choice.getAsJsonObject("rule").get("nbt").getAsBoolean(), "Ignored native input NBT became required");
                }
                TemplateRecipeHandler h = candidate == shapedAdapter ? shaped : shapeless;
                require(h.arecipes.size() == 1 && h.arecipes.get(0).getResult().relx == 88
                        && h.arecipes.get(0).getResult().rely == (h == shaped ? 18 : 16), "Native cache projection changed");
                if (sourceIndex == 0) require(row.record.getAsJsonObject("grid").getAsJsonArray("cells").toString().equals("[0,null,null,1]"),
                        "Explicit empty crafting cells were lost");
            }
        }
        require(milk.stackSize == 7 && iron.stackSize == 19 && iron.getTagCompound().getInteger("ignored") == 4 && output.stackSize == 3,
                "Owned capture mutated the native registry");
        List<IRecipe> unknown = Arrays.asList(new ShapedRecipes(1, 1, new ItemStack[] {iron}, output) {});
        try { adapter(shaped, unknown, true); throw new AssertionError("Unknown overridden selector accepted"); }
        catch (InvocationTargetException ex) { require(ex.getCause() instanceof Jobs.Fault, "Unexpected unknown-selector failure"); }
        // Forge's shapeless matcher is greedy, not a permutation search over requests.
        ItemStack wildcard = new ItemStack(Items.coal, 8, 32767), charcoal = new ItemStack(Items.coal, 9, 1);
        ShapelessOreRecipe greedy = new ShapelessOreRecipe(output, wildcard, charcoal);
        for (int i = 0; i < 9; i++) inv.setInventorySlotContents(i, null);
        inv.setInventorySlotContents(0, new ItemStack(Items.coal, 1, 1));
        inv.setInventorySlotContents(1, new ItemStack(Items.coal, 1, 0));
        require(!greedy.matches(inv, null), "Native greedy choice was replaced with backtracking");
        inv.setInventorySlotContents(0, new ItemStack(Items.coal, 1, 0));
        inv.setInventorySlotContents(1, new ItemStack(Items.coal, 1, 1));
        require(greedy.matches(inv, null), "Native greedy order no longer admits a valid grid");
        RegistryRecipes greedyAdapter = (RegistryRecipes) adapter(shapeless, Arrays.asList(greedy), true);
        Facts greedyFacts = new Facts("en_US"); Set<String> greedyKnown = (Set<String>) MagicApi.field(greedyFacts, "items");
        for (ItemStack s : new ItemStack[] {wildcard, charcoal, new ItemStack(Items.coal), output}) greedyKnown.add(id(s));
        RecipeRow greedyRow = new RecipeRow(greedyFacts, object("owner","Railcraft","handler","native","key","rolling"),"category_test",0);
        require(greedyAdapter.capture(0,greedyRow) && greedyRow.inputs.get(0).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject().getAsJsonObject("rule").get("meta").getAsBoolean(),
                "Native wildcard request lost its unrestricted metadata predicate");
        ShapedRecipes dynamic = new ShapedRecipes(1,1,new ItemStack[] {iron},output).func_92100_c();
        inv.setInventorySlotContents(0, iron.copy());
        require(dynamic.getCraftingResult(inv).getTagCompound().getInteger("ignored") == 4, "Native result-NBT copy changed");
        try { ((RegistryRecipes) adapter(shaped,Arrays.asList(dynamic),true)).capture(0,greedyRow);
            throw new AssertionError("Input-dependent result NBT became a false fixed result"); }
        catch (Jobs.Fault ex) { require(ex.code.equals("recipe_unsupported"), "Wrong dynamic-result failure"); }
        System.out.println("Native rolling: four recipe classes, native first-match lookup, shifted grid, holes, cross-family priority, no container returns and owned NEI projections passed");
    }
    private static Object adapter(TemplateRecipeHandler handler, List<IRecipe> registry, boolean powered) throws Exception {
        Constructor<?> constructor = Class.forName("com.github.dcysteine.nesql.exporter.capture.RollingRecipes")
                .getDeclaredConstructor(TemplateRecipeHandler.class, List.class, boolean.class);
        constructor.setAccessible(true); return constructor.newInstance(handler, registry, powered);
    }
    private static String id(ItemStack stack) { return Identity.item(Item.itemRegistry.getNameForObject(stack.getItem()), Items.feather.getDamage(stack), TypedNbt.encode(stack.getTagCompound())); }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
