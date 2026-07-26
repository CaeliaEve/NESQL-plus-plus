package com.github.dcysteine.nesql.exporter.local;

import com.github.dcysteine.nesql.exporter.canonical.ResourceAuthorityContract;
import com.github.dcysteine.nesql.exporter.canonical.CanonicalRenderAsset;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

public final class ResourceAuthorityContractTest {
    private ResourceAuthorityContractTest() {}

    public static void main(String[] args) throws Exception {
        assertEquals(
                "resolved facade",
                ResourceAuthorityContract.FACADE_RESOLVED,
                ResourceAuthorityContract.facadeStatus(2, 2));
        assertEquals(
                "partial facade",
                ResourceAuthorityContract.FACADE_PARTIAL,
                ResourceAuthorityContract.facadeStatus(2, 1));
        assertEquals(
                "unresolved facade",
                ResourceAuthorityContract.FACADE_UNRESOLVED,
                ResourceAuthorityContract.facadeStatus(0, 0));
        assertEquals(
                "canonical source item id",
                "i~Railcraft~cube~11",
                ResourceAuthorityContract.canonicalItemId("Railcraft~cube~11"));
        assertEquals(
                "canonical source item id is idempotent",
                "i~Railcraft~cube~11",
                ResourceAuthorityContract.canonicalItemId("i~Railcraft~cube~11"));
        assertEquals(
                "canonical source asset id",
                "nesqlpp:item/i~Railcraft~cube~11",
                ResourceAuthorityContract.canonicalItemAssetId("Railcraft~cube~11"));
        assertEquals(
                "current facade image path derives from canonical item id",
                "item/BuildCraftTransport/pipeFacade~0~facadePayload.png",
                CanonicalRenderAssetCollector.canonicalImageFilePath(
                        "item",
                        "i~BuildCraftTransport~pipeFacade~0~facadePayload"));
        assertEquals(
                "current fluid image path derives from canonical fluid id",
                "fluid/EnderIO/ender~molten.png",
                CanonicalRenderAssetCollector.canonicalImageFilePath(
                        "fluid",
                        "f~EnderIO~ender~molten"));
        assertEquals(
                "wrong canonical kind is rejected",
                null,
                CanonicalRenderAssetCollector.canonicalImageFilePath(
                        "item",
                        "f~BuildCraftTransport~pipeFacade~0"));
        expectIllegalArgument("database render asset collection requires EntityManager", new Action() {
            @Override
            public void run() {
                new CanonicalRenderAssetCollector(null, new File("."));
            }
        });

        assertEquals(
                "materialized animation",
                ResourceAuthorityContract.ANIMATION_MATERIALIZED,
                ResourceAuthorityContract.animationStatus(2, 2));
        assertEquals(
                "declared animation with duplicate pixels is static",
                ResourceAuthorityContract.ANIMATION_STATIC,
                ResourceAuthorityContract.animationStatus(20, 1));
        assertEquals(
                "missing frames are unavailable",
                ResourceAuthorityContract.ANIMATION_UNAVAILABLE,
                ResourceAuthorityContract.animationStatus(0, 0));
        expectIllegalArgument("distinct frames cannot exceed materialized frames", new Action() {
            @Override
            public void run() {
                ResourceAuthorityContract.animationStatus(1, 2);
            }
        });

        Map<String, String> manifestFiles = new LinkedHashMap<String, String>();
        RawExportFileCatalog.putManifestFiles(manifestFiles, false, false);
        assertEquals(
                "facade manifest path",
                RawExportFileCatalog.FACADE_RESOLUTIONS_FILE,
                manifestFiles.get("facadeResolutions"));
        assertEquals(
                "animation materialization manifest path",
                RawExportFileCatalog.ANIMATION_FRAME_MATERIALIZATIONS_FILE,
                manifestFiles.get("animationFrameMaterializations"));

        File tempDirectory = Files.createTempDirectory("nesql-resource-authority").toFile();
        try {
            File distinctAtlas = new File(tempDirectory, "distinct.sprite-atlas.png");
            writeVerticalAtlas(distinctAtlas, 16, 16, 0xffff0000, 0xff0000ff);
            CanonicalRenderAsset distinctLegacy = legacyNativeSpriteAsset(20);
            assertTrue(
                    "legacy distinct atlas backfill applied",
                    CanonicalRenderAssetCollector.backfillLegacyNativeSpriteMaterialization(
                            distinctLegacy,
                            distinctAtlas,
                            Integer.valueOf(16),
                            Integer.valueOf(16),
                            Integer.valueOf(20)));
            assertEquals("legacy runtime count retained", Integer.valueOf(20), distinctLegacy.runtimeFrameCount);
            assertEquals("legacy declared count retained", Integer.valueOf(20), distinctLegacy.declaredFrameCount);
            assertEquals("physical frame count comes from atlas", Integer.valueOf(2), distinctLegacy.materializedFrameCount);
            assertEquals("distinct frame count comes from pixels", Integer.valueOf(2), distinctLegacy.distinctFrameCount);
            assertEquals(
                    "different atlas frames materialize animation",
                    ResourceAuthorityContract.ANIMATION_MATERIALIZED,
                    distinctLegacy.materializationStatus);
            assertEquals("different atlas frame descriptor count", 2, distinctLegacy.materializedFrames.size());
            @SuppressWarnings("unchecked")
            Map<String, Object> secondRect = (Map<String, Object>)
                    distinctLegacy.materializedFrames.get(1).get("rect");
            assertEquals(
                    "second physical frame rect",
                    16,
                    ((Number) secondRect.get("y")).intValue());
            assertNotEquals(
                    "different frame hashes differ",
                    distinctLegacy.materializedFrames.get(0).get("contentHash"),
                    distinctLegacy.materializedFrames.get(1).get("contentHash"));

            File repeatedAtlas = new File(tempDirectory, "repeated.sprite-atlas.png");
            writeVerticalAtlas(repeatedAtlas, 16, 16, 0xff00ff00, 0xff00ff00);
            CanonicalRenderAsset repeatedLegacy = legacyNativeSpriteAsset(20);
            assertTrue(
                    "legacy repeated atlas backfill applied",
                    CanonicalRenderAssetCollector.backfillLegacyNativeSpriteMaterialization(
                            repeatedLegacy,
                            repeatedAtlas,
                            Integer.valueOf(16),
                            Integer.valueOf(16),
                            Integer.valueOf(20)));
            assertEquals("repeated atlas physical frame count", Integer.valueOf(2), repeatedLegacy.materializedFrameCount);
            assertEquals("repeated atlas distinct pixel count", Integer.valueOf(1), repeatedLegacy.distinctFrameCount);
            assertEquals(
                    "repeated atlas is static",
                    ResourceAuthorityContract.ANIMATION_STATIC,
                    repeatedLegacy.materializationStatus);
            assertEquals(
                    "duplicate frame hashes match",
                    repeatedLegacy.materializedFrames.get(0).get("contentHash"),
                    repeatedLegacy.materializedFrames.get(1).get("contentHash"));

            File customItem = new File(
                    tempDirectory,
                    "image/item/fixture/custom-animated~0.png");
            writeSolidImage(customItem, 16, 16, 0xffffffff);
            writeJson(
                    new File(
                            tempDirectory,
                            "image/item/fixture/custom-animated~0.render.json"),
                    "{\"schemaVersion\":\"nesqlpp/render-contract/v2-draft\","
                            + "\"renderMode\":\"captured_final_atlas\","
                            + "\"captureSource\":\"inventory_renderer_capture\","
                            + "\"playbackHint\":\"atlas_timeline\"}");
            writeJson(
                    new File(
                            tempDirectory,
                            "image/item/fixture/custom-animated~0.sprite.json"),
                    "{\"frameCount\":2,\"width\":16,\"height\":16,"
                            + "\"animated\":true,"
                            + "\"atlasTexture\":\"fixture:textures/atlas/items.png\","
                            + "\"iconName\":\"fixture:custom_animated\"}");
            writeVerticalAtlas(
                    new File(
                            tempDirectory,
                            "image/item/fixture/custom-animated~0.sprite-atlas.png"),
                    16,
                    16,
                    0xffff0000,
                    0xff0000ff);

            List<CanonicalRenderAsset> customRendererAssets =
                    CanonicalRenderAssetCollector.collectFromExportedFiles(tempDirectory);
            assertEquals("custom renderer fixture asset count", 1, customRendererAssets.size());
            CanonicalRenderAsset customRendererAsset = customRendererAssets.get(0);
            assertEquals(
                    "custom renderer asset identity",
                    "nesqlpp:item/i~fixture~custom-animated~0",
                    customRendererAsset.assetId);
            assertEquals(
                    "auxiliary native animation is materialized",
                    ResourceAuthorityContract.ANIMATION_MATERIALIZED,
                    customRendererAsset.materializationStatus);
            assertEquals(
                    "custom renderer remains the primary render mode",
                    "captured_final_atlas",
                    customRendererAsset.renderMode);
            assertEquals(
                    "native sprite remains auxiliary to custom renderer",
                    "native_sprite_aux",
                    customRendererAsset.animationMode);
            assertEquals(
                    "custom renderer capture source is preserved",
                    "inventory_renderer_capture",
                    customRendererAsset.captureSource);
            assertEquals(
                    "custom renderer retains a captured output frame",
                    1,
                    customRendererAsset.frames.size());

            File customCaptureOutput = new File(
                    tempDirectory,
                    "custom-renderer-framebuffer-captures.jsonl.gz");
            AngelicaFramebufferCaptureStreamCounts customCaptureCounts =
                    new AngelicaFramebufferCaptureFactsWriter(
                            "nesqlpp/test/render",
                            customRendererAssets).write(customCaptureOutput);
            assertEquals(
                    "custom renderer with auxiliary native animation remains a framebuffer capture",
                    1L,
                    customCaptureCounts.framebufferCaptures);
            assertEquals(
                    "custom renderer capture contains a frame",
                    0L,
                    customCaptureCounts.framebufferCapturesWithoutFrames);
            assertTrue(
                    "custom renderer exact asset identity is captured",
                    customCaptureCounts.captureAssetIds.contains(
                            "nesqlpp:item/i~fixture~custom-animated~0"));

            File selfContainedRepository = new File(tempDirectory, "self-contained-repository");
            File promotedSource = new File(
                    selfContainedRepository,
                    "image/item/fixture/promoted~0.sprite-atlas.png");
            writeVerticalAtlas(promotedSource, 16, 16, 0xff112233, 0xff445566);
            File packedSource = new File(
                    selfContainedRepository,
                    "image/item/fixture/already-packed~0.sprite-atlas.png");
            writeVerticalAtlas(packedSource, 16, 16, 0xff778899, 0xffaabbcc);
            writeJson(
                    new File(selfContainedRepository, "canonical/browser-atlas-index.json"),
                    "{\"schemaVersion\":\"nesqlpp/browser-atlas-index/v1\",\"items\":["
                            + "{\"assetId\":\"nesqlpp:item/i~fixture~promoted~0\","
                            + "\"hasStaticAtlas\":true,\"hasAnimatedAtlas\":false},"
                            + "{\"assetId\":\"nesqlpp:item/i~fixture~already-packed~0\","
                            + "\"hasStaticAtlas\":true,\"hasAnimatedAtlas\":true}]}");
            List<CanonicalRenderAsset> selfContainedAssets = new ArrayList<CanonicalRenderAsset>();
            selfContainedAssets.add(canonicalMaterializedAsset(
                    "nesqlpp:item/i~fixture~promoted~0",
                    "item/fixture/promoted~0.sprite-atlas.png"));
            selfContainedAssets.add(canonicalMaterializedAsset(
                    "nesqlpp:item/i~fixture~already-packed~0",
                    "item/fixture/already-packed~0.sprite-atlas.png"));
            File selfContainedRaw = new File(selfContainedRepository, "raw-generation");
            new RawExportRenderAssetCatalogWriter(
                    selfContainedRepository,
                    selfContainedRaw,
                    selfContainedAssets).write();

            JsonArray selfContainedRows = readRows(new File(
                    selfContainedRaw,
                    RawExportFileCatalog.ANIMATION_FRAME_MATERIALIZATIONS_FILE));
            assertEquals("self-contained materialization row count", 2, selfContainedRows.size());
            String promotedRawPath =
                    "assets/animations/materialization-atlases/item/fixture/promoted~0.sprite-atlas.png";
            assertEquals(
                    "compiler-promoted animation atlas is rebased into generation",
                    promotedRawPath,
                    selfContainedRows.get(0).getAsJsonObject().get("atlasFile").getAsString());
            assertTrue(
                    "compiler-promoted animation atlas exists inside generation",
                    new File(selfContainedRaw, promotedRawPath).isFile());
            assertEquals(
                    "already packed animation keeps its existing materialization source",
                    "item/fixture/already-packed~0.sprite-atlas.png",
                    selfContainedRows.get(1).getAsJsonObject().get("atlasFile").getAsString());
            assertTrue(
                    "already packed animation atlas is not redundantly copied",
                    !new File(
                            selfContainedRaw,
                            "assets/animations/materialization-atlases/item/fixture/already-packed~0.sprite-atlas.png")
                            .exists());

            JsonObject missingAssetId = asset("nesqlpp:item/i~fixture~missing-id~0", 1, 1, 1, 1,
                    ResourceAuthorityContract.ANIMATION_STATIC,
                    "single-native-frame-materialized");
            missingAssetId.remove("assetId");
            expectException("missing assetId is rejected", new ThrowingAction() {
                @Override
                public void run() throws Exception {
                    JsonArray invalid = new JsonArray();
                    invalid.add(missingAssetId);
                    new RawExportAnimationFrameMaterializationWriter().write(
                            invalid,
                            new File(tempDirectory, "missing-asset-id.jsonl.gz"));
                }
            });
            JsonObject emptyAssetId = asset("nesqlpp:item/i~fixture~empty-id~0", 1, 1, 1, 1,
                    ResourceAuthorityContract.ANIMATION_STATIC,
                    "single-native-frame-materialized");
            emptyAssetId.addProperty("assetId", "   ");
            expectException("empty assetId is rejected", new ThrowingAction() {
                @Override
                public void run() throws Exception {
                    JsonArray invalid = new JsonArray();
                    invalid.add(emptyAssetId);
                    new RawExportAnimationFrameMaterializationWriter().write(
                            invalid,
                            new File(tempDirectory, "empty-asset-id.jsonl.gz"));
                }
            });

            JsonArray assets = new JsonArray();
            assets.add(asset("nesqlpp:item/i~fixture~animated~0", 2, 2, 2, 2,
                    ResourceAuthorityContract.ANIMATION_MATERIALIZED,
                    "distinct-native-frames-materialized"));
            assets.add(asset("nesqlpp:item/i~Railcraft~cart.redstone.flux~0", 20, 20, 20, 1,
                    ResourceAuthorityContract.ANIMATION_STATIC,
                    "declared-animation-has-single-distinct-frame"));
            assets.add(asset("nesqlpp:item/i~fixture~missing~0", 0, 0, 0, 0,
                    ResourceAuthorityContract.ANIMATION_UNAVAILABLE,
                    "no-native-frame-data"));

            File output = new File(tempDirectory, "frame-materializations.jsonl.gz");
            RawAnimationFrameMaterializationCounts counts =
                    new RawExportAnimationFrameMaterializationWriter().write(assets, output);
            assertEquals("materialization row count", 3L, counts.total);
            assertEquals("materialized count", 1L, counts.materialized);
            assertEquals("static count", 1L, counts.staticFrames);
            assertEquals("unavailable count", 1L, counts.unavailable);

            JsonArray rows = readRows(output);
            assertEquals("serialized row count", 3, rows.size());
            JsonObject railcraft = rows.get(1).getAsJsonObject();
            assertEquals(
                    "Railcraft duplicate animation status",
                    ResourceAuthorityContract.ANIMATION_STATIC,
                    railcraft.get("materializationStatus").getAsString());
            assertEquals(
                    "Railcraft runtime count retained",
                    20,
                    railcraft.get("runtimeFrameCount").getAsInt());
            assertEquals(
                    "Railcraft physical descriptors retained",
                    20,
                    railcraft.getAsJsonArray("frames").size());
            assertEquals(
                    "canonical materialized atlas field",
                    "image/item/i~Railcraft~cart.redstone.flux~0.sprite-atlas.png",
                    railcraft.get("atlasFile").getAsString());
            if (railcraft.has("nativeSpriteAtlasFile")) {
                throw new AssertionError("Animation materialization ABI must export canonical atlasFile");
            }
        } finally {
            deleteRecursively(tempDirectory);
        }
    }

