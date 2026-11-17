package com.github.dcysteine.nesql.exporter.capture;

import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonObject;

import static com.github.dcysteine.nesql.exporter.source.Json.object;

/** Detached render-probe accounting. Preview visibility is separate from the
 * native program closure; transparent native content is not a failure. */
final class VisualProbe {
    private long pixels,visible,programs,nodes,images,programPixels;

    void add(Images.Image image) {
        Jobs.checkpoint();
        if(image.visual!=null){
            VisualGraph.Inspection graph=VisualGraph.inspect(image.visual.root);
            programs=Math.addExact(programs,1);
            nodes=Math.addExact(nodes,graph.nodes);
            images=Math.addExact(images,graph.images);
            programPixels=Math.addExact(programPixels,graph.pixels);
        }
        pixels=Math.addExact(pixels,(long)image.pixels.getWidth()*image.pixels.getHeight());
        for(int y=0;y<image.pixels.getHeight();y++){
            Jobs.checkpoint();
            for(int x=0;x<image.pixels.getWidth();x++)if((image.pixels.getRGB(x,y)>>>24)!=0)visible++;
        }
    }

    JsonObject describe(int index,int icons,int pictures) {
        return object("index",index,"icons",icons,"pictures",pictures,
                "pixels",Long.toString(pixels),"visiblePixels",Long.toString(visible),
                "programs",Long.toString(programs),"programNodes",Long.toString(nodes),
                "programImages",Long.toString(images),"programPixels",Long.toString(programPixels));
    }
}
