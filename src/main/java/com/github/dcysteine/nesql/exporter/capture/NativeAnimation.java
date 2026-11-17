package com.github.dcysteine.nesql.exporter.capture;

import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.github.dcysteine.nesql.exporter.task.Slice;
import com.github.dcysteine.nesql.exporter.core.RendererClock;
import com.google.gson.JsonArray;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.IItemRenderer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;

import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Reviewed inventory renderers sampled on their native clocks. Atlas pixels and
 * the GT clock are restored within every bounded client slice, even on failure.
 * Shader renderers are deliberately not treated as ordinary sprite animations.
 */
final class NativeAnimation {
    private final List<TextureAtlasSprite> sprites = new ArrayList<>();
    private final List<ResourceLocation> atlases = new ArrayList<>();
    private final List<int[][]> timelines = new ArrayList<>();
    private final Object clock;
    private final RendererClock.Profile rendererClock;
    private final long timerResolution;
    private final int ticks, columns;
    private final String location;
    private final BufferedImage sheet;
    private final JsonArray frames = new JsonArray();
    private int captured;

    static NativeAnimation select(Facts.Icon request,IItemRenderer renderer) {
        if (request.item == null) return null;
        ItemStack stack = request.item;
        String item = stack.getItem().getClass().getName();
        String type = renderer == null ? "" : renderer.getClass().getName();
        if (type.equals("appeng.client.render.ItemRenderer")) {
            net.minecraft.block.Block block = net.minecraft.block.Block.getBlockFromItem(stack.getItem());
            if (block != null && block.getClass().getName().equals("appeng.block.misc.BlockSkyCompass")) {
                version("appliedenergistics2");
                Object info = MagicApi.invoke(block.getClass(), block, "getRendererInstance", new Class<?>[0]);
                Object nativeRenderer = MagicApi.invoke(info.getClass(), info, "getRendererInstance", new Class<?>[0]);
                type = nativeRenderer.getClass().getName();
            }
        }
        RendererClock.Profile profile = RendererClock.profile(type);
        if (profile != null) {
            version(profile.mod);
            if (!RendererClock.instrumented(type)) throw new Jobs.Fault("animation_unsupported", "Missing capture clock hook: " + type);
            long resolution = profile.sys ? org.lwjgl.Sys.getTimerResolution() : 1000;
            int cycle;
            try { cycle = RendererClock.cycleTicks(profile.units, resolution); }
            catch (IllegalArgumentException failure) { throw new Jobs.Fault("animation_limit", failure.toString()); }
            return new NativeAnimation(stack, null, profile, resolution, request.registry, cycle, type);
        }
        boolean singularity = item.equals("fox.spiteful.avaritia.items.ItemSingularity")
                && (renderer == null || type.equals("fox.spiteful.avaritia.render.FancyHaloRenderer"));
        Object clock = null;
        boolean rotation = type.equals("gregtech.common.render.items.TranscendentalMetaItemRenderer")
                || type.equals("gregtech.common.render.items.TranscendentMetalRenderer");
        boolean gtLayers = type.equals("gregtech.common.render.items.GeneratedMaterialRenderer")
                || type.equals("gregtech.common.render.items.GeneratedItemRenderer")
                || type.equals("gregtech.common.render.items.CosmicNeutroniumRenderer")
                || type.equals("gregtech.common.render.items.CosmicNeutroniumMetaItemRenderer");
        if (gtLayers) version("gregtech_nh");
        if (type.equals("gregtech.common.render.items.CosmicNeutroniumMetaItemRenderer") && !neutroniumMetaIcon(stack)) return null;
        if (!singularity && !rotation) {
            // Default multi-pass items can have animated base/overlay sprites too.
            // Unknown custom renderers may use unrelated UVs/clocks: never infer equivalence.
            if (!gtLayers && (renderer != null || stack.getItem() instanceof net.minecraft.item.ItemBlock
                    || !stack.getItem().requiresMultipleRenderPasses())) return null;
            NativeAnimation layers = new NativeAnimation(stack, null, null, 1000, request.registry, 1, type);
            return layers.ticks == 1 ? null : layers;
        }
        if (singularity) version("Avaritia");
        if (rotation) {
            version("gregtech_nh");
            Object settings = MagicApi.field(MagicApi.type("gregtech.common.config.Client"), null, "render");
            if (!Boolean.TRUE.equals(MagicApi.field(settings, "renderTransMetalFancy"))) return null;
            clock = MagicApi.invoke(MagicApi.type("gregtech.GTMod"), null, "clientProxy", new Class<?>[0]);
        }
        return new NativeAnimation(stack, clock, null, 1000, request.registry, rotation ? 720 : 1, type);
    }

