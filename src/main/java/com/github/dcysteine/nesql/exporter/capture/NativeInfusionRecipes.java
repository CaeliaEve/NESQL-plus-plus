package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.*;
import java.util.*;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.crafting.InfusionRecipe;

/** Native definition readers; neither cached GUI outputs nor player/world callbacks are recipes. */
final class NativeInfusionRecipes implements AutoCloseable {
  static final String GOLEM="makeo.gadomancy.common.crafting.InfusionUpgradeRecipe",
      OLD="makeo.gadomancy.common.crafting.Familiar_Old_AugmentInfusion",
      REVOLVER="tb.utils.RevolverInfusionRecipe",
      REPLACEMENTS="com.gtnewhorizon.gtnhlib.api.thaumcraft.EnhancedInfusionRecipe";
  private static final String SHIELD="makeo.gadomancy.common.entities.golems.upgrades.GolemUpgradeRunicShield",
      OLD_ITEM="makeo.gadomancy.common.items.baubles.ItemFamiliar_Old",
      OLD_UPGRADE=OLD_ITEM+"$FamiliarUpgrade",REVOLVER_UPGRADE="tb.api.RevolverUpgrade";
  static final class Branch {final NativeInfusionRecipes reader;Branch(NativeInfusionRecipes reader){this.reader=reader;}}
  private final String owner;
  private InfusionRecipe recipe;
  private InfusionLegacyInputs inputs;
  private NativeDefinitionGraph graph;
  private AspectList aspects;
  private JsonObject randomAspects;
  private String research,key,previous;
  private int instability;
  private Object upgrade,registered;
  private List<?> replacements;
  private Object replacementSentinel;
  private boolean replacementsActive;
  private FamiliarInfusionSource familiar;
  private MachinaInfusionSource machina;
  private InfusionInputSource enhanced;
  private boolean missingEnhancedCenter;
  private final List<Object> upgrades=new ArrayList<>();
  private final List<Item> familiarItems=new ArrayList<>();
  private final List<GtItemListBinding> containers=new ArrayList<>();
  private final List<PedestalReturn> pedestalReturns=new ArrayList<>();
  private JsonObject exclusion;
  private boolean closed;

