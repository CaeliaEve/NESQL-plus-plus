package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;

import com.google.gson.*;
import forestry.api.apiculture.*;
import forestry.api.genetics.*;
import java.lang.reflect.*;
import java.util.*;

/** Real Gendustry BeeMutation descriptions through the production root explanation reader. */
final class NativeGendustryConditionsTest {
  @SuppressWarnings("unchecked")
  static void run() throws Exception {
    try (NativeRegistryScope scope = new NativeRegistryScope()) {
      NativeCoreFixesTest.version("gendustry", "1.9.4-GTNH");
      NativeCoreFixesTest.version("bdlib", "1.11.0-GTNH");
      Object controller = new cpw.mods.fml.common.LoadController(cpw.mods.fml.common.Loader.instance());
      scope.replace(cpw.mods.fml.common.Loader.class, cpw.mods.fml.common.Loader.instance(), "modController", controller);
      cpw.mods.fml.common.ModMetadata metadata = new cpw.mods.fml.common.ModMetadata();
      metadata.modId = "gendustry"; metadata.version = "1.9.4-GTNH";
      set(controller, "activeContainer", new cpw.mods.fml.common.DummyModContainer(metadata));
      for (String module : new String[]{"net.bdew.lib.BdLib$", "net.bdew.gendustry.Gendustry$"}) {
        Object value = field(type(module), null, "MODULE$");
        scope.replace(value.getClass(), value, "log", org.apache.logging.log4j.LogManager.getLogger("conditions-fixture"));
      }
      try (GendustryNativeSource source = new GendustryNativeSource()) {
        GendustryGeneticsRecipes.module("items.GeneTemplate");
        set(controller, "activeContainer", null);
        Map<String, IAllele[]> templates = source.root.getGenomeTemplates();
        Class<?> producer = type("net.bdew.gendustry.custom.BeeMutation");
        IBeeMutation mutation = (IBeeMutation)producer.getConstructor(IAlleleBeeSpecies.class,
            IAlleleBeeSpecies.class, IAlleleBeeSpecies.class, float.class).newInstance(
                templates.get("fixture.gendustry.parentA")[0], templates.get("fixture.gendustry.parentB")[0],
                templates.get("fixture.gendustry.result")[0], 7F);
        List mutations = GendustryRootBindings.mutations(source.root); mutations.clear(); mutations.add(mutation);
        Object none = field(type("scala.None$"), null, "MODULE$");
        Collection<String> empty = mutation.getSpecialConditions();
        require(empty.getClass() == type("scala.collection.convert.Wrappers$IterableWrapper") && empty.isEmpty(),
            "Pinned BeeMutation did not produce its native empty JavaConversions wrapper");
        Class<?> some = type("scala.Some");
        set(mutation, "reqTemperature", some.getConstructor(Object.class).newInstance(forestry.api.core.EnumTemperature.HOT));
        set(mutation, "reqHumidity", some.getConstructor(Object.class).newInstance(forestry.api.core.EnumHumidity.ARID));
        List<String> expected = new ArrayList<>(mutation.getSpecialConditions());
        require(expected.size() == 2, "Native temperature/humidity explanation count differs");
        try (GendustryRootBindings bindings = new GendustryRootBindings();
             GendustrySpecimens specimens = new GendustrySpecimens(bindings)) {
          // The root reader only asks the setting value. Avoid initializing unrelated machine/fluid configuration.
          GendustryMutationSettings settings = scope.allocate(GendustryMutationSettings.class);
          set(settings, "settings", new TreeMap<String, Object>());
          set(settings, "defaultSetting", field(type("net.bdew.gendustry.api.EnumMutationSetting"), null, "REQUIREMENTS"));
          set(specimens, "mutationSettings", settings);
          assertLines(specimens, expected);
          set(mutation, "reqTemperature", none);
          assertLines(specimens, Collections.singletonList(expected.get(1)));
          set(mutation, "reqHumidity", none);
          assertLines(specimens, Collections.emptyList());
          boundaries(scope,mutation);
        }
      }
    }
    System.out.println("Native Gendustry conditions: exact producer, populated/empty Scala JavaConversions wrappers, line order and copied explanation facts passed");
  }

