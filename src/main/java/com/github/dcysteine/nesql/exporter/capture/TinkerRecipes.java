package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** TConstruct 1.13.57 definitions. Casting callbacks receive owned semantic projections. */
final class TinkerRecipes implements RegistryRecipes {
    private static final String HANDLERS = "tconstruct.plugins.nei.";
    private static final String CRAFTING = "tconstruct.library.crafting.";
    private final String handlerClass;
    private final List<Object> recipes = new ArrayList<>();
    private final NativeItemExamples examples=new NativeItemExamples();
    private final TinkerCasting.Events events=new TinkerCasting.Events();
    private Object[] anchors;
    private NativeDefinitionGraph definitions;
    private boolean closed;

    static boolean supports(ICraftingHandler handler) {
        return supportsClass(handler.getClass().getName());
    }
    static boolean supportsClass(String name) {
        return name.equals(HANDLERS + "RecipeHandlerAlloying") || name.equals(HANDLERS + "RecipeHandlerMelting")
                || name.equals(HANDLERS + "RecipeHandlerCastingTable") || name.equals(HANDLERS + "RecipeHandlerCastingBasin")
                || name.equals(HANDLERS + "RecipeHandlerDryingRack");
    }
    private static boolean casting(TemplateRecipeHandler handler) { return handler.getClass().getName().equals(HANDLERS + "RecipeHandlerCastingTable")
            || handler.getClass().getName().equals(HANDLERS + "RecipeHandlerCastingBasin"); }
    private static boolean alloying(TemplateRecipeHandler handler) { return handler.getClass().getName().equals(HANDLERS + "RecipeHandlerAlloying"); }
    private static boolean drying(TemplateRecipeHandler handler) { return handler.getClass().getName().equals(HANDLERS + "RecipeHandlerDryingRack"); }
    TinkerRecipes(TemplateRecipeHandler handler) {
        MagicApi.version("TinkerRecipes","TConstruct");
        MagicApi.version("TinkerRecipes","Mantle");
        this.handlerClass = handler.getClass().getName();
        if(!supports(handler))throw fault("Unknown TConstruct handler");
        try{
        anchors=registrationAnchors(handlerClass);Object source=anchors[anchors.length-1];
        if (drying(handler)) recipes.addAll((List<?>)source);
        else if (casting(handler)) {
            if (cpw.mods.fml.common.Loader.isModLoaded("IguanaTweaksTConstruct")) MagicApi.version("TinkerRecipes","IguanaTweaksTConstruct");
            recipes.addAll((List<?>)source);
        } else if (alloying(handler)) recipes.addAll((List<?>)source);
        else for (Object key : ((Map<?, ?>)source).keySet()) {
            Jobs.checkpoint();
            if (!key.getClass().getName().equals("mantle.utils.ItemMetaWrapper")) throw fault("Unadapted melting key: " + key.getClass().getName());
            recipes.add(new ItemStack((Item) field(key, "item"), 1, (Integer) field(key, "meta")));
        }
        if (recipes.size() > 262144) throw fault("TConstruct registry exceeds its budget");
        Map<String,String[]> shapes=new HashMap<>();
        shapes.put(CRAFTING+"CastingRecipe",new String[]{"output","castingMetal","cast","consumeCast","coolTime","ignoreNBT"});
        shapes.put(CRAFTING+"AlloyMix",new String[]{"result","mixers"});
        shapes.put(CRAFTING+"DryingRackRecipes$DryingRecipe",new String[]{"time","input","result"});
        shapes.put("mantle.utils.ItemMetaWrapper",new String[]{"item","meta"});
        definitions=new NativeDefinitionGraph(()->{Object[] current=registrationAnchors(handlerClass);return Arrays.copyOfRange(current,1,current.length);},shapes);
        }catch(RuntimeException|Error failure){close();throw failure;}
    }
    static Object[] registrationAnchors(String handler){
        if(!supportsClass(handler))throw fault("Unknown TConstruct source");
        if(handler.endsWith("RecipeHandlerDryingRack")){
            Object list=field(type(CRAFTING+"DryingRackRecipes"),null,"recipes");requireList(list);return new Object[]{type(CRAFTING+"DryingRackRecipes"),list};
        }
        if(handler.endsWith("RecipeHandlerCastingTable")||handler.endsWith("RecipeHandlerCastingBasin")){
            Object manager=field(type("tconstruct.TConstruct"),null,handler.endsWith("Table")?"tableCasting":"basinCasting");
            if(manager==null)throw new Jobs.Fault("handler_not_ready","TConstruct casting registry is unavailable");
            if(manager.getClass()!=type(CRAFTING+"LiquidCasting"))throw fault("Unadapted native casting owner");Object casts=field(manager,"casts");requireList(casts);return new Object[]{manager,casts};
        }
        Object manager=field(type(CRAFTING+"Smeltery"),null,"instance");
        if(manager==null)throw new Jobs.Fault("handler_not_ready","TConstruct smeltery source is unavailable");
        if(manager.getClass()!=type(CRAFTING+"Smeltery"))throw fault("Unadapted native smeltery owner");
        if(handler.endsWith("RecipeHandlerAlloying")){Object list=field(manager,"alloys");requireList(list);return new Object[]{manager,list};}
        Object temperatures=field(manager,"temperatureList"),fuels=field(manager,"smelteryFuels"),melting=field(manager,"smeltingList");
        for(Object map:new Object[]{temperatures,fuels,melting})if(map==null||map.getClass()!=HashMap.class||((Map<?,?>)map).size()>262144)throw fault("Unadapted melting/temperature/fuel backing");
        return new Object[]{manager,temperatures,fuels,melting};
    }
    private static void requireList(Object list){if(list==null)throw new Jobs.Fault("handler_not_ready","TConstruct source is unavailable");if(list.getClass()!=ArrayList.class||((List<?>)list).size()>262144)throw fault("Unadapted TConstruct definition list");}
    public void verify(){alive();Object[] current=registrationAnchors(handlerClass);if(current.length!=anchors.length)throw changed();for(int i=0;i<current.length;i++)if(current[i]!=anchors[i])throw changed();definitions.verifyRoots();events.verify();}
    public void finishVerification(){verify();definitions.verify();examples.verify();events.finishVerification();}
    public int size() { alive();return recipes.size(); }
    public boolean capture(int index, RecipeRow row) {alive();if(handlerClass.endsWith("RecipeHandlerCastingTable")||handlerClass.endsWith("RecipeHandlerCastingBasin"))return TinkerCasting.capture(handlerClass,recipes.get(index),row,examples,events);return capture(handlerClass,recipes.get(index),row,recipes); }

