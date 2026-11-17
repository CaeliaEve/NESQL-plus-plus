package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.GendustryGeneticsRecipes.*;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.*;
import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import forestry.api.genetics.*;
import java.util.*;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;

/** Owned rule specimens from native genetics sources. No recipe GUI or display-cache callbacks. */
final class GendustryGeneticsCatalog {
  final List<Entry> entries = new ArrayList<>();
  private final NativeStackPool witnesses=new NativeStackPool();
  GendustrySourceTable dnaSources;
  private final GendustrySpecimens specimens;
  private final GendustrySamplerSource samplerSource;

  GendustryGeneticsCatalog(String machine,GendustrySourceTable sourceTable,GendustrySpecimens specimens,GendustrySamplerSource samplerSource) {
    this.specimens=specimens;this.samplerSource=samplerSource;
    dnaSources=sourceTable;
    switch (machine) {
      case "Imprinter":
        imprinter();
        break;
      case "Sampler":
        sampler();
        break;
      case "Transposer":
        transposer();
        break;
      case "Mutatron":
        mutatron();
        break;
      case "Replicator":
        replicator();
        break;
      case "Extractor":
        extractor();
        break;
      default:
        throw fault("Unknown native genetics machine");
    }
  }

  private void add(Entry entry) {
    Jobs.checkpoint();
    if (entries.size() == 65536) throw fault("Native Gendustry rule catalogue exceeds budget");
    entry.inputs.replaceAll((role,stack)->witnesses.intern(stack));
    entry.output=witnesses.intern(entry.output);
    entries.add(entry);
  }
  String signature(){
    JsonArray records=array();long bytes=0;NativeStackPool unique=new NativeStackPool();
    for(Entry entry:entries){
      Jobs.checkpoint();JsonObject inputs=object();
      for(Map.Entry<String,ItemStack> input:entry.inputs.entrySet())inputs.add(input.getKey(),value(unique.key(input.getValue())));
      JsonObject record=object("operation",entry.operation,"inputs",inputs,"output",unique.key(entry.output),
          "exclusion",entry.exclusion,"mutation",entry.mutationBinding,"dna",entry.dna,"observation",entry.observation);
      bytes+=CanonicalJson.bytes(record).length;
      if(bytes>32L*1024*1024)throw new Jobs.Fault("recipe_limit","Gendustry catalogue edges exceed budget");
      records.add(value(CanonicalJson.digest(record)));
    }
    return CanonicalJson.digest(records);
  }
  void clear(){entries.clear();witnesses.clear();}

  @SuppressWarnings("unchecked")
  static Map<String, ISpeciesRoot> rootsMap() {
    if (AlleleManager.alleleRegistry == null)
      throw new Jobs.Fault("handler_not_ready", "Gendustry allele registry is unavailable");
    GendustryRootBindings.admitRegistry();
    Map<String, ISpeciesRoot> roots = AlleleManager.alleleRegistry.getSpeciesRoot();
    if (roots == null)
      throw new Jobs.Fault("handler_not_ready", "Gendustry species roots are unavailable");
    if (roots.size() > 65536) throw fault("Native Gendustry root budget exceeded");
    for(ISpeciesRoot root:roots.values())GendustryRootBindings.admit(root);
    return roots;
  }

  private void imprinter() {
    for (ISpeciesRoot root : specimens.nativeRoots()) {
      IAllele[] genes = specimens.defaults(root);
      int chromosome = root.getKaryotypeKey().ordinal();
      ItemStack template = template(root, genes, false, chromosome);
      for (int form : forms(root)) {
        ItemStack input = member(root, genes, form);
        Entry entry = new Entry("imprint", input.copy());
        entry.inputs.put("individual", input);
        entry.inputs.put("template", template.copy());
        entry.inputs.put("labware", item("labware"));
        add(entry);
      }
    }
  }

