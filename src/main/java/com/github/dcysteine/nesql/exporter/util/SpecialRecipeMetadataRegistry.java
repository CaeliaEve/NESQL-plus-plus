package com.github.dcysteine.nesql.exporter.util;

import com.google.common.collect.ImmutableMap;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.HashMap;
import java.util.Map;

/**
 * Global registry for special recipe metadata.
 * Stores additional information for recipes from Avaritia, Thaumcraft, Botania, Blood Magic, Witchery, etc.
 */
public class SpecialRecipeMetadataRegistry {
    private static final Map<String, SpecialRecipeMetadata> metadataMap = new HashMap<>();

    /**
     * Register metadata for a recipe.
     * @param recipeId The recipe ID
     * @param metadata The metadata to register
     */
    public static void registerMetadata(String recipeId, SpecialRecipeMetadata metadata) {
        metadataMap.put(recipeId, metadata);
    }

    /**
     * Get metadata for a recipe.
     * @param recipeId The recipe ID
     * @return The metadata, or null if not found
     */
    public static SpecialRecipeMetadata getMetadata(String recipeId) {
        return metadataMap.get(recipeId);
    }

    /**
     * Check if a recipe has metadata.
     * @param recipeId The recipe ID
     * @return true if metadata exists for this recipe
     */
    public static boolean hasMetadata(String recipeId) {
        return metadataMap.containsKey(recipeId);
    }

    /**
     * Get all registered metadata.
     * @return Immutable map of all metadata
     */
    public static Map<String, SpecialRecipeMetadata> getAllMetadata() {
        return ImmutableMap.copyOf(metadataMap);
    }

    /**
     * Clear all metadata. Call this before starting a new export.
     */
    public static void clear() {
        metadataMap.clear();
    }

    /**
     * Metadata class for special recipes.
     */
    @EqualsAndHashCode
    @ToString
    public static class SpecialRecipeMetadata {
        private final String recipeType;
        private final Map<String, Object> data;

        public SpecialRecipeMetadata(String recipeType, Map<String, Object> data) {
            this.recipeType = recipeType;
            this.data = data;
        }

        public String getRecipeType() {
            return recipeType;
        }

        public Map<String, Object> getData() {
            return data;
        }
    }
}
