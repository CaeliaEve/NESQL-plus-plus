package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.ItemList;
import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.source.Identity;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.*;
import com.google.common.collect.ArrayListMultimap;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.*;
import net.minecraftforge.oredict.OreDictionary;
import java.nio.file.*;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Actual NEI-Integration handlers and Forge registrations, without launching a game. */
final class NativeForgeRegistryTest {
    @SuppressWarnings("unchecked") static void run() throws Exception {
        Class<?> config=type("tonius.neiintegration.config.Config");
        boolean oreEnabled=(Boolean)field(config,null,"handlerOreDictionary"),fluidEnabled=(Boolean)field(config,null,"handlerFluidRegistry");
        boolean ready=ItemList.loadFinished;Object panel=ItemList.itemMap;
        String[] containers={"containerFluidMap","filledContainerMap","emptyContainers"};Object[] savedContainers=new Object[3];for(int i=0;i<3;i++){Object raw=field(FluidContainerRegistry.class,null,containers[i]);savedContainers[i]=raw instanceof Map?new HashMap<>((Map<?,?>)raw):new HashSet<>((Set<?>)raw);}
        String[] fields={"nameToId","idToName","idToStack","idToStackUn","stackToId"};Object[] original=new Object[fields.length];
        for(int i=0;i<fields.length;i++)original[i]=field(OreDictionary.class,null,fields[i]);
        try {
            set(config,"handlerOreDictionary",true);set(config,"handlerFluidRegistry",true);
            set(OreDictionary.class,"nameToId",NativeRegistryScope.emptyOreStorage("nameToId"));set(OreDictionary.class,"idToName",new ArrayList<>());
            set(OreDictionary.class,"idToStack",new ArrayList<>());set(OreDictionary.class,"idToStackUn",new ArrayList<>());set(OreDictionary.class,"stackToId",NativeRegistryScope.emptyOreStorage("stackToId"));
            ItemList.itemMap=ArrayListMultimap.create();ItemList.loadFinished=true;
            ItemStack wildcard=new ItemStack(Items.dye,0,32767);wildcard.setTagCompound(new NBTTagCompound());wildcard.getTagCompound().setString("raw","retained");
            OreDictionary.registerOre("fixtureWild",wildcard);OreDictionary.getOreID("fixtureEmpty");
            OreDictionary.registerOre("fixtureExact",new ItemStack(Items.iron_ingot,7,0));
            ItemList.itemMap.put(Items.dye,new ItemStack(Items.dye,1,3));ItemList.itemMap.put(Items.dye,new ItemStack(Items.dye,1,9));
            // Preserve a non-bucket capacity and native tagged fluid/container association.
            FluidStack tagged=new FluidStack(FluidRegistry.WATER,250);tagged.tag=new NBTTagCompound();tagged.tag.setString("batch","registered");
            ItemStack filled=new ItemStack(Items.potionitem,3,17);filled.setTagCompound(new NBTTagCompound());filled.getTagCompound().setString("registered","filled");
            require(FluidContainerRegistry.registerFluidContainer(tagged,filled,new ItemStack(Items.glass_bottle,2)),"Tagged native container registration failed");
            JsonArray domains=array(),items=array(),fluids=array(),disabled=array();
            for(String suffix:new String[]{"OreDictionary","FluidRegistry"}) {
                TemplateRecipeHandler handler=(TemplateRecipeHandler)type("tonius.neiintegration.mods.mcforge.RecipeHandler"+suffix).newInstance();
                require(Recipes.adapter(handler)==Recipes.Adapter.FORGE_REGISTRY_REFERENCE,"Native Forge reference is shadowed by a consuming recipe adapter");
                TemplateRecipeHandler.CachedRecipe sentinel=handler.new CachedRecipe(){public codechicken.nei.PositionedStack getResult(){throw new AssertionError("GUI cache read");}};
                handler.arecipes.add(sentinel);
                RegistrationBindings registration=RegistrationBindings.read(handler);
                for(int i=0;i<4;i++)require(registration.same(RegistrationBindings.read((TemplateRecipeHandler)handler.getClass().newInstance())),"Independent registrations disagree on native source");
                JsonObject source=object("owner","neiintegration","handler",handler.getClass().getName(),"key",handler.getHandlerId());
                ForgeRegistryReferences reader=new ForgeRegistryReferences(handler,source);
                List<JsonObject> rows=(List<JsonObject>)field(reader,"records");JsonArray copies=array();for(JsonObject row:rows)copies.add(new JsonParser().parse(row.toString()));domains.add(copies);
                Map<String,ItemStack> specimens=(Map<String,ItemStack>)field(reader,"specimens");
                Map<String,FluidStack> liquids=(Map<String,FluidStack>)field(reader,"liquids");
                for(Map.Entry<String,ItemStack> entry:specimens.entrySet()){JsonObject item=RecipeRow.stackEvidence(entry.getValue());item.remove("amount");item.addProperty("id",entry.getKey());item.add("tags",array());items.add(item);}
                for(Map.Entry<String,FluidStack> entry:liquids.entrySet())fluids.add(object("id",entry.getKey(),"registry",entry.getValue().getFluid().getName(),"nbt",TypedNbt.encode(entry.getValue().tag),"temperature",entry.getValue().getFluid().getTemperature(entry.getValue()),"density",entry.getValue().getFluid().getDensity(entry.getValue()),"viscosity",entry.getValue().getFluid().getViscosity(entry.getValue()),"luminosity",entry.getValue().getFluid().getLuminosity(entry.getValue()),"gaseous",entry.getValue().getFluid().isGaseous(entry.getValue())));
                Facts facts=new Facts("en_US");((Set<String>)field(facts,"items")).addAll(specimens.keySet());((Set<String>)field(facts,"fluids")).addAll(liquids.keySet());
                for(int index=0;index<reader.size();index++)reader.capture(index,facts);
                require(handler.arecipes.size()==1&&handler.arecipes.get(0)==sentinel,"Reference reader changed borrowed native GUI cache");
                if(suffix.equals("OreDictionary")) {
                    JsonObject wild=rows.stream().map(row->row.getAsJsonObject("data")).filter(data->data.has("name")&&data.get("name").getAsString().equals("fixtureWild")).findFirst().get();
                    JsonObject member=wild.getAsJsonArray("members").get(0).getAsJsonObject();
                    require(member.get("meta").getAsInt()==32767&&member.get("amount").getAsInt()==0&&member.getAsJsonArray("displays").size()==2&&!member.get("nbt").isJsonNull(),"Wildcard, raw zero amount, NBT or concrete variants lost");
                    int id=OreDictionary.getOreID("fixtureWild");List<ItemStack> raw=(List<ItemStack>)((List<?>)field(OreDictionary.class,null,"idToStack")).get(id);ItemStack stack=raw.get(0);
                    try{raw.set(0,stack.copy());fails("environment_changed",reader::finishVerification);}finally{raw.set(0,stack);}
                    try{stack.stackSize=19;fails("environment_changed",reader::finishVerification);}finally{stack.stackSize=0;}
                    try{ItemList.loadFinished=false;fails("handler_not_ready",()->new ForgeRegistryReferences(handler,source));}finally{ItemList.loadFinished=true;}
                    List<ItemStack> exact=(List<ItemStack>)((List<?>)field(OreDictionary.class,null,"idToStack")).get(OreDictionary.getOreID("fixtureExact"));ItemStack saved=exact.get(0);
                    MetadataProbe probe=new MetadataProbe();Item.itemRegistry.addObject(12431,"nesql_forge_probe",probe);
                    try{exact.set(0,new ItemStack(probe));probe.calls=0;fails("reference_unsupported",()->new ForgeRegistryReferences(handler,source));require(probe.calls==0,"Foreign metadata callback executed");}finally{exact.set(0,saved);}
                    Object old=field(OreDictionary.class,null,"nameToId");try{set(OreDictionary.class,"nameToId",new HashMap<>((Map<?,?>)old));fails("environment_changed",reader::verify);}finally{set(OreDictionary.class,"nameToId",old);}
                } else {
                    JsonObject lava=rows.stream().map(row->row.getAsJsonObject("data")).filter(data->data.has("fluid")&&data.getAsJsonObject("fluid").get("registry").getAsString().equals("lava")).findFirst().get();
                    require(lava.getAsJsonObject("fluid").get("luminosity").getAsInt()==15,"Native fluid tooltip luminosity omitted");
                    require(!lava.getAsJsonObject("fluid").get("block").isJsonNull(),"Fluid block without an ItemBlock was lost");
                    JsonObject water=rows.stream().map(row->row.getAsJsonObject("data")).filter(data->data.has("fluid")&&data.getAsJsonObject("fluid").get("registry").getAsString().equals("water")).findFirst().get();
                    boolean found=false;for(JsonElement value:water.getAsJsonArray("containers")){JsonObject container=value.getAsJsonObject();if(container.getAsJsonObject("fluid").get("amount").getAsInt()==250){found=true;require(!container.getAsJsonObject("fluid").get("nbt").isJsonNull()&&container.getAsJsonObject("filled").get("amount").getAsInt()==3&&container.getAsJsonObject("empty").get("amount").getAsInt()==2,"Native container amount/NBT changed");}}
                    require(found,"Non-bucket tagged fluid registration was dropped");
                    net.minecraft.block.Block withoutItem=new net.minecraft.block.Block(net.minecraft.block.material.Material.water){};
                    net.minecraft.block.Block.blockRegistry.addObject(3515,"nesql_forge_unitemized",withoutItem);
                    java.lang.reflect.Field blockField=Fluid.class.getDeclaredField("block");blockField.setAccessible(true);Object oldBlock=blockField.get(FluidRegistry.LAVA);
                    try{blockField.set(FluidRegistry.LAVA,withoutItem);try(ForgeRegistryReferences unitemized=new ForgeRegistryReferences(handler,source)){
                        JsonObject entry=((List<JsonObject>)field(unitemized,"records")).stream().map(row->row.getAsJsonObject("data")).filter(data->data.has("fluid")&&data.getAsJsonObject("fluid").get("registry").getAsString().equals("lava")).findFirst().get();
                        require(entry.getAsJsonObject("fluid").getAsJsonObject("block").get("display").isJsonNull(),"Fluid block without an item was replaced or rejected");unitemized.finishVerification();
                    }}finally{blockField.set(FluidRegistry.LAVA,oldBlock);}
                    int density=FluidRegistry.LAVA.getDensity();try{FluidRegistry.LAVA.setDensity(-77);fails("environment_changed",reader::finishVerification);}finally{FluidRegistry.LAVA.setDensity(density);}
                }
                production(handler,facts);reader.finishVerification();String setting="handler"+suffix;
                try{set(config,setting,false);fails("environment_changed",reader::verify);ForgeRegistryReferences off=new ForgeRegistryReferences(handler,source);require(off.size()==1,"Disabled registry must retain definition proof");disabled.add(new JsonParser().parse(((List<JsonObject>)field(off,"records")).get(0).toString()));off.close();}finally{set(config,setting,true);}
                reader.close();reader.close();fails("capture_closed",reader::size);fails("capture_closed",reader::verify);
            }
            Files.createDirectories(Paths.get("build/native-tests"));Files.write(Paths.get("build/native-tests/native-forge-registry-contract.json"),CanonicalJson.bytes(object("domains",domains,"items",items,"fluids",fluids,"disabled",disabled)));
            System.out.println("Native Forge references: actual routing, raw wildcard/NBT/amounts, concrete display, empty groups, fluid properties, source drift and close passed");
        } finally {
            set(config,"handlerOreDictionary",oreEnabled);set(config,"handlerFluidRegistry",fluidEnabled);
            for(int i=0;i<fields.length;i++)set(OreDictionary.class,fields[i],original[i]);
            for(int i=0;i<3;i++){Object raw=field(FluidContainerRegistry.class,null,containers[i]);if(raw instanceof Map){((Map)raw).clear();((Map)raw).putAll((Map)savedContainers[i]);}else{((Set)raw).clear();((Set)raw).addAll((Set)savedContainers[i]);}}
            ItemList.itemMap=(ArrayListMultimap<Item,ItemStack>)panel;ItemList.loadFinished=ready;
        }
    }
    private static void production(TemplateRecipeHandler handler,Facts facts)throws Exception {
        java.lang.reflect.Field locale=null;
        for(java.lang.reflect.Field f:net.minecraft.client.resources.I18n.class.getDeclaredFields())if(f.getType()==net.minecraft.client.resources.Locale.class)locale=f;
        require(locale!=null,"Client locale root absent");locale.setAccessible(true);Object original=locale.get(null);
        try{locale.set(null,new net.minecraft.client.resources.Locale());try(Recipes.Cursor cursor=new Recipes.Handler(handler,0).open(facts)){
            require(cursor.reference()&&cursor.recordKind().equals("references"),"Forge production did not use the reference cursor");cursor.capture(0);cursor.finishVerification();
            for(Facts.Record record:facts.drain().records)require(!record.kind.equals("views")&&!record.kind.equals("tracks"),"Forge production emitted recipe GUI records");
        }}finally{locale.set(null,original);}
    }
    public static final class MetadataProbe extends Item {int calls;@Override public int getDamage(ItemStack stack){calls++;throw new AssertionError("Foreign metadata callback executed");}}
    private static void set(Class<?> owner,String name,Object value)throws Exception{java.lang.reflect.Field field=owner.getDeclaredField(name);field.setAccessible(true);field.set(null,value);}
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static void fails(String code,Runnable action){try{action.run();throw new AssertionError("Expected "+code);}catch(Jobs.Fault error){require(error.code.equals(code),"Expected "+code+", got "+error.code);}}
}
