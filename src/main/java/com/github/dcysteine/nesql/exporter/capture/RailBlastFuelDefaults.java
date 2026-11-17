package com.github.dcysteine.nesql.exporter.capture;

import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonArray;
import cpw.mods.fml.common.Loader;
import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Pinned getFuels definition replay. Reads dependencies, never fills native lazy caches. */
final class RailBlastFuelDefaults {
    private enum Absent { VALUE }
    private static final String ROOT="mods.railcraft.common.";
    final List<ItemStack> fuels=new ArrayList<>();
    final List<Object> anchors=new ArrayList<>();
    private final String signature;

    static RailBlastFuelDefaults read(){return new RailBlastFuelDefaults();}

    private RailBlastFuelDefaults(){
        MagicApi.version("RailBlastFuelDefaults","Railcraft");
        thaumcraft();
        add(item(root("items.RailcraftToolItems"),"itemCoalCoke"),0);
        cokeBlock();
        add(required(Items.coal),0);add(required(Items.coal),1);
        Block block=Blocks.coal_block;anchor(block);
        if(block==null)throw unready("Vanilla coal block is unavailable");
        add(required(Item.getItemFromBlock(block)),0);
        add(item(root("items.firestone.ItemFirestoneRefined"),"item"),32767);
        add(item(root("items.firestone.ItemFirestoneCracked"),"item"),32767);
        JsonArray facts=array();for(ItemStack fuel:fuels)facts.add(RecipeRow.stackEvidence(fuel));
        signature=CanonicalJson.digest(facts);
    }

    void verify(){
        RailBlastFuelDefaults current;
        try{current=read();}
        catch(Jobs.Fault failure){Jobs.Fault changed=changed();changed.initCause(failure);throw changed;}
        if(!signature.equals(current.signature)||anchors.size()!=current.anchors.size())throw changed();
        for(int i=0;i<anchors.size();i++)if(anchors.get(i)!=current.anchors.get(i))throw changed();
    }

    private void thaumcraft(){
        Class<?> plugin=root("plugins.thaumcraft.ThaumcraftPlugin");
        Object loaded=field(plugin,null,"modLoaded");anchor(loaded);
        if(loaded!=null&&loaded.getClass()!=Boolean.class)throw unsupported("Unknown native Thaumcraft loaded flag");
        boolean present;
        if(loaded==null){
            Map<String,cpw.mods.fml.common.ModContainer> mods=Loader.instance().getIndexedModList();
            // getIndexedModList returns an immutable copy; bind the actual root,
            // not a fresh API projection that changes identity on every read.
            anchor(field(Loader.instance(),"namedMods"));anchor(mods.get("Thaumcraft"));
            if(mods.containsKey("Thaumcraft")){
                Object controller=field(Loader.instance(),"modController");anchor(controller);
                if(controller==null)throw unready("Native mod loading state is unavailable");
                if(controller.getClass()!=cpw.mods.fml.common.LoadController.class)
                    throw unsupported("Unknown native mod loading controller");
                String container=mods.get("Thaumcraft").getClass().getName();
                if(!container.equals("cpw.mods.fml.common.FMLModContainer")&&!container.equals("cpw.mods.fml.common.DummyModContainer"))
                    throw unsupported("Unknown native Thaumcraft mod container");
                Object states=field(controller,"modStates");anchor(states);
                if(states==null)throw unready("Native mod states are unavailable");
                if(states.getClass()!=com.google.common.collect.ArrayListMultimap.class)
                    throw unsupported("Unknown native mod state table");
            }
            present=Loader.isModLoaded("Thaumcraft");
            anchor(Boolean.valueOf(present));
        }else present=(Boolean)loaded;
        if(!present)return;
        MagicApi.version("RailBlastFuelDefaults","Thaumcraft");
        Map<?,?> cache=map(field(plugin,null,"itemCache"));anchor(cache);
        Object cached=cache.get("itemResource");anchor(cached);
        if(cached!=null){if(!(cached instanceof Item))throw unsupported("Unknown native Thaumcraft cached item");add((Item)cached,0);return;}
        Map<?,?> flags=map(field(plugin,null,"itemCacheFlag"));anchor(flags);
        Object flag=flags.get("itemResource");anchor(flag);
        if(flag!=null&&flag.getClass()!=Boolean.class)throw unsupported("Unknown native Thaumcraft item flag");
        if(Boolean.TRUE.equals(flag))return;
        // ItemApi.getItem reads this exact public Item field on the first lookup.
        // Its logging/caching error path is not executed or mistaken for an empty list.
        Object resource=field(type("thaumcraft.common.config.ConfigItems"),null,"itemResource");anchor(resource);
        if(resource!=null&&!(resource instanceof Item))throw unsupported("Unknown native Thaumcraft resource definition");
        add((Item)resource,0);
    }

