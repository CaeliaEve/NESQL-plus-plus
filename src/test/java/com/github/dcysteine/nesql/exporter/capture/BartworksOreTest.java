package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.google.gson.*;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;

/** Native registered layers and metadata callbacks, without constructing NEI's GUI. */
final class BartworksOreTest {
  static void run() throws Exception {
    Method snapshot =
        Class.forName("com.github.dcysteine.nesql.exporter.capture.BartworksOreReferences")
            .getDeclaredMethod(
                "snapshot",
                boolean.class,
                JsonObject.class,
                List.class,
                List.class,
                Map.class,
                int.class,
                Map.class);
    snapshot.setAccessible(true);
    set(cpw.mods.fml.common.Loader.instance(), "namedMods", new HashMap<>());
    cpw.mods.fml.common.Loader nativeLoader = cpw.mods.fml.common.Loader.instance();
    cpw.mods.fml.common.LoadController controller =
        new cpw.mods.fml.common.LoadController(nativeLoader);
    cpw.mods.fml.common.ModMetadata metadata = new cpw.mods.fml.common.ModMetadata();
    metadata.modId = "bartworks";
    metadata.name = "Bartworks native test fixture";
    metadata.version = "local-fixture";
    set(controller, "activeContainer", new cpw.mods.fml.common.DummyModContainer(metadata));
    set(nativeLoader, "modController", controller);
    invoke(
        type("bartworks.system.material.Werkstoff$GenerationFeatures"),
        null,
        "initPrefixLogic",
        new Class<?>[0]);
    Class<?> api = type("gregtech.api.GregTechAPI"),
        base = type("bartworks.system.oregen.BWOreLayer"),
        loader = type("bartworks.system.material.WerkstoffLoader");
    setStatic(api, "sBlockOres1", Blocks.stone);
    setStatic(loader, "BWOres", Blocks.dirt);
    setStatic(loader, "BWSmallOres", Blocks.cobblestone);
    Object mineral = field(loader, null, "Bismutite"),
        iron = field(type("gregtech.api.enums.Materials"), null, "Iron");
    @SuppressWarnings("unchecked")
    List<Object> active = (List<Object>) field(base, null, "sList");
    active.clear();
    Object lookup = field(base, null, "NEIMAP");
    invoke(lookup.getClass(), lookup, "clear", new Class<?>[0]);
    setStatic(base, "sWeight", 0);
    Class<?> material = type("gregtech.api.interfaces.ISubTagContainer");
    Constructor<?> constructor =
        type("bartworks.system.oregen.BWWorldGenRoss128b")
            .getConstructor(
                String.class,
                boolean.class,
                int.class,
                int.class,
                int.class,
                int.class,
                int.class,
                material,
                material,
                material,
                material);
    constructor.newInstance(
        "ore.fixture.enabled", true, 5, 45, 7, 3, 12, mineral, iron, mineral, iron);
    constructor.newInstance(
        "ore.fixture.disabled", false, 10, 70, 9, 2, 8, mineral, mineral, iron, iron);
    @SuppressWarnings("unchecked")
    Map<Short, List<Object>> map =
        (Map<Short, List<Object>>) invoke(lookup.getClass(), lookup, "asMap", new Class<?>[0]);
    Map<String, ItemStack> visuals = new TreeMap<>();
    JsonObject source =
        object(
            "owner",
            "bartworks",
            "handler",
            "bartworks.neiHandler.OreNEIHandler",
            "key",
            "bartworks.neiHandler.OreNEIHandler");
    Object generation = invoke(mineral.getClass(), mineral, "getGenerationFeatures", new Class<?>[0]);
    set(generation, "enforceUnification", false);
    @SuppressWarnings("unchecked")
    Map<String, Object> cache = (Map<String, Object>)field(type("bartworks.system.oredict.OreDictHandler"), null, "cache");
    String oreKey = "ore" + ((String)invoke(mineral.getClass(), mineral, "getVarName", new Class<?>[0])).replace(" ", "");
    cache.put(oreKey, org.apache.commons.lang3.tuple.Pair.of(net.minecraft.item.Item.getIdFromItem(net.minecraft.init.Items.apple), (short)7));
    ItemStack nativeSample = (ItemStack)invoke(mineral.getClass(), mineral, "get", new Class<?>[]{type("gregtech.api.enums.OrePrefixes")}, field(type("gregtech.api.enums.OrePrefixes"), null, "ore"));
    require(nativeSample.getItem() == net.minecraft.init.Items.apple && nativeSample.getItemDamage() == 7,
        "Native Werkstoff did not prefer the registered unification specimen");
    JsonArray rows =
        (JsonArray)
            snapshot.invoke(null, true, source, active, Arrays.asList(mineral), map, 7, visuals);
    JsonObject definition = rows.get(0).getAsJsonObject().getAsJsonObject("data");
    require(
        definition.getAsJsonObject("counts").get("layers").getAsInt() == 2,
        "Disabled layer in native lookup lost");
    JsonObject enabled = rows.get(1).getAsJsonObject().getAsJsonObject("data");
    require(
        enabled.getAsJsonArray("roles").size() == 4 && enabled.get("bwMask").getAsInt() == 10,
        "Mixed GT/BW role ownership lost");
    require(
        rows.get(2).getAsJsonObject().getAsJsonObject("data").get("activeOrdinal").isJsonNull(),
        "Disabled generator became active");
    int meta = ((Number) invoke(mineral.getClass(), mineral, "getmID", new Class<?>[0])).intValue();
    JsonObject matching = null;
    for (JsonElement row : rows) {
      JsonObject data = row.getAsJsonObject().getAsJsonObject("data");
      if (data.get("kind").getAsString().equals("bartworksOreLookup")
          && data.get("meta").getAsInt() == meta) matching = data;
    }
    require(
        matching != null && matching.getAsJsonArray("layers").size() == 4,
        "Native duplicate metadata membership was deduplicated");
    JsonObject nativeMaterial = null;
    for (JsonElement row : rows) {
      JsonObject data = row.getAsJsonObject().getAsJsonObject("data");
      if (data.get("kind").getAsString().equals("bartworksOreMaterial")) nativeMaterial = data;
    }
    require(
        nativeMaterial != null && nativeMaterial.getAsJsonArray("samples").size() == 2,
        "Ore and small-ore variants lost");
    ItemStack capturedSample = visuals.get(nativeMaterial.getAsJsonArray("samples").get(0).getAsJsonObject().get("item").getAsString());
    require(nativeMaterial.get("meta").getAsInt() == meta && capturedSample.getItem() == net.minecraft.init.Items.apple
        && capturedSample.getItemDamage() == 7, "Werkstoff identity was confused with the unified item's physical metadata");
    JsonArray facts = array();
    for (Map.Entry<String, ItemStack> entry : visuals.entrySet())
      facts.add(
          object(
              "id",
              entry.getKey(),
              "registry",
              net.minecraft.item.Item.itemRegistry.getNameForObject(entry.getValue().getItem()),
              "meta",
              entry.getValue().getItemDamage(),
              "tags",
              array(),
              "nbt",
              com.github.dcysteine.nesql.exporter.source.TypedNbt.encode(
                  entry.getValue().getTagCompound())));
    java.nio.file.Files.write(
        java.nio.file.Paths.get("build/native-tests/bartworks-ores-contract.json"),
        CanonicalJson.bytes(object("records", rows, "items", facts)));
    try {
      snapshot.invoke(null, true, source, active, Arrays.asList(mineral), map, 8, new TreeMap<>());
      throw new AssertionError("Wrong weight sum accepted");
    } catch (InvocationTargetException error) {
      require(
          error.getCause() instanceof com.github.dcysteine.nesql.exporter.task.Jobs.Fault,
          "Unexpected weight failure");
    }
    try {
      snapshot.invoke(
          null,
          false,
          source,
          Collections.emptyList(),
          Collections.emptyList(),
          Collections.emptyMap(),
          0,
          new TreeMap<>());
      throw new AssertionError("Unready empty accepted");
    } catch (InvocationTargetException error) {
      require(
          ((com.github.dcysteine.nesql.exporter.task.Jobs.Fault) error.getCause())
              .code.equals("handler_not_ready"),
          "Wrong readiness code");
    }
    require(
        ((JsonArray)
                    snapshot.invoke(
                        null,
                        true,
                        source,
                        Collections.emptyList(),
                        Collections.emptyList(),
                        Collections.emptyMap(),
                        0,
                        new TreeMap<>()))
                .size()
            == 1,
        "Ready empty rejected");
    Object handler = allocate(type("bartworks.neiHandler.OreNEIHandler"));
    require(
        Recipes.adapter((codechicken.nei.recipe.ICraftingHandler) handler)
            .name()
            .equals("BARTWORKS_ORES_REFERENCE"),
        "Consumption template still used");
    System.out.println(
        "Bartworks ore: native mixed roles, disabled lookup layer, duplicate memberships, variants, readiness and dedicated route passed");
  }

  private static void require(boolean ok, String text) {
    if (!ok) throw new AssertionError(text);
  }

  private static void set(Object target, String name, Object value) throws Exception {
    for (Class<?> owner = target.getClass(); owner != null; owner = owner.getSuperclass())
      try {
        Field f = owner.getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
        return;
      } catch (NoSuchFieldException absent) {
      }
    throw new NoSuchFieldException(name);
  }

  private static void setStatic(Class<?> owner, String name, Object value) throws Exception {
    Field f = owner.getDeclaredField(name);
    f.setAccessible(true);
    f.set(null, value);
  }

  private static Object allocate(Class<?> owner) throws Exception {
    Class<?> unsafe = Class.forName("sun.misc.Unsafe");
    Field f = unsafe.getDeclaredField("theUnsafe");
    f.setAccessible(true);
    return unsafe.getMethod("allocateInstance", Class.class).invoke(f.get(null), owner);
  }
}
