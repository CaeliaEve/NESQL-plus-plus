package com.github.dcysteine.nesql.elysium.kernel;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Stable tracepoint names emitted by the NESQL++ export kernel. */
public final class ExportTracepoint {
    public static final String MODULE_INIT = "export.module.init";
    public static final String MODULE_EXIT = "export.module.exit";
    public static final String DRIVER_PROBE = "export.driver.probe";
    public static final String DRIVER_BIND = "export.driver.bind";
    public static final String STAGE_RUN = "export.stage.run";
    public static final String RESOURCE_RELEASE = "export.resource.release";

    private static final List<String> ALL = Collections.unmodifiableList(Arrays.asList(
            MODULE_INIT,
            MODULE_EXIT,
            DRIVER_PROBE,
            DRIVER_BIND,
            STAGE_RUN,
            RESOURCE_RELEASE));

    private ExportTracepoint() {}

    public static List<String> all() {
        return ALL;
    }
}
