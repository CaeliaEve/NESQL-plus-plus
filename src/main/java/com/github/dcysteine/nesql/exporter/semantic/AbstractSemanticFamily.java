package com.github.dcysteine.nesql.exporter.semantic;

import com.github.dcysteine.nesql.sql.base.item.Item;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Shared behavior for built-in GTNH semantic item families.
 */
abstract class AbstractSemanticFamily implements SemanticFamily {
    @Override
    public String publicIdentity(Item item, ParsedNbt nbt) {
        return "semantic:" + stableToken(familyId()) + ":" + stableToken(SemanticItemIdentityMapper.baseKey(item));
    }

    @Override
    public String variantIdentity(Item item, ParsedNbt nbt, String payloadHash) {
        if (payloadHash == null || payloadHash.length() == 0) {
            return null;
        }
        return publicIdentity(item, nbt) + ":variant:" + payloadHash.substring(0, Math.min(16, payloadHash.length()));
    }

    @Override
    public String sortKey(Item item, ParsedNbt nbt, Map<String, String> facets) {
        return stableToken(familyId()) + "|" + stableToken(facetSummary(familyId(), facets)) + "|" + stableToken(item.getId());
    }

    @Override
    public int representativePriority(Item item, ParsedNbt nbt) {
        return 0;
    }

    static LinkedHashMap<String, String> newFacets() {
        return new LinkedHashMap<String, String>();
    }

    static void putIfPresent(Map<String, String> facets, String key, String value) {
        String safeValue = safe(value).trim();
        if (safeValue.length() > 0) {
            facets.put(key, safeValue);
        }
    }

    static String firstNbtValue(ParsedNbt nbt, String... keys) {
        for (String key : keys) {
            String value = nbt.first(key);
            if (value != null && value.trim().length() > 0) {
                return value.trim();
            }
        }
        return null;
    }

    static String facetSummary(String family, Map<String, String> facets) {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : facets.entrySet()) {
            if (builder.length() > 0) {
                builder.append(" | ");
            }
            builder.append(entry.getKey()).append('=').append(entry.getValue());
            if (builder.length() > 96) {
                break;
            }
        }
        return builder.length() == 0 ? family : builder.toString();
    }

    static String stableToken(String value) {
        String normalized = safe(value).trim().toLowerCase(Locale.ROOT);
        StringBuilder builder = new StringBuilder(normalized.length());
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '.' || c == '_' || c == '-') {
                builder.append(c);
            } else {
                builder.append('~');
            }
        }
        return builder.length() == 0 ? "unknown" : builder.toString();
    }

    static String lower(String value) {
        return safe(value).toLowerCase(Locale.ROOT);
    }

    static String safe(String value) {
        return value == null ? "" : value;
    }
}
