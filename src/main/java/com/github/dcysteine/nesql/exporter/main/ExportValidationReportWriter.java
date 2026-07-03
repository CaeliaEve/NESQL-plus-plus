package com.github.dcysteine.nesql.exporter.main;

import net.minecraft.util.EnumChatFormatting;

/** Writes a lightweight post-export validation summary without changing exported data contracts. */
final class ExportValidationReportWriter {
    private ExportValidationReportWriter() {}

    static void write(ExportContext exportContext) throws Exception {
        ExportValidationProbeContext context = ExportValidationProbeContext.from(exportContext);
        ExportValidationReport report = new ExportValidationReport();
        for (ExportValidationProbe probe : ExportValidationProbeCatalog.defaultProbes()) {
            probe.inspect(context, report);
        }
        report.validationProbes = ExportValidationProbeCatalog.descriptors();
        report.validationProbeCount = report.validationProbes.size();

        ExportValidationReportStore.ReportFiles reportFiles =
                ExportValidationReportStore.write(context.validationDir, report);

        Logger.chatMessage(
                EnumChatFormatting.GREEN
                        + "[NESQL] Export validation report written: "
                        + reportFiles.reportFile.getAbsolutePath());
        if (!report.warnings.isEmpty()) {
            Logger.chatMessage(
                    EnumChatFormatting.YELLOW
                            + "[NESQL] Validation warnings: "
                            + report.warnings.size()
                            + " (see report)");
        }
    }

}