    static boolean capture(TemplateRecipeHandler handler, Object recipe, RecipeRow row) {
        if (!supports(handler)) throw fault("Unknown TConstruct recipe handler");
        return capture(handler.getClass().getName(),recipe,row,drying(handler)?dryingRecipes():Collections.emptyList());
    }
    private static boolean capture(String handler,Object recipe,RecipeRow row,List<?> entries){
        if (handler.endsWith("RecipeHandlerDryingRack")) return dry(recipe, row, entries);
        if (handler.endsWith("RecipeHandlerCastingTable")||handler.endsWith("RecipeHandlerCastingBasin")) return TinkerCasting.capture(handler, recipe, row);
        if (handler.endsWith("RecipeHandlerAlloying")) return alloy(recipe, row);
        if (!(recipe instanceof ItemStack)) throw fault("Invalid TConstruct melting source");
        melting((ItemStack) recipe, row); return true;
    }

    private static List<?> dryingRecipes() {
        List<?> entries = new ArrayList<>((List<?>) field(type(CRAFTING + "DryingRackRecipes"), null, "recipes"));
        if (entries.size() > 262144) throw fault("Drying registry exceeds its budget");
        for (Object entry : entries) {
            Jobs.checkpoint();
            if (entry == null || !entry.getClass().getName().equals(CRAFTING + "DryingRackRecipes$DryingRecipe"))
                throw fault("Unadapted drying recipe implementation");
        }
        return entries;
    }

    private static boolean dry(Object recipe, RecipeRow row, List<?> entries) {
        if (!entries.contains(recipe)) throw fault("Drying recipe is not registered");
        ItemStack source = (ItemStack) field(recipe, "input");
        // The rack holds one item. The native predicate includes count and literal metadata.
        if (source == null || source.getItem() == null || source.stackSize != 1)
            return row.exclude(object("native", CRAFTING + "DryingRackRecipes", "reason", "native_single_item_rack_cannot_match_registered_template",
                    "input", RecipeRow.stackEvidence(source)));
        NBTTagCompound nbt = source.getTagCompound();
        // Removing frypanKill from the offered stack can never produce either template.
        if (nbt != null && (nbt.hasNoTags() || nbt.hasKey("frypanKill")))
            return row.exclude(object("native", CRAFTING + "DryingRackRecipes", "reason", "native_frypan_tag_removal_cannot_match_registered_nbt",
                    "input", RecipeRow.stackEvidence(source)));
        for (Object prior : entries) {
            if (prior == recipe) break;
            Jobs.checkpoint();
            if (ItemStack.areItemStacksEqual((ItemStack) field(prior, "input"), source))
                return row.exclude(object("native", CRAFTING + "DryingRackRecipes", "reason", "earlier_native_drying_input_match_claims_template",
                        "input", RecipeRow.stackEvidence(source), "earlierIndex", entries.indexOf(prior)));
        }
        int time = (Integer) field(recipe, "time");
        if (time <= 0) return row.exclude(object("native", "tconstruct.blocks.logic.DryingRackLogic",
                "reason", "nonpositive_native_maxTime_disables_drying", "time", time));
        ItemStack output = (ItemStack) field(recipe, "result");
        if (output == null || output.getItem() == null || output.stackSize <= 0) throw fault("Invalid native drying result");
        row.itemInput(0, Collections.singletonList(new RecipeRow.Ingredient(source.copy(), 1, false,
                object("kind", "without_tags", "keys", array("frypanKill")))), false);
        row.itemOutput(0, output.copy(), 10000);
        row.record.addProperty("duration", Integer.toString(time));
        return true;
    }

