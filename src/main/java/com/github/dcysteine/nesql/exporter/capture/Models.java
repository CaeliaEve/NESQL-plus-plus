package com.github.dcysteine.nesql.exporter.capture;

import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.gtnewhorizon.gtnhlib.client.renderer.CapturingTessellator;
import com.gtnewhorizon.gtnhlib.client.renderer.TessellatorManager;
import com.gtnewhorizon.gtnhlib.client.renderer.quad.QuadView;
import cpw.mods.fml.client.registry.ISimpleBlockRenderingHandler;
import cpw.mods.fml.client.registry.RenderingRegistry;
import cpw.mods.fml.relauncher.ReflectionHelper;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.common.util.ForgeDirection;
import gregtech.mixin.interfaces.accessors.TesselatorAccessor;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.render.ISBRWorldContext;

import java.nio.ByteOrder;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Captures the target game's world-quad renderers; GPU-only renderers require explicit adapters. */
final class Models implements AutoCloseable {
    // Forge's public world-pass hooks dispatch chunk-render events. This capture owns only the verified pass field.
    private static final Field WORLD_PASS = ReflectionHelper.findField(ForgeHooksClient.class, "worldRenderPass");
    static { WORLD_PASS.setAccessible(true); }
    private final Surface surface = new Surface();
    private final Map<Integer, List<TextureAtlasSprite>> sprites = new HashMap<>();
    private final Map<Integer, ISimpleBlockRenderingHandler> renderers;

    Models() {
        Structures.version("gtnhlib", "0.7.10");
        TextureMap atlas = (TextureMap) Minecraft.getMinecraft().getTextureManager().getTexture(TextureMap.locationBlocksTexture);
        Map<String, TextureAtlasSprite> uploaded = ReflectionHelper.getPrivateValue(TextureMap.class, atlas, "mapUploadedSprites", "field_94252_e");
        if (uploaded.size() > 65536) throw fault("Block atlas exceeds its sprite budget");
        List<TextureAtlasSprite> ordered = new ArrayList<>(uploaded.values());
        ordered.add(atlas.getAtlasSprite("missingno"));
        ordered.sort(Comparator.comparing(TextureAtlasSprite::getIconName));
        for (TextureAtlasSprite sprite : ordered) {
            if (!Float.isFinite(sprite.getMinU()) || !Float.isFinite(sprite.getMaxU()) || !Float.isFinite(sprite.getMinV()) || !Float.isFinite(sprite.getMaxV())
                    || sprite.getMinU() < 0 || sprite.getMinV() < 0 || sprite.getMaxU() > 1 || sprite.getMaxV() > 1
                    || sprite.getMinU() >= sprite.getMaxU() || sprite.getMinV() >= sprite.getMaxV()) throw fault("Invalid atlas sprite bounds");
            for (int y = bin(sprite.getMinV()); y <= bin(sprite.getMaxV()); y++) for (int x = bin(sprite.getMinU()); x <= bin(sprite.getMaxU()); x++) {
                sprites.computeIfAbsent(y * 128 + x, ignored -> new ArrayList<>()).add(sprite);
            }
        }
        Map<Integer, ISimpleBlockRenderingHandler> registered = ReflectionHelper.getPrivateValue(
                RenderingRegistry.class, RenderingRegistry.instance(), "blockRenderers");
        renderers = new HashMap<>(registered);
    }

