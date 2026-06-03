package com.github.dcysteine.nesql.exporter.semantic;

import com.github.dcysteine.nesql.sql.base.item.Item;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

public final class SemanticItemIdentityMapper {
    private SemanticItemIdentityMapper() {}

    public static SemanticItemIdentity map(Item item) {
        String nbt = safe(item.getNbt());
        String family = nbt.trim().length() == 0 ? null : classify(item, nbt);
        String payloadHash = nbt.trim().length() == 0 ? null : sha256(nbt);
        String base = baseKey(item);

        SemanticItemIdentity identity = new SemanticItemIdentity();
        identity.family = family == null ? "legacy.item" : family;
        identity.classification = family == null
                ? (payloadHash == null ? "untagged-legacy" : "unclassified-tagged")
                : "classified";
        identity.payloadHash = payloadHash;
        identity.publicItemId = family == null
                ? "item:" + stableToken(item.getId())
                : "semantic:" + stableToken(family) + ":" + stableToken(base);
        identity.variantId = payloadHash == null
                ? null
                : identity.publicItemId + ":variant:" + payloadHash.substring(0, 16);
        return identity;
    }

    public static String classify(Item item, String nbt) {
        String mod = lower(item.getModId());
        String internal = lower(item.getInternalName());
        String id = lower(item.getId());
        String payload = lower(nbt);

        if ((mod.contains("buildcraft") || id.contains("buildcraft")) && (internal.contains("facade") || payload.contains("transparent") || payload.contains("hollow"))) {
            return "facade.buildcraft";
        }
        if ((mod.contains("appliedenergistics") || mod.contains("appeng") || id.contains("appeng")) && internal.contains("facade")) {
            return "facade.ae2";
        }
        if (mod.contains("enderio") && (internal.contains("conduitfacade") || payload.contains("source_block") || payload.contains("sourceblock") || payload.contains("painter"))) {
            return "facade.enderio.paint";
        }
        if (mod.contains("enderio") && (internal.contains("soul") || internal.contains("spawner") || payload.contains("mobtype"))) {
            return "entity_capture.enderio";
        }
        if (mod.contains("forestry") && (internal.contains("bee") || payload.contains("chromosomes") || payload.contains("genome"))) {
            return "genetics.forestry";
        }
        if ((mod.contains("binnie") || mod.contains("genetics") || mod.contains("gendustry"))
                && (payload.contains("gene") || payload.contains("species") || payload.contains("allele") || internal.contains("serum") || internal.contains("template"))) {
            return "genetics.binnie-gendustry";
        }
        if (isGregTechLikeTool(mod, internal, payload)) {
            return "tool.gregtech";
        }
        if ((mod.contains("tconstruct") || mod.contains("tinkers")) && (payload.contains("infitool") || payload.contains("renderhead") || payload.contains("head"))) {
            return "tool.tconstruct";
        }
        if (isTConstructPart(mod, internal, payload)) {
            return "toolpart.tconstruct";
        }
        if ((mod.contains("thaumcraft") || mod.contains("tcwands") || id.contains("wand")) && (payload.contains("rod") || payload.contains("cap") || payload.contains("sceptre"))) {
            return "thaumcraft.wand";
        }
        if (isChargedStateVariant(payload)) {
            return mod.contains("gregtech") || internal.contains("gt.") || internal.contains("meta")
                    ? "charge.gregtech"
                    : "charge.generic";
        }
        if (isEntityCaptureVariant(mod, internal, payload)) {
            return "entity_capture.generic";
        }
        if (isCosmeticColorVariant(mod, internal, payload)) {
            return "cosmetic.color";
        }
        if (payload.contains("encodedpattern") || internal.contains("encoded") || internal.contains("pattern")) {
            return "data_carrier.encoded-pattern";
        }
        return null;
    }

    public static String baseKey(Item item) {
        return safe(item.getModId()) + "|" + safe(item.getInternalName()) + "|" + item.getItemDamage();
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                String hex = Integer.toHexString(b & 0xff);
                if (hex.length() == 1) {
                    builder.append('0');
                }
                builder.append(hex);
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime", e);
        }
    }

    private static String stableToken(String value) {
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

    private static String lower(String value) {
        return safe(value).toLowerCase(Locale.ROOT);
    }

    private static boolean isGregTechLikeTool(String mod, String internal, String payload) {
        return internal.contains("metatool")
                || payload.contains("gt.toolstats")
                || (payload.contains("primarymaterial") && payload.contains("secondarymaterial"))
                || (payload.contains("uid0") && payload.contains("uid1") && payload.contains("slot") && payload.contains("gt."));
    }

    private static boolean isTConstructPart(String mod, String internal, String payload) {
        if (!(mod.contains("tconstruct") || mod.contains("tinkers"))) {
            return false;
        }
        return internal.endsWith("part")
                || internal.contains("part")
                || payload.contains("dualmat")
                || payload.contains("material2")
                || payload.contains("renderhandle")
                || payload.contains("renderaccessory");
    }

    private static boolean isChargedStateVariant(String payload) {
        return (payload.contains("electric") || payload.contains("energy"))
                && (payload.contains("maxcharge") || payload.contains("maxdamage") || payload.contains("voltage"));
    }

    private static boolean isEntityCaptureVariant(String mod, String internal, String payload) {
        return internal.contains("mobsoul")
                || internal.contains("mobcrystal")
                || internal.contains("soulvial")
                || payload.contains("mobtype")
                || (payload.contains("entity") && payload.contains("id"));
    }

    private static boolean isCosmeticColorVariant(String mod, String internal, String payload) {
        return (payload.contains("color") || payload.contains("colour"))
                && (mod.contains("botania") || internal.contains("wand") || payload.contains("display"));
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
