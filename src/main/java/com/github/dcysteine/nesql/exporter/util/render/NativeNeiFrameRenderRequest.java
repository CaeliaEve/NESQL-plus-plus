package com.github.dcysteine.nesql.exporter.util.render;

import codechicken.nei.recipe.IRecipeHandler;

import java.io.File;

/** One strict in-game NEI recipe-frame capture request. */
public final class NativeNeiFrameRenderRequest {
    private final String recipeId;
    private final String assetRef;
    private final File outputFile;
    private final IRecipeHandler handler;
    private final int recipeIndex;
    private final int width;
    private final int height;

    public NativeNeiFrameRenderRequest(
            String recipeId,
            String assetRef,
            File outputFile,
            IRecipeHandler handler,
            int recipeIndex,
            int width,
            int height) {
        this.recipeId = recipeId;
        this.assetRef = assetRef;
        this.outputFile = outputFile;
        this.handler = handler;
        this.recipeIndex = recipeIndex;
        this.width = Math.max(1, width);
        this.height = Math.max(1, height);
    }

    public String getRecipeId() {
        return recipeId;
    }

    public String getAssetRef() {
        return assetRef;
    }

    public File getOutputFile() {
        return outputFile;
    }

    public IRecipeHandler getHandler() {
        return handler;
    }

    public int getRecipeIndex() {
        return recipeIndex;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }
}
