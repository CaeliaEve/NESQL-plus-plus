package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import forestry.api.genetics.*;
import java.util.*;

/** Native sampler cache identities and ordered associations; no default function is invoked. */
final class GendustrySamplerSource implements AutoCloseable {
  final List<Row> rows=new ArrayList<>();
  private final GendustrySpecimens specimens;
  private final List<Object> identities=new ArrayList<>();
  private Object cache,samples,associations,storage,defaultFunction;
  private boolean closed;
  GendustrySamplerSource(GendustrySpecimens specimens){
    this.specimens=specimens;
    try{
      cache=GendustryGeneticsRecipes.module("misc.GeneticsCache");samples=field(cache,"geneSamples");associations=field(cache,"speciesChromosomes");storage=NativeScala.storage(associations);defaultFunction=defaultFunction(associations);
      rows.addAll(read(identities));
    }catch(RuntimeException|Error failure){close();throw failure;}
  }
  private List<Row> read(List<Object> observed){
    IdentityHashMap<ISpeciesRoot,Map<Integer,IdentityHashMap<IAllele,List<IAlleleSpecies>>>> byRoot=new IdentityHashMap<>();
    for(Object tuple:NativeScala.values(storage,65536)){
      Object info=NativeScala.pair(tuple,1),source=NativeScala.pair(tuple,2);Info binding=info(info,observed);pin(observed,source);
      List<IAlleleSpecies> species=new ArrayList<>();
      for(Object raw:NativeScala.values(source,65536)){
        if(!(raw instanceof IAlleleSpecies))throw fault("Native sampler association has no species binding");
        IAlleleSpecies allele=(IAlleleSpecies)raw;specimens.bind(binding.root,allele);pin(observed,allele);species.add(allele);
      }
      byRoot.computeIfAbsent(binding.root,key->new HashMap<>()).computeIfAbsent(binding.chromosome,key->new IdentityHashMap<>()).put(binding.allele,species);
    }
    List<Row> result=new ArrayList<>();
    for(Object info:NativeScala.values(samples,65536)){
      Info binding=info(info,observed);Map<Integer,IdentityHashMap<IAllele,List<IAlleleSpecies>>> chromosomes=byRoot.get(binding.root);
      List<IAlleleSpecies> species=chromosomes==null||chromosomes.get(binding.chromosome)==null?Collections.emptyList():chromosomes.get(binding.chromosome).getOrDefault(binding.allele,Collections.emptyList());
      result.add(new Row(info,binding.root,binding.chromosome,binding.allele,species));
    }
    return result;
  }
  private Info info(Object value,List<Object> observed){
    if(value==null||value.getClass()!=type("net.bdew.gendustry.forestry.GeneSampleInfo"))throw fault("Unadapted native gene sample binding owner");
    Object root=field(value,"root"),allele=field(value,"allele"),chromosome=field(value,"chromosome");
    if(!(root instanceof ISpeciesRoot)||!(allele instanceof IAllele)||!(chromosome instanceof Integer)||(Integer)chromosome<0)throw fault("Invalid native gene sample binding");
    specimens.bind((ISpeciesRoot)root,(IAllele)allele);pin(observed,value);pin(observed,root);pin(observed,chromosome);pin(observed,allele);
    return new Info((ISpeciesRoot)root,(Integer)chromosome,(IAllele)allele);
  }
  private static void pin(List<Object> target,Object value){
    Jobs.checkpoint();if(target.size()>=262144)throw new Jobs.Fault("recipe_limit","Native sampler source identity budget exceeded");target.add(value);
  }
  void verify(){
    Jobs.checkpoint();if(closed)throw new Jobs.Fault("capture_closed","Gendustry sampler source is closed");
    if(cache!=GendustryGeneticsRecipes.module("misc.GeneticsCache")||samples!=field(cache,"geneSamples")
        ||associations!=field(cache,"speciesChromosomes")||storage!=NativeScala.storage(associations)||defaultFunction!=defaultFunction(associations))throw changed();
  }
  void finishVerification(){
    verify();List<Object> current=new ArrayList<>();read(current);
    if(current.size()!=identities.size())throw changed();
    for(int i=0;i<current.size();i++){
      Object a=identities.get(i),b=current.get(i);
      if(a instanceof Integer&&b instanceof Integer?!a.equals(b):a!=b)throw changed();
    }
  }
  private static Object defaultFunction(Object associations){
    if(associations==null||associations.getClass()!=type("scala.collection.mutable.Map$WithDefault"))throw fault("Unknown sampler association default-map owner");
    Object function=field(associations,"d");
    if(function==null||function.getClass()!=type("net.bdew.gendustry.misc.GeneticsCache$$anonfun$3"))throw fault("Unadapted sampler association default callback");
    // This pinned stateless body returns an empty SortedSet; invoking it would
    // unnecessarily allocate a set and initialize its ordering context.
    return function;
  }
  public void close(){closed=true;rows.clear();identities.clear();cache=null;samples=null;associations=null;storage=null;defaultFunction=null;}
  private static final class Info{
    final ISpeciesRoot root;final int chromosome;final IAllele allele;
    Info(ISpeciesRoot root,int chromosome,IAllele allele){this.root=root;this.chromosome=chromosome;this.allele=allele;}
  }
  static final class Row{
    final Object nativeInfo;final ISpeciesRoot root;final int chromosome;final IAllele allele;final List<IAlleleSpecies> species;
    Row(Object nativeInfo,ISpeciesRoot root,int chromosome,IAllele allele,List<IAlleleSpecies> species){this.nativeInfo=nativeInfo;this.root=root;this.chromosome=chromosome;this.allele=allele;this.species=new ArrayList<>(species);}
  }
  private static Jobs.Fault fault(String text){return new Jobs.Fault("recipe_unsupported",text);}
  private static Jobs.Fault changed(){return new Jobs.Fault("environment_changed","Gendustry native sampler cache, association or gene binding changed");}
}
