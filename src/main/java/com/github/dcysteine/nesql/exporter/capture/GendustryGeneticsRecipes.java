package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.*;
import forestry.api.genetics.*;
import java.util.*;
import java.util.List;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

/**
 * Native Gendustry process rules and owned catalogue specimens, independent of recipe GUI state.
 */
final class GendustryGeneticsRecipes implements RegistryRecipes {
  private static final String ROOT = "net.bdew.gendustry.nei.";
  private final String machine;
  private final Class<?> handlerClass;
  private GendustryGeneticsCatalog catalogue;
  private JsonObject context;
  private String signature;
  private Object[] anchors;
  private GendustryRootBindings rootBindings;
  private GendustrySpecimens specimens;
  private GendustrySamplerSource samplerSource;
  private String catalogueSignature;
  private GendustrySourceTable sourceTable;
  private boolean closed;

  static boolean supports(ICraftingHandler h) {
    return h != null && supportsClass(h.getClass().getName());
  }

  static boolean supportsClass(String name) {
    if (name == null) return false;
    for (String machine :
        new String[] {"Imprinter", "Sampler", "Transposer", "Mutatron", "Replicator", "Extractor"})
      if (name.equals(ROOT + machine + "Handler")) return true;
    return false;
  }

  static Object[] registrationAnchors(Class<?> handlerClass) {
    String machine = handlerClass.getSimpleName().replace("Handler", "");
    List<Object> source = new ArrayList<>();
    source.add(handlerClass);
    source.add(AlleleManager.alleleRegistry);
    Collections.addAll(source,GendustryConfiguration.anchors(module("machines." + machine.toLowerCase(Locale.ROOT) + ".Machine" + machine)));
    source.add(module("config.Items"));
    source.add(module("items.GeneTemplate"));
    source.add(module("items.GeneSample"));
    if (machine.equals("Sampler")) {
      Object cache = module("misc.GeneticsCache");
      source.add(cache);
      source.add(call(cache, "geneSamples"));
      source.add(call(cache, "speciesChromosomes"));
    }
    if (machine.equals("Extractor")) source.add(module("fluids.LiquidDNASources"));
    return source.toArray();
  }

  GendustryGeneticsRecipes(TemplateRecipeHandler handler) {
    if (!supports(handler)) throw fault("Unadapted Gendustry genetics handler");
    machine = handler.getClass().getSimpleName().replace("Handler", "");
    handlerClass = handler.getClass();
    MagicApi.version("GendustryGeneticsRecipes","gendustry");
    MagicApi.version("GendustryGeneticsRecipes","bdlib");
    try {
      anchors = registrationAnchors(handlerClass);
      rootBindings = new GendustryRootBindings();
      specimens=new GendustrySpecimens(rootBindings);
      if(machine.equals("Sampler"))samplerSource=new GendustrySamplerSource(specimens);
      if(machine.equals("Extractor"))sourceTable=new GendustrySourceTable(module("fluids.LiquidDNASources"),
          "net.bdew.gendustry.fluids.LiquidDNASources$");
      context = snapshot(specimens);
      signature = CanonicalJson.digest(context);
      catalogue = new GendustryGeneticsCatalog(machine,sourceTable,specimens,samplerSource);
      catalogueSignature=catalogue.signature();
      verify();
    } catch (RuntimeException | Error failure) {
      close();
      throw failure;
    }
  }

  public int size() {
    check();
    return catalogue.entries.size();
  }

  public void verify() {
    check();
    Object[] current = registrationAnchors(handlerClass);
    if (current.length != anchors.length) throw changed();
    for (int i = 0; i < anchors.length; i++) if (current[i] != anchors[i]) throw changed();
    rootBindings.verify();specimens.verify();if(samplerSource!=null)samplerSource.verify();
    if(sourceTable!=null)sourceTable.verify();
  }

  public void finishVerification(){
    verify();rootBindings.finishVerification();specimens.finishVerification();if(samplerSource!=null)samplerSource.finishVerification();
    if(sourceTable!=null)sourceTable.finishVerification();
    try(GendustrySpecimens fresh=new GendustrySpecimens(rootBindings)){
      if(!signature.equals(CanonicalJson.digest(snapshot(fresh))))throw changed();
      GendustryGeneticsCatalog observed=new GendustryGeneticsCatalog(machine,sourceTable,fresh,samplerSource);
      try{if(!catalogueSignature.equals(observed.signature()))throw changed();}finally{observed.clear();}
    }
  }

