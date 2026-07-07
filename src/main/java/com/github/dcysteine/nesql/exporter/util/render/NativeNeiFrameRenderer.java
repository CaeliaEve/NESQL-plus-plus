package com.github.dcysteine.nesql.exporter.util.render;

import codechicken.nei.PositionedStack;
import codechicken.nei.guihook.GuiContainerManager;
import codechicken.nei.recipe.GuiCraftingRecipe;
import codechicken.nei.recipe.GuiRecipe;
import codechicken.nei.recipe.GuiUsageRecipe;
import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.IRecipeHandler;
import codechicken.nei.recipe.IUsageHandler;
import com.github.dcysteine.nesql.exporter.main.Logger;
import cpw.mods.fml.client.FMLClientHandler;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/** Captures whole in-game NEI recipe widgets into strict per-recipe PNG frames. */
public enum NativeNeiFrameRenderer {
    INSTANCE;

    private static final String IMAGE_FORMAT_PNG = "PNG";
    private static final int MAX_FRAMES_PER_TICK = 4;

    private Framebuffer framebuffer;
    private int framebufferWidth;
    private int framebufferHeight;
    private ByteBuffer readbackByteBuffer;
    private int[] readbackPixels;
    private int[] flippedPixels;

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    @SuppressWarnings("unused")
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (FMLClientHandler.instance().getWorldClient() == null) {
            return;
        }
        if (!NativeNeiFrameRenderDispatcher.INSTANCE.hasJobs()) {
            return;
        }

