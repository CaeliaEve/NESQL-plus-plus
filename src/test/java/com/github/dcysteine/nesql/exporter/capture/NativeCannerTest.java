package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.source.Identity;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonObject;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.oredict.OreDictionary;
import java.lang.reflect.*;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Actual IC2 managers with a read-only semantic reader in the existing bootstrap. */
final class NativeCannerTest {
    private static final String API = "ic2.api.recipe.", HANDLER = "ic2.neiIntegration.core.recipehandler.";
    static void run() throws Exception {
        TemplateRecipeHandler solid = (TemplateRecipeHandler) Class.forName(HANDLER + "SolidCannerRecipeHandler").newInstance();
        TemplateRecipeHandler.CachedRecipe borrowed=solid.new CachedRecipe(){@Override public codechicken.nei.PositionedStack getResult(){return null;}};
        solid.arecipes.add(borrowed);
        require(Recipes.adapter(solid) != null, "Solid canner has no native registry adapter");
        Object manager = Class.forName("ic2.core.block.machine.CannerBottleRecipeManager").newInstance();
        Class<?> inputApi = Class.forName(API + "IRecipeInput");
        ItemStack container = new ItemStack(Items.bucket, 19), fill = new ItemStack(Items.paper, 13), output = new ItemStack(Items.diamond, 5);
        Object containerRule = input(container, 2), fillRule = input(fill, 3);
        Method add = manager.getClass().getMethod("addRecipe", inputApi, inputApi, ItemStack.class);
        add.invoke(manager, containerRule, fillRule, output);
        Method nativeApply = manager.getClass().getMethod("getOutputFor", ItemStack.class, ItemStack.class, boolean.class, boolean.class);
        ItemStack offeredContainer = container.copy(), offeredFill = fill.copy();
        offeredFill.setTagInfo("owner", new net.minecraft.nbt.NBTTagInt(7));
        Object result = nativeApply.invoke(manager, offeredContainer, offeredFill, true, false);
        require(result != null && offeredContainer.stackSize == 17 && offeredFill.stackSize == 10, "Native solid consumption differs");
        require(nativeApply.invoke(manager, new ItemStack(Items.bucket), fill.copy(), false, false) == null, "Native canner ignored container count");
        RegistryRecipes adapter = adapter(solid, manager);
        RecipeRow row = row(container, fill, output); adapter.capture(0, row);
        require(solid.arecipes.size()==1&&solid.arecipes.get(0)==borrowed,"Canner semantic capture mutated its borrowed NEI cache");
        require(row.inputs.size() == 2 && row.outputs.size() == 1, "Canner lost slots or added returns");
        require(choice(row,0).get("amount").getAsInt() == 2 && choice(row,1).get("amount").getAsInt() == 3,
                "Canner treated container as reusable or used display stack counts");
        for (int i=0;i<2;i++) require(choice(row,i).getAsJsonObject("consume").get("kind").getAsString().equals("consume")
                && choice(row,i).getAsJsonArray("returns").size() == 0 && choice(row,i).getAsJsonObject("rule").get("nbt").getAsBoolean(),
                "Canner gained crafting container returns or exact input NBT");
        require(row.outputs.get(0).getAsJsonObject().get("amount").getAsInt() == 5 && row.record.get("duration").getAsInt() == 200
                && row.record.get("energy").getAsInt() == 2, "Solid canner output or base energy/time changed");
        require(row.inputs.get(0).getAsJsonObject().get("slot").getAsInt() == 0 && row.inputs.get(1).getAsJsonObject().get("slot").getAsInt() == 1,
                "Canner container and fill logical slots swapped");
        RecipeRow repeat = row(container,fill,output); adapter.capture(0,repeat);
        require(repeat.outputs.equals(row.outputs) && container.stackSize == 19 && fill.stackSize == 13 && output.stackSize == 5,
                "Owned canner layout changed native registry or subsequent capture");
        ItemStack nativeOutput=(ItemStack)((List<?>)MagicApi.field(registry(manager).values().iterator().next(),"items")).get(0);
        nativeOutput.stackSize=6;
        changed(adapter::finishVerification,"Canner native output drift escaped sealing");nativeOutput.stackSize=5;
        adapter.finishVerification();adapter.close();adapter.close();
        require(solid.arecipes.size()==1&&solid.arecipes.get(0)==borrowed,"Canner close cleared borrowed cache");
        try {adapter.size();throw new AssertionError("Closed canner reader remained usable");}
        catch(Jobs.Fault failure){require(failure.code.equals("capture_closed"),failure.toString());}
        // Native registry can expose additional outputs even though NEI shows only the first.
        Object multiResult = Class.forName(API+"RecipeOutput").getConstructor(NBTTagCompound.class,ItemStack[].class)
                .newInstance(null,new ItemStack[]{output,new ItemStack(Items.coal,2)});
        registry(manager).replaceAll((key,value)->multiResult);
        RecipeRow multi = row(container,fill,output,new ItemStack(Items.coal)); adapter(solid,manager).capture(0,multi);
        require(multi.outputs.size() == 2, "Hidden native canner output was discarded or invented a UI slot");
        OreDictionary.registerOre("nesqlCannerFill", fill); OreDictionary.registerOre("nesqlCannerFill", new ItemStack(Items.iron_ingot));
        Object ore = Class.forName(API+"RecipeInputOreDict").getConstructor(String.class,int.class).newInstance("nesqlCannerFill",3);
        Object oreManager = manager.getClass().newInstance(); add.invoke(oreManager, containerRule, ore, output);
        RecipeRow alternatives = row(container,fill,output,new ItemStack(Items.iron_ingot)); adapter(solid,oreManager).capture(0,alternatives);
        require(choiceCount(alternatives,1)==2, "Canner ore alternatives collapsed");
        // getRecipes is mutable: bypass addRecipe's ambiguity check as a third-party mod can.
        Map<Object,Object> map = registry(manager);
        Object duplicate = Class.forName(API+"ICannerBottleRecipeManager$Input").getConstructor(inputApi,inputApi).newInstance(containerRule,fillRule);
        map.put(duplicate,result);
        fails(() -> adapter(solid,manager).capture(0,row(container,fill,output)), "Overlapping first-match registry exported unconditional recipes");
        map.remove(duplicate);
        Object unknown = Proxy.newProxyInstance(inputApi.getClassLoader(),new Class<?>[]{inputApi},(p,m,a)->{throw new AssertionError("Unknown predicate executed");});
        Object badKey = Class.forName(API+"ICannerBottleRecipeManager$Input").getConstructor(inputApi,inputApi).newInstance(containerRule,unknown);
        map.clear(); map.put(badKey,result);
        fails(() -> adapter(solid,manager).capture(0,row(container,fill,output)), "Unknown canner predicate was accepted");
        registration(solid);
        fluid();
        System.out.println("Native IC2 canners: read-only semantic quantities, predicates, fluids/NBT, drift/close and hidden outputs passed");
    }
    private static void registration(TemplateRecipeHandler handler)throws Exception {
        Field api=Class.forName(API+"Recipes").getField("cannerBottle");Object priorManager=api.get(null);
        cpw.mods.fml.common.Loader loader=cpw.mods.fml.common.Loader.instance();
        Map<String,cpw.mods.fml.common.ModContainer> priorMods=cpw.mods.fml.relauncher.ReflectionHelper.getPrivateValue(cpw.mods.fml.common.Loader.class,loader,"namedMods");
        Map<String,cpw.mods.fml.common.ModContainer> mods=new HashMap<>(priorMods);
        cpw.mods.fml.common.ModMetadata metadata=new cpw.mods.fml.common.ModMetadata();metadata.modId="IC2";metadata.version="2.2.828-experimental";
        mods.put("IC2",new cpw.mods.fml.common.DummyModContainer(metadata));
        cpw.mods.fml.relauncher.ReflectionHelper.setPrivateValue(cpw.mods.fml.common.Loader.class,loader,mods,"namedMods");
        CannerRecipes reader=null;
        try {
            Object manager=Class.forName("ic2.core.block.machine.CannerBottleRecipeManager").newInstance();api.set(null,manager);
            RegistrationBindings first=RegistrationBindings.read(handler);
            require(first.same(RegistrationBindings.read((TemplateRecipeHandler)handler.getClass().newInstance())),
                    "Canner independent registrations did not bind the shared native manager/map");
            reader=new CannerRecipes(handler);require(reader.size()==0,"Ready-empty canner manager was not a valid empty registry");reader.finishVerification();
            api.set(null,manager.getClass().newInstance());
            require(!first.same(RegistrationBindings.read(handler)),"Different native canner manager was treated as registration-equivalent");
            changed(reader::verify,"Native canner API manager replacement escaped drift checks");api.set(null,manager);
            reader.finishVerification();reader.close();reader=null;
            api.set(null,null);
            try {new CannerRecipes(handler);throw new AssertionError("Missing canner manager became a ready empty set");}
            catch(Jobs.Fault failure){require(failure.code.equals("handler_not_ready"),failure.toString());}
        } finally {
            if(reader!=null)reader.close();api.set(null,priorManager);
            cpw.mods.fml.relauncher.ReflectionHelper.setPrivateValue(cpw.mods.fml.common.Loader.class,loader,priorMods,"namedMods");
        }
    }
    private static void fluid() throws Exception {
        TemplateRecipeHandler handler = (TemplateRecipeHandler) Class.forName(HANDLER+"FluidCannerRecipeHandler").newInstance();
        TemplateRecipeHandler.CachedRecipe borrowed=handler.new CachedRecipe(){@Override public codechicken.nei.PositionedStack getResult(){return null;}};
        handler.arecipes.add(borrowed);
        require(Recipes.adapter(handler)!=null,"Fluid canner has no native registry adapter");
        Object manager = Class.forName("ic2.core.block.machine.CannerEnrichRecipeManager").newInstance();
        ItemStack additive = new ItemStack(Items.paper,19);
        FluidStack water = new FluidStack(FluidRegistry.WATER,1250), product = new FluidStack(FluidRegistry.LAVA,1500);
        water.tag = new NBTTagCompound(); water.tag.setString("grade","pure");
        product.tag = new NBTTagCompound(); product.tag.setInteger("batch",3);
        manager.getClass().getMethod("addRecipe",FluidStack.class,Class.forName(API+"IRecipeInput"),FluidStack.class)
                .invoke(manager,water,input(additive,4),product);
        Method apply = manager.getClass().getMethod("getOutputFor",FluidStack.class,ItemStack.class,boolean.class,boolean.class);
        require(apply.invoke(manager,new FluidStack(FluidRegistry.WATER,2000),additive.copy(),false,false)==null,"Native fluid canner ignores fluid tags");
        FluidStack supply = water.copy(); supply.amount=2000; ItemStack offered = additive.copy();
        Object result = apply.invoke(manager,supply,offered,true,false);
        require(result!=null && supply.amount==750 && offered.stackSize==15,"Native fluid canner quantities differ");
        RegistryRecipes reader=adapter(handler,manager);RecipeRow row = row(additive);reader.capture(0,row);
        require(row.inputs.size()==2 && row.outputs.size()==1,"Fluid canner invented material containers");
        require(row.inputs.get(0).getAsJsonObject().get("kind").getAsString().equals("fluid")
                && choice(row,0).get("amount").getAsInt()==1250 && choice(row,0).getAsJsonObject("rule").get("kind").getAsString().equals("exact"),"Fluid source became cell or lost tags/amount");
        require(choice(row,0).get("id").getAsString().equals(Identity.fluid("water",TypedNbt.encode(water.tag)))
                && row.outputs.get(0).getAsJsonObject().get("id").getAsString().equals(Identity.fluid("lava",TypedNbt.encode(product.tag)))
                && row.outputs.get(0).getAsJsonObject().get("amount").getAsInt()==1500,"Fluid cell's 1000mB/no-NBT display replaced native facts");
        require(row.record.get("duration").getAsInt()==200 && row.record.get("energy").getAsInt()==4,"Fluid canner power/time changed");
        require(handler.arecipes.size()==1&&handler.arecipes.get(0)==borrowed,"Fluid canner semantic capture constructed or changed a borrowed layout");
        FluidStack nativeProduct=(FluidStack)registry(manager).values().iterator().next();
        nativeProduct.amount=1501;changed(reader::finishVerification,"Fluid canner output amount drift escaped sealing");nativeProduct.amount=1500;
        reader.finishVerification();reader.close();reader.close();
        require(water.amount==1250 && product.amount==1500 && additive.stackSize==19,"Fluid UI borrowed registry values");
    }
    private static Object input(ItemStack stack,int amount) throws Exception {return Class.forName(API+"RecipeInputItemStack").getConstructor(ItemStack.class,int.class).newInstance(stack,amount);}
    private static RegistryRecipes adapter(TemplateRecipeHandler handler,Object manager) throws Exception {
        Constructor<?> constructor=Class.forName("com.github.dcysteine.nesql.exporter.capture.CannerRecipes").getDeclaredConstructor(TemplateRecipeHandler.class,Object.class);
        constructor.setAccessible(true);
        try{return (RegistryRecipes)constructor.newInstance(handler,manager);}catch(InvocationTargetException e){throw (Exception)e.getCause();}
    }
    @SuppressWarnings("unchecked") private static Map<Object,Object> registry(Object manager){return (Map<Object,Object>)MagicApi.invoke(manager.getClass(),manager,"getRecipes",new Class<?>[0]);}
    private static JsonObject choice(RecipeRow row,int index){return row.inputs.get(index).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject();}
    private static int choiceCount(RecipeRow row,int index){return row.inputs.get(index).getAsJsonObject().getAsJsonArray("choices").size();}
    @SuppressWarnings("unchecked") private static RecipeRow row(ItemStack... stacks){
        Facts facts=new Facts("en_US"); Set<String> known=(Set<String>)MagicApi.field(facts,"items");
        for(ItemStack stack:stacks)known.add(Identity.item(Item.itemRegistry.getNameForObject(stack.getItem()),stack.getItemDamage(),TypedNbt.encode(stack.getTagCompound())));
        return new RecipeRow(facts,object("owner","IC2","handler","native","key","canner"),"category_test",0);
    }
    private interface Action {void run() throws Exception;}
    private static void changed(Action action,String message)throws Exception{try{action.run();throw new AssertionError(message);}catch(Jobs.Fault failure){require(failure.code.equals("recipe_changed"),failure.toString());}}
    private static void fails(Action action,String message)throws Exception {try{action.run();throw new AssertionError(message);}catch(Jobs.Fault expected){require(expected.code.equals("recipe_unsupported"),"Wrong canner failure: "+expected);}}
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
