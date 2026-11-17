package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.google.gson.*;
import java.lang.invoke.MethodHandle;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;
import thaumcraft.api.aspects.*;
import thaumcraft.api.crafting.InfusionRecipe;

/** Read actual ingredients without filling TC4Tweaks' recipe conversion cache. */
final class InfusionInputSource implements AutoCloseable {
  private static final String API="net.glease.tc4tweak.api.infusionrecipe.";
  static final String ENHANCED=API+"EnhancedInfusionRecipe";
  private static final String MODE="net.glease.tc4tweak.modules.infusionRecipe.InfusionOreDictMode";
  private static final String NETWORK="net.glease.tc4tweak.network.NetworkedConfiguration";
  private InfusionRecipe recipe;
  private final List<Object> predicates=new ArrayList<>();
  private final List<Node> parsed=new ArrayList<>();
  private final List<Object> deferredTargets=new ArrayList<>();
  private final Map<Object,ItemStack> deferredBindings=new IdentityHashMap<>();
  private final Map<String,String[]> shapes=new HashMap<>();
  private final Set<String> ores=new TreeSet<>();
  private final NativeItemExamples examples;
  private Object oreNames,oreStacks,mode;
  private NativeDefinitionGraph graph;
  private boolean closed;
  private boolean preparing=true;
  private boolean checking;
  private int visits;
  private String signature;

