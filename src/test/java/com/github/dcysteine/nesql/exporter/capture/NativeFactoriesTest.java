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
        if (family.equals("tconstruct")) { tconstruct(); return; }
        if (!family.equals("forestry")) throw new IllegalArgumentException("Unknown native test family: " + family);
        forestry();
    }
    private static void tconstruct() throws Exception {
        Class<?> smeltery = Class.forName("tconstruct.library.crafting.Smeltery");
        TemplateRecipeHandler alloying = (TemplateRecipeHandler) Class.forName("tconstruct.plugins.nei.RecipeHandlerAlloying").newInstance();
        require(Recipes.adapter(alloying) != null, "TConstruct alloying has no native registry adapter");
        FluidStack water = new FluidStack(FluidRegistry.WATER, 3), lava = new FluidStack(FluidRegistry.LAVA, 5);
        water.tag = new net.minecraft.nbt.NBTTagCompound(); water.tag.setString("grade", "pure");
        FluidStack output = new FluidStack(FluidRegistry.WATER, 7);
        Class<?> alloy = Class.forName("tconstruct.library.crafting.AlloyMix");
        Object recipe = alloy.getConstructor(FluidStack.class, List.class).newInstance(output, Arrays.asList(water, lava));
        List<FluidStack> supply = new ArrayList<>();
        FluidStack scaledWater = water.copy(); scaledWater.amount = 10;
        supply.add(scaledWater); supply.add(new FluidStack(FluidRegistry.LAVA, 16));
        FluidStack mixed = (FluidStack) alloy.getMethod("mix", List.class).invoke(recipe, supply);
        require(mixed.amount == 21 && supply.get(0).amount == 1 && supply.get(1).amount == 1, "Native alloy does not use maximum integer batches");
        List<FluidStack> wrongTags = new ArrayList<>(Arrays.asList(new FluidStack(FluidRegistry.WATER, 3), lava.copy()));
        require(alloy.getMethod("mix", List.class).invoke(recipe, wrongTags) == null, "Native alloy unexpectedly ignores fluid NBT");
        RecipeRow row = row(new ItemStack(Items.paper), Collections.emptyList());
        capture("TinkerRecipes", alloying, recipe, row);
        require(row.inputs.size() == 2 && row.outputs.get(0).getAsJsonObject().get("amount").getAsString().equals("7"), "Alloy stoichiometry changed");
        require(row.inputs.get(0).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject().get("amount").getAsString().equals("3"), "Alloy input ratio changed");
        String inputId = row.inputs.get(0).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject().get("id").getAsString();
        require(inputId.equals(Identity.fluid("water", TypedNbt.encode(water.tag))) && row.properties.has("tconstruct:batch"), "Alloy NBT or batch policy was lost");
        Object tank = ((List<?>) MagicApi.invoke(alloying.arecipes.get(0).getClass(), alloying.arecipes.get(0), "getFluidTanks", new Class<?>[0])).get(0);
        ((FluidStack) MagicApi.field(tank, "fluid")).amount = 99;
        require(output.amount == 7 && water.amount == 3 && lava.amount == 5, "Alloy display mutated the source registry");
        Object duplicate = alloy.getConstructor(FluidStack.class, List.class).newInstance(output, Arrays.asList(water, water.copy()));
        try { capture("TinkerRecipes", alloying, duplicate, row(new ItemStack(Items.paper), Collections.emptyList()));
            throw new AssertionError("Unmatchable repeated fluid accepted as an ordinary alloy"); }
        catch (Jobs.Fault expected) { require(expected.code.equals("recipe_unsupported"), "Wrong repeated-fluid failure"); }
        try { capture("TinkerRecipes", alloying, new Object(), row(new ItemStack(Items.paper), Collections.emptyList()));
            throw new AssertionError("Unknown alloy implementation accepted"); }
        catch (Jobs.Fault expected) { require(expected.code.equals("recipe_unsupported"), "Wrong unknown-alloy failure"); }

        TemplateRecipeHandler melting = (TemplateRecipeHandler) Class.forName("tconstruct.plugins.nei.RecipeHandlerMelting").newInstance();
        require(Recipes.adapter(melting) != null, "TConstruct melting has no native registry adapter");
        ItemStack input = new ItemStack(Items.paper, 9, 2);
        FluidStack result = new FluidStack(FluidRegistry.WATER, 144);
        smeltery.getMethod("addMelting", ItemStack.class, net.minecraft.block.Block.class, int.class, int.class, FluidStack.class)
                .invoke(null, input, net.minecraft.init.Blocks.stone, 0, 900, result);
        ItemStack tagged = input.copy(); tagged.setTagInfo("ignored", new net.minecraft.nbt.NBTTagInt(1));
        require(((FluidStack) smeltery.getMethod("getSmelteryResult", ItemStack.class).invoke(null, tagged)).amount == 144, "Native melting unexpectedly requires NBT");
        RecipeRow melted = row(input, Collections.emptyList()); capture("TinkerRecipes", melting, input, melted);
        com.google.gson.JsonObject choice = melted.inputs.get(0).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject();
        require(choice.get("amount").getAsString().equals("1") && choice.getAsJsonObject("rule").get("nbt").getAsBoolean()
                && !choice.getAsJsonObject("rule").get("meta").getAsBoolean(), "Melting must consume one exact-metadata item and ignore NBT");
        require(melted.outputs.get(0).getAsJsonObject().get("amount").getAsString().equals("144") && melted.properties.has("tconstruct:temperature"), "Melting amount/temperature was lost");
        require(input.stackSize == 9 && result.amount == 144, "Melting changed native registry objects");
        ItemStack exact = new ItemStack(Items.book, 1, 32767);
        smeltery.getMethod("addMelting", ItemStack.class, net.minecraft.block.Block.class, int.class, int.class, FluidStack.class)
                .invoke(null, exact, net.minecraft.init.Blocks.stone, 0, 800, result);
        require(smeltery.getMethod("getSmelteryResult", ItemStack.class).invoke(null, new ItemStack(Items.book)) == null,
                "Mantle unexpectedly expands wildcard keys");
        RecipeRow unusual = row(exact, Collections.emptyList()); capture("TinkerRecipes", melting, exact, unusual);
        com.google.gson.JsonObject exactChoice = unusual.inputs.get(0).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject();
        require(exactChoice.get("id").getAsString().equals(Identity.item("minecraft:book", 32767, null))
                && !exactChoice.getAsJsonObject("rule").get("meta").getAsBoolean(), "NEI wildcard expansion broadened an exact Mantle key");
        System.out.println("Native TConstruct: alloy stoichiometry, maximum integer batches, fluid NBT, one-item melting, exact metadata, temperature and owned display passed");
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
        capture("ForestryRecipes", handler, source, row);
    }
    private static void capture(String adapter, TemplateRecipeHandler handler, Object source, RecipeRow row) throws Exception {
        Method method = Class.forName("com.github.dcysteine.nesql.exporter.capture." + adapter)
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
