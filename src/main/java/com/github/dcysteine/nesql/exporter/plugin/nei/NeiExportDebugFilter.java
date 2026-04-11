package com.github.dcysteine.nesql.exporter.plugin.nei;

import codechicken.nei.recipe.IRecipeHandler;

public final class NeiExportDebugFilter {

    public enum Mode {
        NONE,
        THAUMCRAFT_FAMILY
    }

    private static volatile Mode mode = Mode.NONE;

    private NeiExportDebugFilter() {}

    public static void setMode(Mode nextMode) {
        mode = nextMode == null ? Mode.NONE : nextMode;
    }

    public static void clear() {
        mode = Mode.NONE;
    }

    public static Mode getMode() {
        return mode;
    }

    public static boolean shouldProcess(IRecipeHandler handler) {
        if (mode == Mode.NONE || handler == null) {
            return true;
        }
        if (mode == Mode.THAUMCRAFT_FAMILY) {
            String text = (
                    safe(handler.getHandlerId())
                            + " " + safe(handler.getRecipeName())
                            + " " + handler.getClass().getName()
            ).toLowerCase();
            return text.contains("thaum")
                    || text.contains("tcnei")
                    || text.contains("timeconqueror")
                    || text.contains("automagy")
                    || text.contains("forbidden")
                    || text.contains("tainted")
                    || text.contains("arcane")
                    || text.contains("infusion")
                    || text.contains("crucible")
                    || text.contains("aspect")
                    || text.contains("奥术")
                    || text.contains("注魔")
                    || text.contains("坩埚")
                    || text.contains("要素");
        }
        return true;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
