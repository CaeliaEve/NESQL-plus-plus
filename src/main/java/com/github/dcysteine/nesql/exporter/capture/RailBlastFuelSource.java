package com.github.dcysteine.nesql.exporter.capture;

import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.*;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Native allowed-fuel definitions, never invokes or fills the lazy getFuels cache. */
final class RailBlastFuelSource implements AutoCloseable {
    private final Object manager, table, backing;
    private final List<?> fuels;
    private final RailBlastFuelDefaults defaults;
    private final IdentityHashMap<Object,Item> items=new IdentityHashMap<>();
    private final NativeDefinitions definitions;
    private boolean closed;

    RailBlastFuelSource(Object manager) {
        MagicApi.version("RailBlastFuelSource","Railcraft");
        if(manager==null || !manager.getClass().getName().equals("mods.railcraft.common.util.crafting.BlastFurnaceCraftingManager"))
            throw fault("Unadapted native Railcraft blast manager");
        this.manager=manager;table=field(manager,"fuels");
        defaults=table==null?RailBlastFuelDefaults.read():null;
        backing=table==null?null:backing(table);fuels=table==null?defaults.fuels:(List<?>)table;
        if(fuels.size()>262144)throw fault("Native Railcraft fuel table exceeds budget");
        for(Object raw:fuels){
            ItemStack stack=stack(raw);items.put(raw,stack.getItem());
        }
        definitions=new NativeDefinitions("Railcraft blast fuel",fuels,this::current,this::definition);
    }
    void verify(){if(closed)throw new Jobs.Fault("capture_closed","Railcraft blast fuel reader is closed");definitions.finishVerification();}
    int size(){verify();return fuels.size();}
    JsonArray templates(){
        verify();JsonArray result=array();for(Object fuel:fuels)result.add(definition(fuel));verify();return result;
    }
    JsonObject declaration(Facts facts){
        verify();JsonArray allowed=array();
        for(Object raw:fuels){
            ItemStack stack=(ItemStack)raw;JsonObject definition=definition(raw);
            ItemStack display=stack.copy();
            if(net.minecraft.init.Items.feather.getDamage(stack)==-1)
                cpw.mods.fml.relauncher.ReflectionHelper.setPrivateValue(ItemStack.class,display,-1,"itemDamage","field_77991_e");
            allowed.add(object("item",facts.item(RailRecipes.example(display)),
                    "metadata",net.minecraft.init.Items.feather.getDamage(stack),"subtypes",definition.get("subtypes"),
                    "templateAmount",Integer.toString(stack.stackSize)));
        }
        JsonObject result=object("allowed",allowed,"filter","nativeItemSubtypeExactNbtEitherWildcard",
                "cache","semiStrictOfferedNbtAbsentReusesLastItemMetaValue",
                "burn","scaffoldZeroBlaze1000LavaAmountScrapZeroNativeFurnaceExceptionZero",
                "tick","masterServerBurnBudgetMinusFiveEvenIdle",
                "reload","positiveInputRecipeOutputRoomUnpausedBudgetAtMostTenPositiveBurnAddSignedInt",
                "depletion","nonPositiveNullUnitNativeContainerOtherwiseDecrement");
        verify();return result;
    }
    private List<?> current(){
        if(field(manager,"fuels")!=table || table!=null&&backing(table)!=backing)throw changed();
        if(defaults!=null)defaults.verify();
        return fuels;
    }
    private JsonObject definition(Object raw){
        ItemStack stack=stack(raw);
        if(items.get(raw)!=stack.getItem())throw changed();
        Item item=stack.getItem();
        audit(item);
        return object("template",RecipeRow.stackEvidence(stack),"subtypes",item.getHasSubtypes());
    }
    static void audit(Item item){
        if(ItemCallbacks.method(item,"getDamage","getDamage",ItemStack.class).getDeclaringClass()!=Item.class
                ||ItemCallbacks.method(item,"getHasSubtypes","func_77614_k").getDeclaringClass()!=Item.class
                ||ItemCallbacks.method(item,"setDamage","setDamage",ItemStack.class,int.class).getDeclaringClass()!=Item.class)
            throw fault("Native Railcraft fuel filter needs an audited metadata/subtype callback");
        try{if(item.getClass().getMethod("hashCode").getDeclaringClass()!=Object.class)throw fault("Unadapted native fuel registry hash callback");}
        catch(NoSuchMethodException failure){throw fault("Missing native fuel registry hash method");}
        String registry=Item.itemRegistry.getNameForObject(item);
        if(registry==null || Item.itemRegistry.getObject(registry)!=item)throw fault("Unregistered native Railcraft fuel template");
    }
    static Object[] registrationAnchors(Object manager){
        Object table=field(manager,"fuels");
        return table==null?RailBlastFuelDefaults.read().anchors.toArray():new Object[]{table};
    }
    private static ItemStack stack(Object raw){
        if(raw==null||raw.getClass()!=ItemStack.class)throw fault("Unadapted native Railcraft fuel stack");
        ItemStack stack=(ItemStack)raw;
        if(stack.getItem()==null||stack.stackSize<=0||net.minecraft.init.Items.feather.getDamage(stack)<-1)
            throw fault("Invalid native Railcraft fuel template");
        return stack;
    }
    private static Object backing(Object table){
        if(table.getClass()==ArrayList.class)return table;
        if(!table.getClass().getName().equals("java.util.Collections$UnmodifiableRandomAccessList"))throw fault("Unadapted native Railcraft fuel list");
        Object list=field(table,"list");
        if(list==null || !list.getClass().getName().equals("mods.railcraft.common.util.crafting.BlastFurnaceCraftingManager$1"))
            throw fault("Unadapted native Railcraft fuel backing list");
        return list;
    }
    public void close(){if(closed)return;closed=true;definitions.close();items.clear();}
    private static Jobs.Fault fault(String message){return new Jobs.Fault("recipe_unsupported",message);}
    private static Jobs.Fault changed(){return new Jobs.Fault("recipe_changed","Native Railcraft blast fuel table changed");}
}
