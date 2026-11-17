package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import java.lang.reflect.Field;

/** Cold projection must equal the real lazy getter without initializing its cache. */
final class GendustryConfigTest {
  static void run()throws Exception{
    try(NativeRegistryScope scope=new NativeRegistryScope()){
      Class<?> nativeType=type("net.bdew.gendustry.machines.sampler.MachineSampler$"),sectionType=type("net.bdew.lib.recipes.gencfg.ConfigSection");
      Object machine=scope.allocate(nativeType),section=sectionType.getConstructor(String.class).newInstance("fixture.");
      Field tuning=type("net.bdew.lib.machine.Machine").getDeclaredField("tuning");tuning.setAccessible(true);tuning.set(machine,section);
      Class<?> entryType=type("net.bdew.lib.recipes.gencfg.ConfigEntry"),numberType=type("net.bdew.lib.recipes.gencfg.EntryDouble");
      for(double amount:new double[]{-0.5,2147483647.5}){
        Object number=numberType.getConstructor(double.class).newInstance(amount);
        invoke(sectionType,section,"set",new Class<?>[]{String.class,entryType},"LabwareConsumeChance",number);
        Field bitmap=nativeType.getDeclaredField("bitmap$0");bitmap.setAccessible(true);bitmap.setByte(machine,(byte)0);
        Object projected=GendustryConfiguration.value(machine,"labwareConsumeChance");
        if(bitmap.getByte(machine)!=0)throw new AssertionError("Projection initialized the borrowed lazy cache");
        Object actual=invoke(nativeType,machine,"labwareConsumeChance",new Class<?>[0]);
        if(!projected.equals(actual))throw new AssertionError("Native round-to-long then narrow-to-int semantics changed");
        Field cached=nativeType.getDeclaredField("labwareConsumeChance");cached.setAccessible(true);cached.setInt(machine,37);
        if(!GendustryConfiguration.value(machine,"labwareConsumeChance").equals(37))throw new AssertionError("Initialized native cache was replaced by current tuning");
      }
    }
  }
}
