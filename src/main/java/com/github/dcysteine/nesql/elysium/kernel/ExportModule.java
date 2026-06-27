package com.github.dcysteine.nesql.elysium.kernel;

import java.util.Collections;
import java.util.List;

public interface ExportModule {
    String id();

    ExportInitcallLevel level();

    default List<String> capabilities() {
        return Collections.emptyList();
    }

    default List<String> stageIds() {
        return Collections.emptyList();
    }

    default void init(ExportKernelContext context) throws Exception {}

    default void exit(ExportKernelContext context) throws Exception {}

    default ExportStageActionRegistrar<?, ?> stageActionRegistrar() {
        return null;
    }
}
