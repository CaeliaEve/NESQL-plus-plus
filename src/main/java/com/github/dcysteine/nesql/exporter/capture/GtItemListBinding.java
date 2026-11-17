package com.github.dcysteine.nesql.exporter.capture;

import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonArray;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Pinned ItemList.get(1) result without sanityCheck/lazy unification cache writes.
 * Bindings belong to a reader; no global cache, registration, or native callback execution.
 */
final class GtItemListBinding {
    private static final String BW = "bartworks.system.material.BWMetaGeneratedItems";
    private static final String EMPTY_MATERIAL_CELL = "cellEmpty";
    private static final String META99_PREFIX = "meta99Prefix";
    private static final String META99 = "gregtech.common.items.MetaGeneratedItem99";
    private final String name;
    private final Item origin;
    private final int metadata;
    private final Read initial;

    static String containerEntry(ItemStack input, String owner) {
        if (owner.equals(BW)) return bwEntry(bwPrefix(input.getItem()));
        int meta = Items.feather.getDamage(input);
        if (owner.equals(META99)) return META99_PREFIX;
        if (owner.equals("gregtech.common.items.MetaGeneratedItem98")) return "Cell_Empty";
        if (owner.equals("gregtech.common.items.MetaGeneratedItem01")
                && (meta >= 32430 && meta <= 32461 || meta == 32465 || meta == 32466)) return "Spray_Empty";
        if (owner.equals("gregtech.common.items.MetaGeneratedItem02")) {
            if (meta >= 32000 && meta < 32100) return "ThermosCan_Empty";
            if (meta >= 32100 && meta < 32200) return "Bottle_Empty";
        }
        return null;
    }
    private static Object bwPrefix(Item item) {
        Class<?> owner = type(BW);
        if (!owner.isInstance(item)) throw fault("Native BW container class identity differs");
        return field(owner, item, "orePrefixes");
    }

    private static String bwEntry(Object prefix) {
        if (prefix == null) return null;
        if (!(prefix instanceof Enum<?>) || ((Enum<?>)prefix).getDeclaringClass() != type("gregtech.api.enums.OrePrefixes"))
            throw fault("Unadapted BW container prefix");
        switch (((Enum<?>)prefix).name()) {
            case "cell": case "cellPlasma": case "cellMolten": return EMPTY_MATERIAL_CELL;
            case "capsule": return "FR_WaxCapsule";
            case "capsuleMolten": return "FR_RefractoryCapsule";
            default: return null;
        }
    }

    /** Materials.Empty.getCells(1), distinct from the ItemList.Cell_Empty entry. */
    static GtItemListBinding emptyMaterialCell() { return new GtItemListBinding(EMPTY_MATERIAL_CELL); }
    /** The pinned BW getter; keeps the item's prefix identity in the reader guard. */
    static GtItemListBinding bartworks(Item item) {
        if (!ItemCallbacks.method(item,"getContainerItem","getContainerItem",ItemStack.class).getDeclaringClass().getName().equals(BW))
            throw fault("Unadapted BW container callback owner");
        String entry = bwEntry(bwPrefix(item));
        return entry == null ? null : new GtItemListBinding(entry, new ItemStack(item, 1, 0));
    }

    GtItemListBinding(String name) {
        this(name, null);
    }
    GtItemListBinding(String name, ItemStack input) {
        this(admitted(name), input, false);
    }

    private static String admitted(String name) {
        MagicApi.version("GtItemListBinding","gregtech_nh");
        return name;
    }

    /** The enclosing CAL reader admits this family before creating its owned bindings. */
    static GtItemListBinding calImprint(Object entry) {
        if (!(entry instanceof Enum<?>) || ((Enum<?>)entry).getDeclaringClass() != type("gregtech.api.enums.ItemList")
                || !((Enum<?>)entry).name().startsWith("CircuitImprint_"))
            throw fault("Unadapted CAL ItemList imprint");
        return new GtItemListBinding(((Enum<?>)entry).name(), null, true);
    }

