package com.github.dcysteine.nesql.exporter.capture;

import java.lang.reflect.*;
import java.util.*;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.*;

/** Real Forge ore constructors retain a readonly ArrayList view, not an exact ArrayList. */
final class NativeOreAlternativesTest {
    static void run() throws Exception {
        OreDictionary.registerOre("fixtureReadonlyCoal",new ItemStack(Items.coal));
        Object recipe=new ShapedOreRecipe(new ItemStack(Items.diamond),"C",'C',"fixtureReadonlyCoal");
        Object alternatives=((ShapedOreRecipe)recipe).getInput()[0];
        if(!NativeOreStorage.oreView(alternatives))throw new AssertionError("Missing actual Forge readonly-view fixture");
        List<Throwable> failures=new ArrayList<>();
        try(NativeRegistryScope scope=new NativeRegistryScope()){
            try {
                Class<?> entry=Class.forName(QedRecipes.class.getName()+"$Entry");
                Constructor<?> ctor=entry.getDeclaredConstructor(Object.class,int.class,boolean.class);ctor.setAccessible(true);
                ctor.newInstance(recipe,0,false);
            } catch(InvocationTargetException failure){failures.add(failure.getCause());}
            try {
                Class<?> entry=Class.forName(RollingRecipes.class.getName()+"$Entry");
                Constructor<?> ctor=entry.getDeclaredConstructor(Object.class,int.class);ctor.setAccessible(true);ctor.newInstance(recipe,0);
            } catch(InvocationTargetException failure){failures.add(failure.getCause());}
            try {
                Object extreme=MagicApi.type("fox.spiteful.avaritia.crafting.ExtremeShapedOreRecipe")
                    .getConstructor(ItemStack.class,Object[].class).newInstance(new ItemStack(Items.diamond),new Object[]{"C",'C',"fixtureReadonlyCoal"});
                Class<?> entry=Class.forName(ExtremeRecipes.class.getName()+"$Raw");
                Constructor<?> ctor=entry.getDeclaredConstructor(Object.class,boolean.class);ctor.setAccessible(true);ctor.newInstance(extreme,false);
            } catch(InvocationTargetException failure){failures.add(failure.getCause());}
        }
        if(!failures.isEmpty()){AssertionError failure=new AssertionError("Native ore alternatives rejected: "+failures);for(Throwable cause:failures)failure.addSuppressed(cause);throw failure;}
        System.out.println("Forge readonly ore alternatives: QED, rolling and extreme boundaries passed");
    }
}