    Draft capture(IBlockAccess world, int x, int y, int z, JsonObject appearance) {
        Block block = world.getBlock(x, y, z);
        TileEntity tile = world.getTileEntity(x, y, z);
        Draft draft = new Draft(appearance);
        TileEntitySpecialRenderer entity = tile == null ? null : TileEntityRendererDispatcher.instance.getSpecialRenderer(tile);
        if (entity != null) Entities.capture(tile, entity, draft);
        int type = block.getRenderType();
        ISimpleBlockRenderingHandler handler = renderers.get(type);
        if (!world(type, handler != null, entity != null)) return draft;
        if (tile instanceof IGregTechTileEntity) {
            IMetaTileEntity machine = ((IGregTechTileEntity) tile).getMetaTileEntity();
            if (machine != null) {
                try {
                    String owner = machine.getClass().getMethod("renderInWorld", ISBRWorldContext.class).getDeclaringClass().getName();
                    if (!owner.equals("gregtech.api.metatileentity.CommonMetaTileEntity") && !owner.equals("gregtech.api.metatileentity.MetaPipeEntity")
                            && !owner.equals("gtPlusPlus.xmod.gregtech.common.tileentities.machines.multi.production.MTEQuantumForceTransformer")) {
                        throw fault("Machine world renderer requires a model adapter: " + owner);
                    }
                } catch (NoSuchMethodException failure) { throw new Jobs.Fault("model_api", "Machine has no target world rendering API"); }
            }
        }
        if (handler != null) {
            String name = handler.getClass().getName();
            if (!name.equals("gregtech.common.render.GTRendererBlock") && !name.equals("gregtech.common.render.GTRendererCasing")) {
                throw fault("World renderer requires a model adapter: " + name);
            }
        } else if (type < 0 || type > 41) throw fault("Block has no supported world renderer: " + type);
        int entityFaces = draft.faces.size();
        int ambient = Minecraft.getMinecraft().gameSettings.ambientOcclusion;
        int pass = MinecraftForgeClient.getRenderPass();
        int worldPass = ForgeHooksClient.getWorldRenderPass();
        float[] bounds = {(float) block.getBlockBoundsMinX(), (float) block.getBlockBoundsMinY(), (float) block.getBlockBoundsMinZ(),
                (float) block.getBlockBoundsMaxX(), (float) block.getBlockBoundsMaxY(), (float) block.getBlockBoundsMaxZ()};
        try {
            Minecraft.getMinecraft().gameSettings.ambientOcclusion = 0;
            surface.render(block.getClass().getName(), () -> {
                Minecraft.getMinecraft().getTextureManager().bindTexture(TextureMap.locationBlocksTexture);
                // GT's DummyWorld shortcut renders every texture in every pass. A normal read view preserves pass selection.
                RenderBlocks renderer = new RenderBlocks(new Access(world));
                renderer.renderAllFaces = true;
                for (int layer = 0; layer < 2; layer++) {
                    Jobs.checkpoint();
                    if (!block.canRenderInPass(layer)) continue;
                    ForgeHooksClient.setRenderPass(layer);
                    worldPass(layer);
                    capture(draft, block, renderer, x, y, z, layer);
                }
            });
        } finally {
            block.setBlockBounds(bounds[0], bounds[1], bounds[2], bounds[3], bounds[4], bounds[5]);
            Minecraft.getMinecraft().gameSettings.ambientOcclusion = ambient;
            ForgeHooksClient.setRenderPass(pass);
            worldPass(worldPass);
        }
        if (draft.faces.size() == entityFaces) throw fault("World renderer supplied no model faces");
        return draft;
    }

    static boolean world(int type, boolean registered, boolean entity) {
        // RenderBlocks omits -1; vanilla chests use 22, which has no world branch unless Forge registers one.
        if (type == -1 || (type == 22 && !registered)) {
            if (!entity) throw fault("Block has no world or entity model renderer: " + type);
            return false;
        }
        return true;
    }

