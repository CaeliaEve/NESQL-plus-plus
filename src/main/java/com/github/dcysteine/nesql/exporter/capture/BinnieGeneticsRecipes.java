package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.*;
import forestry.api.genetics.*;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.item.*;
import net.minecraftforge.fluids.*;
import net.minecraftforge.oredict.OreDictionary;

/**
 * Binnie 2.5.24 genetic state machines. Native catalogues supply specimens, never NEI recipe lists.
 */
final class BinnieGeneticsRecipes implements RegistryRecipes {
  private static final String NEI = "binnie.genetics.nei.",
      MACHINE = "binnie.genetics.machine.",
      ITEM = "binnie.genetics.item.";
  private static final String[] MACHINES = {
    "Acclimatiser",
    "Sequencer",
    "Analyser",
    "Splicer",
    "Isolator",
    "Inoculator",
    "Genepool",
    "Incubator",
    "Polymeriser"
  };
  private Object[] anchors;
  private boolean closed;
  private final String machine;
  private final List<Domain> domains;
  private final List<Entry> entries;
  private JsonObject snapshot;
  private final String signature, batchSignature;
  private Object manager,
      alleleRegistry,
      incubatorRegistry,
      temperatureRegistry,
      humidityRegistry,
      toleranceRegistry;
  private final Map<String, ItemStack> referenceItems = new LinkedHashMap<>();
  private IdentityHashMap<JsonObject,BinnieWitnesses> witnesses;

  static boolean supports(ICraftingHandler handler) {
    for (String name : MACHINES)
      if (handler.getClass().getName().equals(NEI + name + "RecipeHandler")) return true;
    return false;
  }

  static boolean supportsReference(ICraftingHandler handler) {
    return handler.getClass().getName().equals(NEI + "DatabaseRecipeHandler");
  }

  BinnieGeneticsRecipes(TemplateRecipeHandler handler) {
    MagicApi.version("BinnieGeneticsRecipes","BinnieCore");
    MagicApi.version("BinnieGeneticsRecipes","Genetics");
    MagicApi.version("BinnieGeneticsRecipes","Forestry");
    if (!supports(handler) && !supportsReference(handler))
      throw fault("Unknown Binnie genetics handler");
    anchors = registrationAnchors();
    machine =
        handler.getClass().getSimpleName().replace("RecipeHandler", "").toLowerCase(Locale.ROOT);
    manager = field(type("binnie.Binnie"), null, "Genetics");
    alleleRegistry = AlleleManager.alleleRegistry;
    incubatorRegistry = field(type(MACHINE + "incubator.Incubator"), null, "RECIPES");
    temperatureRegistry =
        field(type(MACHINE + "acclimatiser.Acclimatiser"), null, "temperatureItems");
    humidityRegistry = field(type(MACHINE + "acclimatiser.Acclimatiser"), null, "humidityItems");
    toleranceRegistry =
        field(type(MACHINE + "acclimatiser.Acclimatiser"), null, "toleranceSystems");
    domains = readDomains();
    snapshot = readSnapshot(domains);
    signature = CanonicalJson.digest(snapshot);
    batchSignature = CanonicalJson.digest(readBatchSnapshot());
    entries = readEntries();
    finishVerification();
  }

  static Object[] registrationAnchors(codechicken.nei.recipe.IRecipeHandler handler) {
    List<Object> result = new ArrayList<>(Arrays.asList(registrationAnchors()));
    result.add(0, handler.getClass());
    return result.toArray();
  }

  private static Object[] registrationAnchors() {
    Object manager = field(type("binnie.Binnie"), null, "Genetics");
    if (manager == null || AlleleManager.alleleRegistry == null)
      throw new Jobs.Fault(
          "handler_not_ready", "Native genetics manager or allele registry is unavailable");
    if (manager.getClass() != type("binnie.core.genetics.ManagerGenetics"))
      throw fault("Unadapted native genetics manager callbacks: " + manager.getClass().getName());
    Object[] result = {
      manager,
      AlleleManager.alleleRegistry,
      field(manager, "breedingSystems"),
      field(manager, "chromosomeArray"),
      field(type(MACHINE + "incubator.Incubator"), null, "RECIPES"),
      field(type(MACHINE + "acclimatiser.Acclimatiser"), null, "temperatureItems"),
      field(type(MACHINE + "acclimatiser.Acclimatiser"), null, "humidityItems"),
      field(type(MACHINE + "acclimatiser.Acclimatiser"), null, "toleranceSystems")
    };
    for (Object value : result)
      if (value == null)
        throw new Jobs.Fault("handler_not_ready", "Native genetics registry is unavailable");
    List<Object> bindings = new ArrayList<>(Arrays.asList(result));
    // Native comparison sometimes uses == on alleles and systems. Equal serialized values do not
    // prove that a same-process registry still contains the objects observed at open.
    for (Map.Entry<?, ?> entry : identityMap(result[2]).entrySet()) {
      bind(bindings, entry.getKey());
      bind(bindings, entry.getValue());
      ISpeciesRoot root = speciesRoot(entry.getValue());
      if (root != entry.getKey())
        throw fault("Native breeding-system/root binding is inconsistent");
      bind(bindings, root);
      Object templates = root.getGenomeTemplates();
      bind(bindings, templates);
      for (Map.Entry<?, ?> template : identityMap(templates).entrySet()) {
        bind(bindings, template.getKey());
        if (!(template.getValue() instanceof IAllele[]))
          throw fault("Unadapted native genetics template storage");
        IAllele[] alleles = (IAllele[]) template.getValue();
        bind(bindings, alleles);
        if (alleles.length > 65536) throw fault("Native genetics template exceeds budget");
        for (IAllele allele : alleles) bind(bindings, allele == null ? Void.class : allele);
      }
      if (root.getClass().getName().equals("binnie.botany.genetics.FlowerHelper")) {
        Object conversions = field(root, "conversions");
        bind(bindings, conversions);
        for (Map.Entry<?, ?> conversion : identityMap(conversions).entrySet()) {
          bind(bindings, conversion.getKey());
          bind(bindings, conversion.getValue());
          if (conversion.getValue().getClass() != type("binnie.botany.genetics.Flower"))
            throw fault(
                "Unadapted native flower conversion individual: "
                    + conversion.getValue().getClass().getName());
        }
      }
    }
    for (Map.Entry<?, ?> root : identityMap(result[3]).entrySet()) {
      bind(bindings, root.getKey());
      bind(bindings, root.getValue());
      for (Map.Entry<?, ?> chromosome : identityMap(root.getValue()).entrySet()) {
        bind(bindings, chromosome.getKey());
        bind(bindings, chromosome.getValue());
        for (Object allele : identityList(chromosome.getValue())) bind(bindings, allele);
      }
    }
    Object invalid = field(manager, "invalidChromosomeTypes");
    bind(bindings, invalid);
    for (Object chromosome : identityList(invalid)) bind(bindings, chromosome);
    for (int index : new int[] {4, 7})
      for (Object entry : identityList(result[index])) bind(bindings, entry);
    for (int index : new int[] {5, 6})
      for (Object entry : identityMap(result[index]).keySet()) bind(bindings, entry);
    return bindings.toArray();
  }

