package com.github.dcysteine.nesql.exporter.capture;

import codechicken.lib.gui.GuiDraw;
import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonObject;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import java.lang.reflect.Proxy;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** BuildCraft 7.1.44 assembly selection and ordered inventory allocation. */
final class BuildcraftRecipes implements RegistryRecipes {
    private static final String HANDLER="buildcraft.compat.nei.RecipeHandlerAssemblyTable";
    private final TemplateRecipeHandler handler;
    private final List<?> recipes;
    private final Map<Integer,JsonObject> exclusions=new HashMap<>();
    static boolean supports(ICraftingHandler handler){return handler.getClass().getName().equals(HANDLER);}
    BuildcraftRecipes(TemplateRecipeHandler handler){this(handler,registry());}
    private static List<?> registry(){
        version("BuildCraft|Silicon","7.1.44");version("BuildCraft|Compat","7.1.18");
        Object registry=field(type("buildcraft.api.recipes.BuildcraftRecipeRegistry"),null,"assemblyTable");
        if(registry==null||!registry.getClass().getName().equals("buildcraft.core.recipes.AssemblyRecipeManager"))throw fault("Unknown BuildCraft assembly manager");
        return new ArrayList<>((Collection<?>)invoke(registry.getClass(),registry,"getRecipes",new Class<?>[0]));
    }
    BuildcraftRecipes(TemplateRecipeHandler handler,List<?> recipes){
        if(!supports(handler)||recipes.size()>262144)throw fault("Invalid BuildCraft assembly registry");
        this.handler=handler;this.recipes=new ArrayList<>(recipes);
    }
    public int size(){return recipes.size();}
    public boolean capture(int index,RecipeRow row){
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
        List<List<RecipeRow.Ingredient>> requirements=new ArrayList<>();List<Object> visual=new ArrayList<>();
        int choices=0;
        for(List<ItemStack> group:groups){
            int amount=group.get(0).stackSize;List<RecipeRow.Ingredient> ingredients=new ArrayList<>();List<ItemStack> visible=new ArrayList<>();
            for(ItemStack anchor:group){
                Jobs.checkpoint();
                boolean wildcard=anchor.getItemDamage()==-1||anchor.getItemDamage()==32767;
                if(!wildcard&&!ItemStack.areItemStackTagsEqual(anchor,anchor.copy()))throw fault("BuildCraft input NBT depends on native object identity");
                ItemStack template=anchor.copy();template.stackSize=amount;
                ItemStack[] samples=anchor.getItemDamage()==32767?new PositionedStack(template,0,0,true).items:new ItemStack[]{template};
                for(ItemStack sample:samples){
                    if(++choices>65536||sample.getItem()!=anchor.getItem())throw fault("Invalid BuildCraft display expansion");
                    ItemStack owned=sample.copy();owned.stackSize=amount;
                    owned.setTagCompound(anchor.getTagCompound()==null?null:(net.minecraft.nbt.NBTTagCompound)anchor.getTagCompound().copy());
                    ingredients.add(new RecipeRow.Ingredient(owned,amount,false,object("kind","buildcraft","wildcard",wildcard,"subtypes",anchor.getHasSubtypes())));
                    visible.add(owned.copy());
                }
            }
            if(ingredients.isEmpty())throw fault("No BuildCraft input display sample");
            requirements.add(ingredients);visual.add(visible);
        }
        Class<?> api=type("buildcraft.api.recipes.IFlexibleRecipeViewable");
        Object projection=Proxy.newProxyInstance(api.getClassLoader(),new Class<?>[]{api},(proxy,method,args)->{
            switch(method.getName()){
                case "getInputs":return visual;
                case "getOutput":return output.copy();
                case "getEnergyCost":return energy;
                case "getCraftingTime":return 0L;
                default:throw fault("Unexpected native assembly view call: "+method.getName());
            }
        });
        TemplateRecipeHandler.CachedRecipe cached=(TemplateRecipeHandler.CachedRecipe)TinkerRecipes.construct(type(HANDLER+"$CachedAssemblyTableRecipe"),new Class<?>[]{handler.getClass(),api,boolean.class},handler,projection,false);
        @SuppressWarnings("unchecked") List<PositionedStack> positions=(List<PositionedStack>)field(cached,"inputs");
        if(positions.size()!=Math.min(12,groups.size()))throw new Jobs.Fault("slot_changed","Assembly layout changed its native display limit");
        for(int slot=0;slot<requirements.size();slot++){
            row.itemInput(slot<positions.size()?positions.get(slot):null,slot,requirements.get(slot),false);
            for(com.google.gson.JsonElement c:row.inputs.get(slot).getAsJsonObject().getAsJsonArray("choices"))c.getAsJsonObject().add("consume",object("kind","allocated"));
        }
        row.itemOutput(cached.getResult(),0,output,10000);
        row.record.add("process",object("kind","buildcraftAssembly","energy",energy));
        handler.arecipes.clear();handler.arecipes.add(cached);return true;
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
    static int[][] progressBars(){return new int[][]{{81,7,176,17,4,71,100,3}};}
    static void draw(TemplateRecipeHandler handler,int index){
        handler.drawBackground(index);
        GuiDraw.drawStringC(field(handler.arecipes.get(index),"energy")+" RF",93,84,0x808080,false);
    }
    private static Jobs.Fault fault(String message){return new Jobs.Fault("recipe_unsupported",message);}
}
