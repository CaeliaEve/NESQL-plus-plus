package com.github.dcysteine.nesql.elysium.kernel;

public interface ExportStageActionRegistrar<A, C> {
    void register(A actions, C context);
}