    private NativeAnimation(ItemStack stack, Object clock, RendererClock.Profile rendererClock, long timerResolution, String registry, int cycle, String renderer) {
        this.clock = clock;
        this.rendererClock = rendererClock; this.timerResolution = timerResolution;
        ResourceLocation atlas = atlas(stack.getItem().getSpriteNumber());
        int passes = renderer.equals("gregtech.common.render.items.CosmicNeutroniumMetaItemRenderer") ? 1
                : stack.getItem().requiresMultipleRenderPasses() ? stack.getItem().getRenderPasses(stack.getItemDamage()) : 1;
        if (rendererClock == null && (passes < 1 || passes > 16)) throw new Jobs.Fault("animation_limit", "Invalid native render pass count");
        boolean material = renderer.equals("gregtech.common.render.items.GeneratedMaterialRenderer")
                || renderer.equals("gregtech.common.render.items.TranscendentMetalRenderer")
                || renderer.equals("gregtech.common.render.items.CosmicNeutroniumRenderer");
        boolean generated = renderer.equals("gregtech.common.render.items.GeneratedItemRenderer")
                || renderer.equals("gregtech.common.render.items.TranscendentalMetaItemRenderer")
                || renderer.equals("gregtech.common.render.items.CosmicNeutroniumMetaItemRenderer");
        if (rendererClock == null && !material && !generated)
            for (int pass = 0; pass < passes; pass++) add(stack.getItem().getIcon(stack, pass), atlas);
        // GT material rendering reads base and overlay icons through its material API.
        boolean overlay = false, halo = false;
        if (material && MagicApi.type("gregtech.api.interfaces.IGT_ItemWithMaterialRenderer").isInstance(stack.getItem())) {
            Class<?> api = MagicApi.type("gregtech.api.interfaces.IGT_ItemWithMaterialRenderer");
            for (int pass = 0; pass < passes; pass++) {
                IIcon base = (IIcon) MagicApi.invoke(api, stack.getItem(), "getIcon", new Class<?>[] {int.class, int.class}, stack.getItemDamage(), pass);
                add(base, atlas); if (pass == 0 && base != null && renderer.equals("gregtech.common.render.items.CosmicNeutroniumRenderer")) halo = true;
                IIcon top = (IIcon) MagicApi.invoke(api, stack.getItem(), "getOverlayIcon", new Class<?>[] {int.class, int.class}, stack.getItemDamage(), pass);
                add(top, atlas); overlay |= top != null;
            }
        }
        if (generated) {
            IIcon[][] table = (IIcon[][]) MagicApi.field(stack.getItem(), "mIconList");
            int index = stack.getItemDamage() - ((Number) MagicApi.field(stack.getItem(), "mOffset")).intValue();
            IIcon[] icons = table[index]; int selected = 0;
            if (renderer.equals("gregtech.common.render.items.GeneratedItemRenderer") && icons.length > 1) {
                java.util.Map<?,?> stats = (java.util.Map<?,?>) MagicApi.field(stack.getItem(), "mElectricStats");
                Long[] electric = (Long[]) stats.get((short) stack.getItemDamage());
                if (electric != null && electric[3] < 0L) {
                    long charge = ((Number) MagicApi.invoke(stack.getItem().getClass(), stack.getItem(), "getRealCharge", new Class<?>[] {ItemStack.class}, stack)).longValue();
                    selected = charge <= 0 ? 1 : charge >= electric[0] ? 8 : 7 - (int) Math.max(0L, Math.min(5L, (electric[0] - charge) * 6L / electric[0]));
                }
            }
            add(icons[selected], TextureMap.locationItemsTexture);
            halo |= renderer.equals("gregtech.common.render.items.CosmicNeutroniumMetaItemRenderer") && icons[selected] != null;
        }
        if (halo) {
            // Installed neutronium renderers have no inventory clock/RNG use.
            // Their fuzzy halo is a separate native sprite, not a shader.
            Object fuzzy = MagicApi.field(MagicApi.type("gregtech.api.enums.Textures$ItemIcons"), null, "HALO_FUZZY");
            add((IIcon) MagicApi.invoke(fuzzy.getClass(), fuzzy, "getIcon", new Class<?>[0]), TextureMap.locationItemsTexture);
        }
        boolean largeCell = generated && renderer.equals("gregtech.common.render.items.GeneratedItemRenderer")
                && MagicApi.invoke(MagicApi.type(renderer), null, "getLargeFluidCell", new Class<?>[] {ItemStack.class}, stack) != null;
        if (overlay || largeCell) {
            net.minecraftforge.fluids.FluidStack fluid = (net.minecraftforge.fluids.FluidStack) MagicApi.invoke(
                    MagicApi.type("gregtech.api.util.GTUtility"), null, "getFluidForFilledItem", new Class<?>[] {ItemStack.class, boolean.class}, stack, true);
            if (fluid != null && fluid.getFluid() != null) add(fluid.getFluid().getIcon(fluid),
                    largeCell ? TextureMap.locationBlocksTexture : atlas(fluid.getFluid().getSpriteNumber()));
        }
        int[][] durations = new int[timelines.size()][];
        for (int i = 0; i < timelines.size(); i++) {
            int duration = 0; for (int[] step : timelines.get(i)) duration = Math.addExact(duration, step[1]);
            durations[i] = new int[] {duration};
        }
        ticks = period(cycle, durations);
        columns = (int) Math.ceil(Math.sqrt(ticks));
        sheet = new BufferedImage(columns * 64, ((ticks + columns - 1) / columns) * 64, BufferedImage.TYPE_INT_ARGB);
        location = registry + (rendererClock != null ? "#native-renderer-clock-cycle=" : clock == null ? "#native-layer-cycle=" : "#native-gt-rotation-cycle=") + ticks
                + (rendererClock == null ? "" : ";resolution=" + timerResolution);
    }