  InfusionInputSource(InfusionRecipe recipe,NativeItemExamples examples){
    this.recipe=recipe;this.examples=examples;
    try{
      // NEI conversion is not the execution contract of arbitrary dynamic subclasses.
      if(recipe.getClass()!=type(ENHANCED))throw fault("Dynamic infusion execution requires its dedicated reader: "+recipe.getClass().getName());
      owner(recipe,"getRecipeInput",InfusionRecipe.class);owner(recipe,"getComponents",InfusionRecipe.class);
      owner(recipe,"getResearch",InfusionRecipe.class);
      owner(recipe,"matches",type(ENHANCED),java.util.ArrayList.class,ItemStack.class,net.minecraft.world.World.class,net.minecraft.entity.player.EntityPlayer.class);
      for(String method:new String[]{"getRecipeOutput","getAspects","getInstability"})owner(recipe,method,InfusionRecipe.class,ItemStack.class);
      shapes.put(recipe.getClass().getName(),new String[0]);
      shapes.put(Aspect.class.getName(),new String[]{"tag","components"});shapes.put(AspectList.class.getName(),new String[]{"aspects"});
      // Native matching may fill oreID from -1 to the same registered name. Bind
      // the predicate/name/table, and validate that cache's meaning without treating filling as a new recipe.
      shapes.put(API+"RecipeIngredient$1",new String[]{"val$name"});shapes.put(API+"RecipeIngredient$2",new String[]{"val$name"});
      shapes.put(API+"RecipeIngredient$3",new String[]{"val$target","val$checkNBTTags"});shapes.put(API+"RecipeIngredient$4",new String[]{"val$targets","val$checkNBTTags"});
      shapes.put(API+"RecipeIngredient$5",new String[0]);shapes.put(API+"RecipeIngredientOr",new String[]{"or"});shapes.put(API+"RecipeIngredientDefer",new String[]{"supplier"});
      oreNames=field(OreDictionary.class,null,"nameToId");oreStacks=field(OreDictionary.class,null,"idToStack");
      if(!NativeOreStorage.names(oreNames)||oreStacks==null||oreStacks.getClass()!=ArrayList.class)throw fault("Unadapted native infusion ore storage");
      mode=field(type(NETWORK),null,"infusionOreDictMode");
      if(mode==null||!type(MODE).isInstance(mode)||!Arrays.asList(MODE+"$1",MODE+"$2",MODE+"$3",MODE+"$4").contains(mode.getClass().getName()))
        throw fault("Uninitialized or unadapted infusion ore mode");
      shapes.put(mode.getClass().getName(),new String[0]);
      owner(recipe,"getCentral",type(ENHANCED));owner(recipe,"getComponentsExt",type(ENHANCED));
      predicates.add(field(type(ENHANCED),recipe,"central"));
      Object raw=field(type(ENHANCED),recipe,"components");admitList(raw);
      predicates.addAll((List<?>)raw);
      if(predicates.size()>4096)throw fault("Infusion input context exceeds budget");
      for(Object predicate:predicates)parsed.add(parse(predicate,0));
      graph=new NativeDefinitionGraph(this::roots,shapes);
      signature=CanonicalJson.digest(description(parsed));
      preparing=false;
    }catch(RuntimeException|Error error){close();throw error;}
  }
  private Node parse(Object raw,int depth){
    Jobs.checkpoint();if(raw==null||depth>16||++visits>262144)throw fault("Infusion predicate exceeds budget");
    String name=raw.getClass().getName();
    if(name.equals(API+"RecipeIngredient$5"))return new Node("never");
    if(name.equals(API+"RecipeIngredientDefer")){
      ItemStack target=deferred(raw);Node node=new Node("deferred");node.targets=new ItemStack[]{target};node.parts=Collections.singletonList(resolve(target));return node;
    }
    if(name.equals(API+"RecipeIngredientOr")){
      Object[] parts=(Object[])field(type(name),raw,"or");if(parts==null||parts.length>4096)throw fault("Infusion OR predicate exceeds budget");
      Node node=new Node("any");for(Object part:parts)node.parts.add(parse(part,depth+1));return node;
    }
    if(name.equals(API+"RecipeIngredient$1")||name.equals(API+"RecipeIngredient$2")){
      String ore=(String)field(type(name),raw,"val$name");if(ore==null||ore.length()>4096)throw fault("Invalid infusion ore key");
      bindOre(ore);
      int cached=(Integer)field(type(name),raw,"oreID");Object current=((Map<?,?>)oreNames).get(ore);
      if(cached< -1||cached>=0&&!Objects.equals(current,cached))throw fault("Infusion predicate has a stale native ore ID");
      return ore(ore,name.endsWith("$2"));
    }
    if(name.equals(API+"RecipeIngredient$3")||name.equals(API+"RecipeIngredient$4")){
      boolean tags=(Boolean)field(type(name),raw,"val$checkNBTTags");
      ItemStack[] targets=name.endsWith("$3")?new ItemStack[]{(ItemStack)field(type(name),raw,"val$target")}:(ItemStack[])field(type(name),raw,"val$targets");
      if(targets==null||targets.length>4096)throw fault("Invalid infusion item predicate");
      return items(targets,tags);
    }
    throw fault("Infusion predicate needs an adapter: "+name);
  }
  private void bindOre(String name){if(preparing)ores.add(name);else if(!ores.contains(name))throw changed();}
  private Node ore(String name,boolean exclusive){bindOre(name);Node node=new Node("ore");node.name=name;node.nbt=exclusive;return node;}
  private Node items(ItemStack[] targets,boolean nbt){
    if(targets==null||targets.length>4096)throw fault("Enhanced target array exceeds budget");
    for(ItemStack target:targets)NativeStackAudit.audit(target);Node node=new Node("items");node.targets=targets;node.nbt=nbt;return node;
  }
  /** Reproduce InfusionOreDictMode.get on owned lists. Never call its Strict path on native lists. */
  private Node resolve(ItemStack target){
    NativeStackAudit.audit(target);int[] ids=OreDictionary.getOreIDs(target);
    if(ids.length>65536)throw fault("Deferred infusion ore IDs exceed budget");
    List<String> names=new ArrayList<>();for(int id:ids){String name=OreDictionary.getOreName(id);if(!Objects.equals(((Map<?,?>)oreNames).get(name),id))throw fault("Deferred infusion has an invalid ore ID");bindOre(name);names.add(name);}
    if(names.size()>4096)throw fault("Deferred infusion ore predicate exceeds graph budget");
    String rule=((Enum<?>)mode).name();
    if(rule.equals("None")||ids.length==0)return items(new ItemStack[]{target},rule.equals("Strict"));
    if(rule.equals("Default"))return ore(names.get(0),false);
    if(rule.equals("Relaxed")){Node node=new Node("any");for(String name:names)node.parts.add(ore(name,false));return node;}
    if(ids.length==1)return ore(names.get(0),true);
    List<ItemStack> selected=null;boolean nativeList=false;
    for(String name:names){
      List<?> raw=NativeOreTable.members(name);if(raw.size()==1)return items(new ItemStack[]{target},true);List<ItemStack> current=new ArrayList<>();
      for(Object member:raw){if(!(member instanceof ItemStack))throw fault("Invalid deferred ore member");NativeStackAudit.audit((ItemStack)member);current.add((ItemStack)member);}
      if(selected==null){selected=current;nativeList=true;}
      else if(selected.isEmpty()||current.isEmpty()){
        if(nativeList&&!selected.isEmpty())throw new Jobs.Fault("native_factory_mutation","Strict infusion factory would clear a registered ore list before matching");
        selected=new ArrayList<>();nativeList=false;
      }else{
        List<ItemStack> intersection=new ArrayList<>();
        for(ItemStack candidate:current)for(ItemStack required:selected)if(candidate.getItem()==required.getItem()&&Items.feather.getDamage(candidate)==Items.feather.getDamage(required)){intersection.add(candidate);break;}
        selected=intersection;nativeList=false;
      }
      if(selected.size()==1)return items(new ItemStack[]{target},true);
    }
    return items(selected.toArray(new ItemStack[0]),false);
  }
  private final class Node {
    final String kind;String name;boolean nbt;ItemStack[] targets=new ItemStack[0];List<Node> parts=new ArrayList<>();
    Node(String kind){this.kind=kind;}
    JsonObject json(){
      if(kind.equals("never"))return object("kind",kind);
      if(kind.equals("ore"))return object("kind",kind,"name",name,"exclusive",nbt);
      if(kind.equals("deferred"))return object("kind",kind,"template",InfusionLegacyInputs.template(targets[0]),"rule",parts.get(0).json());
      JsonArray values=array();if(kind.equals("items")){for(ItemStack target:targets)values.add(InfusionLegacyInputs.template(target));return object("kind",kind,"templates",values,"nbt",nbt);}
      for(Node part:parts)values.add(part.json());return object("kind","any","options",values);
    }
    boolean matches(ItemStack stack){
      Jobs.checkpoint();if(kind.equals("never"))return false;
      if(kind.equals("ore")){
        Object id=((Map<?,?>)oreNames).get(name);if(id==null)return false;int[] ids=OreDictionary.getOreIDs(stack);
        if(nbt&&ids.length!=1)return false;for(int candidate:ids)if(Objects.equals(candidate,id))return true;return false;
      }
      if(kind.equals("items")){for(ItemStack target:targets)if(target.getItem()==stack.getItem()
          &&(Items.feather.getDamage(target)==32767||Items.feather.getDamage(target)==Items.feather.getDamage(stack))
          &&(!nbt||ItemStack.areItemStackTagsEqual(target,stack)))return true;return false;}
      for(Node part:parts)if(part.matches(stack))return true;return false;
    }
    boolean never(){
      if(kind.equals("never"))return true;
      if(kind.equals("ore"))return ((Map<?,?>)oreNames).get(name)==null;
      if(kind.equals("items")){for(ItemStack target:targets)if(!nbt||ItemStack.areItemStackTagsEqual(target,target.copy()))return false;return true;}
      for(Node part:parts)if(!part.never())return false;return true;
    }
    void samples(Map<String,ItemStack> pool){
      if(kind.equals("ore")){for(ItemStack member:NativeOreTable.copies(name))for(ItemStack sample:examples.permutations(member))candidate(pool,sample);}
      else if(kind.equals("items")){for(ItemStack target:targets)for(ItemStack sample:nbt?examples.copies(target):examples.permutations(target))candidate(pool,sample);}
      else for(Node part:parts)part.samples(pool);
    }
    private void candidate(Map<String,ItemStack> pool,ItemStack sample){
      sample.stackSize=1;if(!matches(sample.copy()))return;
      String key=CanonicalJson.digest(RecipeRow.stackEvidence(sample));ItemStack prior=pool.putIfAbsent(key,sample);
      if(prior!=null)for(int copy=0;copy<3;copy++){
        if(!Objects.equals(prior.getTagCompound()==null?null:prior.getTagCompound().toString(),sample.getTagCompound()==null?null:sample.getTagCompound().toString()))throw fault("Canonical enhanced specimen has different native NBT order");
        prior=prior.copy();sample=sample.copy();
      }
      if(pool.size()>65536)throw fault("Enhanced infusion specimen budget exceeded");
    }
  }
  private static JsonArray description(List<Node> nodes){JsonArray values=array();for(Node node:nodes)values.add(node.json());return values;}
  /** Prove the bound handle is the pinned Utility factory's rule, without invoking it. */
  private ItemStack deferred(Object raw){
    ItemStack bound=deferredBindings.get(raw);if(bound!=null&&!checking)return bound;
    if(bound==null&&!preparing)throw changed();if(bound==null&&deferredBindings.size()>=4096)throw fault("Deferred infusion binding budget exceeded");
    try{
      Method current=type(NETWORK).getMethod("getInfusionOreDictMode");
      if(current.getDeclaringClass()!=type(NETWORK)||current.getReturnType()!=type(MODE)||!Modifier.isStatic(current.getModifiers()))throw fault("Deferred infusion factory has an unknown network-mode route");
    }catch(NoSuchMethodException missing){throw fault("Deferred infusion network-mode getter is unavailable");}
    Object supplier=field(type(API+"RecipeIngredientDefer"),raw,"supplier");
    ItemStack left=new ItemStack(Items.feather,1,0),right=new ItemStack(Items.feather,1,1);
    Object a=invoke(type(API+"Utility"),null,"convertUnderCurrentRule",new Class<?>[]{ItemStack.class},left);
    Object b=invoke(type(API+"Utility"),null,"convertUnderCurrentRule",new Class<?>[]{ItemStack.class},right);
    Object ah=field(type(API+"RecipeIngredientDefer"),a,"supplier"),bh=field(type(API+"RecipeIngredientDefer"),b,"supplier");
    if(!(supplier instanceof MethodHandle)||supplier.getClass()!=ah.getClass()||ah.getClass()!=bh.getClass()
        ||!supplier.getClass().getName().startsWith("java.lang.invoke.BoundMethodHandle$Species_")
        ||normalForm(supplier)!=normalForm(ah)||normalForm(ah)!=normalForm(bh)
        ||field(MethodHandle.class,supplier,"type")!=field(MethodHandle.class,ah,"type"))
      throw fault("Deferred infusion supplier lacks a pinned JDK8 factory proof");
    ItemStack target=null;int fields=0;
    try{
      for(Field member:ah.getClass().getDeclaredFields())if(!Modifier.isStatic(member.getModifiers())){
        if(++fields>16||!Modifier.isFinal(member.getModifiers()))throw fault("Unadapted bound infusion handle storage");member.setAccessible(true);
        Object l=member.get(ah),r=member.get(bh),actual=member.get(supplier);
        if(l==left&&r==right){if(target!=null||!(actual instanceof ItemStack))throw fault("Ambiguous deferred infusion template");target=(ItemStack)actual;}
        else if(member.getType().isPrimitive()?!Objects.equals(l,r)||!Objects.equals(l,actual):l!=r||l!=actual)
          throw fault("Deferred infusion supplier changes more than its template binding");
      }
    }catch(IllegalAccessException error){throw fault("Cannot inspect pinned deferred infusion handle");}
    if(target==null)throw fault("Deferred infusion factory template was not found");NativeStackAudit.audit(target);
    if(bound!=null){if(target!=bound)throw changed();return target;}
    shapes.put(supplier.getClass().getName(),new String[0]);deferredTargets.add(target);deferredBindings.put(raw,target);return target;
  }
  private static Object normalForm(Object handle){
    Object form=field(MethodHandle.class,handle,"form");Class<?> type=type("java.lang.invoke.LambdaForm");
    if(form==null||form.getClass()!=type)throw fault("Unadapted deferred infusion lambda form");
    if(field(type,form,"customized")!=null){form=field(type,form,"transformCache");if(form==null||form.getClass()!=type||field(type,form,"customized")!=null)throw fault("Unadapted customized infusion lambda form");}
    return form;
  }
  private Object[] roots(){
    List<Object> roots=new ArrayList<>();roots.add(recipe);
    for(String name:new String[]{"research","aspects","instability","recipeInput","components","recipeOutput"})roots.add(field(InfusionRecipe.class,recipe,name));
    if(type(ENHANCED).isInstance(recipe)){roots.add(field(type(ENHANCED),recipe,"central"));roots.add(field(type(ENHANCED),recipe,"components"));}
    roots.add(field(type(NETWORK),null,"infusionOreDictMode"));roots.add(deferredTargets);roots.add(predicates);
    for(String ore:ores){Object id=((Map<?,?>)oreNames).get(ore);roots.add(id);roots.add(id==null?null:NativeOreTable.members(ore));}
    if(roots.size()>4096)throw fault("Infusion ore/source roots exceed budget");return roots.toArray();
  }
  private void admitList(Object raw){
    if(!(raw instanceof List<?>)||((List<?>)raw).size()>4095)throw fault("Invalid enhanced infusion components");
    String name=raw.getClass().getName();if(!Arrays.asList("java.util.ArrayList","java.util.LinkedList","java.util.Arrays$ArrayList","java.util.Collections$EmptyList").contains(name))throw fault("Unadapted enhanced infusion component backing");
    if(name.equals("java.util.Collections$EmptyList"))shapes.put(name,new String[0]);
  }
  private static void owner(Object raw,String method,Class<?> expected,Class<?>...arguments){try{if(raw.getClass().getMethod(method,arguments).getDeclaringClass()!=expected)throw fault("Infusion callback needs an adapter: "+raw.getClass().getName()+"."+method);}catch(NoSuchMethodException error){throw fault("Missing infusion callback: "+method);}}
  List<List<MagicRecipes.Candidate>> copies(){
    verify();int total=0;List<List<MagicRecipes.Candidate>> result=new ArrayList<>();
    for(Node predicate:parsed){Map<String,ItemStack> pool=new TreeMap<>();predicate.samples(pool);List<MagicRecipes.Candidate> input=new ArrayList<>();
      for(ItemStack stack:pool.values())if(predicate.matches(stack.copy()))input.add(new MagicRecipes.Candidate(stack,object("kind","exact")));
      total+=input.size();if(total>65536)throw fault("Infusion examples exceed cumulative budget");result.add(input);
    }return result;
  }
  boolean never(int slot){verify();return parsed.get(slot).never();}
  boolean accepts(int slot,ItemStack stack){verify();NativeStackAudit.audit(stack);return parsed.get(slot).matches(stack.copy());}
  boolean acceptsStock(ItemStack stack){
    verify();NativeStackAudit.audit(stack);for(int slot=1;slot<parsed.size();slot++)if(parsed.get(slot).matches(stack.copy()))return true;return false;
  }
  boolean matches(ItemStack center,List<ItemStack> outer){
    verify();NativeStackAudit.audit(center);if(!parsed.get(0).matches(center.copy()))return false;
    List<ItemStack> remaining=new ArrayList<>();for(ItemStack stack:outer){NativeStackAudit.audit(stack);remaining.add(stack.copy());}
    for(int at=1;at<parsed.size();at++){Node node=parsed.get(at);int first=-1;for(int i=0;i<remaining.size();i++)if(node.matches(remaining.get(i))){first=i;break;}
      if(first<0)return false;remaining.remove(first);}
    return true;
  }
  JsonArray describe(){verify();JsonArray current=description(parsed);if(!signature.equals(CanonicalJson.digest(current)))throw changed();return current;}
  void verify(){alive();if(field(OreDictionary.class,null,"nameToId")!=oreNames||field(OreDictionary.class,null,"idToStack")!=oreStacks)throw changed();graph.verifyRoots();}
  void finishVerification(){verify();graph.verify();checking=true;visits=0;
    try{List<Node> current=new ArrayList<>();for(Object predicate:predicates)current.add(parse(predicate,0));if(!signature.equals(CanonicalJson.digest(description(current))))throw changed();}
    finally{checking=false;}}
  private void alive(){Jobs.checkpoint();if(closed)throw new Jobs.Fault("capture_closed","Infusion input source is closed");}
  public void close(){closed=true;if(graph!=null)graph.close();predicates.clear();parsed.clear();deferredTargets.clear();deferredBindings.clear();shapes.clear();ores.clear();recipe=null;oreNames=null;oreStacks=null;mode=null;signature=null;}
  private static Jobs.Fault fault(String text){return new Jobs.Fault("recipe_unsupported",text);}
  private static Jobs.Fault changed(){return new Jobs.Fault("environment_changed","Native infusion input or ore source changed");}
}