  private static Map<?, ?> identityMap(Object value) {
    if (value == null
        || (value.getClass() != LinkedHashMap.class && value.getClass() != HashMap.class))
      throw fault("Unadapted native genetics map storage");
    Map<?, ?> result = (Map<?, ?>) value;
    if (result.size() > 65536) throw fault("Native genetics map exceeds budget");
    return result;
  }

  private static List<?> identityList(Object value) {
    if (value == null || value.getClass() != ArrayList.class)
      throw fault("Unadapted native genetics list storage");
    List<?> result = (List<?>) value;
    if (result.size() > 65536) throw fault("Native genetics list exceeds budget");
    return result;
  }

  private static void bind(List<Object> bindings, Object value) {
    Jobs.checkpoint();
    if (value == null) throw fault("Null native genetics source member");
    if (bindings.size() >= 262144) throw fault("Native genetics identity graph exceeds budget");
    bindings.add(value);
  }

  private void alive() {
    if (closed) throw new Jobs.Fault("capture_closed", "Native genetics reader is closed");
  }

  @Override
  public void close() {
    if (closed) return;
    closed = true;
    domains.clear();
    entries.clear();
    referenceItems.clear();
    if(witnesses!=null)witnesses.clear();witnesses=null;
    snapshot = null;
    anchors = null;
    manager = null;
    alleleRegistry = null;
    incubatorRegistry = null;
    temperatureRegistry = null;
    humidityRegistry = null;
    toleranceRegistry = null;
  }

  public int size() {
    alive();
    return entries.size();
  }

  public void verify() {
    alive();
    try {
      Object[] current = registrationAnchors();
      if (current.length != anchors.length) changed();
      for (int i = 0; i < current.length; i++) if (current[i] != anchors[i]) changed();
      if (manager != field(type("binnie.Binnie"), null, "Genetics")
          || alleleRegistry != AlleleManager.alleleRegistry
          || incubatorRegistry != field(type(MACHINE + "incubator.Incubator"), null, "RECIPES")
          || temperatureRegistry
              != field(type(MACHINE + "acclimatiser.Acclimatiser"), null, "temperatureItems")
          || humidityRegistry
              != field(type(MACHINE + "acclimatiser.Acclimatiser"), null, "humidityItems")
          || toleranceRegistry
              != field(type(MACHINE + "acclimatiser.Acclimatiser"), null, "toleranceSystems")
          || !batchSignature.equals(CanonicalJson.digest(readBatchSnapshot()))) changed();
    } catch (Jobs.Fault error) {
      if (error.code.equals("environment_changed")) throw error;
      Jobs.Fault drift =
          new Jobs.Fault(
              "environment_changed",
              "Binnie genetics native data became invalid: " + error.getMessage());
      drift.initCause(error);
      throw drift;
    }
  }

  @Override
  public void finishVerification() {
    verify();
    try {
      if (!signature.equals(CanonicalJson.digest(readSnapshot(readDomains())))) changed();
      verify();
    } catch (Jobs.Fault error) {
      if (error.code.equals("environment_changed")) throw error;
      Jobs.Fault drift =
          new Jobs.Fault(
              "environment_changed",
              "Binnie genetics native derivation became invalid: " + error.getMessage());
      drift.initCause(error);
      throw drift;
    }
  }

  public boolean capture(int index, RecipeRow row) {
    alive();
    Entry entry = entries.get(index);
    if (entry.shadowed) return false;
    JsonObject projected=BinnieWitnesses.shallow(entry.process);
    if(projected.has("domain")){
      if(witnesses==null)witnesses=new IdentityHashMap<>();
      JsonObject domain=projected.getAsJsonObject("domain");
      BinnieWitnesses selector=witnesses.computeIfAbsent(domain,BinnieWitnesses::new);
      List<JsonObject> inputs=new ArrayList<>();for(Input input:entry.inputs)inputs.add(stack(input.stack));
      projected.add("domain",selector.select(inputs));
    }
    JsonObject process = copy(projected);
    process.addProperty("kind", "binnie_genetics");
    process.addProperty("machine", machine);
    row.record.add("process", process);
    for (Input in : entry.inputs) {
      row.itemInput(in.slot,
          Collections.singletonList(
              new RecipeRow.Ingredient(
                  in.stack.copy(),
                  1,
                  !in.consume.equals("consume"),
                  object("kind", "wildcard", "meta", in.wildcardMeta, "nbt", true))),
          false);
      row.inputs
          .get(row.inputs.size() - 1)
          .getAsJsonObject()
          .getAsJsonArray("choices")
          .get(0)
          .getAsJsonObject()
          .add("consume", object("kind", in.consume));
    }
    for (Liquid in : entry.liquids) {
      FluidStack fluid = in.stack.copy();
      if(machine.equals("incubator")&&fluid.amount==0){
        row.zeroFluidInput(in.slot,fluid,"binnieIncubator");
        continue;
      }
      row.fluidInput(in.slot,
          fluid,
          false,
          fluid.getFluid().getName().equals("bioethanol") && !machine.equals("incubator")
              ? object("kind", "wildcard", "meta", false, "nbt", true)
              : object("kind", "exact"));
      row.inputs
          .get(row.inputs.size() - 1)
          .getAsJsonObject()
          .getAsJsonArray("choices")
          .get(0)
          .getAsJsonObject()
          .add("consume", object("kind", in.consume));
    }
    for (Product out : entry.outputs) {
      if (out.item != null) {
        row.itemOutput(out.slot,
            out.item.copy(),
            10000,
            out.dynamic
                ? Amounts.potential(
                    "binnie:geneticTransform",
                    "nativeInputState",
                    String.valueOf(out.item.stackSize),
                    out.item.stackSize)
                : null);
      } else {
        row.fluidOutput(out.slot,
            out.fluid.copy(),
            out.dynamic
                ? Amounts.potential(
                    "binnie:geneticTransform",
                    "nativeInputState",
                    String.valueOf(out.fluid.amount),
                    out.fluid.amount)
                : null);
      }
    }
    return true;
  }

  public JsonObject exclusion(int index) {
    alive();
    Entry e = entries.get(index);
    return e.shadowed
        ? object(
            "kind",
            "binnie_incubator_shadowed",
            "nativeIndex",
            index,
            "reason",
            "Earlier native registry selector covers this item/fluid identity")
        : null;
  }

  public JsonObject handlerExclusion() {
    return null;
  }

