package com.github.dcysteine.nesql.exporter.local;

import com.google.gson.Gson;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.zip.Deflater;
import java.util.zip.GZIPOutputStream;

final class ModBasedRecipeFileSupport {

    private ModBasedRecipeFileSupport() {}

    static String sanitizeModId(String modId) {
        return modId.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    static File jsonGzipFile(File parentDirectory, String fileNameWithoutGz) {
        return new File(parentDirectory, fileNameWithoutGz + ".gz");
    }

    static long writeCompressedJson(Gson gson, Object value, File compressedFile) throws IOException {
        File parent = compressedFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        try (FileOutputStream fos = new FileOutputStream(compressedFile);
             FastGzipOutputStream gzip = new FastGzipOutputStream(fos);
             OutputStreamWriter writer = new OutputStreamWriter(gzip, StandardCharsets.UTF_8)) {
            gson.toJson(value, writer);
        }
        return compressedFile.length();
    }

    static String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.2f KB", bytes / 1024.0);
        } else {
            return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
        }
    }

    private static final class FastGzipOutputStream extends GZIPOutputStream {
        private FastGzipOutputStream(FileOutputStream outputStream) throws IOException {
            super(outputStream, 1 << 20);
            def.setLevel(Deflater.BEST_SPEED);
        }
    }
}
