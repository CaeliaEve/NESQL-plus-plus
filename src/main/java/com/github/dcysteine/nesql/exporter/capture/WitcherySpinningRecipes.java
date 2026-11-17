package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonArray;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.init.Items;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Witchery fibre processing has counted fibre and unit modifiers, not workbench consumption. */
final class WitcherySpinningRecipes implements RegistryRecipes {
    private static final String HANDLER="alkalus.main.nei.NEI_Handler_SpinningWheel";
    private static final String REGISTRY="com.emoniph.witchery.crafting.SpinningRecipes";
    private List<?> source;
    private final List<Entry> entries=new ArrayList<>();
    private Object manager;
    private NativeDefinitionGraph definitions;
    private boolean closed;
    private final NativeItemExamples examples=new NativeItemExamples();
    static boolean supportsClass(String name){return name.equals(HANDLER);}
    static boolean supports(ICraftingHandler h){return supportsClass(h.getClass().getName());}
    private static List<?> registry(){
        MagicApi.version("WitcherySpinningRecipes","witchery");MagicApi.version("WitcherySpinningRecipes","WitcheryExtras");
        return (List<?>)registrationAnchors()[1];
    }
    static Object[] registrationAnchors(){
        Object manager=field(type(REGISTRY),null,"INSTANCE");if(manager==null)throw new Jobs.Fault("handler_not_ready","Witchery spinning registry is unavailable");
        if(manager.getClass()!=type(REGISTRY))throw fault("Unadapted spinning registry owner");Object source=field(manager,"recipes");
        if(source==null||source.getClass()!=ArrayList.class||((List<?>)source).size()>4096)throw fault("Invalid spinning source backing/budget");
        return new Object[]{manager,source};
    }
    WitcherySpinningRecipes(TemplateRecipeHandler h){
        if(!supports(h))throw fault("Unknown Witchery spinning handler");
        try{source=registry();manager=registrationAnchors()[0];entries.addAll(read());definitions=new NativeDefinitionGraph(()->new Object[]{registrationAnchors()[1]},NativeDefinitionGraph.shapes(REGISTRY+"$SpinningRecipe","fibre","modifiers","result"));}
        catch(RuntimeException|Error failure){close();throw failure;}
    }
    public int size(){alive();return entries.size();}
    public void verify(){alive();Object[] roots=registrationAnchors();if(roots[0]!=manager||roots[1]!=source)throw changed();definitions.verifyRoots();}
    public void finishVerification(){verify();definitions.verify();examples.verify();}
    public boolean capture(int index,RecipeRow row){
        alive();
        Entry e=entries.get(index);
        for(int i=0;i<index;i++){Entry prior=entries.get(i);if(same(e.fibre,prior.fibre)&&prior.fibre.stackSize<=e.fibre.stackSize&&contains(e.modifiers,prior.modifiers))return row.exclude(object("native",REGISTRY,"reason","earlier_native_fibre_threshold_and_modifier_subset_always_wins","earlierIndex",i));}
        add(row,e.fibre,0,e.fibre.stackSize);
        for(int i=0;i<e.modifiers.length;i++)add(row,e.modifiers[i],i+1,1);
        row.itemOutput(0,e.output.copy(),10000);row.record.addProperty("duration","300");
        JsonArray records=array();for(Entry entry:entries){JsonArray modifiers=array();for(ItemStack item:entry.modifiers)modifiers.add(selector(item));records.add(object("fibre",selector(entry.fibre),"count",entry.fibre.stackSize,"modifiers",modifiers));}
        row.record.add("process",object("kind","witcherySpinning","version","0.24.1","selected",index,"definitions",records,"workTicks",300,"altarPowerBits","3f19999a"));
        return true;
    }
    private void add(RecipeRow row,ItemStack raw,int slot,int amount){
        ItemStack item=raw.copy();item.stackSize=amount;item.setTagCompound(null);
        // isItemEqual compares literal metadata, including 32767; this is not an ore wildcard.
        List<RecipeRow.Ingredient> choices=new ArrayList<>();boolean literal=item.getItemDamage()==32767;
        for(ItemStack shown:examples.copies(item))choices.add(new RecipeRow.Ingredient(shown,amount,false,literal?object("kind","metadata","value",32767,"nbt",true,"absent",array()):object("kind","wildcard","meta",false,"nbt",true)));
        row.itemInput(slot,choices,false);
    }
    private List<Entry> read(){
        List<Entry> result=new ArrayList<>();
        for(Object recipe:source){
            Jobs.checkpoint();Entry entry=new Entry(recipe);
            result.add(entry);
        }return result;
    }
    private static boolean contains(ItemStack[] offered,ItemStack[] required){
        boolean[] used=new boolean[offered.length];for(ItemStack need:required){boolean found=false;for(int i=0;i<offered.length;i++)if(!used[i]&&same(offered[i],need)){used[i]=true;found=true;break;}if(!found)return false;}return true;
    }
    private static boolean same(ItemStack a,ItemStack b){return a.getItem()==b.getItem()&&a.getItemDamage()==b.getItemDamage();}
    private static final class Entry{
        final ItemStack fibre,output;final ItemStack[] modifiers;
        Entry(Object recipe){
            if(recipe==null||!recipe.getClass().getName().equals(REGISTRY+"$SpinningRecipe"))throw fault("Unknown Witchery spinning recipe callback");
            fibre=owned((ItemStack)field(recipe,"fibre"));output=owned((ItemStack)field(recipe,"result"));
            ItemStack[] raw=(ItemStack[])field(recipe,"modifiers");
            if(raw==null||raw.length>3)throw fault("Invalid Witchery spinning modifier count");
            modifiers=new ItemStack[raw.length];
            for(int i=0;i<raw.length;i++)modifiers[i]=owned(raw[i]);
        }
    }
    private static ItemStack owned(ItemStack item){
        NativeStackAudit.audit(item);
        if(item==null||item.getItem()==null||item.stackSize<=0||Item.itemRegistry.getNameForObject(item.getItem())==null)throw fault("Invalid Witchery spinning stack");
        if(ItemCallbacks.method(item.getItem(),"getDamage","getDamage",ItemStack.class).getDeclaringClass()!=Item.class)
            throw fault("Witchery spinning has a dynamic metadata callback");
        return item.copy();
    }
    private static com.google.gson.JsonObject selector(ItemStack item){return object("registry",Item.itemRegistry.getNameForObject(item.getItem()),"metadata",Items.feather.getDamage(item));}
    private void alive(){Jobs.checkpoint();if(closed)throw new Jobs.Fault("capture_closed","Witchery spinning reader is closed");}
    public void close(){closed=true;if(definitions!=null)definitions.close();examples.close();source=null;manager=null;entries.clear();}
    private static Jobs.Fault changed(){return new Jobs.Fault("recipe_changed","Witchery spinning native source changed");}
    private static Jobs.Fault fault(String message){return new Jobs.Fault("recipe_unsupported",message);}
}
