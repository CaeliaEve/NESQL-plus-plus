package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.source.Identity;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.google.gson.JsonObject;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentData;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;
import java.lang.reflect.*;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Owned EnderIO registry and native recipe/cache methods; no world, player or GL. */
final class NativeEnchanterTest {
    private static final String ROOT="crazypants.enderio.machine.";
    @SuppressWarnings("unchecked")
    static void run() throws Exception {
        Class<?> inputType=type(ROOT+"recipe.RecipeInput"), recipeType=type(ROOT+"enchanter.EnchanterRecipe");
        require(recipeType.getProtectionDomain().getCodeSource().getLocation().toString().contains("/native-tests/enderio.jar!"),"Pinned EnderIO jar required");
        TemplateRecipeHandler handler=(TemplateRecipeHandler)type("crazypants.enderio.nei.EnchanterRecipeHandler").newInstance();
        require(Recipes.adapter(handler)!=null,"Missing EnderIO enchanter adapter");
        ItemStack material=new ItemStack(Items.dye,3,4);
        Object input=inputType.getConstructor(ItemStack.class,boolean.class).newInstance(material,true);
        Object recipe=recipeType.getConstructor(inputType,Enchantment.class,int.class).newInstance(input,Enchantment.sharpness,2);
        ItemStack tagged=material.copy();tagged.setTagInfo("owner",new net.minecraft.nbt.NBTTagString("arbitrary"));
        require((Boolean)invoke(inputType,input,"isInput",new Class<?>[]{ItemStack.class},tagged),"Native input must ignore NBT");
        Object wildcard=inputType.getConstructor(ItemStack.class,boolean.class).newInstance(new ItemStack(Items.dye,3,32767),true);
        require(!(Boolean)invoke(inputType,wildcard,"isInput",new Class<?>[]{ItemStack.class},material),"Native literal 32767 changed");
        for(int count:new int[]{2,3,5,6,14,15,64}) {
            int expected=Math.min(count/3,5);
            require((Integer)invoke(recipeType,recipe,"getLevelForStackSize",new Class<?>[]{int.class},count)==expected,"Native level boundary changed");
        }
        Class<?> tile=type(ROOT+"enchanter.TileEnchanter"),config=type("crazypants.enderio.config.Config");
        Field base=config.getField("enchanterBaseLevelCost");int oldBase=base.getInt(null);
        try {
            base.setInt(null,7);
            for(int level=1;level<=5;level++)require((Integer)invoke(tile,null,"getEnchantmentCost",new Class<?>[]{recipeType,int.class},recipe,level)==7+2*level*level,"Native quadratic XP differs");
        }finally{base.setInt(null,oldBase);}
        Class<?> adapter=type("com.github.dcysteine.nesql.exporter.capture.EnderEnchanterRecipes");
        Constructor<?> constructor=adapter.getDeclaredConstructor(TemplateRecipeHandler.class,List.class,int.class);constructor.setAccessible(true);
        RegistryRecipes cursor=(RegistryRecipes)constructor.newInstance(handler,Arrays.asList(recipe),7);
        require(cursor.size()==5,"Each native enchantment level needs its own row");
        for(int index=0;index<5;index++) {
            Facts facts=facts(material,new ItemStack(Items.writable_book),book(index+1));
            RecipeRow row=new RecipeRow(facts,object("owner","fixture","handler","enchanter","key","enchanter"),"fixture",index);
            require(cursor.capture(index,row),"Reachable level omitted");
            JsonObject process=row.record.getAsJsonObject("process");
            require(process.get("level").getAsInt()==index+1&&process.get("cost").getAsInt()==7+2*(index+1)*(index+1),"Level or quadratic XP cost lost");
            require(row.inputs.get(1).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject().get("amount").getAsInt()==3*(index+1),"Material count changed");
            require(row.outputs.get(0).getAsJsonObject().get("id").getAsString().equals(id(book(index+1))),"Native enchanted-book NBT differs");
        }
        require(material.stackSize==3&&((ItemStack)invoke(inputType,input,"getInput",new Class<?>[0])).stackSize==3,"Capture mutated registry input");
        // First matching recipe wins BEFORE quantity checks, even if it requires more items.
        Object firstInput=inputType.getConstructor(ItemStack.class,boolean.class).newInstance(new ItemStack(Items.dye,10,4),true);
        Object first=recipeType.getConstructor(inputType,Enchantment.class,int.class).newInstance(firstInput,Enchantment.sharpness,1);
        Object manager=type(ROOT+"enchanter.EnchanterRecipeManager").newInstance();
        ((List<Object>)invoke(manager.getClass(),manager,"getRecipes",new Class<?>[0])).addAll(Arrays.asList(first,recipe));
        require(invoke(manager.getClass(),manager,"getEnchantmentRecipeForInput",new Class<?>[]{ItemStack.class},material)==first,"Native first-match precedence changed");
        cursor=(RegistryRecipes)constructor.newInstance(handler,Arrays.asList(first,recipe),0);
        require(!cursor.capture(5,new RecipeRow(facts(material),object(),"fixture",5)),"Earlier input match must shadow later recipe regardless of quantity");
        // Exact meta before broad input leaves other metadata reachable, with an explicit exclusion.
        Object broad=inputType.getConstructor(ItemStack.class,boolean.class).newInstance(material,false);
        Object later=recipeType.getConstructor(inputType,Enchantment.class,int.class).newInstance(broad,Enchantment.sharpness,2);
        cursor=(RegistryRecipes)constructor.newInstance(handler,Arrays.asList(first,later),0);
        RecipeRow row=new RecipeRow(facts(material,new ItemStack(Items.dye,1,0),new ItemStack(Items.writable_book),book(1)),object(),"fixture",5);
        require(cursor.capture(5,row),"Partially shadowed predicate dropped");
        require(row.inputs.get(1).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject().getAsJsonObject("rule").get("kind").getAsString().equals("except"),"Priority filter omitted");
        // Ore matching ignores template metadata and uses actual native membership.
        OreDictionary.registerOre("nesqlEnchanterFixture",new ItemStack(Items.gold_ingot));
        int ore=OreDictionary.getOreID("nesqlEnchanterFixture");
        Object oreInput=type(ROOT+"recipe.OreDictionaryRecipeInput").getConstructor(ItemStack.class,int.class,int.class).newInstance(new ItemStack(Items.gold_ingot,2),ore,-1);
        require((Boolean)invoke(oreInput.getClass(),oreInput,"isInput",new Class<?>[]{ItemStack.class},new ItemStack(Items.gold_ingot)),"Native ore fixture broken");
        Object oreRecipe=recipeType.getConstructor(inputType,Enchantment.class,int.class).newInstance(oreInput,Enchantment.sharpness,0);
        cursor=(RegistryRecipes)constructor.newInstance(handler,Arrays.asList(oreRecipe),0);
        row=new RecipeRow(facts(new ItemStack(Items.gold_ingot),new ItemStack(Items.writable_book),book(1)),object(),"fixture",0);
        require(cursor.capture(0,row)&&row.record.getAsJsonObject("process").get("cost").getAsInt()==0,"Ore/free recipe lost");
        Object zero=inputType.getConstructor(ItemStack.class,boolean.class).newInstance(new ItemStack(Items.dye,0,4),true);
        Object invalid=recipeType.getConstructor(inputType,Enchantment.class,int.class).newInstance(zero,Enchantment.sharpness,0);
        try {constructor.newInstance(handler,Arrays.asList(invalid),0);throw new AssertionError("Native divide-by-zero input accepted");}
        catch(InvocationTargetException expected) {require(expected.getCause() instanceof com.github.dcysteine.nesql.exporter.task.Jobs.Fault,"Wrong invalid recipe error");}
        Field clock=TemplateRecipeHandler.class.getDeclaredField("cycleticks");clock.setAccessible(true);int oldClock=clock.getInt(handler);
        try {
            EnderEnchanterRecipes.scene(handler,3,()->{
                try{require(clock.getInt(handler)==40,"Native view level clock differs");}catch(IllegalAccessException e){throw new AssertionError(e);}
                throw new IllegalStateException("draw interrupted");
            });
            throw new AssertionError("Draw error swallowed");
        }catch(IllegalStateException expected){require(clock.getInt(handler)==oldClock,"Clock leaked after draw error");}
        System.out.println("EnderIO enchanter: level boundaries, XP, NBT, metadata, precedence, ore and ownership passed");
    }
    private static ItemStack book(int level) {ItemStack book=new ItemStack(Items.enchanted_book);Items.enchanted_book.addEnchantment(book,new EnchantmentData(Enchantment.sharpness,level));return book;}
    private static String id(ItemStack item) {return Identity.item(Item.itemRegistry.getNameForObject(item.getItem()),item.getItemDamage(),TypedNbt.encode(item.getTagCompound()));}
    @SuppressWarnings("unchecked") private static Facts facts(ItemStack... items) {Facts facts=new Facts("en_US");for(ItemStack item:items)((Set<String>)field(facts,"items")).add(id(item));return facts;}
    private static void require(boolean value,String message) {if(!value)throw new AssertionError(message);}
}
