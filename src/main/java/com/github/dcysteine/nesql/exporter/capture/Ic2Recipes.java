package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.IRecipeHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.oredict.OreDictionary;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import java.util.*;
import java.util.function.Supplier;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** IC2 2.2.828 semantic machine rules, independent of NEI cached layouts. */
final class Ic2Recipes implements RegistryRecipes {
    private static final String HANDLERS = "ic2.neiIntegration.core.recipehandler.";
    private static final String API = "ic2.api.recipe.";
    private enum Machine {
        MACERATOR("MaceratorRecipeHandler", "macerator", 300, 2), EXTRACTOR("ExtractorRecipeHandler", "extractor", 300, 2),
        COMPRESSOR("CompressorRecipeHandler", "compressor", 300, 2), CUTTING("MetalFormerRecipeHandlerCutting", "metalformerCutting", 200, 10),
        ROLLING("MetalFormerRecipeHandlerRolling", "metalformerRolling", 200, 10), EXTRUDING("MetalFormerRecipeHandlerExtruding", "metalformerExtruding", 200, 10),
        CENTRIFUGE("CentrifugeRecipeHandler", "centrifuge", 500, 48), WASHING("OreWashingRecipeHandler", "oreWashing", 500, 16),
        BLOCK_CUTTER("BlockCutterRecipeHandler", "blockcutter", 900, 48);
        final String handler, registry;
        final int ticks, energy;
        Machine(String handler,String registry,int ticks,int energy) { this.handler=HANDLERS+handler;this.registry=registry;this.ticks=ticks;this.energy=energy; }
    }
    private static Machine machine(IRecipeHandler handler) {
        for (Machine value : Machine.values()) if (value.handler.equals(handler.getClass().getName())) return value;
        return null;
    }
    static boolean supports(IRecipeHandler handler) { return machine(handler) != null; }
    private final TemplateRecipeHandler handler;
    private final Map<?,?> source;
    private final Supplier<Map<?,?>> current;
    private final List<Map.Entry<?, ?>> recipes = new ArrayList<>();
    private final NativeDefinitions definitions;
    private boolean closed;

