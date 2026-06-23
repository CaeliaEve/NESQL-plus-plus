package com.github.dcysteine.nesql.exporter.plugin.nei.metadata;

/**
 * Metadata exported from NEI's handler registry table.
 *
 * <p>This is intentionally read-only support data. It is not wired into the recipe export path yet; NESQL++
 * can consume it later to improve handler-specific export and layout decisions.</p>
 */
public class NeiHandlerMetadataEntry {
    private String handler;
    private String modName;
    private String itemName;
    private String nbtInfo;
    private String modId;
    private boolean modRequired;
    private String excludedModId;
    private String yShift;
    private String handlerHeight;
    private String handlerWidth;
    private String maxRecipesPerPage;
    private String imageResource;
    private String imageX;
    private String imageY;
    private String imageWidth;
    private String imageHeight;
    private String itemNotes;

    public String getHandler() {
        return handler;
    }

    public String getModName() {
        return modName;
    }

    public String getItemName() {
        return itemName;
    }

    public String getNbtInfo() {
        return nbtInfo;
    }

    public String getModId() {
        return modId;
    }

    public boolean isModRequired() {
        return modRequired;
    }

    public String getExcludedModId() {
        return excludedModId;
    }

    public String getYShift() {
        return yShift;
    }

    public String getHandlerHeight() {
        return handlerHeight;
    }

    public String getHandlerWidth() {
        return handlerWidth;
    }

    public String getMaxRecipesPerPage() {
        return maxRecipesPerPage;
    }

    public String getImageResource() {
        return imageResource;
    }

    public String getImageX() {
        return imageX;
    }

    public String getImageY() {
        return imageY;
    }

    public String getImageWidth() {
        return imageWidth;
    }

    public String getImageHeight() {
        return imageHeight;
    }

    public String getItemNotes() {
        return itemNotes;
    }

    public Integer getYShiftInt() {
        return parseInt(yShift);
    }

    public Integer getHandlerHeightInt() {
        return parseInt(handlerHeight);
    }

    public Integer getHandlerWidthInt() {
        return parseInt(handlerWidth);
    }

    public Integer getMaxRecipesPerPageInt() {
        return parseInt(maxRecipesPerPage);
    }

    public Integer getImageXInt() {
        return parseInt(imageX);
    }

    public Integer getImageYInt() {
        return parseInt(imageY);
    }

    public Integer getImageWidthInt() {
        return parseInt(imageWidth);
    }

    public Integer getImageHeightInt() {
        return parseInt(imageHeight);
    }

    public boolean hasItemIcon() {
        return itemName != null && !itemName.trim().isEmpty();
    }

    private static Integer parseInt(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