    private static CanonicalRenderAsset legacyNativeSpriteAsset(int frameCount) {
        CanonicalRenderAsset asset = new CanonicalRenderAsset();
        asset.frameCount = Integer.valueOf(frameCount);
        return asset;
    }

    private static CanonicalRenderAsset canonicalMaterializedAsset(
            String assetId,
            String atlasFile) {
        CanonicalRenderAsset asset = new CanonicalRenderAsset();
        asset.assetId = assetId;
        asset.variantKey = assetId.substring("nesqlpp:".length());
        asset.runtimeFrameCount = Integer.valueOf(2);
        asset.declaredFrameCount = Integer.valueOf(2);
        asset.materializedFrameCount = Integer.valueOf(2);
        asset.distinctFrameCount = Integer.valueOf(2);
        asset.materializationStatus = ResourceAuthorityContract.ANIMATION_MATERIALIZED;
        asset.materializationReason = "distinct-native-frames-materialized";
        asset.nativeSpriteAtlasFile = atlasFile;
        asset.materializedFrames = new ArrayList<Map<String, Object>>();
        for (int index = 0; index < 2; index++) {
            Map<String, Object> frame = new LinkedHashMap<String, Object>();
            frame.put("materializationIndex", Integer.valueOf(index));
            frame.put("frameIndex", Integer.valueOf(index));
            frame.put("contentHash", "sha256:" + index);
            Map<String, Object> rect = new LinkedHashMap<String, Object>();
            rect.put("x", Integer.valueOf(0));
            rect.put("y", Integer.valueOf(index * 16));
            rect.put("width", Integer.valueOf(16));
            rect.put("height", Integer.valueOf(16));
            frame.put("rect", rect);
            asset.materializedFrames.add(frame);
        }
        return asset;
    }