  public boolean capture(int index, RecipeRow row) {
    verify();
    if (index < 0 || index >= size()) throw fault("Native genetics index outside catalogue");
    GendustryGeneticsCatalog.Entry entry = catalogue.entries.get(index);
    if (entry.exclusion != null) return row.exclude(entry.exclusion);
    JsonObject slots = object();
    int slot = 0;
    for (Map.Entry<String, ItemStack> input : entry.inputs.entrySet()) {
      String role = input.getKey();
      slots.addProperty(role, slot);
      boolean keep =
          role.equals("template")
              && (machine.equals("Imprinter")
                  || machine.equals("Transposer")
                  || machine.equals("Replicator"));
      JsonObject rule =
          role.equals("individual") && entry.dnaSource != null
              ? entry.dnaSource.rule(row.facts)
              : object(
                  "kind",
                  "wildcard",
                  "meta",
                  !role.equals("individual") && !role.startsWith("parent"),
                  "nbt",
                  true);
      row.itemInput(slot,
          Collections.singletonList(new RecipeRow.Ingredient(input.getValue(), 1, keep, rule)),
          false);
      if (role.equals("labware"))
        row.inputs
            .get(slot)
            .getAsJsonObject()
            .getAsJsonArray("choices")
            .get(0)
            .getAsJsonObject()
            .add(
                "consume",
                labwareConsumption(
                    context.getAsJsonObject("config").get("labwareConsumeChance").getAsDouble()));
      slot++;
    }
    JsonObject outputPolicy =
        machine.equals("Imprinter")
            ? object("kind", "imprinter", "wasteRegistry", context.get("wasteRegistry"))
            : machine.equals("Mutatron")
                ? object(
                    "kind",
                    "mutatron",
                    "wasteRegistry",
                    context.get("wasteRegistry"),
                    "emptyCandidatesReturnNull",
                    true)
                : machine.equals("Sampler")
                    ? owned(context.get("samplerPolicy")).getAsJsonObject()
                    : object("kind", "direct");
    JsonObject config = context.getAsJsonObject("config");
    row.record.add(
        "process",
        object(
            "kind",
            "gendustryGenetics",
            "operation",
            entry.operation,
            "config",
            owned(config),
            "roots",
            owned(context.get("roots")),
            "slots",
            slots,
            "rngBound",
            100,
            "labwareThreshold",
            machine.equals("Replicator") ? null : config.get("labwareConsumeChance"),
            "selector",
            index,
            "sources",
            owned(context.get("sources")),
            "outputPolicy",
            outputPolicy));
    if (machine.equals("Mutatron"))
      row.fluidInput(0, nativeFluid("mutagen", config.get("mutagenPerItem").getAsInt()), false);
    if (machine.equals("Replicator")) {
      row.fluidInput(0, nativeFluid("dna", config.get("dnaPerItem").getAsInt()), false);
      row.fluidInput(1, nativeFluid("protein", config.get("proteinPerItem").getAsInt()), false);
    }
    if (entry.output != null) {
      row.itemOutput(0, entry.output, 10000);
      String action =
          machine.equals("Imprinter")
              ? "gendustryGenome"
              : machine.equals("Sampler")
                  ? "gendustryGeneSample"
                  : machine.equals("Replicator")
                      ? "gendustryReplication"
                      : machine.equals("Mutatron") ? "gendustryMutation" : "gendustryGeneTemplate";
      String primary =
          machine.equals("Imprinter") || machine.equals("Sampler")
              ? "individual"
              : machine.equals("Mutatron") ? "parent0" : "template";
      JsonArray samples = array(), bindings = array();
      if (entry.observation) {
        samples.add(
            object(
                "id",
                row.facts.item(entry.output),
                "amount",
                Integer.toString(entry.output.stackSize)));
        JsonArray tuple = array();
        for (JsonElement ignored : row.inputs) tuple.add(value(0));
        bindings.add(tuple);
      }
      row.outputs
          .get(0)
          .getAsJsonObject()
          .add(
              "change",
              object(
                  "input",
                  slots.get(primary).getAsInt(),
                  "action",
                  object("kind", action, "operation", entry.operation, "slots", owned(slots)),
                  "samples",
                  samples,
                  "bindings",
                  bindings));
    } else if (machine.equals("Extractor")) {
      if (entry.dna <= 0) throw fault("Native DNA amount is not positive");
      if (entry.dna > config.get("tankSize").getAsInt())
        throw fault("DNA batch exceeds native output tank capacity");
      row.fluidOutput(0, nativeFluid("dna", entry.dna));
    } else throw new Jobs.Fault("output_missing", "Native genetics rule has no output prototype");
    row.note(
        "gendustry:machine",
        "net.bdew.gendustry.machines."
            + machine.toLowerCase(Locale.ROOT)
            + ".Tile"
            + machine
            + "#tryStart;bdlib.TileBaseProcessor",
        row.facts.locale().equals("zh_CN")
            ? "基因机器使用实际投入的基因组、模板、配置与世界条件；展示物品仅为原生规则样例，不是固定 NBT 或成功保证。模板保留，个体或空白材料按机器取用，实验耗材使用 nextInt(100) 阈值概率。能量按原生储量比例推进，完成仍需产物空间；没有固定加工时长。"
            : "Genetic machines use the offered genome, template, configuration and world conditions. Displayed items are native rule specimens, not fixed NBT or guaranteed success. Templates are retained; individuals or blanks are used by the machine, and labware uses a nextInt(100) threshold. Native stored-energy progress and output space govern completion; no fixed processing duration is claimed.");
    if (entry.mutationBinding != null) mutationNotes(entry.mutationBinding, row);
    return true;
  }

