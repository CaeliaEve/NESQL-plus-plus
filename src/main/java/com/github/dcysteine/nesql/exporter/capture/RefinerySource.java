package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

import codechicken.nei.recipe.IRecipeHandler;
import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.common.collect.HashBiMap;
import com.google.gson.JsonArray;
import java.util.*;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;

/** Same-session Forge identities and native oil/fuel roles; no GUI or polymorphic fluid facts. */
final class RefinerySource implements AutoCloseable {
  private static final String CORE = "micdoodle8.mods.galacticraft.core.";
  private static final String[] MAPS = {"fluids", "fluidIDs", "fluidNames", "delegates"};
  final boolean old, hard;
  final List<Fluid> inputs = new ArrayList<>();
  Fluid oil, output;
  private Object[] roots;
  private List<Object> identities;
  private String signature;
  private final int[] counts = new int[3];
  private final IdentityHashMap<Fluid, Object> delegates = new IdentityHashMap<>();
  private boolean closed;

  RefinerySource() {
    roots = roots();
    for (int i = 0; i < counts.length; i++) counts[i] = ((Map<?, ?>) roots[i]).size();
    old = (Boolean) roots[6];
    hard = (Boolean) roots[7];
    oil = (Fluid) roots[4];
    output = (Fluid) roots[5];
    try {
      Observation initial = observe();
      identities = initial.identities;
      signature = initial.signature;
      for (Map.Entry<String, Fluid> entry : initial.fluids.entrySet())
        if (entry.getKey().startsWith("oil")) inputs.add(entry.getValue());
      for (Fluid fluid : inputs) delegates.put(fluid, null);
      delegates.put(output, null);
      verifyDelegates();
      verify();
    } catch (RuntimeException | Error failure) {
      close();
      throw failure;
    }
  }

  static Object[] registrationAnchors(IRecipeHandler handler) {
    Object[] nativeRoots = roots();
    Object[] result = new Object[nativeRoots.length + 1];
    result[0] = handler.getClass();
    System.arraycopy(nativeRoots, 0, result, 1, nativeRoots.length);
    return result;
  }

  private static Object[] roots() {
    Object[] r = new Object[8];
    for (int i = 0; i < MAPS.length; i++) {
      r[i] = field(FluidRegistry.class, null, MAPS[i]);
      if (r[i] == null)
        throw new Jobs.Fault("handler_not_ready", "Refinery fluid registry unavailable");
      if (r[i].getClass() != (i == 3 ? HashMap.class : HashBiMap.class))
        throw unsupported("registry implementation");
      if (((Map<?, ?>) r[i]).size() > 65536)
        throw new Jobs.Fault("source_budget", "Refinery registry exceeds budget");
    }
    r[4] = field(type(CORE + "GalacticraftCore"), null, "fluidOil");
    r[5] = field(type(CORE + "GalacticraftCore"), null, "fluidFuel");
    r[6] =
        Boolean.valueOf(
            (Boolean) field(type(CORE + "util.ConfigManagerCore"), null, "useOldFuelFluidID"));
    r[7] =
        Boolean.valueOf((Boolean) field(type(CORE + "util.ConfigManagerCore"), null, "hardMode"));
    if (r[4] == null || r[5] == null)
      throw new Jobs.Fault("handler_not_ready", "Refinery oil/fuel roles unavailable");
    if (!(r[4] instanceof Fluid) || !(r[5] instanceof Fluid)) throw unsupported("oil/fuel role");
    return r;
  }

  void verifyRoots() {
    check();
    Object[] actual;
    try {
      actual = roots();
    } catch (Jobs.Fault failure) {
      Jobs.Fault drift = changed();
      drift.initCause(failure);
      throw drift;
    }
    for (int i = 0; i < roots.length; i++) if (roots[i] != actual[i]) throw changed();
    for (int i = 0; i < counts.length; i++)
      if (((Map<?, ?>) roots[i]).size() != counts[i]) throw changed();
  }

  void verify() {
    verifyRoots();
    Observation actual;
    verifyDelegates();
    try {
      actual = observe();
    } catch (Jobs.Fault failure) {
      Jobs.Fault drift = changed();
      drift.initCause(failure);
      throw drift;
    }
    if (!signature.equals(actual.signature) || identities.size() != actual.identities.size())
      throw changed();
    for (int i = 0; i < identities.size(); i++)
      if (identities.get(i) != actual.identities.get(i)) throw changed();
  }

