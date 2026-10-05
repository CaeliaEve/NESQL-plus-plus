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
    public static void main(String[] args) throws Exception { png(); batches(); assets(); OverlapTest.run(); System.out.println("Visual throughput: PNG bytes, provenance independence, eviction and ordered bounded capture passed"); }
    private static void assets() throws Exception {
        java.nio.file.Path root=java.nio.file.Files.createTempDirectory("nesql-visual-assets-");
        try (com.github.dcysteine.nesql.exporter.source.Dataset dataset=new com.github.dcysteine.nesql.exporter.source.Dataset(root.resolve("source"),"fixture",new com.google.gson.JsonObject(),false);
             com.github.dcysteine.nesql.exporter.source.Rows rows=new com.github.dcysteine.nesql.exporter.source.Rows(root.resolve("rows"))) {
            Class<?> type=Class.forName(Capture.class.getName()+"$Sink");
            java.lang.reflect.Constructor<?> constructor=type.getDeclaredConstructors()[0];constructor.setAccessible(true);
            Object sink=constructor.newInstance(dataset,rows,null,null,true);
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