    private void cokeBlock(){
        Object cube=field(root("blocks.aesthetics.cube.EnumCube"),null,"COKE_BLOCK");anchor(cube);
        if(cube==null||!cube.getClass().getName().equals(ROOT+"blocks.aesthetics.cube.EnumCube"))
            throw unready("Native Railcraft coke cube definition is unavailable");
        Object module=field(cube,"module");anchor(module);if(module==null)return;
        Object raw=field(root("modules.ModuleManager"),null,"loadedModules");anchor(raw);
        if(raw==null||!(raw instanceof EnumSet)||!raw.getClass().getName().startsWith("java.util."))
            throw unsupported("Unknown native Railcraft module set");
        boolean loaded=((Set<?>)raw).contains(module);anchor(Boolean.valueOf(loaded));if(!loaded)return;
        Object tag=field(cube,"tag");anchor(tag);
        if(!"coke".equals(tag))throw unsupported("Native Railcraft coke cube tag differs");
        Map<?,?> config=map(field(root("core.RailcraftConfig"),null,"enabledSubBlocks"));anchor(config);
        Object enabled=config.get("cube.coke");anchor(enabled);
        if(enabled==null)throw unready("Native Railcraft coke block setting is unavailable");
        if(enabled.getClass()!=Boolean.class)throw unsupported("Unknown native Railcraft coke setting");
        if(!(Boolean)enabled)return;
        Object block=field(root("blocks.aesthetics.cube.BlockCube"),null,"instance");anchor(block);
        if(block==null)return;
        if(!block.getClass().getName().equals(ROOT+"blocks.aesthetics.cube.BlockCube"))
            throw unsupported("Unknown native Railcraft cube block");
        add(required(Item.getItemFromBlock((Block)block)),((Enum<?>)cube).ordinal());
    }

    private Item item(Class<?> owner,String name){
        Object value=field(owner,null,name);anchor(value);
        if(value!=null&&!(value instanceof Item))throw unsupported("Unknown native Railcraft fuel item");
        return (Item)value;
    }
    private void add(Item item,int metadata){
        if(item==null)return;anchor(item);
        RailBlastFuelSource.audit(item);
        fuels.add(new ItemStack(item,1,metadata));
    }
    private Item required(Item item){anchor(item);if(item==null)throw unready("Native fuel item is unavailable");return item;}
    private void anchor(Object value){anchors.add(value==null?Absent.VALUE:value);}
    private static Class<?> root(String suffix){return type(ROOT+suffix);}
    private static Map<?,?> map(Object value){
        if(value==null)throw unready("Native Railcraft dependency map is unavailable");
        if(value.getClass()!=HashMap.class)throw unsupported("Unknown native Railcraft dependency map");
        return (Map<?,?>)value;
    }
    private static Jobs.Fault unready(String message){return new Jobs.Fault("handler_not_ready",message);}
    private static Jobs.Fault unsupported(String message){return new Jobs.Fault("recipe_unsupported",message);}
    private static Jobs.Fault changed(){return new Jobs.Fault("recipe_changed","Cold native Railcraft fuel dependencies changed");}
}
