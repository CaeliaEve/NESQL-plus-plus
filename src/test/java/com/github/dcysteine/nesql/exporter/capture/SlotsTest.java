package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.PositionedStack;
import com.github.dcysteine.nesql.exporter.source.Identity;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import cpw.mods.fml.relauncher.ReflectionHelper;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static com.github.dcysteine.nesql.exporter.source.Json.object;

/** Native slot order disambiguates overlaps; raw alternative inputs can have no base stack. */
final class SlotsTest {
    private SlotsTest() {}

    static void run() {
        ItemStack first = new ItemStack(Items.paper, 3), second = new ItemStack(Items.diamond, 9);
        FluidStack water = new FluidStack(FluidRegistry.WATER, 125);
        List<GtRecipes.Binding> slots = Arrays.asList(
                new GtRecipes.Binding(0, false, false, false, true, 10, 20),
                new GtRecipes.Binding(0, false, false, false, false, 10, 20),
                new GtRecipes.Binding(1, false, false, false, false, 30, 20),
                new GtRecipes.Binding(2, false, false, true, false, 10, 20),
                new GtRecipes.Binding(0, true, false, false, false, 10, 20));
        // The middle slot is empty; three populated output slots overlap exactly.
        List<PositionedStack> displays = Arrays.asList(display(), display(), display());
        java.util.function.Function<GtRecipes.Binding, Object> source = binding -> binding.input ? first
                : binding.fluid ? water : binding.index == 0 ? first : binding.index == 2 ? second : null;
        List<GtRecipes.Placement> projected = GtRecipes.project(slots, source, displays, false);
        require(projected.size() == 3 && projected.get(0).source == first && projected.get(1).source == second
                && projected.get(1).binding.overflow && projected.get(2).source == water, "Overlapping native slots lost their identities or source values");
        require(GtRecipes.project(slots, source, Collections.singletonList(display()), true).get(0).source == first,
                "Input/output directions collided");
        Facts facts = new Facts("en_US");
        Set<String> known = ReflectionHelper.getPrivateValue(Facts.class, facts, "items");
        known.add(Identity.item("minecraft:paper", 0, null)); known.add(Identity.item("minecraft:diamond", 0, null));
        RecipeRow row = new RecipeRow(facts, object("owner", "fixture", "handler", "slots", "key", "overlap"), "fixture", 0);
        for (GtRecipes.Placement placed : projected) {
            if (placed.binding.fluid) row.fluidOutput(placed.display, placed.binding.index, (FluidStack) placed.source);
            else row.itemOutput(placed.display, placed.binding.index, (ItemStack) placed.source, 10000);
        }
        require(row.outputs.get(0).getAsJsonObject().get("amount").getAsString().equals("3")
                && row.outputs.get(1).getAsJsonObject().get("amount").getAsString().equals("9")
                && row.outputs.get(2).getAsJsonObject().get("amount").getAsString().equals("125")
                && row.outputs.get(1).getAsJsonObject().get("slot").getAsInt() == 2
                && row.elements.size() == 3, "Projection replaced source quantities with display quantities");
        reject("slot_missing", () -> GtRecipes.project(slots, source, displays.subList(0, 2), false));
        reject("slot_missing", () -> GtRecipes.project(slots, source, Arrays.asList(display(), display(), display(), display()), false));
        List<PositionedStack> moved = Arrays.asList(display(), new PositionedStack(first, 11, 20, false), display());
        reject("slot_changed", () -> GtRecipes.project(slots, source, moved, false));
        try { row.fluidOutput(display(), 4, new FluidStack(FluidRegistry.WATER, 0)); throw new AssertionError("Zero output was changed to a fixed positive yield"); }
        catch (Jobs.Fault expected) {
            require(expected.code.equals("invalid_amount") && expected.getMessage().contains("slot=4")
                    && expected.getMessage().contains("registry=water") && expected.getMessage().contains("amount=0"), "Output failure lost its source context");
        }

        ItemStack[] choices = {new ItemStack(Items.iron_axe), new ItemStack(Items.diamond_axe)};
        Object[] inputs = GtRecipes.inputs(new ItemStack[0], new ItemStack[][] {choices, null, {new ItemStack(Items.stick, 0)}});
        require(inputs.length == 3 && inputs[1] == null,
                "Alternative-only inputs were indexed through the empty base array");
        ItemStack[] copies = (ItemStack[]) inputs[0];
        require(copies.length == 2 && copies[0] != choices[0] && copies[1].getItem() == Items.diamond_axe,
                "Alternative projection lost copies or choices");
        require(((ItemStack[]) inputs[2])[0].stackSize == 0, "Catalyst input lost its zero quantity");
        copies[0].stackSize = 8;
        require(choices[0].stackSize == 1, "Projection mutated the registered recipe");
        Object[] fallback = GtRecipes.inputs(new ItemStack[] {first, second}, new ItemStack[][] {new ItemStack[0]});
        require(fallback.length == 2 && ((ItemStack) fallback[0]).stackSize == 3 && fallback[1] != second,
                "An empty or absent alternative hid the base source");
        alternatives(facts);
        permutations(facts);
        quantities(facts);
        scans(facts);
        System.out.println("GT slots: ordered overlaps, empty slots, input/output separation, exact output quantities and alternative-only inputs passed");
    }

