package com.github.dcysteine.nesql.exporter.local;

import com.github.dcysteine.nesql.sql.base.item.Item;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Table-driven catalog for native inventory renderer classification.
 *
 * <p>The catalog replaces a growing chain of string-condition branches with explicit rule data.
 * Adding a renderer family should mean adding a rule entry here, not editing stream writer control
 * flow.</p>
 */
final class AngelicaRendererClassificationCatalog {
    private static final AngelicaRendererClassification VANILLA_ATLAS = classification(
            "vanilla.atlas",
            false,
            false,
            false,
            "unknown",
            "native-renderer",
            "No inventory IItemRenderer registered.");

    private static final AngelicaRendererClassification AE2_NATIVE_SPRITE_ITEM_RENDERER = classification(
            "ae2.native-sprite-item-renderer",
            false,
            false,
            false,
            "unknown",
            "native-renderer",
            "AE2 chargeable base item resolves to a native atlas sprite; NBT charge variants keep framebuffer captures.");

    private static final AngelicaRendererClassification GENERIC_IITEM_RENDERER = classification(
            "generic.iitemrenderer",
            false,
            false,
            false,
            "custom.inventory-renderer",
            "native-renderer",
            "Custom inventory IItemRenderer without known native animation requirements.");

    private static final List<RendererRule> RENDERER_RULES = Collections.unmodifiableList(Arrays.asList(
            rule("cosmicitemrenderer", "avaritia.cosmic", true, true, "avaritia.cosmic", "native-render-tick", "Avaritia cosmic shader item."),
            rule("cosmicbowrenderer", "avaritia.cosmic-bow", true, true, "custom.inventory-renderer", "native-render-tick", "Avaritia infinity bow shader item."),
            rule("fancyhalorenderer", "avaritia.halo", true, true, "avaritia.halo", "native-render-tick", "Avaritia halo shader item."),
            rule("fracturedorerenderer", "avaritia.fractured-ore", true, true, "avaritia.fractured-ore", "native-render-tick", "Avaritia fractured ore renderer."),
            rule("eternalitemrenderer", "eternalsingularity.combined", true, true, "custom.inventory-renderer", "native-renderer", "Eternal Singularity animated renderer."),
            rule("itemrenderercompressedchest", "avaritiaddons.compressed-chest", false, true, "custom.inventory-renderer", "native-renderer", "Avaritiaddons compressed chest renderer."),
            rule("itemrendererinfinitychest", "avaritiaddons.infinity-chest", true, true, "custom.inventory-renderer", "native-renderer", "Avaritiaddons infinity chest renderer."),
            rule("appeng.client.render.itemrenderer", "ae2.item-renderer", false, true, "custom.inventory-renderer", "native-renderer", "Applied Energistics 2 custom item renderer."),
            rule("renderertrophy", "amazingtrophies.trophy", false, true, "custom.inventory-renderer", "native-renderer", "Amazing Trophies item renderer."),
            rule("textureditemrenderer", "gtnhlib.textured-item", false, true, "gtnhlib.textured-item", "native-renderer", "GTNHLib textured item renderer."),
            rule("modelisbrh", "gtnhlib.model-isbrh", false, true, "gtnhlib.model-isbrh", "native-renderer", "GTNHLib inventory model renderer.")
    ));

    private static final List<String> SPECIAL_RENDERER_GAP_TOKENS = Collections.unmodifiableList(Arrays.asList(
            "avaritia",
            "gtnhlib",
            "cosmic",
            "halo",
            "singular",
            "universium",
            "infinity",
            "transcendent",
            "glitch",
            "wireframe",
            "rainbow",
            "gaia"
    ));

    private static final Map<String, AngelicaRendererClassification> CLASSIFICATIONS_BY_KIND = buildClassificationIndex();

    private AngelicaRendererClassificationCatalog() {}

