package com.github.dcysteine.nesql.exporter.util.render;

import com.github.dcysteine.nesql.exporter.main.config.ConfigOptions;
import com.github.dcysteine.nesql.exporter.util.IdUtil;
import com.google.auto.value.AutoOneOf;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.IItemRenderer;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.fluids.FluidStack;

import java.lang.reflect.Method;

/**
 * Note: this class doesn't quite conform to the {@link AutoOneOf} contract, as some of its contents
 * ({@link ItemStack}) are mutable. Shouldn't matter for our limited use-case, though.
 */
@AutoOneOf(RenderJob.JobType.class)
public abstract class RenderJob {
    public enum JobType {
        ITEM, FLUID, ENTITY
    }

    // Multi-frame capture state for GIF animation
    private int frameIndex = 0;
    private Integer totalFrames = null;
    private transient boolean nativeSpriteMetadataLoaded = false;
    private transient NativeSpriteMetadataExtractor.NativeSpriteMetadata nativeSpriteMetadata;
    private transient Boolean hasCustomInventoryRenderer;
    private transient String inventoryRendererClassName;
    private transient String rawInventoryRendererClassName;

    public static RenderJob ofItem(ItemStack itemStack) {
        ItemStack newStack = itemStack.copy();
        newStack.stackSize = 1;
        return AutoOneOf_RenderJob.item(newStack);
    }

    public static RenderJob ofFluid(FluidStack fluidStack) {
        return AutoOneOf_RenderJob.fluid(fluidStack);
    }

    public static RenderJob ofEntity(EntityPreviewRequest entityPreviewRequest) {
        return AutoOneOf_RenderJob.entity(entityPreviewRequest);
    }

    public abstract JobType getType();
    public abstract ItemStack getItem();
    public abstract FluidStack getFluid();
    public abstract EntityPreviewRequest getEntity();

    public NativeSpriteMetadataExtractor.NativeSpriteMetadata getNativeSpriteMetadata() {
        if (!nativeSpriteMetadataLoaded) {
            nativeSpriteMetadata = NativeSpriteMetadataExtractor.extract(this);
            nativeSpriteMetadataLoaded = true;
        }
        return nativeSpriteMetadata;
    }

    /**
     * Whether this job should rely on extracted native atlas metadata instead of framebuffer capture.
     *
     * <p>This is only safe when the icon shown in-game is the atlas sprite itself. Items that use a custom
     * inventory {@link IItemRenderer} can composite extra layers, shaders, or masks on top of the base sprite
     * (for example Avaritia infinity armor), so native atlas playback would not match the real in-game result.
     */
    public boolean shouldPreferNativeSpriteAnimation() {
        if (getType() == JobType.ENTITY) {
            return false;
        }

        NativeSpriteMetadataExtractor.NativeSpriteMetadata nativeMetadata = getNativeSpriteMetadata();
        if (nativeMetadata == null || !Boolean.TRUE.equals(nativeMetadata.animated)) {
            return false;
        }

        if (getType() == JobType.ITEM
                && AnimatedItemRegistry.INSTANCE.requiresFramebufferAnimationCapture(getItem())) {
            return false;
        }

        if (getType() == JobType.ITEM && shouldTrustNativeSpriteDespiteInventoryRenderer(getItem())) {
            return true;
        }

        if (getType() == JobType.ITEM && hasCustomInventoryRenderer()) {
            return false;
        }

        return true;
    }

    /**
     * Whether native sprite sidecar metadata should be emitted for this job.
     *
     * <p>Custom-rendered items still need this sidecar as auxiliary metadata so downstream consumers can
     * recover the native sprite timeline when framebuffer capture is unavailable or intentionally bypassed.
     * The render contract remains responsible for choosing whether that timeline is primary or only a
     * fallback.
     */
    public boolean shouldWriteNativeSpriteMetadata() {
        if (getType() == JobType.ENTITY) {
            return false;
        }
        return getNativeSpriteMetadata() != null;
    }

    /**
     * Check if this render job needs multiple frames for GIF animation.
     * Uses multiple detection methods:
     * 0. Force mode: If FORCE_ALL_ITEMS_ANIMATED is enabled, ALL items and fluids are captured
     * 1. AnimatedItemRegistry (whitelist of known animated items)
     * 2. GT5 IGT_ItemWithMaterialRenderer interface detection
     * 3. IPatchedTextureAtlasSprite animation detection
     */
    public boolean needsMultipleFrames() {
        return !"static".equals(getAnimationDecisionReason());
    }

