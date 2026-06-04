package com.github.dcysteine.nesql.exporter.semantic;

import com.github.dcysteine.nesql.sql.base.item.Item;
import com.google.gson.JsonObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.Map;
import java.util.LinkedHashMap;

public final class SemanticItemIdentityMapper {
    private SemanticItemIdentityMapper() {}

    public static SemanticItemIdentity map(Item item) {
        String nbt = safe(item.getNbt());
        ParsedNbt parsedNbt = ParsedNbt.parse(nbt);
        SemanticFamily semanticFamily = parsedNbt.isEmpty() ? null : SemanticFamilyRegistry.match(item, parsedNbt);
        String family = semanticFamily == null ? (parsedNbt.isEmpty() ? null : classify(item, parsedNbt)) : semanticFamily.familyId();
        String payloadHash = nbt.trim().length() == 0 ? null : sha256(nbt);
        String base = baseKey(item);

        SemanticItemIdentity identity = new SemanticItemIdentity();
        identity.family = family == null ? "legacy.item" : family;
        identity.classification = family == null
                ? (payloadHash == null ? "untagged-legacy" : "unclassified-tagged")
                : "classified";
        identity.payloadHash = payloadHash;
        identity.publicItemId = semanticFamily != null
                ? semanticFamily.publicIdentity(item, parsedNbt)
                : family == null
                        ? "item:" + stableToken(item.getId())
                        : "semantic:" + stableToken(family) + ":" + stableToken(base);
        identity.variantId = semanticFamily != null
                ? semanticFamily.variantIdentity(item, parsedNbt, payloadHash)
                : payloadHash == null
                        ? null
                        : identity.publicItemId + ":variant:" + payloadHash.substring(0, 16);
        applyFacets(identity, item, parsedNbt, semanticFamily);
        return identity;
    }

    public static String classify(Item item, String nbt) {
        return classify(item, ParsedNbt.parse(nbt));
    }

