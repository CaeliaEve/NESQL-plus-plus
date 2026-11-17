package com.github.dcysteine.nesql.exporter.capture;

import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.lang.reflect.Method;
import java.util.*;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Read-only native normalization binding for one concrete result-tag state.
 * Uses the installed 5.09.51.482 field names and callbacks, not current GT source names.
 * Unknown behaviour/tool callbacks fail before invocation. This binding is one stage
 * of GT crafting; result-state transport belongs to GtCraftingTransport.
 */
final class GtItemNormalization implements GtCraftingBinding {
    private static final Set<String> BOOLEAN_CALLBACKS = new HashSet<>(Arrays.asList(
            "gregtech.common.tools.GTTool", "gregtech.common.tools.ToolButcheryKnife",
            "gregtech.common.tools.ToolChainsawLV", "gregtech.common.tools.ToolCrowbar",
            "gregtech.common.tools.ToolFile", "gregtech.common.tools.ToolHardHammer",
            "gregtech.common.tools.ToolKnife", "gregtech.common.tools.ToolMortar",
            "gregtech.common.tools.ToolScrewdriver", "gregtech.common.tools.ToolSoftMallet",
            "gregtech.common.tools.ToolSolderingIron",
            "gtPlusPlus.xmod.gregtech.common.tools.ToolAngleGrinder"));
    private final ItemStack specimen;
    private final List<Object> anchors = new ArrayList<>();
    private final String signature;
    private final JsonObject declaration;

    GtItemNormalization(ItemStack specimen) {
        if (specimen == null || specimen.getItem() == null || specimen.stackSize <= 0)
            throw fault("Native GT normalization requires a positive owned result specimen");
        this.specimen = specimen.copy();
        declaration = read(anchors);
        signature = CanonicalJson.digest(declaration);
    }

    JsonObject declaration() { return new com.google.gson.JsonParser().parse(declaration.toString()).getAsJsonObject(); }

    public Item item(){return specimen.getItem();}
    public String key(){return "normalization:"+CanonicalJson.digest(RecipeRow.stackEvidence(specimen));}
    public boolean mutable(){return !declaration.get("kind").getAsString().equals("identity");}
    public void verify() {
        List<Object> current = new ArrayList<>();
        JsonObject actual = read(current);
        if (current.size() != anchors.size() || !CanonicalJson.digest(actual).equals(signature)) throw changed();
        for (int index = 0; index < current.size(); index++) if (current.get(index) != anchors.get(index)) throw changed();
    }

    ItemStack apply() {
        verify(); ItemStack owned = specimen.copy();
        ItemStack result = (ItemStack)invoke(type("gregtech.api.util.GTUtility"),null,"updateItemStack",new Class<?>[]{ItemStack.class},owned);
        if (result != owned) throw fault("Pinned GT normalization replaced its owned result reference");
        verify(); return owned;
    }

    private JsonObject read(List<Object> bindings) {
        Item item = specimen.getItem(); bindings.add(item);
        if (!type("gregtech.api.items.GTGenericItem").isInstance(item)) return object("kind","identity");
        Class<?> owner = method(item,"isItemStackUsable",ItemStack.class).getDeclaringClass();
        String name = owner.getName();
        if (name.equals("gregtech.api.items.GTGenericItem")) return object("kind","identity","callback",name);
        if (name.equals("gregtech.api.items.MetaBaseItem")) return behaviors(item,bindings);
        if (name.equals("gregtech.common.items.MetaGeneratedItem02")) {
            JsonObject base = behaviors(item,bindings);
            Object array = field(type("gregtech.api.GregTechAPI"),null,"sGeneratedMaterials"); bindings.add(array);
            if (!(array instanceof Object[])) throw fault("Malformed native GT generated material table");
            int meta = Items.feather.getDamage(specimen), index = meta % 1000;
            if (index < 0 || index >= ((Object[])array).length) throw fault("Native GT generated material lookup is out of range");
            Object material = ((Object[])array)[index]; bindings.add(material);
            JsonObject enchantment = null;
            if (material != null) enchantment = materialEnchantment(material,bindings);
            return object("kind","meta02","callback",name,"metadata",meta,
                    "withinInitializationRange",meta>=25000&&meta<27000,"material",material==null?null:(String)field(material,"mName"),
                    "enchantment",enchantment,"behaviors",base);
        }
        if (name.equals("gregtech.api.items.MetaGeneratedTool")) return tool(item,bindings);
        throw fault("Unadapted native GT normalization callback: "+name);
    }

