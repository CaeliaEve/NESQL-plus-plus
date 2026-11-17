package com.github.dcysteine.nesql.exporter.core;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Reviewed inventory-entry observations only. Inactive hooks do not read GL,
 * resolve a model, load a mod class, or change a clock. */
public final class InventoryTrace {
    private static final ThreadLocal<Observer> observer = new ThreadLocal<>();
    private static final Set<String> installed = ConcurrentHashMap.newKeySet();
    public static final String KEY = "micdoodle8.mods.galacticraft.core.client.render.item.ItemRendererKey";
    public static final String COSMIC = "fox.spiteful.avaritia.render.CosmicItemRenderer";
    public static final String COSMIC_BOW = "fox.spiteful.avaritia.render.CosmicBowRenderer";
    public static final String ETERNAL = "singulariteam.eternalsingularity.render.EternalItemRenderer";
    public static final String GT_ITEMS = "gregtech.common.render.items.MetaGeneratedItemRenderer";
    public static final String UNIVERSIUM = "gregtech.common.render.items.UniversiumRenderer";
    public static boolean reviewed(String name) {
        return KEY.equals(name) || COSMIC.equals(name) || COSMIC_BOW.equals(name) || ETERNAL.equals(name)
                || GT_ITEMS.equals(name) || UNIVERSIUM.equals(name) || ForestryTrace.item(name);
    }
    public static boolean instrumented(String name) { return installed.contains(name); }
    static void installed(String name) { installed.add(name); }
    public interface Observer {
        void enter(Object renderer, Object type, Object stack);
        default void exit(Object renderer) { }
    }
    public static void enter(Object renderer, Object type, Object stack) {
        Observer active = observer.get();
        if (active != null) active.enter(renderer, type, stack);
    }
    public static void exit(Object renderer) {
        Observer active = observer.get(); if (active != null) active.exit(renderer);
    }
    public static Scope observe(Observer next) {
        if (next == null) throw new IllegalArgumentException("Inventory observer is required");
        Observer previous = observer.get(); observer.set(next); return new Scope(previous);
    }
    public static final class Scope implements AutoCloseable {
        private final Observer previous;
        private boolean closed;
        private Scope(Observer previous) { this.previous = previous; }
        @Override public void close() {
            if (closed) return; closed = true;
            if (previous == null) observer.remove(); else observer.set(previous);
        }
    }
}
