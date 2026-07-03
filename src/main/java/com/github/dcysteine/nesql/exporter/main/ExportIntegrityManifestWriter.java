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
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Writes stable export identity/checksum artifacts for NeoNEI import and cache invalidation. */
final class ExportIntegrityManifestWriter {
    private static final IntegrityArtifactCatalog ARTIFACT_CATALOG =
            IntegrityArtifactCatalog.validateAndFreeze(
                    Arrays.asList(
                            new FileArtifactDescriptor("raw-manifest", RawExportFileCatalog.MANIFEST_FILE),
                            new FileArtifactDescriptor("raw-report", RawExportFileCatalog.EXPORT_REPORT_FILE),
                            new FileArtifactDescriptor(
                                    "health",
                                    RawExportFileCatalog.validationPath(
                                            RawExportFileCatalog.EXPORT_HEALTH_REPORT_FILE_NAME)),
                            new FileArtifactDescriptor(
                                    "manifest",
                                    RawExportFileCatalog.validationPath(
                                            RawExportFileCatalog.EXPORT_MANIFEST_FILE_NAME)),
                            new FileArtifactDescriptor(
                                    "checksums",
                                    RawExportFileCatalog.validationPath(
                                            RawExportFileCatalog.STAGE_CHECKSUMS_FILE_NAME)),
                            new FileArtifactDescriptor(
                                    "timings",
                                    ExportDebugFile.STAGE_TIMING.validationAliasPath()),
                            new FileArtifactDescriptor(
                                    "native-ui-validation",
                                    RawExportFileCatalog.NATIVE_UI_VALIDATION_FILE)),
                    Arrays.asList(
                            new FileArtifactDescriptor("recipes", RawExportFileCatalog.RECIPE_INDEX_FILE),
                            new FileArtifactDescriptor(
                                    "browser-atlas",
                                    RawExportFileCatalog.BROWSER_ATLAS_INDEX_FILE),
                            new FileArtifactDescriptor("render-backend", RawExportFileCatalog.RENDER_BACKEND_FILE),
                            new FileArtifactDescriptor("special", RawExportFileCatalog.SPECIAL_INDEX_FILE)),
                    Arrays.asList(
                            new DirectoryArtifactDescriptor("items", ArtifactRoot.REPOSITORY, "items"),
                            new DirectoryArtifactDescriptor("recipes", ArtifactRoot.REPOSITORY, "recipes"),
                            new DirectoryArtifactDescriptor("images", ArtifactRoot.REPOSITORY, "image"),
                            new DirectoryArtifactDescriptor("raw-facts", ArtifactRoot.RAW_EXPORT, "facts"),
                            new DirectoryArtifactDescriptor("raw-assets", ArtifactRoot.RAW_EXPORT, "assets"),
                            new DirectoryArtifactDescriptor("raw-models", ArtifactRoot.RAW_EXPORT, "models"),
                            new DirectoryArtifactDescriptor("raw-special", ArtifactRoot.RAW_EXPORT, "special"),
                            new DirectoryArtifactDescriptor(
                                    "raw-control",
                                    ArtifactRoot.RAW_EXPORT,
                                    RawExportFileCatalog.CONTROL_DIRECTORY),
                            new DirectoryArtifactDescriptor(
                                    "raw-debug",
                                    ArtifactRoot.RAW_EXPORT,
                                    RawExportFileCatalog.DEBUG_DIRECTORY)));

    private static final List<FileArtifactDescriptor> FILE_ARTIFACTS_BEFORE_DYNAMIC =
            ARTIFACT_CATALOG.beforeDynamicFiles();
    private static final List<FileArtifactDescriptor> FILE_ARTIFACTS_AFTER_DYNAMIC =
            ARTIFACT_CATALOG.afterDynamicFiles();
    private static final List<DirectoryArtifactDescriptor> DIRECTORY_ARTIFACTS =
            ARTIFACT_CATALOG.directories();

    private ExportIntegrityManifestWriter() {}

