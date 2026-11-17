package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;

import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.common.base.Optional;
import java.util.*;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/** Ready AE2 API fields and native HashSet encounter order. No GUI or definition callbacks. */
final class InscriberSource implements AutoCloseable {
  private State initial;
  private final List<Object> records = new ArrayList<>();
  private final String nameSignature;
  private final int count;

  InscriberSource() {
    State state = read();
    initial = state;
    records.addAll(state.recipes);
    count = state.recipes.size();
    nameSignature = CanonicalJson.digest(RecipeRow.stackEvidence(state.namePress));
  }

  static Object[] registrationAnchors() {
    return read().roots;
  }

  ItemStack namePress() {
    alive();
    return initial.namePress == null ? null : initial.namePress.copy();
  }

  List<?> current() {
    verify();
    return records;
  }

  void finishVerification() {
    verify();
    int index = 0;
    for (Object member : initial.recipes) {
      Jobs.checkpoint();
      if (index >= records.size() || records.get(index++) != member) throw changed();
    }
    if (index != records.size()) throw changed();
  }

  void verify() {
    alive();
    Jobs.checkpoint();
    try {
      State now = read();
      if (now.roots.length != initial.roots.length
          || now.recipes.size() != count
          || !nameSignature.equals(CanonicalJson.digest(RecipeRow.stackEvidence(now.namePress))))
        throw changed();
      for (int i = 0; i < now.roots.length; i++)
        if (now.roots[i] != initial.roots[i]) throw changed();
    } catch (Jobs.Fault cause) {
      if (cause.code.equals("environment_changed")) throw cause;
      Jobs.Fault drift = changed();
      drift.initCause(cause);
      throw drift;
    }
  }

  private void alive() {
    if (initial == null)
      throw new Jobs.Fault("capture_closed", "Native inscriber reader is closed");
  }

  @Override
  public void close() {
    initial = null;
    records.clear();
  }

  private static State read() {
    MagicApi.version("InscriberSource","appliedenergistics2");
    Object api = exact(field(type("appeng.api.AEApi"), null, "HELD_API"), "appeng.core.Api");
    Object registries =
        exact(field(api, "registryContainer"), "appeng.core.features.registries.RegistryContainer");
    Object registry =
        exact(field(registries, "inscriber"), "appeng.core.features.registries.InscriberRegistry");
    Object raw = field(registry, "recipes");
    if (raw == null) throw notReady();
    if (raw.getClass() != HashSet.class) throw fault("Unadapted native inscriber set storage");
    Object definitions = exact(field(api, "definitions"), "appeng.core.ApiDefinitions");
    Object materials =
        exact(field(definitions, "materials"), "appeng.core.api.definitions.ApiMaterials");
    Object name =
        exact(field(materials, "namePress"), "appeng.core.features.DamagedItemDefinition");
    Object optional = field(name, "source");
    Object source = optional(optional);
    List<Object> roots =
        new ArrayList<>(
            Arrays.asList(api, registries, registry, raw, definitions, materials, name, optional));
    ItemStack namePress = null;
    if (source != null) {
      roots.add(source);
      Item item;
      int metadata;
      if (source.getClass() == type("appeng.core.features.MaterialStackSrc")) {
        Object material = field(source, "src");
        if (!(material instanceof Enum)
            || ((Enum<?>) material).getDeclaringClass()
                != type("appeng.items.materials.MaterialType"))
          throw fault("Unadapted native name-press material");
        roots.add(material);
        item = (Item) field(material, "itemInstance");
        metadata = (Integer) field(material, "damageValue");
      } else if (source.getClass() == type("appeng.core.features.ItemStackSrc")) {
        item = (Item) field(source, "item");
        metadata = (Integer) field(source, "damage");
      } else
        throw fault("Unadapted native name-press stack source: " + source.getClass().getName());
      if (item == null) throw notReady();
      roots.add(item);
      namePress = new ItemStack(item, 1, metadata);
      NativeStackAudit.audit(namePress);
      String id = Item.itemRegistry.getNameForObject(item);
      if (id == null || Item.itemRegistry.getObject(id) != item)
        throw fault("Unregistered native name-press item");
    }
    return new State((Collection<?>) raw, namePress, roots.toArray());
  }

  static Object optional(Object value) {
    if (value == null) throw notReady();
    if (!value.getClass().getName().equals("com.google.common.base.Present")
        && !value.getClass().getName().equals("com.google.common.base.Absent"))
      throw fault("Unadapted native optional storage");
    return ((Optional<?>) value).orNull();
  }

  private static Object exact(Object value, String name) {
    if (value == null) throw notReady();
    if (value.getClass() != type(name))
      throw fault("Unadapted native inscriber owner: " + value.getClass().getName());
    return value;
  }

  private static Jobs.Fault notReady() {
    return new Jobs.Fault("handler_not_ready", "Native inscriber source is unavailable");
  }

  private static Jobs.Fault fault(String message) {
    return new Jobs.Fault("recipe_unsupported", message);
  }

  private static Jobs.Fault changed() {
    return new Jobs.Fault(
        "environment_changed", "Native inscriber registry or name-press definition changed");
  }

  private static final class State {
    final Collection<?> recipes;
    final ItemStack namePress;
    final Object[] roots;

    State(Collection<?> recipes, ItemStack namePress, Object[] roots) {
      if (recipes == null) throw notReady();
      if (recipes.getClass() != HashSet.class)
        throw fault("Unadapted native inscriber collection storage");
      if (recipes.size() > 262144)
        throw new Jobs.Fault("recipe_limit", "Native inscriber registry exceeds budget");
      this.recipes = recipes;
      this.namePress = namePress;
      this.roots = roots;
    }
  }
}
