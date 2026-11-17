package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

import com.google.gson.*;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.*;
import net.minecraftforge.oredict.OreDictionary;

/** Native task/reward declarations, not GUI positioned stacks or player histories. */
final class BetterQuestingTest {
  static void run() throws Exception {
    Class<?> adapter =
        Class.forName("com.github.dcysteine.nesql.exporter.capture.BetterQuestingRules");
    Method task = adapter.getDeclaredMethod("task", Object.class, Map.class, Map.class);
    Method reward = adapter.getDeclaredMethod("reward", Object.class, Map.class);
    task.setAccessible(true);
    reward.setAccessible(true);
    Map<String, ItemStack> items = new TreeMap<>();
    Map<String, FluidStack> fluids = new TreeMap<>();
    JsonArray tasks = array(), rewards = array();
    List<Object> nativeTasks = new ArrayList<>(), nativeRewards = new ArrayList<>();
    Class<?> big = type("betterquesting.api.utils.BigItemStack");
    OreDictionary.registerOre("nesqlQuestFixture", new ItemStack(Items.iron_ingot));
    OreDictionary.registerOre("nesqlQuestFixture", new ItemStack(Items.gold_ingot));
    Object stack = big.getConstructor(ItemStack.class).newInstance(new ItemStack(Items.diamond));
    big.getField("stackSize").set(stack, 250000);
    invoke(big, stack, "setOreDict", new Class<?>[] {String.class}, "nesqlQuestFixture");
    for (String name :
        new String[] {
          "Retrieval",
          "OptionalRetrieval",
          "Crafting",
          "Fluid",
          "Meeting",
          "InteractItem",
          "Checkbox",
          "Hunt",
          "Location",
          "XP",
          "InteractEntity",
          "BlockBreak",
          "Scoreboard"
        }) {
      Object nativeTask = type("bq_standard.tasks.Task" + name).newInstance();
      nativeTasks.add(nativeTask);
      if (name.equals("Retrieval") || name.equals("OptionalRetrieval") || name.equals("Crafting")) {
        @SuppressWarnings("unchecked")
        List<Object> list = (List<Object>) field(nativeTask, "requiredItems");
        for (int i = 0; i < 17; i++) list.add(stack);
      }
      if (name.equals("Fluid")) {
        Fluid fluid = new Fluid("nesqlQuestFixtureFluid");
        FluidRegistry.registerFluid(fluid);
        FluidStack required = new FluidStack(fluid, 72000);
        required.tag = new NBTTagCompound();
        required.tag.setString("phase", "native");
        @SuppressWarnings("unchecked")
        List<FluidStack> list = (List<FluidStack>) field(nativeTask, "requiredFluids");
        list.add(required);
      }
      if (name.equals("InteractItem") || name.equals("InteractEntity"))
        set(nativeTask, "targetItem", stack);
      if (name.equals("BlockBreak")) {
        @SuppressWarnings("unchecked")
        List<Object> blocks = (List<Object>) field(nativeTask, "blockTypes");
        blocks.add(type("bq_standard.NbtBlockType").newInstance());
      }
      JsonObject rule = (JsonObject) task.invoke(null, nativeTask, items, fluids);
      tasks.add(rule);
      if (name.equals("OptionalRetrieval"))
        require(rule.get("optional").getAsBoolean(), "Optional retrieval treated as mandatory");
      if (name.equals("Retrieval")) {
        JsonArray requirements = rule.getAsJsonArray("items");
        require(requirements.size() == 17, "GUI's sixteen-cell cap leaked into declaration");
        JsonObject first = requirements.get(0).getAsJsonObject();
        require(
            first.get("amount").getAsInt() == 250000
                && first.getAsJsonArray("candidates").size() == 2,
            "Native large amount or ore alternatives lost");
        require(
            !rule.get("consume").getAsBoolean(), "Detection task converted to consuming recipe");
      }
    }
    for (String name :
        new String[] {"Item", "Choice", "XP", "Command", "Scoreboard", "QuestCompletion"}) {
      Object nativeReward = type("bq_standard.rewards.Reward" + name).newInstance();
      nativeRewards.add(nativeReward);
      if (name.equals("Item") || name.equals("Choice")) {
        @SuppressWarnings("unchecked")
        List<Object> list =
            (List<Object>) field(nativeReward, name.equals("Item") ? "items" : "choices");
        list.add(stack);
      }
      if (name.equals("Scoreboard")) set(nativeReward, "type", "deathCount");
      if (name.equals("QuestCompletion"))
        set(nativeReward, "questNum", UUID.fromString("00000000-0000-0000-0000-000000000002"));
      JsonObject rule = (JsonObject) reward.invoke(null, nativeReward, items);
      rewards.add(rule);
      if (name.equals("Choice"))
        require(
            rule.get("selectionCount").getAsInt() == 1 && !rule.has("selected"),
            "Choice reward exported player selection or all rewards as compulsory");
      if (name.equals("Scoreboard"))
        require(
            rule.get("criteria").getAsString().equals("deathCount"),
            "Lossy writeToNBT dummy criteria copied instead of actual rule");
    }
    require(
        big.getField("stackSize").getInt(stack) == 250000, "Capture mutated native BigItemStack");
    require(
        ((ItemStack) invoke(big, stack, "getBaseStack", new Class<?>[0])).stackSize == 1,
        "Capture mutated native base count");
    require(items.size() >= 3 && fluids.size() == 1, "Native item/fluid facts not collected");
    try {
      task.invoke(null, new Object(), items, fluids);
      throw new AssertionError("Unknown task callback silently accepted");
    } catch (InvocationTargetException expected) {
      require(
          expected.getCause() instanceof com.github.dcysteine.nesql.exporter.task.Jobs.Fault,
          "Unknown task did not fail explicitly");
    }
    BetterQuestingRules.Budget shortBudget = new BetterQuestingRules.Budget(4);
    try {
      BetterQuestingRules.task(nativeTasks.get(0), new TreeMap<>(), new TreeMap<>(), shortBudget);
      throw new AssertionError("Repeated quest patterns bypassed traversal budget");
    } catch (com.github.dcysteine.nesql.exporter.task.Jobs.Fault expected) {
      require(expected.getMessage().contains("budget"), "Unexpected traversal failure");
    }
    JsonObject fixture = object("tasks", tasks, "rewards", rewards);
    Object quest = type("betterquesting.questing.QuestInstance").newInstance();
    Object hidden = type("betterquesting.questing.QuestInstance").newInstance();
    property(
        quest,
        "VISIBILITY",
        Enum.valueOf((Class) type("betterquesting.api.enums.EnumQuestVisibility"), "ALWAYS"));
    property(
        hidden,
        "VISIBILITY",
        Enum.valueOf((Class) type("betterquesting.api.enums.EnumQuestVisibility"), "HIDDEN"));
    property(quest, "NAME", "Native fixture quest");
    property(quest, "DESC", "Native declared rules");
    property(hidden, "DESC", "PRIVATE HIDDEN DECLARATION");
    Object taskStorage = invoke(quest.getClass(), quest, "getTasks", new Class<?>[0]);
    Object rewardStorage = invoke(quest.getClass(), quest, "getRewards", new Class<?>[0]);
    Method addTask = taskStorage.getClass().getMethod("add", int.class, Object.class);
    Method addReward = rewardStorage.getClass().getMethod("add", int.class, Object.class);
    for (int i = nativeTasks.size() - 1; i >= 0; i--)
      addTask.invoke(taskStorage, i * 2, nativeTasks.get(i));
    for (int i = nativeRewards.size() - 1; i >= 0; i--)
      addReward.invoke(rewardStorage, i * 3, nativeRewards.get(i));
    UUID questId = UUID.fromString("00000000-0000-0000-0000-000000000001");
    UUID hiddenId = UUID.fromString("00000000-0000-0000-0000-000000000002");
    UUID missingId = UUID.fromString("00000000-0000-0000-0000-000000000003");
    invoke(
        quest.getClass(),
        quest,
        "setRequirements",
        new Class<?>[] {Iterable.class},
        Arrays.asList(hiddenId, missingId));
    @SuppressWarnings("unchecked")
    Map<UUID, NBTTagCompound> histories = (Map<UUID, NBTTagCompound>) field(quest, "completeUsers");
    NBTTagCompound progress = new NBTTagCompound();
    progress.setString("private", "PRIVATE PLAYER HISTORY");
    histories.put(questId, progress);
    Object settingsApi = field(type("betterquesting.storage.QuestSettings"), null, "INSTANCE");
    Object settingsKey = field(type("betterquesting.api.api.ApiReference"), null, "SETTINGS");
    invoke(
        type("betterquesting.api.api.QuestingAPI"),
        null,
        "registerAPI",
        new Class<?>[] {type("betterquesting.api.api.ApiKey"), Object.class},
        settingsKey,
        settingsApi);
    java.util.function.Predicate<Object> visibility =
        q ->
            (Boolean)
                invoke(
                    type("betterquesting.api2.cache.QuestCache"),
                    null,
                    "isQuestShown",
                    new Class<?>[] {
                      type("betterquesting.api.questing.IQuest"),
                      UUID.class,
                      net.minecraft.entity.player.EntityPlayer.class
                    },
                    q,
                    questId,
                    null);
    Map<UUID, Object> registry = new LinkedHashMap<>();
    registry.put(questId, quest);
    registry.put(hiddenId, hidden);
    Method snapshot =
        type("com.github.dcysteine.nesql.exporter.capture.BetterQuestingReferences")
            .getDeclaredMethod(
                "snapshot",
                boolean.class,
                JsonObject.class,
                Map.class,
                java.util.function.Predicate.class,
                JsonObject.class,
                Map.class,
                Map.class);
    snapshot.setAccessible(true);
    JsonObject source =
        object(
            "owner",
            "betterquesting",
            "handler",
            "bq_standard.integration.nei.QuestRecipeHandler",
            "key",
            "bq_standard.integration.nei.QuestRecipeHandler");
    JsonObject worldSettings =
        object(
            "noRewards",
            false,
            "viewMode",
            false,
            "partyEnable",
            true,
            "fullySyncQuests",
            false,
            "editMode",
            false,
            "hardcore",
            false,
            "livesDefault",
            3,
            "livesMax",
            10);
    items.clear();
    fluids.clear();
    JsonArray rows =
        (JsonArray)
            snapshot.invoke(null, true, source, registry, visibility, worldSettings, items, fluids);
    require(rows.size() == 2, "Native hidden quest became a visible declaration");
    require(
        rows.get(0)
            .getAsJsonObject()
            .getAsJsonObject("data")
            .getAsJsonArray("visible")
            .get(0)
            .getAsString()
            .equals(questId.toString()),
        "Native visibility registry proof is missing");
    JsonObject declaration = rows.get(1).getAsJsonObject().getAsJsonObject("data");
    require(
        declaration.getAsJsonArray("tasks").size() == 13
            && declaration.getAsJsonArray("rewards").size() == 6,
        "Task/reward declaration incomplete");
    require(
        declaration.getAsJsonArray("tasks").get(1).getAsJsonObject().get("nativeId").getAsInt()
            == 2,
        "Native DBEntry IDs reordered or compacted");
    Set<String> states = new TreeSet<>();
    for (JsonElement prereq : declaration.getAsJsonArray("prerequisites"))
      states.add(prereq.getAsJsonObject().get("state").getAsString());
    require(
        states.equals(new TreeSet<>(Arrays.asList("hidden", "missing"))),
        "Hidden and missing prerequisite states merged");
    require(
        !rows.toString().contains("PRIVATE"),
        "Player history or hidden quest content leaked into declaration");
    require(
        histories.get(questId).getString("private").equals("PRIVATE PLAYER HISTORY"),
        "Declaration capture changed player progress");
    JsonArray empty =
        (JsonArray)
            snapshot.invoke(
                null,
                true,
                source,
                Collections.emptyMap(),
                visibility,
                worldSettings,
                new TreeMap<>(),
                new TreeMap<>());
    require(empty.size() == 1, "Ready empty quest registry rejected");
    try {
      snapshot.invoke(null, false, source, registry, visibility, worldSettings, items, fluids);
      throw new AssertionError("Unready quest registry accepted");
    } catch (InvocationTargetException expected) {
      require(
          expected.getCause() instanceof com.github.dcysteine.nesql.exporter.task.Jobs.Fault,
          "Missing readiness failure");
    }
    fixture.add("records", rows);
    JsonArray itemFacts = array(), fluidFacts = array();
    for (Map.Entry<String, ItemStack> entry : items.entrySet()) {
      ItemStack sample = entry.getValue();
      itemFacts.add(
          object(
              "id",
              entry.getKey(),
              "registry",
              net.minecraft.item.Item.itemRegistry.getNameForObject(sample.getItem()),
              "meta",
              sample.getItemDamage(),
              "nbt",
              com.github.dcysteine.nesql.exporter.source.TypedNbt.encode(sample.getTagCompound())));
    }
    for (Map.Entry<String, FluidStack> entry : fluids.entrySet()) {
      FluidStack sample = entry.getValue();
      Fluid fluid = sample.getFluid();
      fluidFacts.add(
          object(
              "id",
              entry.getKey(),
              "registry",
              fluid.getName(),
              "nbt",
              com.github.dcysteine.nesql.exporter.source.TypedNbt.encode(sample.tag),
              "temperature",
              fluid.getTemperature(sample),
              "density",
              fluid.getDensity(sample),
              "viscosity",
              fluid.getViscosity(sample),
              "luminosity",
              fluid.getLuminosity(sample),
              "gaseous",
              fluid.isGaseous(sample)));
    }
    fixture.add("items", itemFacts);
    fixture.add("fluids", fluidFacts);
    java.nio.file.Files.createDirectories(java.nio.file.Paths.get("build/native-tests"));
    java.nio.file.Files.write(
        java.nio.file.Paths.get("build/native-tests/betterquesting-rules-contract.json"),
        com.github.dcysteine.nesql.exporter.source.CanonicalJson.bytes(fixture));
    Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
    unsafeField.setAccessible(true);
    Object handler =
        ((sun.misc.Unsafe) unsafeField.get(null))
            .allocateInstance(type("bq_standard.integration.nei.QuestRecipeHandler"));
    require(
        Recipes.adapter((codechicken.nei.recipe.ICraftingHandler) handler)
            .name()
            .equals("QUESTS_REFERENCE"),
        "Quest declaration still uses a consuming template");
    System.out.println(
        "BetterQuesting: thirteen task rules, six reward rules, large counts, native ore candidates, optional/choice semantics and unknown callback rejection passed");
  }