  private void sampler() {
    if(samplerSource==null)throw fault("Native sampler cache binding is unavailable");
    for(GendustrySamplerSource.Row sample:samplerSource.rows){
      for(IAlleleSpecies allele:sample.species){
        ISpeciesRoot root=allele.getRoot();IAllele[] genes=specimens.template(root,allele.getUID());
        if(genes==null){
          Entry disposition=new Entry("sample",null);disposition.exclusion=object("reason","native_registered_species_has_no_genome_template","root",root.getUID(),"species",allele.getUID(),"nativeClass","net.bdew.gendustry.misc.GeneticsCache$");add(disposition);continue;
        }
        for(int form:forms(root)){
          Entry entry=new Entry("sample",sample(sample.nativeInfo));entry.inputs.put("individual",member(root,genes,form));
          entry.inputs.put("blank",item("geneSampleBlank"));entry.inputs.put("labware",item("labware"));add(entry);
        }
      }
      if(sample.species.isEmpty()){
        Entry disposition=new Entry("sample",null);disposition.exclusion=object("reason","native_sample_cache_has_no_species_binding","root",sample.root.getUID(),"chromosome",sample.chromosome,"allele",sample.allele.getUID(),"nativeClass","net.bdew.gendustry.misc.GeneticsCache$");add(disposition);
      }
    }
  }

  private void transposer() {
    // Native catalogue order lists sample copies before template copies.
    for (boolean whole : new boolean[] {false, true})
      for (ISpeciesRoot root : specimens.nativeRoots()) {
        IAllele[] genes = specimens.defaults(root);
        ItemStack original =
            whole ? template(root, genes, false, 0) : sample(info(root, 0, genes[0]));
        Entry entry = new Entry(whole ? "transposeTemplate" : "transposeSample", original.copy());
        entry.inputs.put("template", original);
        entry.inputs.put("blank", whole ? nativeItem("GeneTemplate") : item("geneSampleBlank"));
        entry.inputs.put("labware", item("labware"));
        entry.observation = true; // Owned copy/blank merge, independent of a world/RNG result.
        add(entry);
      }
  }

  private void mutatron() {
    for (ISpeciesRoot root : specimens.nativeRoots())
      for (IMutation mutation : specimens.root(root).mutations) {
        Jobs.checkpoint();
        if (mutation == null) throw fault("Null native mutation");
        GendustryRootBindings.admitMutation(mutation);
        ISpeciesRoot actualRoot = mutation.getRoot();
        IAllele[] result = mutation.getTemplate();
        if (actualRoot == null || result == null || actualRoot != root)
          throw fault("Native mutation has an invalid root/template");
        for(IAllele allele:result)specimens.bind(root,allele);
        specimens.bind(root,mutation.getAllele0());specimens.bind(root,mutation.getAllele1());
        int species = root.getKaryotypeKey().ordinal();
        if (species < 0 || species >= result.length || result[species] == null)
          throw fault("Native mutation has no result species");
        Object setting = specimens.setting(result[species].getUID());
        if (setting == null) throw fault("Native mutation override is unavailable");
        if (setting.toString().equals("DISABLED")) {
          Entry excluded = new Entry("mutate", null);
          excluded.exclusion =
              object(
                  "reason",
                  "native_mutation_disabled",
                  "code",
                  "native_disabled",
                  "nativeRecipe",
                  mutation.getClass().getName(),
                  "resultSpecies",
                  result[species].getUID(),
                  "setting",
                  "disabled");
          add(excluded);
          continue;
        }
        if (!setting.toString().equals("ENABLED") && !setting.toString().equals("REQUIREMENTS"))
          throw fault("Unadapted native mutation override");
        Entry entry =
            new Entry(
                "mutate",
                member(
                    root,
                    result,
                    root instanceof forestry.api.apiculture.IBeeRoot
                        ? forestry.api.apiculture.EnumBeeType.QUEEN.ordinal()
                        : 0));
        entry.mutationBinding =
            object(
                "root",
                root.getUID(),
                "parent0",
                mutation.getAllele0().getUID(),
                "parent1",
                mutation.getAllele1().getUID());
        entry.inputs.put(
            "parent0",
            member(
                root,
                requiredTemplate(root, mutation.getAllele0().getUID()),
                root instanceof forestry.api.apiculture.IBeeRoot
                    ? forestry.api.apiculture.EnumBeeType.PRINCESS.ordinal()
                    : 0));
        entry.inputs.put(
            "parent1",
            member(
                root,
                requiredTemplate(root, mutation.getAllele1().getUID()),
                root instanceof forestry.api.apiculture.IBeeRoot
                    ? forestry.api.apiculture.EnumBeeType.DRONE.ordinal()
                    : root instanceof forestry.api.arboriculture.ITreeRoot
                        ? forestry.api.arboriculture.EnumGermlingType.POLLEN.ordinal()
                        : 0));
        entry.inputs.put("labware", item("labware"));
        add(entry);
      }
  }