    public String getAnimationDecisionReason() {
        if (getType() == JobType.ENTITY) {
            return "entity-preview";
        }

        if (!ConfigOptions.EXPORT_GIF.get()) {
            return "static";
        }

        if (shouldPreferNativeSpriteAnimation()) {
            return "static";
        }

        if (ConfigOptions.FORCE_ALL_ITEMS_ANIMATED.get()) {
            return "force-all-items";
        }

        if (getType() != JobType.ITEM) {
            return "static";
        }

        ItemStack stack = getItem();

        if (shouldUseContractStaticRenderOnly()) {
            return "static";
        }

        if (AnimatedItemRegistry.INSTANCE.requiresFramebufferAnimationCapture(stack)) {
            return "framebuffer-registry";
        }

        if (AnimatedItemRegistry.INSTANCE.isAnimatedItem(stack)) {
            return "animated-item-registry";
        }

        if (hasGregTechAnimation(stack)) {
            return "gregtech-material-animation";
        }

        if (hasGregTechMachineAnimation(stack)) {
            return "gregtech-machine-animation";
        }

        if (hasAnimatedCustomRenderer(stack)) {
            return "animated-custom-renderer";
        }

        if (hasAnimatedTexture(stack)) {
            return "animated-texture";
        }

        return "static";
    }

    /**
     * Some GTNH shader inventory renderers are already represented by render-contract metadata.
     * Capturing them through an off-screen framebuffer on Java 25 + LWJGL3ify can hard-exit the
     * client inside the shader path. For those renderers, export the safe base icon plus the
     * renderer/shader contract instead of invoking the unsafe inventory renderer repeatedly.
     */
    public boolean shouldUseContractStaticRenderOnly() {
        if (getType() != JobType.ITEM) {
            return false;
        }
        String rendererClassName = getInventoryRendererClassName();
        if (rendererClassName == null || rendererClassName.isEmpty()) {
            return false;
        }
        String normalized = rendererClassName.toLowerCase();
        return isContractSafeRendererClass(normalized);
    }

    /**
     * Whether static-item early-stop should be disabled for this job.
     *
     * <p>For known animated sources we want a full capture window even if the first several
     * frames happen to be identical, because many GTNH animations update more slowly than the
     * static detector window.
     */
    public boolean shouldForceFullCapture() {
        if (getType() == JobType.ENTITY) {
            return true;
        }

        if (!ConfigOptions.EXPORT_GIF.get() || !needsMultipleFrames()) {
            return false;
        }
        if (ConfigOptions.FORCE_ALL_ITEMS_ANIMATED.get()) {
            return false;
        }
        if (getType() != JobType.ITEM) {
            return false;
        }

        ItemStack stack = getItem();
        if (stack == null) {
            return false;
        }

        return AnimatedItemRegistry.INSTANCE.isAnimatedItem(stack)
                || AnimatedItemRegistry.INSTANCE.requiresFramebufferAnimationCapture(stack)
                || hasGregTechAnimation(stack)
                || hasGregTechMachineAnimation(stack)
                || hasAnimatedCustomRenderer(stack)
                || hasAnimatedTexture(stack);
    }

    /**
     * Whether framebuffer capture should manually advance atlas-backed animation frames between captures.
     *
     * <p>This is needed for custom inventory renderers that layer animated atlas sprites (for example
     * Eternal Singularity items). Their render path is correct, but GTNH's visibility-based animation
     * updates may not upload the next atlas frame during headless export unless we explicitly mark the
     * participating sprites as active.
     */
    public boolean shouldAdvanceTextureAtlasBetweenFrames() {
        if (getType() == JobType.ENTITY) {
            return false;
        }

        if (!ConfigOptions.EXPORT_GIF.get()
                || !needsMultipleFrames()
                || getType() != JobType.ITEM) {
            return false;
        }

        ItemStack stack = getItem();
        if (stack == null || stack.getItem() == null) {
            return false;
        }

        return AnimatedItemRegistry.INSTANCE.requiresFramebufferAnimationCapture(stack)
                || hasAnimatedTexture(stack)
                || hasAnimatedRenderPassTexture(stack)
                || hasAnimatedAuxiliaryTexture(stack);
    }