  /** Read-only domain payload, integrated separately from consuming Recipe rows. */
  JsonObject reference(Facts facts) {
    verify();
    JsonArray catalog = array(), containers = array();
    for (Domain domain : domains) {
      JsonObject root = copy(domain.data);
      JsonArray genes = array();
      for (Gene gene : domain.genes) {
        ItemStack serum = serum(gene, false),
            genome = serum(gene, true),
            sequence = sequence(gene, false);
        genes.add(
            object(
                "root",
                domain.root.getUID(),
                "chromosome",
                gene.chromosome.ordinal(),
                "allele",
                gene.allele.getUID(),
                "serum",
                referenceFact(facts, serum),
                "genome",
                referenceFact(facts, genome),
                "sequence",
                referenceFact(facts, sequence)));
      }
      root.add("genes", genes);
      catalog.add(root);
    }
    for (boolean array : new boolean[] {false, true})
      containers.add(
          object(
              "kind",
              array ? "genome" : "serum",
              "empty",
              referenceFact(facts, misc(array ? "EmptyGenome" : "EmptySerum")),
              "item",
              registry(
                  (Item)
                      field(
                          type("binnie.genetics.Genetics"),
                          null,
                          array ? "itemSerumArray" : "itemSerum")),
              "acceptor",
              "zeroCharges",
              "rootGate",
              array ? "sameRootForExistingArray" : "singleSerumAcceptsAnyRoot",
              "genePolicy",
              array ? "replaceSameChromosomeAppendNew" : "replaceSingleGene"));
    return object(
        "kind",
        "binnie_genetics_reference",
        "source",
        object(
            "owner",
            "Genetics",
            "handler",
            NEI + "DatabaseRecipeHandler",
            "key",
            "native-gene-domain"),
        "catalog",
        catalog,
        "containers",
        containers,
        "tracker",
        "GeneTracker.worldOwner",
        "discovery",
        "sequencerCompletion",
        "consuming",
        false);
  }

  private String referenceFact(Facts facts, ItemStack stack) {
    String id =
        com.github.dcysteine.nesql.exporter.source.Identity.item(
            registry(stack.getItem()),
            stack.getItemDamage(),
            TypedNbt.encode(stack.getTagCompound()));
    referenceItems.putIfAbsent(id, stack.copy());
    return facts == null ? id : facts.item(stack);
  }

  Map<String, ItemStack> referenceSpecimens() {
    alive();
    Map<String, ItemStack> result = new LinkedHashMap<>();
    referenceItems.forEach((id, stack) -> result.put(id, stack.copy()));
    return result;
  }

  private List<Domain> readDomains() {
    List<Domain> roots = new ArrayList<>();
    for (Object system :
        bounded(
            (Collection<?>)
                invoke(manager.getClass(), manager, "getActiveSystems", new Class<?>[0]))) {
      Jobs.checkpoint();
      roots.add(new Domain(speciesRoot(system), system));
    }
    roots.sort(Comparator.comparing(d -> d.root.getUID()));
    return roots;
  }

  private static ISpeciesRoot speciesRoot(Object system) {
    String name = system.getClass().getName();
    if (!Arrays.asList(
            "binnie.core.genetics.BeeBreedingSystem",
            "binnie.core.genetics.TreeBreedingSystem",
            "binnie.core.genetics.MothBreedingSystem",
            "binnie.core.genetics.FlowerBreedingSystem")
        .contains(name)) throw fault("Unadapted native breeding-system callbacks: " + name);
    ISpeciesRoot root =
        (ISpeciesRoot) invoke(system.getClass(), system, "getSpeciesRoot", new Class<?>[0]);
    if (root == null)
      throw new Jobs.Fault("handler_not_ready", "Native species root is unavailable");
    if (!Arrays.asList(
            "forestry.apiculture.genetics.BeeHelper",
            "forestry.arboriculture.genetics.TreeHelper",
            "forestry.lepidopterology.genetics.ButterflyHelper",
            "binnie.botany.genetics.FlowerHelper")
        .contains(root.getClass().getName()))
      throw fault("Unadapted native root member/genome callbacks: " + root.getClass().getName());
    return root;
  }

  private JsonObject readSnapshot(List<Domain> source) {
    JsonArray roots = array(), incubators = array(), resources = array();
    for (Domain domain : source) roots.add(domain.signature());
    for (Object raw : bounded((Collection<?>) incubatorRegistry)) incubators.add(incubator(raw));
    for (String resource :
        new String[] {
          "DNADye", "FluorescentDye", "Enzyme", "EmptySequencer", "EmptySerum", "EmptyGenome"
        }) resources.add(stack(misc(resource)));
    for (String field : new String[] {"itemSerum", "itemSerumArray", "itemSequencer"}) {
      Item item = (Item) field(type("binnie.genetics.Genetics"), null, field);
      if (item == null) throw fault("Missing native genetics item " + field);
      resources.add(
          object(
              "item",
              registry(item),
              "callback",
              item.getClass().getName(),
              "maxDamage",
              item.getMaxDamage()));
    }
    JsonArray liquids = array();
    for (String liquid : new String[] {"RawDNA", "BacteriaPoly", "BacteriaVector", "GrowthMedium"})
      liquids.add(fluid(liquid(liquid, 1)));
    FluidStack ethanol = FluidRegistry.getFluidStack("bioethanol", 1);
    if (ethanol == null) throw fault("Native ethanol resource unavailable");
    liquids.add(fluid(ethanol));
    return object(
        "roots",
        roots,
        "incubators",
        incubators,
        "temperature",
        effects((Map<?, ?>) temperatureRegistry),
        "humidity",
        effects((Map<?, ?>) humidityRegistry),
        "tolerances",
        tolerances(),
        "isolatorConsumptionChance",
        (Float)
            field(
                type("binnie.extrabees.config.EBConfigMachines"),
                null,
                "isolatorConsumptionChance"),
        "resources",
        resources,
        "liquids",
        liquids);
  }

  /**
   * Observe native inputs without regenerating members, analysing genomes or constructing Genes.
   */
  private JsonObject readBatchSnapshot() {
    JsonObject result = readSnapshot(Collections.emptyList());
    JsonArray roots = array();
    for (Domain domain : domains) {
      Jobs.checkpoint();
      JsonArray templates = array(), conversions = array();
      for (Map.Entry<String, IAllele[]> entry : domain.root.getGenomeTemplates().entrySet()) {
        JsonArray genes = array();
        for (IAllele allele : entry.getValue())
          genes.add(allele == null ? value(null) : allele(allele));
        templates.add(object("key", entry.getKey(), "alleles", genes));
      }
      if (domain.root.getClass().getName().equals("binnie.botany.genetics.FlowerHelper"))
        for (Map.Entry<?, ?> entry : identityMap(field(domain.root, "conversions")).entrySet()) {
          IIndividual individual = (IIndividual) entry.getValue();
          if (individual.getClass() != type("binnie.botany.genetics.Flower"))
            throw fault(
                "Unadapted native flower conversion individual: "
                    + individual.getClass().getName());
          net.minecraft.nbt.NBTTagCompound tag = new net.minecraft.nbt.NBTTagCompound();
          individual.copy().writeToNBT(tag);
          conversions.add(
              object(
                  "input", stack((ItemStack) entry.getKey()), "individual", TypedNbt.encode(tag)));
        }
      roots.add(
          object(
              "root",
              domain.root.getUID(),
              "chromosomes",
              chromosomes(domain.root),
              "templates",
              templates,
              "conversions",
              conversions));
    }
    result.add("roots", roots);
    return result;
  }

