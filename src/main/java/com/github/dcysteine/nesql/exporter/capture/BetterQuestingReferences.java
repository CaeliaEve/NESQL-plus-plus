package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

import codechicken.nei.recipe.ICraftingHandler;
import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.source.Dataset;
import com.github.dcysteine.nesql.exporter.source.Identity;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.*;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.LoaderState;
import java.util.*;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

/** Visible native quest declarations, without running gameplay, GUI or progress serialization. */
final class BetterQuestingReferences extends ReaderSnapshot implements NativeReferences {
  static final String HANDLER = "bq_standard.integration.nei.QuestRecipeHandler";
  private  JsonObject source;
  private  JsonArray rows;
  private final Map<String, ItemStack> items = new TreeMap<>();
  private final Map<String, FluidStack> fluids = new TreeMap<>();
  private final String signature, domain;
  private final int registrySize;

  static boolean supports(ICraftingHandler handler) {
    return handler != null && HANDLER.equals(handler.getClass().getName());
  }

  BetterQuestingReferences(ICraftingHandler handler, JsonObject source) {
    if (!supports(handler)) throw fault("Unknown quest handler");
    this.source = copy(source);
    rows = current(items, fluids);
    signature = CanonicalJson.digest(rows);
    domain = rows.get(0).getAsJsonObject().get("domain").getAsString();
    registrySize = database().size();
  }

  @SuppressWarnings("unchecked")
  private static Map<UUID, Object> database() {
    Object raw = field(type("betterquesting.questing.QuestDatabase"), null, "INSTANCE");
    if (!(raw instanceof Map)) throw fault("Native quest registry is not a map");
    return (Map<UUID, Object>) raw;
  }

  private static void readiness() {
    Minecraft game = Minecraft.getMinecraft();
    if (!Loader.instance().hasReachedState(LoaderState.AVAILABLE)
        || game == null
        || game.theWorld == null
        || game.thePlayer == null
        || !game.isSingleplayer()
        || field(type("betterquesting.api.storage.BQ_Settings"), null, "curWorldDir") == null
        || game.thePlayer.getExtendedProperties("betterquesting:quest_cache") == null)
      throw new Jobs.Fault(
          "handler_not_ready", "BetterQuesting local world/database/player cache is not loaded");
    Object registry = database();
    if (api("QUEST_DB") != registry || api("SETTINGS") == null)
      throw new Jobs.Fault(
          "handler_not_ready", "BetterQuesting native APIs are not bound to the loaded registry");
  }

  private static Object api(String key) {
    Object nativeKey = field(type("betterquesting.api.api.ApiReference"), null, key);
    return invoke(
        type("betterquesting.api.api.QuestingAPI"),
        null,
        "getAPI",
        new Class<?>[] {type("betterquesting.api.api.ApiKey")},
        nativeKey);
  }

  private JsonArray current(
      Map<String, ItemStack> itemSamples, Map<String, FluidStack> fluidSamples) {
    readiness();
    EntityPlayer player = Minecraft.getMinecraft().thePlayer;
    Object rawUuid =
        invoke(
            type("betterquesting.api.api.QuestingAPI"),
            null,
            "getQuestingUUID",
            new Class<?>[] {EntityPlayer.class},
            player);
    if (!(rawUuid instanceof UUID))
      throw new Jobs.Fault("handler_not_ready", "Native questing player identity is not ready");
    UUID uuid = (UUID) rawUuid;
    Predicate<Object> visibility =
        quest -> {
          Object raw =
              invoke(
                  type("betterquesting.api2.cache.QuestCache"),
                  null,
                  "isQuestShown",
                  new Class<?>[] {
                    type("betterquesting.api.questing.IQuest"), UUID.class, EntityPlayer.class
                  },
                  quest,
                  uuid,
                  player);
          if (!(raw instanceof Boolean))
            throw fault("Native quest visibility callback returned an invalid result");
          return (Boolean) raw;
        };
    JsonObject settings =
        object(
            "noRewards",
            setting("noRewards"),
            "viewMode",
            setting("viewMode"),
            "partyEnable",
            property(api("SETTINGS"), "PARTY_ENABLE"),
            "editMode",
            property(api("SETTINGS"), "EDIT_MODE"),
            "hardcore",
            property(api("SETTINGS"), "HARDCORE"),
            "livesDefault",
            property(api("SETTINGS"), "LIVES_DEF"),
            "livesMax",
            property(api("SETTINGS"), "LIVES_MAX"),
            "fullySyncQuests",
            field(type("drethic.questbook.config.QBConfig"), null, "fullySyncQuests"));
    return snapshot(true, source, database(), visibility, settings, itemSamples, fluidSamples);
  }

