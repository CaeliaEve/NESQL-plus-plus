package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import com.google.common.base.Optional;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.oredict.OreDictionary;
import java.util.*;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;

/** Installed AE2 callback fixture: actual facade grid and direct world handler contracts. */
public final class NativeAeRemainingTest {
    public static boolean forbidWorldViews;
    private NativeAeRemainingTest() {}
    public static void main(String[] args) throws Exception { GameTest.run(); }

    /** Snapshot boundary only: this witness does not admit the native target family. */
    public static void worldSnapshotOnly() throws Exception {
        sun.misc.Unsafe unsafe=(sun.misc.Unsafe)MagicApi.field(sun.misc.Unsafe.class,null,"theUnsafe");
        Class<?> apiClass=Class.forName("appeng.api.AEApi"),cfg=Class.forName("appeng.core.AEConfig");
        Field held=apiClass.getDeclaredField("HELD_API");held.setAccessible(true);
        Object oldApi=held.get(null),oldConfig=cfg.getField("instance").get(null);
        Object config=unsafe.allocateInstance(cfg),api=unsafe.allocateInstance(Class.forName("appeng.core.Api"));
        Object definitions=unsafe.allocateInstance(Class.forName("appeng.core.ApiDefinitions"));
        Object materials=unsafe.allocateInstance(Class.forName("appeng.core.api.definitions.ApiMaterials"));
        Object items=unsafe.allocateInstance(Class.forName("appeng.core.api.definitions.ApiItems"));
        Class enumType=Class.forName("appeng.core.features.AEFeature");
        EnumSet flags=EnumSet.noneOf(enumType);
        flags.add(Enum.valueOf(enumType,"MeteoriteWorldGen"));
        set(config,"featureFlags",flags);set(api,"definitions",definitions);
        set(definitions,"materials",materials);set(definitions,"items",items);
        Object logic=definition(new ItemStack(Items.paper)),calc=definition(new ItemStack(Items.map));
        set(materials,"logicProcessorPress",logic);set(materials,"calcProcessorPress",calc);
        set(materials,"engProcessorPress",definition(new ItemStack(Items.book)));
        unsafe.putObject(unsafe.staticFieldBase(held),unsafe.staticFieldOffset(held),api);
        cfg.getField("instance").set(null,config);
        try {
            ICraftingHandler world=(ICraftingHandler)Class.forName("appeng.integration.modules.NEIHelpers.NEIWorldCraftingHandler").newInstance();
            try(AeWorldRecipes reader=worldSnapshot(unsafe,world)){
                Object calcSource=MagicApi.field(calc,"source");
                Object raw=((Optional<?>)calcSource).get();
                set(raw,"damage",1);
                try{
                    reader.verify();
                    reader.capture(0,NativeCoreFixesTest.row(new ItemStack(Items.paper)));
                    expectFacadeFault("recipe_changed",()->reader.capture(1,NativeCoreFixesTest.row(new ItemStack(Items.map))));
                    expectFacadeFault("recipe_changed",reader::finishVerification);
                }finally{set(raw,"damage",0);}
                Object logicSource=((Optional<?>)MagicApi.field(logic,"source")).get();
                set(logicSource,"damage",1);
                try{expectFacadeFault("recipe_changed",()->reader.capture(0,NativeCoreFixesTest.row(new ItemStack(Items.paper))));}
                finally{set(logicSource,"damage",0);}
                cfg.getField("instance").set(null,null);
                try{expectFacadeFault("recipe_changed",reader::verify);}
                finally{cfg.getField("instance").set(null,config);}
                flags.clear();
                try{expectFacadeFault("recipe_changed",reader::verify);}
                finally{flags.add(Enum.valueOf(enumType,"MeteoriteWorldGen"));}
                RecipeRow failing=NativeCoreFixesTest.row(new ItemStack(Items.paper));
                RuntimeException primary=new RuntimeException("controlled row consumer failure");
                set(failing.facts,"items",new HashSet<String>(){
                    @Override public boolean contains(Object value){flags.clear();throw primary;}
                });
                try{
                    reader.capture(0,failing);throw new AssertionError("Row consumer failure was swallowed");
                }catch(RuntimeException failure){
                    if(failure!=primary||failure.getSuppressed().length!=1
                            ||!(failure.getSuppressed()[0] instanceof com.github.dcysteine.nesql.exporter.task.Jobs.Fault)
                            ||!((com.github.dcysteine.nesql.exporter.task.Jobs.Fault)failure.getSuppressed()[0]).code.equals("recipe_changed"))
                        throw new AssertionError("Post-callback drift did not preserve primary row failure",failure);
                }finally{flags.add(Enum.valueOf(enumType,"MeteoriteWorldGen"));}
                reader.finishVerification();
            }
            worldSnapshotOres(unsafe,world,flags,enumType,materials);
        }finally{
            unsafe.putObject(unsafe.staticFieldBase(held),unsafe.staticFieldOffset(held),oldApi);
            cfg.getField("instance").set(null,oldConfig);
        }
        System.out.println("AE2 world snapshot: unrelated definition deferred to sealing; touched definition, config and feature guards passed");
    }

    private static void worldSnapshotOres(sun.misc.Unsafe unsafe,ICraftingHandler world,EnumSet flags,Class enumType,Object materials)throws Exception{
        flags.add(Enum.valueOf(enumType,"InWorldSingularity"));
        set(materials,"qESingularity",definition(new ItemStack(Items.ender_eye)));
        set(materials,"singularity",definition(new ItemStack(Items.nether_star)));
        List<ItemStack> catalog=new ArrayList<>(Collections.singletonList(new ItemStack(Items.ender_pearl)));
        try(NativeRegistryScope scope=new NativeRegistryScope()){
            OreDictionary.registerOre("dustEnder",new ItemStack(Items.ender_pearl,1,32767));
            List<ItemStack> members=(List<ItemStack>)(List<?>)NativeOreStorage.arrayList(OreDictionary.getOres("dustEnder"));
            scope.replace(codechicken.nei.ItemList.class,null,"items",catalog);
            scope.replace(codechicken.nei.ItemList.class,null,"loadFinished",true);
            try(AeWorldRecipes reader=worldSnapshot(unsafe,world)){
                RecipeRow row=NativeCoreFixesTest.row(new ItemStack(Items.ender_eye),new ItemStack(Items.nether_star),new ItemStack(Items.ender_pearl));
                if(!reader.capture(3,row)||row.inputs.size()!=2)throw new AssertionError("Expected singularity sources, got "+row.record+" exclusion="+row.exclusion);
                if(row.inputs.get(1).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject()
                        .getAsJsonObject("rule").get("metadata").getAsInt()!=32767)throw new AssertionError("World snapshot lost native wildcard rule");
                ItemStack member=members.get(0);members.set(0,member.copy());
                try{
                    reader.verify();reader.capture(0,NativeCoreFixesTest.row(new ItemStack(Items.paper)));
                    expectFacadeFault("recipe_changed",()->reader.capture(3,row));
                    expectFacadeFault("recipe_changed",reader::finishVerification);
                }finally{members.set(0,member);}
                int count=member.stackSize;member.stackSize++;
                try{expectFacadeFault("recipe_changed",()->reader.capture(3,row));}
                finally{member.stackSize=count;}
                catalog.get(0).setItemDamage(1);
                try{
                    reader.verify();reader.capture(0,NativeCoreFixesTest.row(new ItemStack(Items.paper)));
                    expectFacadeFault("recipe_changed",()->reader.capture(3,row));
                    expectFacadeFault("recipe_changed",reader::finishVerification);
                }finally{catalog.get(0).setItemDamage(0);}
                codechicken.nei.ItemList.loadFinished=false;
                try{expectFacadeFault("recipe_changed",reader::verify);}
                finally{codechicken.nei.ItemList.loadFinished=true;}
                reader.finishVerification();
            }
            catalog.clear();
            java.lang.reflect.Constructor<AeWorldSource> sourceConstructor=AeWorldSource.class.getDeclaredConstructor(boolean.class);
            sourceConstructor.setAccessible(true);
            try{
                Object[] roots=sourceConstructor.newInstance(false).anchors();
                if(!Arrays.stream(roots).anyMatch(anchor->anchor==catalog))throw new AssertionError("Registration lost its catalogue root anchor");
                codechicken.nei.ItemList.loadFinished=false;
                try{sourceConstructor.newInstance(false).anchors();}
                finally{codechicken.nei.ItemList.loadFinished=true;}
            }catch(java.lang.reflect.InvocationTargetException failure){
                throw new AssertionError("Source discovery must not materialize wildcard examples",failure.getCause());
            }
            try{
                worldSnapshot(unsafe,world);throw new AssertionError("World reader accepted a wildcard without concrete examples");
            }catch(java.lang.reflect.InvocationTargetException failure){
                if(!(failure.getCause() instanceof com.github.dcysteine.nesql.exporter.task.Jobs.Fault)
                        ||!((com.github.dcysteine.nesql.exporter.task.Jobs.Fault)failure.getCause()).code.equals("recipe_unsupported"))
                    throw failure;
            }
        }
        System.out.println("AE2 world snapshot: ore member identity/count, wildcard metadata/readiness and registration/reader no-example distinction passed");
    }

