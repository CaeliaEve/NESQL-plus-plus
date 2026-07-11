package com.github.dcysteine.nesql.exporter.canonical;

import java.util.List;
import java.util.Map;

/**
 * NESQL++ canonical recipe contract.
 *
 * <p>This remains intentionally simple in the first slice. The immediate goal is
 * to establish one authoritative recipe shape before refactoring exporters.</p>
 */
public class CanonicalRecipe {
    public String recipeId;
    public String family;
    public String sourcePlugin;
    public String sourceMod;
    public CanonicalMachineDescriptor machine;
    public Map<String, Object> layout;
    public List<Map<String, Object>> itemInputs;
    public List<Map<String, Object>> itemOutputs;
    public List<Map<String, Object>> fluidInputs;
    public List<Map<String, Object>> fluidOutputs;
    public Map<String, Object> probabilities;
    public Map<String, Object> metadata;
    public Map<String, Object> renderHints;
    public Map<String, Object> extensions;
}