  private static Object setting(String name) {
    Object raw = field(type("betterquesting.api.storage.BQ_Settings"), null, name);
    if (!(raw instanceof Boolean)) throw fault("Invalid native world quest policy: " + name);
    return raw;
  }

  public int size() { checkOpen();
    return rows.size();
  }

  public String domain() { checkOpen();
    return domain;
  }

  public JsonObject counts() { checkOpen();
    return copy(rows.get(0).getAsJsonObject().getAsJsonObject("data").getAsJsonObject("counts"));
  }

  public void verify() { checkOpen();
    readiness();
    if (registrySize != database().size()) throw drift();
  }

  public void finishVerification() { checkOpen();
    verify();
    if (!signature.equals(CanonicalJson.digest(current(new TreeMap<>(), new TreeMap<>()))))
      throw drift();
  }

  public void capture(int index, Facts facts) { checkOpen();
    if (index < 0 || index >= rows.size()) throw fault("Invalid quest reference index");
    JsonObject row = copy(rows.get(index).getAsJsonObject());
    emitExamples(row.getAsJsonObject("data"), facts);
    facts.row("references", row);
  }

  private void emitExamples(JsonElement value, Facts facts) {
    if (value.isJsonArray())
      for (JsonElement child : value.getAsJsonArray()) emitExamples(child, facts);
    else if (value.isJsonObject())
      for (Map.Entry<String, JsonElement> entry : value.getAsJsonObject().entrySet()) {
        if (entry.getKey().equals("examples"))
          for (JsonElement sample : entry.getValue().getAsJsonArray()) {
            String id = sample.getAsString();
            ItemStack stack = items.get(id);
            if (stack == null || !facts.item(stack.copy()).equals(id)) throw drift();
          }
        else if (entry.getKey().equals("fluid")) {
          String id = entry.getValue().getAsString();
          FluidStack stack = fluids.get(id);
          if (stack == null || !facts.fluid(stack.copy()).equals(id)) throw drift();
        } else if (!entry.getKey().equals("nbt") && !entry.getKey().equals("targetNBT"))
          emitExamples(entry.getValue(), facts);
      }
  }

