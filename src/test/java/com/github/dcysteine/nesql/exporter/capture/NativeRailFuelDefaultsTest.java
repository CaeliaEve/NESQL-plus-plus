package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonArray;
import java.lang.reflect.Field;
import java.util.*;
import net.minecraft.item.ItemStack;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;

/** Reuses the native Rail runner; all lazy builder calls below own their manager. */
final class NativeRailFuelDefaultsTest {
    @SuppressWarnings("unchecked")
    static void run(Object manager, TemplateRecipeHandler handler) throws Exception {
        Class<?> plugin=type("mods.railcraft.common.plugins.thaumcraft.ThaumcraftPlugin");
        Field loaded=plugin.getDeclaredField("modLoaded");loaded.setAccessible(true);
        Object oldLoaded=loaded.get(null);
        Map<Object,Object> itemCache=(Map<Object,Object>)field(plugin,null,"itemCache");
        Map<Object,Object> flagCache=(Map<Object,Object>)field(plugin,null,"itemCacheFlag");
        Map<Object,Object> oldItems=new HashMap<>(itemCache),oldFlags=new HashMap<>(flagCache);
        Set<Object> modules=(Set<Object>)field(type("mods.railcraft.common.modules.ModuleManager"),null,"loadedModules");
        Set<Object> oldModules=new HashSet<>(modules);
        cpw.mods.fml.common.Loader loader=cpw.mods.fml.common.Loader.instance();
        Map<String,cpw.mods.fml.common.ModContainer> oldMods=(Map<String,cpw.mods.fml.common.ModContainer>)field(loader,"namedMods");
        Field controller=loader.getClass().getDeclaredField("modController");controller.setAccessible(true);Object oldController=controller.get(loader);
        Field resource=type("thaumcraft.common.config.ConfigItems").getField("itemResource");Object oldResource=resource.get(null);
        Field coke=type("mods.railcraft.common.items.RailcraftToolItems").getDeclaredField("itemCoalCoke");coke.setAccessible(true);Object oldCoke=coke.get(null);
        try {
            loaded.set(null,Boolean.FALSE);modules.clear();
            try(RailBlastFuelSource source=new RailBlastFuelSource(manager)) {
                JsonArray captured=source.templates();
                require(captured.size()>=3,"Cold native fuel definitions were unavailable");
                require(field(manager,"fuels")==null,"Capture initialized native manager cache");
                require(itemCache.equals(oldItems)&&flagCache.equals(oldFlags),"Capture changed native Thaumcraft caches");
                Object owned=manager.getClass().newInstance();
                List<ItemStack> nativeFuels=(List<ItemStack>)manager.getClass().getMethod("getFuels").invoke(owned);
                require(nativeFuels.size()==captured.size(),"Cold native fuel definition count differs");
                for(int i=0;i<nativeFuels.size();i++)require(captured.get(i).getAsJsonObject().getAsJsonObject("template")
                        .equals(RecipeRow.stackEvidence(nativeFuels.get(i))),"Cold native fuel definition differs at "+i);
                source.verify();
                require(RegistrationBindings.read(handler).same(RegistrationBindings.read(handler)),"Cold independent registrations differ");
                try {
                    loaded.set(null,Boolean.TRUE);
                    try{source.verify();throw new AssertionError("Cold source ignored dependency drift");}
                    catch(Jobs.Fault failure){require(failure.code.equals("recipe_changed"),failure.toString());}
                }finally{loaded.set(null,Boolean.FALSE);}
                source.verify();
            }
            loaded.set(null,null);
            compare(manager,plugin,itemCache,flagCache);
            require(loaded.get(null)==null,"Capture initialized the native mod-loaded cache");
            Map<String,cpw.mods.fml.common.ModContainer> mods=new HashMap<>(oldMods);
            cpw.mods.fml.common.ModMetadata metadata=new cpw.mods.fml.common.ModMetadata();metadata.modId="Thaumcraft";metadata.version="4.2.3.5";
            mods.put("Thaumcraft",new cpw.mods.fml.common.DummyModContainer(metadata));
            cpw.mods.fml.relauncher.ReflectionHelper.setPrivateValue(cpw.mods.fml.common.Loader.class,loader,mods,"namedMods");
            controller.set(loader,new cpw.mods.fml.common.LoadController(loader));
            itemCache.remove("itemResource");flagCache.remove("itemResource");resource.set(null,Items.book);
            compare(manager,plugin,itemCache,flagCache); // fresh native ItemApi branch, without publishing its caches
            require(loaded.get(null)==null&&!itemCache.containsKey("itemResource")&&!flagCache.containsKey("itemResource"),"Fresh capture warmed Thaumcraft");
            try(RailBlastFuelSource source=new RailBlastFuelSource(manager)){
                resource.set(null,Items.paper);fails(source::verify,"recipe_changed");resource.set(null,Items.book);source.verify();
                Field table=manager.getClass().getDeclaredField("fuels");table.setAccessible(true);
                try{table.set(manager,new ArrayList<>());fails(source::verify,"recipe_changed");}finally{table.set(manager,null);}
                source.verify();
            }
            loaded.set(null,Boolean.TRUE);itemCache.put("itemResource",Items.paper);flagCache.put("itemResource",Boolean.TRUE);
            compare(manager,plugin,itemCache,flagCache); // cached Item takes precedence over a prior miss flag
            try(RailBlastFuelSource source=new RailBlastFuelSource(manager)){
                resource.set(null,Items.stick);source.verify(); // shadowed definition is not a dependency
                itemCache.put("itemResource",Items.book);fails(source::verify,"recipe_changed");itemCache.put("itemResource",Items.paper);source.verify();
            }
            itemCache.remove("itemResource");compare(manager,plugin,itemCache,flagCache); // cached negative lookup
            flagCache.remove("itemResource");resource.set(null,null);compare(manager,plugin,itemCache,flagCache);
            loaded.set(null,Boolean.FALSE);coke.set(null,Items.stick);compare(manager,plugin,itemCache,flagCache);
            try(RailBlastFuelSource source=new RailBlastFuelSource(manager)){
                coke.set(null,Items.paper);fails(source::verify,"recipe_changed");coke.set(null,Items.stick);source.verify();
            }
            NativeRailTest.UnknownMetadata unknown=new NativeRailTest.UnknownMetadata();unknown.armed=true;coke.set(null,unknown);
            fails(()->new RailBlastFuelSource(manager),"recipe_unsupported");
            require(unknown.calls==0,"Cold definition invoked an unknown metadata callback");coke.set(null,oldCoke);
            Object cube=field(type("mods.railcraft.common.blocks.aesthetics.cube.EnumCube"),null,"COKE_BLOCK");
            Object factory=field(cube,"module");modules.add(factory);
            Map<Object,Object> settings=(Map<Object,Object>)field(type("mods.railcraft.common.core.RailcraftConfig"),null,"enabledSubBlocks");
            boolean hadSetting=settings.containsKey("cube.coke");Object oldSetting=settings.get("cube.coke");
            try{
                settings.remove("cube.coke");fails(()->new RailBlastFuelSource(manager),"handler_not_ready");
                settings.put("cube.coke",Boolean.FALSE);compare(manager,plugin,itemCache,flagCache);
                try(RailBlastFuelSource source=new RailBlastFuelSource(manager)){
                    settings.put("cube.coke",Boolean.TRUE);fails(source::verify,"recipe_changed");
                    settings.put("cube.coke",Boolean.FALSE);source.verify();
                }
                settings.put("cube.coke",Boolean.TRUE);compare(manager,plugin,itemCache,flagCache);
                Class<?> cubeType=type("mods.railcraft.common.blocks.aesthetics.cube.BlockCube");
                Field instance=cubeType.getDeclaredField("instance");instance.setAccessible(true);Object oldInstance=instance.get(null);
                net.minecraft.block.Block owned=(net.minecraft.block.Block)NativeSqueezerWorkTest.allocate(cubeType);
                Field delegate=net.minecraft.block.Block.class.getDeclaredField("delegate");delegate.setAccessible(true);
                delegate.set(owned,new cpw.mods.fml.common.registry.RegistryDelegate.Delegate<net.minecraft.block.Block>(owned,net.minecraft.block.Block.class));
                require(net.minecraft.block.Block.getIdFromBlock(net.minecraft.block.Block.getBlockById(3501))!=3501
                        &&Item.itemRegistry.getObjectById(3501)==null,"Owned cube registry fixture ID is occupied");
                net.minecraft.block.Block.blockRegistry.addObject(3501,"nesql_fixture:rail_fuel_cube",owned);
                Item.itemRegistry.addObject(3501,"nesql_fixture:rail_fuel_cube",new net.minecraft.item.ItemBlock(owned));
                try{instance.set(null,owned);compare(manager,plugin,itemCache,flagCache);}
                finally{instance.set(null,oldInstance);}
            }finally{if(hadSetting)settings.put("cube.coke",oldSetting);else settings.remove("cube.coke");}
        } finally {
            cpw.mods.fml.relauncher.ReflectionHelper.setPrivateValue(cpw.mods.fml.common.Loader.class,loader,oldMods,"namedMods");
            controller.set(loader,oldController);
            resource.set(null,oldResource);coke.set(null,oldCoke);
            loaded.set(null,oldLoaded);modules.clear();modules.addAll(oldModules);
            itemCache.clear();itemCache.putAll(oldItems);flagCache.clear();flagCache.putAll(oldFlags);
        }
        System.out.println("Native cold Rail fuel definitions match owned native builder without initializing borrowed caches");
    }
    @SuppressWarnings("unchecked")
    private static void compare(Object manager,Class<?> plugin,Map<Object,Object> items,Map<Object,Object> flags)throws Exception{
        Object loaded=field(plugin,null,"modLoaded");Map<Object,Object> savedItems=new HashMap<>(items),savedFlags=new HashMap<>(flags);
        try(RailBlastFuelSource source=new RailBlastFuelSource(manager)){
            JsonArray captured=source.templates();
            require(field(plugin,null,"modLoaded")==loaded&&items.equals(savedItems)&&flags.equals(savedFlags)&&field(manager,"fuels")==null,"Cold capture mutated native state");
            List<ItemStack> actual;
            try{actual=(List<ItemStack>)manager.getClass().getMethod("getFuels").invoke(manager.getClass().newInstance());}
            finally{
                Field loadedField=plugin.getDeclaredField("modLoaded");loadedField.setAccessible(true);loadedField.set(null,loaded);
                items.clear();items.putAll(savedItems);flags.clear();flags.putAll(savedFlags);
            }
            require(actual.size()==captured.size(),"Cold definition count differs from native builder");
            for(int i=0;i<actual.size();i++)require(captured.get(i).getAsJsonObject().getAsJsonObject("template").equals(RecipeRow.stackEvidence(actual.get(i))),"Cold definition differs from native builder at "+i);
            source.verify();
        }
    }
    private static void fails(Runnable action,String code){try{action.run();throw new AssertionError("Expected "+code);}catch(Jobs.Fault failure){require(failure.code.equals(code),failure.toString());}}
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
