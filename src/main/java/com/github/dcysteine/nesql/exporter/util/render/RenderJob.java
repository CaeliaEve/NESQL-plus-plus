package com.github.dcysteine.nesql.exporter.util.render;

import com.github.dcysteine.nesql.exporter.main.config.ConfigOptions;
import com.github.dcysteine.nesql.exporter.util.IdUtil;
import com.google.auto.value.AutoOneOf;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import java.lang.reflect.Method;

/**
 * Note: this class doesn't quite conform to the {@link AutoOneOf} contract, as some of its contents
 * ({@link ItemStack}) are mutable. Shouldn't matter for our limited use-case, though.
 */
@AutoOneOf(RenderJob.JobType.class)
public abstract class RenderJob {
    public enum JobType {
        ITEM, FLUID
    }

    // Multi-frame capture state for GIF animation
    private int frameIndex = 0;
    private Integer totalFrames = null;
    private transient boolean nativeSpriteMetadataLoaded = false;
    private transient NativeSpriteMetadataExtractor.NativeSpriteMetadata nativeSpriteMetadata;

    public static RenderJob ofItem(ItemStack itemStack) {
        ItemStack newStack = itemStack.copy();
        newStack.stackSize = 1;
        return AutoOneOf_RenderJob.item(newStack);
    }

    public static RenderJob ofFluid(FluidStack fluidStack) {
        return AutoOneOf_RenderJob.fluid(fluidStack);
    }

    public abstract JobType getType();
    public abstract ItemStack getItem();
    public abstract FluidStack getFluid();

    public NativeSpriteMetadataExtractor.NativeSpriteMetadata getNativeSpriteMetadata() {
        if (!nativeSpriteMetadataLoaded) {
            nativeSpriteMetadata = NativeSpriteMetadataExtractor.extract(this);
            nativeSpriteMetadataLoaded = true;
        }
        return nativeSpriteMetadata;
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
        if (!ConfigOptions.EXPORT_GIF.get()) {
            return false;
        }

        NativeSpriteMetadataExtractor.NativeSpriteMetadata nativeMetadata = getNativeSpriteMetadata();
        if (nativeMetadata != null && Boolean.TRUE.equals(nativeMetadata.animated)) {
            return false;
        }

        // Method 0: Force-capture mode (capture ALL items and fluids)
        if (ConfigOptions.FORCE_ALL_ITEMS_ANIMATED.get()) {
            return true;
        }

        // Only items can have animated textures (for non-force mode)
        if (getType() != JobType.ITEM) {
            return false;
        }

        ItemStack stack = getItem();

        // Method 1: Check AnimatedItemRegistry (most reliable for known items)
        if (AnimatedItemRegistry.INSTANCE.isAnimatedItem(stack)) {
            return true;
        }

        // Method 2: Check GT5 animation interface
        if (hasGregTechAnimation(stack)) {
            return true;
        }

        // Method 3: Generic animated texture detection
        if (hasAnimatedTexture(stack)) {
            return true;
        }

        return false;
    }

    /**
     * Whether static-item early-stop should be disabled for this job.
     *
     * <p>For known animated sources we want a full capture window even if the first several
     * frames happen to be identical, because many GTNH animations update more slowly than the
     * static detector window.
     */
    public boolean shouldForceFullCapture() {
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
                || hasGregTechAnimation(stack)
                || hasAnimatedTexture(stack);
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

    /**
     * Check if an icon supports animation (implements IPatchedTextureAtlasSprite).
     */
    private boolean isAnimatedIcon(Object icon) {
        if (icon == null) {
            return false;
        }
        try {
            Class<?> patchedSpriteClass = Class.forName("com.mitchej123.hodgepodge.textures.IPatchedTextureAtlasSprite");
            return patchedSpriteClass.isInstance(icon);
        } catch (ClassNotFoundException e) {
            return false;
        }
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
        if (needsMultipleFrames()) {
            return path.replace(".png", ".gif");
        }
        return path;
    }

    public String getSpriteMetadataFilePath() {
        String basePath = getImageFilePath();
        if (basePath.endsWith(".png")) {
            return basePath.substring(0, basePath.length() - 4) + ".sprite.json";
        }
        if (basePath.endsWith(".gif")) {
            return basePath.substring(0, basePath.length() - 4) + ".sprite.json";
        }
        return basePath + ".sprite.json";
    }

    public String getNativeSpriteAtlasFilePath() {
        String basePath = getImageFilePath();
        if (basePath.endsWith(".png")) {
            return basePath.substring(0, basePath.length() - 4) + ".sprite-atlas.png";
        }
        if (basePath.endsWith(".gif")) {
            return basePath.substring(0, basePath.length() - 4) + ".sprite-atlas.png";
        }
        return basePath + ".sprite-atlas.png";
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
}