  private Observation observe() {
    Jobs.checkpoint();
    Map<String, Fluid> fluids = new TreeMap<>();
    long nameChars = 0;
    for (Map.Entry<?, ?> entry : ((Map<?, ?>) roots[0]).entrySet()) {
      if (!(entry.getKey() instanceof String) || !(entry.getValue() instanceof Fluid))
        throw unsupported("fluid table member");
      Fluid fluid = (Fluid) entry.getValue();
      audit(fluid);
      String name = (String) entry.getKey();
      if (!name.equals(fluid.getName()) || name.length() > 4096) throw unsupported("fluid name");
      fluids.put(name, fluid);
      if ((nameChars += name.length()) > 2 * 1024 * 1024)
        throw new Jobs.Fault("source_budget", "Refinery name projection exceeds budget");
    }
    IdentityHashMap<Fluid, Integer> ids = new IdentityHashMap<>();
    Map<Integer, Fluid> byId = new TreeMap<>();
    for (Map.Entry<?, ?> entry : ((Map<?, ?>) roots[1]).entrySet()) {
      if (!(entry.getKey() instanceof Fluid) || !(entry.getValue() instanceof Integer))
        throw unsupported("fluid ID member");
      Fluid fluid = (Fluid) entry.getKey();
      audit(fluid);
      if (fluid.getName().length() > 4096
          || (nameChars += fluid.getName().length()) > 2 * 1024 * 1024)
        throw new Jobs.Fault("source_budget", "Refinery fluid ID projection exceeds budget");
      Integer id = (Integer) entry.getValue();
      if (id <= 0 || byId.put(id, fluid) != null) throw unsupported("fluid ID");
      ids.put(fluid, id);
    }
    Map<Integer, String> names = new TreeMap<>();
    for (Map.Entry<?, ?> entry : ((Map<?, ?>) roots[2]).entrySet()) {
      if (!(entry.getKey() instanceof Integer) || !(entry.getValue() instanceof String))
        throw unsupported("ID name member");
      String name = (String) entry.getValue();
      if (name.length() > 4096 || (nameChars += name.length()) > 2 * 1024 * 1024)
        throw new Jobs.Fault("source_budget", "Refinery ID-name projection exceeds budget");
      names.put((Integer) entry.getKey(), name);
    }
    if (fluids.get(oil.getName()) != oil
        || !oil.getName().startsWith("oil")
        || fluids.get(old ? "fuelgc" : "fuel") != output)
      throw unsupported("configured oil/fuel lookup");
    List<Object> refs = new ArrayList<>();
    JsonArray raw = array(), rawIds = array(), rawNames = array();
    for (Map.Entry<String, Fluid> entry : fluids.entrySet()) {
      Jobs.checkpoint();
      Fluid f = entry.getValue();
      Integer id = ids.get(f);
      if (id == null || !entry.getKey().equals(names.get(id)))
        throw unsupported("inconsistent fluid lookup");
      refs.add(f);
      raw.add(
          object(
              "name",
              entry.getKey(),
              "id",
              id,
              "class",
              f.getClass().getName(),
              "density",
              f.getDensity(),
              "viscosity",
              f.getViscosity(),
              "temperature",
              f.getTemperature(),
              "luminosity",
              f.getLuminosity(),
              "gaseous",
              f.isGaseous()));
    }
    for (Map.Entry<Integer, Fluid> entry : byId.entrySet()) {
      refs.add(entry.getValue());
      rawIds.add(object("id", entry.getKey(), "name", entry.getValue().getName()));
    }
    for (Map.Entry<Integer, String> entry : names.entrySet())
      rawNames.add(object("id", entry.getKey(), "name", entry.getValue()));
    return new Observation(
        fluids,
        refs,
        CanonicalJson.digest(object("fluids", raw, "ids", rawIds, "names", rawNames)));
  }

  private void verifyDelegates() {
    Map<?, ?> table = (Map<?, ?>) roots[3];
    for (Map.Entry<Fluid, Object> entry : delegates.entrySet()) {
      Fluid fluid = entry.getKey();
      Object actual = table.get(fluid);
      if (entry.getValue() != null && entry.getValue() != actual) throw changed();
      if (actual != null) {
        if (!actual
                .getClass()
                .getName()
                .equals("net.minecraftforge.fluids.FluidRegistry$FluidDelegate")
            || field(actual, "fluid") != fluid
            || !fluid.getName().equals(field(actual, "name"))) throw changed();
        // Native FluidStack construction may lazily publish this equivalent delegate. No global
        // write here.
        entry.setValue(actual);
      }
    }
  }

  private static void audit(Fluid fluid) {
    try {
      if (fluid.getClass().getMethod("hashCode").getDeclaringClass() != Object.class
          || fluid.getClass().getMethod("equals", Object.class).getDeclaringClass() != Object.class)
        throw unsupported("fluid key callback");
    } catch (NoSuchMethodException failure) {
      throw unsupported("fluid key method");
    }
  }

  private void check() {
    Jobs.checkpoint();
    if (closed) throw new Jobs.Fault("capture_closed", "Refinery source closed");
  }

  public void close() {
    if (closed) return;
    closed = true;
    inputs.clear();
    delegates.clear();
    oil = null;
    output = null;
    roots = null;
    identities = null;
    signature = null;
  }

  private static Jobs.Fault unsupported(String detail) {
    return new Jobs.Fault("recipe_unsupported", "Unadapted refinery " + detail);
  }

  private static Jobs.Fault changed() {
    return new Jobs.Fault(
        "environment_changed", "Refinery roots, members, mappings, roles or configuration changed");
  }

  private static final class Observation {
    final Map<String, Fluid> fluids;
    final List<Object> identities;
    final String signature;

    Observation(Map<String, Fluid> fluids, List<Object> identities, String signature) {
      this.fluids = fluids;
      this.identities = identities;
      this.signature = signature;
    }
  }
}
