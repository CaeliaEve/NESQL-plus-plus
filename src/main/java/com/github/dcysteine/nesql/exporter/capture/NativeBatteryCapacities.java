package com.github.dcysteine.nesql.exporter.capture;

import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonObject;
import java.util.*;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Pinned Galacticraft/AmunRa capacities. Writes only a caller-owned stack;
 * recursive native loading intentionally retains present nested-tag aliases. */
final class NativeBatteryCapacities implements AutoCloseable {
    private static final String ELECTRIC="micdoodle8.mods.galacticraft.core.energy.item.ItemElectricBase";
    private static final String AMUN="de.katzenpapst.amunra.item.ItemAbstractBatteryUser";
    private static final String FIELD="de.katzenpapst.amunra.item.ItemBaseBattery";
    private static final String GC_ITEMS="micdoodle8.mods.galacticraft.core.items.GCItems";
    private final Map<Integer,Item> numeric=new HashMap<>();
    private final Map<Item,Integer> fields=new IdentityHashMap<>();
    private Item fallback;private boolean observedFallback,closed;
    static final class Value {final float capacity;final JsonObject rule;Value(float capacity,JsonObject rule){this.capacity=capacity;this.rule=rule;}}

    Value resolveOwned(ItemStack stack){alive();return resolve(stack,0,new int[1]);}
    private Value resolve(ItemStack stack,int depth,int[] nodes){
        Jobs.checkpoint();if(depth>16||++nodes[0]>1024)throw fault("Native nested battery capacity exceeds budget");
        if(stack==null||stack.getClass()!=ItemStack.class||stack.getItem()==null||!type(ELECTRIC).isInstance(stack.getItem()))
            throw fault("Native battery would fail its ItemElectricBase cast");
        NativeStackAudit.nbt(stack.getTagCompound());Item item=stack.getItem();String owner=method(item,"getMaxElectricityStored",ItemStack.class).getName();
        if(owner.equals(FIELD)){
            float value=((Number)field(type(FIELD),item,"capacity")).floatValue();int bits=Float.floatToRawIntBits(value);
            Integer old=fields.putIfAbsent(item,bits);if(old!=null&&old!=bits)throw changed();
            if(fields.size()>65536)throw fault("Native capacity source budget exceeded");
            return new Value(value,object("kind","field","owner",owner,"bits",bits(value)));
        }
        Float constant=null;
        if(owner.equals("micdoodle8.mods.galacticraft.core.items.ItemBattery"))constant=15000F;
        else if(owner.equals("micdoodle8.mods.galacticraft.core.items.ItemBatteryInfinite"))constant=Float.POSITIVE_INFINITY;
        else if(owner.equals("galaxyspace.core.item.ItemLeadBattery"))constant=50000F;
        if(constant!=null)return new Value(constant,object("kind","constant","owner",owner,"bits",bits(constant)));
        if(!owner.equals(AMUN))throw fault("Native battery capacity provider needs an adapter: "+owner);
        if(method(item,"getUsedBattery",ItemStack.class,boolean.class)!=type(AMUN))throw fault("AmunRa battery loader is overridden");
        if(!stack.hasTagCompound())stack.setTagCompound(new NBTTagCompound());NBTTagCompound tags=stack.getTagCompound();
        if(tags.hasKey("maxEnergy"))return new Value(tags.getFloat("maxEnergy"),object("kind","amun","battery",null));
        NBTTagCompound stored=tags.getCompoundTag("batteryStack");int id=stored.getShort("id");Item offered=Item.getItemById(id);bind(id,offered);
        NativeStackAudit.identityOwner(offered);
        ItemStack child=ItemStack.loadItemStackFromNBT(stored);boolean defaulted=child==null;
        if(defaulted){
            Object raw=field(type(GC_ITEMS),null,"battery");if(!(raw instanceof Item))throw fault("Galacticraft default battery is not initialized");
            if(observedFallback&&fallback!=raw)throw changed();observedFallback=true;fallback=(Item)raw;child=new ItemStack(fallback,1,0);
        }
        Item selected=child.getItem();NativeStackAudit.identityOwner(selected);int selectedId=Item.getIdFromItem(selected);bind(selectedId,selected);
        String registry=Item.itemRegistry.getNameForObject(selected);
        if(registry==null||Item.itemRegistry.getObject(registry)!=selected)throw fault("Unregistered native battery source");
        Value capacity=resolve(child,depth+1,nodes);tags.setFloat("maxEnergy",capacity.capacity);
        return new Value(capacity.capacity,object("kind","amun","battery",object("defaulted",defaulted,"offeredId",id,
                "offeredRegistry",offered==null?null:Item.itemRegistry.getNameForObject(offered),"selectedId",selectedId,
                "selectedRegistry",registry,"capacity",capacity.rule)));
    }
    private void bind(int id,Item item){
        if(!numeric.containsKey(id)){if(numeric.size()>=65536)throw fault("Native battery registry budget exceeded");numeric.put(id,item);}
        else if(numeric.get(id)!=item)throw changed();
    }
    void verify(){alive();if(observedFallback&&field(type(GC_ITEMS),null,"battery")!=fallback)throw changed();}
    void finishVerification(){verify();for(Map.Entry<Integer,Item> entry:numeric.entrySet())if(Item.getItemById(entry.getKey())!=entry.getValue())throw changed();
        for(Map.Entry<Item,Integer> entry:fields.entrySet())if(Float.floatToRawIntBits(((Number)field(type(FIELD),entry.getKey(),"capacity")).floatValue())!=entry.getValue())throw changed();}
    static Class<?> method(Item item,String name,Class<?>...parameters){try{return item.getClass().getMethod(name,parameters).getDeclaringClass();}
        catch(NoSuchMethodException absent){throw fault("Missing native battery method: "+name);}}
    static String bits(float value){return String.format(Locale.ROOT,"%08x",Float.floatToRawIntBits(value));}
    private void alive(){Jobs.checkpoint();if(closed)throw new Jobs.Fault("capture_closed","Native battery source is closed");}
    @Override public void close(){closed=true;numeric.clear();fields.clear();fallback=null;}
    private static Jobs.Fault fault(String message){return new Jobs.Fault("recipe_unsupported",message);}
    private static Jobs.Fault changed(){return new Jobs.Fault("environment_changed","Native battery capacity source changed");}
}
