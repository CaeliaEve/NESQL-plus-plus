package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;

import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.Gson;
import java.util.*;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;

/** Small oracle against the installed 0.0.51 codec, on owned state and a scoped registry. */
public final class NativeMatterManipulatorTest {
  public static void main(String[] args)throws Exception{GameTest.run();}
  static void run()throws Exception{
    String root=MatterManipulatorCodec.ROOT;
    Class<?> stateType=type(MatterManipulatorCodec.STATE),upgradeType=type(root+"common.items.MMUpgrades");
    List<Object> upgrades=Arrays.asList(upgradeType.getEnumConstants());
    Object architecture=field(type(root+"common.utils.Mods"),null,"ArchitectureCraft");
    try(NativeRegistryScope scope=new NativeRegistryScope()){
      scope.replace(architecture.getClass(),architecture,"checkedMod",true);
      scope.replace(architecture.getClass(),architecture,"modLoaded",false);
      List<NBTTagCompound> specimens=new ArrayList<>();specimens.add(null);
      NBTTagCompound empty=new NBTTagCompound();empty.setString("unknownRoot","discard this");specimens.add(empty);
      NBTTagCompound legacy=new NBTTagCompound(),config=new NBTTagCompound();config.setString("corners","obsolete");
      legacy.setTag("config",config);legacy.setInteger("dv",9);specimens.add(legacy);
      NBTTagCompound installed=new NBTTagCompound();installed.setByte("jv",(byte)1);
      int oldBit=(Integer)field(upgrades.get(0),"bit");int[] flags=new int[oldBit+1];flags[oldBit]=1;
      installed.setIntArray("installedUpgrades",flags);installed.setInteger("upgradeProvidedCapabilities",128);specimens.add(installed);
      try(MatterManipulatorCodec codec=new MatterManipulatorCodec(upgrades)){
        Gson nativeGson=(Gson)field(stateType,null,"GSON");
        for(NBTTagCompound tag:specimens){
          ItemStack input=new ItemStack(Items.paper,7,4);input.setTagCompound(tag==null?null:(NBTTagCompound)tag.copy());
          NBTTagCompound before=input.getTagCompound()==null?null:(NBTTagCompound)input.getTagCompound().copy();
          Object cache=field(nativeGson,"typeTokenCache");int size=((Map<?,?>)cache).size();
          NBTTagCompound normalized=codec.normalize(input);
          require(Objects.equals(before,input.getTagCompound()),"Adapter mutated borrowed state");
          require(field(nativeGson,"typeTokenCache")==cache&&((Map<?,?>)cache).size()==size,"Owned codec populated native Gson cache");
          Object nativeState=invoke(stateType,null,"load",new Class<?>[]{NBTTagCompound.class},tag==null?new NBTTagCompound():(NBTTagCompound)tag.copy());
          NBTTagCompound saved=(NBTTagCompound)invoke(stateType,nativeState,"save",new Class<?>[0]);
          require(normalized.equals(saved),"Owned normalization differs from installed native load/save");
          require(!normalized.hasKey("unknownRoot"),"0.0.51 must replace the entire root");
          for(Object upgrade:upgrades){
            int bit=(Integer)field(upgrade,"bit");if(MatterManipulatorCodec.bits(normalized).get(bit))continue;
            Object fresh=invoke(stateType,null,"load",new Class<?>[]{NBTTagCompound.class},tag==null?new NBTTagCompound():(NBTTagCompound)tag.copy());
            invoke(stateType,fresh,"installUpgrade",new Class<?>[]{upgradeType},upgrade);
            NBTTagCompound expected=(NBTTagCompound)invoke(stateType,fresh,"save",new Class<?>[0]);
            require(MatterManipulatorCodec.install(normalized,bit).equals(expected),"Upgrade bit or pre-install capabilities differ from native save");
          }
        }
        codec.finishVerification();
        scope.replace(Gson.class,nativeGson,"factories",new ArrayList<>((List<?>)field(nativeGson,"factories")));
        try{codec.finishVerification();throw new AssertionError("Replaced codec definition was accepted");}
        catch(Jobs.Fault expected){require(expected.code.equals("environment_changed"),"Wrong codec drift failure");}
      }
    }
    System.out.println("Matter 0.0.51 native codec, old-bit capabilities, ownership and source drift passed");
  }
  private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
