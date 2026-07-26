package com.github.dcysteine.nesql.exporter.local;

import com.github.dcysteine.nesql.exporter.main.ExportValidationReadiness;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/** Fail-closed content, catalog, and readiness validation before generation pointer promotion. */
final class RawExportGenerationValidator implements RawExportGeneration.PublicationValidator {
    private static final String RAW_EXPORT_PREFIX = RawExportFileCatalog.RAW_EXPORT_DIRECTORY + "/";

    @Override
    public void validate(File generationDirectory) throws IOException {
        requireDirectory(generationDirectory, "raw-export generation");
        rejectUnsafeOrTemporaryOutputs(generationDirectory, generationDirectory);

        JsonObject manifest = requireJsonObject(generationDirectory, RawExportFileCatalog.MANIFEST_FILE);
        validateManifestFiles(generationDirectory, manifest);

        JsonObject rawReport = requireJsonObject(generationDirectory, RawExportFileCatalog.EXPORT_REPORT_FILE);
        requireReadyRawValidation(rawReport);
        JsonObject healthReport = requireJsonObject(
                generationDirectory,
                RawExportFileCatalog.validationPath(
                        RawExportFileCatalog.EXPORT_VALIDATION_REPORT_FILE_NAME));
        requireCompileReady(healthReport);

        validateRecipeIndex(generationDirectory);
        validateJsonAndGzipFiles(generationDirectory);
        validateIntegrityCatalog(generationDirectory);
        validateSizeReport(generationDirectory);

        for (String prohibitedPath : RawExportFileCatalog.prohibitedRootOutputs()) {
            File prohibited = RawExportFileCatalog.rawExportFile(generationDirectory, prohibitedPath);
            if (prohibited.exists()) {
                throw new IOException(
                        "Raw-export generation contains prohibited legacy output: "
                                + prohibited.getAbsolutePath());
            }
        }
    }

    private static void validateManifestFiles(File generationDirectory, JsonObject manifest)
            throws IOException {
        JsonObject files = objectMember(manifest, "files", "raw-export manifest");
        if (files.entrySet().isEmpty()) {
            throw new IOException("Raw-export manifest files catalog must not be empty");
        }
        Map<String, String> expectedFiles = new LinkedHashMap<String, String>();
        RawExportManifestBuilder.putManifestFiles(
                expectedFiles,
                files.has("uiFamilyCensus"),
                files.has("uiTemplateCatalog"));
        if (files.entrySet().size() != expectedFiles.size()) {
            throw new IOException(
                    "Raw-export manifest file catalog size mismatch: declared="
                            + files.entrySet().size()
                            + ", required="
                            + expectedFiles.size());
        }
        for (Map.Entry<String, String> required : expectedFiles.entrySet()) {
            JsonElement declared = files.get(required.getKey());
            if (declared == null) {
                throw new IOException("Raw-export manifest is missing required file key: " + required.getKey());
            }
            String declaredPath = jsonString(declared, "raw-export manifest file " + required.getKey());
            if (!required.getValue().equals(declaredPath)) {
                throw new IOException(
                        "Raw-export manifest path mismatch for "
                                + required.getKey()
                                + ": declared="
                                + declaredPath
                                + ", required="
                                + required.getValue());
            }
        }
        for (Map.Entry<String, JsonElement> entry : files.entrySet()) {
            String path = jsonString(entry.getValue(), "raw-export manifest file " + entry.getKey());
            requireRuntimeRelativePath(path, "raw-export manifest file " + entry.getKey());
            requireRegularFile(generationDirectory, path);
        }
    }