    private GtItemListBinding(String name, ItemStack input, boolean imprint) {
        if (!imprint && !name.equals("Spray_Empty") && !name.equals("ThermosCan_Empty") && !name.equals("Bottle_Empty")
                && !name.equals("FR_WaxCapsule") && !name.equals("FR_RefractoryCapsule") && !name.equals("Cell_Empty")
                && !name.equals(EMPTY_MATERIAL_CELL) && !name.equals(META99_PREFIX))
            throw fault("Unadapted ItemList container: " + name);
        this.name = name;
        Item candidate = input == null ? null : input.getItem();
        boolean bw = false;
        if (candidate != null) for (Class<?> owner = candidate.getClass(); owner != null; owner = owner.getSuperclass())
            if (owner.getName().equals(BW)) { bw = true; break; }
        origin = bw || name.equals(META99_PREFIX) ? candidate : null;
        metadata = input == null ? 0 : Items.feather.getDamage(input);
        initial = read();
        verify();
    }

    ItemStack result() { verify(); return initial.result == null ? null : initial.result.copy(); }

    void verify() {
        Read current;
        try { current = read(); }
        catch (RuntimeException failure) {
            Jobs.Fault changed = changed(); changed.initCause(failure); throw changed;
        }
        if (!CanonicalJson.digest(initial.facts).equals(CanonicalJson.digest(current.facts))
                || initial.anchors.size() != current.anchors.size()) throw changed();
        for (int index = 0; index < initial.anchors.size(); index++)
            if (initial.anchors.get(index) != current.anchors.get(index)) throw changed();
    }

    private Read read() {
        Jobs.checkpoint();
        Read read = new Read();
        if (name.equals(META99_PREFIX)) return meta99Prefix(read);
        if (origin != null) {
            read.anchor(origin);
            Object prefix = bwPrefix(origin); read.anchor(prefix);
            if (!name.equals(bwEntry(prefix))) throw fault("Native BW container prefix binding changed");
        }
        if (name.equals(EMPTY_MATERIAL_CELL)) return emptyMaterialCell(read);
        Object entry = field(type("gregtech.api.enums.ItemList"), null, name); read.anchor(entry);
        boolean unset = (Boolean) field(entry, "mHasNotBeenSet");
        read.facts.add(object("unset", unset, "deprecated", field(entry, "mDeprecated"), "warned", field(entry, "mWarned")));
        if (unset) throw new Jobs.Fault("handler_not_ready", "Native GT ItemList is unset: " + name);
        ItemStack original = read.stack(field(entry, "mStack"));
        if (invalid(original)) return read;

        Class<?> unifier = type("gregtech.api.util.GTOreDictUnificator");
        Object table = field(unifier, null, "sItemStack2DataMap");
        Object association = read.lookup(table, original, false, true);
        if (association == null) association = read.lookup(table, original, true, true);
        read.anchor(association);
        ItemStack target = original;
        if (association != null) {
            if (association.getClass() != type("gregtech.api.objects.ItemData")) throw fault("Unadapted native GT ItemData");
            Object prefix = field(association, "mPrefix"), materialStack = field(association, "mMaterial");
            read.anchor(prefix); read.anchor(materialStack);
            Object material = materialStack == null ? null : field(materialStack, "mMaterial"); read.anchor(material);
            boolean blacklisted = (Boolean) field(association, "mBlackListed");
            read.facts.add(object("blacklisted", blacklisted));
            if (prefix != null && material != null) {
                if (!(prefix instanceof Enum<?>) || ((Enum<?>)prefix).getDeclaringClass() != type("gregtech.api.enums.OrePrefixes")
                        || materialStack.getClass() != type("gregtech.api.objects.MaterialStack")) throw fault("Unadapted native GT prefix/material");
                if (!blacklisted) {
                    boolean registered = (Boolean) field(type("gregtech.api.GregTechAPI"), null, "sUnificationEntriesRegistered");
                    read.facts.add(object("registered", registered));
                    boolean pendingBlacklist = false;
                    if (!registered) {
                        Object blacklist = field(unifier, null, "sNoUnificationList");
                        pendingBlacklist = read.lookup(blacklist, original, false, false) != null
                                || read.lookup(blacklist, original, true, false) != null;
                    }
                    if (!pendingBlacklist) {
                        ItemStack cached = read.stack(field(association, "mUnificationTarget"));
                        if (cached == null) {
                            String key = ((Enum<?>)prefix).name() + (String)field(material, "mName");
                            Object names = field(unifier, null, "sName2StackMap"); read.anchor(names);
                            if (names == null || names.getClass() != java.util.HashMap.class) throw fault("Unadapted native GT target map");
                            read.facts.add(object("targetKey", key, "targetMapSize", ((java.util.Map<?,?>)names).size()));
                            cached = read.stack(((java.util.Map<?,?>)names).get(key));
                        }
                        if (!invalid(cached)) target = cached;
                    }
                }
            }
        }
        // Native get copies the target with the original NBT; ItemList then copies amount 1.
        read.result = target.copy();
        read.result.setTagCompound(original.getTagCompound() == null ? null
                : (net.minecraft.nbt.NBTTagCompound)original.getTagCompound().copy());
        read.result.stackSize = 1;
        read.facts.add(RecipeRow.stackEvidence(read.result));
        return read;
    }

