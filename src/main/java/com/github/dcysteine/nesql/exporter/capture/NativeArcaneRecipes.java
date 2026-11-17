package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.*;
import java.util.*;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.oredict.OreDictionary;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.common.config.ConfigItems;

/** Explicit rules for arcane recipes whose inventory behavior is not a template recipe. */
final class NativeArcaneRecipes implements AutoCloseable {
  static final String PRIMAL="dev.rndmorris.salisarcana.common.recipes.RecipeForgivingPrimalCharm",
      PHOTO="witchinggadgets.common.util.recipe.PhotoDevelopingRecipe",
      ATTUNE="shukaro.nodalmechanics.recipe.RecipeAttune";
  static final class Branch {
    final NativeArcaneRecipes reader;final int count;
    Branch(NativeArcaneRecipes reader,int count){this.reader=reader;this.count=count;}
  }
  private final String owner,operation;
  private Object recipe;
  private final Map<String,ItemStack> roles=new TreeMap<>();
  private final List<String> inkNames=new ArrayList<>();
  private final Map<String,Aspect> aspects=new TreeMap<>();
  private Map<?,?> aspectRoot;
  private final NativeItemExamples examples=new NativeItemExamples();
  private NativeDefinitionGraph definitions;
  private boolean closed;

  static boolean supports(Object source){if(source==null)return false;String name=source.getClass().getName();return name.equals(PRIMAL)||name.equals(PHOTO)||name.equals(ATTUNE);}
  NativeArcaneRecipes(Object source){
    if(!supports(source))throw fault("Unknown native arcane recipe");recipe=source;owner=source.getClass().getName();
    operation=owner.equals(PRIMAL)?"primalCharm":owner.equals(PHOTO)?"photoDeveloping":"nodeAttunement";
    try{
      if(owner.equals(PRIMAL)){
        roles.put("shard",new ItemStack((Item)field(source,"shardItem")));
        roles.put("gold",new ItemStack(Items.gold_ingot));roles.put("result",((ItemStack)field(source,"primalCharmItemStack")).copy());
        if(!Arrays.equals((int[])field(source,"SLOT_PRIMAL"),new int[]{0,1,2,6,7,8})||!Arrays.equals((int[])field(source,"SLOT_GOLD"),new int[]{3,5})||!Integer.valueOf(4).equals(field(source,"SLOT_BALANCED")))throw fault("Primal charm native cell roles changed");
      }else if(owner.equals(PHOTO)){
        Item material=(Item)field(type("witchinggadgets.common.WGContent"),null,"ItemMaterial");
        roles.put("plate",new ItemStack(material,1,9));roles.put("paper",new ItemStack(Items.paper));roles.put("result",new ItemStack(material,1,10));
        Object names=field(OreDictionary.class,null,"nameToId");if(!NativeOreStorage.names(names))throw fault("Unadapted ore names backing");
        for(Object raw:((Map<?,?>)names).keySet()){if(!(raw instanceof String))throw fault("Invalid ore name");if(((String)raw).equalsIgnoreCase("dyeBlack"))inkNames.add((String)raw);}Collections.sort(inkNames);
        if(inkNames.isEmpty())throw new Jobs.Fault("handler_not_ready","Photo developing has no registered black dye predicate");
        if(!"SCANCAMERA".equals(field(source,"research")))throw fault("Photo developing research changed");
      }else{
        roles.put("matrix",new ItemStack((Item)field(type("shukaro.nodalmechanics.items.NodalItems"),null,"itemMatrix")));
        roles.put("phial",new ItemStack(ConfigItems.itemEssence,1,1));
      }
      for(ItemStack stack:roles.values())NativeStackAudit.audit(stack);
      Map<?,?> nativeAspects=(Map<?,?>)field(Aspect.class,null,"aspects");
      if(nativeAspects==null||nativeAspects.getClass()!=LinkedHashMap.class||nativeAspects.size()>4096)throw new Jobs.Fault("handler_not_ready","Native aspect registry is unavailable");
      aspectRoot=nativeAspects;for(Map.Entry<?,?> entry:nativeAspects.entrySet()){
        if(!(entry.getKey() instanceof String)||!(entry.getValue() instanceof Aspect))throw fault("Unadapted aspect data owner");
        Aspect aspect=(Aspect)entry.getValue();
        // Localized aspect subclasses can override getName without changing tag identity. Only
        // the base getTag implementation is read here or later by Aspects.id.
        try{if(aspect.getClass().getMethod("getTag").getDeclaringClass()!=Aspect.class)throw fault("Unadapted aspect tag callback");}
        catch(NoSuchMethodException failure){throw fault("Missing native aspect tag getter");}
        Object tag=field(Aspect.class,aspect,"tag");
        if(!(tag instanceof String)||!entry.getKey().equals(tag))throw fault("Aspect registry key disagrees with native tag");
        aspects.put((String)entry.getKey(),aspect);
      }
      Map<String,String[]> shapes=new HashMap<>();
      shapes.put(PRIMAL,new String[]{"shardItem","primalCharmItemStack","SLOT_PRIMAL","SLOT_GOLD","SLOT_BALANCED"});
      shapes.put(PHOTO,new String[]{"research"});shapes.put(ATTUNE,new String[0]);
      definitions=new NativeDefinitionGraph(this::roots,shapes);
    }catch(RuntimeException|Error failure){close();throw failure;}
  }
  List<Branch> branches(){alive();List<Branch> result=new ArrayList<>();for(int count=1;count<=(owner.equals(PRIMAL)?1:owner.equals(PHOTO)?4:8);count++)result.add(new Branch(this,count));return result;}
  String sourceType(){return owner;}
  private Object[] roots(){
    alive();List<Object> result=new ArrayList<>();result.add(recipe);
    if(owner.equals(PRIMAL)){result.add(field(recipe,"shardItem"));result.add(field(recipe,"primalCharmItemStack"));result.add(Items.gold_ingot);}
    else if(owner.equals(PHOTO)){
      result.add(field(type("witchinggadgets.common.WGContent"),null,"ItemMaterial"));result.add(Items.paper);
      for(String name:inkNames)result.add(NativeOreTable.members(name));
    }else{result.add(field(type("shukaro.nodalmechanics.items.NodalItems"),null,"itemMatrix"));result.add(ConfigItems.itemEssence);}
    return result.toArray();
  }
  void verify(){alive();definitions.verifyRoots();Map<?,?> current=(Map<?,?>)field(Aspect.class,null,"aspects");if(current!=aspectRoot||current.size()!=aspects.size())throw changed();
    for(Map.Entry<String,Aspect> entry:aspects.entrySet())if(current.get(entry.getKey())!=entry.getValue()||!entry.getKey().equals(field(Aspect.class,entry.getValue(),"tag")))throw changed();}
  void finishVerification(){verify();definitions.verify();examples.verify();
    if(owner.equals(PHOTO)){List<String> now=new ArrayList<>();for(Object name:((Map<?,?>)field(OreDictionary.class,null,"nameToId")).keySet())if(name instanceof String&&((String)name).equalsIgnoreCase("dyeBlack"))now.add((String)name);Collections.sort(now);if(!now.equals(inkNames))throw changed();}}
  boolean capture(int count,RecipeRow row,boolean creative){
    verify();JsonObject roleIds=object(),aspectIds=object();JsonArray names=array();
    for(Map.Entry<String,ItemStack> role:roles.entrySet())roleIds.addProperty(role.getKey(),row.facts.item(role.getValue()));
    for(Map.Entry<String,Aspect> entry:aspects.entrySet())aspectIds.addProperty(entry.getKey(),Aspects.id(entry.getValue()));
    for(String name:inkNames)names.add(value(name));
    ItemStack result;AspectList cost=new AspectList();String research;
    if(owner.equals(PRIMAL)){
      JsonArray cells=array();for(int slot=0;slot<9;slot++){
        List<RecipeRow.Ingredient> choices=new ArrayList<>();
        if(slot==3||slot==5)choices.add(choice(roles.get("gold").copy(),object("kind","wildcard","meta",true,"nbt",true)));
        else {int first=slot<3?slot:slot-3;for(int i=0;i<(slot==4?1:6);i++){int meta=slot==4?6:(first+i)%6;ItemStack shard=roles.get("shard").copy();shard.setItemDamage(meta);choices.add(choice(shard,object("kind","wildcard","meta",false,"nbt",true)));}}
        row.nativeCraftingInput(slot,choices);cells.add(value(slot));
      }
      row.record.add("grid",object("width",3,"height",3,"cells",cells,"mirror",false));
      result=roles.get("result").copy();for(Aspect aspect:primal())cost.add(aspect,25);research="BASICARTIFACE";
    }else if(owner.equals(PHOTO)){
      ItemStack plate=roles.get("plate").copy();plate.setTagCompound(new NBTTagCompound());
      row.nativeCraftingInput(0,Collections.singletonList(choice(plate,object("kind","tags","meta",false,"keys",array(),"present",array(),"absent",array()))));
      List<RecipeRow.Ingredient> inks=new ArrayList<>();for(String name:inkNames)for(ItemStack template:NativeOreTable.copies(name))for(ItemStack item:examples.copies(template))inks.add(choice(item,object("kind","ore","name",name,"exclusive",false)));
      if(inks.isEmpty())return row.exclude(object("native",owner,"reason","native_photo_black_dye_predicate_empty","oreNames",names));
      for(int i=0;i<count;i++)row.nativeCraftingInput(1+i,Collections.singletonList(choice(roles.get("paper").copy(),object("kind","wildcard","meta",true,"nbt",true))));
      for(int i=0;i<count;i++)row.nativeCraftingInput(1+count+i,inks);
      result=roles.get("result").copy();result.stackSize=count;result.setTagCompound((NBTTagCompound)plate.getTagCompound().copy());
      cost.add(Aspect.AIR,5*count).add(Aspect.WATER,5*count).add(Aspect.ORDER,5*count);research="SCANCAMERA";
    }else{
      ItemStack matrix=roles.get("matrix").copy();row.nativeCraftingInput(0,Collections.singletonList(choice(matrix,object("kind","wildcard","meta",false,"nbt",true))));
      ItemStack phial=roles.get("phial").copy();NBTTagCompound tag=new NBTTagCompound();new AspectList().add(Aspect.AIR,4).writeToNBT(tag);phial.setTagCompound(tag);
      for(int i=0;i<count;i++)row.nativeCraftingInput(1+i,Collections.singletonList(choice(phial.copy(),object("kind","wildcard","meta",false,"nbt",true))));
      result=matrix.copy();NBTTagCompound outputTag=new NBTTagCompound();new AspectList().add(Aspect.AIR,count).writeToNBT(outputTag);result.setTagCompound(outputTag);
      for(Aspect aspect:primal())cost.add(aspect,2);research="NODECATALYZATION";
    }
    row.record.add("process",object("kind","arcaneSpecial","operation",operation,"nativeRecipe",owner,"count",count,"roles",roleIds,"inkNames",names,"aspects",aspectIds));
    row.record.add("magic",object("kind","arcane","aspects",Aspects.amounts(cost),"research",array(Magic.study(research)),"central",null,"instability",null,"payment",null,"creative",creative));
    row.itemOutput(0,result,10000);
    row.note("thaumcraft:inventoryRule",owner+"#matches/getCraftingResult",row.facts.locale().equals("zh_CN")
        ?"展示的是有效输入与产物样本；完整匹配、顺序、NBT 变换、数量及费用由专用原生过程描述，候选不是全部有效状态的穷举。工作台研究、法杖／创造豁免及更早注册可能影响执行。"
        :"Inputs and results are valid specimens. The dedicated native process defines matching, order, NBT changes, quantity and cost; choices do not enumerate every valid state. Workbench research, wand/creative payment and earlier registrations may affect execution.");
    return true;
  }
  private static Aspect[] primal(){return new Aspect[]{Aspect.AIR,Aspect.WATER,Aspect.FIRE,Aspect.ORDER,Aspect.ENTROPY,Aspect.EARTH};}
  private static RecipeRow.Ingredient choice(ItemStack stack,JsonObject rule){stack.stackSize=1;return new RecipeRow.Ingredient(stack,1,false,rule);}
  private void alive(){Jobs.checkpoint();if(closed)throw new Jobs.Fault("capture_closed","Native arcane reader is closed");}
  public void close(){closed=true;if(definitions!=null)definitions.close();examples.close();recipe=null;aspectRoot=null;roles.clear();aspects.clear();inkNames.clear();}
  private static Jobs.Fault fault(String message){return new Jobs.Fault("recipe_unsupported",message);}
  private static Jobs.Fault changed(){return new Jobs.Fault("recipe_changed","Native arcane source changed");}
}
