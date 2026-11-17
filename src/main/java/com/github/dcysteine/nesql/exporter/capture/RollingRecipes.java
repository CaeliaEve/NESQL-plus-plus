package com.github.dcysteine.nesql.exporter.capture;


import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.IRecipeHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import cpw.mods.fml.relauncher.ReflectionHelper;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.*;
import net.minecraftforge.oredict.ShapedOreRecipe;
import net.minecraftforge.oredict.ShapelessOreRecipe;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Railcraft 9.16.33: one ordered registry shared by both native NEI categories. */
final class RollingRecipes implements RegistryRecipes {
    private static final String NEI = "tonius.neiintegration.mods.railcraft.RecipeHandlerRollingMachine";
    private final String handlerName;
    private final List<Entry> all = new ArrayList<>(), visible = new ArrayList<>();
    private final boolean powered;
    private final List<?> registry;
    private final Object manager;
    private final NativeDefinitions definitions;
    private boolean closed;

    static boolean supports(ICraftingHandler handler) {
        String name = handler.getClass().getName();
        return name.equals(NEI + "Shaped") || name.equals(NEI + "Shapeless");
    }
    RollingRecipes(TemplateRecipeHandler handler) { this(handler, registrationSource(handler)); }
    private RollingRecipes(TemplateRecipeHandler handler,Source source){this(handler,source.recipes,power(),source.manager);}
    static Source registrationSource(IRecipeHandler handler) {
        MagicApi.version("RollingRecipes","Railcraft"); MagicApi.version("RollingRecipes","neiintegration");
        if(!(handler instanceof ICraftingHandler)||!supports((ICraftingHandler)handler))throw fault("Unadapted rolling handler");
        Object manager = field(type("mods.railcraft.api.crafting.RailcraftCraftingManager"), null, "rollingMachine");
        if(manager==null)throw new Jobs.Fault("handler_not_ready","Rolling manager is unavailable");
        if (!manager.getClass().getName().equals("mods.railcraft.common.util.crafting.RollingMachineCraftingManager"))
            throw fault("Unadapted rolling registry");
        Object recipes=field(manager,"recipes");if(recipes==null)throw new Jobs.Fault("handler_not_ready","Rolling recipes are unavailable");
        if(recipes.getClass()!=ArrayList.class||invoke(manager.getClass(),manager,"getRecipeList",new Class<?>[0])!=recipes)throw fault("Unadapted rolling source");
        return new Source(manager,(List<?>)recipes);
    }
    static final class Source{final Object manager;final List<?> recipes;Source(Object manager,List<?> recipes){this.manager=manager;this.recipes=recipes;}}
    private static boolean power() {
        return (Boolean)field(type("mods.railcraft.common.core.RailcraftConfig"),null,"machinesRequirePower");
    }
    RollingRecipes(TemplateRecipeHandler handler, List<?> registry, boolean powered) {
        this(handler,registry,powered,null);
    }
    private RollingRecipes(TemplateRecipeHandler handler,List<?> registry,boolean powered,Object manager){
        if(registry==null)throw new Jobs.Fault("handler_not_ready","Rolling registry is unavailable");
        if (!supports(handler) || registry.size() > 262144) throw fault("Invalid rolling handler or registry budget");
        this.handlerName=handler.getClass().getName();this.powered=powered;this.registry=registry;this.manager=manager;
        boolean shaped = handler.getClass().getName().equals(NEI + "Shaped");
        for (Object recipe : registry) {
            Jobs.checkpoint();
            Entry entry = new Entry(recipe,all.size());
            all.add(entry);
            if (entry.shaped == shaped) visible.add(entry);
        }
        definitions=new NativeDefinitions("Rolling machine",all,this::current,entry->definition((Entry)entry));
    }
    private List<?> current(){
        if(registry.size()!=all.size())throw changed();
        if(manager!=null&&(field(type("mods.railcraft.api.crafting.RailcraftCraftingManager"),null,"rollingMachine")!=manager||field(manager,"recipes")!=registry||power()!=powered))throw changed();
        return all;
    }
    public int size() {verify();return visible.size();}
    public void verify(){if(closed)throw new Jobs.Fault("capture_closed","Rolling reader is closed");definitions.verify();}
    public void finishVerification(){verify();definitions.finishVerification();}
    public void close(){if(closed)return;closed=true;definitions.close();all.clear();visible.clear();}
    public boolean capture(int index, RecipeRow row) {
        verify();return definitions.checked(visible.get(index).nativeIndex,()->captureRow(index,row));
    }
    private boolean captureRow(int index,RecipeRow row){
        Entry entry = visible.get(index);
        if (entry.impossible) return row.exclude(object("native", handlerName, "reason", "empty_native_required_input_predicate",
                "nativeIndex", index, "width", entry.width, "height", entry.height));
        JsonArray earlier = new JsonArray();
        int priorChoices = 0;
        for (Entry prior : all) {
            if (prior == entry) break;
            definitions.record(prior.nativeIndex);
            Jobs.checkpoint();
            if (!prior.impossible) {
                for (List<ItemStack> choices : prior.raw) if (choices != null) priorChoices += choices.size();
                if (priorChoices > 1048576) throw fault("Rolling selector budget exceeded");
                earlier.add(prior.selector(row.facts));
            }
        }
        row.record.add("process", object("kind", "rolling", "powered", powered, "earlier", earlier));
        if(entry.copyNbt)row.record.getAsJsonObject("process").addProperty("copyNbt",true);
        if (entry.shaped) row.record.add("grid", entry.grid());
        List<List<RecipeRow.Ingredient>> ingredients = new ArrayList<>();
        Map<String,ItemStack> specimens=new HashMap<>();
        for (int cell = 0; cell < entry.raw.length; cell++) {
            List<ItemStack> templates = entry.raw[cell];
            if (templates == null) continue;
            List<RecipeRow.Ingredient> choices = new ArrayList<>();
            for (ItemStack template : templates) {
                boolean wildcard = template.getItemDamage() == 32767;
                ItemStack[] expanded = wildcard ? new NativeItemVariants(template).copies() : new ItemStack[] {template.copy()};
                if (expanded.length == 0 || choices.size() + expanded.length > 65536) throw fault("Invalid rolling display expansion");
                for (ItemStack sample : expanded) {
                    if (sample == null || sample.getItem() != template.getItem()) throw new Jobs.Fault("slot_changed", "Rolling display substituted native input");
                    ItemStack owned = sample.copy(); owned.stackSize = 1;
                    choices.add(new RecipeRow.Ingredient(owned, 1, false, rule(wildcard)));
                    if(entry.copyNbt)specimens.put(row.facts.item(owned),owned.copy());
                }
            }
            ingredients.add(choices);
        }
        // Logical slots preserve native grid holes; machine consumption never calls containers.
        for(int slot=0;slot<ingredients.size();slot++)row.itemInput(slot,ingredients.get(slot),false);
        if(entry.copyNbt)NativeCraftingNbt.attachResult((ShapedRecipes)entry.nativeRecipe,row,specimens);
        else row.itemOutput(0, entry.output.copy(), 10000);
        return true;
    }
    private JsonObject definition(Entry original){
        if(registry.get(original.nativeIndex)!=original.nativeRecipe)throw changed();
        Entry actual=new Entry(original.nativeRecipe,original.nativeIndex);
        if(original.anchors.size()!=actual.anchors.size())throw changed();
        for(int i=0;i<original.anchors.size();i++)if(original.anchors.get(i)!=actual.anchors.get(i))throw changed();
        return actual.nativeData;
    }
    private static JsonObject rule(boolean wildcard) { return object("kind", "wildcard", "meta", wildcard, "nbt", true); }