    public static String classify(Item item, ParsedNbt parsedNbt) {
        String mod = lower(item.getModId());
        String internal = lower(item.getInternalName());
        String id = lower(item.getId());
        String payload = parsedNbt.lower();

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
        if (isTGregworksPart(mod, internal, payload)) {
            return "toolpart.tgregworks";
        }
        if (isIc2CropSeed(mod, internal, payload)) {
            return "crop.ic2";
        }
        if ((mod.contains("thaumcraft") || mod.contains("tcwands") || id.contains("wand")) && (payload.contains("rod") || payload.contains("cap") || payload.contains("sceptre"))) {
            return "thaumcraft.wand";
        }
        if (isFluidContainerVariant(internal, payload)) {
            return "fluid.container";
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

    private static void applyFacets(SemanticItemIdentity identity, Item item, ParsedNbt parsedNbt, SemanticFamily semanticFamily) {
        if (identity == null || parsedNbt == null || parsedNbt.isEmpty()) {
            return;
        }
        SemanticFamily familyPlugin = semanticFamily == null ? SemanticFamilyRegistry.byId(identity.family) : semanticFamily;
        Map<String, String> facets = familyPlugin == null
                ? semanticFacets(identity.family, item, parsedNbt)
                : familyPlugin.facets(item, parsedNbt);
        if (facets.isEmpty()) {
            return;
        }
        JsonObject object = new JsonObject();
        for (Map.Entry<String, String> entry : facets.entrySet()) {
            object.addProperty(entry.getKey(), entry.getValue());
        }
        identity.facets = object;
        identity.facetSummary = facetSummary(identity.family, facets);
        identity.variantLabel = identity.facetSummary;
        identity.sortKey = familyPlugin == null ? sortKey(identity.family, facets, item) : familyPlugin.sortKey(item, parsedNbt, facets);
    }

    public static Map<String, String> semanticFacets(String family, Item item, String nbt) {
        return semanticFacets(family, item, ParsedNbt.parse(nbt));
    }

    public static Map<String, String> semanticFacets(String family, Item item, ParsedNbt parsedNbt) {
        LinkedHashMap<String, String> facets = new LinkedHashMap<String, String>();
        String normalizedFamily = safe(family);
        if ("facade.buildcraft".equals(normalizedFamily)
                || "facade.ae2".equals(normalizedFamily)
                || "facade.enderio.paint".equals(normalizedFamily)) {
            putIfPresent(facets, "block", firstNbtValue(parsedNbt, "block", "source_block", "sourceBlock"));
            putIfPresent(facets, "metadata", firstNbtValue(parsedNbt, "metadata", "meta"));
            putIfPresent(facets, "hollow", firstNbtValue(parsedNbt, "hollow"));
            putIfPresent(facets, "transparent", firstNbtValue(parsedNbt, "transparent"));
            putIfPresent(facets, "facadeType", firstNbtValue(parsedNbt, "type", "facadeType"));
        } else if ("thaumcraft.wand".equals(normalizedFamily)) {
            putIfPresent(facets, "rod", firstNbtValue(parsedNbt, "rod"));
            putIfPresent(facets, "cap", firstNbtValue(parsedNbt, "cap"));
            putIfPresent(facets, "focus", firstNbtValue(parsedNbt, "focus", "focusId"));
            putIfPresent(facets, "kind", lower(item.getInternalName()).contains("staff") ? "staff" : lower(item.getInternalName()).contains("sceptre") ? "sceptre" : "wand");
        } else if ("toolpart.tconstruct".equals(normalizedFamily)) {
            putIfPresent(facets, "partType", item.getInternalName());
            putIfPresent(facets, "material", String.valueOf(item.getItemDamage()));
            putIfPresent(facets, "material2", firstNbtValue(parsedNbt, "Material2", "material2"));
        } else if ("toolpart.tgregworks".equals(normalizedFamily)) {
            putIfPresent(facets, "partType", item.getInternalName());
            putIfPresent(facets, "material", firstNbtValue(parsedNbt, "material"));
        } else if ("tool.gregtech".equals(normalizedFamily) || "charge.gregtech".equals(normalizedFamily)) {
            putIfPresent(facets, "primaryMaterial", firstNbtValue(parsedNbt, "PrimaryMaterial", "primaryMaterial"));
            putIfPresent(facets, "secondaryMaterial", firstNbtValue(parsedNbt, "SecondaryMaterial", "secondaryMaterial"));
            putIfPresent(facets, "voltage", firstNbtValue(parsedNbt, "Voltage", "voltage"));
            putIfPresent(facets, "charge", firstNbtValue(parsedNbt, "Charge", "Energy", "Electric"));
        } else if ("tool.tconstruct".equals(normalizedFamily)) {
            putIfPresent(facets, "head", firstNbtValue(parsedNbt, "RenderHead", "Head"));
            putIfPresent(facets, "handle", firstNbtValue(parsedNbt, "RenderHandle", "Handle"));
            putIfPresent(facets, "accessory", firstNbtValue(parsedNbt, "RenderAccessory", "Accessory"));
            putIfPresent(facets, "durability", firstNbtValue(parsedNbt, "TotalDurability", "Durability"));
        } else if (normalizedFamily.startsWith("genetics.")) {
            putIfPresent(facets, "root", firstNbtValue(parsedNbt, "root"));
            putIfPresent(facets, "species", firstNbtValue(parsedNbt, "species", "Species"));
            putIfPresent(facets, "allele", firstNbtValue(parsedNbt, "allele"));
            putIfPresent(facets, "chromosomes", firstNbtValue(parsedNbt, "Chromosomes", "chromo"));
        } else if (normalizedFamily.startsWith("entity_capture.")) {
            putIfPresent(facets, "entity", firstNbtValue(parsedNbt, "Name", "EntityId", "EntityID", "mobType", "MobType", "id"));
            putIfPresent(facets, "skeletonType", firstNbtValue(parsedNbt, "SkeletonType"));
        } else if ("cosmetic.color".equals(normalizedFamily)) {
            putIfPresent(facets, "color", firstNbtValue(parsedNbt, "color", "colour"));
            putIfPresent(facets, "color1", firstNbtValue(parsedNbt, "color1"));
            putIfPresent(facets, "color2", firstNbtValue(parsedNbt, "color2"));
        } else if ("data_carrier.encoded-pattern".equals(normalizedFamily)) {
            putIfPresent(facets, "encodedPattern", firstNbtValue(parsedNbt, "encodedPattern", "EncodedPattern"));
            putIfPresent(facets, "output", firstNbtValue(parsedNbt, "out", "output"));
        } else if ("crop.ic2".equals(normalizedFamily)) {
            putIfPresent(facets, "crop", firstNbtValue(parsedNbt, "name"));
            putIfPresent(facets, "growth", firstNbtValue(parsedNbt, "growth"));
            putIfPresent(facets, "gain", firstNbtValue(parsedNbt, "gain"));
            putIfPresent(facets, "resistance", firstNbtValue(parsedNbt, "resistance"));
            putIfPresent(facets, "scan", firstNbtValue(parsedNbt, "scan"));
            putIfPresent(facets, "owner", firstNbtValue(parsedNbt, "owner"));
        } else if ("fluid.container".equals(normalizedFamily)) {
            putIfPresent(facets, "fluid", firstNbtValue(parsedNbt, "FluidName", "fluidName", "Name"));
            putIfPresent(facets, "amount", firstNbtValue(parsedNbt, "Amount", "amount"));
            putIfPresent(facets, "capacity", firstNbtValue(parsedNbt, "Capacity", "capacity"));
        }
        return facets;
    }

    private static String facetSummary(String family, Map<String, String> facets) {
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

    private static String sortKey(String family, Map<String, String> facets, Item item) {
        return stableToken(family)
                + "|"
                + stableToken(facetSummary(family, facets))
                + "|"
                + stableToken(item.getId());
    }

    private static void putIfPresent(Map<String, String> facets, String key, String value) {
        String safeValue = safe(value).trim();
        if (safeValue.length() > 0) {
            facets.put(key, safeValue);
        }
    }

    private static String firstNbtValue(ParsedNbt nbt, String... keys) {
        for (String key : keys) {
            String value = nbt.first(key);
            if (value != null && value.trim().length() > 0) {
                return value.trim();
            }
        }
        return null;
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

    private static boolean isTGregworksPart(String mod, String internal, String payload) {
        if (!(mod.contains("tgregworks") || internal.contains("tgregtoolpart"))) {
            return false;
        }
        return internal.contains("toolpart") || payload.contains("material");
    }

    private static boolean isChargedStateVariant(String payload) {
        return (payload.contains("electric") || payload.contains("energy"))
                && (payload.contains("maxcharge") || payload.contains("maxdamage") || payload.contains("voltage"));
    }

    private static boolean isIc2CropSeed(String mod, String internal, String payload) {
        return mod.equals("ic2")
                && internal.contains("cropseed")
                && (payload.contains("growth") || payload.contains("gain") || payload.contains("resistance") || payload.contains("scan"));
    }

    private static boolean isFluidContainerVariant(String internal, String payload) {
        return (payload.contains("fluidname") || (payload.contains("fluid") && payload.contains("amount")))
                && (internal.contains("cell")
                || internal.contains("bucket")
                || internal.contains("capsule")
                || internal.contains("tank")
                || internal.contains("container")
                || payload.contains("capacity"));
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

