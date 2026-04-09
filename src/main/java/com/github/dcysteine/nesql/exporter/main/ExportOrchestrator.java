package com.github.dcysteine.nesql.exporter.main;

import java.io.File;

/**
 * Single entrypoint for NESQL++ export profiles.
 *
 * <p>Command wrappers delegate here, and profile behavior is supplied by
 * {@link ExportExecutionStrategy} so the orchestrator only owns the shared
 * execution skeleton.</p>
 */
public final class ExportOrchestrator {

    private ExportOrchestrator() {}

    public static void execute(ExportContext exportContext) throws Exception {
        ExportStageRunner.run(exportContext, ExportExecutionStrategy.forProfile(exportContext.profile));
    }
}
