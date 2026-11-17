package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonObject;
import java.util.*;

/** The native canShow flag reads one existing config value; no default/config file loading. */
final class Ic2AdvancedVisibility implements AutoCloseable {
  private Object recipe;
  private final boolean hidden;
  private final List<Object> anchors = new ArrayList<>();
  private final String value;

  Ic2AdvancedVisibility(Object recipe) {
    this.recipe = recipe;
    hidden = (Boolean) field(recipe, "hidden");
    value = read(anchors);
  }

  boolean shown() {
    verify();
    return !hidden || !Boolean.parseBoolean(value);
  }

  JsonObject declaration() {
    verify();
    return object(
        "hidden",
        hidden,
        "hideSecretRecipes",
        hidden ? Boolean.valueOf(value) : null,
        "configValue",
        value);
  }

  void verify() {
    if (recipe == null) throw new Jobs.Fault("capture_closed", "IC2 visibility reader is closed");
    Jobs.checkpoint();
    if (hidden != (Boolean) field(recipe, "hidden")) throw changed();
    List<Object> current = new ArrayList<>();
    String actual = read(current);
    if (!Objects.equals(value, actual) || anchors.size() != current.size()) throw changed();
    for (int index = 0; index < anchors.size(); index++)
      if (anchors.get(index) != current.get(index)) throw changed();
  }

  private String read(List<Object> bindings) {
    if (!hidden) return null;
    Object config = field(type("ic2.core.init.MainConfig"), null, "config");
    if (config == null)
      throw new Jobs.Fault(
          "handler_not_ready", "IC2 secret-recipe visibility config is unavailable");
    Class<?> configType = type("ic2.core.util.Config");
    if (config.getClass() != configType) throw fault("Unadapted IC2 recipe config callback");
    bindings.add(config);
    Object sections = field(config, "sections");
    if (sections == null || sections.getClass() != LinkedHashMap.class)
      throw fault("Unadapted native IC2 section storage");
    bindings.add(sections);
    Object misc = ((Map<?, ?>) sections).get("misc");
    if (misc == null)
      throw new Jobs.Fault("handler_not_ready", "IC2 misc recipe config is unavailable");
    if (misc.getClass() != configType) throw fault("Unadapted IC2 recipe config section");
    bindings.add(misc);
    Object values = field(misc, "values");
    if (values == null || values.getClass() != LinkedHashMap.class)
      throw fault("Unadapted native IC2 config value storage");
    bindings.add(values);
    Object entry = ((Map<?, ?>) values).get("hideSecretRecipes");
    if (entry == null)
      throw new Jobs.Fault("handler_not_ready", "IC2 hideSecretRecipes value is unavailable");
    if (entry.getClass() != type("ic2.core.util.Config$Value"))
      throw fault("Unadapted native IC2 bool config value");
    bindings.add(entry);
    return (String) field(entry, "value");
  }

  public void close() {
    recipe = null;
    anchors.clear();
  }

  private static Jobs.Fault fault(String text) {
    return new Jobs.Fault("recipe_unsupported", text);
  }

  private static Jobs.Fault changed() {
    return new Jobs.Fault(
        "recipe_changed", "IC2 Advanced recipe visibility or native config changed");
  }
}
