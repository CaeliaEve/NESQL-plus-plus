package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

import codechicken.nei.ItemList;
import codechicken.nei.recipe.*;
import com.github.dcysteine.nesql.exporter.source.*;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.*;
import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.item.*;
import net.minecraft.item.crafting.CraftingManager;

/** Native dynamic rules and bounded examples, without NEI GUI or subtype callbacks. */
final class ProjectBlueRecipes implements NativeReferences {
  private static final String HANDLER = "gcewing.projectblue.nei.NEIRecipeHandler";
  private ICraftingHandler handler;
  private List<?> nativeSource, crafting;
  private List<ItemStack> panel;
  private NativeDefinitions definitions;
  private ProjectBlueMaterials materials;
  private final List<JsonObject> records = new ArrayList<>();
  private final Map<String, ItemStack> specimens = new LinkedHashMap<>();
  private final Map<String, Item> core = new LinkedHashMap<>();
  private List<Object> anchors;
  private String sourceSignature, domain;
  private JsonObject counts;
  private boolean closed;

  static boolean supports(ICraftingHandler value) {
    return value != null && supportsClass(value.getClass().getName());
  }

  static boolean supportsClass(String name) {
    return HANDLER.equals(name);
  }

  static List<?> registrationSource(IRecipeHandler handler) {
    if (!supportsClass(handler.getClass().getName()))
      throw fault("Unknown ProjectBlue registration");
    Object source = field(type("gcewing.projectblue.ControlPanelRecipes"), null, "recipes");
    if (source == null)
      throw new Jobs.Fault("handler_not_ready", "Native ProjectBlue recipe list is unavailable");
    if (source.getClass() != ArrayList.class)
      throw fault("Unadapted native ProjectBlue recipe storage");
    return (List<?>) source;
  }

