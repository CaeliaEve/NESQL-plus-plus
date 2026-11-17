package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.*;
import java.util.*;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/** Owned native carving selections, including the two native pickup paths and tool state. */
final class ChiselRecipes implements RegistryRecipes {
  private static final String HANDLER = "team.chisel.compat.nei.RecipeHandlerChisel";
  private static final String TOOL = "team.chisel.item.chisel.ItemChisel";
  private Snapshot snapshot;
  private ChiselSource nativeSource;
  private boolean closed;
  private final List<Page> pages = new ArrayList<>();

  static boolean supports(ICraftingHandler value) {
    return supportsClass(value.getClass().getName());
  }

  static boolean supportsClass(String name) {
    return HANDLER.equals(name);
  }

  ChiselRecipes(TemplateRecipeHandler handler) {
    MagicApi.version("ChiselRecipes","chisel");
    if (!supports(handler)) throw fault("Unknown Chisel handler");
    try {
      nativeSource = new ChiselSource();
      snapshot = nativeSource.snapshot;
      Map<String, Target> targets = new TreeMap<>();
      Map<Item, List<Decision>> partitions = new IdentityHashMap<>();
      Map<Decision, Integer> order = new IdentityHashMap<>();
      IdentityHashMap<List<ItemStack>,List<Decision>> selections=new IdentityHashMap<>();
      List<List<ItemStack>> selectedTargets=new ArrayList<>();
      long references = 0;
      for (Decision decision : snapshot.sources) {
        order.put(decision, order.size());
        partitions
            .computeIfAbsent(decision.source.getItem(), unused -> new ArrayList<>())
            .add(decision);
        List<Decision> selected=selections.get(decision.targets);
        if(selected==null){selected=new ArrayList<>();selections.put(decision.targets,selected);selectedTargets.add(decision.targets);}
        selected.add(decision);
      }
      for(List<ItemStack> group:selectedTargets){
        List<Decision> selected=selections.get(group);
        Set<String> seen = new HashSet<>();
        for (ItemStack target : group) {
          if (++references > 262144)
            throw new Jobs.Fault("source_budget", "Chisel inverse selections exceed budget");
          String key = key(target);
          if (seen.add(key))
            targets.computeIfAbsent(key, unused -> new Target(target)).groups.add(selected);
        }
      }
      Map<List<List<Decision>>,Selection> contexts=new HashMap<>();
      for (Target target : targets.values()) {
        Selection selection=contexts.get(target.groups);
        if(selection==null){
          List<Decision> sources=new ArrayList<>();for(List<Decision> group:target.groups)sources.addAll(group);
          sources.sort(Comparator.comparingInt(order::get));
          Set<Item> registries = Collections.newSetFromMap(new IdentityHashMap<>());
          for (Decision decision : sources)if (decision.wildcard) registries.add(decision.source.getItem());
          Set<Decision> selected = Collections.newSetFromMap(new IdentityHashMap<>());
          selected.addAll(sources);
          for (Item item : registries) selected.addAll(partitions.get(item));
          List<Decision> context = new ArrayList<>(selected);
          context.sort(Comparator.comparingInt(order::get));
          references+=context.size()+sources.size();
          selection=new Selection(sources,context);contexts.put(target.groups,selection);
        }
        references += 2;
        if (references > 262144 || pages.size() > 262142)
          throw new Jobs.Fault("source_budget", "Chisel row contexts exceed cumulative budget");
        pages.add(new Page(selection.sources, target.stack, false, selection.context));
        pages.add(new Page(selection.sources, target.stack, true, selection.context));
      }
      if (pages.size() > 262144) throw fault("Chisel selections exceed row budget");
    } catch (RuntimeException | Error failure) {
      close();
      throw failure;
    }
  }

  public int size() {
    check();
    nativeSource.verifyRoots();
    return pages.size();
  }

  public void verify() {
    check();
    nativeSource.verifyRoots();
  }

  public void finishVerification() {
    check();
    nativeSource.verify();
  }

