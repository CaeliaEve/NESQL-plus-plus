package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.source.Json.*;

import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.source.*;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.*;
import forestry.api.genetics.*;
import forestry.api.lepidopterology.ButterflyManager;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.init.Items;
import net.minecraft.inventory.*;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import net.minecraftforge.fluids.*;

/** Real pinned roots, gene serializers, registries, validators, timing and finish callbacks. */
public final class NativeBinnieGeneticsTest {
  public static int nativeSpecimenBuilds;
  private static final String MACHINE = "binnie.genetics.machine.",
      NEI = "binnie.genetics.nei.",
      ITEM = "binnie.genetics.item.";

  public static void run() throws Exception {
    try {
      Class.forName("com.github.dcysteine.nesql.exporter.capture.BinnieGeneticsRecipes");
    } catch (ClassNotFoundException missing) {
      throw new AssertionError("Native Binnie genetics transformations have no adapter", missing);
    }
    java.io.File oldLaunch = net.minecraft.launchwrapper.Launch.minecraftHome;
    Object oldFml =
        MagicApi.field(cpw.mods.fml.relauncher.FMLInjectionData.class, null, "minecraftHome");
    java.io.File temporary = Files.createTempDirectory("binnie-genetics-native-").toFile();
    net.minecraft.launchwrapper.Launch.minecraftHome = temporary;
    set(cpw.mods.fml.relauncher.FMLInjectionData.class, null, "minecraftHome", temporary);
    try {
      ((net.minecraft.launchwrapper.LaunchClassLoader)
              NativeBinnieGeneticsTest.class.getClassLoader())
          .registerTransformer(NativeGeneticsTest.class.getName() + "$Defaults");
      ((net.minecraft.launchwrapper.LaunchClassLoader)
              NativeBinnieGeneticsTest.class.getClassLoader())
          .registerTransformer(NativeBinnieGeneticsTest.class.getName() + "$TemplateWork");
      NativeGeneticsTest.run();
      test();
    } finally {
      net.minecraft.launchwrapper.Launch.minecraftHome = oldLaunch;
      set(cpw.mods.fml.relauncher.FMLInjectionData.class, null, "minecraftHome", oldFml);
    }
  }

