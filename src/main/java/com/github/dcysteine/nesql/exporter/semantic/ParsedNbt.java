package com.github.dcysteine.nesql.exporter.semantic;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lightweight NBT text helper for exporter-side semantic classification.
 *
 * <p>NESQL++ stores captured item NBT as text. This helper intentionally stays
 * dependency-light and tolerant of Minecraft's loose NBT string shapes while
 * giving semantic families one common lookup surface instead of duplicating
 * lowercase substring and regex extraction logic.</p>
 */
public final class ParsedNbt {
    private static final Pattern KEY_VALUE_PATTERN = Pattern.compile(
            "(?i)(?:^|[,{\\s])([A-Za-z0-9_.:-]+)\\s*:\\s*(?:\\\"([^\\\"]*)\\\"|([^,}\\]]+))");

    private final String raw;
    private final String lower;
    private final Map<String, String> valuesByLowerKey;

    private ParsedNbt(String raw, Map<String, String> valuesByLowerKey) {
        this.raw = raw == null ? "" : raw;
        this.lower = this.raw.toLowerCase(Locale.ROOT);
        this.valuesByLowerKey = valuesByLowerKey;
    }

    public static ParsedNbt parse(String raw) {
        Map<String, String> values = new LinkedHashMap<String, String>();
        if (raw != null && raw.length() > 0) {
            Matcher matcher = KEY_VALUE_PATTERN.matcher(raw);
            while (matcher.find()) {
                String key = normalizeKey(matcher.group(1));
                String quoted = matcher.group(2);
                String unquoted = matcher.group(3);
                String value = quoted != null ? quoted : unquoted;
                if (key.length() > 0 && value != null && !values.containsKey(key)) {
                    values.put(key, stripNumericSuffix(value.trim()));
                }
            }
        }
        return new ParsedNbt(raw, values);
    }

    public boolean isEmpty() {
        return raw.trim().length() == 0;
    }

    public boolean contains(String needle) {
        return lower.contains(needle == null ? "" : needle.toLowerCase(Locale.ROOT));
    }

    public String first(String... keys) {
        if (keys == null) {
            return null;
        }
        for (String key : keys) {
            String value = valuesByLowerKey.get(normalizeKey(key));
            if (value != null && value.trim().length() > 0) {
                return value.trim();
            }
        }
        return null;
    }

    public String raw() {
        return raw;
    }

    public String lower() {
        return lower;
    }

    private static String normalizeKey(String key) {
        return key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
    }

    private static String stripNumericSuffix(String value) {
        return value == null ? null : value.replaceAll("[bBsSlLfFdD]$", "");
    }
}
