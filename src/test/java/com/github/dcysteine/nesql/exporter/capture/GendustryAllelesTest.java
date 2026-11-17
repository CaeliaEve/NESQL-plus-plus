package com.github.dcysteine.nesql.exporter.capture;

import forestry.api.genetics.*;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import java.lang.reflect.Proxy;

/** Callback rejection precedes invocation; scalar drift is checked at sealing. */
final class GendustryAllelesTest {
  static void run()throws Exception{
    int[] calls={0};IAllele unknown=(IAllele)Proxy.newProxyInstance(GendustryAllelesTest.class.getClassLoader(),new Class<?>[]{IAllele.class},
        (proxy,method,args)->{calls[0]++;throw new AssertionError("Unknown allele callback ran");});
    try(GendustryAlleles bindings=new GendustryAlleles()){
      try{bindings.bind(unknown);throw new AssertionError("Unknown allele was admitted");}
      catch(Jobs.Fault expected){if(!expected.code.equals("recipe_unsupported"))throw expected;}
      if(calls[0]!=0)throw new AssertionError("Admission called an unknown allele");
    }
    try(NativeRegistryScope scope=new NativeRegistryScope();GendustryAlleles bindings=new GendustryAlleles()){
      Class<?> base=Class.forName("forestry.core.genetics.alleles.Allele"),integer=Class.forName("forestry.core.genetics.alleles.AlleleInteger");
      IAllele allele=(IAllele)scope.allocate(integer);
      scope.replace(base,allele,"uid","fixture.gendustry.integer");
      scope.replace(integer,allele,"value",42);bindings.bind(allele);bindings.finishVerification();
      scope.replace(integer,allele,"value",43);
      try{bindings.finishVerification();throw new AssertionError("Same-identity allele value drift was accepted");}
      catch(Jobs.Fault expected){if(!expected.code.equals("environment_changed"))throw expected;}
    }
    try(NativeRegistryScope scope=new NativeRegistryScope();GendustryAlleles bindings=new GendustryAlleles()){
      Class<?> plant=Class.forName("forestry.core.genetics.alleles.AllelePlantType"),base=Class.forName("forestry.core.genetics.alleles.Allele");
      IAllelePlantType allele=(IAllelePlantType)scope.allocate(plant);
      scope.replace(base,allele,"uid","forestry.plantTypeFixture");scope.replace(base,allele,"unlocalizedName","fixture.plant");
      java.util.EnumSet<net.minecraftforge.common.EnumPlantType> types=java.util.EnumSet.of(net.minecraftforge.common.EnumPlantType.Plains);
      scope.replace(plant,allele,"types",types);bindings.bind(allele);
      IAllele[] copy=ForestryMembers.ownedTemplate(new IAllele[]{allele});
      ((IAllelePlantType)copy[0]).getPlantTypes().add(net.minecraftforge.common.EnumPlantType.Desert);
      if(types.size()!=1||!copy[0].getUID().equals(allele.getUID()))throw new AssertionError("Tree factory still aliases borrowed plant types");
      bindings.finishVerification();
    }
    treeOwnership();
  }

  private static void treeOwnership() throws Exception {
    try(NativeRegistryScope scope=new NativeRegistryScope()){
      Class<?> base=Class.forName("forestry.core.genetics.alleles.Allele"),
          plant=Class.forName("forestry.core.genetics.alleles.AllelePlantType"),
          species=Class.forName("forestry.arboriculture.genetics.alleles.AlleleTreeSpecies"),
          chromosome=Class.forName("forestry.core.genetics.Chromosome"),
          genome=Class.forName("forestry.arboriculture.genetics.TreeGenome");
      IAllele nativePlant=(IAllele)scope.allocate(plant),nativeSpecies=(IAllele)scope.allocate(species);
      java.util.EnumSet<net.minecraftforge.common.EnumPlantType> shared=java.util.EnumSet.of(net.minecraftforge.common.EnumPlantType.Plains);
      scope.replace(base,nativePlant,"uid","fixture.plant");scope.replace(base,nativeSpecies,"uid","fixture.tree");
      scope.replace(plant,nativePlant,"types",shared);
      scope.replace(species,nativeSpecies,"nativeType",net.minecraftforge.common.EnumPlantType.Desert);
      IChromosome[] borrowed=new IChromosome[forestry.api.arboriculture.EnumTreeChromosome.values().length];
      borrowed[forestry.api.arboriculture.EnumTreeChromosome.SPECIES.ordinal()]=(IChromosome)TinkerRecipes.construct(chromosome,new Class<?>[]{IAllele.class},nativeSpecies);
      borrowed[forestry.api.arboriculture.EnumTreeChromosome.PLANT.ordinal()]=(IChromosome)TinkerRecipes.construct(chromosome,new Class<?>[]{IAllele.class},nativePlant);
      IGenome main=(IGenome)scope.allocate(genome),mate=(IGenome)scope.allocate(genome);
      scope.replace(Class.forName("forestry.core.genetics.Genome"),main,"chromosomes",borrowed);
      scope.replace(Class.forName("forestry.core.genetics.Genome"),mate,"chromosomes",borrowed);
      IIndividual actual=ForestryMembers.fromOwnedTreeGenomes(main,mate,true);
      if(shared.size()!=1||main.getChromosomes()==borrowed||main.getChromosomes()[forestry.api.arboriculture.EnumTreeChromosome.PLANT.ordinal()]==borrowed[forestry.api.arboriculture.EnumTreeChromosome.PLANT.ordinal()])
        throw new AssertionError("Tree NBT path still writes or aliases the registered plant chromosome");
      net.minecraft.nbt.NBTTagCompound actualNbt=new net.minecraft.nbt.NBTTagCompound();actual.writeToNBT(actualNbt);
      IGenome oracle=(IGenome)scope.allocate(genome);
      scope.replace(Class.forName("forestry.core.genetics.Genome"),oracle,"chromosomes",borrowed);
      IIndividual expected=(IIndividual)TinkerRecipes.construct(Class.forName("forestry.arboriculture.genetics.Tree"),new Class<?>[]{forestry.api.arboriculture.ITreeGenome.class},oracle);
      expected.analyze();net.minecraft.nbt.NBTTagCompound expectedNbt=new net.minecraft.nbt.NBTTagCompound();expected.writeToNBT(expectedNbt);
      expectedNbt.setTag("Mate",expectedNbt.getCompoundTag("Genome").copy());
      if(!actualNbt.equals(expectedNbt))throw new AssertionError("Owned tree changed native genotype, mate or analyzed serialization");
      int[] calls={0};IChromosome foreign=(IChromosome)Proxy.newProxyInstance(GendustryAllelesTest.class.getClassLoader(),new Class<?>[]{IChromosome.class},(p,m,a)->{calls[0]++;throw new AssertionError("Foreign chromosome callback ran");});
      IGenome invalid=(IGenome)scope.allocate(genome);
      scope.replace(Class.forName("forestry.core.genetics.Genome"),invalid,"chromosomes",new IChromosome[]{foreign});
      try{ForestryMembers.fromOwnedTreeGenomes(invalid,null,false);throw new AssertionError("Foreign chromosome was admitted");}
      catch(Jobs.Fault expectedFault){if(!expectedFault.code.equals("recipe_unsupported"))throw expectedFault;}
      if(calls[0]!=0)throw new AssertionError("Foreign chromosome was invoked before admission");
    }
  }
}