    static AngelicaRendererClassification classifyItemRenderer(Item item, String rendererClass) {
        if (rendererClass == null || rendererClass.trim().isEmpty()) {
            return VANILLA_ATLAS;
        }
        if (isAe2NativeSpriteOnlyRenderer(item, rendererClass)) {
            return AE2_NATIVE_SPRITE_ITEM_RENDERER;
        }
        String lower = rendererClass.toLowerCase(Locale.ROOT);
        for (RendererRule rule : RENDERER_RULES) {
            if (rule.matches(lower)) {
                return rule.classification;
            }
        }
        return GENERIC_IITEM_RENDERER;
    }

    static AngelicaRendererClassification byKind(String kind) {
        if (kind == null) {
            return null;
        }
        return CLASSIFICATIONS_BY_KIND.get(kind);
    }

    static boolean isKnownSpecialRendererGap(
            Item item,
            String rendererClass,
            AngelicaRendererClassification classification) {
        if (classification == null || !"generic.iitemrenderer".equals(classification.kind)) {
            return false;
        }
        StringBuilder haystack = new StringBuilder();
        if (rendererClass != null) {
            haystack.append(rendererClass).append('|');
        }
        if (item != null) {
            haystack.append(item.getModId()).append('|')
                    .append(item.getInternalName()).append('|')
                    .append(item.getLocalizedName());
        }
        String lower = haystack.toString().toLowerCase(Locale.ROOT);
        for (String token : SPECIAL_RENDERER_GAP_TOKENS) {
            if (lower.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isAe2NativeSpriteOnlyRenderer(Item item, String rendererClass) {
        if (item == null || rendererClass == null) {
            return false;
        }
        String lowerRenderer = rendererClass.toLowerCase(Locale.ROOT);
        if (!lowerRenderer.contains("appeng.client.render.itemrenderer")) {
            return false;
        }
        if (item.hasNbt()) {
            return false;
        }
        String modId = item.getModId() == null ? "" : item.getModId().toLowerCase(Locale.ROOT);
        String internalName = item.getInternalName() == null ? "" : item.getInternalName();
        if (!modId.contains("appliedenergistics2")) {
            return false;
        }
        return "tile.BlockEnergyCell".equals(internalName)
                || "tile.BlockDenseEnergyCell".equals(internalName);
    }

    private static RendererRule rule(
            String rendererClassToken,
            String kind,
            boolean usesShader,
            boolean requiresFramebufferCapture,
            String shaderFamily,
            String shaderTimeSource,
            String notes) {
        return new RendererRule(
                rendererClassToken,
                classification(
                        kind,
                        usesShader,
                        requiresFramebufferCapture,
                        true,
                        shaderFamily,
                        shaderTimeSource,
                        notes));
    }

    private static AngelicaRendererClassification classification(
            String kind,
            boolean usesShader,
            boolean requiresFramebufferCapture,
            boolean shaderExportEligible,
            String shaderFamily,
            String shaderTimeSource,
            String notes) {
        return new AngelicaRendererClassification(
                kind,
                usesShader,
                requiresFramebufferCapture,
                shaderExportEligible,
                shaderFamily,
                shaderTimeSource,
                notes);
    }

    private static Map<String, AngelicaRendererClassification> buildClassificationIndex() {
        Map<String, AngelicaRendererClassification> out = new LinkedHashMap<String, AngelicaRendererClassification>();
        put(out, VANILLA_ATLAS);
        put(out, AE2_NATIVE_SPRITE_ITEM_RENDERER);
        put(out, GENERIC_IITEM_RENDERER);
        for (RendererRule rule : RENDERER_RULES) {
            put(out, rule.classification);
        }
        return Collections.unmodifiableMap(out);
    }

    private static void put(Map<String, AngelicaRendererClassification> out, AngelicaRendererClassification classification) {
        out.put(classification.kind, classification);
    }

    private static final class RendererRule {
        private final String rendererClassToken;
        private final AngelicaRendererClassification classification;

        private RendererRule(String rendererClassToken, AngelicaRendererClassification classification) {
            this.rendererClassToken = rendererClassToken;
            this.classification = classification;
        }

        private boolean matches(String lowerRendererClass) {
            return lowerRendererClass.contains(rendererClassToken);
        }
    }
}
