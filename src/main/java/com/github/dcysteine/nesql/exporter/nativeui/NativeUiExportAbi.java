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
    public static final String UI_BACKGROUNDS_DIRECTORY = "assets/ui-backgrounds";
    public static final String GT_NEI_BACKGROUND_ASSET_REF =
            UI_BACKGROUNDS_DIRECTORY + "/gregtech/nei_single_recipe.png";
    public static final String GT_NEI_BACKGROUND_RESOURCE =
            "gregtech:textures/gui/background/nei_single_recipe.png";

    public static final String COORDINATE_SPACE = "nei_pixels";
    public static final String SCALE_MODE = "uniform-scale";
    public static final String ANCHOR = "top-left";
    public static final int SLOT_SIZE = 18;
    public static final int SLOT_PITCH = 18;

    public static final String BACKGROUND_STATUS_CAPTURED = "captured";
    public static final String BACKGROUND_STATUS_MISSING = "missing";
    public static final String BACKGROUND_KIND_TEXTURE_REGION = "texture-region";
    public static final String BACKGROUND_KIND_GT_MODULAR_UI = "gt-modular-ui";
    public static final String BACKGROUND_KIND_UNKNOWN = "unknown";
    public static final String BACKGROUND_SCALING_NINE_SLICE = "nine-slice";
    public static final String INTERACTION_PAYLOAD_SCHEMA = "neonei/native-ui-interaction/v1";
    public static final String INTERACTION_KIND_NONE = "none";
    public static final String INTERACTION_KIND_ITEM_CLICK = "item-click";
    public static final String INTERACTION_TARGET_NONE = "none";
    public static final String INTERACTION_TARGET_ITEM = "item";

    private NativeUiExportAbi() {}

    public static String schema(String root, String suffix) {
        return root + "/" + suffix;
    }
}
