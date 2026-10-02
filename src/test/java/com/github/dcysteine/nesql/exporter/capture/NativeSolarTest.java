package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.source.Identity;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonObject;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import java.lang.reflect.*;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Isolated real AdvancedSolarPanel registry and machine methods; no world or energy network. */
final class NativeSolarTest {
    static void run() throws Exception {
        TemplateRecipeHandler handler = (TemplateRecipeHandler) type("advsolar.client.nei.MTRecipeHandler").newInstance();
        require(Recipes.adapter(handler) != null, "Molecular transformer has no production adapter");
        Field registry = type("advsolar.utils.MTRecipeManager").getField("transformerRecipes");
        Object saved = registry.get(null);
        try {
            ItemStack input = new ItemStack(Items.milk_bucket, 3, 32767);
            input.setTagInfo("ignored", new net.minecraft.nbt.NBTTagInt(7));
            ItemStack output = new ItemStack(Items.diamond, 2);
            output.setTagInfo("result", new net.minecraft.nbt.NBTTagString("preserved"));
            Object first = recipe(input, output, Integer.MAX_VALUE);
            Object later = recipe(new ItemStack(Items.milk_bucket, 1, 32767), new ItemStack(Items.gold_ingot), 1);
            List<Object> records = Arrays.asList(first, later);
            registry.set(null, new ArrayList<>(records));
            RegistryRecipes cursor = adapter(handler, records);
            RecipeRow row = row(input, output);
            require(cursor.size() == 2 && cursor.capture(0, row) && !cursor.capture(1, row(input, output)), "First native identity must block later quantity/output variants");
            JsonObject choice = row.inputs.get(0).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject();
            require(choice.get("amount").getAsInt() == 3 && choice.getAsJsonObject("rule").get("nbt").getAsBoolean()
                    && !choice.getAsJsonObject("rule").get("meta").getAsBoolean() && choice.getAsJsonArray("returns").size() == 0,
                    "Native counts, literal 32767, ignored NBT or no-container consumption changed");
            require(row.record.get("energy").isJsonNull() && row.record.get("duration").isJsonNull()
                    && row.properties.getAsJsonObject("advancedsolar:energyPerOperation").getAsJsonObject("value").get("value").getAsInt() == Integer.MAX_VALUE,
                    "Total EU became a per-tick rate or fixed duration");
            require(row.outputs.get(0).getAsJsonObject().get("amount").getAsInt() == 2, "Output count changed");
            Class<?> machineType = type("advsolar.common.tiles.TileEntityMolecularTransformer");
            Object machine = machineType.newInstance();
            ItemStack[] slots = (ItemStack[]) field(machine, "workSlots");
            for (int meta : new int[] {0, 32767}) for (int count : new int[] {1, 3, 5}) {
                slots[0] = new ItemStack(Items.milk_bucket, count, meta);
                boolean matches = (Boolean) invoke(machineType, machine, "canSmelt", new Class<?>[0]);
                require(matches == (meta == 32767 && count >= 3), "Native metadata/quantity predicate differs");
                if (matches) require(machineType.getField("lastRecipeNumber").getInt(machine) == 0, "Native manager chose a later duplicate");
            }
            slots[1] = new ItemStack(Items.gold_ingot);
            require(!(Boolean) invoke(machineType, machine, "canSmelt", new Class<?>[0]), "Output rejection unexpectedly falls through to later recipe");
            slots[1] = null;
            require((Boolean) invoke(machineType, machine, "canSmelt", new Class<?>[0]), "Owned machine cannot prepare recipe");
            machineType.getField("doWork").setBoolean(machine, true);
            require((Integer) invoke(machineType, machine, "gainFuel", new Class<?>[] {int.class}, Integer.MAX_VALUE - 1) == 0
                    && slots[1] == null, "Native craft finished before total energy was supplied");
            require((Integer) invoke(machineType, machine, "gainFuel", new Class<?>[] {int.class}, 2) == 1
                    && ItemStack.areItemStacksEqual(slots[1], output), "Energy remainder or native output changed");
            ItemStack shownInput = (ItemStack) field(handler.arecipes.get(0), "input");
            ItemStack shownOutput = (ItemStack) field(handler.arecipes.get(0), "output");
            shownInput.stackSize = 99; shownOutput.setTagCompound(null);
            require(input.stackSize == 3 && input.getTagCompound().getInteger("ignored") == 7 && output.hasTagCompound(), "Display mutates native recipe");

            Object zero = recipe(new ItemStack(Items.paper, 0), new ItemStack(Items.gold_ingot), 0);
            RegistryRecipes free = adapter(handler, Collections.singletonList(zero));
            RecipeRow retained = row(new ItemStack(Items.paper), new ItemStack(Items.gold_ingot)); free.capture(0, retained);
            JsonObject kept = retained.inputs.get(0).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject();
            require(kept.get("amount").getAsInt() == 1 && kept.getAsJsonObject("consume").get("kind").getAsString().equals("keep"), "Zero-count template lost its required present stack");
            registry.set(null, new ArrayList<>(Collections.singletonList(zero)));
            machine = machineType.newInstance(); slots = (ItemStack[]) field(machine, "workSlots"); slots[0] = new ItemStack(Items.paper);
            require((Boolean) invoke(machineType, machine, "canSmelt", new Class<?>[0]), "Native zero-cost recipe rejected");
            machineType.getField("doWork").setBoolean(machine, true);
            require((Integer) invoke(machineType, machine, "gainFuel", new Class<?>[] {int.class}, 0) == 0 && slots[1] != null, "Native zero-energy operation stalled");
            reject(() -> adapter(handler, Collections.singletonList(new Object())));
            for (Object invalid : Arrays.asList(recipe(new ItemStack(Items.paper, -1), output, 1),
                    recipe(new ItemStack(Items.paper, 65), output, 1), recipe(input, output, -1)))
                reject(() -> adapter(handler, Collections.singletonList(invalid)).capture(0, row(input, output)));
            Class<?> adapter = type("com.github.dcysteine.nesql.exporter.capture.SolarRecipes");
            Field ticks = handler.getClass().getField("ticks"); ticks.setInt(handler, 63);
            try { invoke(adapter, null, "scene", new Class<?>[] {TemplateRecipeHandler.class, Runnable.class}, handler, (Runnable) () -> {
                require((Integer) field(handler, "ticks") == 0, "Native progress was baked into background"); throw new IllegalStateException("fixture");
            }); throw new AssertionError("Drawing exception swallowed"); }
            catch (IllegalStateException expected) { require(expected.getMessage().equals("fixture"), "Wrong drawing error"); }
            require(ticks.getInt(handler) == 63, "Native clock was not restored after failure");
            System.out.println("Native molecular transformer: literal metadata, ordered blocking, zero/large energy, quantities, owned views and clock restoration passed");
        } finally { registry.set(null, saved); }
    }
    private static Object recipe(ItemStack input, ItemStack output, int energy) throws Exception {
        Class<?> type = type("advsolar.utils.MTRecipeRecord"); Object record = type.newInstance();
        type.getField("inputStack").set(record, input); type.getField("outputStack").set(record, output); type.getField("energyPerOperation").setInt(record, energy);
        return record;
    }
    private static RegistryRecipes adapter(TemplateRecipeHandler handler, List<?> records) {
        return new SolarRecipes(handler, records);
    }
    @SuppressWarnings("unchecked")
    private static RecipeRow row(ItemStack... stacks) {
        Facts facts = new Facts("en_US"); Set<String> known = (Set<String>) field(facts, "items");
        for (ItemStack stack : stacks) known.add(Identity.item(net.minecraft.item.Item.itemRegistry.getNameForObject(stack.getItem()),
                Items.feather.getDamage(stack), TypedNbt.encode(stack.getTagCompound())));
        return new RecipeRow(facts, object("owner", "fixture", "handler", "solar", "key", "native"), "fixture", 0);
    }
    private static void reject(Runnable action) {
        try { action.run(); } catch (Jobs.Fault failure) { require(failure.code.equals("recipe_unsupported"), "Wrong rejection: " + failure); return; }
        throw new AssertionError("Invalid native recipe accepted");
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
