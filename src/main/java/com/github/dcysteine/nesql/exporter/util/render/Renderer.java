package com.github.dcysteine.nesql.exporter.util.render;

import codechicken.lib.gui.GuiDraw;
import codechicken.nei.guihook.GuiContainerManager;
import com.github.dcysteine.nesql.exporter.main.Logger;
import com.github.dcysteine.nesql.exporter.main.config.ConfigOptions;
import cpw.mods.fml.client.FMLClientHandler;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.texture.ITextureObject;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.util.IIcon;
import net.minecraftforge.fluids.FluidStack;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/** Singleton class that handles rendering items and fluids and saving the resulting image data. */
public enum Renderer {
    INSTANCE;

    private static final String IMAGE_FORMAT_PNG = "PNG";
    public static final String IMAGE_FILE_EXTENSION = get_image_file_extension();

    public static String get_image_file_extension() {
        return ".png";
    }

    private int imageDim;
    private File imageDirectory;
    private Framebuffer framebuffer;
    private ByteBuffer readbackByteBuffer;
    private int[] readbackPixels;
    private int[] flippedPixels;

    // Used for intermittent logging.
    private int loggingCounter;

    /**
     * This method is meant to be called from the client thread, prior to setting the dispatcher
     * state to {@code INITIALIZING}. It performs initialization of non-render-related variables.
     */
    public void preinitialize(File imageDirectory) {
        this.imageDim = ConfigOptions.ICON_DIMENSION.get();
        this.imageDirectory = imageDirectory;
        this.loggingCounter = 0;
    }

    /**
     * Perform initialization of render-related variables, which must be done on the render thread.
     */
    private void initialize() {
        this.framebuffer = new Framebuffer(imageDim, imageDim, true);
        int pixelCount = imageDim * imageDim;
        this.readbackByteBuffer = BufferUtils.createByteBuffer(4 * pixelCount);
        this.readbackPixels = new int[pixelCount];
        this.flippedPixels = new int[pixelCount];
        RenderDispatcher.INSTANCE.setImageDirectory(imageDirectory);

        RenderDispatcher.INSTANCE.setRendererState(RenderDispatcher.RendererState.INITIALIZED);
    }

