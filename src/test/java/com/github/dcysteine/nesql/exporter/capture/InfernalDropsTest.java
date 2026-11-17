package com.github.dcysteine.nesql.exporter.capture;

import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.*;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import java.lang.reflect.*;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Installed InfernalMobsCore getters with owned fields, never a running game or random world generation. */
final class InfernalDropsTest {
    static void run() throws Exception {
        Class<?> adapter = Class.forName("com.github.dcysteine.nesql.exporter.capture.InfernalDropsReferences");
        Method snapshot = adapter.getDeclaredMethod("snapshot", Object.class, boolean.class, JsonObject.class, Map.class);
        snapshot.setAccessible(true);
        Class<?> nativeCore = type("atomicstryker.infernalmobs.common.InfernalMobsCore");
        Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); unsafeField.setAccessible(true);
        Object core = ((sun.misc.Unsafe) unsafeField.get(null)).allocateInstance(nativeCore);
        ItemStack book = new ItemStack(Items.book, 3);
        NBTTagCompound tag = new NBTTagCompound(); tag.setString("author", "native infernal pool"); book.setTagCompound(tag);
        ArrayList<ItemStack> elite = new ArrayList<>(Arrays.asList(book, book.copy()));
        set(core, "dropIdListElite", elite);
        set(core, "dropIdListUltra", new ArrayList<>(Arrays.asList(new ItemStack(Items.enchanted_book, 1))));
        set(core, "dropIdListInfernal", new ArrayList<ItemStack>());
        set(core, "dimensionBlackList", new ArrayList<>(Arrays.asList(-1, 7)));
        for (String tier : Arrays.asList("Elite", "Ultra", "Inferno")) {
            set(core, Character.toLowerCase(tier.charAt(0)) + tier.substring(1) + "Rarity", tier.equals("Elite") ? 100 : tier.equals("Ultra") ? 5 : 3);
            set(core, "min" + tier + "Modifiers", tier.equals("Elite") ? 2 : tier.equals("Ultra") ? 6 : 11);
            set(core, "max" + tier + "Modifiers", tier.equals("Elite") ? 5 : tier.equals("Ultra") ? 10 : 15);
        }
        JsonObject source = object("owner", "mobsinfo", "handler", "com.kuba6000.mobsinfo.nei.MobHandlerInfernal", "key", "mobsinfo.mobhandlerinfernal");
        Map<String, ItemStack> specimens = new TreeMap<>();
        JsonArray records = (JsonArray) snapshot.invoke(null, core, true, source, specimens);
        if (records.size() != 7 || specimens.size() != 2) throw new AssertionError("Repeated pool entry lost or fake output created for empty pool");
        JsonObject first = records.get(2).getAsJsonObject().getAsJsonObject("data");
        JsonObject second = records.get(3).getAsJsonObject().getAsJsonObject("data");
        if (!first.get("amount").getAsString().equals("3") || !first.get("item").equals(second.get("item"))
                || first.get("entry").getAsInt() != 0 || second.get("entry").getAsInt() != 1)
            throw new AssertionError("Loaded quantities or duplicate selection provenance changed");
        JsonObject last = records.get(6).getAsJsonObject().getAsJsonObject("data");
        if (!last.get("tier").getAsString().equals("inferno") || last.get("entries").getAsInt() != 0)
            throw new AssertionError("Empty native pool not explicitly retained");
        String before = CanonicalJson.digest(records); elite.get(0).stackSize = 4;
        JsonArray changed = (JsonArray) snapshot.invoke(null, core, true, source, new HashMap<>());
        if (before.equals(CanonicalJson.digest(changed))) throw new AssertionError("Loaded quantity drift invisible");
        elite.get(0).stackSize = 3; set(core, "eliteRarity", 0);
        try {
            snapshot.invoke(null, core, true, source, new HashMap<>());
            throw new AssertionError("Invalid native rarity hidden by clamping");
        } catch (InvocationTargetException expected) { if (!(expected.getCause() instanceof Jobs.Fault)) throw expected; }
        set(core, "eliteRarity", 100);
        JsonArray disabled = (JsonArray) snapshot.invoke(null, null, false, source, new HashMap<>());
        if (disabled.size() != 1 || disabled.get(0).getAsJsonObject().getAsJsonObject("data").get("enabled").getAsBoolean())
            throw new AssertionError("Disabled native page became an unready fake pool");
        JsonArray items = array();
        for (Map.Entry<String, ItemStack> specimen : specimens.entrySet()) {
            ItemStack item = specimen.getValue();
            items.add(object("id", specimen.getKey(), "registry", net.minecraft.item.Item.itemRegistry.getNameForObject(item.getItem()),
                    "meta", item.getItemDamage(), "nbt", TypedNbt.encode(item.getTagCompound()), "tags", array()));
        }
        java.nio.file.Files.write(java.nio.file.Paths.get("build/native-tests/infernal-drops-contract.json"),
                CanonicalJson.bytes(object("records", records, "disabled", disabled, "items", items)));
        System.out.println("Infernal references: real getters, duplicate entries, quantity/NBT, native policies, empty/disabled and drift passed");
    }
    private static void set(Object owner, String name, Object value) throws Exception {
        Field field = owner.getClass().getDeclaredField(name); field.setAccessible(true); field.set(owner, value);
    }
}