    private void capture(Draft draft, Block block, RenderBlocks renderer, int x, int y, int z, int pass) {
        if (TessellatorManager.isCurrentlyCapturing()) throw new Jobs.Fault("model_busy", "Another renderer owns the tessellator capture");
        TessellatorManager.startCapturing();
        CapturingTessellator tessellator = (CapturingTessellator) TessellatorManager.get();
        try {
            tessellator.resetOffset();
            tessellator.setTranslation(-x, -y, -z);
            tessellator.startDrawingQuads();
            tessellator.setBrightness(15 << 20 | 15 << 4);
            tessellator.setColorOpaque_F(1, 1, 1);
            renderer.renderBlockByRenderType(block, x, y, z);
            if (!TessellatorManager.isCurrentlyCapturing()) throw fault("World renderer changed capture ownership");
            if (((TesselatorAccessor) tessellator).gt5u$isDrawing()) tessellator.draw();
            for (QuadView quad : tessellator.getQuads()) {
                if (draft.faces.size() >= 1024) throw fault("Block model exceeds 1024 faces");
                TextureAtlasSprite sprite = sprite(quad);
                JsonArray vertices = new JsonArray();
                for (int vertex = 0; vertex < 4; vertex++) {
                    float u = (quad.getTexU(vertex) - sprite.getMinU()) / (sprite.getMaxU() - sprite.getMinU());
                    float v = (quad.getTexV(vertex) - sprite.getMinV()) / (sprite.getMaxV() - sprite.getMinV());
                    int color = quad.getColor(vertex);
                    if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) color = Integer.reverseBytes(color);
                    vertices.add(object("at", array(coordinate(quad.getX(vertex)), coordinate(1 - quad.getY(vertex)), coordinate(1 - quad.getZ(vertex))),
                            "uv", array(Float.toString(Math.max(0, Math.min(1, u))), Float.toString(Math.max(0, Math.min(1, v)))),
                            "color", uint(Integer.toUnsignedLong(color))));
                }
                draft.faces.add(new Face(new Paint(sprite), object("texture", null, "pass", pass == 0 ? "solid" : "blend", "vertices", vertices)));
            }
        } finally {
            // Discard before stop: stop's implicit draw must never strand its saved translation after an error.
            tessellator.discard();
            if (TessellatorManager.isCurrentlyCapturing()) TessellatorManager.stopCapturingToPooledQuads();
            tessellator.clearQuads(); tessellator.resetOffset();
        }
    }

    private TextureAtlasSprite sprite(QuadView quad) {
        float minU = Float.POSITIVE_INFINITY, minV = Float.POSITIVE_INFINITY, maxU = Float.NEGATIVE_INFINITY, maxV = Float.NEGATIVE_INFINITY;
        for (int index = 0; index < 4; index++) {
            float u = quad.getTexU(index), v = quad.getTexV(index);
            if (!Float.isFinite(u) || !Float.isFinite(v) || u < 0 || u > 1 || v < 0 || v > 1) throw fault("Model texture coordinates leave the block atlas");
            minU = Math.min(minU, u); maxU = Math.max(maxU, u); minV = Math.min(minV, v); maxV = Math.max(maxV, v);
        }
        List<TextureAtlasSprite> candidates = sprites.get(bin((minV + maxV) / 2) * 128 + bin((minU + maxU) / 2));
        if (candidates != null) for (TextureAtlasSprite sprite : candidates) {
            if (minU >= sprite.getMinU() - 0.000001f && maxU <= sprite.getMaxU() + 0.000001f
                    && minV >= sprite.getMinV() - 0.000001f && maxV <= sprite.getMaxV() + 0.000001f) return sprite;
        }
        throw fault("Model face does not refer to one declared atlas sprite");
    }

    private static int bin(float value) { return Math.max(0, Math.min(127, (int) (value * 128))); }
    private static String coordinate(float value) {
        if (!Float.isFinite(value) || Math.abs(value) > 256) throw fault("Model geometry exceeds local bounds");
        return Float.toString(value);
    }
    private static Jobs.Fault fault(String message) { return new Jobs.Fault("model_unsupported", message); }
    private static void worldPass(int pass) {
        try { WORLD_PASS.setInt(null, pass); }
        catch (IllegalAccessException failure) { throw new Jobs.Fault("model_api", "Cannot set the target Forge world render pass"); }
    }
    @Override public void close() { try (GlState state = new GlState()) { surface.close(); } }

    static final class Draft {
        final JsonObject appearance;
        final List<Face> faces = new ArrayList<>();
        Draft(JsonObject appearance) { this.appearance = appearance; }
    }
    static final class Face {
        final Paint paint;
        final JsonObject record;
        Face(Paint paint, JsonObject record) { this.paint = paint; this.record = record; }
    }
    static final class Paint {
        static final Paint PLAIN = new Paint();
        final TextureAtlasSprite sprite;
        final ResourceLocation resource;
        private Paint() { this.sprite = null; this.resource = null; }
        Paint(TextureAtlasSprite sprite) { this.sprite = sprite; this.resource = null; }
        Paint(ResourceLocation resource) { this.sprite = null; this.resource = resource; }
        Object key() { return sprite != null ? sprite : resource != null ? resource : PLAIN; }
    }

    private static final class Access implements IBlockAccess {
        private final IBlockAccess world;
        Access(IBlockAccess world) { this.world = world; }
        @Override public Block getBlock(int x, int y, int z) { return world.getBlock(x, y, z); }
        @Override public TileEntity getTileEntity(int x, int y, int z) { return world.getTileEntity(x, y, z); }
        @Override public int getLightBrightnessForSkyBlocks(int x, int y, int z, int light) { return world.getLightBrightnessForSkyBlocks(x, y, z, light); }
        @Override public int getBlockMetadata(int x, int y, int z) { return world.getBlockMetadata(x, y, z); }
        @Override public int isBlockProvidingPowerTo(int x, int y, int z, int side) { return world.isBlockProvidingPowerTo(x, y, z, side); }
        @Override public boolean isAirBlock(int x, int y, int z) { return world.isAirBlock(x, y, z); }
        @Override public BiomeGenBase getBiomeGenForCoords(int x, int z) { return world.getBiomeGenForCoords(x, z); }
        @Override public int getHeight() { return world.getHeight(); }
        @Override public boolean extendedLevelsInChunkCache() { return world.extendedLevelsInChunkCache(); }
        @Override public boolean isSideSolid(int x, int y, int z, ForgeDirection side, boolean fallback) { return world.isSideSolid(x, y, z, side, fallback); }
    }
}
