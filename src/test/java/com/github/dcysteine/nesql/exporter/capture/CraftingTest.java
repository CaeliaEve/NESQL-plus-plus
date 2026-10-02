package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.ShapedRecipeHandler;
import com.github.dcysteine.nesql.exporter.source.Identity;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import cpw.mods.fml.relauncher.ReflectionHelper;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.oredict.ShapedOreRecipe;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Native map result on owned inventory; no world lookup or map-ID allocation. */
final class CraftingTest {
    static void run() {
        RecipesMapExtending map = new RecipesMapExtending();
        if (map.getRecipeOutput().stackSize != 0 || map.getRecipeOutput().getItem() != Items.map)
            throw new AssertionError("Native map placeholder changed");
        ItemStack center = new ItemStack(Items.filled_map, 17, 37);
        NBTTagCompound tags = new NBTTagCompound(); tags.setString("owner", "retained"); center.setTagCompound(tags);
        ItemStack pending = Crafting.pending(map, center);
        if (pending.getItem() != Items.filled_map || pending.getItemDamage() != 37 || pending.stackSize != 1
                || !pending.getTagCompound().getBoolean("map_is_scaling")
                || !pending.getTagCompound().getString("owner").equals("retained")
                || center.getTagCompound().hasKey("map_is_scaling") || center.stackSize != 17)
            throw new AssertionError("Pending map lost identity/data or mutated input");
        ShapedRecipes ordinary = new ShapedRecipes(1, 1, new ItemStack[]{new ItemStack(Items.paper)}, new ItemStack(Items.map, 0));
        ShapedOreRecipe empty = new ShapedOreRecipe(new ItemStack(Items.paper), "x", 'x', "nesqlMissingMapTestOre");
        ShapedRecipeHandler handler = new ShapedRecipeHandler();
        Crafting crafting = new Crafting(handler, Arrays.asList(ordinary, empty,
                new ShapelessRecipes(new ItemStack(Items.paper), Collections.singletonList(new ItemStack(Items.paper))), map));
        if (handler.arecipes.size() != 2 || !crafting.sourceType(1).equals(RecipesMapExtending.class.getName()))
            throw new AssertionError("Native skip/order lost recipe binding");
        Facts facts = new Facts("en_US");
        Set<String> known = ReflectionHelper.getPrivateValue(Facts.class, facts, "items");
        for (ItemStack item : Arrays.asList(new ItemStack(Items.paper), center, pending))
            known.add(Identity.item(net.minecraft.item.Item.itemRegistry.getNameForObject(item.getItem()), item.getItemDamage(), TypedNbt.encode(item.getTagCompound())));
        ShapedRecipeHandler.CachedShapedRecipe cached = (ShapedRecipeHandler.CachedShapedRecipe) handler.arecipes.get(1);
        for (PositionedStack input : cached.ingredients) if (input.relx == 43 && input.rely == 24) {
            input.items = new ItemStack[]{center.copy()}; input.item = input.items[0];
        }
        RecipeRow row = new RecipeRow(facts, object("owner", "minecraft", "handler", "test", "key", "test"), "test", 1);
        if (crafting.capture(0, row)) throw new AssertionError("Zero output picture was mistaken for map semantics");
        if (!crafting.capture(1, row) || row.inputs.size() != 9 || row.outputs.size() != 1
                || !row.record.getAsJsonObject("process").get("kind").getAsString().equals("mapScaling")
                || row.outputs.get(0).getAsJsonObject().getAsJsonObject("change").get("input").getAsInt() != 4
                || row.record.getAsJsonObject("grid").getAsJsonArray("cells").size() != 9)
            throw new AssertionError("Map symbolic process or native grid missing");
        Collections.swap(handler.arecipes, 0, 1);
        try { crafting.capture(1, row); throw new AssertionError("Changed cache/source binding accepted"); }
        catch (Jobs.Fault expected) { if (!expected.code.equals("slot_changed")) throw expected; }
        System.out.println("Crafting: native map placeholder, pending output, source/cache binding and grid passed");
    }
}