    private static void scans(Facts facts) {
        net.minecraft.nbt.NBTTagCompound genome = new net.minecraft.nbt.NBTTagCompound();
        genome.setString("testAllele", "fixture.oak");
        net.minecraft.nbt.NBTTagCompound tags = new net.minecraft.nbt.NBTTagCompound();
        tags.setTag("Genome", genome); tags.setBoolean("IsAnalyzed", false);
        ItemStack input = new ItemStack(Items.paper, 12, 7); input.setTagCompound(tags);
        input.setStackDisplayName("Unrelated name to discard on first analysis");
        net.minecraft.nbt.NBTTagCompound original = (net.minecraft.nbt.NBTTagCompound) input.getTagCompound().copy();
        java.util.function.Function<ItemStack, forestry.api.genetics.IIndividual> reader = stack -> {
            net.minecraft.nbt.NBTTagCompound encoded = new net.minecraft.nbt.NBTTagCompound();
            encoded.setTag("Genome", stack.getTagCompound().getCompoundTag("Genome").copy());
            boolean[] analyzed = {stack.getTagCompound().getBoolean("IsAnalyzed")};
            // A strict API boundary double: unknown calls fail; native registry/serialization is checked in game.
            return (forestry.api.genetics.IIndividual) java.lang.reflect.Proxy.newProxyInstance(
                    SlotsTest.class.getClassLoader(), new Class<?>[] {forestry.api.genetics.IIndividual.class}, (proxy, method, args) -> {
                        switch (method.getName()) {
                            case "isAnalyzed": return analyzed[0];
                            case "analyze": boolean changed = !analyzed[0]; analyzed[0] = true; return changed;
                            case "writeToNBT":
                                net.minecraft.nbt.NBTTagCompound target = (net.minecraft.nbt.NBTTagCompound) args[0];
                                target.setTag("Genome", encoded.getTag("Genome").copy()); target.setBoolean("IsAnalyzed", analyzed[0]); return null;
                            default: throw new AssertionError("Unexpected individual API: " + method);
                        }
                    });
        };
        ItemStack analyzed = Scan.analyze(input, reader);
        require(analyzed.stackSize == 12 && Items.feather.getDamage(analyzed) == 7
                && analyzed.getTagCompound().getBoolean("IsAnalyzed") && !analyzed.hasDisplayName(),
                "Scan did not use fresh native serialization and preserve the whole stack");
        analyzed.setStackDisplayName("Keep on already analyzed branch");
        ItemStack copied = Scan.analyze(analyzed, reader);
        require(copied != analyzed && ItemStack.areItemStackTagsEqual(copied, analyzed) && copied.stackSize == 12,
                "Already analyzed input was serialized again or lost its count");
        require(input.stackSize == 12 && original.equals(input.getTagCompound()), "Analysis mutated the offered stack");

        Set<String> known = ReflectionHelper.getPrivateValue(Facts.class, facts, "items");
        for (boolean state : new boolean[] {false, true}) {
            net.minecraft.nbt.NBTTagCompound canonical = new net.minecraft.nbt.NBTTagCompound();
            canonical.setTag("Genome", genome.copy()); canonical.setBoolean("IsAnalyzed", state);
            known.add(Identity.item("minecraft:paper", 7, com.github.dcysteine.nesql.exporter.source.TypedNbt.encode(canonical)));
        }
        RecipeRow base = new RecipeRow(facts, object("owner", "fixture", "handler", "scan", "key", "scan"), "fixture", 3);
        List<RecipeRow> rows = Scan.branches(base, "rootTrees", Arrays.asList(input, input.copy()),
                new FluidStack(FluidRegistry.WATER, 100), display(), display(), display(), reader);
        require(rows.size() == 2, "Scanner lost its analyzed or unanalyzed branch");
        for (int index = 0; index < rows.size(); index++) {
            RecipeRow row = rows.get(index);
            com.google.gson.JsonObject subject = row.inputs.get(0).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject();
            com.google.gson.JsonObject liquid = row.inputs.get(1).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject();
            com.google.gson.JsonObject change = row.outputs.get(0).getAsJsonObject().getAsJsonObject("change");
            require(subject.getAsJsonObject("consume").get("kind").getAsString().equals("stack")
                    && subject.getAsJsonObject("rule").get("analyzed").getAsBoolean() == (index == 1)
                    && liquid.get("amount").getAsString().equals("100")
                    && liquid.getAsJsonObject("consume").get("kind").getAsString().equals(index == 0 ? "consume" : "keep")
                    && row.record.get("duration").getAsString().equals(index == 0 ? "500" : "1")
                    && row.record.get("energy").getAsString().equals(index == 0 ? "2" : "1")
                    && change.getAsJsonArray("samples").size() == 1, "Scan branch costs, deduplication or member semantics changed");
        }
        reject("scan_semantics", () -> Scan.branches(base, "rootTrees", Collections.singletonList(new ItemStack(Items.paper)),
                new FluidStack(FluidRegistry.WATER, 100), display(), display(), display(), reader));
        reject("scan_semantics", () -> Scan.branches(base, "rootTrees", Collections.singletonList(input),
                new FluidStack(FluidRegistry.WATER, 100), display(), display(), display(), stack -> {
                    stack.getTagCompound().getCompoundTag("Genome").setString("testAllele", "invented.default");
                    return reader.apply(stack);
                }));
        require(original.equals(input.getTagCompound()), "Sample normalization changed the shared NEI member");
        System.out.println("Scanner: native API boundary, two analysis branches, whole-stack counts, fresh/preserved NBT, deduplication and missing-genome rejection passed");
    }