    private static void version(String id) { TargetAdmission.require("NativeAnimation", id); }

    /** The installed meta renderer returns without drawing for these native
     * empty states. It never asks for battery selection or render-pass callbacks. */
    private static boolean neutroniumMetaIcon(ItemStack stack) {
        if (!MagicApi.type("gregtech.api.items.MetaGeneratedItem").isInstance(stack.getItem())) return false;
        IIcon[][] table = (IIcon[][]) MagicApi.field(stack.getItem(), "mIconList");
        int index = stack.getItemDamage() - ((Number) MagicApi.field(stack.getItem(), "mOffset")).intValue();
        IIcon[] icons = table[index];
        return icons != null && icons.length > 0 && icons[0] != null;
    }

    private static ResourceLocation atlas(int number) { return number == 0 ? TextureMap.locationBlocksTexture : TextureMap.locationItemsTexture; }
    private void add(IIcon icon, ResourceLocation atlas) {
        if (!(icon instanceof TextureAtlasSprite)) return;
        TextureAtlasSprite sprite = (TextureAtlasSprite) icon;
        if (sprite.getFrameCount() <= 1 || sprites.contains(sprite)) return;
        Images.checkAnimation(sprite, 0xffffff);
        sprites.add(sprite); atlases.add(atlas); timelines.add(Images.timeline(sprite));
    }

