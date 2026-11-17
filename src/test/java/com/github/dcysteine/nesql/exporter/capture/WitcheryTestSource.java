package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import codechicken.nei.ItemList;
import codechicken.nei.recipe.TemplateRecipeHandler;
import java.util.*;
import net.minecraft.item.ItemStack;

/** Real native singleton/backing tables in one isolated JVM fixture scope.
 * The production readers keep their normal constructors and admission gates. */
public final class WitcheryTestSource implements AutoCloseable {
    private final NativeRegistryScope scope=new NativeRegistryScope();
    private final List<RegistryRecipes> readers=new ArrayList<>();
    WitcheryTestSource() throws Exception {
        try {
            NativeCoreFixesTest.version("witchery","0.24.1");
            NativeCoreFixesTest.version("WitcheryExtras","1.3.8");
            NativeCoreFixesTest.version("WitchingGadgets","1.7.25-GTNH");
            scope.replace(ItemList.class,null,"loadFinished",true);
            Class<?> witchery=type("com.emoniph.witchery.Witchery");
            for(String role:new String[]{"Items","Blocks","Fluids"})scope.replace(witchery,null,role,field(witchery,null,role));
        } catch(Exception|Error failure) {
            try{scope.close();}catch(Exception|Error closing){failure.addSuppressed(closing);}throw failure;
        }
    }
    RegistryRecipes open(TemplateRecipeHandler handler,List<?> definitions) throws Exception {
        String name=handler.getClass().getName();List<?> owned=new ArrayList<>(definitions);
        RegistryRecipes reader;
        if(name.equals("witchinggadgets.client.nei.NEIInfernalBlastfurnaceHandler")){
            scope.replace(type("witchinggadgets.common.util.recipe.InfernalBlastfurnaceRecipe"),null,"recipes",owned);
            reader=new WitcheryCookingRecipes(handler);
        } else if(name.endsWith("NEI_Handler_Kettle")){
            Class<?> type=type("com.emoniph.witchery.crafting.KettleRecipes");
            scope.replace(type,field(type,null,"INSTANCE"),"recipes",owned);
            reader=new WitcheryCookingRecipes(handler);
        } else if(name.endsWith("NEI_Handler_Cauldron")){
            Class<?> type=type("com.emoniph.witchery.brewing.WitcheryBrewRegistry");Object registry=field(type,null,"INSTANCE");
            Hashtable<Object,Object> dispatch=new Hashtable<>();
            Class<?> key=type(WitcheryBrewActions.KEY),power=type("com.emoniph.witchery.brewing.AltarPower");
            Class<?> action=type("com.emoniph.witchery.brewing.action.BrewActionRitualRecipe");
            Class<?> recipe=type(action.getName()+"$Recipe");Object empty=java.lang.reflect.Array.newInstance(recipe,0);
            java.lang.reflect.Constructor<?> constructor=action.getConstructor(key,power,empty.getClass());
            for(Object definition:definitions){
                dispatch.put(field(definition,"ITEM_KEY"),definition);
                for(Object row:(Object[])field(definition,"recipes"))for(ItemStack ingredient:(ItemStack[])field(row,"ingredients")){
                    Object physical=key.getConstructor(net.minecraft.item.Item.class,int.class).newInstance(ingredient.getItem(),ingredient.getItemDamage());
                    dispatch.put(physical,constructor.newInstance(physical,power.getConstructor(int.class).newInstance(0),empty));
                }
            }
            scope.replace(type,registry,"recipes",owned);scope.replace(type,registry,"ingredients",dispatch);
            reader=new WitcheryCauldronRecipes(handler);
        } else if(name.endsWith("NEI_Handler_Oven")){
            scope.replace(type("alkalus.main.core.crafting.OvenRecipes"),null,"mRecipeMap",owned);
            reader=new WitcheryOvenRecipes(handler);
        } else throw new AssertionError("Unknown native Witchery fixture handler: "+name);
        readers.add(reader);return reader;
    }
    void candidates(ItemStack... stacks) throws Exception {
        com.google.common.collect.ArrayListMultimap<net.minecraft.item.Item,ItemStack> table=com.google.common.collect.ArrayListMultimap.create();
        for(ItemStack stack:stacks)table.put(stack.getItem(),stack);
        scope.replace(ItemList.class,null,"itemMap",table);
    }
    public static Object emptyBrewRegistry(Class<?> owner) throws Exception {
        java.lang.reflect.Field access=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");access.setAccessible(true);
        Object registry=((sun.misc.Unsafe)access.get(null)).allocateInstance(owner);
        for(String name:new String[]{"ingredients","recipes"}){
            java.lang.reflect.Field field=owner.getDeclaredField(name);field.setAccessible(true);
            field.set(registry,name.equals("ingredients")?new Hashtable<>():new ArrayList<>());
        }
        return registry;
    }
    @Override public void close() throws Exception {
        Throwable failure=null;
        for(int i=readers.size()-1;i>=0;i--)try{readers.get(i).close();}catch(RuntimeException|Error closing){if(failure==null)failure=closing;else failure.addSuppressed(closing);}
        try{scope.close();}catch(Exception|Error closing){if(failure==null)failure=closing;else failure.addSuppressed(closing);}
        if(failure instanceof Error)throw (Error)failure;if(failure!=null)throw (Exception)failure;
    }
}