  ProjectBlueRecipes(ICraftingHandler handler, JsonObject source) {
    if (!supports(handler)) throw fault("Unknown ProjectBlue handler");
    this.handler = handler;
    try {
      nativeSource = registrationSource(handler);
      definitions =
          new NativeDefinitions(
              "ProjectBlue",
              nativeSource,
              () -> registrationSource(this.handler),
              value ->
                  object(
                      "native",
                      value == null ? null : value.getClass().getName(),
                      "mode",
                      ProjectBlueLogic.mode(value)));
      JsonArray data = array();
      JsonObject definition =
          object(
              "kind",
              "projectBlueDefinition",
              "source",
              source,
              "ready",
              true,
              "consuming",
              false,
              "gridWidth",
              3,
              "gridHeight",
              3,
              "pickup",
              "vanillaWorkbenchAllOccupiedCells",
              "nativeRules",
              nativeSource.size(),
              "core",
              new JsonObject());
      data.add(definition);
      int examples = 0, roles = 0, materialRows = 0;
      if (!nativeSource.isEmpty()) {
        if (!ItemList.loadFinished || ItemList.items == null)
          throw new Jobs.Fault(
              "handler_not_ready", "ProjectBlue examples require a finished native item catalogue");
        panel = ItemList.items;
        crafting = CraftingManager.getInstance().getRecipeList();
        for (String name : ProjectBlueLogic.CORE) {
          Object item = field(type(ProjectBlueLogic.ROOT), null, name);
          if (!(item instanceof Item))
            throw new Jobs.Fault("handler_not_ready", "Missing ProjectBlue item: " + name);
          core.put(name, (Item) item);
          definition.getAsJsonObject("core").addProperty(name, registry((Item) item));
        }
        materials = new ProjectBlueMaterials();
        for (String table : new String[] {"cache", "registry", "aliases"})
          for (JsonElement entry : materials.records().getAsJsonArray(table)) {
            JsonObject row = entry.getAsJsonObject();
            row.addProperty(
                "kind",
                table.equals("cache")
                    ? "projectBlueCachedMaterial"
                    : table.equals("registry")
                        ? "projectBlueRegisteredMaterial"
                        : "projectBlueMaterialAlias");
            data.add(row);
            materialRows++;
          }
        SourceState state = readSource();
        anchors = state.anchors;
        sourceSignature = CanonicalJson.digest(state.facts);
        for (JsonElement role : state.roles) {
          JsonObject row = role.getAsJsonObject();
          row.addProperty("kind", "projectBlueItem");
          data.add(row);
          roles++;
        }
        ProjectBlueLogic logic = new ProjectBlueLogic(core, materials);
        ItemStack saw = null;
        List<ItemStack> controls = new ArrayList<>(), sprays = new ArrayList<>();
        for (ItemStack stack : panel) {
          if (stack == null || stack.getClass() != ItemStack.class)
            throw fault("Unadapted ProjectBlue catalogue stack");
          Item item = stack.getItem();
          if (item == null) throw fault("Null ProjectBlue catalogue item");
          if (saw == null && type("codechicken.microblock.ItemSaw").isInstance(item)) saw = stack;
          if (ProjectBlueLogic.paintable(stack)) controls.add(stack);
          if (type("gcewing.projectblue.SprayCanItem").isInstance(item)) sprays.add(stack);
        }
        for (int index = 0; index < nativeSource.size(); index++) {
          String mode = ProjectBlueLogic.mode(nativeSource.get(index));
          data.add(
              object(
                  "kind",
                  "projectBlueRule",
                  "index",
                  index,
                  "recipeClass",
                  nativeSource.get(index).getClass().getName(),
                  "mode",
                  mode,
                  "inputs",
                  mode.equals("paint") ? array(0, 1) : array(0, 4),
                  "requiredEmpty",
                  mode.equals("paint") ? array() : array(1, 3),
                  "ignored",
                  mode.equals("paint") ? array(2, 3, 4, 5, 6, 7, 8) : array(2, 5, 6, 7, 8),
                  "outputAmount",
                  mode.equals("miniature") ? 8 : 1,
                  "materialPolicy",
                  mode.equals("panel")
                      ? "defaultWood"
                      : mode.equals("miniature") ? "coverRequiresMaterial" : "numericSprayColor",
                  "anchored",
                  true,
                  "mirrored",
                  false));
          int emitted = 0;
          if (mode.equals("paint")) {
            for (ItemStack control : controls) {
              for (ItemStack spray : sprays) {
                ItemStack[] cells = new ItemStack[9];
                cells[0] = control;
                cells[1] = spray;
                if (example(data, index, mode, cells, logic)) {
                  examples++;
                  emitted++;
                }
                if (emitted >= 32) break;
              }
              if (emitted >= 32) break;
            }
          } else if (saw != null) {
            for (ItemStack offered : panel) {
              ItemStack[] cells = new ItemStack[9];
              cells[0] = saw;
              cells[4] = offered;
              if (example(data, index, mode, cells, logic)) {
                examples++;
                emitted++;
              }
              if (emitted >= 32) break;
            }
          }
        }
      }
      counts =
          object(
              "rules",
              nativeSource.size(),
              "roles",
              roles,
              "materials",
              materialRows,
              "examples",
              examples,
              "items",
              specimens.size());
      definition.add("counts", counts);
      domain = Identity.content("reference", object("kind", "projectBlueRules", "records", data));
      for (int index = 0; index < data.size(); index++) {
        JsonObject row = object("domain", domain, "order", index, "data", data.get(index));
        row.addProperty("id", Identity.content("refrow", row));
        if (CanonicalJson.bytes(row).length > 1024 * 1024)
          throw new Jobs.Fault("reference_limit", "ProjectBlue reference leaf exceeds 1 MiB");
        records.add(row);
      }
      finishVerification();
    } catch (RuntimeException | Error failure) {
      close();
      throw failure;
    }
  }

  public int size() {
    check();
    return records.size();
  }

  public String domain() {
    check();
    return domain;
  }

  public JsonObject counts() {
    check();
    return copy(counts);
  }

  public void verify() {
    check();
    if (registrationSource(handler) != nativeSource) throw changed();
    definitions.finishVerification();
    if (!nativeSource.isEmpty()) {
      if (!ItemList.loadFinished
          || ItemList.items != panel
          || CraftingManager.getInstance().getRecipeList() != crafting) throw changed();
      for (String name : ProjectBlueLogic.CORE)
        if (field(type(ProjectBlueLogic.ROOT), null, name) != core.get(name)) throw changed();
      materials.verify();
    }
  }

  public void finishVerification() {
    verify();
    if (nativeSource.isEmpty()) return;
    SourceState current = readSource();
    if (!sourceSignature.equals(CanonicalJson.digest(current.facts))
        || anchors.size() != current.anchors.size()) throw changed();
    for (int index = 0; index < anchors.size(); index++)
      if (anchors.get(index) != current.anchors.get(index)) throw changed();
  }