  private static void test() throws Exception {
    NativeCoreFixesTest.version("BinnieCore", "2.5.24");
    NativeCoreFixesTest.version("Genetics", "2.5.24");
    NativeCoreFixesTest.version("Forestry", "4.10.17");
    Class<?> binnie = Class.forName("binnie.Binnie"),
        genetics = Class.forName("binnie.genetics.Genetics");
    Object manager = MagicApi.field(binnie, null, "Genetics");
    ISpeciesRoot butterfly = ButterflyManager.butterflyRoot,
        flower =
            (ISpeciesRoot)
                MagicApi.field(Class.forName("binnie.botany.core.BotanyCore"), null, "speciesRoot");
    IAlleleRegistry original = AlleleManager.alleleRegistry;
    Map<String, IAllele> additions = new HashMap<>();
    List<ISpeciesRoot> roots = Arrays.asList(butterfly, flower);
    AlleleManager.alleleRegistry =
        (IAlleleRegistry)
            Proxy.newProxyInstance(
                IAlleleRegistry.class.getClassLoader(),
                new Class<?>[] {IAlleleRegistry.class},
                (p, m, a) -> {
                  if (m.getName().equals("getIndividual")) {
                    for (ISpeciesRoot root : roots)
                      if (root.isMember((ItemStack) a[0])) return root.getMember((ItemStack) a[0]);
                    return null;
                  }
                  if (m.getName().equals("getSpeciesRoot")) {
                    if (a == null || a.length == 0) {
                      Map<String, ISpeciesRoot> result = new LinkedHashMap<>();
                      for (ISpeciesRoot root : roots) result.put(root.getUID(), root);
                      return result;
                    }
                    if (a[0] instanceof ItemStack) {
                      for (ISpeciesRoot root : roots)
                        if (root.isMember((ItemStack) a[0])) return root;
                      return null;
                    }
                    if (a[0] instanceof String) {
                      for (ISpeciesRoot root : roots) if (root.getUID().equals(a[0])) return root;
                      return null;
                    }
                  }
                  if (m.getName().equals("getAllele") && additions.containsKey(a[0]))
                    return additions.get(a[0]);
                  try {
                    return m.invoke(original, a);
                  } catch (InvocationTargetException failure) {
                    throw failure.getCause();
                  }
                });
    Map<ISpeciesRoot, Map<IChromosomeType, List<IAllele>>> chromosomeMap =
        (Map) MagicApi.field(manager, "chromosomeArray");
    Map<ISpeciesRoot, Object> systems = (Map) MagicApi.field(manager, "breedingSystems");
    systems.clear();
    chromosomeMap.clear();
    for (ISpeciesRoot root : roots) {
      Object system =
          allocate(
              Class.forName(
                  "binnie.core.genetics."
                      + (root == butterfly ? "MothBreedingSystem" : "FlowerBreedingSystem")));
      systems.put(root, system);
      Map<IChromosomeType, List<IAllele>> map = new LinkedHashMap<>();
      IAllele[] template = root.getGenomeTemplates().values().iterator().next();
      for (IChromosomeType chromosome : root.getKaryotype())
        map.put(chromosome, new ArrayList<>(Arrays.asList(template[chromosome.ordinal()])));
      IChromosomeType chromosome = root.getKaryotype()[1];
      IAllele old = template[1];
      IAllele extra =
          (IAllele)
              Proxy.newProxyInstance(
                  old.getClass().getClassLoader(),
                  old.getClass().getInterfaces(),
                  (p, m, a) -> {
                    if (m.getName().equals("getUID"))
                      return "fixture.binnie.nondefault." + root.getUID();
                    if (m.getName().equals("equals")) return p == a[0];
                    if (m.getName().equals("hashCode")) return System.identityHashCode(p);
                    try {
                      return m.invoke(old, a);
                    } catch (InvocationTargetException e) {
                      throw e.getCause();
                    }
                  });
      additions.put(extra.getUID(), extra);
      map.get(chromosome).add(extra);
      chromosomeMap.put(root, map);
    }
    Item misc = NativeCoreFixesTest.item("binnie.core.item.ItemMisc", "binnie_genetics_misc");
    Object[] miscellaneous =
        (Object[]) Class.forName(ITEM + "GeneticsItems").getMethod("values").invoke(null);
    set(misc.getClass(), misc, "items", miscellaneous);
    genetics.getField("itemGenetics").set(null, misc);
    for (String[] entry :
        new String[][] {
          {"itemSerum", "ItemSerum"},
          {"itemSerumArray", "ItemSerumArray"},
          {"itemSequencer", "ItemSequence"}
        }) {
      Item item = (Item) Class.forName(ITEM + entry[1]).newInstance();
      Item.itemRegistry.addObject(
          5300 + Arrays.asList("itemSerum", "itemSerumArray", "itemSequencer").indexOf(entry[0]),
          "fixture:" + entry[0],
          item);
      genetics.getField(entry[0]).set(null, item);
    }
    for (Object liquid :
        (Object[]) Class.forName(ITEM + "GeneticLiquid").getMethod("values").invoke(null)) {
      String id = (String) liquid.getClass().getMethod("getIdentifier").invoke(liquid);
      if (FluidRegistry.getFluid(id.toLowerCase(Locale.ROOT)) == null)
        FluidRegistry.registerFluid(new Fluid(id.toLowerCase(Locale.ROOT)));
    }
    if (FluidRegistry.getFluid("bioethanol") == null)
      FluidRegistry.registerFluid(new Fluid("bioethanol"));
    Map<ItemStack, Float> temperature =
        (Map)
            MagicApi.field(
                Class.forName(MACHINE + "acclimatiser.Acclimatiser"), null, "temperatureItems");
    Map<ItemStack, Float> humidity =
        (Map)
            MagicApi.field(
                Class.forName(MACHINE + "acclimatiser.Acclimatiser"), null, "humidityItems");
    temperature.clear();
    humidity.clear();
    temperature.put(new ItemStack(Items.blaze_powder), 0.37f);
    temperature.put(new ItemStack(Items.snowball), -0.82f);
    humidity.put(new ItemStack(Items.reeds), 0.19f);
    List<Object> tolerances =
        (List)
            MagicApi.field(
                Class.forName(MACHINE + "acclimatiser.Acclimatiser"), null, "toleranceSystems");
    tolerances.clear();
    Class<?> toleranceType = Class.forName(MACHINE + "acclimatiser.ToleranceType");
    Object temperatureType = toleranceType.getField("Temperature").get(null);
    IChromosomeType tolerance =
        Arrays.stream(butterfly.getKaryotype())
            .filter(c -> c.getAlleleClass() == IAlleleTolerance.class)
            .findFirst()
            .get();
    Class.forName(MACHINE + "acclimatiser.Acclimatiser")
        .getMethod("addTolerance", String.class, IChromosomeType.class, toleranceType)
        .invoke(null, butterfly.getUID(), tolerance, temperatureType);
    List<Object> incubators =
        (List) MagicApi.field(Class.forName(MACHINE + "incubator.Incubator"), null, "RECIPES");
    incubators.clear();
    Class<?> recipeType = Class.forName(MACHINE + "incubator.IncubatorRecipe");
    FluidStack medium = fluid("GrowthMedium", 31), bacteria = fluid("BacteriaPoly", 43);
    medium.tag = new NBTTagCompound();
    medium.tag.setString("culture", "nondefault");
    Constructor<?> recipe =
        recipeType.getConstructor(
            ItemStack.class, FluidStack.class, FluidStack.class, float.class, float.class);
    Object first =
        recipe.newInstance(new ItemStack(Items.wheat, 3), medium, bacteria, 0.31f, 0.17f);
    recipeType
        .getMethod("setOutputStack", ItemStack.class)
        .invoke(first, new ItemStack(Items.paper, 7));
    incubators.add(first);
    Object second =
        recipe.newInstance(
            new ItemStack(Items.wheat, 1), medium.copy(), bacteria.copy(), 0.91f, 0.02f);
    incubators.add(second);
    Class.forName("binnie.extrabees.config.EBConfigMachines")
        .getField("isolatorConsumptionChance")
        .setFloat(null, 0.23f);
    IAllele[] template = butterfly.getGenomeTemplates().values().iterator().next();
    IChromosomeType chromosome = butterfly.getKaryotype()[1];
    IAllele nondefault = chromosomeMap.get(butterfly).get(chromosome).get(1);
    Object gene = gene(nondefault, chromosome, butterfly);
    ItemStack conversionInput = new ItemStack(Items.apple, 3, 0);
    conversionInput.setTagInfo("nativeIgnoredInput", new NBTTagString("nondefault"));
    IAllele[] flowerTemplate = flower.getGenomeTemplates().values().iterator().next().clone();
    flowerTemplate[1] = chromosomeMap.get(flower).get(flower.getKaryotype()[1]).get(1);
    flower
        .getClass()
        .getMethod("addConversion", ItemStack.class, IAllele[].class)
        .invoke(flower, conversionInput, flowerTemplate);
    ItemStack converted =
        (ItemStack)
            Class.forName(MACHINE + "analyser.Analyser")
                .getMethod("analyse", ItemStack.class)
                .invoke(null, conversionInput.copy());
    require(
        flower.isMember(converted) && flower.getMember(converted).isAnalyzed(),
        "Native Flower conversion was not analysed");
    require(
        flower
            .getMember(converted)
            .getGenome()
            .getActiveAllele(flower.getKaryotype()[1])
            .getUID()
            .equals(flowerTemplate[1].getUID()),
        "Native conversion discarded nondefault registered genome");
    ItemStack specimen =
        butterfly.getMemberStack(butterfly.templateAsIndividual(template.clone()), 2);
    specimen.setTagInfo("fixtureUnrelated", new NBTTagString("kept-or-rewritten-natively"));
    geneCallbacks(specimen, gene, butterfly, chromosome, nondefault);
    processors(specimen, gene, butterfly);
    nativeIsolationAndIncubation(specimen, butterfly, first, medium);
    Map<String, RegistryRecipes> captures = new LinkedHashMap<>();
    for (String name :
        new String[] {
          "Acclimatiser",
          "Sequencer",
          "Analyser",
          "Splicer",
          "Isolator",
          "Inoculator",
          "Genepool",
          "Incubator",
          "Polymeriser"
        }) {
      TemplateRecipeHandler handler =
          (TemplateRecipeHandler) Class.forName(NEI + name + "RecipeHandler").newInstance();
      TemplateRecipeHandler.CachedRecipe borrowed =
          handler.new CachedRecipe() {
            public codechicken.nei.PositionedStack getResult() {
              throw new AssertionError("Binnie semantic capture requested borrowed GUI");
            }
          };
      handler.arecipes.add(borrowed);
      require(Recipes.adapter(handler) != null, name + " has no production native routing");
      RegistryRecipes cursor = new BinnieGeneticsRecipes(handler);
      captures.put(name, cursor);
      require(cursor.size() > 0, name + " has no native domain rows");
      RecipeRow row = knownRow(cursor, handler);
      require(cursor.capture(0, row), name + " failed native capture");
      require(
          row.properties.entrySet().isEmpty(),
          name + " typed rules still inject fixed-language duplicate prose");
      require(
          handler.arecipes.size() == 1 && handler.arecipes.get(0) == borrowed,
          name + " semantic capture changed borrowed GUI cache");
      JsonObject process = row.record.getAsJsonObject("process");
      require(
          process.get("kind").getAsString().equals("binnie_genetics")
              && process.get("machine").getAsString().equals(name.toLowerCase(Locale.ROOT)),
          name + " lost process identity");
      for (JsonElement out : row.outputs)
        if (!name.equals("Sequencer") && !name.equals("Incubator"))
          require(
              out.getAsJsonObject().get("amount").isJsonNull(),
              name + " fabricated fixed genome output");
      require(
          row.record.get("duration").isJsonNull() && row.record.get("energy").isJsonNull(),
          name + " flattened state/charges/RF into fixed timing");
      List<ItemStack> specimens = specimens(cursor);
      String witness = "binnie-genetics-" + name.toLowerCase(Locale.ROOT);
      row.record.add(
          "source",
          object("owner", "Genetics", "handler", handler.getClass().getName(), "key", witness));
      row.record.addProperty(
          "category", Identity.origin("category", row.record.getAsJsonObject("source")));
      NativeContractFixture.writeOwnedWithDurability(
          witness + "-semantic",
          row,
          specimens,
          fluid("RawDNA", 1),
          fluid("BacteriaPoly", 1),
          fluid("BacteriaVector", 1),
          new FluidStack(FluidRegistry.getFluid("bioethanol"), 1),
          medium,
          bacteria);
      ItemStack displayed = specimens.get(0);
      displayed.stackSize = 63;
      displayed.setTagInfo("viewMutation", new NBTTagInt(7));
      RecipeRow repeat = knownRow(cursor, handler);
      require(cursor.capture(0, repeat), name + " lost owned repeat capture");
      require(
          repeat.inputs.equals(row.inputs) && repeat.outputs.equals(row.outputs),
          name + " source was changed by owned specimen mutation");
      cursor.verify();
      RegistrationBindings bindings = RegistrationBindings.read(handler);
      for (int registration = 0; registration < 4; registration++)
        require(
            bindings.same(
                RegistrationBindings.read(
                    (TemplateRecipeHandler) handler.getClass().newInstance())),
            name + " four registration sources differ");
      try (Recipes.Cursor production =
          new Recipes.Handler(handler, 0).open(knownRow(cursor, handler).facts)) {
        require(
            MagicApi.field(production, "handler") == null,
            name + " production retains GUI handler");
        production.capture(0);
        production.finishVerification();
      }
    }
    benchmarkVerification(captures.get("Analyser"));
    // A batch guard must not build specimens; sealing must still execute the native derivation.
    nativeSpecimenBuilds = 0;
    for (int batch = 0; batch < 3; batch++) captures.get("Analyser").verify();
    require(
        nativeSpecimenBuilds == 0,
        "Batch verification rebuilt " + nativeSpecimenBuilds + " native genetics specimens");
    captures.get("Analyser").finishVerification();
    require(nativeSpecimenBuilds > 0, "Sealing omitted the full native genetics derivation");

    Map<String, IAllele[]> nativeTemplates = butterfly.getGenomeTemplates();
    Map.Entry<String, IAllele[]> nativeTemplate = nativeTemplates.entrySet().iterator().next();
    IAllele[] originalTemplate = nativeTemplate.getValue();
    nativeTemplate.setValue(originalTemplate.clone());
    try {
      captures.get("Analyser").finishVerification();
      throw new AssertionError("Same-content native template replacement passed sealing");
    } catch (Jobs.Fault expected) {
      require(expected.code.equals("environment_changed"), "Wrong native template drift fault");
    } finally {
      nativeTemplate.setValue(originalTemplate);
    }
    // Native gene comparisons use allele object identity: an equal-UID replacement is drift.
    List<IAllele> boundAlleles = chromosomeMap.get(butterfly).get(butterfly.getKaryotype()[1]);
    IAllele boundAllele = boundAlleles.get(1);
    IAllele replacement =
        (IAllele)
            Proxy.newProxyInstance(
                boundAllele.getClass().getClassLoader(),
                boundAllele.getClass().getInterfaces(),
                (p, m, a) -> {
                  if (m.getName().equals("equals")) return p == a[0];
                  if (m.getName().equals("hashCode")) return System.identityHashCode(p);
                  try {
                    return m.invoke(boundAllele, a);
                  } catch (InvocationTargetException failure) {
                    throw failure.getCause();
                  }
                });
    boundAlleles.set(1, replacement);
    try {
      captures.get("Splicer").finishVerification();
      throw new AssertionError("Same-UID native allele replacement passed sealing");
    } catch (Jobs.Fault expected) {
      require(
          expected.code.equals("environment_changed"), "Wrong native allele identity drift fault");
    } finally {
      boundAlleles.set(1, boundAllele);
    }
    RegistryRecipes analyser = captures.get("Analyser");
    boolean foundConversion = false;
    for (Object entry : (List<?>) MagicApi.field(analyser, "entries"))
      for (Object in : (List<?>) MagicApi.field(entry, "inputs")) {
        ItemStack value = (ItemStack) MagicApi.field(in, "stack");
        if (value.isItemEqual(conversionInput)) foundConversion = true;
      }
    require(foundConversion, "Analyser omitted native flower conversion selectors");
    conversionInput.setTagInfo("nativeIgnoredInput", new NBTTagString("changed"));
    expectDrift(analyser);
    conversionInput.setTagInfo("nativeIgnoredInput", new NBTTagString("nondefault"));
    RegistryRecipes incubator = captures.get("Incubator");
    require(
        incubator.size() == 2
            && !incubator.capture(
                1,
                knownRow(
                    incubator,
                    (TemplateRecipeHandler)
                        Class.forName(NEI + "IncubatorRecipeHandler").newInstance()))
            && incubator.exclusion(1) != null,
        "Incubator first-match priority was erased");
    medium.tag.setString("culture", "changed");
    expectDrift(incubator);
    medium.tag.setString("culture", "nondefault");
    Class.forName("binnie.extrabees.config.EBConfigMachines")
        .getField("isolatorConsumptionChance")
        .setFloat(null, 0.77f);
    expectDrift(captures.get("Isolator"));
    Class.forName("binnie.extrabees.config.EBConfigMachines")
        .getField("isolatorConsumptionChance")
        .setFloat(null, 0.23f);
    temperature.put(new ItemStack(Items.blaze_powder), 0.42f);
    expectDrift(captures.get("Acclimatiser"));
    // Restore exact map order/content before rebuilding the read-only reference.
    temperature.clear();
    temperature.put(new ItemStack(Items.blaze_powder), 0.37f);
    temperature.put(new ItemStack(Items.snowball), -0.82f);
    TemplateRecipeHandler database =
        (TemplateRecipeHandler) Class.forName(NEI + "DatabaseRecipeHandler").newInstance();
    BinnieGeneticsRecipes reference = new BinnieGeneticsRecipes(database);
    require(
        !BinnieGeneticsRecipes.supports(database)
            && BinnieGeneticsRecipes.supportsReference(database),
        "Database was counted as a consuming machine");
    List<ItemStack> referenceSpecimens = new ArrayList<>();
    Facts facts = new Facts("en_US");
    for (ISpeciesRoot root : roots)
      for (Map.Entry<IChromosomeType, List<IAllele>> entry : chromosomeMap.get(root).entrySet())
        for (IAllele allele : entry.getValue()) {
          Object g = gene(allele, entry.getKey(), root);
          for (ItemStack value : new ItemStack[] {serum(g, false), serum(g, true), sequence(g)}) {
            restore(facts, value);
            referenceSpecimens.add(value);
          }
        }
    for (String symbol : new String[] {"EmptySerum", "EmptyGenome"}) {
      ItemStack value = misc(symbol);
      restore(facts, value);
      referenceSpecimens.add(value);
    }
    JsonObject domain = reference.reference(facts);
    require(
        reference.size() == 0
            && !domain.get("consuming").getAsBoolean()
            && domain.getAsJsonArray("catalog").size() == 2
            && domain.getAsJsonArray("containers").size() == 2,
        "Database became a consuming recipe or lost native genes/containers");
    Files.write(
        Paths.get("build/native-tests/binnie-genetics-reference.json"),
        CanonicalJson.bytes(domain));
    NativeContractFixture.writeReference("binnie-genetics-reference", domain, referenceSpecimens);
    List<JsonObject> referenceRows =
        com.github.dcysteine.nesql.exporter.source.GeneticsReferences.rows(domain);
    JsonArray normalized = array();
    for (JsonObject row : referenceRows) {
      require(
          CanonicalJson.bytes(row).length <= 1024 * 1024,
          "Native reference leaf exceeded source row limit");
      normalized.add(row);
    }
    NativeContractFixture.writeReference(
        "binnie-genetics-reference-rows", object("records", normalized), referenceSpecimens);
    require(Recipes.adapter(database) != null, "Native Database has no production reference route");
    NativeGeneticsReferences referenceCursor = new NativeGeneticsReferences(database);
    require(
        referenceCursor.size() == referenceRows.size()
            && referenceCursor.counts().get("genes").getAsInt() > 0,
        "Database diagnostic count does not reflect native gene records");
    Facts referenceFacts = new Facts("en_US");
    for (ItemStack value : referenceSpecimens) restore(referenceFacts, value);
    JsonArray capturedReferences = array();
    for (int index = 0; index < referenceCursor.size(); index++) {
      referenceCursor.verify();
      referenceCursor.capture(index, referenceFacts);
      for (Facts.Record row : referenceFacts.drain().records)
        if (row.kind.equals("references")) capturedReferences.add(row.value);
    }
    require(
        capturedReferences.equals(normalized),
        "Production native reference cursor diverges from bounded source contract");
    nativeSpecimenBuilds = 0;
    referenceCursor.verify();
    require(nativeSpecimenBuilds == 0, "Database batch guard regenerated native specimens");
    referenceCursor.finishVerification();
    require(nativeSpecimenBuilds > 0, "Database sealing skipped full native derivation");
    referenceCursor.close();
    referenceCursor.close();
    try {
      referenceCursor.size();
      throw new AssertionError("Closed native Database remained readable");
    } catch (Jobs.Fault expected) {
      require(expected.code.equals("capture_closed"), "Wrong closed Database fault");
    }
    require(
        domain.toString().contains("fixture.binnie.nondefault."),
        "Reference discarded nondefault registered genes");
    reference.close();
    reference.close();
    Object foreignManager = unknownManager(manager);
    try (NativeRegistryScope scope = new NativeRegistryScope()) {
      scope.replace(binnie, null, "Genetics", foreignManager);
      try {
        new BinnieGeneticsRecipes(
            (TemplateRecipeHandler) Class.forName(NEI + "AnalyserRecipeHandler").newInstance());
        throw new AssertionError("Unknown genetics manager callback was accepted");
      } catch (Jobs.Fault expected) {
        require(
            expected.code.equals("recipe_unsupported"), "Wrong foreign genetics callback fault");
      }
      require(
          foreignManager.getClass().getField("calls").getInt(null) == 0,
          "Unknown genetics manager callback executed before rejection");
    }
    try (NativeRegistryScope scope = new NativeRegistryScope()) {
      scope.replace(manager.getClass(), manager, "breedingSystems", new LinkedHashMap<>());
      scope.replace(manager.getClass(), manager, "chromosomeArray", new LinkedHashMap<>());
      TemplateRecipeHandler emptyHandler =
          (TemplateRecipeHandler) Class.forName(NEI + "AnalyserRecipeHandler").newInstance();
      try (RegistryRecipes empty = new BinnieGeneticsRecipes(emptyHandler)) {
        require(empty.size() == 0, "Ready empty native gene domain invented recipes");
        empty.finishVerification();
      }
      scope.replace(binnie, null, "Genetics", null);
      try {
        new BinnieGeneticsRecipes(emptyHandler);
        throw new AssertionError("Unavailable native gene manager became an empty success");
      } catch (Jobs.Fault expected) {
        require(expected.code.equals("handler_not_ready"), "Wrong unavailable gene manager fault");
      }
    }
    for (RegistryRecipes cursor : captures.values()) {
      cursor.close();
      cursor.close();
      try {
        cursor.size();
        throw new AssertionError("Closed Binnie reader remained readable");
      } catch (Jobs.Fault expected) {
        require(expected.code.equals("capture_closed"), "Wrong closed Binnie fault");
      }
      for (Runnable operation : new Runnable[] {cursor::verify, cursor::finishVerification}) {
        try {
          operation.run();
          throw new AssertionError("Closed Binnie verification remained usable");
        } catch (Jobs.Fault expected) {
          require(expected.code.equals("capture_closed"), "Wrong closed verification fault");
        }
      }
    }
    System.out.println(
        "Native Binnie genetics: all nine machine domains, native gene/NBT transforms, serum gates, nondefault alleles, sequencing resume/owner, timing/catalyst, incubator priority/NBT/config drift, GUI-free owned rows, four registrations, lifecycle and Database reference passed");
  }

