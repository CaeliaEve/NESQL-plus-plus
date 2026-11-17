package com.github.dcysteine.nesql.exporter.capture;

import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import cpw.mods.fml.relauncher.ReflectionHelper;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Supplier;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.*;
import net.minecraft.world.World;
import net.minecraftforge.oredict.ShapedOreRecipe;
import net.minecraftforge.oredict.ShapelessOreRecipe;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Ordered native crafting definitions. Logical cells never come from a NEI display cache. */
final class NativeCraftingRecipes implements RegistryRecipes {
    private final List<?> source;
    private final Supplier<? extends List<?>> current;
    private final List<Entry> entries = new ArrayList<>();
    private final List<Object> nativeOrder;
    private final NativeDefinitions definitions;
    private final GtCraftingBindings gtBindings=new GtCraftingBindings();
    private final NativeItemExamples examples=new NativeItemExamples();
    private boolean closed;

    NativeCraftingRecipes(boolean shaped) {
        this(registry(), shaped, NativeCraftingRecipes::registry);
    }
    private static List<?> registry() {
        CraftingManager manager = CraftingManager.getInstance();
        if (manager == null || manager.getRecipeList() == null)
            throw new Jobs.Fault("handler_not_ready", "Native crafting manager/list is unavailable");
        if (manager.getClass() != CraftingManager.class) throw fault("Native crafting manager override requires a dedicated selector");
        return manager.getRecipeList();
    }
    private NativeCraftingRecipes(List<?> source, boolean shaped, Supplier<? extends List<?>> current) {
        if (source == null) throw new Jobs.Fault("handler_not_ready", "Native crafting registry is unavailable");
        if(source.getClass()!=ArrayList.class)throw fault("Unadapted native crafting registry backing");
        if (source.size() > 262144) throw new Jobs.Fault("recipe_limit", "Native crafting registry exceeds its budget");
        this.source = source; this.current = current;
        nativeOrder = new ArrayList<>(source);
        try{
        for (int index = 0; index < source.size(); index++) {
            Jobs.checkpoint(); Object recipe = source.get(index);
            if (recipe == null) throw fault("Null native crafting definition at " + index);
            boolean selected = shaped ? recipe instanceof ShapedRecipes || recipe instanceof ShapedOreRecipe
                    : recipe instanceof ShapelessRecipes || recipe instanceof ShapelessOreRecipe;
            if (selected) entries.add(new Entry(recipe, index));
        }
        definitions = new NativeDefinitions("Native crafting", entries, this::currentVisible,
                raw -> ((Entry)raw).definition());
        }catch(RuntimeException|Error failure){close();throw failure;}
    }
    private List<?> currentVisible() {
        if (current.get() != source || source.size() != nativeOrder.size())
            throw new Jobs.Fault("recipe_changed", "Native crafting registry/count changed");
        return entries;
    }
    public int size() { verify(); return entries.size(); }
    public void verify() {
        if (closed) throw new Jobs.Fault("capture_closed", "Native crafting reader is closed");
        definitions.verify();
    }
    public void finishVerification() {
        verify();
        gtBindings.verify();
        for (int index = 0; index < nativeOrder.size(); index++) {
            Jobs.checkpoint();
            if (source.get(index) != nativeOrder.get(index))
                throw new Jobs.Fault("recipe_changed", "Native crafting full registry order changed at " + index);
        }
        definitions.finishVerification();
        gtBindings.verify();
        examples.verify();
    }
    public void close() {
        if (!closed) { closed = true; entries.clear(); nativeOrder.clear(); if(definitions!=null)definitions.close(); gtBindings.close();examples.close(); }
    }
    public boolean capture(int index, RecipeRow row) {
        verify(); return definitions.checked(index, () -> capture(entries.get(index), row));
    }
    private boolean capture(Entry entry, RecipeRow row) {
        if (WirelessTerminalCrafting.supports(entry.recipe))
            return new WirelessTerminalCrafting((IRecipe)entry.recipe).capture(row);
        Raw raw = entry.read();
        if (raw.empty) return row.exclude(object("native", entry.recipe.getClass().getName(),
                "reason", "empty_native_required_input_predicate", "nativeIndex", entry.index));
        int slot = 0;
        Map<String, ItemStack> specimens = new HashMap<>();
        List<ItemStack> mapCenters = new ArrayList<>();
        if (entry.recipe instanceof RecipesMapExtending) verifyMap(raw);
        for (List<ItemStack> group : raw.inputs) {
            if (group == null) continue;
            List<RecipeRow.Ingredient> choices = new ArrayList<>();
            for (ItemStack template : group) {
                boolean wildcard = template.getItemDamage() == 32767;
                List<ItemStack> expanded = representatives(template,raw);
                if (expanded.isEmpty() || choices.size() + expanded.size() > 65536)
                    throw fault("Native crafting metadata candidates exceed their budget or are unavailable");
                for (ItemStack sample : expanded) {
                    Jobs.checkpoint();
                    ItemStack owned = sample.copy(); owned.stackSize = 1;
                    JsonObject matching = NativeItemMetadata.rule(template,wildcard,true);
                    if (raw.forestry) matching.addProperty("nbt", !template.hasTagCompound() || template.getTagCompound().hasNoTags());
                    choices.add(new RecipeRow.Ingredient(owned, 1, false, matching));
                    if (raw.copyNbt || raw.gt != null) specimens.put(row.facts.item(owned), owned.copy());
                    if (entry.recipe instanceof RecipesMapExtending && slot == 4) mapCenters.add(owned.copy());
                }
            }
            row.nativeCraftingInput(slot++, choices);
        }
        if (raw.shaped) row.record.add("grid", raw.grid());
        if (raw.gt != null) {
            List<List<ItemStack>> groups=new ArrayList<>();
            for(com.google.gson.JsonElement input:row.inputs) {
                List<ItemStack> concrete=new ArrayList<>();
                for(com.google.gson.JsonElement choice:input.getAsJsonObject().getAsJsonArray("choices")) {
                    ItemStack sample=specimens.get(choice.getAsJsonObject().get("id").getAsString());
                    if(sample==null)throw new Jobs.Fault("slot_changed","GT native input specimen was lost");
                    concrete.add(sample.copy());
                }
                groups.add(concrete);
            }
            try(GtCraftingCapturePlan plan=raw.shaped
                    ?GtCraftingCapturePlan.prepare((IRecipe)entry.recipe,groups,placements(raw))
                    :GtCraftingCapturePlan.prepareUnordered((IRecipe)entry.recipe,groups)) {
                GtCraftingTransport.attach(plan,row);gtBindings.retain(plan);
            }
        } else if (entry.recipe instanceof RecipesMapExtending) {
            Products result = Products.observe(mapCenters, object("kind", "mapScaling"),
                    center -> Crafting.pending((RecipesMapExtending)entry.recipe, center), row.facts, 4);
            row.itemOutput(0, result.output, 10000); result.attach(row);
            row.record.add("process", object("kind", "mapScaling"));
        } else if (raw.copyNbt) NativeCraftingNbt.attach((ShapedRecipes)entry.recipe, row, specimens);
        else row.itemOutput(0, raw.output.copy(), 10000);
        if (!raw.shaped) row.property("minecraft:shapeless", row.facts.locale().equals("zh_CN")?"无序合成":"Shapeless crafting", true);
        return true;
    }