  public void capture(int index, Facts facts) {
    verify();
    JsonObject row = records.get(index);
    JsonObject data = row.getAsJsonObject("data");
    try {
      if (data.get("kind").getAsString().equals("projectBlueExample")) {
        for (JsonElement cell : data.getAsJsonArray("cells"))
          if (!cell.isJsonNull())
            captureItem(cell.getAsJsonObject().get("item").getAsString(), facts);
        captureItem(data.getAsJsonObject("output").get("item").getAsString(), facts);
      }
      facts.row("references", copy(row));
    } catch (RuntimeException | Error failure) {
      try {
        verify();
      } catch (RuntimeException | Error drift) {
        if (drift != failure) failure.addSuppressed(drift);
      }
      throw failure;
    }
    verify();
  }

  private void captureItem(String id, Facts facts) {
    if (!id.equals(facts.item(specimens.get(id).copy()))) throw changed();
  }

  private boolean example(
      JsonArray data, int rule, String mode, ItemStack[] cells, ProjectBlueLogic logic) {
    ProjectBlueLogic.Outcome outcome = logic.evaluate(mode, cells);
    if (outcome.output == null) return false;
    JsonArray inputs = array();
    for (ItemStack stack : cells) inputs.add(stack == null ? JsonNull.INSTANCE : specimen(stack));
    data.add(
        object(
            "kind",
            "projectBlueExample",
            "rule",
            rule,
            "cells",
            inputs,
            "materialKey",
            ProjectBlueLogic.materialKey(mode, cells),
            "output",
            specimen(outcome.output)));
    return true;
  }

  private JsonObject specimen(ItemStack stack) {
    JsonObject raw = ProjectBlueLogic.raw(stack);
    String id =
        Identity.item(
            raw.get("registry").getAsString(), raw.get("meta").getAsInt(), raw.get("nbt"));
    ItemStack copy = stack.copy();
    copy.stackSize = 1;
    specimens.putIfAbsent(id, copy);
    return object("item", id, "amount", Integer.toString(stack.stackSize));
  }

