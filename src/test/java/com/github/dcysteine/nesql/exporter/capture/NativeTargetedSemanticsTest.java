package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

import com.github.dcysteine.nesql.exporter.source.Identity;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.*;
import java.util.*;
import net.minecraft.init.Items;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.*;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.nbt.*;
import thaumcraft.api.aspects.*;
import thaumcraft.api.crafting.InfusionRecipe;

/** Native oracles for the two returned recipes whose behavior is not a fixed sample. */
final class NativeTargetedSemanticsTest {
  static void wireless() throws Exception {
    try (NativeRegistryScope scope = new NativeRegistryScope()) {
      NativeCoreFixesTest.version("ae2fc", "1.4.120-gtnh");
      NativeCoreFixesTest.version("ae2wct", "1.12.7");
      Item terminal = NativeCoreFixesTest.item("com.glodblock.github.common.item.ItemWirelessFluidTerminal", "ae2fc:targeted_terminal");
      Item booster = NativeCoreFixesTest.item("net.p455w0rd.wirelesscraftingterminal.items.ItemInfinityBooster", "ae2wct:targeted_booster");
      ItemStack input = new ItemStack(terminal, 1, 7), card = new ItemStack(booster);
      input.setTagCompound(new NBTTagCompound()); input.getTagCompound().setString("owner", "retained");
      Object entry = Enum.valueOf((Class)type("net.p455w0rd.wirelesscraftingterminal.items.ItemEnum"), "BOOSTER_CARD");
      scope.replace(entry.getClass(), entry, "item", booster);
      Class<?> owner = type("com.glodblock.github.loader.recipe.WirelessTerminalRecipe");
      IRecipe recipe = (IRecipe)owner.getConstructor(ItemStack.class).newInstance(input);
      String flag = (String)field(type("com.glodblock.github.common.item.ItemBaseWirelessTerminal"), null, "infinityBoosterCard");
      InventoryCrafting grid = NativeCraftingTest.inventory(3, 3, input, card, new ItemStack(Items.diamond));
      require(recipe.matches(grid, null), "Native wireless recipe checked unrelated grid cells");
      ItemStack expected = recipe.getCraftingResult(grid);
      require(expected.getItem() == terminal && expected.getItemDamage() == 7
          && expected.getTagCompound().getString("owner").equals("retained")
          && expected.getTagCompound().getBoolean(flag), "Native wireless output projection changed");
      grid.getStackInSlot(0).getTagCompound().setBoolean(flag, true);
      require(!recipe.matches(grid, null), "Native wireless recipe accepted an installed booster");
      grid = NativeCraftingTest.inventory(3, 3, card, input);
      require(!recipe.matches(grid, null), "Native wireless recipe became shapeless");
      RecipeRow row = NativeCraftingTest.row(false, input, card, expected);
      try (RegistryRecipes reader = NativeCraftingTest.reader(Collections.singletonList(recipe), false)) {
        require(reader.capture(0, row), "Native wireless upgrade was excluded");
        JsonObject process = row.record.getAsJsonObject("process");
        require(process != null && process.get("kind").getAsString().equals("wirelessTerminalUpgrade")
            && process.get("flag").getAsString().equals(flag)
            && row.record.getAsJsonObject("grid").getAsJsonArray("cells").get(1).getAsInt() == 1,
            "Wireless upgrade lost its fixed first two cells and NBT flag");
        require(row.outputs.get(0).getAsJsonObject().getAsJsonObject("change").getAsJsonObject("action")
            .get("kind").getAsString().equals("patch"), "Wireless upgrade was flattened to a fixed result");
        reader.finishVerification();
        NativeContractFixture.writeOwned("targeted-wireless-upgrade", row, Arrays.asList(input, card, expected));
      }
      require(!input.getTagCompound().hasKey(flag), "Wireless capture mutated its source template");
    }
    System.out.println("Targeted wireless: fixed cells, ignored extras, prior-install rejection and full NBT copy passed");
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  static void randomCosts() throws Exception {
    try (NativeRegistryScope scope = new NativeRegistryScope()) {
      NativeCoreFixesTest.version("gadomancy", "1.5.17");
      Class<?> network = type("net.glease.tc4tweak.network.NetworkedConfiguration");
      scope.replace(network, null, "infusionOreDictMode", Enum.valueOf(
          (Class)type("net.glease.tc4tweak.modules.infusionRecipe.InfusionOreDictMode"), "Default"));
      Class<?> configType = type("net.glease.tc4tweak.ConfigurationHandler");
      Object config = field(configType, null, "INSTANCE");
      scope.replace(configType, config, "infusionRecipeNBTCarryOver", false);
      scope.replace(configType, config, "infusionRecipeNBTWhitelist", new ArrayList<String>());
      Object merger = invoke(type("net.glease.tc4tweak.api.InfusionExtAPI$RecipeNBTBehavior"),
          null, "mergeNBT", new Class<?>[]{Set.class}, (Object)null);
      scope.replace(type("net.glease.tc4tweak.modules.infusionRecipe.InfusionRecipeGetOutput"), null, "MERGER", merger);
      Class<?> costType = type("makeo.gadomancy.common.utils.RandomizedAspectList");
      for (boolean halfCap : new boolean[]{false, true}) {
        AspectList random = (AspectList)costType.newInstance();
        invoke(costType, random, "addAspectRandomBase", new Class<?>[]{Aspect.class, int.class}, Aspect.AIR, 9);
        invoke(costType, random, "setHalfCap", new Class<?>[]{boolean.class}, halfCap);
        java.lang.reflect.Field clock = costType.getDeclaredField("lastRandomization"); clock.setAccessible(true);
        clock.setLong(random, 123);
        random.aspects.put(Aspect.FIRE, 777);
        ItemStack center = new ItemStack(Items.paper), output = new ItemStack(Items.diamond);
        InfusionRecipe raw = new InfusionRecipe("", output, 2, random, center, new ItemStack[0]);
        JsonObject origin = object("owner", "aspectrecipeindex",
            "handler", "com.gtnewhorizons.aspectrecipeindex.nei.InfusionRecipeHandler", "key", "targeted-random-cost");
        RecipeRow row = new RecipeRow(NativeCoreFixesTest.row(center, output).facts, origin, Identity.origin("category", origin), 0);
        try (NativeItemExamples examples = new NativeItemExamples();
             InfusionLegacyInputs.Context context = new InfusionLegacyInputs.Context();
             InfusionInheritance inheritance = new InfusionInheritance();
             NativeInfusionRecipes reader = new NativeInfusionRecipes(raw, examples, context)) {
          require(reader.capture(row, inheritance), "Random-cost native infusion was excluded");
          JsonObject law = row.record.getAsJsonObject("process").getAsJsonObject("randomAspects");
          require(law != null && law.get("semantics").getAsString().equals("gadomancyRandomCostsV1")
              && !law.has("version") && law.get("halfCap").getAsBoolean() == halfCap
              && law.getAsJsonArray("bases").get(0).getAsJsonObject().get("amount").getAsString().equals("9")
              && row.record.getAsJsonObject("magic").getAsJsonArray("aspects").size() == 0,
              "Random infusion costs were sampled or advertised as fixed costs");
          require(field(costType, random, "lastRandomization").equals(Long.valueOf(123))
              && random.aspects.get(Aspect.FIRE) == 777, "Cost capture advanced native RNG/cache state");
          random.aspects.clear(); random.aspects.put(Aspect.WATER, 11);
          clock.setLong(random, 456);
          reader.finishVerification();
          NativeContractFixture.writeOwned("targeted-random-infusion-" + halfCap, row, Arrays.asList(center, output));
          java.nio.file.Path fixture = NativeContractFixture.directory()
              .resolve("targeted-random-infusion-" + halfCap + "-contract.json");
          JsonObject document = new JsonParser().parse(new String(java.nio.file.Files.readAllBytes(fixture),
              java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
          document.add("aspects", array(object("id", Aspects.id(Aspect.AIR), "source", Aspects.source(Aspect.AIR))));
          java.nio.file.Files.write(fixture, com.github.dcysteine.nesql.exporter.source.CanonicalJson.bytes(document));
          ((Map<Aspect,Integer>)field(costType, random, "aspectMap")).put(Aspect.AIR, 10);
          try { reader.finishVerification(); throw new AssertionError("Random cost definition drift was ignored"); }
          catch (Jobs.Fault expected) { require(expected.code.equals("environment_changed"), expected.toString()); }
        }
      }
    }
    System.out.println("Targeted random infusion: half-cap/base rules, no RNG writes, derived-cache independence and definition drift passed");
  }

  private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
