package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;

import codechicken.nei.ItemList;
import com.github.dcysteine.nesql.exporter.source.Probe;
import com.gtnewhorizon.structurelib.StructureLib;
import com.gtnewhorizon.structurelib.alignment.constructable.IMultiblockInfoContainer;
import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import com.github.dcysteine.nesql.exporter.source.Identity;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import static com.github.dcysteine.nesql.exporter.source.Json.*;
import net.minecraft.tileentity.TileEntity;

/** The installed Botania constructor, provider and tile tick run in an owned preview. */
final class NativeBotaniaStructureTest {
  @SuppressWarnings("unchecked")
  static void run() throws Exception {
    try (NativeRegistryScope scope = new NativeRegistryScope()) {
      NativeBotaniaSpecialTest.active(scope);
      Class<?> blocks = type("vazkii.botania.common.block.ModBlocks");
      Block enchanter = (Block) type("vazkii.botania.common.block.mana.BlockEnchanter").newInstance();
      Block pylon = (Block) type("vazkii.botania.common.block.BlockPylon").newInstance();
      java.lang.reflect.Constructor<?> flowerConstructor = type("vazkii.botania.common.block.BlockModFlower").getDeclaredConstructor();
      flowerConstructor.setAccessible(true);
      Block flower = (Block) flowerConstructor.newInstance();
      scope.replace(blocks, null, "enchanter", enchanter);
      scope.replace(blocks, null, "pylon", pylon);
      scope.replace(blocks, null, "flower", flower);
      scope.replace(type("vazkii.botania.api.BotaniaAPI"), null, "internalHandler",
          type("vazkii.botania.common.core.handler.InternalMethodHandler").newInstance());
      scope.replace(StructureLib.class, null, "itemConstructableTrigger", Items.stick);
      scope.replace(type("vazkii.botania.common.Botania"), null, "structureLibLoaded", true);
      scope.replace(type("vazkii.botania.common.Botania"), null, "proxy",
          type("vazkii.botania.common.core.proxy.CommonProxy").newInstance());
      scope.replace(ItemList.class, null, "loadFinished", true);
      scope.replace(ItemList.class, null, "items", new ArrayList<ItemStack>());
      Map<String, Class<?>> names = new HashMap<>((Map<String, Class<?>>) field(TileEntity.class, null, "nameToClassMap"));
      Map<Class<?>, String> types = new HashMap<>((Map<Class<?>, String>) field(TileEntity.class, null, "classToNameMap"));
      for (String name : new String[] {"TilePylon", "TileEnchanter"}) {
        Class<?> nativeTile = type("vazkii.botania.common.block.tile." + name);
        names.put("nesql:" + name, nativeTile); types.put(nativeTile, "nesql:" + name);
      }
      scope.replace(TileEntity.class, null, "nameToClassMap", names);
      scope.replace(TileEntity.class, null, "classToNameMap", types);
      Class<?> tile = type("vazkii.botania.common.block.tile.TileEnchanter");
      String key = tile.getCanonicalName();
      IMultiblockInfoContainer<?> previous = IMultiblockInfoContainer.MULTIBLOCK_MAP.get(key);
      try {
        tile.getMethod("makeMultiblockSet").invoke(null);
        IMultiblockInfoContainer<?> provider = IMultiblockInfoContainer.MULTIBLOCK_MAP.get(key);
        if (provider == null) throw new AssertionError("Native enchanter provider was not registered");
        Structures.Machine source = Structures.Machine.compat("Botania", key, provider,
            new ItemStack(enchanter));
        if (!source.identity().equals("structure_dc4e99079dc02c90c9d08225a077e2eeae5ee29bbf6c15e6b65784fd24d53a12"))
          throw new AssertionError("Native enchanter fixture no longer matches the historical identity");
        try (Preview preview = new Preview(source, Probe.defaults(), null, true)) {
          int steps = 0;
          while (!preview.build()) if (++steps > 64) throw new AssertionError("Enchanter construction stalled");
          TileEntity controller = preview.world.getTileEntity(0, 64, 0);
          if (controller == null || controller.getClass() != tile)
            throw new AssertionError("Native enchanter completion removed its controller");
          boolean displayFlower = preview.world.blocks.values().stream().anyMatch(block ->
              block.getClass().getName().equals("vazkii.botania.common.block.tile.TileEnchanter$StructureLibFlower"));
          if (!displayFlower || preview.world.blocks.size() < 20
              || (Boolean) tile.getMethod("canEnchanterExist", net.minecraft.world.World.class,
                  int.class, int.class, int.class, int.class).invoke(null, preview.world, 0, 64, 0, 0))
            throw new AssertionError("Native display geometry was replaced with a claimed operational machine");
          Facts facts = new Facts("en_US");
          Set<String> known = (Set<String>) field(facts, "items");
          // This data-profile fixture checks native geometry/NBT, without invoking item UI callbacks.
          for (Map.Entry<Long, Block> cell : preview.world.blocks.entrySet()) {
            long coordinate = cell.getKey();
            int x = (short) (coordinate >>> 24), y = (int) (coordinate & 255),
                z = (short) (coordinate >>> 8);
            ItemStack picked = cell.getValue().getPickBlock(new MovingObjectPosition(x, y, z, 1,
                Vec3.createVectorHelper(x, y, z)), preview.world, x, y, z);
            if (picked != null && picked.getItem() != null)
              known.add(Identity.item(net.minecraft.item.Item.itemRegistry.getNameForObject(picked.getItem()),
                  picked.getItemDamage(), TypedNbt.encode(picked.getTagCompound())));
          }
          String captured = null;
          JsonArray records = array();
          JsonObject build = null;
          for (int step = 0; captured == null && step < 256; step++) {
            captured = preview.capture(facts, source.identity());
            for (Facts.Record record : facts.drain().records) {
              records.add(object("kind", record.kind, "value", record.value));
              if (record.kind.equals("builds")) build = record.value;
            }
          }
          if (captured == null || build == null
              || !build.get("completion").getAsString().equals("nativeDisplayBeforeControllerTick")
              || !build.get("facing").getAsString().equals("north"))
            throw new AssertionError("Native display snapshot lost its explicit serialized completion boundary");
          java.nio.file.Files.write(java.nio.file.Paths.get(System.getProperty("user.dir"),
              "botania-enchanter-structure-contract.json"), CanonicalJson.bytes(object(
                  "source", source.origin(), "structure", source.identity(), "records", records)));
        }
      } finally {
        if (previous == null) IMultiblockInfoContainer.MULTIBLOCK_MAP.remove(key);
        else IMultiblockInfoContainer.MULTIBLOCK_MAP.put(key, previous);
      }
    }
    System.out.println("Native Botania structure: historical identity, owned provider construction and controller retention passed");
  }
}