    private static void quantities(Facts facts) {
        java.util.Map<Integer, com.google.gson.JsonObject> rules = Amounts.sparge(0, 7, 200, 1000);
        RecipeRow row = new RecipeRow(facts, object("owner", "fixture", "handler", "sparge", "key", "quantities"), "fixture", 0);
        for (int slot = 1; slot < 7; slot++) row.fluidOutput(display(), slot,
                new FluidStack(FluidRegistry.WATER, slot == 2 ? 123 : 0), rules.get(slot));
        require(row.outputs.size() == 6 && row.outputs.get(0).getAsJsonObject().get("amount").isJsonNull()
                && row.outputs.get(1).getAsJsonObject().get("amount").isJsonNull(), "Sparging retained a placeholder or a previously sampled amount");
        require(rules.get(1).getAsJsonArray("after").size() == 5 && rules.get(6).getAsJsonArray("after").size() == 4
                && rules.get(2).get("limit").getAsJsonPrimitive().isString(), "Sparging lost dependency order or exact limits");
        reject("quantity_rule", () -> Amounts.sparge(0, 4, 200, 2));
        reject("quantity_rule", () -> Amounts.sparge(0, 7, 0, 1000));
        require(Amounts.sparge(0, 2, 0, 1).get(1).getAsJsonArray("after").size() == 0, "A remainder without draws was rejected");

        ItemStack[] outputs = new ItemStack[26]; outputs[25] = new ItemStack(Items.paper, 9);
        Set<String> captured = new java.util.HashSet<>();
        reject("slot_missing", () -> GtRecipes.covered(captured, outputs, outputs, 25, false, false, null));
        RecipeRow hidden = new RecipeRow(facts, object("owner", "fixture", "handler", "slots", "key", "hidden"), "fixture", 0);
        GtRecipes.covered(captured, outputs, outputs, 25, false, false,
                (slot, value) -> hidden.itemOutput(null, slot, (ItemStack) value, 10000));
        require(hidden.outputs.size() == 1 && hidden.elements.size() == 0
                && hidden.outputs.get(0).getAsJsonObject().get("slot").getAsInt() == 25
                && hidden.outputs.get(0).getAsJsonObject().get("amount").getAsString().equals("9"),
                "A hidden native output was dropped or assigned fabricated coordinates");
    }

