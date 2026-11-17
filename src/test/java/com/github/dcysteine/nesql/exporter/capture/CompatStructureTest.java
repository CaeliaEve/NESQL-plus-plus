package com.github.dcysteine.nesql.exporter.capture;

import com.github.dcysteine.nesql.exporter.source.Probe;
import com.google.gson.JsonObject;
import com.gtnewhorizon.structurelib.StructureLib;
import com.gtnewhorizon.structurelib.alignment.constructable.IMultiblockInfoContainer;
import com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.IChannelDefinition;
import java.util.Collections;
import java.util.Map;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntityFurnace;

/** Native StructureLib wrapper on an owned vanilla tile; no GUI or live world is used. */
final class CompatStructureTest {
  static void run() throws Exception {
    String key = TileEntityFurnace.class.getCanonicalName();
    IMultiblockInfoContainer<?> previous = IMultiblockInfoContainer.MULTIBLOCK_MAP.get(key);
    java.lang.reflect.Field triggerField =
        StructureLib.class.getDeclaredField("itemConstructableTrigger");
    triggerField.setAccessible(true);
    Item trigger = (Item) triggerField.get(null);
    ItemStack controller = new ItemStack(Blocks.furnace, 3, 0);
    int[] creative = {0}, survival = {0};
    IMultiblockInfoContainer<TileEntityFurnace> provider =
        new IMultiblockInfoContainer<TileEntityFurnace>() {
          public void construct(
              ItemStack stack, boolean hints, TileEntityFurnace tile, ExtendedFacing facing) {
            require(
                !hints && facing == ExtendedFacing.DEFAULT && stack.stackSize == 2,
                "Native construction parameters changed");
            require(
                tile.getWorldObj() instanceof Preview.Space, "Construction borrowed a live world");
            creative[0]++;
            tile.getWorldObj().setBlock(1, 64, 0, Blocks.stone, 2, 2);
            stack.stackSize = 63;
          }

          public int survivalConstruct(
              ItemStack stack,
              int budget,
              ISurvivalBuildEnvironment env,
              TileEntityFurnace tile,
              ExtendedFacing facing) {
            require(
                budget == 256 && stack.stackSize == 2,
                "Native bounded survival parameters changed");
            survival[0]++;
            return -2; // Native compat GUI explicitly falls back to construct for this result.
          }

          public String[] getDescription(ItemStack stack) {
            return new String[] {"Native construction-only structure"};
          }
        };
    try {
      triggerField.set(null, Items.stick);
      IMultiblockInfoContainer.registerTileClass(TileEntityFurnace.class, provider);
      Structures.Machine source = Structures.Machine.compat("minecraft", key, provider, controller);
      diagnosticSweep(source);
      java.util.LinkedHashMap<Object, ItemStack> nativeStacks = new java.util.LinkedHashMap<>();
      TileEntityFurnace scanTile = new TileEntityFurnace();
      nativeStacks.put(provider.toConstructable(scanTile, ExtendedFacing.DEFAULT), controller);
      Map<String, IMultiblockInfoContainer<?>> fixtureProviders =
          Collections.singletonMap(key, provider);
      java.util.List<Structures.Machine> planned =
          Structures.compatMachines(nativeStacks, fixtureProviders);
      require(
          planned.size() == 1 && planned.get(0).identity().equals(source.identity()),
          "Native controller scan was not routed to owned geometry");
      java.util.Map<Long, java.util.Set<Object>> components = new java.util.TreeMap<>();
      ItemStack component = new ItemStack(Items.stick, 7, 0);
      long componentHash = component.getItem().hashCode() * 31L + component.getItemDamage() * 31L;
      Object wrapper = nativeStacks.keySet().iterator().next();
      components.put(componentHash, Collections.singleton(wrapper));
      components.put(componentHash + 31, Collections.singleton(wrapper));
      blockrenderer6343.client.utils.ConstructableData tiers =
          new blockrenderer6343.client.utils.ConstructableData();
      tiers.setMaxTier(3, java.util.Arrays.asList("coil", "glass"));
      tiers.addChannelDefinition(IChannelDefinition.bounded("bounded", 2, 9));
      tiers.addChannelDefinition(IChannelDefinition.unbounded("unbounded"));
      int[] callbacks = {0};
      IChannelDefinition opaque = new IChannelDefinition() {
        public String getChannel() { callbacks[0]++; throw new AssertionError("Unknown channel getter executed"); }
        public int getMinimumValue() { callbacks[0]++; throw new AssertionError("Unknown minimum executed"); }
        public int getMaximumValue() { callbacks[0]++; throw new AssertionError("Unknown maximum executed"); }
        public boolean isValueValid(int value) { callbacks[0]++; throw new AssertionError("Unknown validation executed"); }
        public int normalizeValue(int value) { callbacks[0]++; throw new AssertionError("Unknown normalization executed"); }
      };
      // Seed owned custom definitions: native registration itself calls their getters.
      tiers.getChannelDefinitions().put("opaque", opaque);
      tiers.getChannelMaxTierMap().put("opaque", -7);
      tiers.getChannelDefinitions().put("validator", new IChannelDefinition() {
        public String getChannel() { callbacks[0]++; throw new AssertionError("Unknown channel getter executed"); }
        public boolean isValueValid(int value) { callbacks[0]++; throw new AssertionError("Unknown validation executed"); }
      });
      tiers.getChannelMaxTierMap().put("validator", Integer.MAX_VALUE);
      tiers.getChannelDefinitions().put("normalizer", new IChannelDefinition() {
        public String getChannel() { callbacks[0]++; throw new AssertionError("Unknown channel getter executed"); }
        public int normalizeValue(int value) { callbacks[0]++; throw new AssertionError("Unknown normalization executed"); }
      });
      tiers.getChannelMaxTierMap().put("normalizer", Integer.MAX_VALUE);
      Map<Long, Integer> nativeTiers = tiers.getItemTiers();
      nativeTiers.put(componentHash, 2);
      nativeTiers.put(componentHash + 31, -2);
      Map<Long, java.util.Collection<String>> nativeChannels = tiers.getItemChannels();
      nativeChannels.put(componentHash, Collections.unmodifiableSet(
          new java.util.LinkedHashSet<>(java.util.Arrays.asList("glass", "coil", "not-scanned"))));
      StructureReferences.Binding binding = new StructureReferences.Binding(source, wrapper, tiers);
      com.google.gson.JsonArray referenceRows =
          StructureReferences.snapshot(
              com.github.dcysteine.nesql.exporter.source.Json.object(
                  "owner",
                  "blockrenderer6343",
                  "handler",
                  StructureReferences.COMPAT,
                  "key",
                  "fixture.structure.compat"),
              "compat",
              Collections.singletonList(binding),
              components,
              Collections.singletonList(component),
              new java.util.TreeMap<>());
      JsonObject definition = referenceRows.get(0).getAsJsonObject().getAsJsonObject("data");
      require(
          definition.getAsJsonObject("counts").get("unresolvedLookups").getAsInt() == 1,
          "Native hash with no panel specimen was silently discarded");
      JsonObject information = referenceRows.get(1).getAsJsonObject().getAsJsonObject("data");
      require(
          information.get("structure").getAsString().equals(source.identity())
              && information.get("maxTier").getAsInt() == 3
              && information.getAsJsonObject("channels").get("coil").getAsInt() == 3
              && information.getAsJsonObject("channels").get("glass").getAsInt() == 3
              && information.get("hasData").getAsBoolean()
              && information.get("hasTierData").getAsBoolean()
              && information
                      .getAsJsonArray("tierItems")
                      .get(0)
                      .getAsJsonObject()
                      .get("tier")
                      .getAsInt()
                  == 2,
          "Controller, native channel or exact tier association was lost");
      require(information.getAsJsonArray("tierItems").get(0).getAsJsonObject()
              .getAsJsonArray("channels").toString().equals("[\"glass\",\"coil\",\"not-scanned\"]")
          && information.getAsJsonArray("tierItems").get(1).getAsJsonObject()
              .getAsJsonArray("channels").size() == 0
          && information.getAsJsonArray("tierItems").get(1).getAsJsonObject().get("tier").getAsInt() == -2,
          "Ordered multi-channel/default/signed native facts were changed");
      JsonObject definitions = information.getAsJsonObject("channelDefinitions");
      require(definitions.getAsJsonObject("bounded").get("minimum").getAsInt() == 2
          && definitions.getAsJsonObject("bounded").get("maximum").getAsInt() == 9
          && definitions.getAsJsonObject("bounded").get("kind").getAsString().equals("bounded")
          && definitions.getAsJsonObject("unbounded").get("kind").getAsString().equals("unbounded")
          && definitions.getAsJsonObject("unbounded").get("maximum").getAsInt() == Integer.MAX_VALUE
          && definitions.getAsJsonObject("opaque").get("minimum").isJsonNull()
          && definitions.getAsJsonObject("opaque").get("maximum").isJsonNull()
          && definitions.getAsJsonObject("opaque").get("validation").getAsString().equals("opaqueCustom")
          && definitions.getAsJsonObject("opaque").get("normalization").getAsString().equals("opaqueCustom")
          && definitions.getAsJsonObject("validator").get("validation").getAsString().equals("opaqueCustom")
          && definitions.getAsJsonObject("validator").get("normalization").getAsString().equals("clampToBounds")
          && definitions.getAsJsonObject("normalizer").get("validation").getAsString().equals("inclusiveBounds")
          && definitions.getAsJsonObject("normalizer").get("normalization").getAsString().equals("opaqueCustom")
          && callbacks[0] == 0, "Channel definitions were lost or unknown callbacks executed");
      blockrenderer6343.client.utils.ConstructableData definitionOnly =
          new blockrenderer6343.client.utils.ConstructableData();
      definitionOnly.addChannelDefinition(IChannelDefinition.bounded("bounded", 2, 9));
      JsonObject definitionOnlyInformation = StructureReferences.snapshot(
          definition.getAsJsonObject("source"), "compat", Collections.singletonList(
              new StructureReferences.Binding(source, wrapper, definitionOnly)),
          Collections.emptyMap(), Collections.emptyList(), new java.util.TreeMap<>())
          .get(1).getAsJsonObject().getAsJsonObject("data");
      require(definitionOnlyInformation.get("hasData").getAsBoolean()
          && !definitionOnlyInformation.get("hasTierData").getAsBoolean()
          && definitionOnlyInformation.get("maxTier").getAsInt() == 1
          && definitionOnlyInformation.getAsJsonObject("channels").get("bounded").getAsInt() == 9,
          "Definition-only metadata was confused with tier data");
      require(
          StructureReferences.supportsClass(StructureReferences.COMPAT)
              && StructureReferences.supportsClass(StructureReferences.GT)
              && !StructureReferences.supportsClass("unverified.StructureHandler"),
          "Dedicated native structure route is not explicit");
      com.google.gson.JsonArray nativeItems = new com.google.gson.JsonArray();
      for (ItemStack specimen : new ItemStack[] {controller, component}) {
        String registry = Item.itemRegistry.getNameForObject(specimen.getItem());
        nativeItems.add(
            com.github.dcysteine.nesql.exporter.source.Json.object(
                "registry",
                registry,
                "meta",
                specimen.getItemDamage(),
                "nbt",
                null,
                "id",
                com.github.dcysteine.nesql.exporter.source.Identity.item(
                    registry, specimen.getItemDamage(), null)));
      }
      java.nio.file.Files.write(
          java.nio.file.Paths.get(
              System.getProperty("user.dir"), "structure-reference-contract.json"),
          com.github.dcysteine.nesql.exporter.source.CanonicalJson.bytes(
              com.github.dcysteine.nesql.exporter.source.Json.object(
                  "records", referenceRows, "items", nativeItems)));
      require(
          component.stackSize == 7 && nativeTiers.get(componentHash) == 2,
          "Reading native lookup information mutated its source");
      require(
          Structures.compatMachines(Collections.emptyMap(), Collections.emptyMap()).isEmpty(),
          "Ready empty registry rejected");
      try {
        Structures.compatMachines(Collections.emptyMap(), fixtureProviders);
        throw new AssertionError("Incomplete scan accepted");
      } catch (com.github.dcysteine.nesql.exporter.task.Jobs.Fault expected) {
        require(expected.code.equals("handler_not_ready"), "Wrong incomplete scan failure");
      }
      Facts facts = new Facts("en_US");
      // Tooltip/graphics are outside this owned construction fixture.
      java.util.Set<String> known =
          cpw.mods.fml.relauncher.ReflectionHelper.getPrivateValue(Facts.class, facts, "items");
      for (ItemStack stack :
          new ItemStack[] {
            controller, new ItemStack(Blocks.stone, 1, 2), new ItemStack(Blocks.stone, 1, 0)
          }) {
        known.add(
            com.github.dcysteine.nesql.exporter.source.Identity.item(
                Item.itemRegistry.getNameForObject(stack.getItem()), stack.getItemDamage(), null));
        if (stack.getItem() != controller.getItem()) {
          String registry = Item.itemRegistry.getNameForObject(stack.getItem());
          nativeItems.add(
              com.github.dcysteine.nesql.exporter.source.Json.object(
                  "registry",
                  registry,
                  "meta",
                  stack.getItemDamage(),
                  "nbt",
                  null,
                  "id",
                  com.github.dcysteine.nesql.exporter.source.Identity.item(
                      registry, stack.getItemDamage(), null)));
        }
      }
      com.google.gson.JsonArray rows = new com.google.gson.JsonArray();
      JsonObject structure = null, build = null;
      int shapes = 0, blocks = 0;
      try (Structures.Cursor cursor =
          new Structures.Cursor(
              source,
              facts,
              null,
              Collections.singletonList(new Probe(2, Collections.emptyMap())),
              true)) {
        for (int step = 0; step < 512; step++) {
          boolean done = cursor.capture();
          for (Facts.Record record : facts.drain().records) {
            rows.add(
                com.github.dcysteine.nesql.exporter.source.Json.object(
                    "kind", record.kind, "value", record.value));
            if (record.kind.equals("structures")) structure = record.value;
            if (record.kind.equals("builds")) build = record.value;
            if (record.kind.equals("shapes")) shapes++;
            if (record.kind.equals("blocks")) blocks++;
          }
          if (done) break;
        }
      }
      require(structure != null && build != null, "Actual native geometry was not emitted");
      require(
          structure.get("definitionKind").getAsString().equals("construction")
              && structure.getAsJsonArray("pieces").size() == 0
              && structure.get("problem").isJsonNull(),
          "A native construction-only provider was reported as an unsupported definition");
      require(
          build.get("method").getAsString().equals("creative")
              && build.get("facing").getAsString().equals("north")
              && build.get("result").isJsonNull()
              && build.get("cells").getAsInt() == 2
              && shapes == 1
              && blocks == 2,
          "Native creative fallback or controller geometry was lost");
      require(
          creative[0] == 1 && survival[0] == 1 && controller.stackSize == 3,
          "Native callbacks repeated or mutated the registry controller");
      require(
          build.getAsJsonArray("size").toString().equals("[2,1,1]"),
          "Native construction bounds changed");
      java.nio.file.Path output =
          java.nio.file.Paths.get(System.getProperty("user.dir"), "compat-structure-contract.json");
      java.nio.file.Files.write(
          output,
          com.github.dcysteine.nesql.exporter.source.CanonicalJson.bytes(
              com.github.dcysteine.nesql.exporter.source.Json.object(
                  "records", rows, "items", nativeItems)));
      com.github.dcysteine.nesql.exporter.source.SemanticStructureTest.run(rows);
      IMultiblockInfoContainer.MULTIBLOCK_MAP.remove(key);
      try {
        new Preview(source, Probe.defaults(), null, true);
        throw new AssertionError("Provider drift accepted");
      } catch (com.github.dcysteine.nesql.exporter.task.Jobs.Fault expected) {
        require(expected.code.equals("environment_changed"), "Wrong provider drift failure");
      }
    } finally {
      triggerField.set(null, trigger);
      if (previous == null) IMultiblockInfoContainer.MULTIBLOCK_MAP.remove(key);
      else IMultiblockInfoContainer.MULTIBLOCK_MAP.put(key, previous);
    }
    System.out.println(
        "Compat structures: actual wrapper, owned placement, creative fallback, geometry, parameters and drift passed");
  }