    /** The private 5.09.51.482 prefix selector and its borrowed container, without invoking callbacks. */
    private Read meta99Prefix(Read read) {
        Class<?> owner = type(META99), prefixes = type("gregtech.api.enums.OrePrefixes");
        if (origin == null || !owner.isInstance(origin)) throw fault("Native GT99 container owner differs");
        read.anchor(origin);
        Object raw = field(owner, null, "CRACKED_CELL_TYPES");
        int count = ((Number)field(owner, null, "NUM_CRACKED_CELL_TYPES")).intValue();
        if (!(raw instanceof com.google.common.collect.ImmutableList) || count < 0 || count > 32
                || ((List<?>)raw).size() != count) throw fault("Unadapted native GT99 prefix table");
        read.anchor(raw); read.facts.add(value(count));
        int meta = metadata;
        Object prefix = null;
        if (meta >= 0 && meta < 1000) prefix = field(prefixes, null, "cellMolten");
        else if (meta >= 10000 && meta < 10000 + count * 1000) prefix = ((List<?>)raw).get(meta / 1000 - 10);
        read.anchor(prefix);
        if (prefix == null) return read;
        if (!(prefix instanceof Enum<?>) || ((Enum<?>)prefix).getDeclaringClass() != prefixes)
            throw fault("Unadapted native GT99 selected prefix");
        read.facts.add(value(((Enum<?>)prefix).name()));
        ItemStack container = read.stack(field(prefixes, prefix, "mContainerItem"));
        if (container != null) {
            if (container.getItem() == null || container.stackSize <= 0) throw fault("Invalid GT99 prefix container");
            read.result = container.copy();
        }
        return read;
    }

    private Read emptyMaterialCell(Read read) {
        Class<?> prefixes = type("gregtech.api.enums.OrePrefixes"), materials = type("gregtech.api.enums.Materials");
        Object prefix = field(prefixes, null, "cell"), empty = field(materials, null, "Empty");
        if (!(prefix instanceof Enum<?>) || ((Enum<?>)prefix).getDeclaringClass() != prefixes
                || empty == null || empty.getClass() != materials) throw fault("Unadapted native empty material cell roles");
        read.anchor(prefix); read.anchor(empty);
        boolean preventable = read.member(field(prefixes, null, "mPreventableComponents"), prefix, java.util.LinkedList.class, prefixes);
        boolean disabled = preventable && read.member(field(prefixes, prefix, "mDisabledItems"), empty, java.util.HashSet.class, materials);
        read.facts.add(object("preventable", preventable, "disabled", disabled));
        if (disabled) return read;
        Object materialName = field(materials, empty, "mName");
        if (!(materialName instanceof String) || ((String)materialName).length() > 4096) throw fault("Unadapted native empty material name");
        String key = ((Enum<?>)prefix).name() + (String)materialName;
        Object names = field(type("gregtech.api.util.GTOreDictUnificator"), null, "sName2StackMap");
        if (names == null || names.getClass() != java.util.HashMap.class) throw fault("Unadapted native GT material target map");
        read.anchor(names); read.facts.add(object("targetKey", key, "targetMapSize", ((java.util.Map<?,?>)names).size()));
        ItemStack target = read.stack(((java.util.Map<?,?>)names).get(key));
        if (target == null) {
            // Native getFirstOre selects Forge's first ItemStack, even if invalid.
            // Do not call getOres:
            // its missing-name path registers a new ore ID in this installed version.
            List<?> members = NativeOreTable.members(key);
            Object oreNames = field(OreDictionary.class, null, "nameToId"), oreTable = field(OreDictionary.class, null, "idToStack");
            read.anchor(oreNames); read.anchor(oreTable); read.anchor(members);
            read.facts.add(object("oreId", ((java.util.Map<?,?>)oreNames).get(key), "oreCount", ((List<?>)oreTable).size()));
            for (Object member : members) {
                Jobs.checkpoint();
                ItemStack candidate = read.stack(member);
                if (candidate != null) { target = candidate; break; }
            }
        }
        if (!invalid(target)) {
            read.result = target.copy(); read.result.stackSize = 1;
            read.facts.add(RecipeRow.stackEvidence(read.result));
        }
        return read;
    }

