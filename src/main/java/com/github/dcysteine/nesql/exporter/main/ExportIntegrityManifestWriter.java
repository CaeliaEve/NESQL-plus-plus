package com.github.dcysteine.nesql.exporter.main;

import com.github.dcysteine.nesql.elysium.kernel.ExportControlFile;
import com.github.dcysteine.nesql.elysium.kernel.ExportDebugFile;
import com.github.dcysteine.nesql.elysium.kernel.ExportSchemaCatalog;
import com.github.dcysteine.nesql.exporter.local.RawExportFileCatalog;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.util.EnumChatFormatting;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Writes stable export identity/checksum artifacts for NeoNEI import and cache invalidation. */
final class ExportIntegrityManifestWriter {
    private ExportIntegrityManifestWriter() {}

    static void write(ExportContext exportContext) {
        try {
            File repositoryDirectory = exportContext.paths.repositoryDirectory;
            File rawDir = RawExportFileCatalog.rawExportDirectory(repositoryDirectory);
            File validationDir = RawExportFileCatalog.validationDirectory(rawDir);
            if (!validationDir.exists()) {
                validationDir.mkdirs();
            }

            File checksumFile = new File(validationDir, RawExportFileCatalog.STAGE_CHECKSUMS_FILE_NAME);
            Map<String, ArtifactChecksum> previousArtifacts = readPreviousArtifacts(checksumFile);
            List<ArtifactChecksum> artifacts = collectArtifacts(repositoryDirectory, rawDir);
            annotateChanges(artifacts, previousArtifacts);
            ExportManifest manifest = new ExportManifest();
            manifest.schemaVersion = ExportSchemaCatalog.EXPORT_MANIFEST;
            manifest.generatedAtEpochMs = System.currentTimeMillis();
            manifest.repository = exportContext.paths.repositoryName;
            manifest.profile = exportContext.profile.profileId;
            manifest.selection = exportContext.selection.describe();
            manifest.nesqlImplementationVersion = implementationVersion();
            manifest.databaseFile = relative(repositoryDirectory, exportContext.paths.databaseFile);
            manifest.imageDirectory = relative(repositoryDirectory, exportContext.paths.imageDirectory);
            manifest.rawExportDirectory = RawExportFileCatalog.RAW_EXPORT_DIRECTORY;
            manifest.artifacts = artifacts;

            ChecksumReport checksumReport = new ChecksumReport();
            checksumReport.schemaVersion = ExportSchemaCatalog.STAGE_CHECKSUMS;
            checksumReport.generatedAtEpochMs = manifest.generatedAtEpochMs;
            checksumReport.repository = manifest.repository;
            checksumReport.profile = manifest.profile;
            checksumReport.selection = manifest.selection;
            checksumReport.artifacts = artifacts;

            writeJson(new File(validationDir, RawExportFileCatalog.EXPORT_MANIFEST_FILE_NAME), manifest);
            writeJson(new File(validationDir, RawExportFileCatalog.EXPORT_MANIFEST_DASH_FILE_NAME), manifest);
            writeJson(checksumFile, checksumReport);
            writeJson(new File(validationDir, RawExportFileCatalog.STAGE_CHECKSUMS_DASH_FILE_NAME), checksumReport);

            Logger.chatMessage(
                    EnumChatFormatting.GREEN
                            + "[NESQL] Export manifest/checksums written: "
                            + new File(validationDir, RawExportFileCatalog.EXPORT_MANIFEST_FILE_NAME).getAbsolutePath());
        } catch (Exception e) {
            Logger.MOD.warn("Failed to write NESQL++ export manifest/checksums", e);
        }
    }