    /**
     * Mark atlas sprites referenced by this item so GTNH/Angelica uploads their next animation frame.
     */
    public void markAnimatedTexturesForUpdate() {
        if (getType() != JobType.ITEM) {
            return;
        }

        ItemStack stack = getItem();
        if (stack == null || stack.getItem() == null) {
            return;
        }

        net.minecraft.item.Item item = stack.getItem();
        TextureAnimationInspector.markIconForAnimationUpdate(stack.getIconIndex());

        int passes = 1;
        try {
            if (item.requiresMultipleRenderPasses()) {
                passes = Math.max(passes, item.getRenderPasses(stack.getItemDamage()));
            }
        } catch (Throwable ignored) {
        }

        for (int pass = 0; pass < passes; pass++) {
            try {
                TextureAnimationInspector.markIconForAnimationUpdate(item.getIcon(stack, pass));
            } catch (Throwable ignored) {
            }
            try {
                TextureAnimationInspector.markIconForAnimationUpdate(
                        item.getIconFromDamageForRenderPass(stack.getItemDamage(), pass));
            } catch (Throwable ignored) {
            }
        }

        markAuxiliaryAnimatedTexture(item, "getMaskTexture", new Class<?>[] { ItemStack.class, net.minecraft.entity.player.EntityPlayer.class },
                new Object[] { stack, null });
        markAuxiliaryAnimatedTexture(item, "getHaloTexture", new Class<?>[] { ItemStack.class }, new Object[] { stack });
        markAuxiliaryAnimatedTexture(item, "getOverlayIcon", new Class<?>[] { ItemStack.class }, new Object[] { stack });
        markAuxiliaryAnimatedTexture(item, "getMaskIcon", new Class<?>[] { ItemStack.class }, new Object[] { stack });
        markAuxiliaryAnimatedTexture(item, "getHaloIcon", new Class<?>[] { ItemStack.class }, new Object[] { stack });
        markAuxiliaryAnimatedTexture(item, "getGlowIcon", new Class<?>[] { ItemStack.class }, new Object[] { stack });
        markAuxiliaryAnimatedTexture(item, "getFrameIcon", new Class<?>[] { ItemStack.class }, new Object[] { stack });
        markAuxiliaryAnimatedTexture(item, "getIconOverlay", new Class<?>[] { ItemStack.class }, new Object[] { stack });
        markAuxiliaryAnimatedTexture(item, "getMaskTexture", new Class<?>[0], new Object[0]);
        markAuxiliaryAnimatedTexture(item, "getHaloTexture", new Class<?>[0], new Object[0]);
        markAuxiliaryAnimatedTexture(item, "getOverlayIcon", new Class<?>[0], new Object[0]);
    }

    /**
     * Check if item has GT5 animation (implements IGT_ItemWithMaterialRenderer).
     */
    private boolean hasGregTechAnimation(ItemStack stack) {
        try {
            Class<?> rendererInterface = Class.forName("gregtech.api.interfaces.IGT_ItemWithMaterialRenderer");
            if (!rendererInterface.isInstance(stack.getItem())) {
                return false;
            }

            // Check if any icon has animation support
            Object item = stack.getItem();
            int meta = stack.getItemDamage();

            try {
                Method getRenderPasses = rendererInterface.getMethod("getRenderPasses", int.class);
                int passes = (Integer) getRenderPasses.invoke(item, meta);

                Method getIcon = rendererInterface.getMethod("getIcon", int.class, int.class);
                Method getOverlayIcon = rendererInterface.getMethod("getOverlayIcon", int.class, int.class);

                for (int pass = 0; pass < passes; pass++) {
                    Object icon = getIcon.invoke(item, meta, pass);
                    if (isAnimatedIcon(icon)) {
                        return true;
                    }

                    Object overlay = getOverlayIcon.invoke(item, meta, pass);
                    if (isAnimatedIcon(overlay)) {
                        return true;
                    }
                }
            } catch (Exception e) {
                // Item might not support getRenderPasses
                return false;
            }
        } catch (ClassNotFoundException e) {
            // GT5 not available
            return false;
        } catch (Exception e) {
            return false;
        }

        return false;
    }

