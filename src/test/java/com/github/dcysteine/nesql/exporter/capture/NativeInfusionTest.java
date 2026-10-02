package com.github.dcysteine.nesql.exporter.capture;

import com.github.dcysteine.nesql.exporter.task.Jobs;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import java.lang.reflect.Method;
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
        System.out.println("Native infusion: impossible sentinel/OR, retained exact alternatives and unknown predicate rejection passed");
    }

    @SuppressWarnings("unchecked")
    private static List<MagicRecipes.Candidate> capture(Object predicate) throws Exception {
        Method method = MagicRecipes.class.getDeclaredMethod("ingredient", Object.class);
        method.setAccessible(true);
        try { return (List<MagicRecipes.Candidate>) method.invoke(null, predicate); }
        catch (java.lang.reflect.InvocationTargetException error) {
            if (error.getCause() instanceof Error) throw (Error) error.getCause();
            throw (Exception) error.getCause();
        }
    }
}