    private JsonObject behaviors(Item item,List<Object> bindings) {
        Object raw = field(item,"mItemBehaviors"); bindings.add(raw);
        if (!(raw instanceof Map<?,?>)) throw fault("Native GT behavior table is unavailable");
        Object list = ((Map<?,?>)raw).get((short)Items.feather.getDamage(specimen)); bindings.add(list);
        JsonArray callbacks = array();
        if (list != null) {
            if (!(list instanceof ArrayList<?>) || ((List<?>)list).size()>4096) throw fault("Malformed native GT behavior list");
            for (Object behavior : (List<?>)list) {
                Jobs.checkpoint(); if (behavior==null) throw fault("Null native GT behavior"); bindings.add(behavior);
                Class<?> erased = method(behavior,"isItemStackUsable",Item.class,ItemStack.class).getDeclaringClass();
                Class<?> typed = method(behavior,"isItemStackUsable",type("gregtech.api.items.MetaBaseItem"),ItemStack.class).getDeclaringClass();
                if (!erased.getName().equals("gregtech.common.items.behaviors.BehaviourNone") || erased!=typed)
                    throw fault("Unadapted native GT usability behavior: "+behavior.getClass().getName());
                callbacks.add(object("class",behavior.getClass().getName(),"owner",erased.getName()));
            }
        }
        return object("kind","identityBehaviors","callbacks",callbacks);
    }

    private JsonObject tool(Item item,List<Object> bindings) {
        Object raw = field(item,"mToolStats"); bindings.add(raw);
        if (!(raw instanceof Map<?,?>)) throw fault("Native GT tool-stat table is unavailable");
        int meta = Items.feather.getDamage(specimen);
        Object stats = ((Map<?,?>)raw).get((short)meta); bindings.add(stats);
        if (meta%2!=0 || stats==null) return object("kind","invalidTool","metadata",meta,"remove","ench");
        Enchantment[] enchantments = (Enchantment[])toolCallback(stats,"getEnchantments",true);
        int[] levels = (int[])toolCallback(stats,"getEnchantmentLevels",true);
        if (enchantments==null || levels==null || enchantments.length!=levels.length || levels.length>4096)
            throw fault("Malformed native GT tool enchantment arrays");
        JsonArray added = array();
        for(int index=0;index<levels.length;index++){
            Enchantment enchantment=enchantments[index]; if(enchantment==null)throw fault("Null native GT tool enchantment");
            bindings.add(enchantment); added.add(object("id",enchantment.effectId,"level",levels[index],"type",enchantment.type.name()));
        }
        Object material = invoke(type("gregtech.api.items.MetaGeneratedTool"),null,"getPrimaryMaterial",new Class<?>[]{ItemStack.class},specimen.copy());
        if(material==null)throw fault("Native GT primary material is unavailable"); bindings.add(material);
        JsonObject materialRule=materialEnchantment(material,bindings);
        HashMap<Integer,Integer> effective=new HashMap<>();
        Enchantment nativeMaterial=(Enchantment)field(material,"mEnchantmentTools");
        int materialLevel=((Number)field(material,"mEnchantmentToolsLevel")).intValue();
        if(nativeMaterial!=null){
            effective.put(nativeMaterial.effectId,materialLevel);
            if(nativeMaterial==Enchantment.fortune)effective.put(Enchantment.looting.effectId,materialLevel);
            if(nativeMaterial==Enchantment.knockback)effective.put(Enchantment.power.effectId,materialLevel);
            if(nativeMaterial==Enchantment.fireAspect)effective.put(Enchantment.flame.effectId,materialLevel);
        }
        for(int index=0;index<levels.length;index++)if(levels[index]>0){
            int id=enchantments[index].effectId;Integer previous=effective.get(id);
            effective.put(id,previous==null?levels[index]:previous==levels[index]?previous+1:Math.max(previous,levels[index]));
        }
        // Native short-circuiting never reads the mod enchantment for an empty map
        // or a map containing only the unconditional 33 / high-level 20 bypasses.
        boolean needsRadioactivity=false;
        for(Map.Entry<Integer,Integer> entry:effective.entrySet())
            if(entry.getKey()!=33 && !(entry.getKey()==20 && entry.getValue()>2))needsRadioactivity=true;
        Enchantment radioactive=null;
        if(needsRadioactivity){
            // Observe the already-registered instance; no mod initialization/registration.
            bindings.add(Enchantment.enchantmentsList);
            for(Enchantment enchantment:Enchantment.enchantmentsList)if(enchantment!=null
                    && enchantment.getClass().getName().equals("gregtech.api.enchants.EnchantmentRadioactivity")){
                if(radioactive!=null)throw fault("Ambiguous native GT radioactivity enchantment");radioactive=enchantment;
            }
            if(radioactive==null)throw new Jobs.Fault("handler_not_ready","Native GT radioactivity enchantment is not registered");
            bindings.add(radioactive);
        }
        JsonArray filters=array();
        for(Map.Entry<Integer,Integer> entry:effective.entrySet()){
            int id=entry.getKey(),level=entry.getValue();
            boolean bypass=id==33||id==20&&level>2||radioactive!=null&&id==radioactive.effectId;
            Enchantment registered=id>=0&&id<Enchantment.enchantmentsList.length?Enchantment.enchantmentsList[id]:null;
            bindings.add(registered);
            if(!bypass&&registered==null)throw new Jobs.Fault("handler_not_ready","Native tool enchantment filter is unregistered: "+id);
            filters.add(object("id",id,"level",level,"bypass",bypass,"type",registered==null?null:registered.type.name()));
        }
        return object("kind","tool","metadata",meta,"statClass",stats.getClass().getName(),
                "primaryMaterial",(String)field(material,"mName"),"materialEnchantment",materialRule,"filters",filters,
                "materialQuality",((Number)field(material,"mToolQuality")).intValue(),
                "levelRule",method(stats,"getEnchantmentLevels",ItemStack.class).getDeclaringClass().getName().equals("gregtech.common.tools.ToolButcheryKnife")?"butcheryMaterialQuality":"empty",
                "enchantments",added,"weapon",toolCallback(stats,"isWeapon",false),
                "ranged",toolCallback(stats,"isRangedWeapon",false),"mining",toolCallback(stats,"isMiningTool",false),
                "radioactivity",radioactive==null?null:radioactive.effectId);
    }

