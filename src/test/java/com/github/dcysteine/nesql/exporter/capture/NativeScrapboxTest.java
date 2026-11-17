package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.source.Identity;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import java.lang.reflect.*;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Native drop manager on owned records; no item use, spawning or world execution. */
final class NativeScrapboxTest {
    @SuppressWarnings("unchecked")
    static void run() throws Exception {
        try(NativeRegistryScope scope=new NativeRegistryScope()){
        NativeCoreFixesTest.version("IC2","2.2.828-experimental");
        Class<?> managerType=type("ic2.core.item.ItemScrapbox$ScrapboxRecipeManager");
        require(managerType.getProtectionDomain().getCodeSource().getLocation().toString().contains("/native-tests/ic2.jar!"),"Pinned IC2 jar required");
        Constructor<?> constructor=managerType.getDeclaredConstructor();constructor.setAccessible(true);
        Object manager=constructor.newInstance();
        Field total=type("ic2.core.item.ItemScrapbox$Drop").getDeclaredField("topChance");total.setAccessible(true);
        float previous=total.getFloat(null);
        ItemStack iron=new ItemStack(Items.iron_ingot,2), gold=new ItemStack(Items.gold_ingot,3);
        Field boxField=type("ic2.core.Ic2Items").getField("scrapBox");Object priorBox=boxField.get(null);
        Object common=cpw.mods.fml.common.FMLCommonHandler.instance();
        Field side=common.getClass().getDeclaredField("sidedDelegate");side.setAccessible(true);
        scope.replace(common.getClass(),common,"sidedDelegate",Proxy.newProxyInstance(side.getType().getClassLoader(),new Class<?>[]{side.getType()},(p,m,args)->{
            if(m.getName().equals("getSide"))return cpw.mods.fml.relauncher.Side.SERVER;
            throw new UnsupportedOperationException("Unexpected FML side call: "+m.getName());
        }));
        cpw.mods.fml.common.Loader loader=cpw.mods.fml.common.Loader.instance();
        cpw.mods.fml.common.LoadController ownedController=new cpw.mods.fml.common.LoadController(loader);
        Field active=ownedController.getClass().getDeclaredField("activeContainer");active.setAccessible(true);
        cpw.mods.fml.common.ModMetadata metadata=new cpw.mods.fml.common.ModMetadata();metadata.modId="nesql_native_fixture";metadata.name="Native fixture";
        active.set(ownedController,new cpw.mods.fml.common.DummyModContainer(metadata));scope.replace(loader.getClass(),loader,"modController",ownedController);
        Class<?> internal=type("ic2.core.init.InternalName");
        Item nativeBox=(Item)type("ic2.core.item.ItemScrapbox").getConstructor(internal).newInstance(Enum.valueOf((Class)internal,"itemScrapbox"));
        ItemStack box=new ItemStack(nativeBox,9);
        scope.replace(type("ic2.core.Ic2Items"),null,"scrapBox",box.copy());
        scope.replace(type("ic2.api.recipe.Recipes"),null,"scrapboxDrops",manager);
        TemplateRecipeHandler handler=(TemplateRecipeHandler)type("ic2.neiIntegration.core.recipehandler.ScrapboxRecipeHandler").newInstance();
        require(Recipes.adapter(handler)!=null,"Missing scrapbox adapter");
        Random random=(Random)field(type("ic2.core.IC2"),null,"random");
        Field seedField=Random.class.getDeclaredField("seed");seedField.setAccessible(true);
        java.util.concurrent.atomic.AtomicLong seed=(java.util.concurrent.atomic.AtomicLong)seedField.get(random);long priorSeed=seed.get();
        try {
            total.setFloat(null,0);boxField.set(null,box.copy());
            for(int i=0;i<4;i++) invoke(managerType,manager,"addDrop",new Class<?>[]{ItemStack.class,float.class},i==2?gold:iron,new float[]{0,0.1f,0.2f,0.7f}[i]);
            TemplateRecipeHandler usage=(TemplateRecipeHandler)handler.getClass().newInstance();
            List<Recipes.Handler> discovered=Recipes.handlers(Collections.singletonList(handler),Collections.emptyList(),
                    Collections.singletonList(usage),Collections.emptyList());
            require(discovered.size()==1,"Independent scrapbox registrations duplicated the native identity");
            Recipes.Handler registration=discovered.get(0);
            require(registration.describe().get("registrationEquivalence").getAsString().equals("shared-native-bindings"),
                    "Equal reflected scrapbox weights were treated as different native sources");
            RegistrationBindings binding=RegistrationBindings.read(handler);
            float weight=total.getFloat(null);
            try {
                total.setFloat(null,weight+1f);
                require(!binding.same(RegistrationBindings.read(usage)),"Changed scrapbox weight stayed registration-equivalent");
                expect("environment_changed",registration::describe);
            } finally {total.setFloat(null,weight);}
            Field dropField=managerType.getDeclaredField("drops");dropField.setAccessible(true);
            Object nativeDrops=dropField.get(manager);
            try {
                dropField.set(manager,new ArrayList<>((List<?>)nativeDrops));
                require(!binding.same(RegistrationBindings.read(usage)),"Same-content replacement drop registry stayed equivalent");
                expect("environment_changed",registration::describe);
            } finally {dropField.set(manager,nativeDrops);}
            registration.describe();
            ScrapboxRecipes cursor=new ScrapboxRecipes(handler);
            cursor.verify();cursor.finishVerification();
            try {total.setFloat(null,weight+1f);expect("environment_changed",cursor::verify);}
            finally {total.setFloat(null,weight);}
            require(cursor.size()==2,"Equal outcome branches were not aggregated");
            int a=ScrapboxRecipes.below(total.getFloat(null),0.1f), b=ScrapboxRecipes.below(total.getFloat(null),0.1f+0.2f);
            for(int k:new int[]{0,a-1,a,a+1,b-1,b,b+1,(1<<24)-1}) {
                // Choose the exact next(24) value without replacing native Random or its consumer.
                long mask=(1L<<48)-1, multiplier=0x5DEECE66DL, inverse=0xDFE05BCB1365L;
                random.setSeed(((((long)k<<24)-11)*inverse&mask)^multiplier);
                ItemStack offered=box.copy();
                ItemStack actual=(ItemStack)invoke(managerType,manager,"getDrop",new Class<?>[]{ItemStack.class,boolean.class},offered,true);
                require(actual.getItem()==(k>=a&&k<b?Items.gold_ingot:Items.iron_ingot),"Native threshold differs at "+k);
                require(offered.stackSize==8&&actual!=iron&&actual!=gold,"Native consume/copy contract changed");
            }
            long sum=0;
            for(int i=0;i<cursor.size();i++) {
                Facts facts=new Facts("en_US");
                for(ItemStack item:new ItemStack[]{box,iron,gold}) ((Set<String>)field(facts,"items")).add(Identity.item(Item.itemRegistry.getNameForObject(item.getItem()),item.getItemDamage(),TypedNbt.encode(item.getTagCompound())));
                RecipeRow row=new RecipeRow(facts,object("owner","fixture","handler","scrapbox","key","scrapbox"),"fixture",i);
                require(cursor.capture(i,row),"Reachable drop omitted");
                com.google.gson.JsonObject out=row.outputs.get(0).getAsJsonObject(), chance=out.getAsJsonObject("chance");
                long n=chance.get("numerator").getAsLong(), d=chance.get("denominator").getAsLong();
                long samples=n*(1L<<24)/d;sum+=samples;
                require(samples==(i==0?(1<<24)-(b-a):b-a),"Wrong exact drop probability");
                require(out.get("amount").getAsString().equals(i==0?"2":"3"),"Drop quantity changed");
                require(row.inputs.get(0).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject().get("amount").getAsString().equals("1"),"Opening cost changed");
            }
            require(sum==(1<<24)&&box.stackSize==9&&iron.stackSize==2&&gold.stackSize==3,"Probability mass or ownership lost");
            total.setFloat(null,Float.NaN);
            try { new ScrapboxRecipes(handler);throw new AssertionError("NaN total accepted"); }
            catch(com.github.dcysteine.nesql.exporter.task.Jobs.Fault error) {require(error.code.equals("recipe_unsupported"),"Wrong bad-total rejection");}
            total.setFloat(null,0);
            Object zero=constructor.newInstance();
            invoke(managerType,zero,"addDrop",new Class<?>[]{ItemStack.class,float.class},iron,0f);
            invoke(managerType,zero,"addDrop",new Class<?>[]{ItemStack.class,float.class},gold,0f);
            scope.replace(type("ic2.api.recipe.Recipes"),null,"scrapboxDrops",zero);
            ScrapboxRecipes zeroCursor=new ScrapboxRecipes(handler);
            RecipeRow unused=new RecipeRow(new Facts("en_US"),object("owner","fixture","handler","scrapbox","key","scrapbox"),"fixture",0);
            require(!zeroCursor.capture(0,unused),"Unreachable zero-weight first outcome exported");
            require(((ItemStack)invoke(managerType,zero,"getDrop",new Class<?>[]{ItemStack.class,boolean.class},box.copy(),false)).getItem()==Items.gold_ingot,"Native zero-total tail rule changed");
            Object first=((List<?>)field(zero,"drops")).get(0);
            Field upper=first.getClass().getDeclaredField("upperChanceBound");upper.setAccessible(true);upper.setFloat(first,1f);
            try {new ScrapboxRecipes(handler);throw new AssertionError("Nonmonotone thresholds accepted");}
            catch(com.github.dcysteine.nesql.exporter.task.Jobs.Fault error) {require(error.code.equals("recipe_unsupported"),"Wrong threshold rejection");}
            System.out.println("Scrapbox native manager: crafting/usage equivalence, scalar/source drift, exact float boundaries, repeated outcomes, zero weight, quantity, consumption and ownership passed");
        } finally {total.setFloat(null,previous);boxField.set(null,priorBox);seed.set(priorSeed);}
        }
    }
    private static void expect(String code,Runnable action) {
        try {action.run();throw new AssertionError("Expected "+code);}
        catch(com.github.dcysteine.nesql.exporter.task.Jobs.Fault error) {require(error.code.equals(code),"Wrong rejection: "+error.code);}
    }
    private static void require(boolean value,String message) {if(!value)throw new AssertionError(message);}
}