  public boolean capture(int index, RecipeRow row) {
    check();
    if (index < 0 || index >= pages.size())
      throw new IndexOutOfBoundsException("Chisel selection " + index);
    nativeSource.verifyRoots();
    Page page = pages.get(index);
    List<RecipeRow.Ingredient> inputs = new ArrayList<>();
    for (Decision d : page.sources)
      inputs.add(
          new RecipeRow.Ingredient(
              d.source.copy(),
              1,
              false,
              object("kind", "chiselSource", "meta", d.wildcard, "nbt", true)));
    row.itemInput(0, inputs, false);
    if (page.bulk)
      for (JsonElement choice : row.inputs.get(0).getAsJsonObject().getAsJsonArray("choices"))
        choice.getAsJsonObject().add("consume", object("kind", "stack"));
    List<RecipeRow.Ingredient> tools = new ArrayList<>();
    for (Tool tool : snapshot.tools)
      tools.add(
          new RecipeRow.Ingredient(
              tool.stack.copy(), 1, true, object("kind", "chiselTool", "source", 0)));
    row.itemInput(1, tools, false);
    ItemStack result = page.bulk ? fresh(page.target) : page.target.copy();
    row.itemOutput(0,
        result,
        10000,
        Amounts.potential(
            "chisel:selection",
            "compatibleToolAndNativeTarget",
            Integer.toString(result.stackSize),
            result.stackSize));
    JsonArray decisions = array(), toolPolicies = array();
    String selectedTarget = key(page.target);
    for (Decision decision : page.context) {
      JsonArray targets = array();
      for (ItemStack target : decision.targets)
        if (selectedTarget.equals(key(target))) {
          targets.add(object("item", row.facts.item(target), "count", target.stackSize));
          break;
        }
      decisions.add(
          object(
              "item",
              row.facts.item(decision.source),
              "nativeId",
              Item.getIdFromItem(decision.source.getItem()),
              "nativeWildcard",
              decision.wildcard,
              "targets",
              targets));
    }
    JsonArray samples = array();
    ItemStack first = null;
    for (Tool tool : snapshot.tools) {
      toolPolicies.add(
          object(
              "item",
              row.facts.item(tool.stack),
              "owner",
              TOOL,
              "damageEnabled",
              snapshot.damage,
              "maxDamage",
              tool.maxDamage));
      ItemStack changed = sampleTool(tool);
      if (first == null) first = changed;
      samples.add(object("id", row.facts.item(changed), "amount", "1"));
    }
    row.itemOutput(1, first, 10000);
    JsonObject returned = row.outputs.get(1).getAsJsonObject();
    returned.addProperty("role", "return");
    returned.add(
        "change",
        object(
            "input",
            1,
            "action",
            object("kind", "chiselTool", "source", 0, "mode", page.bulk ? "stack" : "single"),
            "samples",
            samples));
    row.record.add(
        "process",
        object(
            "kind",
            "chisel",
            "mode",
            page.bulk ? "stack" : "single",
            "sourceSlot",
            0,
            "toolSlot",
            1,
            "target",
            row.facts.item(page.target),
            "targetCount",
            page.target.stackSize,
            "selection",
            "nativeLookupExactPartitionsThenWildcard",
            "sourceNbt",
            "ignored",
            "canChisel",
            "nativeItemChiselAlways",
            "onChisel",
            "configuredAllowChiselDamage",
            "damagePolicy",
            "vanillaCreativeUnbreakingUnbreakable",
            "damagePoints",
            1,
            "targetTag",
            "chiselTarget",
            "sources",
            decisions,
            "tools",
            toolPolicies));
    return true;
  }

