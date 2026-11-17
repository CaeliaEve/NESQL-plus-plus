package com.github.dcysteine.nesql.exporter.capture;

import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.google.gson.*;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import java.lang.reflect.*;
import java.util.*;
import java.util.function.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

final class ItemAspectReferencesTest {
    static void run() throws Exception {
        Class<?> adapter = Class.forName("com.github.dcysteine.nesql.exporter.capture.ItemAspectReferences");
        Method snapshot = adapter.getDeclaredMethod("snapshot", List.class, Function.class, Function.class, boolean.class, JsonObject.class, Map.class);
        snapshot.setAccessible(true);
        JsonObject source = object("owner", "aspectrecipeindex", "handler",
                "com.gtnewhorizons.aspectrecipeindex.nei.ItemsContainingAspectHandler", "key", "item-aspects");
        ItemStack book = new ItemStack(Items.book, 1);
        NBTTagCompound tag = new NBTTagCompound(); tag.setString("author", "native specimen"); book.setTagCompound(tag);
        Function<Integer, ItemStack> cache = key -> key == 7 ? book : null;
        Function<ItemStack, JsonElement> aspects = stack -> Aspects.amounts(new AspectList().add(Aspect.AIR, 12).add(Aspect.FIRE, 3));
        Map<String, ItemStack> specimens = new TreeMap<>();
        List<String> keys = Arrays.asList("@7", "#7", "@8", "@invalid");
        JsonArray records = (JsonArray) snapshot.invoke(null, keys, cache, aspects, false, source, specimens);
        JsonArray configured = (JsonArray) snapshot.invoke(null, keys, cache, aspects, true, source, new HashMap<>());
        if (!records.get(0).getAsJsonObject().getAsJsonObject("data").get("visibility").getAsString().equals("nativePlayerKnowledge")
                || !configured.get(0).getAsJsonObject().getAsJsonObject("data").get("visibility").getAsString().equals("allRegisteredAspects")
                || records.get(0).getAsJsonObject().get("domain").equals(configured.get(0).getAsJsonObject().get("domain")))
            throw new AssertionError("Configured scanned-item visibility lost from content identity");
        if (records.size() != 5 || specimens.size() != 1) throw new AssertionError("Lost scan provenance or duplicate identity");
        JsonObject first = records.get(1).getAsJsonObject().getAsJsonObject("data");
        if (!first.get("status").getAsString().equals("available") || first.getAsJsonArray("aspects").size() != 2
                || !records.get(3).getAsJsonObject().getAsJsonObject("data").get("status").getAsString().equals("cacheMiss")
                || !records.get(4).getAsJsonObject().getAsJsonObject("data").get("status").getAsString().equals("invalidKey")
                || book.stackSize != 1) throw new AssertionError("Aspect quantity became item consumption or missing evidence");
        JsonArray empty = (JsonArray) snapshot.invoke(null, Collections.emptyList(), cache, aspects, false, source, new HashMap<>());
        if (empty.size() != 1) throw new AssertionError("Ready empty scanned list must retain definition");
        JsonArray absent = (JsonArray) snapshot.invoke(null, null, cache, aspects, false, source, new HashMap<>());
        if (!empty.equals(absent)) throw new AssertionError("Loaded native null scan list differs from empty native query");
        JsonArray changed = (JsonArray) snapshot.invoke(null, keys, cache,
                (Function<ItemStack, JsonElement>) stack -> Aspects.amounts(new AspectList().add(Aspect.AIR, 13)), false, source, new HashMap<>());
        if (records.equals(changed)) throw new AssertionError("Bonus aspect drift invisible");
        try {
            snapshot.invoke(null, keys, cache, (Function<ItemStack, JsonElement>) stack -> { throw new IllegalStateException("native failure"); }, false, source, new HashMap<>());
            throw new AssertionError("Native callback error swallowed");
        } catch (InvocationTargetException expected) { if (!(expected.getCause() instanceof IllegalStateException)) throw expected; }
        JsonArray items = array();
        for (Map.Entry<String, ItemStack> entry : specimens.entrySet()) {
            ItemStack stack = entry.getValue();
            items.add(object("id", entry.getKey(), "registry", Item.itemRegistry.getNameForObject(stack.getItem()),
                    "meta", stack.getItemDamage(), "nbt", TypedNbt.encode(stack.getTagCompound()), "tags", array(), "aspects", aspects.apply(stack)));
        }
        JsonArray definitions = array();
        for (Aspect aspect : Arrays.asList(Aspect.AIR, Aspect.FIRE)) definitions.add(object("id", Aspects.id(aspect),
                "source", Aspects.source(aspect), "components", array(), "discovered", true));
        java.nio.file.Path target = java.nio.file.Paths.get("build/native-tests/item-aspects-contract.json");
        java.nio.file.Files.write(target, CanonicalJson.bytes(object("records", records, "configuredRecords", configured, "items", items, "aspects", definitions)));
        System.out.println("Item aspect references: quantities, NBT, scan provenance, cache gaps, empty and callback guards passed");
    }
}