  private void mutationNotes(JsonObject binding, RecipeRow row) {
    for (JsonElement element : context.getAsJsonArray("conditionNotes")) {
      JsonObject explanation = element.getAsJsonObject();
      if (!binding.get("root").equals(explanation.get("root"))) continue;
      boolean same =
          binding.get("parent0").equals(explanation.get("parent0"))
              && binding.get("parent1").equals(explanation.get("parent1"));
      boolean reversed =
          binding.get("parent0").equals(explanation.get("parent1"))
              && binding.get("parent1").equals(explanation.get("parent0"));
      if (!same && !reversed) continue;
      boolean enforced =
          explanation.get("beeMutation").getAsBoolean()
              && explanation.get("setting").getAsString().equals("requirements");
      int line = 0;
      for (JsonElement text : explanation.getAsJsonArray("lines")) {
        String prefix =
            row.facts.locale().equals("zh_CN")
                ? (enforced ? "需要满足原生条件：" : "原生条件说明（当前覆盖／根类型不执行此条件）：")
                : (enforced
                    ? "Required native condition: "
                    : "Native condition text (not enforced by this override/root): ");
        row.note(
            "gendustry:mutation." + explanation.get("index").getAsInt() + "." + line++,
            explanation.get("class").getAsString()
                + "#getSpecialConditions;root="
                + explanation.get("root").getAsString()
                + ";result="
                + explanation.get("result").getAsString(),
            prefix + text.getAsString());
      }
    }
  }

  static JsonObject labwareConsumption(double chance) {
    if (!Double.isFinite(chance)) throw fault("Non-finite native Gendustry threshold");
    int successes = Math.max(0, Math.min(100, (int) Math.ceil(chance)));
    return successes == 0
        ? object("kind", "keep")
        : successes == 100
            ? object("kind", "consume")
            : object(
                "kind",
                "chance",
                "chance",
                com.github.dcysteine.nesql.exporter.source.Chance.of(successes, 100));
  }