  private static void benchmarkVerification(RegistryRecipes reader) throws Exception {
    String target = System.getenv("NESQL_BINNIE_VERIFY_BENCHMARK");
    if (target == null) return;
    JsonObject result =
        object("scope", "owned two-root native fixture, warm JVM; not game export throughput");
    for (boolean sealing : new boolean[] {false, true}) {
      long[] nanos = new long[7];
      nativeSpecimenBuilds = 0;
      for (int sample = 0; sample < nanos.length; sample++) {
        long started = System.nanoTime();
        if (sealing) reader.finishVerification();
        else reader.verify();
        nanos[sample] = System.nanoTime() - started;
      }
      JsonArray samples = array();
      for (long sample : nanos) samples.add(new JsonPrimitive(sample));
      Arrays.sort(nanos);
      result.add(
          sealing ? "seal" : "batch",
          object(
              "samplesNs",
              samples,
              "p50Ns",
              new JsonPrimitive(nanos[3]),
              "p95Ns",
              new JsonPrimitive(nanos[6]),
              "nativeSpecimenBuilds",
              nativeSpecimenBuilds));
    }
    Files.write(Paths.get(target), CanonicalJson.bytes(result));
  }

  /** Count real template work, leaving pinned root implementations and their outputs untouched. */
  public static final class TemplateWork implements net.minecraft.launchwrapper.IClassTransformer {
    @Override
    public byte[] transform(String name, String transformed, byte[] bytes) {
      if (!name.equals("forestry.lepidopterology.genetics.ButterflyHelper")
          && !name.equals("binnie.botany.genetics.FlowerHelper")) return bytes;
      org.objectweb.asm.tree.ClassNode node = new org.objectweb.asm.tree.ClassNode();
      new org.objectweb.asm.ClassReader(bytes).accept(node, 0);
      String owner = NativeBinnieGeneticsTest.class.getName().replace('.', '/');
      for (Object raw : node.methods) {
        org.objectweb.asm.tree.MethodNode method = (org.objectweb.asm.tree.MethodNode) raw;
        if (!method.name.equals("templateAsIndividual")
            || (method.access & org.objectweb.asm.Opcodes.ACC_BRIDGE) != 0) continue;
        org.objectweb.asm.tree.InsnList count = new org.objectweb.asm.tree.InsnList();
        count.add(
            new org.objectweb.asm.tree.FieldInsnNode(
                org.objectweb.asm.Opcodes.GETSTATIC, owner, "nativeSpecimenBuilds", "I"));
        count.add(new org.objectweb.asm.tree.InsnNode(org.objectweb.asm.Opcodes.ICONST_1));
        count.add(new org.objectweb.asm.tree.InsnNode(org.objectweb.asm.Opcodes.IADD));
        count.add(
            new org.objectweb.asm.tree.FieldInsnNode(
                org.objectweb.asm.Opcodes.PUTSTATIC, owner, "nativeSpecimenBuilds", "I"));
        method.instructions.insert(count);
      }
      org.objectweb.asm.ClassWriter writer =
          new org.objectweb.asm.ClassWriter(org.objectweb.asm.ClassWriter.COMPUTE_MAXS);
      node.accept(writer);
      return writer.toByteArray();
    }
  }

