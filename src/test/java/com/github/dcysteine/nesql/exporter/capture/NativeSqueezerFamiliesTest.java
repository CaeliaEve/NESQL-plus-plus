package com.github.dcysteine.nesql.exporter.capture;

import com.google.gson.*;
import cpw.mods.fml.common.*;
import net.minecraft.item.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.*;
import java.lang.reflect.Field;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Runs the actual pinned container methods on owned stacks, without a world or mod startup. */
final class NativeSqueezerFamiliesTest {
    private static int next=30200;
    private static int nextBlock=3800;

    static void run() throws Exception {
        Map<String,ModContainer> mods=new HashMap<>();
        for(String[] row:new String[][]{{"AWWayofTime","1.7.52"},{"TConstruct","1.13.57-GTNH"},
                {"gregtech_nh","5.09.51.482"},{"ae2fc","1.4.120-gtnh"},{"WitchingGadgets","1.7.25-GTNH"},
                {"EnderIO","2.9.28"},{"ExtraUtilities","1.2.12"},{"GalacticraftCore","3.3.13-GTNH"},
                {"GalacticraftMars","3.3.13-GTNH"},{"GalaxySpace","1.1.121-GTNH"},{"gravisuiteneo","1.3.6"},
                {"hodgepodge","2.6.112"},{"Automagy","0.28.2"}}){
            ModMetadata meta=new ModMetadata();meta.modId=row[0];meta.version=row[1];mods.put(row[0],new DummyModContainer(meta));
        }
        cpw.mods.fml.relauncher.ReflectionHelper.setPrivateValue(Loader.class,Loader.instance(),mods,"namedMods");
        String family=System.getProperty("nesql.nativeFamily","");
        if(family.endsWith("canisters"))canisters();
        else if(family.endsWith("automagy"))automagy();
        else if(family.endsWith("metacells"))NativeSqueezerGtTest.metacells();
        else if(family.endsWith("pump"))pump();
        else if(family.endsWith("gt"))NativeSqueezerGtTest.run();
        else simple();
        System.out.println("Native squeezer container families: loaded-fluid reads, native drain, disjoint selection, key conflicts passed");
    }

