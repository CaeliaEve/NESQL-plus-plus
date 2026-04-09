package com.github.dcysteine.nesql.exporter.local;

import com.github.dcysteine.nesql.exporter.canonical.CanonicalExportMapper;
import com.github.dcysteine.nesql.exporter.canonical.CanonicalFluid;
import com.github.dcysteine.nesql.exporter.canonical.CanonicalItem;
import com.github.dcysteine.nesql.exporter.canonical.CanonicalRecipe;
import com.github.dcysteine.nesql.exporter.canonical.CanonicalRepositoryModel;
import com.github.dcysteine.nesql.exporter.main.Logger;
import com.github.dcysteine.nesql.sql.base.fluid.Fluid;
import com.github.dcysteine.nesql.sql.base.recipe.Recipe;
import com.github.dcysteine.nesql.sql.gregtech.GregTechRecipe;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import jakarta.persistence.EntityManager;
import net.minecraft.util.EnumChatFormatting;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Writes a first-pass canonical snapshot for NESQL++.
 *
 * <p>This writer emits the authoritative NESQL++ repository model for downstream
 * consumers and refinement passes.</p>
 */
public class CanonicalRepositorySnapshotWriter {
    private static final String OUTPUT_DIRECTORY = "canonical";
    private static final String OUTPUT_FILE = "repository.json";

    private final EntityManager entityManager;
    private final File exportDirectory;
    private final String sourceProfileId;
    private final boolean includeRenderAssets;

    public CanonicalRepositorySnapshotWriter(EntityManager entityManager, File exportDirectory) {
        this(entityManager, exportDirectory, "unknown", true);
    }

    public CanonicalRepositorySnapshotWriter(EntityManager entityManager, File exportDirectory, String sourceProfileId) {
        this(entityManager, exportDirectory, sourceProfileId, true);
    }

    public CanonicalRepositorySnapshotWriter(
            EntityManager entityManager,
            File exportDirectory,
            String sourceProfileId,
            boolean includeRenderAssets) {
        this.entityManager = entityManager;
        this.exportDirectory = exportDirectory;
        this.sourceProfileId = sourceProfileId;
        this.includeRenderAssets = includeRenderAssets;
    }

    public void export() throws IOException {
        Logger.chatMessage(EnumChatFormatting.AQUA + "Exporting NESQL++ canonical snapshot...");

        CanonicalRepositoryModel model = new CanonicalRepositoryModel();
        model.sourceProfileId = sourceProfileId;

        @SuppressWarnings("unchecked")
        List<com.github.dcysteine.nesql.sql.base.item.Item> items =
                entityManager.createQuery("SELECT i FROM Item i", com.github.dcysteine.nesql.sql.base.item.Item.class)
                        .getResultList();
        for (com.github.dcysteine.nesql.sql.base.item.Item item : items) {
            CanonicalItem mapped = CanonicalExportMapper.mapItem(item);
            model.items.add(mapped);
        }

        @SuppressWarnings("unchecked")
        List<Fluid> fluids =
                entityManager.createQuery("SELECT f FROM Fluid f", Fluid.class)
                        .getResultList();
        for (Fluid fluid : fluids) {
            CanonicalFluid mapped = CanonicalExportMapper.mapFluid(fluid);
            model.fluids.add(mapped);
        }

        @SuppressWarnings("unchecked")
        List<GregTechRecipe> gtRecipes =
                entityManager.createQuery("SELECT gtr FROM com.github.dcysteine.nesql.sql.gregtech.GregTechRecipe gtr", GregTechRecipe.class)
                        .getResultList();
        Map<String, GregTechRecipe> gtByRecipeId = new HashMap<>();
        for (GregTechRecipe gtRecipe : gtRecipes) {
            if (gtRecipe.getRecipe() != null) {
                gtByRecipeId.put(gtRecipe.getRecipe().getId(), gtRecipe);
            }
        }

        @SuppressWarnings("unchecked")
        List<Recipe> recipes =
                entityManager.createQuery("SELECT DISTINCT r FROM Recipe r LEFT JOIN FETCH r.recipeType", Recipe.class)
                        .getResultList();
        for (Recipe recipe : recipes) {
            CanonicalRecipe mapped = CanonicalExportMapper.mapRecipe(recipe, gtByRecipeId.get(recipe.getId()));
            model.recipes.add(mapped);
        }

        if (includeRenderAssets) {
            model.renderAssets.addAll(new CanonicalRenderAssetCollector(entityManager, exportDirectory).collectAll());
        }

        File canonicalDir = new File(exportDirectory, OUTPUT_DIRECTORY);
        if (!canonicalDir.exists()) {
            canonicalDir.mkdirs();
        }

        File out = new File(canonicalDir, OUTPUT_FILE);
        Gson gson = new GsonBuilder()
                .setPrettyPrinting()
                .serializeNulls()
                .create();

        try (FileOutputStream fos = new FileOutputStream(out);
             OutputStreamWriter writer = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
            gson.toJson(model, writer);
        }

        Logger.chatMessage(EnumChatFormatting.GREEN + "NESQL++ canonical snapshot written:");
        Logger.chatMessage(EnumChatFormatting.YELLOW + "  " + out.getAbsolutePath());
    }
}
