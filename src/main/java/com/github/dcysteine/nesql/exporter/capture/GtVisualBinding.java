package com.github.dcysteine.nesql.exporter.capture;

import com.github.dcysteine.nesql.exporter.core.InventoryTrace;
import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import java.util.*;
import com.google.gson.JsonArray;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Read the pinned dispatcher inputs, not another getRendererForItemStack call
 * with arbitrary material/unification callbacks during checkpoint recovery. */
final class GtVisualBinding implements NativeAuthorities.Proof {
    private final ItemStack query;private final Object dispatcher,expected;
    private final Read initial;
    GtVisualBinding(ItemStack query,Object dispatcher,Object expected){
        MagicApi.version("GtVisualBinding","gregtech_nh");
        if(query==null||query.getClass()!=ItemStack.class||dispatcher==null||!dispatcher.getClass().getName().equals(InventoryTrace.GT_ITEMS))throw changed();
        this.query=query.copy();this.dispatcher=dispatcher;this.expected=expected;initial=read();check();
    }
    @Override public void check(){
        Read now=read();
        if(initial.anchors.size()!=now.anchors.size()||!CanonicalJson.digest(initial.facts).equals(CanonicalJson.digest(now.facts)))throw changed();
        for(int i=0;i<initial.anchors.size();i++)if(initial.anchors.get(i)!=now.anchors.get(i))throw changed();
    }
    private Read read(){
        Read read=new Read();Item item=query.getItem();
        try{if(item==null||item.getClass().getMethod("getDamage",ItemStack.class).getDeclaringClass()!=Item.class)throw changed();}
        catch(NoSuchMethodException error){throw changed();}
        short meta=((Number)field(query,"itemDamage")).shortValue();Object raw=field(dispatcher.getClass(),null,"specialRenderers");
        if(raw==null||raw.getClass()!=HashMap.class||((Map<?,?>)raw).size()>4096)throw changed();read.anchor(raw);
        read.facts.add(object("metadata",(int)meta,"specialSize",((Map<?,?>)raw).size()));
        Object selected=null;boolean found=false;
        for(Map.Entry<?,?> entry:((Map<?,?>)raw).entrySet()){
            Object key=entry.getKey();if(key==null||!key.getClass().getName().equals("gregtech.common.render.items.MetaGeneratedItemRenderer$RendererKey"))continue;
            if(field(key,"item")==item&&((Number)field(key,"metadata")).shortValue()==meta){
                if(found)throw changed();found=true;read.anchor(key);selected=entry.getValue();
            }
        }
        read.facts.add(value(found));
        if(!found){
            Class<?> owner=type("gregtech.api.items.MetaGeneratedItem");
            Class<?> materialOwner;
            try{materialOwner=item.getClass().getMethod("getMaterialRenderer",int.class).getDeclaringClass();
                if(materialOwner!=owner&&materialOwner!=type("gregtech.api.items.MetaGeneratedItemX32"))throw changed();
                if(item.getClass().getMethod("allowMaterialRenderer",int.class).getDeclaringClass()!=owner)throw changed();}
            catch(NoSuchMethodException error){throw changed();}
            short offset=((Number)field(item,"mOffset")).shortValue();read.facts.add(value((int)offset));
            if(meta>=offset)selected=field(dispatcher,"mItemRenderer");
            else{
                // X32 selects the generated material before the unification fallback.
                // Read the same table without invoking an overridable item callback.
                if(materialOwner!=owner){
                    Class<?> materials=type("gregtech.api.enums.Materials");
                    Object table=field(type("gregtech.api.GregTechAPI"),null,"sGeneratedMaterials");
                    int index=meta%1000;
                    if(table==null||table.getClass()!=java.lang.reflect.Array.newInstance(materials,0).getClass()
                            ||index<0||index>=java.lang.reflect.Array.getLength(table))throw changed();
                    read.anchor(table);read.facts.add(value(index));
                    Object material=java.lang.reflect.Array.get(table,index);read.anchor(material);
                    if(material!=null){if(material.getClass()!=materials)throw changed();selected=field(material,"renderer");}
                }
                if(selected==null){
                Object table=field(type("gregtech.api.util.GTOreDictUnificator"),null,"sItemStack2DataMap");
                Object data=read.lookup(table,query,false,true);if(data==null)data=read.lookup(table,query,true,true);read.anchor(data);
                if(data!=null){
                    if(data.getClass()!=type("gregtech.api.objects.ItemData"))throw changed();
                    Object prefix=field(data,"mPrefix");read.anchor(prefix);
                    if(prefix!=null){
                        Object stack=field(data,"mMaterial");read.anchor(stack);
                        if(stack!=null){if(stack.getClass()!=type("gregtech.api.objects.MaterialStack"))throw changed();Object material=field(stack,"mMaterial");read.anchor(material);
                            if(material!=null){if(material.getClass()!=type("gregtech.api.enums.Materials"))throw changed();selected=field(material,"renderer");}
                        }
                    }
                }
                }
                if(selected==null)selected=field(dispatcher,"mMaterialRenderer");
            }
        }
        read.anchor(selected);if(selected!=expected)throw changed();return read;
    }
    /** Only query Item.hashCode is consumed by the native strategy. Stored
     * collision keys are inspected as ItemStack fields: no unrelated Item
     * hash/name/NBT callback is required merely to pass a hash-table slot. */
    private static final class Read {
        final List<Object> anchors=new ArrayList<>();final JsonArray facts=array();
        void anchor(Object value){anchors.add(value);}
        Object lookup(Object table,ItemStack query,boolean wildcard,boolean map){
            if(!map||table==null||!table.getClass().getName().equals("it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap"))throw changed();anchor(table);
            Object strategy=field(table,"strategy");anchor(strategy);
            if(strategy!=field(type("gregtech.api.objects.GTItemStack"),null,"ITEMSTACK_HASH_STRATEGY2")||!strategy.getClass().getName().equals("gregtech.api.objects.GTItemStack$1"))throw changed();
            try{if(query.getItem().getClass().getMethod("hashCode").getDeclaringClass()!=Object.class)throw changed();}catch(NoSuchMethodException error){throw changed();}
            Object[] keys=(Object[])field(table,"key"),values=(Object[])field(table,"value");anchor(keys);anchor(values);
            int n=((Number)field(table,"n")).intValue(),mask=((Number)field(table,"mask")).intValue(),size=((Number)field(table,"size")).intValue();
            if(n<=0||(n&(n-1))!=0||mask!=n-1||keys.length!=n+1||values.length!=n+1||size<0||size>n)throw changed();
            int meta=wildcard?32767:((Number)field(query,"itemDamage")).intValue();
            int hash=System.identityHashCode(query.getItem())*38197+meta,mixed=hash*0x9E3779B9,slot=(mixed^mixed>>>16)&mask;
            facts.add(object("capacity",n,"size",size,"metadata",meta));
            for(int visited=0;visited<Math.min(n,4096);visited++,slot=(slot+1)&mask){
                Jobs.checkpoint();Object raw=keys[slot];anchor(raw);if(raw==null)return null;if(raw.getClass()!=ItemStack.class)throw changed();
                ItemStack key=(ItemStack)raw;Item item=key.getItem();int damage=((Number)field(key,"itemDamage")).intValue();anchor(item);facts.add(value(damage));
                if(item==query.getItem()&&damage==meta){Object found=values[slot];anchor(found);return found;}
            }throw changed();
        }
    }
    private static Jobs.Fault changed(){return new Jobs.Fault("animation_changed","Native GT inventory dispatcher source binding changed or is unreviewed");}
}
