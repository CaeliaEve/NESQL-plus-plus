package com.github.dcysteine.nesql.exporter.capture;

import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Shared dependencies and failed publication, not a GUI or renderer fixture. */
public final class VisualGraphTest {
    public static void run() throws Exception {
        preflight();
        Images.Image pixels = new Images.Image(VisualThroughputTest.pixels(2,2,0xffabcd12),new com.google.gson.JsonArray(),"capture","owned");
        VisualGraph.Node sampler=VisualGraph.node(object("kind","ownedSampler")).image("level",pixels);
        VisualGraph.Node root=VisualGraph.node(object("kind","ownedScene")).link("first",sampler).link("second",sampler);
        List<JsonObject> rows=new ArrayList<>();int[] writes={0};
        String id=VisualGraph.publish(root,new VisualGraph.Writer(){
            @Override public String asset(Images.Image image) throws Exception {writes[0]++;return "asset_"+CanonicalJson.digest(VisualThroughputTest.encode(image.pixels));}
            @Override public void row(JsonObject row){rows.add(row);}
        });
        require(writes[0]==1&&rows.size()==2,"Shared texture/sampler was encoded or published twice");
        JsonObject result=rows.get(1).getAsJsonObject("data");
        require(result.get("first").equals(result.get("second"))&&id.equals(rows.get(1).get("id").getAsString()),"Parent did not bind its exact resource closure");
        require(!rows.get(0).getAsJsonObject("data").has("first"),"Binding modified a borrowed child record");
        List<JsonObject> incomplete=new ArrayList<>();
        try{VisualGraph.publish(root,new VisualGraph.Writer(){
            @Override public String asset(Images.Image image){throw new java.util.concurrent.CancellationException("cancelled dependency");}
            @Override public void row(JsonObject row){incomplete.add(row);}
        });throw new AssertionError("Cancelled asset became a successful root");}catch(java.util.concurrent.CancellationException expected){}
        require(incomplete.isEmpty(),"A root/child was published before its texture dependency");
        VisualGraph.Node cycle=VisualGraph.node(object("kind","ownedCycle"));cycle.link("self",cycle);
        try{VisualGraph.publish(cycle,new VisualGraph.Writer(){
            @Override public String asset(Images.Image image){throw new AssertionError("Cycle reached asset writer");}
            @Override public void row(JsonObject row){throw new AssertionError("Cycle reached row writer");}
        });throw new AssertionError("Cyclic visual graph was admitted");}catch(IllegalArgumentException expected){}
        List<JsonObject> sharedRows=new ArrayList<>();VisualGraph.Publisher publisher=new VisualGraph.Publisher(new VisualGraph.Writer(){
            @Override public String asset(Images.Image image){throw new AssertionError("A shared lookup became a bitmap");}
            @Override public void row(JsonObject row){sharedRows.add(row);}
        });
        VisualGraph.Node lookup=VisualGraph.node(object("kind","ownedLookup","bits","12345678"));
        publisher.publish(VisualGraph.node(object("kind","sceneOne")).link("lookup",lookup));
        publisher.publish(VisualGraph.node(object("kind","sceneTwo")).link("lookup",lookup));
        require(sharedRows.size()==3&&sharedRows.get(1).getAsJsonObject("data").get("lookup").equals(sharedRows.get(2).getAsJsonObject("data").get("lookup")),
                "The same immutable lookup was serialized again for another inventory icon");
        try{lookup.link("late",sampler);throw new AssertionError("A published lookup changed after memoizing its identity");}catch(IllegalStateException expected){}
    }
    /** A valid preview must not conceal a broken program or count a shared
     * sampler twice. This runs the same collector used by diagnostic rendering. */
    private static void preflight() {
        Images.Image texture = new Images.Image(VisualThroughputTest.pixels(2,2,0xff123456),new com.google.gson.JsonArray(),"capture","texture");
        VisualGraph.Node sampler=VisualGraph.node(object("kind","ownedSampler")).image("level",texture);
        VisualGraph.Node root=VisualGraph.node(object("kind","ownedScene")).link("first",sampler).link("second",sampler);
        Images.Image preview=new Images.Image(VisualThroughputTest.pixels(1,1,0),new com.google.gson.JsonArray(),"capture","preview",false,new InventoryVisual(root));
        VisualProbe probe=new VisualProbe();probe.add(preview);
        JsonObject checked=probe.describe(6,1,0);
        require(checked.get("pixels").getAsLong()==1&&checked.get("visiblePixels").getAsLong()==0,
                "A legitimate transparent preview was rejected or counted as program pixels");
        require(checked.get("programs").getAsInt()==1&&checked.get("programNodes").getAsLong()==2
                        &&checked.get("programImages").getAsLong()==1&&checked.get("programPixels").getAsLong()==4,
                "Diagnostic preflight omitted or double-counted the native resource closure");
        VisualGraph.Node cycle=VisualGraph.node(object("kind","ownedCycle"));cycle.link("self",cycle);
        Images.Image invalid=new Images.Image(preview.pixels,new com.google.gson.JsonArray(),"capture","bad",false,new InventoryVisual(cycle));
        try{new VisualProbe().add(invalid);throw new AssertionError("A valid preview hid a cyclic program during preflight");}catch(IllegalArgumentException expected){}
        VisualGraph.Node nested=VisualGraph.node(object("kind","ownedSampler")).image("level",preview);
        try{VisualGraph.inspect(nested);throw new AssertionError("Preflight admitted a preview program as a sampler image");}catch(IllegalArgumentException expected){}
        VisualGraph.Node deep=VisualGraph.node(object("kind","ownedLeaf"));
        for(int i=0;i<65;i++)deep=VisualGraph.node(object("kind","ownedBranch")).link("child",deep);
        try{VisualGraph.inspect(deep);throw new AssertionError("Preflight skipped the publication depth budget");}catch(IllegalArgumentException expected){}
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
