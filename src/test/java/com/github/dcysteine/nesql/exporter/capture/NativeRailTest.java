package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.recipe.TemplateRecipeHandler;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Item;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import com.github.dcysteine.nesql.exporter.source.Identity;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import java.util.*;
import java.lang.reflect.Method;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Runs actual Railcraft managers in the existing isolated native bootstrap. */
final class NativeRailTest {
    static void run() throws Exception {
        String prefix = "tonius.neiintegration.mods.railcraft.RecipeHandler";
        for (String name : new String[] {"CokeOven", "BlastFurnace"}) {
            TemplateRecipeHandler handler = (TemplateRecipeHandler) Class.forName(prefix + name).newInstance();
            require(Recipes.adapter(handler) != null, "Missing native Railcraft adapter: " + name);
            verify(handler, name.equals("CokeOven"));
        }
        System.out.println("Native Railcraft: two-pass priority, subtype/wildcard metadata, exact/ignored NBT, synthetic presence, unit input, fluids, timing and owned projections passed");
    }
    @SuppressWarnings("unchecked")
    private static void verify(TemplateRecipeHandler handler, boolean coke) throws Exception {
        Class<?> managerType = Class.forName("mods.railcraft.common.util.crafting." + (coke ? "CokeOven" : "BlastFurnace") + "CraftingManager");
        Object manager = managerType.newInstance();
        ItemStack broad = new ItemStack(Items.coal, 17, 0), exact = new ItemStack(Items.coal, 7, 1);
        broad.setTagInfo("synthetic", new net.minecraft.nbt.NBTTagByte((byte) 0)); // ignored by the manager, rejected by the machine only on offered stacks
        ItemStack tagged = exact.copy(); tagged.setTagInfo("owner", new net.minecraft.nbt.NBTTagInt(1));
        ItemStack durable = new ItemStack(Items.iron_pickaxe, 6, 20);
        ItemStack zero = new ItemStack(Items.coal, 5, 2);
        ItemStack paper = new ItemStack(Items.paper, 2), book = new ItemStack(Items.book, 3), stick = new ItemStack(Items.stick, 4);
        add(manager, coke, broad, false, false, 51, paper);
        add(manager, coke, exact, true, true, 25, book);
        add(manager, coke, tagged, true, true, 25, stick);
        add(manager, coke, exact, true, true, 25, new ItemStack(Items.apple)); // unreachable duplicate
        add(manager, coke, durable, true, false, 25, stick);
        add(manager, coke, zero, true, false, 0, paper);
        List<?> recipes = (List<?>) managerType.getMethod("getRecipes").invoke(manager);
        RailRecipes adapter = new RailRecipes(handler, recipes);
        Map<String, ItemStack> known = new HashMap<>();
        for (Item item : new Item[] {Items.coal, Items.iron_pickaxe}) for (int meta : new int[] {0,1,2,5,20,21,32767}) {
            for (int tag = 0; tag < 4; tag++) {
                ItemStack stack = new ItemStack(item, 1, meta);
                if (tag > 0) stack.setTagCompound(new NBTTagCompound());
                if (tag == 2) stack.getTagCompound().setInteger("owner", 1);
                if (tag == 3) stack.getTagCompound().setBoolean("synthetic", false);
                known.put(id(stack), stack);
            }
        }
        for (ItemStack stack : new ItemStack[] {paper,book,stick,new ItemStack(Items.apple)}) known.put(id(stack), stack);
        List<RecipeRow> rows = new ArrayList<>();
        for (int i = 0; i < adapter.size(); i++) {
            Facts facts = new Facts("en_US"); ((Set<String>) MagicApi.field(facts, "items")).addAll(known.keySet());
            RecipeRow row = new RecipeRow(facts, object("owner","Railcraft","handler","native","key","furnace"), "category_test", i);
            if (!adapter.capture(i, row)) continue;
            rows.add(row);
            require(row.inputs.size() == 1, "Fuel was made into a fixed recipe ingredient");
            for (JsonElement choice : row.inputs.get(0).getAsJsonObject().getAsJsonArray("choices"))
                require(choice.getAsJsonObject().get("amount").getAsString().equals("1") && choice.getAsJsonObject().getAsJsonArray("returns").size() == 0,
                        "Native input count/absence of container returns changed");
            require(row.outputs.size() == (coke ? 2 : 1), "Native fluid output lost");
            if (row.properties.getAsJsonObject("railcraft:registeredTime").getAsJsonObject("value").get("value").getAsString().equals("51"))
                require(row.record.get("duration").getAsString().equals(coke ? "100" : "51"), "Native pulse quantization changed");
            if (!coke) require(row.properties.has("railcraft:fuel"), "Blast furnace lost independent fuel requirement");
        }
        require(rows.size() == 5, "Shadowed native entry was retained or an unshadowed recipe was dropped");
        Method lookup = managerType.getMethod("getRecipe", ItemStack.class);
        for (ItemStack stack : known.values()) {
            if (stack.getItem() != Items.coal && stack.getItem() != Items.iron_pickaxe) continue;
            for (int count : new int[] {1,23}) {
                ItemStack offered = stack.copy(); offered.stackSize = count;
                Object winner = lookup.invoke(manager, offered);
                if (coke && offered.hasTagCompound() && offered.getTagCompound().hasKey("synthetic")) winner = null;
                String result = winner == null ? null : id((ItemStack) winner.getClass().getMethod("getOutput").invoke(winner));
                List<String> matches = new ArrayList<>();
                for (RecipeRow row : rows) {
                    boolean accepts = false;
                    for (JsonElement raw : row.inputs.get(0).getAsJsonObject().getAsJsonArray("choices")) {
                        JsonObject choice = raw.getAsJsonObject();
                        accepts |= matches(choice.getAsJsonObject("rule"), known.get(choice.get("id").getAsString()), offered, known);
                    }
                    if (accepts) matches.add(row.outputs.get(0).getAsJsonObject().get("id").getAsString());
                }
                require(result == null ? matches.isEmpty() : matches.equals(Collections.singletonList(result)),
                        "Native priority differs: " + offered + "; matches=" + matches + "; expected=" + result);
            }
        }
        require(broad.stackSize == 17 && broad.getTagCompound().hasKey("synthetic") && exact.stackSize == 7 && tagged.getTagCompound().getInteger("owner") == 1 && paper.stackSize == 2,
                "Adapter mutated registry inputs or outputs");
        try { new RailRecipes(handler, Collections.singletonList(new Object())); throw new AssertionError("Unknown override was accepted"); }
        catch (Jobs.Fault expected) { require(expected.code.equals("recipe_unsupported"), "Wrong unknown implementation failure"); }
        if (coke) {
            Object overflow = managerType.newInstance();
            add(overflow, true, exact, true, true, Integer.MAX_VALUE, paper);
            RailRecipes invalid = new RailRecipes(handler, (List<?>) managerType.getMethod("getRecipes").invoke(overflow));
            try { invalid.capture(0, new RecipeRow(new Facts("en_US"), object("owner","Railcraft","handler","native","key","overflow"), "category_test",0));
                throw new AssertionError("Native counter wraparound was flattened to a linear duration"); }
            catch (Jobs.Fault expected) { require(expected.code.equals("recipe_unsupported"), "Wrong counter wraparound failure"); }
        }
        for (int[] bar : RailRecipes.progressBars(handler)) require(Ui.progress(bar[4],bar[5],bar[6],bar[7]).size() > 1, "Native progress flattened");
    }
    private static void add(Object manager, boolean coke, ItemStack input, boolean damage, boolean nbt, int time, ItemStack output) throws Exception {
        if (coke) manager.getClass().getMethod("addRecipe",ItemStack.class,boolean.class,boolean.class,ItemStack.class,FluidStack.class,int.class)
                .invoke(manager,input,damage,nbt,output,new FluidStack(FluidRegistry.WATER,250),time);
        else manager.getClass().getMethod("addRecipe",ItemStack.class,boolean.class,boolean.class,int.class,ItemStack.class).invoke(manager,input,damage,nbt,time,output);
    }
    private static String id(ItemStack stack) { return Identity.item(Item.itemRegistry.getNameForObject(stack.getItem()), Items.feather.getDamage(stack), TypedNbt.encode(stack.getTagCompound())); }
    private static boolean matches(JsonObject rule, ItemStack anchor, ItemStack offered, Map<String, ItemStack> known) {
        String kind = rule.get("kind").getAsString();
        if (kind.equals("except")) {
            if (!matches(rule.getAsJsonObject("base"), anchor, offered, known)) return false;
            for (JsonElement raw : rule.getAsJsonArray("exclude")) {
                JsonObject prior = raw.getAsJsonObject();
                if (matches(prior.getAsJsonObject("rule"), known.get(prior.get("id").getAsString()), offered, known)) return false;
            }
            return true;
        }
        if (offered.getItem() != anchor.getItem()) return false;
        if (kind.equals("exact")) return offered.getItemDamage() == anchor.getItemDamage() && ItemStack.areItemStackTagsEqual(offered, anchor);
        if (!rule.get("meta").getAsBoolean() && offered.getItemDamage() != anchor.getItemDamage()) return false;
        if (kind.equals("tags")) return !offered.hasTagCompound() || !offered.getTagCompound().hasKey("synthetic");
        return rule.get("nbt").getAsBoolean() || ItemStack.areItemStackTagsEqual(offered, anchor);
    }
    private static void require(boolean test, String message) { if (!test) throw new AssertionError(message); }
}
