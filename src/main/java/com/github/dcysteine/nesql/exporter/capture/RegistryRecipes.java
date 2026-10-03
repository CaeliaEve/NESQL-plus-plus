package com.github.dcysteine.nesql.exporter.capture;

/** A registry cursor projects one owned native view; false means a proven impossible recipe. */
interface RegistryRecipes {
    int size();
    boolean capture(int index, RecipeRow row);
    /** Optional bounded proof for a non-exportable native entry, retained in jobs/checks. */
    default com.google.gson.JsonObject exclusion(int index) { return null; }
    /** Handler-wide native disable evidence also exists when its recipe list is empty. */
    default com.google.gson.JsonObject handlerExclusion() { return null; }
}
