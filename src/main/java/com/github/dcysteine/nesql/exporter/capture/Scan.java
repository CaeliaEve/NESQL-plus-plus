package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.PositionedStack;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonElement;
import forestry.api.genetics.AlleleManager;
import forestry.api.genetics.IIndividual;
import forestry.api.genetics.ISpeciesRoot;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTOreDictUnificator;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** The pinned MTEScanner analyzes a whole input stack, not its named NEI output placeholder. */
final class Scan {
    private Scan() {}

    static String root(String map, GTRecipe recipe) {
        if (!map.equals("gt.recipe.scanner") || !recipe.mFakeRecipe || recipe.mInputs.length != 1 || recipe.mInputs[0] == null) return null;
        Forestry.require();
        ISpeciesRoot root = AlleleManager.alleleRegistry.getSpeciesRoot(recipe.mInputs[0].copy());
        if (root == null) return null;
        implementation(root.getUID());
        return root.getUID();
    }

    static List<RecipeRow> capture(String root, GTRecipe recipe, List<GtRecipes.Placement> inputs,
                                   List<GtRecipes.Placement> outputs, RecipeRow row) {
        if (recipe.mInputs[0].stackSize != 1 || recipe.mOutputs.length != 1 || recipe.mOutputs[0] == null
                || recipe.mOutputs[0].getItem() != recipe.mInputs[0].getItem() || recipe.mOutputs[0].stackSize != 1
                || recipe.getOutputChance(0) != 10000 || recipe.mSpecialItems != null
                || recipe.mFluidInputs.length != 1 || recipe.mFluidOutputs.length != 0
                || recipe.mDuration != 500 || recipe.mEUt != 2 || inputs.size() != 2 || outputs.size() != 1) {
            throw fault("Forestry scanner registration differs from the pinned native recipe");
        }
        GtRecipes.Placement input = inputs.stream().filter(p -> !p.binding.fluid).findFirst().orElseThrow(() -> fault("Missing scan subject"));
        GtRecipes.Placement fluid = inputs.stream().filter(p -> p.binding.fluid).findFirst().orElseThrow(() -> fault("Missing scan honey"));
        GtRecipes.Placement output = outputs.get(0);
        if (input.binding.index != 0 || input.binding.special || fluid.binding.index != 0 || output.binding.index != 0
                || output.binding.fluid || !(fluid.source instanceof FluidStack)) throw fault("Unexpected scan slot binding");
        FluidStack honey = (FluidStack) fluid.source;
        if (honey.amount != 100 || !honey.isFluidEqual(gregtech.api.enums.Materials.Honey.getFluid(100))) throw fault("Changed scan honey requirement");
        List<RecipeRow.Ingredient> expanded = GtRecipes.ingredients(input.source, recipe.isNBTSensitive,
                item -> new PositionedStack(GTOreDictUnificator.getNonUnifiedStacks(item), input.display.relx, input.display.rely, true).items);
        GtRecipes.displayed(0, input.display, expanded);
        List<ItemStack> specimens = new ArrayList<>();
        for (RecipeRow.Ingredient candidate : expanded) specimens.add(candidate.display);
        return branches(row, root, specimens, honey, input.display, fluid.display, output.display,
                stack -> individual(root, stack));
    }

