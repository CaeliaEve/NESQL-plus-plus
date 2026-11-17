package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;

import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import java.util.*;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraftforge.fluids.*;

/** Borrowed native definitions. Batch guards are cheap; sealing checks the complete graph. */
final class ForestryRecipeBindings implements AutoCloseable {
  private final Class<?> handler;
  private Object[] roots;
  private List<Object> identities;
  private int[] sizes;

  ForestryRecipeBindings(Class<?> handler) {
    this.handler=handler; roots=registrationAnchors(handler);
    sizes=new int[roots.length];for(int i=0;i<roots.length;i++)sizes[i]=size(roots[i]);
    identities=observe();
  }

  static Object[] registrationAnchors(Class<?> handler) {
    String name=handler.getName();
    if(!supportsClass(name))throw unsupported("Unknown Forestry factory family");
    String machine=handler.getSimpleName().substring("NEIHandler".length());
    Object mode=field(type("forestry.api.core.ForestryAPI"),null,"activeMode");
    if(mode!=null&&!mode.getClass().getName().equals("forestry.core.config.GameMode"))throw unsupported("Unadapted Forestry difficulty callback");
    List<Object> roots=new ArrayList<>();Collections.addAll(roots,handler,machine.equals("Bottler")?forge("filledContainerMap",false):recipes(machine),mode);
    if(!machine.equals("Bottler")){
      Object manager=field(type("forestry.api.recipes.RecipeManagers"),null,Character.toLowerCase(machine.charAt(0))+machine.substring(1)+"Manager");
      if(manager==null)throw notReady();
      if(!manager.getClass().getName().equals("forestry.factory.recipes."+machine+"RecipeManager"))throw unsupported("Unadapted Forestry recipe manager");
      roots.add(manager);
    }
    if(machine.equals("Fermenter")||machine.equals("Moistener")){
      Object fuels=field(type("forestry.api.fuels.FuelManager"),null,machine.equals("Fermenter")?"fermenterFuel":"moistenerResource");
      table(fuels);roots.add(fuels);
    }
    if(machine.equals("Fabricator"))roots.add(recipes("FabricatorSmelting"));
    if(machine.equals("Squeezer")){
      Object containers=field(type("forestry.factory.recipes.SqueezerRecipeManager"),null,"containerRecipes");table(containers);roots.add(containers);roots.add(forge("filledContainerMap",false));
    }
    if(machine.equals("Bottler")||machine.equals("Squeezer")){roots.add(forge("containerFluidMap",false));roots.add(forge("emptyContainers",true));}
    return roots.toArray();
  }

  static boolean supportsClass(String name){if(name==null)return false;for(String machine:new String[]{"Fermenter","Moistener","Carpenter","Centrifuge","Still","Fabricator","Bottler","Squeezer"})if(name.equals("forestry.factory.recipes.nei.NEIHandler"+machine))return true;return false;}
  private static Object recipes(String machine){Object value=field(type("forestry.factory.recipes."+machine+"RecipeManager"),null,"recipes");if(value==null)throw notReady();if(value.getClass()!=HashSet.class||((Set<?>)value).size()>262144)throw unsupported("Unadapted Forestry recipe storage");return value;}
  private static void table(Object value){if(value==null)throw notReady();if(!value.getClass().getName().equals("forestry.core.utils.datastructures.ItemStackMap")||((Map<?,?>)value).size()>65536)throw unsupported("Unadapted Forestry native lookup storage");}
  private static Object forge(String name,boolean set){Object value=field(FluidContainerRegistry.class,null,name);if(value==null)throw notReady();if(value.getClass()!=(set?HashSet.class:HashMap.class)||size(value)>262144)throw unsupported("Unadapted Forge container table");return value;}
  private static int size(Object value){return value instanceof Collection?((Collection<?>)value).size():value instanceof Map?((Map<?,?>)value).size():-1;}

  void verifyRoots() {
    alive();Object[] actual=registrationAnchors(handler);
    if(actual.length!=roots.length)throw changed();
    for(int i=0;i<roots.length;i++)if(roots[i]!=actual[i]||size(actual[i])!=sizes[i])throw changed();
  }

  void verify() {
    verifyRoots();List<Object> actual=observe();
    if(actual.size()!=identities.size())throw changed();
    for(int i=0;i<actual.size();i++)if(actual.get(i)!=identities.get(i))throw changed();
  }

  private List<Object> observe() {
    Graph graph=new Graph();
    graph.visit(roots[1]);
    if(roots[2]!=null)graph.visit(field(roots[2],"floatSettings"));
    for(int i=3;i<roots.length;i++){Object value=roots[i];if(value instanceof Map||value instanceof Collection)graph.visit(value);else graph.pin(value);}
    return graph.values;
  }