    static int period(int cycle, int[][] durations) {
        if (cycle < 1 || cycle > 4096) throw new Jobs.Fault("animation_limit", "Native cycle must be within 1..4096 ticks");
        for (int[] duration : durations) {
            int ticks = duration[0];
            if (ticks <= 0) throw new Jobs.Fault("animation_invalid", "Native layer has no positive cycle");
            long next = (long) cycle / gcd(cycle, ticks) * ticks;
            if (next > 4096) throw new Jobs.Fault("animation_limit", "Native composite exceeds 4096 ticks");
            cycle = (int) next;
        }
        return cycle;
    }
    private static int gcd(int a, int b) { while (b != 0) { int next = a % b; a = b; b = next; } return a; }
    static int frame(int[][] timeline, int tick) {
        int duration = 0; for (int[] step : timeline) duration += step[1];
        tick %= duration;
        for (int[] step : timeline) { if (tick < step[1]) return step[0]; tick -= step[1]; }
        throw new IllegalStateException("Invalid native timeline");
    }
    /** Return the highest sprite mip level that the bound atlas is allowed to expose. */
    static int mipLevelLimit(int textureMaxLevel, int width, int height) {
        if (textureMaxLevel < 0 || width <= 0 || height <= 0) return -1;
        int limit = Math.min(12, textureMaxLevel);
        int level = 0;
        while (level < limit && (width >> (level + 1)) > 0 && (height >> (level + 1)) > 0) level++;
        return level;
    }

    Images.Image advance(Images images, Facts.Icon request) throws Exception {
        if (ticks == 1) return new Images.Image(Images.decode(64, 64, images.render(request)), new JsonArray(), "capture", request.registry);
        // Keep the actual stitched pixels, including interpolation/mipmap state,
        // rather than reconstructing the caller's current frame approximately.
        try (GlState state = new GlState(location); Patches patches = new Patches(atlases, sprites)) {
            captured = Slice.run(captured, ticks, tick -> {
                for (int i = 0; i < sprites.size(); i++) patches.upload(i, timelines.get(i), tick);
                byte[][] rgba = {null};
                if (rendererClock == null) withClock(clock, tick, () -> rgba[0] = images.render(request));
                else try (RendererClock.Scope phase = RendererClock.at(tick, timerResolution)) { rgba[0] = images.render(request); }
                BufferedImage image = Images.decode(64, 64, rgba[0]);
                int x = (tick % columns) * 64, y = (tick / columns) * 64;
                sheet.setRGB(x, y, 64, 64, image.getRGB(0, 0, 64, 64, null, 0, 64), 0, 64);
                frames.add(object("x", x, "y", y, "width", 64, "height", 64, "ticks", 1));
                return 64 * 64 * 4;
            });
        }
        return captured == ticks ? new Images.Image(sheet, frames, "capture", location) : null;
    }

    static void withClock(Object clock, long tick, Runnable draw) {
        if (clock == null) { draw.run(); return; }
        Field field = null;
        for (Class<?> type = clock.getClass(); type != null && field == null; type = type.getSuperclass()) {
            try { field = type.getDeclaredField("mAnimationTick"); } catch (NoSuchFieldException ignored) { }
        }
        if (field == null || field.getType() != long.class) throw new Jobs.Fault("animation_unsupported", "Missing GT native animation clock");
        field.setAccessible(true);
        try {
            long previous = field.getLong(clock);
            try { field.setLong(clock, tick); draw.run(); } finally { field.setLong(clock, previous); }
        } catch (IllegalAccessException failure) { throw new Jobs.Fault("animation_unsupported", failure.toString()); }
    }