  private static void geneCallbacks(
      ItemStack specimen,
      Object gene,
      ISpeciesRoot root,
      IChromosomeType chromosome,
      IAllele allele)
      throws Exception {
    ItemStack mutated = specimen.copy();
    NBTTagCompound original = (NBTTagCompound) specimen.getTagCompound().copy();
    Class<?> geneType = Class.forName("binnie.genetics.api.IGene");
    Class.forName(MACHINE + "inoculator.Inoculator")
        .getMethod("setGene", geneType, ItemStack.class, int.class)
        .invoke(null, gene, mutated, 1);
    require(
        mutated
            .getTagCompound()
            .getCompoundTag("Genome")
            .getTagList("Chromosomes", 10)
            .getCompoundTagAt(chromosome.ordinal())
            .getString("UID1")
            .equals(allele.getUID()),
        "Native setGene failed to write inactive homolog");
    require(
        mutated.getTagCompound().getString("fixtureUnrelated").equals("kept-or-rewritten-natively")
            && specimen.getTagCompound().equals(original),
        "Native gene callback or fixture mutated unrelated/source NBT");
    ItemStack seq = sequence(gene);
    Object record =
        Class.forName("binnie.genetics.genetics.SequencerItem")
            .getConstructor(ItemStack.class)
            .newInstance(seq);
    set(record.getClass(), record, "sequenced", 37);
    set(record.getClass(), record, "analysed", false);
    record.getClass().getMethod("writeToItem", ItemStack.class).invoke(record, seq);
    ItemStack analysed =
        (ItemStack)
            Class.forName(MACHINE + "analyser.Analyser")
                .getMethod("analyse", ItemStack.class)
                .invoke(null, seq.copy());
    Object decoded =
        Class.forName("binnie.genetics.genetics.SequencerItem")
            .getConstructor(ItemStack.class)
            .newInstance(analysed);
    require(
        (Integer) MagicApi.field(decoded, "sequenced") == 37
            && (Boolean) MagicApi.field(decoded, "analysed"),
        "Native analysis erased stored sequencing progress");
    Object decodedGene = record.getClass().getMethod("getGene").invoke(decoded);
    require(
        ((IAllele) decodedGene.getClass().getMethod("getAllele").invoke(decodedGene))
            .getUID()
            .equals(allele.getUID()),
        "Native sequence analysis defaulted its gene");
    set(record.getClass(), record, "sequenced", 183);
    record.getClass().getMethod("writeToItem", ItemStack.class).invoke(record, seq);
    Object wrapped = record.getClass().getConstructor(ItemStack.class).newInstance(seq);
    require(
        (Integer) MagicApi.field(wrapped, "sequenced") == -73,
        "Native sequencing progress lost signed byte persistence");
    ItemStack serum = serum(gene, false);
    Object engineering = Class.forName("binnie.genetics.genetics.Engineering");
    require(
        (Boolean)
            ((Class<?>) engineering)
                .getMethod("isGeneAcceptor", ItemStack.class)
                .invoke(null, serum),
        "Empty native serum cannot accept a gene");
    serum.setItemDamage(0);
    require(
        !(Boolean)
            ((Class<?>) engineering)
                .getMethod("isGeneAcceptor", ItemStack.class)
                .invoke(null, serum),
        "Charged serum accepted gene replacement");
  }

