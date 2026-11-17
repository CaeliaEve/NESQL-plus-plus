package com.github.dcysteine.nesql.exporter.capture;

/** A registry cursor reads native semantics; false requires a proven impossible recipe. */
interface RegistryRecipes extends AutoCloseable {
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
    /** Full semantic drift check at sealing; fast batch guards need not rescan every recipe. */
    default void finishVerification() { verify(); }
    /** Native exclusion proof. A false capture must provide this or RecipeRow.exclude evidence. */
    default com.google.gson.JsonObject exclusion(int index) { return null; }
    /** Handler-wide native disable evidence also exists when its recipe list is empty. */
    default com.google.gson.JsonObject handlerExclusion() { return null; }
    /** Release owned snapshot state only; never alter a borrowed native registry. */
    @Override default void close() {}
}
