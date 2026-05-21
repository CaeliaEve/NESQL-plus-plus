package com.github.dcysteine.nesql.exporter.main;

import com.google.gson.GsonBuilder;
import net.minecraft.util.EnumChatFormatting;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

/** Writes stable export identity/checksum artifacts for NeoNEI import and cache invalidation. */
final class ExportIntegrityManifestWriter {
    private ExportIntegrityManifestWriter() {}

    static void write(ExportContext exportContext) {
        try {
            File repositoryDirectory = exportContext.paths.repositoryDirectory;
            File canonicalDir = new File(repositoryDirectory, "canonical");
            if (!canonicalDir.exists()) {
                canonicalDir.mkdirs();
            }

            List<ArtifactChecksum> artifacts = collectArtifacts(repositoryDirectory, canonicalDir);
            ExportManifest manifest = new ExportManifest();
            manifest.schemaVersion = "nesqlpp/export-manifest/v1";
            manifest.generatedAtEpochMs = System.currentTimeMillis();
            manifest.repository = exportContext.paths.repositoryName;
            manifest.profile = exportContext.profile.profileId;
            manifest.selection = exportContext.selection.describe();
            manifest.nesqlImplementationVersion = implementationVersion();
            manifest.databaseFile = relative(repositoryDirectory, exportContext.paths.databaseFile);
            manifest.imageDirectory = relative(repositoryDirectory, exportContext.paths.imageDirectory);
            manifest.canonicalDirectory = "canonical";
            manifest.artifacts = artifacts;

            ChecksumReport checksumReport = new ChecksumReport();
            checksumReport.schemaVersion = "nesqlpp/stage-checksums/v1";
            checksumReport.generatedAtEpochMs = manifest.generatedAtEpochMs;
            checksumReport.repository = manifest.repository;
            checksumReport.profile = manifest.profile;
            checksumReport.selection = manifest.selection;
            checksumReport.artifacts = artifacts;

            writeJson(new File(canonicalDir, "export-manifest.json"), manifest);
            writeJson(new File(canonicalDir, "stage-checksums.json"), checksumReport);

            Logger.chatMessage(
                    EnumChatFormatting.GREEN
                            + "[NESQL] Export manifest/checksums written: "
                            + new File(canonicalDir, "export-manifest.json").getAbsolutePath());
        } catch (Exception e) {
            Logger.MOD.warn("Failed to write NESQL++ export manifest/checksums", e);
        }
    }

    private static List<ArtifactChecksum> collectArtifacts(File repositoryDirectory, File canonicalDir) {
        List<ArtifactChecksum> artifacts = new ArrayList<ArtifactChecksum>();
        addFile(artifacts, repositoryDirectory, new File(canonicalDir, "export-stage-timings.json"), "timings");
        addFile(artifacts, repositoryDirectory, new File(canonicalDir, "export-validation-report.json"), "health");
        addFile(artifacts, repositoryDirectory, new File(canonicalDir, "export-health-report.json"), "health");
        addFile(artifacts, repositoryDirectory, new File(canonicalDir, "render-assets.json"), "render-assets");
        addFile(artifacts, repositoryDirectory, new File(canonicalDir, "animation-manifest.json"), "animation");
        addFile(artifacts, repositoryDirectory, new File(canonicalDir, "atlas-manifest.json"), "atlas");
        addFile(artifacts, repositoryDirectory, new File(canonicalDir, "animated-atlas-manifest.json"), "animated-atlas");
        addFile(artifacts, repositoryDirectory, new File(canonicalDir, "browser-layout-index.json"), "browser-layout");
        addFile(artifacts, repositoryDirectory, new File(canonicalDir, "browser-atlas-index.json"), "browser-atlas");
        addFile(artifacts, repositoryDirectory, new File(canonicalDir, "render-index.json"), "render-index");
        addFile(artifacts, repositoryDirectory, new File(canonicalDir, "multiblock-blueprints.json"), "multiblocks");
        addFile(artifacts, repositoryDirectory, new File(canonicalDir, "block-face-metadata.json"), "block-faces");
        addFile(artifacts, repositoryDirectory, new File(canonicalDir, "entity-previews.json"), "entity-previews");
        addFile(artifacts, repositoryDirectory, new File(canonicalDir, "entity-models.json"), "entity-models");
        addDirectorySummary(artifacts, repositoryDirectory, new File(repositoryDirectory, "items"), "items");
        addDirectorySummary(artifacts, repositoryDirectory, new File(repositoryDirectory, "recipes"), "recipes");
        addDirectorySummary(artifacts, repositoryDirectory, new File(repositoryDirectory, "image"), "images");
        addDirectorySummary(artifacts, repositoryDirectory, new File(canonicalDir, "atlases"), "atlas-pages");
        addDirectorySummary(artifacts, repositoryDirectory, new File(canonicalDir, "animated-atlases"), "animated-atlas-pages");
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
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isFile()) {
            stats.fileCount++;
            stats.bytes += file.length();
            stats.update(file.getName(), file.length(), file.lastModified());
            return;
        }
        File[] children = file.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            collectDirectoryStats(child, stats);
        }
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
        String canonicalDirectory;
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

        void update(String name, long bytes, long modifiedAt) {
            String value = name + ":" + bytes + ":" + modifiedAt + "\n";
            digest.update(value.getBytes(StandardCharsets.UTF_8));
        }

        String digest() {
            return toHex(digest.digest());
        }
    }
}
