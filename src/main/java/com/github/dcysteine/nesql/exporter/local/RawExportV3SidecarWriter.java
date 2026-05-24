package com.github.dcysteine.nesql.exporter.local;

import com.github.dcysteine.nesql.exporter.canonical.CanonicalRenderAsset;
import com.github.dcysteine.nesql.exporter.main.ExportContext;
import com.github.dcysteine.nesql.exporter.main.Logger;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import jakarta.persistence.EntityManager;
import net.minecraft.util.EnumChatFormatting;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

/**
 * Writes the first raw-export v3 sidecar without replacing the current canonical
 * export structure.
 *
 * <p>This is intentionally conservative: the sidecar records counts, source
 * profile, selected stages, and references to the existing canonical outputs.
 * Later rebuild phases can replace each placeholder JSONL file with true raw
 * fact streams while NeoNEI continues to consume the current export layout.</p>
 */
public final class RawExportV3SidecarWriter {
    private static final String OUTPUT_DIRECTORY = "raw-export";
    private static final String SCHEMA_VERSION = "nesqlpp/raw-export/v3-alpha1";

    private final EntityManager entityManager;
    private final File repositoryDirectory;
    private final ExportContext exportContext;
    private final List<CanonicalRenderAsset> renderAssets;

    public RawExportV3SidecarWriter(
            EntityManager entityManager,
            File repositoryDirectory,
            ExportContext exportContext,
            List<CanonicalRenderAsset> renderAssets) {
        this.entityManager = entityManager;
        this.repositoryDirectory = repositoryDirectory;
        this.exportContext = exportContext;
        this.renderAssets = renderAssets == null
                ? java.util.Collections.<CanonicalRenderAsset>emptyList()
                : renderAssets;
    }

    public void export() throws IOException {
        File rawDir = new File(repositoryDirectory, OUTPUT_DIRECTORY);
        ensureDirectory(rawDir);

        createJsonlPlaceholders(rawDir);

        RawExportReport report = buildReport();
        RawExportManifest manifest = buildManifest(report);

        Gson gson = new GsonBuilder().setPrettyPrinting().serializeNulls().create();
        writeJson(gson, new File(rawDir, "manifest.json"), manifest);
        writeJson(gson, new File(rawDir, "export_report.json"), report);

        Logger.chatMessage(EnumChatFormatting.GREEN + "Raw-export v3 sidecar written:");
        Logger.chatMessage(EnumChatFormatting.YELLOW + "  " + rawDir.getAbsolutePath());
    }

    private RawExportManifest buildManifest(RawExportReport report) {
        RawExportManifest manifest = new RawExportManifest();
        manifest.schemaVersion = SCHEMA_VERSION;
        manifest.generatedAt = utcNow();
        manifest.repositoryName = exportContext.paths.repositoryName;
        manifest.profile = exportContext.profile.profileId;
        manifest.selection = exportContext.selection.describe();
        manifest.status = "sidecar-alpha";
        manifest.notes.add("Canonical export remains authoritative in this phase.");
        manifest.notes.add("JSONL files are present as stable compiler targets and will be populated incrementally.");
        manifest.files.add(fileRef("items", "items.jsonl", "placeholder-jsonl"));
        manifest.files.add(fileRef("fluids", "fluids.jsonl", "placeholder-jsonl"));
        manifest.files.add(fileRef("recipes", "recipes.jsonl", "placeholder-jsonl"));
        manifest.files.add(fileRef("groups", "groups.jsonl", "placeholder-jsonl"));
        manifest.files.add(fileRef("nei-order", "nei_order.jsonl", "placeholder-jsonl"));
        manifest.files.add(fileRef("textures", "textures.jsonl", "placeholder-jsonl"));
        manifest.files.add(fileRef("animations", "animations.jsonl", "placeholder-jsonl"));
        manifest.files.add(fileRef("nei-handlers", "nei_handlers.jsonl", "placeholder-jsonl"));
        manifest.files.add(fileRef("multiblocks", "multiblocks.jsonl", "placeholder-jsonl"));
        manifest.files.add(fileRef("entities", "entities.jsonl", "placeholder-jsonl"));
        manifest.files.add(fileRef("export-report", "export_report.json", "report"));
        manifest.files.add(fileRef("stage-timings", "../canonical/export-stage-timings.json", "canonical-report"));
        manifest.files.add(fileRef("integrity-manifest", "../canonical/export-integrity-manifest.json", "canonical-report"));
        manifest.files.add(fileRef("canonical-repository", "../canonical/repository.json", "canonical-source"));
        manifest.counts = report.counts;
        return manifest;
    }

