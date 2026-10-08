package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.google.gson.JsonObject;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.inventory.InventoryCrafting;
import java.lang.reflect.*;
import java.util.*;

/** Native greedy early-failure boundaries and exact safe shapeless orb conversion. */
public final class NativeBloodOrbShapelessTest {
    private static final String RECIPE="WayofTime.alchemicalWizardry.api.items.ShapelessBloodOrbRecipe";
    public static void main(String[] args)throws Exception{NativeGendustryFluidsTest.main(new String[]{"blood-orb-shapeless"});}
    static void run()throws Exception{
        NativeCoreFixesTest.version("AWWayofTime","1.7.52");
        Item low=NativeCoreFixesTest.item("WayofTime.alchemicalWizardry.common.items.EnergyBattery","shapeless_orb_low");
        Item high=NativeCoreFixesTest.item("WayofTime.alchemicalWizardry.common.items.ApprenticeBloodOrb","shapeless_orb_high");
        Field level=Class.forName("WayofTime.alchemicalWizardry.common.items.EnergyBattery").getDeclaredField("orbLevel");level.setAccessible(true);level.setInt(low,1);level.setInt(high,5);
        ItemStack orb=new ItemStack(high,13,9);orb.setTagInfo("ownerName",new net.minecraft.nbt.NBTTagString("retained"));
        require(high.getContainerItem(orb)==orb,"Native orb must return the exact offered stack");
        ItemStack paper=new ItemStack(Items.paper,17,2);paper.setTagInfo("ignored",new net.minecraft.nbt.NBTTagByte((byte)3));
        ItemStack output=new ItemStack(Items.diamond,3);output.setTagInfo("typed",new net.minecraft.nbt.NBTTagShort((short)9));
        IRecipe safe=recipe(output,paper,Items.water_bucket,high);
        ItemStack[] offered={new ItemStack(Items.paper,1,2),new ItemStack(Items.water_bucket),orb};
        for(int a=0;a<3;a++)for(int b=0;b<3;b++)if(a!=b){int c=3-a-b;require(safe.matches(grid(offered[a],offered[b],offered[c]),null),"Native safe suffix rejected inventory permutation");}
        require(!safe.matches(grid(offered[0],offered[1],new ItemStack(low)),null),"Native insufficient orb tier accepted");
        ItemStack crafted=safe.getCraftingResult(grid(offered));
        require(crafted.stackSize==3&&crafted.getTagCompound().getShort("typed")==9,"Native copied output NBT/count changed");
        crafted.stackSize=41;require(safe.getRecipeOutput().stackSize==3,"Native crafting result aliases its template");
        IRecipe early=recipe(output,high,Items.paper);
        require(!early.matches(grid(new ItemStack(Items.paper),orb),null)&&early.matches(grid(orb,new ItemStack(Items.paper)),null),"Native integer early-return counterexample changed");
        net.minecraftforge.oredict.OreDictionary.registerOre("nesqltestShapelessOrbOverlap",new ItemStack(Items.paper,1,32767));
        IRecipe overlap=recipe(output,"nesqltestShapelessOrbOverlap",new ItemStack(Items.paper,1,2),high);
        require(!overlap.matches(grid(new ItemStack(Items.paper,1,2),new ItemStack(Items.paper,1,3),orb),null)
                &&overlap.matches(grid(new ItemStack(Items.paper,1,3),new ItemStack(Items.paper,1,2),orb),null),"Native greedy overlap counterexample changed");
        IRecipe duplicate=recipe(output,Items.paper,high,high);
        require(duplicate.matches(grid(orb,new ItemStack(Items.paper),orb.copy()),null)&&!duplicate.matches(grid(orb,new ItemStack(Items.paper)),null),"Native equal orb predicates lost distinct-slot cardinality");
        IRecipe empty=recipe(output,"nesqltestShapelessOrbEmpty",high);
        require(!empty.matches(grid(orb),null),"Native empty ore predicate unexpectedly matches");
        Class<?> adapter;
        try{adapter=Class.forName("com.github.dcysteine.nesql.exporter.capture.BloodOrbShapelessRecipes");}
        catch(ClassNotFoundException missing){throw new AssertionError("Missing native safe shapeless blood orb adapter",missing);}
        TemplateRecipeHandler handler=(TemplateRecipeHandler)Class.forName("WayofTime.alchemicalWizardry.client.nei.NEIBloodOrbShapelessHandler").newInstance();
        Constructor<?> constructor=adapter.getDeclaredConstructor(TemplateRecipeHandler.class);constructor.setAccessible(true);
        List registry=net.minecraft.item.crafting.CraftingManager.getInstance().getRecipeList();
        registry.add(safe);
        try{
            RegistryRecipes cursor=(RegistryRecipes)constructor.newInstance(handler);require(cursor.size()==1,"Native shapeless discovery lost recipe");cursor.verify();
            RecipeRow row=NativeCoreFixesTest.row(new ItemStack(high),new ItemStack(low),new ItemStack(Items.paper,1,2),new ItemStack(Items.water_bucket),new ItemStack(Items.bucket),output);
            require(cursor.capture(0,row)&&row.inputs.size()==3&&row.outputs.size()==1&&row.record.get("grid").isJsonNull(),"Safe recipe gained a fixed grid or lost ingredients");
            JsonObject orbChoice=row.inputs.get(2).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject();
            require(row.inputs.get(2).getAsJsonObject().getAsJsonArray("choices").size()==1&&orbChoice.getAsJsonObject("consume").get("kind").getAsString().equals("keep")
                    &&orbChoice.getAsJsonObject("rule").get("meta").getAsBoolean()&&orbChoice.getAsJsonObject("rule").get("nbt").getAsBoolean(),"Shapeless orb tier/owner/metadata preservation changed");
            require(row.inputs.get(1).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject().getAsJsonArray("returns").size()==1,"Native water bucket return disappeared");
            require(row.outputs.get(0).getAsJsonObject().get("amount").getAsString().equals("3")&&handler.arecipes.get(0).getIngredients().size()==3,"Native output quantity or view changed");
            require(handler.arecipes.get(0).getIngredients().get(2).relx==25&&handler.arecipes.get(0).getIngredients().get(2).rely==24,"Native shapeless two-column stack order changed");
            handler.arecipes.get(0).getResult().items[0].stackSize=91;
            require(safe.getRecipeOutput().stackSize==3&&paper.stackSize==17&&orb.getTagCompound().getString("ownerName").equals("retained"),"Native cached projection mutated registry/offered stack");
            row.finish();java.nio.file.Path file=java.nio.file.Files.createTempFile("blood-orb-shapeless-",".json");
            try{java.nio.file.Files.write(file,CanonicalJson.bytes(row.record));require(new com.google.gson.JsonParser().parse(new String(java.nio.file.Files.readAllBytes(file),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("inputs").size()==3,"Durable shapeless facts lost inputs");}
            finally{java.nio.file.Files.delete(file);}
            level.setInt(high,6);try{cursor.verify();throw new AssertionError("Orb level drift accepted");}catch(com.github.dcysteine.nesql.exporter.task.Jobs.Fault expected){require(expected.code.equals("environment_changed"),"Wrong orb drift failure");}finally{level.setInt(high,5);}
        }finally{registry.remove(safe);}
        for(IRecipe rejected:new IRecipe[]{early,overlap}){
            registry.add(rejected);try{constructor.newInstance(handler);throw new AssertionError("Order-dependent native shapeless recipe accepted");}
            catch(InvocationTargetException expected){require(expected.getCause() instanceof com.github.dcysteine.nesql.exporter.task.Jobs.Fault,"Wrong native greedy guard");}
            finally{registry.remove(rejected);}
        }
        registry.add(duplicate);try{
            RegistryRecipes cursor=(RegistryRecipes)constructor.newInstance(handler);
            RecipeRow row=NativeCoreFixesTest.row(new ItemStack(Items.paper),new ItemStack(low),new ItemStack(high),output);
            require(cursor.capture(0,row)&&row.inputs.size()==3,"Equivalent orb predicates were collapsed or rejected");
        }finally{registry.remove(duplicate);}
        registry.add(empty);try{
            RegistryRecipes cursor=(RegistryRecipes)constructor.newInstance(handler);
            require(cursor.size()==1&&!cursor.capture(0,NativeCoreFixesTest.row())&&cursor.exclusion(0)!=null,"Native empty predicate lost its registry exclusion evidence");
        }finally{registry.remove(empty);}
        Item custom=new Item(){@Override public boolean hasContainerItem(ItemStack stack){throw new AssertionError("Unknown shapeless container callback executed");}};
        Item.itemRegistry.addObject(31887,"nesqltest:unknown_shapeless_companion",custom);
        IRecipe unsafe=recipe(output,custom,high);registry.add(unsafe);
        try{constructor.newInstance(handler);throw new AssertionError("Unknown shapeless container callback accepted");}
        catch(InvocationTargetException expected){require(expected.getCause() instanceof com.github.dcysteine.nesql.exporter.task.Jobs.Fault,"Unknown callback was invoked");}
        finally{registry.remove(unsafe);}
        System.out.println("Native shapeless Blood Orb: all safe permutations, integer early-return and overlapping-greedy counterexamples, equivalent duplicate slots, tier/keep/container/NBT, owned native view, durable JSON, registry drift and callback guards passed");
    }
    private static IRecipe recipe(ItemStack output,Object... input)throws Exception{return (IRecipe)Class.forName(RECIPE).getConstructor(ItemStack.class,Object[].class).newInstance(output,input);}
    private static InventoryCrafting grid(ItemStack... stacks){InventoryCrafting grid=new InventoryCrafting(new net.minecraft.inventory.Container(){public boolean canInteractWith(net.minecraft.entity.player.EntityPlayer player){return true;}},3,3);for(int i=0;i<stacks.length;i++)grid.setInventorySlotContents(i,stacks[i].copy());return grid;}
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