    /**
     * Generic animated texture detection for non-GT items.
     */
    private boolean hasAnimatedTexture(ItemStack stack) {
        try {
            // Check if the item's icon implements any animation interface
            net.minecraft.util.IIcon icon = stack.getIconIndex();
            return isAnimatedIcon(icon);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean hasAnimatedRenderPassTexture(ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return false;
        }

        net.minecraft.item.Item item = stack.getItem();
        int passes = 1;
        try {
            if (item.requiresMultipleRenderPasses()) {
                passes = Math.max(passes, item.getRenderPasses(stack.getItemDamage()));
            }
        } catch (Throwable ignored) {
        }

        for (int pass = 0; pass < passes; pass++) {
            try {
                if (isAnimatedIcon(item.getIcon(stack, pass))) {
                    return true;
                }
            } catch (Throwable ignored) {
            }
            try {
                if (isAnimatedIcon(item.getIconFromDamageForRenderPass(stack.getItemDamage(), pass))) {
                    return true;
                }
            } catch (Throwable ignored) {
            }
        }

        return false;
    }

    private boolean hasAnimatedAuxiliaryTexture(ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return false;
        }

        net.minecraft.item.Item item = stack.getItem();
        return isAnimatedAuxiliaryTexture(item, "getMaskTexture",
                        new Class<?>[] { ItemStack.class, net.minecraft.entity.player.EntityPlayer.class },
                        new Object[] { stack, null })
                || isAnimatedAuxiliaryTexture(item, "getHaloTexture",
                        new Class<?>[] { ItemStack.class },
                        new Object[] { stack })
                || isAnimatedAuxiliaryTexture(item, "getOverlayIcon",
                        new Class<?>[] { ItemStack.class },
                        new Object[] { stack })
                || isAnimatedAuxiliaryTexture(item, "getMaskIcon",
                        new Class<?>[] { ItemStack.class },
                        new Object[] { stack })
                || isAnimatedAuxiliaryTexture(item, "getHaloIcon",
                        new Class<?>[] { ItemStack.class },
                        new Object[] { stack })
                || isAnimatedAuxiliaryTexture(item, "getGlowIcon",
                        new Class<?>[] { ItemStack.class },
                        new Object[] { stack })
                || isAnimatedAuxiliaryTexture(item, "getFrameIcon",
                        new Class<?>[] { ItemStack.class },
                        new Object[] { stack })
                || isAnimatedAuxiliaryTexture(item, "getIconOverlay",
                        new Class<?>[] { ItemStack.class },
                        new Object[] { stack })
                || isAnimatedAuxiliaryTexture(item, "getMaskTexture",
                        new Class<?>[0],
                        new Object[0])
                || isAnimatedAuxiliaryTexture(item, "getHaloTexture",
                        new Class<?>[0],
                        new Object[0])
                || isAnimatedAuxiliaryTexture(item, "getOverlayIcon",
                        new Class<?>[0],
                        new Object[0]);
    }

    /**
     * Check if an icon supports animation (implements IPatchedTextureAtlasSprite).
     */
    private boolean isAnimatedIcon(Object icon) {
        return TextureAnimationInspector.isAnimatedIcon(icon);
    }

    private boolean isAnimatedAuxiliaryTexture(
            Object target,
            String methodName,
            Class<?>[] parameterTypes,
            Object[] args) {
        try {
            Method method = target.getClass().getMethod(methodName, parameterTypes);
            return isAnimatedIcon(method.invoke(target, args));
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void markAuxiliaryAnimatedTexture(
            Object target,
            String methodName,
            Class<?>[] parameterTypes,
            Object[] args) {
        try {
            Method method = target.getClass().getMethod(methodName, parameterTypes);
            TextureAnimationInspector.markIconForAnimationUpdate(method.invoke(target, args));
        } catch (Throwable ignored) {
        }
    }

    /**
     * Check if a GregTech ItemMachines stack uses animated block/hatch/pipe textures in inventory.
     */
    private boolean hasGregTechMachineAnimation(ItemStack stack) {
        return GregTechAnimationDetector.hasAnimatedMachineTexture(stack);
    }

    /**
     * Detect known custom inventory renderers that animate over time instead of relying on sprite metadata.
     *
     * <p>These renderers commonly rotate, pulse, glitch, or draw procedural wireframes in inventory, so
     * framebuffer multi-frame capture is required to match in-game appearance.
     */
    private boolean hasAnimatedCustomRenderer(ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return false;
        }

        String rendererClassName = getInventoryRendererClassName();
        if (rendererClassName == null || rendererClassName.isEmpty()) {
            return false;
        }

        return isAnimatedCustomRendererClass(rendererClassName);
    }

    private boolean shouldTrustNativeSpriteDespiteInventoryRenderer(ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return false;
        }

        String registryName;
        try {
            Object name = net.minecraft.item.Item.itemRegistry.getNameForObject(stack.getItem());
            registryName = name == null ? "" : name.toString();
        } catch (Throwable ignored) {
            registryName = "";
        }

        return registryName.equals("Thaumcraft:ItemWispEssence")
                || registryName.equals("Thaumcraft:ItemResource")
                || registryName.equals("Thaumcraft:ItemCrystalEssence")
                || registryName.equals("Thaumcraft:ItemCrystal")
                || registryName.equals("Thaumcraft:ItemEldritchObject")
                || registryName.equals("Thaumcraft:ItemPrimordialPearl");
    }

