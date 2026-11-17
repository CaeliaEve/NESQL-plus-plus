package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import java.lang.reflect.Field;
import java.util.*;

/** Pinned Gendustry 1.9.4 / bdlib 1.11 lazy values, projected without changing bitmap or cache. */
final class GendustryConfiguration {
  private static final String CFG="net.bdew.lib.recipes.gencfg.";
  private GendustryConfiguration(){}
  static Object value(Object machine,String key){
    int mask=mask(machine,key),bitmap=((Number)field(machine,"bitmap$0")).intValue();
    if((bitmap&mask)!=0)return field(machine,key);
    Object tuning=field(machine,"tuning"),entry=entry(tuning,configKey(key));
    try{
      Field target=machine.getClass().getDeclaredField(key);
      if(target.getType()==boolean.class){
        if(entry.getClass()!=type(CFG+"EntryStr"))throw fault("Native boolean tuning is not a string");
        Object text=field(entry,"v");if(!(text instanceof String))throw fault("Native boolean tuning is null");
        Set<String> trueValues=new HashSet<>();
        for(Object value:NativeScala.values(field(tuning,"trueVals"),65536)){
          if(!(value instanceof String))throw fault("Native boolean truth set contains non-text values");trueValues.add((String)value);
        }
        return trueValues.contains(((String)text).toLowerCase(Locale.US));
      }
      if(entry.getClass()!=type(CFG+"EntryDouble"))throw fault("Native numeric tuning is not an EntryDouble");
      double number=(Double)field(entry,"v");
      if(target.getType()==int.class)return (int)Math.round(number);
      if(target.getType()==float.class)return (float)number;
      throw fault("Unadapted native tuning scalar type: "+key);
    }catch(NoSuchFieldException error){throw fault("Missing pinned native tuning cache: "+key);}
  }
  static Object[] anchors(Object machine){
    name(machine);Object tuning=field(machine,"tuning");
    return new Object[]{machine,tuning,tuning==null?null:raw(tuning),tuning==null?null:field(tuning,"trueVals")};
  }
  static Object entry(Object section,String key){
    for(Object tuple:NativeScala.values(raw(section),65536)){
      Object name=NativeScala.pair(tuple,1);if(!(name instanceof String))throw fault("Native tuning key is not text");
      if(key.equals(name)){Object value=NativeScala.pair(tuple,2);if(value==null)throw fault("Native tuning entry is null: "+key);return value;}
    }
    throw fault("Native tuning key is missing: "+key);
  }
  static Object raw(Object section){
    if(section==null)throw new Jobs.Fault("handler_not_ready","Gendustry machine tuning has not been loaded");
    if(section.getClass()!=type(CFG+"ConfigSection")&&section.getClass()!=type("net.bdew.gendustry.config.Tuning$"))throw fault("Unknown native tuning section owner");
    Object raw=field(section,"raw"),storage=NativeScala.storage(raw);
    if(!storage.getClass().getName().startsWith("scala.collection.immutable."))throw fault("Native tuning storage is not an immutable map");return raw;
  }
  private static String configKey(String key){
    if(key.equals("secretChance"))return "SecretMutationChance";
    if(key.equals("dnaPerItem"))return "DNAPerItem";if(key.equals("dnaTankSize"))return "DNATankSize";
    return Character.toUpperCase(key.charAt(0))+key.substring(1);
  }
  private static String name(Object machine){
    if(machine==null)throw new Jobs.Fault("handler_not_ready","Gendustry native machine is unavailable");
    String name=machine.getClass().getName();
    if(machine.getClass()!=type(name)||!name.startsWith("net.bdew.gendustry.machines.")||!name.endsWith("$"))throw fault("Unknown native machine module owner");
    String simple=machine.getClass().getSimpleName();
    if(!simple.startsWith("Machine")||simple.length()<=8)throw fault("Unknown native machine module name");
    simple=simple.substring("Machine".length(),simple.length()-1);
    String packageName=simple.equals("MutagenProducer")?"mproducer":simple.toLowerCase(Locale.ROOT);
    if(!name.equals("net.bdew.gendustry.machines."+packageName+".Machine"+simple+"$"))throw fault("Unknown native machine module package");return simple;
  }
  private static int mask(Object machine,String key){
    String name=name(machine);int base;
    switch(name){case "Imprinter":base=8;break;case "Sampler":case "Transposer":case "Liquifier":case "MutagenProducer":base=2;break;
      case "Mutatron":base=128;break;case "Replicator":base=32;break;case "Extractor":base=4;break;default:throw fault("Unadapted native machine configuration");}
    int common=Arrays.asList("mjPerItem","powerUseRate","maxReceivedEnergy","activationEnergy","maxStoredEnergy").indexOf(key);
    if(common>=0)return base<<common;
    switch(name+"#"+key){
      case "Imprinter#labwareConsumeChance":case "Sampler#labwareConsumeChance":case "Transposer#labwareConsumeChance":case "Liquifier#tankSize":case "MutagenProducer#tankSize":case "Mutatron#tankSize":case "Replicator#dnaTankSize":case "Extractor#tankSize":return 1;
      case "Imprinter#deathChanceNatural":case "Mutatron#mutagenPerItem":case "Replicator#proteinTankSize":case "Extractor#labwareConsumeChance":return 2;
      case "Imprinter#deathChanceArtificial":case "Mutatron#labwareConsumeChance":case "Replicator#dnaPerItem":return 4;
      case "Mutatron#degradeChanceNatural":case "Replicator#proteinPerItem":return 8;
      case "Mutatron#deathChanceArtificial":case "Replicator#makePristineBees":return 16;
      case "Mutatron#secretChance":return 32;
      default:throw fault("Unadapted pinned machine tuning field: "+name+"#"+key);
    }
  }
  private static Jobs.Fault fault(String text){return new Jobs.Fault("recipe_unsupported",text);}
}
