package com.github.dcysteine.nesql.exporter.capture;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;

/** Only the pinned read-only resolver/stack-splitting callbacks, not an item allowlist. */
final class LootBagItems {
    private LootBagItems() {}
    static void audit(Item item) {
        Class<?> limit=ItemCallbacks.method(item,"getItemStackLimit","getItemStackLimit",ItemStack.class).getDeclaringClass();
        // ItemDescriptor constructs physical metadata; it never calls getDamage.
        // The inherited Item limit also reads only maxStackSize, including ToolCore.
        if(limit==Item.class){require(item,"getItemStackLimit","func_77639_j",Item.class.getName());return;}
        Class<?> damage=ItemCallbacks.method(item,"getDamage","getDamage",ItemStack.class).getDeclaringClass();
        if(damage!=Item.class){
            if(!damage.getName().equals("gtPlusPlus.core.item.base.BaseItemBurnable"))throw fault("metadata",damage);
            MagicApi.version("LootBagItems","gregtech_nh");
        }
        String name=limit.getName();
        switch(name){
            case "biomesoplenty.common.items.ItemBOPFood":
                MagicApi.version("LootBagItems","BiomesOPlenty");
                if(item.getClass()!=type(name)||damage!=Item.class)throw fault("BOP food metadata",damage);
                return;
            case "thaumcraft.common.items.ItemResource":
                MagicApi.version("LootBagItems","Thaumcraft");
                if(damage!=Item.class)throw fault("resource metadata",damage);
                require(item,"getItemStackLimit","func_77639_j",Item.class.getName());
                return;
            case "gregtech.api.items.MetaGeneratedTool": // final constant one
            case "gregtech.common.items.MetaGeneratedItem98": // constant 64
            case "gregtech.common.items.MetaGeneratedItem99": // private prefix lookup; no virtual callbacks
                MagicApi.version("LootBagItems","gregtech_nh");
                return;
            case "gregtech.api.items.MetaGeneratedItemX32":
                Object prefixes=field(type(name),item,"mGeneratedPrefixList");
                if(prefixes==null||prefixes.getClass()!=type("[Lgregtech.api.enums.OrePrefixes;"))
                    throw new Jobs.Fault("recipe_unsupported","Unadapted loot GT prefix array");
                break;
            case "gregtech.api.items.MetaGeneratedItemX01":
            case "gregtech.api.items.MetaBaseItem": break;
            default:throw fault("stack limit",limit);
        }
        MagicApi.version("LootBagItems","gregtech_nh");
        require(item,"getElectricStats","getElectricStats","gregtech.api.items.MetaGeneratedItem",ItemStack.class);
        require(item,"getFluidContainerStats","getFluidContainerStats","gregtech.api.items.MetaGeneratedItem",ItemStack.class);
        require(item,"getRealCharge","getRealCharge","gregtech.api.items.MetaBaseItem",ItemStack.class);
        for(String fieldName:new String[]{"mElectricStats","mFluidContainerStats"}){
            Object value=field(item,fieldName);
            if(value==null||value.getClass()!=java.util.concurrent.ConcurrentHashMap.class)throw new Jobs.Fault("recipe_unsupported","Unadapted loot GT statistics table");
        }
    }
    private static void require(Item item,String mcp,String srg,String owner,Class<?>... args){
        Class<?> actual;
        try { actual=(mcp.equals("getElectricStats")||mcp.equals("getFluidContainerStats")||mcp.equals("getRealCharge")||mcp.equals("getOrePrefix"))
                ?item.getClass().getMethod(mcp,args).getDeclaringClass():ItemCallbacks.method(item,mcp,srg,args).getDeclaringClass(); }
        catch(NoSuchMethodException failure){throw new Jobs.Fault("recipe_unsupported","Missing loot item callback: "+mcp);}
        if(!actual.getName().equals(owner))throw fault(mcp,actual);
    }
    private static Jobs.Fault fault(String callback,Class<?> owner){return new Jobs.Fault("recipe_unsupported","Unadapted loot "+callback+" callback: "+owner.getName());}
}
