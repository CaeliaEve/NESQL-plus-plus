package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.recipe.TemplateRecipeHandler;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import com.google.gson.JsonObject;
import com.github.dcysteine.nesql.exporter.source.Identity;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import static com.github.dcysteine.nesql.exporter.source.Json.*;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.common.util.ForgeDirection;
import java.lang.reflect.*;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;

/** Actual Soul Binder methods with isolated registry holders; no world, mod lifecycle or GL. */
final class NativeSoulTest {
    private static final String ROOT="crazypants.enderio.";
    static void run() throws Exception {
        Class<?> mod=type(ROOT+"EnderIO"),raw=type(ROOT+"machine.soul.BasicSoulBinderRecipe"),input=type(ROOT+"machine.MachineRecipeInput");
        require(raw.getProtectionDomain().getCodeSource().getLocation().toString().contains("/native-tests/enderio.jar!"),"Pinned native EnderIO required");
        Constructor<?> constructor=type(ROOT+"item.ItemSoulVessel").getDeclaredConstructor();constructor.setAccessible(true);
        Item vessel=(Item)constructor.newInstance();mod.getDeclaredField("itemSoulVessel").set(null,vessel);
        Constructor<?> brokenCtor=type(ROOT+"machine.spawner.ItemBrokenSpawner").getDeclaredConstructor();brokenCtor.setAccessible(true);
        Item broken=(Item)brokenCtor.newInstance();mod.getDeclaredField("itemBrokenSpawner").set(null,broken);
        Item.itemRegistry.addObject(30001,"fixture:soul_vessel",vessel);Item.itemRegistry.addObject(30002,"fixture:broken_spawner",broken);
        Object block=allocate(type(ROOT+"machine.spawner.BlockPoweredSpawner"));mod.getDeclaredField("blockPoweredSpawner").set(null,block);
        Class<?> configType=type(ROOT+"machine.spawner.PoweredSpawnerConfig");Object config=allocate(configType);
        assign(config,"blackList",new ArrayList<>(Arrays.asList("Forbidden")));assign(config,"costs",new HashMap<>());configType.getDeclaredField("instance").set(null,config);
        Object basic=raw.getConstructor(ItemStack.class,ItemStack.class,int.class,int.class,String.class,String[].class)
            .newInstance(new ItemStack(Items.paper,7),new ItemStack(Items.gold_ingot,2),1000,16,"fixture",new String[]{"Zombie","","7"});
        Object offered=Array.newInstance(input,2);
        ItemStack vial=vial(vessel,"Zombie"), material=new ItemStack(Items.paper,1);
        material.setTagCompound(new NBTTagCompound());material.getTagCompound().setInteger("owner",7);
        Array.set(offered,0,input.getConstructor(int.class,ItemStack.class).newInstance(0,vial));
        Array.set(offered,1,input.getConstructor(int.class,ItemStack.class).newInstance(1,material));
        require((Boolean)invoke(raw,basic,"isRecipe",new Class<?>[]{offered.getClass()},offered),"Native material count or NBT became a restriction");
        List<?> consumed=(List<?>)invoke(raw,basic,"getQuantitiesConsumed",new Class<?>[]{offered.getClass()},offered);
        require(consumed.size()==2&&((ItemStack)field(consumed.get(1),"item")).stackSize==1,"Native soul binding consumes one, not example count");
        Object[] results=(Object[])invoke(raw,basic,"getCompletedResult",new Class<?>[]{float.class,offered.getClass()},0.9f,offered);
        require(results.length==2&&((ItemStack)field(results[0],"item")).getItem()==vessel&&((ItemStack)field(results[1],"item")).stackSize==2,"Empty vial and fixed result order changed");
        vial.getTagCompound().setInteger("id",7);
        require(vial.getTagCompound().getString("id").equals("7")&&(Boolean)invoke(raw,basic,"isRecipe",new Class<?>[]{offered.getClass()},offered),"Native numeric id extraction changed");
        vial.getTagCompound().removeTag("id");
        require(!(Boolean)invoke(raw,basic,"isRecipe",new Class<?>[]{offered.getClass()},offered),"Missing id accepted as empty string");
        Object dual=raw.getConstructor(ItemStack.class,ItemStack.class,int.class,int.class,String.class,String[].class)
            .newInstance(new ItemStack(vessel),new ItemStack(Items.gold_ingot),1000,1,"dual",new String[]{"Zombie"});
        Array.set(offered,0,input.getConstructor(int.class,ItemStack.class).newInstance(0,vial(vessel,"Zombie")));
        Array.set(offered,1,input.getConstructor(int.class,ItemStack.class).newInstance(1,vial(vessel,"Sheep")));
        require((Boolean)invoke(raw,dual,"isRecipe",new Class<?>[]{offered.getClass()},offered),"Second vessel should satisfy material identity");
        require(((Object[])invoke(raw,dual,"getCompletedResult",new Class<?>[]{float.class,offered.getClass()},0f,offered)).length==0,"Last vessel did not control the native completion gate");
        Object missing=raw.getConstructor(ItemStack.class,ItemStack.class,int.class,int.class,String.class,String[].class)
            .newInstance(new ItemStack(Items.paper),new ItemStack(Items.gold_ingot),1000,1,"missing",new String[]{null});
        Array.set(offered,0,input.getConstructor(int.class,ItemStack.class).newInstance(0,new ItemStack(Items.diamond)));
        Array.set(offered,1,input.getConstructor(int.class,ItemStack.class).newInstance(1,new ItemStack(Items.paper)));
        require((Boolean)invoke(raw,missing,"isRecipe",new Class<?>[]{offered.getClass()},offered),"Allowed null soul extraction must also match non-vessel items");
        Object spawner=field(type(ROOT+"machine.soul.SoulBinderSpawnerRecipe"),null,"instance");
        ItemStack source=vial(vessel,"Unregistered.But.Not.Blacklisted");
        Array.set(offered,0,input.getConstructor(int.class,ItemStack.class).newInstance(0,source));
        Array.set(offered,1,input.getConstructor(int.class,ItemStack.class).newInstance(1,new ItemStack(broken,1,31)));
        require((Boolean)invoke(spawner.getClass(),spawner,"isRecipe",new Class<?>[]{offered.getClass()},offered),"Spawner predicate was narrowed to displayed registered mobs");
        results=(Object[])invoke(spawner.getClass(),spawner,"getCompletedResult",new Class<?>[]{float.class,offered.getClass()},0f,offered);
        ItemStack result=(ItemStack)field(results[1],"item");
        require(result.getItemDamage()==0&&result.getTagCompound().getString("mobType").equals("Unregistered.But.Not.Blacklisted"),"Spawner output did not reconstruct a fresh mobType tag");
        source.getTagCompound().setString("id","Forbidden");
        require(!(Boolean)invoke(spawner.getClass(),spawner,"isRecipe",new Class<?>[]{offered.getClass()},offered),"Spawner blacklist ignored");
        xp(mod);
        System.out.println("Soul native evidence: slot/count/NBT, last-vessel override, unregistered mob predicate, blacklist and raw XP passed");
        TemplateRecipeHandler handler=(TemplateRecipeHandler)type(ROOT+"nei.SoulBinderRecipeHandler").newInstance();
        require(Recipes.adapter(handler)!=null,"Missing native Soul Binder adapter");
        Class<?> adapter=type("com.github.dcysteine.nesql.exporter.capture.SoulRecipes");
        Constructor<?> factory=adapter.getDeclaredConstructor(TemplateRecipeHandler.class,List.class,Item.class,List.class,int.class,boolean.class,List.class);factory.setAccessible(true);
        RegistryRecipes cursor=(RegistryRecipes)factory.newInstance(handler,Arrays.asList(basic,dual,spawner),vessel,Arrays.asList("Forbidden"),825,false,Arrays.asList("Zombie","Forbidden"));
        RecipeRow row=row(vessel,broken);require(cursor.size()==3&&cursor.capture(0,row),"Native soul rows lost");
        JsonObject process=row.record.getAsJsonObject("process");
        require(process.get("experience").getAsInt()==272&&process.get("levels").getAsInt()==16&&!process.get("drains").getAsBoolean(),"Raw XP gate or missing-fluid debit lost");
        require(row.inputs.get(1).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject().get("amount").getAsString().equals("1"),"Example material count exported as consumption");
        require(row.outputs.get(0).getAsJsonObject().getAsJsonObject("quantity").get("kind").getAsString().equals("soul"),"Correlated empty-result gate lost");
        row=row(vessel,broken);require(cursor.capture(1,row),"Dual-vessel row missing");
        require(row.record.getAsJsonObject("process").getAsJsonArray("earlier").size()==1,"Prior recipe selector lost");
        row=row(vessel,broken);require(cursor.capture(2,row),"Spawner row missing");
        JsonObject filter=row.inputs.get(0).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject().getAsJsonObject("rule").getAsJsonObject("filter");
        require(filter.get("exclude").getAsBoolean()&&filter.getAsJsonArray("names").toString().equals("[null,\"Forbidden\"]"),"Spawner examples replaced blacklist predicate");
        require(row.outputs.get(1).getAsJsonObject().getAsJsonObject("change").getAsJsonObject("action").get("kind").getAsString().equals("soul"),"Spawner dynamic reconstruction lost");
        require(((ItemStack)invoke(raw,basic,"getInputStack",new Class<?>[0])).stackSize==7,"Native sample mutated");
        try {factory.newInstance(handler,Arrays.asList(new Object()),vessel,Arrays.asList("Forbidden"),825,true,Arrays.asList("Zombie"));throw new AssertionError("Unknown Soul class accepted");}
        catch(InvocationTargetException expected){require(expected.getCause() instanceof com.github.dcysteine.nesql.exporter.task.Jobs.Fault,"Wrong unknown Soul failure");}
        registryGate(handler,basic);
        System.out.println("Soul adapter: owned snapshots, raw XP, prior selectors, conditional output and dynamic spawner passed");
    }
    private static void xp(Class<?> mod)throws Exception {
        Class<?> util=type(ROOT+"xp.XpUtil");
        for(int[] sample:new int[][]{{0,0},{1,17},{15,255},{16,272},{30,825},{31,887}})
            require((Integer)invoke(util,null,"getExperienceForLevel",new Class<?>[]{int.class},sample[0])==sample[1],"Native XP curve changed");
        Fluid fluid=new Fluid("fixture_soul_xp");FluidRegistry.registerFluid(fluid);mod.getDeclaredField("fluidXpJuice").set(null,fluid);
        Object tank=type(ROOT+"xp.ExperienceContainer").getConstructor(int.class).newInstance(1000);
        invoke(tank.getClass(),tank,"addExperience",new Class<?>[]{int.class},300);
        Object drained=invoke(tank.getClass(),tank,"drain",new Class<?>[]{ForgeDirection.class,int.class,boolean.class},ForgeDirection.UNKNOWN,272*20,true);
        require((Integer)field(drained,"amount")==5440&&(Integer)invoke(tank.getClass(),tank,"getExperienceTotal",new Class<?>[0])==28,"XP debit became an experience-level subtraction");
        mod.getDeclaredField("fluidXpJuice").set(null,null);
        require(invoke(tank.getClass(),tank,"drain",new Class<?>[]{ForgeDirection.class,int.class,boolean.class},ForgeDirection.UNKNOWN,20,true)==null,"Missing XP fluid should leave native tank untouched");
        require((Integer)invoke(tank.getClass(),tank,"getExperienceTotal",new Class<?>[0])==28,"Missing-fluid drain consumed XP");
    }
    @SuppressWarnings("unchecked") private static void registryGate(TemplateRecipeHandler handler,Object basic)throws Exception {
        Object loader=cpw.mods.fml.common.Loader.instance();Field named=loader.getClass().getDeclaredField("namedMods");named.setAccessible(true);Object mods=named.get(loader);
        cpw.mods.fml.common.ModContainer mod=(cpw.mods.fml.common.ModContainer)Proxy.newProxyInstance(NativeSoulTest.class.getClassLoader(),new Class<?>[]{cpw.mods.fml.common.ModContainer.class},(proxy,method,args)->{
            if(method.getName().equals("getVersion"))return "2.9.28";throw new AssertionError("Unexpected mod query");});
        Map<Object,Object> registry=(Map<Object,Object>)field(field(type(ROOT+"machine.MachineRecipeRegistry"),null,"instance"),"machineRecipes");
        Object key=field(field(type(ROOT+"ModObject"),null,"blockSoulBinder"),"unlocalisedName"),prior=registry.get(key);
        Map<String,Object> records=new LinkedHashMap<>();records.put("fixture",basic);
        try {
            named.set(loader,Collections.singletonMap("EnderIO",mod));registry.put(key,records);
            require(new SoulRecipes(handler).size()==1,"Native Soul runtime registry not captured");
            Object spawner=field(type(ROOT+"machine.soul.SoulBinderSpawnerRecipe"),null,"instance");records.put("spawner",spawner);
            List<String> nativeExamples=new ArrayList<>((List<String>)invoke(spawner.getClass(),spawner,"getSupportedSouls",new Class<?>[0]));Collections.sort(nativeExamples);
            require(field(new SoulRecipes(handler),"examples").equals(nativeExamples),"Soul display examples differ from the native NEI mob list");
            records.put("unknown",new Object());
            try{new SoulRecipes(handler);throw new AssertionError("Unknown Soul runtime class ignored");}
            catch(com.github.dcysteine.nesql.exporter.task.Jobs.Fault expected){require(expected.code.equals("recipe_unsupported"),"Wrong Soul runtime error");}
            registry.remove(key);int size=registry.size();
            try{new SoulRecipes(handler);throw new AssertionError("Missing Soul runtime registry accepted");}
            catch(com.github.dcysteine.nesql.exporter.task.Jobs.Fault expected){require(registry.size()==size&&!registry.containsKey(key),"Read-only snapshot created a registry");}
        }finally{named.set(loader,mods);if(prior==null)registry.remove(key);else registry.put(key,prior);}
    }
    private static ItemStack vial(Item item,String id){ItemStack stack=new ItemStack(item);stack.setTagCompound(new NBTTagCompound());stack.getTagCompound().setString("id",id);return stack;}
    @SuppressWarnings("unchecked") private static RecipeRow row(Item vessel,Item broken){
        Facts facts=new Facts("en_US");Set<String> seen=(Set<String>)field(facts,"items");
        for(Object registered:Item.itemRegistry){Item item=(Item)registered;seen.add(Identity.item(Item.itemRegistry.getNameForObject(item),0,TypedNbt.encode(null)));}
        for(String id:new String[]{"","7","Zombie"}){
            ItemStack v=vial(vessel,id);seen.add(Identity.item(Item.itemRegistry.getNameForObject(vessel),0,TypedNbt.encode(v.getTagCompound())));
            NBTTagCompound tag=new NBTTagCompound();tag.setString("mobType",id);seen.add(Identity.item(Item.itemRegistry.getNameForObject(broken),0,TypedNbt.encode(tag)));
        }
        return new RecipeRow(facts,object("owner","fixture","handler","soul","key","soul"),"fixture",0);
    }
    private static Object allocate(Class<?> type)throws Exception {Field f=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);return ((sun.misc.Unsafe)f.get(null)).allocateInstance(type);}
    private static void assign(Object target,String name,Object value)throws Exception {Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);f.set(target,value);}
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