  private void replicator() {
    // The three stock previews retain their order; other registered species are also admitted.
    Map<String, IAlleleSpecies> species = new TreeMap<>();
    Map<String, IAllele> alleles = AlleleManager.alleleRegistry.getRegisteredAlleles();
    if (alleles == null || alleles.size() > 262144)
      throw fault("Native registered allele catalogue is unavailable or too large");
    for (IAllele allele : alleles.values())
      if (allele instanceof IAlleleSpecies){GendustryAlleles.admit(allele);species.put(allele.getUID(), (IAlleleSpecies) allele);}
    List<String> ordered = new ArrayList<>();
    for (String stock :
        new String[] {"forestry.speciesForest", "forestry.treeOak", "forestry.lepiCabbageWhite"})
      if (species.containsKey(stock)) ordered.add(stock);
    for (String uid : species.keySet()) if (!ordered.contains(uid)) ordered.add(uid);
    for (String uid : ordered) {
      ISpeciesRoot root = species.get(uid).getRoot();
      IAllele[] genes = specimens.template(root,uid);
      if (genes == null){
        Entry disposition=new Entry("replicate",null);disposition.exclusion=object("reason","native_registered_species_has_no_genome_template","root",root.getUID(),"species",uid,"nativeClass","net.bdew.gendustry.misc.GeneticsCache$");add(disposition);continue;
      }
      ItemStack offered = template(root, genes, true, 0);
      // Work on a clone even for a provider that returns a shared default array.
      IAllele[] selected = specimens.defaults(root);
      for (int index = 0; index < genes.length; index++)
        if (genes[index] != null) selected[index] = genes[index];
      ItemStack output=specimens.replica(root,selected,root instanceof forestry.api.apiculture.IBeeRoot?2:0,
          (Boolean)GendustryConfiguration.value(module("machines.replicator.MachineReplicator"),"makePristineBees"));
      Entry entry = new Entry("replicate", output);
      entry.inputs.put("template", offered);
      add(entry);
    }
  }

  private void extractor() {
    if(dnaSources==null)throw fault("Native DNA source table is unavailable");
    for (GendustrySourceTable.Entry source : dnaSources.entries) {
      Entry entry = new Entry("extract", null);
      entry.dna = source.amount;
      entry.dnaSource = source;
      entry.inputs.put("individual", source.specimen());
      entry.inputs.put("labware", item("labware"));
      if (source.amount <= 0)
        entry.exclusion =
            object(
                "reason",
                "native_input_slot_rejected",
                "nativeRecipe",
                "net.bdew.gendustry.machines.extractor.TileExtractor",
                "registry",
                Item.itemRegistry.getNameForObject(source.item),
                "metadata",
                source.meta,
                "nativeValue",
                source.amount,
                "condition",
                "LiquidDNASources.getValue(stack) > 0");
      add(entry);
    }
  }

  static IAllele[] defaults(ISpeciesRoot root) {
    if (root == null) throw fault("Missing native species root");
    IAllele[] genes = root.getDefaultTemplate();
    if (genes == null || genes.length == 0 || genes.length > 256 || genes[0] == null)
      throw fault("Native species default template is unavailable");
    return genes.clone();
  }

  private IAllele[] requiredTemplate(ISpeciesRoot root, String uid) {
    IAllele[] genes = specimens.template(root,uid);
    if (genes == null) throw fault("Native mutation parent template is unavailable: " + uid);
    return genes;
  }

