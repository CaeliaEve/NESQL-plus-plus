package com.github.dcysteine.nesql.exporter.plugin.nei;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Session-local NEI handler timings, copied into export-plugin-timings.json after collection. */
public final class NeiExportTimingRegistry {
    private static final List<HandlerTiming> TIMINGS = new ArrayList<HandlerTiming>();

    private NeiExportTimingRegistry() {}

    public static synchronized void clear() {
        TIMINGS.clear();
    }

    public static synchronized void record(
            int index,
            int total,
            String handlerId,
            String handlerName,
            String handlerClass,
            boolean templateHandler,
            boolean itemScan,
            int loadedRecipes,
            int exportedRecipes,
            long loadElapsedMs,
            long exportElapsedMs,
            long totalElapsedMs) {
        TIMINGS.add(new HandlerTiming(
                index,
                total,
                handlerId,
                handlerName,
                handlerClass,
                templateHandler,
                itemScan,
                loadedRecipes,
                exportedRecipes,
                loadElapsedMs,
                exportElapsedMs,
                totalElapsedMs));
    }

    public static synchronized List<HandlerTiming> snapshot() {
        return new ArrayList<HandlerTiming>(TIMINGS);
    }

    public static synchronized List<HandlerTiming> slowestSnapshot(int limit) {
        List<HandlerTiming> copy = snapshot();
        copy.sort(new Comparator<HandlerTiming>() {
            @Override
            public int compare(HandlerTiming left, HandlerTiming right) {
                return Long.compare(right.totalElapsedMs, left.totalElapsedMs);
            }
        });
        if (copy.size() > limit) {
            return new ArrayList<HandlerTiming>(copy.subList(0, limit));
        }
        return copy;
    }

    public static final class HandlerTiming {
        public final int index;
        public final int total;
        public final String handlerId;
        public final String handlerName;
        public final String handlerClass;
        public final boolean templateHandler;
        public final boolean itemScan;
        public final int loadedRecipes;
        public final int exportedRecipes;
        public final long loadElapsedMs;
        public final String loadElapsed;
        public final long exportElapsedMs;
        public final String exportElapsed;
        public final long totalElapsedMs;
        public final String totalElapsed;

        HandlerTiming(
                int index,
                int total,
                String handlerId,
                String handlerName,
                String handlerClass,
                boolean templateHandler,
                boolean itemScan,
                int loadedRecipes,
                int exportedRecipes,
                long loadElapsedMs,
                long exportElapsedMs,
                long totalElapsedMs) {
            this.index = index;
            this.total = total;
            this.handlerId = handlerId;
            this.handlerName = handlerName;
            this.handlerClass = handlerClass;
            this.templateHandler = templateHandler;
            this.itemScan = itemScan;
            this.loadedRecipes = loadedRecipes;
            this.exportedRecipes = exportedRecipes;
            this.loadElapsedMs = loadElapsedMs;
            this.loadElapsed = formatDuration(loadElapsedMs);
            this.exportElapsedMs = exportElapsedMs;
            this.exportElapsed = formatDuration(exportElapsedMs);
            this.totalElapsedMs = totalElapsedMs;
            this.totalElapsed = formatDuration(totalElapsedMs);
        }

        private static String formatDuration(long elapsedMs) {
            long totalSeconds = Math.max(0L, elapsedMs / 1000L);
            long hours = totalSeconds / 3600L;
            long minutes = (totalSeconds % 3600L) / 60L;
            long seconds = totalSeconds % 60L;
            if (hours > 0L) {
                return String.format("%dh %02dm %02ds", hours, minutes, seconds);
            }
            if (minutes > 0L) {
                return String.format("%dm %02ds", minutes, seconds);
            }
            return String.format("%ds", seconds);
        }
    }
}