  static JsonArray snapshot(
      boolean ready,
      JsonObject source,
      Map<UUID, Object> registry,
      Predicate<Object> visibility,
      JsonObject settings,
      Map<String, ItemStack> items,
      Map<String, FluidStack> fluids) {
    if (!ready) throw new Jobs.Fault("handler_not_ready", "Native quest registry is not loaded");
    for (String key :
        new String[] {
          "noRewards", "viewMode", "partyEnable", "fullySyncQuests", "editMode", "hardcore"
        }) {
      JsonElement entry = settings.get(key);
      if (entry == null || !entry.isJsonPrimitive() || !entry.getAsJsonPrimitive().isBoolean())
        throw fault("Invalid world boolean policy: " + key);
    }
    for (String key : new String[] {"livesDefault", "livesMax"}) {
      JsonElement entry = settings.get(key);
      if (entry == null || !entry.isJsonPrimitive() || !entry.getAsJsonPrimitive().isNumber())
        throw fault("Invalid world integer policy: " + key);
      try {
        entry.getAsBigDecimal().intValueExact();
      } catch (ArithmeticException error) {
        throw fault("Invalid world integer policy: " + key);
      }
    }
    if (registry.size() > 65536) throw fault("Quest registry exceeds budget");
    LinkedHashMap<UUID, Object> visible = new LinkedHashMap<>();
    for (Map.Entry<UUID, Object> entry : registry.entrySet()) {
      Jobs.checkpoint();
      if (entry.getKey() == null
          || entry.getValue() == null
          || !entry.getValue().getClass().getName().equals("betterquesting.questing.QuestInstance"))
        throw fault("Unadapted/null quest definition owner");
      if (visibility.test(entry.getValue())) visible.put(entry.getKey(), entry.getValue());
    }
    JsonArray registeredIds = array(), visibleIds = array();
    for (UUID id : registry.keySet()) registeredIds.add(value(id.toString()));
    for (UUID id : visible.keySet()) visibleIds.add(value(id.toString()));
    JsonArray data = array();
    int taskCount = 0, rewardCount = 0;
    BetterQuestingRules.Budget budget = new BetterQuestingRules.Budget();
    long bytes = 0;
    for (Map.Entry<UUID, Object> entry : visible.entrySet()) {
      Jobs.checkpoint();
      Object quest = entry.getValue();
      UUID id = entry.getKey();
      JsonArray
          tasks =
              entries(BetterQuestingRules.call(quest, "getTasks"), false, items, fluids, budget),
          rewards =
              entries(BetterQuestingRules.call(quest, "getRewards"), true, items, fluids, budget);
      taskCount = Math.addExact(taskCount, tasks.size());
      rewardCount = Math.addExact(rewardCount, rewards.size());
      if (taskCount > 262144
          || rewardCount > 262144
          || items.size() > 262144
          || fluids.size() > 65536) throw fault("Quest declaration total budget exceeded");
      Object rawRequirements = BetterQuestingRules.call(quest, "getRequirements");
      if (!(rawRequirements instanceof Set) || ((Set<?>) rawRequirements).size() > 65536)
        throw fault("Invalid native prerequisites");
      JsonArray requirements = array();
      for (Object raw : (Set<?>) rawRequirements) {
        budget.claim();
        if (!(raw instanceof UUID)) throw fault("Invalid prerequisite UUID");
        Object kind =
            invoke(quest.getClass(), quest, "getRequirementType", new Class<?>[] {UUID.class}, raw);
        requirements.add(
            object(
                "questUuid",
                raw.toString(),
                "type",
                BetterQuestingRules.enumName(kind),
                "state",
                visible.containsKey(raw)
                    ? "visible"
                    : registry.containsKey(raw) ? "hidden" : "missing"));
      }
      JsonObject policy =
          object(
              "main",
              property(quest, "MAIN"),
              "taskLogic",
              BetterQuestingRules.enumName(property(quest, "LOGIC_TASK")),
              "questLogic",
              BetterQuestingRules.enumName(property(quest, "LOGIC_QUEST")),
              "repeatTicks",
              property(quest, "REPEAT_TIME"),
              "repeatRelative",
              property(quest, "REPEAT_REL"),
              "global",
              property(quest, "GLOBAL"),
              "globalShare",
              property(quest, "GLOBAL_SHARE"),
              "autoClaim",
              property(quest, "AUTO_CLAIM"),
              "lockedProgress",
              property(quest, "LOCKED_PROGRESS"),
              "simultaneous",
              property(quest, "SIMULTANEOUS"),
              "silent",
              property(quest, "SILENT"));
      JsonObject record =
          object(
              "kind",
              "questDeclaration",
              "questUuid",
              id.toString(),
              "ordinal",
              data.size(),
              "name",
              localized(id, quest, false),
              "description",
              localized(id, quest, true),
              "visibility",
              BetterQuestingRules.enumName(property(quest, "VISIBILITY")),
              "icon",
              BetterQuestingRules.nullableStack(property(quest, "ICON"), items, budget),
              "policy",
              policy,
              "prerequisites",
              requirements,
              "tasks",
              tasks,
              "rewards",
              rewards);
      int recordBytes = CanonicalJson.bytes(record).length;
      bytes += recordBytes;
      if (recordBytes > Dataset.RECORD_LIMIT - 512 || bytes > 32L * 1024 * 1024)
        throw fault("Quest declaration exceeds Source record or total memory budget");
      data.add(record);
    }
    JsonObject counts =
        object(
            "quests",
            visible.size(),
            "tasks",
            taskCount,
            "rewards",
            rewardCount,
            "items",
            items.size(),
            "fluids",
            fluids.size());
    JsonArray records = array();
    records.add(
        object(
            "kind",
            "questsDefinition",
            "source",
            source,
            "ready",
            true,
            "consuming",
            false,
            "scope",
            "nativeCurrentPlayerVisibility",
            "registered",
            registry.size(),
            "registry",
            registeredIds,
            "visible",
            visibleIds,
            "settings",
            settings,
            "counts",
            counts));
    if (CanonicalJson.bytes(records.get(0)).length > Dataset.RECORD_LIMIT - 512)
      throw fault("Quest registry identity proof exceeds Source record budget");
    for (JsonElement row : data) records.add(row);
    String domain =
        Identity.content("reference", object("kind", "betterQuesting", "records", records));
    JsonArray rows = array();
    for (int order = 0; order < records.size(); order++) {
      JsonObject row = object("domain", domain, "order", order, "data", records.get(order));
      row.addProperty("id", Identity.content("refrow", row));
      rows.add(row);
    }
    return rows;
  }