    private RawExportReport buildReport() {
        RawExportReport report = new RawExportReport();
        report.schemaVersion = SCHEMA_VERSION + "/report";
        report.generatedAt = utcNow();
        report.profile = exportContext.profile.profileId;
        report.selection = exportContext.selection.describe();

        RawExportCounts counts = new RawExportCounts();
        counts.items = countQuery("SELECT COUNT(i) FROM Item i");
        counts.fluids = countQuery("SELECT COUNT(f) FROM Fluid f");
        counts.recipes = countQuery("SELECT COUNT(r) FROM Recipe r");
        counts.recipeTypes = countQuery("SELECT COUNT(rt) FROM RecipeType rt");
        counts.renderAssets = renderAssets.size();
        counts.itemModFiles = countFiles(new File(repositoryDirectory, "items"), "items.json.gz");
        counts.recipeModFiles = countFiles(new File(repositoryDirectory, "recipes"), "recipes.json.gz");
        counts.canonicalFiles = countFiles(new File(repositoryDirectory, "canonical"), null);
        report.counts = counts;

        report.validation.missingTextureCount = 0;
        report.validation.missingAnimationMetadataCount = 0;
        report.validation.missingGroupOrOrderCount = 0;
        report.validation.failedStages = new ArrayList<String>();
        report.validation.status = "not-yet-enforced";
        return report;
    }

    private long countQuery(String query) {
        if (entityManager == null) {
            return -1L;
        }
        try {
            Object value = entityManager.createQuery(query).getSingleResult();
            if (value instanceof Number) {
                return ((Number) value).longValue();
            }
        } catch (Exception e) {
            Logger.MOD.warn("Failed to calculate raw-export count for query: " + query, e);
        }
        return -1L;
    }

    private static FileRef fileRef(String logicalName, String path, String kind) {
        FileRef ref = new FileRef();
        ref.logicalName = logicalName;
        ref.path = path;
        ref.kind = kind;
        return ref;
    }

    private static void createJsonlPlaceholders(File rawDir) throws IOException {
        String[] names = new String[] {
                "items.jsonl",
                "fluids.jsonl",
                "recipes.jsonl",
                "groups.jsonl",
                "nei_order.jsonl",
                "textures.jsonl",
                "animations.jsonl",
                "nei_handlers.jsonl",
                "multiblocks.jsonl",
                "entities.jsonl"
        };
        for (String name : names) {
            File file = new File(rawDir, name);
            if (!file.exists()) {
                try (FileOutputStream ignored = new FileOutputStream(file)) {
                    // Empty JSONL is a valid placeholder for alpha sidecars.
                }
            }
        }
    }

    private static long countFiles(File root, String requiredName) {
        if (root == null || !root.exists()) {
            return 0L;
        }
        if (root.isFile()) {
            return requiredName == null || requiredName.equals(root.getName()) ? 1L : 0L;
        }
        long count = 0L;
        File[] children = root.listFiles();
        if (children == null) {
            return 0L;
        }
        for (File child : children) {
            count += countFiles(child, requiredName);
        }
        return count;
    }

    private static void ensureDirectory(File directory) throws IOException {
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("Failed to create directory: " + directory.getAbsolutePath());
        }
    }

    private static void writeJson(Gson gson, File out, Object value) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(out);
             OutputStreamWriter writer = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
            gson.toJson(value, writer);
        }
    }

    private static String utcNow() {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(new Date());
    }

    private static final class RawExportManifest {
        String schemaVersion;
        String generatedAt;
        String repositoryName;
        String profile;
        String selection;
        String status;
        RawExportCounts counts;
        List<String> notes = new ArrayList<String>();
        List<FileRef> files = new ArrayList<FileRef>();
    }

    private static final class RawExportReport {
        String schemaVersion;
        String generatedAt;
        String profile;
        String selection;
        RawExportCounts counts;
        RawExportValidation validation = new RawExportValidation();
    }

    private static final class RawExportCounts {
        long items;
        long fluids;
        long recipes;
        long recipeTypes;
        long renderAssets;
        long itemModFiles;
        long recipeModFiles;
        long canonicalFiles;
    }

    private static final class RawExportValidation {
        String status;
        long missingTextureCount;
        long missingAnimationMetadataCount;
        long missingGroupOrOrderCount;
        List<String> failedStages;
    }

    private static final class FileRef {
        String logicalName;
        String path;
        String kind;
    }
}
