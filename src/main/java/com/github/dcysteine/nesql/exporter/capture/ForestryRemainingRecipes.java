package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.source.Identity;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.*;
import forestry.api.recipes.*;
import net.minecraft.init.Items;
import net.minecraft.item.*;
import net.minecraft.inventory.IInventory;
import net.minecraftforge.fluids.*;
import net.minecraftforge.oredict.OreDictionary;
import java.lang.reflect.*;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Forestry 4.10.17: owned casting/smelting rules and native fixed/dynamic bottle filling. */
final class ForestryRemainingRecipes implements RegistryRecipes {
    private static final String PREFIX="forestry.factory.recipes.";
    private final boolean bottler;
    private final ForestryRecipeBindings bindings;
    private final NativeItemExamples examples=new NativeItemExamples();
    private boolean closed;
    private final List<OwnedCraft> crafting=new ArrayList<>();
    private final List<OwnedSmelt> smelting=new ArrayList<>();
    private final List<Bottle> bottles=new ArrayList<>();
    private JsonObject context;
    private final String signature,program;
    private final Set<Facts> published=Collections.newSetFromMap(new IdentityHashMap<Facts,Boolean>());

    static boolean supports(ICraftingHandler handler){String name=handler.getClass().getName();return name.equals(PREFIX+"nei.NEIHandlerBottler")||name.equals(PREFIX+"nei.NEIHandlerFabricator");}
    ForestryRemainingRecipes(TemplateRecipeHandler handler){
        MagicApi.version("ForestryRemainingRecipes","Forestry");if(!supports(handler))throw fault("Unadapted Forestry factory handler");
        bindings=new ForestryRecipeBindings(handler.getClass());bottler=handler.getClass().getName().endsWith("Bottler");
        context=snapshot();signature=CanonicalJson.digest(context);
        program=bottler?null:Identity.content("program",object("kind","forestryFabricator","rules",context));
        if(bottler){
            for(FluidContainerRegistry.FluidContainerData data:FluidContainerRegistry.getRegisteredFluidContainerData()){
                Jobs.checkpoint();ItemStack empty=item(data.emptyContainer);JsonObject callback=callback(empty.getItem());
                if(callback!=null && callback.get("kind").getAsString().equals("unsupported")){bottles.add(new Bottle(null,callback,empty,data.fluid.copy()));continue;}
                Object nativeRecipe=invoke(type(PREFIX+"BottlerRecipe"),null,"getRecipe",new Class<?>[]{FluidStack.class,ItemStack.class},data.fluid.copy(),empty.copy());
                if(nativeRecipe!=null)bottles.add(new Bottle(nativeRecipe,callback,empty,data.fluid.copy()));
            }
        }else{
            for(Object recipe:registry("FabricatorRecipeManager"))crafting.add(new OwnedCraft((IFabricatorRecipe)recipe));
            for(Object recipe:registry("FabricatorSmeltingRecipeManager"))smelting.add(new OwnedSmelt((IFabricatorSmeltingRecipe)recipe));
        }
        verify();
    }
    public int size(){alive();return bottler?bottles.size():crafting.size()+smelting.size();}
    public void verify(){alive();bindings.verifyRoots();}
    public void finishVerification(){verify();bindings.verify();if(!signature.equals(CanonicalJson.digest(snapshot())))throw new Jobs.Fault("recipe_changed","Forestry native registry, ore membership or callback configuration changed");examples.verify();}
    public String program(Facts facts){
        if(bottler)return null;verify();
        if(!published.contains(facts)){
            List<JsonObject> records=new ArrayList<>();
            chunks("fabricatorRecipes",context.getAsJsonArray("crafting"),records);
            chunks("fabricatorSmelting",context.getAsJsonArray("smelting"),records);
            for(JsonObject record:records)facts.row("programs",record);
            published.add(facts);
        }
        return program;
    }
    private void chunks(String kind,JsonArray source,List<JsonObject> records){
        JsonArray rows=array();int offset=0,bytes=0;
        for(JsonElement value:source){Jobs.checkpoint();int size=CanonicalJson.bytes(value).length+1;if(size>900000)throw fault("Fabricator rule exceeds its row budget");
            if(rows.size()==128||bytes+size>900000){chunk(kind,offset,rows,records);offset+=rows.size();rows=array();bytes=0;}
            rows.add(owned(value));bytes+=size;
        }
        if(rows.size()>0||source.size()==0)chunk(kind,offset,rows,records);
    }
    private void chunk(String kind,int offset,JsonArray rows,List<JsonObject> records){
        JsonObject row=object("program",program,"offset",offset,"data",object("kind",kind,"rows",rows));row.addProperty("id",program+"."+Identity.content("chunk",row));records.add(row);
    }
    public boolean capture(int index,RecipeRow row){
        if(index<0||index>=size())throw new IndexOutOfBoundsException("Forestry recipe "+index);
        if(bottler)return bottle(bottles.get(index),row);
        if(index<crafting.size())return craft(index,row);
        return smelt(index-crafting.size(),row);
    }
    public JsonObject exclusion(int index){
        if(bottler)return null;
        if(index<crafting.size())for(int cell=0;cell<crafting.get(index).ingredients.length;cell++){
            Object ingredient=crafting.get(index).ingredients[cell];if(ingredient instanceof List&&((List<?>)ingredient).isEmpty())return object("kind","forestry-fabricator-unreachable","reason","native ingredient OR list is empty and never matches","cell",cell);
        }
        return null;
    }
    private boolean bottle(Bottle source,RecipeRow row){
        if(source.recipe==null)throw fault("Unadapted Forestry fluid-container callback: "+source.callback);
        Object ownedRecipe=invoke(type(PREFIX+"BottlerRecipe"),null,"getRecipe",new Class<?>[]{FluidStack.class,ItemStack.class},source.fluid.copy(),source.empty.copy());
        if(ownedRecipe==null)throw new Jobs.Fault("recipe_changed","Forestry bottle projection disappeared");
        ItemStack empty=item((ItemStack)field(ownedRecipe,"empty")),filled=item((ItemStack)field(ownedRecipe,"filled"));
        FluidStack fluid=fluid((FluidStack)field(ownedRecipe,"input"));
        row.itemInput(0,Collections.singletonList(new RecipeRow.Ingredient(empty,1,false,object("kind","wildcard","meta",false,"nbt",true))),false);
        if(source.callback==null){
            row.fluidInput(0,fluid,false);
            filled.stackSize=1;row.itemOutput(0,filled,10000);
        }else{
            // FluidHelper always requests at most 1000 mB, but drains the simulated
            // tank amount rather than the amount the callback actually accepts.
            row.fluidInput(0,source.fluid,false,object("kind","wildcard","meta",false,"nbt",true));
            row.itemOutput(0,filled,10000,
                    object("kind","potential","stat","forestry:bottlerCompletion","condition","Produced only when the filled container reaches capacity; partial filling replaces the input slot","nominal","1"));
        }
        row.record.add("process",object("kind","forestryBottler","callback",owned(source.callback),"fluid",fluid.getFluid().getName(),
                "retainedInput",stack(empty),"requirement",liquid(fluid),"result",stack(filled)));
        float multiplier=((fluid.getFluid().getViscosity(fluid)/1000f-1f)/20f+1f)*(fluid.amount/1000f);
        row.note("forestry:bottler-cycle","forestry.factory.tiles.TileBottler#workCycle",row.facts.locale().equals("zh_CN")?"所示罐量的基础工作步数 "+Math.round(multiplier*5f)+"，难度前能量参数 "+Math.round(multiplier*1000f)+" RF，每步 5 个游戏 tick。动态容器在填满前留在输入槽，部分填充不表示已完成产出。":"Base work steps for the shown tank amount: "+Math.round(multiplier*5f)+"; energy parameter before difficulty: "+Math.round(multiplier*1000f)+" RF; 5 game ticks per step. Dynamic containers remain in the input slot until full; a partial fill is not a completed result.");
        return true;
    }
    private boolean craft(int index,RecipeRow row){
        OwnedCraft source=crafting.get(index);
        for(Object ingredient:source.ingredients)if(ingredient instanceof List && ((List<?>)ingredient).isEmpty())return false;
        int slot=0;
        if(source.plan!=null){
            List<RecipeRow.Ingredient> choices=choices(source.plan,1,true);
            row.itemInput(slot++,choices,false);
            if(source.planUse.get("kind").getAsString().equals("wax"))for(JsonElement choice:row.inputs.get(0).getAsJsonObject().getAsJsonArray("choices"))
                choice.getAsJsonObject().add("consume",object("kind","damage","points",source.output.stackSize));
        }
        JsonArray cells=array();for(int cell=0;cell<source.ingredients.length;cell++){
            Object ingredient=source.ingredients[cell];if(ingredient==null){cells.add(value(null));continue;}
            List<RecipeRow.Ingredient> choices=new ArrayList<>();
            for(ItemStack item:members(ingredient))choices.addAll(choices(item,1,false));
            if(choices.isEmpty())return false;
            cells.add(value(slot));row.itemInput(slot++,choices,false);
            for(JsonElement choice:row.inputs.get(row.inputs.size()-1).getAsJsonObject().getAsJsonArray("choices"))choice.getAsJsonObject().add("consume",object("kind","allocated"));
        }
        if(source.liquid!=null)row.fluidInput(0,source.liquid,false);
        row.itemOutput(0,source.output,10000);
        row.record.add("grid",object("width",source.width,"height",source.height,"cells",cells,"mirror",true));
        row.record.add("process",object("kind","forestryFabricator","program",program,"operation","craft","selector",index));
        requirements(row);return true;
    }
    private boolean smelt(int index,RecipeRow row){
        OwnedSmelt source=smelting.get(index);
        row.itemInput(0,choices(source.resource,1,false),false);row.fluidOutput(0,source.product);
        row.record.add("process",object("kind","forestryFabricator","program",program,"operation","smelt","selector",index));
        row.note("forestry:melting-point","forestry.factory.tiles.TileFabricator#workCycle",row.facts.locale().equals("zh_CN")?"熔化最低热量 "+source.meltingPoint:"Minimum smelting heat: "+source.meltingPoint);requirements(row);return true;
    }
    private static void requirements(RecipeRow row){row.note("forestry:fabricator-cycle","forestry.factory.tiles.TileFabricator#workCycle",row.facts.locale().equals("zh_CN")?"基础每个热循环能量参数 200 RF、增加 100 热量，每步 5 个游戏 tick。实际受难度、供能、热量、储罐和库存状态约束。":"Base heat-cycle energy parameter: 200 RF; heat gained: 100; 5 game ticks per step. Difficulty, power, heat, tank and inventory state govern operation.");}
    private JsonObject snapshot(){
        JsonArray recipes=array(),smelts=array();
        if(bottler){
            FluidContainerRegistry.FluidContainerData[] registered=FluidContainerRegistry.getRegisteredFluidContainerData();if(registered.length>262144)throw fault("Bottler registry exceeds budget");
            for(FluidContainerRegistry.FluidContainerData data:registered){Jobs.checkpoint();recipes.add(object("empty",stack(data.emptyContainer),"filled",stack(data.filledContainer),"fluid",liquid(data.fluid),"callback",callback(data.emptyContainer.getItem())));}
            return bounded(object("containers",recipes));
        }
        for(Object raw:registry("FabricatorRecipeManager")){Jobs.checkpoint();exact(raw,"FabricatorRecipe");OwnedCraft recipe=new OwnedCraft((IFabricatorRecipe)raw);
            JsonArray matrix=array();for(Object ingredient:recipe.ingredients){if(ingredient==null){matrix.add(value(null));continue;}if(ingredient instanceof ItemStack)matrix.add(stack((ItemStack)ingredient));else{JsonArray alternatives=array();for(ItemStack member:members(ingredient))alternatives.add(stack(member));matrix.add(alternatives);}}
            recipes.add(object("width",recipe.width,"height",recipe.height,"plan",stack(recipe.plan),"ingredients",matrix,"fluid",liquid(recipe.liquid),"result",stack(recipe.output),"planUse",recipe.planUse,"stockItems",stockItems(recipe.ingredients)));
        }
        for(Object raw:registry("FabricatorSmeltingRecipeManager")){Jobs.checkpoint();exact(raw,"FabricatorSmeltingRecipe");OwnedSmelt recipe=new OwnedSmelt((IFabricatorSmeltingRecipe)raw);smelts.add(object("resource",stack(recipe.resource),"product",liquid(recipe.product),"meltingPoint",recipe.meltingPoint));}
        return bounded(object("crafting",recipes,"smelting",smelts));
    }
    private static JsonObject bounded(JsonObject value){if(CanonicalJson.bytes(value).length>16*1024*1024)throw fault("Forestry rules exceed 16 MiB");return value;}
    @SuppressWarnings("unchecked") private static Collection<?> registry(String manager){Object value=field(type(PREFIX+manager),null,"recipes");if(!(value instanceof Set)||((Set<?>)value).size()>262144)throw fault("Invalid Forestry native registry");return (Collection<?>)value;}
    private List<RecipeRow.Ingredient> choices(ItemStack input,int amount,boolean keep){return examples.choices(item(input),amount,keep);}
    private void alive(){Jobs.checkpoint();if(closed)throw new Jobs.Fault("capture_closed","Forestry fabrication/bottling reader is closed");}
    public void close(){if(closed)return;closed=true;bindings.close();examples.close();crafting.clear();smelting.clear();bottles.clear();published.clear();context=null;}
    private static List<ItemStack> members(Object raw){
        if(raw instanceof ItemStack)return Collections.singletonList(item((ItemStack)raw));
        if(!(raw instanceof ArrayList))throw fault("Unadapted native Forestry ingredient: "+raw.getClass().getName());
        if(((ArrayList<?>)raw).size()>65536)throw fault("Forestry ingredient list exceeds budget");List<ItemStack> result=new ArrayList<>();for(Object value:(ArrayList<?>)raw){if(!(value instanceof ItemStack))throw fault("Forestry ingredient list contains non-item");result.add(item((ItemStack)value));}return result;
    }
    private static JsonObject stack(ItemStack item){if(item==null)return null;ItemStack copy=item(item);JsonObject value=SqueezerRules.stack(copy);value.addProperty("subtypes",copy.getHasSubtypes());value.addProperty("maxDamage",copy.getMaxDamage());
        // Native stock removal can invoke a container callback once per removed
        // chunk, so retain the callback owner rather than assuming unit returns.
        value.addProperty("containerOwner",ItemCallbacks.method(copy.getItem(),"getContainerItem","getContainerItem",ItemStack.class).getDeclaringClass().getName());
        value.add("container",container(copy));return value;}
    private static JsonObject container(ItemStack input){
        if(!ItemCallbacks.hasContainer(input))return object("kind","none");
        ItemStack returned=ItemCallbacks.container(input,input.getItemDamage()==OreDictionary.WILDCARD_VALUE);
        if(returned==null)return object("kind","none");
        if(ItemCallbacks.method(input.getItem(),"doesContainerItemLeaveCraftingGrid","doesContainerItemLeaveCraftingGrid",ItemStack.class).getDeclaringClass()!=Item.class)throw fault("Unadapted Forestry container stow callback");
        JsonObject descriptor=SqueezerRules.stack(returned);descriptor.addProperty("subtypes",returned.getHasSubtypes());descriptor.addProperty("maxDamage",returned.getMaxDamage());
        return object("kind","fixed","result",descriptor,"leave",input.getItem().doesContainerItemLeaveCraftingGrid(input.copy()));
    }
    private static JsonArray stockItems(Object[] ingredients){
        TreeMap<String,JsonObject> values=new TreeMap<>();
        for(Object ingredient:ingredients)if(ingredient!=null)for(ItemStack template:members(ingredient)){
            JsonObject descriptor=stack(template);values.put(CanonicalJson.digest(descriptor),descriptor);
            for(int id:OreDictionary.getOreIDs(template))for(ItemStack variant:OreDictionary.getOres(OreDictionary.getOreName(id))){
                Jobs.checkpoint();JsonObject candidate=stack(variant);values.put(CanonicalJson.digest(candidate),candidate);if(values.size()>65536)throw fault("Fabricator stock alternatives exceed budget");
            }
        }
        JsonArray rows=array();for(JsonObject value:values.values())rows.add(value);return rows;
    }
    private static JsonObject liquid(FluidStack source){if(source==null)return null;FluidStack fluid=fluid(source);String name=FluidRegistry.getFluidName(fluid.getFluid());if(name==null)throw fault("Unregistered Forestry fluid");
        try{if(fluid.getFluid().getClass().getMethod("getViscosity",FluidStack.class).getDeclaringClass()!=Fluid.class)throw fault("Unadapted Forestry fluid viscosity callback");}catch(NoSuchMethodException failure){throw fault("Missing fluid viscosity callback");}
        return object("registry",name,"amount",fluid.amount,"nbt",TypedNbt.encode(fluid.tag),"viscosity",fluid.getFluid().getViscosity(fluid));}
    private static JsonObject callback(Item item){
        if(!(item instanceof IFluidContainerItem))return null;String kind="unsupported",owner="";int capacity=0;
        try{
            String read=item.getClass().getMethod("getFluid",ItemStack.class).getDeclaringClass().getName();
            String fill=item.getClass().getMethod("fill",ItemStack.class,FluidStack.class,boolean.class).getDeclaringClass().getName();
            String cap=item.getClass().getMethod("getCapacity",ItemStack.class).getDeclaringClass().getName();
            if(read.equals(ItemFluidContainer.class.getName())&&fill.equals(read)&&cap.equals(read)){kind="forge";owner=read;capacity=(Integer)field(item,"capacity");}
            else if(item.getClass().getName().equals("ic2.core.item.ItemFluidCell")&&read.equals("ic2.core.item.ItemIC2FluidContainer")&&fill.equals(read)&&cap.equals(read)){
                MagicApi.version("ForestryRemainingRecipes","IC2");kind="ic2";owner=read;capacity=(Integer)field(item,"capacity");
            }
        }catch(NoSuchMethodException failure){throw fault("Missing Forestry fluid-container API");}
        return object("kind",kind,"capacity",capacity,"nbtKey","Fluid","owner",owner);
    }
    private static JsonObject planUse(ItemStack plan){
        JsonArray wax=array();Class<?> api=type("forestry.core.items.ICraftingPlan");
        for(Object raw:Item.itemRegistry)if(api.isInstance(raw)){
            Item item=(Item)raw;validatePlan(item);wax.add(object("registry",Item.itemRegistry.getNameForObject(item),"maxDamage",item.getMaxDamage()));if(wax.size()>4096)throw fault("Fabricator plan registry exceeds budget");
        }
        if(plan==null||!api.isInstance(plan.getItem()))return object("kind","keep","waxPlans",wax);
        validatePlan(plan.getItem());return object("kind","wax","maxDamage",plan.getMaxDamage(),"waxPlans",wax);
    }
    private static void validatePlan(Item item){
        try{if(!item.getClass().getMethod("planUsed",ItemStack.class,ItemStack.class).getDeclaringClass().getName().equals("forestry.apiculture.items.ItemWaxCast"))throw fault("Unadapted Forestry plan-used callback");}
        catch(NoSuchMethodException failure){throw fault("Missing Forestry plan-used callback");}
    }
    private static ItemStack item(ItemStack value){if(value==null||value.getClass()!=ItemStack.class||value.getItem()==null||value.stackSize<=0)throw fault("Invalid Forestry item template");
        if(ItemCallbacks.method(value.getItem(),"getDamage","getDamage",ItemStack.class).getDeclaringClass()!=Item.class)throw fault("Unadapted Forestry metadata callback");return value.copy();}
    private static FluidStack fluid(FluidStack value){if(value==null||value.getClass()!=FluidStack.class||value.getFluid()==null||value.amount<=0)throw fault("Invalid Forestry fluid template");return value.copy();}
    private static JsonElement owned(JsonElement value){return value==null?JsonNull.INSTANCE:new JsonParser().parse(value.toString());}
    private static void exact(Object source,String kind){if(source==null||!source.getClass().getName().equals(PREFIX+kind))throw fault("Unadapted Forestry recipe implementation");}
    private static Jobs.Fault fault(String message){return new Jobs.Fault("recipe_unsupported",message);}