    /**
     * Perform destruction of render-related variables, which must be done on the render thread.
     */
    private void destroy() {
        framebuffer.deleteFramebuffer();
        framebuffer = null;
        readbackByteBuffer = null;
        readbackPixels = null;
        flippedPixels = null;

        RenderDispatcher.INSTANCE.setRendererState(RenderDispatcher.RendererState.UNINITIALIZED);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    @SuppressWarnings("unused")
    public void onClientTick(TickEvent.ClientTickEvent event) {
        // 只在阶段结束时处理，避免每tick处理多次
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        // 确保在游戏界面中渲染，不在菜单界面
        if (FMLClientHandler.instance().getWorldClient() == null) {
            return;
        }
        switch (RenderDispatcher.INSTANCE.getRendererState()) {
            case UNINITIALIZED:
            case ERROR:
                return;

            case INITIALIZING:
                initialize();
                return;

            case INITIALIZED:
                break;

            case DESTROYING:
                destroy();
                return;

            default:
                throw new IllegalArgumentException(
                        "Unrecognized render state: "
                                + RenderDispatcher.INSTANCE.getRendererState());
        }

        RenderDispatcher.INSTANCE.beginClientTick();

        if (RenderDispatcher.INSTANCE.noJobsRemaining()) {
            RenderDispatcher.INSTANCE.notifyJobsCompleted();
            return;
        }

        if (Logger.intermittentLog(++loggingCounter)) {
            Logger.MOD.info("Remaining render jobs: {}", RenderDispatcher.INSTANCE.getTotalJobCount());
            loggingCounter = 0;
        }

        setupRenderState();
        try {
            int iconsPerTick = Math.max(ConfigOptions.RENDER_ICONS_PER_TICK.get(), 1024);

            for (int i = 0; i < iconsPerTick; i++) {
                Optional<RenderJob> jobOptional = RenderDispatcher.INSTANCE.getJob();
                if (!jobOptional.isPresent()) {
                    break;
                }

                RenderJob job = jobOptional.get();
                RenderContractMetadataExtractor.writeIfAvailable(job, imageDirectory);
                if (job.shouldWriteNativeSpriteMetadata()) {
                    NativeSpriteMetadataExtractor.writeIfAvailable(job, imageDirectory);
                }
                clearBuffer();
                render(job);
                BufferedImage image = readImage(job);

                // Handle multi-frame GIF capture vs single-frame PNG
                if (job.needsMultipleFrames()) {
                    advanceTextureAnimations(job);
                    // Multi-frame: Handled by RenderDispatcher
                    RenderDispatcher.INSTANCE.completeJob(job, image);
                } else {
                    // Single-frame: Write directly to file
                    File outputFile = new File(imageDirectory, job.getImageFilePath());
                    // Not sure why, but this check fails spuriously every now and then.
                    // It complains that the file exists, but I checked and it didn't actually exist.
                    // Let's just... ignore it for now XD
                    // The failures might be due to Windows getting confused by '~' in filenames XS
                    /*
                    if (outputFile.exists()) {
                        // If we cannot avoid queueing up duplicate render jobs, we can replace this
                        // throw with a continue, and move this check to before we call readImage(job)
                        throw new RuntimeException(
                                "Render output file already exists: " + outputFile.getPath());
                    }
                     */

                    File parentDir = outputFile.getParentFile();
                    if (parentDir.exists() && !parentDir.isDirectory()) {
                        throw new RuntimeException(
                                "Render output file directory already exists as a file: "
                                        + parentDir.getPath());
                    } else if (!parentDir.exists() && !parentDir.mkdirs()) {
                        throw new RuntimeException(
                                "Could not create render output file directory: "
                                        + parentDir.getPath());
                    }

                    try {
                        ImageIO.write(image, IMAGE_FORMAT_PNG, outputFile);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        } finally {
            teardownRenderState();
        }
    }

    private void advanceTextureAnimations(RenderJob job) {
        if (job == null || !job.shouldAdvanceTextureAtlasBetweenFrames()) {
            return;
        }

        job.markAnimatedTexturesForUpdate();

        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null) {
            return;
        }

        Object blocksAtlas = minecraft.getTextureMapBlocks();
        updateTextureAnimations(blocksAtlas);

        try {
            ITextureObject itemsAtlasObject = minecraft.getTextureManager().getTexture(TextureMap.locationItemsTexture);
            if (itemsAtlasObject != null && itemsAtlasObject != blocksAtlas) {
                updateTextureAnimations(itemsAtlasObject);
            }
        } catch (Throwable ignored) {
        }
    }

    private void updateTextureAnimations(Object textureObject) {
        if (textureObject == null) {
            return;
        }

        try {
            if (textureObject instanceof TextureMap) {
                ((TextureMap) textureObject).updateAnimations();
                return;
            }

            java.lang.reflect.Method method = textureObject.getClass().getMethod("updateAnimations");
            method.invoke(textureObject);
        } catch (Throwable ignored) {
        }
    }

    private void render(RenderJob job) {
        switch (job.getType()) {
            case ITEM:
                GuiContainerManager.drawItem(0, 0, job.getItem());
                break;

            case FLUID:
                FluidStack fluidStack = job.getFluid();
                IIcon icon = fluidStack.getFluid().getIcon(fluidStack);
                // Some fluids don't set their icon colour, so we have to blend in the colour.
                int colour = fluidStack.getFluid().getColor(fluidStack);
                GL11.glColor3ub(
                        (byte) ((colour & 0xFF0000) >> 16),
                        (byte) ((colour & 0x00FF00) >> 8),
                        (byte) (colour & 0x0000FF));

                GuiDraw.changeTexture(TextureMap.locationBlocksTexture);
                GuiDraw.gui.drawTexturedModelRectFromIcon(0, 0, icon, 16, 16);

                // Reset colour blending.
                GL11.glColor4f(1f, 1f, 1f, 1f);
                break;

            default:
                throw new IllegalArgumentException("Unrecognized job type: " + job);
        }
    }

    /** Returns the rendered image, in {@link BufferedImage#TYPE_INT_ARGB} format. */
    private BufferedImage readImage(RenderJob job) {
        ensureReadbackBuffers();
        readbackByteBuffer.clear();
        GL11.glReadPixels(
                0, 0, imageDim, imageDim,
                GL12.GL_BGRA, GL11.GL_UNSIGNED_BYTE, readbackByteBuffer);

        // OpenGL uses inverted y-coordinates compared to our draw methods.
        // So we must flip the saved image vertically.
        //
        // Unfortunately, for some reason, the rendering seems to break if we try to invert using
        // OpenGL matrix transforms, so let's just do this on the pixel array.
        readbackByteBuffer.asIntBuffer().get(readbackPixels);
        for (int i = 0; i < readbackPixels.length; i++) {
            int x = i % imageDim;
            int y = imageDim - (i / imageDim + 1);
            flippedPixels[i] = readbackPixels[x + imageDim * y];
        }

        BufferedImage image =
                new BufferedImage(imageDim, imageDim, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, imageDim, imageDim, flippedPixels, 0, imageDim);
        return image;
    }

    private void ensureReadbackBuffers() {
        int pixelCount = imageDim * imageDim;
        if (readbackByteBuffer == null || readbackByteBuffer.capacity() < 4 * pixelCount) {
            readbackByteBuffer = BufferUtils.createByteBuffer(4 * pixelCount);
        }
        if (readbackPixels == null || readbackPixels.length != pixelCount) {
            readbackPixels = new int[pixelCount];
        }
        if (flippedPixels == null || flippedPixels.length != pixelCount) {
            flippedPixels = new int[pixelCount];
        }
    }

    private void clearBuffer() {
        // Parameters are RGBA. Set full transparent background.
        GL11.glClearColor(0f, 0f, 0f, 0f);
        GL11.glClearDepth(1D);
        GL11.glClear(16384 | 256);
    }

    private void setupRenderState() {
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glLoadIdentity();
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glLoadIdentity();
        GL11.glOrtho(0.0, 1.0, 1.0, 0.0, -100.0, 100.0);
        double scaleFactor = 1 / 16.0;
        GL11.glScaled(scaleFactor, scaleFactor, scaleFactor);
        // We need to end with the model-view matrix selected. It's what the rendering code expects.
        GL11.glMatrixMode(GL11.GL_MODELVIEW);

        RenderHelper.enableGUIStandardItemLighting();
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);

        framebuffer.bindFramebuffer(true);
        // Do we need to bind GL_DRAW_FRAMEBUFFER here as well? Seems to work fine as-is though...
        OpenGlHelper.func_153171_g(GL30.GL_READ_FRAMEBUFFER, framebuffer.framebufferObject);
    }

    private void teardownRenderState() {
        framebuffer.unbindFramebuffer();
    }
}
