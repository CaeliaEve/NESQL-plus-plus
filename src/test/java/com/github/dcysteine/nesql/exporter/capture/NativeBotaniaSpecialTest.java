package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;

import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonObject;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;
import thaumcraft.api.aspects.Aspect;

/** Four target-runtime boundaries, isolated without launching a client or exporting a world. */
final class NativeBotaniaSpecialTest {
  interface Probe { void run() throws Exception; }

  static void run() throws Exception {
    List<Throwable> failures = new ArrayList<>();
    probe("mini flower NBT selection", NativeBotaniaPoolTest::special, failures);
    probe("runic named head", NativeBotaniaRunicTest::special, failures);
    probe("elven native allele storage", NativeBotaniaSpecialTest::alleles, failures);
    probe("arcane inherited aspect data", NativeBotaniaSpecialTest::aspects, failures);
    if (!failures.isEmpty()) {
      AssertionError failure = new AssertionError("Native Botania/arcane boundaries failed: "
          + failures.size());
      for (Throwable cause : failures) failure.addSuppressed(cause);
      throw failure;
    }
    System.out.println("Native Botania/arcane: special NBT selection, isolated head state, "
        + "native LinkedHashMap alleles and inherited aspect getters passed");
  }

  private static void probe(String label, Probe test, List<Throwable> failures) {
    try { test.run(); }
    catch (Throwable failure) {
      failures.add(new AssertionError(label, failure));
      System.err.println("Native boundary red: " + label + ": " + failure);
    }
  }