  private static Object unknownManager(Object source) throws Exception {
    String name = "fixture.UnknownGeneticsManager", internal = name.replace('.', '/');
    String parent = source.getClass().getName().replace('.', '/');
    org.objectweb.asm.ClassWriter writer = new org.objectweb.asm.ClassWriter(0);
    writer.visit(52, org.objectweb.asm.Opcodes.ACC_PUBLIC, internal, null, parent, null);
    writer
        .visitField(
            org.objectweb.asm.Opcodes.ACC_PUBLIC | org.objectweb.asm.Opcodes.ACC_STATIC,
            "calls",
            "I",
            null,
            null)
        .visitEnd();
    org.objectweb.asm.MethodVisitor method =
        writer.visitMethod(
            org.objectweb.asm.Opcodes.ACC_PUBLIC,
            "getActiveSystems",
            "()Ljava/util/Collection;",
            null,
            null);
    method.visitCode();
    method.visitFieldInsn(org.objectweb.asm.Opcodes.GETSTATIC, internal, "calls", "I");
    method.visitInsn(org.objectweb.asm.Opcodes.ICONST_1);
    method.visitInsn(org.objectweb.asm.Opcodes.IADD);
    method.visitFieldInsn(org.objectweb.asm.Opcodes.PUTSTATIC, internal, "calls", "I");
    method.visitVarInsn(org.objectweb.asm.Opcodes.ALOAD, 0);
    method.visitMethodInsn(
        org.objectweb.asm.Opcodes.INVOKESPECIAL,
        parent,
        "getActiveSystems",
        "()Ljava/util/Collection;",
        false);
    method.visitInsn(org.objectweb.asm.Opcodes.ARETURN);
    method.visitMaxs(2, 1);
    method.visitEnd();
    writer.visitEnd();
    byte[] bytes = writer.toByteArray();
    Class<?> owner =
        new ClassLoader(NativeBinnieGeneticsTest.class.getClassLoader()) {
          Class<?> define() {
            return defineClass(name, bytes, 0, bytes.length);
          }
        }.define();
    Object result = allocate(owner); // Avoid ManagerBase's constructor registration side effect.
    for (Field field : source.getClass().getDeclaredFields())
      if (!Modifier.isStatic(field.getModifiers())) {
        field.setAccessible(true);
        field.set(result, field.get(source));
      }
    return result;
  }

