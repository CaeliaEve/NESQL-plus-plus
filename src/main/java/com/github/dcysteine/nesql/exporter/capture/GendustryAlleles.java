package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import forestry.api.genetics.*;
import java.lang.reflect.Method;
import java.util.*;

/** Only the installed mods' pure allele accessors may run during owned specimen creation. */
final class GendustryAlleles implements AutoCloseable {
  private static final Set<String> BASIC=new HashSet<>(Arrays.asList(
      "forestry.core.genetics.alleles.Allele","magicbees.bees.Allele","net.bdew.gendustry.custom.BeeSpecies",
      "binnie.botany.genetics.AlleleColor","binnie.botany.genetics.AlleleEffectNone","binnie.botany.genetics.FlowerSpecies",
      "binnie.extrabees.genetics.ExtraBeesFlowers","binnie.extrabees.genetics.effect.ExtraBeesEffect",
      "binnie.extratrees.genetics.ButterflySpecies","binnie.extratrees.genetics.ExtraTreeFruitGene","binnie.extratrees.genetics.ExtraTreeSpecies",
      "gregtech.common.bees.GTAlleleHelper$1","gregtech.loaders.misc.bees.GTFlowers"));
  private static final Set<String> SPECIES=new HashSet<>(Arrays.asList(
      "forestry.api.apiculture.IAlleleBeeSpecies","forestry.api.arboriculture.IAlleleTreeSpecies","forestry.api.lepidopterology.IAlleleButterflySpecies",
      "forestry.apiculture.genetics.alleles.AlleleBeeSpecies","forestry.arboriculture.genetics.alleles.AlleleTreeSpecies",
      "forestry.lepidopterology.genetics.AlleleButterflySpecies","binnie.botany.genetics.FlowerSpecies",
      "binnie.extratrees.genetics.ButterflySpecies","binnie.extratrees.genetics.ExtraTreeSpecies","net.bdew.gendustry.custom.BeeSpecies"));
  private static final Set<String> INTEGER=new HashSet<>(Arrays.asList(
      "forestry.core.genetics.alleles.AlleleInteger","binnie.botany.genetics.AlleleColor","gregtech.loaders.misc.GTBees$AlleleInteger"));
  private final IdentityHashMap<IAllele,State> observations=new IdentityHashMap<>();
  private final Set<Class<?>> owners=Collections.newSetFromMap(new IdentityHashMap<>());
  private final Locale locale=Locale.getDefault();
  private long bytes;
  private boolean closed;

