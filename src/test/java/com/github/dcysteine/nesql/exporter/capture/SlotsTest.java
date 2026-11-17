package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.PositionedStack;
import com.github.dcysteine.nesql.exporter.source.Identity;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import cpw.mods.fml.relauncher.ReflectionHelper;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static com.github.dcysteine.nesql.exporter.source.Json.object;

/** Native ingredient alternatives and quantities, independent of GUI geometry/cache probes. */
final class SlotsTest {
    private SlotsTest() {}

    static void run() {
        ItemStack first = new ItemStack(Items.paper, 3), second = new ItemStack(Items.diamond, 9);
        Facts facts = new Facts("en_US");
        Set<String> known = ReflectionHelper.getPrivateValue(Facts.class, facts, "items");
        known.add(Identity.item("minecraft:paper", 0, null)); known.add(Identity.item("minecraft:diamond", 0, null));
        RecipeRow row = new RecipeRow(facts, object("owner", "fixture", "handler", "slots", "key", "overlap"), "fixture", 0);
        try { row.fluidOutput(4, new FluidStack(FluidRegistry.WATER, 0)); throw new AssertionError("Zero output was changed to a fixed positive yield"); }
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
        quantities(facts);
        scans(facts);
        unregisteredDisplay();
        nativeFailures();
        System.out.println("GT ingredients: exact quantities, alternative-only inputs, symbolic predicates and native scanner branches passed");
    }