    Ic2Recipes(TemplateRecipeHandler handler) {
        this(handler,registrationSource(handler));
    }
    private Ic2Recipes(TemplateRecipeHandler handler,NativeSource selected) {
        this(handler,selected.recipes,()->{
            if(field(type(API+"Recipes"),null,machine(handler).registry)!=selected.manager
                    ||field(selected.manager,"recipes")!=selected.recipes)throw changed("native manager or storage instance changed");
            NativeSource actual=nativeSource(handler);
            if(actual.manager!=selected.manager)throw changed("native manager instance changed");
            return actual.recipes;
        });
    }
    static final class NativeSource {
        final Object manager;final Map<?,?> recipes;
        NativeSource(Object manager,Map<?,?> recipes){this.manager=manager;this.recipes=recipes;}
    }
    static NativeSource registrationSource(IRecipeHandler handler) {
        MagicApi.version("Ic2Recipes","IC2");
        return nativeSource(handler);
    }
    private static NativeSource nativeSource(IRecipeHandler handler) {
        Machine mode=machine(handler);if(mode==null)throw fault("Unadapted IC2 machine handler");
        Object manager=field(type(API+"Recipes"),null,mode.registry);
        if(manager==null)throw new Jobs.Fault("handler_not_ready","IC2 native machine manager is unavailable");
        if(!manager.getClass().getName().equals("ic2.core.BasicMachineRecipeManager"))throw fault("Unadapted IC2 machine manager: "+manager.getClass().getName());
        Object backing=field(manager,"recipes");
        if(backing==null)throw new Jobs.Fault("handler_not_ready","IC2 native machine storage is unavailable");
        if(backing.getClass()!=HashMap.class)throw fault("Unadapted IC2 machine storage map");
        // getRecipes returns a fresh anonymous view on each call. Bind its exact
        // native owner and underlying storage, never the ephemeral view instance.
        Object exposed=invoke(handler.getClass(),handler,"getRecipeList",new Class<?>[0]);
        if(exposed==null||!exposed.getClass().getName().equals("ic2.core.BasicMachineRecipeManager$1")
                ||field(exposed,"this$0")!=manager)throw fault("IC2 handler exposes a different native source");
        return new NativeSource(manager,(Map<?,?>)backing);
    }
    Ic2Recipes(TemplateRecipeHandler handler,Map<?,?> source) {this(handler,source,()->source);}
    private Ic2Recipes(TemplateRecipeHandler handler,Map<?,?> source,Supplier<Map<?,?>> current) {
        if(!supports(handler))throw fault("Unadapted IC2 machine handler");
        if(source==null)throw new Jobs.Fault("handler_not_ready","IC2 machine recipe map is unavailable");
        if(source.size()>262144)throw new Jobs.Fault("recipe_limit","IC2 native recipe map exceeds budget");
        this.handler = handler;
        this.source=source;this.current=current;
        for (Map.Entry<?, ?> recipe : source.entrySet()) {
            Jobs.checkpoint();
            recipes.add(new AbstractMap.SimpleImmutableEntry<>(recipe));
        }
        definitions=new NativeDefinitions("IC2 machine",recipes,this::currentVisible,raw->definition((Map.Entry<?,?>)raw));
    }
    public int size() {verify();return recipes.size(); }
    public void verify() {
        if(closed)throw new Jobs.Fault("capture_closed","IC2 machine reader is closed");
        definitions.verify();
    }
    private List<?> currentVisible() {
        if(current.get()!=source||source.size()!=recipes.size())throw changed("native map or count changed");
        return recipes;
    }
    public void finishVerification() {
        verify();int index=0;
        for(Map.Entry<?,?> record:source.entrySet()) {
            Jobs.checkpoint();Map.Entry<?,?> original=recipes.get(index++);
            if(record.getKey()!=original.getKey()||record.getValue()!=original.getValue())throw changed("native order or entry instance changed");
        }
        definitions.finishVerification();
    }
    public void close(){if(closed)return;closed=true;recipes.clear();definitions.close();}
    public boolean capture(int index, RecipeRow row) {
        verify();return definitions.checked(index,()->{
            Map.Entry<?,?> recipe=recipes.get(index);capture(handler,recipe.getKey(),recipe.getValue(),row);return true;
        });
    }
    private com.google.gson.JsonObject definition(Map.Entry<?,?> recipe) {
        if(source.get(recipe.getKey())!=recipe.getValue())throw changed("native output instance changed");
        com.google.gson.JsonArray inputs=array(),outputs=array();
        for(RecipeRow.Ingredient ingredient:ingredients(recipe.getKey()))inputs.add(object("stack",RecipeRow.stackEvidence(ingredient.item),
                "amount",Long.toString(ingredient.amount),"rule",ingredient.rule));
        Object output=recipe.getValue();
        if(output==null||!output.getClass().getName().equals(API+"RecipeOutput"))throw fault("Unknown IC2 output type");
        Object products=field(output,"items");
        if(!(products instanceof List<?>)||((List<?>)products).isEmpty()||((List<?>)products).size()>4096)throw fault("Invalid IC2 output list");
        for(Object item:(List<?>)products) {
            Jobs.checkpoint();if(!(item instanceof ItemStack)||((ItemStack)item).getItem()==null||((ItemStack)item).stackSize<=0)throw fault("Invalid IC2 output stack");
            outputs.add(RecipeRow.stackEvidence((ItemStack)item));
        }
        return object("machine",machine(handler).name(),"inputs",inputs,"outputs",outputs,
                "metadata",com.github.dcysteine.nesql.exporter.source.TypedNbt.encode((NBTTagCompound)field(output,"metadata")));
    }