  private static JsonObject allele(IAllele allele) {
    JsonArray toleranceBounds = null;
    if (allele instanceof IAlleleTolerance) {
      Object tolerance =
          invoke(
              type("binnie.core.genetics.Tolerance"),
              null,
              "get",
              new Class<?>[] {EnumTolerance.class},
              ((IAlleleTolerance) allele).getValue());
      toleranceBounds = array();
      for (int bound :
          (int[]) invoke(tolerance.getClass(), tolerance, "getBounds", new Class<?>[0]))
        toleranceBounds.add(value(bound));
    }
    return object(
        "uid",
        allele.getUID(),
        "class",
        allele.getClass().getName(),
        "toleranceBounds",
        toleranceBounds);
  }

  private JsonArray chromosomes(ISpeciesRoot root) {
    Map<?, ?> map =
        (Map<?, ?>)
            invoke(
                manager.getClass(),
                manager,
                "getChromosomeMap",
                new Class<?>[] {ISpeciesRoot.class},
                root);
    if (map == null) throw fault("Missing native Binnie chromosome catalogue: " + root.getUID());
    JsonArray result = array();
    for (IChromosomeType chromosome : root.getKaryotype()) {
      JsonArray alleles = array();
      Object values = map.get(chromosome);
      if (values != null)
        for (Object raw : bounded((Collection<?>) values)) {
          JsonObject gene = allele((IAllele) raw);
          gene.addProperty("compatible", chromosome.getAlleleClass().isInstance(raw));
          alleles.add(gene);
        }
      result.add(
          object(
              "ordinal",
              chromosome.ordinal(),
              "name",
              chromosome.getName(),
              "alleleClass",
              chromosome.getAlleleClass().getName(),
              "invalid",
              invoke(
                  manager.getClass(),
                  manager,
                  "isInvalidChromosome",
                  new Class<?>[] {IChromosomeType.class},
                  chromosome),
              "alleles",
              alleles));
    }
    return result;
  }

