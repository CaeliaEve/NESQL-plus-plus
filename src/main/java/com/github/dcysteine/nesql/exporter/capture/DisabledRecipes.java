package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonObject;
import java.util.*;
import net.minecraftforge.oredict.OreDictionary;

/** Current-environment native disable proofs, never an active-machine recipe adapter. */
final class DisabledRecipes extends ReaderSnapshot implements RegistryRecipes {
  private enum Family {
    ROCK("tonius.neiintegration.mods.railcraft.RecipeHandlerRockCrusher", "Railcraft", "9.16.33",
        "mods.railcraft.common.util.misc.Game", "isGTNH", "mods.railcraft.common.blocks.machine.alpha.TileRockCrusher", "native_machine_disabled"),
    COMPRESSION("fox.spiteful.avaritia.compat.nei.CompressionHandler", "Avaritia", "1.77",
        "fox.spiteful.avaritia.Avaritia", "isDreamCraftLoaded", "fox.spiteful.avaritia.tile.TileEntityCompressor", "native_recipe_lookup_disabled");
    final String handler, mod, version, owner, flag, machine, reason;
    Family(String handler, String mod, String version, String owner, String flag, String machine, String reason) {
      this.handler=handler; this.mod=mod; this.version=version; this.owner=owner; this.flag=flag;
      this.machine=machine; this.reason=reason;
    }
    boolean disabled() { return (Boolean) field(type(owner), null, flag); }
  }
  private final Family family;
  private final List<String> types=new ArrayList<>();
  private final List<Integer> indices=new ArrayList<>();
  private Object manager;
  private List<?> registry;
  private Object[] members;
  private int[] oreIds;
  private OreTable ores;

  private static Family family(ICraftingHandler handler) {
    if(handler!=null)for(Family family:Family.values())
      if(handler.getClass().getName().equals(family.handler))
        return handler.getClass()==type(family.handler)?family:null;
    return null;
  }
  static boolean supports(ICraftingHandler handler) {
    Family family=family(handler);return family!=null&&family.disabled();
  }
  static Object[] registrationAnchors(ICraftingHandler handler) {
    Family family=family(handler);
    if(family==null)throw fault("Unknown disabled native handler");
    requireDisabled(family);Object[] source=source(family);
    return new Object[]{handler.getClass(),source[0],source[1]};
  }

  DisabledRecipes(TemplateRecipeHandler handler) {
    family=family(handler);
    if(family==null)throw fault("Unknown disabled native handler");
    try {
      requireDisabled(family);Object[] source=source(family);
      manager=source[0];registry=(List<?>)source[1];members=registry.toArray();
      oreIds=new int[members.length];Arrays.fill(oreIds,-1);
      for(int index=0;index<members.length;index++) {
        Jobs.checkpoint();Object recipe=members[index];
        if(family==Family.ROCK) {
          if(recipe==null)continue; // Native loadAllRecipes omits null entries.
          if(!type("mods.railcraft.api.crafting.IRockCrusherRecipe").isInstance(recipe))
            throw fault("Invalid disabled rock-crusher registration");
          // The disable flag is decisive. No recipe getter is needed, including custom implementations.
        } else {
          if(recipe==null)throw fault("Null disabled compressor recipe");
          Class<?> owner=recipe.getClass();
          if(owner==type("fox.spiteful.avaritia.crafting.CompressOreRecipe")) {
            int id=(Integer)field(recipe,"oreID");oreIds[index]=id;
            if(ores==null)ores=new OreTable();
            if(ores.observe(id).members.length==0)continue;
          } else if(owner!=type("fox.spiteful.avaritia.crafting.CompressorRecipe"))
            throw fault("Unadapted disabled compressor enumeration owner");
        }
        types.add(recipe.getClass().getName());indices.add(index);
      }
      finishVerification();
    } catch(RuntimeException|Error failure){close();throw failure;}
  }

  public int size(){verify();return types.size();}
  public boolean capture(int index,RecipeRow row){verify();types.get(index);return false;}
  public void verify(){
    checkOpen();requireDisabled(family);Object[] current=source(family);
    if(current[0]!=manager||current[1]!=registry||registry.size()!=members.length)throw changed();
    if(ores!=null)ores.verify();
  }
  public void finishVerification(){
    verify();
    for(int i=0;i<members.length;i++) {
      Jobs.checkpoint();if(registry.get(i)!=members[i])throw changed();
      if(family==Family.COMPRESSION&&members[i]!=null&&members[i].getClass()==type("fox.spiteful.avaritia.crafting.CompressOreRecipe")
          &&(Integer)field(members[i],"oreID")!=oreIds[i])throw changed();
    }
    if(ores!=null)ores.finishVerification();
  }
  public JsonObject handlerExclusion(){
    verify();return object("reason",family.reason,"scope","current_environment","mod",family.mod,
        "version",family.version,"condition",family.owner+"."+family.flag,"value",true,"machine",family.machine);
  }
  public JsonObject exclusion(int index){
    JsonObject proof=handlerExclusion();
    proof.addProperty("sourceClass",types.get(index));proof.addProperty("nativeIndex",indices.get(index));return proof;
  }
  @Override protected void release(){
    types.clear();indices.clear();manager=null;registry=null;members=null;oreIds=null;
    if(ores!=null)ores.close();ores=null;
  }

