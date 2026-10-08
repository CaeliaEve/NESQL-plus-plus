package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.source.Identity;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.*;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.*;
import net.minecraft.nbt.NBTTagInt;
import net.minecraftforge.oredict.ShapedOreRecipe;
import java.lang.reflect.*;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** The regression catches treating QED as ordinary crafting (container returns) or losing registry priority. */
final class NativeQedTest {
    static void run() throws Exception {
        Class<?> adapter;
        try { adapter = Class.forName("com.github.dcysteine.nesql.exporter.capture.QedRecipes"); }
        catch (ClassNotFoundException missing) { throw new AssertionError("QED has no native registry and ender-flux adapter", missing); }
        TemplateRecipeHandler handler = (TemplateRecipeHandler) Class.forName("com.rwtema.extrautils.nei.EnderConstructorHandler").newInstance();
        ItemStack milk = new ItemStack(Items.milk_bucket, 23), coal = new ItemStack(Items.coal, 17, 0);
        ItemStack result = new ItemStack(Items.emerald, 3); result.setTagInfo("native", new NBTTagInt(12));
        List<IRecipe> recipes = new ArrayList<>();
        recipes.add(new ShapelessRecipes(new ItemStack(Items.diamond), Collections.singletonList(coal.copy())));
        recipes.add(new ShapedRecipes(1, 1, new ItemStack[]{coal.copy()}, new ItemStack(Items.apple)));
        recipes.add(new ShapedRecipes(2, 1, new ItemStack[]{milk.copy(), coal.copy()}, result));
        Class<?> manager = Class.forName("com.rwtema.extrautils.tileentity.enderconstructor.EnderConstructorRecipesHandler");
        manager.getField("recipes").set(null, new ArrayList<>(recipes));
        Constructor<?> ctor = adapter.getDeclaredConstructor(TemplateRecipeHandler.class, List.class); ctor.setAccessible(true);
        RegistryRecipes capture = (RegistryRecipes) ctor.newInstance(handler, recipes);
        require(capture.size() == 2, "QED native loader must omit shapeless display entries, while retaining their selection priority");
        Facts facts = new Facts("en_US");
        @SuppressWarnings("unchecked") Set<String> items = (Set<String>) MagicApi.field(facts, "items");
        for (ItemStack stack : Arrays.asList(milk, coal, result, new ItemStack(Items.diamond), new ItemStack(Items.apple))) items.add(id(stack));
        RecipeRow row = new RecipeRow(facts, object("owner", "ExtraUtilities", "handler", handler.getClass().getName(), "key", "qed"), "category_test", 1);
        require(capture.capture(1, row), "Native shaped QED recipe was lost");
        JsonObject process = row.record.getAsJsonObject("process");
        require(process.get("kind").getAsString().equals("qed") && process.get("enderFlux").getAsString().equals("20000"),
                "Native QED ender-flux cost was lost or mislabeled as EU/t");
        require(process.getAsJsonArray("earlier").size() == 2
                && process.getAsJsonArray("earlier").get(0).getAsJsonObject().get("grid").isJsonNull(),
                "A hidden shapeless native entry was dropped from first-match priority");
        require(row.record.get("energy").isJsonNull() && row.record.get("duration").isJsonNull(), "Supply-dependent QED timing became a fixed energy/time value");
        require(row.inputs.size() == 2 && row.outputs.get(0).getAsJsonObject().get("amount").getAsString().equals("3"), "Native QED quantities changed");
        JsonObject grid = row.record.getAsJsonObject("grid");
        require(grid.get("width").getAsInt() == 2 && grid.get("height").getAsInt() == 1 && grid.get("mirror").getAsBoolean(), "Native QED grid changed");
        for (JsonElement raw : row.inputs) {
            JsonObject choice = raw.getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject();
            require(choice.get("amount").getAsString().equals("1") && choice.getAsJsonArray("returns").size() == 0
                    && choice.getAsJsonObject("consume").get("kind").getAsString().equals("consume"),
                    "QED incorrectly honored template count or crafting-container returns");
            require(choice.getAsJsonObject("rule").get("nbt").getAsBoolean(), "QED ordinary crafting matching should ignore offered NBT");
        }
        for (boolean mirror : new boolean[]{false, true}) {
            InventoryCrafting inventory = new InventoryCrafting(new Container() { public boolean canInteractWith(EntityPlayer p) { return false; } }, 3, 3);
            ItemStack offered = milk.copy(); offered.stackSize = 1; offered.setTagInfo("offered", new NBTTagInt(99));
            inventory.setInventorySlotContents(mirror ? 1 : 0, offered);
            inventory.setInventorySlotContents(mirror ? 0 : 1, coal.copy());
            ItemStack nativeOutput = (ItemStack) manager.getMethod("findMatchingRecipe", InventoryCrafting.class, net.minecraft.world.World.class).invoke(null, inventory, null);
            require(ItemStack.areItemStacksEqual(nativeOutput, result), "Fixture does not match native QED mirrored/ignored-NBT behavior");
        }
        require(handler.arecipes.size() == 1 && handler.arecipes.get(0).getIngredients().size() == 2, "QED source-owned view was not installed");
        require(milk.stackSize == 23 && coal.stackSize == 17 && result.getTagCompound().getInteger("native") == 12, "QED capture mutated registry stacks");
        capture.verify(); result.stackSize = 4;
        try { capture.verify(); throw new AssertionError("QED output mutation was not detected"); }
        catch (Jobs.Fault expected) { require(expected.code.equals("recipe_changed"), "Wrong QED drift guard failure"); }
        result.stackSize = 3;
        try { ctor.newInstance(handler, Collections.singletonList(new Object())); throw new AssertionError("Unknown QED callback executed or accepted"); }
        catch (InvocationTargetException expected) { require(expected.getCause() instanceof Jobs.Fault, "Wrong QED callback guard"); }
        row.finish(); java.nio.file.Files.write(java.nio.file.Paths.get("build/native-tests/qed-record.json"), CanonicalJson.bytes(row.record));
        System.out.println("Native QED: shaped enumeration, hidden shapeless priority, unit consumption without containers, mirrored grid, typed output NBT, 20000 flux and registry drift passed");
    }
    private static String id(ItemStack stack) { return Identity.item(Item.itemRegistry.getNameForObject(stack.getItem()), Items.feather.getDamage(stack), TypedNbt.encode(stack.getTagCompound())); }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
