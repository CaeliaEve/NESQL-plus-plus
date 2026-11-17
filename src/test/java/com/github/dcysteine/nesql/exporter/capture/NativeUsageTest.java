package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.recipe.IRecipeHandler;
import codechicken.nei.ItemList;
import com.google.gson.*;
import com.github.dcysteine.nesql.exporter.source.*;
import net.minecraft.init.Items;
import net.minecraft.item.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fluids.*;
import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.capture.NativeCellQueries.call;
import static com.github.dcysteine.nesql.exporter.source.Json.*;
import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Installed usage-only classes; no recipe GUI, live world, callback invocation or borrowed input. */
public final class NativeUsageTest {
    private static final String[] HANDLERS = {
        "appeng.integration.modules.NEIHelpers.NEICellViewHandler",
        "com.glodblock.github.nei.NEICellViewHandler",
        "li.cil.oc.integration.nei.ManualUsageHandler",
        "li.cil.oc.integration.nei.CallbackDocHandler"
    };
    static void run() throws Exception {
        try(NativeRegistryScope scope=new NativeRegistryScope()){
        NativeCoreFixesTest.version("appliedenergistics2","rv3-beta-695-GTNH");
        NativeCoreFixesTest.version("ae2fc","1.4.120-gtnh");
        NativeCoreFixesTest.version("OpenComputers","1.11.20-GTNH");
        NativeCoreFixesTest.version("computronics","1.9.3-GTNH");
        NativeCoreFixesTest.version("thaumicenergistics","1.7.14-GTNH");
        NativeCoreFixesTest.version("opensecurity","1.2.0-GTNH");
        sun.misc.Unsafe unsafe=(sun.misc.Unsafe)MagicApi.field(sun.misc.Unsafe.class,null,"theUnsafe");
        scope.replace(ItemList.class,null,"items",new ArrayList<ItemStack>());scope.replace(ItemList.class,null,"loadFinished",true);
        Object api=unsafe.allocateInstance(type("appeng.core.Api"));
        Object registries=unsafe.allocateInstance(type("appeng.core.features.registries.RegistryContainer"));
        Object cells=type("appeng.core.features.registries.CellRegistry").newInstance();
        set(registries,"cell",cells);set(api,"registryContainer",registries);set(api,"storageHelper",type("appeng.core.api.ApiStorage").newInstance());
        scope.replace(type("appeng.api.AEApi"),null,"HELD_API",api);
        List<Object> providers=(List<Object>)field(cells,"handlers");
        providers.add(type("appeng.core.features.registries.entries.BasicCellHandler").newInstance());
        providers.add(type("com.glodblock.github.common.storage.FluidCellHandler").newInstance());
        Item essentia=NativeCoreFixesTest.item("thaumicenergistics.common.items.ItemEssentiaCell","usage_essentia_cell");
        providers.add(essentia);
        Object ocItems=field(type("li.cil.oc.common.init.Items$"),null,"MODULE$");
        Object manual=field(type("li.cil.oc.client.Manual$"),null,"MODULE$");
        Object driver=field(type("li.cil.oc.server.driver.Registry$"),null,"MODULE$");
        scope.replace(type("li.cil.oc.api.API"),null,"items",ocItems);
        scope.replace(type("li.cil.oc.api.API"),null,"manual",manual);
        scope.replace(type("li.cil.oc.api.API"),null,"driver",driver);
        scope.replace(ocItems.getClass(),ocItems,"descriptors",type("scala.collection.mutable.HashMap").newInstance());
        scope.replace(ocItems.getClass(),ocItems,"names",type("scala.collection.mutable.HashMap").newInstance());
        scope.replace(ocItems.getClass(),ocItems,"registeredItems",buffer());
        for(String name:Arrays.asList("pathProviders","contentProviders","imageProviders","tabs"))scope.replace(manual.getClass(),manual,name,buffer());
        for(String name:Arrays.asList("environmentProviders","items","blocks"))scope.replace(driver.getClass(),driver,name,buffer());
        scope.replace(driver.getClass(),driver,"locked",true);
        add(call(manual,"pathProviders"),field(type("li.cil.oc.integration.opencomputers.ModOpenComputers$DefinitionPathProvider$"),null,"MODULE$"));
        add(call(manual,"contentProviders"),type("li.cil.oc.api.prefab.ResourceContentProvider").getConstructor(String.class,String.class).newInstance("opencomputers","doc/"));
        String image="li.cil.oc.client.renderer.markdown.segment.render.";
        for(String[] row:new String[][]{{"","Texture"},{"item:","Item"},{"block:","Block"},{"oredict:","OreDict"}})
            add(call(manual,"imageProviders"),type("scala.Tuple2").getConstructor(Object.class,Object.class).newInstance(row[0],field(type(image+row[1]+"ImageProvider$"),null,"MODULE$")));
        extensionAdmission(unsafe,cells,manual);
        for(String name:HANDLERS) {
            IRecipeHandler handler=(IRecipeHandler)unsafe.allocateInstance(MagicApi.type(name));
            Recipes.Handler entry=new Recipes.Handler(handler,0);
            if(!entry.supported)throw new AssertionError("Missing dedicated usage reader: "+name);
            if(entry.prototype!=null)throw new AssertionError("Usage handler was forced into a crafting prototype");
            RegistrationBindings first=RegistrationBindings.read(handler);
            if(first==null||!first.same(RegistrationBindings.read((IRecipeHandler)unsafe.allocateInstance(type(name)))))
                throw new AssertionError("Missing semantic registration roots: "+name);
        }
        JsonArray domains=array(),facts=array();
        cellQueries(unsafe,cells,domains,facts);
        manualQueries(scope,manual,ocItems,domains,facts);
        callbackQueries(driver,ocItems,domains,facts);
        Path output=Paths.get(System.getProperty("nesql.nativeUsageFixture","build/native-tests/usage-query-contract.json"));Files.createDirectories(output.toAbsolutePath().getParent());
        Map<String,JsonElement> unique=new LinkedHashMap<>();for(JsonElement item:facts){String id=item.getAsJsonObject().get("id").getAsString();JsonElement prior=unique.putIfAbsent(id,item);require(prior==null||prior.equals(item),"Conflicting native item fixture identity");}
        JsonArray uniqueFacts=array();for(JsonElement item:unique.values())uniqueFacts.add(item);
        JsonArray fluids=array();fluids.add(object("id",Identity.fluid("water",null),"registry","water","nbt",null));
        Files.write(output,CanonicalJson.bytes(object("domains",domains,"items",uniqueFacts,"fluids",fluids)));
        System.out.println("Four native usage readers: exact routes, registration roots, item/fluid long counts, owned NBT, manual redirects/PNG/fallback and callback metadata passed");
        }
    }
    private static void extensionAdmission(sun.misc.Unsafe unsafe,Object cells,Object manual)throws Exception{
        List<String> failures=new ArrayList<>();
        for(String name:new String[]{"com.glodblock.github.crossmod.opencomputers.DriverDualFluidInterface$Provider","com.glodblock.github.crossmod.opencomputers.DriverLevelMaintainer$Provider","com.glodblock.github.crossmod.opencomputers.DriverOCPatternEditor$Provider","pcl.opensecurity.drivers.MoarRam","pcl.opensecurity.drivers.RFIDReaderCardDriver","pcl.opensecurity.items.ItemSecureNetworkCard","pl.asie.computronics.integration.buildcraft.pluggable.ItemDockingUpgrade","pl.asie.computronics.item.ItemOCSpecialParts","pl.asie.computronics.item.ItemOpenComputers"}){
            Object instance=unsafe.allocateInstance(type(name));
            String role=name.endsWith("MoarRam")||name.endsWith("RFIDReaderCardDriver")?"itemDriver":"environment";
            try{NativeUsageAdmission.admit(instance,role);}catch(com.github.dcysteine.nesql.exporter.task.Jobs.Fault failure){failures.add(failure.getMessage());}
        }
        for(int i=0;i<2;i++)try{RegistrationBindings.read((IRecipeHandler)unsafe.allocateInstance(type(HANDLERS[i])));}catch(com.github.dcysteine.nesql.exporter.task.Jobs.Fault failure){failures.add(failure.getMessage());}
        Object unknown=new Object();List<Object> cellProviders=(List<Object>)field(cells,"handlers");cellProviders.add(unknown);
        try{
            RegistrationBindings.read((IRecipeHandler)unsafe.allocateInstance(type(HANDLERS[0])));
            try{new NativeCellQueries(HANDLERS[0]).read("en_US");throw new AssertionError("Unknown provider entered native queries");}catch(com.github.dcysteine.nesql.exporter.task.Jobs.Fault expected){require(expected.code.equals("reference_unsupported"),"Unknown cell provider lost its error");}
        }catch(com.github.dcysteine.nesql.exporter.task.Jobs.Fault failure){failures.add("Discovery was blocked by execution admission: "+failure.getMessage());}
        finally{cellProviders.remove(unknown);}
        Object paths=call(manual,"pathProviders");add(paths,unknown);
        try{RegistrationBindings.read((IRecipeHandler)unsafe.allocateInstance(type(HANDLERS[2])));}
        catch(com.github.dcysteine.nesql.exporter.task.Jobs.Fault failure){failures.add("Manual discovery was blocked by execution admission: "+failure.getMessage());}
        finally{call(paths,"remove",new Class<?>[]{int.class},((Number)call(paths,"length")).intValue()-1);}
        require(failures.isEmpty(),"Installed extension inventory/admission gaps: "+failures);
    }
    private static void cellQueries(sun.misc.Unsafe unsafe,Object cells,JsonArray domains,JsonArray facts)throws Exception{
        Item item=NativeCoreFixesTest.item("appeng.items.storage.ItemBasicStorageCell","usage_item_cell");
        set(item,"totalBytes",16000000000L);set(item,"perType",8);
        Item fluid=NativeCoreFixesTest.item("com.glodblock.github.common.item.ItemBasicFluidStorageCell","usage_fluid_cell");
        set(fluid,"totalBytes",16000000000L);set(fluid,"perType",8);set(fluid,"maxType",63);
        long large=3000000123L;
        ItemStack loaded=new ItemStack(item),empty=new ItemStack(item),liquid=new ItemStack(fluid),emptyLiquid=new ItemStack(fluid);
        NBTTagCompound cargo=new NBTTagCompound(),entry=new NBTTagCompound();new ItemStack(Items.iron_ingot).writeToNBT(entry);
        cargo.setTag("#0",entry);cargo.setLong("@0",large);cargo.setLong("ic",large);cargo.setShort("it",(short)1);loaded.setTagCompound(cargo);
        NBTTagCompound tank=new NBTTagCompound(),stored=new NBTTagCompound();new FluidStack(FluidRegistry.WATER,1).writeToNBT(stored);
        tank.setTag("#0",stored);tank.setLong("@0",large);tank.setLong("fc",large);tank.setShort("ft",(short)1);liquid.setTagCompound(tank);
        JsonElement before=TypedNbt.encode(cargo),beforeTank=TypedNbt.encode(tank);
        Item creative=NativeCoreFixesTest.item("appeng.items.storage.ItemCreativeStorageCell","usage_creative_cell");
        Object creativeProvider=type("appeng.core.features.registries.entries.CreativeCellHandler").newInstance();
        ((List<Object>)field(cells,"handlers")).add(creativeProvider);
        ItemList.items.addAll(Arrays.asList(loaded,empty,liquid,emptyLiquid,new ItemStack(Items.stick),loaded.copy(),new ItemStack(creative)));
        for(int mode=0;mode<2;mode++){
            String handler=HANDLERS[mode];NativeUsageReferences.Data data=new NativeCellQueries(handler).read("en_US");
            require(data.observations.size()==(mode==0?3:2)&&data.duplicates==1&&data.unmatched==(mode==0?3:4),"Native cell selection/counts differ");
            require(!data.items.containsKey(Identity.item("minecraft:stick",0,null)),"Unmatched usage inputs retained unnecessary NBT copies");
            JsonObject result=data.observations.get(0).getAsJsonObject().getAsJsonObject("result");
            require(result.get("totalBytes").getAsString().equals("16000000000"),"Cell byte capacity truncated");
            require(result.getAsJsonArray("entries").get(0).getAsJsonObject().get("amount").getAsString().equals(Long.toString(large)),"Cell entry long quantity truncated");
            require(data.observations.get(1).getAsJsonObject().getAsJsonObject("result").getAsJsonArray("entries").size()==0,"Empty cell became a missing/invalid query");
            if(mode==0){JsonObject fault=data.observations.get(2).getAsJsonObject().getAsJsonObject("result");
                require(fault.get("kind").getAsString().equals("cellFault")&&fault.get("fault").getAsString().equals("missingCellInventory"),"Native null inventory fault became an empty cell or non-match");}
            seal(data,handler,"en_US",domains,facts);
        }
        require(before.equals(TypedNbt.encode(cargo))&&beforeTank.equals(TypedNbt.encode(tank))&&empty.getTagCompound()==null&&emptyLiquid.getTagCompound()==null,"Cell query mutated borrowed NBT");
        ((List<Object>)field(cells,"handlers")).remove(creativeProvider);
        IRecipeHandler handler=(IRecipeHandler)unsafe.allocateInstance(type(HANDLERS[0]));
        try(NativeUsageReferences reader=new NativeUsageReferences(handler,origin(HANDLERS[0]),"en_US")){
            reader.finishVerification();Object original=((List<?>)field(cells,"handlers")).get(0);
            ((List<Object>)field(cells,"handlers")).set(0,type("appeng.core.features.registries.entries.BasicCellHandler").newInstance());
            try{reader.verify();throw new AssertionError("Replaced cell provider remained valid");}catch(com.github.dcysteine.nesql.exporter.task.Jobs.Fault error){require(error.code.equals("environment_changed"),"Wrong provider drift failure");}
            finally{((List<Object>)field(cells,"handlers")).set(0,original);}
        }
        ItemList.items.clear();
    }
    private static void manualQueries(NativeRegistryScope scope,Object manual,Object ocItems,JsonArray domains,JsonArray facts)throws Exception{
        call(ocItems,"registerItem",new Class<?>[]{Item.class,String.class},Items.paper,"eeprom");
        Object pathProvider=NativeOcQueries.sequence(call(manual,"pathProviders")).get(0);
        require("%LANGUAGE%/item/eeprom.md".equals(call(pathProvider,"pathFor",new Class<?>[]{ItemStack.class},new ItemStack(Items.paper))),"Native manual fixture registration is not a queryable item");
        ItemList.items.add(new ItemStack(Items.paper));ItemList.items.add(new ItemStack(Items.stick));
        java.awt.image.BufferedImage pixels=new java.awt.image.BufferedImage(2,1,java.awt.image.BufferedImage.TYPE_INT_ARGB);
        pixels.setRGB(0,0,0xffff0000);pixels.setRGB(1,0,0xff00ff00);ByteArrayOutputStream png=new ByteArrayOutputStream();javax.imageio.ImageIO.write(pixels,"PNG",png);
        Map<String,byte[]> files=new HashMap<>();
        files.put("opencomputers:doc/zh_CN/index.md","中文手册\n[芯片](item/eeprom.md)\n".getBytes(StandardCharsets.UTF_8));
        files.put("opencomputers:doc/en_US/index.md","English manual\n[EEPROM](item/eeprom.md)\n".getBytes(StandardCharsets.UTF_8));
        files.put("opencomputers:doc/en_US/item/eeprom.md","#redirect ../general/card.md\n".getBytes(StandardCharsets.UTF_8));
        files.put("opencomputers:doc/en_US/general/card.md","Native EEPROM\n[Home](../index.md)\n![Pixels](opencomputers:test.png)\n![Iron](item:minecraft:iron_ingot)\n".getBytes(StandardCharsets.UTF_8));
        files.put("opencomputers:test.png",png.toByteArray());
        Minecraft minecraft=scope.allocate(Minecraft.class);scope.replace(Minecraft.class,null,"theMinecraft",minecraft);
        Class<?> resource=type("net.minecraft.client.resources.IResource"),manager=type("net.minecraft.client.resources.IReloadableResourceManager");
        Object resources=Proxy.newProxyInstance(manager.getClassLoader(),new Class<?>[]{manager},(p,m,a)->{
            if(!m.getName().equals("getResource"))throw new AssertionError("Unexpected resource call: "+m);
            byte[] bytes=files.get(a[0].toString());if(bytes==null)throw new FileNotFoundException(a[0].toString());
            return Proxy.newProxyInstance(resource.getClassLoader(),new Class<?>[]{resource},(r,n,b)->{
                if(n.getName().equals("getInputStream"))return new ByteArrayInputStream(bytes);
                throw new AssertionError("Unexpected resource accessor: "+n);
            });
        });scope.replace(Minecraft.class,minecraft,"mcResourceManager",resources);
        for(String locale:Arrays.asList("zh_CN","en_US")){
            NativeUsageReferences.Data data=new NativeOcManual().read(locale);
            require(data.observations.size()==1&&data.unmatched==1,"Native manual item matching differs");
            require(data.resources.size()==1,"Manual native PNG closure is missing");
            JsonObject raw=data.resources.get(0).getAsJsonObject();require(Arrays.equals(Base64.getDecoder().decode(raw.get("base64").getAsString()),png.toByteArray()),"Manual native PNG bytes changed");
            JsonObject matched=data.documents.get(1).getAsJsonObject().getAsJsonObject("content");
            require(matched.get("fallbackAttempted").getAsBoolean()==locale.equals("zh_CN"),"Native language fallback differs");
            require(matched.getAsJsonArray("redirects").size()==2&&matched.getAsJsonArray("images").size()==2,"Manual redirect/content closure differs");
            seal(data,HANDLERS[2],locale,domains,facts);
        }
        IRecipeHandler handler=(IRecipeHandler)((sun.misc.Unsafe)field(sun.misc.Unsafe.class,null,"theUnsafe")).allocateInstance(type(HANDLERS[2]));
        try(NativeUsageReferences reader=new NativeUsageReferences(handler,origin(HANDLERS[2]),"zh_CN")){
            reader.finishVerification();files.put("opencomputers:doc/zh_CN/index.md","changed native manual".getBytes(StandardCharsets.UTF_8));
            try{reader.finishVerification();throw new AssertionError("Changed manual bytes remained valid");}
            catch(com.github.dcysteine.nesql.exporter.task.Jobs.Fault error){require(error.code.equals("environment_changed"),"Wrong manual drift failure");}
        }
        ItemList.items.clear();
    }
    private static void callbackQueries(Object driver,Object ocItems,JsonArray domains,JsonArray facts)throws Exception{
        Object provider=field(type("li.cil.oc.integration.opencomputers.DriverEEPROM$Provider$"),null,"MODULE$");add(field(driver,"environmentProviders"),provider);
        ItemList.items.add(new ItemStack(Items.paper));ItemList.items.add(new ItemStack(Items.stick));
        NativeUsageReferences.Data data=new NativeOcCallbacks().read("en_US");
        require(data.observations.size()==1&&data.unmatched==1&&data.documents.size()==1,"Native EEPROM API documentation lookup differs");
        JsonObject methods=data.documents.get(0).getAsJsonObject().getAsJsonObject("content");require(methods.getAsJsonArray("methods").size()>0,"Native callback annotations missing");
        JsonObject probe=NativeOcCallbacks.document(CallbackFixture.class).getAsJsonObject("content");
        require(probe.getAsJsonArray("methods").size()==1&&probe.get("ignoredAnnotations").getAsInt()==1,"Native annotation signature filtering differs");
        require(probe.getAsJsonArray("methods").get(0).getAsJsonObject().get("name").getAsString().equals("nativeData"),"Native callback name override lost");
        seal(data,HANDLERS[3],"en_US",domains,facts);ItemList.items.clear();
        Object foreign=new Object(){public Class<?> getEnvironment(ItemStack input){throw new AssertionError("Unknown provider executed");}};
        add(field(driver,"environmentProviders"),foreign);
        try{new NativeOcCallbacks().roots();throw new AssertionError("Unknown environment provider admitted");}
        catch(com.github.dcysteine.nesql.exporter.task.Jobs.Fault error){require(error.code.equals("reference_unsupported"),"Wrong unknown provider rejection");}
        finally{call(field(driver,"environmentProviders"),"$minus$eq",new Class<?>[]{Object.class},foreign);}
    }
    private static void seal(NativeUsageReferences.Data data,String handler,String locale,JsonArray domains,JsonArray facts){
        data.seal(origin(handler),locale);domains.add(data.records);
        for(Map.Entry<String,ItemStack> row:data.items.entrySet())facts.add(object("id",row.getKey(),"registry",Item.itemRegistry.getNameForObject(row.getValue().getItem()),"meta",Items.feather.getDamage(row.getValue()),"nbt",TypedNbt.encode(row.getValue().getTagCompound())));
    }
    private static JsonObject origin(String handler){return object("owner",handler.equals(HANDLERS[0])?"appliedenergistics2":handler.equals(HANDLERS[1])?"ae2fc":"OpenComputers","handler",handler,"key",handler);}
    private static Object buffer()throws Exception{return type("scala.collection.mutable.ArrayBuffer").newInstance();}
    private static void add(Object buffer,Object value){call(buffer,"$plus$eq",new Class<?>[]{Object.class},value);}
    private static void set(Object owner,String name,Object value)throws Exception{
        for(Class<?> type=owner.getClass();type!=null;type=type.getSuperclass())try{Field f=type.getDeclaredField(name);f.setAccessible(true);f.set(owner,value);return;}catch(NoSuchFieldException absent){}
        throw new AssertionError("Missing owned fixture field: "+name);
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static class CallbackFixture {
        public static Object[] inspect(Object context,Object arguments){throw new AssertionError("Documented callback was executed");}
        public static void invalid(){throw new AssertionError("Invalid documented callback was executed");}
    }
    /** Remove only display-offset initialization; every production query/registry method is native. */
    public static final class NoGuiInitialization implements IClassTransformer {
        public byte[] transform(String name,String transformed,byte[] bytes) {
            if(bytes==null)return bytes;
            boolean fixture="com.github.dcysteine.nesql.exporter.capture.NativeUsageTest$CallbackFixture".equals(transformed);
            if(!fixture&&!"appeng.integration.modules.NEIHelpers.NEICellViewHandler".equals(transformed)
                    &&!"com.glodblock.github.nei.NEICellViewHandler".equals(transformed)
                    &&!"appeng.api.AEApi".equals(transformed)&&!"appeng.core.Api".equals(transformed))return bytes;
            ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);
            if(fixture)for(MethodNode method:node.methods)if(method.name.equals("inspect")||method.name.equals("invalid")){
                if(method.name.equals("inspect"))method.desc="(Lli/cil/oc/api/machine/Context;Lli/cil/oc/api/machine/Arguments;)[Ljava/lang/Object;";
                AnnotationNode annotation=new AnnotationNode("Lli/cil/oc/api/machine/Callback;");annotation.values=new ArrayList<>();
                annotation.values.addAll(Arrays.asList("value","nativeData","doc","function():string -- Native metadata only","direct",true,"limit",3));
                method.visibleAnnotations=new ArrayList<>(Collections.singletonList(annotation));
            }
            for(MethodNode method:node.methods)if(method.name.equals("<clinit>")) {
                method.instructions.clear();method.tryCatchBlocks.clear();method.localVariables=null;
                method.instructions.add(new InsnNode(Opcodes.RETURN));method.maxStack=method.maxLocals=0;
            }
            ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);node.accept(writer);return writer.toByteArray();
        }
    }
}
