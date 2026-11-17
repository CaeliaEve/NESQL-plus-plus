package com.github.dcysteine.nesql.exporter.capture;

import com.github.dcysteine.nesql.exporter.task.Jobs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/** Exhausted-fuel callbacks only. Conditional/RNG/resource-consuming callbacks need rules. */
final class FuelContainers {
    private FuelContainers() {}
    static ItemStack fixed(ItemStack exhausted){
        return fixed(exhausted,null);
    }
    static ItemStack fixed(ItemStack exhausted,java.util.List<GtItemListBinding> bindings){
        Item item=exhausted.getItem();
        Class<?> result=ItemCallbacks.method(item,"getContainerItem","getContainerItem",ItemStack.class).getDeclaringClass();
        if(result==Item.class){
            require(item,"hasContainerItem","hasContainerItem",ItemStack.class);
            require(item,"hasContainerItem","func_77634_r");
            require(item,"getContainerItem","func_77668_q");
            return item.getContainerItem(exhausted.copy());
        }
        switch(result.getName()){
            case "mods.railcraft.common.fluids.ItemBucketRailcraft":
                // Native furnace consumption invokes this getter even when the
                // separate vanilla container predicate is false.
                return ItemCallbacks.pedestalContainer(exhausted,bindings);
            // These read-only callbacks and their transitive predicates were audited
            // for the pinned pack by ItemCallbacks. Preserve count zero on the owned stack.
            case "ic2.core.item.resources.ItemCell":
            case "gregtech.api.items.GTGenericItem":
            case "gregtech.api.items.MetaGeneratedItemX01":
            case "gregtech.api.items.MetaGeneratedItemX32":
            case "gregtech.common.items.MetaGeneratedItem01":
            case "gregtech.common.items.MetaGeneratedItem02":
                return ItemCallbacks.container(exhausted,false,bindings);
            default:throw new Jobs.Fault("recipe_unsupported","Fuel container needs an audited rule: "+result.getName());
        }
    }
    private static void require(Item item,String mcp,String srg,Class<?>... args){
        Class<?> owner=ItemCallbacks.method(item,mcp,srg,args).getDeclaringClass();
        if(owner!=Item.class)throw new Jobs.Fault("recipe_unsupported","Fuel container predicate needs an audited rule: "+owner.getName());
    }
}