    static List<RecipeRow> branches(RecipeRow base, String root, List<ItemStack> specimens, FluidStack honey,
                                    PositionedStack input, PositionedStack fluid, PositionedStack output,
                                    Function<ItemStack, IIndividual> reader) {
        List<ItemStack> fresh = new ArrayList<>(), analyzed = new ArrayList<>();
        for (ItemStack specimen : specimens) {
            Jobs.checkpoint();
            if (!specimen.hasTagCompound() || !specimen.getTagCompound().hasKey("Genome", 10)) {
                throw fault("Scan display has no explicit genome; refusing to invent a default species");
            }
            // Each member sample is a native serialization fixed point. It represents this genotype,
            // not the named output template or an arbitrary replacement species.
            ItemStack before = specimen.copy(); before.stackSize = 1;
            before.setTagCompound(write(read(reader, before)));
            if (!specimen.getTagCompound().getTag("Genome").equals(before.getTagCompound().getTag("Genome"))
                    || !java.util.Objects.equals(specimen.getTagCompound().getTag("Mate"), before.getTagCompound().getTag("Mate"))) {
                throw fault("Native deserialization repaired or normalized a genome; an explicit allele adapter is required");
            }
            before.getTagCompound().setBoolean("IsAnalyzed", false);
            NBTTagCompound canonical = write(read(reader, before));
            if (!canonical.equals(before.getTagCompound())) throw fault("Scan member serialization is not stable");
            ItemStack after = analyze(before, reader);
            NBTTagCompound expected = (NBTTagCompound) before.getTagCompound().copy(); expected.setBoolean("IsAnalyzed", true);
            if (!expected.equals(after.getTagCompound())) throw fault("Native analysis changes more than the analyzed state of a canonical member");
            fresh.add(before); analyzed.add(after);
        }
        if (fresh.isEmpty()) throw fault("Scan has no concrete input members");
        List<RecipeRow> result = new ArrayList<>();
        for (boolean done : new boolean[] {false, true}) {
            List<ItemStack> members = done ? analyzed : fresh;
            RecipeRow row = base.branch();
            row.record.addProperty("duration", done ? "1" : "500");
            row.record.addProperty("energy", done ? "1" : "2");
            List<RecipeRow.Ingredient> ingredients = new ArrayList<>();
            for (ItemStack member : members) ingredients.add(new RecipeRow.Ingredient(member, 1, false,
                    object("kind", "member", "root", root, "analyzed", done)));
            row.itemInput(input, 0, ingredients, false);
            for (JsonElement choice : row.inputs.get(0).getAsJsonObject().getAsJsonArray("choices")) {
                choice.getAsJsonObject().add("consume", object("kind", "stack"));
            }
            // Even an already analyzed member needs 100 mB in the tank, but consumes none.
            row.fluidInput(fluid, 0, honey, done);
            Products product = Products.observe(members, object("kind", "analyze"), member -> analyze(member, reader), row.facts);
            row.itemOutput(output, 0, product.output, 10000);
            product.attach(row);
            result.add(row);
        }
        return result;
    }

    static ItemStack analyze(ItemStack input, Function<ItemStack, IIndividual> reader) {
        ItemStack copy = input.copy();
        IIndividual individual = read(reader, copy);
        boolean prior = individual.isAnalyzed(), changed = individual.analyze();
        if (changed == prior || !individual.isAnalyzed()) throw fault("Native analysis returned an inconsistent state");
        if (changed) copy.setTagCompound(write(individual));
        return copy;
    }

    private static IIndividual individual(String root, ItemStack stack) {
        ISpeciesRoot actual = AlleleManager.alleleRegistry.getSpeciesRoot(stack);
        if (actual == null || !root.equals(actual.getUID())) throw fault("Scan candidate changed its species root");
        IIndividual member = AlleleManager.alleleRegistry.getIndividual(stack);
        if (member == null || !member.getClass().getName().equals(implementation(root))) throw fault("Unknown scan member implementation for " + root);
        return member;
    }

    private static String implementation(String root) {
        switch (root) {
            case "rootTrees": return "forestry.arboriculture.genetics.Tree";
            case "rootBees": return "forestry.apiculture.genetics.Bee";
            case "rootButterflies": return "forestry.lepidopterology.genetics.Butterfly";
            default: throw fault("No scanner member adapter for " + root);
        }
    }

    private static IIndividual read(Function<ItemStack, IIndividual> reader, ItemStack input) {
        IIndividual result = reader.apply(input.copy());
        if (result == null) throw fault("Forestry did not recognize a scan member");
        return result;
    }

    private static NBTTagCompound write(IIndividual member) {
        NBTTagCompound nbt = new NBTTagCompound(); member.writeToNBT(nbt);
        if (!nbt.hasKey("Genome", 10)) throw fault("Serialized scan member has no genome");
        return nbt;
    }

    private static Jobs.Fault fault(String message) { return new Jobs.Fault("scan_semantics", message); }
}
