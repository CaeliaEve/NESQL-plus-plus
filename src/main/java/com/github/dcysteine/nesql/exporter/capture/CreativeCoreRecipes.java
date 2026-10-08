package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.init.Items;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraftforge.oredict.OreDictionary;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Pinned CreativeCore native predicates; IRecipeInfo's displayed stacks are not its matcher. */
final class CreativeCoreRecipes implements RegistryRecipes {
    private static final String ROOT = "com.creativemd.creativecore.";
    private static final String HANDLER = ROOT + "api.nei.NEIRecipeInfoHandler";
    private static final String SHAPED = ROOT + "common.recipe.BetterShapedRecipe";
    private static final String SHAPELESS = ROOT + "common.recipe.entry.BetterShapelessRecipe";
    private final TemplateRecipeHandler handler;
    private final List<Entry> entries;
    private final String signature;

    static boolean supports(ICraftingHandler handler) { return handler.getClass().getName().equals(HANDLER); }
    CreativeCoreRecipes(TemplateRecipeHandler handler) {
        version("creativecore", "1.5.14-GTNH");
        if (!supports(handler)) throw fault("Unadapted CreativeCore recipe handler");
        this.handler = handler; entries = registry(); signature = signature(entries);
    }
    public int size() { return entries.size(); }
    public boolean capture(int index, RecipeRow row) { return capture(entries.get(index), handler, row); }
    public void verify() {
        if (!signature.equals(signature(registry()))) throw new Jobs.Fault("environment_changed", "CreativeCore recipe registry or input predicates changed");
    }
    private static List<Entry> registry() {
        List<Entry> result = new ArrayList<>(); Class<?> api = type(ROOT + "common.recipe.IRecipeInfo");
        for (Object recipe : CraftingManager.getInstance().getRecipeList()) if (api.isInstance(recipe)) {
            Jobs.checkpoint();
            result.add(new Entry((IRecipe)recipe));
            if (result.size() > 262144) throw fault("CreativeCore native registry exceeds its budget");
        }
        return result;
    }
    static boolean capture(IRecipe source, TemplateRecipeHandler handler, RecipeRow row) {
        return capture(new Entry(source), handler, row);
    }
    private static boolean capture(Entry entry, TemplateRecipeHandler handler, RecipeRow row) {
        if (!supports(handler)) throw fault("Unadapted CreativeCore native view");
        Object[] projection = new Object[entry.groups.size()];
        for (int i = 0; i < projection.length; i++) {
            Group group = entry.groups.get(i);
            if (group == null) continue;
            if (group.patterns.isEmpty()) return false;
            ArrayList<ItemStack> display = new ArrayList<>();
            for (Pattern pattern : group.patterns) display.add(pattern.item.copy());
            projection[i] = display;
        }
        // The pinned shapeless UI truncates height with integer division. Preserve
        // that view while retaining every semantic ingredient, including omitted cells.
        TemplateRecipeHandler.CachedRecipe cached = (TemplateRecipeHandler.CachedRecipe)TinkerRecipes.construct(
                type(HANDLER + "$CachedInfoShapedRecipe"),
                new Class<?>[]{handler.getClass(), int.class, int.class, Object[].class, ItemStack.class},
                handler, entry.width, entry.height, projection, entry.output.copy());
        List<PositionedStack> positions = cached.getIngredients();
        JsonArray cells = array(); int slot = 0, visible = 0;
        for (int cell = 0; cell < entry.groups.size(); cell++) {
            Group group = entry.groups.get(cell);
            if (group == null) { cells.add(value(null)); continue; }
            int x = 25 + cell % entry.width * 18, y = 6 + cell / entry.width * 18;
            PositionedStack position = null;
            for (PositionedStack candidate : positions) if (candidate.relx == x && candidate.rely == y) {
                if (position != null) throw new Jobs.Fault("slot_changed", "CreativeCore native view repeats a cell");
                position = candidate;
            }
            if (cell < entry.width * entry.height) {
                if (position == null) throw new Jobs.Fault("slot_changed", "CreativeCore native view lost a visible input");
                visible++;
            } else if (position != null) throw new Jobs.Fault("slot_changed", "CreativeCore native view unexpectedly displays a truncated cell");
            List<RecipeRow.Ingredient> choices = new ArrayList<>(); Map<String, ItemStack> returns = new HashMap<>();
            for (Pattern pattern : group.patterns) {
                choices.add(new RecipeRow.Ingredient(pattern.item.copy(), 1, false, pattern.rule()));
                if (pattern.returned != null) returns.put(row.facts.item(pattern.item), pattern.returned);
            }
            row.itemInput(position, slot, choices, false);
            for (com.google.gson.JsonElement value : row.inputs.get(row.inputs.size() - 1).getAsJsonObject().getAsJsonArray("choices")) {
                JsonObject choice = value.getAsJsonObject(); ItemStack returned = returns.get(choice.get("id").getAsString());
                if (returned != null) choice.getAsJsonArray("returns").add(object("kind", "item", "id", row.facts.item(returned), "amount", Integer.toString(returned.stackSize)));
            }
            cells.add(value(slot++));
        }
        if (positions.size() != visible) throw new Jobs.Fault("slot_changed", "CreativeCore native view gained unexpected inputs");
        if (entry.shaped) row.record.add("grid", object("width", entry.width, "height", entry.height, "cells", cells, "mirror", true));
        row.itemOutput(cached.getResult(), 0, entry.output.copy(), 10000);
        handler.arecipes.clear(); handler.arecipes.add(cached); return true;
    }
    private static final class Entry {
        final boolean shaped;
        final int width, height;
        final List<Group> groups = new ArrayList<>();
        final ItemStack output;
        final String signature;
        Entry(IRecipe recipe) {
            if (recipe == null || !(recipe.getClass().getName().equals(SHAPED) || recipe.getClass().getName().equals(SHAPELESS)))
                throw fault("Unadapted CreativeCore IRecipeInfo implementation");
            shaped = recipe.getClass().getName().equals(SHAPED);
            List<?> raw = shaped ? Arrays.asList((Object[])field(recipe,"info")) : (List<?>)field(recipe,"info");
            width = (Integer)field(recipe,"width");
            height = shaped ? (Integer)field(recipe,"height") : raw.size() / Math.max(1,width);
            if (width < 1 || width > 3 || height < 1 || height > 3 || raw.size() < 1 || raw.size() > 9
                    || shaped && raw.size() != width * height) throw fault("Invalid CreativeCore native crafting grid");
            Work work = new Work(); JsonArray inputs = array();
            for (Object input : raw) {
                if (input == null) {
                    if (!shaped) throw fault("Native CreativeCore shapeless recipe contains a null predicate");
                    groups.add(null); inputs.add(value(null)); continue;
                }
                Group group = new Group(input, work);
                if (!shaped) for (Group previous : groups) {
                    // Equal or disjoint predicates make the native greedy removal
                    // equivalent to unordered matching. Partial overlaps do not.
                    if (!group.signature.equals(previous.signature) && group.overlaps(previous,work))
                        throw fault("CreativeCore shapeless predicates overlap with native order-dependent greedy removal");
                }
                groups.add(group); inputs.add(value(group.signature));
            }
            ItemStack result = (ItemStack)field(recipe,"output");
            if (result == null || result.getItem() == null || result.stackSize <= 0) throw fault("Invalid CreativeCore fixed recipe output");
            output = result.copy();
            signature = CanonicalJson.digest(object("class", recipe.getClass().getName(), "width", width, "height", height, "inputs", inputs, "output", stack(output)));
        }
    }
    private static final class Group {
        final List<Pattern> patterns = new ArrayList<>();
        final String signature;
        Group(Object source, Work work) {
            String name = source.getClass().getName();
            int minimum = (Integer)field(source,"stackSize");
            if (minimum < 0 || minimum > 1) throw fault("CreativeCore matcher minimum stack count differs from vanilla unit consumption");
            switch (name) {
                case ROOT + "common.utils.stack.StackInfoItem":
                    patterns.add(new Pattern(new ItemStack((Item)field(source,"item")),true,true)); break;
                case ROOT + "common.utils.stack.StackInfoItemStack":
                    // Metadata 32767 is literal in this native predicate, not Forge's wildcard.
                    patterns.add(new Pattern((ItemStack)field(source,"stack"),false,!(Boolean)field(source,"needNBT"))); break;
                case ROOT + "common.utils.stack.StackInfoOre":
                    String ore = (String)field(source,"ore");
                    int id = -1; for (String key : OreDictionary.getOreNames()) { work.step(); if (key.equals(ore)) { id = OreDictionary.getOreID(key); break; } }
                    if (id >= 0) for (ItemStack item : OreDictionary.getOres(id)) {
                        work.step(); patterns.add(new Pattern(item,Items.feather.getDamage(item)==32767,true));
                    }
                    break;
                case ROOT + "common.utils.stack.StackInfoBlock":
                case ROOT + "common.utils.stack.StackInfoMaterial":
                    boolean material = name.endsWith("StackInfoMaterial");
                    Object criterion = field(source,material?"material":"block");
                    if (criterion == null) throw fault("CreativeCore block/material predicate has no target");
                    String getter = cpw.mods.fml.relauncher.ReflectionHelper.findMethod(Block.class,null,
                            new String[]{"getMaterial","func_149688_o"}).getName();
                    for (Object registered : Item.itemRegistry) {
                        work.step(); Item candidate=(Item)registered; Block block=Block.getBlockFromItem(candidate);
                        if (block == null) continue;
                        if (material) {
                            try {
                                if (block.getClass().getMethod(getter).getDeclaringClass()!=Block.class)
                                    throw fault("Unadapted CreativeCore block material callback: "+block.getClass().getName());
                            } catch(NoSuchMethodException error) { throw fault("Missing native block material getter"); }
                        }
                        if ((material?block.getMaterial():block)==criterion) patterns.add(new Pattern(new ItemStack(candidate),true,true));
                    }
                    break;
                default: throw fault("Unadapted CreativeCore StackInfo predicate: " + name);
            }
            TreeMap<String, Pattern> unique = new TreeMap<>();
            for (Pattern pattern : patterns) { work.step(); unique.put(CanonicalJson.digest(pattern.record()),pattern); }
            patterns.clear(); patterns.addAll(unique.values());
            JsonArray values = array(); for (Pattern pattern : patterns) values.add(pattern.record());
            // A minimum of zero or one accepts every positive offered stack. Both
            // still lose one item through ordinary workbench consumption.
            signature = CanonicalJson.digest(values);
        }
        boolean overlaps(Group other,Work work) {
            for (Pattern a : patterns) for (Pattern b : other.patterns) { work.step(); if (a.overlaps(b)) return true; }
            return false;
        }
    }
    private static final class Pattern {
        final ItemStack item, returned;
        final boolean meta, nbt;
        Pattern(ItemStack source,boolean meta,boolean nbt) {
            if (source == null || source.getItem() == null || Item.itemRegistry.getNameForObject(source.getItem()) == null)
                throw fault("Invalid CreativeCore ingredient stack");
            if (ItemCallbacks.method(source.getItem(),"getDamage","getDamage",ItemStack.class).getDeclaringClass()!=Item.class)
                throw fault("Unadapted CreativeCore ingredient metadata callback");
            this.meta=meta; this.nbt=nbt; item=source.copy(); item.stackSize=1;
            if (meta) item.setItemDamage(0);
            if (nbt) item.setTagCompound(null);
            returned=ItemCallbacks.container(item,meta);
        }
        boolean overlaps(Pattern other) {
            return item.getItem()==other.item.getItem() && (meta || other.meta || item.getItemDamage()==other.item.getItemDamage())
                    && (nbt || other.nbt || ItemStack.areItemStackTagsEqual(item,other.item));
        }
        JsonObject rule(){return meta || nbt ? object("kind","wildcard","meta",meta,"nbt",nbt) : object("kind","exact");}
        JsonObject record(){return object("item",stack(item),"rule",rule(),"returned",returned==null?null:stack(returned));}
    }
    private static JsonObject stack(ItemStack item) {
        return object("item",Item.itemRegistry.getNameForObject(item.getItem()),"meta",Items.feather.getDamage(item),"amount",item.stackSize,"nbt",TypedNbt.encode(item.getTagCompound()));
    }
    private static String signature(List<Entry> entries) { JsonArray values=array(); for(Entry entry:entries)values.add(value(entry.signature)); return CanonicalJson.digest(values); }
    private static final class Work { int count; void step(){Jobs.checkpoint();if(++count>262144)throw fault("CreativeCore predicate work exceeds its budget");} }
    private static Jobs.Fault fault(String message){return new Jobs.Fault("recipe_unsupported",message);}
}