    @SuppressWarnings("unchecked")
    static void capture(TemplateRecipeHandler handler, Object input, Object output, RecipeRow row) {
        Machine machine = machine(handler);
        if (machine == null) throw fault("Unverified IC2 machine " + handler.getClass().getName());
        List<RecipeRow.Ingredient> ingredients = ingredients(input);
        if (output == null || !output.getClass().getName().equals(API + "RecipeOutput")) throw fault("Unknown IC2 output type");
        List<?> raw = (List<?>) field(output, "items");
        if (raw.isEmpty() || raw.size() > 4096) throw fault("Invalid IC2 output count");
        List<ItemStack> products = new ArrayList<>();
        for (Object item : raw) {
            Jobs.checkpoint();
            if (!(item instanceof ItemStack) || ((ItemStack) item).getItem() == null || ((ItemStack) item).stackSize <= 0) {
                throw fault("IC2 machine has no fixed positive output");
            }
            products.add(((ItemStack) item).copy());
        }
        NBTTagCompound metadata = (NBTTagCompound) field(output, "metadata");
        int water = 0;
        if (machine == Machine.CENTRIFUGE) {
            if (metadata == null || !metadata.hasKey("minHeat", 3) || metadata.getInteger("minHeat") < 0) {
                throw fault("IC2 centrifuge has no nonnegative native heat requirement");
            }
            row.property("ic2:minHeat", "Minimum heat", metadata.getInteger("minHeat"));
        }
        if (machine == Machine.WASHING) {
            if (metadata == null) throw fault("IC2 washing recipe has no metadata and cannot operate");
            // Native getInteger defaults absent fields to zero; drain(0) consumes no fluid.
            // Water admission checks the fluid ID only, not its tags.
            water = Math.max(0, metadata.getInteger("amount"));
        }
        if (machine == Machine.BLOCK_CUTTER) {
            if (metadata == null) throw fault("IC2 block cutter has no metadata and cannot operate");
            row.property("ic2:bladeHardness", "Installed cutting blade minimum hardness (blade is not consumed)", metadata.getInteger("hardness"));
        }
        if (metadata != null) for (Object key : metadata.func_150296_c()) {
            if (!(machine == Machine.CENTRIFUGE && key.equals("minHeat") || machine == Machine.WASHING && key.equals("amount")
                    || machine == Machine.BLOCK_CUTTER && key.equals("hardness")))
                throw fault("Unadapted IC2 recipe metadata: " + key);
        }
        row.record.addProperty("duration", Integer.toString(machine.ticks));
        row.record.addProperty("energy", Integer.toString(machine.energy));
        row.itemInput(0, ingredients, false);
        if (water > 0) row.fluidInput(0, new FluidStack(FluidRegistry.WATER, water), false,
                object("kind", "wildcard", "meta", false, "nbt", true));
        for (int slot = 0; slot < products.size(); slot++) row.itemOutput(slot, products.get(slot), 10000);
    }
    private static Jobs.Fault changed(String detail){return new Jobs.Fault("recipe_changed","IC2 machine "+detail);}

    static List<RecipeRow.Ingredient> ingredients(Object input) {
        if (input == null) throw fault("Missing IC2 input predicate");
        String name = input.getClass().getName();
        boolean ore = name.equals(API + "RecipeInputOreDict");
        if (!ore && !name.equals(API + "RecipeInputItemStack")) throw fault("Unadapted IC2 input predicate: " + name);
        int amount = (Integer) invoke(input.getClass(), input, "getAmount", new Class<?>[0]);
        if (amount <= 0) throw fault("IC2 input amount must be positive");
        List<?> offered = (List<?>) invoke(input.getClass(), input, "getInputs", new Class<?>[0]);
        if (offered.isEmpty() || offered.size() > 65536) throw fault("IC2 predicate has no finite input set");
        Integer override = ore ? (Integer) field(input, "meta") : null;
        List<RecipeRow.Ingredient> result = new ArrayList<>();
        try(NativeItemExamples examples=new NativeItemExamples()){
        for (Object value : offered) {
            Jobs.checkpoint();
            if (!(value instanceof ItemStack) || ((ItemStack) value).getItem() == null) throw fault("IC2 predicate contains an invalid item");
            if(ItemCallbacks.method(((ItemStack)value).getItem(),"getDamage","getDamage",ItemStack.class).getDeclaringClass()!=net.minecraft.item.Item.class)
                throw fault("IC2 ingredient requires an audited metadata getter: "+((ItemStack)value).getItem().getClass().getName());
            ItemStack item = ((ItemStack) value).copy(); item.stackSize = amount;
            if (override != null) item.setItemDamage(override);
            boolean wildcard = item.getItemDamage() == OreDictionary.WILDCARD_VALUE;
            ItemStack[] variants = wildcard ? examples.permutations(item) : new ItemStack[] {item};
            if (variants.length == 0 || variants.length + result.size() > 65536) throw fault("IC2 input expansion exceeds its budget");
            for (ItemStack variant : variants) {
                if (!(Boolean) invoke(input.getClass(), input, "matches", new Class<?>[] {ItemStack.class}, variant.copy())) {
                    throw new Jobs.Fault("slot_changed", "IC2 displayed candidate does not satisfy its native predicate");
                }
                result.add(new RecipeRow.Ingredient(variant.copy(), amount, false, object("kind", "wildcard", "meta", wildcard, "nbt", true)));
            }
        }
        }
        return result;
    }
    private static Jobs.Fault fault(String message) { return new Jobs.Fault("recipe_unsupported", message); }
}