  private static void nativeIsolationAndIncubation(
      ItemStack specimen, ISpeciesRoot root, Object recipe, FluidStack medium) throws Exception {
    Fixture isolation = new Fixture();
    isolation.inventory.setInventorySlotContents(5, specimen.copy());
    isolation.inventory.setInventorySlotContents(1, misc("EmptySequencer"));
    isolation.tanks[0].fill(new FluidStack(FluidRegistry.getFluid("bioethanol"), 10), true);
    Object logic = isolation.logic("isolator", 4800, 192000);
    set(logic.getClass(), logic, "ethanolPerProcess", 10f);
    set(logic.getClass(), logic, "enzymePerProcess", 0.5f);
    call(logic, "onFinishTask");
    ItemStack output = isolation.inventory.getStackInSlot(6);
    require(
        output != null
            && isolation.inventory.getStackInSlot(1) == null
            && isolation.tanks[0].getFluidAmount() == 0,
        "Native isolator did not return one sequence/consume vial and ethanol");
    Object sequence =
        Class.forName("binnie.genetics.genetics.SequencerItem")
            .getConstructor(ItemStack.class)
            .newInstance(output);
    Object gene = sequence.getClass().getMethod("getGene").invoke(sequence);
    IAllele allele = (IAllele) gene.getClass().getMethod("getAllele").invoke(gene);
    IChromosomeType chromosome =
        (IChromosomeType) gene.getClass().getMethod("getChromosome").invoke(gene);
    IGenome genome = root.getMember(specimen).getGenome();
    require(
        allele.getUID().equals(genome.getActiveAllele(chromosome).getUID())
            || allele.getUID().equals(genome.getInactiveAllele(chromosome).getUID()),
        "Native random isolation fabricated a gene absent from input homologs");
    Fixture incubator = new Fixture();
    incubator.inventory.setInventorySlotContents(3, new ItemStack(Items.wheat, 3));
    incubator.tanks[0].fill(medium.copy(), true);
    recipe
        .getClass()
        .getMethod("doTask", Class.forName("binnie.core.machines.MachineUtil"))
        .invoke(recipe, incubator.util);
    int paper = 0;
    for (int slot = 0; slot < incubator.inventory.getSizeInventory(); slot++) {
      ItemStack stack = incubator.inventory.getStackInSlot(slot);
      if (stack != null && stack.getItem() == Items.paper) paper += stack.stackSize;
    }
    require(
        paper == 7
            && incubator.tanks[0].getFluidAmount() == 0
            && incubator.tanks[1].getFluidAmount() == 43,
        "Native incubator callback lost nondefault fixed item/fluid products");
    int remaining = incubator.inventory.getStackInSlot(3).stackSize;
    require(
        remaining == 2 || remaining == 3,
        "Native incubator loss threshold consumed more than one input");
  }

