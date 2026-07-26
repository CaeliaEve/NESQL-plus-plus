package com.github.dcysteine.nesql.exporter.plugin.nei.metadata;

import java.util.Locale;

/**
 * Shared heuristic classifier for NEI UI family census and raw-export metadata.
 */
public final class NeiUiFamilyClassifier {
    private NeiUiFamilyClassifier() {}

    public static String classifyHandlerFamily(String handlerClass, String itemName, String modId) {
        String rawDescriptor = firstNonBlank(handlerClass, "") + " " + firstNonBlank(itemName, "") + " "
                + firstNonBlank(modId, "");
        String descriptor = rawDescriptor.toLowerCase(Locale.ROOT);
        String words = identifierWords(rawDescriptor);
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
        if (descriptor.contains("thaum")) {
            return "thaumcraft";
        }
        if (descriptor.contains("botania")) {
            return "botania";
        }
        if (containsWord(words, "fluid") || containsWord(words, "liquid") || containsWord(words, "chemical")) {
            return "fluid-machine";
        }
        return "native-nei";
    }

    public static String inferLayoutKind(String handlerClass, String itemName, String family) {
        String rawDescriptor = firstNonBlank(handlerClass, "") + " " + firstNonBlank(itemName, "") + " "
                + firstNonBlank(family, "");
        String descriptor = rawDescriptor.toLowerCase(Locale.ROOT);
        String words = identifierWords(rawDescriptor);
        if (descriptor.contains("crafting") || descriptor.contains("shaped") || descriptor.contains("shapeless")) {
            return "crafting-grid";
        }
        if (descriptor.contains("furnace") || descriptor.contains("smelting")) {
            return "furnace";
        }
        if (containsWord(words, "fluid") || containsWord(words, "liquid") || containsWord(words, "chemical")) {
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

    private static String identifierWords(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return value
                .replaceAll("([a-z0-9])([A-Z])", "$1 $2")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
    }

    private static boolean containsWord(String words, String expected) {
        if (words == null || words.isEmpty() || expected == null || expected.isEmpty()) {
            return false;
        }
        return (" " + words + " ").contains(" " + expected + " ");
    }
}
