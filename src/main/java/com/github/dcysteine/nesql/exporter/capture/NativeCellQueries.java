package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.capture.NativeUsageReferences.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;
import codechicken.nei.ItemList;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.*;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

/** Pinned AE2 item and AE2FC fluid cell queries, on owned stacks with no save provider. */
final class NativeCellQueries implements NativeUsageReferences.Source {
    private static final Set<String> PROVIDERS=new HashSet<>(Arrays.asList(
        "appeng.core.features.registries.entries.BasicCellHandler",
        "appeng.core.features.registries.entries.CreativeCellHandler",
        "appeng.core.features.registries.entries.VoidCellHandler",
        "com.glodblock.github.common.storage.FluidCellHandler",
        "com.glodblock.github.crossmod.extracells.storage.ProxyItemCellHandler",
        "com.glodblock.github.crossmod.extracells.storage.ProxyVoidCellHandler",
        "thaumicenergistics.common.items.ItemEssentiaCell"));
    private static final Set<String> ITEMS=new HashSet<>(Arrays.asList(
        "appeng.items.storage.ItemBasicStorageCell","appeng.items.storage.ItemAdvancedStorageCell",
        "appeng.items.storage.ItemExtremeStorageCell","appeng.items.storage.ItemCreativeStorageCell",
        "appeng.items.storage.ItemVoidStorageCell","appeng.items.tools.powered.ToolPortableCell",
        "appeng.items.tools.powered.ToolMassCannon","appeng.items.tools.powered.ToolColorApplicator",
        "com.glodblock.github.common.item.ItemBasicFluidStorageCell","com.glodblock.github.common.item.ItemMultiFluidStorageCell",
        "com.glodblock.github.common.item.ItemFluidExtremeStorageCell","com.glodblock.github.common.item.ItemCreativeFluidStorageCell",
        "com.glodblock.github.common.item.ItemFluidVoidStorageCell","com.glodblock.github.common.item.ItemPortableFluidCell",
        "com.glodblock.github.common.item.ItemInfinityWaterStorageCell",
        "com.glodblock.github.crossmod.extracells.storage.ProxyExtremeStorageCell",
        "com.glodblock.github.crossmod.extracells.storage.ProxyFluidStorageCell",
        "com.glodblock.github.crossmod.extracells.storage.ProxyItemStorageCell"));
    private final boolean fluid;
    NativeCellQueries(String handler){
        if(!AE.equals(handler)&&!FLUID.equals(handler))throw fault("Unknown native cell query");
        fluid=FLUID.equals(handler);MagicApi.version("NativeCellQueries","appliedenergistics2");
        if(fluid)MagicApi.version("NativeCellQueries","ae2fc");
    }
    private Object[] roles(){
        Object api=exact(field(type("appeng.api.AEApi"),null,"HELD_API"),"appeng.core.Api");
        Object registries=exact(field(api,"registryContainer"),"appeng.core.features.registries.RegistryContainer");
        Object registry=exact(call(registries,"cell"),"appeng.core.features.registries.CellRegistry");
        Object handlers=field(registry,"handlers");
        if(handlers==null||handlers.getClass()!=ArrayList.class)throw fault("Unadapted native cell handler registry");
        return new Object[]{api,registries,registry,handlers};
    }
    public Object[] roots(){return roots(true);}
    public Object[] registrationRoots(){return roots(false);}
    private Object[] roots(boolean admit){
        Object[] roles=roles();List<Object> roots=new ArrayList<>(Arrays.asList(roles));
        List<?> providers=(List<?>)roles[3];
        if(providers.size()>1024)throw fault("Native cell provider budget exceeded");
        for(Object provider:providers){
            if(provider==null||admit&&!PROVIDERS.contains(provider.getClass().getName()))throw fault("Unadapted cell provider: "+(provider==null?"null":provider.getClass().getName()));
            if(admit&&provider.getClass().getName().equals("thaumicenergistics.common.items.ItemEssentiaCell"))NativeUsageAdmission.admit(provider,"cellProvider");
            roots.add(provider);
        }
        int count=panelSize();roots.add(ItemList.items);roots.add(count);roots.add(ItemList.loadFinished);return roots.toArray();
    }
    public Data read(String locale){
        roots();Object[] roles=roles();Object registry=roles[2];
        Data data=new Data(fluid?"aeFluidCell":"aeItemCell",fluid?"1.4.120-gtnh":"rv3-beta-695-GTNH");
        List<ItemStack> panel=panel();data.scanned=panel.size();
        for(int index=0;index<panel.size();index++){
            Jobs.checkpoint();ItemStack original=panel.get(index);
            if(original==null||original.getItem()==null||original.stackSize<=0)throw fault("Invalid NEI cell query input");
            ItemStack input=Data.owned(original);if(!data.query(input))continue;
            JsonObject result=query(registry,input.copy(),data);
            if(result==null)data.unmatched++;else data.observation(index,input,result);
        }
        return data;
    }
    private JsonObject query(Object registry,ItemStack input,Data data){
        // The admitted providers test only their storage item interfaces before entering callbacks.
        if((type("appeng.api.implementations.items.IStorageCell").isInstance(input.getItem())
                ||fluid&&type("com.glodblock.github.common.storage.IStorageFluidCell").isInstance(input.getItem()))
                &&!ITEMS.contains(input.getItem().getClass().getName()))throw fault("Unadapted native storage item: "+input.getItem().getClass().getName());
        Object provider=call(registry,"getHandler",new Class<?>[]{ItemStack.class},input);
        if(provider==null)return null;
        if(!PROVIDERS.contains(provider.getClass().getName()))throw fault("Unadapted selected cell provider");
        if(provider.getClass().getName().equals("thaumicenergistics.common.items.ItemEssentiaCell")){
            NativeUsageAdmission.admit(provider,"cellProvider");
        }
        Object channel=field(type("appeng.api.storage.StorageChannel"),null,fluid?"FLUIDS":"ITEMS");
        Object handler=call(provider,"getCellInventory",new Class<?>[]{ItemStack.class,type("appeng.api.storage.ISaveProvider"),type("appeng.api.storage.StorageChannel")},input,null,channel);
        Class<?> expected=type(fluid?"com.glodblock.github.common.storage.FluidCellInventoryHandler":"appeng.me.storage.CellInventoryHandler");
        if(handler==null||!expected.isInstance(handler))return null;
        if(handler.getClass()!=expected)throw fault("Unadapted cell inventory query subclass");
        if(!fluid&&call(handler,"getCellInv")==null){
            // Creative/void handlers may wrap an inventory that is not ICellInventory.
            // The pinned NEI viewer dereferences that null in getTotalBytes. Preserve
            // this exact native fault as an observation, not an empty cell/non-match.
            return object("kind","cellFault","channel","items","provider",provider.getClass().getName(),
                "inventory",handler.getClass().getName(),"fault","missingCellInventory",
                "nativeException","java.lang.NullPointerException");
        }
        long total=number(call(handler,"getTotalBytes"));if(total<=0)return null;
        Object list;
        try{list=type(fluid?"appeng.util.item.FluidList":"appeng.util.item.ItemList").newInstance();}
        catch(ReflectiveOperationException failure){throw new IllegalStateException(failure);}
        call(handler,"getAvailableItems",new Class<?>[]{type("appeng.api.storage.data.IItemList"),int.class},list,0);
        if(!(list instanceof Iterable))throw fault("Invalid native cell result list");
        List<Object> contents=new ArrayList<>();for(Object member:(Iterable<?>)list){
            if(contents.size()>=262144)throw fault("Native cell content budget exceeded");
            exact(member,fluid?"appeng.util.item.AEFluidStack":"appeng.util.item.AEItemStack");contents.add(member);
        }
        contents.sort((a,b)->Long.compare(number(call(b,"getStackSize")),number(call(a,"getStackSize"))));
        JsonArray entries=array();for(Object member:contents){
            long amount=number(call(member,"getStackSize"));if(amount<=0)throw fault("Native available cell entry has no positive amount");
            String id;
            if(fluid){FluidStack stack=(FluidStack)call(member,"getFluidStack");stack=stack.copy();stack.amount=1;id=data.fluid(stack);}
            else{ItemStack stack=(ItemStack)call(member,"getItemStack");stack=stack.copy();stack.stackSize=1;id=data.item(stack);}
            entries.add(object("kind",fluid?"fluid":"item","id",id,"amount",Long.toString(amount)));
        }
        return object("kind","cell","channel",fluid?"fluids":"items","provider",provider.getClass().getName(),
            "inventory",handler.getClass().getName(),"totalBytes",Long.toString(total),"usedBytes",Long.toString(number(call(handler,"getUsedBytes"))),
            "totalTypes",Long.toString(number(call(handler,"getTotalTypes"))),"usedTypes",Long.toString(number(call(handler,"getUsedTypes"))),"entries",entries);
    }
    static List<ItemStack> panel(){
        panelSize();
        synchronized(ItemList.items){return new ArrayList<>(ItemList.items);}
    }
    static int panelSize(){
        if(!ItemList.loadFinished||ItemList.items==null)throw new Jobs.Fault("handler_not_ready","Native usage item catalogue is not ready");
        int size=ItemList.items.size();if(size>262144)throw fault("Native usage item catalogue exceeds budget");return size;
    }
    static Object exact(Object value,String owner){
        if(value==null)throw new Jobs.Fault("handler_not_ready","Native usage source is not initialized: "+owner);
        if(!owner.equals(value.getClass().getName()))throw fault("Unadapted native usage source: "+value.getClass().getName());return value;
    }
    static Object call(Object owner,String method){return call(owner,method,new Class<?>[0]);}
    static Object call(Object owner,String method,Class<?>[] types,Object... args){
        try{Method target=owner.getClass().getMethod(method,types);target.setAccessible(true);return target.invoke(owner,args);}
        catch(InvocationTargetException failure){Throwable cause=failure.getCause();if(cause instanceof Error)throw (Error)cause;if(cause instanceof RuntimeException)throw (RuntimeException)cause;throw new IllegalStateException(cause);}
        catch(ReflectiveOperationException failure){throw new IllegalStateException("Missing pinned usage callback: "+owner.getClass().getName()+"."+method,failure);}
    }
    private static long number(Object value){if(!(value instanceof Number))throw fault("Invalid native cell counter");return ((Number)value).longValue();}
}
