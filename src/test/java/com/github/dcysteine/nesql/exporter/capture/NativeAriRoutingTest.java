package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.recipe.ICraftingHandler;
import java.lang.reflect.Field;
import sun.misc.Unsafe;

/** Route actual ARI handler classes without constructing their GUI/font state. */
final class NativeAriRoutingTest {
    static void run() throws Exception {
        Field access = Unsafe.class.getDeclaredField("theUnsafe");
        access.setAccessible(true);
        Unsafe memory = (Unsafe) access.get(null);
        String prefix = "com.gtnewhorizons.aspectrecipeindex.nei.";
        for (String name : new String[] {
                "arcaneworkbench.ShapedArcaneRecipeHandler",
                "arcaneworkbench.ShapelessArcaneRecipeHandler",
                "AlchemyRecipeHandler", "InfusionRecipeHandler" }) {
            ICraftingHandler handler = (ICraftingHandler) memory.allocateInstance(Class.forName(prefix + name));
            if (Recipes.adapter(handler) != Recipes.Adapter.MAGIC)
                throw new AssertionError("Registered ARI handler lost its native recipe adapter: " + name);
            RegistrationBindings.read(handler);
        }
        ICraftingHandler wand = (ICraftingHandler) memory.allocateInstance(
                Class.forName(prefix + "arcaneworkbench.WandRecipeHandler"));
        if (Recipes.adapter(wand) != null)
            throw new AssertionError("Independent wand crafting was mistaken for ordinary arcane crafting");
        System.out.println("ARI target routing: four native registry handlers; independent wand handler not misclassified");
    }
}
