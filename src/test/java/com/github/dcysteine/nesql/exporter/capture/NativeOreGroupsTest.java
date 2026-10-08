package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.ItemList;
import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.source.Identity;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.google.gson.*;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.oredict.OreDictionary;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Real Forge getOres snapshot; no live registry, tooltip, world, or display mutation. */
public final class NativeOreGroupsTest {
    @SuppressWarnings("unchecked") static void run() throws Exception {
        Item palette = new Item() {
            @Override public String getUnlocalizedName(ItemStack stack) {
                return "fixture." + net.minecraft.item.ItemDye.field_150923_a[getDamage(stack)];
            }
        }.setHasSubtypes(true);
        Item.itemRegistry.addObject(31006, "fixture:ore_palette", palette);
        ItemStack wildcard = new ItemStack(palette, 3, 32767);
        NBTTagCompound tag = new NBTTagCompound(); tag.setString("fixture", "raw wildcard"); wildcard.setTagCompound(tag);
        ItemStack exact = new ItemStack(palette, -4, 7);
        OreDictionary.registerOre("nesqlOreOrder", wildcard);
        OreDictionary.registerOre("nesqlOreOrder", exact);
        OreDictionary.registerOre("nesqlOreOrder", exact.copy());
        require(OreDictionary.getOres("nesqlOreOrder", false).size() == 2, "Native registration duplicate suppression changed");
        // Mods can mutate legacy underlying lists. Preserve the actual list even when it contains duplicates.
        Field raw = OreDictionary.class.getDeclaredField("idToStack"); raw.setAccessible(true);
        ((List<List<ItemStack>>) raw.get(null)).get(OreDictionary.getOreID("nesqlOreOrder")).add(exact.copy());
        OreDictionary.getOreID("nesqlOreEmpty");
        ItemStack unknown = new ItemStack(palette, 2, 14);
        OreDictionary.registerOre("nesqlOreWithoutDisplay", unknown);
        List<ItemStack> nativeRows = OreDictionary.getOres("nesqlOreOrder", false);
        JsonArray before = new JsonArray(); for (ItemStack row : nativeRows) before.add(template(row));
        JsonObject proof = object("group", "nesqlOreOrder", "entries", before, "empty", OreDictionary.getOres("nesqlOreEmpty", false).size());
        Files.createDirectories(Paths.get("build/native-tests"));
        Files.write(Paths.get("build/native-tests/ore-registry-snapshot.json"), CanonicalJson.bytes(proof));
        require(before.get(0).getAsJsonObject().get("meta").getAsInt() == 32767, "Wildcard raw metadata was normalized");
        require(before.get(1).equals(before.get(2)), "The native list did not expose duplicate entries");
        try { wildcard.getDisplayName(); throw new AssertionError("Expected unsafe wildcard display"); }
        catch (ArrayIndexOutOfBoundsException expected) { /* A registry template is not a display stack. */ }
        System.out.println("Native ore snapshot: raw wildcard/NBT/count, signed count, duplicate sequence and empty group observed");

        Class<?> captureType;
        try { captureType = Class.forName("com.github.dcysteine.nesql.exporter.capture.OreGroups"); }
        catch (ClassNotFoundException missing) { throw new AssertionError("Native ore groups have no typed capture", missing); }
        Constructor<?> constructor = captureType.getDeclaredConstructor(); constructor.setAccessible(true);
        Method capture = captureType.getDeclaredMethod("capture", Facts.class); capture.setAccessible(true);
        // Only known concrete instances may be used as display facts.
        ItemList.itemMap = com.google.common.collect.ArrayListMultimap.create();
        ItemStack display = new ItemStack(palette, 1, 2), exactDisplay = exact.copy(); exactDisplay.stackSize = 1;
        ItemList.itemMap.put(palette, wildcard.copy());
        ItemList.itemMap.put(palette, display.copy()); ItemList.itemMap.put(palette, exactDisplay.copy());
        Facts facts = new Facts("en_US");
        for (ItemStack stack : Arrays.asList(display, exactDisplay)) facts.restore("items", object("id", id(stack)));
        Object cursor = constructor.newInstance();
        List<JsonObject> groups = new ArrayList<>(), members = new ArrayList<>();
        int steps = 0;
        boolean done;
        do {
            try { done = (Boolean) capture.invoke(cursor, facts); }
            catch (InvocationTargetException failure) { throw new AssertionError("Ore capture failed", failure.getCause()); }
            for (Facts.Record record : facts.drain().records) {
                if (record.kind.equals("ore-groups")) groups.add(record.value);
                if (record.kind.equals("ore-members")) members.add(record.value);
            }
            require(++steps < 100000, "Unbounded ore capture");
        } while (!done);
        JsonObject group = groups.stream().filter(row -> row.get("name").getAsString().equals("nesqlOreOrder")).findFirst().get();
        require(group.get("members").getAsInt() == 3, "Native duplicate positions lost");
        List<JsonObject> entries = new ArrayList<>();
        for (JsonObject row : members) if (row.get("group").equals(group.get("id"))) entries.add(row);
        require(entries.size() == 3, "Membership records omitted");
        for (int index = 0; index < entries.size(); index++) {
            require(entries.get(index).get("index").getAsInt() == index, "Registration order changed");
            require(entries.get(index).getAsJsonObject("template").equals(before.get(index)), "Original template changed");
        }
        require(!entries.get(1).get("id").equals(entries.get(2).get("id")), "Duplicate list entries share an identity");
        require(entries.get(0).get("display").getAsString().equals(id(display)), "Wildcard must select a verified concrete example");
        JsonObject empty = groups.stream().filter(row -> row.get("name").getAsString().equals("nesqlOreEmpty")).findFirst().get();
        require(empty.get("members").getAsInt() == 0, "Empty native group disappeared");
        JsonObject unavailable = groups.stream().filter(row -> row.get("name").getAsString().equals("nesqlOreWithoutDisplay")).findFirst().get();
        require(members.stream().filter(row -> row.get("group").equals(unavailable.get("id"))).allMatch(row -> row.get("display").isJsonNull()), "Unobserved metadata became a fabricated display item");
        JsonArray after = new JsonArray(); for (ItemStack row : nativeRows) after.add(template(row));
        require(before.equals(after) && template(wildcard).get("meta").getAsInt() == 32767, "Capture mutated native registry stacks");
        require(groups.stream().map(row -> row.get("name").getAsString()).sorted().collect(java.util.stream.Collectors.toList())
                .equals(groups.stream().map(row -> row.get("name").getAsString()).collect(java.util.stream.Collectors.toList())), "Group names are not sorted");
        Object changedCursor = constructor.newInstance();
        List<ItemStack> mutable = ((List<List<ItemStack>>) raw.get(null)).get(OreDictionary.getOreID("nesqlOreOrder"));
        boolean changed = false, rejected = false;
        try {
            do {
                try { done = (Boolean) capture.invoke(changedCursor, facts); }
                catch (InvocationTargetException failure) {
                    if (changed && failure.getCause() instanceof com.github.dcysteine.nesql.exporter.task.Jobs.Fault) { rejected = true; break; }
                    throw failure;
                }
                for (Facts.Record row : facts.drain().records) if (!changed && row.kind.equals("ore-members") && row.value.get("group").equals(group.get("id"))) {
                    mutable.set(0, new ItemStack(palette, 8, 3)); changed = true;
                }
            } while (!done);
        } finally { mutable.set(0, wildcard.copy()); }
        require(changed && rejected, "Same-size ore registry replacement was not detected");
        System.out.println("Native ore groups: typed rows, exact templates, safe display examples, bounded capture and immutable registry passed");
    }
    private static JsonObject template(ItemStack stack) { return object("registry", Item.itemRegistry.getNameForObject(stack.getItem()),
            "meta", Items.feather.getDamage(stack), "nbt", TypedNbt.encode(stack.getTagCompound()), "amount", Integer.toString(stack.stackSize)); }
    private static String id(ItemStack stack) { return Identity.item(Item.itemRegistry.getNameForObject(stack.getItem()), Items.feather.getDamage(stack), TypedNbt.encode(stack.getTagCompound())); }
    private static void require(boolean okay, String message) { if (!okay) throw new AssertionError(message); }
}