  private ItemStack sampleTool(Tool tool) {
    // The canonical witness has one fresh native tool, no enchantments and one source item.
    // Native ItemChisel accepts every variation; onChisel returns the observed damage flag.
    ItemStack changed = tool.stack.copy();
    net.minecraft.nbt.NBTTagCompound tags = new net.minecraft.nbt.NBTTagCompound();
    tags.setTag("chiselTarget", new net.minecraft.nbt.NBTTagCompound());
    changed.setTagCompound(tags);
    if (snapshot.damage && tool.maxDamage > 0) changed.setItemDamage(1);
    return changed;
  }

  private static ItemStack fresh(ItemStack item) {
    return new ItemStack(item.getItem(), 1, item.getItemDamage());
  }

  static String key(ItemStack item) {
    return CanonicalJson.digest(raw(item));
  }

  static JsonObject raw(ItemStack item) {
    return object(
        "registry",
        Item.itemRegistry.getNameForObject(item.getItem()),
        "meta",
        item.getItemDamage(),
        "nbt",
        TypedNbt.encode(item.getTagCompound()),
        "count",
        item.stackSize);
  }

  private void check() {
    Jobs.checkpoint();
    if (closed) throw new Jobs.Fault("capture_closed", "Chisel reader closed");
  }

  public void close() {
    if (closed) return;
    closed = true;
    pages.clear();
    if (snapshot != null) {
      snapshot.sources.clear();
      snapshot.tools.clear();
      snapshot.groups = null;
    }
    snapshot = null;
    if (nativeSource != null) nativeSource.close();
    nativeSource = null;
  }

  static final class Decision {
    final ItemStack source;
    final boolean wildcard;
    final List<ItemStack> targets;

    Decision(ItemStack source, boolean wildcard, List<ItemStack> targets) {
      this.source = source;
      this.wildcard = wildcard;
      this.targets = targets;
    }

    JsonObject json() {
      JsonArray rows = array();
      for (ItemStack target : targets) rows.add(raw(target));
      return object("source", raw(source), "wildcard", wildcard, "targets", rows);
    }
  }

  static final class Tool {
    final ItemStack stack;
    final int maxDamage;

    Tool(ItemStack stack, int maxDamage) {
      this.stack = stack;
      this.maxDamage = maxDamage;
    }

    JsonObject json() {
      return object(
          "item",
          raw(stack),
          "owner",
          stack.getItem().getClass().getName(),
          "maxDamage",
          maxDamage);
    }
  }

  static final class Snapshot {
    final boolean damage;
    JsonArray groups;
    final List<Decision> sources = new ArrayList<>();
    final List<Tool> tools = new ArrayList<>();

    Snapshot(boolean damage) {
      this.damage = damage;
    }

    JsonObject json() {
      JsonArray decisions = array(), policies = array(),targetSets=array();
      IdentityHashMap<List<ItemStack>,Integer> indices=new IdentityHashMap<>();
      for (Decision d : sources) {
        Integer index=indices.get(d.targets);
        if(index==null){index=indices.size();indices.put(d.targets,index);JsonArray rows=array();for(ItemStack target:d.targets)rows.add(raw(target));targetSets.add(rows);}
        decisions.add(object("source",raw(d.source),"wildcard",d.wildcard,"targetSet",index));
      }
      for (Tool t : tools) policies.add(t.json());
      return object("groups", groups, "sources", decisions, "targetSets",targetSets,"tools", policies, "damage", damage);
    }
  }

  private static final class Page {
    final List<Decision> sources, context;
    final ItemStack target;
    final boolean bulk;

    Page(List<Decision> sources, ItemStack target, boolean bulk, List<Decision> context) {
      this.sources = sources;
      this.target = target.copy();
      this.bulk = bulk;
      this.context = context;
    }
  }

  private static final class Target {
    final ItemStack stack;
    final List<List<Decision>> groups = new ArrayList<>();

    Target(ItemStack stack) {
      this.stack = stack;
    }
  }

  private static final class Selection {
    final List<Decision> sources,context;
    Selection(List<Decision> sources,List<Decision> context){this.sources=sources;this.context=context;}
  }

  private static Jobs.Fault fault(String message) {
    return new Jobs.Fault("recipe_unsupported", message);
  }
}
