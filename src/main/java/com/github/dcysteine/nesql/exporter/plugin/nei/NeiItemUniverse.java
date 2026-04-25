package com.github.dcysteine.nesql.exporter.plugin.nei;

import codechicken.nei.ItemList;
import com.github.dcysteine.nesql.exporter.main.Logger;
import com.github.dcysteine.nesql.exporter.util.IdUtil;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagByte;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Provides the effective NEI item universe used during export.
 *
 * <p>Thaumcraft only exposes a tiny sample of wand/staff/sceptre variants through {@code getSubItems},
 * which means recipe handlers never see most rod/cap combinations. We synthesize those combinations here
 * so recipe scans can discover the full GTNH set.
 */
final class NeiItemUniverse {
    private static final AtomicBoolean LOGGED_THAUMCRAFT_AUGMENTATION = new AtomicBoolean(false);

    private NeiItemUniverse() {}

    static List<ItemStack> getItems() {
        List<ItemStack> baseItems = ItemList.items != null ? ItemList.items : Collections.<ItemStack>emptyList();
        LinkedHashMap<String, ItemStack> unique = new LinkedHashMap<String, ItemStack>(baseItems.size() + 256);

        for (ItemStack stack : baseItems) {
            addUnique(unique, stack);
        }

        int baseCount = unique.size();
        int thaumcraftAdded = addThaumcraftWandVariants(unique);
        if (thaumcraftAdded > 0 && LOGGED_THAUMCRAFT_AUGMENTATION.compareAndSet(false, true)) {
            Logger.MOD.info(
                    "Augmented NEI export item universe with {} Thaumcraft wand/staff/sceptre variants (base={}, total={})",
                    thaumcraftAdded,
                    baseCount,
                    unique.size());
        }

        return new ArrayList<ItemStack>(unique.values());
    }

    static ItemStack getSeedItem() {
        for (ItemStack stack : getItems()) {
            if (stack != null && stack.getItem() != null) {
                return stack;
            }
        }
        return null;
    }

    private static void addUnique(Map<String, ItemStack> unique, ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return;
        }

