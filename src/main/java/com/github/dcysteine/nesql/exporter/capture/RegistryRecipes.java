package com.github.dcysteine.nesql.exporter.capture;

/** A registry cursor projects one owned native view; false means a proven impossible recipe. */
interface RegistryRecipes {
    int size();
    boolean capture(int index, RecipeRow row);
}