    private boolean isAnimatedCustomRendererClass(String rendererClassName) {
        String normalized = rendererClassName.toLowerCase();

        if (normalized.contains("itemrenderertier") && normalized.contains("rocket")) {
            return true;
        }
        if (normalized.contains("itemrenderershuttle")) {
            return true;
        }
        if (normalized.contains("itemrendererrocket")) {
            return true;
        }

        return isContractSafeRendererClass(normalized)
                || normalized.contains("transcendentalmetaitemrenderer")
                || normalized.contains("glitcheffectmetaitemrenderer")
                || normalized.contains("wireframetesseractrenderer")
                || normalized.contains("singularity")
                || normalized.contains("singular")
                || normalized.contains("avaritia")
                || normalized.contains("eternal")
                || normalized.contains("universal")
                || normalized.contains("infinitymetaitemrenderer")
                || normalized.contains("transcendentmetalrenderer")
                || normalized.contains("infinityrenderer")
                || normalized.contains("universiumrenderer")
                || normalized.contains("cosmicneutroniumrenderer")
                || normalized.contains("cosmicneutroniummetaitemrenderer")
                || normalized.contains("rainbowoverlayrenderer")
                || normalized.contains("rainbowoverlaymetaitemrenderer")
                || normalized.contains("gaiaspiritrenderer");
    }

    /**
     * Renderer families with declarative render contracts that should not be invoked through
     * repeated off-screen framebuffer capture on Java 25 + LWJGL3ify. The web runtime can replay
     * these shader/time effects from the emitted contract, while NESQL++ still exports the safe
     * base icon and native sprite metadata.
     */
    private boolean isContractSafeRendererClass(String normalizedRendererClassName) {
        if (normalizedRendererClassName == null || normalizedRendererClassName.isEmpty()) {
            return false;
        }

        return normalizedRendererClassName.contains("cosmicitemrenderer")
                || normalizedRendererClassName.contains("transcendentalmetaitemrenderer")
                || normalizedRendererClassName.contains("transcendentmetalrenderer")
                || normalizedRendererClassName.contains("infinitymetaitemrenderer")
                || normalizedRendererClassName.contains("infinityrenderer")
                || normalizedRendererClassName.contains("cosmicneutroniummetaitemrenderer")
                || normalizedRendererClassName.contains("cosmicneutroniumrenderer")
                || normalizedRendererClassName.contains("universiumrenderer")
                || normalizedRendererClassName.contains("glitcheffectmetaitemrenderer")
                || normalizedRendererClassName.contains("glitcheffectrenderer")
                || normalizedRendererClassName.contains("wireframetesseractrenderer")
                || normalizedRendererClassName.contains("rainbowoverlaymetaitemrenderer")
                || normalizedRendererClassName.contains("rainbowoverlayrenderer")
                || normalizedRendererClassName.contains("gaiaspiritrenderer");
    }
    private boolean hasCustomInventoryRenderer() {
        if (getType() != JobType.ITEM) {
            return false;
        }

        if (hasCustomInventoryRenderer == null) {
            hasCustomInventoryRenderer = detectCustomInventoryRenderer(getItem());
        }

        return Boolean.TRUE.equals(hasCustomInventoryRenderer);
    }