    private static void validateRecipeIndex(File generationDirectory) throws IOException {
        JsonObject index = requireJsonObject(generationDirectory, RawExportFileCatalog.RECIPE_INDEX_FILE);
        JsonArray shards = arrayMember(index, "shards", "recipe index");
        long declaredShardCount = longMember(index, "shardCount", "recipe index");
        long declaredRecipeCount = longMember(index, "recipeCount", "recipe index");
        if (declaredShardCount != shards.size()) {
            throw new IOException(
                    "Recipe index shardCount mismatch: declared="
                            + declaredShardCount
                            + ", actual="
                            + shards.size());
        }
        long totalRecipes = 0L;
        for (int indexPosition = 0; indexPosition < shards.size(); indexPosition++) {
            JsonElement element = shards.get(indexPosition);
            if (element == null || !element.isJsonObject()) {
                throw new IOException("Recipe index shard entry is not an object: " + indexPosition);
            }
            JsonObject shard = element.getAsJsonObject();
            String path = stringMember(shard, "path", "recipe index shard");
            requireRuntimeRelativePath(path, "recipe index shard path");
            if (!path.startsWith("facts/recipes/by-handler/") || !path.endsWith(".jsonl.gz")) {
                throw new IOException("Recipe shard path is outside the handler shard ABI: " + path);
            }
            File shardFile = requireRegularFile(generationDirectory, path);
            long declaredCount = longMember(shard, "recipeCount", "recipe index shard");
            long actualCount = countJsonlGzip(shardFile);
            if (declaredCount != actualCount) {
                throw new IOException(
                        "Recipe shard count mismatch for "
                                + path
                                + ": declared="
                                + declaredCount
                                + ", actual="
                                + actualCount);
            }
            totalRecipes += actualCount;
        }
        if (declaredRecipeCount != totalRecipes) {
            throw new IOException(
                    "Recipe index recipeCount mismatch: declared="
                            + declaredRecipeCount
                            + ", actual="
                            + totalRecipes);
        }
    }

    private static void validateJsonAndGzipFiles(File file) throws IOException {
        if (file.isDirectory()) {
            File[] children = sortedChildren(file);
            for (File child : children) {
                validateJsonAndGzipFiles(child);
            }
            return;
        }
        String name = file.getName().toLowerCase(java.util.Locale.ROOT);
        if (name.endsWith(".jsonl.gz")) {
            countJsonlGzip(file);
        } else if (name.endsWith(".json.gz")) {
            parseGzipJson(file);
        } else if (name.endsWith(".gz")) {
            readGzipFully(file);
        } else if (name.endsWith(".jsonl")) {
            countJsonl(file);
        } else if (name.endsWith(".json")) {
            parseJson(file);
        }
    }

    private static void validateIntegrityCatalog(File generationDirectory) throws IOException {
        JsonObject checksums = requireJsonObject(
                generationDirectory,
                RawExportFileCatalog.validationPath(RawExportFileCatalog.STAGE_CHECKSUMS_FILE_NAME));
        JsonObject exportManifest = requireJsonObject(
                generationDirectory,
                RawExportFileCatalog.validationPath(RawExportFileCatalog.EXPORT_MANIFEST_FILE_NAME));
        JsonArray checksumArtifacts = arrayMember(checksums, "artifacts", "stage checksum report");
        JsonArray manifestArtifacts = arrayMember(exportManifest, "artifacts", "export integrity manifest");
        if (checksumArtifacts.size() == 0) {
            throw new IOException("Export integrity artifact catalog must not be empty");
        }
        if (!checksumArtifacts.equals(manifestArtifacts)) {
            throw new IOException("Export manifest and stage checksum artifact catalogs differ");
        }
        Map<String, Boolean> seenPaths = new HashMap<String, Boolean>();
        for (int index = 0; index < checksumArtifacts.size(); index++) {
            JsonElement element = checksumArtifacts.get(index);
            if (element == null || !element.isJsonObject()) {
                throw new IOException("Integrity artifact entry is not an object: " + index);
            }
            validateIntegrityArtifact(generationDirectory, element.getAsJsonObject(), seenPaths);
        }
        for (String requiredPath : Arrays.asList(
                RAW_EXPORT_PREFIX + RawExportFileCatalog.MANIFEST_FILE,
                RAW_EXPORT_PREFIX + RawExportFileCatalog.EXPORT_REPORT_FILE,
                RAW_EXPORT_PREFIX + "facts",
                RAW_EXPORT_PREFIX + "assets",
                RAW_EXPORT_PREFIX + "models",
                RAW_EXPORT_PREFIX + "special",
                RAW_EXPORT_PREFIX + RawExportFileCatalog.CONTROL_DIRECTORY,
                RAW_EXPORT_PREFIX + RawExportFileCatalog.DEBUG_DIRECTORY)) {
            if (!seenPaths.containsKey(requiredPath)) {
                throw new IOException("Export integrity catalog is missing required artifact: " + requiredPath);
            }
        }
    }

