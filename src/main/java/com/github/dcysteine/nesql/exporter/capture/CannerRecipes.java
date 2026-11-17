package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.recipe.IRecipeHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import com.google.gson.JsonObject;
import java.util.*;
import java.util.function.Supplier;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** IC2 2.2.828 bottle/enrichment registries; native display cells are never recipe substances. */
final class CannerRecipes implements RegistryRecipes {
    private static final String HANDLERS = "ic2.neiIntegration.core.recipehandler.", API = "ic2.api.recipe.";
    private final boolean fluid;
    private final Object manager;
    private final Supplier<Object> current;
    private final Map<?,?> recipes;
    private final List<Entry> entries=new ArrayList<>();
    private final NativeDefinitions definitions;
    private boolean closed;

    static boolean supports(IRecipeHandler handler) {
        String name = handler.getClass().getName();
        return name.equals(HANDLERS + "SolidCannerRecipeHandler") || name.equals(HANDLERS + "FluidCannerRecipeHandler");
    }
    private static boolean fluid(IRecipeHandler handler) { return handler.getClass().getName().equals(HANDLERS + "FluidCannerRecipeHandler"); }
    static Object registrationSource(IRecipeHandler handler) {
        MagicApi.version("CannerRecipes","IC2");
        if(!supports(handler))throw fault("Unadapted IC2 canner handler");
        Object manager=field(type(API + "Recipes"), null, fluid(handler) ? "cannerEnrich" : "cannerBottle");
        if(manager==null)throw new Jobs.Fault("handler_not_ready","IC2 canner manager is unavailable");
        return manager;
    }
    CannerRecipes(TemplateRecipeHandler handler) { this(handler,registrationSource(handler),
            ()->field(type(API+"Recipes"),null,fluid(handler)?"cannerEnrich":"cannerBottle")); }
    CannerRecipes(TemplateRecipeHandler handler, Object manager) {
        this(handler,manager,()->manager);
    }
    private CannerRecipes(TemplateRecipeHandler handler,Object manager,Supplier<Object> current) {
        this.fluid=fluid(handler);this.manager=manager;this.current=current;
        if(manager==null)throw new Jobs.Fault("handler_not_ready","IC2 canner manager is unavailable");
        if (!supports(handler) || !manager.getClass().getName().equals("ic2.core.block.machine.Canner" + (fluid ? "Enrich" : "Bottle") + "RecipeManager")) {
            throw fault("Unadapted IC2 canner manager");
        }
        recipes=registry();
        if (recipes.size() > 262144) throw new Jobs.Fault("recipe_limit","IC2 canner registry exceeds its budget");
        for (Map.Entry<?, ?> source : recipes.entrySet()) { Jobs.checkpoint();entries.add(new Entry(source)); }
        definitions=new NativeDefinitions("IC2 canner",entries,this::currentVisible,entry->((Entry)entry).definition());
    }
    public int size() { verify();return entries.size(); }
    public void verify() {
        if(closed)throw new Jobs.Fault("capture_closed","IC2 canner reader is closed");
        definitions.verify();
    }
    private Map<?,?> registry() {
        Object value=invoke(manager.getClass(),manager,"getRecipes",new Class<?>[0]);
        if(value==null)throw new Jobs.Fault("handler_not_ready","IC2 canner recipe map is unavailable");
        if(!(value instanceof Map<?,?>))throw fault("Malformed IC2 canner registry");
        return (Map<?,?>)value;
    }
    private List<?> currentVisible() {
        if(current.get()!=manager||registry()!=recipes||recipes.size()!=entries.size())throw changed("manager, recipe map or count changed");
        return entries;
    }
    public void finishVerification() {
        verify();int index=0;
        for(Map.Entry<?,?> source:recipes.entrySet()) {
            Jobs.checkpoint();Entry entry=entries.get(index++);
            if(source.getKey()!=entry.key||source.getValue()!=entry.value)throw changed("native first-match iteration order changed");
        }
        definitions.finishVerification();
    }
    public void close(){if(closed)return;closed=true;entries.clear();definitions.close();}
    public boolean capture(int index, RecipeRow row) {
        verify();return definitions.checked(index,()->captureRow(index,row));
    }
    private boolean captureRow(int index,RecipeRow row) {
        Value entry = entry(index);
        // Native HashMap iteration picks the first matching predicate before checking counts.
        // addRecipe normally rejects collisions, but getRecipes is mutable and ore lists can grow.
        // Do not flatten a colliding selector into independent unconditional recipe choices.
        long[] budget = {1048576};
        for (int other = 0; other < size(); other++) {
            Jobs.checkpoint();
            if (other == index) continue;
            Value sibling = entry(other);
            if (fluid ? entry.input.isFluidEqual(sibling.input) && overlaps(entry.fill, sibling.fill, budget)
                    : overlaps(entry.container, sibling.container, budget) && overlaps(entry.fill, sibling.fill, budget)) {
                throw fault("Overlapping native canner selectors at " + index + " and " + other);
            }
        }
        if (fluid) {
            row.fluidInput(0, entry.input, false);
            row.itemInput(0, entry.fill, false);
            row.fluidOutput(0, entry.output);
            row.property("ic2:cannerMode", "Mode", "EnrichLiquid");
            row.property("ic2:cannerTanks", "Input/output tank capacity (mB each)", 8000);
            row.property("ic2:cannerPackaging", "Enriched fluid enters the output tank; installed containers may then be filled separately", true);
        } else {
            row.itemInput(0, entry.container, false);
            row.itemInput(1, entry.fill, false);
            for (int i = 0; i < entry.products.size(); i++) row.itemOutput(i, entry.products.get(i), 10000);
        }
        row.record.addProperty("duration", "200");
        row.record.addProperty("energy", fluid ? "4" : "2");
        return true;
    }
    private Value entry(int index) {
        return ((Entry)definitions.record(index)).owned;
    }
    private Value read(Object key,Object result) {
        if (key == null || !key.getClass().getName().equals(API + "ICanner" + (fluid ? "Enrich" : "Bottle") + "RecipeManager$Input")) throw fault("Unknown canner selector");
        Value entry = new Value();
        entry.fill = Ic2Recipes.ingredients(field(key, fluid ? "additive" : "fill"));
        if (fluid) {
            entry.input = fluid(field(key, "fluid")); entry.output = fluid(result);
        } else {
            entry.container = Ic2Recipes.ingredients(field(key, "container"));
            Object output = result;
            if (output == null || !output.getClass().getName().equals(API + "RecipeOutput")) throw fault("Unknown canner output");
            List<?> products = (List<?>) field(output, "items");
            if (products.isEmpty() || products.size() > 4096) throw fault("Invalid canner output count");
            entry.products = new ArrayList<>();
            for (Object value : products) {
                if (!(value instanceof ItemStack) || ((ItemStack)value).getItem() == null || ((ItemStack)value).stackSize <= 0) throw fault("Invalid canner item output");
                entry.products.add(((ItemStack)value).copy());
            }
        }
        return entry;
    }
    private static FluidStack fluid(Object value) {
        if (!(value instanceof FluidStack) || ((FluidStack)value).getFluid() == null || ((FluidStack)value).amount <= 0) throw fault("Invalid canner fluid");
        return ((FluidStack)value).copy();
    }
    private static boolean overlaps(List<RecipeRow.Ingredient> left, List<RecipeRow.Ingredient> right, long[] budget) {
        for (RecipeRow.Ingredient a : left) for (RecipeRow.Ingredient b : right) {
            Jobs.checkpoint();
            if (--budget[0] < 0) throw fault("Canner selector overlap check exceeds its budget");
            if (a.item.getItem() == b.item.getItem() && (a.rule.get("meta").getAsBoolean() || b.rule.get("meta").getAsBoolean()
                    || a.item.getItemDamage() == b.item.getItemDamage())) return true;
        }
        return false;
    }
    private final class Entry {
        final Object key,value,fill,container;final Value owned;
        Entry(Map.Entry<?,?> source) {
            key=source.getKey();value=source.getValue();owned=read(key,value);
            fill=field(key,fluid?"additive":"fill");container=fluid?null:field(key,"container");
        }
        JsonObject definition() {
            if(recipes.get(key)!=value||field(key,fluid?"additive":"fill")!=fill||!fluid&&field(key,"container")!=container)
                throw changed("selector or result instance changed");
            Value now=read(key,value);
            com.google.gson.JsonArray products=array();if(now.products!=null)for(ItemStack stack:now.products)products.add(RecipeRow.stackEvidence(stack));
            return object("fill",ingredients(now.fill),"container",now.container==null?null:ingredients(now.container),
                    "input",fluidFacts(now.input),"output",fluidFacts(now.output),"products",products,
                    "metadata",fluid?null:com.github.dcysteine.nesql.exporter.source.TypedNbt.encode((net.minecraft.nbt.NBTTagCompound)field(value,"metadata")));
        }
    }
    private static com.google.gson.JsonArray ingredients(List<RecipeRow.Ingredient> input) {
        com.google.gson.JsonArray result=array();for(RecipeRow.Ingredient ingredient:input)
            result.add(object("stack",RecipeRow.stackEvidence(ingredient.item),"amount",Long.toString(ingredient.amount),"rule",ingredient.rule));
        return result;
    }
    private static JsonObject fluidFacts(FluidStack stack) {
        return stack==null?null:object("registry",stack.getFluid().getName(),"amount",stack.amount,
                "nbt",com.github.dcysteine.nesql.exporter.source.TypedNbt.encode(stack.tag));
    }
    private static final class Value {
        List<RecipeRow.Ingredient> container, fill;
        List<ItemStack> products;
        FluidStack input, output;
    }
    private static Jobs.Fault changed(String detail){return new Jobs.Fault("recipe_changed","IC2 canner "+detail);}
    private static Jobs.Fault fault(String message) { return new Jobs.Fault("recipe_unsupported", message); }
}