  private static Object[] source(Family family) {
    version("DisabledRecipes",family.mod);Object manager,raw;
    if(family==Family.COMPRESSION) {
      manager=type("fox.spiteful.avaritia.crafting.CompressorManager");
      raw=field((Class<?>)manager,null,"recipes");
    } else {
      MagicApi.version("DisabledRecipes","neiintegration");
      manager=field(type("mods.railcraft.api.crafting.RailcraftCraftingManager"),null,"rockCrusher");
      if(manager==null)throw new Jobs.Fault("handler_not_ready","Rock-crusher manager is unavailable");
      if(manager.getClass()!=type("mods.railcraft.common.util.crafting.RockCrusherCraftingManager"))
        throw fault("Unadapted rock-crusher manager owner");
      raw=field(manager,"recipes");
    }
    if(raw==null)throw new Jobs.Fault("handler_not_ready","Disabled native registry is unavailable");
    if(raw.getClass()!=ArrayList.class)throw fault("Unadapted disabled native registry storage");
    if(((List<?>)raw).size()>4096)throw new Jobs.Fault("recipe_limit","Disabled identity proof budget exceeded");
    return new Object[]{manager,raw};
  }
  private static void requireDisabled(Family family) {
    if(!family.disabled())throw new Jobs.Fault("environment_changed",family.mod+" native disable guard changed; no exclusion is valid");
  }

  /** Only native safeOre's enumeration predicate is observed; no outputs or costs are queried. */
  private static final class OreTable implements AutoCloseable {
    private List<?> names=table("idToName"),stacks=table("idToStack"),views=table("idToStackUn");
    private final int[] sizes={names.size(),stacks.size(),views.size()};
    private final Map<Integer,Ore> observed=new HashMap<>();
    private long members;
    Ore observe(int id) {
      Ore found=observed.get(id);if(found!=null)return found;
      if(id<0||id>=names.size()||id>=stacks.size()||id>=views.size())
        throw new Jobs.Fault("native_error","Compressor ore ID is not registered; reading it would create a native Unknown ore");
      Object raw=stacks.get(id),view=views.get(id),name=names.get(id);
      if(!(name instanceof String)||raw==null||raw.getClass()!=ArrayList.class
          ||view==null||view.getClass()!=type("net.minecraftforge.oredict.OreDictionary$UnmodifiableArrayList")
          ||field(view,"list")!=raw)throw fault("Unadapted compressor native ore binding");
      List<?> entries=(List<?>)raw;
      if((members+=entries.size())>262144)throw new Jobs.Fault("recipe_limit","Disabled compressor ore proof budget exceeded");
      Ore ore=new Ore(name,view,entries);observed.put(id,ore);return ore;
    }
    void verify() {
      if(names!=table("idToName")||stacks!=table("idToStack")||views!=table("idToStackUn")
          ||names.size()!=sizes[0]||stacks.size()!=sizes[1]||views.size()!=sizes[2])throw changed();
    }
    void finishVerification() {
      verify();
      for(Map.Entry<Integer,Ore> observed:observed.entrySet()) {
        Jobs.checkpoint();int id=observed.getKey();Ore ore=observed.getValue();
        if(names.get(id)!=ore.name||stacks.get(id)!=ore.entries||views.get(id)!=ore.view
            ||field(ore.view,"list")!=ore.entries||ore.entries.size()!=ore.members.length)throw changed();
        for(int i=0;i<ore.members.length;i++)if(ore.entries.get(i)!=ore.members[i])throw changed();
      }
    }
    public void close(){names=null;stacks=null;views=null;observed.clear();}
    private static List<?> table(String name) {
      Object value=field(OreDictionary.class,null,name);
      if(value==null)throw new Jobs.Fault("handler_not_ready","Compressor ore table is unavailable");
      if(value.getClass()!=ArrayList.class)throw fault("Unadapted compressor ore table storage");
      return (List<?>)value;
    }
  }
  private static final class Ore {
    final Object name,view;final List<?> entries;final Object[] members;
    Ore(Object name,Object view,List<?> entries){this.name=name;this.view=view;this.entries=entries;members=entries.toArray();}
  }
  private static Jobs.Fault fault(String message){return new Jobs.Fault("recipe_unsupported",message);}
  private static Jobs.Fault changed(){return new Jobs.Fault("environment_changed","Disabled native source or enumeration proof changed");}
}
