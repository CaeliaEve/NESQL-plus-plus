package com.github.dcysteine.nesql.exporter.plugin.nei;

import codechicken.nei.ItemList;
import com.github.dcysteine.nesql.exporter.main.Logger;
import com.github.dcysteine.nesql.exporter.plugin.PluginExportResult;
import com.github.dcysteine.nesql.exporter.util.IdUtil;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagByte;
import net.minecraft.nbt.NBTTagCompound;

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
    private static final AtomicBoolean LOGGED_ITEM_UNIVERSE_SUMMARY = new AtomicBoolean(false);
    private static final AtomicBoolean LOGGED_THAUMCRAFT_AUGMENTATION = new AtomicBoolean(false);

    private NeiItemUniverse() {}

    static Snapshot load() {
        boolean showSummary = LOGGED_ITEM_UNIVERSE_SUMMARY.compareAndSet(false, true);
        if (showSummary) {
            Logger.chatMessage("Preparation stage");
            Logger.chatMessage("Initializing item index...");
        }

        List<ItemStack> baseItems = ItemList.items != null ? ItemList.items : Collections.<ItemStack>emptyList();
        NeiItemUniverseCollector.Result<ItemStack> coreItems = NeiItemUniverseCollector.collect(
                baseItems,
                new NeiItemUniverseCollector.Normalizer<ItemStack, ItemStack>() {
                    @Override
                    public NeiItemUniverseCollector.Normalized<ItemStack> normalize(ItemStack stack) {
                        if (stack == null) {
                            throw new IllegalArgumentException("entry is null");
                        }
                        if (stack.getItem() == null) {
                            throw new IllegalArgumentException("entry item is null");
                        }
                        ItemStack normalized = IdUtil.stableIdentityCopy(stack);
                        return new NeiItemUniverseCollector.Normalized<ItemStack>(
                                normalized,
                                IdUtil.itemId(normalized));
                    }
                });
        LinkedHashMap<String, ItemStack> unique = new LinkedHashMap<String, ItemStack>(baseItems.size() + 256);
        unique.putAll(coreItems.keyedValues());

        int baseCount = unique.size();
        ThaumcraftAugmentation thaumcraftAugmentation = addThaumcraftWandVariants(unique);
        int thaumcraftAdded = thaumcraftAugmentation.added;
        if (thaumcraftAdded > 0 && LOGGED_THAUMCRAFT_AUGMENTATION.compareAndSet(false, true)) {
            Logger.MOD.info(
                    "Augmented NEI export item universe with {} Thaumcraft wand/staff/sceptre variants (base={}, total={})",
                    thaumcraftAdded,
                    baseCount,
                    unique.size());
        }

        if (showSummary) {
            Logger.MOD.info(
                    "Initialized NEI item universe: {} items/variants (base={}, thaumcraftAugmented={})",
                    unique.size(),
                    baseCount,
                    thaumcraftAdded);
            Logger.chatMessage(String.format("Scanning NEI item index: %,d entries", unique.size()));
            Logger.chatMessage(String.format("Discovered %,d items/variants", unique.size()));
            Logger.chatMessage("Building export context, please wait");
        }

        PluginExportResult itemUniverseResult = PluginExportResult.builder()
                .merge(coreItems.toPluginExportResult())
                .merge(thaumcraftAugmentation.result)
                .build();
        return new Snapshot(new ArrayList<ItemStack>(unique.values()), itemUniverseResult);
    }

    static void normalizeStableIdentity(ItemStack stack) {
        ItemStack normalized = IdUtil.stableIdentityCopy(stack);
        stack.setItemDamage(normalized.getItemDamage());
        stack.setTagCompound(normalized.getTagCompound());
    }

    private static boolean addUnique(Map<String, ItemStack> unique, ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            throw new IllegalStateException("Thaumcraft item-universe variant is empty");
        }

        ItemStack normalized = stack.copy();
        if (normalized == null) {
            throw new IllegalStateException("Thaumcraft item-universe variant copy returned null");
        }
        normalized.stackSize = 1;
        return unique.putIfAbsent(IdUtil.itemId(normalized), normalized) == null;
    }

    private static ThaumcraftAugmentation addThaumcraftWandVariants(Map<String, ItemStack> unique) {
        return runThaumcraftAugmentation(
                unique,
                new ThaumcraftPresenceProbe() {
                    @Override
                    public Class<?> loadRootClass() throws ClassNotFoundException {
                        return Class.forName("thaumcraft.common.config.ConfigItems");
                    }
                },
                new InstalledThaumcraftAugmenter() {
                    @Override
                    @SuppressWarnings("unchecked")
                    public ThaumcraftAugmentation augment(
                            Class<?> configItemsClass, Map<String, ?> target) throws Exception {
                        return augmentInstalledThaumcraft(
                                configItemsClass,
                                (Map<String, ItemStack>) target);
                    }
                });
    }

    static ThaumcraftAugmentation runThaumcraftAugmentation(
            Map<String, ?> unique,
            ThaumcraftPresenceProbe presenceProbe,
            InstalledThaumcraftAugmenter installedAugmenter) {
        final Class<?> configItemsClass;
        try {
            configItemsClass = presenceProbe.loadRootClass();
        } catch (ClassNotFoundException absent) {
            return new ThaumcraftAugmentation(
                    0,
                    PluginExportResult.skipped(
                            "thaumcraft-class-absent",
                            "Thaumcraft ConfigItems is not installed"));
        }

        try {
            return installedAugmenter.augment(configItemsClass, unique);
        } catch (Exception failure) {
            throwIfFatal(failure);
            return new ThaumcraftAugmentation(
                    0,
                    PluginExportResult.failed(
                            "thaumcraft-item-universe-augmentation-failed",
                            failure.getClass().getName() + ": " + safeMessage(failure)));
        }
    }

    private static ThaumcraftAugmentation augmentInstalledThaumcraft(
            Class<?> configItemsClass, Map<String, ItemStack> unique) throws Exception {
        Object wandItemValue = configItemsClass.getField("itemWandCasting").get(null);
        if (!(wandItemValue instanceof Item)) {
            throw new IllegalStateException("Thaumcraft ConfigItems.itemWandCasting is not an Item");
        }

        Item wandItem = (Item) wandItemValue;
        Class<?> wandItemClass = Class.forName("thaumcraft.common.items.wands.ItemWandCasting");
        if (!wandItemClass.isInstance(wandItem)) {
            throw new IllegalStateException("Thaumcraft wand item has an incompatible runtime type");
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
            throw new IllegalStateException("Thaumcraft wand rod/cap registry is empty");
        }

        WandTemplates templates = resolveTemplates(unique.values(), wandItem, isStaffMethod, isSceptreMethod);
        int before = unique.size();
        int attempted = 0;
        int failed = 0;
        PluginExportResult.Builder result = PluginExportResult.builder();

        for (Map.Entry<String, Object> rodEntry : rods.entrySet()) {
            Object rod = rodEntry.getValue();
            if (!wandRodClass.isInstance(rod)) {
                failed++;
                result.error(
                        "thaumcraft-wand-registry-entry-invalid",
                        "Invalid rod registry entry: " + rodEntry.getKey(),
                        true);
                continue;
            }

            boolean staffRod = staffRodClass.isInstance(rod);
            for (Map.Entry<String, Object> capEntry : caps.entrySet()) {
                Object cap = capEntry.getValue();
                if (!wandCapClass.isInstance(cap)) {
                    failed++;
                    result.error(
                            "thaumcraft-wand-registry-entry-invalid",
                            "Invalid cap registry entry: " + capEntry.getKey(),
                            true);
                    continue;
                }

                attempted += 2;
                failed += addVariant(
                        unique,
                        templates.wand,
                        setRodMethod,
                        setCapMethod,
                        rod,
                        cap,
                        false,
                        rodEntry.getKey() + "/" + capEntry.getKey() + "/wand",
                        result);
                failed += addVariant(
                        unique,
                        staffRod ? templates.staff : templates.sceptre,
                        setRodMethod,
                        setCapMethod,
                        rod,
                        cap,
                        !staffRod,
                        rodEntry.getKey() + "/" + capEntry.getKey() + (staffRod ? "/staff" : "/sceptre"),
                        result);
            }
        }

        int added = unique.size() - before;
        PluginExportResult.Status status = failed == 0
                ? PluginExportResult.Status.SUCCESS
                : (failed >= attempted && added == 0
                        ? PluginExportResult.Status.FAILED
                        : PluginExportResult.Status.PARTIAL);
        return new ThaumcraftAugmentation(
                added,
                result.status(status)
                        .count("thaumcraftVariantsAttempted", attempted)
                        .count("thaumcraftVariantsAdded", added)
                        .count("thaumcraftVariantsFailed", failed)
                        .build());
    }

    private static int addVariant(
            Map<String, ItemStack> unique,
            ItemStack template,
            Method setRodMethod,
            Method setCapMethod,
            Object rod,
            Object cap,
            boolean sceptre,
            String variantId,
            PluginExportResult.Builder result) {
        try {
            addUnique(
                    unique,
                    createVariantStack(template, setRodMethod, setCapMethod, rod, cap, sceptre));
            return 0;
        } catch (Exception failure) {
            throwIfFatal(failure);
            result.error(
                    "thaumcraft-wand-variant-failed",
                    variantId + ": " + failure.getClass().getName() + ": " + safeMessage(failure),
                    true);
            return 1;
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
            Method isSceptreMethod) throws Exception {
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
            } catch (Exception failure) {
                throwIfFatal(failure);
                throw new IllegalStateException(
                        "Thaumcraft item-universe template could not be classified",
                        failure);
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

    private static void throwIfFatal(Throwable failure) {
        Throwable current = failure;
        while (current instanceof java.lang.reflect.InvocationTargetException
                && ((java.lang.reflect.InvocationTargetException) current).getCause() != null) {
            current = ((java.lang.reflect.InvocationTargetException) current).getCause();
        }
        if (current instanceof Error) {
            throw (Error) current;
        }
    }

    private static String safeMessage(Throwable failure) {
        return failure.getMessage() == null ? "<no message>" : failure.getMessage();
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

    interface ThaumcraftPresenceProbe {
        Class<?> loadRootClass() throws ClassNotFoundException;
    }

    interface InstalledThaumcraftAugmenter {
        ThaumcraftAugmentation augment(
                Class<?> configItemsClass, Map<String, ?> target) throws Exception;
    }

    static final class ThaumcraftAugmentation {
        final int added;
        final PluginExportResult result;

        ThaumcraftAugmentation(int added, PluginExportResult result) {
            this.added = added;
            this.result = result;
        }
    }

    static final class Snapshot {
        private final List<ItemStack> items;
        private final PluginExportResult itemUniverseResult;

        private Snapshot(List<ItemStack> items, PluginExportResult itemUniverseResult) {
            this.items = Collections.unmodifiableList(items);
            this.itemUniverseResult = itemUniverseResult;
        }

        List<ItemStack> items() {
            return items;
        }

        PluginExportResult itemUniverseResult() {
            return itemUniverseResult;
        }
    }
}
