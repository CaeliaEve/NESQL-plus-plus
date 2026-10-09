package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.TemplateRecipeHandler;
import net.minecraft.item.ItemStack;
import net.minecraft.init.Items;
import java.util.*;

/** Native view contract for Chisel's selectable targets. */
public final class NativeChiselTest {
    public static void run() throws Exception {
        TemplateRecipeHandler handler=(TemplateRecipeHandler)Class.forName("team.chisel.compat.nei.RecipeHandlerChisel").newInstance();
        require(Recipes.adapter(handler)!=null,"Chisel dispatch missing");
        ItemStack sourceStack=new ItemStack(Items.paper,1,0), targetStackA=new ItemStack(Items.book,1,0), targetStackB=new ItemStack(Items.stick,1,0);
        PositionedStack source=new PositionedStack(sourceStack,74,4,false);
        PositionedStack targetA=new PositionedStack(targetStackA,3,37,false);
        PositionedStack targetB=new PositionedStack(targetStackB,21,37,false);
        ChiselRecipes.Page page=new ChiselRecipes.Page(source,Arrays.asList(targetA,targetB));
        ChiselRecipes adapter=new ChiselRecipes(handler,Collections.singletonList(page));
        RecipeRow row=NativeCoreFixesTest.row(sourceStack,targetStackA,targetStackB);
        List<RecipeRow> rows=adapter.captureRows(0,row);
        require(rows.size()==2,"Chisel targets were merged");
        require(rows.get(0).outputs.size()==1&&rows.get(1).outputs.size()==1,"Chisel target became simultaneous output");
        require(rows.get(0).properties.has("chisel:toolRequired"),"Chisel tool requirement missing");
        require(rows.get(0).properties.getAsJsonObject("chisel:toolWear").getAsJsonObject("value").get("value").getAsInt()==1,"Chisel wear contract missing");
        require(rows.get(0).properties.getAsJsonObject("chisel:targetTag").getAsJsonObject("value").has("text"),"Chisel target NBT contract missing");
        System.out.println("Native Chisel: alternative targets, single-output rows, tool requirement, wear and target NBT passed");
    }
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
