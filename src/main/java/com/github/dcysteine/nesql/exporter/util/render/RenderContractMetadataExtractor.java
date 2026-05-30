package com.github.dcysteine.nesql.exporter.util.render;

import com.github.dcysteine.nesql.exporter.main.Logger;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.FluidStack;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

final class RenderContractMetadataExtractor {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final Set<String> WRITTEN = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());

    private RenderContractMetadataExtractor() {}

    static void writeIfAvailable(RenderJob job, File imageDirectory) {
        if (job == null || imageDirectory == null) {
            return;
        }

        String relativePath = job.getRenderContractFilePath();
        if (relativePath == null || relativePath.isEmpty()) {
            return;
        }

        if (!WRITTEN.add(relativePath)) {
            return;
        }

        File outputFile = new File(imageDirectory, relativePath);
        File parent = outputFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        try (OutputStreamWriter writer =
                     new OutputStreamWriter(new FileOutputStream(outputFile, false), StandardCharsets.UTF_8)) {
            GSON.toJson(extract(job), writer);
        } catch (Exception e) {
            Logger.MOD.warn("Failed to write render contract metadata for {}", outputFile.getAbsolutePath(), e);
        }
    }

    static RenderContractMetadata extract(RenderJob job) {
        RenderContractMetadata metadata = new RenderContractMetadata();
        metadata.schemaVersion = "nesqlpp/render-contract/v2-draft";
        metadata.sourceType = job.getType() == RenderJob.JobType.ITEM ? "item" : "fluid";

        NativeSpriteMetadataExtractor.NativeSpriteMetadata nativeMetadata = job.getNativeSpriteMetadata();
        LayerAnalysis layerAnalysis = analyzeLayers(job);
        String rendererFamily = detectRendererFamily(job);
        if ("gregtech_material_renderer".equals(rendererFamily)) {
            layerAnalysis = mergeLayerAnalysis(layerAnalysis, analyzeGregTechMaterialLayers(job));
        } else if ("gtnhlib_textured_item_renderer".equals(rendererFamily)
                || "materiallib_textured_item_renderer".equals(rendererFamily)) {
            layerAnalysis = mergeLayerAnalysis(layerAnalysis, analyzeTexturedItemLayers(job));
        }
        boolean hasCustomInventoryRenderer = job.usesCustomInventoryRenderer();
        boolean hasNativeSprite = nativeMetadata != null;

        metadata.rendererFamily = rendererFamily;
        metadata.captureSource =
                determineCaptureSource(job, rendererFamily, hasCustomInventoryRenderer, layerAnalysis, hasNativeSprite);
        metadata.renderMode =
                determineRenderMode(job, rendererFamily, hasCustomInventoryRenderer, layerAnalysis, hasNativeSprite);
        metadata.playbackHint = determinePlaybackHint(metadata.renderMode);
        metadata.layers = layerAnalysis.layers.isEmpty() ? null : layerAnalysis.layers;
        metadata.rendererContract = buildRendererContract(job, rendererFamily, layerAnalysis, nativeMetadata);
        metadata.captureContract = buildCaptureContract(job, hasCustomInventoryRenderer, hasNativeSprite);
        metadata.shaderContract = buildShaderContract(rendererFamily);

        return metadata;
    }

    private static String determineRenderMode(
            RenderJob job,
            String rendererFamily,
            boolean hasCustomInventoryRenderer,
            LayerAnalysis layerAnalysis,
            boolean hasNativeSprite) {
        if (job.shouldUseContractStaticRenderOnly()) {
            return "renderer_family";
        }
        if (hasCustomInventoryRenderer) {
            return "captured_final_atlas";
        }
        if (rendererFamily != null && !rendererFamily.isEmpty()) {
            return "renderer_family";
        }
        if (layerAnalysis.layered) {
            return "layered_item";
        }
        if (hasNativeSprite) {
            return "native_sprite";
        }
        return job.needsMultipleFrames() ? "captured_final_atlas" : "native_sprite";
    }

    private static String determineCaptureSource(
            RenderJob job,
            String rendererFamily,
            boolean hasCustomInventoryRenderer,
            LayerAnalysis layerAnalysis,
            boolean hasNativeSprite) {
        if (job.shouldUseContractStaticRenderOnly()) {
            return "renderer_contract_safe_icon";
        }
        if (hasCustomInventoryRenderer) {
            return rendererFamily != null && !rendererFamily.isEmpty()
                    ? "inventory_renderer_family_capture"
                    : "inventory_renderer_capture";
        }
        if (rendererFamily != null && !rendererFamily.isEmpty()) {
            return "known_renderer_family";
        }
        if (layerAnalysis.layered) {
            return "layer_analysis";
        }
        if (hasNativeSprite && !hasCustomInventoryRenderer) {
            return "native_sprite_metadata";
        }
        return job.needsMultipleFrames() ? "framebuffer_multiframe" : "framebuffer_singleframe";
    }

    private static String determinePlaybackHint(String renderMode) {
        if ("captured_final_atlas".equals(renderMode)) {
            return "atlas_timeline";
        }
        if ("renderer_family".equals(renderMode)) {
            return "renderer_family_adapter";
        }
        if ("layered_item".equals(renderMode)) {
            return "layered_canvas";
        }
        return "native_sprite";
    }

    private static Map<String, Object> buildRendererContract(
            RenderJob job,
            String rendererFamily,
            LayerAnalysis layerAnalysis,
            NativeSpriteMetadataExtractor.NativeSpriteMetadata nativeMetadata) {
        Map<String, Object> contract = new LinkedHashMap<>();
        contract.put("jobType", job.getType().name().toLowerCase());
        contract.put("usesCustomInventoryRenderer", job.usesCustomInventoryRenderer());
        contract.put("needsMultipleFrames", job.needsMultipleFrames());
        contract.put("shouldPreferNativeSpriteAnimation", job.shouldPreferNativeSpriteAnimation());
        if (rendererFamily != null) {
            contract.put("family", rendererFamily);
        }
        if (job.getInventoryRendererClassName() != null) {
            contract.put("inventoryRendererClass", job.getInventoryRendererClassName());
        }
        if (job.getRawInventoryRendererClassName() != null
                && !job.getRawInventoryRendererClassName().equals(job.getInventoryRendererClassName())) {
            contract.put("rawInventoryRendererClass", job.getRawInventoryRendererClassName());
        }

        if (job.getType() == RenderJob.JobType.ITEM) {
            ItemStack stack = job.getItem();
            if (stack != null && stack.getItem() != null) {
                contract.put("itemClass", stack.getItem().getClass().getName());
                contract.put("renderPassCount", layerAnalysis.passCount);
                contract.put("requiresMultipleRenderPasses", layerAnalysis.requiresMultipleRenderPasses);
                List<String> detectedInterfaces = detectKnownInterfaces(stack.getItem());
                if (!detectedInterfaces.isEmpty()) {
                    contract.put("detectedInterfaces", detectedInterfaces);
                }
            }
        } else {
            FluidStack fluidStack = job.getFluid();
            if (fluidStack != null && fluidStack.getFluid() != null) {
                contract.put("fluidClass", fluidStack.getFluid().getClass().getName());
            }
        }

        if (nativeMetadata != null) {
            if (nativeMetadata.iconName != null) {
                contract.put("iconName", nativeMetadata.iconName);
            }
            if (nativeMetadata.atlasTexture != null) {
                contract.put("atlasTexture", nativeMetadata.atlasTexture);
            }
            if (nativeMetadata.frameCount != null) {
                contract.put("nativeFrameCount", nativeMetadata.frameCount);
            }
        }

        return contract;
    }

    private static LayerAnalysis mergeLayerAnalysis(LayerAnalysis base, LayerAnalysis specialized) {
        if (specialized == null || specialized.layers == null || specialized.layers.isEmpty()) {
            return base;
        }
        specialized.passCount = Math.max(base.passCount, specialized.passCount);
        specialized.requiresMultipleRenderPasses =
                base.requiresMultipleRenderPasses || specialized.requiresMultipleRenderPasses;
        specialized.layered = base.layered || specialized.layered || specialized.layers.size() > 1;
        return specialized;
    }

    private static Map<String, Object> buildCaptureContract(
            RenderJob job,
            boolean hasCustomInventoryRenderer,
            boolean hasNativeSprite) {
        Map<String, Object> contract = new LinkedHashMap<>();
        contract.put("imagePath", normalizePath(job.getImageFilePath()));
        contract.put("outputPath", normalizePath(job.getOutputFilePath()));
        contract.put("renderContractPath", normalizePath(job.getRenderContractFilePath()));
        contract.put("spriteMetadataPath", normalizePath(job.getSpriteMetadataFilePath()));
        contract.put("nativeSpriteAtlasPath", normalizePath(job.getNativeSpriteAtlasFilePath()));
        contract.put("multiFrame", job.needsMultipleFrames());
        contract.put("frameCount", job.needsMultipleFrames() ? job.getFrameIndex() + 1 : 1);
        contract.put("hasCustomInventoryRenderer", hasCustomInventoryRenderer);
        contract.put("hasNativeSpriteMetadata", hasNativeSprite);
        return contract;
    }

    private static Map<String, Object> buildShaderContract(String rendererFamily) {
        if (rendererFamily == null || rendererFamily.isEmpty()) {
            return null;
        }

        Map<String, Object> shader = new LinkedHashMap<>();
        if ("avaritia_cosmic".equals(rendererFamily)) {
            shader.put("shaderType", "universium");
            shader.put("inventoryOnly", Boolean.FALSE);
            shader.put("maskDriven", Boolean.TRUE);
            return shader;
        }
        if ("gt_universium".equals(rendererFamily)) {
            shader.put("shaderType", "universium");
            shader.put("inventoryOnly", Boolean.TRUE);
            return shader;
        }
        if ("gt_infinity".equals(rendererFamily)) {
            shader.put("shaderType", "infinity_halo_pulse");
            shader.put("inventoryOnly", Boolean.TRUE);
            shader.put("timeDriven", Boolean.TRUE);
            shader.put("halo", Boolean.TRUE);
            shader.put("pulse", Boolean.TRUE);
            return shader;
        }
        if ("gt_cosmic_neutronium".equals(rendererFamily)) {
            shader.put("shaderType", "cosmic_neutronium_halo");
            shader.put("inventoryOnly", Boolean.TRUE);
            shader.put("halo", Boolean.TRUE);
            return shader;
        }
        if ("gt_glitch".equals(rendererFamily)) {
            shader.put("shaderType", "glitch");
            shader.put("timeDriven", Boolean.TRUE);
            return shader;
        }
        if ("gt_wireframe_tesseract".equals(rendererFamily)) {
            shader.put("shaderType", "wireframe_tesseract");
            shader.put("inventoryOnly", Boolean.TRUE);
            shader.put("timeDriven", Boolean.TRUE);
            return shader;
        }
        if ("gt_rainbow_overlay".equals(rendererFamily)) {
            shader.put("shaderType", "rainbow_overlay");
            shader.put("inventoryOnly", Boolean.TRUE);
            shader.put("timeDriven", Boolean.TRUE);
            return shader;
        }
        if ("botania_gaia_spirit".equals(rendererFamily)) {
            shader.put("shaderType", "gaia_spirit");
            shader.put("inventoryOnly", Boolean.TRUE);
            shader.put("timeDriven", Boolean.TRUE);
            return shader;
        }
        if ("rocket_inventory_renderer".equals(rendererFamily)) {
            shader.put("shaderType", "rotating_model_capture");
            shader.put("inventoryOnly", Boolean.TRUE);
            shader.put("timeDriven", Boolean.TRUE);
            shader.put("rotationSource", "lwjgl_sys_time");
            return shader;
        }
        if ("gt_transcendent_metal".equals(rendererFamily)) {
            shader.put("timeDriven", Boolean.TRUE);
            shader.put("timeSource", "gregtech_animation_render_ticks");
            shader.put("rotationDegreesPerTick", 3.5D);
            return shader;
        }
        return null;
    }

    private static LayerAnalysis analyzeLayers(RenderJob job) {
        if (job.getType() != RenderJob.JobType.ITEM) {
            return LayerAnalysis.empty();
        }

        ItemStack stack = job.getItem();
        if (stack == null || stack.getItem() == null) {
            return LayerAnalysis.empty();
        }

        net.minecraft.item.Item item = stack.getItem();
        int meta = stack.getItemDamage();
        int passCount = 1;
        boolean requiresMultipleRenderPasses = false;

        try {
            requiresMultipleRenderPasses = item.requiresMultipleRenderPasses();
        } catch (Throwable ignored) {
        }

        try {
            passCount = Math.max(passCount, item.getRenderPasses(meta));
        } catch (Throwable ignored) {
        }

        if (requiresMultipleRenderPasses && passCount <= 1) {
            passCount = 2;
        }

        List<Map<String, Object>> layers = new ArrayList<>();
        boolean tinted = false;
        for (int pass = 0; pass < passCount; pass++) {
            Map<String, Object> layer = new LinkedHashMap<>();
            layer.put("passIndex", pass);
            layer.put("blend", pass == 0 ? "normal" : "overlay");

            IIcon icon = resolvePassIcon(item, stack, meta, pass);
            if (icon != null) {
                layer.put("iconClass", icon.getClass().getName());
                String iconName = resolveIconName(icon);
                if (iconName != null) {
                    layer.put("iconName", iconName);
                }
                ResourceLocation atlasResource = resolveAtlasResource(stack);
                if (atlasResource != null) {
                    layer.put("atlasTexture", atlasResource.toString());
                }
            }

            int tint = resolveTint(item, stack, pass);
            layer.put("tintARGB", tint);
            if (tint != 0xFFFFFF) {
                tinted = true;
            }

            layers.add(layer);
        }

        LayerAnalysis analysis = new LayerAnalysis();
        analysis.passCount = passCount;
        analysis.requiresMultipleRenderPasses = requiresMultipleRenderPasses;
        analysis.layers = layers;
        analysis.layered = passCount > 1 || tinted;
        return analysis;
    }

    private static LayerAnalysis analyzeGregTechMaterialLayers(RenderJob job) {
        if (job.getType() != RenderJob.JobType.ITEM) {
            return LayerAnalysis.empty();
        }

        ItemStack stack = job.getItem();
        if (stack == null || stack.getItem() == null) {
            return LayerAnalysis.empty();
        }

        try {
            Object item = stack.getItem();
            Class<?> rendererInterface =
                    Class.forName("gregtech.api.interfaces.IGT_ItemWithMaterialRenderer", false, item.getClass().getClassLoader());
            if (!rendererInterface.isInstance(item)) {
                return LayerAnalysis.empty();
            }

            Method getRenderPasses = rendererInterface.getMethod("getRenderPasses", int.class);
            Method getIcon = rendererInterface.getMethod("getIcon", int.class, int.class);
            Method getOverlayIcon = rendererInterface.getMethod("getOverlayIcon", int.class, int.class);
            Method getRGBa = rendererInterface.getMethod("getRGBa", ItemStack.class);

            int meta = stack.getItemDamage();
            int passes = Math.max(1, ((Number) getRenderPasses.invoke(item, meta)).intValue());
            short[] rgba = null;
            Object rgbaValue = getRGBa.invoke(item, stack);
            if (rgbaValue instanceof short[]) {
                rgba = (short[]) rgbaValue;
            }

            List<Map<String, Object>> layers = new ArrayList<>();
            for (int pass = 0; pass < passes; pass++) {
                Object baseIcon = getIcon.invoke(item, meta, pass);
                addFamilyLayer(layers, pass, "base", baseIcon, resolveAtlasResource(stack), rgba);

                Object overlayIcon = getOverlayIcon.invoke(item, meta, pass);
                addFamilyLayer(layers, pass, "overlay", overlayIcon, resolveAtlasResource(stack), null);
            }

            LayerAnalysis analysis = new LayerAnalysis();
            analysis.passCount = passes;
            analysis.requiresMultipleRenderPasses = passes > 1;
            analysis.layers = layers;
            analysis.layered = !layers.isEmpty() && layers.size() > 1;
            return analysis;
        } catch (Throwable ignored) {
            return LayerAnalysis.empty();
        }
    }

    private static LayerAnalysis analyzeTexturedItemLayers(RenderJob job) {
        if (job.getType() != RenderJob.JobType.ITEM) {
            return LayerAnalysis.empty();
        }

        ItemStack stack = job.getItem();
        if (stack == null || stack.getItem() == null) {
            return LayerAnalysis.empty();
        }

        try {
            Method getTextures = stack.getItem().getClass().getMethod("getTextures", ItemStack.class);
            Object texturesValue = getTextures.invoke(stack.getItem(), stack);
            if (!(texturesValue instanceof Object[])) {
                return LayerAnalysis.empty();
            }

            Object[] textures = (Object[]) texturesValue;
            List<Map<String, Object>> layers = new ArrayList<>();
            ResourceLocation atlasResource = resolveAtlasResource(stack);

            for (int index = 0; index < textures.length; index++) {
                Object texture = textures[index];
                if (texture == null) {
                    continue;
                }

                Map<String, Object> layer = new LinkedHashMap<>();
                layer.put("layerIndex", index);
                layer.put("layerRole", "texture");
                layer.put("textureClass", texture.getClass().getName());
                layer.put("blend", index == 0 ? "normal" : "overlay");

                IIcon icon = resolveTextureLayerIcon(texture, stack);
                if (icon != null) {
                    layer.put("iconClass", icon.getClass().getName());
                    String iconName = resolveIconName(icon);
                    if (iconName != null) {
                        layer.put("iconName", iconName);
                    }
                    if (atlasResource != null) {
                        layer.put("atlasTexture", atlasResource.toString());
                    }
                }

                Integer tint = resolveTextureLayerTint(texture, stack);
                if (tint != null) {
                    layer.put("tintARGB", tint);
                }

                layers.add(layer);
            }

            LayerAnalysis analysis = new LayerAnalysis();
            analysis.passCount = textures.length;
            analysis.requiresMultipleRenderPasses = textures.length > 1;
            analysis.layers = layers;
            analysis.layered = !layers.isEmpty() && layers.size() > 1;
            return analysis;
        } catch (Throwable ignored) {
            return LayerAnalysis.empty();
        }
    }

    private static IIcon resolvePassIcon(net.minecraft.item.Item item, ItemStack stack, int meta, int pass) {
        try {
            Method method = item.getClass().getMethod("getIcon", ItemStack.class, int.class);
            Object icon = method.invoke(item, stack, pass);
            if (icon instanceof IIcon) {
                return (IIcon) icon;
            }
        } catch (Exception ignored) {
        }

        try {
            Method method = item.getClass().getMethod("getIconFromDamageForRenderPass", int.class, int.class);
            Object icon = method.invoke(item, meta, pass);
            if (icon instanceof IIcon) {
                return (IIcon) icon;
            }
        } catch (Exception ignored) {
        }

        try {
            if (pass == 0) {
                return stack.getIconIndex();
            }
        } catch (Exception ignored) {
        }

        return null;
    }

    private static int resolveTint(net.minecraft.item.Item item, ItemStack stack, int pass) {
        try {
            return item.getColorFromItemStack(stack, pass);
        } catch (Throwable ignored) {
            return 0xFFFFFF;
        }
    }

    private static void addFamilyLayer(
            List<Map<String, Object>> layers,
            int pass,
            String role,
            Object iconValue,
            ResourceLocation atlasResource,
            short[] rgba) {
        if (!(iconValue instanceof IIcon)) {
            return;
        }

        IIcon icon = (IIcon) iconValue;
        Map<String, Object> layer = new LinkedHashMap<>();
        layer.put("passIndex", pass);
        layer.put("layerRole", role);
        layer.put("blend", "overlay".equals(role) ? "overlay" : "normal");
        layer.put("iconClass", icon.getClass().getName());
        String iconName = resolveIconName(icon);
        if (iconName != null) {
            layer.put("iconName", iconName);
        }
        if (atlasResource != null) {
            layer.put("atlasTexture", atlasResource.toString());
        }
        if (rgba != null && rgba.length >= 3 && !"overlay".equals(role)) {
            layer.put("tintARGB", toArgb(rgba));
            layer.put("tintRGBA", shortsToList(rgba));
        }
        layers.add(layer);
    }

    private static IIcon resolveTextureLayerIcon(Object texture, ItemStack stack) {
        try {
            java.lang.reflect.Field field = texture.getClass().getField("icon");
            Object function = field.get(texture);
            if (function instanceof java.util.function.Function) {
                @SuppressWarnings("unchecked")
                java.util.function.Function<Object, Object> iconFunction =
                        (java.util.function.Function<Object, Object>) function;
                Object icon = iconFunction.apply(stack);
                if (icon instanceof IIcon) {
                    return (IIcon) icon;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static Integer resolveTextureLayerTint(Object texture, ItemStack stack) {
        String[] fieldNames = { "color", "colour" };
        for (String fieldName : fieldNames) {
            try {
                java.lang.reflect.Field field = texture.getClass().getField(fieldName);
                Object function = field.get(texture);
                if (function instanceof java.util.function.Function) {
                    @SuppressWarnings("unchecked")
                    java.util.function.Function<Object, Object> colorFunction =
                            (java.util.function.Function<Object, Object>) function;
                    Object color = colorFunction.apply(stack);
                    Integer argb = colorToArgb(color);
                    if (argb != null) {
                        return argb;
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static Integer colorToArgb(Object color) {
        if (color == null) {
            return null;
        }

        try {
            Method toIntArgb = color.getClass().getMethod("toIntARGB");
            Object value = toIntArgb.invoke(color);
            if (value instanceof Number) {
                return ((Number) value).intValue();
            }
        } catch (Throwable ignored) {
        }

        try {
            Method getRed = color.getClass().getMethod("getRed");
            Method getGreen = color.getClass().getMethod("getGreen");
            Method getBlue = color.getClass().getMethod("getBlue");
            Method getAlpha = color.getClass().getMethod("getAlpha");
            short[] rgba = new short[] {
                    ((Number) getRed.invoke(color)).shortValue(),
                    ((Number) getGreen.invoke(color)).shortValue(),
                    ((Number) getBlue.invoke(color)).shortValue(),
                    ((Number) getAlpha.invoke(color)).shortValue()
            };
            return toArgb(rgba);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String resolveIconName(IIcon icon) {
        if (icon instanceof TextureAtlasSprite) {
            return ((TextureAtlasSprite) icon).getIconName();
        }
        try {
            Method method = icon.getClass().getMethod("getIconName");
            Object value = method.invoke(icon);
            return value instanceof String ? (String) value : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Integer toArgb(short[] rgba) {
        if (rgba == null || rgba.length < 3) {
            return null;
        }
        int alpha = rgba.length > 3 ? (rgba[3] & 0xFF) : 0xFF;
        int red = rgba[0] & 0xFF;
        int green = rgba[1] & 0xFF;
        int blue = rgba[2] & 0xFF;
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }

    private static List<Integer> shortsToList(short[] rgba) {
        List<Integer> values = new ArrayList<>();
        if (rgba == null) {
            return values;
        }
        for (short channel : rgba) {
            values.add(channel & 0xFF);
        }
        return values;
    }

    private static ResourceLocation resolveAtlasResource(ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return null;
        }
        try {
            Method method = stack.getItem().getClass().getMethod("getItemSpriteNumber");
            Object value = method.invoke(stack.getItem());
            if (value instanceof Number && ((Number) value).intValue() == 1) {
                return TextureMap.locationItemsTexture;
            }
        } catch (Exception ignored) {
        }
        return TextureMap.locationBlocksTexture;
    }

    private static String detectRendererFamily(RenderJob job) {
        if (job.getType() != RenderJob.JobType.ITEM) {
            return null;
        }

        ItemStack stack = job.getItem();
        if (stack == null || stack.getItem() == null) {
            return null;
        }

        net.minecraft.item.Item item = stack.getItem();
        String rendererClass = job.getInventoryRendererClassName();
        String itemClass = item.getClass().getName();

        if (contains(rendererClass, "CosmicItemRenderer") || itemClass.startsWith("fox.spiteful.avaritia")) {
            return "avaritia_cosmic";
        }

        if (contains(rendererClass, "TranscendentMetalRenderer")
                || contains(rendererClass, "TranscendentalMetaItemRenderer")) {
            return "gt_transcendent_metal";
        }

        if (contains(rendererClass, "InfinityRenderer")
                || contains(rendererClass, "InfinityMetaItemRenderer")) {
            return "gt_infinity";
        }

        if (contains(rendererClass, "CosmicNeutroniumRenderer")
                || contains(rendererClass, "CosmicNeutroniumMetaItemRenderer")) {
            return "gt_cosmic_neutronium";
        }

        if (contains(rendererClass, "UniversiumRenderer")) {
            return "gt_universium";
        }

        if (contains(rendererClass, "GlitchEffectRenderer")
                || contains(rendererClass, "GlitchEffectMetaItemRenderer")) {
            return "gt_glitch";
        }

        if ((contains(rendererClass, "ItemRendererTier") && contains(rendererClass, "Rocket"))
                || contains(rendererClass, "ItemRendererRocket")
                || contains(rendererClass, "ItemRendererShuttle")) {
            return "rocket_inventory_renderer";
        }

        if (contains(rendererClass, "WireframeTesseractRenderer")) {
            return "gt_wireframe_tesseract";
        }

        if (contains(rendererClass, "RainbowOverlayRenderer")
                || contains(rendererClass, "RainbowOverlayMetaItemRenderer")) {
            return "gt_rainbow_overlay";
        }

        if (contains(rendererClass, "GaiaSpiritRenderer")) {
            return "botania_gaia_spirit";
        }

        if (implementsInterface(item, "gregtech.api.interfaces.IGT_ItemWithMaterialRenderer")
                || contains(rendererClass, "GeneratedMaterialRenderer")
                || contains(rendererClass, "GeneratedItemRenderer")) {
            return "gregtech_material_renderer";
        }

        if (implementsInterface(item, "com.gtnewhorizon.gtnhlib.itemrendering.ItemWithTextures")
                || contains(rendererClass, "com.gtnewhorizon.gtnhlib.itemrendering.TexturedItemRenderer")) {
            return "gtnhlib_textured_item_renderer";
        }

        if (implementsInterface(item, "com.github.bartimaeusnek.bartworks.system.material.ItemWithTextures")
                || contains(rendererClass, "materiallib")
                || contains(rendererClass, "MaterialLib")
                || contains(itemClass, "materiallib")
                || contains(itemClass, "MaterialLib")) {
            return "materiallib_textured_item_renderer";
        }

        return null;
    }

    private static List<String> detectKnownInterfaces(net.minecraft.item.Item item) {
        if (item == null) {
            return Collections.emptyList();
        }

        String[] interfaceNames = {
                "gregtech.api.interfaces.IGT_ItemWithMaterialRenderer",
                "com.gtnewhorizon.gtnhlib.itemrendering.ItemWithTextures",
                "com.github.bartimaeusnek.bartworks.system.material.ItemWithTextures"
        };
        Set<String> detected = new LinkedHashSet<>();
        for (String interfaceName : interfaceNames) {
            if (implementsInterface(item, interfaceName)) {
                detected.add(interfaceName);
            }
        }
        return new ArrayList<>(detected);
    }

    private static boolean implementsInterface(Object instance, String interfaceName) {
        if (instance == null || interfaceName == null || interfaceName.isEmpty()) {
            return false;
        }
        try {
            Class<?> type = Class.forName(interfaceName, false, instance.getClass().getClassLoader());
            return type.isInstance(instance);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean contains(String value, String needle) {
        return value != null && needle != null && value.contains(needle);
    }

    private static String normalizePath(String path) {
        return path == null ? null : path.replace('\\', '/');
    }

    static final class RenderContractMetadata {
        String schemaVersion;
        String sourceType;
        String renderMode;
        String rendererFamily;
        String captureSource;
        String playbackHint;
        List<Map<String, Object>> layers;
        Map<String, Object> rendererContract;
        Map<String, Object> shaderContract;
        Map<String, Object> captureContract;
    }

    private static final class LayerAnalysis {
        int passCount = 1;
        boolean requiresMultipleRenderPasses;
        boolean layered;
        List<Map<String, Object>> layers = Collections.emptyList();

        static LayerAnalysis empty() {
            return new LayerAnalysis();
        }
    }
}
