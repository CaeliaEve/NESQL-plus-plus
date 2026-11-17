package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import forestry.api.apiculture.*;
import forestry.api.genetics.*;
import java.util.*;
import net.minecraft.item.Item;

/** Owned fixture tables with the installed Forestry registry/root/allele/member implementations. */
final class GendustryNativeSource implements AutoCloseable {
  private final NativeRegistryScope scope=new NativeRegistryScope();
  final IBeeRoot root;
  final IAllele[] defaults;
  GendustryNativeSource() throws Exception {
    try{
      Class<?> registryType=type("forestry.core.genetics.alleles.AlleleRegistry");
      scope.replace(AlleleManager.class,null,"alleleRegistry",registryType.newInstance());
      root=(IBeeRoot)type("forestry.apiculture.genetics.BeeHelper").newInstance();
      scope.replace(BeeManager.class,null,"beeRoot",root);
      scope.replace(BeeManager.class,null,"beeFactory",type("forestry.apiculture.genetics.BeeFactory").newInstance());
      // Initialize the native enum normally, then bind its existing default-template field.
      Class<?> definitionType=type("forestry.apiculture.genetics.BeeDefinition");
      Object forest=field(definitionType,null,"FOREST");
      IAlleleRegistry registry=(IAlleleRegistry)registryType.newInstance();
      scope.replace(AlleleManager.class,null,"alleleRegistry",registry);
      registry.registerSpeciesRoot(root);
      defaults=new IAllele[EnumBeeChromosome.values().length];
      for(EnumBeeChromosome chromosome:EnumBeeChromosome.values()){
        IAllele allele=allele(chromosome);
        defaults[chromosome.ordinal()]=allele;if(allele!=null)registry.registerAllele(allele,chromosome);
      }
      scope.replace(definitionType,forest,"template",defaults);
      Map<String,IAllele[]> templates=root.getGenomeTemplates();
      for(String name:new String[]{"parentA","parentB","result"}){
        IAllele[] genes=defaults.clone();
        IAllele species=species("fixture.gendustry."+name);genes[EnumBeeChromosome.SPECIES.ordinal()]=species;
        registry.registerAllele(species,EnumBeeChromosome.SPECIES);templates.put(species.getUID(),genes);
      }
      IAllele[] first=templates.get("fixture.gendustry.parentA");
      defaults[EnumBeeChromosome.SPECIES.ordinal()]=first[EnumBeeChromosome.SPECIES.ordinal()];
      scope.replace(type("forestry.apiculture.genetics.BeeHelper"),null,"beeMutations",new ArrayList<>());
      IBeeMutation mutation=(IBeeMutation)type("forestry.apiculture.genetics.BeeMutation")
          .getConstructor(IAlleleBeeSpecies.class,IAlleleBeeSpecies.class,IAllele[].class,int.class)
          .newInstance(first[0],templates.get("fixture.gendustry.parentB")[0],templates.get("fixture.gendustry.result"),7);
      root.registerMutation(mutation);
      Class<?> itemsType=type("forestry.apiculture.items.ItemRegistryApiculture");
      Object items=scope.allocate(itemsType);
      for(EnumBeeType form:new EnumBeeType[]{EnumBeeType.DRONE,EnumBeeType.PRINCESS,EnumBeeType.QUEEN,EnumBeeType.LARVAE}){
        Item item=(Item)type("forestry.apiculture.items.ItemBeeGE").getConstructor(EnumBeeType.class).newInstance(form);
        Item.itemRegistry.addObject(31960+form.ordinal(),"nesql_fixture:bee_"+form.name().toLowerCase(Locale.ROOT),item);
        String name=form==EnumBeeType.DRONE?"beeDroneGE":form==EnumBeeType.PRINCESS?"beePrincessGE":form==EnumBeeType.QUEEN?"beeQueenGE":"beeLarvaeGE";
        scope.replace(itemsType,items,name,item);
      }
      scope.replace(type("forestry.plugins.PluginApiculture"),null,"items",items);
    }catch(Exception|Error failure){try{scope.close();}catch(Exception|Error closing){failure.addSuppressed(closing);}throw failure;}
  }
  private IAllele allele(EnumBeeChromosome chromosome)throws Exception{
    // The pinned BeeBranchDefinition leaves the obsolete HUMIDITY chromosome empty.
    if(chromosome==EnumBeeChromosome.HUMIDITY)return null;
    String uid="fixture.gendustry."+chromosome.name().toLowerCase(Locale.ROOT),base="forestry.core.genetics.alleles.";
    Class<?> kind=chromosome.getAlleleClass();
    if(kind==IAlleleBeeSpecies.class)return species(uid);
    if(kind==IAlleleInteger.class)return (IAllele)type(base+"AlleleInteger").getConstructor(String.class,String.class,String.class,int.class,boolean.class)
        .newInstance("fixture","gendustry",chromosome.name(),chromosome==EnumBeeChromosome.LIFESPAN?47:1,true);
    if(kind==IAlleleFloat.class)return (IAllele)type(base+"AlleleFloat").getConstructor(String.class,String.class,String.class,float.class,boolean.class)
        .newInstance("fixture","gendustry",chromosome.name(),1F,true);
    if(kind==IAlleleBoolean.class)return (IAllele)type(base+"AlleleBoolean").getConstructor(String.class,String.class,boolean.class,boolean.class)
        .newInstance("fixture",chromosome.name(),false,true);
    if(kind==IAlleleTolerance.class)return (IAllele)type(base+"AlleleTolerance").getConstructor(String.class,String.class,String.class,EnumTolerance.class,boolean.class)
        .newInstance("fixture","gendustry",chromosome.name(),EnumTolerance.NONE,true);
    if(kind==IAlleleArea.class)return (IAllele)type(base+"AlleleArea").getConstructor(String.class,String.class,String.class,int[].class,boolean.class)
        .newInstance("fixture","gendustry",chromosome.name(),new int[]{1,1,1},true);
    if(kind==IAlleleFlowers.class)return (IAllele)type("forestry.apiculture.genetics.alleles.AlleleFlowers")
        .getConstructor(String.class,String.class,String.class,IFlowerProvider.class,boolean.class).newInstance("fixture","gendustry",chromosome.name(),null,true);
    if(kind==IAlleleBeeEffect.class)return (IAllele)type("forestry.apiculture.genetics.alleles.AlleleEffectNone").getConstructor(String.class,boolean.class).newInstance(uid,true);
    throw new AssertionError("Unassigned native bee chromosome "+chromosome+": "+kind);
  }
  private IAllele species(String uid)throws Exception{
    return (IAllele)type("forestry.apiculture.genetics.alleles.AlleleBeeSpecies")
        .getConstructor(String.class,String.class,String.class,String.class,boolean.class,IClassification.class,String.class,int.class,int.class)
        .newInstance(uid,uid,"fixture",uid,true,null,uid,0xffffff,0x333333);
  }
  @Override public void close()throws Exception{scope.close();}
}
