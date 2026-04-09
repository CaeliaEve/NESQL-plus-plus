package com.github.dcysteine.nesql.exporter.local;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.GZIPOutputStream;

final class ModBasedRecipeFileSupport {

    private ModBasedRecipeFileSupport() {}

    static String sanitizeModId(String modId) {
        return modId.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    static File compressFile(File sourceFile) throws IOException {
        File compressedFile = new File(sourceFile.getAbsolutePath() + ".gz");
        try (FileInputStream fis = new FileInputStream(sourceFile);
             FileOutputStream fos = new FileOutputStream(compressedFile);
             GZIPOutputStream gzip = new GZIPOutputStream(fos)) {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = fis.read(buffer)) > 0) {
                gzip.write(buffer, 0, len);
            }
        }
        return compressedFile;
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
}
