package com.github.dcysteine.nesql.exporter.capture;


import codechicken.nei.recipe.IRecipeHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** BuildCraft 7.1.44 assembly selection and ordered inventory allocation. */
final class BuildcraftRecipes implements RegistryRecipes {
    private static final String HANDLER="buildcraft.compat.nei.RecipeHandlerAssemblyTable";
    private static final String MANAGER="buildcraft.core.recipes.AssemblyRecipeManager";
    private final List<?> recipes;
    private final List<?> registry;
    private final Object manager;
    private final Map<?,?> table;
    private final IdentityHashMap<Object,List<String>> keys=new IdentityHashMap<>();
    private final NativeDefinitions definitions;
    private final Map<Integer,JsonObject> exclusions=new HashMap<>();
    private boolean closed;
    static boolean supports(IRecipeHandler handler){return handler.getClass().getName().equals(HANDLER);}
    BuildcraftRecipes(TemplateRecipeHandler handler){this(handler,registrationSource(handler));}
    static Object registrationSource(IRecipeHandler handler){
        if(!supports(handler))throw fault("Unknown BuildCraft assembly handler");
        MagicApi.version("BuildcraftRecipes","BuildCraft|Silicon");MagicApi.version("BuildcraftRecipes","BuildCraft|Compat");
        Object registry=field(type(MANAGER),null,"INSTANCE");
        if(registry==null)throw new Jobs.Fault("handler_not_ready","BuildCraft assembly manager is unavailable");
        if(!registry.getClass().getName().equals(MANAGER)||registry!=field(type("buildcraft.api.recipes.BuildcraftRecipeRegistry"),null,"assemblyTable"))
            throw fault("BuildCraft assembly API differs from the native machine manager");
        return registry;
    }
    private BuildcraftRecipes(TemplateRecipeHandler handler,Object manager){this(handler,nativeRows(manager),manager);}
    private static List<?> nativeRows(Object manager){
        Object table=field(manager,"assemblyRecipes");
        if(table==null)throw new Jobs.Fault("handler_not_ready","BuildCraft assembly table is unavailable");
        if(table.getClass()!=HashMap.class)throw fault("Unknown BuildCraft assembly storage map");
        if(((Map<?,?>)table).size()>262144)throw new Jobs.Fault("recipe_limit","BuildCraft assembly registry exceeds budget");
        return new ArrayList<>(((Map<?,?>)table).values());
    }
    BuildcraftRecipes(TemplateRecipeHandler handler,List<?> recipes){
        this(handler,recipes,null);
    }
    private BuildcraftRecipes(TemplateRecipeHandler handler,List<?> recipes,Object manager){
        if(!supports(handler))throw fault("Invalid BuildCraft assembly handler");
        if(recipes==null)throw new Jobs.Fault("handler_not_ready","BuildCraft assembly registry is unavailable");
        if(recipes.size()>262144)throw new Jobs.Fault("recipe_limit","BuildCraft assembly registry exceeds budget");
        this.recipes=new ArrayList<>(recipes);this.registry=manager==null?recipes:this.recipes;this.manager=manager;
        table=manager==null?null:(Map<?,?>)field(manager,"assemblyRecipes");
        if(table!=null)for(Map.Entry<?,?> entry:table.entrySet()){
            Jobs.checkpoint();if(!(entry.getKey() instanceof String))throw fault("Unknown BuildCraft assembly key");
            keys.computeIfAbsent(entry.getValue(),ignored->new ArrayList<>()).add((String)entry.getKey());
        }
        definitions=new NativeDefinitions("BuildCraft assembly",this.recipes,this::currentVisible,this::definition);
    }
    public int size(){verify();return recipes.size();}
    public void verify(){if(closed)throw new Jobs.Fault("capture_closed","BuildCraft assembly reader is closed");definitions.verify();}
    private List<?> currentVisible(){
        if(manager!=null&&(field(type(MANAGER),null,"INSTANCE")!=manager
                ||field(type("buildcraft.api.recipes.BuildcraftRecipeRegistry"),null,"assemblyTable")!=manager
                ||field(manager,"assemblyRecipes")!=table||table.size()!=recipes.size()))throw changed("native manager, table or count changed");
        return registry;
    }
    public void finishVerification(){
        verify();if(table!=null){int index=0;for(Object recipe:table.values()){
            Jobs.checkpoint();if(recipe!=recipes.get(index++))throw changed("native map selection order changed");
        }}definitions.finishVerification();
    }
    public void close(){if(closed)return;closed=true;recipes.clear();keys.clear();exclusions.clear();definitions.close();}
    public boolean capture(int index,RecipeRow row){
        verify();return definitions.checked(index,()->captureRow(index,row));
    }
    private boolean captureRow(int index,RecipeRow row){
        Object recipe=recipes.get(index);
        if(recipe==null||!recipe.getClass().getName().equals("buildcraft.core.recipes.FlexibleRecipe"))throw fault("Unadapted BuildCraft assembly recipe: "+(recipe==null?"null":recipe.getClass().getName()));
        List<?> fluids=(List<?>)field(recipe,"inputFluids");
        for(Object value:fluids)if(!(value instanceof FluidStack)||((FluidStack)value).getFluid()==null||((FluidStack)value).amount<=0)throw fault("Invalid assembly fluid requirement");
        if(!fluids.isEmpty()){
            if(exclusions.size()>=4096&&!exclusions.containsKey(index))throw fault("Assembly exclusion budget exceeded");
            exclusions.put(index,object("reason","native_machine_has_no_fluid_slots","machine","buildcraft.silicon.TileAssemblyTable","sourceClass",recipe.getClass().getName(),"nativeIndex",index));
            return false;
        }
        ItemStack output=item(field(recipe,"output"));
        int energy=(Integer)field(recipe,"energyCost");
        List<List<ItemStack>> groups=new ArrayList<>();
        for(Object fixed:(List<?>)field(recipe,"inputItems"))groups.add(Collections.singletonList(item(fixed)));
        for(Object alternatives:(List<?>)field(recipe,"inputItemsWithAlternatives")){
            if(!(alternatives instanceof List<?>)||((List<?>)alternatives).isEmpty())throw fault("Empty BuildCraft alternative list has no native amount");
            List<ItemStack> group=new ArrayList<>();for(Object value:(List<?>)alternatives)group.add(item(value,group.isEmpty()));groups.add(group);
        }
        if(groups.size()>4096)throw fault("Invalid BuildCraft requirement count");
        List<List<RecipeRow.Ingredient>> requirements=new ArrayList<>();
        int choices=0;
        for(List<ItemStack> group:groups){
            int amount=group.get(0).stackSize;List<RecipeRow.Ingredient> ingredients=new ArrayList<>();
            for(ItemStack anchor:group){
                Jobs.checkpoint();
                inputPolicy(anchor);
                boolean wildcard=anchor.getItemDamage()==-1||anchor.getItemDamage()==32767;
                if(!wildcard&&!ItemStack.areItemStackTagsEqual(anchor,anchor.copy()))throw fault("BuildCraft input NBT depends on native object identity");
                ItemStack template=anchor.copy();template.stackSize=amount;
                ItemStack[] samples=anchor.getItemDamage()==32767?new NativeItemVariants(template).copies():new ItemStack[]{template};
                for(ItemStack sample:samples){
                    if(++choices>65536||sample.getItem()!=anchor.getItem())throw fault("Invalid BuildCraft display expansion");
                    ItemStack owned=sample.copy();owned.stackSize=amount;
                    owned.setTagCompound(anchor.getTagCompound()==null?null:(net.minecraft.nbt.NBTTagCompound)anchor.getTagCompound().copy());
                    ingredients.add(new RecipeRow.Ingredient(owned,amount,false,object("kind","buildcraft","wildcard",wildcard,"subtypes",anchor.getHasSubtypes())));
                }
            }
            if(ingredients.isEmpty())throw fault("No BuildCraft input display sample");
            requirements.add(ingredients);
        }
        for(int slot=0;slot<requirements.size();slot++){
            row.itemInput(slot,requirements.get(slot),false);
            for(com.google.gson.JsonElement c:row.inputs.get(slot).getAsJsonObject().getAsJsonArray("choices"))c.getAsJsonObject().add("consume",object("kind","allocated"));
        }
        row.itemOutput(0,output,10000);
        row.record.add("process",object("kind","buildcraftAssembly","energy",energy));
        return true;
    }
    private JsonObject definition(Object recipe){
        if(recipe==null||!recipe.getClass().getName().equals("buildcraft.core.recipes.FlexibleRecipe"))throw fault("Unadapted BuildCraft assembly recipe");
        if(table!=null)for(String key:keys.get(recipe))if(table.get(key)!=recipe)throw changed("native keyed recipe instance changed");
        JsonArray fluids=array();
        for(Object raw:nativeList(recipe,"inputFluids")){
            Jobs.checkpoint();if(raw==null||raw.getClass()!=FluidStack.class||((FluidStack)raw).getFluid()==null||((FluidStack)raw).amount<=0)throw fault("Invalid assembly fluid requirement");
            FluidStack fluid=(FluidStack)raw;fluids.add(object("fluid",fluid.getFluid().getName(),"amount",Integer.toString(fluid.amount),"nbt",com.github.dcysteine.nesql.exporter.source.TypedNbt.encode(fluid.tag)));
        }
        // A native machine with no fluid inventory cannot execute this entry;
        // no irrelevant recipe/item callbacks are evaluated to prove exclusion.
        if(fluids.size()>0)return object("noFluidSlots",true,"fluids",fluids);
        JsonArray fixed=array(),alternatives=array();int count=0;
        for(Object raw:nativeList(recipe,"inputItems")){
            Jobs.checkpoint();if(++count>65536)throw fault("Assembly definition candidate budget exceeded");fixed.add(itemFacts(item(raw)));
        }
        for(Object raw:nativeList(recipe,"inputItemsWithAlternatives")){
            if(!(raw instanceof List<?>)||((List<?>)raw).isEmpty())throw fault("Invalid assembly alternative group");
            JsonArray group=array();boolean first=true;
            for(Object candidate:(List<?>)raw){Jobs.checkpoint();if(++count>65536)throw fault("Assembly definition candidate budget exceeded");group.add(itemFacts(item(candidate,first)));first=false;}
            alternatives.add(group);
        }
        return object("id",field(recipe,"id"),"energy",field(recipe,"energyCost"),"craftingTime",Long.toString((Long)field(recipe,"craftingTime")),
            "output",RecipeRow.stackEvidence(item(field(recipe,"output"))),"fixed",fixed,"alternatives",alternatives);
    }
    private static List<?> nativeList(Object recipe,String key){Object list=field(recipe,key);if(list==null||list.getClass()!=ArrayList.class)throw fault("Unknown native assembly list: "+key);return (List<?>)list;}
    private static JsonObject itemFacts(ItemStack stack){
        inputPolicy(stack);JsonArray expanded=array();
        if(stack.getItemDamage()==32767){
            ItemStack template=stack.copy();template.stackSize=1;
            ItemStack[] samples=new NativeItemVariants(template).copies();
            if(samples.length==0||samples.length>65536)throw fault("Invalid assembly metadata expansion");
            for(ItemStack sample:samples){
                Jobs.checkpoint();if(sample==null||sample.getItem()!=stack.getItem())throw fault("Invalid assembly metadata specimen");
                ItemStack owned=sample.copy();owned.stackSize=1;owned.setTagCompound(stack.getTagCompound()==null?null:(net.minecraft.nbt.NBTTagCompound)stack.getTagCompound().copy());
                expanded.add(RecipeRow.stackEvidence(owned));
            }
        }
        return object("stack",RecipeRow.stackEvidence(stack),"subtypes",stack.getHasSubtypes(),"expanded",expanded);
    }
    private static void inputPolicy(ItemStack stack){
        NativeItemSubtypes.read(stack.getItem());
    }
    public JsonObject exclusion(int index){JsonObject proof=exclusions.get(index);return proof==null?null:new com.google.gson.JsonParser().parse(proof.toString()).getAsJsonObject();}
    private static ItemStack item(Object value){
        return item(value,true);
    }
    private static ItemStack item(Object value,boolean requireAmount){
        if(!(value instanceof ItemStack)||((ItemStack)value).getItem()==null||requireAmount&&((ItemStack)value).stackSize<=0)throw fault("Invalid BuildCraft item");
        ItemStack stack=(ItemStack)value;
        try{if(stack.getItem().getClass().getMethod("getDamage",ItemStack.class).getDeclaringClass()!=Item.class)throw fault("BuildCraft item has custom metadata semantics");}
        catch(NoSuchMethodException error){throw fault("Missing native metadata getter");}
        return stack.copy();
    }
    private static Jobs.Fault fault(String message){return new Jobs.Fault("recipe_unsupported",message);}
    private static Jobs.Fault changed(String message){return new Jobs.Fault("recipe_changed","BuildCraft assembly "+message);}
}
