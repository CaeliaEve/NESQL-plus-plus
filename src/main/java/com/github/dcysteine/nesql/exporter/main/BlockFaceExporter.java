package com.github.dcysteine.nesql.exporter.main;

import com.github.dcysteine.nesql.exporter.main.config.ConfigOptions;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import cpw.mods.fml.relauncher.FMLInjectionData;
import net.minecraft.client.Minecraft;
import net.minecraft.block.Block;
import net.minecraft.block.BlockDirectional;
import net.minecraft.block.BlockFurnace;
import net.minecraft.block.BlockRotatedPillar;
import net.minecraft.block.BlockStairs;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IIcon;
import net.minecraft.util.ResourceLocation;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPOutputStream;

/** Exports block-side texture metadata and orientation hints for frontend 3D rendering. */
public final class BlockFaceExporter {
    private static final String REPOSITORY_PATH_FORMAT_STRING = "nesql" + File.separator + "%s";
    private static final String OUTPUT_RELATIVE_PATH =
            "multiblocks" + File.separator + "block-face-textures.json.gz";

    private static final String[] SIDE_NAMES = {
            "down", "up", "north", "south", "west", "east"
    };

    private final String repositoryName;
    private final File repositoryDirectory;
    private final Set<String> exportedTextureTargets = new HashSet<>();

    public BlockFaceExporter() {
        this(ConfigOptions.REPOSITORY_NAME.get());
    }

    public BlockFaceExporter(String repositoryName) {
        this.repositoryName = repositoryName;
        this.repositoryDirectory =
                new File(
                        (File) FMLInjectionData.data()[6],
                        String.format(REPOSITORY_PATH_FORMAT_STRING, repositoryName));
    }

    public void exportReportException() {
        try {
            export();
        } catch (Exception e) {
            Logger.MOD.error("Block face export failed", e);
            Logger.chatMessage(EnumChatFormatting.RED + "Block face export failed: " + e.getMessage());
        }
    }

    public void export() throws Exception {
        Logger.MOD.info("============================================================");
        Logger.MOD.info("=== NESQL Block Face Export STARTED ===");
        Logger.MOD.info("============================================================");
        Logger.MOD.info("Repository: {}", repositoryDirectory.getAbsolutePath());

        Logger.chatMessage(EnumChatFormatting.AQUA + "Starting block face export...");
        Logger.chatMessage(EnumChatFormatting.YELLOW + "Repository: " + repositoryName);

        if (!repositoryDirectory.exists() && !repositoryDirectory.mkdirs()) {
            throw new IllegalStateException("Failed to create repository directory: " + repositoryDirectory);
        }

        File multiblockDir = new File(repositoryDirectory, "multiblocks");
        if (!multiblockDir.exists() && !multiblockDir.mkdirs()) {
            throw new IllegalStateException("Failed to create multiblocks directory: " + multiblockDir);
        }

        List<BlockFaceEntry> entries = new ArrayList<>();
        int scannedBlocks = 0;

        @SuppressWarnings("unchecked")
        Iterable<Object> keys = (Iterable<Object>) Block.blockRegistry.getKeys();
        for (Object keyObj : keys) {
            if (keyObj == null) continue;
            String blockName = keyObj.toString();
            Object blockObj = Block.blockRegistry.getObject(keyObj);
            if (!(blockObj instanceof Block)) continue;

            Block block = (Block) blockObj;
            scannedBlocks++;

            if (scannedBlocks % 200 == 0) {
                Logger.chatMessage(EnumChatFormatting.GRAY + "Scanned blocks: " + scannedBlocks);
            }

            String modId = extractModId(blockName);
            String internalName = extractInternalName(blockName);
            String orientationKind = detectOrientationKind(block);

            for (int meta = 0; meta <= 15; meta++) {
                FaceCollection faceCollection = collectFaces(block, meta);
                Map<String, String> faces = faceCollection.faces;
                if (faces.isEmpty()) continue;

                BlockFaceEntry entry = new BlockFaceEntry();
                entry.blockId = makeBlockItemId(modId, internalName, meta);
                entry.blockRegistryName = blockName;
                entry.modId = modId;
                entry.internalName = internalName;
                entry.meta = meta;
                entry.renderType = safeInt(block.getRenderType());
                entry.opaqueCube = safeBool(block.isOpaqueCube());
                entry.normalCube = safeBool(block.renderAsNormalBlock());
                entry.hasTileEntity = safeBool(block.hasTileEntity(meta));
                entry.orientationKind = orientationKind;
                entry.faces = faces;
                entry.faceUv = faceCollection.faceUv;
                entry.faceTextureUrls = faceCollection.faceTextureUrls;
                entries.add(entry);
            }
        }

        ExportPayload payload = new ExportPayload();
        payload.generatedAtEpochMs = System.currentTimeMillis();
        payload.totalBlocksScanned = scannedBlocks;
        payload.totalBlockStatesExported = entries.size();
        payload.entries = entries;

        File output = new File(repositoryDirectory, OUTPUT_RELATIVE_PATH);
        writeGzipJson(output, payload);

        Logger.MOD.info("Block face export complete: {}", output.getAbsolutePath());
        Logger.MOD.info("Scanned blocks: {}", scannedBlocks);
        Logger.MOD.info("Exported block states: {}", entries.size());
        Logger.MOD.info("Exported unique block face textures: {}", exportedTextureTargets.size());

        Logger.chatMessage(EnumChatFormatting.GREEN + "Block face export complete!");
        Logger.chatMessage(EnumChatFormatting.YELLOW + "Output: " + output.getAbsolutePath());
        Logger.chatMessage(EnumChatFormatting.YELLOW + "States exported: " + entries.size());
        Logger.chatMessage(EnumChatFormatting.YELLOW + "Face textures exported: " + exportedTextureTargets.size());
    }

