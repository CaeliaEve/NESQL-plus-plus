package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;

import codechicken.nei.recipe.IRecipeHandler;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import java.util.*;

/** Exact same-process sources shared by crafting and usage registrations; never NEI pages. */
final class BloodRitualBindings {
  private static final String ROOT = "WayofTime.alchemicalWizardry.";
  final String family, registryClass, registryField;
  final Class<?> handlerClass;
  final List<?> recipes;
  private final Object[] roots;

  BloodRitualBindings(IRecipeHandler handler) {
    handlerClass = handler.getClass();
    String name = handlerClass.getName();
    if (name.equals(ROOT + "client.nei.NEIAltarRecipeHandler")) {
      family = "altar";
      registryClass = ROOT + "api.altarRecipeRegistry.AltarRecipeRegistry";
      registryField = "altarRecipes";
    } else if (name.equals(ROOT + "client.nei.NEIBindingRitualHandler")) {
      family = "binding";
      registryClass = ROOT + "api.bindingRegistry.BindingRegistry";
      registryField = "bindingRecipes";
    } else if (name.equals(ROOT + "client.nei.NEIMeteorRecipeHandler")) {
      family = "meteor";
      registryClass = ROOT + "common.summoning.meteor.MeteorRegistry";
      registryField = "meteorList";
    } else throw new Jobs.Fault("recipe_unsupported", "Unknown native Blood ritual " + name);
    recipes = current();
    roots = roots();
  }

  List<?> current() {
    Object source = field(type(registryClass), null, registryField);
    if (source == null)
      throw new Jobs.Fault("handler_not_ready", "Blood " + family + " registry is unavailable");
    if (source.getClass() != ArrayList.class && source.getClass() != LinkedList.class)
      throw new Jobs.Fault("recipe_unsupported", "Unadapted Blood ritual list storage");
    if (((List<?>) source).size() > 65536)
      throw new Jobs.Fault("recipe_limit", "Blood ritual registry exceeds budget");
    return (List<?>) source;
  }

  Object[] anchors() {
    return roots.clone();
  }

  void verify() {
    Object[] now = roots();
    if (now.length != roots.length) throw drift();
    for (int i = 0; i < now.length; i++) if (now[i] != roots[i]) throw drift();
  }

  private Object[] roots() {
    List<?> source = current();
    List<Object> result = new ArrayList<>(Arrays.asList(handlerClass, source));
    if (!source.isEmpty() && !family.equals("altar")) {
      Object map = field(type(ROOT + "api.rituals.Rituals"), null, "ritualMap");
      if (map == null)
        throw new Jobs.Fault("handler_not_ready", "Blood ritual definitions unavailable");
      if (map.getClass() != HashMap.class && map.getClass() != LinkedHashMap.class)
        throw new Jobs.Fault("recipe_unsupported", "Unadapted Blood ritual map storage");
      Object ritual =
          ((Map<?, ?>) map).get(family.equals("binding") ? "AW006Binding" : "AW019FallingTower");
      if (ritual == null)
        throw new Jobs.Fault("handler_not_ready", "Native Blood ritual is unavailable");
      Collections.addAll(result, map, ritual, field(ritual, "effect"));
      if (family.equals("binding"))
        result.add(field(type(ROOT + "AlchemicalWizardry"), null, "ritualCostBinding"));
      else {
        Object reagents =
            field(type(ROOT + "common.summoning.meteor.MeteorReagentRegistry"), null, "reagents");
        if (reagents == null)
          throw new Jobs.Fault("handler_not_ready", "Meteor reagents unavailable");
        if (reagents.getClass() != HashMap.class && reagents.getClass() != LinkedHashMap.class)
          throw new Jobs.Fault("recipe_unsupported", "Unadapted meteor reagent map storage");
        Collections.addAll(
            result,
            reagents,
            field(
                type(ROOT + "common.summoning.meteor.MeteorComponent"),
                null,
                "defaultMeteorBlock"));
      }
    }
    return result.toArray();
  }

  private Jobs.Fault drift() {
    return new Jobs.Fault("registry_drift", "Blood " + family + " source roots changed");
  }
}
