package com.github.dcysteine.nesql.elysium.kernel;

public interface ExportModule {
    String id();

    ExportInitcallLevel level();

    default void init(ExportKernelContext context) throws Exception {}

    default void exit(ExportKernelContext context) throws Exception {}

    default ExportStageActionRegistrar<?, ?> stageActionRegistrar() {
        return null;
    }
}