    private static void writeGzipJson(File output, ExportPayload payload) throws Exception {
        Gson gson = new GsonBuilder().disableHtmlEscaping().create();
        try (FileOutputStream fos = new FileOutputStream(output);
             GZIPOutputStream gzip = new GZIPOutputStream(fos);
             Writer writer = new OutputStreamWriter(gzip, StandardCharsets.UTF_8)) {
            gson.toJson(payload, writer);
        }
    }

    private FaceCollection collectFaces(Block block, int meta) {
        Map<String, String> faces = new LinkedHashMap<>();
        Map<String, FaceUv> faceUv = new LinkedHashMap<>();
        Map<String, String> faceTextureUrls = new LinkedHashMap<>();
        for (int side = 0; side < 6; side++) {
            IIcon icon = getIcon(block, side, meta);
            String iconName = getIconName(icon);
            if (iconName != null && !iconName.isEmpty()) {
                faces.put(SIDE_NAMES[side], iconName);
                FaceUv uv = buildUv(icon);
                if (uv != null) {
                    faceUv.put(SIDE_NAMES[side], uv);
                }
                String exportedTextureUrl = exportTextureForIcon(iconName);
                if (exportedTextureUrl != null && !exportedTextureUrl.isEmpty()) {
                    faceTextureUrls.put(SIDE_NAMES[side], exportedTextureUrl);
                }
            }
        }
        FaceCollection result = new FaceCollection();
        result.faces = faces;
        result.faceUv = faceUv.isEmpty() ? null : faceUv;
        result.faceTextureUrls = faceTextureUrls.isEmpty() ? null : faceTextureUrls;
        return result;
    }

    private static IIcon getIcon(Block block, int side, int meta) {
        try {
            return block.getIcon(side, meta);
        } catch (Throwable t) {
            return null;
        }
    }

    private static String getIconName(IIcon icon) {
        if (icon == null) return null;
        try {
            return icon.getIconName();
        } catch (Throwable t) {
            return null;
        }
    }

