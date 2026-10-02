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
        casting();
        drying();
        System.out.println("Native TConstruct: alloy stoichiometry, maximum integer batches, fluid NBT, one-item melting, exact metadata, temperature and owned display passed");
    }
    private static void drying() throws Exception {
        Class<?> registry = Class.forName("tconstruct.library.crafting.DryingRackRecipes");
        Class<?> type = Class.forName("tconstruct.library.crafting.DryingRackRecipes$DryingRecipe");
        Constructor<?> constructor = type.getDeclaredConstructor(ItemStack.class, int.class, ItemStack.class);
        constructor.setAccessible(true);
        Method matches = type.getMethod("matches", ItemStack.class);
        ItemStack input = new ItemStack(Items.water_bucket, 1, 32767), output = new ItemStack(Items.diamond, 3);
        input.setTagInfo("owner", new net.minecraft.nbt.NBTTagString("required"));
        Object recipe = constructor.newInstance(input, 123, output);
        ItemStack offered = input.copy(); offered.setTagInfo("frypanKill", new net.minecraft.nbt.NBTTagByte((byte) 1));
        require((Boolean) matches.invoke(recipe, offered) && offered.getTagCompound().hasKey("frypanKill"),
                "Native drying should strip only frypanKill on a copy");
        offered.setTagInfo("extra", new net.minecraft.nbt.NBTTagInt(1));
        require(!(Boolean) matches.invoke(recipe, offered), "Native drying unexpectedly accepts other extra NBT");
        offered = input.copy(); offered.stackSize = 2;
        require(!(Boolean) matches.invoke(recipe, offered), "Native drying ignores input quantity");
        offered = input.copy(); offered.setItemDamage(0);
        require(!(Boolean) matches.invoke(recipe, offered), "Native drying treats 32767 as a wildcard");
        ItemStack plain = new ItemStack(Items.paper), empty = plain.copy();
        empty.setTagCompound(new net.minecraft.nbt.NBTTagCompound());
        Object plainRecipe = constructor.newInstance(plain, 40, output);
        Object emptyRecipe = constructor.newInstance(empty, 40, output);
        require((Boolean) matches.invoke(plainRecipe, empty) && !(Boolean) matches.invoke(emptyRecipe, empty),
                "Native drying empty-compound normalization changed");
        TemplateRecipeHandler handler = (TemplateRecipeHandler) Class.forName("tconstruct.plugins.nei.RecipeHandlerDryingRack").newInstance();
        require(Recipes.adapter(handler) != null, "Drying rack has no native registry adapter");
        Field recipes = registry.getField("recipes"); Object previous = recipes.get(null);
        ArrayList<Object> entries = new ArrayList<>(); entries.add(recipe); entries.add(plainRecipe); entries.add(emptyRecipe);
        recipes.set(null, entries);
        try {
            RecipeRow row = row(input, Collections.singletonList(output)); capture("TinkerRecipes", handler, recipe, row);
            com.google.gson.JsonObject choice = row.inputs.get(0).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject();
            require(choice.getAsJsonObject("rule").get("kind").getAsString().equals("without_tags")
                    && choice.getAsJsonObject("rule").getAsJsonArray("keys").toString().equals("[\"frypanKill\"]"), "Drying widened NBT matching");
            require(choice.get("amount").getAsString().equals("1") && choice.getAsJsonArray("returns").size() == 0
                    && choice.getAsJsonObject("consume").get("kind").getAsString().equals("consume"), "Drying invented container returns");
            require(choice.get("id").getAsString().equals(Identity.item("minecraft:water_bucket", 32767, TypedNbt.encode(input.getTagCompound())))
                    && row.record.get("duration").getAsString().equals("123")
                    && row.outputs.get(0).getAsJsonObject().get("amount").getAsString().equals("3"), "Drying changed metadata, timing or fixed yield");
            require(handler.arecipes.get(0).getIngredient().relx == 44 && handler.arecipes.get(0).getResult().relx == 98,
                    "Native drying slot positions changed");
            handler.arecipes.get(0).getIngredient().items[0].getTagCompound().setString("owner", "mutated");
            handler.arecipes.get(0).getResult().items[0].stackSize = 99;
            require(output.stackSize == 3 && input.getTagCompound().getString("owner").equals("required"), "Drying display mutated registry facts");
            ItemStack oversized = input.copy(); oversized.stackSize = 2;
            ItemStack stripped = input.copy(); stripped.setTagInfo("frypanKill", new net.minecraft.nbt.NBTTagByte((byte) 0));
            for (Object impossible : Arrays.asList(emptyRecipe, constructor.newInstance(oversized, 40, output), constructor.newInstance(stripped, 40, output),
                    constructor.newInstance(input, 0, output), constructor.newInstance(input, 1, plain))) {
                entries.add(impossible);
                require(!TinkerRecipes.capture(handler, impossible, row(input, Collections.singletonList(output))),
                        "Drying exported an unreachable or shadowed recipe");
            }
            // Even a zero-time first match blocks later recipes; never search for the first usable match.
            entries.clear(); entries.add(constructor.newInstance(plain, 0, output)); entries.add(plainRecipe);
            require(!TinkerRecipes.capture(handler, plainRecipe, row(plain, Collections.singletonList(output))),
                    "Drying bypassed the native first-match blocker");
        } finally { recipes.set(null, previous); }
        System.out.println("Native drying: selective NBT removal, exact count/meta, unreachable templates, precedence, duration and owned display passed");
    }
    private static void casting() throws Exception {
        Class<?> recipeType = Class.forName("tconstruct.library.crafting.CastingRecipe");
        Constructor<?> constructor = recipeType.getConstructor(ItemStack.class, FluidStack.class, ItemStack.class, boolean.class,
                int.class, Class.forName("tconstruct.library.client.FluidRenderProperties"), boolean.class);
        ItemStack cast = new ItemStack(Items.paper); cast.setTagInfo("owner", new net.minecraft.nbt.NBTTagString("required"));
        ItemStack result = new ItemStack(Items.diamond, 3);
        FluidStack fluid = new FluidStack(FluidRegistry.LAVA, 144);
        for (String kind : new String[] {"Table", "Basin"}) {
            TemplateRecipeHandler handler = (TemplateRecipeHandler) Class.forName("tconstruct.plugins.nei.RecipeHandlerCasting" + kind).newInstance();
            require(Recipes.adapter(handler) != null, "TConstruct casting " + kind + " has no native adapter");
            Object recipe = constructor.newInstance(result, fluid, cast, false, 80, null, false);
            require(!(Boolean) recipeType.getMethod("matches", FluidStack.class, ItemStack.class).invoke(recipe, fluid, new ItemStack(Items.paper)), "Native casting lost required NBT");
            RecipeRow row = row(cast, Collections.singletonList(result)); capture("TinkerRecipes", handler, recipe, row);
            com.google.gson.JsonObject choice = row.inputs.get(0).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject();
            require(choice.getAsJsonObject("rule").get("kind").getAsString().equals("exact")
                    && choice.getAsJsonObject("consume").get("kind").getAsString().equals("keep"), "Casting changed exact reusable mould semantics");
            require(row.record.get("duration").getAsString().equals("80") && row.outputs.get(0).getAsJsonObject().get("amount").getAsString().equals("3"), "Casting lost cooling time/result count");
            handler.arecipes.get(0).getResult().items[0].stackSize = 99;
            require(result.stackSize == 3 && fluid.amount == 144 && cast.stackSize == 1, "Casting mutated native recipes");
        }
        Item pattern = (Item) Class.forName("tconstruct.smeltery.items.MetalPattern").getConstructor(String.class, String.class).newInstance("cast", "test");
        Item.itemRegistry.addObject(30001, "nesqltest:cast", pattern);
        ItemStack patternStack = new ItemStack(pattern);
        Object recipe = constructor.newInstance(patternStack, fluid, cast, false, 80, null, false);
        Class<?> eventType = Class.forName("tconstruct.library.event.SmelteryCastedEvent$CastingTable");
        cpw.mods.fml.common.eventhandler.Event event = (cpw.mods.fml.common.eventhandler.Event) eventType
                .getConstructor(recipeType, ItemStack.class).newInstance(recipe, patternStack.copy());
        Object target = Class.forName("iguanaman.iguanatweakstconstruct.tweaks.handlers.CastHandler").newInstance();
        cpw.mods.fml.common.eventhandler.ASMEventHandler listener = new cpw.mods.fml.common.eventhandler.ASMEventHandler(target,
                target.getClass().getMethod("onCasted", eventType), null);
        int bus = (Integer) MagicApi.field(cpw.mods.fml.common.eventhandler.EventBus.class, net.minecraftforge.common.MinecraftForge.EVENT_BUS, "busID");
        event.getListenerList().register(bus, cpw.mods.fml.common.eventhandler.EventPriority.NORMAL, listener);
        try {
            TemplateRecipeHandler handler = (TemplateRecipeHandler) Class.forName("tconstruct.plugins.nei.RecipeHandlerCastingTable").newInstance();
            RecipeRow row = row(cast, Collections.singletonList(patternStack)); capture("TinkerRecipes", handler, recipe, row);
            require(row.inputs.get(0).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject().getAsJsonObject("consume")
                    .get("kind").getAsString().equals("consume"), "Registered Iguana cast-consumption event was ignored");
            require(!(Boolean) MagicApi.field(recipe, "consumeCast"), "Casting event mutated the original recipe");
            final int[] invoked = {0};
            cpw.mods.fml.common.eventhandler.IEventListener unknown = ignored -> invoked[0]++;
            event.getListenerList().register(bus, cpw.mods.fml.common.eventhandler.EventPriority.LOW, unknown);
            try {
                Method dispatch = Class.forName("com.github.dcysteine.nesql.exporter.capture.TinkerCasting")
                        .getDeclaredMethod("dispatch", cpw.mods.fml.common.eventhandler.Event.class); dispatch.setAccessible(true);
                try { dispatch.invoke(null, event); throw new AssertionError("Unknown casting listener was executed"); }
                catch (InvocationTargetException expected) { require(expected.getCause() instanceof Jobs.Fault, "Wrong unknown-listener failure"); }
                require(invoked[0] == 0 && !(Boolean) MagicApi.field(event, "consumeCast"),
                        "Casting callbacks ran before the entire listener snapshot was validated");
            } finally { cpw.mods.fml.common.eventhandler.ListenerList.unregisterAll(bus, unknown); }
        } finally { cpw.mods.fml.common.eventhandler.ListenerList.unregisterAll(bus, listener); }
        TemplateRecipeHandler table = (TemplateRecipeHandler) Class.forName("tconstruct.plugins.nei.RecipeHandlerCastingTable").newInstance();
        ItemStack wildcardCast = cast.copy(); wildcardCast.setItemDamage(32767); wildcardCast.stackSize = 8;
        Object wildcard = constructor.newInstance(result, fluid, wildcardCast, true, 80, null, false);
        require((Boolean) recipeType.getMethod("matches", FluidStack.class, ItemStack.class).invoke(wildcard, fluid, new ItemStack(Items.paper)),
                "Native wildcard cast should ignore both source count and tags");
        RecipeRow expanded = row(cast, Collections.singletonList(result)); capture("TinkerRecipes", table, wildcard, expanded);
        require(expanded.inputs.get(0).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject().getAsJsonObject("rule")
                .get("nbt").getAsBoolean(), "Wildcard casting retained an invalid NBT requirement");
        System.out.println("Native casting: table/basin, exact and wildcard moulds, copied output, actual Iguana consumption callback, unknown-listener refusal passed");
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
        require(row.inputs.get(0).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject().getAsJsonObject("rule").get("kind").getAsString().equals("exact"),
                "Required Forestry NBT must use the contract's exact rule, not an empty wildcard");
        require(row.outputs.size() == 12, "Native products beyond nine UI slots or duplicate rolls were lost");
        String rare = Identity.item("minecraft:paper", 0, null);
        boolean rareFound = false;
        for (com.google.gson.JsonElement output : row.outputs) if (output.getAsJsonObject().get("id").getAsString().equals(rare)
                && !output.getAsJsonObject().get("chance").equals(Chance.decimal(1, 1))) {
            rareFound = true;
            require(output.getAsJsonObject().get("chance").equals(Chance.of(2071, 16777216)), "Native nextFloat probability differs from the actual 24-bit sample space");
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