  private JsonObject snapshot(GendustrySpecimens specimens) {
    Object cfg = module("machines." + machine.toLowerCase(Locale.ROOT) + ".Machine" + machine);
    JsonObject config = object();
    // Explicit nulls mean not applicable. Keep the same serialized key set as the typed contract.
    for (String field :
        new String[] {
          "labwareConsumeChance",
          "deathChanceNatural",
          "deathChanceArtificial",
          "mutagenPerItem",
          "tankSize",
          "secretChance",
          "degradeChanceNatural",
          "dnaPerItem",
          "proteinPerItem",
          "dnaTankSize",
          "proteinTankSize",
          "makePristineBees"
        }) config.add(field, JsonNull.INSTANCE);
    List<String> keys =
        new ArrayList<>(
            Arrays.asList(
                "mjPerItem",
                "powerUseRate",
                "activationEnergy",
                "maxStoredEnergy",
                "maxReceivedEnergy"));
    if (!machine.equals("Replicator")) keys.add("labwareConsumeChance");
    if (machine.equals("Imprinter"))
      keys.addAll(Arrays.asList("deathChanceNatural", "deathChanceArtificial"));
    if (machine.equals("Mutatron"))
      keys.addAll(
          Arrays.asList(
              "mutagenPerItem",
              "tankSize",
              "secretChance",
              "degradeChanceNatural",
              "deathChanceArtificial"));
    if (machine.equals("Replicator"))
      keys.addAll(
          Arrays.asList(
              "dnaPerItem",
              "proteinPerItem",
              "dnaTankSize",
              "proteinTankSize",
              "makePristineBees"));
    if (machine.equals("Extractor")) keys.add("tankSize");
    for (String key : keys) {
      Object n = GendustryConfiguration.value(cfg,key);
      if (n instanceof Boolean) config.addProperty(key, (Boolean) n);
      else if (n instanceof Number && Double.isFinite(((Number) n).doubleValue()))
        config.addProperty(key, (Number) n);
      else throw fault("Invalid native Gendustry configuration: " + key);
    }
    JsonArray explanations = array();
    JsonObject result =
        object(
            "config",
            config,
            "roots",
            roots(machine.equals("Mutatron") ? explanations : null,specimens),
            "sources",
            sourceTable==null?null:sourceTable.facts(),
            "wasteRegistry",
            machine.equals("Imprinter") || machine.equals("Mutatron") ? wasteRegistry() : null);
    result.add("conditionNotes", explanations);
    if (machine.equals("Sampler")) {
      ItemStack nativeSample = new ItemStack((net.minecraft.item.Item) module("items.GeneSample"));
      String registry =
          net.minecraft.item.Item.itemRegistry.getNameForObject(nativeSample.getItem());
      if (registry == null) throw fault("Native GeneSample is unregistered");
      result.add(
          "samplerPolicy",
          object(
              "kind",
              "sampler",
              "sampleRegistry",
              registry,
              "sampleMeta",
              net.minecraft.init.Items.feather.getDamage(nativeSample)));
    }
    if (CanonicalJson.bytes(result).length > 900000)
      throw fault("Gendustry context exceeds row budget");
    return result;
  }

  private void check() {
    Jobs.checkpoint();
    if (closed)
      throw new Jobs.Fault("capture_closed", "Native Gendustry genetics reader is closed");
  }

  public void close() {
    if (closed) return;
    closed = true;
    if(sourceTable!=null){sourceTable.close();sourceTable=null;}
    if (rootBindings != null) {
      rootBindings.close();
      rootBindings = null;
    }
    if (catalogue != null) {
      catalogue.clear();
      catalogue.dnaSources = null;
      catalogue = null;
    }
    if(samplerSource!=null){samplerSource.close();samplerSource=null;}
    if(specimens!=null){specimens.close();specimens=null;}
    catalogueSignature=null;
    context = null;
    signature = null;
    anchors = null;
  }

  private static Jobs.Fault changed() {
    return new Jobs.Fault(
        "environment_changed", "Gendustry native sources or configuration changed");
  }

  static JsonArray roots() {
    try(GendustryRootBindings bindings=new GendustryRootBindings();GendustrySpecimens specimens=new GendustrySpecimens(bindings)){
      return roots(null,specimens);
    }
  }