  private static JsonArray entries(
      Object storage,
      boolean reward,
      Map<String, ItemStack> items,
      Map<String, FluidStack> fluids,
      BetterQuestingRules.Budget budget) {
    List<?> entries = BetterQuestingRules.list(BetterQuestingRules.call(storage, "getEntries"));
    JsonArray result = array();
    int previous = -1;
    for (Object entry : entries) {
      Object id = BetterQuestingRules.call(entry, "getID"),
          value = BetterQuestingRules.call(entry, "getValue");
      if (!(id instanceof Integer) || (Integer) id <= previous)
        throw fault("Unsorted or duplicate native task/reward ID");
      previous = (Integer) id;
      JsonObject rule =
          reward
              ? BetterQuestingRules.reward(value, items, budget)
              : BetterQuestingRules.task(value, items, fluids, budget);
      Object factory = BetterQuestingRules.call(value, "getFactoryID");
      if (factory == null) throw fault("Missing task/reward factory identity");
      result.add(
          object(
              "nativeId",
              id,
              "nativeClass",
              value.getClass().getName(),
              "factory",
              factory.toString(),
              "labelKey",
              BetterQuestingRules.string(BetterQuestingRules.call(value, "getUnlocalisedName")),
              "rule",
              rule));
    }
    return result;
  }

  static Object property(Object target, String name) {
    Object property = field(type("betterquesting.api.properties.NativeProps"), null, name);
    return invoke(
        target.getClass(),
        target,
        "getProperty",
        new Class<?>[] {type("betterquesting.api.properties.IPropertyType")},
        property);
  }

  private static JsonObject localized(UUID uuid, Object quest, boolean description) {
    Class<?> translation = type("betterquesting.api2.utils.QuestTranslation");
    String key =
        BetterQuestingRules.string(
            invoke(
                translation,
                null,
                description ? "buildQuestDescriptionKey" : "buildQuestNameKey",
                new Class<?>[] {UUID.class},
                uuid));
    String value =
        BetterQuestingRules.string(
            invoke(
                translation,
                null,
                description ? "translateQuestDescription" : "translateQuestName",
                new Class<?>[] {UUID.class, type("betterquesting.api.questing.IQuest")},
                uuid,
                quest));
    return object(
        "key",
        key,
        "raw",
        BetterQuestingRules.string(property(quest, description ? "DESC" : "NAME")),
        "value",
        value);
  }

  private static JsonObject copy(JsonObject value) {
    return new JsonParser().parse(value.toString()).getAsJsonObject();
  }

  private static Jobs.Fault fault(String message) {
    return new Jobs.Fault("handler_unsupported", "BetterQuesting: " + message);
  }

  private static Jobs.Fault drift() {
    return new Jobs.Fault(
        "environment_changed",
        "Native quest definitions, visibility, settings or language changed");
  }

    @Override protected void release(){items.clear();fluids.clear();source=null;rows=null;}
}