    private static final class Bottle {
        final Object recipe;final JsonObject callback;final ItemStack empty;final FluidStack fluid;
        Bottle(Object recipe,JsonObject callback,ItemStack empty,FluidStack fluid){this.recipe=recipe;this.callback=callback;this.empty=empty.copy();this.fluid=fluid.copy();}
    }
    private static final class OwnedSmelt {
        final ItemStack resource;final FluidStack product;final int meltingPoint;
        OwnedSmelt(IFabricatorSmeltingRecipe source){resource=item(source.getResource());product=fluid(source.getProduct());meltingPoint=source.getMeltingPoint();if(meltingPoint<0||meltingPoint>5000)throw fault("Unreachable Fabricator melting point");}
    }
    private static final class OwnedCraft implements IFabricatorRecipe {
        final ItemStack plan,output;final FluidStack liquid;final int width,height;final Object[] ingredients;final JsonObject planUse;
        OwnedCraft(IFabricatorRecipe source){this(source.getPlan(),source.getLiquid(),source.getRecipeOutput(),source.getWidth(),source.getHeight(),source.getIngredients(),planUse(source.getPlan()));if(source.preservesNbt())throw fault("Unadapted Forestry NBT-preserving recipe");}
        OwnedCraft(ItemStack plan,FluidStack liquid,ItemStack output,int width,int height,Object[] ingredients,JsonObject planUse){
            this.plan=plan==null?null:item(plan);this.liquid=liquid==null?null:fluid(liquid);this.output=output==null?null:item(output);this.width=width;this.height=height;this.planUse=planUse;
            if(width<1||width>3||height<1||height>3||ingredients==null||ingredients.length!=width*height)throw fault("Invalid Forestry crafting matrix");
            this.ingredients=new Object[ingredients.length];for(int i=0;i<ingredients.length;i++){Object raw=ingredients[i];if(raw==null)continue;this.ingredients[i]=raw instanceof ItemStack?item((ItemStack)raw):new ArrayList<>(members(raw));}
        }
        public ItemStack getPlan(){return plan==null?null:plan.copy();}public FluidStack getLiquid(){return liquid==null?null:liquid.copy();}
        public ItemStack getRecipeOutput(){return output==null?null:output.copy();}public ItemStack getCraftingResult(IInventory inventory){return getRecipeOutput();}
        public int getWidth(){return width;}public int getHeight(){return height;}public boolean preservesNbt(){return false;}
        public Object[] getIngredients(){Object[] copy=new Object[ingredients.length];for(int i=0;i<copy.length;i++){Object raw=ingredients[i];copy[i]=raw==null?null:raw instanceof ItemStack?((ItemStack)raw).copy():new ArrayList<>(members(raw));}return copy;}
        public boolean matches(ItemStack offered,ItemStack[][] grid){if(plan!=null&&!(Boolean)invoke(type("forestry.core.utils.ItemStackUtil"),null,"isCraftingEquivalent",new Class<?>[]{ItemStack.class,ItemStack.class},plan,offered))return false;return (Boolean)invoke(type("forestry.core.recipes.RecipeUtil"),null,"matches",new Class<?>[]{Object[].class,int.class,int.class,ItemStack[][].class},getIngredients(),width,height,grid);}
    }
}