    private static final class Entry {
        final boolean shaped, mirror, copyNbt, impossible;
        final int width, height;
        final List<ItemStack>[] raw;
        final ItemStack output;
        final Object nativeRecipe;final int nativeIndex;
        final List<Object> anchors=new ArrayList<>();final JsonObject nativeData;
        @SuppressWarnings("unchecked") Entry(Object value,int nativeIndex) {
            this.nativeRecipe=value;this.nativeIndex=nativeIndex;
            if (value == null) throw fault("Null entry in rolling registry");
            Class<?> c = value.getClass();
            if (c != ShapedRecipes.class && c != ShapedOreRecipe.class && c != ShapelessRecipes.class && c != ShapelessOreRecipe.class)
                throw fault("Unadapted rolling selector: " + c.getName());
            shaped = c == ShapedRecipes.class || c == ShapedOreRecipe.class;
            boolean ore = c == ShapedOreRecipe.class || c == ShapelessOreRecipe.class;
            width = c == ShapedRecipes.class ? ((ShapedRecipes) value).recipeWidth : shaped ? (Integer) field(value, "width") : 0;
            height = c == ShapedRecipes.class ? ((ShapedRecipes) value).recipeHeight : shaped ? (Integer) field(value, "height") : 0;
            mirror = shaped && (!ore || (Boolean) field(value, "mirrored"));
            copyNbt = c == ShapedRecipes.class && (Boolean) ReflectionHelper.getPrivateValue(ShapedRecipes.class, (ShapedRecipes) value, "field_92101_f");
            Object input = c == ShapedOreRecipe.class ? ((ShapedOreRecipe) value).getInput()
                    : c == ShapelessOreRecipe.class ? ((ShapelessOreRecipe) value).getInput()
                    : c == ShapedRecipes.class ? ((ShapedRecipes) value).recipeItems : ((ShapelessRecipes) value).recipeItems;
            if(input==null)throw fault("Unready rolling input definition");
            if(input instanceof List<?> && input.getClass()!=ArrayList.class&&input.getClass()!=Arrays.asList().getClass()
                    &&input.getClass()!=Collections.singletonList(null).getClass()&&input.getClass()!=Collections.emptyList().getClass())throw fault("Unadapted rolling input list");
            anchors.add(input);
            Object[] cells = input instanceof List<?> ? ((List<?>) input).toArray() : ((Object[]) input).clone();
            if (cells.length == 0 || cells.length > 9 || shaped && (width < 1 || width > 3 || height < 1 || height > 3 || cells.length != width * height))
                throw fault("Invalid rolling grid");
            raw = (List<ItemStack>[]) new List<?>[cells.length];
            int count = 0, occupied = 0; boolean empty = false;
            JsonArray rawCells=array();
            for (int i = 0; i < cells.length; i++) {
                if (cells[i] == null) { if (!shaped) throw fault("Null shapeless rolling ingredient");rawCells.add(com.google.gson.JsonNull.INSTANCE);continue; }
                anchors.add(cells[i]);
                if(cells[i] instanceof ArrayList<?>)anchors.add(NativeOreStorage.arrayList(cells[i]));
                occupied++;
                List<?> choices = cells[i] instanceof ArrayList<?> && ore ? NativeOreStorage.arrayList(cells[i]) : Collections.singletonList(cells[i]);
                raw[i] = new ArrayList<>(); empty |= choices.isEmpty();
                JsonArray rawChoices=array();rawCells.add(rawChoices);
                for (Object choice : choices) {
                    if (++count > 65536 || !(choice instanceof ItemStack) || ((ItemStack) choice).getItem() == null) throw fault("Invalid rolling ingredient");
                    ItemStack stack = (ItemStack) choice;
                    if(stack.getClass()!=ItemStack.class)throw fault("Unadapted rolling stack callbacks");
                    anchors.add(stack);anchors.add(stack.getItem());
                    try {
                        if (stack.getItem().getClass().getMethod("getDamage", ItemStack.class).getDeclaringClass() != Item.class)
                            throw fault("Rolling input uses a custom metadata getter");
                    } catch (NoSuchMethodException ex) { throw fault("Missing native metadata getter"); }
                    rawChoices.add(RecipeRow.stackEvidence(stack));
                    ItemStack owned = stack.copy(); owned.stackSize = 1; raw[i].add(owned);
                }
            }
            if (occupied == 0) throw fault("Rolling recipe has no occupied input cells");
            impossible = empty;
            ItemStack result = ((IRecipe) value).getRecipeOutput();
            if (result == null || result.getItem() == null || result.stackSize <= 0) throw fault("Rolling recipe has no positive fixed result");
            if(result.getClass()!=ItemStack.class)throw fault("Unadapted rolling result stack");
            anchors.add(result);anchors.add(result.getItem());
            output = result.copy();
            nativeData=object("class",c.getName(),"width",width,"height",height,"mirror",mirror,"copyNbt",copyNbt,"cells",rawCells,"output",RecipeRow.stackEvidence(result));
        }
        JsonObject grid() {
            JsonArray cells = new JsonArray(); int slot = 0;
            for (List<ItemStack> group : raw) cells.add(value(group == null ? null : slot++));
            return object("width", width, "height", height, "cells", cells, "mirror", mirror);
        }
        JsonObject selector(Facts facts) {
            JsonArray inputs = new JsonArray();
            for (List<ItemStack> group : raw) if (group != null) {
                JsonArray choices = new JsonArray(); Set<String> seen = new HashSet<>();
                for (ItemStack stack : group) {
                    JsonObject choice = object("id", facts.item(stack), "rule", rule(stack.getItemDamage() == 32767));
                    if (seen.add(choice.toString())) choices.add(choice);
                }
                inputs.add(choices);
            }
            return object("grid", shaped ? grid() : null, "inputs", inputs);
        }
    }
    private static Jobs.Fault fault(String message) { return new Jobs.Fault("recipe_unsupported", message); }
    private static Jobs.Fault changed(){return new Jobs.Fault("recipe_changed","Native rolling source, power, order or recipe facts changed");}
}
