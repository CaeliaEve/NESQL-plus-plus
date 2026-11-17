package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonObject;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import thaumcraft.api.crafting.InfusionRecipe;
import thaumcraft.common.items.wands.ItemWandCasting;

/** Pinned Machina callbacks are stateless; the definition, not a GUI output, owns the law. */
final class MachinaInfusionSource implements AutoCloseable {
  static final String RECIPE="jcm2606.thaumicmachina.core.crafting.infusion.RecipeInfusionWandAugmentation";
  private static final String AUGMENT="jcm2606.thaumicmachina.wand.augmentation.";
  private InfusionRecipe recipe;
  private Object augmentation;
  private final String name;
  private final List<String> prerequisites;
  private final boolean definitionWand;
  private boolean closed;

  MachinaInfusionSource(InfusionRecipe recipe){
    MagicApi.version("MachinaInfusionSource","ThaumicMachina");
    this.recipe=recipe;augmentation=field(recipe,"augmentation");
    name=name(augmentation);prerequisites=prerequisites(augmentation);
    ItemStack declared=(ItemStack)field(InfusionRecipe.class,recipe,"recipeInput");
    definitionWand=declared!=null&&declared.getItem() instanceof ItemWandCasting;
  }
  private static Class<?> admitted(Object raw){
    if(raw==null)throw fault("Null Machina augmentation");
    Class<?> owner=raw.getClass();String name=owner.getName();
    if(!Arrays.asList("AugmentationChargeBuffer","AugmentationContactDischarge","AugmentationTaintCapping","AugmentationTaintedCore","AugmentationVisChannel").stream().anyMatch(s->name.equals(AUGMENT+s)))
      throw fault("Unadapted Machina augmentation callback: "+name);
    for(Field field:owner.getDeclaredFields())if(!Modifier.isStatic(field.getModifiers()))throw fault("Machina augmentation gained instance state");
    for(String method:Arrays.asList("getAugmentationName","getPrerequisiteAugmentations"))try{
      if(owner.getMethod(method).getDeclaringClass()!=owner)throw fault("Machina augmentation callback owner differs");
    }catch(NoSuchMethodException error){throw fault("Missing Machina augmentation callback");}
    return owner;
  }
  private static String name(Object raw){
    Class<?> owner=admitted(raw);Object result=invoke(owner,raw,"getAugmentationName",new Class<?>[0]);
    if(!(result instanceof String)||((String)result).isEmpty()||((String)result).length()>65535)throw fault("Invalid Machina augmentation name");
    return (String)result;
  }
  private static List<String> prerequisites(Object raw){
    Class<?> owner=admitted(raw);Object array=invoke(owner,raw,"getPrerequisiteAugmentations",new Class<?>[0]);
    List<String> result=new ArrayList<>();if(array==null)return result;
    if(!array.getClass().isArray()||Array.getLength(array)>4096)throw fault("Invalid Machina prerequisite array");
    for(int i=0;i<Array.getLength(array);i++)result.add(name(Array.get(array,i)));
    return result;
  }
  JsonObject describe(){verify();com.google.gson.JsonArray required=array();for(String name:prerequisites)required.add(value(name));return object("kind","wand","name",name,"prerequisites",required,"definitionWand",definitionWand);}
  boolean definitionWand(){verify();return definitionWand;}
  private static boolean has(ItemStack stack,String name){
    if(!stack.hasTagCompound())return false;
    NBTTagList list=stack.getTagCompound().getTagList("Augmentations",8);
    if(list.tagCount()>4096)throw fault("Machina augmentation NBT exceeds budget");
    for(int i=0;i<list.tagCount();i++)if(name.equals(list.getStringTagAt(i)))return true;
    return false;
  }
  boolean accepts(ItemStack stack){
    alive();if(!definitionWand||has(stack,name))return false;
    for(String required:prerequisites)if(!has(stack,required))return false;
    // The shipped matcher checks the DEFINITION's wand class, not the offered center.
    return true;
  }
  ItemStack specimen(ItemStack owned){alive();for(String required:prerequisites)if(!has(owned,required))append(owned,required);return owned;}
  ItemStack output(ItemStack owned){alive();ItemStack result=owned.copy();append(result,name);return result;}
  private static void append(ItemStack owned,String name){
    if(!owned.hasTagCompound())owned.setTagCompound(new NBTTagCompound());
    NBTTagCompound root=owned.getTagCompound();NBTTagList list=root.getTagList("Augmentations",8);
    if(list.tagCount()>=4096)throw fault("Machina augmentation output exceeds budget");
    list.appendTag(new NBTTagString(name));root.setTag("Augmentations",list);
  }
  void verify(){alive();if(field(recipe,"augmentation")!=augmentation)throw changed();}
  void finishVerification(){verify();if(!name.equals(name(augmentation))||!prerequisites.equals(prerequisites(augmentation))
      ||definitionWand!=(((ItemStack)field(InfusionRecipe.class,recipe,"recipeInput")).getItem() instanceof ItemWandCasting))throw changed();}
  private void alive(){Jobs.checkpoint();if(closed)throw new Jobs.Fault("capture_closed","Machina infusion source is closed");}
  public void close(){closed=true;recipe=null;augmentation=null;prerequisites.clear();}
  private static Jobs.Fault fault(String message){return new Jobs.Fault("recipe_unsupported",message);}
  private static Jobs.Fault changed(){return new Jobs.Fault("environment_changed","Machina infusion source changed");}
}