    static final class Patches implements AutoCloseable {
        private final List<Patch> patches = new ArrayList<>();
        private final List<Integer> textures = new ArrayList<>();
        Patches(List<ResourceLocation> atlases, List<TextureAtlasSprite> sprites) {
            if (sprites.isEmpty()) return;
            CompressedReadback readback = new CompressedReadback();
            OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
            for (int i = 0; i < sprites.size(); i++) {
                Minecraft.getMinecraft().getTextureManager().bindTexture(atlases.get(i));
                int texture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
                textures.add(texture); patches.add(new Patch(texture, sprites.get(i), readback));
            }
        }
        void upload(int index, int[][] timeline, int tick) {
            OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, textures.get(index));
            patches.get(index).upload(timeline, tick);
        }
        @Override public void close() {
            if (patches.isEmpty()) return;
            OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
            for (int i = 0; i < patches.size(); i++) {
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, textures.get(i)); patches.get(i).close();
            }
        }
    }

    /** Preserve each used region; compressed formats use bounded encoded-mip staging. */
    static final class Patch implements AutoCloseable {
        final TextureAtlasSprite sprite; final List<ByteBuffer> previous = new ArrayList<>();
        final List<CompressedRegion> compressed = new ArrayList<>();
        Patch(int texture, TextureAtlasSprite sprite) {
            this(texture, sprite, new CompressedReadback());
        }
        private Patch(int texture, TextureAtlasSprite sprite, CompressedReadback readback) {
            this.sprite = sprite;
            int read = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING), fbo = GL30.glGenFramebuffers();
            try {
                GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, fbo);
                GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);
                GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1); GL11.glPixelStorei(GL11.GL_PACK_ROW_LENGTH, 0);
                GL11.glPixelStorei(GL11.GL_PACK_SKIP_ROWS, 0); GL11.glPixelStorei(GL11.GL_PACK_SKIP_PIXELS, 0);
                int baseLevel = GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_BASE_LEVEL);
                int maxLevel = GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL);
                if (baseLevel != 0)
                    throw new Jobs.Fault("animation_unsupported", "Native atlas base level must be zero: sprite="
                            + sprite.getIconName() + "; baseLevel=" + baseLevel);
                int lastLevel = mipLevelLimit(maxLevel, sprite.getIconWidth(), sprite.getIconHeight());
                if (lastLevel < 0)
                    throw new Jobs.Fault("animation_unsupported", "Native atlas has an invalid mip level limit: sprite="
                            + sprite.getIconName() + "; maxLevel=" + maxLevel);
                for (int level = 0; level <= lastLevel; level++) {
                    if (GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, level, GL11.GL_TEXTURE_WIDTH) == 0) break;
                    if (GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, level, GL13.GL_TEXTURE_COMPRESSED) != 0) {
                        compressed.add(readback.region(sprite, level));
                        previous.add(null);
                        continue;
                    }
                    GL30.glFramebufferTexture2D(GL30.GL_READ_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, texture, level);
                    GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
                    int status = GL30.glCheckFramebufferStatus(GL30.GL_READ_FRAMEBUFFER);
                    if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
                        int width = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, level, GL11.GL_TEXTURE_WIDTH);
                        int height = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, level, GL11.GL_TEXTURE_HEIGHT);
                        int format = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, level, GL11.GL_TEXTURE_INTERNAL_FORMAT);
                        boolean compressedFormat = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, level, GL13.GL_TEXTURE_COMPRESSED) != 0;
                        if (!compressedFormat) {
                            // Some 1.7.10 drivers reject an atlas level as an
                            // FBO attachment even though the texture is still
                            // readable. Read the level directly and crop the
                            // sprite region so this driver quirk does not
                            // erase an otherwise valid animation.
                            previous.add(readWholeLevel(texture, sprite, level, width, height));
                            compressed.add(null);
                            continue;
                        }
                        throw new Jobs.Fault("render_failed", "Cannot preserve native atlas region: sprite="
                                + sprite.getIconName() + "; texture=" + texture + "; level=" + level
                                + "; atlas=" + width + "x" + height
                                + "; region=" + (sprite.getOriginX() >> level) + "," + (sprite.getOriginY() >> level)
                                + "," + (sprite.getIconWidth() >> level) + "x" + (sprite.getIconHeight() >> level)
                                + "; format=0x" + Integer.toHexString(format) + "; compressed=" + compressedFormat
                                + "; status=0x" + Integer.toHexString(status)
                                + "; baseLevel=" + GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_BASE_LEVEL)
                                + "; maxLevel=" + GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL));
                    }
                    ByteBuffer bytes = BufferUtils.createByteBuffer((sprite.getIconWidth() >> level) * (sprite.getIconHeight() >> level) * 4);
                    GL11.glReadPixels(sprite.getOriginX() >> level, sprite.getOriginY() >> level, sprite.getIconWidth() >> level, sprite.getIconHeight() >> level, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, bytes);
                    ((java.nio.Buffer)bytes).rewind();
                    previous.add(bytes);
                    compressed.add(null);
                }
            } finally { GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, read); GL30.glDeleteFramebuffers(fbo); }
        }
        private static ByteBuffer readWholeLevel(int texture, TextureAtlasSprite sprite, int level, int width, int height) {
            int x = sprite.getOriginX() >> level, y = sprite.getOriginY() >> level;
            int w = Math.max(1, sprite.getIconWidth() >> level), h = Math.max(1, sprite.getIconHeight() >> level);
            if (width <= 0 || height <= 0 || x < 0 || y < 0 || x + w > width || y + h > height)
                throw new Jobs.Fault("render_failed", "Native atlas region is outside its mip level: sprite="
                        + sprite.getIconName() + "; texture=" + texture + "; level=" + level);
            long bytes = (long) width * height * 4L;
            if (bytes > 64L * 1024L * 1024L)
                throw new Jobs.Fault("texture_limit", "Native atlas mip exceeds readback budget: sprite=" + sprite.getIconName());
            ByteBuffer whole = BufferUtils.createByteBuffer((int) bytes);
            GL11.glGetTexImage(GL11.GL_TEXTURE_2D, level, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, whole);
            GlState.check(sprite.getIconName(), "native atlas direct readback");
            java.nio.Buffer wholeCursor = whole;
            wholeCursor.rewind();
            ByteBuffer saved = BufferUtils.createByteBuffer(w * h * 4);
            int stride = width * 4;
            for (int row = 0; row < h; row++) {
                ByteBuffer source = whole.duplicate();
                java.nio.Buffer sourceCursor = source;
                sourceCursor.position((y + row) * stride + x * 4);
                sourceCursor.limit(sourceCursor.position() + w * 4);
                saved.put(source);
            }
            java.nio.Buffer savedCursor = saved;
            savedCursor.flip();
            return saved;
        }
        void upload(int[][] timeline, int tick) {
            int duration = 0; for (int[] step : timeline) duration += step[1];
            int remaining = tick % duration, index = 0;
            while (remaining >= timeline[index][1]) remaining -= timeline[index++][1];
            int[][] data = sprite.getFrameTextureData(timeline[index][0]);
            int[][] next = sprite.getFrameTextureData(timeline[(index + 1) % timeline.length][0]);
            for (int level = 0; level < previous.size(); level++) {
                if (level >= data.length || data[level] == null) throw new Jobs.Fault("animation_missing", "Missing native mipmap frame: " + sprite.getIconName());
                ByteBuffer bytes = BufferUtils.createByteBuffer(data[level].length * 4);
                for (int p = 0; p < data[level].length; p++) {
                    int color = data[level][p];
                    if (Images.interpolated(sprite) && remaining > 0) {
                        if (level >= next.length || next[level] == null || next[level].length != data[level].length)
                            throw new Jobs.Fault("animation_missing", "Missing interpolated native frame");
                        color = NativeSamplers.interpolate(color, next[level][p], remaining, timeline[index][1]);
                    }
                    bytes.put((byte)(color >>> 16)).put((byte)(color >>> 8)).put((byte)color).put((byte)(color >>> 24));
                }
                ((java.nio.Buffer)bytes).flip(); transfer(level, bytes);
            }
        }
        private void transfer(int level, ByteBuffer bytes) {
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0);
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1); GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0); GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0);
            GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, level, sprite.getOriginX() >> level, sprite.getOriginY() >> level,
                    sprite.getIconWidth() >> level, sprite.getIconHeight() >> level, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, bytes);
        }
        @Override public void close() {
            for (int level = 0; level < previous.size(); level++) {
                if (compressed.get(level) != null) compressed.get(level).restore(level);
                else { ((java.nio.Buffer)previous.get(level)).rewind(); transfer(level, previous.get(level)); }
            }
        }
    }

    /** S3TC cannot be an FBO color attachment. Preserve encoded blocks instead
     * of decoding/recompressing (which can change pixels). GL 1.3 reads a whole
     * mip; one bounded staging buffer is reused across all sprites in a slice.
     * Only blocks touched by this sprite survive the constructor. */
    private static final class CompressedReadback {
        private static final int MAX_BYTES = 64 * 1024 * 1024;
        private ByteBuffer staging;

        CompressedRegion region(TextureAtlasSprite sprite, int level) {
            int format = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, level, GL11.GL_TEXTURE_INTERNAL_FORMAT);
            int blockBytes;
            switch (format) {
                case 0x83f0: case 0x83f1: blockBytes = 8; break; // DXT1 RGB / RGBA
                case 0x83f2: case 0x83f3: blockBytes = 16; break; // DXT3 / DXT5 RGBA
                default: throw new Jobs.Fault("render_failed", "Unreviewed compressed native atlas format: sprite="
                        + sprite.getIconName() + "; level=" + level + "; format=0x" + Integer.toHexString(format));
            }
            int width = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, level, GL11.GL_TEXTURE_WIDTH);
            int height = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, level, GL11.GL_TEXTURE_HEIGHT);
            int size = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, level, GL13.GL_TEXTURE_COMPRESSED_IMAGE_SIZE);
            long expected = ((width + 3L) / 4) * ((height + 3L) / 4) * blockBytes;
            int x = sprite.getOriginX() >> level, y = sprite.getOriginY() >> level;
            int w = sprite.getIconWidth() >> level, h = sprite.getIconHeight() >> level;
            if (width <= 0 || height <= 0 || x < 0 || y < 0 || w <= 0 || h <= 0
                    || (long)x + w > width || (long)y + h > height || size != expected || size > MAX_BYTES)
                throw new Jobs.Fault("render_failed", "Compressed native atlas exceeds validated readback bounds: sprite="
                        + sprite.getIconName() + "; level=" + level + "; atlas=" + width + "x" + height
                        + "; region=" + x + "," + y + "," + w + "x" + h + "; bytes=" + size);
            // S3TC subimage uploads must align to blocks, except a whole tiny mip.
            // Reject before ANY animation upload, not after partly mutating the atlas.
            if (x % 4 != 0 || y % 4 != 0 || w % 4 != 0 && w != width || h % 4 != 0 && h != height)
                throw new Jobs.Fault("animation_unsupported", "Compressed native sprite upload is not block-aligned: sprite="
                        + sprite.getIconName() + "; level=" + level + "; region=" + x + "," + y + "," + w + "x" + h);
            if (staging == null || staging.capacity() < size) staging = BufferUtils.createByteBuffer(size);
            ((java.nio.Buffer)staging).clear(); ((java.nio.Buffer)staging).limit(size);
            GL13.glGetCompressedTexImage(GL11.GL_TEXTURE_2D, level, staging);
            GlState.check(sprite.getIconName(), "compressed atlas readback");
            int bx = x / 4, by = y / 4, right = (x + w + 3) / 4, bottom = (y + h + 3) / 4;
            int rowBytes = (right - bx) * blockBytes, stride = ((width + 3) / 4) * blockBytes;
            ByteBuffer saved = BufferUtils.createByteBuffer(rowBytes * (bottom - by));
            for (int row = by; row < bottom; row++) {
                ByteBuffer source = staging.duplicate();
                ((java.nio.Buffer)source).position(row * stride + bx * blockBytes);
                ((java.nio.Buffer)source).limit(source.position() + rowBytes); saved.put(source);
            }
            ((java.nio.Buffer)saved).flip();
            return new CompressedRegion(format, bx * 4, by * 4,
                    Math.min(width, right * 4) - bx * 4, Math.min(height, bottom * 4) - by * 4, saved);
        }
    }

    private static final class CompressedRegion {
        final int format, x, y, width, height;
        final ByteBuffer bytes;
        CompressedRegion(int format, int x, int y, int width, int height, ByteBuffer bytes) {
            this.format = format; this.x = x; this.y = y; this.width = width; this.height = height; this.bytes = bytes;
        }
        void restore(int level) {
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0);
            ((java.nio.Buffer)bytes).rewind();
            GL13.glCompressedTexSubImage2D(GL11.GL_TEXTURE_2D, level, x, y, width, height, format, bytes);
        }
    }
}
