package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;
import java.lang.reflect.*;
import java.util.*;

/** Pinned native matcher, actual registration entry, overlap and borrowed storage ownership. */
public final class NativeSpinningTest {
  public static void run() throws Exception {
    try(NativeRegistryScope scope=new NativeRegistryScope()){
      NativeCoreFixesTest.version("WitchingGadgets","1.7.25-GTNH");
      Class<?> type=Class.forName("witchinggadgets.common.util.recipe.SpinningRecipe");
      Constructor<?> make=type.getConstructor(ItemStack.class,Object[].class);
      Method match=type.getMethod("inputsMatch",ItemStack[].class);
      ItemStack paper=new ItemStack(Items.paper,19),dye=new ItemStack(Items.dye,7,2),out=new ItemStack(Items.string,3);
      Object recipe=make.newInstance(out,new Object[]{paper,dye});
      ItemStack tagged=dye.copy();tagged.setTagInfo("ignored",new net.minecraft.nbt.NBTTagInt(1));
      require((Boolean)match.invoke(recipe,(Object)new ItemStack[]{tagged,paper}),"Native swap/NBT predicate changed");
      require(!(Boolean)match.invoke(recipe,(Object)new ItemStack[]{paper,new ItemStack(Items.dye,1,3)}),"Native metadata predicate changed");
      TemplateRecipeHandler handler=(TemplateRecipeHandler)Class.forName("witchinggadgets.client.nei.NEISpinningWheelHandler").newInstance();
      handler.arecipes.add(null);
      ArrayList<Object> source=new ArrayList<>(Collections.singletonList(recipe));scope.replace(type,null,"recipeList",source);
      try(SpinningRecipes cursor=new SpinningRecipes(handler)){
        RecipeRow row=row(new ItemStack(Items.paper),new ItemStack(Items.dye,1,2),out);
        require(cursor.capture(0,row)&&row.inputs.size()==2&&row.outputs.size()==1,"Spinning native slots lost");
        require(row.inputs.get(0).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject().get("amount").getAsString().equals("1"),"Template count became consumption");
        require(row.record.get("duration").isJsonNull()&&row.record.getAsJsonObject("process").get("kind").getAsString().equals("witchingSpinning"),"Native state rules were reduced to fixed elapsed time");
        cursor.finishVerification();
        require(handler.arecipes.size()==1&&handler.arecipes.get(0)==null&&paper.stackSize==19&&dye.stackSize==7,"Reader modified borrowed GUI or inputs");
        NativeContractFixture.writeOwned("witching-spinning-native",row,Arrays.asList(new ItemStack(Items.paper),new ItemStack(Items.dye,1,2),out));
      }
      OreDictionary.registerOre("nesqlSpinningFiber",new ItemStack(Items.paper));
      OreDictionary.registerOre("nesqlSpinningFiber",new ItemStack(Items.leather));
      Object overlap=make.newInstance(out,new Object[]{"nesqlSpinningFiber","nesqlSpinningFiber"});
      require((Boolean)match.invoke(overlap,(Object)new ItemStack[]{paper,new ItemStack(Items.diamond)}),"Pinned list-removal/sticky-match counterexample changed");
      source.clear();source.add(overlap);
      try(SpinningRecipes cursor=new SpinningRecipes(handler)){
        RecipeRow row=row(new ItemStack(Items.paper),new ItemStack(Items.leather),out);
        require(cursor.capture(0,row)&&row.inputs.get(0).getAsJsonObject().getAsJsonArray("choices").size()==2,"Overlapping native lists remain unadapted");
        NativeContractFixture.writeOwned("witching-spinning-overlap",row,Arrays.asList(new ItemStack(Items.paper),new ItemStack(Items.leather),out));
        out.stackSize=4;
        try{cursor.finishVerification();throw new AssertionError("Native source drift was accepted");}
        catch(Jobs.Fault expected){require(expected.code.equals("environment_changed"),"Wrong drift failure");}
      }
      int[] calls={0};ArrayList<ItemStack> foreign=new ArrayList<ItemStack>(){public Iterator<ItemStack> iterator(){calls[0]++;throw new AssertionError("Foreign iterator ran");}};
      Object invalid=make.newInstance(out,new Object[]{paper});scope.replace(type,invalid,"input",new Object[]{foreign});source.clear();source.add(invalid);
      try{new SpinningRecipes(handler);throw new AssertionError("Foreign ingredient storage was admitted");}
      catch(Jobs.Fault expected){require(expected.code.equals("recipe_unsupported"),"Wrong callback admission failure");}
      require(calls[0]==0,"Unknown collection callback ran before admission");
      source.clear();SpinningRecipes empty=new SpinningRecipes(handler);require(empty.size()==0,"Ready-empty registry changed meaning");empty.finishVerification();empty.close();empty.close();
      try{empty.size();throw new AssertionError("Closed reader remains usable");}catch(Jobs.Fault expected){require(expected.code.equals("capture_closed"),"Wrong closed failure");}
    }
  }
  private static RecipeRow row(ItemStack... items){
    RecipeRow row=NativeCoreFixesTest.row(items);
    com.google.gson.JsonObject origin=com.github.dcysteine.nesql.exporter.source.Json.object("owner","WitchingGadgets","handler","witchinggadgets.client.nei.NEISpinningWheelHandler","key","spinning-native");
    row.record.add("source",origin);row.record.addProperty("category",com.github.dcysteine.nesql.exporter.source.Identity.origin("category",origin));return row;
  }
  private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