    private static boolean alloy(Object recipe, RecipeRow row) {
        if (recipe == null || !recipe.getClass().getName().equals(CRAFTING + "AlloyMix"))
            throw fault("Unadapted alloy implementation: " + (recipe == null ? "null" : recipe.getClass().getName()));
        List<?> mixers = (List<?>) field(recipe, "mixers");
        if (mixers.isEmpty()) return row.exclude(object("native", CRAFTING + "AlloyMix",
                "reason", "no_native_mixer_defines_minimum_alloy_batch", "mixers", 0));
        if (mixers.size() > 4096) throw fault("Alloy ingredients exceed their budget");
        List<FluidStack> inputs = new ArrayList<>();
        for (Object raw : mixers) {
            Jobs.checkpoint();
            FluidStack input = fluid(raw);
            for (FluidStack prior : inputs) if (prior.isFluidEqual(input))
                throw fault("Repeated alloy fluid cannot be matched by the smeltery's merged tank");
            inputs.add(input);
        }
        FluidStack output = fluid(field(recipe, "result"));
        for (int slot = 0; slot < inputs.size(); slot++) row.fluidInput(slot, inputs.get(slot), false);
        row.fluidOutput(0, output);
        row.note("tconstruct:batch", "tconstruct.library.crafting.AlloyMix#mix",row.facts.locale().equals("zh_CN")?"按所列流体数量的最大整数倍进行合金混合。":"Alloying uses the maximum whole multiple of the listed fluid quantities.");
        return true;
    }

    private static void melting(ItemStack source, RecipeRow row) {
        if (source.getItem() == null) throw fault("Invalid melting item");
        ItemStack input = source.copy(); input.stackSize = 1; input.setTagCompound(null);
        Class<?> registry = type(CRAFTING + "Smeltery");
        FluidStack output = fluid(invoke(registry, null, "getSmelteryResult", new Class<?>[] {ItemStack.class}, input.copy()));
        int temperature = (Integer) invoke(registry, null, "getLiquifyTemperature", new Class<?>[] {ItemStack.class}, input.copy());
        // Mantle's ItemMetaWrapper uses exact metadata, including 32767; it does not implement wildcard lookup.
        // Replace NEI's generic wildcard expansion with this exact registered input.
        row.itemInput(0, Collections.singletonList(new RecipeRow.Ingredient(input, 1, false,
                object("kind", "wildcard", "meta", false, "nbt", true))), false);
        row.fluidOutput(0, output);
        row.note("tconstruct:temperature", "tconstruct.library.crafting.Smeltery#getLiquifyTemperature",row.facts.locale().equals("zh_CN")?"熔化温度 "+temperature+" °C；加热时间取决于燃料与当前温度。":"Melting temperature: "+temperature+" °C; heating time depends on fuel and current temperature.");
        // Heating time depends on fuel/active temperature; do not invent a constant duration or EU cost.
    }
    private static FluidStack fluid(Object raw) {
        if (!(raw instanceof FluidStack)) throw fault("Missing native TConstruct fluid");
        FluidStack fluid = (FluidStack) raw;
        if (fluid.getFluid() == null || fluid.amount <= 0) throw fault("Invalid native TConstruct fluid amount");
        return fluid.copy();
    }
    static Object construct(Class<?> owner, Class<?>[] parameters, Object... arguments) {
        try { return owner.getConstructor(parameters).newInstance(arguments); }
        catch (InvocationTargetException error) {
            Throwable cause = error.getCause();
            if (cause instanceof Error) throw (Error) cause;
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            Jobs.Fault failure = fault("Native TConstruct constructor failed: " + owner.getName()); failure.initCause(cause); throw failure;
        } catch (ReflectiveOperationException error) {
            Jobs.Fault failure = fault("Missing native TConstruct constructor: " + owner.getName()); failure.initCause(error); throw failure;
        }
    }
    private static Jobs.Fault fault(String message) { return new Jobs.Fault("recipe_unsupported", message); }
    private static Jobs.Fault changed(){return new Jobs.Fault("recipe_changed","TConstruct native source changed");}
    private void alive(){Jobs.checkpoint();if(closed)throw new Jobs.Fault("capture_closed","TConstruct reader is closed");}
    public void close(){closed=true;if(definitions!=null)definitions.close();examples.close();events.close();anchors=null;recipes.clear();}
}
