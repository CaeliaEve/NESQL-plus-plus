package com.github.dcysteine.nesql.exporter.capture;

import com.github.dcysteine.nesql.exporter.task.Jobs;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import thaumcraft.api.crafting.InfusionRecipe;
import thaumcraft.api.aspects.AspectList;
import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Native TC4Tweaks predicates, including the intentionally impossible ERROR sentinel. */
public final class NativeInfusionTest {
    private NativeInfusionTest() {}
    public static void main(String[] args) throws Exception { GameTest.run(); }

    public static void run() throws Exception {
        Class<?> api = Class.forName("net.glease.tc4tweak.api.infusionrecipe.RecipeIngredient");
        Object impossible = api.getField("ERROR").get(null);
        Object apple = api.getMethod("item", boolean.class, ItemStack.class).invoke(null, true, new ItemStack(Items.apple));
        Method or = api.getMethod("or", api);
        Method matches = api.getMethod("matches", ItemStack.class);
        if ((Boolean) matches.invoke(impossible, new ItemStack(Items.apple))) throw new AssertionError("Pinned sentinel changed semantics");
        if (!capture(impossible).isEmpty()) throw new AssertionError("Impossible predicate exported its fire placeholder");
        if (!capture(or.invoke(impossible, impossible)).isEmpty()) throw new AssertionError("Impossible OR became satisfiable");
        for (Object combination : new Object[] {or.invoke(impossible, apple), or.invoke(apple, impossible)}) {
            List<MagicRecipes.Candidate> values = capture(combination);
            if (values.size() != 1 || values.get(0).item.getItem() != Items.apple
                    || !values.get(0).rule.get("kind").getAsString().equals("exact")) {
                throw new AssertionError("Removing an impossible OR branch lost a valid alternative or its matching rule");
            }
        }
        Object unknown = java.lang.reflect.Proxy.newProxyInstance(api.getClassLoader(), new Class<?>[] {api},
                (proxy, method, arguments) -> { throw new AssertionError("Unknown predicate must not be sampled as its semantics"); });
        try {
            capture(or.invoke(apple, unknown));
            throw new AssertionError("Unknown OR branch was silently dropped");
        } catch (Jobs.Fault expected) {
            if (!expected.code.equals("recipe_unsupported")) throw expected;
        }
        Object center=api.getMethod("item",boolean.class,ItemStack.class).invoke(null,true,new ItemStack(Items.diamond));
        Object carrot=api.getMethod("item",boolean.class,ItemStack.class).invoke(null,true,new ItemStack(Items.carrot));
        InfusionRecipe recipe=definition(center,Arrays.asList(apple,carrot),new ItemStack[]{new ItemStack(Items.gold_ingot)});
        Object network=Class.forName("net.glease.tc4tweak.network.NetworkedConfiguration");
        Field setting=((Class<?>)network).getDeclaredField("infusionOreDictMode");setting.setAccessible(true);Object prior=setting.get(null);
        setting.set(null,Enum.valueOf((Class)Class.forName("net.glease.tc4tweak.modules.infusionRecipe.InfusionOreDictMode"),"Default"));
        try(NativeItemExamples examples=new NativeItemExamples();InfusionLegacyInputs.Context context=new InfusionLegacyInputs.Context();
            NativeInfusionRecipes reader=new NativeInfusionRecipes(recipe,examples,context)){
            List<List<ItemStack>> slots=new ArrayList<>();slots.add(new ArrayList<>(Collections.singletonList(new ItemStack(Items.diamond))));
            slots.add(new ArrayList<>(Collections.singletonList(new ItemStack(Items.apple))));slots.add(new ArrayList<>(Collections.singletonList(new ItemStack(Items.carrot))));
            Method witness=NativeInfusionRecipes.class.getDeclaredMethod("enhancedWitness",List.class);witness.setAccessible(true);witness.invoke(reader,slots);
            if(slots.size()!=4||slots.get(3).get(0).getItem()!=Items.gold_ingot)throw new AssertionError("Different startup/cycle cardinalities lost independent cycle stock");
            reader.finishVerification();context.finishVerification();examples.verify();
        }finally{setting.set(null,prior);}
        System.out.println("Native infusion: predicates, unknown-owner rejection and independent startup/cycle stock passed");
    }

    @SuppressWarnings("unchecked")
    private static List<MagicRecipes.Candidate> capture(Object predicate) throws Exception {
        Field setting=Class.forName("net.glease.tc4tweak.network.NetworkedConfiguration").getDeclaredField("infusionOreDictMode");setting.setAccessible(true);Object prior=setting.get(null);
        if(prior==null)setting.set(null,Enum.valueOf((Class)Class.forName("net.glease.tc4tweak.modules.infusionRecipe.InfusionOreDictMode"),"Default"));
        try(NativeItemExamples examples=new NativeItemExamples();InfusionInputSource source=new InfusionInputSource(definition(predicate,Collections.emptyList(),new ItemStack[0]),examples)){
            List<MagicRecipes.Candidate> choices=source.copies().get(0);source.finishVerification();examples.verify();return choices;
        }finally{setting.set(null,prior);}
    }
    private static InfusionRecipe definition(Object central,List<?> components,ItemStack[] cycle) throws Exception {
        Field access=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");access.setAccessible(true);
        Class<?> enhanced=Class.forName(InfusionInputSource.ENHANCED);
        InfusionRecipe recipe=(InfusionRecipe)((sun.misc.Unsafe)access.get(null)).allocateInstance(enhanced);
        set(enhanced,recipe,"central",central);set(enhanced,recipe,"components",new ArrayList<>(components));
        set(InfusionRecipe.class,recipe,"recipeInput",new ItemStack(Items.diamond));set(InfusionRecipe.class,recipe,"components",cycle);
        set(InfusionRecipe.class,recipe,"recipeOutput",new ItemStack(Items.diamond));set(InfusionRecipe.class,recipe,"research","");set(InfusionRecipe.class,recipe,"aspects",new AspectList());
        return recipe;
    }
    private static void set(Class<?> owner,Object target,String name,Object value)throws Exception{
        Field field=owner.getDeclaredField(name);field.setAccessible(true);field.set(target,value);
    }
}
