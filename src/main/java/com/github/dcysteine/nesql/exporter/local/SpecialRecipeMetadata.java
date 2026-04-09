package com.github.dcysteine.nesql.exporter.local;

import com.google.gson.Gson;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.Map;

/**
 * Generic metadata class for special recipe types (Avaritia, Thaumcraft, Botania, Blood Magic, Witchery).
 * Stores key-value pairs of additional recipe data.
 */
@EqualsAndHashCode
@ToString
public class SpecialRecipeMetadata {
    private String recipeType;
    private Map<String, String> data;

    public SpecialRecipeMetadata(String recipeType, Map<String, String> data) {
        this.recipeType = recipeType;
        this.data = data;
    }

    public String getRecipeType() {
        return recipeType;
    }

    public Map<String, String> getData() {
        return data;
    }

    /**
     * Converts this metadata to JSON string for storage/export
     */
    public String toJson() {
        return new Gson().toJson(this);
    }
}
