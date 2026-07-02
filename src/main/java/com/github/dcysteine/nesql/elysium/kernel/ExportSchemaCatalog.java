package com.github.dcysteine.nesql.elysium.kernel;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Stable export schema catalog.
 *
 * <p>This is the export-kernel equivalent of a Linux ABI catalog: writers should reference these
 * constants instead of embedding schema strings at call sites. ControlFS schemas are stable
 * consumer-facing contracts; DebugFS schemas are diagnostic-only and may change between builds.</p>
 */
public final class ExportSchemaCatalog {
    public static final String RAW_EXPORT_ABI = "nesqlpp/raw-export/alpha1";

    public static final String CONTROL_ROOT = "nesqlpp/export-control-plane/v1";
    public static final String CONTROL_INDEX = CONTROL_ROOT + "/index";
    public static final String CONTROL_ABI = CONTROL_ROOT + "/abi";
    public static final String CONTROL_CAPABILITIES = CONTROL_ROOT + "/capabilities";
    public static final String CONTROL_MODULES = CONTROL_ROOT + "/modules";
    public static final String CONTROL_DRIVERS = CONTROL_ROOT + "/drivers";
    public static final String CONTROL_HEALTH = CONTROL_ROOT + "/health";
    public static final String CONTROL_VERSION = CONTROL_ROOT + "/version";

    public static final String DEBUG_KERNEL_TRACE = "nesqlpp/export-debug-kernel-trace/v1";
    public static final String DEBUG_STAGE_TIMING = "nesqlpp/export-debug-stage-timing/v1";
    public static final String DEBUG_STAGE_CHECKPOINT = "nesqlpp/export-debug-stage-checkpoint/v1";

    public static final String EXPORT_VALIDATION = "nesqlpp/export-validation/v1";
    public static final String EXPORT_ERROR = "nesqlpp/export-error/v1";
    public static final String EXPORT_MANIFEST = "nesqlpp/export-manifest/v1";
    public static final String STAGE_CHECKSUMS = "nesqlpp/stage-checksums/v1";

    private static final List<String> CONTROL_SCHEMAS = Collections.unmodifiableList(Arrays.asList(
            CONTROL_INDEX,
            CONTROL_ABI,
            CONTROL_CAPABILITIES,
            CONTROL_MODULES,
            CONTROL_DRIVERS,
            CONTROL_HEALTH,
            CONTROL_VERSION));

    private static final List<String> DEBUG_SCHEMAS = Collections.unmodifiableList(Arrays.asList(
            DEBUG_KERNEL_TRACE,
            DEBUG_STAGE_TIMING,
            DEBUG_STAGE_CHECKPOINT));

    private ExportSchemaCatalog() {}

    public static List<String> controlSchemas() {
        return CONTROL_SCHEMAS;
    }

    public static List<String> debugSchemas() {
        return DEBUG_SCHEMAS;
    }
}
