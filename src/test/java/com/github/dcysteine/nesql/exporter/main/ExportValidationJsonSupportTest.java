package com.github.dcysteine.nesql.exporter.main;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ExportValidationJsonSupportTest {
    private ExportValidationJsonSupportTest() {}

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("nesql-validation-file-counts-");
        try {
            Files.createFile(root.resolve("first.png"));
            Files.createFile(root.resolve("only.gif"));
            Files.createFile(root.resolve("first.render.json"));
            Files.createFile(root.resolve("only.sprite.json"));
            Path nested = Files.createDirectories(root.resolve("nested"));
            Files.createFile(nested.resolve("second.png"));
            Files.createFile(nested.resolve("second.render.json"));
            Files.createFile(nested.resolve("ignored.txt"));

            int[] counts = ExportValidationJsonSupport.countFilesBySuffix(
                    root.toFile(), ".png", ".gif", ".render.json", ".sprite.json");
            require(counts.length == 4, "one count is returned for every suffix");
            require(counts[0] == 2, "PNG files are counted recursively");
            require(counts[1] == 1, "GIF files are counted recursively");
            require(counts[2] == 2, "render metadata files are counted recursively");
            require(counts[3] == 1, "sprite metadata files are counted recursively");
            require(
                    ExportValidationJsonSupport.countFiles(root.toFile(), ".png") == counts[0],
                    "single-suffix compatibility delegates to the shared traversal");

            int[] duplicateAndOverlapping = ExportValidationJsonSupport.countFilesBySuffix(
                    root.toFile(), ".png", ".png", ".json", ".render.json", "");
            require(duplicateAndOverlapping[0] == 2, "first duplicate suffix count is preserved");
            require(duplicateAndOverlapping[1] == 2, "second duplicate suffix count is preserved");
            require(duplicateAndOverlapping[2] == 3, "overlapping JSON suffix counts every matching file");
            require(duplicateAndOverlapping[3] == 2, "specific overlapping suffix count is independent");
            require(duplicateAndOverlapping[4] == 7, "empty suffix counts every file");

            final int[] directoriesVisited = {0};
            ExportValidationJsonSupport.countFilesBySuffix(
                    root.toFile(),
                    directory -> directoriesVisited[0]++,
                    ".png",
                    ".gif",
                    ".render.json",
                    ".sprite.json");
            require(directoriesVisited[0] == 2, "the shared production traversal enumerates each directory once");

            int[] missingRootCounts = ExportValidationJsonSupport.countFilesBySuffix(
                    new File(root.toFile(), "missing"), ".png", ".gif");
            require(missingRootCounts[0] == 0 && missingRootCounts[1] == 0, "missing roots return zero counts");
            int[] nullRootCounts = ExportValidationJsonSupport.countFilesBySuffix(null, ".png");
            require(nullRootCounts[0] == 0, "null roots return zero counts");
            requireThrows(
                    () -> ExportValidationJsonSupport.countFilesBySuffix(root.toFile(), ".png", null),
                    "null suffixes fail explicitly");
        } finally {
            deleteRecursively(root.toFile());
        }
    }

    private static void deleteRecursively(File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        file.delete();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void requireThrows(Runnable action, String message) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError(message);
    }
}