    private static boolean invalid(ItemStack stack) { return stack == null || stack.getItem() == null || stack.stackSize < 0; }

    private static final class Read {
        final List<Object> anchors = new ArrayList<>();
        final JsonArray facts = array();
        ItemStack result;
        void anchor(Object value) { anchors.add(value); }
        boolean member(Object raw, Object wanted, Class<?> storage, Class<?> memberType) {
            if (raw == null || raw.getClass() != storage || ((java.util.Collection<?>)raw).size() > 65536)
                throw fault("Unadapted GT container membership storage");
            anchor(raw); boolean found = false;
            for (Object member : (java.util.Collection<?>)raw) {
                Jobs.checkpoint();
                if (member == null || (member instanceof Enum<?> ? ((Enum<?>)member).getDeclaringClass() != memberType : member.getClass() != memberType))
                    throw fault("Unadapted GT container membership entry");
                anchor(member); if (member == wanted) found = true;
            }
            facts.add(object("members", ((java.util.Collection<?>)raw).size(), "found", found));
            return found;
        }
        ItemStack stack(Object raw) {
            anchor(raw);
            if (raw == null) { facts.add(value(null)); return null; }
            if (raw.getClass() != ItemStack.class) throw fault("Unadapted native GT ItemStack callback");
            ItemStack stack = (ItemStack)raw; anchor(stack.getItem());
            if (stack.getItem() != null) { NativeStackAudit.auditRaw(stack); auditHash(stack); }
            facts.add(stack.getItem() == null ? object("missingItem",true,"amount",stack.stackSize) : RecipeRow.stackEvidence(stack));
            return stack;
        }
        Object lookup(Object table, ItemStack query, boolean wildcard, boolean map) {
            anchor(table);
            String expected = map ? "it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap"
                    : "it.unimi.dsi.fastutil.objects.ObjectOpenCustomHashSet";
            if (table == null || !table.getClass().getName().equals(expected)) throw fault("Unadapted GT association/blacklist storage");
            Object strategy = field(table, "strategy"); anchor(strategy);
            if (strategy == null || strategy != field(type("gregtech.api.objects.GTItemStack"), null, "ITEMSTACK_HASH_STRATEGY2")
                    || !strategy.getClass().getName().equals("gregtech.api.objects.GTItemStack$1")) throw fault("Unadapted GT ItemStack hash strategy");
            // Native strategy calls the offered item's hashCode. Reject foreign code before executing it.
            auditHash(query);
            Object[] keys = (Object[])field(table, "key"); anchor(keys);
            Object[] values = map ? (Object[])field(table, "value") : null; if (map) anchor(values);
            int n = ((Number)field(table,"n")).intValue(), mask = ((Number)field(table,"mask")).intValue(),
                    size = ((Number)field(table,"size")).intValue();
            if (n <= 0 || (n & (n - 1)) != 0 || mask != n - 1 || keys.length != n + 1
                    || map && values.length != n + 1 || size < 0 || size > n) throw fault("Malformed GT native hash storage");
            facts.add(object("capacity",n,"size",size,"wildcard",wildcard));
            int meta = wildcard ? 32767 : Items.feather.getDamage(query);
            int hash = System.identityHashCode(query.getItem()) * 38197 + meta;
            int mixed = hash * 0x9E3779B9, slot = (mixed ^ mixed >>> 16) & mask;
            for (int visited = 0; visited < Math.min(n,4096); visited++, slot = (slot + 1) & mask) {
                Jobs.checkpoint();
                ItemStack key = stack(keys[slot]);
                if (key == null) return null;
                if (key.getItem() == query.getItem() && Items.feather.getDamage(key) == meta) {
                    Object found = map ? values[slot] : key; anchor(found); return found;
                }
            }
            throw fault("GT native hash probe exceeds its traversal budget");
        }
    }
    private static void auditHash(ItemStack stack) {
        try { if (stack.getItem().getClass().getMethod("hashCode").getDeclaringClass() != Object.class)
            throw fault("Unadapted GT Item hashCode callback"); }
        catch (NoSuchMethodException failure) { throw fault("Missing GT item hashCode"); }
    }
    private static Jobs.Fault fault(String message) { return new Jobs.Fault("recipe_unsupported", message); }
    private static Jobs.Fault changed() { return new Jobs.Fault("recipe_changed", "Native GT ItemList/unification binding changed"); }
}