    private static List<ArtifactChecksum> collectArtifacts(File repositoryDirectory, File rawDir) {
        List<ArtifactChecksum> artifacts = new ArrayList<ArtifactChecksum>();
        addFile(
                artifacts,
                repositoryDirectory,
                RawExportFileCatalog.rawExportFile(rawDir, RawExportFileCatalog.MANIFEST_FILE),
                "raw-manifest");
        addFile(
                artifacts,
                repositoryDirectory,
                RawExportFileCatalog.rawExportFile(rawDir, RawExportFileCatalog.EXPORT_REPORT_FILE),
                "raw-report");
        addFile(
                artifacts,
                repositoryDirectory,
                RawExportFileCatalog.rawExportFile(
                        rawDir,
                        RawExportFileCatalog.validationPath(RawExportFileCatalog.EXPORT_HEALTH_REPORT_FILE_NAME)),
                "health");
        addFile(
                artifacts,
                repositoryDirectory,
                RawExportFileCatalog.rawExportFile(
                        rawDir,
                        RawExportFileCatalog.validationPath(RawExportFileCatalog.EXPORT_MANIFEST_FILE_NAME)),
                "manifest");
        addFile(
                artifacts,
                repositoryDirectory,
                RawExportFileCatalog.rawExportFile(
                        rawDir,
                        RawExportFileCatalog.validationPath(RawExportFileCatalog.STAGE_CHECKSUMS_FILE_NAME)),
                "checksums");
        addFile(
                artifacts,
                repositoryDirectory,
                RawExportFileCatalog.rawExportFile(rawDir, ExportDebugFile.STAGE_TIMING.validationAliasPath()),
                "timings");
        addFile(
                artifacts,
                repositoryDirectory,
                RawExportFileCatalog.rawExportFile(rawDir, RawExportFileCatalog.NATIVE_UI_VALIDATION_FILE),
                "native-ui-validation");
        for (ExportControlFile file : ExportControlFile.values()) {
            addControlFile(
                    artifacts,
                    repositoryDirectory,
                    rawDir,
                    file,
                    RawExportFileCatalog.controlArtifactStage(file));
        }
        for (ExportDebugFile file : ExportDebugFile.values()) {
            addDebugFile(
                    artifacts,
                    repositoryDirectory,
                    rawDir,
                    file,
                    RawExportFileCatalog.debugArtifactStage(file));
        }
        addFile(
                artifacts,
                repositoryDirectory,
                RawExportFileCatalog.rawExportFile(rawDir, "facts/recipes/index.json"),
                "recipes");
        addFile(
                artifacts,
                repositoryDirectory,
                RawExportFileCatalog.rawExportFile(rawDir, "assets/textures/browser_atlas_index.json"),
                "browser-atlas");
        addFile(
                artifacts,
                repositoryDirectory,
                RawExportFileCatalog.rawExportFile(rawDir, "facts/render/backend.json"),
                "render-backend");
        addFile(
                artifacts,
                repositoryDirectory,
                RawExportFileCatalog.rawExportFile(rawDir, "special/index.json"),
                "special");
        addDirectorySummary(artifacts, repositoryDirectory, new File(repositoryDirectory, "items"), "items");
        addDirectorySummary(artifacts, repositoryDirectory, new File(repositoryDirectory, "recipes"), "recipes");
        addDirectorySummary(artifacts, repositoryDirectory, new File(repositoryDirectory, "image"), "images");
        addDirectorySummary(artifacts, repositoryDirectory, new File(rawDir, "facts"), "raw-facts");
        addDirectorySummary(artifacts, repositoryDirectory, new File(rawDir, "assets"), "raw-assets");
        addDirectorySummary(artifacts, repositoryDirectory, new File(rawDir, "models"), "raw-models");
        addDirectorySummary(artifacts, repositoryDirectory, new File(rawDir, "special"), "raw-special");
        addDirectorySummary(
                artifacts,
                repositoryDirectory,
                new File(rawDir, RawExportFileCatalog.CONTROL_DIRECTORY),
                "raw-control");
        addDirectorySummary(
                artifacts,
                repositoryDirectory,
                new File(rawDir, RawExportFileCatalog.DEBUG_DIRECTORY),
                "raw-debug");
        return artifacts;
    }

    private static void addFile(List<ArtifactChecksum> artifacts, File root, File file, String stage) {
        ArtifactChecksum artifact = new ArtifactChecksum();
        artifact.stage = stage;
        artifact.family = stage;
        artifact.path = relative(root, file);
        artifact.exists = file.exists() && file.isFile();
        if (artifact.exists) {
            artifact.bytes = file.length();
            artifact.sha256 = sha256(file);
        }
        artifacts.add(artifact);
    }

    private static void addControlFile(
            List<ArtifactChecksum> artifacts,
            File root,
            File rawDir,
            ExportControlFile file,
            String stage) {
        addFile(artifacts, root, new File(rawDir, file.rawExportPath().replace('/', File.separatorChar)), stage);
    }

    private static void addDebugFile(
            List<ArtifactChecksum> artifacts,
            File root,
            File rawDir,
            ExportDebugFile file,
            String stage) {
        addFile(artifacts, root, new File(rawDir, file.rawExportDebugPath().replace('/', File.separatorChar)), stage);
    }

    private static void addDirectorySummary(List<ArtifactChecksum> artifacts, File root, File dir, String stage) {
        DirectoryStats stats = new DirectoryStats();
        collectDirectoryStats(dir, stats);
        ArtifactChecksum artifact = new ArtifactChecksum();
        artifact.stage = stage;
        artifact.family = stage;
        artifact.path = relative(root, dir);
        artifact.exists = dir.exists() && dir.isDirectory();
        artifact.fileCount = stats.fileCount;
        artifact.bytes = stats.bytes;
        artifact.sha256 = stats.digest();
        artifacts.add(artifact);
    }

