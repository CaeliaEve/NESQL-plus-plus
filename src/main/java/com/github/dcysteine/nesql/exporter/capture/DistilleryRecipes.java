package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonArray;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Pinned Witchery distillation: unit reagents, consumed jars and independent output slots. */
final class DistilleryRecipes implements RegistryRecipes {
    private static final String HANDLER="alkalus.main.nei.NEI_Handler_Distillery";
    private static final String REGISTRY="com.emoniph.witchery.crafting.DistilleryRecipes";
    private final TemplateRecipeHandler handler;
    private final List<?> source;
    private final int count;
    private final ItemStack jar;
    private final String signature;
    static boolean supports(ICraftingHandler handler) { return handler.getClass().getName().equals(HANDLER); }
    DistilleryRecipes(TemplateRecipeHandler handler) { this(handler,registry(),jar()); }
    private static List<?> registry() {
        version("witchery","0.24.1");
        version("WitcheryExtras","1.3.8");
        return (List<?>)field(invoke(type(REGISTRY),null,"instance",new Class<?>[0]),"recipes");
    }
    private static ItemStack jar() {
        Object item=field(field(field(type("com.emoniph.witchery.Witchery"),null,"Items"),"GENERIC"),"itemEmptyClayJar");
        return (ItemStack)invoke(item.getClass(),item,"createStack",new Class<?>[]{int.class},1);
    }
    DistilleryRecipes(TemplateRecipeHandler handler,List<?> source,ItemStack jar) {
        if(!supports(handler)||source==null||source.size()>65536)throw fault("Invalid distillery registry");
        if(jar==null)throw fault("Distillery jar is missing");
        this.handler=handler;this.source=source;count=source.size();this.jar=jar.copy();signature=signature();
    }
    public int size(){return count;}
    public void verify(){if(!signature.equals(signature()))throw new Jobs.Fault("environment_changed","Distillery registry changed");}
    public boolean capture(int index,RecipeRow row) {
        Object nativeRecipe=source.get(index);
        ItemStack[] inputs=(ItemStack[])field(nativeRecipe,"inputs"),outputs=(ItemStack[])field(nativeRecipe,"outputs");
        int jars=(Integer)field(nativeRecipe,"jars");
        // Own the view so its displayed source counts cannot leak into consumption.
        List<PositionedStack> shownInputs=new ArrayList<>(),shownOutputs=new ArrayList<>();
        for(int i=0;i<2;i++)if(inputs[i]!=null){
            ItemStack item=inputs[i].copy();item.stackSize=1;item.setTagCompound(null);
            boolean meta=!item.getHasSubtypes();if(meta)item.setItemDamage(0);
            PositionedStack pos=new PositionedStack(item,43,i==0?5:23,false);shownInputs.add(pos);
            row.itemInput(pos,i,1,false,false,object("kind","wildcard","meta",meta,"nbt",true));
        }
        if(jars>0){
            ItemStack item=jar.copy();item.stackSize=jars;
            PositionedStack pos=new PositionedStack(item,43,43,false);shownInputs.add(pos);
            row.itemInput(pos,2,jars,false,false,object("kind","wildcard","meta",false,"nbt",true));
        }
        for(int i=0;i<4;i++)if(outputs[i]!=null){
            ItemStack item=outputs[i].copy();
            PositionedStack pos=new PositionedStack(item,105+(i%2)*18,5+(i/2)*18,false);shownOutputs.add(pos);
            row.itemOutput(pos,i,item,10000);
        }
        row.record.addProperty("duration","800");
        row.property("witchery:altarPowerPerWorkTick","Altar power per successful work tick",0.6f);
        row.property("witchery:powerRequired","Requires a reachable altar power source",true);
        row.property("witchery:inputOrder","Reagent slots may be swapped",true);
        row.property("witchery:outputInventory","Outputs shown for empty output slots",true);
        handler.arecipes.clear();handler.arecipes.add(handler.new CachedRecipe(){
            public List<PositionedStack> getIngredients(){return shownInputs;}
            public PositionedStack getResult(){return null;}
            public List<PositionedStack> getOtherStacks(){return shownOutputs;}
        });
        return true;
    }
    static int[][] progressBars(){return new int[][]{{63,3,176,29,39,35,200,0},{28,8,185,-2,12,30,35,3}};}
    private String signature(){
        JsonArray rows=array(); List<ItemStack[]> seen=new ArrayList<>();long comparisons=0;
        for(Object recipe:source){
            Jobs.checkpoint();
            if(recipe==null||!recipe.getClass().getName().equals(REGISTRY+"$DistilleryRecipe"))throw fault("Unknown distillery recipe callback");
            ItemStack[] in=(ItemStack[])field(recipe,"inputs"),out=(ItemStack[])field(recipe,"outputs");
            int jars=(Integer)field(recipe,"jars");
            if(in.length!=2||out.length!=4||in[0]==null||jars<0||jars>64)throw fault("Invalid distillery recipe shape");
            JsonArray a=array(),b=array();int count=0;
            for(ItemStack item:in){
                if(item!=null && (ItemCallbacks.method(item.getItem(),"getHasSubtypes","func_77614_k").getDeclaringClass()!=Item.class
                        || ItemCallbacks.method(item.getItem(),"getDamage","getDamage",ItemStack.class).getDeclaringClass()!=Item.class))
                    throw fault("Distillery requires an adapter for a dynamic metadata predicate");
                a.add(item==null?value(null):object("stack",stack(item),"subtypes",item.getHasSubtypes()));
            }
            for(ItemStack item:out){b.add(stack(item));if(item!=null)count++;}
            if(count==0)throw fault("Distillery has no outputs");
            for(ItemStack[] previous:seen) { Jobs.checkpoint();
                if(++comparisons>262144)throw fault("Distillery registry overlap budget exceeded");
                if((overlap(in[0],previous[0])&&overlap(in[1],previous[1]))
                    ||(overlap(in[0],previous[1])&&overlap(in[1],previous[0])))
                throw fault("Distillery has overlapping first-match recipes; conditional registry priority requires an adapter");
            }
            seen.add(in);
            rows.add(object("inputs",a,"outputs",b,"jars",jars));
        }
        return CanonicalJson.digest(object("recipes",rows,"jar",stack(jar)));
    }
    private static boolean overlap(ItemStack a,ItemStack b){
        if(a==null||b==null)return a==b;
        return a.getItem()==b.getItem()&&(!a.getHasSubtypes()||a.getItemDamage()==b.getItemDamage());
    }
    private static com.google.gson.JsonElement stack(ItemStack item){
        if(item==null)return value(null);
        if(item.getItem()==null||item.stackSize<=0||Item.itemRegistry.getNameForObject(item.getItem())==null)throw fault("Invalid distillery stack");
        return object("id",Item.itemRegistry.getNameForObject(item.getItem()),"meta",item.getItemDamage(),"amount",item.stackSize,"nbt",TypedNbt.encode(item.getTagCompound()));
    }
    private static Jobs.Fault fault(String message){return new Jobs.Fault("recipe_unsupported",message);}
}
