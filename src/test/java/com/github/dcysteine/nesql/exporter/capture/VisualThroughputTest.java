package com.github.dcysteine.nesql.exporter.capture;

import com.google.gson.JsonArray;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.imageio.ImageIO;

/** Small offline behavior checks; no game instance or benchmark claim. */
public final class VisualThroughputTest {
    public static void main(String[] args) throws Exception { preflight(); png(); batches(); assets(); checkpointAssets(); OverlapTest.run(); System.out.println("Visual throughput: PNG bytes, provenance independence, eviction and ordered bounded capture passed"); }
    /** Production Sink encoding and checkpoint journaling share the same asset bytes and rows. */
    private static void checkpointAssets() throws Exception {
        java.nio.file.Path root = java.nio.file.Files.createTempDirectory("nesql-sink-checkpoint-");
        com.google.gson.JsonObject provenance = com.github.dcysteine.nesql.exporter.source.Json.object(
                "environment", repeat('1'), "runtime", repeat('2'), "session", repeat('3'), "selection", repeat('4'));
        com.google.gson.JsonObject[] receipt = {null};
        String source;
        try {
            try (com.github.dcysteine.nesql.exporter.source.Dataset dataset = new com.github.dcysteine.nesql.exporter.source.Dataset(root.resolve("source"), "fixture", new com.google.gson.JsonObject(), false);
                 com.github.dcysteine.nesql.exporter.source.Rows rows = new com.github.dcysteine.nesql.exporter.source.Rows(root.resolve("rows"));
                 com.github.dcysteine.nesql.exporter.source.Checkpoints checkpoint = new com.github.dcysteine.nesql.exporter.source.Checkpoints(root.resolve("first"), provenance, java.util.Collections.emptyList(), "fixture", saved -> receipt[0] = saved)) {
                checkpoint.begin("base");
                Class<?> type = Class.forName(Capture.class.getName() + "$Sink");
                java.lang.reflect.Constructor<?> constructor = type.getDeclaredConstructors()[0]; constructor.setAccessible(true);
                Object sink = constructor.newInstance(dataset, rows, null, null, true, checkpoint, root.resolve("source"));
                java.lang.reflect.Method asset = type.getDeclaredMethod("asset", Images.Image.class); asset.setAccessible(true);
                try (AutoCloseable owner = (AutoCloseable) sink) {
                    Images.Image image = new Images.Image(pixels(4, 4, 0xff123456), new JsonArray(), "capture", "fixture:journal");
                    String first = (String) asset.invoke(sink, image);
                    require(first.equals(asset.invoke(sink, image)), "Journal changed deduplicated asset identity");
                    JsonArray frames = new JsonArray();
                    frames.add(com.github.dcysteine.nesql.exporter.source.Json.object("x", 0, "y", 0, "width", 4, "height", 4, "ticks", 3));
                    asset.invoke(sink, new Images.Image(image.pixels, frames, "capture", "fixture:journal"));
                }
                checkpoint.finish(new com.google.gson.JsonObject()); checkpoint.release(true);
                rows.write(dataset); source = dataset.seal();
            }
            try (com.github.dcysteine.nesql.exporter.source.Dataset dataset = new com.github.dcysteine.nesql.exporter.source.Dataset(root.resolve("resumed"), "fixture", new com.google.gson.JsonObject(), false);
                 com.github.dcysteine.nesql.exporter.source.Rows rows = new com.github.dcysteine.nesql.exporter.source.Rows(root.resolve("resumed-rows"));
                 com.github.dcysteine.nesql.exporter.source.Checkpoints checkpoint = new com.github.dcysteine.nesql.exporter.source.Checkpoints(root.resolve("second"), provenance, java.util.Collections.emptyList(), "fixture", saved -> {})) {
                int[] assets = {0};
                int units = checkpoint.replay(root.resolve("first"), receipt[0].get("sha256").getAsString(), dataset, rows, (kind, row) -> { if (kind.equals("assets")) assets[0]++; }, (unit, evidence) -> {});
                require(units == 1 && assets[0] == 2, "Sink journal lost native asset provenance or frame metadata");
                rows.write(dataset); require(source.equals(dataset.seal()), "Sink checkpoint changed Source bytes");
            }
        } finally {
            // Only this test's new temporary tree is owned here; production archives are never consulted.
            try (java.util.stream.Stream<java.nio.file.Path> files = java.nio.file.Files.walk(root)) {
                for (java.nio.file.Path file : files.sorted(java.util.Comparator.reverseOrder()).collect(java.util.stream.Collectors.toList())) java.nio.file.Files.delete(file);
            }
        }
    }
    private static String repeat(char value) { char[] text = new char[64]; Arrays.fill(text, value); return new String(text); }
    private static void preflight() throws Exception {
        net.minecraft.client.renderer.texture.TextureAtlasSprite sprite = new net.minecraft.client.renderer.texture.TextureAtlasSprite("fixture:animation") {};
        sprite.setIconWidth(1); sprite.setIconHeight(1);
        sprite.setFramesTextureData(Arrays.asList(new int[][] {new int[] {1}}, new int[][] {new int[] {2}}));
        cpw.mods.fml.relauncher.ReflectionHelper.setPrivateValue(net.minecraft.client.renderer.texture.TextureAtlasSprite.class, sprite,
                new net.minecraft.client.resources.data.AnimationMetadataSection(Arrays.asList(
                        new net.minecraft.client.resources.data.AnimationFrame(0, 1), new net.minecraft.client.resources.data.AnimationFrame(1, 1)), 1, 1, 1), "animationMetadata");
        checkAnimation(sprite);
        sprite.setFramesTextureData(Arrays.asList(new int[][] {new int[] {1}}, new int[][] {null}));
        try { checkAnimation(sprite); throw new AssertionError("Preflight missed an unavailable animation frame"); }
        catch (com.github.dcysteine.nesql.exporter.task.Jobs.Fault expected) { require(expected.code.equals("animation_missing"), "Wrong frame failure"); }
        sprite.setIconWidth(4096); sprite.setIconHeight(4096);
        try { checkAnimation(sprite); throw new AssertionError("Preflight missed oversized packed animation"); }
        catch (com.github.dcysteine.nesql.exporter.task.Jobs.Fault expected) { require(expected.code.equals("texture_limit"), "Wrong budget failure"); }
    }
    private static void checkAnimation(net.minecraft.client.renderer.texture.TextureAtlasSprite sprite) throws Exception {
        java.lang.reflect.Method method = Images.class.getDeclaredMethod("checkAnimation", net.minecraft.client.renderer.texture.TextureAtlasSprite.class, int.class);
        method.setAccessible(true);
        try { method.invoke(null, sprite, 0xffffff); }
        catch (java.lang.reflect.InvocationTargetException failure) { throw (Exception) failure.getCause(); }
    }
    private static void assets() throws Exception {
        java.nio.file.Path root=java.nio.file.Files.createTempDirectory("nesql-visual-assets-");
        try (com.github.dcysteine.nesql.exporter.source.Dataset dataset=new com.github.dcysteine.nesql.exporter.source.Dataset(root.resolve("source"),"fixture",new com.google.gson.JsonObject(),false);
             com.github.dcysteine.nesql.exporter.source.Rows rows=new com.github.dcysteine.nesql.exporter.source.Rows(root.resolve("rows"))) {
            Class<?> type=Class.forName(Capture.class.getName()+"$Sink");
            java.lang.reflect.Constructor<?> constructor=type.getDeclaredConstructors()[0];constructor.setAccessible(true);
            Object sink=constructor.newInstance(dataset,rows,null,null,true,null,null);
            java.lang.reflect.Method asset=type.getDeclaredMethod("asset",Images.Image.class);asset.setAccessible(true);
            try (AutoCloseable owner=(AutoCloseable)sink) {
                Images.Image first=new Images.Image(pixels(8,4,0xff123456),new JsonArray(),"capture","native:first");
                String a=(String)asset.invoke(sink,first),repeat=(String)asset.invoke(sink,first);
                Images.Image other=new Images.Image(pixels(8,4,0xff123456),new JsonArray(),"capture","native:other");
                String b=(String)asset.invoke(sink,other);
                JsonArray frames=new JsonArray(); frames.add(com.github.dcysteine.nesql.exporter.source.Json.object("x",0,"y",0,"width",8,"height",4,"ticks",4));
                String c=(String)asset.invoke(sink,new Images.Image(first.pixels,frames,"capture","native:first"));
                String path="assets/"+com.github.dcysteine.nesql.exporter.source.CanonicalJson.digest(encode(first.pixels))+".png";
                com.google.gson.JsonObject expected=com.github.dcysteine.nesql.exporter.source.Json.object("path",path,"width",8,"height",4,"frames",new JsonArray(),"interpolate",false,
                        "source",com.github.dcysteine.nesql.exporter.source.Json.object("kind","capture","location","native:first"));
                require(a.equals(com.github.dcysteine.nesql.exporter.source.Identity.content("asset",expected)) && a.equals(repeat) && !a.equals(b) && !a.equals(c),
                        "PNG reuse changed asset identity, provenance or animation metadata");
                try(java.util.stream.Stream<java.nio.file.Path> files=java.nio.file.Files.list(root.resolve("source/assets"))) {
                    require(files.count()==1,"Identical PNGs produced multiple files");
                }
                require(rows.check().get("assets")==3,"Duplicate asset rows changed the unique result");
            }
        }
        java.nio.file.Files.delete(root);
    }
    private static void png() throws Exception {
        PngCache cache = new PngCache(4096, 2);
        BufferedImage a = pixels(8, 4, 0xff123456), same = pixels(8, 4, 0xff123456);
        byte[] expected = encode(a), first = cache.encode(a), second = cache.encode(same);
        require(Arrays.equals(first, expected) && second == first, "Equal ARGB pixels must reuse exact original PNG bytes");
        BufferedImage parent = pixels(10, 6, 0xff123456);
        require(cache.encode(parent.getSubimage(1, 1, 8, 4)) == first, "Subimage offsets corrupted the pixel key");
        same.setRGB(7, 3, 0x00123457);
        require(Arrays.equals(cache.encode(same), encode(same)), "Changed transparent RGB was lost");
        BufferedImage reshaped = pixels(4, 8, 0xff123456);
        require(Arrays.equals(cache.encode(reshaped), encode(reshaped)), "Dimensions were omitted from reuse identity");
        require(cache.encode(a) != first, "LRU entry bound did not evict old pixels");
        PngCache tiny = new PngCache(1, 2);
        require(tiny.encode(a) != tiny.encode(a), "Oversized PNG must not enter cache");
        BufferedImage indexed = new BufferedImage(8, 4, BufferedImage.TYPE_BYTE_INDEXED);
        require(Arrays.equals(cache.encode(indexed), encode(indexed)) && cache.encode(indexed) != cache.encode(indexed),
                "Different color models must retain their original encoding");
    }
    private static void batches() throws Exception {
        List<String> saved = new ArrayList<>(); int[] recipe = {7};
        VisualBatch batch = new VisualBatch();
        for (int i = 0; i < 40; i++) {
            final int index = i;
            batch.add(() -> new Images.Image(pixels(2, 2, recipe[0]), new JsonArray(), "capture", "recipe:"+recipe[0]+":"+index),
                    image -> saved.add(image.location + ":" + image.pixels.getRGB(0,0)));
        }
        batch.capture();
        require(saved.isEmpty() && !batch.done(), "Capture must not write assets or exceed 32 operations");
        try { batch.capture(); throw new AssertionError("Undrained pixels were accumulated"); }
        catch (IllegalStateException expected) { }
        batch.write();
        while (!batch.done()) { batch.capture(); batch.write(); }
        recipe[0] = 8;
        require(saved.size()==40, "Capture lost pictures");
        for (int i=0;i<40;i++) require(saved.get(i).equals("recipe:7:"+i+":7"), "Cursor advanced before its pictures completed");
        VisualBatch bounded = new VisualBatch(); int[] captured={0};
        for(int i=0;i<3;i++) bounded.add(() -> { captured[0]++; return new Images.Image(pixels(1024,1024,1),new JsonArray(),"capture","large"); }, image -> {});
        bounded.capture(); require(captured[0]==1, "Pixel budget did not yield"); bounded.write();
        VisualBatch failure = new VisualBatch();
        failure.add(() -> { throw new IllegalArgumentException("native failure"); }, image -> {throw new AssertionError("Failed image committed");});
        try {failure.capture(); throw new AssertionError("Capture error swallowed");} catch (IllegalArgumentException expected) { }
    }
    static BufferedImage pixels(int width,int height,int color) {
        BufferedImage image=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);
        int[] row=new int[width]; Arrays.fill(row,color);
        for(int y=0;y<height;y++)image.setRGB(0,y,width,1,row,0,width);
        return image;
    }
    static byte[] encode(BufferedImage image) throws Exception {ByteArrayOutputStream out=new ByteArrayOutputStream();ImageIO.write(image,"png",out);return out.toByteArray();}
    static void require(boolean ok,String message) {if(!ok)throw new AssertionError(message);}
}
