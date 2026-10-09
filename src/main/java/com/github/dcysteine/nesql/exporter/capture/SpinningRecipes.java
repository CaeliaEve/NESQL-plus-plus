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

/** Witching Gadgets' unit-consumption machine, with its greedy predicate boundary retained. */
final class SpinningRecipes implements RegistryRecipes {
    private static final String HANDLER="witchinggadgets.client.nei.NEISpinningWheelHandler";
    private static final String RECIPE="witchinggadgets.common.util.recipe.SpinningRecipe";
    private final TemplateRecipeHandler handler;
    private final List<?> source;
    private final List<Entry> entries;
    private final String signature;
    static boolean supports(ICraftingHandler h){return h.getClass().getName().equals(HANDLER);}
    SpinningRecipes(TemplateRecipeHandler h){this(h,registry());}
    private static List<?> registry(){
        version("WitchingGadgets","1.7.25-GTNH");
        return (List<?>)field(type(RECIPE),null,"recipeList");
    }
    SpinningRecipes(TemplateRecipeHandler h,List<?> source){
        if(!supports(h)||source==null||source.size()>65536)throw fault("Invalid spinning registry");
        handler=h;this.source=source;entries=read();signature=signature(entries);
    }
    public int size(){return entries.size();}
    public void verify(){if(!signature.equals(signature(read())))throw new Jobs.Fault("environment_changed","Spinning registry changed");}
    public boolean capture(int index,RecipeRow row){
        Entry entry=entries.get(index);List<PositionedStack> positions=new ArrayList<>();
        for(int slot=0;slot<entry.groups.size();slot++){
            List<RecipeRow.Ingredient> choices=new ArrayList<>();List<ItemStack> display=new ArrayList<>();
            for(ItemStack raw:entry.groups.get(slot)){
                ItemStack item=raw.copy();item.stackSize=1;item.setTagCompound(null);
                boolean wildcard=item.getItemDamage()==32767;
                if(wildcard){
                    ItemStack example=null;
                    for(ItemStack candidate:codechicken.nei.ItemList.items){
                        Jobs.checkpoint();
                        if(candidate!=null&&candidate.getItem()==item.getItem()&&candidate.getItemDamage()>=0&&candidate.getItemDamage()!=32767){example=candidate;break;}
                    }
                    if(example==null)throw fault("Spinning wildcard has no concrete NEI display example");
                    item.setItemDamage(example.getItemDamage());
                }
                display.add(item);choices.add(new RecipeRow.Ingredient(item,1,false,object("kind","wildcard","meta",wildcard,"nbt",true)));
            }
            PositionedStack pos=new PositionedStack(display.toArray(new ItemStack[0]),21,9+18*slot,false);
            positions.add(pos);row.itemInput(pos,slot,choices,false);
        }
        PositionedStack output=new PositionedStack(entry.output.copy(),126,46,false);
        row.itemOutput(output,0,entry.output.copy(),10000);
        // The native counter increments from 0 through 120, then completes on the next tick.
        row.record.addProperty("duration","121");
        row.property("witchinggadgets:outputInventory","Output shown for an empty output slot",true);
        handler.arecipes.clear();handler.arecipes.add(handler.new CachedRecipe(){
            public List<PositionedStack> getIngredients(){return positions;}
            public PositionedStack getResult(){return output;}
        });return true;
    }
    private List<Entry> read(){
        List<Entry> result=new ArrayList<>();int work=0;
        for(Object recipe:source){
            Jobs.checkpoint();Entry entry=new Entry(recipe);
            // Preserve registry first-match semantics rather than inventing independent outputs.
            for(Entry previous:result)if(previous.groups.size()==entry.groups.size()){
                if(++work>262144)throw fault("Spinning registry overlap budget exceeded");
                if(compatible(entry.groups,previous.groups,0,new boolean[entry.groups.size()]))
                    throw fault("Spinning registry has overlapping first-match recipes");
            }
            result.add(entry);
        }return result;
    }
    private static boolean compatible(List<List<ItemStack>> a,List<List<ItemStack>> b,int slot,boolean[] used){
        if(slot==a.size())return true;
        for(int i=0;i<b.size();i++)if(!used[i]&&overlap(a.get(slot),b.get(i))){
            used[i]=true;boolean found=compatible(a,b,slot+1,used);used[i]=false;if(found)return true;
        }return false;
    }
    private static final class Entry {
        final List<List<ItemStack>> groups=new ArrayList<>();final ItemStack output;final String signature;
        Entry(Object recipe){
            if(recipe==null||!recipe.getClass().getName().equals(RECIPE))throw fault("Unknown spinning recipe callback");
            Object[] raw=(Object[])field(recipe,"input");
            if(raw.length<1||raw.length>5)throw fault("Invalid spinning input count");
            JsonArray inputs=array();List<Boolean> listGroups=new ArrayList<>();
            for(Object input:raw){
                boolean list=input instanceof ArrayList;
                if(!(input instanceof ItemStack)&&!list)throw fault("Unknown spinning ingredient predicate");
                List<?> values=list?(List<?>)input:Collections.singletonList(input);
                if(values.isEmpty()||values.size()>65536)throw fault("Empty or oversized spinning ingredient group");
                List<ItemStack> group=new ArrayList<>();JsonArray encoded=array();
                for(Object value:values){
                    Jobs.checkpoint();if(!(value instanceof ItemStack))throw fault("Invalid spinning ingredient");
                    ItemStack item=(ItemStack)value;
                    encoded.add(stack(item));
                    if(ItemCallbacks.method(item.getItem(),"getDamage","getDamage",ItemStack.class).getDeclaringClass()!=Item.class)
                        throw fault("Spinning has a dynamic metadata predicate");
                    group.add(item.copy());
                }
                // Lists do not break the outer native removal loop. Even equal lists are unsafe.
                for(int i=0;i<groups.size();i++)if(overlap(group,groups.get(i))
                        &&(list||listGroups.get(i)||!samePattern(group.get(0),groups.get(i).get(0))))
                    throw fault("Spinning greedy ingredient predicates overlap");
                groups.add(group);listGroups.add(list);inputs.add(encoded);
            }
            ItemStack nativeOutput=(ItemStack)field(recipe,"output");
            if(nativeOutput==null||nativeOutput.stackSize<=0)throw fault("Spinning has no fixed output");
            output=nativeOutput.copy();signature=CanonicalJson.digest(object("inputs",inputs,"output",stack(output)));
        }
    }
    private static boolean overlap(List<ItemStack>a,List<ItemStack>b){
        long work=0;for(ItemStack x:a)for(ItemStack y:b){
            Jobs.checkpoint();if(++work>262144)throw fault("Spinning predicate overlap budget exceeded");
            if(x.getItem()==y.getItem()&&(x.getItemDamage()==32767||y.getItemDamage()==32767||x.getItemDamage()==y.getItemDamage()))return true;
        }return false;
    }
    private static boolean samePattern(ItemStack a,ItemStack b){return a.getItem()==b.getItem()&&a.getItemDamage()==b.getItemDamage();}
    private static com.google.gson.JsonObject stack(ItemStack item){
        if(item==null||item.getItem()==null||Item.itemRegistry.getNameForObject(item.getItem())==null)throw fault("Invalid spinning stack");
        return object("item",Item.itemRegistry.getNameForObject(item.getItem()),"meta",item.getItemDamage(),"amount",item.stackSize,"nbt",TypedNbt.encode(item.getTagCompound()));
    }
    private static String signature(List<Entry> entries){JsonArray values=array();for(Entry e:entries)values.add(value(e.signature));return CanonicalJson.digest(values);}
    private static Jobs.Fault fault(String message){return new Jobs.Fault("recipe_unsupported",message);}
}
