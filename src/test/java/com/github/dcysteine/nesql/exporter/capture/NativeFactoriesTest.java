package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.source.Chance;
import com.github.dcysteine.nesql.exporter.source.Identity;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import java.lang.reflect.*;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Selected real-jar conformance cases, sharing the existing nativeMachinesTest bootstrap. */
final class NativeFactoriesTest {
    static void run(String family) throws Exception {
        if (!family.equals("forestry")) throw new IllegalArgumentException("Unknown native test family: " + family);
        forestry();
    }
    private static void forestry() throws Exception {
        String prefix = "forestry.factory.";
        TemplateRecipeHandler centrifuge = (TemplateRecipeHandler) Class.forName(prefix + "recipes.nei.NEIHandlerCentrifuge").newInstance();
        require(Recipes.adapter(centrifuge) != null, "Forestry centrifuge has no native registry adapter");
        ItemStack input = new ItemStack(Items.apple, 17);
        input.setTagInfo("owner", new net.minecraft.nbt.NBTTagString("required"));
        Map<ItemStack, Float> products = new LinkedHashMap<>();
        for (int i = 0; i < 11; i++) products.put(new ItemStack(Items.paper, i + 1, i), i == 0 ? 0.0001234f : 0.5f);
        products.put(new ItemStack(Items.paper), 1f); // Independent roll of the same item; only this one fits the native UI.
        Object source = Class.forName(prefix + "recipes.CentrifugeRecipe").getConstructor(int.class, ItemStack.class, Map.class)
                .newInstance(23, input, products);
        Method matches = Class.forName("forestry.core.utils.ItemStackUtil").getMethod("isCraftingEquivalent", ItemStack.class, ItemStack.class);
        require(!(Boolean) matches.invoke(null, input, new ItemStack(Items.apple)), "Native tagged input unexpectedly accepts missing tags");
        RecipeRow row = row(input, products.keySet());
        capture(centrifuge, source, row);
        require(row.inputs.get(0).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject().get("amount").getAsString().equals("1"),
                "Centrifuge consumed display quantity instead of one native input");
        require(!row.inputs.get(0).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject().getAsJsonObject("rule").get("nbt").getAsBoolean(), "Required Forestry NBT was lost");
        require(row.outputs.size() == 12, "Native products beyond nine UI slots or duplicate rolls were lost");
        String rare = Identity.item("minecraft:paper", 0, null);
        boolean rareFound = false;
        for (com.google.gson.JsonElement output : row.outputs) if (output.getAsJsonObject().get("id").getAsString().equals(rare)
                && !output.getAsJsonObject().get("chance").equals(Chance.decimal(1, 1))) {
            rareFound = true;
            require(output.getAsJsonObject().get("chance").equals(Chance.decimal(0.0001234f, 1)), "Native float probability was rounded to GT's 1/10000 grid");
            for (com.google.gson.JsonElement element : row.elements) if (element.getAsJsonObject().get("direction").getAsString().equals("output"))
                require(!element.getAsJsonObject().get("slot").equals(output.getAsJsonObject().get("slot")),
                        "Invisible rare roll took the guaranteed duplicate's native display slot");
        }
        require(rareFound, "Rare native product was lost");
        require(row.record.get("duration").getAsString().equals("23") && row.record.get("energy").isJsonNull()
                && row.properties.has("forestry:energyRF"), "Native RF or processing time was misrepresented as EU");
        List<Map.Entry<ItemStack, Float>> reversed = new ArrayList<>(products.entrySet()); Collections.reverse(reversed);
        Map<ItemStack, Float> reordered = new LinkedHashMap<>();
        for (Map.Entry<ItemStack, Float> entry : reversed) reordered.put(entry.getKey(), entry.getValue());
        Object sameRecipe = Class.forName(prefix + "recipes.CentrifugeRecipe").getConstructor(int.class, ItemStack.class, Map.class).newInstance(23, input, reordered);
        RecipeRow repeat = row(input, products.keySet()); capture(centrifuge, sameRecipe, repeat);
        require(row.outputs.equals(repeat.outputs), "Unordered native product maps changed recipe identity");
        centrifuge.arecipes.get(0).getOtherStacks().get(0).items[0].stackSize = 99;
        require(input.stackSize == 17, "Native layout mutated the recipe input");
        for (ItemStack originalOutput : products.keySet()) require(originalOutput.stackSize == originalOutput.getItemDamage() + 1,
                "Native layout mutated the recipe product registry");
        Object unknown = Proxy.newProxyInstance(source.getClass().getClassLoader(), new Class<?>[] {forestry.api.recipes.ICentrifugeRecipe.class},
                (proxy, method, args) -> { throw new AssertionError("Unknown implementation must not execute"); });
        try { capture(centrifuge, unknown, row(input, products.keySet())); throw new AssertionError("Unknown recipe implementation accepted"); }
        catch (Jobs.Fault expected) { require(expected.code.equals("recipe_unsupported"), "Wrong unknown recipe error"); }

        TemplateRecipeHandler still = (TemplateRecipeHandler) Class.forName(prefix + "recipes.nei.NEIHandlerStill").newInstance();
        require(Recipes.adapter(still) != null, "Forestry still has no native registry adapter");
        FluidStack water = new FluidStack(FluidRegistry.WATER, 11), lava = new FluidStack(FluidRegistry.LAVA, 3);
        water.tag = new net.minecraft.nbt.NBTTagCompound(); water.tag.setString("batch", "required");
        Object recipe = Class.forName(prefix + "recipes.StillRecipe").getConstructor(int.class, FluidStack.class, FluidStack.class).newInstance(7, water, lava);
        Facts facts = new Facts("en_US");
        RecipeRow distilled = new RecipeRow(facts, object("owner", "Forestry", "handler", "native", "key", "still"), "category_test", 0);
        capture(still, recipe, distilled);
        require(distilled.inputs.get(0).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject().get("amount").getAsString().equals("77")
                && distilled.outputs.get(0).getAsJsonObject().get("amount").getAsString().equals("21"), "Still did not multiply native fluid units by cycles");
        require(distilled.record.get("duration").getAsString().equals("7"), "Still native time changed");
        require(water.amount == 11 && lava.amount == 3 && water.tag.getString("batch").equals("required"), "Still mutated native fluid stacks");
        Object overflow = Class.forName(prefix + "recipes.StillRecipe").getConstructor(int.class, FluidStack.class, FluidStack.class).newInstance(Integer.MAX_VALUE, water, lava);
        try { capture(still, overflow, distilled); throw new AssertionError("Overflowing native batch accepted"); }
        catch (Jobs.Fault expected) { require(expected.code.equals("recipe_unsupported"), "Wrong native overflow error"); }
        System.out.println("Native Forestry: unit consumption, conditional NBT, exact probability, hidden outputs, cycle-scaled fluids, RF units, source ownership and unknown overrides passed");
    }
    private static void capture(TemplateRecipeHandler handler, Object source, RecipeRow row) throws Exception {
        Method method = Class.forName("com.github.dcysteine.nesql.exporter.capture.ForestryRecipes")
                .getDeclaredMethod("capture", TemplateRecipeHandler.class, Object.class, RecipeRow.class);
        method.setAccessible(true);
        try { method.invoke(null, handler, source, row); }
        catch (InvocationTargetException error) { if (error.getCause() instanceof Error) throw (Error) error.getCause(); throw (Exception) error.getCause(); }
    }
    @SuppressWarnings("unchecked")
    private static RecipeRow row(ItemStack input, Collection<ItemStack> outputs) {
        Facts facts = new Facts("en_US");
        Set<String> known = (Set<String>) MagicApi.field(facts, "items");
        List<ItemStack> items = new ArrayList<>(outputs); items.add(input);
        for (ItemStack stack : items) known.add(Identity.item(Item.itemRegistry.getNameForObject(stack.getItem()), stack.getItemDamage(), TypedNbt.encode(stack.getTagCompound())));
        return new RecipeRow(facts, object("owner", "Forestry", "handler", "native", "key", "centrifuge"), "category_test", 0);
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
