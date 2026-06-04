package com.github.dcysteine.nesql.exporter.semantic;

import com.github.dcysteine.nesql.sql.base.item.Item;

import java.util.Map;

/**
 * Plugin contract for GTNH-native semantic item families.
 *
 * <p>Families own NBT-aware public identity, variant identity, facets, sorting,
 * and representative priority. The current mapper still contains legacy rules,
 * but new high-confidence families should move behind this interface instead of
 * adding more ad-hoc checks to {@link SemanticItemIdentityMapper}.</p>
 */
public interface SemanticFamily {
    String familyId();

    boolean matches(Item item, ParsedNbt nbt);

    String publicIdentity(Item item, ParsedNbt nbt);

    String variantIdentity(Item item, ParsedNbt nbt, String payloadHash);

    Map<String, String> facets(Item item, ParsedNbt nbt);

    String sortKey(Item item, ParsedNbt nbt, Map<String, String> facets);

    int representativePriority(Item item, ParsedNbt nbt);
}