    private static FaceUv buildUv(IIcon icon) {
        if (icon == null) return null;
        try {
            FaceUv uv = new FaceUv();
            uv.minU = icon.getMinU();
            uv.maxU = icon.getMaxU();
            uv.minV = icon.getMinV();
            uv.maxV = icon.getMaxV();
            return uv;
        } catch (Throwable t) {
            return null;
        }
    }

    private String exportTextureForIcon(String iconName) {
        if (iconName == null || iconName.isEmpty()) return null;
        int sep = iconName.indexOf(':');
        if (sep <= 0 || sep >= iconName.length() - 1) return null;

        String modId = iconName.substring(0, sep);
        String textureKey = iconName.substring(sep + 1);
        if (textureKey.isEmpty()) return null;

        String normalizedTextureKey = textureKey.replace('\\', '/');
        String relativeTexturePath = "blockface/" + modId + "/" + normalizedTextureKey + ".png";
        String normalizedTarget = relativeTexturePath.replace('\\', '/');

        if (!exportedTextureTargets.contains(normalizedTarget)) {
            File targetFile = new File(repositoryDirectory, "image" + File.separator + normalizedTarget.replace('/', File.separatorChar));
            if (targetFile.exists()) {
                exportedTextureTargets.add(normalizedTarget);
            } else {
                File parent = targetFile.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs()) {
                    return null;
                }
                if (!copyResourceTexture(modId, normalizedTextureKey, targetFile)) {
                    return null;
                }
                exportedTextureTargets.add(normalizedTarget);
            }
        }

        return "/images/" + normalizedTarget;
    }

    private static boolean copyResourceTexture(String modId, String textureKey, File targetFile) {
        ResourceLocation[] candidates = new ResourceLocation[] {
                new ResourceLocation(modId, "textures/blocks/" + textureKey + ".png"),
                new ResourceLocation(modId, "textures/items/" + textureKey + ".png")
        };

        for (ResourceLocation location : candidates) {
            if (copyResource(location, targetFile)) {
                return true;
            }
        }
        return false;
    }

    private static boolean copyResource(ResourceLocation location, File targetFile) {
        try (java.io.InputStream in = Minecraft.getMinecraft().getResourceManager().getResource(location).getInputStream();
             FileOutputStream out = new FileOutputStream(targetFile)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) >= 0) {
                out.write(buffer, 0, read);
            }
            return true;
        } catch (IOException ignored) {
            return false;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static String detectOrientationKind(Block block) {
        if (block instanceof BlockRotatedPillar) return "axis_meta";
        if (block instanceof BlockStairs) return "stairs_meta";
        if (block instanceof BlockDirectional) return "facing_meta";
        if (block instanceof BlockFurnace) return "furnace_meta";
        return "none";
    }

    private static String makeBlockItemId(String modId, String internalName, int meta) {
        return "i~" + modId + "~" + internalName + "~" + meta;
    }

    private static String extractModId(String blockName) {
        int idx = blockName.indexOf(':');
        return idx > 0 ? blockName.substring(0, idx) : "minecraft";
    }

    private static String extractInternalName(String blockName) {
        int idx = blockName.indexOf(':');
        return idx > 0 ? blockName.substring(idx + 1) : blockName;
    }

    private static int safeInt(int value) {
        return value;
    }

    private static boolean safeBool(boolean value) {
        return value;
    }

    private static final class ExportPayload {
        long generatedAtEpochMs;
        int totalBlocksScanned;
        int totalBlockStatesExported;
        List<BlockFaceEntry> entries;
    }

    private static final class BlockFaceEntry {
        String blockId;
        String blockRegistryName;
        String modId;
        String internalName;
        int meta;
        int renderType;
        boolean opaqueCube;
        boolean normalCube;
        boolean hasTileEntity;
        String orientationKind;
        Map<String, String> faces;
        Map<String, FaceUv> faceUv;
        Map<String, String> faceTextureUrls;
    }

    private static final class FaceCollection {
        Map<String, String> faces;
        Map<String, FaceUv> faceUv;
        Map<String, String> faceTextureUrls;
    }

    private static final class FaceUv {
        float minU;
        float maxU;
        float minV;
        float maxV;
    }
}