  private static void diagnosticSweep(Structures.Machine source) throws Exception {
    JsonObject report = new JsonObject();
    int[] visited = {0};
    try {
      Audit.checkStructures(report, java.util.Arrays.asList(source, source, source), (machine, checked) -> {
        int index = visited[0]++;
        checked.add("source", machine.origin());
        checked.addProperty("status", index == 0 ? "passed" : "unsupported");
        checked.add("limitations", com.github.dcysteine.nesql.exporter.source.Json.array("native failure " + index));
        if (index == 2) throw new com.github.dcysteine.nesql.exporter.task.Jobs.Fault("preview_failed", "native failed construction");
      });
      throw new AssertionError("Failed native structures became a passed handler");
    } catch (com.github.dcysteine.nesql.exporter.task.Jobs.Fault expected) {
      require(expected.code.equals("structure_unsupported"), "Wrong aggregate structure status");
    }
    require(visited[0] == 3 && report.has("structureChecks")
        && report.getAsJsonArray("structureChecks").size() == 3,
        "Native structure sweep stopped early or discarded checked limitations");
    JsonObject failed = report.getAsJsonArray("structureChecks").get(2).getAsJsonObject();
    require(failed.get("status").getAsString().equals("failed")
        && failed.getAsJsonObject("error").get("message").getAsString().equals("native failed construction")
        && failed.getAsJsonArray("limitations").size() == 1, "Native failure context was lost");
    visited[0] = 0;
    report = new JsonObject();
    try {
      Audit.checkStructures(report, java.util.Arrays.asList(source, source), (machine, checked) -> {
        visited[0]++;
        throw new com.github.dcysteine.nesql.exporter.task.Jobs.Fault("environment_changed", "native provider drift");
      });
      throw new AssertionError("Unsafe structure drift was aggregated as an ordinary failure");
    } catch (com.github.dcysteine.nesql.exporter.task.Jobs.Fault expected) {
      require(expected.code.equals("environment_changed") && visited[0] == 1
          && report.getAsJsonArray("structureChecks").size() == 1, "Fatal structure drift lost isolation");
    }
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }
}
