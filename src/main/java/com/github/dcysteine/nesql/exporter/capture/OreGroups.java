package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.ItemList;
import com.github.dcysteine.nesql.exporter.source.Identity;
import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonObject;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.source.Json.object;

/** Registry templates and optional observed display examples are separate facts. */
final class OreGroups {
    private final String[] names;
    private final Map<Item, Examples> examples = new IdentityHashMap<>();
    private final com.google.common.collect.ListMultimap<Item, ItemStack> itemMap = ItemList.itemMap;
    private final byte[][] snapshots;
    private final int[] sizes;
    private java.security.MessageDigest snapshot = digest(), verified = digest();
    private int exampleCount, verifyGroup, verifyMember;
    private boolean verifying;
    private Iterator<Examples> verifyExamples;
    private Examples verifyExample;
    private List<ItemStack> entries;
    private int group, member, size;
    private String id;

    OreGroups() {
        names = Arrays.stream(OreDictionary.getOreNames()).filter(Objects::nonNull).sorted().toArray(String[]::new);
        if (names.length > 65536) throw fault("Too many registered ore groups");
        snapshots = new byte[names.length][];
        sizes = new int[names.length];
    }

    int count() { return names.length; }
    int completed() { return group; }

    /** At most sixteen rows or two milliseconds of native work per scheduling slice. */
    boolean capture(Facts facts) {
        if (ItemList.itemMap != itemMap) throw fault("NEI display registry changed during ore capture");
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(2);
        if (verifying) return verify(deadline);
        int work = 0, rows = 0;
        while (group < names.length) {
            Jobs.checkpoint();
            if (work > 0 && (rows >= 16 || System.nanoTime() >= deadline)) return false;
            if (entries == null) {
                entries = OreDictionary.getOres(names[group], false);
                size = entries.size();
                sizes[group] = size;
                if (size > 1000000) throw fault("Ore group exceeds its membership budget: " + names[group]);
                JsonObject origin = object("owner", "Forge", "handler", "net.minecraftforge.oredict.OreDictionary", "key", names[group]);
                id = Identity.origin("oregroup", origin);
                facts.row("ore-groups", object("id", id, "source", origin, "name", names[group], "order", group, "members", size));
                work++; rows++;
            }
            if (entries.size() != size) throw fault("Ore registration list changed during capture: " + names[group]);
            if (member == size) { snapshots[group] = snapshot.digest(); group++; member = 0; entries = null; continue; }
            if (rows >= 16 || System.nanoTime() >= deadline) return false;
            ItemStack original = entries.get(member);
            if (original == null || original.getItem() == null) throw fault("Empty ore registration: " + names[group] + "/" + member);
            ItemStack pattern = original.copy();
            String registry = Item.itemRegistry.getNameForObject(pattern.getItem());
            if (registry == null || Item.itemRegistry.getObject(registry) != pattern.getItem()) throw fault("Unregistered ore template item");
            int meta = Items.feather.getDamage(pattern);
            JsonObject template = object("registry", registry, "meta", meta, "nbt", TypedNbt.encode(pattern.getTagCompound()), "amount", Integer.toString(pattern.stackSize));
            String identity = Identity.item(registry, meta, template.get("nbt"));
            Examples known = examples.get(pattern.getItem());
            if (known == null) {
                known = new Examples(pattern.getItem(), itemMap);
                exampleCount += known.size;
                if (exampleCount > 262144) throw fault("All ore display examples exceed their total budget");
                examples.put(pattern.getItem(), known);
            }
            while (!known.complete()) {
                Jobs.checkpoint();
                if (work > 0 && System.nanoTime() >= deadline) return false;
                known.next(); work++;
            }
            ItemStack display = meta == OreDictionary.WILDCARD_VALUE ? known.first : known.exact.get(identity);
            // Native matching receives copies, preserving both registry and NEI-owned stacks.
            String displayId = display != null && OreDictionary.itemMatches(pattern.copy(), display.copy(), false)
                    ? facts.item(display.copy()) : null;
            facts.row("ore-members", object("id", id + ".member_" + String.format(Locale.ROOT, "%08x", member),
                    "group", id, "index", member, "template", template, "display", displayId));
            snapshot.update(CanonicalJson.bytes(template));
            member++; work++; rows++;
        }
        verifying = true;
        return false;
    }

