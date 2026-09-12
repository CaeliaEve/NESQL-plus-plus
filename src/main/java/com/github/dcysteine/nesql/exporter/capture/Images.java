package com.github.dcysteine.nesql.exporter.capture;

import codechicken.lib.gui.GuiDraw;
import codechicken.nei.guihook.GuiContainerManager;
import com.github.dcysteine.nesql.exporter.task.ClientThread;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonArray;
import cpw.mods.fml.relauncher.ReflectionHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.data.AnimationMetadataSection;
import net.minecraft.item.ItemBlock;
import net.minecraft.util.IIcon;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.IItemRenderer;
import net.minecraftforge.client.MinecraftForgeClient;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Only sprite copies and GL calls run on the client; pixel transforms and encoding run on the worker. */
final class Images implements AutoCloseable {
    private static final int SIZE = 64;
    private final Surface icons = new Surface();
    private final Surface scenes = new Surface();

    Image paint(Models.Paint paint, ClientThread.Session client) throws Exception {
        if (paint.sprite != null) return sprite(paint.sprite, client);
        if (paint.resource != null) return resource(paint.resource, client);
        return plain();
    }

    static Image plain() {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 0xffffffff);
        return new Image(image, new JsonArray(), "entity", "nesql:untextured");
    }

    Image picture(Facts.Picture picture, ClientThread.Session client) throws Exception {
        int width = picture.width * picture.scale, height = picture.height * picture.scale;
        byte[] rgba = client.call(() -> icons.capture(width, height, picture.width, picture.height, 1000, picture.location, picture.draw));
        return new Image(decode(width, height, rgba), new JsonArray(), "capture", picture.location);
    }

    Image scene(Facts.Scene scene, ClientThread.Session client) throws Exception {
        int width = scene.width * 2, height = scene.height * 2;
        byte[] rgba = client.call(() -> scenes.capture(width, height, scene.width, scene.height, 1000, scene.location, scene.draw));
        return new Image(decode(width, height, rgba), new JsonArray(), "capture", scene.location);
    }

    Image capture(Facts.Icon request, ClientThread.Session client) throws Exception {
        Plan plan = client.call(() -> plan(request));
        if (plan == null) {
            byte[] rgba = client.call(() -> render(request));
            return new Image(decode(SIZE, SIZE, rgba), new JsonArray(), "capture", request.registry);
        }
        return animation(plan, client);
    }

    Image sprite(TextureAtlasSprite sprite, ClientThread.Session client) throws Exception {
        Plan plan = client.call(() -> {
            Plan animated = animation(sprite, 0xffffff);
            return animated != null ? animated : new Plan(sprite, sprite.getIconWidth(), sprite.getIconHeight(), 0xffffff);
        });
        if (!plan.frames.isEmpty()) return animation(plan, client);
        int width = plan.width, height = plan.height;
        byte[] rgba = client.call(() -> icons.capture(width, height, width, height, 100, plan.location, () -> {
            RenderHelper.disableStandardItemLighting();
            GL11.glDisable(GL11.GL_BLEND); GL11.glDisable(GL11.GL_ALPHA_TEST); GL11.glDisable(GL11.GL_DEPTH_TEST);
            GuiDraw.changeTexture(TextureMap.locationBlocksTexture);
            GuiDraw.gui.drawTexturedModelRectFromIcon(0, 0, sprite, width, height);
        }));
        return new Image(decode(width, height, rgba), new JsonArray(), "capture", plan.location);
    }

    Image resource(ResourceLocation location, ClientThread.Session client) throws Exception {
        byte[] bytes = client.call(() -> {
            try (InputStream input = Minecraft.getMinecraft().getResourceManager().getResource(location).getInputStream()) {
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192]; int count;
                while ((count = input.read(buffer)) != -1) {
                    Jobs.checkpoint();
                    if (output.size() + count > 64 * 1024 * 1024) throw new Jobs.Fault("texture_limit", "Entity texture exceeds its byte budget");
                    output.write(buffer, 0, count);
                }
                return output.toByteArray();
            }
        });
        try (MemoryCacheImageInputStream input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            java.util.Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new Jobs.Fault("texture_invalid", "Entity texture has no image decoder");
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                if (!reader.getFormatName().equalsIgnoreCase("png") || reader.getWidth(0) < 1 || reader.getHeight(0) < 1
                        || reader.getWidth(0) > 4096 || reader.getHeight(0) > 4096) throw new Jobs.Fault("texture_limit", "Invalid entity texture dimensions or format");
                return new Image(reader.read(0), new JsonArray(), "resource", location.toString());
            } finally { reader.dispose(); }
        }
    }

    private Image animation(Plan plan, ClientThread.Session client) throws Exception {
        int count = plan.frames.size();
        int columns = Math.max(1, (plan.height * count + 16383) / 16384);
        int rows = (count + columns - 1) / columns;
        int width = plan.width * columns, height = plan.height * rows;
        if (width > 16384 || height > 16384 || (long) width * height > 16 * 1024 * 1024) {
            throw new Jobs.Fault("texture_limit", "Animation exceeds its pixel budget: " + plan.location);
        }
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        int index = 0;
        Map<Integer, Integer> positions = new LinkedHashMap<>();
        for (int frame : plan.frames) {
            Jobs.checkpoint();
            // Sprite arrays may contain holes; copy only the frames referenced by the timeline.
            int[] colors = client.call(() -> {
                int[][] levels = plan.sprite.getFrameTextureData(frame);
                if (levels == null || levels.length == 0 || levels[0] == null || levels[0].length != plan.width * plan.height) {
                    throw new Jobs.Fault("animation_missing", "Sprite frame is unavailable: " + plan.location + "/" + frame);
                }
                return levels[0].clone();
            });
            for (int pixel = 0; pixel < colors.length; pixel++) {
                int color = colors[pixel];
                int red = ((color >>> 16) & 255) * ((plan.tint >>> 16) & 255) / 255;
                int green = ((color >>> 8) & 255) * ((plan.tint >>> 8) & 255) / 255;
                int blue = (color & 255) * (plan.tint & 255) / 255;
                colors[pixel] = (color & 0xff000000) | (red << 16) | (green << 8) | blue;
            }
            image.setRGB((index % columns) * plan.width, (index / columns) * plan.height,
                    plan.width, plan.height, colors, 0, plan.width);
            positions.put(frame, index++);
        }
        JsonArray timeline = new JsonArray();
        for (int[] step : plan.timeline) {
            int position = positions.get(step[0]);
            timeline.add(object("x", (position % columns) * plan.width, "y", (position / columns) * plan.height,
                    "width", plan.width, "height", plan.height, "ticks", step[1]));
        }
        return new Image(image, timeline, "resource", plan.location);
    }

    private Plan plan(Facts.Icon request) {
        IIcon icon = request.item != null ? request.item.getIconIndex() : request.fluid.getFluid().getIcon(request.fluid);
        boolean flat = request.item == null || (!(request.item.getItem() instanceof ItemBlock)
                && !request.item.getItem().requiresMultipleRenderPasses()
                && MinecraftForgeClient.getItemRenderer(request.item, IItemRenderer.ItemRenderType.INVENTORY) == null);
        if (!(icon instanceof TextureAtlasSprite) || !flat) return null;
        return animation((TextureAtlasSprite) icon, request.item != null
                ? request.item.getItem().getColorFromItemStack(request.item, 0) : request.fluid.getFluid().getColor(request.fluid));
    }

    private Plan animation(TextureAtlasSprite sprite, int tint) {
        if (sprite.getFrameCount() <= 1) return null;
        AnimationMetadataSection metadata = ReflectionHelper.getPrivateValue(TextureAtlasSprite.class, sprite,
                "animationMetadata", "field_110982_k");
        if (metadata == null) throw new Jobs.Fault("animation_missing", "Animated sprite has no timeline: " + sprite.getIconName());
        int width = sprite.getIconWidth(), height = sprite.getIconHeight();
        if (width <= 0 || height <= 0 || width > 4096 || height > 4096) throw new Jobs.Fault("texture_limit", "Invalid sprite dimensions");
        int count = metadata.getFrameCount() == 0 ? sprite.getFrameCount() : metadata.getFrameCount();
        if (count > 4096) throw new Jobs.Fault("texture_limit", "Sprite timeline exceeds 4096 steps");
        Plan plan = new Plan(sprite, width, height, tint);
        for (int index = 0; index < count; index++) {
            int frame = metadata.getFrameCount() == 0 ? index : metadata.getFrameIndex(index);
            int ticks = metadata.getFrameCount() == 0 ? metadata.getFrameTime() : metadata.getFrameTimeSingle(index);
            if (frame < 0 || frame >= sprite.getFrameCount() || ticks <= 0 || ticks > 72000) {
                throw new Jobs.Fault("animation_invalid", "Invalid sprite timeline: " + sprite.getIconName());
            }
            if (!plan.frames.contains(frame)) plan.frames.add(frame);
            plan.timeline.add(new int[] { frame, ticks });
        }
        return plan;
    }

    private byte[] render(Facts.Icon request) {
        Jobs.checkpoint();
        IIcon icon = request.item != null ? request.item.getIconIndex() : request.fluid.getFluid().getIcon(request.fluid);
        if (icon == null && request.item == null) throw new Jobs.Fault("texture_missing", "Fluid has no texture: " + request.registry);
        return icons.capture(SIZE, SIZE, 16, 16, 100, request.registry, () -> {
            GL11.glEnable(GL12.GL_RESCALE_NORMAL);
            RenderHelper.enableGUIStandardItemLighting();
            if (request.item != null) GuiContainerManager.drawItem(0, 0, request.item);
            else {
                RenderHelper.disableStandardItemLighting();
                int color = request.fluid.getFluid().getColor(request.fluid);
                GL11.glColor4f(((color >>> 16) & 255) / 255f, ((color >>> 8) & 255) / 255f, (color & 255) / 255f, 1);
                GuiDraw.changeTexture(TextureMap.locationBlocksTexture);
                GuiDraw.gui.drawTexturedModelRectFromIcon(0, 0, icon, 16, 16);
            }
        });
    }

    private static BufferedImage decode(int width, int height, byte[] rgba) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        int[] pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            int offset = (y * width + x) * 4;
            pixels[(height - y - 1) * width + x] = ((rgba[offset + 3] & 255) << 24) | ((rgba[offset] & 255) << 16)
                    | ((rgba[offset + 1] & 255) << 8) | (rgba[offset + 2] & 255);
        }
        return image;
    }

    @Override public void close() { try (GlState state = new GlState()) { icons.close(); scenes.close(); } }

    private static final class Plan {
        final TextureAtlasSprite sprite;
        final int width, height, tint;
        final String location;
        final List<Integer> frames = new ArrayList<>();
        final List<int[]> timeline = new ArrayList<>();
        Plan(TextureAtlasSprite sprite, int width, int height, int tint) {
            this.sprite = sprite; this.width = width; this.height = height; this.tint = tint; this.location = sprite.getIconName();
        }
    }

    static final class Image {
        final BufferedImage pixels;
        final JsonArray frames;
        final String kind, location;
        Image(BufferedImage pixels, JsonArray frames, String kind, String location) {
            this.pixels = pixels; this.frames = frames; this.kind = kind; this.location = location;
        }
    }
}
