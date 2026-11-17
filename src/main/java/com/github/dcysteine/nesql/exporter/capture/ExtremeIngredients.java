package com.github.dcysteine.nesql.exporter.capture;

import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.*;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Content candidates and consumption dependencies; no PositionedStack/render callbacks. */
final class ExtremeIngredients {
    private ExtremeIngredients() {}
    static List<Choice> expand(ItemStack template, boolean ignoreNbt) {
        boolean wildcard = NativeItemMetadata.value(template) == 32767;
        NativeItemVariants metadata = wildcard ? new NativeItemVariants(template) : null;
        List<ItemStack> samples = metadata == null ? Collections.singletonList(template) : Arrays.asList(metadata.copies());
        List<Choice> result = new ArrayList<>();
        for (ItemStack sample : samples) {
            ItemStack owned = sample.copy(); owned.stackSize = 1;
            owned.setTagCompound(template.getTagCompound() == null ? null : (NBTTagCompound)template.getTagCompound().copy());
            result.add(new Choice(owned, wildcard, ignoreNbt, metadata));
        }
        return result;
    }
    static final class Choice {
        final ItemStack sample;
        final boolean wildcard, ignoreNbt;
        final NativeItemVariants metadata;
        final GtToolConsumption tool;
        final List<GtItemListBinding> itemLists = new ArrayList<>();
        final List<Object> anchors = new ArrayList<>();
        final String signature;
        Choice(ItemStack sample, boolean wildcard, boolean ignoreNbt, NativeItemVariants metadata) {
            this.sample = sample; this.wildcard = wildcard; this.ignoreNbt = ignoreNbt; this.metadata = metadata;
            tool = GtToolConsumption.supports(sample) ? new GtToolConsumption(sample) : null;
            signature = CanonicalJson.digest(read(anchors, itemLists));
        }
        RecipeRow.Ingredient ingredient() {
            return new RecipeRow.Ingredient(sample.copy(), 1, false,NativeItemMetadata.rule(sample,wildcard,ignoreNbt));
        }
        void verify(Set<Object> checkedMetadata) {
            if (metadata != null && checkedMetadata.add(metadata)) metadata.verify();
            if (tool != null) tool.verify();
            for (GtItemListBinding binding : itemLists) binding.verify();
            List<Object> actual = new ArrayList<>();
            if (!signature.equals(CanonicalJson.digest(read(actual, null)))) throw changed();
            ExtremeRecipes.sameAnchors(anchors, actual);
        }
        private JsonObject read(List<Object> bindings, List<GtItemListBinding> lists) {
            if (tool != null) return tool.declaration();
            Item item = sample.getItem();
            JsonObject dynamic=NativeCraftingContainers.consumption(sample);
            if(dynamic!=null){bindings.add(item);return dynamic;}
            String owner = ItemCallbacks.method(item, "getContainerItem", "getContainerItem", ItemStack.class).getDeclaringClass().getName();
            // Audit predicates before reading any implementation-specific dependency.
            ItemStack returned = ItemCallbacks.container(sample.copy(), false, lists);
            bindings.add(item);
            if (owner.equals(Item.class.getName())) {
                bindings.add(cpw.mods.fml.relauncher.ReflectionHelper.getPrivateValue(Item.class, item, "containerItem", "field_77700_c"));
            } else if (owner.equals("ic2.core.item.resources.ItemCell")) {
                addStack(bindings, field(type("ic2.core.Ic2Items"), null, "cell"));
            } else if (owner.equals("gregtech.api.items.MetaGeneratedItemX01") || owner.equals("gregtech.api.items.MetaGeneratedItemX32")
                    || owner.equals("gregtech.common.items.MetaGeneratedItem01")) {
                int meta = net.minecraft.init.Items.feather.getDamage(sample);
                boolean single = owner.equals("gregtech.api.items.MetaGeneratedItemX01");
                if (single || meta >= 0 && meta < 32000) {
                    Object[] materials = (Object[])field(type("gregtech.api.GregTechAPI"), null, "sGeneratedMaterials");
                    bindings.add(materials);
                    int index = single ? meta : meta % 1000;
                    bindings.add(index >= 0 && index < materials.length ? materials[index] : null);
                    if (single) {
                        Object prefix = field(item, "mPrefix"); bindings.add(prefix);
                        if (prefix != null) addStack(bindings, field(prefix, "mContainerItem"));
                    } else {
                        Object[] prefixes = (Object[])field(item, "mGeneratedPrefixList"); bindings.add(prefixes);
                        if (meta / 1000 >= prefixes.length) throw fault("Extreme container prefix index is unavailable");
                        Object prefix = prefixes[meta / 1000]; bindings.add(prefix);
                        if (prefix != null) addStack(bindings, field(prefix, "mContainerItem"));
                    }
                }
            }
            if (returned != null && returned.getItem() == item && ItemStack.areItemStackTagsEqual(sample, returned)
                    && net.minecraft.init.Items.feather.getDamage(returned) > net.minecraft.init.Items.feather.getDamage(sample)) {
                for (Class<?>[] args : new Class<?>[][] {new Class<?>[0], new Class<?>[] {ItemStack.class}})
                    if (ItemCallbacks.method(item, "getMaxDamage", args.length == 0 ? "func_77612_l" : "getMaxDamage", args).getDeclaringClass() != Item.class)
                        throw fault("Extreme crafting container requires an audited durability getter");
            }
            return object("owner", owner, "returned", RecipeRow.stackEvidence(returned));
        }
    }
    private static void addStack(List<Object> anchors, Object value) {
        anchors.add(value);
        if (value != null) ExtremeRecipes.stack(value, anchors);
    }
    private static Jobs.Fault fault(String message) { return new Jobs.Fault("recipe_unsupported", message); }
    private static Jobs.Fault changed() { return new Jobs.Fault("recipe_changed", "Extreme native candidate or consumption dependency changed"); }
}
