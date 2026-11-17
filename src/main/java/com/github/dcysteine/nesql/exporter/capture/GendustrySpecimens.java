package com.github.dcysteine.nesql.exporter.capture;

import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import forestry.api.genetics.*;
import java.util.*;
import net.minecraft.item.ItemStack;

/** One reader-owned root context, shared by rule metadata and catalogue specimen generation. */
final class GendustrySpecimens implements AutoCloseable {
  final Map<String,Root> roots=new LinkedHashMap<>();
  private final IdentityHashMap<ISpeciesRoot,Root> byRoot=new IdentityHashMap<>();
  private final List<ISpeciesRoot> nativeOrder=new ArrayList<>();
  private final Map<Key,ItemStack> members=new HashMap<>();
  private final GendustryRootBindings bindings;
  private GendustryMutationSettings mutationSettings;
  private long bytes;
  private int alleleCount,stackCount;
  private boolean closed;
  GendustrySpecimens(GendustryRootBindings bindings){
    this.bindings=bindings;
    try{for(Map.Entry<String,ISpeciesRoot> entry:GendustryGeneticsCatalog.rootsMap().entrySet()){
      Root value=new Root(entry.getValue());roots.put(entry.getKey(),value);byRoot.put(value.nativeRoot,value);nativeOrder.add(value.nativeRoot);
    }}catch(RuntimeException|Error failure){close();throw failure;}
  }
  Root root(ISpeciesRoot source){alive();Root result=byRoot.get(source);if(result==null)throw fault("Native species root is outside the bound catalogue");return result;}
  List<ISpeciesRoot> nativeRoots(){alive();return nativeOrder;}
  void bind(ISpeciesRoot root,IAllele allele){root(root);bindings.bindAllele(allele);}
  Object setting(String uid){alive();if(mutationSettings==null)mutationSettings=new GendustryMutationSettings();return mutationSettings.setting(uid);}
  void verify(){alive();if(mutationSettings!=null)mutationSettings.verify();}
  void finishVerification(){verify();if(mutationSettings!=null)mutationSettings.finishVerification();}
  IAllele[] defaults(ISpeciesRoot root){return root(root).defaults.clone();}
  IAllele[] template(ISpeciesRoot root,String uid){IAllele[] genes=root(root).templates.get(uid);return genes==null?null:genes.clone();}
  ItemStack member(ISpeciesRoot root,IAllele[] genes,int form){return member(root,genes,form,null);}
  ItemStack replica(ISpeciesRoot root,IAllele[] genes,int form,boolean pristine){return member(root,genes,form,pristine);}
  private ItemStack member(ISpeciesRoot root,IAllele[] genes,int form,Boolean pristine){
    alive();root(root);bindings.bindAlleles(genes);Key key=new Key(root,genes,form,pristine);ItemStack cached=members.get(key);
    if(cached==null){
      IIndividual individual=ForestryMembers.create(root,genes);individual.analyze();
      if(pristine!=null&&individual instanceof forestry.api.apiculture.IBee){
        forestry.api.apiculture.IBee bee=(forestry.api.apiculture.IBee)individual;bee.setIsNatural(pristine);bee.mate(bee);
      }
      cached=retain(root.getMemberStack(individual,form));members.put(key,cached);
    }
    return cached.copy();
  }
  List<ItemStack> defaultForms(ISpeciesRoot root){
    Root context=root(root);
    if(context.forms==null){
      context.forms=new ArrayList<>();IIndividual member=ForestryMembers.create(root,context.defaults);
      // The native queen conversion may mate this owned individual. Preserve
      // the original form-group order; don't reuse it for independent previews.
      for(int form:GendustryGeneticsCatalog.forms(root))context.forms.add(retain(root.getMemberStack(member,form)));
    }
    List<ItemStack> copies=new ArrayList<>();for(ItemStack stack:context.forms)copies.add(stack.copy());return copies;
  }
  private IAllele[] genes(IAllele[] source){
    if(source==null||source.length==0||source.length>256)throw fault("Native genome template is unavailable");
    bindings.bindAlleles(source);alleleCount+=source.length;
    if(alleleCount>262144)throw new Jobs.Fault("recipe_limit","Gendustry shared genotype context exceeds budget");return source.clone();
  }
  private ItemStack retain(ItemStack source){
    NativeStackAudit.audit(source);if(source.stackSize<=0)throw fault("Native genetics member specimen is absent");
    bytes+=CanonicalJson.bytes(TypedNbt.encode(source.getTagCompound())).length;
    if(++stackCount>65536||bytes>32L*1024*1024)throw new Jobs.Fault("recipe_limit","Gendustry cumulative member specimens exceed budget");return source.copy();
  }
  private void alive(){Jobs.checkpoint();if(closed)throw new Jobs.Fault("capture_closed","Gendustry specimen context is closed");}
  public void close(){closed=true;if(mutationSettings!=null)mutationSettings.close();mutationSettings=null;roots.clear();byRoot.clear();members.clear();nativeOrder.clear();}
  final class Root{
    final ISpeciesRoot nativeRoot;final IAllele[] defaults;final Map<String,IAllele[]> templates=new LinkedHashMap<>();
    final List<IMutation> mutations=new ArrayList<>();private List<ItemStack> forms;
    Root(ISpeciesRoot root){
      nativeRoot=root;GendustryRootBindings.admit(root);defaults=genes(root.getDefaultTemplate());
      for(Map.Entry<String,IAllele[]> entry:GendustryRootBindings.templates(root).entrySet())templates.put(entry.getKey(),genes(entry.getValue()));
      for(IMutation mutation:GendustryRootBindings.mutations(root)){
        GendustryRootBindings.admitMutation(mutation);bindings.bindAllele(mutation.getAllele0());bindings.bindAllele(mutation.getAllele1());
        bindings.bindAlleles(mutation.getTemplate());mutations.add(mutation);
        if(mutations.size()>65536)throw new Jobs.Fault("recipe_limit","Gendustry mutation context exceeds budget");
      }
    }
  }
  private static final class Key{
    final ISpeciesRoot root;final IAllele[] genes;final int form,hash;final Boolean pristine;
    Key(ISpeciesRoot root,IAllele[] genes,int form,Boolean pristine){this.root=root;this.genes=genes.clone();this.form=form;this.pristine=pristine;
      int hash=31*System.identityHashCode(root)+form;for(IAllele gene:genes)hash=31*hash+System.identityHashCode(gene);this.hash=31*hash+(pristine==null?0:pristine?1:2);}
    public int hashCode(){return hash;}
    public boolean equals(Object other){
      if(!(other instanceof Key))return false;Key key=(Key)other;
      if(root!=key.root||form!=key.form||!Objects.equals(pristine,key.pristine)||genes.length!=key.genes.length)return false;
      for(int i=0;i<genes.length;i++)if(genes[i]!=key.genes[i])return false;return true;
    }
  }
  private static Jobs.Fault fault(String text){return new Jobs.Fault("recipe_unsupported",text);}
}
