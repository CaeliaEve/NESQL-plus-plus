package com.github.dcysteine.nesql.exporter.capture;

import com.github.dcysteine.nesql.exporter.task.Jobs;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/** Capture a bounded pixel slice on the client, then consume it on the exporter.
 * The caller retains the native recipe until every slice has been submitted.
 * Detached record/PNG commits may finish later in the bounded output queue.
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
        // A native animation yields between frame slices. Do not advance its
        // owning recipe or publish an incomplete sheet.
        long started = System.nanoTime(), bytes = 0; int operations = 0;
        while (captured < draws.size() && operations++ < 32) {
            Jobs.checkpoint();
            Images.Image image = draws.get(captured).call();
            if (image == null) break;
            ready.add(image); captured++;
            bytes += (long) image.pixels.getWidth() * image.pixels.getHeight() * 4;
            if (bytes >= 4 * 1024 * 1024L || System.nanoTime() - started >= 4_000_000) break;
        }
    }
    void write() throws Exception {
        for (Images.Image image : ready) saves.get(written++).accept(image);
        ready.clear();
    }
    boolean done() { return written == draws.size(); }
}
