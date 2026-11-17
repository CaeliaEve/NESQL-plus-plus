package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.recipe.TemplateRecipeHandler;
import net.minecraft.block.Block;
import net.minecraft.init.Items;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.oredict.OreDictionary;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;

/** Pinned native banner matching and NBT conversion; no client/player lifecycle. */
final class NativeBannerTest {
    static void run() throws Exception {
        TemplateRecipeHandler handler=(TemplateRecipeHandler)type("ganymedes01.etfuturum.compat.nei.BannerPatternHandler").newInstance();
        require(Recipes.adapter(handler)!=null,"Banner patterns have no dedicated adapter");
        NativeCoreFixesTest.version("etfuturum","2.6.2.25-GTNH");
        java.io.File fixtureHome=java.nio.file.Files.createTempDirectory("native-banner-").toFile();
        net.minecraft.launchwrapper.Launch.minecraftHome=fixtureHome;
        java.lang.reflect.Field injectedHome=cpw.mods.fml.relauncher.FMLInjectionData.class.getDeclaredField("minecraftHome");
        injectedHome.setAccessible(true);injectedHome.set(null,fixtureHome);
        java.lang.reflect.Field access=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");access.setAccessible(true);
        sun.misc.Unsafe unsafe=(sun.misc.Unsafe)access.get(null);
        Class<?> definitions=type("ganymedes01.etfuturum.ModBlocks");Object definition=unsafe.allocateInstance(definitions);
        java.lang.reflect.Field nativeBlock=definitions.getDeclaredField("theBlock");nativeBlock.setAccessible(true);
        nativeBlock.set(definition,type("ganymedes01.etfuturum.blocks.BlockBanner").newInstance());
        java.lang.reflect.Field registered=definitions.getDeclaredField("BANNER");
        unsafe.putObject(unsafe.staticFieldBase(registered),unsafe.staticFieldOffset(registered),definition);
        Block block=(Block)invoke(definition.getClass(),definition,"get",new Class<?>[0]);
        Block.blockRegistry.addObject(3500,"nesqltest:native_banner",block);
        Item blockItem=(Item)type("ganymedes01.etfuturum.blocks.itemblocks.ItemBlockBanner").getConstructor(Block.class).newInstance(block);
        Item.itemRegistry.addObject(3500,"nesqltest:native_banner",blockItem);
        Item banner=Item.getItemFromBlock(block);
        for(int color=0;color<16;color++)OreDictionary.registerOre(
            (String)invoke(type("ganymedes01.etfuturum.lib.EnumColor"),
                ((Object[])field(type("ganymedes01.etfuturum.lib.EnumColor"),null,"VALUES"))[color],"getOreName",new Class<?>[0]),
            new ItemStack(Items.dye,1,color));
        IRecipe nativeRecipe=(IRecipe)type("ganymedes01.etfuturum.recipes.crafting.RecipeAddPattern").newInstance();
        InventoryCrafting grid=grid();
        ItemStack input=new ItemStack(banner,7,11);input.setTagCompound(new NBTTagCompound());input.getTagCompound().setString("owner","keep");
        grid.setInventorySlotContents(0,input);grid.setInventorySlotContents(8,new ItemStack(Items.dye,2,1));
        require(nativeRecipe.matches(grid,null),"Native bottom-right mask not admitted");
        ItemStack output=nativeRecipe.getCraftingResult(grid);
        require(output.stackSize==1&&output.getItemDamage()==11&&output.getTagCompound().getString("owner").equals("keep"),
            "Banner must preserve base metadata and unrelated NBT");
        NBTTagCompound pattern=output.getTagCompound().getCompoundTag("BlockEntityTag").getTagList("Patterns",10).getCompoundTagAt(0);
        require(pattern.getString("Pattern").equals("br")&&pattern.getInteger("Color")==14,"Native mask/color changed");
        require(input.stackSize==7&&!input.getTagCompound().hasKey("BlockEntityTag"),"Native callback mutated input");
        NBTTagCompound be=new NBTTagCompound();NBTTagList patterns=new NBTTagList();
        for(int i=0;i<6;i++){NBTTagCompound p=new NBTTagCompound();p.setString("Pattern","br");p.setInteger("Color",i);patterns.appendTag(p);}
        be.setTag("Patterns",patterns);input.getTagCompound().setTag("BlockEntityTag",be);
        require(!nativeRecipe.matches(grid,null),"Six-pattern banner accepted another pattern");
        input.getTagCompound().removeTag("BlockEntityTag");
        grid.setInventorySlotContents(7,new ItemStack(Items.dye,1,4));
        require(!nativeRecipe.matches(grid,null),"Mixed dye mask accepted");grid.setInventorySlotContents(7,null);
        // Special catalysts accept no dye and default to native dye index15.
        grid.setInventorySlotContents(8,new ItemStack(Items.skull,1,4));
        require(nativeRecipe.matches(grid,null),"Native catalyst without dye rejected");
        require(nativeRecipe.getCraftingResult(grid).getTagCompound().getCompoundTag("BlockEntityTag")
            .getTagList("Patterns",10).getCompoundTagAt(0).getInteger("Color")==15,"Absent dye default changed");
        grid.setInventorySlotContents(7,new ItemStack(Items.dye,1,4));
        require(nativeRecipe.matches(grid,null),"Native optional catalyst dye rejected");
        grid.setInventorySlotContents(6,new ItemStack(Items.dye,1,4));
        require(!nativeRecipe.matches(grid,null),"Special catalyst accepted two dye cells");
        grid.setInventorySlotContents(6,null);grid.setInventorySlotContents(7,null);
        for(boolean empty:new boolean[]{false,true}){
            NBTTagList wrongType=new NBTTagList();wrongType.appendTag(new net.minecraft.nbt.NBTTagInt(4));
            if(empty)wrongType.removeTag(0);
            NBTTagCompound malformed=new NBTTagCompound();malformed.setTag("Patterns",wrongType);
            input.getTagCompound().setTag("BlockEntityTag",malformed);
            require(nativeRecipe.matches(grid,null),"Wrong-typed history incorrectly counted towards cap");
            require(nativeRecipe.getCraftingResult(grid).getTagCompound().equals(input.getTagCompound()),
                "Native wrong-typed history should remain unchanged, including an empty typed list");
        }
        input.getTagCompound().removeTag("BlockEntityTag");
        capture(handler,banner);
        System.out.println("Native banners: masks, dye correlation, optional catalyst dye, six-pattern cap, preserved NBT and owned adapter passed");
    }
    private static InventoryCrafting grid(){return new InventoryCrafting(new Container(){public boolean canInteractWith(EntityPlayer p){return false;}},3,3);}
    private static void capture(TemplateRecipeHandler handler,Item banner)throws Exception {
        TemplateRecipeHandler.CachedRecipe sentinel=handler.new CachedRecipe(){
            public codechicken.nei.PositionedStack getResult(){return null;}
            public java.util.List<codechicken.nei.PositionedStack> getIngredients(){return java.util.Collections.emptyList();}
        };
        handler.arecipes.add(sentinel);
        RegistryRecipes adapter=(RegistryRecipes)type("com.github.dcysteine.nesql.exporter.capture.BannerRecipes")
            .getDeclaredConstructor(TemplateRecipeHandler.class).newInstance(handler);
        require(adapter.size()>0,"Native banner inventory empty");
        java.util.List<ItemStack> specimens=new java.util.ArrayList<>();specimens.add(new ItemStack(banner));
        for(int color=0;color<16;color++)specimens.add(new ItemStack(Items.dye,1,color));
        java.util.List<ItemStack> fixtureItems=new java.util.ArrayList<>(specimens);
        for(Object pattern:(Object[])field(type("ganymedes01.etfuturum.tileentities.TileEntityBanner$EnumBannerPattern"),null,"VALUES")){
            ItemStack catalyst=(ItemStack)invoke(pattern.getClass(),pattern,"getCraftingStack",new Class<?>[0]);
            if(catalyst!=null){specimens.add(catalyst);fixtureItems.add(catalyst);}
            String id=(String)invoke(pattern.getClass(),pattern,"getPatternID",new Class<?>[0]);
            for(int color=0;color<16;color++){
                ItemStack sample=new ItemStack(banner);NBTTagCompound root=new NBTTagCompound(),be=new NBTTagCompound(),p=new NBTTagCompound();NBTTagList patterns=new NBTTagList();
                p.setString("Pattern",id);p.setInteger("Color",color);patterns.appendTag(p);be.setTag("Patterns",patterns);root.setTag("BlockEntityTag",be);sample.setTagCompound(root);specimens.add(sample);
            }
        }
        boolean shape=false,catalyst=false,noDye=false;
        for(int i=0;i<adapter.size();i++){
            RecipeRow row=NativeCoreFixesTest.row(specimens.toArray(new ItemStack[0]));adapter.capture(i,row);
            require(handler.arecipes.size()==1&&handler.arecipes.get(0)==sentinel,"Banner semantic capture changed a borrowed native GUI cache");
            com.google.gson.JsonObject p=row.record.getAsJsonObject("process");
            require(p!=null&&p.get("kind").getAsString().equals("bannerPattern"),"Banner recipe lost its native process");
            require(row.outputs.get(0).getAsJsonObject().has("change"),"Banner output lost preserved input NBT");
            String mode=p.get("mode").getAsString();
            java.util.List<ItemStack> rowItems=new java.util.ArrayList<>(fixtureItems);
            String outputId=row.outputs.get(0).getAsJsonObject().get("id").getAsString();
            for(ItemStack sample:specimens)if(com.github.dcysteine.nesql.exporter.source.Identity.item(
                Item.itemRegistry.getNameForObject(sample.getItem()),sample.getItemDamage(),
                com.github.dcysteine.nesql.exporter.source.TypedNbt.encode(sample.getTagCompound())).equals(outputId))rowItems.add(sample);
            if(!shape&&mode.equals("mask")){fixture("native-banner-mask",row,handler,rowItems);shape=true;}
            if(!catalyst&&mode.equals("catalyst")){fixture("native-banner-catalyst",row,handler,rowItems);catalyst=true;}
            if(!noDye&&mode.equals("catalystNoDye")){fixture("native-banner-no-dye",row,handler,rowItems);noDye=true;}
        }
        require(shape&&catalyst&&noDye,"Native banner variants missing");
        Object[] patterns=(Object[])field(type("ganymedes01.etfuturum.tileentities.TileEntityBanner$EnumBannerPattern"),null,"VALUES");
        Object selected=patterns[1];java.lang.reflect.Field mask=selected.getClass().getDeclaredField("craftingLayers");mask.setAccessible(true);
        String[] borrowed=(String[])mask.get(selected);
        try {
            mask.set(selected,borrowed.clone());
            fails("recipe_changed",adapter::finishVerification);
        } finally {mask.set(selected,borrowed);}
        adapter.finishVerification();
        @SuppressWarnings("unchecked") java.util.List<ItemStack> reds=(java.util.List<ItemStack>)((java.util.List<?>)field(OreDictionary.class,null,"idToStack"))
            .get(OreDictionary.getOreID("dyeRed"));ItemStack first=reds.get(0);
        try {reds.set(0,first.copy());fails("recipe_changed",adapter::finishVerification);}
        finally {reds.set(0,first);}
        int originalAmount=first.stackSize;
        try {first.stackSize++;fails("recipe_changed",adapter::finishVerification);}
        finally {first.stackSize=originalAmount;}
        MetadataProbe unknown=new MetadataProbe();ItemStack foreign=new ItemStack(unknown);unknown.calls=0;
        // The producer must reject this owner before any metadata callback, even
        // if a foreign mod has inserted its stack directly into a native ore list.
        java.util.List<ItemStack> rawReds=reds;
        rawReds.add(foreign);
        try {fails("recipe_unsupported",()->new BannerRecipes(handler));require(unknown.calls==0,"Banner reader executed foreign metadata");}
        finally {rawReds.remove(rawReds.size()-1);}
        wildcard(handler,rawReds);
        production(handler,specimens);
        Thread.currentThread().interrupt();
        try {adapter.size();throw new AssertionError("Banner reader ignored cancellation");}
        catch(java.util.concurrent.CancellationException expected){}
        finally {Thread.interrupted();}
        OreDictionary.registerOre("dyeRed",new ItemStack(Items.apple));
        try{adapter.verify();throw new AssertionError("Changed native dye inventory reused snapshot");}
        catch(com.github.dcysteine.nesql.exporter.task.Jobs.Fault expected){require(expected.code.equals("recipe_changed"),"Wrong banner drift fault");}
        adapter.close();adapter.close();fails("capture_closed",adapter::size);
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static void fails(String code,Runnable action){
        try{action.run();throw new AssertionError("Banner accepted invalid boundary: "+code);}
        catch(com.github.dcysteine.nesql.exporter.task.Jobs.Fault failure){require(failure.code.equals(code),"Wrong banner boundary: "+failure.code);}
    }
    private static void wildcard(TemplateRecipeHandler handler,java.util.List<ItemStack> source){
        boolean ready=codechicken.nei.ItemList.loadFinished;
        com.google.common.collect.ListMultimap<Item,ItemStack> table=codechicken.nei.ItemList.itemMap;
        source.add(new ItemStack(Items.dye,8,32767));
        try {
            codechicken.nei.ItemList.itemMap=com.google.common.collect.ArrayListMultimap.create();codechicken.nei.ItemList.loadFinished=false;
            fails("handler_not_ready",()->new BannerRecipes(handler));
            codechicken.nei.ItemList.loadFinished=true;
            ItemStack variant=new ItemStack(Items.dye,4,1);codechicken.nei.ItemList.itemMap.put(Items.dye,variant);
            try(BannerRecipes reader=new BannerRecipes(handler)){
                variant.stackSize++;fails("recipe_changed",reader::finishVerification);variant.stackSize--;
                codechicken.nei.ItemList.loadFinished=false;fails("recipe_changed",reader::finishVerification);codechicken.nei.ItemList.loadFinished=true;
                reader.finishVerification();require(variant.stackSize==4,"Banner changed a borrowed wildcard variant");
            }
        } finally {source.remove(source.size()-1);codechicken.nei.ItemList.itemMap=table;codechicken.nei.ItemList.loadFinished=ready;}
    }
    private static void production(TemplateRecipeHandler handler,java.util.List<ItemStack> specimens)throws Exception {
        java.lang.reflect.Field locale=null;
        for(java.lang.reflect.Field f:net.minecraft.client.resources.I18n.class.getDeclaredFields())if(f.getType()==net.minecraft.client.resources.Locale.class)locale=f;
        require(locale!=null,"Native banner locale root unavailable");locale.setAccessible(true);Object borrowedLocale=locale.get(null);
        java.lang.reflect.Field patterns=type("ganymedes01.etfuturum.tileentities.TileEntityBanner$EnumBannerPattern").getField("VALUES");
        Object original=patterns.get(null);
        java.lang.reflect.Field access=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");access.setAccessible(true);
        sun.misc.Unsafe unsafe=(sun.misc.Unsafe)access.get(null);Object base=unsafe.staticFieldBase(patterns);long offset=unsafe.staticFieldOffset(patterns);
        try {
            locale.set(null,new net.minecraft.client.resources.Locale());
            RegistrationBindings bindings=RegistrationBindings.read(handler);
            require(bindings.same(RegistrationBindings.read((TemplateRecipeHandler)handler.getClass().newInstance())),"Independent banner registration roots differ");
            try(Recipes.Cursor cursor=new Recipes.Handler(handler,0).open(NativeCoreFixesTest.row(specimens.toArray(new ItemStack[0])).facts)) {
                require(field(cursor,"handler")==null,"Banner production retained a GUI handler");cursor.capture(0);cursor.finishVerification();
            }
            try(BannerRecipes reader=new BannerRecipes(handler)) {
                unsafe.putObject(base,offset,((Object[])original).clone());
                require(!bindings.same(RegistrationBindings.read(handler)),"Banner registration lost its pattern root");
                fails("recipe_changed",reader::size);
                unsafe.putObject(base,offset,null);fails("recipe_changed",reader::size);fails("handler_not_ready",()->new BannerRecipes(handler));
            } finally {unsafe.putObject(base,offset,original);}
            unsafe.putObject(base,offset,java.lang.reflect.Array.newInstance(type("ganymedes01.etfuturum.tileentities.TileEntityBanner$EnumBannerPattern"),0));
            try(BannerRecipes reader=new BannerRecipes(handler)){require(reader.size()==0,"Ready-empty native banner source was rejected");reader.finishVerification();}
        } finally {unsafe.putObject(base,offset,original);locale.set(null,borrowedLocale);}
    }
    public static final class MetadataProbe extends Item {
        int calls;
        @Override public int getDamage(ItemStack stack){calls++;throw new AssertionError("Foreign banner metadata callback executed");}
    }
    private static void fixture(String name,RecipeRow row,TemplateRecipeHandler handler,java.util.List<ItemStack> items)throws Exception {
        com.google.gson.JsonObject origin=com.github.dcysteine.nesql.exporter.source.Json.object("owner","etfuturum","handler",handler.getClass().getName(),"key",name);
        row.record.add("source",origin);row.record.addProperty("category",com.github.dcysteine.nesql.exporter.source.Identity.origin("category",origin));
        NativeContractFixture.writeOwnedWithOres(name,row,items);
    }
    /** Replace only the global block registry startup. Pattern/item/crafting callbacks remain native. */
    public static final class Definitions implements net.minecraft.launchwrapper.IClassTransformer {
        public byte[] transform(String name,String transformed,byte[] bytes){
            if(bytes==null||!name.equals("ganymedes01.etfuturum.ModBlocks"))return bytes;
            org.objectweb.asm.tree.ClassNode n=new org.objectweb.asm.tree.ClassNode();new org.objectweb.asm.ClassReader(bytes).accept(n,0);
            for(Object raw:n.methods){org.objectweb.asm.tree.MethodNode m=(org.objectweb.asm.tree.MethodNode)raw;if(m.name.equals("<clinit>")){
                m.instructions.clear();m.tryCatchBlocks.clear();if(m.localVariables!=null)m.localVariables.clear();
                m.instructions.add(new org.objectweb.asm.tree.InsnNode(org.objectweb.asm.Opcodes.RETURN));m.maxStack=0;m.maxLocals=0;
            }}org.objectweb.asm.ClassWriter w=new org.objectweb.asm.ClassWriter(0);n.accept(w);return w.toByteArray();
        }
    }
}
