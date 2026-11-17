package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.ItemList;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;
import java.util.*;

/** Concrete display metadata from the loaded NEI catalog; template NBT/count remain native. */
final class CatalogExamples {
    private CatalogExamples() {}
    static List<ItemStack> metadata(ItemStack template) {
        if(Items.feather.getDamage(template)!=OreDictionary.WILDCARD_VALUE)
            return Collections.singletonList(template.copy());
        List<ItemStack> result=new ArrayList<>();Set<Integer> seen=new HashSet<>();
        for(ItemStack candidate:ItemList.items) {
            Jobs.checkpoint();
            if(candidate==null||candidate.getItem()!=template.getItem()||candidate.stackSize<=0)continue;
            int meta=Items.feather.getDamage(candidate);
            if(meta<0||meta==OreDictionary.WILDCARD_VALUE||!seen.add(meta))continue;
            ItemStack copy=template.copy();copy.setItemDamage(meta);result.add(copy);
            if(result.size()>65536)throw new Jobs.Fault("recipe_unsupported","Native metadata examples exceed budget");
        }
        if(result.isEmpty())throw new Jobs.Fault("recipe_unsupported","Wildcard has no concrete NEI metadata examples: "
                +net.minecraft.item.Item.itemRegistry.getNameForObject(template.getItem()));
        return result;
    }
}
