package com.github.dcysteine.nesql.exporter.main;

import java.util.List;

/** Bridges render asset residency and animation validation into the probe catalog. */
final class ExportValidationRenderAssetValidationProbe implements ExportValidationProbe {
    public String id() {
        return "validation.render-assets";
    }

    public List<String> capabilities() {
        return ExportValidationProbe.capabilityList(
                "validation.render.assets",
                "validation.render.animation");
    }

    public void inspect(
            ExportValidationProbeContext context,
            ExportValidationReport report) {
        ExportValidationRenderAssetProbe.inspect(context.repositoryDirectory, context.rawDir, report);
    }
}