  static void admit(IAllele allele){
    if(allele==null)return;Class<?> owner=allele.getClass();
    for(String method:new String[]{"getUID","isDominant"})require(owner,method,BASIC);
    String textOwner=method(owner,"toString").getDeclaringClass().getName();
    if(!BASIC.contains(textOwner)&&!textOwner.equals("java.lang.Object")&&!textOwner.equals("java.lang.Enum"))throw fault("Unadapted allele diagnostic text callback");
    try{
      for(Method method:new Method[]{owner.getMethod("hashCode"),owner.getMethod("equals",Object.class)})
        if(method.getDeclaringClass()!=Object.class&&method.getDeclaringClass()!=Enum.class)throw fault("Unadapted allele identity/hash callback");
    }catch(NoSuchMethodException error){throw fault("Missing allele identity methods");}
    if(allele instanceof Enum)try{
      if(owner.getMethod("toString").getDeclaringClass()!=Enum.class)throw fault("Unadapted allele enum name callback");
    }catch(NoSuchMethodException error){throw fault("Missing allele enum name method");}
    if(allele instanceof IAlleleSpecies){
      require(owner,"getRoot",SPECIES);
      String rootOwner=method(owner,"getRoot").getDeclaringClass().getName();
      if(rootOwner.equals("binnie.extratrees.genetics.ExtraTreeSpecies")||rootOwner.equals("binnie.extratrees.genetics.ButterflySpecies")){
        Object manager=field(type("binnie.Binnie"),null,"Genetics");
        if(manager==null||manager.getClass()!=type("binnie.core.genetics.ManagerGenetics"))throw fault("Unadapted Binnie root accessor context");
        GendustryRootBindings.admitRegistry();
      }
    }
    if(allele instanceof IAlleleInteger)require(owner,"getValue",INTEGER);
    if(allele instanceof IAllelePlantType)require(owner,"getPlantTypes",Collections.singleton("forestry.core.genetics.alleles.AllelePlantType"));
    if(allele instanceof forestry.api.arboriculture.IAlleleTreeSpecies)require(owner,"getPlantType",new HashSet<>(Arrays.asList(
        "forestry.arboriculture.genetics.alleles.AlleleTreeSpecies","binnie.extratrees.genetics.ExtraTreeSpecies")));
  }
  void bind(IAllele allele){
    alive();if(allele==null||observations.containsKey(allele))return;
    if(!owners.contains(allele.getClass())){admit(allele);owners.add(allele.getClass());}
    if(observations.size()>=262144)throw new Jobs.Fault("recipe_limit","Gendustry allele observation count exceeds budget");
    State state=read(allele);bytes+=state.uid.length()*2L;
    if(bytes>32L*1024*1024)throw new Jobs.Fault("recipe_limit","Gendustry allele text exceeds cumulative budget");
    observations.put(allele,state);
  }
  void finishVerification(){
    alive();if(!locale.equals(Locale.getDefault()))throw changed();
    for(Map.Entry<IAllele,State> entry:observations.entrySet()){
      State a=entry.getValue(),b=read(entry.getKey());
      if(!a.uid.equals(b.uid)||a.dominant!=b.dominant||a.root!=b.root||!Objects.equals(a.value,b.value)
          ||a.plants!=b.plants||!Objects.equals(a.plantValues,b.plantValues)||a.nativePlant!=b.nativePlant)throw changed();
    }
  }
  private static State read(IAllele allele){
    Jobs.checkpoint();String uid=allele.getUID();
    if(uid==null||uid.isEmpty()||uid.length()>16384)throw fault("Native allele UID is missing or exceeds budget");
    ISpeciesRoot root=allele instanceof IAlleleSpecies?((IAlleleSpecies)allele).getRoot():null;
    if(allele instanceof IAlleleSpecies)GendustryRootBindings.admit(root);
    EnumSet<?> plants=null;
    if(allele instanceof IAllelePlantType){plants=((IAllelePlantType)allele).getPlantTypes();
      if(plants==null||!Arrays.asList("java.util.RegularEnumSet","java.util.JumboEnumSet").contains(plants.getClass().getName()))throw fault("Unknown native plant-type storage");}
    Object nativePlant=allele instanceof forestry.api.arboriculture.IAlleleTreeSpecies?((forestry.api.arboriculture.IAlleleTreeSpecies)allele).getPlantType():null;
    return new State(uid,allele.isDominant(),root,allele instanceof IAlleleInteger?((IAlleleInteger)allele).getValue():null,plants,nativePlant);
  }
  private static Method method(Class<?> owner,String name){try{return owner.getMethod(name);}catch(NoSuchMethodException error){throw fault("Missing native allele method: "+name);}}
  private static void require(Class<?> owner,String name,Set<String> allowed){
    Method method=method(owner,name);Class<?> declaration=method.getDeclaringClass();
    if(!allowed.contains(declaration.getName())||declaration!=type(declaration.getName()))throw fault("Unadapted allele callback: "+declaration.getName()+"#"+name);
  }
  private void alive(){Jobs.checkpoint();if(closed)throw new Jobs.Fault("capture_closed","Gendustry allele bindings are closed");}
  public void close(){closed=true;observations.clear();owners.clear();}
  private static final class State{
    final String uid;final boolean dominant;final ISpeciesRoot root;final Integer value;final EnumSet<?> plants,plantValues;final Object nativePlant;
    State(String uid,boolean dominant,ISpeciesRoot root,Integer value,EnumSet<?> plants,Object nativePlant){this.uid=uid;this.dominant=dominant;this.root=root;this.value=value;this.plants=plants;this.plantValues=plants==null?null:plants.clone();this.nativePlant=nativePlant;}
  }
  private static Jobs.Fault fault(String text){return new Jobs.Fault("recipe_unsupported",text);}
  private static Jobs.Fault changed(){return new Jobs.Fault("environment_changed","Gendustry allele UID, dominance, root or numeric value changed");}
}