    private static void alternatives(Facts facts) {
        ItemStack[] source = {new ItemStack(Items.paper, 3), new ItemStack(Items.paper, 8), new ItemStack(Items.paper, 0),
                new ItemStack(Items.paper, 2, OreDictionary.WILDCARD_VALUE), new ItemStack(Items.paper, 8)};
        List<RecipeRow.Ingredient> ingredients = GtRecipes.ingredients(source, true,
                item -> new PositionedStack(item, 10, 20, true).items);
        PositionedStack display = new PositionedStack(source, 10, 20, true);
        for (ItemStack item : display.items) item.stackSize = 1; // Native renderRealStackSizes=false.
        GtRecipes.displayed(0, display, ingredients);
        RecipeRow row = new RecipeRow(facts, object("owner", "fixture", "handler", "slots", "key", "alternatives"), "fixture", 0);
        row.itemInput(display, 0, ingredients, false);
        com.google.gson.JsonArray choices = row.inputs.get(0).getAsJsonObject().getAsJsonArray("choices");
        require(choices.size() == 4, "Same-item quantities/consumption were merged or identical choices were retained");
        require(choices.get(0).getAsJsonObject().get("amount").getAsString().equals("3")
                && choices.get(1).getAsJsonObject().get("amount").getAsString().equals("8")
                && choices.get(2).getAsJsonObject().get("amount").getAsString().equals("1")
                && choices.get(2).getAsJsonObject().getAsJsonObject("consume").get("kind").getAsString().equals("keep")
                && choices.get(3).getAsJsonObject().get("amount").getAsString().equals("2")
                && choices.get(3).getAsJsonObject().getAsJsonObject("rule").get("meta").getAsBoolean(),
                "An alternative inherited the first candidate's amount, consumption or wildcard rule");
        require(source[0].stackSize == 3 && source[2].stackSize == 0 && source[3].getItemDamage() == OreDictionary.WILDCARD_VALUE,
                "Alternative capture changed the registered stacks");
        display.items[1] = new ItemStack(Items.diamond);
        reject("slot_changed", () -> GtRecipes.displayed(0, display, ingredients));
        reject("invalid_amount", () -> GtRecipes.ingredients(new ItemStack(Items.paper, -1), false,
                item -> new PositionedStack(item, 0, 0, true).items));
        ItemStack tagged = new ItemStack(Items.paper, 4, OreDictionary.WILDCARD_VALUE);
        net.minecraft.nbt.NBTTagCompound tags = new net.minecraft.nbt.NBTTagCompound(); tags.setLong("owner", Long.MAX_VALUE); tagged.setTagCompound(tags);
        List<RecipeRow.Ingredient> sensitive = GtRecipes.ingredients(tagged, true, item -> new ItemStack[] {new ItemStack(Items.paper)});
        require(sensitive.get(0).item.getTagCompound().getLong("owner") == Long.MAX_VALUE && !sensitive.get(0).display.hasTagCompound(),
                "The display permutation replaced the source NBT predicate");
        GtRecipes.displayed(0, display(), sensitive);
        sensitive.get(0).item.getTagCompound().setLong("owner", 0);
        require(tags.getLong("owner") == Long.MAX_VALUE, "Source NBT was shared with its expanded candidate");
    }

