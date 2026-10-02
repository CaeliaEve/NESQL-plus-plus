package com.github.dcysteine.nesql.exporter.task;

import java.util.function.LongSupplier;

/** Cooperative native work; a single native call cannot be preempted. */
public final class Slice {
    private Slice() {}
    public interface Step { long take(int index) throws Exception; }
    public static int run(int begin, int end, Step step) throws Exception {
        return run(begin, end, step, System::nanoTime);
    }
    static int run(int begin, int end, Step step, LongSupplier clock) throws Exception {
        long started = clock.getAsLong(), bytes = 0;
        int index = begin;
        while (index < end && index - begin < 32) {
            Jobs.checkpoint();
            long size = step.take(index++);
            if (size < 0) throw new IllegalArgumentException("Negative capture size");
            if (size >= 4 * 1024 * 1024L - bytes) break;
            bytes += size;
            if (clock.getAsLong() - started >= 4_000_000) break;
        }
        return index;
    }
}
