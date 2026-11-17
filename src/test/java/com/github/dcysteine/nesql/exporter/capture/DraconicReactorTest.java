package com.github.dcysteine.nesql.exporter.capture;

import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.google.gson.*;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import java.lang.reflect.*;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Actual installed ConfigHandler/ContainerReactor constants, with owned registered item specimens. */
final class DraconicReactorTest {
    static void run() throws Exception {
        Class<?> adapter = Class.forName("com.github.dcysteine.nesql.exporter.capture.DraconicReactorReferences");
        Class<?> config = Class.forName("com.brandon3055.draconicevolution.common.handler.ConfigHandler");
        set(config, "reactorFuelStorage", 10368); set(config, "reactorFuelValue", 16); set(config, "reactorChaosValue", 16);
        set(config, "reactorOutputMultiplier", 1.5D); set(config, "reactorFuelUsageMultiplier", 1D);
        set(config, "linearReactorFuelUsage", true); set(config, "enableReactorBigBoom", true); set(config, "enableAutomation", false);
        Class<?> constants = Class.forName("com.brandon3055.draconicevolution.common.container.ContainerReactor");
        Method snapshot = adapter.getDeclaredMethod("snapshot", JsonObject.class, ItemStack[].class, Class.class, Class.class, Map.class);
        snapshot.setAccessible(true);
        ItemStack[] specimens = {new ItemStack(Items.emerald), new ItemStack(Items.diamond), new ItemStack(Items.iron_ingot),
                new ItemStack(Items.gold_nugget, 1, 1), new ItemStack(Items.quartz, 1, 0), new ItemStack(Items.quartz, 1, 1),
                new ItemStack(Items.quartz, 1, 2), new ItemStack(Items.nether_star)};
        JsonObject source = object("owner", "DraconicEvolution", "handler", "com.brandon3055.draconicevolution.integration.nei.ReactorNEIHandler", "key", "draconicevolution.reactor");
        Map<String, ItemStack> items = new TreeMap<>();
        JsonArray rows = (JsonArray) snapshot.invoke(null, source, specimens, config, constants, items);
        JsonObject data = rows.get(0).getAsJsonObject().getAsJsonObject("data");
        if (rows.size() != 1 || items.size() != 8 || data.get("consuming").getAsBoolean()
                || !data.getAsJsonArray("fuels").get(0).getAsJsonObject().get("units").getAsString().equals("1296")
                || !data.getAsJsonArray("products").get(3).getAsJsonObject().get("units").getAsString().equals("11664"))
            throw new AssertionError("Reactor diagram became a one-item-to-one-shard consuming recipe");
        String before = CanonicalJson.digest(rows); set(config, "reactorOutputMultiplier", 2D);
        if (before.equals(CanonicalJson.digest((JsonArray) snapshot.invoke(null, source, specimens, config, constants, new HashMap<>()))))
            throw new AssertionError("Reactor runtime configuration drift was omitted");
        set(config, "reactorOutputMultiplier", 1.5D);
        JsonArray records = array();
        for (Map.Entry<String, ItemStack> entry : items.entrySet()) {
            ItemStack item = entry.getValue();
            records.add(object("id", entry.getKey(), "registry", net.minecraft.item.Item.itemRegistry.getNameForObject(item.getItem()),
                    "meta", item.getItemDamage(), "nbt", TypedNbt.encode(item.getTagCompound()), "tags", array()));
        }
        java.nio.file.Files.write(java.nio.file.Paths.get("build/native-tests/draconic-reactor-contract.json"),
                CanonicalJson.bytes(object("records", rows, "items", records)));
        System.out.println("Draconic reactor: installed configured units, eight roles, no fake consumption, configuration drift passed");
    }
    private static void set(Class<?> owner, String name, Object value) throws Exception { owner.getField(name).set(null, value); }
}