  private List<Entry> readEntries() {
    List<Entry> rows =
        new ArrayList<Entry>() {
          @Override
          public boolean add(Entry row) {
            Jobs.checkpoint();
            if (size() >= 65536)
              throw new Jobs.Fault("recipe_limit", "Native genetics row budget exceeded");
            return super.add(row);
          }
        };
    if (machine.equals("database")) return rows;
    if (machine.equals("incubator")) {
      List<JsonObject> earlier = new ArrayList<>();
      int ordinal = 0;
      for (Object raw : bounded((Collection<?>) incubatorRegistry)) {
        JsonObject nativeData = incubator(raw);
        ItemStack input =
            owned((ItemStack) invoke(raw.getClass(), raw, "getInputStack", new Class<?>[0]));
        FluidStack inputFluid =
            ((FluidStack) invoke(raw.getClass(), raw, "getInput", new Class<?>[0])).copy();
        Entry e =
            new Entry(
                object(
                    "nativeIndex",
                    ordinal++,
                    "recipe",
                    nativeData,
                    "earlier",
                    json(earlier),
                    "rfPerTick",
                    2,
                    "attemptInterval",
                    20,
                    "selection",
                    "firstRegistryMatchCached",
                    "fluidGate",
                    "amountOnlyAfterSelection",
                    "outputGate",
                    "nativeRoomCheck"));
        for (JsonObject prior : earlier) if (covers(prior, nativeData)) e.shadowed = true;
        earlier.add(copy(nativeData));
        e.in(input, 3, "staged", input.getItemDamage() == OreDictionary.WILDCARD_VALUE)
            .fluid(inputFluid, 0, "staged");
        ItemStack output =
            (ItemStack) invoke(raw.getClass(), raw, "getExpectedOutput", new Class<?>[0]);
        FluidStack outputFluid =
            (FluidStack) invoke(raw.getClass(), raw, "getOutput", new Class<?>[0]);
        if (output != null)
          e.out(output, 0, raw.getClass().getSimpleName().equals("IncubatorRecipeLarvae"));
        if (outputFluid != null) e.out(outputFluid, 0, false);
        rows.add(e);
      }
      return rows;
    }
    for (Domain domain : domains) {
      if (machine.equals("analyser"))
        for (Conversion conversion : domain.conversions) {
          ItemStack input = conversion.input.copy();
          if (!analysable(input)) continue;
          ItemStack output =
              (ItemStack)
                  invoke(
                      type(MACHINE + "analyser.Analyser"),
                      null,
                      "analyse",
                      new Class<?>[] {ItemStack.class},
                      input.copy());
          rows.add(
              new Entry(
                      base(
                          domain,
                          "baseTicks",
                          300,
                          "baseRf",
                          9000,
                          "dyePerTick",
                          0.002f,
                          "inputGate",
                          "analysableAndUnanalysed",
                          "transformation",
                          "nativeAnalyseRewrite",
                          "conversion",
                          "nativeBreedingSystemFirstConversion"))
                  .in(input, 6, "staged", false)
                  .in(misc("DNADye"), 13, "buffer", false)
                  .out(output, 0, true));
        }
      if (machine.equals("sequencer")) {
        for (Gene gene : domain.genes)
          for (int grade = 0; grade < 6; grade++) {
            ItemStack seq = sequence(gene, false);
            seq.setItemDamage(grade);
            JsonObject data =
                base(
                    domain,
                    "baseTicks",
                    19200,
                    "rfPerTick",
                    20,
                    "grade",
                    grade,
                    "ownerRequired",
                    true,
                    "strength",
                    "oneMinusSquaredQualityTimesThreeQuarters",
                    "resume",
                    "storedSequencedInteger",
                    "progressWrite",
                    "truncatedProgressInteger",
                    "discovery",
                    "worldOwnerGeneTracker",
                    "progressEncoding",
                    "signedByteAfterIntegerTruncation",
                    "dyeChargePerCycle",
                    0.4f,
                    "outputGate",
                    "emptyVialCountBelow64");
            rows.add(
                new Entry(data)
                    .in(seq, 5, "staged", false)
                    .in(misc("FluorescentDye"), 0, "buffer", false)
                    .out(misc("EmptySequencer"), 0, false));
          }
        continue;
      }
      if (machine.equals("polymeriser")) {
        for (Gene gene : domain.genes)
          for (int form = 0; form < 3; form++) {
            ItemStack input = form == 2 ? sequence(gene, false) : serum(gene, form == 1);
            if (form == 2) input.setItemDamage(1);
            ItemStack output = input.copy();
            output.setItemDamage(input.getItemDamage() - 1);
            JsonObject data =
                base(
                    domain,
                    "baseTicks",
                    2400,
                    "baseRf",
                    96000,
                    "timeScale",
                    "geneCountTimesCatalyst",
                    "chargeGate",
                    "damagePositive",
                    "goldOptional",
                    true,
                    "goldTimeScale",
                    0.2f,
                    "goldCharge",
                    0.4f,
                    "dnaPerGene",
                    50,
                    "bacteriaPerGene",
                    10,
                    "tankGate",
                    1,
                    "drain",
                    "floatAccumulatorOneMillibucketPerTick",
                    "chargeChange",
                    1,
                    "state",
                    "accumulatorsPersistBetweenCycles");
            rows.add(
                new Entry(data)
                    .in(input, 0, "staged", true)
                    .fluid(liquid("RawDNA", 1), 1, "buffer")
                    .fluid(liquid("BacteriaPoly", 1), 0, "buffer")
                    .out(output, 0, true));
          }
        continue;
      }
      for (Member member : domain.members) {
        ItemStack input = member.stack.copy();
        if (machine.equals("analyser")) {
          if (!analysable(input)) continue;
          ItemStack output =
              (ItemStack)
                  invoke(
                      type(MACHINE + "analyser.Analyser"),
                      null,
                      "analyse",
                      new Class<?>[] {ItemStack.class},
                      input.copy());
          rows.add(
              new Entry(
                      base(
                          domain,
                          "baseTicks",
                          300,
                          "baseRf",
                          9000,
                          "dyePerTick",
                          0.002f,
                          "inputGate",
                          "analysableAndUnanalysed",
                          "transformation",
                          "nativeAnalyseRewrite",
                          "conversion",
                          "nativeBreedingSystemFirstConversion"))
                  .in(input, 6, "staged", false)
                  .in(misc("DNADye"), 13, "buffer", false)
                  .out(output, 0, true));
        } else if (machine.equals("genepool")) {
          rows.add(
              new Entry(
                      base(
                          domain,
                          "baseTicks",
                          400,
                          "baseRf",
                          8000,
                          "yield",
                          "beeDrone10OtherBee30MatedBee50Other10",
                          "ethanolPerDna",
                          1.2f,
                          "enzymeCharge",
                          0.25f,
                          "deleteWholeInputStack",
                          true,
                          "tankGate",
                          1,
                          "drain",
                          "floatAccumulatorOneMillibucketPerTick",
                          "state",
                          "accumulatorsPersistBetweenCycles"))
                  .in(input, 0, "staged", false)
                  .in(misc("Enzyme"), 7, "buffer", false)
                  .fluid(ethanol(), 1, "buffer")
                  .out(liquid("RawDNA", dnaAmount(input)), 0, true));
        } else if (machine.equals("isolator")) {
          if (domain.genes.isEmpty()) continue;
          Gene gene = domain.isolatedGene(input);
          rows.add(
              new Entry(
                      base(
                          domain,
                          "baseTicks",
                          4800,
                          "baseRf",
                          192000,
                          "enzymeCharge",
                          0.5f,
                          "ethanol",
                          10,
                          "consumptionThreshold",
                          snapshot.get("isolatorConsumptionChance"),
                          "chromosomeSelection",
                          "uniformNonNullNativeChromosome",
                          "homologSelection",
                          "uniformActiveInactive",
                          "outputGate",
                          "emptySequenceSlot",
                          "invalidGenome",
                          "noGeneNoConsumption"))
                  .in(input, 5, "staged", false)
                  .in(misc("EmptySequencer"), 1, "consume", false)
                  .in(misc("Enzyme"), 0, "buffer", false)
                  .fluid(ethanol(10), 0, "staged")
                  .out(sequence(gene, false), 0, true));
        } else if (machine.equals("inoculator") || machine.equals("splicer")) {
          if (!member.manipulable || domain.genes.isEmpty()) continue;
          for (boolean array : new boolean[] {false, true}) {
            Gene gene = domain.mismatchedGene(input);
            if (gene == null) continue;
            ItemStack serum = serum(gene, array);
            serum.setItemDamage(0);
            ItemStack output = input.copy();
            // This is a conditional specimen. The native finish rewrites analysis NBT then both
            // homologs.
            Object individual = ForestryMembers.read(output);
            if (individual != null && !((IIndividual) individual).isAnalyzed()) {
              ((IIndividual) individual).analyze();
              net.minecraft.nbt.NBTTagCompound tag = new net.minecraft.nbt.NBTTagCompound();
              ((IIndividual) individual).writeToNBT(tag);
              output.setTagCompound(tag);
            }
            applyGene(machine, gene, output, 0);
            applyGene(machine, gene, output, 1);
            JsonObject data =
                base(
                    domain,
                    "baseTicks",
                    machine.equals("splicer") ? 1200 : 12000,
                    "baseRf",
                    machine.equals("splicer") ? 12000000 : 600000,
                    "timeScale",
                    machine.equals("splicer") ? "mismatchedGenesHalfIncrement" : "geneCount",
                    "serum",
                    "nativeSingleOrArray",
                    "inputGate",
                    "nativeDnaManipulable",
                    "rootGate",
                    "firstSerumGeneRootMember",
                    "defunctGate",
                    "allGenesAlreadyBothHomologsUid",
                    "chargesGate",
                    "nonzero",
                    "serumDamage",
                    1,
                    "transformation",
                    "analyseThenSetBothHomologs",
                    "beeChromosomeOffset",
                    "humidityAndLaterMinusOne",
                    "alleleClassGate",
                    true,
                    "mismatchComparison",
                    "alleleObjectIdentity");
            Entry e =
                new Entry(data)
                    .in(input, 9, "staged", false)
                    .in(serum, 0, "wear", true)
                    .out(output, 0, true);
            if (machine.equals("inoculator")) {
              data.addProperty("bacteria", 15);
              data.addProperty("tankGate", 1);
              data.addProperty("drain", "floatAccumulatorOneMillibucketPerTick");
              e.fluid(liquid("BacteriaVector", 1), 0, "buffer");
            }
            rows.add(e);
          }
        } else if (machine.equals("acclimatiser")) {
          int ordinal = 0;
          for (Object system : bounded((Collection<?>) toleranceRegistry)) {
            int nativeOrdinal = ordinal++;
            if (!domain.root.getUID().equals(field(system, "uid"))) continue;
            Object toleranceType = field(system, "type");
            String effectType = ((Enum<?>) toleranceType).name();
            IChromosomeType chromosome = (IChromosomeType) field(system, "chromosomeOrdinal");
            Map<?, ?> effects =
                effectType.equals("Humidity")
                    ? (Map<?, ?>) humidityRegistry
                    : (Map<?, ?>) temperatureRegistry;
            if (effectType.equals("PH"))
              continue; // Native ToleranceType.PH.getEffect always returns zero.
            for (Object key : bounded(effects.keySet())) {
              ItemStack resource = owned((ItemStack) key);
              float effect =
                  ((Number)
                          invoke(
                              toleranceType.getClass(),
                              toleranceType,
                              "getEffect",
                              new Class<?>[] {ItemStack.class},
                              resource.copy()))
                      .floatValue();
              if (effect == 0) continue;
              if (!(Boolean)
                  invoke(
                      system.getClass(),
                      system,
                      "canAlter",
                      new Class<?>[] {ItemStack.class, ItemStack.class},
                      input.copy(),
                      resource.copy())) continue;
              Object first =
                  invoke(
                      type(MACHINE + "acclimatiser.Acclimatiser"),
                      null,
                      "getToleranceSystem",
                      new Class<?>[] {ItemStack.class, ItemStack.class},
                      input.copy(),
                      resource.copy());
              if (first != system) continue;
              JsonObject data =
                  base(
                      domain,
                      "systemOrdinal",
                      nativeOrdinal,
                      "chromosome",
                      chromosome.ordinal(),
                      "toleranceType",
                      effectType,
                      "effect",
                      effect,
                      "systems",
                      copyArray(snapshot.getAsJsonArray("tolerances")),
                      "temperature",
                      copyArray(snapshot.getAsJsonArray("temperature")),
                      "humidity",
                      copyArray(snapshot.getAsJsonArray("humidity")),
                      "rfPerTick",
                      2,
                      "attemptInterval",
                      100,
                      "selection",
                      "firstSystemThenUniformValidOccupiedSlot",
                      "alteration",
                      "activeBoundsExpandClampFiveThenWidthProbabilityRandomHomolog",
                      "consumption",
                      "firstItemEqualReagentEvenUnchanged",
                      "inputGate",
                      "nativeCanAlterActiveTolerance");
              rows.add(
                  new Entry(data)
                      .in(input, 4, "staged", false)
                      .in(resource, 0, "staged", false)
                      .out(input, 0, true));
            }
          }
        }
        if (rows.size() > 65536) throw fault("Binnie genetics native row budget exceeded");
      }
      if (machine.equals("analyser"))
        for (Gene gene : domain.genes) {
          ItemStack seq = sequence(gene, false);
          ItemStack output =
              (ItemStack)
                  invoke(
                      type(MACHINE + "analyser.Analyser"),
                      null,
                      "analyse",
                      new Class<?>[] {ItemStack.class},
                      seq.copy());
          rows.add(
              new Entry(
                      base(
                          domain,
                          "baseTicks",
                          300,
                          "baseRf",
                          9000,
                          "dyePerTick",
                          0.002f,
                          "inputGate",
                          "analysableAndUnanalysed",
                          "transformation",
                          "nativeSequenceAnalysedFlag",
                          "conversion",
                          "none"))
                  .in(seq, 6, "staged", true)
                  .in(misc("DNADye"), 13, "buffer", false)
                  .out(output, 0, true));
        }
    }
    if (rows.size() > 65536) throw fault("Binnie genetics native row budget exceeded");
    return rows;
  }

