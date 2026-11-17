package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.*;
import forestry.api.apiculture.*;
import forestry.api.genetics.*;
import java.util.*;
import net.minecraft.item.Item;

/** Source bindings for the MagicBees pool callback. This is not callback admission:
 * BotaniaPoolRecipes must still reject it until its cross-project law is complete.
 * No BeeGenome cache reads/writes and no live matches/getOutput calls occur here. */
final class MagicBeesPoolSource implements AutoCloseable {
  private final GendustryAlleles alleles = new GendustryAlleles();
  private List<Object> identities;
  private JsonObject definition;
  private String signature;
  private Map<String,IAllele> sourceAlleles;
  private boolean closed;

  MagicBeesPoolSource() {
    try {
      identities = new ArrayList<>();
      definition = read(identities);
      signature = CanonicalJson.digest(definition);
    } catch (RuntimeException | Error failure) {
      close(); throw failure;
    }
  }

  JsonObject snapshot() {
    alive(); return new JsonParser().parse(definition.toString()).getAsJsonObject();
  }
  String formRegistry(String form){
    alive();JsonElement value=definition.getAsJsonObject("forms").get(form);
    if(value==null)throw unsupported("Unknown native bee form");
    return value.getAsString();
  }

  /** Describe a historical cache object without resolving its UID into a new object. */
  JsonObject cachedAllele(IAllele allele) {
    alive();if(allele==null)return null;alleles.bind(allele);
    return object("uid",allele.getUID(),"dominant",allele.isDominant(),
        "beeSpecies",allele instanceof IAlleleBeeSpecies,
        "sourceIdentity",sourceAlleles.get(allele.getUID())==allele);
  }

  void finishVerification() {
    alive();
    try {
      List<Object> current = new ArrayList<>();
      JsonObject actual = read(current);
      if (!signature.equals(CanonicalJson.digest(actual)) || current.size() != identities.size())
        throw changed();
      for (int i = 0; i < current.size(); i++) if (current.get(i) != identities.get(i)) throw changed();
      alleles.finishVerification();
    } catch (Jobs.Fault cause) {
      if (cause.code.equals("environment_changed")) throw cause;
      Jobs.Fault drift = changed(); drift.initCause(cause); throw drift;
    }
  }