    private static void validateIntegrityArtifact(
            File generationDirectory,
            JsonObject artifact,
            Map<String, Boolean> seenPaths) throws IOException {
        String path = stringMember(artifact, "path", "integrity artifact");
        if (seenPaths.put(path, Boolean.TRUE) != null) {
            throw new IOException("Duplicate integrity artifact path: " + path);
        }
        if (!path.startsWith(RAW_EXPORT_PREFIX)) {
            return;
        }
        String relativePath = path.substring(RAW_EXPORT_PREFIX.length());
        requireRuntimeRelativePath(relativePath, "integrity artifact path");
        File actual = RawExportFileCatalog.rawExportFile(generationDirectory, relativePath);
        boolean expectedExists = booleanMember(artifact, "exists", "integrity artifact");
        boolean actualExists = actual.exists();
        if (expectedExists != actualExists) {
            throw new IOException(
                    "Integrity artifact existence mismatch for "
                            + path
                            + ": declared="
                            + expectedExists
                            + ", actual="
                            + actualExists);
        }
        if (!actualExists) {
            return;
        }
        long expectedBytes = longMember(artifact, "bytes", "integrity artifact");
        int expectedFileCount = intMember(artifact, "fileCount", "integrity artifact");
        String expectedSha256 = stringMember(artifact, "sha256", "integrity artifact");
        if (actual.isFile()) {
            requireRegularFile(generationDirectory, relativePath);
            requireEqual(path + " bytes", expectedBytes, actual.length());
            requireEqual(path + " fileCount", expectedFileCount, 0);
            requireEqual(path + " sha256", expectedSha256, sha256(actual));
            return;
        }
        if (!actual.isDirectory() || Files.isSymbolicLink(actual.toPath())) {
            throw new IOException("Integrity artifact is not a regular directory: " + actual.getAbsolutePath());
        }
        DirectoryStats stats = new DirectoryStats();
        collectDirectoryStats(actual, actual, stats);
        requireEqual(path + " bytes", expectedBytes, stats.bytes);
        requireEqual(path + " fileCount", expectedFileCount, stats.fileCount);
        requireEqual(path + " sha256", expectedSha256, stats.digest());
    }

    private static void validateSizeReport(File generationDirectory) throws IOException {
        JsonObject report = requireJsonObject(generationDirectory, RawExportFileCatalog.SIZE_REPORT_FILE);
        String strategy = stringMember(report, "strategy", "raw-export size report");
        if (!RawExportFileCatalog.SIZE_REPORT_STRATEGY.equals(strategy)) {
            throw new IOException("Unexpected raw-export size report strategy: " + strategy);
        }
        if (!"pass".equals(stringMember(report, "status", "raw-export size report"))) {
            throw new IOException("Raw-export size report does not pass prohibited-output validation");
        }
        JsonArray prohibited = arrayMember(report, "prohibitedOutputs", "raw-export size report");
        if (prohibited.size() != 0) {
            throw new IOException("Raw-export size report contains prohibited outputs");
        }
        long expectedBytes = RawExportSizeReportBuilder.directorySizeExcludingSizeReport(generationDirectory);
        long declaredBytes = longMember(report, "totalBytes", "raw-export size report");
        requireEqual("raw-export size report totalBytes", declaredBytes, expectedBytes);
    }