  private SourceState readSource() {
    SourceState result = new SourceState();
    // Observe registered classes, never invoke getSubItems or a recipe callback.
    TreeMap<String, Item> items = new TreeMap<>();
    for (Object value : Item.itemRegistry) {
      Jobs.checkpoint();
      if (value instanceof Item) items.put(registry((Item) value), (Item) value);
    }
    for (Map.Entry<String, Item> entry : items.entrySet()) {
      Item item = entry.getValue();
      JsonArray roles = array();
      if (type("codechicken.microblock.ItemSaw").isInstance(item)) roles.add(value("saw"));
      if (type("gcewing.projectblue.SprayCanItem").isInstance(item)) roles.add(value("spray"));
      if (type("gcewing.projectblue.ControlItem").isInstance(item)) {
        Object kind = field(item, "type");
        if (!(kind instanceof Enum)) throw fault("Uninitialized native ProjectBlue control type");
        if (((Enum<?>) kind).name().equals("LEVER")) roles.add(value("lever"));
        if (((Enum<?>) kind).name().equals("BUTTON")) roles.add(value("button"));
      }
      Block block = Block.getBlockFromItem(item);
      if (item instanceof ItemBlock) {
        if (block == Blocks.lever) roles.add(value("fullLever"));
        if (block == Blocks.stone_button) roles.add(value("stoneButton"));
        if (block == Blocks.wooden_button) roles.add(value("woodButton"));
      }
      if (roles.size() == 0) continue;
      ProjectBlueLogic.audit(new ItemStack(item));
      Integer maximum = null;
      Boolean damageable = null;
      if (type("codechicken.microblock.ItemSaw").isInstance(item)) {
        for (String[] method :
            new String[][] {
              {"getContainerItem", "getContainerItem"},
              {"doesContainerItemLeaveCraftingGrid", "doesContainerItemLeaveCraftingGrid"}
            })
          if (!ItemCallbacks.method(item, method[0], method[1], ItemStack.class)
              .getDeclaringClass()
              .getName()
              .equals("codechicken.microblock.ItemSaw"))
            throw fault("Unadapted ProjectBlue saw return callback");
        if (!ItemCallbacks.method(item, "hasContainerItem", "func_77634_r")
                .getDeclaringClass()
                .getName()
                .equals("codechicken.microblock.ItemSaw")
            || ItemCallbacks.method(item, "hasContainerItem", "hasContainerItem", ItemStack.class)
                    .getDeclaringClass()
                != Item.class
            || ItemCallbacks.method(item, "isDamageable", "func_77645_m").getDeclaringClass()
                != Item.class
            || ItemCallbacks.method(item, "getMaxDamage", "func_77612_l").getDeclaringClass()
                != Item.class) throw fault("Unadapted ProjectBlue saw durability predicate");
        maximum = item.getMaxDamage();
        damageable = item.isDamageable();
      }
      if (type("gcewing.projectblue.SprayCanItem").isInstance(item)) {
        ProjectBlueLogic.auditPaint(item);
        for (String method :
            new String[] {
              "hasContainerItem", "getContainerItem", "doesContainerItemLeaveCraftingGrid"
            })
          if (!ItemCallbacks.method(item, method, method, ItemStack.class)
              .getDeclaringClass()
              .getName()
              .equals("gcewing.projectblue.SprayCanItem"))
            throw fault("Unadapted native spray return callback");
        if (!ItemCallbacks.method(item, "getMaxDamage", "func_77612_l")
            .getDeclaringClass()
            .getName()
            .equals("gcewing.projectblue.SprayCanItem"))
          throw fault("Unadapted spray exhaustion predicate");
        maximum = item.getMaxDamage();
      }
      JsonObject row =
          object(
              "registry",
              entry.getKey(),
              "roles",
              roles,
              "maxDamage",
              maximum,
              "damageable",
              damageable);
      result.roles.add(row);
      result.anchors.add(item);
    }
    JsonArray panelFacts = array(), craftingFacts = array();
    if (panel.size() > 1000000 || crafting.size() > 1000000)
      throw fault("ProjectBlue native source exceeds budget");
    for (ItemStack stack : panel) {
      Jobs.checkpoint();
      if (stack == null || stack.getClass() != ItemStack.class)
        throw fault("Unadapted native catalogue stack");
      result.anchors.add(stack);
      result.anchors.add(stack.getTagCompound());
      panelFacts.add(RecipeRow.stackEvidence(stack));
    }
    for (int index = 0; index < crafting.size(); index++) {
      Object rule = crafting.get(index);
      boolean nativeRule = false;
      for (Object registered : nativeSource)
        if (registered == rule) {
          nativeRule = true;
          break;
        }
      if (nativeRule) {
        result.anchors.add(rule);
        craftingFacts.add(object("index", index, "native", rule.getClass().getName()));
      }
    }
    // Every registered ProjectBlue list instance must really be installed in crafting.
    for (Object rule : nativeSource) {
      boolean found = false;
      for (Object registered : crafting)
        if (registered == rule) {
          found = true;
          break;
        }
      if (!found)
        throw new Jobs.Fault(
            "handler_not_ready", "ProjectBlue native rule is not installed in crafting");
    }
    result.facts =
        object(
            "roles",
            result.roles,
            "panel",
            panelFacts,
            "crafting",
            craftingFacts,
            "craftingCount",
            crafting.size());
    return result;
  }

  JsonArray records() {
    check();
    JsonArray result = array();
    for (JsonObject row : records) result.add(copy(row));
    return result;
  }

  Map<String, ItemStack> specimenCopies() {
    check();
    Map<String, ItemStack> result = new LinkedHashMap<>();
    specimens.forEach((id, stack) -> result.put(id, stack.copy()));
    return result;
  }

  private static String registry(Item item) {
    String key = Item.itemRegistry.getNameForObject(item);
    if (key == null || Item.itemRegistry.getObject(key) != item)
      throw fault("Unregistered native ProjectBlue item");
    return key;
  }

  private void check() {
    Jobs.checkpoint();
    if (closed) throw new Jobs.Fault("capture_closed", "ProjectBlue reader is closed");
  }

  public void close() {
    if (closed) return;
    closed = true;
    if (definitions != null) definitions.close();
    if (materials != null) materials.close();
    records.clear();
    specimens.clear();
    core.clear();
    if (anchors != null) anchors.clear();
    handler = null;
    nativeSource = null;
    panel = null;
    crafting = null;
    definitions = null;
    materials = null;
    anchors = null;
    counts = null;
    sourceSignature = null;
    domain = null;
  }

  private static JsonObject copy(JsonObject value) {
    return new JsonParser().parse(value.toString()).getAsJsonObject();
  }

  private static Jobs.Fault fault(String message) {
    return new Jobs.Fault("recipe_unsupported", message);
  }

  private static Jobs.Fault changed() {
    return new Jobs.Fault(
        "recipe_changed", "Native ProjectBlue rule, item, catalogue or source changed");
  }

  private static final class SourceState {
    final List<Object> anchors = new ArrayList<>();
    final JsonArray roles = array();
    JsonObject facts;
  }
}
