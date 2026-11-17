package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonObject;
import java.util.*;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

/** Native one-item rules, independent of display caches and test-injected contexts. */
final class GendustryFluidRecipes implements RegistryRecipes {
  private static final String ROOT="net.bdew.gendustry.";
  private final Class<?> handlerClass;
  private final boolean protein;
  private Object registry,machine,fluidModule;
  private Fluid fluid;
  private GendustrySourceTable table;
  private JsonObject config;
  private String signature;
  private Object[] configAnchors;
  private boolean closed;

  static boolean supportsClass(String name){return (ROOT+"nei.LiquifierHandler").equals(name)
      ||(ROOT+"nei.MutagenProducerHandler").equals(name);}
  static boolean supports(ICraftingHandler handler){return handler!=null&&supportsClass(handler.getClass().getName());}

  static Object[] registrationAnchors(Class<?> handler){
    boolean protein=(ROOT+"nei.LiquifierHandler").equals(handler.getName());
    if(!supportsClass(handler.getName()))throw fault("Unknown native Gendustry fluid handler");
    Object registry=source(protein),machine=machine(protein),fluids=module("config.Fluids");
    Object fluid=call(fluids,protein?"protein":"mutagen");
    List<Object> anchors=new ArrayList<>(Arrays.asList(handler,registry,field(registry,"values"),machine,fluids,fluid));
    Collections.addAll(anchors,GendustryConfiguration.anchors(machine));return anchors.toArray();
  }

  GendustryFluidRecipes(TemplateRecipeHandler handler){
    if(!supports(handler))throw fault("Unadapted Gendustry fluid handler");
    handlerClass=handler.getClass();protein=handlerClass.getName().endsWith("LiquifierHandler");
    MagicApi.version("GendustryFluidRecipes","gendustry");MagicApi.version("GendustryFluidRecipes","bdlib");
    try{
      registry=source(protein);machine=machine(protein);fluidModule=module("config.Fluids");
      Object value=call(fluidModule,protein?"protein":"mutagen");
      if(!(value instanceof Fluid))throw new Jobs.Fault("handler_not_ready","Gendustry native fluid is unavailable");
      fluid=(Fluid)value;
      if(!fluid.getName().equals(protein?"protein":"mutagen"))throw fault("Native Gendustry fluid role changed");
      table=new GendustrySourceTable(registry,ROOT+"fluids."+(protein?"ProteinSources$":"MutagenSources$"));
      configAnchors=GendustryConfiguration.anchors(machine);config=configuration();signature=CanonicalJson.digest(config);verify();
    }catch(RuntimeException|Error failure){close();throw failure;}
  }

  public int size(){alive();return table.entries.size();}
  public void verify(){
    alive();
    if(source(protein)!=registry||machine(protein)!=machine||module("config.Fluids")!=fluidModule
        ||call(fluidModule,protein?"protein":"mutagen")!=fluid)throw changed();
    Object[] current=GendustryConfiguration.anchors(machine);if(current.length!=configAnchors.length)throw changed();
    for(int i=0;i<current.length;i++)if(current[i]!=configAnchors[i])throw changed();
    table.verify();
  }

  public void finishVerification(){verify();table.finishVerification();if(!signature.equals(CanonicalJson.digest(configuration())))throw changed();}

  public boolean capture(int index,RecipeRow row){
    verify();if(index<0||index>=size())throw fault("Gendustry fluid source index outside catalogue");
    GendustrySourceTable.Entry entry=table.entries.get(index);
    if(entry.amount<=0)return row.exclude(exclusion(index));
    if(entry.amount>config.get("tankSize").getAsInt())throw fault("Gendustry batch exceeds the native output tank");
    ItemStack input=entry.specimen();
    row.itemInput(0,Collections.singletonList(new RecipeRow.Ingredient(input,1,false,entry.rule(row.facts))),false);
    FluidStack output=new FluidStack(fluid,entry.amount);row.fluidOutput(0,output);
    row.record.add("process",object("kind","gendustryFluid","version","1.9.4-GTNH",
        "machine",protein?"protein":"mutagen","input",0,"fluid",row.facts.fluid(output),
        "config",GendustryGeneticsRecipes.owned(config),"sources",table.facts()));
    row.note("gendustry:fluid-machine",ROOT+"machines."+(protein?"liquifier.TileLiquifier":"mproducer.TileMutagenProducer")
        +"#tryStart,tryFinish,isItemValidForSlot;bdlib.TileBaseProcessor",
        row.facts.locale().equals("zh_CN")
            ?"原生来源先检查精确元数据，再检查 32767；精确零值和负值会阻断通配。正常插入只接受正值。成功启动取用一件且无合成容器返还；加工中保留整批待输出流体，空间不足会等待，不丢弃溢出。直接修改库存绕过准入时，非正值也可能被取用但不形成工作批次。进度依实际储能推进，没有固定时长。"
            :"Native sources check exact metadata before 32767; exact zero and negative entries block wildcard fallback. Normal insertion accepts positive values only. Starting uses one item without a crafting-container return. The full fluid batch remains in progress until output space is available; overflow is not discarded. Direct inventory edits can bypass admission and consume a nonpositive source without a working batch. Progress depends on stored energy, not a fixed duration.");
    return true;
  }

  public JsonObject exclusion(int index){
    alive();GendustrySourceTable.Entry entry=table.entries.get(index);if(entry.amount>0)return null;
    return object("reason","native_input_slot_rejected","scope","registered_entry","nativeClass",ROOT+"fluids.FluidSourceRegistry",
        "condition","isItemValidForSlot requires getValue(stack) > 0","nativeValue",entry.amount,
        "item",net.minecraft.item.Item.itemRegistry.getNameForObject(entry.item),"metadata",entry.meta,
        "bypassBehavior","Direct inventory mutation bypasses slot admission and may consume one without a working batch");
  }

  private JsonObject configuration(){
    JsonObject value=object();
    for(String key:new String[]{"mjPerItem","powerUseRate","activationEnergy","maxStoredEnergy","maxReceivedEnergy","tankSize"}){
      Object number=GendustryConfiguration.value(machine,key);
      if(!(number instanceof Number)||!Double.isFinite(((Number)number).doubleValue()))throw fault("Invalid native Gendustry configuration: "+key);
      value.addProperty(key,(Number)number);
    }
    if(value.get("mjPerItem").getAsDouble()<=0||value.get("powerUseRate").getAsDouble()<0
        ||value.get("activationEnergy").getAsDouble()<0||value.get("tankSize").getAsInt()<=0)throw fault("Invalid native Gendustry processor parameters");
    return value;
  }

  public void close(){if(closed)return;closed=true;if(table!=null)table.close();table=null;registry=null;machine=null;fluidModule=null;fluid=null;config=null;signature=null;configAnchors=null;}
  private void alive(){Jobs.checkpoint();if(closed)throw new Jobs.Fault("capture_closed","Gendustry fluid reader is closed");}
  private static Object source(boolean protein){return module("fluids."+(protein?"ProteinSources":"MutagenSources"));}
  private static Object machine(boolean protein){return module("machines."+(protein?"liquifier.MachineLiquifier":"mproducer.MachineMutagenProducer"));}
  private static Object module(String name){return field(type(ROOT+name+"$"),null,"MODULE$");}
  private static Object call(Object target,String name){return invoke(target.getClass(),target,name,new Class<?>[0]);}
  private static Jobs.Fault changed(){return new Jobs.Fault("environment_changed","Gendustry native fluid source/configuration changed");}
  private static Jobs.Fault fault(String message){return new Jobs.Fault("recipe_unsupported",message);}
}