    private static void requireReadyRawValidation(JsonObject report) throws IOException {
        JsonObject validation = objectMember(report, "validation", "raw-export report");
        String readiness = stringMember(validation, "readinessStatus", "raw-export validation");
        if (!"ready".equals(readiness)) {
            String blockedGates = blockedGateSummary(validation);
            throw new IOException(
                    "Raw-export generation validation is not ready for publication: "
                            + readiness
                            + (blockedGates.isEmpty() ? "" : "; blocked gates: " + blockedGates));
        }
    }

    private static String blockedGateSummary(JsonObject validation) {
        if (!validation.has("gates") || !validation.get("gates").isJsonArray()) {
            return "";
        }
        StringBuilder summary = new StringBuilder();
        JsonArray gates = validation.getAsJsonArray("gates");
        for (JsonElement element : gates) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject gate = element.getAsJsonObject();
            String status = optionalStringMember(gate, "status");
            if ("ready".equals(status)) {
                continue;
            }
            if (summary.length() > 0) {
                summary.append(" | ");
            }
            String name = optionalStringMember(gate, "name");
            String detail = optionalStringMember(gate, "summary");
            summary.append(name == null ? "<unnamed>" : name)
                    .append('=')
                    .append(status == null ? "<missing-status>" : status);
            if (detail != null && !detail.isEmpty()) {
                summary.append(" (").append(detail).append(')');
            }
        }
        return summary.toString();
    }

    private static String optionalStringMember(JsonObject object, String memberName) {
        if (object == null || !object.has(memberName) || !object.get(memberName).isJsonPrimitive()) {
            return null;
        }
        String value = object.get(memberName).getAsString();
        return value == null ? null : value.trim();
    }

    private static void requireCompileReady(JsonObject report) throws IOException {
        String readiness = stringMember(report, "compileReadinessStatus", "export validation report");
        if (!ExportValidationReadiness.isPublishable(readiness)) {
            throw new IOException("Raw-export generation compile readiness blocks publication: " + readiness);
        }
    }

    private static long countJsonlGzip(File file) throws IOException {
        try (FileInputStream input = new FileInputStream(file);
             GZIPInputStream gzip = new GZIPInputStream(input);
             InputStreamReader decoded = new InputStreamReader(gzip, StandardCharsets.UTF_8);
             BufferedReader reader = new BufferedReader(decoded)) {
            return countJsonlRows(file, reader);
        } catch (IOException e) {
            throw new IOException("Failed to read gzip JSONL: " + file.getAbsolutePath(), e);
        }
    }

    private static long countJsonl(File file) throws IOException {
        try (FileInputStream input = new FileInputStream(file);
             InputStreamReader decoded = new InputStreamReader(input, StandardCharsets.UTF_8);
             BufferedReader reader = new BufferedReader(decoded)) {
            return countJsonlRows(file, reader);
        }
    }

    private static long countJsonlRows(File file, BufferedReader reader) throws IOException {
        long rows = 0L;
        String line;
        JsonParser parser = new JsonParser();
        while ((line = reader.readLine()) != null) {
            if (line.trim().isEmpty()) {
                continue;
            }
            try {
                JsonElement element = parser.parse(line);
                if (element == null || !element.isJsonObject()) {
                    throw new IOException("JSONL row is not an object");
                }
            } catch (IOException e) {
                throw e;
            } catch (Exception e) {
                throw new IOException(
                        "Invalid JSONL row " + (rows + 1L) + " in " + file.getAbsolutePath(),
                        e);
            }
            rows++;
        }
        return rows;
    }

    private static void parseGzipJson(File file) throws IOException {
        try (FileInputStream input = new FileInputStream(file);
             GZIPInputStream gzip = new GZIPInputStream(input);
             InputStreamReader reader = new InputStreamReader(gzip, StandardCharsets.UTF_8)) {
            parseJsonReader(file, reader);
        } catch (IOException e) {
            throw new IOException("Failed to read gzip JSON: " + file.getAbsolutePath(), e);
        }
    }

    private static void readGzipFully(File file) throws IOException {
        try (FileInputStream input = new FileInputStream(file);
             GZIPInputStream gzip = new GZIPInputStream(input)) {
            byte[] buffer = new byte[64 * 1024];
            while (gzip.read(buffer) >= 0) {
                // Exhaust the stream so CRC/truncation failures are observed.
            }
        } catch (IOException e) {
            throw new IOException("Failed to read gzip artifact: " + file.getAbsolutePath(), e);
        }
    }

    private static void parseJson(File file) throws IOException {
        try (FileInputStream input = new FileInputStream(file);
             InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            parseJsonReader(file, reader);
        }
    }

    private static void parseJsonReader(File file, InputStreamReader reader) throws IOException {
        try {
            JsonElement element = new JsonParser().parse(reader);
            if (element == null || element.isJsonNull()) {
                throw new IOException("JSON artifact is empty or null: " + file.getAbsolutePath());
            }
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Failed to parse JSON artifact: " + file.getAbsolutePath(), e);
        }
    }

    private static JsonObject requireJsonObject(File root, String relativePath) throws IOException {
        File file = requireRegularFile(root, relativePath);
        try (FileInputStream input = new FileInputStream(file);
             InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            JsonElement element = new JsonParser().parse(reader);
            if (element == null || !element.isJsonObject()) {
                throw new IOException("Raw-export publication JSON must contain an object: " + file.getAbsolutePath());
            }
            return element.getAsJsonObject();
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Failed to parse raw-export publication JSON: " + file.getAbsolutePath(), e);
        }
    }

    private static File requireRegularFile(File root, String relativePath) throws IOException {
        requireRuntimeRelativePath(relativePath, "raw-export file");
        File file = RawExportFileCatalog.rawExportFile(root, relativePath);
        if (!file.exists() || !file.isFile() || Files.isSymbolicLink(file.toPath())) {
            throw new IOException("Raw-export generation is missing required regular file: " + file.getAbsolutePath());
        }
        return file;
    }

    private static void requireRuntimeRelativePath(String path, String label) throws IOException {
        if (path == null
                || path.trim().isEmpty()
                || path.startsWith("/")
                || path.startsWith("\\")
                || path.startsWith("./")
                || path.startsWith(RAW_EXPORT_PREFIX)
                || path.contains("..")
                || path.indexOf('\\') >= 0) {
            throw new IOException(label + " is not a safe generation-relative path: " + path);
        }
    }

    private static void requireDirectory(File directory, String label) throws IOException {
        if (directory == null
                || !directory.exists()
                || !directory.isDirectory()
                || Files.isSymbolicLink(directory.toPath())) {
            throw new IOException(
                    "Missing " + label + ": "
                            + (directory == null ? "<null>" : directory.getAbsolutePath()));
        }
    }

    private static JsonObject objectMember(JsonObject parent, String key, String label) throws IOException {
        JsonElement element = parent.get(key);
        if (element == null || !element.isJsonObject()) {
            throw new IOException(label + " is missing object member: " + key);
        }
        return element.getAsJsonObject();
    }

    private static JsonArray arrayMember(JsonObject parent, String key, String label) throws IOException {
        JsonElement element = parent.get(key);
        if (element == null || !element.isJsonArray()) {
            throw new IOException(label + " is missing array member: " + key);
        }
        return element.getAsJsonArray();
    }

    private static String stringMember(JsonObject parent, String key, String label) throws IOException {
        JsonElement element = parent.get(key);
        if (element == null || element.isJsonNull()) {
            throw new IOException(label + " is missing string member: " + key);
        }
        return jsonString(element, label + " member " + key);
    }

    private static String jsonString(JsonElement element, String label) throws IOException {
        try {
            return element.getAsString();
        } catch (Exception e) {
            throw new IOException(label + " is not a string", e);
        }
    }

    private static long longMember(JsonObject parent, String key, String label) throws IOException {
        try {
            JsonElement element = parent.get(key);
            if (element == null || element.isJsonNull()) {
                throw new IOException(label + " is missing numeric member: " + key);
            }
            return element.getAsLong();
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException(label + " has invalid numeric member: " + key, e);
        }
    }

    private static int intMember(JsonObject parent, String key, String label) throws IOException {
        long value = longMember(parent, key, label);
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new IOException(label + " numeric member is outside int range: " + key);
        }
        return (int) value;
    }

    private static boolean booleanMember(JsonObject parent, String key, String label) throws IOException {
        try {
            JsonElement element = parent.get(key);
            if (element == null || element.isJsonNull()) {
                throw new IOException(label + " is missing boolean member: " + key);
            }
            return element.getAsBoolean();
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException(label + " has invalid boolean member: " + key, e);
        }
    }

    private static void rejectUnsafeOrTemporaryOutputs(File root, File file) throws IOException {
        if (Files.isSymbolicLink(file.toPath())) {
            throw new IOException("Raw-export generation contains a symbolic link: " + file.getAbsolutePath());
        }
        String name = file.getName();
        if (!file.equals(root)
                && (name.endsWith(".tmp") || name.endsWith(".part") || name.startsWith(".staging-"))) {
            throw new IOException("Raw-export generation contains temporary output: " + file.getAbsolutePath());
        }
        if (file.isDirectory()) {
            for (File child : sortedChildren(file)) {
                rejectUnsafeOrTemporaryOutputs(root, child);
            }
        }
    }

    private static File[] sortedChildren(File directory) throws IOException {
        File[] children = directory.listFiles();
        if (children == null) {
            throw new IOException("Failed to list raw-export generation directory: " + directory.getAbsolutePath());
        }
        Arrays.sort(children, new Comparator<File>() {
            @Override
            public int compare(File left, File right) {
                return left.getName().compareTo(right.getName());
            }
        });
        return children;
    }

    private static String sha256(File file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[1024 * 1024];
            try (FileInputStream input = new FileInputStream(file)) {
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    if (read > 0) {
                        digest.update(buffer, 0, read);
                    }
                }
            }
            return toHex(digest.digest());
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Failed to hash raw-export artifact: " + file.getAbsolutePath(), e);
        }
    }

    private static void collectDirectoryStats(File root, File file, DirectoryStats stats)
            throws IOException {
        if (file.isFile()) {
            stats.fileCount++;
            stats.bytes += file.length();
            stats.update(relative(root, file), file.length());
            return;
        }
        for (File child : sortedChildren(file)) {
            collectDirectoryStats(root, child, stats);
        }
    }

    private static String relative(File root, File file) {
        return root.toPath().relativize(file.toPath()).toString().replace(File.separatorChar, '/');
    }

    private static String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(String.format("%02x", value & 0xff));
        }
        return builder.toString();
    }

    private static void requireEqual(String label, long expected, long actual) throws IOException {
        if (expected != actual) {
            throw new IOException(label + " mismatch: declared=" + expected + ", actual=" + actual);
        }
    }

    private static void requireEqual(String label, String expected, String actual) throws IOException {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new IOException(label + " mismatch: declared=" + expected + ", actual=" + actual);
        }
    }

    private static final class DirectoryStats {
        long bytes;
        int fileCount;
        final MessageDigest digest;

        DirectoryStats() throws IOException {
            try {
                digest = MessageDigest.getInstance("SHA-256");
            } catch (Exception e) {
                throw new IOException("SHA-256 is unavailable", e);
            }
        }

        void update(String name, long bytes) {
            digest.update((name + ":" + bytes + "\n").getBytes(StandardCharsets.UTF_8));
        }

        String digest() {
            return toHex(digest.digest());
        }
    }
}
