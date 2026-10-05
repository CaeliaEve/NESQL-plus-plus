package com.github.dcysteine.nesql.exporter.capture;

import com.github.dcysteine.nesql.exporter.task.Slice;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/** Capture a bounded pixel slice on the client, then consume it on the exporter.
 * The caller retains the current native recipe until all slices have been written.
 */
final class VisualBatch {
    interface Save { void accept(Images.Image image) throws Exception; }
    private final List<Callable<Images.Image>> draws = new ArrayList<>();
    private final List<Save> saves = new ArrayList<>();
    private final List<Images.Image> ready = new ArrayList<>();
    private int captured, written;
    void add(Callable<Images.Image> draw, Save save) { draws.add(draw); saves.add(save); }
    void capture() throws Exception {
        if (!ready.isEmpty()) throw new IllegalStateException("Write the previous pixel slice before capturing another");
        captured = Slice.run(captured, draws.size(), index -> {
            Images.Image image = draws.get(index).call(); ready.add(image);
            return (long) image.pixels.getWidth() * image.pixels.getHeight() * 4;
        });
    }
    void write() throws Exception {
        for (Images.Image image : ready) saves.get(written++).accept(image);
        ready.clear();
    }
    boolean done() { return written == draws.size(); }
}