  private static void processors(ItemStack specimen, Object gene, ISpeciesRoot root)
      throws Exception {
    Fixture f = new Fixture();
    f.inventory.setInventorySlotContents(9, specimen.copy());
    f.inventory.setInventorySlotContents(0, serum(gene, false));
    f.inventory.getStackInSlot(0).setItemDamage(0);
    Object inoculator = f.logic("inoculator", 600, 24000);
    require(
        call(inoculator, "isValidSerum") == null, "Native nondefault serum incorrectly defunct");
    call(inoculator, "onFinishTask");
    require(
        f.inventory.getStackInSlot(0).getItemDamage() == 1,
        "Native inoculation did not consume one serum charge");
    require(
        AlleleManager.alleleRegistry.getIndividual(f.inventory.getStackInSlot(9)).isAnalyzed(),
        "Native inoculation omitted analysis");
    require(
        call(inoculator, "isValidSerum") != null,
        "Native homozygous identical serum was not rejected as defunct");
    f.inventory.setInventorySlotContents(9, specimen.copy());
    Object splicer = f.logic("splicer", 1200, 12000000);
    int mismatch = (Integer) call(splicer, "getGenesToUse");
    set(splicer.getClass(), splicer, "nOfGenes", mismatch);
    require(
        mismatch == 1 && (Integer) call(splicer, "getProcessLength") == 1200,
        "Native splicer mismatch/timing did not use actual genome");
    f.inventory.setInventorySlotContents(0, serum(gene, true));
    Object polymeriser = f.logic("polymeriser", 2400, 96000);
    require(
        (Integer) call(polymeriser, "getProcessLength") == 2400,
        "Native one-gene polymeriser duration changed");
    f.charges[1] = 1;
    require(
        (Integer) call(polymeriser, "getProcessLength") == 480,
        "Native gold catalyst factor changed");
    int damage = f.inventory.getStackInSlot(0).getItemDamage();
    call(polymeriser, "onFinishTask");
    require(
        f.inventory.getStackInSlot(0).getItemDamage() == damage - 1,
        "Native polymeriser did not add one serum charge");
    ItemStack seq = sequence(gene);
    Object sequenceData =
        Class.forName("binnie.genetics.genetics.SequencerItem")
            .getConstructor(ItemStack.class)
            .newInstance(seq);
    set(sequenceData.getClass(), sequenceData, "sequenced", 37);
    sequenceData.getClass().getMethod("writeToItem", ItemStack.class).invoke(sequenceData, seq);
    seq.setItemDamage(4);
    f.inventory.setInventorySlotContents(5, seq);
    Object sequencer = f.logic("sequencer", 19200, 384000);
    call(sequencer, "onStartTask");
    require(
        ((Number) call(sequencer, "getProgress")).floatValue() == 37,
        "Native sequencing did not resume stored progress");
    require(
        (Integer) call(sequencer, "getProcessLength") == 18624,
        "Native grade-dependent sequencing strength changed");
    require(call(sequencer, "canProgress") != null, "Native ownerless sequencing accepted");
    f.inventory.setInventorySlotContents(0, specimen.copy());
    Object genepool = f.logic("genepool", 400, 8000);
    require(
        (Integer)
                invokePrivate(genepool, "getDNAAmount", new Class<?>[] {ItemStack.class}, specimen)
            == 10,
        "Native non-bee DNA yield changed");
    call(genepool, "onFinishTask");
    require(
        f.inventory.getStackInSlot(0) == null && f.tanks[0].getFluidAmount() == 10,
        "Native gene pool did not delete input/fill DNA");
  }

  private static List<ItemStack> specimens(RegistryRecipes cursor) {
    Object entry = ((List<?>) MagicApi.field(cursor, "entries")).get(0);
    List<ItemStack> items = new ArrayList<>();
    for (Object in : (List<?>) MagicApi.field(entry, "inputs"))
      items.add(((ItemStack) MagicApi.field(in, "stack")).copy());
    for (Object out : (List<?>) MagicApi.field(entry, "outputs")) {
      ItemStack stack = (ItemStack) MagicApi.field(out, "item");
      if (stack != null) items.add(stack.copy());
    }
    return items;
  }

  private static RecipeRow knownRow(RegistryRecipes cursor, TemplateRecipeHandler handler)
      throws Exception {
    Facts facts = new Facts("en_US");
    Object entries = MagicApi.field(cursor, "entries");
    Object entry = ((List<?>) entries).get(0);
    for (Object in : (List<?>) MagicApi.field(entry, "inputs"))
      restore(facts, (ItemStack) MagicApi.field(in, "stack"));
    for (Object out : (List<?>) MagicApi.field(entry, "outputs")) {
      ItemStack item = (ItemStack) MagicApi.field(out, "item");
      if (item != null) restore(facts, item);
    }
    return new RecipeRow(
        facts,
        object("owner", "Genetics", "handler", handler.getClass().getName(), "key", "native"),
        "category_test",
        0);
  }

  private static void restore(Facts facts, ItemStack item) {
    facts.restore(
        "items",
        object(
            "id",
            Identity.item(
                Item.itemRegistry.getNameForObject(item.getItem()),
                item.getItemDamage(),
                TypedNbt.encode(item.getTagCompound()))));
  }

