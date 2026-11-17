package com.github.dcysteine.nesql.exporter.capture;

import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonObject;
import java.lang.reflect.Method;
import java.util.*;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Installed MetaGeneratedTool container semantics, without invoking wear or RNG.
 * The native electric branch draws nextInt(0,25) only after successful native use.
 * A concrete preview is not a guarantee that an arbitrary offered tool is usable.
 */
final class GtToolConsumption {
    private static final String TOOL="gregtech.api.items.MetaGeneratedTool";
    private static final Map<String,Integer> COSTS=costs();
    private final ItemStack specimen;
    private final GtItemNormalization normalization;
    private final List<Object> anchors=new ArrayList<>();
    private final JsonObject declaration;
    private final String signature;

    GtToolConsumption(ItemStack specimen) {
        if(specimen==null||specimen.getItem()==null||specimen.stackSize<=0||!type(TOOL).isInstance(specimen.getItem()))
            throw fault("Native GT tool consumption needs a concrete positive tool specimen");
        this.specimen=specimen.copy();normalization=new GtItemNormalization(this.specimen);
        declaration=read(anchors);signature=CanonicalJson.digest(declaration);
    }
    JsonObject declaration(){return new com.google.gson.JsonParser().parse(declaration.toString()).getAsJsonObject();}
    static boolean supports(ItemStack stack) {
        if(stack==null||stack.getItem()==null)return false;
        for(Class<?> owner=stack.getItem().getClass();owner!=null;owner=owner.getSuperclass())if(owner.getName().equals(TOOL))return true;
        return false;
    }
    JsonObject consumption() {
        verify();if(!declaration.get("present").getAsBoolean())return null;
        return object("kind","gtTool","cost",declaration.get("cost"),"owner",declaration.get("costOwner"),
                "sample",object("electric",declaration.get("electric"),"damage",declaration.get("toolDamage"),"limit",declaration.get("toolMaxDamage")));
    }
    void verify() {
        normalization.verify();List<Object> current=new ArrayList<>();JsonObject actual=read(current);
        if(current.size()!=anchors.size()||!signature.equals(CanonicalJson.digest(actual)))throw changed();
        for(int index=0;index<current.size();index++)if(anchors.get(index)!=current.get(index))throw changed();
    }
    private JsonObject read(List<Object> bindings) {
        Item item=specimen.getItem();bindings.add(item);
        requireOwner(item,"hasContainerItem",TOOL,ItemStack.class);
        requireOwner(item,"getContainerItem",TOOL,ItemStack.class);
        requireOwner(item,"getToolStats",TOOL,ItemStack.class);
        requireOwner(item,"doesContainerItemLeaveCraftingGrid",TOOL,ItemStack.class);
        requireOwner(item,"getElectricStats",TOOL,ItemStack.class);
        Object table=field(item,"mToolStats");bindings.add(table);
        if(!(table instanceof Map<?,?>))throw new Jobs.Fault("handler_not_ready","Native GT tool-stat table unavailable");
        int meta=net.minecraft.init.Items.feather.getDamage(specimen);
        Object stats=((Map<?,?>)table).get((short)meta);bindings.add(stats);
        if(meta%2!=0||stats==null)return object("kind","gtToolContainer","present",false,"metadata",meta);
        String costOwner=owner(stats,"getToolDamagePerContainerCraft");
        Integer declared=COSTS.get(costOwner);
        if(declared==null)throw fault("Unadapted native GT crafting wear callback: "+costOwner);
        int cost=((Number)invoke(stats.getClass(),stats,"getToolDamagePerContainerCraft",new Class<?>[0])).intValue();
        if(cost!=declared)throw fault("Pinned GT crafting wear constant changed: "+costOwner);
        String brokenOwner=owner(stats,"getBrokenItem",ItemStack.class);
        if(!brokenOwner.equals("gregtech.common.tools.GTTool")&&!brokenOwner.equals("gregtech.common.tools.ToolKnife"))
            throw fault("Unadapted native GT broken-tool result: "+brokenOwner);
        // Those two installed method bodies are both aconst_null / areturn.
        // Do not execute getContainerItem/doDamage/use or advance a native RNG.
        Long[] electric=(Long[])invoke(item.getClass(),item,"getElectricStats",new Class<?>[]{ItemStack.class},specimen.copy());
        if(electric!=null) {
            if(electric.length!=4)throw fault("Malformed native electric tool statistics");
            for(Long number:electric)if(number==null)throw fault("Null native electric tool statistic");
        }
        ItemStack preview=normalization.apply();
        return object("kind","gtToolContainer","present",true,"metadata",meta,"cost",cost,"costOwner",costOwner,
                "electric",electric!=null,"wearChance",object("numerator","1","denominator",electric==null?"1":"25"),
                "requiresSuccessfulEnergyUse",electric!=null,"electricStats",electric==null?null:array(
                        Long.toString(electric[0]),Long.toString(electric[1]),Long.toString(electric[2]),Long.toString(electric[3])),
                "toolDamage",Long.toString((Long)invoke(type(TOOL),null,"getToolDamage",new Class<?>[]{ItemStack.class},preview.copy())),
                "toolMaxDamage",Long.toString((Long)invoke(type(TOOL),null,"getToolMaxDamage",new Class<?>[]{ItemStack.class},preview.copy())),
                "breakAtNativeLimit",true,"brokenOwner",brokenOwner,"brokenResult",null,
                "returnAmount","1","leaveCraftingGrid",false,"normalization",normalization.declaration());
    }
    private static Map<String,Integer> costs() {
        Map<String,Integer> costs=new HashMap<>();String prefix="gregtech.common.tools.";
        String[] names={"GTTool","ToolButcheryKnife","ToolBuzzSaw","ToolChainsawHV","ToolChainsawLV","ToolChainsawMV",
                "ToolCrowbar","ToolDrillHV","ToolDrillLV","ToolDrillMV","ToolFile","ToolHardHammer","ToolJackHammer",
                "ToolKnife","ToolMortar","ToolRollingPin","ToolSaw","ToolScoop","ToolScrewdriver","ToolScrewdriverLV",
                "ToolSolderingIron","ToolWireCutter","ToolWrenchHV","ToolWrenchMV"};
        int[] values={800,100,100,12800,800,3200,100,12800,100,3200,400,400,3200,100,400,100,200,200,400,200,1000,400,12800,3200};
        for(int index=0;index<names.length;index++)costs.put(prefix+names[index],values[index]);
        costs.put("gtPlusPlus.xmod.gregtech.common.tools.ToolAngleGrinder",400);
        return Collections.unmodifiableMap(costs);
    }
    private static String owner(Object target,String name,Class<?>...arguments) {
        try{
            Method method;
            try{method=target.getClass().getMethod(name,arguments);}
            catch(NoSuchMethodException missing){method=target.getClass().getMethod(MagicApi.runtimeMethod(Item.class,name),arguments);}
            return method.getDeclaringClass().getName();
        }
        catch(NoSuchMethodException failure){throw fault("Missing native GT tool-consumption callback: "+name);}
    }
    private static void requireOwner(Object target,String name,String expected,Class<?>...arguments) {
        if(!owner(target,name,arguments).equals(expected))throw fault("Unadapted native GT tool callback: "+name);
    }
    private static Jobs.Fault fault(String message){return new Jobs.Fault("recipe_unsupported",message);}
    private static Jobs.Fault changed(){return new Jobs.Fault("recipe_changed","Native GT tool consumption configuration changed");}
}
