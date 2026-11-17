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

/** One installed MobDrop specimen exercises rules the generic NEI result accessor loses. */
final class MobDropsTest {
    static void run() throws Exception {
        Class<?> adapter = Class.forName("com.github.dcysteine.nesql.exporter.capture.MobDropsReferences");
        Method snapshot = adapter.getDeclaredMethod("snapshot", List.class, Map.class, boolean.class, JsonObject.class, Map.class);
        snapshot.setAccessible(true);
        Class<?> nativeDrop = type("com.kuba6000.mobsinfo.api.MobDrop");
        ItemStack stack = new ItemStack(Items.book, 3);
        NBTTagCompound tag = new NBTTagCompound(); tag.setString("author", "native drop"); stack.setTagCompound(tag);
        Object drop = nativeDrop.getMethod("create", ItemStack.class).invoke(null, stack);
        nativeDrop.getField("chance").setInt(drop, 12500);
        nativeDrop.getField("enchantable").set(drop, 30);
        nativeDrop.getField("lootable").setBoolean(drop, true);
        nativeDrop.getField("playerOnly").setBoolean(drop, true);
        HashMap<Integer, Integer> damages = new LinkedHashMap<>(); damages.put(3, 2); damages.put(7, 5);
        nativeDrop.getField("damages").set(drop, damages);
        Class<?> modifier = type("com.kuba6000.mobsinfo.api.IChanceModifier$BaseChance");
        Object nativeModifier = modifier.getConstructor(double.class).newInstance(3.25);
        ((List<Object>) nativeDrop.getField("chanceModifiers").get(drop)).add(nativeModifier);
        nativeDrop.getField("variableChance").setBoolean(drop, true);
        ((List<String>) nativeDrop.getField("additionalInfo").get(drop)).add("Native conditional note");
        Page page = new Page(); Rule rule = new Rule(drop, page.mob);
        Map<String, Object> rules = Collections.singletonMap(page.mobname, rule);
        JsonObject source = object("owner", "mobsinfo", "handler", "com.kuba6000.mobsinfo.nei.MobHandler", "key", "mobsinfo.mobhandler");
        Map<String, ItemStack> specimens = new TreeMap<>();
        JsonArray records = (JsonArray) snapshot.invoke(null, Collections.singletonList(page), rules, true, source, specimens);
        JsonObject row = records.get(2).getAsJsonObject().getAsJsonObject("data");
        if (records.size() != 3 || row.get("baseChance").getAsInt() != 12500 || !row.get("amount").getAsString().equals("3")
                || !row.get("playerOnly").getAsBoolean() || row.get("enchantmentPower").getAsInt() != 30
                || !row.getAsJsonArray("modifiers").get(0).getAsJsonObject().get("payload").getAsString().equals("400a000000000000")
                || !row.getAsJsonArray("damages").toString().equals("[{\"meta\":3,\"weight\":2},{\"meta\":7,\"weight\":5}]"))
            throw new AssertionError("Native rules lost, probability clamped or modifier serialization changed");
        if (records.get(1).getAsJsonObject().getAsJsonObject("data").get("visible").getAsBoolean())
            throw new AssertionError("Locked mob became discovered");
        String before = CanonicalJson.digest(records);
        damages.put(7, 6); rule.mMaxDamageChance = 8;
        JsonArray changed = (JsonArray) snapshot.invoke(null, Collections.singletonList(page), rules, true, source, new HashMap<>());
        if (before.equals(CanonicalJson.digest(changed))) throw new AssertionError("Damage weight drift invisible");
        damages.put(7, 5); rule.mMaxDamageChance = 7;
        try {
            snapshot.invoke(null, Collections.singletonList(page), Collections.emptyMap(), true, source, new HashMap<>());
            throw new AssertionError("Missing authoritative rule cache silently accepted");
        } catch (InvocationTargetException expected) { if (!(expected.getCause() instanceof Jobs.Fault)) throw expected; }
        page.fail = true;
        try {
            snapshot.invoke(null, Collections.singletonList(page), rules, true, source, new HashMap<>());
            throw new AssertionError("Native visibility callback swallowed");
        } catch (InvocationTargetException expected) { if (!(expected.getCause() instanceof IllegalStateException)) throw expected; }
        JsonArray items = array();
        for (Map.Entry<String, ItemStack> specimen : specimens.entrySet()) {
            ItemStack item = specimen.getValue();
            items.add(object("id", specimen.getKey(), "registry", net.minecraft.item.Item.itemRegistry.getNameForObject(item.getItem()),
                    "meta", item.getItemDamage(), "nbt", TypedNbt.encode(item.getTagCompound()), "tags", array()));
        }
        java.nio.file.Files.write(java.nio.file.Paths.get("build/native-tests/mob-drops-contract.json"),
                CanonicalJson.bytes(object("records", records, "items", items)));
        page.fail = false; page.visible = true;
        JsonArray visible = (JsonArray) snapshot.invoke(null, Collections.singletonList(page), rules, true, source, new HashMap<>());
        java.nio.file.Files.write(java.nio.file.Paths.get("build/native-tests/mob-drops-visible-contract.json"),
                CanonicalJson.bytes(object("records", visible, "items", items)));
        System.out.println("Mob references: raw chance, amount/NBT, modifiers, damage weights, hidden state and failures passed");
    }
    public static final class Page {
        final String mobname = "FixtureMob", localizedName = "Native fixture mob", mod = "Fixture";
        final Object mob = new Object(); final float maxHealth = 20;
        final int infernaltype = 1; final boolean isPeacefulAllowed = false, isUsableInVial = true;
        final List<ItemStack> mInput = Collections.singletonList(new ItemStack(Items.spawn_egg));
        final List<String> additionalInformation = Collections.singletonList("Native mob note");
        final Set<Object> spawnList = Collections.emptySet(); boolean fail, visible;
        public boolean isUnlocked() { if (fail) throw new IllegalStateException("native visibility failure"); return visible; }
    }
    public static final class Rule {
        final List<Object> mOutputs; final Object entity; final String entityName = "FixtureMob"; int mMaxDamageChance = 7;
        Rule(Object drop, Object entity) { mOutputs = Arrays.asList(drop); this.entity = entity; }
    }
}
