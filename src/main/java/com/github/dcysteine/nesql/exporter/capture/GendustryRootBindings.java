package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;

import com.github.dcysteine.nesql.exporter.task.Jobs;
import forestry.api.genetics.*;
import java.util.*;

/** Borrowed storage bindings: cheap root checks in batches and full identities when sealing. */
final class GendustryRootBindings implements AutoCloseable {
  private final List<Object> identities = new ArrayList<>();
  private final GendustryAlleles alleles=new GendustryAlleles();
  private boolean closed;
  private Object[] anchors;
  private int[] sizes;

  GendustryRootBindings() {
    try{anchors=anchors();sizes=new int[anchors.length];for(int i=0;i<anchors.length;i++)sizes[i]=count(anchors[i]);read(identities);}
    catch(RuntimeException|Error error){close();throw error;}
  }

  void verify() {
    if (closed) throw new Jobs.Fault("capture_closed", "Gendustry root bindings are closed");
    Object[] actual=anchors();if(actual.length!=anchors.length)throw changed();
    for(int i=0;i<anchors.length;i++)if(actual[i]!=anchors[i]||count(actual[i])!=sizes[i])throw changed();
  }
  void finishVerification(){
    verify();
    List<Object> actual = new ArrayList<>();
    read(actual);
    if (actual.size() != identities.size()) throw changed();
    for (int index = 0; index < actual.size(); index++)
      if (actual.get(index) != identities.get(index)) throw changed();
    alleles.finishVerification();
  }
  void bindAlleles(IAllele[] values){
    if(closed)throw new Jobs.Fault("capture_closed","Gendustry root bindings are closed");
    if(values==null||values.length>256)throw new Jobs.Fault("recipe_unsupported","Invalid native allele template");
    for(IAllele value:values)alleles.bind(value);
  }
  void bindAllele(IAllele value){if(closed)throw new Jobs.Fault("capture_closed","Gendustry root bindings are closed");alleles.bind(value);}

  private static void pin(List<Object> target, Object value) {
    Jobs.checkpoint();
    if (target.size() == 262144)
      throw new Jobs.Fault("recipe_limit", "Gendustry root identity budget exceeded");
    target.add(value);
  }

  private static void mapStorage(Map<?, ?> map, int limit) {
    if (map == null)
      throw new Jobs.Fault("handler_not_ready", "Gendustry native template storage is unavailable");
    Class<?> owner = map.getClass();
    if (owner != HashMap.class && owner != LinkedHashMap.class && owner != TreeMap.class)
      throw new Jobs.Fault(
          "recipe_unsupported", "Unadapted Gendustry map storage: " + owner.getName());
    if (owner == TreeMap.class && ((TreeMap<?, ?>) map).comparator() != null)
      throw new Jobs.Fault("recipe_unsupported", "Unadapted Gendustry map comparator");
    if (map.size() > limit)
      throw new Jobs.Fault("recipe_limit", "Gendustry native map exceeds budget");
  }

  private void read(List<Object> target) {
    admitRegistry();IAlleleRegistry registry = AlleleManager.alleleRegistry;
    pin(target, registry);
    Object rootsStorage=field(registry,"rootMap"),alleleStorage=field(registry,"alleleMap");
    mapStorage((Map<?,?>)rootsStorage,65536);mapStorage((Map<?,?>)alleleStorage,262144);
    pin(target,rootsStorage);pin(target,alleleStorage);pin(target,field(registry,"blacklist"));
    Map<String, ISpeciesRoot> roots = GendustryGeneticsCatalog.rootsMap();
    for (String uid : new TreeSet<>(roots.keySet())) {
      ISpeciesRoot root = roots.get(uid);
      if (root == null) throw changed();
      admit(root);
      pin(target, root);
      Map<String, IAllele[]> templates = templates(root);
      pin(target, templates);
      for (String species : new TreeSet<>(templates.keySet())) {
        IAllele[] alleles = templates.get(species);
        if (alleles == null || alleles.length > 256) throw changed();
        pin(target, alleles);
        for (IAllele allele : alleles){this.alleles.bind(allele);pin(target, allele);}
      }
      bindAlleles(root.getDefaultTemplate());
      List<? extends IMutation> mutations = mutations(root);pin(target,mutations);
      for (IMutation mutation : mutations) {
        if (mutation == null) throw changed();
        admitMutation(mutation);
        pin(target, mutation);
        IAllele parent0=mutation.getAllele0(),parent1=mutation.getAllele1();
        alleles.bind(parent0);alleles.bind(parent1);pin(target, parent0);pin(target, parent1);
        IAllele[] alleles = mutation.getTemplate();
        if (alleles == null || alleles.length > 256) throw changed();
        // A template getter can return a copy. Its allele identities are still source bindings.
        for (IAllele allele : alleles){this.alleles.bind(allele);pin(target, allele);}
      }
    }
    Map<String, IAllele> registered = registry.getRegisteredAlleles();
    if (registered != null) {
      if (registered.size() > 262144)
        throw new Jobs.Fault("recipe_limit", "Gendustry allele identity budget exceeded");
      for (String uid : new TreeSet<>(registered.keySet())){IAllele allele=registered.get(uid);alleles.bind(allele);pin(target, allele);}
    }
  }

  public void close() {
    closed = true;
    identities.clear();alleles.close();anchors=null;sizes=null;
  }