    static void write(ExportContext exportContext) throws Exception {
        File repositoryDirectory = exportContext.paths.repositoryDirectory;
        File rawDir = RawExportFileCatalog.rawExportDirectory(repositoryDirectory);
        File validationDir = RawExportFileCatalog.validationDirectory(rawDir);
        ExportIntegrityOutputFileCatalog.ensureDirectory(validationDir);

        File checksumFile = ExportIntegrityOutputFileCatalog.checksumFile(validationDir);
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

        for (ExportIntegrityOutputFileCatalog.OutputFile outputFile
                : ExportIntegrityOutputFileCatalog.outputFiles(validationDir, manifest, checksumReport)) {
            writeJson(outputFile.file, outputFile.payload);
        }

        Logger.chatMessage(
                EnumChatFormatting.GREEN
                        + "[NESQL] Export manifest/checksums written: "
                        + ExportIntegrityOutputFileCatalog.manifestFile(validationDir).getAbsolutePath());
    }

    private static List<ArtifactChecksum> collectArtifacts(File repositoryDirectory, File rawDir) throws Exception {
        List<ArtifactChecksum> artifacts = new ArrayList<ArtifactChecksum>();
        addFileArtifacts(artifacts, repositoryDirectory, rawDir, FILE_ARTIFACTS_BEFORE_DYNAMIC);
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
        addFileArtifacts(artifacts, repositoryDirectory, rawDir, FILE_ARTIFACTS_AFTER_DYNAMIC);
        addDirectoryArtifacts(artifacts, repositoryDirectory, rawDir);
        return artifacts;
    }

    private static void addFileArtifacts(
            List<ArtifactChecksum> artifacts,
            File repositoryDirectory,
            File rawDir,
            List<FileArtifactDescriptor> descriptors) throws Exception {
        for (FileArtifactDescriptor descriptor : descriptors) {
            addFile(
                    artifacts,
                    repositoryDirectory,
                    RawExportFileCatalog.rawExportFile(rawDir, descriptor.relativePath),
                    descriptor.stage);
        }
    }

    private static void addFile(List<ArtifactChecksum> artifacts, File root, File file, String stage) throws Exception {
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
            String stage) throws Exception {
        addFile(artifacts, root, new File(rawDir, file.rawExportPath().replace('/', File.separatorChar)), stage);
    }

    private static void addDebugFile(
            List<ArtifactChecksum> artifacts,
            File root,
            File rawDir,
            ExportDebugFile file,
            String stage) throws Exception {
        addFile(artifacts, root, new File(rawDir, file.rawExportDebugPath().replace('/', File.separatorChar)), stage);
    }

    private static void addDirectoryArtifacts(
            List<ArtifactChecksum> artifacts,
            File repositoryDirectory,
            File rawDir) throws Exception {
        for (DirectoryArtifactDescriptor descriptor : DIRECTORY_ARTIFACTS) {
            addDirectorySummary(
                    artifacts,
                    repositoryDirectory,
                    artifactDirectory(repositoryDirectory, rawDir, descriptor),
                    descriptor.stage);
        }
    }

    private static File artifactDirectory(
            File repositoryDirectory,
            File rawDir,
            DirectoryArtifactDescriptor descriptor) {
        File root = descriptor.root == ArtifactRoot.RAW_EXPORT ? rawDir : repositoryDirectory;
        return new File(root, descriptor.relativePath.replace('/', File.separatorChar));
    }

    private static void addDirectorySummary(
            List<ArtifactChecksum> artifacts,
            File root,
            File dir,
            String stage) throws Exception {
        if (dir.exists() && !dir.isDirectory()) {
            throw new IOException(
                    "Export integrity directory artifact path exists but is not a directory: "
                            + dir.getAbsolutePath());
        }
        DirectoryStats stats = new DirectoryStats();
        if (dir.exists()) {
            collectDirectoryStats(dir, stats);
        }
        ArtifactChecksum artifact = new ArtifactChecksum();
        artifact.stage = stage;
        artifact.family = stage;
        artifact.path = relative(root, dir);
        artifact.exists = dir.exists();
        artifact.fileCount = stats.fileCount;
        artifact.bytes = stats.bytes;
        artifact.sha256 = stats.digest();
        artifacts.add(artifact);
    }

    private static void collectDirectoryStats(File file, DirectoryStats stats) throws Exception {
        collectDirectoryStats(file, file, stats);
    }