    private static void collectDirectoryStats(File file, DirectoryStats stats) {
        collectDirectoryStats(file, file, stats);
    }

    private static void collectDirectoryStats(File root, File file, DirectoryStats stats) {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isFile()) {
            stats.fileCount++;
            stats.bytes += file.length();
            stats.update(relative(root, file), file.length());
            return;
        }
        File[] children = file.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            collectDirectoryStats(root, child, stats);
        }
    }

    private static Map<String, ArtifactChecksum> readPreviousArtifacts(File checksumFile) {
        Map<String, ArtifactChecksum> previous = new HashMap<String, ArtifactChecksum>();
        if (checksumFile == null || !checksumFile.exists()) {
            return previous;
        }
        try (InputStreamReader reader =
                     new InputStreamReader(new FileInputStream(checksumFile), StandardCharsets.UTF_8)) {
            ChecksumReport report = new Gson().fromJson(reader, ChecksumReport.class);
            if (report == null || report.artifacts == null) {
                return previous;
            }
            for (ArtifactChecksum artifact : report.artifacts) {
                previous.put(artifactKey(artifact), artifact);
            }
        } catch (Exception e) {
            Logger.MOD.warn("Failed to read previous NESQL++ stage checksums", e);
        }
        return previous;
    }

    private static void annotateChanges(
            List<ArtifactChecksum> artifacts,
            Map<String, ArtifactChecksum> previousArtifacts) {
        for (ArtifactChecksum artifact : artifacts) {
            ArtifactChecksum previous = previousArtifacts.get(artifactKey(artifact));
            artifact.previousSha256 = previous == null ? null : previous.sha256;
            artifact.changed =
                    previous == null
                            || artifact.exists != previous.exists
                            || artifact.bytes != previous.bytes
                            || artifact.fileCount != previous.fileCount
                            || !safeEquals(artifact.sha256, previous.sha256);
            artifact.unchanged = !artifact.changed;
            artifact.skippableByChecksum = artifact.exists && artifact.sha256 != null;
        }
    }

    private static String artifactKey(ArtifactChecksum artifact) {
        if (artifact == null) {
            return "";
        }
        return String.valueOf(artifact.stage) + "|" + String.valueOf(artifact.path);
    }

    private static boolean safeEquals(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }

    private static String sha256(File file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[1024 * 1024];
            try (FileInputStream fis = new FileInputStream(file)) {
                int read;
                while ((read = fis.read(buffer)) >= 0) {
                    if (read > 0) {
                        digest.update(buffer, 0, read);
                    }
                }
            }
            return toHex(digest.digest());
        } catch (Exception e) {
            Logger.MOD.warn("Failed to hash export artifact {}", file.getAbsolutePath(), e);
            return null;
        }
    }

    private static String relative(File root, File file) {
        try {
            return root.toPath().relativize(file.toPath()).toString().replace(File.separatorChar, '/');
        } catch (Exception ignored) {
            return file.getPath().replace(File.separatorChar, '/');
        }
    }

    private static void writeJson(File file, Object payload) throws Exception {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        try (FileOutputStream fos = new FileOutputStream(file, false);
             OutputStreamWriter writer = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
            new GsonBuilder().setPrettyPrinting().create().toJson(payload, writer);
        }
    }

    private static String implementationVersion() {
        Package pkg = ExportIntegrityManifestWriter.class.getPackage();
        String value = pkg == null ? null : pkg.getImplementationVersion();
        return value == null || value.trim().isEmpty() ? "unknown" : value;
    }

    private static String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(String.format("%02x", value & 0xff));
        }
        return builder.toString();
    }

    private static final class ExportManifest {
        String schemaVersion;
        long generatedAtEpochMs;
        String repository;
        String profile;
        String selection;
        String nesqlImplementationVersion;
        String databaseFile;
        String imageDirectory;
        String rawExportDirectory;
        List<ArtifactChecksum> artifacts;
    }

    private static final class ChecksumReport {
        String schemaVersion;
        long generatedAtEpochMs;
        String repository;
        String profile;
        String selection;
        List<ArtifactChecksum> artifacts;
    }

    private static final class ArtifactChecksum {
        String stage;
        String family;
        String path;
        boolean exists;
        long bytes;
        int fileCount;
        String sha256;
        String previousSha256;
        boolean changed;
        boolean unchanged;
        boolean skippableByChecksum;
    }

    private static final class DirectoryStats {
        long bytes;
        int fileCount;
        private final MessageDigest digest;

        DirectoryStats() {
            try {
                this.digest = MessageDigest.getInstance("SHA-256");
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        void update(String name, long bytes) {
            String value = name + ":" + bytes + "\n";
            digest.update(value.getBytes(StandardCharsets.UTF_8));
        }

        String digest() {
            return toHex(digest.digest());
        }
    }
}