    public boolean usesCustomInventoryRenderer() {
        if (getType() != JobType.ITEM) {
            return false;
        }
        return hasCustomInventoryRenderer();
    }

    public String getInventoryRendererClassName() {
        if (getType() != JobType.ITEM) {
            return null;
        }

        if (inventoryRendererClassName != null) {
            return inventoryRendererClassName;
        }

        ItemStack stack = getItem();
        if (stack == null || stack.getItem() == null) {
            return null;
        }

        try {
            IItemRenderer renderer =
                    MinecraftForgeClient.getItemRenderer(stack, IItemRenderer.ItemRenderType.INVENTORY);
            if (renderer == null) {
                return null;
            }

            rawInventoryRendererClassName = renderer.getClass().getName();
            IItemRenderer effectiveRenderer = resolveEffectiveInventoryRenderer(stack, renderer);
            inventoryRendererClassName =
                    effectiveRenderer != null ? effectiveRenderer.getClass().getName() : rawInventoryRendererClassName;
            return inventoryRendererClassName;
        } catch (Throwable ignored) {
            return null;
        }
    }

    public String getRawInventoryRendererClassName() {
        if (getType() != JobType.ITEM) {
            return null;
        }

        if (rawInventoryRendererClassName == null) {
            getInventoryRendererClassName();
        }

        return rawInventoryRendererClassName;
    }

    private boolean detectCustomInventoryRenderer(ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return false;
        }

