package com.github.dcysteine.nesql.exporter.capture;

import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import gregtech.api.util.GTRecipe;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

/** Owned objects of the exact remapped release; no machine/world/recipe registry is invoked. */
final class NativeGtProbabilityTest {
    static void run() throws Exception {
        require(GTRecipe.class.getProtectionDomain().getCodeSource().getLocation().toString()
                .contains("/gregtech-2.9-probability.jar!"), "Exact remapped 2.9 release jar required");
        Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        GTRecipe recipe = (GTRecipe) ((sun.misc.Unsafe) field.get(null)).allocateInstance(GTRecipe.class);
        recipe.mInputs = new ItemStack[] {new ItemStack(Items.paper, 4), null, new ItemStack(Items.paper, 4)};
        recipe.mOutputs = new ItemStack[] {new ItemStack(Items.paper, 3), null, new ItemStack(Items.diamond, 9)};
        recipe.mFluidInputs = new FluidStack[] {new FluidStack(FluidRegistry.WATER, 1000), new FluidStack(FluidRegistry.LAVA, 0)};
        recipe.mFluidOutputs = new FluidStack[] {new FluidStack(FluidRegistry.WATER, 125)};
        recipe.mAltFluidInputs = new FluidStack[][] {
            {new FluidStack(FluidRegistry.WATER, 1000), new FluidStack(FluidRegistry.LAVA, 500)}, {}};
        recipe.mInputChances = new int[] {0};
        recipe.mOutputChances = new int[] {1000, 10000, 9000};
        recipe.mFluidInputChances = new int[] {5000, 0};
        recipe.mFluidOutputChances = new int[] {2500};
        recipe.mEnabled = true;
        Facts facts = NativeCoreFixesTest.row(recipe.mInputs[0], recipe.mOutputs[2]).facts;
        // Prime fluid identities without asking for localized display callbacks.
        for (FluidStack fluid : new FluidStack[] {recipe.mFluidInputs[0], recipe.mFluidInputs[1]})
            facts.restore("fluids", com.github.dcysteine.nesql.exporter.source.Json.object("id",
                    com.github.dcysteine.nesql.exporter.source.Identity.fluid(fluid.getFluid().getName(), null)));
        JsonObject captured = GtProbability.capture(recipe, facts, false);
        String path = System.getProperty("nesql.gtProbabilityFixture",
                "../elysium-compiler/contracts/fixtures/gt-native-probability-facts.json");
        JsonObject expected = new JsonParser().parse(new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8)).getAsJsonObject();
        require(captured.equals(expected), "GT native evidence differs from shared Compiler fixture: " + captured);
        require(recipe.getInputChance(2) == 10000 && recipe.getInputChance(0) == 0,
                "Native short/default/zero chance distinction changed");
        require(recipe.getOutputChance(2) == 9000 && recipe.getFluidInputChance(0) == 5000
                && recipe.getFluidInputChance(1) == 0 && recipe.getFluidOutputChance(0) == 2500
                && recipe.getFluidOutputChance(1) == 10000, "Native chance channels were confused");
        JsonObject input = com.github.dcysteine.nesql.exporter.source.Json.object("choices",
                com.github.dcysteine.nesql.exporter.source.Json.array(com.github.dcysteine.nesql.exporter.source.Json.object(
                        "amount", "4", "consume", com.github.dcysteine.nesql.exporter.source.Json.object("kind", "consume"))));
        GtProbability.inputChance(input, 0);
        JsonObject choice = input.getAsJsonArray("choices").get(0).getAsJsonObject();
        require(choice.get("amount").getAsString().equals("4")
                && choice.getAsJsonObject("consume").get("kind").getAsString().equals("chance")
                && choice.getAsJsonObject("consume").getAsJsonObject("chance").get("numerator").getAsString().equals("0"),
                "Chance zero removed the full native admission quantity");
        GtProbability.validateProjection(recipe);
        String before = GtRecipeSelection.signature(recipe);
        for (int[] chances : new int[][] {recipe.mInputChances, recipe.mOutputChances,
                recipe.mFluidInputChances, recipe.mFluidOutputChances}) {
            int old = chances[0];
            chances[0] = old + 1;
            require(!before.equals(GtRecipeSelection.signature(recipe)), "A native chance channel was omitted from drift");
            chances[0] = old;
        }
        recipe.mAltFluidInputs[0][1].amount = 501;
        require(!before.equals(GtRecipeSelection.signature(recipe)), "Alternative amount drift was omitted");
        recipe.mAltFluidInputs[0][1].amount = 500;
        recipe.mAltFluidInputs[1] = null;
        require(!before.equals(GtRecipeSelection.signature(recipe)), "Null and empty alternative slots were conflated");
        recipe.mAltFluidInputs[1] = new FluidStack[0];
        recipe.mInputChances = null;
        require(GtProbability.capture(recipe, facts, false).get("inputChances").isJsonNull(),
                "Null native chance array became empty/default array");
        recipe.mInputChances = new int[0];
        require(GtProbability.capture(recipe, facts, false).getAsJsonArray("inputChances").size() == 0,
                "Empty native chance array became null");
        recipe.mInputChances = new int[] {-1};
        require(GtProbability.capture(recipe, facts, false).getAsJsonArray("inputChances").get(0).getAsInt() == -1,
                "Malformed raw evidence was clamped");
        try { GtProbability.validateProjection(recipe); throw new AssertionError("Malformed executable projection admitted"); }
        catch (Jobs.Fault expectedFault) { require(expectedFault.code.equals("invalid_chance"), "Wrong malformed chance rejection"); }
        System.out.println("GT probability: shared capture fixture, native sparse/default/zero slots, fluid alternatives, drift and malformed projection passed");
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
