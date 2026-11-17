package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.gtnewhorizon.structurelib.alignment.constructable.IConstructable;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import java.lang.reflect.*;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

/** The actual GT cleanroom constructs geometry without a StructureLib definition. */
final class NativeGtConstructionTest {
  static void run() throws Exception {
    try (NativeRegistryScope scope = new NativeRegistryScope()) {
      NativeCoreFixesTest.version("gregtech_nh", "5.09.51.482");
      Class<?> type = type("gregtech.common.tileentities.machines.multi.MTECleanroom");
      IConstructable nativeMachine = (IConstructable) scope.allocate(type);
      require(nativeMachine.getStructureDefinition() == null
          && type.getMethod("getStructureDefinition").getDeclaringClass() == IConstructable.class,
          "Pinned cleanroom no longer uses the construction-only native contract");
      scope.replace(type, null, "CASING_BLOCK", Blocks.stone);
      scope.replace(type, null, "FILTER_BLOCK", Blocks.glass);
      for (int count : new int[] {1, 2, 8}) {
        try (Preview.Space world = new Preview.Space()) {
          IGregTechTileEntity base = (IGregTechTileEntity) Proxy.newProxyInstance(
              NativeGtConstructionTest.class.getClassLoader(),
              new Class<?>[] {IGregTechTileEntity.class}, (proxy, method, args) -> {
                switch (method.getName()) {
                  case "getWorld": return world;
                  case "getXCoord": case "getZCoord": return 0;
                  case "getYCoord": return (short) 64;
                  default: throw new AssertionError("Unexpected cleanroom operation: " + method.getName());
                }
              });
          scope.replace(type("gregtech.api.metatileentity.MetaTileEntity"), nativeMachine,
              "mBaseMetaTileEntity", base);
          world.setBlock(0, 64, 0, Blocks.furnace, 0, 2);
          ItemStack trigger = new ItemStack(Items.stick, count);
          nativeMachine.construct(trigger, false);
          int radius = Math.min(count, 7), height = Math.max(radius * 2, 3);
          int side = radius * 2 + 1;
          int expected = side * side * (height + 1) - (side - 2) * (side - 2) * (height - 1);
          require(world.blocks.size() == expected && trigger.stackSize == count,
              "Native cleanroom geometry or trigger ownership changed");
          require(world.getBlock(radius, 64 - height, radius) == Blocks.stone
              && world.getBlockMetadata(radius, 64 - height, radius) == 2
              && world.getBlock(0, 64, 1) == (radius == 1 ? Blocks.stone : Blocks.glass)
              && world.getBlockMetadata(0, 64, 1) == (radius == 1 ? 2 : 11)
              && world.getBlock(0, 63, 0) == Blocks.air
              && world.getBlock(0, 64, 0) == Blocks.furnace,
              "Native casing, filter, air cavity or controller was lost");
        }
      }
      // Isolate placement/graphics bootstrap; run the actual Cursor definition decision.
      Preview preview = scope.allocate(Preview.class);
      scope.replace(Preview.class, preview, "constructable", nativeMachine);
      scope.replace(Preview.class, preview, "context", nativeMachine);
      Structures.Cursor cursor = scope.allocate(Structures.Cursor.class);
      scope.replace(Structures.Cursor.class, cursor, "preview", preview);
      scope.replace(Structures.Cursor.class, cursor, "source",
          new Structures.Machine(1172, (IMetaTileEntity) nativeMachine));
      scope.replace(Structures.Cursor.class, cursor, "facts", new Facts("en_US"));
      scope.replace(Structures.Cursor.class, cursor, "trigger", new ItemStack(Items.stick));
      scope.replace(Structures.Cursor.class, cursor, "complete", true);
      JsonObject record = object("definitionKind", "structurelib", "problem", null);
      scope.replace(Structures.Cursor.class, cursor, "record", record);
      Method describe = Structures.Cursor.class.getDeclaredMethod("describe", JsonArray.class);
      describe.setAccessible(true);
      try { describe.invoke(cursor, new JsonArray()); }
      catch (InvocationTargetException failure) { throw (Exception) failure.getCause(); }
      require(record.get("definitionKind").getAsString().equals("gtconstruction")
          && record.get("problem").isJsonNull(),
          "Actual native construction-only GT geometry was marked unsupported");
    }
    System.out.println("Native GT construction: cleanroom shell, filter, cavity, count cap, owned trigger and explicit definition kind passed");
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }
}