  static JsonArray roots(JsonArray explanations,GendustrySpecimens specimens) {
    JsonArray rows = array();
    for (String uid : new TreeSet<>(specimens.roots.keySet())) {
      GendustrySpecimens.Root definition=specimens.roots.get(uid);ISpeciesRoot root=definition.nativeRoot;
      JsonArray chromosomes = array(), required = array(), templates = array(), mutations = array();
      for (IChromosomeType gene : root.getKaryotype())
        chromosomes.add(object("ordinal", gene.ordinal(), "name", gene.getName()));
      Object template = module("items.GeneTemplate");
      for (Object n :
          sequence(
              MagicApi.invoke(
                  template.getClass(),
                  template,
                  "getRequiredChromosomes",
                  new Class<?>[] {ISpeciesRoot.class},
                  root))) required.add(value(((Number) n).intValue()));
      for (String species : new TreeSet<>(definition.templates.keySet()))
        templates.add(
            object("species", species, "alleles", alleles(definition.templates.get(species))));
      int mutationIndex = 0;
      for (IMutation mutation : definition.mutations) {
        GendustryRootBindings.admitMutation(mutation);
        IAllele[] mutationTemplate=mutation.getTemplate();
        for(IAllele allele:mutationTemplate)specimens.bind(root,allele);
        specimens.bind(root,mutation.getAllele0());specimens.bind(root,mutation.getAllele1());
        String species = mutationTemplate[root.getKaryotypeKey().ordinal()].getUID();
        Object setting = specimens.setting(species);
        if (explanations != null) {
          JsonArray lines = array();
          for (String text : mutationDescriptions(mutation)) {
              Jobs.checkpoint();
              if (text == null || lines.size() == 128 || text.length() > 16384)
                throw fault("Native mutation condition text exceeds budget or is absent");
              lines.add(value(text));
            }
          explanations.add(
              object(
                  "root",
                  uid,
                  "index",
                  mutationIndex,
                  "class",
                  mutation.getClass().getName(),
                  "parent0",
                  mutation.getAllele0().getUID(),
                  "parent1",
                  mutation.getAllele1().getUID(),
                  "result",
                  species,
                  "setting",
                  setting.toString().toLowerCase(Locale.ROOT),
                  "beeMutation",
                  mutation instanceof forestry.api.apiculture.IBeeMutation,
                  "lines",
                  lines));
        }
        mutationIndex++;
        mutations.add(
            object(
                "class",
                mutation.getClass().getName(),
                "parent0",
                mutation.getAllele0().getUID(),
                "parent1",
                mutation.getAllele1().getUID(),
                "template",
                alleles(mutationTemplate),
                "secret",
                mutation.isSecret(),
                "conditions",
                array(),
                "setting",
                setting.toString().toLowerCase(java.util.Locale.ROOT),
                "beeMutation",
                mutation instanceof forestry.api.apiculture.IBeeMutation));
      }
      String rootKind =
          root instanceof forestry.api.apiculture.IBeeRoot
              ? "bee"
              : root instanceof forestry.api.arboriculture.ITreeRoot
                  ? "tree"
                  : root instanceof forestry.api.lepidopterology.IButterflyRoot
                      ? "butterfly"
                      : "other";
      JsonArray forms = array();
      List<ItemStack> formSamples=specimens.defaultForms(root);int formIndex=0;
      for (int form : GendustryGeneticsCatalog.forms(root)) {
        ItemStack stack = formSamples.get(formIndex++);
        if (stack == null) throw fault("Native genetics member form is unavailable");
        forms.add(
            object(
                "ordinal",
                form,
                "registry",
                net.minecraft.item.Item.itemRegistry.getNameForObject(stack.getItem()),
                "meta",
                net.minecraft.init.Items.feather.getDamage(stack),
                "nbt",
                com.github.dcysteine.nesql.exporter.source.TypedNbt.encode(
                    stack.getTagCompound())));
      }
      rows.add(
          object(
              "uid",
              uid,
              "class",
              root.getClass().getName(),
              "kind",
              rootKind,
              "speciesChromosome",
              root.getKaryotypeKey().ordinal(),
              "lifespan",
              lifespan(root,specimens),
              "forms",
              forms,
              "chromosomes",
              chromosomes,
              "required",
              required,
              "defaultTemplate",
              alleles(definition.defaults),
              "templates",
              templates,
              "mutations",
              mutations));
    }
    return rows;
  }

  private static List<String> mutationDescriptions(IMutation mutation) {
    Class<?> producer;
    try { producer=mutation.getClass().getMethod("getSpecialConditions").getDeclaringClass(); }
    catch (NoSuchMethodException missing) { throw fault("Missing native mutation explanation producer"); }
    if(producer.getName().equals("net.bdew.gendustry.custom.BeeMutation")) {
      if(mutation.getClass()!=type(producer.getName()))throw fault("Unadapted Gendustry mutation explanation producer");
      MagicApi.version("GendustryGeneticsRecipes","gendustry");MagicApi.version("GendustryGeneticsRecipes","bdlib");
      Object none=field(type("scala.None$"),null,"MODULE$");Class<?> some=type("scala.Some");
      for(String name:new String[]{"reqTemperature","reqHumidity","reqBiome","reqBlock","reqBlockMeta"}) {
        Object option=field(producer,mutation,name);
        if(option!=none&&(option==null||option.getClass()!=some))throw fault("Unadapted native mutation explanation option");
      }
    }
    return descriptionValues(mutation,mutation.getSpecialConditions());
  }