    /** A bounded second pass catches same-size replacements and in-place NBT edits. */
    private boolean verify(long deadline) {
        int checked = 0;
        while (verifyGroup < names.length) {
            Jobs.checkpoint();
            if (checked > 0 && (checked >= 16 || System.nanoTime() >= deadline)) return false;
            List<ItemStack> current = OreDictionary.getOres(names[verifyGroup], false);
            if (current.size() != sizes[verifyGroup]) throw fault("Ore registration list changed during capture: " + names[verifyGroup]);
            if (verifyMember < current.size()) {
                verified.update(CanonicalJson.bytes(template(current.get(verifyMember++)))); checked++;
            } else {
                if (!Arrays.equals(verified.digest(), snapshots[verifyGroup])) throw fault("Ore registration templates changed during capture: " + names[verifyGroup]);
                verifyGroup++; verifyMember = 0; checked++;
            }
        }
        if (verifyExamples == null) verifyExamples = examples.values().iterator();
        while (verifyExample != null || verifyExamples.hasNext()) {
            Jobs.checkpoint();
            if (checked > 0 && (checked >= 16 || System.nanoTime() >= deadline)) return false;
            if (verifyExample == null) verifyExample = verifyExamples.next();
            if (verifyExample.nativeRows.size() != verifyExample.size) throw fault("NEI display examples changed during ore capture");
            if (verifyMember < verifyExample.size) {
                verified.update(CanonicalJson.bytes(template(verifyExample.nativeRows.get(verifyMember++)))); checked++;
            } else {
                if (!Arrays.equals(verified.digest(), verifyExample.fingerprint)) throw fault("NEI display templates changed during ore capture");
                verifyExample = null; verifyMember = 0; checked++;
            }
        }
        String[] currentNames = Arrays.stream(OreDictionary.getOreNames()).filter(Objects::nonNull).sorted().toArray(String[]::new);
        if (!Arrays.equals(names, currentNames)) throw fault("Ore group names changed during capture");
        return true;
    }

    private static JsonObject template(ItemStack stack) {
        if (stack == null || stack.getItem() == null) throw fault("Empty native ore template");
        return object("registry", Item.itemRegistry.getNameForObject(stack.getItem()), "meta", Items.feather.getDamage(stack),
                "nbt", TypedNbt.encode(stack.getTagCompound()), "amount", Integer.toString(stack.stackSize));
    }

    private static java.security.MessageDigest digest() {
        try { return java.security.MessageDigest.getInstance("SHA-256"); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }

    private static final class Examples {
        final List<ItemStack> nativeRows;
        final Item item;
        final int size;
        final Map<String, ItemStack> exact = new HashMap<>();
        ItemStack first;
        final java.security.MessageDigest hash = digest();
        byte[] fingerprint;
        int index;
        Examples(Item item, com.google.common.collect.ListMultimap<Item, ItemStack> itemMap) {
            this.item = item;
            nativeRows = itemMap == null ? Collections.emptyList() : itemMap.get(item);
            size = nativeRows.size();
            if (size > 262144) throw fault("Ore display examples exceed their budget");
        }
        boolean complete() {
            if (nativeRows.size() != size) throw fault("NEI display examples changed during ore capture");
            if (index == size && fingerprint == null) fingerprint = hash.digest();
            return index == size;
        }
        void next() {
            ItemStack stack = nativeRows.get(index++);
            if (stack == null || stack.getItem() == null) throw fault("Empty NEI ore display example");
            if (stack.getItem() != item) throw fault("NEI ore display example has a different registry item");
            hash.update(CanonicalJson.bytes(template(stack)));
            int meta = Items.feather.getDamage(stack);
            if (meta == OreDictionary.WILDCARD_VALUE) return;
            ItemStack copy = stack.copy();
            String key = Identity.item(Item.itemRegistry.getNameForObject(copy.getItem()), meta, TypedNbt.encode(copy.getTagCompound()));
            exact.putIfAbsent(key, copy);
            if (first == null) first = copy;
        }
    }

    private static Jobs.Fault fault(String message) { return new Jobs.Fault("ore_groups", message); }
}