    private static int[][] placements(Raw raw) {
        int count=0;for(List<ItemStack> group:raw.inputs)if(group!=null)count++;
        int[][] positions=new int[raw.mirror?2:1][count];int slot=0;
        for(int cell=0;cell<raw.inputs.size();cell++)if(raw.inputs.get(cell)!=null) {
            positions[0][slot]=cell/raw.width*3+cell%raw.width;
            if(raw.mirror)positions[1][slot]=cell/raw.width*3+raw.width-1-cell%raw.width;
            slot++;
        }
        return positions;
    }

    private static void verifyMap(Raw raw) {
        if (raw.width != 3 || raw.height != 3 || raw.inputs.size() != 9 || !raw.mirror)
            throw fault("Native map scaling grid changed");
        for (int cell = 0; cell < 9; cell++) {
            List<ItemStack> group = raw.inputs.get(cell);
            if (group == null || group.size() != 1) throw fault("Native map scaling ingredient changed");
            ItemStack input = group.get(0);
            if (input.getItem() != (cell == 4 ? Items.filled_map : Items.paper)
                    || input.getItemDamage() != (cell == 4 ? 32767 : 0)) throw fault("Native map scaling input changed at " + cell);
        }
    }

    private final class Entry {
        final Object recipe; final int index;
        Entry(Object recipe, int index) { this.recipe = recipe; this.index = index; }
        Raw read() {
            if (source.get(index) != recipe) throw new Jobs.Fault("recipe_changed", "Native crafting source changed at " + index);
            return new Raw((IRecipe)recipe);
        }
        JsonObject definition() {
            if (WirelessTerminalCrafting.supports(recipe)) {
                if (source.get(index) != recipe) throw new Jobs.Fault("recipe_changed", "Wireless native source changed");
                return object("nativeIndex", index, "wireless", new WirelessTerminalCrafting((IRecipe)recipe).definition());
            }
            Raw raw = read(); JsonArray inputs = array();
            for (List<ItemStack> group : raw.inputs) {
                if (group == null) inputs.add(value(null));
                else {
                    JsonArray choices = array();
                    for (ItemStack stack : group) {
                        JsonArray specimens = array();
                        for (ItemStack sample : representatives(stack,raw)) {
                            ItemStack consumed = sample.copy(); consumed.stackSize = 1;
                            if(GtToolConsumption.supports(consumed))specimens.add(object("stack",RecipeRow.stackEvidence(consumed),
                                    "toolContainer",new GtToolConsumption(consumed).declaration()));
                            else if(NativeCraftingContainers.consumption(consumed)!=null)
                                specimens.add(object("stack",RecipeRow.stackEvidence(consumed),"dynamicContainer",NativeCraftingContainers.consumption(consumed)));
                            else {
                                ItemStack returned = ItemCallbacks.container(consumed, false);
                                specimens.add(object("stack", RecipeRow.stackEvidence(consumed), "container", RecipeRow.stackEvidence(returned)));
                            }
                        }
                        choices.add(object("stack", RecipeRow.stackEvidence(stack), "specimens", specimens));
                    }
                    inputs.add(choices);
                }
            }
            return object("nativeIndex", index, "class", recipe.getClass().getName(), "width", raw.width, "height", raw.height,
                    "mirror", raw.mirror, "copyNbt", raw.copyNbt, "forestry", raw.forestry,
                    "gregtech", raw.gt, "inputs", inputs, "output", RecipeRow.stackEvidence(raw.output));
        }
    }
    /** Reader-owned native content permutations, without a recipe display wrapper. */
    private ItemStack[] expand(ItemStack template, boolean templateTags) {
        boolean wildcard = template.getItemDamage() == 32767;
        ItemStack[] samples = templateTags ? examples.copies(template) : examples.permutations(template);
        if (samples == null || samples.length == 0 || samples.length > 65536)
            throw fault("Native crafting metadata candidates are unavailable or exceed their budget");
        for (ItemStack sample : samples) {
            Jobs.checkpoint();
            if (sample == null || sample.getItem() != template.getItem() || sample.getItemDamage() == 32767
                    || !wildcard && sample.getItemDamage() != template.getItemDamage())
                throw new Jobs.Fault("slot_changed", "Native crafting metadata expansion substituted a native input");
        }
        return samples;
    }
    private List<ItemStack> representatives(ItemStack template,Raw raw) {
        List<ItemStack> samples=new ArrayList<>();
        for(ItemStack sample:expand(template,raw.forestry)) {
            samples.add(sample);
            // The pinned ingredient matcher ignores declared NBT. Keeping-NBT
            // constrains the offered inputs relative to one another, not to the
            // templates. Untagged witnesses therefore remain natively admitted.
            if(raw.gt!=null&&raw.gt.get("keepingNbt").getAsBoolean()&&sample.hasTagCompound()) {
                ItemStack neutral=sample.copy();neutral.setTagCompound(null);samples.add(neutral);
            }
            if(samples.size()>65536)throw new Jobs.Fault("recipe_limit","Native GT representatives exceed budget");
        }
        return samples;
    }
    static final class Raw {
        final List<List<ItemStack>> inputs = new ArrayList<>();
        final int width, height; final boolean shaped, mirror, copyNbt, empty, forestry;
        final ItemStack output;
        final JsonObject gt;
        Raw(IRecipe recipe) {
            Class<?> parent = recipe instanceof ShapedRecipes ? ShapedRecipes.class
                    : recipe instanceof ShapedOreRecipe ? ShapedOreRecipe.class
                    : recipe instanceof ShapelessRecipes ? ShapelessRecipes.class
                    : recipe instanceof ShapelessOreRecipe ? ShapelessOreRecipe.class : null;
            if (parent == null) throw fault("Unadapted native crafting family: " + recipe.getClass().getName());
            forestry = recipe.getClass().getName().equals("forestry.core.recipes.ShapedRecipeCustom");
            if (forestry) MagicApi.version("NativeCraftingRecipes","Forestry");
            gt = GtCraftingRules.supports(recipe) ? GtCraftingRules.definition(recipe) : null;
            Class<?> resultOwner = gt != null || recipe.getClass() == RecipesMapExtending.class ? recipe.getClass() : parent;
            requireMethod(recipe, "getCraftingResult", "func_77572_b", resultOwner, InventoryCrafting.class);
            requireMethod(recipe, "getRecipeOutput", "func_77571_b", parent);
            requireMethod(recipe, "matches", "func_77569_a", forestry ? recipe.getClass() : resultOwner, InventoryCrafting.class, World.class);
            requireMethod(recipe, "getRecipeSize", "func_77570_a", parent);
            boolean ore = parent == ShapedOreRecipe.class || parent == ShapelessOreRecipe.class;
            if (ore) requireMethod(recipe, "getInput", "getInput", parent);
            shaped = parent == ShapedRecipes.class || parent == ShapedOreRecipe.class;
            width = parent == ShapedRecipes.class ? ((ShapedRecipes)recipe).recipeWidth : shaped ? (Integer)field(recipe, "width") : 0;
            height = parent == ShapedRecipes.class ? ((ShapedRecipes)recipe).recipeHeight : shaped ? (Integer)field(recipe, "height") : 0;
            mirror = shaped && (!ore || (Boolean)field(recipe, "mirrored"));
            copyNbt = parent == ShapedRecipes.class && (Boolean)ReflectionHelper.getPrivateValue(ShapedRecipes.class, (ShapedRecipes)recipe, "field_92101_f");
            Object input = parent == ShapedRecipes.class ? ((ShapedRecipes)recipe).recipeItems
                    : parent == ShapelessRecipes.class ? ((ShapelessRecipes)recipe).recipeItems
                    : forestry ? field(recipe.getClass(), recipe, "input")
                    : parent == ShapedOreRecipe.class ? ((ShapedOreRecipe)recipe).getInput() : ((ShapelessOreRecipe)recipe).getInput();
            Object[] cells = input instanceof List<?> ? nativeList(input).toArray() : input instanceof Object[] ? (Object[])input : null;
            if (cells == null || cells.length == 0 || cells.length > 9 || shaped && (width < 1 || width > 3 || height < 1 || height > 3 || cells.length != width * height))
                throw fault("Invalid native crafting logical grid");
            int count = 0, occupied = 0; boolean missing = false;
            for (Object cell : cells) {
                if (cell == null) { if (!shaped) throw fault("Null native shapeless ingredient"); inputs.add(null); continue; }
                occupied++;
                List<?> group = ore && cell instanceof ArrayList<?> ? nativeList(cell) : Collections.singletonList(cell);
                List<ItemStack> stacks = new ArrayList<>(); missing |= group.isEmpty();
                for (Object choice : group) {
                    if (++count > 65536 || !(choice instanceof ItemStack))
                        throw fault("Invalid native crafting ingredient");
                    ItemStack stack = (ItemStack)choice;
                    NativeStackAudit.auditLogical(stack);
                    stacks.add(stack.copy());
                }
                inputs.add(stacks);
            }
            if (occupied == 0) throw fault("Native crafting definition has no occupied cells");
            empty = missing;
            ItemStack result = recipe.getRecipeOutput();
            if(result!=null)NativeStackAudit.auditRaw(result);
            if (result == null || result.getItem() == null || result.stackSize <= 0 && recipe.getClass() != RecipesMapExtending.class)
                throw fault("Native crafting has no positive fixed result");
            output = result.copy();
        }
        JsonObject grid() {
            JsonArray cells = array(); int slot = 0;
            for (List<ItemStack> group : inputs) cells.add(value(group == null ? null : slot++));
            return object("width", width, "height", height, "cells", cells, "mirror", mirror);
        }
    }
    private static List<?> nativeList(Object source){
        Set<Object> seen=Collections.newSetFromMap(new IdentityHashMap<Object,Boolean>());
        for(int depth=0;depth<=8;depth++){
            Jobs.checkpoint();if(source==null||!seen.add(source))throw fault("Invalid native crafting list chain");Class<?> owner=source.getClass();
            if(owner==ArrayList.class||owner==LinkedList.class||owner==type("java.util.Arrays$ArrayList")
                ||owner==type("java.util.Collections$EmptyList")||owner==type("java.util.Collections$SingletonList")){
                if(((List<?>)source).size()>65536)throw fault("Native crafting list exceeds budget");return (List<?>)source;
            }
            if(owner==type("net.minecraftforge.oredict.OreDictionary$UnmodifiableArrayList")
                ||owner==type("java.util.Collections$UnmodifiableList")||owner==type("java.util.Collections$UnmodifiableRandomAccessList")){
                source=field(source,"list");continue;
            }
            throw fault("Unadapted native crafting list owner: "+owner.getName());
        }
        throw fault("Native crafting list wrappers exceed budget");
    }
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void requireMethod(IRecipe recipe, String mcp, String srg, Class<?> expected, Class<?>... arguments) {
        Method nativeMethod = ReflectionHelper.findMethod((Class)expected, null, new String[]{mcp, srg}, arguments);
        try {
            if (recipe.getClass().getMethod(nativeMethod.getName(), arguments).getDeclaringClass() != expected)
                throw fault("Unadapted native crafting callback " + mcp + ": " + recipe.getClass().getName());
        } catch (NoSuchMethodException failure) { throw fault("Missing native crafting method " + mcp); }
    }
    private static Jobs.Fault fault(String message) { return new Jobs.Fault("recipe_unsupported", message); }
}