    private static AeWorldRecipes worldSnapshot(sun.misc.Unsafe unsafe,ICraftingHandler world)throws Exception{
        java.lang.reflect.Constructor<AeWorldSource> constructor=AeWorldSource.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        AeWorldRecipes reader=(AeWorldRecipes)unsafe.allocateInstance(AeWorldRecipes.class);
        set(reader,"handler",world);set(reader,"source",constructor.newInstance());
        java.lang.reflect.Method read=AeWorldRecipes.class.getDeclaredMethod("read");read.setAccessible(true);
        List<?> pages=(List<?>)read.invoke(reader);set(reader,"pages",pages);
        java.lang.reflect.Method digest=AeWorldRecipes.class.getDeclaredMethod("digest",List.class);digest.setAccessible(true);
        set(reader,"signature",digest.invoke(null,pages));
        return reader;
    }

    public static void worldOreStorageOnly() throws Exception {
        Field access=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");access.setAccessible(true);
        adapterFixtures((sun.misc.Unsafe)access.get(null),Items.stick,new ItemStack(Items.stick),false);
        System.out.println("AE2 world native rules, registration equivalence and ore drift passed with optimized="+Boolean.getBoolean("nesql.nativeOreOptimized"));
    }

    public static void run() throws Exception {
        Class<?> worldClass = Class.forName("appeng.integration.modules.NEIHelpers.NEIWorldCraftingHandler");
        ICraftingHandler world = (ICraftingHandler) worldClass.newInstance();
        if (Recipes.adapter(world) == null) throw new AssertionError("Missing AE2 direct world dispatch");
        if (world instanceof TemplateRecipeHandler || !AeWorldRecipes.supports(world)
                || world.getRecipeHandler("not-an-item") != world || world.numRecipes() != 0)
            throw new AssertionError("AE2 in-world handler must retain its direct ICraftingHandler contract");

        Class<?> facadeClass = Class.forName("appeng.items.parts.ItemFacade");
        Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); unsafeField.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);
        Object facade = NativeCoreFixesTest.item(facadeClass.getName(), "ae_facade");
        Class<?> comparable = Class.forName("appeng.api.definitions.IComparableDefinition");
        Object anchor = Proxy.newProxyInstance(comparable.getClassLoader(), new Class<?>[] {comparable}, (proxy, method, arguments) -> {
            if (method.getName().equals("isSameAs")) return arguments[0] instanceof ItemStack
                    && ((ItemStack) arguments[0]).getItem() == Items.iron_ingot;
            if (method.getName().equals("toString")) return "fixture-anchor";
            throw new AssertionError("Unexpected anchor callback: " + method);
        });
        Class<?> recipeClass = Class.forName("appeng.recipes.game.FacadeRecipe");
        Object recipe = unsafe.allocateInstance(recipeClass);
        set(recipe, "anchor", anchor);
        set(recipe, "maybeFacade", Optional.of(facade));
        Class<?> configClass = Class.forName("appeng.core.FacadeConfig");
        Object previous = configClass.getField("instance").get(null);
        Field minecraftHome = Class.forName("cpw.mods.fml.relauncher.FMLInjectionData").getDeclaredField("minecraftHome");
        minecraftHome.setAccessible(true); Object previousHome = minecraftHome.get(null);
        minecraftHome.set(null, new File("."));
        configClass.getField("instance").set(null, configClass.getConstructor(File.class).newInstance(new File("build/native-tests/facade-fixture.cfg")));
        try {
            InventoryCrafting grid = new InventoryCrafting(new Container() {
                @Override public boolean canInteractWith(EntityPlayer player) { return true; }
            }, 3, 3);
            for (int cell : new int[] {1, 3, 5, 7}) grid.setInventorySlotContents(cell, new ItemStack(Items.iron_ingot));
            grid.setInventorySlotContents(4, new ItemStack(Blocks.stone));
            java.lang.reflect.Method matches = recipeClass.getMethod("matches", InventoryCrafting.class, net.minecraft.world.World.class);
            java.lang.reflect.Method result = recipeClass.getMethod("getCraftingResult", InventoryCrafting.class);
            if (!(Boolean) matches.invoke(recipe, grid, null)) throw new AssertionError("Native facade cross failed");
            ItemStack crafted = (ItemStack) result.invoke(recipe, grid);
            if (crafted == null || crafted.stackSize != 4 || crafted.getItem() != facade)
                throw new AssertionError("Native facade result must be a fresh four-stack");
            grid.setInventorySlotContents(0, new ItemStack(Items.apple));
            if ((Boolean) matches.invoke(recipe, grid, null)) throw new AssertionError("Native facade accepted a filled corner");
            grid.setInventorySlotContents(0, null);
            ItemStack tagged = new ItemStack(Blocks.stone); tagged.setTagInfo("owner", new net.minecraft.nbt.NBTTagString("fixture"));
            grid.setInventorySlotContents(4, tagged);
            if ((Boolean) matches.invoke(recipe, grid, null)) throw new AssertionError("Native facade accepted a tagged texture");
            tagged.setTagCompound(new NBTTagCompound()); grid.setInventorySlotContents(4, tagged);
            if ((Boolean) matches.invoke(recipe, grid, null)) throw new AssertionError("Native facade accepted even empty root NBT");
            grid.setInventorySlotContents(4, new ItemStack(Blocks.stone));
            grid.setInventorySlotContents(1, new ItemStack(Items.gold_ingot));
            if ((Boolean) matches.invoke(recipe, grid, null)) throw new AssertionError("Native facade accepted a wrong anchor");
            adapterFixtures(unsafe, facade, crafted);
        } finally { configClass.getField("instance").set(null, previous); minecraftHome.set(null, previousHome); }
        System.out.println("Native AE2 Facade and In-world: direct handler, cross/corners, four output, NBT and anchor callbacks passed");
    }

    /** Registry/feature setup is isolated; all cursor, grid and growth callbacks remain native. */
    private static void adapterFixtures(sun.misc.Unsafe unsafe, Object facade, ItemStack crafted) throws Exception {
        adapterFixtures(unsafe,facade,crafted,true);
    }
    private static void adapterFixtures(sun.misc.Unsafe unsafe, Object facade, ItemStack crafted, boolean includeFacades) throws Exception {
        Map<String,cpw.mods.fml.common.ModContainer> nativeMods=(Map<String,cpw.mods.fml.common.ModContainer>)MagicApi.field(cpw.mods.fml.common.Loader.instance(),"namedMods");
        cpw.mods.fml.common.ModContainer previousMod=nativeMods.get("appliedenergistics2");
        NativeCoreFixesTest.version("appliedenergistics2", "rv3-beta-695-GTNH");
        Class<?> apiClass=Class.forName("appeng.api.AEApi"); Field held=apiClass.getDeclaredField("HELD_API");held.setAccessible(true);
        Object oldApi=held.get(null);
        Class<?> cfg=Class.forName("appeng.core.AEConfig");Object oldConfig=cfg.getField("instance").get(null);
        Object config=unsafe.allocateInstance(cfg);
        Class enumClass=Class.forName("appeng.core.features.AEFeature");
        java.util.EnumSet enabled=java.util.EnumSet.allOf(enumClass);set(config,"featureFlags",enabled);
        cfg.getField("instance").set(null,config);
        Item seed=NativeCoreFixesTest.item("appeng.items.misc.ItemCrystalSeed", "ae_seed");seed.setHasSubtypes(true);
        Map<String,ItemStack> materials=new LinkedHashMap<>();
        String[] names={"certusQuartzCrystalCharged","logicProcessorPress","calcProcessorPress","engProcessorPress",
                "fluixCrystal","qESingularity","purifiedCertusQuartzCrystal","purifiedNetherQuartzCrystal","purifiedFluixCrystal","singularity"};
        Item[] samples={Items.diamond,Items.paper,Items.map,Items.book,Items.emerald,Items.ender_eye,Items.gold_ingot,
                Items.iron_ingot,Items.gold_nugget,Items.nether_star};
        Map<String,Object> definitions=new HashMap<>();
        for(int i=0;i<names.length;i++){ItemStack stack=new ItemStack(samples[i]);materials.put(names[i],stack);definitions.put(names[i],definition(stack));}
        Object materialsApi=unsafe.allocateInstance(Class.forName("appeng.core.api.definitions.ApiMaterials"));
        for(Map.Entry<String,Object> entry:definitions.entrySet())set(materialsApi,entry.getKey(),entry.getValue());
        Map<String,Object> itemDefinitions=new HashMap<>();itemDefinitions.put("facade",definition(new ItemStack((Item)facade)));
        itemDefinitions.put("crystalSeed",definition(new ItemStack(seed)));
        Object itemsApi=unsafe.allocateInstance(Class.forName("appeng.core.api.definitions.ApiItems"));
        for(Map.Entry<String,Object> entry:itemDefinitions.entrySet())set(itemsApi,entry.getKey(),entry.getValue());
        Object definitionsApi=unsafe.allocateInstance(Class.forName("appeng.core.ApiDefinitions"));
        set(definitionsApi,"materials",materialsApi);set(definitionsApi,"items",itemsApi);
        Object api=unsafe.allocateInstance(Class.forName("appeng.core.Api"));set(api,"definitions",definitionsApi);
        unsafe.putObject(unsafe.staticFieldBase(held),unsafe.staticFieldOffset(held),api);
        OreDictionary.registerOre("dustEnder",new ItemStack(Items.ender_pearl));
        if(includeFacades)set(facade,"subTypes",new ArrayList<>(Collections.singletonList(crafted.copy())));
        try {
            if(includeFacades)facadeFixtures(unsafe,facade,crafted,held);
            ICraftingHandler world=(ICraftingHandler)Class.forName("appeng.integration.modules.NEIHelpers.NEIWorldCraftingHandler").newInstance();
            forbidWorldViews=true;
            int oreNameCount=((Map<?,?>)MagicApi.field(OreDictionary.class,null,"nameToId")).size();
            AeWorldRecipes cursor=new AeWorldRecipes(world);
            if(((Map<?,?>)MagicApi.field(OreDictionary.class,null,"nameToId")).size()!=oreNameCount)throw new AssertionError("AE2 reader registered a missing ore name");
            List<ItemStack> known=new ArrayList<>(materials.values());
            known.addAll(Arrays.asList(new ItemStack(Items.redstone),new ItemStack(Items.quartz),new ItemStack(Items.ender_pearl),
                    new ItemStack(seed,1,0),new ItemStack(seed,1,600),new ItemStack(seed,1,1200)));
            if(cursor.size()!=9)throw new AssertionError("Native enabled world pages were omitted");
            worldLanguages(world,known);
            Field rawOres=OreDictionary.class.getDeclaredField("idToStack");rawOres.setAccessible(true);
            List<ItemStack> nativeEnderAlternatives=((List<List<ItemStack>>)rawOres.get(null)).get(OreDictionary.getOreID("dustEnder"));
            ItemStack duplicateEnder=nativeEnderAlternatives.get(0).copy();
            nativeEnderAlternatives.add(duplicateEnder);
            try {
                AeWorldRecipes duplicateCursor=new AeWorldRecipes(world);
                RecipeRow duplicateRow=NativeCoreFixesTest.row(known.toArray(new ItemStack[0]));
                duplicateCursor.capture(5,duplicateRow);
                com.google.gson.JsonObject process=duplicateRow.record.getAsJsonObject("process");
                int choices=duplicateRow.inputs.get(1).getAsJsonObject().getAsJsonArray("choices").size();
                if(process.getAsJsonArray("sourceIdentities").get(1).getAsJsonArray().size()!=choices)
                    throw new AssertionError("Duplicate ore member desynchronized native identities and inputs");
                duplicateCursor.close();
            } finally { nativeEnderAlternatives.remove(duplicateEnder); }
            for(int i=0;i<cursor.size();i++){
                RecipeRow row=NativeCoreFixesTest.row(known.toArray(new ItemStack[0]));cursor.capture(i,row);
                if(!row.record.getAsJsonObject("process").get("kind").getAsString().equals("ae2WorldAcquisition"))
                    throw new AssertionError("World acquisition has no typed conditional action");
                row.record.add("source",com.github.dcysteine.nesql.exporter.source.Json.object("owner","appliedenergistics2","handler",world.getClass().getName(),"key","ae-world-"+i));
                row.record.addProperty("category",com.github.dcysteine.nesql.exporter.source.Identity.origin("category",row.record.getAsJsonObject("source")));
                // Localized native transport is generated by worldLanguages; the
                // old semantic fixtures remain historical development evidence.
            }
            // The direct Handler.open path must never cast this native handler to TemplateRecipeHandler.
            Facts facts=NativeCoreFixesTest.row(known.toArray(new ItemStack[0])).facts;
            try(Recipes.Cursor opened=new Recipes.Handler(world,0).open(facts)){
                if(opened.size()!=9)throw new AssertionError("Direct open lost native world pages");opened.capture(4);opened.capture(5);opened.finishVerification();
            }
            java.lang.reflect.Method growth=seed.getClass().getMethod("triggerGrowth",ItemStack.class);
            java.lang.reflect.Method multiplier=seed.getClass().getMethod("getMultiplier",net.minecraft.block.Block.class,net.minecraft.block.material.Material.class);
            if(!Blocks.lava.getMaterial().isLiquid()
                    || Float.floatToIntBits((Float)multiplier.invoke(seed,Blocks.lava,Blocks.lava.getMaterial()))
                    !=Float.floatToIntBits((Float)multiplier.invoke(seed,Blocks.water,Blocks.water.getMaterial())))
                throw new AssertionError("Native seed growth narrowed liquid material to water");
            for(int band=0;band<3;band++) {
                ItemStack source=new ItemStack(seed,7,band*600);source.setTagInfo("progress",new net.minecraft.nbt.NBTTagInt((band+1)*600-1));
                source.setTagInfo("owner",new net.minecraft.nbt.NBTTagString("must-not-survive"));
                ItemStack output=(ItemStack)growth.invoke(seed,source);
                if(output.getItem()!=samples[band+6]||output.stackSize!=7||output.getTagCompound()!=null)
                    throw new AssertionError("Native seed completion must preserve count and use fresh definition NBT");
            }
            Object singularitySource=((Optional<?>)MagicApi.field(definitions.get("singularity"),"source")).get();set(singularitySource,"damage",1);
            try{cursor.capture(5,NativeCoreFixesTest.row(known.toArray(new ItemStack[0])));throw new AssertionError("Native singularity input drift escaped sealed snapshot");}
            catch(com.github.dcysteine.nesql.exporter.task.Jobs.Fault expected){if(!expected.code.equals("recipe_changed"))throw expected;}
            finally{set(singularitySource,"damage",0);}
            Object seedDefinition=itemDefinitions.get("crystalSeed");
            set(itemsApi,"crystalSeed",definition(new ItemStack(Items.paper)));
            try{cursor.capture(6,NativeCoreFixesTest.row(known.toArray(new ItemStack[0])));throw new AssertionError("Native seed definition drift escaped sealed snapshot");}
            catch(com.github.dcysteine.nesql.exporter.task.Jobs.Fault expected){if(!expected.code.equals("recipe_changed"))throw expected;}
            finally{set(itemsApi,"crystalSeed",seedDefinition);}
            ItemStack enderAlternative=OreDictionary.getOres("dustEnder").get(0);
            int enderMeta=enderAlternative.getItemDamage();enderAlternative.setItemDamage(enderMeta+1);
            try{cursor.capture(5,NativeCoreFixesTest.row(known.toArray(new ItemStack[0])));throw new AssertionError("Native ender alternatives drift escaped sealed snapshot");}
            catch(com.github.dcysteine.nesql.exporter.task.Jobs.Fault expected){if(!expected.code.equals("recipe_changed"))throw expected;}
            finally{enderAlternative.setItemDamage(enderMeta);}
            cursor.verify();
            worldBoundaries(unsafe,world,cursor,api,materialsApi,config,cfg,enabled,definitions,nativeEnderAlternatives,known);
            enabled.remove(Enum.valueOf(enumClass,"InWorldFluix"));
            try{cursor.verify();throw new AssertionError("Native feature drift escaped sealed snapshot");}
            catch(com.github.dcysteine.nesql.exporter.task.Jobs.Fault expected){if(!expected.code.equals("recipe_changed"))throw expected;}
            cursor.close();cursor.close();expectFacadeFault("capture_closed",cursor::size);
        } finally {
            forbidWorldViews=false;
            unsafe.putObject(unsafe.staticFieldBase(held),unsafe.staticFieldOffset(held),oldApi);cfg.getField("instance").set(null,oldConfig);
            if(previousMod==null)nativeMods.remove("appliedenergistics2");else nativeMods.put("appliedenergistics2",previousMod);
        }
    }

    private static void worldBoundaries(sun.misc.Unsafe unsafe,ICraftingHandler world,AeWorldRecipes cursor,Object api,Object materialsApi,
            Object config,Class<?> cfg,EnumSet enabled,Map<String,Object> definitions,List<ItemStack> ender,List<ItemStack> known) throws Exception {
        ICraftingHandler second=(ICraftingHandler)world.getClass().newInstance();
        if(!RegistrationBindings.read(world).same(RegistrationBindings.read(second)))throw new AssertionError("AE2 world registrations lost native source identity");
        Object previous=definitions.get("logicProcessorPress");set(materialsApi,"logicProcessorPress",definition(new ItemStack(Items.paper)));
        try{expectFacadeFault("recipe_changed",cursor::finishVerification);}finally{set(materialsApi,"logicProcessorPress",previous);}
        Object optional=MagicApi.field(previous,"source");Class<?> sourceType=Class.forName("appeng.core.features.IStackSrc");int[] calls={0};
        Object unknown=Proxy.newProxyInstance(sourceType.getClassLoader(),new Class<?>[]{sourceType},(p,m,a)->{calls[0]++;throw new AssertionError("Unknown AE2 world source callback executed");});
        set(previous,"source",Optional.of(unknown));try{expectFacadeFault("recipe_unsupported",()->new AeWorldRecipes(world));if(calls[0]!=0)throw new AssertionError("AE2 source callback was audited too late");}finally{set(previous,"source",optional);}
        EnumSet saved=enabled.clone();enabled.clear();
        try(AeWorldRecipes empty=new AeWorldRecipes(world)){if(empty.size()!=0)throw new AssertionError("Disabled native world feature set was not empty");empty.finishVerification();}
        finally{enabled.addAll(saved);}
        cfg.getField("instance").set(null,null);try{expectFacadeFault("handler_not_ready",()->new AeWorldRecipes(world));expectFacadeFault("recipe_changed",cursor::verify);}finally{cfg.getField("instance").set(null,config);}
        int count=ender.get(0).stackSize;ender.get(0).stackSize=0;
        try(AeWorldRecipes zero=new AeWorldRecipes(world)){
            RecipeRow row=NativeCoreFixesTest.row(known.toArray(new ItemStack[0]));if(!zero.capture(5,row)||!row.inputs.get(1).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject().get("amount").getAsString().equals("1"))throw new AssertionError("Native ore membership incorrectly used registered stack amount");
        }finally{ender.get(0).stackSize=count;}
        Map<?,?> names=(Map<?,?>)MagicApi.field(OreDictionary.class,null,"nameToId");List<?> table=(List<?>)MagicApi.field(OreDictionary.class,null,"idToStack");
        List<List<ItemStack>> lists=new ArrayList<>(),copies=new ArrayList<>();
        for(String name:new String[]{"dustEnder","dustEnderPearl"})if(names.containsKey(name)){
            List<ItemStack> list=(List<ItemStack>)table.get((Integer)names.get(name));lists.add(list);copies.add(new ArrayList<>(list));list.clear();
        }
        try(AeWorldRecipes missing=new AeWorldRecipes(world)){
            RecipeRow row=NativeCoreFixesTest.row(known.toArray(new ItemStack[0]));if(missing.capture(5,row)||row.exclusion==null)throw new AssertionError("Unreachable native ore action lacks exclusion proof");missing.finishVerification();
        }finally{for(int i=0;i<lists.size();i++)lists.get(i).addAll(copies.get(i));}
        worldEnderPredicates(world,ender,known);
        // Startup-only enum initialization registers mod entities. Use an owned instance;
        // MaterialStackSrc, MaterialType.stack and definition bodies remain native.
        Object growth=unsafe.allocateInstance(Class.forName("appeng.items.materials.MaterialType"));
        Object oldItem=MagicApi.field(growth,"itemInstance");int oldDamage=(Integer)MagicApi.field(growth,"damageValue");
        set(growth,"itemInstance",Items.diamond);set(growth,"damageValue",0);
        Object materialSource=Class.forName("appeng.core.features.MaterialStackSrc").getConstructor(growth.getClass()).newInstance(growth);
        Object materialDefinition=Class.forName("appeng.core.features.DamagedItemDefinition").getConstructor(sourceType).newInstance(materialSource);
        ItemStack nativeStack=(ItemStack)MagicApi.invoke(materialSource.getClass(),materialSource,"stack",new Class<?>[]{int.class},1);
        if(nativeStack.getItem()!=Items.diamond||nativeStack.getItemDamage()!=0)throw new AssertionError("Native material source fixture is invalid");
        Object charged=definitions.get("certusQuartzCrystalCharged");set(materialsApi,"certusQuartzCrystalCharged",materialDefinition);
        try(AeWorldRecipes nativeMaterial=new AeWorldRecipes(world)){
            nativeMaterial.capture(0,NativeCoreFixesTest.row(known.toArray(new ItemStack[0])));set(growth,"damageValue",1);
            expectFacadeFault("recipe_changed",nativeMaterial::finishVerification);
        }finally{set(materialsApi,"certusQuartzCrystalCharged",charged);set(growth,"itemInstance",oldItem);set(growth,"damageValue",oldDamage);}
        cursor.finishVerification();
    }
    private static void worldEnderPredicates(ICraftingHandler world,List<ItemStack> members,List<ItemStack> known)throws Exception{
        com.google.gson.JsonArray observations=com.github.dcysteine.nesql.exporter.source.Json.array();
        for(int registered:new int[]{0,32767})for(int offered:new int[]{0,1,32767})for(boolean tagged:new boolean[]{false,true})for(int count:new int[]{1,3}){
            ItemStack template=new ItemStack(Items.ender_pearl,99,registered),input=new ItemStack(Items.ender_pearl,count,offered);
            if(tagged)input.setTagCompound(new NBTTagCompound());
            boolean accepted=OreDictionary.itemMatches(input,template,false);
            if(accepted!=(offered==32767||offered==registered))throw new AssertionError("Pinned native ender matcher changed");
            observations.add(com.github.dcysteine.nesql.exporter.source.Json.object("registered",registered,"offered",offered,"tagged",tagged,"count",count,"accepted",accepted));
        }
        java.nio.file.Files.write(java.nio.file.Paths.get("build/native-tests/ae-world-ender-matching.json"),com.github.dcysteine.nesql.exporter.source.CanonicalJson.bytes(observations));
        List<ItemStack> saved=new ArrayList<>(members);List<ItemStack> oldCatalog=codechicken.nei.ItemList.items;
        boolean oldReady=codechicken.nei.ItemList.loadFinished;
        members.clear();members.add(new ItemStack(Items.ender_pearl,1,32767));
        codechicken.nei.ItemList.items=new ArrayList<>(Collections.singletonList(new ItemStack(Items.ender_pearl)));
        codechicken.nei.ItemList.loadFinished=true;
        try(AeWorldRecipes reader=new AeWorldRecipes(world)){
            RecipeRow row=NativeCoreFixesTest.row(known.toArray(new ItemStack[0]));reader.capture(5,row);
            com.google.gson.JsonObject rule=row.inputs.get(1).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject().getAsJsonObject("rule");
            if(!rule.get("kind").getAsString().equals("ae2EnderDust")||rule.get("metadata").getAsInt()!=32767)
                throw new AssertionError("AE2 native reversed ore arguments were replaced by generic ore membership");
            row.record.add("source",com.github.dcysteine.nesql.exporter.source.Json.object("owner","appliedenergistics2","handler",world.getClass().getName(),"key","ae-world-ender-wildcard"));
            row.record.addProperty("category",com.github.dcysteine.nesql.exporter.source.Identity.origin("category",row.record.getAsJsonObject("source")));
            NativeContractFixture.writeOwned("ae-world-ender-notes",row,known);
        }finally{members.clear();members.addAll(saved);codechicken.nei.ItemList.items=oldCatalog;codechicken.nei.ItemList.loadFinished=oldReady;}
    }

    /** Real immutable native definitions; no arbitrary proxy stack source is trusted by the product. */
    private static void facadeFixtures(sun.misc.Unsafe unsafe,Object facade,ItemStack crafted,Field held) throws Exception {
        Object previousApi=held.get(null);
        Object api=unsafe.allocateInstance(Class.forName("appeng.core.Api"));
        Object definitions=unsafe.allocateInstance(Class.forName("appeng.core.ApiDefinitions"));
        Object items=unsafe.allocateInstance(Class.forName("appeng.core.api.definitions.ApiItems"));
        Object parts=unsafe.allocateInstance(Class.forName("appeng.core.api.definitions.ApiParts"));
        Class stateClass=Class.forName("appeng.core.features.ActivityState");Object enabled=Enum.valueOf(stateClass,"Enabled");
        Object facadeDefinition=Class.forName("appeng.core.features.ItemDefinition").getConstructor(Item.class,stateClass).newInstance(facade,enabled);
        Class sourceClass=Class.forName("appeng.core.features.IStackSrc");
        Object anchorSource=Class.forName("appeng.core.features.ItemStackSrc").getConstructor(Item.class,int.class,stateClass).newInstance(Items.iron_ingot,0,enabled);
        Object anchorDefinition=Class.forName("appeng.core.features.DamagedItemDefinition").getConstructor(sourceClass).newInstance(anchorSource);
        set(items,"facade",facadeDefinition);set(parts,"cableAnchor",anchorDefinition);
        set(definitions,"items",items);set(definitions,"parts",parts);set(api,"definitions",definitions);
        unsafe.putObject(unsafe.staticFieldBase(held),unsafe.staticFieldOffset(held),api);
        try {
            TemplateRecipeHandler h=(TemplateRecipeHandler)Class.forName("appeng.integration.modules.NEIHelpers.NEIFacadeRecipeHandler").newInstance();
            TemplateRecipeHandler second=(TemplateRecipeHandler)h.getClass().newInstance();
            TemplateRecipeHandler.CachedRecipe borrowed=h.new CachedRecipe(){@Override public codechicken.nei.PositionedStack getResult(){return null;}};
            h.arecipes.add(borrowed);
            AeFacadeRecipes cursor=new AeFacadeRecipes(h);
            RecipeRow row=NativeCoreFixesTest.row(new ItemStack(Items.iron_ingot),new ItemStack(Blocks.stone),crafted);
            row.record.add("source",com.github.dcysteine.nesql.exporter.source.Json.object("owner","appliedenergistics2","handler",h.getClass().getName(),"key","ae-facade"));
            if(cursor.size()!=1||!cursor.capture(0,row))throw new AssertionError("Native facade list was not captured");
            if(h.arecipes.size()!=1||h.arecipes.get(0)!=borrowed)throw new AssertionError("AE2 facade capture wrote borrowed NEI cache");
            if(!row.inputs.get(2).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject().getAsJsonObject("rule").get("kind").getAsString().equals("facadeTexture"))throw new AssertionError("Facade lost strict absent-NBT admission");
            if(!RegistrationBindings.read(h).same(RegistrationBindings.read(second)))throw new AssertionError("Independent facade registrations lost native source identity");
            NativeContractFixture.writeOwned("ae-facade-semantic",row,Arrays.asList(new ItemStack(Items.iron_ingot),new ItemStack(Blocks.stone),crafted));
            Object config=MagicApi.field(Class.forName("appeng.core.FacadeConfig"),null,"instance");
            net.minecraftforge.common.config.Configuration cfg=(net.minecraftforge.common.config.Configuration)config;
            net.minecraftforge.common.config.ConfigCategory category=cfg.getCategory("minecraft");
            net.minecraftforge.common.config.Property property=category.get("stone");
            boolean changedBefore=cfg.hasChanged();String value=property.getString();
            cursor.finishVerification();
            if(changedBefore!=cfg.hasChanged()||property!=category.get("stone")||!value.equals(property.getString()))throw new AssertionError("Facade export wrote native configuration");
            property.set("false");try{expectFacadeFault("recipe_changed",cursor::finishVerification);}finally{property.set(value);}
            category.put("stone",new net.minecraftforge.common.config.Property("stone",value,net.minecraftforge.common.config.Property.Type.BOOLEAN));
            try{expectFacadeFault("recipe_changed",cursor::finishVerification);}finally{category.put("stone",property);}
            List<ItemStack> examples=(List<ItemStack>)MagicApi.field(facade,"subTypes");ItemStack example=examples.get(0);
            examples.set(0,example.copy());try{expectFacadeFault("recipe_changed",cursor::finishVerification);}finally{examples.set(0,example);}
            set(facade,"subTypes",new ArrayList<>(examples));try{expectFacadeFault("recipe_changed",cursor::verify);}finally{set(facade,"subTypes",examples);}
            set(anchorSource,"damage",1);try{expectFacadeFault("recipe_changed",cursor::verify);}finally{set(anchorSource,"damage",0);}
            set(facade,"subTypes",null);try{expectFacadeFault("recipe_changed",cursor::verify);}finally{set(facade,"subTypes",examples);}
            RegistrationBindings binding=RegistrationBindings.read(h);
            Object replacement=Class.forName("appeng.core.features.DamagedItemDefinition").getConstructor(sourceClass).newInstance(anchorSource);
            set(parts,"cableAnchor",replacement);set(second,"anchorDefinition",replacement);
            try{if(binding.same(RegistrationBindings.read(second)))throw new AssertionError("Same-content native facade definition replacement escaped source binding");expectFacadeFault("recipe_changed",cursor::verify);}
            finally{set(parts,"cableAnchor",anchorDefinition);set(second,"anchorDefinition",anchorDefinition);}
            set(facade,"subTypes",null);try{expectFacadeFault("handler_not_ready",()->new AeFacadeRecipes(h));if(MagicApi.field(facade,"subTypes")!=null)throw new AssertionError("Export initialized borrowed facade cache");}finally{set(facade,"subTypes",examples);}
            category.remove("stone");try{expectFacadeFault("handler_not_ready",()->new AeFacadeRecipes(h));if(category.containsKey("stone"))throw new AssertionError("Export inserted native facade property");}finally{category.put("stone",property);}
            set(facade,"subTypes",new ArrayList<ItemStack>());try(AeFacadeRecipes empty=new AeFacadeRecipes(h)){if(empty.size()!=0)throw new AssertionError("Ready empty facade list was rejected");empty.finishVerification();}finally{set(facade,"subTypes",examples);}
            int[] unknownCalls={0};Object unknown=Proxy.newProxyInstance(sourceClass.getClassLoader(),new Class<?>[]{sourceClass},(p,m,a)->{unknownCalls[0]++;throw new AssertionError("Unknown facade source callback executed");});
            set(anchorDefinition,"source",Optional.of(unknown));try{expectFacadeFault("recipe_unsupported",()->new AeFacadeRecipes(h));if(unknownCalls[0]!=0)throw new AssertionError("Facade source was audited after executing callback");}finally{set(anchorDefinition,"source",Optional.of(anchorSource));}
            cursor.finishVerification();cursor.close();cursor.close();expectFacadeFault("capture_closed",cursor::size);
            if(examples.size()!=1||examples.get(0)!=example||h.arecipes.get(0)!=borrowed)throw new AssertionError("Facade close changed borrowed examples/cache");
            metadataFixtures(facade,h,examples);
            System.out.println("AE2 facade semantic reader: native API identities, ready config/list, source/entry/config drift, empty/missing, unknown callback and close passed");
        } finally {unsafe.putObject(unsafe.staticFieldBase(held),unsafe.staticFieldOffset(held),previousApi);}
    }
    private static void expectFacadeFault(String code,Runnable call){
        try{call.run();throw new AssertionError("Missing facade fault: "+code);}catch(com.github.dcysteine.nesql.exporter.task.Jobs.Fault failure){if(!failure.code.equals(code))throw failure;}
    }
    private static void metadataFixtures(Object facade,TemplateRecipeHandler handler,List<ItemStack> original) throws Exception {
        // Vanilla subtype metadata is preserved; no generic ItemBlock=0 assumption.
        ItemStack wool=new ItemStack(Blocks.wool,1,12);
        if(new AeFacadeMetadata(wool.getItem()).apply(12)!=wool.getItem().getMetadata(12))throw new AssertionError("Native wool metadata changed");
        ItemStack leaves=new ItemStack(Blocks.leaves,1,0);
        if(new AeFacadeMetadata(leaves.getItem()).apply(0)!=leaves.getItem().getMetadata(0))throw new AssertionError("Native leaf placement bit was dropped");
        UnknownMetadata.calls=0;Item unknown=new UnknownMetadata(Blocks.stone);
        expectFacadeFault("recipe_unsupported",()->new AeFacadeMetadata(unknown));
        if(UnknownMetadata.calls!=0)throw new AssertionError("Unknown metadata callback ran before audit");
        dependentMetadataFixtures();
        Class<? extends net.minecraft.item.ItemBlock> enclosed=(Class<? extends net.minecraft.item.ItemBlock>)Class.forName("micdoodle8.mods.galacticraft.core.items.ItemBlockEnclosed");
        net.minecraft.block.Block block=new FacadeBlock();
        int blockId=3590;String blockName="nesqltest:facade_enclosed";
        Object forgeRegistry=cpw.mods.fml.common.registry.GameData.getBlockRegistry();
        java.lang.reflect.Method add=forgeRegistry.getClass().getDeclaredMethod("addObjectRaw",int.class,String.class,Object.class);
        add.setAccessible(true);add.invoke(forgeRegistry,blockId,blockName,block);
        if(net.minecraft.block.Block.blockRegistry!=forgeRegistry)net.minecraft.block.Block.blockRegistry.addObject(blockId,blockName,block);
        Item blockItem=enclosed.getConstructor(net.minecraft.block.Block.class).newInstance(block);
        Object forgeItems=cpw.mods.fml.common.registry.GameData.getItemRegistry();
        java.lang.reflect.Method addItem=forgeItems.getClass().getDeclaredMethod("addObjectRaw",int.class,String.class,Object.class);
        addItem.setAccessible(true);addItem.invoke(forgeItems,blockId,blockName,blockItem);
        if(Item.itemRegistry!=forgeItems)Item.itemRegistry.addObject(blockId,blockName,blockItem);
        ItemStack sourceTexture=new ItemStack(block,1,4);
        ItemStack example=(ItemStack)MagicApi.invoke(facade.getClass(),facade,"createFacadeForItem",new Class<?>[]{ItemStack.class,boolean.class},sourceTexture.copy(),false);
        ItemStack texture=(ItemStack)MagicApi.invoke(facade.getClass(),facade,"getTextureItem",new Class<?>[]{ItemStack.class},example.copy());
        ItemStack expected=(ItemStack)MagicApi.invoke(facade.getClass(),facade,"createFacadeForItem",new Class<?>[]{ItemStack.class,boolean.class},texture.copy(),false);
        expected.stackSize=4;
        if(texture.getItemDamage()!=0||enclosed.getMethod("getMetadata",int.class).invoke(texture.getItem(),0).equals(0))throw new AssertionError("Native non-idempotent facade metadata fixture is not effective");
        set(facade,"subTypes",new ArrayList<>(Collections.singletonList(example)));
        try(AeFacadeRecipes reader=new AeFacadeRecipes(handler)){
            RecipeRow row=NativeCoreFixesTest.row(new ItemStack(Items.iron_ingot),texture,expected);
            row.record.add("source",com.github.dcysteine.nesql.exporter.source.Json.object("owner","appliedenergistics2","handler",handler.getClass().getName(),"key","ae-facade-normalized"));
            row.record.addProperty("category",com.github.dcysteine.nesql.exporter.source.Identity.origin("category",row.record.getAsJsonObject("source")));
            reader.capture(0,row);reader.finishVerification();
            String output=com.github.dcysteine.nesql.exporter.source.Identity.item(Item.itemRegistry.getNameForObject(expected.getItem()),expected.getItemDamage(),com.github.dcysteine.nesql.exporter.source.TypedNbt.encode(expected.getTagCompound()));
            if(!row.outputs.get(0).getAsJsonObject().get("id").getAsString().equals(output)||row.record.getAsJsonObject("process").get("textureMetadata").getAsInt()!=4)throw new AssertionError("Facade normalized output differs from actual native callback");
            NativeContractFixture.writeOwned("ae-facade-normalized",row,Arrays.asList(new ItemStack(Items.iron_ingot),texture,expected));
        }finally{set(facade,"subTypes",original);}
    }
    public static final class FacadeBlock extends net.minecraft.block.Block {
        public FacadeBlock(){super(net.minecraft.block.material.Material.rock);}
    }
    private static void dependentMetadataFixtures() throws Exception {
        net.minecraft.block.Block ordinary=new FacadeBlock();
        Item xu=(Item)Class.forName("com.rwtema.extrautils.item.ItemBlockMetadata").getConstructor(net.minecraft.block.Block.class).newInstance(ordinary);
        AeFacadeMetadata xuRule=new AeFacadeMetadata(xu);
        for(boolean flag:new boolean[]{true,false}){
            set(xu,"hasBlockMetadata",flag);
            if(xuRule.apply(5)!=xu.getMetadata(5))throw new AssertionError("XU metadata flag differs from native callback");
        }
        sun.misc.Unsafe unsafe=(sun.misc.Unsafe)MagicApi.field(sun.misc.Unsafe.class,null,"theUnsafe");
        Class<?> blockType=Class.forName("ganymedes01.etfuturum.blocks.BaseSubtypesBlock");
        int typeId=3580;
        for(String name:new String[]{"BaseItemBlock","BaseSubtypesPotableItemBlock"}){
            net.minecraft.block.Block typesBlock=(net.minecraft.block.Block)unsafe.allocateInstance(blockType);
            String[] types={"first","second","third"};set(typesBlock,"types",types);
            Item item=(Item)Class.forName("ganymedes01.etfuturum.blocks.itemblocks."+name).getConstructor(net.minecraft.block.Block.class).newInstance(typesBlock);
            registerMetadataItem(typesBlock,item,typeId++);
            AeFacadeMetadata rule=new AeFacadeMetadata(item);
            for(int metadata=0;metadata<8;metadata++)if(rule.apply(metadata)!=item.getMetadata(metadata))throw new AssertionError("EtFuturum types modulo differs from native callback");
            set(typesBlock,"types",types.clone());try{expectFacadeFault("recipe_changed",()->rule.apply(1));}finally{set(typesBlock,"types",types);}
        }
        Item shulker=(Item)Class.forName("ganymedes01.etfuturum.blocks.itemblocks.ItemBlockShulkerBox").getConstructor(net.minecraft.block.Block.class).newInstance(ordinary);
        if(new AeFacadeMetadata(shulker).apply(7)!=shulker.getMetadata(7))throw new AssertionError("Shulker native block metadata changed");
        Item chest=(Item)Class.forName("cpw.mods.ironchest.ItemIronChest").getConstructor(net.minecraft.block.Block.class).newInstance(ordinary);
        AeFacadeMetadata chestRule=new AeFacadeMetadata(chest);
        for(int metadata=0;metadata<16;metadata++)if(chestRule.apply(metadata)!=chest.getMetadata(metadata))throw new AssertionError("Chest enum metadata differs from native callback");
        Item ae=NativeCoreFixesTest.item("appeng.block.AEBaseItemBlock","ae_metadata");
        AeFacadeMetadata aeRule=new AeFacadeMetadata(ae);
        for(boolean flag:new boolean[]{true,false}){ae.setHasSubtypes(flag);if(aeRule.apply(3)!=ae.getMetadata(3))throw new AssertionError("AE2 subtype flag differs from native callback");}
    }
    private static void registerMetadataItem(net.minecraft.block.Block block,Item item,int id) throws Exception {
        String name="nesqltest:metadata_"+id;
        for(Object registry:new Object[]{cpw.mods.fml.common.registry.GameData.getBlockRegistry(),cpw.mods.fml.common.registry.GameData.getItemRegistry()}){
            java.lang.reflect.Method add=registry.getClass().getDeclaredMethod("addObjectRaw",int.class,String.class,Object.class);add.setAccessible(true);
            add.invoke(registry,id,name,registry==cpw.mods.fml.common.registry.GameData.getBlockRegistry()?block:item);
        }
        if(net.minecraft.block.Block.blockRegistry!=cpw.mods.fml.common.registry.GameData.getBlockRegistry())net.minecraft.block.Block.blockRegistry.addObject(id,name,block);
        if(Item.itemRegistry!=cpw.mods.fml.common.registry.GameData.getItemRegistry())Item.itemRegistry.addObject(id,name,item);
    }
    public static final class UnknownMetadata extends net.minecraft.item.ItemBlock {
        static int calls;
        public UnknownMetadata(net.minecraft.block.Block block){super(block);}
        @Override public int getMetadata(int metadata){calls++;throw new AssertionError("Unknown metadata callback executed");}
    }

    private static Object definition(ItemStack stack) throws Exception {
        Class<?> sourceType=Class.forName("appeng.core.features.IStackSrc");
        Class enumType=Class.forName("appeng.core.features.ActivityState");
        Object source=Class.forName("appeng.core.features.ItemStackSrc").getConstructor(Item.class,int.class,enumType)
            .newInstance(stack.getItem(),stack.getItemDamage(),Enum.valueOf(enumType,"Enabled"));
        return Class.forName("appeng.core.features.DamagedItemDefinition").getConstructor(sourceType).newInstance(source);
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); field.set(target, value);
    }

    /** Skip unrelated complete mod registry boot; instance() and every tested callback stay native. */
    private static void worldLanguages(ICraftingHandler handler,List<ItemStack> known)throws Exception{
        Field translator=net.minecraft.util.StatCollector.class.getDeclaredField("localizedName");translator.setAccessible(true);
        Object prior=translator.get(null);
        List<String> englishIds=new ArrayList<>();
        try{
            for(String locale:new String[]{"en_US","zh_CN"}){
                net.minecraft.util.StringTranslate owned=new net.minecraft.util.StringTranslate();
                Map<String,String> dictionary=(Map<String,String>)MagicApi.field(owned,"languageList");
                try(java.io.InputStream input=handler.getClass().getResourceAsStream("/assets/appliedenergistics2/lang/"+locale+".lang")){
                    if(input==null)throw new AssertionError("Pinned AE2 language resource is unavailable: "+locale);
                    dictionary.putAll(net.minecraft.util.StringTranslate.parseLangFile(input));
                }
                translator.set(null,owned);
                try(AeWorldRecipes reader=new AeWorldRecipes(handler)){
                    for(int i=0;i<reader.size();i++){
                        RecipeRow row=NativeCoreFixesTest.row(locale,known.toArray(new ItemStack[0]));reader.capture(i,row);
                        row.record.add("source",com.github.dcysteine.nesql.exporter.source.Json.object("owner","appliedenergistics2","handler",handler.getClass().getName(),"key","ae-world-"+i));
                        row.record.addProperty("category",com.github.dcysteine.nesql.exporter.source.Identity.origin("category",row.record.getAsJsonObject("source")));
                        row.finish();String id=row.record.get("id").getAsString();
                        if(locale.equals("en_US"))englishIds.add(id);
                        else if(!englishIds.get(i).equals(id))throw new AssertionError("Native localized AE2 description changed recipe identity at "+i);
                        NativeContractFixture.writeOwned("ae-world-locale-"+i+"-"+locale,row,known);
                    }
                    reader.finishVerification();
                    String key="gui.appliedenergistics2.inWorldFluix",saved=dictionary.get(key);
                    try{
                        dictionary.put(key,"controlled locale drift");
                        try{reader.capture(4,NativeCoreFixesTest.row(locale,known.toArray(new ItemStack[0])));throw new AssertionError("AE2 language changed during one capture without rejection");}
                        catch(com.github.dcysteine.nesql.exporter.task.Jobs.Fault failure){if(!failure.code.equals("recipe_changed"))throw failure;}
                    }finally{if(saved==null)dictionary.remove(key);else dictionary.put(key,saved);}
                    reader.finishVerification();
                }
            }
        }finally{translator.set(null,prior);}
        System.out.println("AE2 native en_US/zh_CN explanations retain all nine stable recipe identities");
    }
    public static final class IsolatedDefinitions implements net.minecraft.launchwrapper.IClassTransformer {
        @Override public byte[] transform(String name,String transformedName,byte[] bytes) {
            if(name.equals("appeng.integration.modules.NEIHelpers.NEIWorldCraftingHandler")&&bytes!=null){
                org.objectweb.asm.tree.ClassNode node=new org.objectweb.asm.tree.ClassNode();
                new org.objectweb.asm.ClassReader(bytes).accept(node,0);
                for(org.objectweb.asm.tree.MethodNode method:node.methods)if(method.name.equals("getRecipeHandler")||method.name.equals("getResultStack")){
                    org.objectweb.asm.tree.InsnList guard=new org.objectweb.asm.tree.InsnList();
                    org.objectweb.asm.tree.LabelNode allowed=new org.objectweb.asm.tree.LabelNode();
                    guard.add(new org.objectweb.asm.tree.FieldInsnNode(org.objectweb.asm.Opcodes.GETSTATIC,
                        NativeAeRemainingTest.class.getName().replace('.','/'),"forbidWorldViews","Z"));
                    guard.add(new org.objectweb.asm.tree.JumpInsnNode(org.objectweb.asm.Opcodes.IFEQ,allowed));
                    guard.add(new org.objectweb.asm.tree.TypeInsnNode(org.objectweb.asm.Opcodes.NEW,"java/lang/AssertionError"));
                    guard.add(new org.objectweb.asm.tree.InsnNode(org.objectweb.asm.Opcodes.DUP));
                    guard.add(new org.objectweb.asm.tree.LdcInsnNode("Semantic AE2 world reader requested a native GUI selection"));
                    guard.add(new org.objectweb.asm.tree.MethodInsnNode(org.objectweb.asm.Opcodes.INVOKESPECIAL,"java/lang/AssertionError","<init>","(Ljava/lang/Object;)V",false));
                    guard.add(new org.objectweb.asm.tree.InsnNode(org.objectweb.asm.Opcodes.ATHROW));guard.add(allowed);
                    method.instructions.insert(guard);
                }
                org.objectweb.asm.ClassWriter writer=new org.objectweb.asm.ClassWriter(org.objectweb.asm.ClassWriter.COMPUTE_MAXS|org.objectweb.asm.ClassWriter.COMPUTE_FRAMES);
                node.accept(writer);return writer.toByteArray();
            }
            if((!name.equals("appeng.api.AEApi")&&!name.equals("appeng.core.Api")&&!name.equals("appeng.items.materials.MaterialType"))||bytes==null)return bytes;
            org.objectweb.asm.tree.ClassNode node=new org.objectweb.asm.tree.ClassNode();
            new org.objectweb.asm.ClassReader(bytes).accept(node,0);
            for(org.objectweb.asm.tree.MethodNode method:node.methods)if(method.name.equals("<clinit>")) {
                method.instructions.clear();method.tryCatchBlocks.clear();
                if(method.localVariables!=null)method.localVariables.clear();
                method.instructions.add(new org.objectweb.asm.tree.InsnNode(org.objectweb.asm.Opcodes.RETURN));
                method.maxLocals=0;method.maxStack=0;
            }
            org.objectweb.asm.ClassWriter writer=new org.objectweb.asm.ClassWriter(0);node.accept(writer);return writer.toByteArray();
        }
    }
}
