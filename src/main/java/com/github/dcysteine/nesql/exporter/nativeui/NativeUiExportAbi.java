package com.github.dcysteine.nesql.exporter.nativeui;

import com.github.dcysteine.nesql.elysium.kernel.ExportSchemaCatalog;

/** Stable raw-export Native UI ABI constants for scalable NEI surface replay. */
public final class NativeUiExportAbi {
    public static final String RAW_EXPORT_SCHEMA = ExportSchemaCatalog.RAW_EXPORT_ABI;
    public static final String UI_FAMILY_CENSUS_SCHEMA = RAW_EXPORT_SCHEMA + "/ui-family-census";
    public static final String UI_TEMPLATE_CATALOG_SCHEMA = RAW_EXPORT_SCHEMA + "/ui-template-catalog";
    public static final String NATIVE_UI_VALIDATION_SCHEMA = RAW_EXPORT_SCHEMA + "/native-ui-validation";

    public static final String UI_FAMILY_CENSUS_FILE = "validation/ui-family-census.json";
    public static final String UI_TEMPLATE_CATALOG_FILE = "validation/ui-template-catalog.json";
    public static final String NATIVE_UI_VALIDATION_FILE = "validation/native-ui-abi.json";
    public static final String NEI_HANDLERS_FILE = "facts/nei/handlers.jsonl.gz";
    public static final String NEI_HANDLER_LAYOUTS_FILE = "facts/nei/handler-layouts.jsonl.gz";
    public static final String COORDINATE_SPACE = "nei_pixels";
    public static final String SCALE_MODE = "uniform-scale";
    public static final String ANCHOR = "top-left";
    public static final int SLOT_SIZE = 18;
    public static final int SLOT_PITCH = 18;

    public static final String BACKGROUND_STATUS_SEMANTIC = "semantic";
    public static final String BACKGROUND_STATUS_MISSING = "missing";
    public static final String BACKGROUND_KIND_GT_MODULAR_UI = "gt-modular-ui";
    public static final String BACKGROUND_KIND_CANONICAL_NEI_TEMPLATE = "canonical-nei-template";
    public static final String BACKGROUND_KIND_UNKNOWN = "unknown";
    public static final String BACKGROUND_SCALING_NINE_SLICE = "nine-slice";
    public static final String INTERACTION_PAYLOAD_SCHEMA = "neonei/native-ui-interaction/v1";
    public static final String INTERACTION_KIND_NONE = "none";
    public static final String INTERACTION_KIND_ITEM_CLICK = "item-click";
    public static final String INTERACTION_TARGET_NONE = "none";
    public static final String INTERACTION_TARGET_ITEM = "item";

    public static final String STATUS_OK = "ok";
    public static final String STATUS_BLOCKED = "blocked";
    public static final String LAYOUT_COUNT_FIELD = "layoutCount";
    public static final String SLOT_COUNT_FIELD = "slotCount";
    public static final String RECT_COUNT_FIELD = "rectCount";
    public static final String PRIMITIVE_COUNT_FIELD = "primitiveCount";
    public static final String MISSING_SURFACE_COUNT_FIELD = "missingSurfaceCount";
    public static final String SLOT_BOUNDS_VIOLATION_COUNT_FIELD = "slotBoundsViolationCount";
    public static final String RECT_BOUNDS_VIOLATION_COUNT_FIELD = "rectBoundsViolationCount";
    public static final String PRIMITIVE_BOUNDS_VIOLATION_COUNT_FIELD = "primitiveBoundsViolationCount";
    public static final String BACKGROUND_BOUNDS_VIOLATION_COUNT_FIELD = "backgroundBoundsViolationCount";
    public static final String COORDINATE_CONTRACT_VIOLATION_COUNT_FIELD = "coordinateContractViolationCount";
    public static final String INTERACTION_CONTRACT_VIOLATION_COUNT_FIELD = "interactionContractViolationCount";
    public static final String[] REQUIRED_POSITIVE_COUNTERS = new String[] {
            LAYOUT_COUNT_FIELD,
            SLOT_COUNT_FIELD
    };
    public static final String[] ZERO_VIOLATION_COUNTERS = new String[] {
            MISSING_SURFACE_COUNT_FIELD,
            SLOT_BOUNDS_VIOLATION_COUNT_FIELD,
            RECT_BOUNDS_VIOLATION_COUNT_FIELD,
            PRIMITIVE_BOUNDS_VIOLATION_COUNT_FIELD,
            BACKGROUND_BOUNDS_VIOLATION_COUNT_FIELD,
            COORDINATE_CONTRACT_VIOLATION_COUNT_FIELD,
            INTERACTION_CONTRACT_VIOLATION_COUNT_FIELD
    };

    private NativeUiExportAbi() {}

    public static String schema(String root, String suffix) {
        return root + "/" + suffix;
    }
}
