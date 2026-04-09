package com.github.dcysteine.nesql.exporter.main;

import net.minecraft.util.EnumChatFormatting;

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
}