  static boolean supports(Object raw){if(raw==null)return false;String name=raw.getClass().getName();return name.equals(InfusionRecipe.class.getName())||name.equals(GOLEM)||name.equals(OLD)||name.equals(REVOLVER)||name.equals(FamiliarInfusionSource.RECIPE)||name.equals(REPLACEMENTS)||name.equals(MachinaInfusionSource.RECIPE)||name.equals(InfusionInputSource.ENHANCED);}
  NativeInfusionRecipes(InfusionRecipe source,NativeItemExamples examples,InfusionLegacyInputs.Context context){
    recipe=source;owner=source.getClass().getName();
    if(!supports(source))throw fault("Unknown native infusion definition owner: "+owner);
    try{
      if(field(InfusionRecipe.class,recipe,"recipeInput")==null
          &&(owner.equals(InfusionInputSource.ENHANCED)||baseNullInputGate(source))){
        try{if(source.getClass().getMethod("getRecipeInput").getDeclaringClass()!=InfusionRecipe.class)throw fault("Null-input gate has an unknown getter owner");}
        catch(NoSuchMethodException absent){throw fault("Missing enhanced input getter");}
        missingEnhancedCenter=true;graph=new NativeDefinitionGraph(this::roots,NativeDefinitionGraph.shapes(owner));return;
      }
      aspects=(AspectList)field(InfusionRecipe.class,recipe,"aspects");research=(String)field(InfusionRecipe.class,recipe,"research");
      instability=(Integer)field(InfusionRecipe.class,recipe,"instability");
      if(aspects==null||research==null)throw fault("Invalid native infusion cost or research: owner="+owner+"; aspects="+(aspects==null?"null":aspects.getClass().getName())+"; research="+research);
      randomAspects=InfusionCostRules.random(aspects);
      if(randomAspects!=null&&!owner.equals(InfusionRecipe.class.getName()))throw fault("Randomized cost needs an adapter for its native infusion subtype");
      inputs=new InfusionLegacyInputs(source,examples,context,!owner.equals(REVOLVER)&&!owner.equals(OLD)&&!owner.equals(FamiliarInfusionSource.RECIPE)&&!owner.equals(MachinaInfusionSource.RECIPE)&&!owner.equals(InfusionInputSource.ENHANCED),owner.equals(FamiliarInfusionSource.RECIPE)||owner.equals(InfusionInputSource.ENHANCED),owner.equals(MachinaInfusionSource.RECIPE));
      if(owner.equals(GOLEM)){
        upgrade=field(recipe,"upgrade");
        if(upgrade==null||upgrade.getClass()!=type(SHIELD))throw fault("Golem upgrade needs a source-specific adapter");
        key="runicShield";
      }else if(owner.equals(REPLACEMENTS)){
        Object raw=field(recipe,"replacements");
        if(!(raw instanceof List<?>)||((List<?>)raw).size()>4096||!Arrays.asList("java.util.ArrayList","java.util.LinkedList","java.util.Arrays$ArrayList","java.util.Collections$SingletonList","java.util.Collections$EmptyList").contains(raw.getClass().getName()))
          throw fault("GTNH infusion replacements need an admitted native list backing");
        replacements=(List<?>)raw;
        replacementsActive=replacementMixin();
        replacementSentinel=field(type(REPLACEMENTS),null,"NO_REPLACEMENT");
        if(replacementSentinel==null||replacementSentinel.getClass()!=type(REPLACEMENTS+"$Replacement")
            ||field(replacementSentinel,"input")!=null||field(replacementSentinel,"output")!=null||!Boolean.TRUE.equals(field(replacementSentinel,"strict")))
          throw fault("GTNH infusion replacement sentinel changed");
        for(Object entry:replacements){
          if(entry==null||entry.getClass()!=type(REPLACEMENTS+"$Replacement"))throw fault("Unknown GTNH infusion replacement owner");
          Object in=field(entry,"input"),out=field(entry,"output");if(in!=null)NativeStackAudit.audit((ItemStack)in);if(out!=null)NativeStackAudit.audit((ItemStack)out);
        }
      }else if(owner.equals(OLD)){
        upgrade=field(recipe,"upgradeToAdd");
        if(upgrade==null||upgrade.getClass()!=type(OLD_UPGRADE))throw fault("Unknown old familiar upgrade enum");
        key=(String)field(upgrade,"tag");Object prior=field(upgrade,"neededUpgrade");previous=prior==null?null:(String)field(prior,"tag");
        familiarItems.addAll(familiarItems());
      }else if(owner.equals(FamiliarInfusionSource.RECIPE)){
        familiar=new FamiliarInfusionSource(recipe);
      }else if(owner.equals(MachinaInfusionSource.RECIPE)){
        machina=new MachinaInfusionSource(recipe);
      }else if(owner.equals(InfusionInputSource.ENHANCED)){
        enhanced=new InfusionInputSource(recipe,examples);
      }else if(owner.equals(REVOLVER)){
        upgrade=field(recipe,"upgrade");registered=field(type(REVOLVER_UPGRADE),null,"allUpgrades");
        if(registered==null||!registered.getClass().isArray()||java.lang.reflect.Array.getLength(registered)>4096)throw fault("Invalid revolver upgrade registry");
        for(int i=0;i<java.lang.reflect.Array.getLength(registered);i++){Object entry=java.lang.reflect.Array.get(registered,i);if(entry!=null)bindUpgrade(entry);}
        bindUpgrade(upgrade);
        for(int i=0;i<upgrades.size();i++)for(Object conflict:conflicts(upgrades.get(i)))bindUpgrade(conflict);
      }
      Map<String,String[]> shapes=new HashMap<>();
      List<String> fields=new ArrayList<>(Arrays.asList("aspects","research","components","recipeInput","recipeOutput","instability"));
      if(owner.equals(GOLEM)||owner.equals(REVOLVER))fields.add("upgrade");if(owner.equals(OLD))fields.add("upgradeToAdd");
      if(owner.equals(REPLACEMENTS))fields.add("replacements");
      if(owner.equals(MachinaInfusionSource.RECIPE)){fields.add("augmentation");fields.add("isResearchRecipe");
        String prefix="jcm2606.thaumicmachina.wand.augmentation.";
        for(String suffix:Arrays.asList("AugmentationChargeBuffer","AugmentationContactDischarge","AugmentationTaintCapping","AugmentationTaintedCore","AugmentationVisChannel"))shapes.put(prefix+suffix,new String[0]);}
      shapes.put(owner,owner.equals(InfusionInputSource.ENHANCED)?new String[0]:fields.toArray(new String[0]));shapes.put(SHIELD,new String[0]);shapes.put(OLD_UPGRADE,new String[0]);
      shapes.put(REVOLVER_UPGRADE,new String[]{"id","maxLevel","instability","conflicts"});
      shapes.put(AspectList.class.getName(),new String[]{"aspects"});shapes.put(Aspect.class.getName(),new String[]{"tag"});
      InfusionCostRules.shapes(shapes,randomAspects);
      shapes.put(REPLACEMENTS+"$Replacement",new String[]{"input","output","strict"});
      shapes.put("java.util.Collections$SingletonList",new String[]{"element"});shapes.put("java.util.Collections$EmptyList",new String[0]);
      graph=new NativeDefinitionGraph(this::roots,shapes);
    }catch(RuntimeException|Error failure){close();throw failure;}
  }
  Branch branch(){alive();return new Branch(this);}
  private static boolean baseNullInputGate(InfusionRecipe source){
    try{return source.getClass().getMethod("matches",java.util.ArrayList.class,ItemStack.class,net.minecraft.world.World.class,net.minecraft.entity.player.EntityPlayer.class).getDeclaringClass()==InfusionRecipe.class;}
    catch(NoSuchMethodException missing){throw fault("Missing native infusion matcher");}
  }
  String sourceType(){return owner;}
  JsonObject exclusion(){return exclusion;}
  private void bindUpgrade(Object raw){if(raw==null||raw.getClass()!=type(REVOLVER_UPGRADE))throw fault("Unadapted revolver upgrade callback owner");if(identityIndex(raw)<0){if(upgrades.size()>=4096)throw fault("Revolver upgrade graph exceeds budget");upgrades.add(raw);}}
  private int identityIndex(Object raw){for(int i=0;i<upgrades.size();i++)if(upgrades.get(i)==raw)return i;return -1;}
  private static List<?> conflicts(Object raw){Object list=field(raw,"conflicts");if(list==null||list.getClass()!=ArrayList.class||((List<?>)list).size()>4096)throw fault("Invalid revolver conflict list");return (List<?>)list;}
  private static List<Item> familiarItems(){
    List<Item> result=new ArrayList<>();Class<?> type=type(OLD_ITEM);
    for(Object raw:Item.itemRegistry)if(type.isInstance(raw)){
      Item item=(Item)raw;
      try{if(item.getClass().getMethod("hasUpgrade",ItemStack.class,type(OLD_UPGRADE)).getDeclaringClass()!=type
          ||item.getClass().getMethod("addUpgrade",ItemStack.class,type(OLD_UPGRADE)).getDeclaringClass()!=type)throw fault("Old familiar overrides upgrade semantics");}
      catch(NoSuchMethodException error){throw fault("Old familiar upgrade methods are unavailable");}
      result.add(item);
    }
    result.sort(Comparator.comparing(Item.itemRegistry::getNameForObject));return result;
  }
  private Object[] roots(){
    if(missingEnhancedCenter)return new Object[]{recipe,field(InfusionRecipe.class,recipe,"recipeInput")};
    List<Object> roots=new ArrayList<>();roots.add(recipe);
    if(owner.equals(InfusionInputSource.ENHANCED))for(String name:Arrays.asList("research","aspects","instability","recipeInput","components","recipeOutput"))roots.add(field(InfusionRecipe.class,recipe,name));
    if(owner.equals(REVOLVER))roots.add(field(type(REVOLVER_UPGRADE),null,"allUpgrades"));
    if(owner.equals(REPLACEMENTS)){roots.add(field(type(REPLACEMENTS),null,"NO_REPLACEMENT"));roots.add(replacementMixin());}
    if(owner.equals(OLD)){roots.add(field(upgrade,"tag"));Object prior=field(upgrade,"neededUpgrade");roots.add(prior);roots.add(prior==null?null:field(prior,"tag"));roots.add(familiarItems);}
    return roots.toArray();
  }
  private JsonObject effect(){
    if(machina!=null)return machina.describe();
    if(familiar!=null)return familiar.describe();
    if(owner.equals(GOLEM))return object("kind","golem","namespace","gadomancy","key",key);
    if(owner.equals(OLD)){JsonArray items=array();for(Item item:familiarItems)items.add(value(Item.itemRegistry.getNameForObject(item)));return object("kind","oldFamiliar","key",key,"previous",previous,"items",items);}
    if(owner.equals(REVOLVER)){
      JsonArray table=array(),nodes=array();for(int i=0;i<java.lang.reflect.Array.getLength(registered);i++){Object raw=java.lang.reflect.Array.get(registered,i);table.add(value(raw==null?null:identityIndex(raw)));}
      for(Object raw:upgrades){JsonArray conflicts=array();for(Object other:conflicts(raw))conflicts.add(value(identityIndex(other)));
        nodes.add(object("id",field(raw,"id"),"maxLevel",field(raw,"maxLevel"),"instability",field(raw,"instability"),"conflicts",conflicts));}
      return object("kind","revolver","target",identityIndex(upgrade),"registered",table,"upgrades",nodes);
    }
    return object("kind","declared");
  }
  private JsonArray replacementRules(){
    JsonArray result=array();if(replacements==null)return result;
    for(Object entry:replacements){ItemStack input=(ItemStack)field(entry,"input"),output=(ItemStack)field(entry,"output");
      result.add(object("input",input==null?null:InfusionLegacyInputs.template(input),
          "output",output==null?null:object("item",InfusionLegacyInputs.template(output),"amount",output.stackSize),"strict",field(entry,"strict")));}
    return result;
  }
  private static boolean replacementMatch(ItemStack target,ItemStack offered,boolean strict){
    return target!=null&&offered!=null&&target.getItem()==offered.getItem()
        &&(!strict&&net.minecraft.init.Items.feather.getDamage(target)==32767||net.minecraft.init.Items.feather.getDamage(target)==net.minecraft.init.Items.feather.getDamage(offered));
  }
  private static boolean replacementMixin(){
    Class<?> matrix=type("thaumcraft.common.tiles.TileInfusionMatrix");
    try{
      java.lang.reflect.Field state=matrix.getDeclaredField("gTNHLib$replacements");
      if(state.getType()!=List.class||java.lang.reflect.Modifier.isStatic(state.getModifiers())
          ||matrix.getDeclaredMethod("gTNHLib$getReplacement",ItemStack.class).getReturnType()!=type(REPLACEMENTS+"$Replacement"))
        throw fault("Unknown merged GTNH infusion replacement shape");
      return true;
    }catch(NoSuchFieldException absent){
      try{matrix.getDeclaredMethod("gTNHLib$getReplacement",ItemStack.class);throw fault("Partial GTNH infusion replacement merge");}
      catch(NoSuchMethodException alsoAbsent){return false;}
    }catch(NoSuchMethodException partial){throw fault("Partial GTNH infusion replacement merge");}
  }
  private Object replacement(ItemStack offered){if(replacementsActive&&replacements!=null)for(Object entry:replacements)
    if(replacementMatch((ItemStack)field(entry,"input"),offered,(Boolean)field(entry,"strict")))return entry==replacementSentinel?null:entry;return null;}
  boolean capture(RecipeRow row,InfusionInheritance inheritance){
    verify();exclusion=null;
    if(missingEnhancedCenter){exclusion=object("reason","native_infusion_null_recipe_input","native",owner);return false;}
    inheritance.bindNativeOutput(recipe);
    if(!inputs.hasCenter()){exclusion=object("reason","native_infusion_null_recipe_input","native",owner);return false;}
    if(owner.equals(REVOLVER)&&research.isEmpty()){exclusion=object("reason","native_revolver_requires_nonempty_research","native",owner);return false;}
    if(machina!=null&&!machina.definitionWand()){exclusion=object("reason","native_machina_definition_is_not_a_wand","native",owner);return false;}
    List<List<MagicRecipes.Candidate>> enhancedChoices=enhanced==null?null:enhanced.copies();
    if(enhancedChoices!=null){
      for(int slot=0;slot<enhancedChoices.size();slot++)if(enhancedChoices.get(slot).isEmpty()){
        if(enhanced.never(slot)){exclusion=object("reason","native_infusion_required_predicate_is_impossible","native",owner,"slot",slot);return false;}
        throw fault("Enhanced predicate has no native specimen; this is not proof of an unreachable recipe");
      }
    }
    List<ItemStack> sourceCenters=enhancedChoices==null?inputs.centralCopies():candidateStacks(enhancedChoices.get(0));
    List<ItemStack> centers=new ArrayList<>();
    for(ItemStack center:sourceCenters){
      center.stackSize=1;
      if(familiar!=null){if(!familiar.acceptsClass(center))continue;center=familiar.specimen(center);}
      if(machina!=null)center=machina.specimen(center);
      if(owner.equals(OLD)){
        if(!familiarItems.contains(center.getItem()))continue;
        if(previous!=null&&!booleanTag(center,previous)){if(!center.hasTagCompound())center.setTagCompound(new NBTTagCompound());center.getTagCompound().setBoolean(previous,true);}
      }
      if(admitted(center))centers.add(center);
    }
    if(centers.isEmpty())throw fault("Native infusion has no admitted central specimen; this is not an environment exclusion");
    List<List<ItemStack>> components;
    if(enhancedChoices==null)components=inputs.componentCopies();else{components=new ArrayList<>();for(int slot=1;slot<enhancedChoices.size();slot++)components.add(candidateStacks(enhancedChoices.get(slot)));}
    List<List<ItemStack>> slots=new ArrayList<>();slots.add(centers);slots.addAll(components);
    if(enhanced!=null)enhancedWitness(slots);
    int total=0;
    for(int slot=0;slot<slots.size();slot++){
      List<RecipeRow.Ingredient> choices=new ArrayList<>();
      for(ItemStack stack:slots.get(slot)){if(++total>65536)throw fault("Infusion cumulative specimen budget exceeded");
        if(enhanced==null)inputs.validateSpecimen(slot,stack);
        else if(slot==0?!enhanced.accepts(0,stack):!enhanced.acceptsStock(stack)&&!inputs.acceptsCycleStock(stack))
          throw fault("Enhanced stock specimen changed its bound startup/cycle role");
        inputs.observe(row.facts,stack);choices.add(new RecipeRow.Ingredient(stack.copy(),1,false,object("kind","exact")));}
      row.itemInput(slot,choices,false);
      for(JsonElement value:row.inputs.get(slot).getAsJsonObject().getAsJsonArray("choices"))value.getAsJsonObject().add("consume",object("kind",enhanced!=null&&slot>0?"allocated":"stack"));
      if(slot>0){JsonArray captured=row.inputs.get(slot).getAsJsonObject().getAsJsonArray("choices");
        for(int at=0;at<captured.size();at++){
          ItemStack specimen=slots.get(slot).get(at),returned;
          Object replacement=replacement(specimen);
          if(replacement!=null){
            ItemStack output=(ItemStack)field(replacement,"output");
            // The Mixin's retention test has the OFFERED item as target. NBT and
            // count do not participate, even when the declared output has different tags.
            boolean keep=replacementMatch(specimen,output,(Boolean)field(replacement,"strict"));
            if(keep)captured.get(at).getAsJsonObject().add("consume",object("kind","keep"));
            returned=keep||output==null?null:output.copy();
          }else{
            returned=ItemCallbacks.pedestalContainer(specimen,containers);pedestalReturns.add(new PedestalReturn(specimen,returned));
          }
          if(returned!=null&&returned.stackSize<=0)throw fault("GTNH infusion replacement has a nonpositive native output count");
          if(returned!=null&&returned.stackSize>inputs.pedestalLimit())returned.stackSize=inputs.pedestalLimit();
          if(returned!=null)captured.get(at).getAsJsonObject().getAsJsonArray("returns").add(object("kind","item","id",row.facts.item(returned),"amount",Integer.toString(returned.stackSize)));
        }
      }
    }
    Products product=owner.equals(InfusionRecipe.class.getName())||owner.equals(REPLACEMENTS)||enhanced!=null?Products.infusion(recipe,centers,row.facts,inheritance)
        :Products.observe(centers,object("kind","nativeInfusion"),this::output,row.facts);
    JsonArray costs=costs(centers.get(0));int instability=instability(centers.get(0));
    // Choices can share a row only if the displayed magic costs remain correlated.
    for(ItemStack center:centers)if(!costs.equals(costs(center))||instability!=instability(center))throw fault("Infusion central specimens have different costs; separate native branches are required");
    JsonArray researchLinks=array();if(!research.isEmpty())researchLinks.add(Magic.study(research));
    row.record.add("magic",object("kind","infusion","aspects",costs,"research",researchLinks,"central",0,"instability",instability,"payment",null,"creative",false));
    row.record.add("process",object("kind","nativeInfusion","nativeRecipe",owner,"declaredInput",InfusionLegacyInputs.template(inputs.center()),"pattern",enhanced==null?inputs.describe():inputs.describe(enhanced.describe()),"effect",effect(),"baseAspects",rawCosts(0),"baseInstability",this.instability,"pedestals",replacementRules(),"pedestalLimit",inputs.pedestalLimit(),"replacementsActive",replacementsActive));
    if(randomAspects!=null)row.record.getAsJsonObject("process").add("randomAspects",randomAspects);
    row.itemOutput(0,product.output,10000);product.attach(row);
    row.note("thaumcraft:infusionRule",owner+"#matches/getRecipeOutput",row.facts.locale().equals("zh_CN")
        ?(enhanced!=null?"增强注魔分别记录启动谓词与构造时保存的取料模板。两个阶段各自按原生顺序匹配；启动允许剩余材料，实际使用的基座整体替换，未使用的库存保留。候选为单件样本，容器返还只在该基座被使用时成立，不保证固定批次用量。仍需满足研究、注册优先级与祭坛运行条件。":"候选是符合原生规则的单件输入样本，不是全部 NBT 状态的穷举。材料按原生顺序逐个取用首个匹配基座；是否允许多余材料见过程规则。研究和祭坛运行仍是执行条件。中心升级、费用与原生容器返还由专用过程保留。")
        :(enhanced!=null?"Enhanced infusion records startup predicates separately from its stored cycle templates. Each phase preserves native matching order. Startup allows leftovers; used pedestals are replaced as a whole and unused stock remains. Choices are one-item specimens. Container returns apply only when that pedestal is used, without guaranteed batch quantities. Research, registration priority and live altar conditions still apply.":"Choices are admitted one-item specimens, not an enumeration of every NBT state. Components greedily take the first matching pedestal in native order; the process defines whether extras are accepted. Research and altar execution remain conditions. The dedicated process retains central upgrades, costs and native container returns."));
    return true;
  }
  private static List<ItemStack> candidateStacks(List<MagicRecipes.Candidate> candidates){List<ItemStack> result=new ArrayList<>();for(MagicRecipes.Candidate candidate:candidates)result.add(candidate.item.copy());return result;}
  private void enhancedWitness(List<List<ItemStack>> slots){
    List<ItemStack> outer=firstStock(slots);
    if(enhanced.matches(slots.get(0).get(0),outer)&&inputs.matchesCycle(outer))return;
    // Each phase consumes first matches in its own declaration order and accepts
    // leftovers. Prefix its complete ordered witness with the other phase's
    // witness: earlier matches cannot consume a later witness before its own
    // still-present first match. No exponential tuple search or equal-cardinality
    // assumption is necessary. These are physical stock samples, not fixed costs.
    slots.addAll(inputs.cycleCopies());
    if(slots.size()>4096)throw fault("Enhanced startup/cycle stock layout exceeds input budget");
    outer=firstStock(slots);
    if(!enhanced.matches(slots.get(0).get(0),outer)||!inputs.matchesCycle(outer))
      throw new Jobs.Fault("native_definition_invariant","Ordered enhanced startup/cycle stock witnesses disagree with their bound predicates");
  }
  private static List<ItemStack> firstStock(List<List<ItemStack>> slots){
    List<ItemStack> outer=new ArrayList<>();for(int slot=1;slot<slots.size();slot++){
      if(slots.get(slot).isEmpty())throw fault("Native infusion stock role has no safe specimen; not an unreachable-recipe proof");
      outer.add(slots.get(slot).get(0));
    }return outer;
  }
  private boolean admitted(ItemStack center){
    if(machina!=null)return machina.accepts(center);
    if(familiar!=null)return familiar.admitted(center);
    if(owner.equals(GOLEM)){
      NBTTagCompound mod=center.hasTagCompound()?center.getTagCompound().getCompoundTag("gadomancy"):new NBTTagCompound();
      return mod.getCompoundTag("upgrades").getTag(key)==null;
    }
    if(owner.equals(OLD))return familiarItems.contains(center.getItem())&&(previous==null||booleanTag(center,previous));
    if(owner.equals(REVOLVER)){
      for(NBTTagCompound tag:revolverTags(center)){
        int id=tag.getInteger("id");Object existing=registered(id);
        if(existing!=null)for(Object conflict:conflicts(existing))if(conflict==upgrade)return false;
      }
      return level(center)+1<=(Integer)field(upgrade,"maxLevel");
    }
    return true;
  }
  private Object registered(int id){if(id<0)throw fault("Native revolver state indexes its registry with a negative id");return id<java.lang.reflect.Array.getLength(registered)?java.lang.reflect.Array.get(registered,id):null;}
  private static List<NBTTagCompound> revolverTags(ItemStack input){List<NBTTagCompound> result=new ArrayList<>();
    if(input.hasTagCompound()&&input.getTagCompound().hasKey("tb.upgrades")){NBTTagList list=input.getTagCompound().getTagList("tb.upgrades",10);if(list.tagCount()>4096)throw fault("Native revolver NBT exceeds budget");for(int i=0;i<list.tagCount();i++)result.add(list.getCompoundTagAt(i));}return result;}
  private int level(ItemStack input){for(NBTTagCompound tag:revolverTags(input))if(registered(tag.getInteger("id"))==upgrade)return tag.getInteger("level");return 0;}
  private static boolean booleanTag(ItemStack stack,String key){return stack.hasTagCompound()&&stack.getTagCompound().getBoolean(key);}
  private ItemStack output(ItemStack center){
    if(machina!=null)return machina.output(center);
    if(familiar!=null)return familiar.outputOwned(center);
    ItemStack copy=center.copy();
    if(owner.equals(GOLEM)){
      if(!copy.hasTagCompound())copy.setTagCompound(new NBTTagCompound());NBTTagCompound root=copy.getTagCompound(),mod=root.getCompoundTag("gadomancy");
      if(!(root.getTag("gadomancy") instanceof NBTTagCompound))root.setTag("gadomancy",mod);
      NBTTagCompound upgrades=mod.getCompoundTag("upgrades");if(!mod.hasKey("upgrades"))mod.setTag("upgrades",upgrades);upgrades.setByte(key,(byte)1);
    }else if(owner.equals(OLD)){
      if(!copy.hasTagCompound())copy.setTagCompound(new NBTTagCompound());copy.getTagCompound().setBoolean(key,true);
    }else if(owner.equals(REVOLVER)){
      int level=level(copy)+1,id=(Integer)field(upgrade,"id");
      if(!copy.hasTagCompound())copy.setTagCompound(new NBTTagCompound());NBTTagCompound root=copy.getTagCompound();
      if(!root.hasKey("tb.upgrades"))root.setTag("tb.upgrades",new NBTTagList());
      NBTTagList list=root.getTagList("tb.upgrades",10);for(int i=0;i<list.tagCount();i++)if(list.getCompoundTagAt(i).getInteger("id")==id){list.getCompoundTagAt(i).setInteger("level",level);return copy;}
      NBTTagCompound added=new NBTTagCompound();added.setInteger("id",id);added.setInteger("level",level);list.appendTag(added);
    }else throw fault("Ordinary infusion output must use its declared result");
    return copy;
  }
  private int instability(ItemStack center){return owner.equals(REVOLVER)?instability+(Integer)field(upgrade,"instability")*level(center):instability;}
  private JsonArray costs(ItemStack center){
    return rawCosts(owner.equals(REVOLVER)?level(center):0);
  }
  private JsonArray rawCosts(int level){
    if(randomAspects!=null)return array();
    Map<String,Integer> amounts=new TreeMap<>();JsonArray values=array();
    for(Aspect aspect:aspects.getAspects()){
      int amount=aspects.getAmount(aspect);if(aspect==null){if(amount==0)continue;throw fault("Null infusion aspect has nonzero cost");}
      if(level>0)amount=level>=32?0:amount<<level;
      if(amounts.put(Aspects.id(aspect),amount)!=null)throw fault("Duplicate native infusion aspect");
    }
    for(Map.Entry<String,Integer> amount:amounts.entrySet())values.add(object("aspect",amount.getKey(),"amount",Integer.toString(amount.getValue())));
    return values;
  }
  void verify(){alive();if(inputs!=null)inputs.verify();graph.verifyRoots();if(familiar!=null)familiar.verify();if(machina!=null)machina.verify();if(enhanced!=null)enhanced.verify();}
  private static final class PedestalReturn {
    final ItemStack specimen;final String signature;
    PedestalReturn(ItemStack specimen,ItemStack result){this.specimen=specimen;signature=com.github.dcysteine.nesql.exporter.source.CanonicalJson.digest(result==null?JsonNull.INSTANCE:RecipeRow.stackEvidence(result));}
    void verify(){List<GtItemListBinding> bindings=new ArrayList<>();ItemStack current=ItemCallbacks.pedestalContainer(specimen,bindings);
      if(!signature.equals(com.github.dcysteine.nesql.exporter.source.CanonicalJson.digest(current==null?JsonNull.INSTANCE:RecipeRow.stackEvidence(current))))throw changed();
      for(GtItemListBinding binding:bindings)binding.verify();}
  }
  void finishVerification(){verify();graph.verify();if(enhanced!=null)enhanced.finishVerification();if(inputs!=null)inputs.finishVerification();if(familiar!=null)familiar.finishVerification();if(machina!=null)machina.finishVerification();for(GtItemListBinding binding:containers)binding.verify();for(PedestalReturn returned:pedestalReturns)returned.verify();
    if(owner.equals(OLD)){List<Item> current=familiarItems();if(current.size()!=familiarItems.size())throw changed();for(int i=0;i<current.size();i++)if(current.get(i)!=familiarItems.get(i))throw changed();}}
  private void alive(){Jobs.checkpoint();if(closed)throw new Jobs.Fault("capture_closed","Native infusion reader is closed");}
  public void close(){closed=true;if(inputs!=null)inputs.close();if(graph!=null)graph.close();if(familiar!=null)familiar.close();if(machina!=null)machina.close();if(enhanced!=null)enhanced.close();enhanced=null;machina=null;familiar=null;recipe=null;aspects=null;upgrade=null;registered=null;replacements=null;replacementSentinel=null;upgrades.clear();familiarItems.clear();containers.clear();pedestalReturns.clear();exclusion=null;}
  private static Jobs.Fault fault(String message){return new Jobs.Fault("recipe_unsupported",message);}
  private static Jobs.Fault changed(){return new Jobs.Fault("environment_changed","Native infusion definition changed");}
}
