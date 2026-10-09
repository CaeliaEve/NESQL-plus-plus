package com.github.dcysteine.nesql.exporter.capture;

/** A registry cursor projects one owned native view; false means a proven impossible recipe. */
interface RegistryRecipes {
    int size();
    boolean capture(int index, RecipeRow row);
    /** A native page may represent alternative results; adapters can expand it into owned rows. */
    default java.util.List<RecipeRow> captureRows(int index, RecipeRow row) {
        return capture(index, row) ? java.util.Collections.singletonList(row) : java.util.Collections.emptyList();
    }
    /** Publish one shared rule context, referenced by its category. */
    default String program(Facts facts) { return null; }
    /** Check shared registry drift once per game-thread capture batch. */
    default void verify() {}
    /** Optional bounded proof for a non-exportable native entry, retained in jobs/checks. */
    default com.google.gson.JsonObject exclusion(int index) { return null; }
    /** Handler-wide native disable evidence also exists when its recipe list is empty. */
    default com.google.gson.JsonObject handlerExclusion() { return null; }
}