  static int[] forms(ISpeciesRoot root) {
    // Only forms the native Forestry factories can construct. Enum sentinels are not members.
    if (root instanceof forestry.api.apiculture.IBeeRoot)
      return new int[] {
        forestry.api.apiculture.EnumBeeType.DRONE.ordinal(),
            forestry.api.apiculture.EnumBeeType.PRINCESS.ordinal(),
        forestry.api.apiculture.EnumBeeType.QUEEN.ordinal(),
            forestry.api.apiculture.EnumBeeType.LARVAE.ordinal()
      };
    if (root instanceof forestry.api.arboriculture.ITreeRoot)
      return new int[] {
        forestry.api.arboriculture.EnumGermlingType.SAPLING.ordinal(),
        forestry.api.arboriculture.EnumGermlingType.POLLEN.ordinal()
      };
    if (root instanceof forestry.api.lepidopterology.IButterflyRoot)
      return new int[] {
        forestry.api.lepidopterology.EnumFlutterType.BUTTERFLY.ordinal(),
        forestry.api.lepidopterology.EnumFlutterType.SERUM.ordinal(),
        forestry.api.lepidopterology.EnumFlutterType.CATERPILLAR.ordinal()
      };
    if(root.getClass().getName().equals("binnie.botany.genetics.FlowerHelper")){
      Class<?> stage=type("binnie.botany.api.EnumFlowerStage");
      int[] forms=new int[3];int i=0;for(String name:new String[]{"FLOWER","SEED","POLLEN"})forms[i++]=((Enum<?>)field(stage,null,name)).ordinal();return forms;
    }
    throw fault("Unadapted native genetics member forms: "+root.getClass().getName());
  }

  private ItemStack member(ISpeciesRoot root, IAllele[] genes, int form) {
    if (genes == null || genes.length == 0 || genes.length > 256)
      throw fault("Invalid native genotype specimen");
    return specimens.member(root,genes,form);
  }

  private static ItemStack owned(ItemStack stack) {
    NativeStackAudit.audit(stack);
    if (stack == null || stack.stackSize <= 0) throw fault("Native genetics specimen is absent");
    return stack.copy();
  }

  private static ItemStack item(String role) {
    Object value = call(module("config.Items"), role);
    if (!(value instanceof Item) || Item.itemRegistry.getNameForObject(value) == null)
      throw new Jobs.Fault("handler_not_ready", "Gendustry item role is unavailable: " + role);
    return new ItemStack((Item) value);
  }

  private static ItemStack nativeItem(String name) {
    Object value = module("items." + name);
    if (!(value instanceof Item) || Item.itemRegistry.getNameForObject(value) == null)
      throw new Jobs.Fault("handler_not_ready", "Gendustry gene item is unavailable: " + name);
    return new ItemStack((Item) value);
  }

  private static Object info(ISpeciesRoot root, int chromosome, IAllele allele) {
    if (allele == null) throw fault("Missing native sample allele");
    try {
      return type("net.bdew.gendustry.forestry.GeneSampleInfo")
          .getConstructor(ISpeciesRoot.class, int.class, IAllele.class)
          .newInstance(root, chromosome, allele);
    } catch (ReflectiveOperationException failure) {
      throw fault("Native gene sample constructor unavailable: " + failure);
    }
  }

  private static ItemStack sample(Object info) {
    Object item = module("items.GeneSample");
    return owned(
        (ItemStack)
            invoke(item.getClass(), item, "newStack", new Class<?>[] {info.getClass()}, info));
  }

  private static ItemStack template(
      ISpeciesRoot root, IAllele[] genes, boolean whole, int chromosome) {
    ItemStack stack = nativeItem("GeneTemplate");
    NBTTagCompound tags = new NBTTagCompound();
    tags.setString("species", root.getUID());
    NBTTagList samples = new NBTTagList();
    for (int index = 0; index < genes.length; index++)
      if (genes[index] != null && (whole || index == chromosome)) {
        NBTTagCompound sample = new NBTTagCompound();
        sample.setString("species", root.getUID());
        sample.setInteger("chromosome", index);
        sample.setString("allele", genes[index].getUID());
        samples.appendTag(sample);
      }
    if (samples.tagCount() == 0) throw fault("Native template specimen has no usable gene");
    tags.setTag("samples", samples);
    stack.setTagCompound(tags);
    return stack;
  }

  static final class Entry {
    final String operation;
    final Map<String, ItemStack> inputs = new LinkedHashMap<>();
    ItemStack output;
    JsonObject exclusion;
    JsonObject mutationBinding;
    GendustrySourceTable.Entry dnaSource;
    int dna;
    boolean observation;

    Entry(String operation, ItemStack output) {
      this.operation = operation;
      this.output = output;
    }
  }

  private static Jobs.Fault fault(String text) {
    return new Jobs.Fault("recipe_unsupported", text);
  }
}
