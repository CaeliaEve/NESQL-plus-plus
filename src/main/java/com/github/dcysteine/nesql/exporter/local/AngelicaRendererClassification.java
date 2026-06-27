package com.github.dcysteine.nesql.exporter.local;

/** Native-renderer classification used by Angelica item renderer fact emission. */
final class AngelicaRendererClassification {
    final String kind;
    final boolean usesShader;
    final boolean requiresFramebufferCapture;
    final boolean shaderExportEligible;
    final String shaderFamily;
    final String shaderTimeSource;
    final String notes;

    AngelicaRendererClassification(
            String kind,
            boolean usesShader,
            boolean requiresFramebufferCapture,
            boolean shaderExportEligible,
            String shaderFamily,
            String shaderTimeSource,
            String notes) {
        this.kind = kind;
        this.usesShader = usesShader;
        this.requiresFramebufferCapture = requiresFramebufferCapture;
        this.shaderExportEligible = shaderExportEligible;
        this.shaderFamily = shaderFamily;
        this.shaderTimeSource = shaderTimeSource;
        this.notes = notes;
    }
}