        try {
            return MinecraftForgeClient.getItemRenderer(stack, IItemRenderer.ItemRenderType.INVENTORY) != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private IItemRenderer resolveEffectiveInventoryRenderer(ItemStack stack, IItemRenderer renderer) {
        if (renderer == null) {
            return null;
        }

        try {
            Method delegateResolver = renderer.getClass().getDeclaredMethod("getRendererForItemStack", ItemStack.class);
            delegateResolver.setAccessible(true);
            Object delegate = delegateResolver.invoke(renderer, stack);
            if (delegate instanceof IItemRenderer) {
                return (IItemRenderer) delegate;
            }
        } catch (Throwable ignored) {
        }

        return renderer;
    }

    /**
     * Get the file path for this render job.
     * For multi-frame jobs, returns the final GIF path.
     */
    public String getImageFilePath() {
        switch (getType()) {
            case ITEM:
                return IdUtil.imageFilePath(getItem());

            case FLUID:
                return IdUtil.imageFilePath(getFluid());

            case ENTITY:
                return getEntity() == null ? null : getEntity().getImageFilePath();

            default:
                throw new IllegalStateException("Unhandled job type: " + this);
        }
    }

    /**
     * Get the file path for the current frame (used during multi-frame capture).
     * This is a temporary file that won't be saved to disk.
     */
    public String getFrameFilePath() {
        String basePath = getImageFilePath();
        if (basePath == null) {
            return null;
        }
        if (needsMultipleFrames()) {
            // Replace extension with _frame_N.png for temporary tracking
            return basePath.replace(".png", "_frame_" + frameIndex + ".png")
                           .replace(".gif", "_frame_" + frameIndex + ".png");
        }
        return basePath;
    }

    /**
     * Get the final output file path (with correct extension).
     */
    public String getOutputFilePath() {
        String path = getImageFilePath();
        if (path == null) {
            return null;
        }
        if (needsMultipleFrames()) {
            return path.replace(".png", ".gif");
        }
        return path;
    }

    public String getSpriteMetadataFilePath() {
        String basePath = getImageFilePath();
        if (basePath == null) {
            return null;
        }
        if (basePath.endsWith(".png")) {
            return basePath.substring(0, basePath.length() - 4) + ".sprite.json";
        }
        if (basePath.endsWith(".gif")) {
            return basePath.substring(0, basePath.length() - 4) + ".sprite.json";
        }
        return basePath + ".sprite.json";
    }

    public String getRenderContractFilePath() {
        String basePath = getImageFilePath();
        if (basePath == null) {
            return null;
        }
        if (basePath.endsWith(".png")) {
            return basePath.substring(0, basePath.length() - 4) + ".render.json";
        }
        if (basePath.endsWith(".gif")) {
            return basePath.substring(0, basePath.length() - 4) + ".render.json";
        }
        return basePath + ".render.json";
    }

    public String getNativeSpriteAtlasFilePath() {
        String basePath = getImageFilePath();
        if (basePath == null) {
            return null;
        }
        if (basePath.endsWith(".png")) {
            return basePath.substring(0, basePath.length() - 4) + ".sprite-atlas.png";
        }
        if (basePath.endsWith(".gif")) {
            return basePath.substring(0, basePath.length() - 4) + ".sprite-atlas.png";
        }
        return basePath + ".sprite-atlas.png";
    }

    public String getRenderSignature() {
        StringBuilder builder = new StringBuilder();
        builder.append("nesqlpp/render-signature/v1|");
        builder.append("type=").append(getType().name()).append('|');
        builder.append("output=").append(getOutputFilePath()).append('|');
        builder.append("multi=").append(needsMultipleFrames()).append('|');
        builder.append("frames=").append(getRequestedFrameCount()).append('|');
        builder.append("delay=").append(getRequestedFrameDelayMs()).append('|');
        builder.append("native=").append(shouldPreferNativeSpriteAnimation()).append('|');
        if (getType() == JobType.ITEM) {
            ItemStack stack = getItem();
            if (stack != null && stack.getItem() != null) {
                builder.append("item=")
                        .append(net.minecraft.item.Item.itemRegistry.getNameForObject(stack.getItem()))
                        .append('|');
                builder.append("damage=").append(stack.getItemDamage()).append('|');
                builder.append("itemClass=").append(stack.getItem().getClass().getName()).append('|');
                builder.append("renderer=").append(getInventoryRendererClassName()).append('|');
                builder.append("rawRenderer=").append(getRawInventoryRendererClassName()).append('|');
                try {
                    builder.append("icon=")
                            .append(stack.getIconIndex() == null ? "" : stack.getIconIndex().getIconName())
                            .append('|');
                } catch (Throwable ignored) {
                }
            }
        } else if (getType() == JobType.FLUID) {
            FluidStack fluidStack = getFluid();
            if (fluidStack != null && fluidStack.getFluid() != null) {
                builder.append("fluid=").append(fluidStack.getFluid().getName()).append('|');
                builder.append("fluidClass=").append(fluidStack.getFluid().getClass().getName()).append('|');
            }
        } else if (getType() == JobType.ENTITY && getEntity() != null) {
            builder.append("entity=").append(getEntity().getMobName()).append('|');
        }
        NativeSpriteMetadataExtractor.NativeSpriteMetadata nativeMetadata = getNativeSpriteMetadata();
        if (nativeMetadata != null) {
            builder.append("nativeIcon=").append(nativeMetadata.iconName).append('|');
            builder.append("nativeFrames=").append(nativeMetadata.frameCount).append('|');
            builder.append("nativeAtlas=").append(nativeMetadata.atlasTexture).append('|');
        }
        return builder.toString();
    }

    public String getRenderSignatureFilePath() {
        String outputPath = getOutputFilePath();
        if (outputPath == null) {
            return null;
        }
        if (outputPath.endsWith(".png") || outputPath.endsWith(".gif")) {
            return outputPath.substring(0, outputPath.length() - 4) + ".render-signature.json";
        }
        return outputPath + ".render-signature.json";
    }

    /**
     * Increment the frame index for multi-frame capture.
     */
    public void incrementFrame() {
        frameIndex++;
    }

    /**
     * Get the current frame index.
     */
    public int getFrameIndex() {
        return frameIndex;
    }

    /**
     * Check if this job has captured all required frames.
     */
    public boolean isCaptureComplete() {
        if (totalFrames == null) {
            totalFrames = ConfigOptions.GIF_FRAMES.get();
        }
        return frameIndex >= totalFrames;
    }

    /**
     * Reset the frame capture state for re-use.
     */
    public void resetCapture() {
        frameIndex = 0;
        totalFrames = null;
    }

    public int getRequestedFrameCount() {
        if (getType() == JobType.ENTITY && getEntity() != null) {
            return getEntity().getFrameCount();
        }
        return ConfigOptions.GIF_FRAMES.get();
    }

    public int getRequestedFrameDelayMs() {
        if (getType() == JobType.ENTITY && getEntity() != null) {
            return getEntity().getFrameDelayMs();
        }
        return GifRenderer.DEFAULT_CAPTURE_FRAME_DELAY_MS;
    }
}
