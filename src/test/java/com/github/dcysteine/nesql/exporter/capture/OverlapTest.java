package com.github.dcysteine.nesql.exporter.capture;

import com.github.dcysteine.nesql.exporter.source.Dataset;
import com.github.dcysteine.nesql.exporter.source.Rows;
import com.github.dcysteine.nesql.exporter.task.Pipeline;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.*;
import static com.github.dcysteine.nesql.exporter.source.Json.object;
import static com.github.dcysteine.nesql.exporter.capture.VisualThroughputTest.require;

/** Catch per-recipe flushes, native JSON alias leakage and out-of-order records. */
final class OverlapTest {
    static void run() throws Exception {
        Path root=Files.createTempDirectory("nesql-overlap-");
        ExecutorService exporter=Executors.newSingleThreadExecutor();
        CountDownLatch release=new CountDownLatch(1), started=new CountDownLatch(1);
        try (Dataset dataset=new Dataset(root.resolve("source"),"fixture",new JsonObject(),false);
             Rows rows=new Rows(root.resolve("rows"))) {
            Class<?> type=Class.forName(Capture.class.getName()+"$Sink");
            java.lang.reflect.Constructor<?> ctor=type.getDeclaredConstructors()[0];ctor.setAccessible(true);
            Object sink=ctor.newInstance(dataset,rows,null,null,true,null,null);
            Field field=type.getDeclaredField("encoding");field.setAccessible(true);
            Pipeline queue=(Pipeline)field.get(sink);
            Method records=type.getDeclaredMethod("records",Facts.Batch.class);records.setAccessible(true);
            Method visuals=type.getDeclaredMethod("visuals",Facts.Batch.class);visuals.setAccessible(true);
            try (AutoCloseable owned=(AutoCloseable)sink) {
                Facts.Batch batch=new Facts.Batch();
                JsonObject shared=object("amount",2);
                JsonObject recipe=object("id","recipe_fixture","nested",shared,"view",null);
                JsonObject sprite=object("kind","sprite","asset",null,"x",0,"y",0,"width",1,"height",1,"z",1);
                JsonArray elements=new JsonArray();elements.add(sprite);
                batch.records.add(new Facts.Record("recipes",recipe));
                Facts.Batch priming=new Facts.Batch();
                priming.pictures.add(new Facts.Picture(sprite,"asset","fixture:progress",()->{}));
                Field saves=VisualBatch.class.getDeclaredField("saves");saves.setAccessible(true);
                Images.Image image=new Images.Image(VisualThroughputTest.pixels(2,2,0xff00ff00),new JsonArray(),"capture","fixture:pixel");
                VisualBatch first=(VisualBatch)visuals.invoke(sink,priming);
                @SuppressWarnings("unchecked") List<VisualBatch.Save> initial=(List<VisualBatch.Save>)saves.get(first);
                initial.get(0).accept(image);records.invoke(sink,priming);
                require(!sprite.get("asset").isJsonNull(),"Shared native layout advanced before its picture asset ID was available");
                queue.submit(1,()->{started.countDown();require(release.await(5,TimeUnit.SECONDS),"Encoder test latch timed out");return new byte[0];},bytes->{});
                require(started.await(2,TimeUnit.SECONDS),"Encoder did not start");
                JsonObject item=object("id","item_fixture","icon",null,"name","before");
                batch.icons.add(new Facts.Icon("items",item,null,null,"fixture:item"));
                batch.scenes.add(new Facts.Scene(recipe,elements,16,16,0,"fixture:scene",()->{}));
                VisualBatch visual=(VisualBatch)visuals.invoke(sink,batch);
                @SuppressWarnings("unchecked") List<VisualBatch.Save> commits=(List<VisualBatch.Save>)saves.get(visual);
                // Use production save callbacks, keeping only native/GL drawing out of this test.
                commits.get(0).accept(image);commits.get(1).accept(image);
                Future<?> detached=exporter.submit(()->{records.invoke(sink,batch);return null;});
                try {detached.get(2,TimeUnit.SECONDS);}
                catch(TimeoutException blocked) {throw new AssertionError("Recipe records blocked behind PNG encoding; next native capture cannot overlap",blocked);}
                // A second native recipe mutates all the original shared JSON while PNG is blocked.
                shared.addProperty("amount",999);sprite.addProperty("x",999);elements.add(object("kind","invalid"));item.addProperty("name","after");
                Facts.Batch next=new Facts.Batch();next.records.add(new Facts.Record("recipes",object("id","recipe_next","amount",3)));
                records.invoke(sink,next);
                release.countDown();queue.flush();
                // The actual queued view must use the old elements with its late asset ID filled in.
                String imageId=com.github.dcysteine.nesql.exporter.source.Identity.content("asset",object("path",
                    "assets/"+com.github.dcysteine.nesql.exporter.source.CanonicalJson.digest(VisualThroughputTest.encode(image.pixels))+".png",
                    "width",2,"height",2,"frames",new JsonArray(),"interpolate",false,"source",object("kind","capture","location","fixture:pixel")));
                JsonArray expectedElements=new JsonArray();
                expectedElements.add(object("kind","sprite","asset",imageId,"x",0,"y",0,"width",16,"height",16,"z",0));
                expectedElements.add(object("kind","sprite","asset",imageId,"x",0,"y",0,"width",1,"height",1,"z",1));
                String viewId=com.github.dcysteine.nesql.exporter.source.Identity.content("view",object("width",16,"height",16,"elements",expectedElements));
                rows.add("recipes",object("id","recipe_fixture","nested",object("amount",2),"view",viewId));
                rows.add("items",object("id","item_fixture","icon",imageId,"name","before"));
                require(rows.check().get("recipes")==2,"Queued records were lost or native JSON leaked");
            } finally {release.countDown();}
        } finally {
            release.countDown();exporter.shutdownNow();require(exporter.awaitTermination(5,TimeUnit.SECONDS),"Exporter helper leaked");
            Files.delete(root);
        }
        failure();
        System.out.println("Recipe overlap: delayed PNG, detached nested JSON, late picture/view aliases, ordered commits and failure propagation passed");
    }
    private static void failure() throws Exception {
        try(Pipeline queue=new Pipeline()) {
            int[] committed={0};
            queue.submit(1,()->{throw new IllegalStateException("encode failure");},bytes->committed[0]++);
            queue.submit(1,()->new byte[0],bytes->committed[0]++);
            try{queue.flush();throw new AssertionError("Encoding failure swallowed");}
            catch(IllegalStateException expected){require(expected.getMessage().equals("encode failure"),"Wrong failure");}
            require(committed[0]==0,"Records committed after failed encoding");
        }
    }
}
