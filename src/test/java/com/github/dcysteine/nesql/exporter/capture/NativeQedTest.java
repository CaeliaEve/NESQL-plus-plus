package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.source.Identity;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.*;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.*;
import net.minecraft.nbt.NBTTagInt;
import net.minecraftforge.oredict.ShapedOreRecipe;
import java.lang.reflect.*;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** The regression catches treating QED as ordinary crafting (container returns) or losing registry priority. */
final class NativeQedTest {
    static void run() throws Exception {
        Class<?> adapter;
        try { adapter = Class.forName("com.github.dcysteine.nesql.exporter.capture.QedRecipes"); }
        catch (ClassNotFoundException missing) { throw new AssertionError("QED has no native registry and ender-flux adapter", missing); }
        TemplateRecipeHandler handler = (TemplateRecipeHandler) Class.forName("com.rwtema.extrautils.nei.EnderConstructorHandler").newInstance();
        TemplateRecipeHandler.CachedRecipe sentinel=handler.new CachedRecipe(){
            public codechicken.nei.PositionedStack getResult(){return null;}
            public List<codechicken.nei.PositionedStack> getIngredients(){return Collections.emptyList();}
        };
        handler.arecipes.add(sentinel);
        ItemStack milk = new ItemStack(Items.milk_bucket, 23), coal = new ItemStack(Items.coal, 17, 0);
        ItemStack result = new ItemStack(Items.emerald, 3); result.setTagInfo("native", new NBTTagInt(12));
        List<IRecipe> recipes = new ArrayList<>();
        recipes.add(new ShapelessRecipes(new ItemStack(Items.diamond), Collections.singletonList(coal.copy())));
        recipes.add(new ShapedRecipes(1, 1, new ItemStack[]{coal.copy()}, new ItemStack(Items.apple)));
        recipes.add(new ShapedRecipes(2, 1, new ItemStack[]{milk.copy(), coal.copy()}, result));
        Class<?> manager = Class.forName("com.rwtema.extrautils.tileentity.enderconstructor.EnderConstructorRecipesHandler");
        Object borrowedTable=manager.getField("recipes").get(null);
        try {
        manager.getField("recipes").set(null, new ArrayList<>(recipes));
        Constructor<?> ctor = adapter.getDeclaredConstructor(TemplateRecipeHandler.class, List.class); ctor.setAccessible(true);
        RegistryRecipes capture = (RegistryRecipes) ctor.newInstance(handler, recipes);
        require(capture.size() == 2, "QED native loader must omit shapeless display entries, while retaining their selection priority");
        Facts facts = new Facts("en_US");
        @SuppressWarnings("unchecked") Set<String> items = (Set<String>) MagicApi.field(facts, "items");
        for (ItemStack stack : Arrays.asList(milk, coal, result, new ItemStack(Items.diamond), new ItemStack(Items.apple))) items.add(id(stack));
        RecipeRow row = new RecipeRow(facts, object("owner", "ExtraUtilities", "handler", handler.getClass().getName(), "key", "qed"), "category_test", 1);
        require(capture.capture(1, row), "Native shaped QED recipe was lost");
        JsonObject process = row.record.getAsJsonObject("process");
        require(process.get("kind").getAsString().equals("qed") && process.get("enderFlux").getAsString().equals("20000"),
                "Native QED ender-flux cost was lost or mislabeled as EU/t");
        require(process.getAsJsonArray("earlier").size() == 2
                && process.getAsJsonArray("earlier").get(0).getAsJsonObject().get("grid").isJsonNull(),
                "A hidden shapeless native entry was dropped from first-match priority");
        require(row.record.get("energy").isJsonNull() && row.record.get("duration").isJsonNull(), "Supply-dependent QED timing became a fixed energy/time value");
        require(row.inputs.size() == 2 && row.outputs.get(0).getAsJsonObject().get("amount").getAsString().equals("3"), "Native QED quantities changed");
        JsonObject grid = row.record.getAsJsonObject("grid");
        require(grid.get("width").getAsInt() == 2 && grid.get("height").getAsInt() == 1 && grid.get("mirror").getAsBoolean(), "Native QED grid changed");
        for (JsonElement raw : row.inputs) {
            JsonObject choice = raw.getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject();
            require(choice.get("amount").getAsString().equals("1") && choice.getAsJsonArray("returns").size() == 0
                    && choice.getAsJsonObject("consume").get("kind").getAsString().equals("consume"),
                    "QED incorrectly honored template count or crafting-container returns");
            require(choice.getAsJsonObject("rule").get("nbt").getAsBoolean(), "QED ordinary crafting matching should ignore offered NBT");
        }
        for (boolean mirror : new boolean[]{false, true}) {
            InventoryCrafting inventory = new InventoryCrafting(new Container() { public boolean canInteractWith(EntityPlayer p) { return false; } }, 3, 3);
            ItemStack offered = milk.copy(); offered.stackSize = 1; offered.setTagInfo("offered", new NBTTagInt(99));
            inventory.setInventorySlotContents(mirror ? 1 : 0, offered);
            inventory.setInventorySlotContents(mirror ? 0 : 1, coal.copy());
            ItemStack nativeOutput = (ItemStack) manager.getMethod("findMatchingRecipe", InventoryCrafting.class, net.minecraft.world.World.class).invoke(null, inventory, null);
            require(ItemStack.areItemStacksEqual(nativeOutput, result), "Fixture does not match native QED mirrored/ignored-NBT behavior");
        }
        require(handler.arecipes.size()==1&&handler.arecipes.get(0)==sentinel,"QED capture replaced a borrowed native GUI cache");
        require(milk.stackSize == 23 && coal.stackSize == 17 && result.getTagCompound().getInteger("native") == 12, "QED capture mutated registry stacks");
        capture.finishVerification(); result.stackSize = 4;
        try { capture.finishVerification(); throw new AssertionError("QED output mutation was not detected"); }
        catch (Jobs.Fault expected) { require(expected.code.equals("recipe_changed"), "Wrong QED drift guard failure"); }
        result.stackSize = 3;
        ShapedRecipes selected=(ShapedRecipes)recipes.get(2);ItemStack originalInput=selected.recipeItems[0];
        try {
            selected.recipeItems[0]=originalInput.copy();fails("recipe_changed",capture::finishVerification);
        } finally {selected.recipeItems[0]=originalInput;}
        capture.finishVerification();capture.close();capture.close();fails("capture_closed",capture::size);
        try(RegistryRecipes empty=(RegistryRecipes)ctor.newInstance(handler,new ArrayList<>())) {
            require(empty.size()==0,"QED ready-empty table was rejected");empty.finishVerification();
        }
        try { ctor.newInstance(handler, Collections.singletonList(new Object())); throw new AssertionError("Unknown QED callback executed or accepted"); }
        catch (InvocationTargetException expected) { require(expected.getCause() instanceof Jobs.Fault, "Wrong QED callback guard"); }
        row.finish(); java.nio.file.Files.write(java.nio.file.Paths.get("build/native-tests/qed-record.json"), CanonicalJson.bytes(row.record));
        production(handler,recipes,Arrays.asList(milk,coal,result,new ItemStack(Items.diamond),new ItemStack(Items.apple)));
        NativeMachinesTest.ExtremeMetadataProbe metadata=new NativeMachinesTest.ExtremeMetadataProbe();
        fails("recipe_unsupported",()->new QedRecipes(handler,new ArrayList<>(Collections.singletonList(new ShapedRecipes(1,1,new ItemStack[]{new ItemStack(metadata)},new ItemStack(Items.diamond))))));
        require(metadata.calls==0,"QED snapshot executed an unknown metadata callback");
        ItemStack negative=new ItemStack(Items.paper);
        cpw.mods.fml.relauncher.ReflectionHelper.setPrivateValue(ItemStack.class,negative,-1,"itemDamage","field_77991_e");
        fails("recipe_unsupported",()->new QedRecipes(handler,new ArrayList<>(Collections.singletonList(new ShapedRecipes(1,1,new ItemStack[]{negative},new ItemStack(Items.diamond))))));
        NativeMachinesTest.ExtremeContainerProbe irrelevant=new NativeMachinesTest.ExtremeContainerProbe();
        try(RegistryRecipes ignored=new QedRecipes(handler,new ArrayList<>(Collections.singletonList(new ShapedRecipes(1,1,new ItemStack[]{new ItemStack(irrelevant)},new ItemStack(Items.diamond)))))) {
            ignored.finishVerification();require(irrelevant.calls==0,"QED called a container even though the machine never returns one");
        }
        copyNbt(handler,manager);
        System.out.println("Native QED: shaped enumeration, hidden shapeless priority, unit consumption without containers, mirrored grid, typed output NBT, 20000 flux and registry drift passed");
        } finally {manager.getField("recipes").set(null,borrowedTable);}
    }
    @SuppressWarnings("unchecked") private static void production(TemplateRecipeHandler handler,List<IRecipe> recipes,List<ItemStack> known)throws Exception {
        Map<String,cpw.mods.fml.common.ModContainer> mods=(Map<String,cpw.mods.fml.common.ModContainer>)MagicApi.field(cpw.mods.fml.common.Loader.instance(),"namedMods");
        cpw.mods.fml.common.ModContainer oldMod=mods.get("ExtraUtilities");
        Field locale=null;for(Field field:net.minecraft.client.resources.I18n.class.getDeclaredFields())if(field.getType()==net.minecraft.client.resources.Locale.class)locale=field;
        require(locale!=null,"Native locale root missing");locale.setAccessible(true);Object oldLocale=locale.get(null);
        Class<?> manager=Class.forName("com.rwtema.extrautils.tileentity.enderconstructor.EnderConstructorRecipesHandler");
        Field table=manager.getField("recipes");Object active=table.get(null);
        try {
            NativeCoreFixesTest.version("ExtraUtilities","1.2.12");locale.set(null,new net.minecraft.client.resources.Locale());
            RegistrationBindings binding=RegistrationBindings.read(handler);
            require(binding.same(RegistrationBindings.read((TemplateRecipeHandler)handler.getClass().newInstance())),"Independent QED registration sources differ");
            try(Recipes.Cursor cursor=new Recipes.Handler(handler,0).open(NativeCoreFixesTest.row(known.toArray(new ItemStack[0])).facts)) {
                require(MagicApi.field(cursor,"handler")==null,"QED production still owns a native GUI projection handler");
                cursor.capture(1);cursor.finishVerification();
            }
            try(QedRecipes reader=new QedRecipes(handler)) {
                table.set(null,new ArrayList<>(recipes));
                require(!binding.same(RegistrationBindings.read(handler)),"QED registration accepted a replacement native table");
                fails("recipe_changed",reader::size);table.set(null,active);
                Thread.currentThread().interrupt();
                try{reader.size();throw new AssertionError("QED reader ignored cancellation");}
                catch(java.util.concurrent.CancellationException expected){}
                finally{Thread.interrupted();}
                reader.finishVerification();
            }
        } finally {
            table.set(null,active);locale.set(null,oldLocale);
            if(oldMod==null)mods.remove("ExtraUtilities");else mods.put("ExtraUtilities",oldMod);
        }
    }
    private static void fails(String code,Runnable action) {
        try{action.run();throw new AssertionError("QED accepted invalid boundary: "+code);}
        catch(Jobs.Fault failure){require(failure.code.equals(code),"Wrong QED boundary: "+failure.code);}
    }
    private static void copyNbt(TemplateRecipeHandler handler,Class<?> manager)throws Exception {
        ItemStack left=new ItemStack(Items.paper,5),right=new ItemStack(Items.coal,8),base=new ItemStack(Items.emerald,3);
        left.setTagInfo("owner",new net.minecraft.nbt.NBTTagString("left"));right.setTagInfo("owner",new net.minecraft.nbt.NBTTagString("right"));
        base.setTagInfo("base",new NBTTagInt(41));
        ShapedRecipes recipe=new ShapedRecipes(2,1,new ItemStack[]{left,right},base);
        cpw.mods.fml.relauncher.ReflectionHelper.setPrivateValue(ShapedRecipes.class,recipe,true,"field_92101_f");
        ItemStack fromLeft=base.copy(),fromRight=base.copy();
        fromLeft.setTagCompound((net.minecraft.nbt.NBTTagCompound)left.getTagCompound().copy());
        fromRight.setTagCompound((net.minecraft.nbt.NBTTagCompound)right.getTagCompound().copy());
        List<ItemStack> known=Arrays.asList(left,right,base,fromLeft,fromRight);
        RecipeRow captured=NativeCoreFixesTest.row(known.toArray(new ItemStack[0]));
        try(QedRecipes reader=new QedRecipes(handler,new ArrayList<>(Collections.singletonList(recipe)))) {
            reader.capture(0,captured);reader.finishVerification();
            require(captured.record.getAsJsonObject("process").get("copyNbt").getAsBoolean(),"QED lost its native tag-transfer flag");
            JsonArray samples=captured.outputs.get(0).getAsJsonObject().getAsJsonObject("change").getAsJsonArray("samples");
            require(samples.size()==3&&samples.get(1).getAsJsonObject().get("id").getAsString().equals(id(fromLeft))
                    &&samples.get(2).getAsJsonObject().get("id").getAsString().equals(id(fromRight)),"QED native tag-transfer projections changed");
            JsonObject origin=object("owner","ExtraUtilities","handler",handler.getClass().getName(),"key","native-qed-nbt");
            captured.record.add("source",origin);captured.record.addProperty("category",Identity.origin("category",origin));
            NativeContractFixture.writeOwned("native-qed-nbt",captured,known);
        }
        Object active=manager.getField("recipes").get(null);
        try {
            manager.getField("recipes").set(null,new ArrayList<>(Collections.singletonList(recipe)));
            for(boolean mirrored:new boolean[]{false,true}) {
                InventoryCrafting offered=new InventoryCrafting(new Container(){public boolean canInteractWith(EntityPlayer player){return false;}},3,3);
                offered.setInventorySlotContents(mirrored?1:0,left.copy());offered.setInventorySlotContents(mirrored?0:1,right.copy());
                ItemStack actual=(ItemStack)manager.getMethod("findMatchingRecipe",InventoryCrafting.class,net.minecraft.world.World.class).invoke(null,offered,null);
                require(ItemStack.areItemStacksEqual(actual,mirrored?fromLeft:fromRight),"Native QED did not use its last tagged offered cell");
            }
        } finally {manager.getField("recipes").set(null,active);}
        require(left.stackSize==5&&right.stackSize==8&&base.getTagCompound().getInteger("base")==41,"QED native projections changed borrowed definitions");
    }
    private static String id(ItemStack stack) { return Identity.item(Item.itemRegistry.getNameForObject(stack.getItem()), Items.feather.getDamage(stack), TypedNbt.encode(stack.getTagCompound())); }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