  private static ItemStack misc(String name) throws Exception {
    Object value = Class.forName(ITEM + "GeneticsItems").getField(name).get(null);
    return (ItemStack) value.getClass().getMethod("get", int.class).invoke(value, 1);
  }

  private static FluidStack fluid(String name, int amount) throws Exception {
    Object value = Class.forName(ITEM + "GeneticLiquid").getField(name).get(null);
    return (FluidStack) value.getClass().getMethod("get", int.class).invoke(value, amount);
  }

  private static Object gene(IAllele allele, IChromosomeType chromosome, ISpeciesRoot root)
      throws Exception {
    return Class.forName("binnie.core.genetics.Gene")
        .getConstructor(IAllele.class, IChromosomeType.class, ISpeciesRoot.class)
        .newInstance(allele, chromosome, root);
  }

  private static ItemStack serum(Object gene, boolean array) throws Exception {
    return (ItemStack)
        Class.forName(ITEM + (array ? "ItemSerumArray" : "ItemSerum"))
            .getMethod("create", Class.forName("binnie.genetics.api.IGene"))
            .invoke(null, gene);
  }

  private static ItemStack sequence(Object gene) throws Exception {
    return (ItemStack)
        Class.forName(ITEM + "ItemSequence")
            .getMethod("create", Class.forName("binnie.genetics.api.IGene"))
            .invoke(null, gene);
  }

  private static void expectDrift(RegistryRecipes cursor) {
    try {
      cursor.verify();
      throw new AssertionError("Native registry/config drift accepted");
    } catch (Jobs.Fault fault) {
      require(fault.code.equals("environment_changed"), "Wrong native drift fault");
    }
  }

  private static void set(Class<?> owner, Object target, String name, Object value)
      throws Exception {
    for (Class<?> type = owner; type != null; type = type.getSuperclass())
      try {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
        return;
      } catch (NoSuchFieldException ignored) {
      }
    throw new NoSuchFieldException(name);
  }

  private static Object call(Object target, String name) throws Exception {
    return invokePrivate(target, name, new Class<?>[0]);
  }

  private static Object invokePrivate(Object target, String name, Class<?>[] types, Object... args)
      throws Exception {
    for (Class<?> owner = target.getClass(); owner != null; owner = owner.getSuperclass())
      try {
        Method method = owner.getDeclaredMethod(name, types);
        method.setAccessible(true);
        try {
          return method.invoke(target, args);
        } catch (InvocationTargetException error) {
          Throwable cause = error.getCause();
          if (cause instanceof Error) throw (Error) cause;
          if (cause instanceof Exception) throw (Exception) cause;
          throw error;
        }
      } catch (NoSuchMethodException ignored) {
      }
    throw new NoSuchMethodException(name);
  }

  private static Object allocate(Class<?> type) throws Exception {
    Field access = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
    access.setAccessible(true);
    return ((sun.misc.Unsafe) access.get(null)).allocateInstance(type);
  }

  private static Object primitive(Class<?> type) {
    if (type == boolean.class) return false;
    if (type == int.class) return 0;
    if (type == float.class) return 0f;
    return null;
  }

  private static void require(boolean value, String message) {
    if (!value) throw new AssertionError(message);
  }

  private static final class Fixture {
    final InventoryBasic inventory = new InventoryBasic("fixture", false, 30);
    final FluidTank[] tanks = {new FluidTank(10000), new FluidTank(10000)};
    final float[] charges = new float[30];
    final Object machine, util;

    Fixture() throws Exception {
      Class<?> machineType = Class.forName("binnie.core.machines.IMachine"),
          tankType = Class.forName("binnie.core.machines.power.ITankMachine"),
          chargedType = Class.forName("binnie.core.machines.inventory.IChargedSlots");
      Object tankProxy =
          Proxy.newProxyInstance(
              tankType.getClassLoader(),
              new Class<?>[] {tankType},
              (p, m, a) ->
                  m.getName().equals("getTank")
                      ? tanks[(Integer) a[0]]
                      : m.getName().equals("getTanks") ? tanks : primitive(m.getReturnType()));
      Object charged =
          Proxy.newProxyInstance(
              chargedType.getClassLoader(),
              new Class<?>[] {chargedType},
              (p, m, a) -> {
                if (m.getName().equals("getCharge")) return charges[(Integer) a[0]];
                if (m.getName().equals("alterCharge")) {
                  charges[(Integer) a[0]] += (Float) a[1];
                  return null;
                }
                return primitive(m.getReturnType());
              });
      Object world = allocate(NativeBinnieLumbermillTest.NativeWorkWorld.class);
      set(net.minecraft.world.World.class, world, "rand", new Random(17));
      net.minecraft.tileentity.TileEntity tile = new net.minecraft.tileentity.TileEntity();
      Object[] utility = {null};
      machine =
          Proxy.newProxyInstance(
              machineType.getClassLoader(),
              new Class<?>[] {machineType},
              (p, m, a) -> {
                if (m.getName().equals("getMachineUtil")) return utility[0];
                if (m.getName().equals("getWorld")) return world;
                if (m.getName().equals("getTileEntity")) return tile;
                if (m.getName().equals("getInterface")) {
                  if (a[0] == IInventory.class) return inventory;
                  if (a[0] == tankType) return tankProxy;
                  if (a[0] == chargedType) return charged;
                }
                return primitive(m.getReturnType());
              });
      util =
          Class.forName("binnie.core.machines.MachineUtil")
              .getConstructor(machineType)
              .newInstance(machine);
      utility[0] = util;
    }

    Object logic(String name, int ticks, int energy) throws Exception {
      Class<?> type =
          Class.forName(
              MACHINE
                  + name
                  + "."
                  + Character.toUpperCase(name.charAt(0))
                  + name.substring(1)
                  + "ComponentLogic");
      Object logic = allocate(type);
      type.getMethod("setMachine", Class.forName("binnie.core.machines.IMachine"))
          .invoke(logic, machine);
      if (!name.equals("sequencer")) {
        set(type, logic, "processLength", ticks);
        set(type, logic, "processEnergy", energy);
      }
      return logic;
    }
  }
}