  private static void assertLines(GendustrySpecimens specimens, List<String> expected) {
    JsonArray notes = new JsonArray();
    GendustryGeneticsRecipes.roots(notes, specimens);
    require(notes.size() == 1, "Native Gendustry mutation explanation disappeared");
    JsonArray lines = notes.get(0).getAsJsonObject().getAsJsonArray("lines");
    require(lines.size() == expected.size(), "Native Gendustry condition count was flattened");
    for (int index = 0; index < expected.size(); index++)
      require(lines.get(index).getAsString().equals(expected.get(index)), "Native condition text/order differs");
  }
  private static void boundaries(NativeRegistryScope scope,IMutation mutation)throws Exception {
    Method read=GendustryGeneticsRecipes.class.getDeclaredMethod("descriptionValues",IMutation.class,Collection.class);
    read.setAccessible(true);
    Collection<?> wrapper=mutation.getSpecialConditions();
    Class<?> list=type("scala.collection.immutable.List"),node=type("scala.collection.immutable.$colon$colon");
    Object nil=field(type("scala.collection.immutable.Nil$"),null,"MODULE$");
    Constructor<?> cons=node.getConstructor(Object.class,list);
    org.objectweb.asm.ClassWriter writer=new org.objectweb.asm.ClassWriter(0);
    String name="com/github/dcysteine/nesql/fixture/ForeignConditionList";
    writer.visit(org.objectweb.asm.Opcodes.V1_8,org.objectweb.asm.Opcodes.ACC_PUBLIC|org.objectweb.asm.Opcodes.ACC_SUPER,
        name,null,"scala/collection/immutable/List",null);writer.visitEnd();byte[] bytes=writer.toByteArray();
    Class<?> foreignClass=new ClassLoader(NativeGendustryConditionsTest.class.getClassLoader()) {
      Class<?> define(){return defineClass(name.replace('/','.'),bytes,0,bytes.length);}
    }.define();
    Object foreignTail=scope.allocate(foreignClass);
    set(wrapper,"underlying",cons.newInstance("first",foreignTail));
    rejected(read,mutation,wrapper);
    Object cycle=cons.newInstance("cycle",nil);set(cycle,"tl",cycle);set(wrapper,"underlying",cycle);
    rejected(read,mutation,wrapper);
    set(wrapper,"underlying",cons.newInstance(new String(new char[16385]),nil));
    rejected(read,mutation,wrapper);
    Object longList=nil;
    for(int index=0;index<129;index++)longList=cons.newInstance("line",longList);
    set(wrapper,"underlying",longList);rejected(read,mutation,wrapper);
    ForeignCollection foreign=new ForeignCollection();rejected(read,mutation,foreign);
    require(foreign.calls==0,"Foreign collection callback executed before rejection");
    final int[] calls={0};
    IMutation unknown=(IMutation)Proxy.newProxyInstance(IMutation.class.getClassLoader(),new Class<?>[]{IMutation.class},
        (proxy,method,args)->{calls[0]++;throw new AssertionError("Foreign mutation callback executed");});
    rejected(read,unknown,wrapper);
    require(calls[0]==0,"Native wrapper admitted an unknown producer");
  }
  private static void rejected(Method read,IMutation mutation,Collection<?> descriptions)throws Exception {
    try {read.invoke(null,mutation,descriptions);throw new AssertionError("Unadapted condition collection was accepted");}
    catch(InvocationTargetException failure){
      Throwable cause=failure.getCause();
      require(cause instanceof com.github.dcysteine.nesql.exporter.task.Jobs.Fault
          &&((com.github.dcysteine.nesql.exporter.task.Jobs.Fault)cause).code.equals("recipe_unsupported"),
          "Foreign condition storage executed or failed outside its precise refusal");
    }
  }
  private static final class ForeignCollection extends AbstractCollection<String> {
    int calls;
    public int size(){calls++;throw new AssertionError("Foreign condition size executed");}
    public Iterator<String> iterator(){calls++;throw new AssertionError("Foreign condition iterator executed");}
  }
  private static void set(Object target, String name, Object value) throws Exception {
    for (Class<?> owner = target.getClass(); owner != null; owner = owner.getSuperclass()) {
      try { Field field = owner.getDeclaredField(name); field.setAccessible(true); field.set(target, value); return; }
      catch (NoSuchFieldException missing) { }
    }
    throw new NoSuchFieldException(name);
  }
  private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