  private JsonObject base(Domain domain, Object... pairs) {
    JsonObject data = object(pairs);
    // Private immutable source graph; capture projects and detaches the row before publication.
    data.add("domain", domain.data);
    return data;
  }

  private static boolean analysable(ItemStack input) {
    return (Boolean)
        invoke(
            type(MACHINE + "analyser.Analyser"),
            null,
            "isAnalysable",
            new Class<?>[] {ItemStack.class},
            input.copy());
  }

  private JsonArray tolerances() {
    JsonArray result = array();
    for (Object system : bounded((Collection<?>) toleranceRegistry)) {
      if (!system.getClass().getName().equals(MACHINE + "acclimatiser.ToleranceSystem"))
        throw fault("Unadapted tolerance-system callbacks: " + system.getClass().getName());
      result.add(
          object(
              "root",
              field(system, "uid"),
              "chromosome",
              ((IChromosomeType) field(system, "chromosomeOrdinal")).ordinal(),
              "type",
              ((Enum<?>) field(system, "type")).name()));
    }
    return result;
  }

  private static JsonArray effects(Map<?, ?> source) {
    JsonArray result = array();
    for (Map.Entry<?, ?> e : source.entrySet()) {
      if (!(e.getKey() instanceof ItemStack)
          || !(e.getValue() instanceof Float)
          || !Float.isFinite((Float) e.getValue()))
        throw fault("Invalid native acclimatiser effect");
      result.add(object("item", stack(owned((ItemStack) e.getKey())), "effect", e.getValue()));
    }
    return result;
  }

  private static JsonObject incubator(Object raw) {
    String name = raw.getClass().getName();
    if (!Arrays.asList(
            MACHINE + "incubator.IncubatorRecipe", MACHINE + "incubator.IncubatorRecipeLarvae")
        .contains(name))
      throw fault("Unadapted native incubator callbacks (fluid, output, task): " + name);
    return object(
        "callback",
        name.endsWith("Larvae") ? "larvaeToDrone" : "fixed",
        "input",
        stack(owned((ItemStack) invoke(raw.getClass(), raw, "getInputStack", new Class<?>[0]))),
        "fluidInput",
        fluid((FluidStack) invoke(raw.getClass(), raw, "getInput", new Class<?>[0])),
        "fluidOutput",
        nullableFluid((FluidStack) invoke(raw.getClass(), raw, "getOutput", new Class<?>[0])),
        "output",
        nullableStack(
            (ItemStack) invoke(raw.getClass(), raw, "getExpectedOutput", new Class<?>[0])),
        "chance",
        invoke(raw.getClass(), raw, "getChance", new Class<?>[0]),
        "lossChance",
        invoke(raw.getClass(), raw, "getLossChance", new Class<?>[0]));
  }

  private static boolean covers(JsonObject prior, JsonObject next) {
    JsonObject a = prior.getAsJsonObject("input"), b = next.getAsJsonObject("input");
    return a.get("item").equals(b.get("item"))
        && (a.get("meta").getAsInt() == OreDictionary.WILDCARD_VALUE
            || a.get("meta").equals(b.get("meta")))
        && prior
            .getAsJsonObject("fluidInput")
            .get("fluid")
            .equals(next.getAsJsonObject("fluidInput").get("fluid"))
        && prior
            .getAsJsonObject("fluidInput")
            .get("nbt")
            .equals(next.getAsJsonObject("fluidInput").get("nbt"));
  }

  private static void applyGene(String machine, Gene gene, ItemStack stack, int homolog) {
    invoke(
        type(MACHINE + machine + "." + title(machine)),
        null,
        "setGene",
        new Class<?>[] {type("binnie.genetics.api.IGene"), ItemStack.class, int.class},
        gene.nativeGene,
        stack,
        homolog);
  }

  private static ItemStack serum(Gene gene, boolean array) {
    return owned(
        (ItemStack)
            invoke(
                type(ITEM + (array ? "ItemSerumArray" : "ItemSerum")),
                null,
                "create",
                new Class<?>[] {type("binnie.genetics.api.IGene")},
                gene.nativeGene));
  }

  private static ItemStack sequence(Gene gene, boolean filled) {
    return owned(
        (ItemStack)
            invoke(
                type(ITEM + "ItemSequence"),
                null,
                "create",
                new Class<?>[] {type("binnie.genetics.api.IGene"), boolean.class},
                gene.nativeGene,
                filled));
  }

