package com.github.dcysteine.nesql.exporter.canonical;

import java.util.ArrayList;
import java.util.List;

/**
 * Lightweight validator for NESQL++ canonical contracts.
 *
 * <p>This is intentionally minimal in the first slice. It exists so new
 * canonical mappers have one place to add field-level validation instead of
 * silently emitting malformed structures.</p>
 */
public final class CanonicalContractValidator {

    private CanonicalContractValidator() {}

    public static List<String> validateItem(CanonicalItem item) {
        List<String> issues = new ArrayList<>();
        if (item == null) {
            issues.add("item:null");
            return issues;
        }
        if (isBlank(item.itemId)) issues.add("item.itemId:missing");
        if (isBlank(item.modId)) issues.add("item.modId:missing");
        if (isBlank(item.internalName)) issues.add("item.internalName:missing");
        if (isBlank(item.localizedName)) issues.add("item.localizedName:missing");
        return issues;
    }

    public static List<String> validateFluid(CanonicalFluid fluid) {
        List<String> issues = new ArrayList<>();
        if (fluid == null) {
            issues.add("fluid:null");
            return issues;
        }
        if (isBlank(fluid.fluidId)) issues.add("fluid.fluidId:missing");
        if (isBlank(fluid.modId)) issues.add("fluid.modId:missing");
        if (isBlank(fluid.internalName)) issues.add("fluid.internalName:missing");
        if (isBlank(fluid.localizedName)) issues.add("fluid.localizedName:missing");
        return issues;
    }

    public static List<String> validateMachine(CanonicalMachineDescriptor machine) {
        List<String> issues = new ArrayList<>();
        if (machine == null) {
            issues.add("machine:null");
            return issues;
        }
        if (isBlank(machine.machineId)) issues.add("machine.machineId:missing");
        if (isBlank(machine.category)) issues.add("machine.category:missing");
        if (isBlank(machine.machineType)) issues.add("machine.machineType:missing");
        return issues;
    }

    public static List<String> validateRecipe(CanonicalRecipe recipe) {
        List<String> issues = new ArrayList<>();
        if (recipe == null) {
            issues.add("recipe:null");
            return issues;
        }
        if (isBlank(recipe.recipeId)) issues.add("recipe.recipeId:missing");
        if (isBlank(recipe.family)) issues.add("recipe.family:missing");
        if (recipe.machine == null) issues.add("recipe.machine:missing");
        if (recipe.layout == null) issues.add("recipe.layout:missing");
        if (recipe.itemInputs == null) issues.add("recipe.itemInputs:missing");
        if (recipe.itemOutputs == null) issues.add("recipe.itemOutputs:missing");
        return issues;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
