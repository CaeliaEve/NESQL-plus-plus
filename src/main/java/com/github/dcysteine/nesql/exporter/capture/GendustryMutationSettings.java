package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;
import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonObject;
import java.util.*;

/** Read a ready override cache, or project its exact cold definition; never call apply/default. */
final class GendustryMutationSettings implements AutoCloseable {
  private final Object machine;
  private Object[] anchors;
  private SortedMap<String,Object> settings;
  private Object defaultSetting;
  private String signature;
  private boolean closed;
  GendustryMutationSettings(){
    machine=GendustryGeneticsRecipes.module("machines.mutatron.MachineMutatron");
    try{anchors=roots();Read read=read();settings=read.values;defaultSetting=read.fallback;signature=read.signature();}
    catch(RuntimeException|Error error){close();throw error;}
  }
  Object setting(String uid){alive();return settings.containsKey(uid)?settings.get(uid):defaultSetting;}
  void verify(){
    alive();Object[] current=roots();if(current.length!=anchors.length)throw changed();
    for(int i=0;i<current.length;i++)if(current[i]!=anchors[i])throw changed();
  }
  void finishVerification(){verify();if(!signature.equals(read().signature()))throw changed();}
  private boolean ready(){return (((Number)field(machine,"bitmap$0")).intValue()&64)!=0;}
  private Object[] roots(){
    Object active=GendustryGeneticsRecipes.module("machines.mutatron.MachineMutatron");
    if(active!=machine)throw changed();
    if(ready()){
      Object raw=field(machine,"mutatronOverrides"),function=constant(raw);
      return new Object[]{machine,Boolean.TRUE,raw,NativeScala.storage(raw),function,field(function,"d$2")};
    }
    Object api=GendustryGeneticsRecipes.module("apiimpl.MutatronOverridesImpl"),tuning=GendustryGeneticsRecipes.module("config.Tuning");
    Object genetics=GendustryConfiguration.entry(tuning,"Genetics"),overrides=GendustryConfiguration.entry(genetics,"MutatronOverrides");
    return new Object[]{machine,Boolean.FALSE,api,field(api,"overrides"),tuning,GendustryConfiguration.raw(tuning),
        genetics,GendustryConfiguration.raw(genetics),overrides,GendustryConfiguration.raw(overrides)};
  }
  private Read read(){
    Read read=new Read();
    if(ready()){
      Object raw=field(machine,"mutatronOverrides"),function=constant(raw);read.fallback=settingValue(field(function,"d$2"));readTable(raw,read.values);return read;
    }
    Object api=GendustryGeneticsRecipes.module("apiimpl.MutatronOverridesImpl");readTable(field(api,"overrides"),read.values);
    Object tuning=GendustryGeneticsRecipes.module("config.Tuning"),genetics=GendustryConfiguration.entry(tuning,"Genetics"),overrides=GendustryConfiguration.entry(genetics,"MutatronOverrides");
    for(Object tuple:NativeScala.values(GendustryConfiguration.raw(overrides),65536)){
      Object key=NativeScala.pair(tuple,1),value=NativeScala.pair(tuple,2);if(!(key instanceof String))throw fault("Native override key is not text");
      if(value==null||value.getClass()!=type("net.bdew.lib.recipes.gencfg.EntryStr"))continue;
      Object text=field(value,"v");if(!(text instanceof String))throw fault("Native override text is null");
      String name=((String)text).toUpperCase(Locale.US);
      if(Arrays.asList("ENABLED","DISABLED","REQUIREMENTS").contains(name))read.values.put((String)key,field(type("net.bdew.gendustry.api.EnumMutationSetting"),null,name));
    }
    read.fallback=field(type("net.bdew.gendustry.api.EnumMutationSetting"),null,"ENABLED");return read;
  }
  private static Object constant(Object raw){
    if(raw==null||raw.getClass()!=type("scala.collection.immutable.Map$WithDefault"))throw fault("Unknown native override default map");
    Object function=field(raw,"d");
    if(function==null||function.getClass()!=type("scala.collection.immutable.Map$$anonfun$withDefaultValue$1"))throw fault("Unadapted native override default callback");
    return function;
  }
  private static void readTable(Object raw,Map<String,Object> target){
    Object storage=NativeScala.storage(raw);if(!storage.getClass().getName().startsWith("scala.collection.immutable."))throw fault("Native override backing is not immutable");
    for(Object tuple:NativeScala.values(storage,65536)){
      Object key=NativeScala.pair(tuple,1);if(!(key instanceof String))throw fault("Native override key is not text");
      target.put((String)key,settingValue(NativeScala.pair(tuple,2)));
    }
  }
  private static Object settingValue(Object value){
    if(value==null||value.getClass()!=type("net.bdew.gendustry.api.EnumMutationSetting"))throw fault("Invalid native mutation setting");return value;
  }
  private static final class Read{
    final SortedMap<String,Object> values=new TreeMap<>();Object fallback;
    String signature(){JsonObject rows=object();for(Map.Entry<String,Object> entry:values.entrySet())rows.addProperty(entry.getKey(),((Enum<?>)entry.getValue()).name());
      return CanonicalJson.digest(object("values",rows,"default",((Enum<?>)fallback).name()));}
  }
  private void alive(){Jobs.checkpoint();if(closed)throw new Jobs.Fault("capture_closed","Gendustry mutation settings are closed");}
  public void close(){closed=true;anchors=null;if(settings!=null)settings.clear();settings=null;defaultSetting=null;signature=null;}
  private static Jobs.Fault fault(String text){return new Jobs.Fault("recipe_unsupported",text);}
  private static Jobs.Fault changed(){return new Jobs.Fault("environment_changed","Gendustry mutation override definition or cache state changed");}
}