  private JsonObject read(List<Object> pins) {
    MagicApi.version("MagicBeesPoolSource","Forestry"); MagicApi.version("MagicBeesPoolSource","MagicBees");
    GendustryRootBindings.admitRegistry();
    IAlleleRegistry registry = AlleleManager.alleleRegistry;
    IBeeRoot root = BeeManager.beeRoot;
    GendustryRootBindings.admit(root);
    if (root.getClass() != type("forestry.apiculture.genetics.BeeHelper")
        || field(type("magicbees.bees.BeeManager"), null, "beeRoot") != root)
      throw unsupported("MagicBees and Forestry select different bee roots");
    pin(pins, registry); pin(pins, root);
    Map<String,IAllele> registered = table(field(registry,"alleleMap"),262144);
    Map<String,IAllele> deprecated = table(field(registry,"deprecatedAlleleMap"),262144);
    Map<String,IAllele[]> templates = GendustryRootBindings.templates(root);
    pin(pins, registered); pin(pins, deprecated); pin(pins, templates);
    IChromosomeType[] karyotype = root.getKaryotype();
    if (!Arrays.equals(karyotype, EnumBeeChromosome.values()))
      throw unsupported("Unadapted native bee karyotype");
    JsonArray chromosomes = array();
    for (IChromosomeType chromosome : karyotype)
      chromosomes.add(value(chromosome.getAlleleClass().getName()));

    // UID equality is insufficient for native Object.equals. Refuse ambiguous
    // registrations rather than merging two distinct native allele identities.
    Map<String,IAllele> canonical = new HashMap<>();
    JsonArray values = array();
    for (String key : new TreeSet<>(registered.keySet())) {
      IAllele allele = registered.get(key);
      if(allele==null)throw unsupported("Null native allele registration");
      admit(allele,pins,canonical);
      JsonArray slots = array();
      for (IChromosomeType chromosome : karyotype)
        if (chromosome.getAlleleClass().isInstance(allele)) slots.add(value(chromosome.ordinal()));
      values.add(object("key",key,"uid",allele.getUID(),"dominant",allele.isDominant(),"slots",slots));
    }
    JsonObject aliases = object();
    for (String key : new TreeSet<>(deprecated.keySet())) {
      IAllele allele = deprecated.get(key);
      if(allele==null)throw unsupported("Null native allele alias");
      admit(allele,pins,canonical);
      aliases.addProperty(key,allele.getUID());
      // An alias-only allele still needs its native compatibility/dominance facts.
      if (!registered.containsValue(allele)) {
        JsonArray slots = array();
        for (IChromosomeType chromosome : karyotype)
          if (chromosome.getAlleleClass().isInstance(allele)) slots.add(value(chromosome.ordinal()));
        values.add(object("key",key,"uid",allele.getUID(),"dominant",allele.isDominant(),"slots",slots,"aliasOnly",true));
      }
    }
    JsonObject species = object();
    for (String key : new TreeSet<>(templates.keySet()))
      species.add(key,template(templates.get(key),karyotype.length,pins,canonical));
    Object forest = field(type("forestry.apiculture.genetics.BeeDefinition"),null,"FOREST");
    // BeeDefinition.getTemplate() returns an Arrays.copyOf on every call. Bind
    // native storage, not that short-lived getter result, while snapshotting its
    // exact allele contents just like the registered species templates.
    Object defaultTemplate = field(forest,"template");
    if (!(defaultTemplate instanceof IAllele[]))
      throw unsupported("Native FOREST template is unavailable");
    JsonArray defaults = template((IAllele[])defaultTemplate,karyotype.length,pins,canonical);
    Object genome = field(forest,"genome");
    if (genome == null) throw new Jobs.Fault("handler_not_ready","Native FOREST genome is unavailable");
    if (genome.getClass() != type("forestry.apiculture.genetics.BeeGenome"))
      throw unsupported("Unadapted default bee genome");
    pin(pins,forest); pin(pins,genome);
    Object raw = field(genome,"chromosomes"); pin(pins,raw);
    if (!(raw instanceof IChromosome[]) || ((IChromosome[])raw).length != karyotype.length)
      throw unsupported("Invalid default bee chromosomes");
    JsonArray forestGenes = array();
    for (IChromosome chromosome : (IChromosome[])raw) {
      pin(pins,chromosome);
      if (chromosome == null) {forestGenes.add(JsonNull.INSTANCE);continue;}
      if (chromosome.getClass() != type("forestry.core.genetics.Chromosome"))
        throw unsupported("Unadapted default chromosome owner");
      IAllele a=chromosome.getPrimaryAllele(),b=chromosome.getSecondaryAllele();
      admit(a,pins,canonical);admit(b,pins,canonical);
      forestGenes.add(object("primary",uid(a),"secondary",uid(b)));
    }
    Object items = field(type("forestry.plugins.PluginApiculture"),null,"items");
    if (items == null || items.getClass() != type("forestry.apiculture.items.ItemRegistryApiculture"))
      throw unsupported("Native bee item roles are unavailable");
    pin(pins,items); JsonObject forms=object();
    for (String name : new String[]{"beeDroneGE","beePrincessGE","beeQueenGE","beeLarvaeGE"}) {
      Object item=field(items,name);pin(pins,item);
      if (!(item instanceof Item) || Item.itemRegistry.getNameForObject(item)==null)
        throw unsupported("Unregistered native bee form");
      forms.addProperty(name,Item.itemRegistry.getNameForObject(item));
    }
    // Lookup membership and object descriptors are different: a template can
    // retain an object absent from both registry maps. Do not invent a lookup.
    JsonObject definitions=object();
    for(String uid:new TreeSet<>(canonical.keySet())){
      IAllele allele=canonical.get(uid);JsonArray slots=array();
      for(IChromosomeType chromosome:karyotype)
        if(chromosome.getAlleleClass().isInstance(allele))slots.add(value(chromosome.ordinal()));
      definitions.add(uid,object("uid",uid,"dominant",allele.isDominant(),"slots",slots));
    }
    JsonObject result=object("forestry","4.10.17","magicbees","2.9.4-GTNH",
        "root",root.getUID(),"chromosomes",chromosomes,"alleles",values,"aliases",aliases,
        "definitions",definitions,
        "templates",species,"defaults",defaults,"forest",forestGenes,"forms",forms,
        "clearInvalidChromosomes",field(type("forestry.core.config.Config"),null,"clearInvalidChromosomes"));
    if(CanonicalJson.bytes(result).length>16*1024*1024)
      throw new Jobs.Fault("recipe_limit","Native pool matching source exceeds 16 MiB");
    if(sourceAlleles==null)sourceAlleles=new HashMap<>(canonical);
    return result;
  }

  private JsonArray template(IAllele[] genes,int count,List<Object> pins,Map<String,IAllele> canonical) {
    if(genes==null||genes.length!=count)throw unsupported("Invalid native bee default/template length");
    pin(pins,genes);JsonArray result=array();
    for(IAllele allele:genes){admit(allele,pins,canonical);result.add(allele==null?JsonNull.INSTANCE:value(allele.getUID()));}
    return result;
  }
  private void admit(IAllele allele,List<Object> pins,Map<String,IAllele> canonical) {
    pin(pins,allele);if(allele==null)return;alleles.bind(allele);
    IAllele prior=canonical.putIfAbsent(allele.getUID(),allele);
    if(prior!=null&&prior!=allele)throw unsupported("Distinct native alleles share a canonical UID");
  }
  private static String uid(IAllele allele){return allele==null?null:allele.getUID();}
  @SuppressWarnings("unchecked") private static <T> Map<String,T> table(Object raw,int limit) {
    if(raw==null||raw.getClass()!=LinkedHashMap.class||((Map<?,?>)raw).size()>limit)
      throw unsupported("Unadapted native allele lookup storage");
    for(Object key:((Map<?,?>)raw).keySet())if(!(key instanceof String)||((String)key).length()>16384)
      throw unsupported("Invalid native allele lookup key");
    return (Map<String,T>)raw;
  }
  private static void pin(List<Object> pins,Object value){Jobs.checkpoint();if(pins.size()==262144)throw new Jobs.Fault("recipe_limit","Pool source identity budget exceeded");pins.add(value);}
  private void alive(){if(closed)throw new Jobs.Fault("capture_closed","MagicBees pool source is closed");}
  public void close(){closed=true;alleles.close();if(identities!=null)identities.clear();identities=null;sourceAlleles=null;definition=null;signature=null;}
  private static Jobs.Fault unsupported(String text){return new Jobs.Fault("recipe_unsupported",text);}
  private static Jobs.Fault changed(){return new Jobs.Fault("environment_changed","Native pool registry, defaults, forms or repair configuration changed");}
}
