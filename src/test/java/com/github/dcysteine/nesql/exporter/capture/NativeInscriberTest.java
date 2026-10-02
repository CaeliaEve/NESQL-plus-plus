package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.source.Identity;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.google.gson.JsonObject;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Pinned registry/cache getters and Platform comparator on owned objects, without an AE network. */
final class NativeInscriberTest {
    @SuppressWarnings({"unchecked", "rawtypes"})
    static void run() throws Exception {
        String root="appeng.";
        Class<?> handlerType=type(root+"integration.modules.NEIHelpers.NEIInscriberRecipeHandler");
        require(handlerType.getProtectionDomain().getCodeSource().getLocation().toString().contains("/native-tests/ae2.jar!"),"Pinned AE jar required");
        TemplateRecipeHandler handler=(TemplateRecipeHandler)handlerType.newInstance();
        require(Recipes.adapter(handler)!=null,"Missing inscriber adapter");
        ItemStack center=new ItemStack(Items.iron_ingot,17), top=new ItemStack(Items.paper,9), bottom=new ItemStack(Items.gold_ingot), output=new ItemStack(Items.diamond,3);
        ItemStack empty=center.copy(); empty.setTagCompound(new NBTTagCompound());
        require(same(center,empty),"Native null/empty NBT equivalence changed");
        empty.setTagInfo("owner",new NBTTagString("other"));
        require(!same(center,empty),"Native precise matcher ignored NBT");
        ItemStack wildcard=center.copy();wildcard.setItemDamage(32767);
        require(!same(center,wildcard),"AE precise metadata is literal, not a wildcard");
        NBTTagList typed=new NBTTagList();typed.appendTag(new NBTTagString("x"));typed.removeTag(0);
        ItemStack a=center.copy(), b=center.copy();a.setTagInfo("list",typed);b.setTagInfo("list",new NBTTagList());
        require(same(a,b),"AE empty-list element type must be ignored");
        a.setTagInfo("number",new NBTTagFloat(-0f));b.setTagInfo("number",new NBTTagFloat(0f));
        require(same(a,b),"AE float signed zero must compare equal");
        Class<?> recipeType=type(root+"core.features.registries.entries.InscriberRecipe"), modeType=type(root+"api.features.InscriberProcessType");
        List<Object> nativeRecipes=new ArrayList<>();
        for(String mode:new String[]{"Inscribe","Press"}) nativeRecipes.add(recipeType.getConstructor(Collection.class,ItemStack.class,ItemStack.class,ItemStack.class,modeType)
                .newInstance(Arrays.asList(center,empty),output,top,bottom,Enum.valueOf((Class)modeType,mode)));
        nativeRecipes.add(recipeType.getConstructor(Collection.class,ItemStack.class,ItemStack.class,ItemStack.class,modeType)
                .newInstance(Collections.singletonList(center),output,null,bottom,Enum.valueOf((Class)modeType,"Press")));
        InscriberRecipes cursor=new InscriberRecipes(handler,nativeRecipes,new ItemStack(Items.name_tag));
        require(cursor.size()==3,"Native registry order lost");
        for(int index=0;index<cursor.size();index++) {
            Facts facts=new Facts("en_US");
            for(ItemStack item:new ItemStack[]{center,empty,top,bottom,output,new ItemStack(Items.name_tag)})
                ((Set<String>)field(facts,"items")).add(Identity.item(Item.itemRegistry.getNameForObject(item.getItem()),item.getItemDamage(),TypedNbt.encode(item.getTagCompound())));
            RecipeRow row=new RecipeRow(facts,object("owner","fixture","handler","inscriber","key","inscriber"),"fixture",index);
            require(cursor.capture(index,row),"Native row disappeared");
            JsonObject process=row.record.getAsJsonObject("process");
            require(process.get("mode").getAsString().equals(index==0?"inscribe":"press"),"Process mode changed");
            require(row.inputs.size()==(index==2?1:3),"Absent top incorrectly requires the declared bottom");
            require(row.outputs.get(0).getAsJsonObject().get("amount").getAsString().equals("3"),"Output count changed");
            require(row.record.get("duration").isJsonNull()&&row.record.get("energy").isJsonNull(),"Invented fixed tick/EU cost");
            for(com.google.gson.JsonElement value:row.inputs) {
                JsonObject in=value.getAsJsonObject(), choice=in.getAsJsonArray("choices").get(0).getAsJsonObject();
                require(choice.get("amount").getAsString().equals("1")&&choice.getAsJsonObject("rule").get("kind").getAsString().equals("ae"),"Native single-stack precise matching lost");
                require(choice.getAsJsonObject("consume").get("kind").getAsString().equals(index==0&&in.get("slot").getAsInt()!=2?"keep":"consume"),"Press/inscribe plate consumption lost");
                require(choice.getAsJsonArray("returns").size()==0,"Machine gained crafting container returns");
            }
        }
        require(center.stackSize==17&&top.stackSize==9&&output.stackSize==3,"Source-owned stacks were mutated");
        Object named=recipeType.getConstructor(Collection.class,ItemStack.class,ItemStack.class,ItemStack.class,modeType)
                .newInstance(Collections.singletonList(center),output,new ItemStack(Items.name_tag),null,Enum.valueOf((Class)modeType,"Press"));
        require(!new InscriberRecipes(handler,Collections.singletonList(named),new ItemStack(Items.name_tag)).capture(0,
                new RecipeRow(new Facts("en_US"),object("owner","fixture","handler","inscriber","key","inscriber"),"fixture",0)),"Name branch did not preempt registered recipe");
        center.setTagInfo("float",new NBTTagFloat(Float.NaN));
        try { new InscriberRecipes(handler,nativeRecipes,null); throw new AssertionError("NaN predicate accepted"); }
        catch(com.github.dcysteine.nesql.exporter.task.Jobs.Fault error) {require(error.code.equals("recipe_unsupported"),"Wrong NaN rejection");}
        center.setTagCompound(null);
        try { new InscriberRecipes(handler,Collections.singletonList(new Object()),null); throw new AssertionError("Unknown getters accepted"); }
        catch(com.github.dcysteine.nesql.exporter.task.Jobs.Fault error) {require(error.code.equals("recipe_unsupported"),"Wrong unknown recipe rejection");}
        System.out.println("Inscriber native registry/cache and comparator: modes, plate omission, exact metadata, null/empty NBT, empty lists, signed zero, count and ownership passed");
    }
    private static boolean same(ItemStack a,ItemStack b) {return (Boolean)invoke(type("appeng.util.Platform"),null,"isSameItemPrecise",new Class<?>[]{ItemStack.class,ItemStack.class},a,b);}
    private static void require(boolean value,String message) {if(!value)throw new AssertionError(message);}
}