  private static final class Graph {
    final List<Object> values=new ArrayList<>();
    long payloadBytes;
    void pin(Object value){Jobs.checkpoint();if(values.size()>=262144)throw unsupported("Forestry definition identity budget exceeded");values.add(value);}
    void collection(Object value){pin(value);Collection<?> source=(Collection<?>)value;if(source.size()>262144)throw unsupported("Forestry definitions exceed row budget");for(Object member:source)visit(member);}
    void visit(Object value){
      pin(value);if(value==null||value instanceof String||value instanceof Number||value instanceof Boolean)return;
      Class<?> owner=value.getClass();
      if(value instanceof Item){NativeStackAudit.identityOwner(value);return;}
      if(owner==ItemStack.class){ItemStack stack=(ItemStack)value;NativeStackAudit.audit(stack);pin(stack.getItem());nbt(stack.getTagCompound());return;}
      if(owner==FluidStack.class){FluidStack stack=(FluidStack)value;pin(stack.getFluid());nbt(stack.tag);return;}
      if(value instanceof Fluid)return;
      if(NativeOreStorage.oreView(value)){visit(NativeOreStorage.arrayList(value));return;}
      if(owner==ArrayList.class||owner==HashSet.class){collection(value);return;}
      if(owner==Object[].class||owner==ItemStack[].class){for(Object child:(Object[])value)visit(child);return;}
      if(owner==HashMap.class||owner==LinkedHashMap.class||owner==Hashtable.class||owner.getName().equals("forestry.core.utils.datastructures.ItemStackMap")||owner.getName().equals("com.google.common.collect.RegularImmutableMap")||owner.getName().equals("com.google.common.collect.SingletonImmutableBiMap")||owner.getName().equals("com.google.common.collect.EmptyImmutableBiMap")){
        Map<?,?> map=(Map<?,?>)value;if(map.size()>65536)throw unsupported("Forestry definition map exceeds budget");
        for(Map.Entry<?,?> entry:map.entrySet()){visit(entry.getKey());visit(entry.getValue());}return;
      }
      String[] fields;
      switch(owner.getName()){
        case "forestry.factory.recipes.FermenterRecipe":fields=new String[]{"resource","fluidResource","output"};break;
        case "forestry.factory.recipes.CentrifugeRecipe":fields=new String[]{"input","outputs"};break;
        case "forestry.factory.recipes.StillRecipe":fields=new String[]{"input","output"};break;
        case "forestry.factory.recipes.SqueezerRecipe":fields=new String[]{"resources","fluidOutput","remnants"};break;
        case "forestry.factory.recipes.SqueezerContainerRecipe":fields=new String[]{"emptyContainer","remnants"};break;
        case "forestry.factory.recipes.FabricatorRecipe":fields=new String[]{"plan","molten","internal"};break;
        case "forestry.factory.recipes.FabricatorSmeltingRecipe":fields=new String[]{"resource","product"};break;
        case "net.minecraftforge.fluids.FluidContainerRegistry$FluidContainerData":fields=new String[]{"fluid","filledContainer","emptyContainer"};break;
        case "net.minecraftforge.fluids.FluidContainerRegistry$ContainerKey":fields=new String[]{"container","stack"};break;
        case "forestry.factory.recipes.MoistenerRecipe":fields=new String[]{"resource","product"};break;
        case "forestry.factory.recipes.CarpenterRecipe":fields=new String[]{"liquid","box","internal"};break;
        case "forestry.core.recipes.ShapedRecipeCustom":fields=new String[]{"output","input"};break;
        case "forestry.api.fuels.FermenterFuel":fields=new String[]{"item"};break;
        case "forestry.api.fuels.MoistenerFuel":fields=new String[]{"item","product"};break;
        default:throw unsupported("Unadapted Forestry definition owner: "+owner.getName());
      }
      for(String name:fields)visit(field(value,name));
    }
    void nbt(NBTBase tag){
      NativeStackAudit.nbt(tag);payloadBytes+=CanonicalJson.bytes(TypedNbt.encode(tag)).length;
      if(payloadBytes>32L*1024*1024)throw unsupported("Forestry definition NBT exceeds cumulative budget");
      nodes(tag);
    }
    void nodes(NBTBase tag){
      pin(tag);if(tag==null)return;
      if(tag.getClass()==NBTTagCompound.class){Map<?,?> map=(Map<?,?>)field(tag,"tagMap");pin(map);for(Object child:map.values())nodes((NBTBase)child);}
      else if(tag.getClass()==NBTTagList.class){List<?> list=(List<?>)field(tag,"tagList");pin(list);for(Object child:list)nodes((NBTBase)child);}
      else if(tag.getClass()==NBTTagByteArray.class)pin(((NBTTagByteArray)tag).func_150292_c());
      else if(tag.getClass()==NBTTagIntArray.class)pin(((NBTTagIntArray)tag).func_150302_c());
    }
  }

  public void close(){roots=null;if(identities!=null)identities.clear();identities=null;}
  private void alive(){if(roots==null)throw new Jobs.Fault("capture_closed","Forestry source bindings are closed");}
  private static Jobs.Fault notReady(){return new Jobs.Fault("handler_not_ready","Forestry processing source is unavailable");}
  private static Jobs.Fault changed(){return new Jobs.Fault("environment_changed","Forestry native source or member identity changed");}
  private static Jobs.Fault unsupported(String text){return new Jobs.Fault("recipe_unsupported",text);}
}
