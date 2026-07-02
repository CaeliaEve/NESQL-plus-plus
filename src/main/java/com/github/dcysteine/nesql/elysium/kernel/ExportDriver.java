package com.github.dcysteine.nesql.elysium.kernel;

import java.util.Collections;
import java.util.List;

public interface ExportDriver {
    String id();

    String busId();

    default List<String> capabilities() {
        return Collections.emptyList();
    }

    DriverProbeResult probe(ExportDevice device, ExportKernelContext context);

    default void bind(ExportDevice device, ExportKernelContext context) throws Exception {}
}