  static void active(NativeRegistryScope scope) throws Exception {
    NativeCoreFixesTest.version("Botania", "1.12.28-GTNH");
    cpw.mods.fml.common.Loader loader = cpw.mods.fml.common.Loader.instance();
    cpw.mods.fml.common.LoadController controller = new cpw.mods.fml.common.LoadController(loader);
    cpw.mods.fml.common.ModMetadata metadata = new cpw.mods.fml.common.ModMetadata();
    metadata.modId = "Botania";
    scope.replace(controller.getClass(), controller, "activeContainer",
        new cpw.mods.fml.common.DummyModContainer(metadata));
    scope.replace(loader.getClass(), loader, "modController", controller);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static void alleles() throws Exception {
    try (NativeRegistryScope scope = new NativeRegistryScope()) {
      Class<?> registryType = type("forestry.core.genetics.alleles.AlleleRegistry");
      Object registry = registryType.newInstance();
      Map<Object,Object> alleles = (Map<Object,Object>) field(registry, "alleleMap");
      require(alleles.getClass() == LinkedHashMap.class,
          "Pinned Forestry registry no longer owns a native LinkedHashMap");
      Object species = scope.allocate(type("forestry.apiculture.genetics.alleles.AlleleBeeSpecies"));
      Class<?> allele = type("forestry.core.genetics.alleles.Allele");
      scope.replace(allele, species, "uid", "nesql.elvenSpecies");
      scope.replace(allele, species, "isDominant", true);
      alleles.put("nesql.elvenSpecies", species);
      NativeCoreFixesTest.version("gendustry", "1.9.4-GTNH");
      Class<?> customType=type("net.bdew.gendustry.custom.BeeSpecies");
      Object custom=scope.allocate(customType);
      scope.replace(customType,custom,"getUID","gendustry.bee.nativeDominance");
      scope.replace(customType,custom,"isDominant",false);
      alleles.put("gendustry.bee.nativeDominance",custom);
      scope.replace(forestry.api.genetics.AlleleManager.class, null, "alleleRegistry", registry);
      Class<?> entryType = type(BotaniaWorldRecipes.class.getName() + "$Entry");
      Object entry = scope.allocate(entryType);
      scope.replace(entryType, entry, "species", "nesql.elvenSpecies");
      scope.replace(entryType, entry, "source", new SpeciesHolder(species));
      BotaniaWorldRecipes reader = scope.allocate(BotaniaWorldRecipes.class);
      scope.replace(BotaniaWorldRecipes.class, reader, "entries",
          new ArrayList(Collections.singletonList(entry)));
      Method dominance = BotaniaWorldRecipes.class.getDeclaredMethod("dominance");
      dominance.setAccessible(true);
      JsonObject result = (JsonObject) call(dominance, reader);
      require(result.get("nesql.elvenSpecies").getAsBoolean(),
          "Native elven species dominance disappeared");
      require(!result.get("gendustry.bee.nativeDominance").getAsBoolean(),
          "Gendustry native species dominance disappeared");
      scope.replace(customType,custom,"isDominant",true);
      require(((JsonObject)call(dominance,reader)).get("gendustry.bee.nativeDominance").getAsBoolean(),
          "Gendustry dominance field drift was not observed");
      ForeignAlleleMap foreign = new ForeignAlleleMap();
      scope.replace(registryType, registry, "alleleMap", foreign);
      try { call(dominance, reader); throw new AssertionError("Foreign allele storage was accepted"); }
      catch (Jobs.Fault expected) {
        require(expected.code.equals("recipe_unsupported") && foreign.calls == 0,
            "Elven storage audit called an unknown map callback");
      }
    }
  }

  private static void aspects() throws Exception {
    try (NativeRegistryScope scope = new NativeRegistryScope()) {
      scope.replace(Aspect.class, null, "aspects", new LinkedHashMap<>(Aspect.aspects));
      Aspect inherited = (Aspect) scope.allocate(
          type("gregtech.common.GTThaumcraftCompat$LocalizedCustomAspectName"));
      scope.replace(Aspect.class, inherited, "tag", "nesql_inherited_arcane");
      Aspect.aspects.put("nesql_inherited_arcane", inherited);
      scope.replace(type("witchinggadgets.common.WGContent"), null, "ItemMaterial", Items.paper);
      OreDictionary.registerOre("dyeBlack", new ItemStack(Items.dye));
      Class<?> sourceType = type(NativeArcaneRecipes.PHOTO);
      Object source = scope.allocate(sourceType);
      scope.replace(sourceType.getSuperclass(), source, "research", "SCANCAMERA");
      try (NativeArcaneRecipes reader = new NativeArcaneRecipes(source)) {
        require(reader.branches().size() == 4 && inherited.getTag().equals("nesql_inherited_arcane"),
            "Inherited native aspect data did not preserve photo-developing branches");
        reader.finishVerification();
        scope.replace(Aspect.class, inherited, "tag", "nesql_changed_arcane");
        try { reader.finishVerification();
          throw new AssertionError("Inherited native aspect tag drift was ignored"); }
        catch (Jobs.Fault expected) {
          require(expected.code.equals("recipe_changed"), "Wrong native aspect drift fault");
        }
        scope.replace(Aspect.class, inherited, "tag", "nesql_inherited_arcane");
      }
      ForeignAspect foreign = new ForeignAspect("nesql_foreign_arcane");
      try { new NativeArcaneRecipes(source).close();
        throw new AssertionError("Arcane accepted an unknown aspect getter override"); }
      catch (Jobs.Fault expected) {
        require(expected.code.equals("recipe_unsupported") && foreign.calls == 0,
            "Arcane audited aspect data after executing the foreign getter");
      }
    }
  }

  static class DataAspect extends Aspect {
    DataAspect(String tag) { super(tag, 0x123456, new Aspect[] {Aspect.AIR, Aspect.EARTH}); }
  }
  static final class ForeignAspect extends DataAspect {
    int calls;
    ForeignAspect(String tag) { super(tag); }
    @Override public String getTag() { calls++; throw new AssertionError("Foreign getTag executed"); }
  }
  static final class ForeignAlleleMap extends LinkedHashMap<Object,Object> {
    int calls;
    @Override public int size() { calls++; throw new AssertionError("Foreign map size executed"); }
  }
  static final class SpeciesHolder {
    final Object inputSpecies;
    SpeciesHolder(Object species) { inputSpecies = species; }
  }
  private static Object call(Method method, Object target) throws Exception {
    try { return method.invoke(target); }
    catch (InvocationTargetException failure) {
      Throwable cause = failure.getCause();
      if (cause instanceof Error) throw (Error) cause;
      throw (Exception) cause;
    }
  }
  private static void require(boolean value, String message) {
    if (!value) throw new AssertionError(message);
  }
}
