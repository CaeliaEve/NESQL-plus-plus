package com.github.dcysteine.nesql.exporter.capture;

import com.github.dcysteine.nesql.exporter.task.Jobs;
import java.util.*;
import net.minecraft.item.Item;

/** Coalesced dependencies survive each owned plan until the reader seals.
 * Identity/no-callback branches retain nothing. Repeated concrete states share
 * one binding, rather than retaining inventories, full plans or result traces.
 */
final class GtCraftingBindings implements AutoCloseable {
    private final Map<Item,Map<String,GtCraftingBinding>> items=new IdentityHashMap<>();
    private int count;
    private boolean closed;

    void retain(GtCraftingCapturePlan plan) {
        requireOpen();plan.verify();
        for(GtCraftingBinding binding:plan.bindings()) {
            Jobs.checkpoint();if(!binding.mutable())continue;
            Map<String,GtCraftingBinding> records=items.computeIfAbsent(binding.item(),ignored->new LinkedHashMap<>());
            String key=binding.key();GtCraftingBinding prior=records.get(key);
            if(prior!=null)prior.verify();
            else {
                if(count>=262144)throw new Jobs.Fault("recipe_limit","Native GT crafting dependencies exceed their reader budget");
                binding.verify();records.put(key,binding);count++;
            }
        }
        plan.verify();
    }
    void verify() {
        requireOpen();
        for(Map<String,GtCraftingBinding> group:items.values())for(GtCraftingBinding binding:group.values()) {
            Jobs.checkpoint();binding.verify();
        }
    }
    int size(){requireOpen();return count;}
    private void requireOpen(){if(closed)throw new Jobs.Fault("capture_closed","GT crafting dependency reader is closed");}
    public void close(){if(closed)return;closed=true;items.clear();count=0;}
}