    private Object toolCallback(Object stats,String name,boolean stack) {
        Class<?>[] arguments=stack?new Class<?>[]{ItemStack.class}:new Class<?>[0];
        Method callback=method(stats,name,arguments);String owner=callback.getDeclaringClass().getName();
        // All 43 installed tool classes were inspected. Boolean owners below have
        // constant bodies. Butchery levels depend on native primary material quality;
        // they are read on an owned concrete stack and bind that material/configuration.
        boolean supported=stack?(owner.equals("gregtech.common.tools.GTTool")||owner.equals("gregtech.common.tools.ToolButcheryKnife"))
                :BOOLEAN_CALLBACKS.contains(owner);
        if(!supported)
            throw fault("Unadapted native GT tool-stat callback: "+owner+"."+name);
        return invoke(callback.getDeclaringClass(),stats,name,arguments,stack?new Object[]{specimen.copy()}:new Object[0]);
    }

    private static JsonObject materialEnchantment(Object material,List<Object> bindings) {
        Object raw=field(material,"mEnchantmentTools");bindings.add(raw);
        if(raw==null)return null;
        if(!(raw instanceof Enchantment))throw fault("Malformed native material enchantment");
        Enchantment enchantment=(Enchantment)raw;
        return object("id",enchantment.effectId,"type",enchantment.type.name(),"level",((Number)field(material,"mEnchantmentToolsLevel")).intValue(),
                "fortune",enchantment==Enchantment.fortune,"knockback",enchantment==Enchantment.knockback,"fireAspect",enchantment==Enchantment.fireAspect);
    }
    private static Method method(Object target,String name,Class<?>...args) {
        try{return target.getClass().getMethod(name,args);}
        catch(NoSuchMethodException failure){throw fault("Missing native normalization callback: "+target.getClass().getName()+"."+name);}
    }
    private static Jobs.Fault fault(String message){return new Jobs.Fault("recipe_unsupported",message);}
    private static Jobs.Fault changed(){return new Jobs.Fault("recipe_changed","Native GT normalization callbacks/configuration changed");}
}