        int rendered = 0;
        while (rendered < MAX_FRAMES_PER_TICK) {
            Optional<NativeNeiFrameRenderRequest> requestOptional =
                    NativeNeiFrameRenderDispatcher.INSTANCE.getJob();
            if (!requestOptional.isPresent()) {
                break;
            }

            NativeNeiFrameRenderRequest request = requestOptional.get();
            try {
                BufferedImage image = renderIsolatedFrame(request);
                writeFrame(request, image);
                NativeNeiFrameRenderDispatcher.INSTANCE.completeJob(request);
            } catch (Throwable failure) {
                NativeNeiFrameRenderDispatcher.INSTANCE.failJob(request, failure);
            }
            rendered++;
        }
    }

    private BufferedImage renderIsolatedFrame(NativeNeiFrameRenderRequest request) throws Exception {
        int width = Math.max(1, request.getWidth());
        int height = Math.max(1, request.getHeight());
        ensureFramebuffer(width, height);

        GuiScreen previousScreen = installRecipeScreenContext(request, width, height);
        AngelicaGlStateSnapshot glStateSnapshot = AngelicaGlStateSnapshot.capture();
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        try {
            resetFrameRenderState(width, height);
            clearBuffer();
            renderRecipeWidget(request);
            checkOpenGlError(request, "render");
            BufferedImage image = readImage(width, height);
            checkOpenGlError(request, "readback");
            return image;
        } finally {
            try {
                RenderHelper.disableStandardItemLighting();
                GL11.glColor4f(1f, 1f, 1f, 1f);
                GL11.glDisable(GL11.GL_BLEND);
                GL11.glDisable(GL11.GL_ALPHA_TEST);
                GL11.glDisable(GL11.GL_DEPTH_TEST);
                OpenGlHelper.setActiveTexture(OpenGlHelper.lightmapTexUnit);
                GL11.glDisable(GL11.GL_TEXTURE_2D);
                OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
                GL11.glEnable(GL11.GL_TEXTURE_2D);
            } catch (Throwable ignored) {
            }
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPopAttrib();
            glStateSnapshot.restore(null);
            Minecraft.getMinecraft().currentScreen = previousScreen;
        }
    }

    /**
     * Some NEI handlers render by consulting {@code Minecraft.currentScreen} and casting it back to
     * {@link GuiRecipe}; MobsInfo's scrollable handler is one example. Native frame capture runs
     * from the NESQL progress GUI, so direct handler.drawBackground calls otherwise crash with
     * ClassCastException even though the same handler renders correctly inside NEI.
     *
     * <p>Install a minimal real GuiRecipe that contains only the request handler for the duration of
     * the off-screen frame. This keeps the renderer on the strict in-game NEI path instead of adding
     * heuristic fallbacks or skipping recipe frames.</p>
     */
    private GuiScreen installRecipeScreenContext(
            NativeNeiFrameRenderRequest request,
            int frameWidth,
            int frameHeight) throws Exception {
        Minecraft minecraft = Minecraft.getMinecraft();
        GuiScreen previousScreen = minecraft.currentScreen;
        GuiRecipe<?> recipeScreen = createRecipeScreen(request.getHandler());
        minecraft.currentScreen = recipeScreen;
        try {
            int screenWidth = previousScreen != null && previousScreen.width > 0
                    ? previousScreen.width
                    : Math.max(176, frameWidth);
            int screenHeight = previousScreen != null && previousScreen.height > 0
                    ? previousScreen.height
                    : Math.max(166, frameHeight + 68);
            recipeScreen.setWorldAndResolution(minecraft, screenWidth, screenHeight);
            return previousScreen;
        } catch (Throwable initializationFailure) {
            minecraft.currentScreen = previousScreen;
            throw initializationFailure;
        }
    }

    private GuiRecipe<?> createRecipeScreen(IRecipeHandler handler) throws Exception {
        if (handler instanceof ICraftingHandler) {
            ArrayList<ICraftingHandler> handlers = new ArrayList<ICraftingHandler>();
            handlers.add((ICraftingHandler) handler);
            GuiRecipe<?> recipeScreen = instantiateRecipeScreen(
                    GuiCraftingRecipe.class,
                    handlers,
                    Boolean.TRUE);
            tryLimitToOneRecipe(recipeScreen);
            return recipeScreen;
        }
        if (handler instanceof IUsageHandler) {
            ArrayList<IUsageHandler> handlers = new ArrayList<IUsageHandler>();
            handlers.add((IUsageHandler) handler);
            GuiRecipe<?> recipeScreen = instantiateRecipeScreen(
                    GuiUsageRecipe.class,
                    handlers);
            tryLimitToOneRecipe(recipeScreen);
            return recipeScreen;
        }
        throw new IllegalArgumentException(
                "Native NEI frame handler is neither crafting nor usage handler: "
                        + handler.getClass().getName());
    }

    private GuiRecipe<?> instantiateRecipeScreen(
            Class<? extends GuiRecipe> recipeScreenClass,
            ArrayList<?> handlers,
            Object... legacyExtraArguments) throws Exception {
        try {
            Constructor<? extends GuiRecipe> constructor =
                    recipeScreenClass.getDeclaredConstructor(ArrayList.class);
            constructor.setAccessible(true);
            return constructor.newInstance(handlers);
        } catch (NoSuchMethodException ignored) {
            Class<?>[] parameterTypes = new Class<?>[legacyExtraArguments.length + 1];
            Object[] arguments = new Object[legacyExtraArguments.length + 1];
            parameterTypes[0] = ArrayList.class;
            arguments[0] = handlers;
            for (int i = 0; i < legacyExtraArguments.length; i++) {
                Object argument = legacyExtraArguments[i];
                parameterTypes[i + 1] = argument instanceof Boolean ? Boolean.TYPE : argument.getClass();
                arguments[i + 1] = argument;
            }
            Constructor<? extends GuiRecipe> constructor =
                    recipeScreenClass.getDeclaredConstructor(parameterTypes);
            constructor.setAccessible(true);
            return constructor.newInstance(arguments);
        }
    }

    private void tryLimitToOneRecipe(GuiRecipe<?> recipeScreen) {
        if (recipeScreen == null) {
            return;
        }
        try {
            recipeScreen.getClass().getMethod("limitToOneRecipe").invoke(recipeScreen);
        } catch (NoSuchMethodException ignored) {
            // NEI 2.8.x removed this setter and reports false from isLimitedToOneRecipe().
            // The off-screen context still contains exactly one handler/recipe, which is all
            // handler drawBackground/drawForeground implementations need during capture.
        } catch (Throwable failure) {
            Logger.MOD.debug(
                    "Could not enable single-recipe mode on native NEI frame context {}",
                    recipeScreen.getClass().getName(),
                    failure);
        }
    }

    private void ensureFramebuffer(int width, int height) {
        if (framebuffer != null && framebufferWidth == width && framebufferHeight == height) {
            return;
        }
        if (framebuffer != null) {
            framebuffer.deleteFramebuffer();
        }
        framebuffer = new Framebuffer(width, height, true);
        framebufferWidth = width;
        framebufferHeight = height;
        int pixelCount = width * height;
        readbackByteBuffer = BufferUtils.createByteBuffer(4 * pixelCount);
        readbackPixels = new int[pixelCount];
        flippedPixels = new int[pixelCount];
    }

    private void resetFrameRenderState(int width, int height) {
        framebuffer.bindFramebuffer(true);
        OpenGlHelper.func_153171_g(GL30.GL_READ_FRAMEBUFFER, framebuffer.framebufferObject);
        GL11.glViewport(0, 0, width, height);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glLoadIdentity();
        GL11.glOrtho(0.0D, (double) width, (double) height, 0.0D, -100.0D, 100.0D);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glLoadIdentity();
        GL11.glColor4f(1f, 1f, 1f, 1f);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
    }

    private void renderRecipeWidget(NativeNeiFrameRenderRequest request) {
        prepareCachedRecipeForNativeDraw(request);
        safeOnUpdate(request);
        GuiContainerManager.enable2DRender();
        GL11.glColor4f(1f, 1f, 1f, 1f);
        request.getHandler().drawBackground(request.getRecipeIndex());

        RenderHelper.enableGUIStandardItemLighting();
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240.0f, 240.0f);
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
        GL11.glColor4f(1f, 1f, 1f, 1f);

        List<PositionedStack> inputs = safeStacks(new StackSupplier() {
            @Override
            public List<PositionedStack> get() {
                return request.getHandler().getIngredientStacks(request.getRecipeIndex());
            }
        });
        for (PositionedStack stack : inputs) {
            drawPositionedStack(stack);
        }

        PositionedStack result = safeResult(request);
        List<PositionedStack> others = safeStacks(new StackSupplier() {
            @Override
            public List<PositionedStack> get() {
                return request.getHandler().getOtherStacks(request.getRecipeIndex());
            }
        });
        if (result == null) {
            for (PositionedStack stack : others) {
                drawPositionedStack(stack);
            }
        } else {
            for (PositionedStack stack : others) {
                drawPositionedStack(stack);
            }
            drawPositionedStack(result);
        }

        RenderHelper.disableStandardItemLighting();
        GuiContainerManager.enable2DRender();
        GL11.glColor4f(1f, 1f, 1f, 1f);
        request.getHandler().drawForeground(request.getRecipeIndex());
    }

    private static void prepareCachedRecipeForNativeDraw(NativeNeiFrameRenderRequest request) {
        if (request == null || request.getHandler() == null) {
            return;
        }
        try {
            Object frontend = readFieldValue(request.getHandler(), "frontend");
            if (frontend == null
                    || !frontend.getClass().getName().contains("PurificationUnitParticleExtractorFrontend")) {
                return;
            }

            Object recipe = cachedRecipe(request);
            if (recipe == null) {
                return;
            }
            ensureListFieldSize(recipe, "mInputs", 2);
        } catch (Throwable failure) {
            Logger.MOD.debug(
                    "Could not prepare native NEI cached recipe slots for {}",
                    request.getAssetRef(),
                    failure);
        }
    }

    private static Object cachedRecipe(NativeNeiFrameRenderRequest request) throws Exception {
        Object recipesObject = readFieldValue(request.getHandler(), "arecipes");
        if (!(recipesObject instanceof List)) {
            return null;
        }
        List<?> recipes = (List<?>) recipesObject;
        int recipeIndex = request.getRecipeIndex();
        if (recipeIndex < 0 || recipeIndex >= recipes.size()) {
            return null;
        }
        return recipes.get(recipeIndex);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void ensureListFieldSize(Object target, String fieldName, int minimumSize) throws Exception {
        Object value = readFieldValue(target, fieldName);
        if (!(value instanceof List)) {
            return;
        }
        List list = (List) value;
        while (list.size() < minimumSize) {
            list.add(null);
        }
    }

    private static Object readFieldValue(Object target, String fieldName) throws Exception {
        if (target == null) {
            return null;
        }
        Class<?> current = target.getClass();
        while (current != null) {
            try {
                Field field = current.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private static void safeOnUpdate(NativeNeiFrameRenderRequest request) {
        try {
            request.getHandler().onUpdate();
        } catch (Throwable ignored) {
        }
    }

    private static void drawPositionedStack(PositionedStack stack) {
        if (stack == null || stack.item == null) {
            return;
        }
        GuiContainerManager.drawItem(stack.relx, stack.rely, stack.item);
    }

    private static PositionedStack safeResult(NativeNeiFrameRenderRequest request) {
        try {
            return request.getHandler().getResultStack(request.getRecipeIndex());
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static List<PositionedStack> safeStacks(StackSupplier supplier) {
        try {
            List<PositionedStack> stacks = supplier.get();
            return stacks == null ? Collections.<PositionedStack>emptyList() : stacks;
        } catch (Throwable ignored) {
            return Collections.emptyList();
        }
    }

    private interface StackSupplier {
        List<PositionedStack> get();
    }

    private BufferedImage readImage(int width, int height) {
        ensureReadbackBuffers(width, height);
        readbackByteBuffer.clear();
        GL11.glReadPixels(0, 0, width, height, GL12.GL_BGRA, GL11.GL_UNSIGNED_BYTE, readbackByteBuffer);

        readbackByteBuffer.asIntBuffer().get(readbackPixels);
        for (int i = 0; i < readbackPixels.length; i++) {
            int x = i % width;
            int y = height - (i / width + 1);
            flippedPixels[i] = readbackPixels[x + width * y];
        }

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, width, height, flippedPixels, 0, width);
        return image;
    }

    private void ensureReadbackBuffers(int width, int height) {
        int pixelCount = width * height;
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
        GL11.glClearColor(0f, 0f, 0f, 0f);
        GL11.glClearDepth(1D);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
    }

    private void writeFrame(NativeNeiFrameRenderRequest request, BufferedImage image) throws Exception {
        File outputFile = request.getOutputFile();
        File parent = outputFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IllegalStateException("Could not create native NEI frame directory: " + parent.getAbsolutePath());
        }
        ImageIO.write(image, IMAGE_FORMAT_PNG, outputFile);
        if (!outputFile.isFile() || outputFile.length() <= 0L) {
            throw new IllegalStateException("Native NEI frame output is empty: " + outputFile.getAbsolutePath());
        }
    }

    private void checkOpenGlError(NativeNeiFrameRenderRequest request, String phase) {
        int error = GL11.glGetError();
        if (error != GL11.GL_NO_ERROR) {
            Logger.MOD.warn(
                    "OpenGL error {} during native NEI frame {} for {}",
                    error,
                    phase,
                    request == null ? "<null>" : request.getAssetRef());
            while (GL11.glGetError() != GL11.GL_NO_ERROR) {
                // Drain the queue so one handler does not poison later captures.
            }
        }
    }
}