  private static List<String> descriptionValues(IMutation mutation,Collection<?> descriptions) {
    List<String> values=new ArrayList<>();if(descriptions==null)return values;
    String owner=descriptions.getClass().getName();
    if(owner.equals("scala.collection.convert.Wrappers$IterableWrapper")) {
      if(mutation.getClass()!=type("net.bdew.gendustry.custom.BeeMutation")||descriptions.getClass()!=type(owner))
        throw fault("Unadapted native mutation explanation wrapper producer");
      Object current=field(type(owner),descriptions,"underlying"),nil=field(type("scala.collection.immutable.Nil$"),null,"MODULE$");
      Class<?> node=type("scala.collection.immutable.$colon$colon");
      Set<Object> seen=Collections.newSetFromMap(new IdentityHashMap<Object,Boolean>());
      while(current!=nil) {
        Jobs.checkpoint();
        if(current==null||current.getClass()!=node||!seen.add(current))throw fault("Unadapted native mutation explanation list tail");
        descriptionText(values,field(node,current,"head"));
        current=field(node,current,"tl");
      }
      return values;
    }
    if(!Arrays.asList("java.util.ArrayList","java.util.LinkedList","java.util.Arrays$ArrayList",
        "java.util.Collections$EmptyList","java.util.Collections$SingletonList").contains(owner)
        ||descriptions.getClass()!=type(owner))throw fault("Unadapted native mutation explanation storage");
    for(Object text:descriptions){Jobs.checkpoint();descriptionText(values,text);}
    return values;
  }

  private static void descriptionText(List<String> values,Object text) {
    if(!(text instanceof String)||values.size()==128||((String)text).length()>16384)
      throw fault("Native mutation condition text exceeds budget or is absent");
    values.add((String)text);
  }

  private static JsonElement lifespan(ISpeciesRoot root,GendustrySpecimens specimens) {
    IChromosomeType gene =
        root instanceof forestry.api.apiculture.IBeeRoot
            ? forestry.api.apiculture.EnumBeeChromosome.LIFESPAN
            : root instanceof forestry.api.lepidopterology.IButterflyRoot
                ? forestry.api.lepidopterology.EnumButterflyChromosome.LIFESPAN
                : null;
    if (gene == null) return JsonNull.INSTANCE;
    Collection<IAllele> registered = AlleleManager.alleleRegistry.getRegisteredAlleles(gene);
    if (registered == null || registered.size() > 65536)
      throw fault("Native lifespan allele registry unavailable");
    Map<String, Integer> ordered = new TreeMap<>();
    for (IAllele allele : registered) {
      Jobs.checkpoint();
      specimens.bind(root,allele);
      if (!(allele instanceof IAlleleInteger)
          || allele.getUID() == null
          || allele.getUID().isEmpty()) throw fault("Unknown native lifespan allele");
      int lifespan = ((IAlleleInteger) allele).getValue();
      Integer old = ordered.put(allele.getUID(), lifespan);
      if (old != null && old != lifespan) throw fault("Conflicting native lifespan allele values");
    }
    JsonObject values = object();
    for (Map.Entry<String, Integer> entry : ordered.entrySet())
      values.addProperty(entry.getKey(), entry.getValue());
    return object("chromosome", gene.ordinal(), "values", values);
  }

  private static JsonArray alleles(IAllele[] genes) {
    JsonArray rows = array();
    for (IAllele gene : genes) rows.add(value(gene == null ? null : gene.getUID()));
    return rows;
  }

  static FluidStack nativeFluid(String name, int amount) {
    Fluid fluid = (Fluid) call(module("config.Fluids"), name);
    if (fluid == null || amount <= 0) throw fault("Invalid native Gendustry fluid " + name);
    return new FluidStack(fluid, amount);
  }

  private static String wasteRegistry() {
    ItemStack waste =
        new ItemStack((net.minecraft.item.Item) call(module("config.Items"), "waste"));
    String registry =
        (String) net.minecraft.item.Item.itemRegistry.getNameForObject(waste.getItem());
    if (registry == null) throw fault("Native Gendustry waste registry unavailable");
    return registry;
  }

  static Object module(String name) {
    return MagicApi.field(type("net.bdew.gendustry." + name + "$"), null, "MODULE$");
  }

  static List<Object> sequence(Object value) {
    Object iterator = call(value, "iterator");
    List<Object> rows = new ArrayList<>();
    while ((Boolean) call(iterator, "hasNext")) {
      if (rows.size() >= 65536) throw fault("Gendustry registry exceeds budget");
      rows.add(call(iterator, "next"));
    }
    return rows;
  }

  static JsonElement owned(JsonElement value) {
    return value == null ? JsonNull.INSTANCE : new JsonParser().parse(value.toString());
  }

  static Object call(Object o, String n) {
    return MagicApi.invoke(o.getClass(), o, n, new Class<?>[0]);
  }

  private static Jobs.Fault fault(String m) {
    return new Jobs.Fault("recipe_unsupported", m);
  }
}
