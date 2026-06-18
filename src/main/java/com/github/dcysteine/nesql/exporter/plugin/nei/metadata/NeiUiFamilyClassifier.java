package com.github.dcysteine.nesql.exporter.plugin.nei.metadata;

import java.util.Locale;

/**
 * Shared heuristic classifier for NEI UI family census and raw-export metadata.
 */
public final class NeiUiFamilyClassifier {
    private NeiUiFamilyClassifier() {}

    public static String classifyHandlerFamily(String handlerClass, String itemName, String modId) {
        String descriptor = (firstNonBlank(handlerClass, "") + " " + firstNonBlank(itemName, "") + " "
                + firstNonBlank(modId, ""))
                .toLowerCase(Locale.ROOT);
        if (descriptor.contains("shaped") || descriptor.contains("shapeless") || descriptor.contains("crafting")) {
            return "crafting-table";
        }
        if (descriptor.contains("furnace") || descriptor.contains("smelting")) {
            return "furnace";
        }
        if (descriptor.contains("brewing")) {
            return "brewing";
        }
        if (descriptor.contains("gregtech") || descriptor.contains("gt.")) {
            return "gregtech-machine";
        }
        if (descriptor.contains("thaum")
                || descriptor.contains("arcane")
                || descriptor.contains("crucible")
                || descriptor.contains("infusion")) {
            return "thaumcraft";
        }
        if (descriptor.contains("botania") || descriptor.contains("mana")) {
            return "botania";
        }
        if (descriptor.contains("fluid") || descriptor.contains("liquid") || descriptor.contains("chemical")) {
            return "fluid-machine";
        }
        return "native-nei";
    }

    public static String inferLayoutKind(String handlerClass, String itemName, String family) {
        String descriptor = (firstNonBlank(handlerClass, "") + " " + firstNonBlank(itemName, "") + " "
                + firstNonBlank(family, ""))
                .toLowerCase(Locale.ROOT);
        if (descriptor.contains("crafting") || descriptor.contains("shaped") || descriptor.contains("shapeless")) {
            return "crafting-grid";
        }
        if (descriptor.contains("furnace") || descriptor.contains("smelting")) {
            return "furnace";
        }
        if (descriptor.contains("fluid") || descriptor.contains("liquid") || descriptor.contains("chemical")) {
            return "fluid-machine";
        }
        if (descriptor.contains("gregtech") || descriptor.contains("machine")) {
            return "machine";
        }
        return "native-nei";
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null) {
                String trimmed = value.trim();
                if (!trimmed.isEmpty()) {
                    return trimmed;
                }
            }
        }
        return "";
    }
}
