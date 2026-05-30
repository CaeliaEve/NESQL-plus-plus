package com.github.dcysteine.nesql.exporter.main;

import net.minecraft.util.EnumChatFormatting;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;

final class ExportDiagnosticsSupport {

    private ExportDiagnosticsSupport() {}

    static void announceStageStart(ExportContext exportContext, ExportStage stage, int index, int total) {
        String message =
                EnumChatFormatting.GRAY
                        + "[NESQL] Stage "
                        + index
                        + "/"
                        + total
                        + ": "
                        + stage.name();
        Logger.chatMessage(message);
        Logger.MOD.info(
                "Export stage {}/{} started: {} [profile={}, repository={}]",
                index,
                total,
                stage,
                exportContext.profile.profileId,
                exportContext.paths.repositoryName);
    }

    static File writeFailureReport(ExportContext exportContext, ExportStage stage, Throwable error) {
        File repositoryDirectory = exportContext.paths.repositoryDirectory;
        if (!repositoryDirectory.exists()) {
            repositoryDirectory.mkdirs();
        }

        File reportFile = new File(repositoryDirectory, "nesql-debug-report.txt");
        try (PrintWriter writer =
                new PrintWriter(
                        new OutputStreamWriter(
                                new FileOutputStream(reportFile, false), StandardCharsets.UTF_8))) {
            writer.println("NESQL Export Failure Report");
            writer.println("==========================");
            writer.println("Generated: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
            writer.println("Repository: " + exportContext.paths.repositoryDirectory.getAbsolutePath());
            writer.println("Repository Name: " + exportContext.paths.repositoryName);
            writer.println("Profile: " + exportContext.profile.profileId);
            writer.println("Stage: " + (stage == null ? "<unknown>" : stage.name()));
            writer.println();

            Throwable root = rootCause(error);
            writer.println("Exception Type: " + error.getClass().getName());
            writer.println("Exception Message: " + safeMessage(error));
            writer.println("Root Cause Type: " + root.getClass().getName());
            writer.println("Root Cause Message: " + safeMessage(root));
            writer.println();

            writer.println("Stack Trace:");
            writer.println("------------");
            error.printStackTrace(writer);
        } catch (Exception reportError) {
            Logger.MOD.error("Failed to write NESQL debug report", reportError);
        }
        writeFailureJsonl(exportContext, stage, error, reportFile);
        return reportFile;
    }

    static String summarizeThrowable(Throwable error) {
        Throwable root = rootCause(error);
        return root.getClass().getSimpleName() + ": " + safeMessage(root);
    }

    private static Throwable rootCause(Throwable error) {
        Throwable cursor = error;
        while (cursor.getCause() != null && cursor.getCause() != cursor) {
            cursor = cursor.getCause();
        }
        return cursor;
    }

    private static String safeMessage(Throwable error) {
        return error.getMessage() == null ? "<no message>" : error.getMessage();
    }

    private static void writeFailureJsonl(
            ExportContext exportContext,
            ExportStage stage,
            Throwable error,
            File reportFile) {
        File validationDirectory = new File(exportContext.paths.repositoryDirectory, "validation");
        if (!validationDirectory.exists() && !validationDirectory.mkdirs()) {
            Logger.MOD.warn("Failed to create NESQL validation directory: {}", validationDirectory.getAbsolutePath());
            return;
        }

        File errorsFile = new File(validationDirectory, "errors.jsonl");
        Throwable root = rootCause(error);
        JsonObject entry = new JsonObject();
        entry.addProperty("schemaVersion", "nesqlpp/export-error/v1");
        entry.addProperty("generatedAt", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ").format(new Date()));
        entry.addProperty("repository", exportContext.paths.repositoryName);
        entry.addProperty("profile", exportContext.profile.profileId);
        entry.addProperty("selection", exportContext.selection.describe());
        entry.addProperty("stage", stage == null ? "<unknown>" : stage.name());
        entry.addProperty("errorClass", error.getClass().getName());
        entry.addProperty("message", safeMessage(error));
        entry.addProperty("rootCauseClass", root.getClass().getName());
        entry.addProperty("rootCauseMessage", safeMessage(root));
        entry.addProperty(
                "debugReport",
                relativize(exportContext.paths.repositoryDirectory, reportFile));

        try (FileOutputStream fos = new FileOutputStream(errorsFile, true);
             OutputStreamWriter writer = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
            writer.write(new GsonBuilder().disableHtmlEscaping().create().toJson(entry));
            writer.write('\n');
        } catch (Exception writeError) {
            Logger.MOD.error("Failed to write NESQL validation/errors.jsonl", writeError);
        }
    }

    private static String relativize(File root, File file) {
        try {
            return root.toPath().toAbsolutePath().normalize()
                    .relativize(file.toPath().toAbsolutePath().normalize())
                    .toString()
                    .replace(File.separatorChar, '/');
        } catch (Exception ignored) {
            return file.getName();
        }
    }
}