  private static ItemStack misc(String symbol) {
    Object value = field(type(ITEM + "GeneticsItems"), null, symbol);
    return owned((ItemStack) invoke(value.getClass(), value, "get", new Class<?>[] {int.class}, 1));
  }

  private static FluidStack liquid(String symbol, int amount) {
    Object value = field(type(ITEM + "GeneticLiquid"), null, symbol);
    FluidStack fluid =
        (FluidStack) invoke(value.getClass(), value, "get", new Class<?>[] {int.class}, amount);
    if (fluid == null) throw fault("Native genetics liquid unavailable: " + symbol);
    return fluid.copy();
  }

  private static FluidStack ethanol() {
    return ethanol(1);
  }

  private static FluidStack ethanol(int amount) {
    FluidStack fluid = FluidRegistry.getFluidStack("bioethanol", amount);
    if (fluid == null) throw fault("Native ethanol resource unavailable");
    return fluid;
  }

  private int dnaAmount(ItemStack input) {
    Object bee = invoke(manager.getClass(), manager, "getBeeRoot", new Class<?>[0]);
    if (bee == null
        || AlleleManager.alleleRegistry.getSpeciesRoot(input) != bee
        || (Boolean)
            invoke(bee.getClass(), bee, "isDrone", new Class<?>[] {ItemStack.class}, input.copy()))
      return 10;
    return (Boolean)
            invoke(bee.getClass(), bee, "isMated", new Class<?>[] {ItemStack.class}, input.copy())
        ? 50
        : 30;
  }

  private static ItemStack owned(ItemStack item) {
    if (item == null || item.getItem() == null) throw fault("Empty native genetics item");
    NativeStackAudit.audit(item);
    registry(item.getItem());
    return item.copy();
  }

  private static String registry(Item item) {
    String name = Item.itemRegistry.getNameForObject(item);
    if (name == null || Item.itemRegistry.getObject(name) != item)
      throw fault("Unregistered native genetics item: " + item.getClass().getName());
    return name;
  }

  private static JsonObject stack(ItemStack item) {
    NativeStackAudit.audit(item);
    return object(
        "item",
        registry(item.getItem()),
        "meta",
        item.getItemDamage(),
        "amount",
        item.stackSize,
        "nbt",
        TypedNbt.encode(item.getTagCompound()));
  }

  private static JsonObject nullableStack(ItemStack item) {
    return item == null ? null : stack(owned(item));
  }

  private static JsonObject fluid(FluidStack fluid) {
    if (fluid == null
        || fluid.getFluid() == null
        || FluidRegistry.getFluid(fluid.getFluid().getName()) != fluid.getFluid())
      throw fault("Unregistered native genetics fluid");
    return object(
        "fluid",
        fluid.getFluid().getName(),
        "amount",
        fluid.amount,
        "nbt",
        TypedNbt.encode(fluid.tag));
  }

  private static JsonObject nullableFluid(FluidStack fluid) {
    return fluid == null ? null : fluid(fluid);
  }

  private static JsonObject copy(JsonObject value) {
    return new JsonParser().parse(value.toString()).getAsJsonObject();
  }

  private static JsonObject object(Object... fields) {
    for (int index = 1; index < fields.length; index += 2)
      if (fields[index] instanceof Float) {
        float nativeValue = (Float) fields[index];
        if (!Float.isFinite(nativeValue))
          throw fault("Non-finite native genetics probability or charge");
        fields[index] = new JsonPrimitive(nativeValue);
      }
    return com.github.dcysteine.nesql.exporter.source.Json.object(fields);
  }

  private static JsonArray copyArray(JsonArray value) {
    return new JsonParser().parse(value.toString()).getAsJsonArray();
  }

  private static JsonArray json(Collection<JsonObject> rows) {
    JsonArray result = array();
    for (JsonObject row : rows) result.add(copy(row));
    return result;
  }

  private static <T> Collection<T> bounded(Collection<T> source) {
    if (source == null || source.size() > 65536)
      throw fault("Invalid native genetics registry budget");
    return source;
  }

  private static String title(String value) {
    return Character.toUpperCase(value.charAt(0)) + value.substring(1);
  }

  private static Jobs.Fault fault(String message) {
    return new Jobs.Fault("recipe_unsupported", message);
  }

  private static void changed() {
    throw new Jobs.Fault(
        "environment_changed",
        "Binnie native genetics registries, types, genes, resources or configuration changed");
  }

  private static final class Gene {
    final IAllele allele;
    final IChromosomeType chromosome;
    final Object nativeGene;

    Gene(IAllele allele, IChromosomeType chromosome, ISpeciesRoot root) {
      this.allele = allele;
      this.chromosome = chromosome;
      try {
        nativeGene =
            type("binnie.core.genetics.Gene")
                .getConstructor(IAllele.class, IChromosomeType.class, ISpeciesRoot.class)
                .newInstance(allele, chromosome, root);
      } catch (ReflectiveOperationException error) {
        throw fault("Native Gene constructor changed: " + error);
      }
    }
  }

  private final class Domain {
    final ISpeciesRoot root;
    final Object system;
    final JsonObject data;
    final List<Gene> genes = new ArrayList<>();
    final List<Member> members = new ArrayList<>();
    final List<Conversion> conversions = new ArrayList<>();

