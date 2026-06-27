package com.github.dcysteine.nesql.exporter.local;

/** Native-renderer classification used by Angelica item renderer fact emission. */
final class AngelicaRendererClassification {
    final String kind;
    final boolean usesShader;
    final boolean requiresFramebufferCapture;
    final String notes;

    AngelicaRendererClassification(String kind, boolean usesShader, boolean requiresFramebufferCapture, String notes) {
        this.kind = kind;
        this.usesShader = usesShader;
        this.requiresFramebufferCapture = requiresFramebufferCapture;
        this.notes = notes;
    }
}