    private static void collectDirectoryStats(File root, File file, DirectoryStats stats) throws Exception {
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
            throw new IOException("Failed to list export integrity artifact directory: " + file.getAbsolutePath());
        }
        for (File child : children) {
            collectDirectoryStats(root, child, stats);
        }
    }

    private static Map<String, ArtifactChecksum> readPreviousArtifacts(File checksumFile) throws Exception {
        Map<String, ArtifactChecksum> previous = new HashMap<String, ArtifactChecksum>();
        if (checksumFile == null) {
            throw new IOException("Export integrity checksum file must not be null");
        }
        if (!checksumFile.exists()) {
            return previous;
        }
        if (!checksumFile.isFile()) {
            throw new IOException(
                    "Export integrity checksum path exists but is not a file: " + checksumFile.getAbsolutePath());
        }
        try (InputStreamReader reader =
                     new InputStreamReader(new FileInputStream(checksumFile), StandardCharsets.UTF_8)) {
            ChecksumReport report = new Gson().fromJson(reader, ChecksumReport.class);
            if (report == null) {
                throw new IOException(
                        "Export integrity checksum report is empty or null JSON: "
                                + checksumFile.getAbsolutePath());
            }
            if (report.artifacts == null) {
                throw new IOException(
                        "Export integrity checksum report artifacts must not be null: "
                                + checksumFile.getAbsolutePath());
            }
            for (ArtifactChecksum artifact : report.artifacts) {
                previous.put(artifactKey(artifact), artifact);
            }
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

    private static String sha256(File file) throws Exception {
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
    }

    private static String relative(File root, File file) {
        try {
            return root.toPath().relativize(file.toPath()).toString().replace(File.separatorChar, '/');
        } catch (Exception ignored) {
            return file.getPath().replace(File.separatorChar, '/');
        }
    }

    private static void ensureDirectory(File directory) throws Exception {
        ExportIntegrityOutputFileCatalog.ensureDirectory(directory);
    }

    private static void writeJson(File file, Object payload) throws Exception {
        File parent = file.getParentFile();
        ensureDirectory(parent);
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

    private static void requireArtifactGroup(String group, List<?> descriptors) {
        if (descriptors == null || descriptors.isEmpty()) {
            throw new IllegalStateException("Export integrity " + group + " artifact catalog must not be empty");
        }
    }

    private static void requireArtifactStage(String stage, String descriptorName) {
        if (stage == null || stage.trim().isEmpty()) {
            throw new IllegalStateException("Export integrity artifact stage must be non-empty: " + descriptorName);
        }
        for (int i = 0; i < stage.length(); i++) {
            char value = stage.charAt(i);
            boolean valid =
                    (value >= 'a' && value <= 'z')
                            || (value >= '0' && value <= '9')
                            || value == '-';
            if (!valid) {
                throw new IllegalStateException(
                        "Export integrity artifact stage must be kebab-case: " + descriptorName);
            }
        }
    }

    private static void requireRuntimeRelativePath(String label, String value, String descriptorName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalStateException(label + " must be non-empty: " + descriptorName);
        }
        if (value.startsWith("/")
                || value.startsWith("\\")
                || value.indexOf('\\') >= 0
                || value.contains("..")
                || value.startsWith(RawExportFileCatalog.RAW_EXPORT_DIRECTORY + "/")
                || value.startsWith("./")
                || value.endsWith("/")
                || value.indexOf("//") >= 0) {
            throw new IllegalStateException(label + " must be a runtime-relative artifact path: " + descriptorName);
        }
    }

    private static final class IntegrityArtifactCatalog {
        private final List<FileArtifactDescriptor> beforeDynamicFiles;
        private final List<FileArtifactDescriptor> afterDynamicFiles;
        private final List<DirectoryArtifactDescriptor> directories;

        private IntegrityArtifactCatalog(
                List<FileArtifactDescriptor> beforeDynamicFiles,
                List<FileArtifactDescriptor> afterDynamicFiles,
                List<DirectoryArtifactDescriptor> directories) {
            this.beforeDynamicFiles = beforeDynamicFiles;
            this.afterDynamicFiles = afterDynamicFiles;
            this.directories = directories;
        }

        private static IntegrityArtifactCatalog validateAndFreeze(
                List<FileArtifactDescriptor> beforeDynamicFiles,
                List<FileArtifactDescriptor> afterDynamicFiles,
                List<DirectoryArtifactDescriptor> directories) {
            requireArtifactGroup("before-dynamic file", beforeDynamicFiles);
            requireArtifactGroup("after-dynamic file", afterDynamicFiles);
            requireArtifactGroup("directory", directories);
            Set<String> paths = new LinkedHashSet<String>();
            Set<String> identities = new LinkedHashSet<String>();
            List<FileArtifactDescriptor> validatedBefore = validateFileArtifacts(
                    "before-dynamic",
                    beforeDynamicFiles,
                    paths,
                    identities);
            List<FileArtifactDescriptor> validatedAfter = validateFileArtifacts(
                    "after-dynamic",
                    afterDynamicFiles,
                    paths,
                    identities);
            List<DirectoryArtifactDescriptor> validatedDirectories = validateDirectoryArtifacts(
                    directories,
                    paths,
                    identities);
            return new IntegrityArtifactCatalog(validatedBefore, validatedAfter, validatedDirectories);
        }

        private static List<FileArtifactDescriptor> validateFileArtifacts(
                String group,
                List<FileArtifactDescriptor> descriptors,
                Set<String> paths,
                Set<String> identities) {
            List<FileArtifactDescriptor> validated = new ArrayList<FileArtifactDescriptor>();
            for (FileArtifactDescriptor descriptor : descriptors) {
                if (descriptor == null) {
                    throw new IllegalStateException(
                            "Export integrity file artifact descriptor must not be null: " + group);
                }
                descriptor.validate(paths, identities);
                validated.add(descriptor);
            }
            return Collections.unmodifiableList(validated);
        }

        private static List<DirectoryArtifactDescriptor> validateDirectoryArtifacts(
                List<DirectoryArtifactDescriptor> descriptors,
                Set<String> paths,
                Set<String> identities) {
            List<DirectoryArtifactDescriptor> validated = new ArrayList<DirectoryArtifactDescriptor>();
            for (DirectoryArtifactDescriptor descriptor : descriptors) {
                if (descriptor == null) {
                    throw new IllegalStateException("Export integrity directory artifact descriptor must not be null");
                }
                descriptor.validate(paths, identities);
                validated.add(descriptor);
            }
            return Collections.unmodifiableList(validated);
        }

        private List<FileArtifactDescriptor> beforeDynamicFiles() {
            return beforeDynamicFiles;
        }

        private List<FileArtifactDescriptor> afterDynamicFiles() {
            return afterDynamicFiles;
        }

        private List<DirectoryArtifactDescriptor> directories() {
            return directories;
        }
    }

    private static final class FileArtifactDescriptor {
        private final String stage;
        private final String relativePath;

        private FileArtifactDescriptor(String stage, String relativePath) {
            this.stage = stage;
            this.relativePath = relativePath;
        }

        private void validate(Set<String> paths, Set<String> identities) {
            requireArtifactStage(stage, stage);
            requireRuntimeRelativePath("Export integrity file artifact path", relativePath, stage);
            requireUniqueArtifactPath(paths, ArtifactRoot.RAW_EXPORT, relativePath);
            requireUniqueArtifactIdentity(identities, stage, ArtifactRoot.RAW_EXPORT, relativePath);
        }
    }

    private static final class DirectoryArtifactDescriptor {
        private final String stage;
        private final ArtifactRoot root;
        private final String relativePath;

        private DirectoryArtifactDescriptor(String stage, ArtifactRoot root, String relativePath) {
            this.stage = stage;
            this.root = root;
            this.relativePath = relativePath;
        }

        private void validate(Set<String> paths, Set<String> identities) {
            requireArtifactStage(stage, stage);
            if (root == null) {
                throw new IllegalStateException("Export integrity directory artifact root must be non-null: " + stage);
            }
            requireRuntimeRelativePath("Export integrity directory artifact path", relativePath, stage);
            requireUniqueArtifactPath(paths, root, relativePath);
            requireUniqueArtifactIdentity(identities, stage, root, relativePath);
        }
    }

    private static void requireUniqueArtifactPath(Set<String> paths, ArtifactRoot root, String relativePath) {
        String key = root.name() + ":" + relativePath;
        if (!paths.add(key)) {
            throw new IllegalStateException("Duplicate export integrity artifact path: " + key);
        }
    }

    private static void requireUniqueArtifactIdentity(
            Set<String> identities,
            String stage,
            ArtifactRoot root,
            String relativePath) {
        String key = stage + "|" + root.name() + ":" + relativePath;
        if (!identities.add(key)) {
            throw new IllegalStateException("Duplicate export integrity artifact descriptor: " + key);
        }
    }

    private enum ArtifactRoot {
        REPOSITORY,
        RAW_EXPORT
    }

    static final class ExportManifest {
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

    static final class ChecksumReport {
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