    private static void permutations(Facts facts) {
        List<ItemStack> previous = new java.util.ArrayList<>(codechicken.nei.ItemList.itemMap.get(Items.paper));
        ItemStack a = new ItemStack(Items.paper, 1, 0), b = new ItemStack(Items.paper, 1, 1), c = new ItemStack(Items.paper, 1, 2);
        ItemStack[] source = {new ItemStack(Items.paper, 3, OreDictionary.WILDCARD_VALUE), new ItemStack(Items.paper, 0, 1)};
        try {
            codechicken.nei.ItemList.itemMap.replaceValues(Items.paper, Arrays.asList(a, b, c));
            PositionedStack cached = new PositionedStack(source, 10, 20, true);
            for (ItemStack item : cached.items) item.stackSize = 1;
            // A GT cached display predates the current NEI wildcard ordering.
            codechicken.nei.ItemList.itemMap.replaceValues(Items.paper, Arrays.asList(a, c, b));
            List<RecipeRow.Ingredient> ingredients = GtRecipes.ingredients(source, false,
                    item -> new PositionedStack(item, 10, 20, true).items);
            require(cached.items[1].getItemDamage() != ingredients.get(1).display.getItemDamage(),
                    "Native permutation fixture did not reorder the second alternative");
            GtRecipes.displayed(2, cached, ingredients);
            Set<String> known = ReflectionHelper.getPrivateValue(Facts.class, facts, "items");
            known.add(Identity.item("minecraft:paper", 1, null)); known.add(Identity.item("minecraft:paper", 2, null));
            RecipeRow row = new RecipeRow(facts, object("owner", "fixture", "handler", "slots", "key", "permutations"), "fixture", 0);
            row.itemInput(cached, 2, ingredients, false);
            com.google.gson.JsonArray choices = row.inputs.get(0).getAsJsonObject().getAsJsonArray("choices");
            require(choices.size() == 4 && choices.get(0).getAsJsonObject().get("amount").getAsString().equals("3")
                    && choices.get(3).getAsJsonObject().getAsJsonObject("consume").get("kind").getAsString().equals("keep")
                    && cached.items[1].getItemDamage() == 1 && source[0].stackSize == 3 && source[1].stackSize == 0,
                    "Display reordering changed source semantics or the shared cached display");
            ItemStack[] original = cached.items.clone();
            cached.items[1] = cached.items[0].copy();
            reject("slot_changed", () -> GtRecipes.displayed(2, cached, ingredients));
            cached.items = original.clone();
            cached.items[1] = cached.items[1].copy();
            net.minecraft.nbt.NBTTagCompound tag = new net.minecraft.nbt.NBTTagCompound(); tag.setInteger("owner", 1);
            cached.items[1].setTagCompound(tag);
            try { GtRecipes.displayed(2, cached, ingredients); throw new AssertionError("NBT drift was accepted"); }
            catch (Jobs.Fault expected) {
                require(expected.code.equals("slot_changed") && expected.getMessage().contains("slot=2")
                        && expected.getMessage().contains("minecraft:paper") && expected.getMessage().contains("meta=1")
                        && expected.getMessage().contains("id=item_"), "Candidate mismatch lost its slot and exact item identity");
            }
            cached.items = Arrays.copyOf(original, original.length - 1);
            reject("slot_changed", () -> GtRecipes.displayed(2, cached, ingredients));
            cached.items = original.clone(); cached.items[1] = null;
            reject("slot_changed", () -> GtRecipes.displayed(2, cached, ingredients));
        } finally {
            codechicken.nei.ItemList.itemMap.replaceValues(Items.paper, previous);
        }
        System.out.println("GT alternatives: native cached wildcard reorder, multiplicity, NBT drift, independent quantities and unchanged cache passed");
    }

    private static PositionedStack display() { return new PositionedStack(new ItemStack(Items.paper), 10, 20, false); }
    private static void reject(String code, Runnable action) {
        try { action.run(); throw new AssertionError("Expected " + code); }
        catch (Jobs.Fault expected) { require(expected.code.equals(code), "Wrong projection failure: " + expected.code); }
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