        try {
            ItemStack normalized = stack.copy();
            normalized.stackSize = 1;
            unique.putIfAbsent(IdUtil.itemId(normalized), normalized);
        } catch (Throwable ignored) {
            // NEI occasionally exposes malformed stacks; keep export resilient.
        }
    }

    private static int addThaumcraftWandVariants(Map<String, ItemStack> unique) {
        try {
            Class<?> configItemsClass = Class.forName("thaumcraft.common.config.ConfigItems");
            Object wandItemValue = configItemsClass.getField("itemWandCasting").get(null);
            if (!(wandItemValue instanceof Item)) {
                return 0;
            }

            Item wandItem = (Item) wandItemValue;
            Class<?> wandItemClass = Class.forName("thaumcraft.common.items.wands.ItemWandCasting");
            if (!wandItemClass.isInstance(wandItem)) {
                return 0;
            }

            Class<?> wandRodClass = Class.forName("thaumcraft.api.wands.WandRod");
            Class<?> wandCapClass = Class.forName("thaumcraft.api.wands.WandCap");
            Class<?> staffRodClass = Class.forName("thaumcraft.api.wands.StaffRod");

            Method setRodMethod = wandItemClass.getMethod("setRod", ItemStack.class, wandRodClass);
            Method setCapMethod = wandItemClass.getMethod("setCap", ItemStack.class, wandCapClass);
            Method isStaffMethod = wandItemClass.getMethod("isStaff", ItemStack.class);
            Method isSceptreMethod = wandItemClass.getMethod("isSceptre", ItemStack.class);

            @SuppressWarnings("unchecked")
            Map<String, Object> rods = (Map<String, Object>) wandRodClass.getField("rods").get(null);
            @SuppressWarnings("unchecked")
            Map<String, Object> caps = (Map<String, Object>) wandCapClass.getField("caps").get(null);
            if (rods == null || rods.isEmpty() || caps == null || caps.isEmpty()) {
                return 0;
            }

            WandTemplates templates = resolveTemplates(unique.values(), wandItem, isStaffMethod, isSceptreMethod);
            int before = unique.size();

            for (Object rod : rods.values()) {
                if (!wandRodClass.isInstance(rod)) {
                    continue;
                }

                boolean staffRod = staffRodClass.isInstance(rod);
                for (Object cap : caps.values()) {
                    if (!wandCapClass.isInstance(cap)) {
                        continue;
                    }

                    addUnique(unique, createVariantStack(templates.wand, setRodMethod, setCapMethod, rod, cap, false));
                    if (staffRod) {
                        addUnique(unique, createVariantStack(templates.staff, setRodMethod, setCapMethod, rod, cap, false));
                    } else {
                        addUnique(unique, createVariantStack(templates.sceptre, setRodMethod, setCapMethod, rod, cap, true));
                    }
                }
            }

            return unique.size() - before;
        } catch (Throwable t) {
            Logger.MOD.debug("Thaumcraft wand universe augmentation unavailable", t);
            return 0;
        }
    }

    private static ItemStack createVariantStack(
            ItemStack template,
            Method setRodMethod,
            Method setCapMethod,
            Object rod,
            Object cap,
            boolean sceptre) throws Exception {
        ItemStack generated = template.copy();
        generated.stackSize = 1;
        generated.setTagCompound(null);
        setRodMethod.invoke(generated.getItem(), generated, rod);
        setCapMethod.invoke(generated.getItem(), generated, cap);
        if (sceptre) {
            generated.setTagInfo("sceptre", new NBTTagByte((byte) 1));
        }
        return generated;
    }

    private static WandTemplates resolveTemplates(
            Collection<ItemStack> source,
            Item wandItem,
            Method isStaffMethod,
            Method isSceptreMethod) {
        ItemStack wandTemplate = null;
        ItemStack staffTemplate = null;
        ItemStack sceptreTemplate = null;

        for (ItemStack stack : source) {
            if (stack == null || stack.getItem() != wandItem) {
                continue;
            }

            try {
                boolean isStaff = Boolean.TRUE.equals(isStaffMethod.invoke(wandItem, stack));
                boolean isSceptre = Boolean.TRUE.equals(isSceptreMethod.invoke(wandItem, stack));
                if (isStaff && staffTemplate == null) {
                    staffTemplate = sanitizeTemplate(stack);
                } else if (isSceptre && sceptreTemplate == null) {
                    sceptreTemplate = sanitizeTemplate(stack);
                } else if (!isStaff && !isSceptre && wandTemplate == null) {
                    wandTemplate = sanitizeTemplate(stack);
                }
            } catch (Exception ignored) {
            }
        }

        if (wandTemplate == null) {
            wandTemplate = new ItemStack(wandItem, 1, 0);
        }
        if (staffTemplate == null) {
            staffTemplate = wandTemplate.copy();
        }
        if (sceptreTemplate == null) {
            sceptreTemplate = wandTemplate.copy();
        }

        wandTemplate.setTagCompound(null);
        staffTemplate.setTagCompound(null);
        sceptreTemplate.setTagCompound(null);

        return new WandTemplates(wandTemplate, staffTemplate, sceptreTemplate);
    }

    private static ItemStack sanitizeTemplate(ItemStack template) {
        ItemStack copy = template.copy();
        copy.stackSize = 1;
        copy.setTagCompound(null);
        return copy;
    }

    private static final class WandTemplates {
        final ItemStack wand;
        final ItemStack staff;
        final ItemStack sceptre;

        private WandTemplates(ItemStack wand, ItemStack staff, ItemStack sceptre) {
            this.wand = wand;
            this.staff = staff;
            this.sceptre = sceptre;
        }
    }
}