    private static void writeVerticalAtlas(
            File output,
            int width,
            int frameHeight,
            int... colors) throws Exception {
        File parent = output.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        BufferedImage image = new BufferedImage(
                width,
                frameHeight * colors.length,
                BufferedImage.TYPE_INT_ARGB);
        for (int frameIndex = 0; frameIndex < colors.length; frameIndex++) {
            int[] pixels = new int[width * frameHeight];
            java.util.Arrays.fill(pixels, colors[frameIndex]);
            image.setRGB(
                    0,
                    frameIndex * frameHeight,
                    width,
                    frameHeight,
                    pixels,
                    0,
                    width);
        }
        ImageIO.write(image, "PNG", output);
    }

    private static void writeSolidImage(
            File output,
            int width,
            int height,
            int color) throws Exception {
        File parent = output.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        int[] pixels = new int[width * height];
        java.util.Arrays.fill(pixels, color);
        image.setRGB(0, 0, width, height, pixels, 0, width);
        ImageIO.write(image, "PNG", output);
    }

    private static void writeJson(File output, String json) throws Exception {
        File parent = output.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        Files.write(output.toPath(), json.getBytes(StandardCharsets.UTF_8));
    }

    private static JsonObject asset(
            String assetId,
            int runtimeFrameCount,
            int declaredFrameCount,
            int materializedFrameCount,
            int distinctFrameCount,
            String status,
            String reason) {
        JsonObject asset = new JsonObject();
        asset.addProperty("assetId", assetId);
        asset.addProperty("variantKey", assetId.substring("nesqlpp:".length()));
        asset.addProperty("runtimeFrameCount", runtimeFrameCount);
        asset.addProperty("declaredFrameCount", declaredFrameCount);
        asset.addProperty("materializedFrameCount", materializedFrameCount);
        asset.addProperty("distinctFrameCount", distinctFrameCount);
        asset.addProperty("materializationStatus", status);
        asset.addProperty("materializationReason", reason);
        asset.addProperty(
                "nativeSpriteAtlasFile",
                "image/" + assetId.substring("nesqlpp:".length()) + ".sprite-atlas.png");
        JsonArray frames = new JsonArray();
        for (int index = 0; index < materializedFrameCount; index++) {
            JsonObject frame = new JsonObject();
            frame.addProperty("materializationIndex", index);
            frame.addProperty("frameIndex", index);
            frame.addProperty("contentHash", "sha256:" + (distinctFrameCount <= 1 ? "same" : index));
            JsonObject rect = new JsonObject();
            rect.addProperty("x", 0);
            rect.addProperty("y", index * 16);
            rect.addProperty("width", 16);
            rect.addProperty("height", 16);
            frame.add("rect", rect);
            frames.add(frame);
        }
        asset.add("materializedFrames", frames);
        return asset;
    }

    private static JsonArray readRows(File file) throws Exception {
        JsonArray rows = new JsonArray();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new GZIPInputStream(Files.newInputStream(file.toPath())),
                StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.trim().isEmpty()) {
                    rows.add(new JsonParser().parse(line));
                }
            }
        }
        return rows;
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        file.delete();
    }

    private static void expectIllegalArgument(String label, Action action) {
        try {
            action.run();
            throw new AssertionError(label + ": expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    private static void expectException(String label, ThrowingAction action) {
        try {
            action.run();
            throw new AssertionError(label + ": expected exception");
        } catch (AssertionError error) {
            throw error;
        } catch (Exception expected) {
        }
    }

    private static void assertEquals(String label, Object expected, Object actual) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(label + ": expected=" + expected + ", actual=" + actual);
        }
    }

    private static void assertTrue(String label, boolean value) {
        if (!value) {
            throw new AssertionError(label);
        }
    }

    private static void assertNotEquals(String label, Object left, Object right) {
        if (left == null ? right == null : left.equals(right)) {
            throw new AssertionError(label + ": both=" + left);
        }
    }

    private interface Action {
        void run();
    }

    private interface ThrowingAction {
        void run() throws Exception;
    }
}