  private static void set(Object target, String name, Object value) throws Exception {
    Field field = target.getClass().getField(name);
    field.set(target, value);
  }

  private static void property(Object target, String name, Object value) {
    Object key = field(type("betterquesting.api.properties.NativeProps"), null, name);
    invoke(
        target.getClass(),
        target,
        "setProperty",
        new Class<?>[] {type("betterquesting.api.properties.IPropertyType"), Object.class},
        key,
        value);
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }

  /** The installed class initializer only reads GUI font height. No quest rule is transformed. */
  public static final class UnusedGuiConstants
      implements net.minecraft.launchwrapper.IClassTransformer {
    public byte[] transform(String name, String transformed, byte[] bytes) {
      if (bytes == null || !name.equals("bq_standard.integration.nei.QuestRecipeHandler"))
        return bytes;
      org.objectweb.asm.tree.ClassNode node = new org.objectweb.asm.tree.ClassNode();
      new org.objectweb.asm.ClassReader(bytes).accept(node, 0);
      node.methods.removeIf(
          method -> ((org.objectweb.asm.tree.MethodNode) method).name.equals("<clinit>"));
      org.objectweb.asm.ClassWriter writer = new org.objectweb.asm.ClassWriter(0);
      node.accept(writer);
      return writer.toByteArray();
    }
  }
}