    private static void unregisteredDisplay() {
        ItemStack ghost = new ItemStack(new Item() {}, 3, 17);
        ItemStack paper = new ItemStack(Items.paper);
        try {
            GtRecipes.ingredients(ghost, false, ignored -> new ItemStack[] {paper});
            throw new AssertionError("Unification disguised an unregistered recipe source as a valid item");
        } catch (Jobs.Fault failure) {
            require(failure.code.equals("unregistered_item") && failure.getMessage().contains("native source")
                    && failure.getMessage().contains("meta=17"), "Failure did not distinguish the native source from its display expansion");
        }
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
                new FluidStack(FluidRegistry.WATER, 100), reader);
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
                new FluidStack(FluidRegistry.WATER, 100), reader));
        reject("scan_semantics", () -> Scan.branches(base, "rootTrees", Collections.singletonList(input),
                new FluidStack(FluidRegistry.WATER, 100), stack -> {
                    stack.getTagCompound().getCompoundTag("Genome").setString("testAllele", "invented.default");
                    return reader.apply(stack);
                }));
        require(original.equals(input.getTagCompound()), "Sample normalization changed the shared NEI member");
        System.out.println("Scanner: native API boundary, two analysis branches, whole-stack counts, fresh/preserved NBT, deduplication and missing-genome rejection passed");
    }

    private static void quantities(Facts facts) {
        java.util.Map<Integer, com.google.gson.JsonObject> rules = Amounts.sparge(0, 7, 200, 1000);
        RecipeRow row = new RecipeRow(facts, object("owner", "fixture", "handler", "sparge", "key", "quantities"), "fixture", 0);
        for (int slot = 1; slot < 7; slot++) row.fluidOutput(slot,
                new FluidStack(FluidRegistry.WATER, slot == 2 ? 123 : 0), rules.get(slot));
        require(row.outputs.size() == 6 && row.outputs.get(0).getAsJsonObject().get("amount").isJsonNull()
                && row.outputs.get(1).getAsJsonObject().get("amount").isJsonNull(), "Sparging retained a placeholder or a previously sampled amount");
        require(rules.get(1).getAsJsonArray("after").size() == 5 && rules.get(6).getAsJsonArray("after").size() == 4
                && rules.get(2).get("limit").getAsJsonPrimitive().isString(), "Sparging lost dependency order or exact limits");
        reject("quantity_rule", () -> Amounts.sparge(0, 4, 200, 2));
        reject("quantity_rule", () -> Amounts.sparge(0, 7, 0, 1000));
        require(Amounts.sparge(0, 2, 0, 1).get(1).getAsJsonArray("after").size() == 0, "A remainder without draws was rejected");

        // Branch quantity assertions (Extreme Heat Exchanger)
        com.google.gson.JsonObject branchNormal = Amounts.branch("steam_output", "normal", "tRealConsume < threshold", null, 32000);
        com.google.gson.JsonObject branchSuper = Amounts.branch("steam_output", "superheated", "tRealConsume >= threshold", null, 64000);
        require("branch".equals(branchNormal.get("kind").getAsString()) && "normal".equals(branchNormal.get("branch").getAsString()), "Invalid branch structure");
        RecipeRow branchRow = new RecipeRow(facts, object("owner", "fixture", "handler", "heat_exchanger", "key", "branch"), "fixture", 0);
        branchRow.fluidOutput(0, new FluidStack(FluidRegistry.WATER, 0), branchNormal);
        branchRow.fluidOutput(1, new FluidStack(FluidRegistry.WATER, 0), branchSuper);
        require(branchRow.outputs.size() == 2 && branchRow.outputs.get(0).getAsJsonObject().get("amount").isJsonNull()
                && branchRow.outputs.get(1).getAsJsonObject().get("amount").isJsonNull(), "Branch quantity did not clear fixed amount");
        reject("quantity_rule", () -> Amounts.branch("steam_output", "normal", null, null, 0));

        // Potential quantity assertions (Tree Farm)
        com.google.gson.JsonObject potential = Amounts.potential("forestry.yield", "yield_dependent", "0", 4);
        require("potential".equals(potential.get("kind").getAsString()) && "forestry.yield".equals(potential.get("stat").getAsString()), "Invalid potential structure");
        RecipeRow potentialRow = new RecipeRow(facts, object("owner", "fixture", "handler", "tree_farm", "key", "potential"), "fixture", 0);
        potentialRow.itemOutput(0, new ItemStack(Items.paper, 0), 10000, potential);
        require(potentialRow.outputs.size() == 1 && potentialRow.outputs.get(0).getAsJsonObject().get("amount").isJsonNull(), "Potential quantity did not clear fixed amount");
        require(Amounts.potential("forestry.yield", "canBearFruit=false", "0", 0).get("nominal").getAsString().equals("0"),
                "Zero potential quantity must remain representable");

    }

    private static void alternatives(Facts facts) {
        ItemStack[] source = {new ItemStack(Items.paper, 3), new ItemStack(Items.paper, 8), new ItemStack(Items.paper, 0),
                new ItemStack(Items.paper, 2, OreDictionary.WILDCARD_VALUE), new ItemStack(Items.paper, 8)};
        List<RecipeRow.Ingredient> expanded = GtRecipes.ingredients(source, true,
                item -> new PositionedStack(item, 0, 0, true).items);
        RecipeRow row = new RecipeRow(facts, object("owner", "fixture", "handler", "gt", "key", "alternatives"), "fixture", 0);
        row.itemInput(0, expanded, false);
        com.google.gson.JsonArray choices = row.inputs.get(0).getAsJsonObject().getAsJsonArray("choices");
        require(choices.size() == 4 && choices.get(0).getAsJsonObject().get("amount").getAsString().equals("3")
                && choices.get(1).getAsJsonObject().get("amount").getAsString().equals("8")
                && choices.get(2).getAsJsonObject().getAsJsonObject("consume").get("kind").getAsString().equals("keep")
                && choices.get(3).getAsJsonObject().getAsJsonObject("rule").get("meta").getAsBoolean(),
                "Native alternatives lost distinct quantities, catalysts, wildcard rules or exact deduplication");
        require(source[0].stackSize == 3 && source[2].stackSize == 0, "Semantic expansion changed a source stack");
        reject("invalid_amount", () -> GtRecipes.ingredients(new ItemStack(Items.paper, -1), false,
                item -> new ItemStack[] {item}));
        ItemStack original = new ItemStack(Items.paper, 3); original.setTagInfo("owner", new net.minecraft.nbt.NBTTagInt(7));
        List<RecipeRow.Ingredient> exact = GtRecipes.ingredients(original, true, item -> new ItemStack[] {new ItemStack(Items.paper)});
        require(exact.get(0).item.getTagCompound().getInteger("owner") == 7
                && exact.get(0).item.getTagCompound() != original.getTagCompound()
                && !exact.get(0).display.hasTagCompound() && exact.get(0).amount == 3,
                "Exact native NBT or quantity was replaced by its display example");
        exact.get(0).item.getTagCompound().setInteger("owner", 8);
        require(original.getTagCompound().getInteger("owner") == 7, "Owned predicate NBT aliases the registered source");
        List<RecipeRow.Ingredient> ignored = GtRecipes.ingredients(original, false, item -> new ItemStack[] {item});
        require(ignored.get(0).rule.get("nbt").getAsBoolean(), "Native ignored-NBT matching became exact");
    }

    private static void nativeFailures() {
        for (Throwable failure : new Throwable[] {new java.util.concurrent.CancellationException("cancelled"),
                new Jobs.Fault("slot_changed", "native fatal"), new LinkageError("native linkage")}) {
            try { MagicApi.invoke(SlotsTest.class, null, "nativeFailure", new Class<?>[] {Throwable.class}, failure); throw new AssertionError("Native failure swallowed"); }
            catch (Throwable actual) { require(actual == failure, "Native reflection masked cancellation/fatal/error: " + actual); }
        }
        java.io.IOException checked = new java.io.IOException("native checked exception");
        try { MagicApi.invoke(SlotsTest.class, null, "nativeFailure", new Class<?>[] {Throwable.class}, checked); throw new AssertionError("Native checked failure swallowed"); }
        catch (Jobs.Fault actual) { require(actual.getCause() == checked && actual.getMessage().contains("nativeFailure"), "Native checked cause lost"); }
    }

    private static void nativeFailure(Throwable failure) throws Throwable { throw failure; }
    private static void reject(String code, Runnable action) {
        try { action.run(); throw new AssertionError("Expected " + code); }
        catch (Jobs.Fault expected) { require(expected.code.equals(code), "Wrong projection failure: " + expected.code); }
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
