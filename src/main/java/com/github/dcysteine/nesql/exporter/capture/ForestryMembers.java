package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import forestry.api.genetics.*;
import java.lang.reflect.Field;
import java.util.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

/** Native factories and NBT readers may mutate owned members, never registered plant-type sets. */
final class ForestryMembers {
  private ForestryMembers(){}
  static IAllele[] ownedTemplate(IAllele[] source){
    if(source==null||source.length==0||source.length>256)throw fault("Invalid native genotype specimen");
    IAllele[] genes=source.clone();
    IdentityHashMap<IAllele,IAllele> copies=new IdentityHashMap<>();
    for(int i=0;i<genes.length;i++){
      Jobs.checkpoint();genes[i]=ownedAllele(genes[i],copies);
    }
    return genes;
  }
  static IIndividual create(ISpeciesRoot root,IAllele[] genes){
    GendustryRootBindings.admit(root);IIndividual member=root.templateAsIndividual(ownedTemplate(genes));
    String expected=root instanceof forestry.api.apiculture.IBeeRoot?"forestry.apiculture.genetics.Bee"
        :root instanceof forestry.api.arboriculture.ITreeRoot?"forestry.arboriculture.genetics.Tree"
        :root instanceof forestry.api.lepidopterology.IButterflyRoot?"forestry.lepidopterology.genetics.Butterfly":"binnie.botany.genetics.Flower";
    if(member==null||member.getClass()!=type(expected))throw fault("Unadapted native individual factory result");return member;
  }

  static ISpeciesRoot root(ItemStack source){
    if(source==null)return null;
    NativeStackAudit.audit(source);GendustryRootBindings.admitRegistry();
    Map<?,?> roots=storage(field(AlleleManager.alleleRegistry,"rootMap"),65536);
    ItemStack owned=source.copy();
    // The native registry selects the first matching root in insertion order.
    for(Object value:roots.values()){
      Jobs.checkpoint();
      if(!(value instanceof ISpeciesRoot))throw fault("Invalid native species-root entry");
      ISpeciesRoot root=(ISpeciesRoot)value;GendustryRootBindings.admit(root);
      if(root.isMember(owned))return root;
    }
    return null;
  }

  static IIndividual read(ItemStack source){
    ISpeciesRoot root=root(source);return root==null?null:read(root,source);
  }

  static IIndividual read(ISpeciesRoot root,ItemStack source){
    GendustryRootBindings.admit(root);
    if(source==null)return null;
    NativeStackAudit.audit(source);ItemStack owned=source.copy();
    if(root instanceof forestry.api.apiculture.IBeeRoot)return bee(root,owned);
    if(!(root instanceof forestry.api.arboriculture.ITreeRoot))return root.getMember(owned);
    if(!root.isMember(owned)||owned.getTagCompound()==null)return null;
    if(root!=forestry.api.arboriculture.TreeManager.treeRoot)throw fault("Native tree genome root differs from the selected root");
    NBTTagCompound tag=owned.getTagCompound();
    IGenome genome;
    if(tag.hasKey("Genome"))genome=treeGenome(root,tag.getCompoundTag("Genome"));
    else {
      Object oak=field(type("forestry.arboriculture.genetics.TreeDefinition"),null,"Oak");
      Object nativeGenome=field(oak,"genome");
      if(nativeGenome==null||nativeGenome.getClass()!=type("forestry.arboriculture.genetics.TreeGenome"))
        throw new Jobs.Fault("handler_not_ready","Native default tree genome is unavailable");
      NBTTagCompound serialized=new NBTTagCompound();
      admitChromosomes((IGenome)nativeGenome);
      ((IGenome)nativeGenome).writeToNBT(serialized);
      genome=treeGenome(root,serialized);
    }
    IGenome mate=tag.hasKey("Mate")?treeGenome(root,tag.getCompoundTag("Mate")):null;
    return fromOwnedTreeGenomes(genome,mate,tag.getBoolean("IsAnalyzed"));
  }

