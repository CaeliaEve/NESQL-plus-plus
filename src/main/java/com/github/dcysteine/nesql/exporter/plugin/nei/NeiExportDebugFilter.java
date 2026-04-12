package com.github.dcysteine.nesql.exporter.plugin.nei;

import codechicken.nei.recipe.IRecipeHandler;

public final class NeiExportDebugFilter {

    public enum Mode {
        NONE,
        THAUMCRAFT_FAMILY,
        BOTANIA_FAMILY
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

        String text = (
                safe(handler.getHandlerId())
                        + " " + safe(handler.getRecipeName())
                        + " " + handler.getClass().getName()
        ).toLowerCase();

        if (mode == Mode.THAUMCRAFT_FAMILY) {
            return text.contains("thaum")
                    || text.contains("tcnei")
                    || text.contains("timeconqueror")
                    || text.contains("automagy")
                    || text.contains("forbidden")
                    || text.contains("tainted")
                    || text.contains("arcane")
                    || text.contains("infusion")
                    || text.contains("crucible")
                    || text.contains("aspect");
        }

        if (mode == Mode.BOTANIA_FAMILY) {
            return text.contains("botania")
                    || text.contains("vazkii")
                    || text.contains("mana")
                    || text.contains("rune")
                    || text.contains("daisy")
                    || text.contains("terra")
                    || text.contains("petal")
                    || text.contains("elven")
                    || text.contains("brew");
        }

        return true;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