  static void admitRegistry(){
    Object registry=AlleleManager.alleleRegistry;
    if(registry==null)throw new Jobs.Fault("handler_not_ready","Gendustry allele registry is unavailable");
    if(registry.getClass()!=type("forestry.core.genetics.alleles.AlleleRegistry"))throw new Jobs.Fault("recipe_unsupported","Unadapted Gendustry allele registry callback owner: "+registry.getClass().getName());
  }
  static void admit(ISpeciesRoot root){
    if(root==null)throw new Jobs.Fault("handler_not_ready","Native species root is unavailable");
    if(!Arrays.asList("forestry.apiculture.genetics.BeeHelper","forestry.arboriculture.genetics.TreeHelper",
        "forestry.lepidopterology.genetics.ButterflyHelper","binnie.botany.genetics.FlowerHelper").contains(root.getClass().getName())||root.getClass()!=type(root.getClass().getName()))
      throw new Jobs.Fault("recipe_unsupported","Unadapted Gendustry native root callback owner: "+root.getClass().getName());
  }
  @SuppressWarnings("unchecked")
  static Map<String,IAllele[]> templates(ISpeciesRoot root){
    admit(root);Object raw=field(root,"speciesTemplates");mapStorage((Map<?,?>)raw,65536);return (Map<String,IAllele[]>)raw;
  }
  @SuppressWarnings("unchecked")
  static List<? extends IMutation> mutations(ISpeciesRoot root){
    admit(root);String owner=root.getClass().getName(),name=owner.contains("BeeHelper")?"beeMutations":owner.contains("TreeHelper")?"treeMutations":owner.contains("ButterflyHelper")?"butterflyMutations":"flowerMutations";
    Object raw=field(root.getClass(),null,name);
    if(raw==null)throw new Jobs.Fault("handler_not_ready","Gendustry mutation source is unavailable");
    if(raw.getClass()!=ArrayList.class)throw new Jobs.Fault("recipe_unsupported","Unadapted Gendustry mutation backing storage");
    if(((List<?>)raw).size()>65536)throw new Jobs.Fault("recipe_limit","Gendustry mutation source exceeds budget");return (List<? extends IMutation>)raw;
  }
  private static Object[] anchors(){
    admitRegistry();Object registry=AlleleManager.alleleRegistry;Object roots=field(registry,"rootMap"),alleles=field(registry,"alleleMap");
    mapStorage((Map<?,?>)roots,65536);mapStorage((Map<?,?>)alleles,262144);
    List<Object> values=new ArrayList<>(Arrays.asList(registry,roots,alleles,field(registry,"blacklist")));
    for(Object raw:((Map<?,?>)roots).values()){
      if(!(raw instanceof ISpeciesRoot))throw changed();ISpeciesRoot root=(ISpeciesRoot)raw;admit(root);values.add(root);
      Object templates=field(root,"speciesTemplates");mapStorage((Map<?,?>)templates,65536);values.add(templates);
      String name=root.getClass().getName(),mutations=name.contains("BeeHelper")?"beeMutations":name.contains("TreeHelper")?"treeMutations":name.contains("ButterflyHelper")?"butterflyMutations":"flowerMutations";
      Object mutationList=field(root.getClass(),null,mutations);if(mutationList==null||mutationList.getClass()!=ArrayList.class||((List<?>)mutationList).size()>65536)throw new Jobs.Fault("recipe_unsupported","Unadapted Gendustry mutation backing storage");values.add(mutationList);
    }
    return values.toArray();
  }
  private static int count(Object source){
    if(source==null)throw changed();Class<?> c=source.getClass();
    if(c==HashMap.class||c==LinkedHashMap.class)return ((Map<?,?>)source).size();
    if(c==ArrayList.class)return ((List<?>)source).size();return -1;
  }
  static void admitMutation(IMutation mutation){
    if(mutation==null)throw changed();
    Set<String> dataOwners=new HashSet<>(Arrays.asList("forestry.core.genetics.mutations.Mutation",
        "binnie.extratrees.genetics.ExtraTreeMutation","binnie.botany.genetics.FlowerMutation",
        "fox.spiteful.avaritia.compat.forestry.ExpensiveMutation","net.bdew.gendustry.custom.BeeMutation"));
    Set<String> rootOwners=new HashSet<>(Arrays.asList("forestry.apiculture.genetics.BeeMutation",
        "forestry.arboriculture.genetics.TreeMutation","forestry.lepidopterology.genetics.ButterflyMutation",
        "binnie.extratrees.genetics.ExtraTreeMutation","binnie.botany.genetics.FlowerMutation",
        "fox.spiteful.avaritia.compat.forestry.ExpensiveMutation","net.bdew.gendustry.custom.BeeMutation"));
    for(String method:new String[]{"getAllele0","getAllele1","getTemplate","isSecret","getSpecialConditions","getRoot"}){
      try{
        String owner=mutation.getClass().getMethod(method).getDeclaringClass().getName();
        if(!(method.equals("getRoot")?rootOwners:dataOwners).contains(owner)||mutation.getClass().getMethod(method).getDeclaringClass()!=type(owner))throw new Jobs.Fault("recipe_unsupported","Unadapted Gendustry mutation callback: "+owner+"#"+method);
      }catch(NoSuchMethodException error){throw new Jobs.Fault("recipe_unsupported","Missing native mutation callback: "+method);}
    }
  }

  private static Jobs.Fault changed() {
    return new Jobs.Fault(
        "environment_changed", "Gendustry native root/template/allele identity changed");
  }
}
