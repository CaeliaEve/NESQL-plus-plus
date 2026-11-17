package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.recipe.*;
import codechicken.nei.PositionedStack;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import java.util.*;

/** All four NEI registries contribute identities; usage-only is never blindly cast. */
final class HandlerDiscoveryTest {
    static void run() {
        scalarBindings();
        List<ICraftingHandler> crafting = new ArrayList<>();
        List<ICraftingHandler> serialCrafting = new ArrayList<>();
        List<IUsageHandler> usage = new ArrayList<>();
        List<IUsageHandler> serialUsage = new ArrayList<>();
            crafting.add(new FurnaceRecipeHandler());
            usage.add(new FurnaceRecipeHandler());
            usage.add(new ShapedRecipeHandler());
            crafting.add(new ShapedRecipeHandler());
            serialCrafting.add(new ShapelessRecipeHandler());
            serialUsage.add(new ShapelessRecipeHandler());
            IUsageHandler only = new UsageOnly();
            serialUsage.add(only);
            List<Recipes.Handler> found = Recipes.handlers(crafting, serialCrafting, usage, serialUsage);
            if (found.size() != 4) throw new AssertionError("Four registry inventory omitted identities: " + found.size());
            Recipes.Handler furnace = found.stream().filter(h -> h.origin.get("handler").getAsString().equals(FurnaceRecipeHandler.class.getName())).findFirst().get();
            if (furnace.describe().getAsJsonArray("registrations").size() != 2)
                throw new AssertionError("Dual registration provenance disappeared");
            if (!furnace.describe().get("registrationEquivalence").getAsString().equals("shared-native-bindings"))
                throw new AssertionError("Native furnace registrations lost their shared registry binding");
            for (Recipes.Handler entry : found) if (entry.adapter == Recipes.Adapter.SHAPED || entry.adapter == Recipes.Adapter.SHAPELESS)
                if (!entry.describe().get("registrationEquivalence").getAsString().equals("shared-native-bindings"))
                    throw new AssertionError("Native crafting registrations lost their shared manager/list binding");
            List<Recipes.Handler> unknown = Recipes.handlers(Collections.singletonList(new UnknownTemplate()), Collections.emptyList(),
                    Collections.singletonList(new UnknownTemplate()), Collections.emptyList());
            if (!unknown.get(0).describe().get("registrationEquivalence").getAsString().equals("unverified"))
                throw new AssertionError("An unreviewed family's class/key was promoted to semantic equivalence");
            Recipes.Handler usageOnly = found.stream().filter(h -> h.origin.get("key").getAsString().equals("fixture.usageOnly")).findFirst().get();
            if (usageOnly.supported || !usageOnly.describe().get("reason").getAsString().contains("usage"))
                throw new AssertionError("Unknown usage-only handler claimed support");
            usage.add(usage.get(0));
            if (Recipes.handlers(crafting, serialCrafting, usage, serialUsage).size() != 4)
                throw new AssertionError("Same object registration duplicated identity");
            usage.add(new FurnaceRecipeHandler());
            try {
                Recipes.handlers(crafting, serialCrafting, usage, serialUsage);
                throw new AssertionError("Conflicting instances within one registry accepted");
            } catch (com.github.dcysteine.nesql.exporter.task.Jobs.Fault expected) {
                if (!expected.code.equals("handler_conflict")) throw expected;
            }
            System.out.println("Four NEI registries: union, provenance and usage-only safety passed");
    }

    /** Boxing cannot change a source; mutable registries and foreign equality remain identity-only. */
    private static void scalarBindings() {
        try {
            java.lang.reflect.Constructor<RegistrationBindings> constructor=RegistrationBindings.class.getDeclaredConstructor(Object[].class);
            constructor.setAccessible(true);
            Object[] first={new String("source"),new Boolean(true),new Byte((byte)7),new Short((short)700),
                    new Integer(700),new Long(700),new Character('x'),new Float(1.25f),new Double(1.25d)};
            Object[] second={new String("source"),new Boolean(true),new Byte((byte)7),new Short((short)700),
                    new Integer(700),new Long(700),new Character('x'),new Float(1.25f),new Double(1.25d)};
            RegistrationBindings baseline=constructor.newInstance((Object)first);
            if(!baseline.same(constructor.newInstance((Object)second)))throw new AssertionError("Equal native scalar bindings conflicted after boxing");
            for(Object[] pair:new Object[][]{{0f,-0f},{0d,-0d},{Float.intBitsToFloat(0x7fc00001),Float.intBitsToFloat(0x7fc00002)},
                    {Double.longBitsToDouble(0x7ff8000000000001L),Double.longBitsToDouble(0x7ff8000000000002L)},
                    {Integer.valueOf(7),Long.valueOf(7)}, {new ArrayList<>(),new ArrayList<>()}, {new Object[0],new Object[0]}}) {
                if(constructor.newInstance((Object)new Object[]{pair[0]}).same(constructor.newInstance((Object)new Object[]{pair[1]})))
                    throw new AssertionError("Different native source or scalar bits stayed equivalent: "+pair[0].getClass().getName());
            }
            Object foreign=new Object(){public boolean equals(Object other){throw new AssertionError("Foreign equality callback invoked");}};
            RegistrationBindings owned=constructor.newInstance((Object)new Object[]{foreign});
            if(!owned.same(constructor.newInstance((Object)new Object[]{foreign}))
                    ||owned.same(constructor.newInstance((Object)new Object[]{new Object()})))
                throw new AssertionError("Foreign native anchors lost identity semantics");
        } catch(ReflectiveOperationException failure) {throw new AssertionError(failure);}
    }

    private static final class UnknownTemplate extends TemplateRecipeHandler {
        public String getRecipeName() { return "Unknown semantic source"; }
        public String getGuiTexture() { throw new AssertionError("GUI access during discovery"); }
    }

    private static final class UsageOnly implements IUsageHandler {
        public String getHandlerId() { return "fixture.usageOnly"; }
        public String getRecipeName() { return "Usage only"; }
        public int numRecipes() { throw new AssertionError("Recipe callback during discovery"); }
        public IUsageHandler getUsageHandler(String id, Object... inputs) { throw new AssertionError("Usage callback during discovery"); }
        public void drawBackground(int index) { throw new AssertionError("GUI callback during discovery"); }
        public void drawForeground(int index) { throw new AssertionError("GUI callback during discovery"); }
        public List<PositionedStack> getIngredientStacks(int index) { return Collections.emptyList(); }
        public List<PositionedStack> getOtherStacks(int index) { return Collections.emptyList(); }
        public PositionedStack getResultStack(int index) { return null; }
        public void onUpdate() { }
        public boolean hasOverlay(GuiContainer gui, Container container, int index) { return false; }
        public codechicken.nei.api.IRecipeOverlayRenderer getOverlayRenderer(GuiContainer gui, int index) { return null; }
        public codechicken.nei.api.IOverlayHandler getOverlayHandler(GuiContainer gui, int index) { return null; }
        public List<String> handleTooltip(GuiRecipe<?> gui, List<String> tip, int index) { return tip; }
        public List<String> handleItemTooltip(GuiRecipe<?> gui, ItemStack stack, List<String> tip, int index) { return tip; }
        public boolean keyTyped(GuiRecipe<?> gui, char key, int code, int index) { return false; }
        public boolean mouseClicked(GuiRecipe<?> gui, int button, int index) { return false; }
    }
}
