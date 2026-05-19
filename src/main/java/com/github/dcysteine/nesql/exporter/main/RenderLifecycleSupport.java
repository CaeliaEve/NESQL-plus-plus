package com.github.dcysteine.nesql.exporter.main;

import com.github.dcysteine.nesql.exporter.util.render.RenderDispatcher;
import com.github.dcysteine.nesql.exporter.util.render.Renderer;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Loader;
import net.minecraft.util.EnumChatFormatting;

import java.io.File;

/**
 * Shared renderer lifecycle support for export profiles that produce images.
 */
public final class RenderLifecycleSupport {
    private static boolean rendererHookRegistered = false;

    private RenderLifecycleSupport() {}

    public static boolean initializeRendererOrSkip(File imageDirectory) {
        Logger.chatMessage(EnumChatFormatting.AQUA + "Initializing renderer.");

        if (!imageDirectory.exists() && !imageDirectory.mkdirs()) {
            Logger.chatMessage(EnumChatFormatting.RED + "Could not create image directory!");
            Logger.chatMessage(EnumChatFormatting.RED + "Skipping rendering!");
            RenderDispatcher.INSTANCE.setRendererState(RenderDispatcher.RendererState.ERROR);
            return false;
        }

        ensureRendererHookRegistered();
        Renderer.INSTANCE.preinitialize(imageDirectory);
        RenderDispatcher.INSTANCE.setImageDirectory(imageDirectory);
        RenderDispatcher.INSTANCE.setRendererState(RenderDispatcher.RendererState.INITIALIZING);
        warnBugTorch();
        return true;
    }

    public static void initializeRendererOrThrow(File imageDirectory) throws Exception {
        Logger.chatMessage(EnumChatFormatting.AQUA + "Initializing renderer.");

        if (!imageDirectory.exists() && !imageDirectory.mkdirs()) {
            Logger.chatMessage(EnumChatFormatting.RED + "Could not create image directory!");
            throw new Exception("Failed to create image directory");
        }

        ensureRendererHookRegistered();
        Renderer.INSTANCE.preinitialize(imageDirectory);
        RenderDispatcher.INSTANCE.setImageDirectory(imageDirectory);
        RenderDispatcher.INSTANCE.setRendererState(RenderDispatcher.RendererState.INITIALIZING);
        warnBugTorch();
    }

    public static void awaitRenderCompletion() {
        Logger.chatMessage(EnumChatFormatting.AQUA + "Waiting for rendering to finish...");
        Logger.MOD.info("Remaining render jobs: {}", RenderDispatcher.INSTANCE.getJobCount());
        try {
            RenderDispatcher.INSTANCE.waitUntilJobsComplete();
        } catch (InterruptedException wakeUp) {
            Thread.currentThread().interrupt();
        }
        RenderDispatcher.INSTANCE.setRendererState(RenderDispatcher.RendererState.DESTROYING);
        Logger.chatMessage(EnumChatFormatting.AQUA + "Rendering complete!");
        int skippedDuplicates = RenderDispatcher.INSTANCE.getDuplicateJobSkipCount();
        if (skippedDuplicates > 0) {
            Logger.chatMessage(
                    EnumChatFormatting.GREEN
                            + "Skipped duplicate render jobs: "
                            + skippedDuplicates);
        }
        int skippedExistingOutputs = RenderDispatcher.INSTANCE.getExistingOutputSkipCount();
        if (skippedExistingOutputs > 0) {
            Logger.chatMessage(
                    EnumChatFormatting.GREEN
                            + "Skipped unchanged render outputs: "
                            + skippedExistingOutputs);
        }
    }

    private static void warnBugTorch() {
        if (Loader.isModLoaded("bugtorch")) {
            Logger.chatMessage(EnumChatFormatting.RED + "BugTorch mod appears to be loaded;");
            Logger.chatMessage(EnumChatFormatting.RED + "enchanted items might not render correctly!");
        }
    }

    private static synchronized void ensureRendererHookRegistered() {
        if (rendererHookRegistered) {
            return;
        }
        FMLCommonHandler.instance().bus().register(Renderer.INSTANCE);
        rendererHookRegistered = true;
        Logger.MOD.info("Renderer tick hook registered lazily for export.");
    }
}
