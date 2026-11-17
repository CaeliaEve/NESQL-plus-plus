package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import net.minecraft.init.Items;
import net.minecraft.item.*;
import net.minecraft.nbt.*;

/** Real installed IC2 callback objects in an isolated item registry; no GUI/world or live game. */
final class LatheReferencesTest {
  static void run() throws Exception {
    // IC2's static platform selection expects Forge's side delegate even for pure data callbacks.
    cpw.mods.fml.common.FMLCommonHandler.instance()
        .beginLoading(
            (cpw.mods.fml.common.IFMLSidedHandler)
                Proxy.newProxyInstance(
                    LatheReferencesTest.class.getClassLoader(),
                    new Class<?>[] {cpw.mods.fml.common.IFMLSidedHandler.class},
                    (proxy, method, args) ->
                        method.getName().equals("getSide")
                            ? cpw.mods.fml.relauncher.Side.SERVER
                            : null));
    Field access = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
    access.setAccessible(true);
    sun.misc.Unsafe memory = (sun.misc.Unsafe) access.get(null);
    Class<?> material = type("ic2.core.item.resources.ItemLatheDefault$LatheMaterial"),
        blank = type("ic2.core.item.resources.ItemLatheDefault");
    List<ItemStack> panel = new ArrayList<>();
    Field delegate = Item.class.getField("delegate");
    delegate.setAccessible(true);
    for (String name : new String[] {"WOOD", "IRON"}) {
      Item item = (Item) memory.allocateInstance(blank);
      delegate.set(
          item, new cpw.mods.fml.common.registry.RegistryDelegate.Delegate<>(item, Item.class));
      item.setMaxStackSize(1);
      item.setHasSubtypes(true);
      Field field = blank.getDeclaredField("material");
      field.setAccessible(true);
      field.set(item, Enum.valueOf((Class) material, name));
      Item.itemRegistry.addObject(
          name.equals("WOOD") ? 12100 : 12101,
          "lathe_fixture_" + name.toLowerCase(Locale.ROOT),
          item);
      panel.add(new ItemStack(item));
    }
    Item tool = (Item) memory.allocateInstance(type("ic2.core.item.tool.ItemLathingTool"));
    delegate.set(
        tool, new cpw.mods.fml.common.registry.RegistryDelegate.Delegate<>(tool, Item.class));
    tool.setMaxDamage(50);
    tool.setMaxStackSize(1);
    Item.itemRegistry.addObject(12102, "lathe_fixture_tool", tool);
    panel.add(new ItemStack(tool));
    // The native IRON callback returns this runtime registry field; an explicit vanilla specimen
    // isolates its amount semantics.
    Field dust = type("ic2.core.Ic2Items").getField("smallIronDust");
    Object previous = dust.get(null);
    dust.set(null, new ItemStack(Items.iron_ingot, 2));
    try {
      JsonObject source =
          object(
              "owner",
              "IC2",
              "handler",
              "ic2.neiIntegration.core.recipehandler.LatheRecipeHandler",
              "key",
              "native-lathe-domain");
      Ic2LatheRecipes.Snapshot snapshot = Ic2LatheRecipes.snapshot(source, panel);
      JsonObject counts =
          snapshot
              .records
              .get(0)
              .getAsJsonObject()
              .getAsJsonObject("data")
              .getAsJsonObject("counts");
      require(
          counts.get("workpieces").getAsInt() == 2 && counts.get("tools").getAsInt() == 1,
          "Native workpiece/tool discovery differs");
      require(
          counts.get("states").getAsInt() == 3368 && counts.get("toolStates").getAsInt() == 50,
          "Reachable native state closure was truncated");
      require(
          counts.get("transitions").getAsInt() == 13310,
          "Native single-lane transition closure differs");
      require(
          !panel.get(0).hasTagCompound(),
          "Reading native metadata mutated the source panel workpiece");
      boolean threshold = false, broke = false;
      for (JsonElement raw : snapshot.records) {
        JsonObject data = raw.getAsJsonObject().getAsJsonObject("data");
        if (data.get("kind").getAsString().equals("latheState"))
          for (JsonElement edge : data.getAsJsonArray("transitions")) {
            JsonObject chance = edge.getAsJsonObject().getAsJsonObject("roll");
            if (chance.get("thresholdBits").getAsString().equals("3dcccccd"))
              threshold = chance.get("tickets").getAsInt() == 1677722;
          }
        if (data.get("kind").getAsString().equals("latheToolState")
            && data.get("damage").getAsInt() == 49) broke = data.get("after").isJsonNull();
      }
      require(threshold && broke, "Float nextFloat probability or final tool break was simplified");
      ItemStack invalid = panel.get(0).copy();
      NBTTagCompound root = new NBTTagCompound(), state = new NBTTagCompound();
      state.setInteger("l0", 0);
      root.setTag("state", state);
      invalid.setTagCompound(root);
      try {
        Ic2LatheRecipes.snapshot(source, Arrays.asList(invalid));
        throw new AssertionError("Illegal native lathe state accepted");
      } catch (Jobs.Fault fault) {
        require(fault.code.equals("reference_invalid"), "Wrong illegal lathe state classification");
      }
      JsonArray items = array();
      for (Map.Entry<String, ItemStack> entry : snapshot.specimens.entrySet()) {
        ItemStack item = entry.getValue();
        items.add(
            object(
                "id",
                entry.getKey(),
                "registry",
                Item.itemRegistry.getNameForObject(item.getItem()),
                "meta",
                item.getItemDamage(),
                "nbt",
                TypedNbt.encode(item.getTagCompound())));
      }
      Path output = Paths.get("build/native-tests/lathe-reference-contract.json");
      Files.createDirectories(output.getParent());
      Files.write(
          output,
          object("records", snapshot.records, "items", items)
              .toString()
              .getBytes(StandardCharsets.UTF_8));
    } finally {
      dust.set(null, previous);
    }
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }
}