  /** BeeGenome.fromNBT writes a process-global LoadingCache. Use its exact native
   * NBT constructor on owned tags instead; scalar member state still goes through
   * Bee's native reader. No recipe/cache or borrowed-NBT mutation is required. */
  private static IIndividual bee(ISpeciesRoot root,ItemStack owned){
    MagicApi.version("ForestryMembers","Forestry");
    if(!root.isMember(owned))return null;
    if(root!=forestry.api.apiculture.BeeManager.beeRoot)
      throw fault("Native bee genome root differs from the selected root");
    NBTTagCompound tag=owned.getTagCompound();
    NBTTagCompound state=tag==null?null:(NBTTagCompound)tag.copy();
    if(state!=null){state.removeTag("Genome");state.removeTag("Mate");}
    IIndividual member=(IIndividual)TinkerRecipes.construct(type("forestry.apiculture.genetics.Bee"),
        new Class<?>[]{NBTTagCompound.class},state);
    if(tag!=null&&tag.hasKey("Genome"))set(member,"genome",nativeGenome(root,tag.getCompoundTag("Genome"),true));
    if(tag!=null&&tag.hasKey("Mate"))set(member,"mate",nativeGenome(root,tag.getCompoundTag("Mate"),true));
    return member;
  }

  /** These genome objects came from owned NBT; replace their arrays before Tree.setGenome runs. */
  static IIndividual fromOwnedTreeGenomes(IGenome genome,IGenome mate,boolean analyzed){
    IdentityHashMap<IAllele,IAllele> copies=new IdentityHashMap<>();
    isolate(genome,copies);if(mate!=null)isolate(mate,copies);
    IIndividual tree=(IIndividual)TinkerRecipes.construct(type("forestry.arboriculture.genetics.Tree"),
        new Class<?>[]{forestry.api.arboriculture.ITreeGenome.class},genome);
    if(mate!=null)set(tree,"mate",mate);
    if(analyzed)tree.analyze();
    return tree;
  }

  private static IGenome treeGenome(ISpeciesRoot root,NBTTagCompound tag){
    return nativeGenome(root,tag,false);
  }

  private static IGenome nativeGenome(ISpeciesRoot root,NBTTagCompound tag,boolean bee){
    GendustryRootBindings.admitRegistry();
    admitTemplate(root.getDefaultTemplate());
    Map<?,?> alleles=storage(field(AlleleManager.alleleRegistry,"alleleMap"),262144),
        deprecated=storage(field(AlleleManager.alleleRegistry,"deprecatedAlleleMap"),262144);
    Map<String,IAllele[]> templates=GendustryRootBindings.templates(root);
    NBTTagList chromosomes=tag.getTagList("Chromosomes",10);
    if(chromosomes.tagCount()>65536)throw new Jobs.Fault("recipe_limit","Native genome NBT exceeds chromosome budget");
    for(int i=0;i<chromosomes.tagCount();i++){
      Jobs.checkpoint();NBTTagCompound chromosome=chromosomes.getCompoundTagAt(i);
      int slot=chromosome.getByte("Slot");
      if(slot<0||slot>=root.getDefaultTemplate().length)continue;
      for(String key:new String[]{"UID0","UID1"}){
        String uid=chromosome.getString(key);Object raw=alleles.get(uid);
        if(raw==null)raw=deprecated.get(uid);
        if(raw==null)continue; // Native repair/error behavior remains in the owned genome constructor.
        if(!(raw instanceof IAllele))throw fault("Invalid native allele registry entry");
        IAllele allele=(IAllele)raw;GendustryAlleles.admit(allele);
        if(allele instanceof IAlleleSpecies){
          IAllele[] template=templates.get(allele.getUID());if(template!=null)admitTemplate(template);
        }
      }
    }
    if(!bee)return (IGenome)TinkerRecipes.construct(type("forestry.arboriculture.genetics.TreeGenome"),
        new Class<?>[]{NBTTagCompound.class},tag);
    try{
      java.lang.reflect.Constructor<?> constructor=type("forestry.apiculture.genetics.BeeGenome")
          .getDeclaredConstructor(NBTTagCompound.class);
      constructor.setAccessible(true);return (IGenome)constructor.newInstance(tag);
    }catch(java.lang.reflect.InvocationTargetException failure){
      Throwable cause=failure.getCause();if(cause instanceof Error)throw (Error)cause;
      if(cause instanceof RuntimeException)throw (RuntimeException)cause;
      throw fault("Native owned bee genome constructor failed: "+cause);
    }catch(ReflectiveOperationException failure){throw fault("Missing native owned bee genome constructor: "+failure);}
  }