    Domain(ISpeciesRoot root, Object system) {
      this.root = root;
      this.system = system;
      JsonArray chromosomes = chromosomes(root), types = array();
      Map<?, ?> map =
          (Map<?, ?>)
              invoke(
                  manager.getClass(),
                  manager,
                  "getChromosomeMap",
                  new Class<?>[] {ISpeciesRoot.class},
                  root);
      if (map == null) throw fault("Missing native Binnie chromosome catalogue: " + root.getUID());
      for (IChromosomeType chromosome : root.getKaryotype()) {
        Object values = map.get(chromosome);
        if (values != null)
          for (Object raw : bounded((Collection<?>) values)) {
            IAllele allele = (IAllele) raw;
            if (chromosome.getAlleleClass().isInstance(allele)) {
              if (genes.size() >= 65536) throw fault("Native Binnie gene domain exceeds budget");
              genes.add(new Gene(allele, chromosome, root));
            }
          }
      }
      int[] active = (int[]) invoke(system.getClass(), system, "getActiveTypes", new Class<?>[0]);
      for (int ordinal : active) types.add(value(ordinal));
      for (IAllele[] template : bounded(root.getGenomeTemplates().values()))
        for (int ordinal : active) {
          Jobs.checkpoint();
          if (members.size() >= 65536) throw fault("Native Binnie member domain exceeds budget");
          ItemStack specimen =
              owned(root.getMemberStack(ForestryMembers.create(root,template), ordinal));
          boolean manipulable =
              (Boolean)
                  invoke(
                      system.getClass(),
                      system,
                      "isDNAManipulable",
                      new Class<?>[] {ItemStack.class},
                      specimen.copy());
          members.add(new Member(specimen, ordinal, manipulable));
        }
      if (members.size() > 65536 || genes.size() > 65536)
        throw fault("Native Binnie gene/member domain exceeds budget");
      JsonArray nativeMembers = array(), nativeConversions = array();
      for (Member member : members) {
        IIndividual analysed = ForestryMembers.read(root,member.stack);
        analysed.analyze();
        net.minecraft.nbt.NBTTagCompound tag = new net.minecraft.nbt.NBTTagCompound();
        analysed.writeToNBT(tag);
        ItemStack nativeAnalysis = member.stack.copy();
        nativeAnalysis.setTagCompound(tag);
        nativeMembers.add(
            object(
                "type",
                member.type,
                "manipulable",
                member.manipulable,
                "stack",
                stack(member.stack),
                "analysed",
                stack(nativeAnalysis),
                "dnaYield",
                dnaAmount(member.stack)));
      }
      if (root.getClass().getName().equals("binnie.botany.genetics.FlowerHelper")) {
        Map<?, ?> conversionMap = (Map<?, ?>) field(root, "conversions");
        bounded(conversionMap.entrySet());
        for (Map.Entry<?, ?> entry : conversionMap.entrySet()) {
          ItemStack source = owned((ItemStack) entry.getKey());
          IIndividual original = (IIndividual) entry.getValue();
          ItemStack registered = owned(root.getMemberStack(original.copy(), active[0]));
          ItemStack selected =
              (ItemStack)
                  invoke(
                      system.getClass(),
                      system,
                      "getConversionStack",
                      new Class<?>[] {ItemStack.class},
                      source.copy());
          if (selected == null)
            throw fault("Native flower conversion callback omitted registered selector");
          conversions.add(new Conversion(source, owned(selected)));
          IIndividual analysis = ForestryMembers.read(root,selected);
          analysis.analyze();
          net.minecraft.nbt.NBTTagCompound analysisTag = new net.minecraft.nbt.NBTTagCompound();
          analysis.writeToNBT(analysisTag);
          ItemStack analysed = selected.copy();
          analysed.setTagCompound(analysisTag);
          nativeConversions.add(
              object(
                  "input",
                  stack(source),
                  "registered",
                  stack(registered),
                  "output",
                  stack(selected),
                  "analysed",
                  stack(analysed)));
        }
      }
      data =
          object(
              "root",
              root.getUID(),
              "rootClass",
              root.getClass().getName(),
              "systemClass",
              system.getClass().getName(),
              "chromosomes",
              chromosomes,
              "memberTypes",
              types,
              "members",
              nativeMembers,
              "conversions",
              nativeConversions,
              "resources",
              object(
                  "serum",
                  registry((Item) field(type("binnie.genetics.Genetics"), null, "itemSerum")),
                  "genome",
                  registry((Item) field(type("binnie.genetics.Genetics"), null, "itemSerumArray")),
                  "sequence",
                  registry((Item) field(type("binnie.genetics.Genetics"), null, "itemSequencer")),
                  "dnaDye",
                  stack(misc("DNADye")),
                  "fluorescentDye",
                  stack(misc("FluorescentDye")),
                  "enzyme",
                  stack(misc("Enzyme")),
                  "emptySequence",
                  stack(misc("EmptySequencer")),
                  "rawDna",
                  fluid(liquid("RawDNA", 1)),
                  "polymerBacteria",
                  fluid(liquid("BacteriaPoly", 1)),
                  "vectorBacteria",
                  fluid(liquid("BacteriaVector", 1)),
                  "ethanol",
                  fluid(ethanol())));
    }

    Gene mismatchedGene(ItemStack input) {
      IIndividual individual = ForestryMembers.read(root,input);
      for (Gene gene : genes) {
        IGenome genome = individual.getGenome();
        if (!gene.allele.getUID().equals(genome.getActiveAllele(gene.chromosome).getUID())
            || !gene.allele.getUID().equals(genome.getInactiveAllele(gene.chromosome).getUID()))
          return gene;
      }
      return null;
    }

    Gene isolatedGene(ItemStack input) {
      IIndividual individual = ForestryMembers.read(root,input);
      for (Gene gene : genes)
        if (gene.allele
                .getUID()
                .equals(individual.getGenome().getActiveAllele(gene.chromosome).getUID())
            || gene.allele
                .getUID()
                .equals(individual.getGenome().getInactiveAllele(gene.chromosome).getUID()))
          return gene;
      throw fault("Native member has no registered compatible chromosome to isolate");
    }

    JsonObject signature() {
      JsonObject result = copy(data);
      JsonArray samples = array();
      for (Member member : members)
        samples.add(
            object(
                "type",
                member.type,
                "manipulable",
                member.manipulable,
                "stack",
                stack(member.stack)));
      result.add("members", samples);
      return result;
    }
  }

  private static final class Member {
    final ItemStack stack;
    final int type;
    final boolean manipulable;

    Member(ItemStack stack, int type, boolean manipulable) {
      this.stack = stack;
      this.type = type;
      this.manipulable = manipulable;
    }
  }

  private static final class Conversion {
    final ItemStack input, output;

    Conversion(ItemStack input, ItemStack output) {
      this.input = input;
      this.output = output;
    }
  }

  private static final class Input {
    final ItemStack stack;
    final int slot;
    final String consume;
    final boolean wildcardMeta;

    Input(ItemStack stack, int slot, String consume, boolean wildcardMeta) {
      this.stack = owned(stack);
      this.slot = slot;
      this.consume = consume;
      this.wildcardMeta = wildcardMeta;
    }
  }

  private static final class Liquid {
    final FluidStack stack;
    final int slot;
    final String consume;

    Liquid(FluidStack stack, int slot, String consume) {
      this.stack = stack.copy();
      this.slot = slot;
      this.consume = consume;
    }
  }

  private static final class Product {
    final ItemStack item;
    final FluidStack fluid;
    final int slot;
    final boolean dynamic;

    Product(ItemStack item, FluidStack fluid, int slot, boolean dynamic) {
      this.item = item == null ? null : owned(item);
      this.fluid = fluid == null ? null : fluid.copy();
      this.slot = slot;
      this.dynamic = dynamic;
    }
  }

  private static final class Entry {
    final JsonObject process;
    final List<Input> inputs = new ArrayList<>();
    final List<Liquid> liquids = new ArrayList<>();
    final List<Product> outputs = new ArrayList<>();
    boolean shadowed;

    Entry(JsonObject process) {
      this.process = process;
    }

    Entry in(ItemStack item, int slot, String consume, boolean wildcardMeta) {
      inputs.add(new Input(item, slot, consume, wildcardMeta));
      return this;
    }

    Entry fluid(FluidStack fluid, int slot, String consume) {
      liquids.add(new Liquid(fluid, slot, consume));
      return this;
    }

    Entry out(ItemStack item, int slot, boolean dynamic) {
      outputs.add(new Product(item, null, slot, dynamic));
      return this;
    }

    Entry out(FluidStack fluid, int slot, boolean dynamic) {
      outputs.add(new Product(null, fluid, slot, dynamic));
      return this;
    }
  }
}