    private static void pump() throws Exception {
        Item pump=item("gtPlusPlus.core.item.tool.misc.ItemGregtechPump","fixture:squeezer_pump");
        fieldSet(pump,"mOffset",(short)1000);
        JsonArray unrelated=array(object("key",object("kind","item","registry","minecraft:bucket")));
        String registry=Item.itemRegistry.getNameForObject(pump);
        JsonObject descriptor=new SqueezerCallbacks(unrelated).describe(registry,pump);
        require(descriptor.get("kind").getAsString().equals("noContainerRule"),"GT++ pump remains an unsupported squeezer callback");
        require(descriptor.get("readEffect").getAsString().equals("gtppPump"),"Pump initialization cannot be labeled preserve");
        JsonArray observations=array();
        for(int meta:new int[]{Integer.MIN_VALUE,0,1000,1001,1004,1005,Integer.MAX_VALUE}) {
            ItemStack offered=new ItemStack(pump,3,meta);
            observePump(observations,descriptor,"fresh-"+meta,offered);
        }
        ItemStack partial=new ItemStack(pump,2,1002);NBTTagCompound tag=new NBTTagCompound();
        tag.setString("retained","opaque");tag.setString("capacityInit","present-wrong-type");tag.setInteger("mCapacity",37);
        tag.setString("mFluid","water");tag.setInteger("mFluidAmount",600);partial.setTagCompound(tag);
        observePump(observations,descriptor,"missing-init-with-capacity",partial);
        for(net.minecraft.nbt.NBTBase marker:new net.minecraft.nbt.NBTBase[]{new net.minecraft.nbt.NBTTagByte((byte)0),new net.minecraft.nbt.NBTTagString("present")}){
            ItemStack existing=partial.copy();existing.getTagCompound().setTag("mInit",marker);
            observePump(observations,descriptor,"present-init-"+marker.getId(),existing);
        }
        ItemStack loaded=new ItemStack(pump,1,1001);tag=new NBTTagCompound();
        tag.setBoolean("mInit",true);tag.setString("mFluid","water");tag.setInteger("mFluidAmount",600);loaded.setTagCompound(tag);
        noRule(pump,loaded,"gtppPump");observePump(observations,descriptor,"loaded",loaded);
        java.nio.file.Path out=java.nio.file.Paths.get("build/native-tests/squeezer-pump-observations.json");
        java.nio.file.Files.write(out,new GsonBuilder().setPrettyPrinting().create().toJson(observations).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static void observePump(JsonArray observations,JsonObject descriptor,String name,ItemStack offered) throws Exception {
        JsonObject before=SqueezerRules.stack(offered);
        Object empty=invoke(type("forestry.core.fluids.FluidHelper"),null,"getEmptyContainer",new Class<?>[]{ItemStack.class},offered);
        require(empty==null||((ItemStack)empty).getItem()==offered.getItem(),"Pump drain escaped its own registry");
        JsonObject after=SqueezerRules.stack(offered);
        observations.add(object("name",name,"program",object("ordinary",array(),"containers",array(),"filled",array(),"dynamic",array(descriptor)),
                "before",array(before),"after",array(after),"selected",null));
    }

    private static void simple() throws Exception {
        Item sigil=item("WayofTime.alchemicalWizardry.common.items.sigil.SigilFluid","fixture:squeezer_sigil");
        fieldSet(sigil,"capacity",1000);
        noRule(sigil,filled(sigil,"Fluid",600),"preserve");
        Item tinker=item("tconstruct.smeltery.itemblocks.LavaTankItemBlock","fixture:squeezer_tinker");
        noRule(tinker,filled(tinker,"Fluid",600),"preserve");
        Item flask=item("gregtech.common.items.ItemVolumetricFlask","fixture:squeezer_flask");
        noRule(flask,filled(flask,"Fluid",600),"preserve");
        Item machine=item("gregtech.common.blocks.ItemMachines","fixture:squeezer_gt_machine");
        noRule(machine,filled(machine,"mFluid",600),"preserve");
        Item certus=item("com.glodblock.github.common.item.ItemCertusQuartzTank","fixture:squeezer_certus");
        noRule(certus,filled(certus,"tileEntity",600),"preserve");
        Item crystal=item("witchinggadgets.common.items.ItemCrystalCapsule","fixture:squeezer_crystal");
        ItemStack crystalStack=new ItemStack(crystal);NBTTagCompound crystalNbt=new NBTTagCompound();
        crystalNbt.setString("fluid","water");crystalNbt.setString("retained","opaque");crystalStack.setTagCompound(crystalNbt);
        noRule(crystal,crystalStack,"preserve");
        Item ender=item("crazypants.enderio.machine.tank.BlockItemTank","fixture:squeezer_ender_tank");
        ItemStack enderStack=filled(ender,"tankContents",600);enderStack.getTagCompound().setInteger("tankType",0);
        noRule(ender,enderStack,"preserve");
        Item drum=item("com.rwtema.extrautils.item.ItemBlockDrum","fixture:squeezer_drum");
        fieldSet(drum,"slaveItem",new ItemFluidContainer(1000));
        noRule(drum,filled(drum,"Fluid",600),"preserve");
    }

    private static void canisters() throws Exception {
        Object apis=field(type("cpw.mods.fml.common.ModAPIManager"),null,"INSTANCE");
        if(field(apis,"apiContainers")==null)fieldSet(apis,"apiContainers",new HashMap<String,Object>());
        Item oil=item("micdoodle8.mods.galacticraft.core.items.ItemOilCanister","fixture:squeezer_gc_oil");
        Item fuel=item("micdoodle8.mods.galacticraft.core.items.ItemFuelCanister","fixture:squeezer_gc_fuel");
        Item nitrogen=item("micdoodle8.mods.galacticraft.planets.asteroids.items.ItemCanisterLiquidNitrogen","fixture:squeezer_gc_nitrogen");
        Item oxygen=item("micdoodle8.mods.galacticraft.planets.asteroids.items.ItemCanisterLiquidOxygen","fixture:squeezer_gc_oxygen");
        Item methane=item("micdoodle8.mods.galacticraft.planets.asteroids.items.ItemCanisterMethane","fixture:squeezer_gc_methane");
        staticSet(type("micdoodle8.mods.galacticraft.core.items.GCItems"),"oilCanister",oil);
        for(Item canister:new Item[]{oil,fuel,nitrogen,oxygen,methane}){
            fieldSet(canister,"allowedFluid","water");fieldSet(canister,"capacity",1000);
            ItemStack offered=new ItemStack(canister,1,401);offered.setTagCompound(new NBTTagCompound());
            offered.getTagCompound().setString("retained","opaque");
            noRule(canister,offered,"preserve",oil);
        }
        Item bio=item("galaxyspace.core.item.ItemBioFuelCanister","fixture:squeezer_gs_bio");
        staticSet(type("galaxyspace.core.register.GSItems"),"BioFuelCanister",bio);
        fieldSet(bio,"allowedFluid","water");fieldSet(bio,"capacity",1000);
        noRule(bio,new ItemStack(bio,1,401),"preserve");
        Item plasma=item("com.gtnewhorizons.gravisuiteneo.items.ItemPlasmaCell","fixture:squeezer_plasma");
        noRule(plasma,filled(plasma,"Fluid",600),"ensureCompound");
        ItemStack empty=new ItemStack(plasma);
        require(((IFluidContainerItem)plasma).getFluid(empty)==null&&empty.getTagCompound()!=null
                &&empty.getTagCompound().hasNoTags(),"Actual GraviSuiteNeo read did not create the empty NBT compound");
    }

    private static void automagy() throws Exception {
        Item tank=item("tuhljin.automagy.blocks.ItemBlockThirstyTank","fixture:squeezer_thirsty");
        require(tank instanceof IFluidContainerItem,"Actual pinned Hodgepodge mixin was not applied to the fixture");
        ItemStack stack=new ItemStack(tank);stack.setTagCompound(new FluidStack(FluidRegistry.WATER,600).writeToNBT(new NBTTagCompound()));
        stack.getTagCompound().setString("retained","opaque");noRule(tank,stack,"preserve");
    }

    /** A wrong noFluid classification, or broad admission despite a key conflict, must fail here. */
    private static void noRule(Item item,ItemStack offered,String effect) throws Exception {
        noRule(item,offered,effect,item);
    }
    private static void noRule(Item item,ItemStack offered,String effect,Item expectedEmpty) throws Exception {
        String registry=Item.itemRegistry.getNameForObject(item);
        IFluidContainerItem callback=(IFluidContainerItem)item;
        JsonObject before=SqueezerRules.stack(offered);
        FluidStack nativeFluid=callback.getFluid(offered);
        require(nativeFluid!=null&&nativeFluid.amount>0,"Fixture did not contain native fluid: "+registry);
        ItemStack empty=(ItemStack)invoke(type("forestry.core.fluids.FluidHelper"),null,"getEmptyContainer",new Class<?>[]{ItemStack.class},offered);
        require(empty!=null&&empty.getItem()==expectedEmpty,"Native drain produced the wrong empty item: "+registry);
        require(before.equals(SqueezerRules.stack(offered)),"Native read/drain changed the offered original: "+registry);
        JsonArray unrelated=array(object("key",object("kind","item","registry","minecraft:bucket")));
        JsonObject descriptor=new SqueezerCallbacks(unrelated).describe(registry,item);
        require(descriptor.get("kind").getAsString().equals("noContainerRule"),"Loaded native fluid family remains unsupported: "+item.getClass().getName());
        require(descriptor.get("readEffect").getAsString().equals(effect),"Read NBT effect was not preserved: "+registry);
        require(member(descriptor.getAsJsonArray("drainedRegistries"),registry),"Source registry was omitted from disjoint proof: "+registry);
        require(member(descriptor.getAsJsonArray("drainedRegistries"),Item.itemRegistry.getNameForObject(expectedEmpty)),"Native empty target was omitted from disjoint proof: "+registry);
        for(JsonObject key:new JsonObject[]{object("kind","item","registry",registry),
                object("kind","stack","stack",SqueezerRules.stack(empty)),
                object("kind","ore","members",array(SqueezerRules.stack(empty)))}){
            JsonObject conflicting=new SqueezerCallbacks(array(object("key",key))).describe(registry,item);
            require(conflicting.get("kind").getAsString().equals("unsupported"),"A real container-key intersection acquired a non-match proof: "+registry);
        }
    }

    private static ItemStack filled(Item item,String key,int amount){
        ItemStack stack=new ItemStack(item);NBTTagCompound root=new NBTTagCompound();
        root.setTag(key,new FluidStack(FluidRegistry.WATER,amount).writeToNBT(new NBTTagCompound()));
        root.setString("retained","opaque");stack.setTagCompound(root);return stack;
    }
    private static Item item(String name,String registry) throws Exception {
        Item item=(Item)NativeSqueezerWorkTest.allocate(type(name));
        Field delegate=Item.class.getField("delegate");delegate.setAccessible(true);
        delegate.set(item,new cpw.mods.fml.common.registry.RegistryDelegate.Delegate<>(item,Item.class));
        if(item instanceof ItemBlock){
            net.minecraft.block.Block block=new net.minecraft.block.Block(net.minecraft.block.material.Material.rock){};
            fieldSet(item,"field_150939_a",block);int id=nextBlock++;
            net.minecraft.block.Block.blockRegistry.addObject(id,registry,block);Item.itemRegistry.addObject(id,registry,item);
        }else Item.itemRegistry.addObject(next++,registry,item);
        return item;
    }
    private static void fieldSet(Object value,String name,Object content) throws Exception {
        for(Class<?> owner=value.getClass();owner!=null;owner=owner.getSuperclass()){
            try{Field field=owner.getDeclaredField(name);field.setAccessible(true);field.set(value,content);return;}
            catch(NoSuchFieldException ignored){}
        }
        throw new AssertionError("Missing fixture field "+name);
    }
    private static void staticSet(Class<?> owner,String name,Object content) throws Exception {Field field=owner.getDeclaredField(name);field.setAccessible(true);field.set(null,content);}
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private static boolean member(JsonArray values,String target){for(JsonElement value:values)if(value.getAsString().equals(target))return true;return false;}

    /** Uses the pinned method bodies, just as the target profile's actual mixin does. */
    public static final class Definitions implements net.minecraft.launchwrapper.IClassTransformer {
        public byte[] transform(String name,String transformed,byte[] bytes){
            if(bytes==null)return null;
            if(name.startsWith("micdoodle8.")||name.startsWith("galaxyspace."))return new GameTest.OptionalApis().transform(name,transformed,bytes);
            if(!name.equals("tuhljin.automagy.blocks.ItemBlockThirstyTank"))return bytes;
            try(java.io.InputStream stream=getClass().getClassLoader().getResourceAsStream("com/mitchej123/hodgepodge/mixins/late/automagy/MixinItemBlockThirstyTank.class")){
                org.objectweb.asm.tree.ClassNode mixin=new org.objectweb.asm.tree.ClassNode(),target=new org.objectweb.asm.tree.ClassNode();
                new org.objectweb.asm.ClassReader(stream).accept(mixin,0);new org.objectweb.asm.ClassReader(bytes).accept(target,0);
                target.version=Math.max(target.version,mixin.version);
                target.interfaces.add("net/minecraftforge/fluids/IFluidContainerItem");
                for(Object raw:mixin.methods){
                    org.objectweb.asm.tree.MethodNode method=(org.objectweb.asm.tree.MethodNode)raw;
                    if(method.name.startsWith("<"))continue;
                    for(org.objectweb.asm.tree.AbstractInsnNode insn:method.instructions.toArray()){
                        if(insn instanceof org.objectweb.asm.tree.MethodInsnNode){org.objectweb.asm.tree.MethodInsnNode call=(org.objectweb.asm.tree.MethodInsnNode)insn;if(call.owner.equals(mixin.name))call.owner=target.name;}
                        if(insn instanceof org.objectweb.asm.tree.FieldInsnNode){org.objectweb.asm.tree.FieldInsnNode field=(org.objectweb.asm.tree.FieldInsnNode)insn;if(field.owner.equals(mixin.name))field.owner=target.name;}
                        if(insn instanceof org.objectweb.asm.tree.FrameNode){org.objectweb.asm.tree.FrameNode frame=(org.objectweb.asm.tree.FrameNode)insn;if(frame.local!=null)Collections.replaceAll(frame.local,mixin.name,target.name);if(frame.stack!=null)Collections.replaceAll(frame.stack,mixin.name,target.name);}
                    }
                    target.methods.add(method);
                }
                org.objectweb.asm.ClassWriter out=new org.objectweb.asm.ClassWriter(0);target.accept(out);return out.toByteArray();
            }catch(java.io.IOException failure){throw new IllegalStateException(failure);}
        }
    }
}
