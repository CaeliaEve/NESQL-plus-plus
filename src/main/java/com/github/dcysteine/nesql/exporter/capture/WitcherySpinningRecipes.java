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

/** Witchery fibre processing has counted fibre and unit modifiers, not workbench consumption. */
final class WitcherySpinningRecipes implements RegistryRecipes {
    private static final String HANDLER="alkalus.main.nei.NEI_Handler_SpinningWheel";
    private static final String REGISTRY="com.emoniph.witchery.crafting.SpinningRecipes";
    private final TemplateRecipeHandler handler;
    private final List<?> source;
    private final List<Entry> entries;
    private final String signature;
    static boolean supports(ICraftingHandler h){return h.getClass().getName().equals(HANDLER);}
    WitcherySpinningRecipes(TemplateRecipeHandler h){this(h,registry());}
    private static List<?> registry(){
        version("witchery","0.24.1");version("WitcheryExtras","1.3.8");
        return (List<?>)field(invoke(type(REGISTRY),null,"instance",new Class<?>[0]),"recipes");
    }
    WitcherySpinningRecipes(TemplateRecipeHandler h,List<?> source){
        if(!supports(h)||source==null||source.size()>65536)throw fault("Invalid Witchery spinning registry");
        this.handler=h;this.source=source;entries=read();signature=signature(entries);
    }
    public int size(){return entries.size();}
    public void verify(){if(!signature.equals(signature(read())))throw new Jobs.Fault("environment_changed","Witchery spinning registry changed");}
    public boolean capture(int index,RecipeRow row){
        Entry e=entries.get(index);List<PositionedStack> inputs=new ArrayList<>();
        add(row,inputs,e.fibre,0,e.fibre.stackSize,51,9);
        for(int i=0;i<e.modifiers.length;i++)add(row,inputs,e.modifiers[i],i+1,1,51+i*18,42);
        PositionedStack output=new PositionedStack(e.output.copy(),113,9,false);
        row.itemOutput(output,0,e.output.copy(),10000);row.record.addProperty("duration","300");
        row.property("witchery:altarPowerPerWorkTick","Altar power per successful work tick",0.6f);
        row.property("witchery:powerRequired","Requires a reachable altar power source",true);
        row.property("witchery:extraModifiers","Unmatched additional modifier slots are retained",true);
        row.property("witchery:outputInventory","Output shown for an empty output slot",true);
        handler.arecipes.clear();handler.arecipes.add(handler.new CachedRecipe(){
            public List<PositionedStack> getIngredients(){return inputs;}
            public PositionedStack getResult(){return output;}
        });return true;
    }
    private static void add(RecipeRow row,List<PositionedStack> inputs,ItemStack raw,int slot,int amount,int x,int y){
        ItemStack item=raw.copy();item.stackSize=amount;item.setTagCompound(null);
        // isItemEqual compares literal metadata, including 32767; this is not an ore wildcard.
        if(item.getItemDamage()==32767)throw fault("Witchery spinning literal sentinel input needs a verified display example");
        PositionedStack pos=new PositionedStack(item,x,y,false);inputs.add(pos);
        row.itemInput(pos,slot,amount,false,false,object("kind","wildcard","meta",false,"nbt",true));
    }
    static int[][] progressBars(){return new int[][]{{51,25,176,0,14,14,48,7}};}
    private List<Entry> read(){
        List<Entry> result=new ArrayList<>();long work=0;
        for(Object recipe:source){
            Jobs.checkpoint();Entry entry=new Entry(recipe);
            for(Entry previous:result){
                if(++work>262144)throw fault("Witchery spinning registry overlap budget exceeded");
                if(same(entry.fibre,previous.fibre)&&unionSize(entry.modifiers,previous.modifiers)<=3)
                    throw fault("Witchery spinning has overlapping first-match recipes");
            }
            result.add(entry);
        }return result;
    }
    private static int unionSize(ItemStack[] a,ItemStack[] b){
        boolean[] used=new boolean[b.length];int count=a.length;
        for(ItemStack item:a)for(int i=0;i<b.length;i++)if(!used[i]&&same(item,b[i])){used[i]=true;break;}
        for(boolean matched:used)if(!matched)count++;
        return count;
    }
    private static boolean same(ItemStack a,ItemStack b){return a.getItem()==b.getItem()&&a.getItemDamage()==b.getItemDamage();}
    private static final class Entry{
        final ItemStack fibre,output;final ItemStack[] modifiers;final String signature;
        Entry(Object recipe){
            if(recipe==null||!recipe.getClass().getName().equals(REGISTRY+"$SpinningRecipe"))throw fault("Unknown Witchery spinning recipe callback");
            fibre=owned((ItemStack)field(recipe,"fibre"));output=owned((ItemStack)field(recipe,"result"));
            ItemStack[] raw=(ItemStack[])field(recipe,"modifiers");
            if(raw==null||raw.length>3)throw fault("Invalid Witchery spinning modifier count");
            modifiers=new ItemStack[raw.length];JsonArray list=array();
            for(int i=0;i<raw.length;i++){modifiers[i]=owned(raw[i]);list.add(stack(modifiers[i]));}
            signature=CanonicalJson.digest(object("fibre",stack(fibre),"modifiers",list,"output",stack(output)));
        }
    }
    private static ItemStack owned(ItemStack item){
        if(item==null||item.getItem()==null||item.stackSize<=0||Item.itemRegistry.getNameForObject(item.getItem())==null)throw fault("Invalid Witchery spinning stack");
        if(ItemCallbacks.method(item.getItem(),"getDamage","getDamage",ItemStack.class).getDeclaringClass()!=Item.class)
            throw fault("Witchery spinning has a dynamic metadata callback");
        return item.copy();
    }
    private static com.google.gson.JsonObject stack(ItemStack item){return object("item",Item.itemRegistry.getNameForObject(item.getItem()),"meta",item.getItemDamage(),"amount",item.stackSize,"nbt",TypedNbt.encode(item.getTagCompound()));}
    private static String signature(List<Entry> values){JsonArray rows=array();for(Entry e:values)rows.add(value(e.signature));return CanonicalJson.digest(rows);}
    private static Jobs.Fault fault(String message){return new Jobs.Fault("recipe_unsupported",message);}
}
