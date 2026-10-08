package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.source.Identity;
import com.google.gson.JsonObject;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.inventory.InventoryCrafting;
import java.lang.reflect.*;
import java.util.*;

/** Native CreativeCore matching, cache layout and durable facts, including guarded greedy matching. */
public final class NativeCreativeCoreTest {
    private static final String ROOT = "com.creativemd.creativecore.";
    public static void main(String[] args) throws Exception { NativeGendustryFluidsTest.main(new String[]{"creativecore"}); }
    static void run() throws Exception {
        NativeCoreFixesTest.version("creativecore", "1.5.14-GTNH");
        ItemStack exact = new ItemStack(Items.dye, 11, 32767);
        exact.setTagInfo("typed", new net.minecraft.nbt.NBTTagShort((short)5));
        ItemStack output = new ItemStack(Items.diamond, 3);
        output.setTagInfo("result", new net.minecraft.nbt.NBTTagByte((byte)7));
        Object paper = info("StackInfoItem", new Class<?>[]{Item.class, int.class}, Items.paper, 0);
        Object dye = info("StackInfoItemStack", new Class<?>[]{ItemStack.class, boolean.class, int.class}, exact, true, 1);
        Object bucket = info("StackInfoItem", new Class<?>[]{Item.class, int.class}, Items.water_bucket, 1);
        IRecipe recipe = shaped(2, new Object[]{paper, dye, null, bucket}, output);
        InventoryCrafting inventory = inventory();
        inventory.setInventorySlotContents(0, new ItemStack(Items.paper,1,7));
        inventory.setInventorySlotContents(1, exact.copy()); inventory.setInventorySlotContents(4,new ItemStack(Items.water_bucket));
        require(recipe.matches(inventory,null), "Native shaped recipe rejected wildcard item and exact typed NBT");
        inventory.getStackInSlot(1).setItemDamage(0); require(!recipe.matches(inventory,null), "Native StackInfoItemStack treated metadata 32767 as wildcard");
        inventory.setInventorySlotContents(1,exact.copy());
        inventory.getStackInSlot(1).setTagInfo("typed",new net.minecraft.nbt.NBTTagInt(5));
        require(!recipe.matches(inventory,null), "Native typed NBT did not distinguish short and int");
        Class<?> adapter;
        try { adapter = Class.forName("com.github.dcysteine.nesql.exporter.capture.CreativeCoreRecipes"); }
        catch (ClassNotFoundException missing) { throw new AssertionError("Missing native CreativeCore recipe adapter",missing); }
        TemplateRecipeHandler handler=(TemplateRecipeHandler)Class.forName(ROOT+"api.nei.NEIRecipeInfoHandler").newInstance();
        Method capture=adapter.getDeclaredMethod("capture",IRecipe.class,TemplateRecipeHandler.class,RecipeRow.class); capture.setAccessible(true);
        RecipeRow row=NativeCoreFixesTest.row(new ItemStack(Items.paper),exact,new ItemStack(Items.water_bucket),new ItemStack(Items.bucket),output);
        require((Boolean)capture.invoke(null,recipe,handler,row),"Valid native shaped recipe excluded");
        JsonObject a=choice(row,0),b=choice(row,1),c=choice(row,2);
        require(a.get("amount").getAsString().equals("1") && a.getAsJsonObject("consume").get("kind").getAsString().equals("consume"),"Native zero matcher threshold became a free/catalyst input");
        require(a.getAsJsonObject("rule").get("meta").getAsBoolean() && b.getAsJsonObject("rule").get("kind").getAsString().equals("exact"),"Item wildcard or exact typed predicate changed");
        require(c.getAsJsonArray("returns").size()==1 && c.getAsJsonArray("returns").get(0).getAsJsonObject().get("id").getAsString().equals(Identity.item("minecraft:bucket",0,null)),"Bucket return missing");
        require(row.record.getAsJsonObject("grid").get("mirror").getAsBoolean() && row.record.getAsJsonObject("grid").getAsJsonArray("cells").get(2).isJsonNull(),"Native mirror/grid hole changed");
        require(row.outputs.get(0).getAsJsonObject().get("amount").getAsString().equals("3"),"Output count changed");
        handler.arecipes.get(0).getResult().items[0].stackSize=44;
        require(recipe.getRecipeOutput().stackSize==3 && exact.stackSize==11,"Owned native view mutated source");
        row.finish();
        java.nio.file.Path file=java.nio.file.Files.createTempFile("creativecore-facts-",".json");
        try {
            java.nio.file.Files.write(file,CanonicalJson.bytes(row.record));
            require(new com.google.gson.JsonParser().parse(new String(java.nio.file.Files.readAllBytes(file),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("inputs").size()==3,"Durable facts lost native inputs");
        } finally { java.nio.file.Files.delete(file); }
        Object many=info("StackInfoItem",new Class<?>[]{Item.class,int.class},Items.paper,3);
        rejected(capture,shaped(1,new Object[]{many},output),handler,NativeCoreFixesTest.row(new ItemStack(Items.paper),output),"minimum count and unit consumption conflated");
        Object paper2=info("StackInfoItemStack",new Class<?>[]{ItemStack.class,boolean.class,int.class},new ItemStack(Items.paper,1,2),false,1);
        IRecipe greedy=shapeless(new Object[]{paper,paper2},output);
        inventory=inventory(); inventory.setInventorySlotContents(0,new ItemStack(Items.paper,1,2)); inventory.setInventorySlotContents(1,new ItemStack(Items.paper,1,4));
        require(!greedy.matches(inventory,null),"Native greedy overlap fixture should fail in this offered order");
        inventory.setInventorySlotContents(0,new ItemStack(Items.paper,1,4)); inventory.setInventorySlotContents(1,new ItemStack(Items.paper,1,2));
        require(greedy.matches(inventory,null),"Native greedy overlap fixture should accept reversed offered order");
        rejected(capture,greedy,handler,NativeCoreFixesTest.row(new ItemStack(Items.paper),new ItemStack(Items.paper,1,2),output),"order-dependent overlap became unordered alternatives");
        Object iron=info("StackInfoItem",new Class<?>[]{Item.class,int.class},Items.iron_ingot,1);
        Object gold=info("StackInfoItem",new Class<?>[]{Item.class,int.class},Items.gold_ingot,1);
        IRecipe simple=shapeless(new Object[]{paper,bucket,iron,gold},output);
        RecipeRow simpleRow=NativeCoreFixesTest.row(new ItemStack(Items.paper),new ItemStack(Items.water_bucket),new ItemStack(Items.bucket),new ItemStack(Items.iron_ingot),new ItemStack(Items.gold_ingot),output);
        require((Boolean)capture.invoke(null,simple,handler,simpleRow)&&simpleRow.inputs.size()==4&&simpleRow.record.get("grid").isJsonNull(),"Disjoint shapeless facts lost fourth native ingredient");
        require(handler.arecipes.get(0).getIngredients().size()==3,"Native floor-height cache layout changed");
        net.minecraftforge.oredict.OreDictionary.registerOre("nesqlCreativeInputs",new ItemStack(Items.apple,7,32767));
        net.minecraftforge.oredict.OreDictionary.registerOre("nesqlCreativeInputs",new ItemStack(Items.stick,2,3));
        Object ore=info("StackInfoOre",new Class<?>[]{String.class,int.class},"nesqlCreativeInputs",1);
        IRecipe oreRecipe=shaped(1,new Object[]{ore},output);
        inventory=inventory();inventory.setInventorySlotContents(0,new ItemStack(Items.apple,1,5));
        require(oreRecipe.matches(inventory,null),"Native ore wildcard fixture rejected");
        inventory.setInventorySlotContents(0,new ItemStack(Items.stick,1,4));
        require(!oreRecipe.matches(inventory,null),"Native exact ore metadata fixture accepted wrong metadata");
        RecipeRow oreRow=NativeCoreFixesTest.row(new ItemStack(Items.apple),new ItemStack(Items.stick,1,3),output);
        require((Boolean)capture.invoke(null,oreRecipe,handler,oreRow)
                &&oreRow.inputs.get(0).getAsJsonObject().getAsJsonArray("choices").size()==2,"Native ore alternatives lost");
        Item unsafe=new Item(){@Override public boolean hasContainerItem(ItemStack stack){throw new AssertionError("Unknown container callback executed");}};
        Item.itemRegistry.addObject(31889,"nesqltest:creative_unsafe",unsafe);
        Object unknown=info("StackInfoItem",new Class<?>[]{Item.class,int.class},unsafe,1);
        rejected(capture,shaped(1,new Object[]{unknown},output),handler,NativeCoreFixesTest.row(new ItemStack(unsafe),output),"unknown native companion callback executed");
        net.minecraft.block.material.Material material=new net.minecraft.block.material.Material(net.minecraft.block.material.MapColor.airColor);
        net.minecraft.block.Block block=new net.minecraft.block.Block(material){};
        Item blockItem=new net.minecraft.item.ItemBlock(block);
        net.minecraft.block.Block.blockRegistry.addObject(4090,"nesqltest:creative_block",block);
        Item.itemRegistry.addObject(31890,"nesqltest:creative_block",blockItem);
        for(String kind:new String[]{"StackInfoBlock","StackInfoMaterial"}) {
            Object predicate=kind.equals("StackInfoBlock")?info(kind,new Class<?>[]{net.minecraft.block.Block.class,int.class},block,1)
                    :info(kind,new Class<?>[]{net.minecraft.block.material.Material.class,int.class},material,1);
            IRecipe blockRecipe=shaped(1,new Object[]{predicate},output);
            inventory=inventory();inventory.setInventorySlotContents(0,new ItemStack(blockItem,1,7));
            require(blockRecipe.matches(inventory,null),"Native block/material predicate rejected metadata variant");
            RecipeRow blockRow=NativeCoreFixesTest.row(new ItemStack(blockItem),output);
            require((Boolean)capture.invoke(null,blockRecipe,handler,blockRow)
                    &&blockRow.inputs.get(0).getAsJsonObject().getAsJsonArray("choices").size()==1
                    &&choice(blockRow,0).getAsJsonObject("rule").get("meta").getAsBoolean(),"Native block/material alternatives changed");
        }
        List registry=CraftingManager.getInstance().getRecipeList(); registry.add(recipe);
        try {
            Constructor<?> constructor=adapter.getDeclaredConstructor(TemplateRecipeHandler.class);constructor.setAccessible(true);
            RegistryRecipes cursor=(RegistryRecipes)constructor.newInstance(handler);
            require(cursor.size()==1,"Live CraftingManager interface discovery lost recipe"); cursor.verify();
            output.stackSize=4;
            try { cursor.verify();throw new AssertionError("Registry result drift accepted"); }
            catch(com.github.dcysteine.nesql.exporter.task.Jobs.Fault expected){require(expected.code.equals("environment_changed"),"Wrong registry drift fault");}
        } finally { registry.remove(recipe);output.stackSize=3; }
        System.out.println("Native CreativeCore: item/exact typed predicates, zero threshold unit use, containers, shape/mirror, owned native cache, durable JSON, guarded count/greedy branches, complete registry and drift passed");
    }
    private static Object info(String suffix,Class<?>[] types,Object... args)throws Exception{return Class.forName(ROOT+"common.utils.stack."+suffix).getConstructor(types).newInstance(args);}
    private static IRecipe shaped(int width,Object[] inputs,ItemStack output)throws Exception{
        Class<?> info=Class.forName(ROOT+"common.utils.stack.StackInfo");Object array=Array.newInstance(info,inputs.length);
        for(int i=0;i<inputs.length;i++)Array.set(array,i,inputs[i]);
        return (IRecipe)Class.forName(ROOT+"common.recipe.BetterShapedRecipe").getConstructor(int.class,array.getClass(),ItemStack.class).newInstance(width,array,output);
    }
    private static IRecipe shapeless(Object[] inputs,ItemStack output)throws Exception{return (IRecipe)Class.forName(ROOT+"common.recipe.entry.BetterShapelessRecipe").getConstructor(ArrayList.class,ItemStack.class).newInstance(new ArrayList<>(Arrays.asList(inputs)),output);}
    private static InventoryCrafting inventory(){return new InventoryCrafting(new net.minecraft.inventory.Container(){public boolean canInteractWith(net.minecraft.entity.player.EntityPlayer player){return true;}},3,3);}
    private static JsonObject choice(RecipeRow row,int i){return row.inputs.get(i).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject();}
    private static void rejected(Method capture,IRecipe recipe,TemplateRecipeHandler handler,RecipeRow row,String message)throws Exception{
        try{capture.invoke(null,recipe,handler,row);throw new AssertionError(message);}catch(InvocationTargetException expected){require(expected.getCause() instanceof com.github.dcysteine.nesql.exporter.task.Jobs.Fault,"Wrong guarded failure: "+message);}
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
