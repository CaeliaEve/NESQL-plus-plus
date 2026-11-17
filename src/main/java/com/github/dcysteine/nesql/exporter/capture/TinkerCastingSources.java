package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;

import com.github.dcysteine.nesql.exporter.task.Jobs;
import cpw.mods.fml.common.eventhandler.Event;
import java.lang.reflect.Method;
import java.util.*;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.init.Items;
import net.minecraft.item.*;
import net.minecraft.nbt.*;

/** Pinned casting listener laws over owned event stacks; never invokes a listener. */
final class TinkerCastingSources implements AutoCloseable {
    private static final String REGISTRY="tconstruct.library.TConstructRegistry";
    private static final String WEAPONS="tconstruct.weaponry.TinkerWeaponry";
    private static final String RESTRICTIONS="iguanaman.iguanatweakstconstruct.restriction.RestrictionHelper";
    private static final String EXTRA="com.rwtema.extrautils.ExtraUtils";
    private final Map<String,NativeDefinitionGraph> graphs=new LinkedHashMap<>();
    private boolean closed;

    void bind(String owner){
        alive();if(graphs.containsKey(owner))return;
        Map<String,String[]> shapes=new HashMap<>();
        shapes.put("tconstruct.library.tools.ToolMaterial",new String[]{"materialName"});
        shapes.put("tconstruct.library.tools.ArrowMaterial",new String[0]);
        shapes.put("tconstruct.library.tools.BowMaterial",new String[0]);
        shapes.put("mantle.utils.ItemMetaWrapper",new String[]{"item","meta"});
        shapes.put("java.util.Collections$SingletonList",new String[]{"element"});
        shapes.put("java.util.Collections$EmptyList",new String[0]);
        shapes.put("java.util.UUID",new String[]{"mostSigBits","leastSigBits"});
        shapes.put("net.minecraft.entity.ai.attributes.RangedAttribute",new String[]{"unlocalizedName"});
        graphs.put(owner,new NativeDefinitionGraph(()->roots(owner),shapes));
    }
    private static Object[] roots(String owner){
        switch(owner){
            case "materials":return new Object[]{table("toolMaterials")};
            case "weaponry":return new Object[]{table("arrowMaterials"),table("bowMaterials"),
                field(type(WEAPONS),null,"arrowhead"),field(type(WEAPONS),null,"partBowLimb"),field(type(WEAPONS),null,"partCrossbowLimb")};
            case "restrictions":return new Object[]{restrictions(),field(type(WEAPONS),null,"partBolt")};
            case "extra":return new Object[]{field(type(EXTRA),null,"tcon_unstable_material_id"),field(type(EXTRA),null,"tcon_bedrock_material_id"),
                field(type("com.rwtema.extrautils.modintegration.TConEvents"),null,"uuid"),SharedMonsterAttributes.movementSpeed};
            default:throw fault("Unknown casting dependency owner: "+owner);
        }
    }
    private static Map<?,?> table(String name){
        Object map=field(type(REGISTRY),null,name);
        if(map==null||map.getClass()!=HashMap.class||((Map<?,?>)map).size()>65536)throw fault("Unadapted native casting material table: "+name);
        return (Map<?,?>)map;
    }
    private static Map<?,?> restrictions(){
        Object raw=field(type(RESTRICTIONS),null,"patternMaterialLookup");
        if(raw==null||raw.getClass()!=HashMap.class||((Map<?,?>)raw).size()>65536)throw fault("Unadapted native pattern restriction table");
        return (Map<?,?>)raw;
    }
    int material(ItemStack output){
        alive();if(output==null||!type("tconstruct.library.util.IToolPart").isInstance(output.getItem()))return -1;
        NativeStackAudit.audit(output);
        try{
            Method getter=output.getItem().getClass().getMethod("getMaterialID",ItemStack.class);
            String owner=getter.getDeclaringClass().getName();
            if((!owner.equals("tconstruct.tools.items.ToolPart")&&!owner.equals("tconstruct.library.tools.DynamicToolPart"))
                ||ItemCallbacks.method(output.getItem(),"getDamage","getDamage",ItemStack.class).getDeclaringClass()!=Item.class)
                throw fault("Unadapted casting material predicate: "+owner);
            bind("materials");int metadata=Items.feather.getDamage(output);
            // Both native getter owners test containsKey, even for a null value.
            return table("toolMaterials").containsKey(metadata)?metadata:-1;
        }catch(NoSuchMethodException error){throw fault("Missing tool-part material getter");}
    }
    void weaponry(Event event){
        bind("weaponry");Object recipe=field(event,"recipe");ItemStack output=recipe==null?null:(ItemStack)field(recipe,"output");
        if(output==null||!type("tconstruct.library.util.IToolPart").isInstance(output.getItem()))return;
        int material=material(output);Object item=output.getItem();
        if(item==field(type(WEAPONS),null,"arrowhead")&&table("arrowMaterials").get(material)==null)event.setResult(Event.Result.DENY);
        if((item==field(type(WEAPONS),null,"partBowLimb")||item==field(type(WEAPONS),null,"partCrossbowLimb"))
            &&table("bowMaterials").get(material)==null)event.setResult(Event.Result.DENY);
    }
    void restrict(Event event){
        bind("restrictions");Object recipe=field(event,"recipe");ItemStack output=recipe==null?null:(ItemStack)field(recipe,"output");
        if(output==null||output.getItem()==field(type(WEAPONS),null,"partBolt")||!type("tconstruct.library.util.IToolPart").isInstance(output.getItem()))return;
        int id=material(output);Map<?,?> materials=table("toolMaterials");
        // getMaterial falls back to the native constant Wood=0 only for an absent key.
        Object material=materials.get(materials.containsKey(id)?id:0);
        ItemStack cast=(ItemStack)field(recipe,"cast");
        if(cast==null)throw fault("Native restriction callback dereferences a null pattern");
        NativeStackAudit.audit(cast);
        Object key=TinkerRecipes.construct(type("mantle.utils.ItemMetaWrapper"),new Class<?>[]{ItemStack.class},cast.copy());
        Object raw=restrictions().get(key);boolean restricted=true;
        if(raw!=null){
            if(!(raw instanceof List<?>)||((List<?>)raw).size()>65536||!Arrays.asList("java.util.ArrayList","java.util.LinkedList","java.util.Arrays$ArrayList","java.util.Collections$SingletonList","java.util.Collections$EmptyList").contains(raw.getClass().getName()))
                throw fault("Unadapted native restriction material list");
            for(Object allowed:(List<?>)raw){
                if(allowed==null||material==null)throw fault("Native restriction material lookup dereferences null");
                String name=(String)field(type("tconstruct.library.tools.ToolMaterial"),allowed,"materialName");
                if(name==null)throw fault("Native restriction material has a null name");
                if(name.equals(field(type("tconstruct.library.tools.ToolMaterial"),material,"materialName"))){restricted=false;break;}
            }
        }
        if(restricted)event.setResult(Event.Result.DENY);
    }
    void consumePattern(Event event){
        ItemStack output=(ItemStack)field(event,"output");
        if(type("tconstruct.library.util.IPattern").isInstance(output.getItem()))setConsume(event);
    }
    private static void setConsume(Event event){
        try{java.lang.reflect.Field flag=event.getClass().getField("consumeCast");flag.setBoolean(event,true);}
        catch(ReflectiveOperationException error){throw fault("Missing native casting consumption flag");}
    }
    boolean extra(Event event,boolean timer){
        bind("extra");int configured=(Integer)field(type(EXTRA),null,timer?"tcon_unstable_material_id":"tcon_bedrock_material_id");
        if(configured<=0)return false;
        ItemStack output=(ItemStack)field(event,"output");
        if(output==null||!type("tconstruct.library.util.IToolPart").isInstance(output.getItem())||material(output)!=configured)return false;
        if(output.getTagCompound()==null)output.setTagCompound(new NBTTagCompound());
        // The timer's world clocks/current dimension are runtime conditions; never
        // query them or bake them into a fixed result specimen.
        if(timer)return true;
        UUID uuid=(UUID)field(type("com.rwtema.extrautils.modintegration.TConEvents"),null,"uuid");
        if(SharedMonsterAttributes.movementSpeed.getClass()!=type("net.minecraft.entity.ai.attributes.RangedAttribute"))throw fault("Bedrockium movement attribute needs an adapter");
        NBTTagCompound tag=output.getTagCompound(),entry=new NBTTagCompound();NBTTagList list=tag.getTagList("AttributeModifiers",10);
        entry.setString("AttributeName",(String)field(type("net.minecraft.entity.ai.attributes.BaseAttribute"),SharedMonsterAttributes.movementSpeed,"unlocalizedName"));
        entry.setString("Name","[TCon]Bedrockium Weight");entry.setDouble("Amount",-0.1D);entry.setInteger("Operation",2);
        entry.setLong("UUIDMost",uuid.getMostSignificantBits());entry.setLong("UUIDLeast",uuid.getLeastSignificantBits());
        list.appendTag(entry);tag.setTag("AttributeModifiers",list);return false;
    }
    void verify(){alive();for(NativeDefinitionGraph graph:graphs.values())graph.verifyRoots();}
    void finishVerification(){verify();for(NativeDefinitionGraph graph:graphs.values())graph.verify();}
    private void alive(){Jobs.checkpoint();if(closed)throw new Jobs.Fault("capture_closed","Casting dependency reader is closed");}
    public void close(){closed=true;for(NativeDefinitionGraph graph:graphs.values())graph.close();graphs.clear();}
    private static Jobs.Fault fault(String text){return new Jobs.Fault("recipe_unsupported",text);}
}
