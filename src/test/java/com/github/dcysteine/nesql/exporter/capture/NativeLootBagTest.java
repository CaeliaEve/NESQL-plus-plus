package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.recipe.TemplateRecipeHandler;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.EnumRarity;
import java.util.*;
import java.lang.reflect.*;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;

/** Pinned native grouping, fortune weights, retry state, stack splitting and persistent limits. */
final class NativeLootBagTest {
    private static final String ROOT="eu.usrv.enhancedlootbags.";
    static void run()throws Exception {
        Class<?> mod=type(ROOT+"EnhancedLootBags"),manager=type(ROOT+"core.LootGroupsHandler");
        Object priorRegistry=field(mod,null,"LootGroupHandler"),priorConfig=field(mod,null,"ELBCfg"),priorRandom=field(mod,null,"Rnd"),priorBag=field(manager,null,"mLootBagItem");
        @SuppressWarnings("unchecked") Map<String,cpw.mods.fml.common.ModContainer> mods=(Map<String,cpw.mods.fml.common.ModContainer>)field(cpw.mods.fml.common.Loader.instance(),"namedMods");
        Map<String,cpw.mods.fml.common.ModContainer> priorMods=new HashMap<>(mods);
        Field language=null;for(Field candidate:net.minecraft.client.resources.I18n.class.getDeclaredFields())if(candidate.getType()==net.minecraft.client.resources.Locale.class)language=candidate;
        require(language!=null,"Native client locale field missing");language.setAccessible(true);Object priorLanguage=language.get(null);language.set(null,new net.minecraft.client.resources.Locale());
        try {
        TemplateRecipeHandler handler=(TemplateRecipeHandler)type(ROOT+"integration.nei.LootBagRecipeHandler").newInstance();
        TemplateRecipeHandler.CachedRecipe borrowed=handler.new CachedRecipe(){public codechicken.nei.PositionedStack getResult(){return null;}};handler.arecipes.add(borrowed);
        require(Recipes.adapter(handler)!=null,"Loot bags have no native opening adapter");
        NativeCoreFixesTest.version("enhancedlootbags","1.2.8");
        NativeCoreFixesTest.version("YAMCore","0.7.1");
        Object registry=type(ROOT+"core.LootGroupsHandler").getConstructor(java.io.File.class)
            .newInstance(java.nio.file.Files.createTempDirectory("lootbag-native-").toFile());
        set(type(ROOT+"EnhancedLootBags"),null,"LootGroupHandler",registry);
        Object cfg=allocate(type(ROOT+"config.ELBConfig"));set(cfg.getClass(),cfg,"AllowFortuneBags",true);
        set(type(ROOT+"EnhancedLootBags"),null,"ELBCfg",cfg);
        Item bag=(Item)type(ROOT+"core.items.ItemLootBag").getConstructor(registry.getClass()).newInstance(registry);
        Item.itemRegistry.addObject(31900,"nesqltest:lootbag",bag);set(registry.getClass(),null,"mLootBagItem",bag);
        Object factory=type(ROOT+"core.serializer.LootGroupsFactory").newInstance();
        Object groups=type(ROOT+"core.serializer.LootGroups").newInstance();
        @SuppressWarnings("unchecked") List<Object> table=(List<Object>)call(groups,"getLootTable");
        Object group=invoke(factory.getClass(),factory,"createLootGroup",new Class<?>[]{int.class,String.class,EnumRarity.class,int.class,int.class,boolean.class},
            1,"native group",EnumRarity.common,2,5,true);
        Object trash=invoke(factory.getClass(),factory,"createLootGroup",new Class<?>[]{int.class,String.class,EnumRarity.class,int.class,int.class,boolean.class},
            0,"trash",EnumRarity.common,1,1,false);
        Object a=drop(factory,"minecraft:diamond","limited",65,false,10,1,"pair");
        Object b=drop(factory,"minecraft:gold_ingot","paired",3,true,30,0,"PAIR");
        Object invalid=drop(factory,"missing:never","invalid",1,false,10,1,"");
        Object t=drop(factory,"minecraft:coal","trash",2,false,3,0,"");
        @SuppressWarnings("unchecked") List<Object> drops=(List<Object>)call(group,"getDrops");drops.add(a);drops.add(b);
        @SuppressWarnings("unchecked") List<Object> trashDrops=(List<Object>)call(trash,"getDrops");trashDrops.add(t);
        table.add(group);table.add(trash);set(registry.getClass(),registry,"_mLootGroups",groups);set(registry.getClass(),registry,"_mClientSideLootGroups",groups);
        Object db=type("eu.usrv.yamcore.persisteddata.PersistedDataBase").getConstructor(java.io.File.class,String.class,String.class)
            .newInstance(java.nio.file.Files.createTempDirectory("lootbag-limits-").toFile(),"limits.dat","fixture");
        set(registry.getClass(),registry,"_mPersistedDB",db);
        net.minecraft.entity.player.EntityPlayer player=(net.minecraft.entity.player.EntityPlayer)allocate(net.minecraft.entity.player.EntityPlayerMP.class);
        for(Field f:net.minecraft.entity.player.EntityPlayer.class.getDeclaredFields())if(f.getType()==com.mojang.authlib.GameProfile.class){f.setAccessible(true);f.set(player,new com.mojang.authlib.GameProfile(new UUID(1,2),"native"));}
        for(Field f:net.minecraft.entity.Entity.class.getDeclaredFields())if(f.getType()==UUID.class){f.setAccessible(true);f.set(player,new UUID(1,2));}
        require(((List<?>)invoke(registry.getClass(),registry,"getItemGroupDrops",new Class<?>[]{group.getClass(),a.getClass()},group,a)).size()==2,"Correlated item group was separated");
        require((Integer)invoke(registry.getClass(),registry,"recalcWeightByFortune",new Class<?>[]{int.class,int.class},3,1)==3,"Native fortune floors its reduction");
        require((Integer)invoke(registry.getClass(),registry,"recalcWeightByFortune",new Class<?>[]{int.class,int.class},10,2)==4,"Native second fortune reduction changed");
        set(type(ROOT+"EnhancedLootBags"),null,"Rnd",new Draw(0.0,0));
        @SuppressWarnings("unchecked") List<ItemStack> result=(List<ItemStack>)invoke(bag.getClass(),bag,"getRandomLootItems",new Class<?>[]{net.minecraft.entity.player.EntityPlayer.class,group.getClass()},player,group);
        require(result.size()==3&&result.get(0).stackSize==64&&result.get(1).stackSize==1&&result.get(2).stackSize==1,
            "Native grouped amount must split at stack limit and random count is nextInt(amount)+1");
        require((Integer)invoke(registry.getClass(),registry,"getDropCount",new Class<?>[]{net.minecraft.entity.player.EntityPlayer.class,group.getClass(),a.getClass()},player,group,a)==1,"Limited draw count missing");
        result=(List<ItemStack>)invoke(bag.getClass(),bag,"getRandomLootItems",new Class<?>[]{net.minecraft.entity.player.EntityPlayer.class,group.getClass()},player,group);
        require(result.size()==1&&result.get(0).getItem()==Items.gold_ingot,"Exhausted player limit must remove only its group member");
        drops.clear();drops.add(invalid);invoke(group.getClass(),group,"updateMaxWeight",new Class<?>[0]);
        result=(List<ItemStack>)invoke(bag.getClass(),bag,"getRandomLootItems",new Class<?>[]{net.minecraft.entity.player.EntityPlayer.class,group.getClass()},player,group);
        require(result.isEmpty()&&(Integer)invoke(registry.getClass(),registry,"getDropCount",new Class<?>[]{net.minecraft.entity.player.EntityPlayer.class,group.getClass(),a.getClass()},player,group,invalid)==1,
            "Native limit is charged before failed item resolution");
        drops.clear();drops.add(a);drops.add(b);invoke(group.getClass(),group,"updateMaxWeight",new Class<?>[0]);
        set(type(ROOT+"EnhancedLootBags"),null,"Rnd",new Draw(0.0,0));
        Object merged=invoke(registry.getClass(),registry,"getMergedGroupFromID",new Class<?>[]{int.class,int.class},1,1);
        require(((List<?>)call(merged,"getDrops")).size()==3,"Trash merge missing");
        require(invoke(registry.getClass(),registry,"getMergedGroupFromID",new Class<?>[]{int.class,int.class},1,3)==group,"Allowed fortuneIII must bypass trash merge");
        capture(handler,bag);
        require(handler.arecipes.size()==1&&handler.arecipes.get(0)==borrowed,"Lootbag capture rewrote borrowed NEI cache");
        installedConfiguration(handler,registry,bag);
        System.out.println("Native loot bags: correlated groups, integer fortune reduction, split/random counts, player limits, failed-resolution debit and snapshot drift passed");
        } finally {set(mod,null,"LootGroupHandler",priorRegistry);set(mod,null,"ELBCfg",priorConfig);set(mod,null,"Rnd",priorRandom);set(manager,null,"mLootBagItem",priorBag);mods.clear();mods.putAll(priorMods);language.set(null,priorLanguage);}
    }
    private static void installedConfiguration(TemplateRecipeHandler handler,Object registry,Item bag)throws Exception {
        java.nio.file.Path file=java.nio.file.Paths.get("E:/GTNH/.minecraft/versions/GT New Horizons 2.8.4-java8/config/EnhancedLootBags/LootBags.xml");
        if(!java.nio.file.Files.isRegularFile(file))throw new AssertionError("Pinned lootbag configuration missing");
        javax.xml.bind.JAXBContext context=javax.xml.bind.JAXBContext.newInstance(type(ROOT+"core.serializer.LootGroups"));
        Object groups=context.createUnmarshaller().unmarshal(file.toFile());
        set(registry.getClass(),registry,"_mLootGroups",groups);set(registry.getClass(),registry,"_mBufferedLootGroups",new HashMap<>());
        RegistryRecipes adapter=(RegistryRecipes)type("com.github.dcysteine.nesql.exporter.capture.LootBagRecipes")
            .getDeclaredConstructor(TemplateRecipeHandler.class).newInstance(handler);
        require(adapter.size()==51,"Pinned configuration group count changed");
        List<ItemStack> known=new ArrayList<>();
        for(Object group:(List<?>)call(groups,"getLootTable")){
            known.add(new ItemStack(bag,1,(Integer)call(group,"getGroupID")));
            for(Object drop:(List<?>)call(group,"getDrops")){
                ItemStack item=(ItemStack)invoke(drop.getClass(),drop,"getItemStack",new Class<?>[]{int.class},1);
                if(item!=null)known.add(item);
            }
        }
        Draw untouched=new Draw(0.0,0);set(type(ROOT+"EnhancedLootBags"),null,"Rnd",untouched);
        int maxBytes=0,maxOutputs=0;
        for(int index=0;index<adapter.size();index++) {
            RecipeRow row=NativeCoreFixesTest.row(known.toArray(new ItemStack[0]));adapter.capture(index,row);
            maxBytes=Math.max(maxBytes,com.github.dcysteine.nesql.exporter.source.CanonicalJson.bytes(row.record).length);
            maxOutputs=Math.max(maxOutputs,row.outputs.size());
        }
        require(maxBytes<1024*1024&&maxOutputs<=4096,"Pinned loot rules exceed transport row/output budget");
        require(untouched.calls==0,"Pinned configuration capture changed opening RNG");
        adapter.finishVerification();adapter.close();adapter.close();
        System.out.println("Pinned loot configuration: 51 groups, max row bytes="+maxBytes+", max resolved outputs="+maxOutputs+", zero RNG/storage execution");
    }
    private static void capture(TemplateRecipeHandler handler,Item bag)throws Exception {
        RegistryRecipes adapter=(RegistryRecipes)type("com.github.dcysteine.nesql.exporter.capture.LootBagRecipes")
            .getDeclaredConstructor(TemplateRecipeHandler.class).newInstance(handler);
        ItemStack[] known={new ItemStack(bag,1,1),new ItemStack(bag),new ItemStack(Items.diamond),new ItemStack(Items.gold_ingot),new ItemStack(Items.coal)};
        RecipeRow row=NativeCoreFixesTest.row(known);adapter.capture(0,row);
        require(row.outputs.size()==3,"Loot bag/trash outputs lost");
        require(row.inputs.get(0).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject().getAsJsonObject("consume")
            .get("kind").getAsString().equals("allocated"),"Bag became an unconditional input debit");
        require(row.outputs.get(0).getAsJsonObject().get("amount").isJsonNull(),"Loot draw became guaranteed output");
        write("lootbag-semantic","lootbag",row,handler,known);
        lifecycle(handler,bag,adapter,known);
        set(type(ROOT+"EnhancedLootBags"),null,"Rnd",new Draw(0.0,0));
        Object registry=field(type(ROOT+"EnhancedLootBags"),null,"LootGroupHandler");
        @SuppressWarnings("unchecked") List<Object> table=(List<Object>)call(call(registry,"getLootGroups"),"getLootTable");
        @SuppressWarnings("unchecked") List<Object> drops=(List<Object>)call(table.get(0),"getDrops");
        drops.add(drop(type(ROOT+"core.serializer.LootGroupsFactory").newInstance(),"missing:unresolved","kept-descriptor",1,false,7,2,"pair"));
        RegistryRecipes unresolved=(RegistryRecipes)type("com.github.dcysteine.nesql.exporter.capture.LootBagRecipes")
            .getDeclaredConstructor(TemplateRecipeHandler.class).newInstance(handler);
        Draw untouched=new Draw(0.0,0);set(type(ROOT+"EnhancedLootBags"),null,"Rnd",untouched);
        RecipeRow missingRow=NativeCoreFixesTest.row(known);unresolved.capture(0,missingRow);unresolved.verify();
        require(untouched.calls==0,"Capture consumed native opening RNG");
        require(missingRow.outputs.size()==3,"Unresolved drop acquired a fake item output");
        com.google.gson.JsonArray entries=missingRow.record.getAsJsonObject("process").getAsJsonArray("groups").get(0).getAsJsonObject().getAsJsonArray("drops");
        require(entries.get(2).getAsJsonObject().get("resolved").isJsonNull()
            &&entries.get(2).getAsJsonObject().get("descriptor").getAsString().equals("missing:unresolved"),"Unresolved native descriptor disappeared");
        write("lootbag-unresolved-semantic","lootbag-unresolved",missingRow,handler,known);unresolved.close();
        drops.add(drops.get(0));
        try{adapter.verify();throw new AssertionError("Changed loot registry reused snapshot");}
        catch(com.github.dcysteine.nesql.exporter.task.Jobs.Fault expected){require(expected.code.equals("recipe_changed"),"Wrong loot drift fault");}
        adapter.close();
    }
    private static void write(String file,String key,RecipeRow row,TemplateRecipeHandler handler,ItemStack[] known)throws Exception {
        com.google.gson.JsonObject origin=com.github.dcysteine.nesql.exporter.source.Json.object("owner","enhancedlootbags","handler",handler.getClass().getName(),"key",key);
        row.record.add("source",origin);row.record.addProperty("category",com.github.dcysteine.nesql.exporter.source.Identity.origin("category",origin));NativeContractFixture.writeOwned(file,row,Arrays.asList(known));
    }
    @SuppressWarnings("unchecked") private static void lifecycle(TemplateRecipeHandler handler,Item bag,RegistryRecipes adapter,ItemStack[] known)throws Exception {
        Object registry=field(type(ROOT+"EnhancedLootBags"),null,"LootGroupHandler"),groups=field(registry,"_mLootGroups");
        List<Object> table=(List<Object>)field(groups,"mLootGroups"),drops=(List<Object>)field(table.get(0),"mDrops");
        RegistrationBindings binding=RegistrationBindings.read(handler);require(binding.same(RegistrationBindings.read((TemplateRecipeHandler)handler.getClass().newInstance())),"Loot independent registrations have different sources");
        Object cache=field(registry,"_mBufferedLootGroups");
        try {set(registry.getClass(),registry,"_mBufferedLootGroups",new HashMap<>((Map<?,?>)cache));expect("recipe_changed",adapter::verify);require(!binding.same(RegistrationBindings.read(handler)),"Loot buffer replacement stayed equivalent");}
        finally {set(registry.getClass(),registry,"_mBufferedLootGroups",cache);}
        Object first=table.get(0);Object copy=type(ROOT+"core.serializer.LootGroupsFactory").newInstance();copy=invoke(copy.getClass(),copy,"copyLootGroup",new Class<?>[]{first.getClass()},first);
        try{table.set(0,copy);expect("recipe_changed",adapter::verify);}finally{table.set(0,first);}
        Object config=field(type(ROOT+"EnhancedLootBags"),null,"ELBCfg");
        try{set(config.getClass(),config,"AllowFortuneBags",false);expect("recipe_changed",adapter::verify);}finally{set(config.getClass(),config,"AllowFortuneBags",true);}
        Object cachedWeight=field(first,"mMaxWeight");Draw untouched=new Draw(0,0);set(type(ROOT+"EnhancedLootBags"),null,"Rnd",untouched);
        adapter.finishVerification();require(field(first,"mMaxWeight").equals(cachedWeight)&&untouched.calls==0,"Loot verification initialized weight or used RNG");
        UnknownLimit unknown=new UnknownLimit();cpw.mods.fml.common.registry.GameRegistry.registerItem(unknown,"nesql_loot_unknown");
        require(cpw.mods.fml.common.registry.GameRegistry.findItem("minecraft","nesql_loot_unknown")==unknown,"Native resolver fixture item was not registered");
        Object entry=drop(type(ROOT+"core.serializer.LootGroupsFactory").newInstance(),"minecraft:nesql_loot_unknown","unknown",1,false,1,0,"");drops.add(entry);
        try{expect("recipe_unsupported",()->new LootBagRecipes(handler));require(unknown.calls==0,"Unknown loot stack-limit callback executed");}finally{drops.remove(entry);}
        try(Recipes.Cursor cursor=new Recipes.Handler(handler,0).open(NativeCoreFixesTest.row(known).facts)){require(cursor.size()==2,"Production loot cursor lost groups");cursor.capture(0);cursor.finishVerification();}
        List<Object> previous=new ArrayList<>(table);
        try{table.clear();try(RegistryRecipes empty=new LootBagRecipes(handler)){require(empty.size()==0,"Ready-empty loot registry rejected");empty.finishVerification();}}
        finally{table.addAll(previous);}
        try{set(registry.getClass(),registry,"_mLootGroups",null);expect("handler_not_ready",()->new LootBagRecipes(handler));}
        finally{set(registry.getClass(),registry,"_mLootGroups",groups);}
        try(RegistryRecipes closing=new LootBagRecipes(handler)){closing.close();closing.close();expect("capture_closed",closing::verify);}
    }
    private static void expect(String code,Runnable action){try{action.run();throw new AssertionError("Expected "+code);}catch(com.github.dcysteine.nesql.exporter.task.Jobs.Fault failure){require(failure.code.equals(code),"Wrong failure: "+failure.code);}}
    private static final class UnknownLimit extends Item {int calls;public int getItemStackLimit(ItemStack stack){calls++;throw new AssertionError("Unknown limit callback executed");}}
    private static Object drop(Object factory,String item,String id,int count,boolean random,int weight,int limit,String group){
        return invoke(factory.getClass(),factory,"createDrop",new Class<?>[]{String.class,String.class,String.class,int.class,boolean.class,int.class,int.class,String.class},item,id,"",count,random,weight,limit,group);
    }
    private static Object call(Object target,String method){return invoke(target.getClass(),target,method,new Class<?>[0]);}
    private static Object allocate(Class<?> type)throws Exception{Field f=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);return ((sun.misc.Unsafe)f.get(null)).allocateInstance(type);}
    private static void set(Class<?> type,Object target,String name,Object value)throws Exception{Field f=type.getDeclaredField(name);f.setAccessible(true);f.set(target,value);}
    private static void require(boolean v,String m){if(!v)throw new AssertionError(m);}
    private static final class Draw extends Random {final double selection;final int number;int calls;Draw(double s,int n){selection=s;number=n;}public double nextDouble(){calls++;return selection;}public int nextInt(int bound){calls++;if(bound<=0)throw new IllegalArgumentException();return Math.min(number,bound-1);}}
}