  private static void isolate(IGenome genome,IdentityHashMap<IAllele,IAllele> copies){
    IChromosome[] source=admitChromosomes(genome),owned=source.clone();
    for(int i=0;i<owned.length;i++){
      Jobs.checkpoint();IChromosome chromosome=source[i];if(chromosome==null)continue;
      owned[i]=(IChromosome)TinkerRecipes.construct(type("forestry.core.genetics.Chromosome"),
          new Class<?>[]{IAllele.class,IAllele.class},
          ownedAllele(chromosome.getPrimaryAllele(),copies),ownedAllele(chromosome.getSecondaryAllele(),copies));
    }
    set(genome,"chromosomes",owned);
  }

  private static IChromosome[] admitChromosomes(IGenome genome){
    if(genome==null||genome.getClass()!=type("forestry.arboriculture.genetics.TreeGenome"))throw fault("Unadapted native tree genome owner");
    Object raw=field(genome,"chromosomes");
    if(!(raw instanceof IChromosome[])||((IChromosome[])raw).length>256)throw fault("Invalid native tree chromosome array");
    IChromosome[] chromosomes=(IChromosome[])raw;
    for(IChromosome chromosome:chromosomes){
      Jobs.checkpoint();if(chromosome==null)continue;
      if(chromosome.getClass()!=type("forestry.core.genetics.Chromosome"))throw fault("Unadapted native chromosome callback owner");
      GendustryAlleles.admit(chromosome.getPrimaryAllele());GendustryAlleles.admit(chromosome.getSecondaryAllele());
    }
    return chromosomes;
  }

  private static IAllele ownedAllele(IAllele allele,IdentityHashMap<IAllele,IAllele> copies){
    GendustryAlleles.admit(allele);if(!(allele instanceof IAllelePlantType))return allele;
    IAllele cached=copies.get(allele);if(cached!=null)return cached;
    EnumSet<?> types=((IAllelePlantType)allele).getPlantTypes();
    if(types==null||!Arrays.asList("java.util.RegularEnumSet","java.util.JumboEnumSet").contains(types.getClass().getName()))throw fault("Unknown native plant-type storage");
    Object copy=ownedPlant(types,allele.isDominant());
    Class<?> base=type("forestry.core.genetics.alleles.Allele");
    for(String name:new String[]{"uid","unlocalizedName"})set(base,copy,name,field(base,allele,name));
    copies.put(allele,(IAllele)copy);return (IAllele)copy;
  }
  private static Object ownedPlant(EnumSet<?> types,boolean dominant){
    try{
      java.lang.reflect.Constructor<?> constructor=type("forestry.core.genetics.alleles.AllelePlantType")
          .getDeclaredConstructor(String.class,EnumSet.class,boolean.class);
      constructor.setAccessible(true);return constructor.newInstance("ownedSpecimen",types.clone(),dominant);
    }catch(java.lang.reflect.InvocationTargetException failure){
      Throwable cause=failure.getCause();if(cause instanceof Error)throw (Error)cause;if(cause instanceof RuntimeException)throw (RuntimeException)cause;
      throw fault("Owned native plant allele constructor failed: "+cause);
    }catch(ReflectiveOperationException failure){throw fault("Cannot construct the pinned native plant allele: "+failure);}
  }

  private static void admitTemplate(IAllele[] template){
    if(template==null||template.length==0||template.length>256)throw fault("Native tree default template is unavailable");
    for(IAllele allele:template){Jobs.checkpoint();GendustryAlleles.admit(allele);}
  }

  private static Map<?,?> storage(Object raw,int limit){
    if(raw==null)throw new Jobs.Fault("handler_not_ready","Native genetic registry storage is unavailable");
    if(raw.getClass()!=HashMap.class&&raw.getClass()!=LinkedHashMap.class)throw fault("Unadapted native genetic registry storage");
    Map<?,?> map=(Map<?,?>)raw;if(map.size()>limit)throw new Jobs.Fault("recipe_limit","Native genetic registry exceeds budget");return map;
  }

  private static void set(Object target,String name,Object value){set(target.getClass(),target,name,value);}
  private static void set(Class<?> owner,Object target,String name,Object value){
    for(Class<?> current=owner;current!=null&&current!=Object.class;current=current.getSuperclass())try{
      Field field=current.getDeclaredField(name);field.setAccessible(true);field.set(target,value);return;
    }catch(NoSuchFieldException ignored){}catch(ReflectiveOperationException error){throw fault("Cannot write owned native member field: "+name);}
    throw fault("Missing owned native member field: "+name);
  }
  private static Jobs.Fault fault(String text){return new Jobs.Fault("recipe_unsupported",text);}
}
