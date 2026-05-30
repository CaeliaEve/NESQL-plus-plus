package com.github.dcysteine.nesql.exporter.local;

import com.github.dcysteine.nesql.exporter.canonical.CanonicalExportMapper;
import com.github.dcysteine.nesql.exporter.canonical.CanonicalFluid;
import com.github.dcysteine.nesql.exporter.canonical.CanonicalItem;
import com.github.dcysteine.nesql.exporter.canonical.CanonicalRecipe;
import com.github.dcysteine.nesql.exporter.canonical.CanonicalRenderAsset;
import com.github.dcysteine.nesql.exporter.main.Logger;
import com.github.dcysteine.nesql.sql.base.fluid.Fluid;
import com.github.dcysteine.nesql.sql.base.recipe.Recipe;
import com.github.dcysteine.nesql.sql.gregtech.GregTechRecipe;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonWriter;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import net.minecraft.util.EnumChatFormatting;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Writes a first-pass canonical snapshot for NESQL++.
 *
 * <p>This writer emits the authoritative NESQL++ repository model for downstream
 * consumers and refinement passes. The snapshot is streamed in bounded JPA pages
 * because GTNH-scale exports contain hundreds of thousands of recipes; building a
 * single in-memory repository object leaves too little heap for Minecraft's own
 * world-save threads.</p>
 */
public class CanonicalRepositorySnapshotWriter {
    private static final String OUTPUT_DIRECTORY = "canonical";
    private static final String OUTPUT_FILE = "repository.json";
    private static final int ITEM_BATCH_SIZE = 4096;
    private static final int FLUID_BATCH_SIZE = 2048;
    private static final int RECIPE_BATCH_SIZE = 512;

    private final EntityManager entityManager;
    private final File exportDirectory;
    private final String sourceProfileId;
    private final boolean includeRenderAssets;
    private final Gson gson;

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
        this.gson = new GsonBuilder()
                .serializeNulls()
                .create();
    }

    public List<CanonicalRenderAsset> export() throws IOException {
        Logger.chatMessage(EnumChatFormatting.AQUA + "Exporting NESQL++ canonical snapshot...");

        File canonicalDir = new File(exportDirectory, OUTPUT_DIRECTORY);
        if (!canonicalDir.exists() && !canonicalDir.mkdirs()) {
            throw new IOException("Could not create canonical directory: " + canonicalDir.getAbsolutePath());
        }

        File out = new File(canonicalDir, OUTPUT_FILE);
        try (FileOutputStream fos = new FileOutputStream(out);
             OutputStreamWriter outputWriter = new OutputStreamWriter(fos, StandardCharsets.UTF_8);
             JsonWriter writer = new JsonWriter(outputWriter)) {
            writer.setSerializeNulls(true);
            writer.beginObject();
            writer.name("schemaVersion").value("nesqlpp/v1-draft");
            writer.name("sourceProfileId").value(sourceProfileId);

            writer.name("items");
            writeItems(writer);

            writer.name("fluids");
            writeFluids(writer);

            writer.name("recipes");
            writeRecipes(writer);

            // Render assets are intentionally not embedded in repository.json.
            // Dedicated manifest/atlas stages consume the returned collection below.
            writer.name("renderAssets");
            writer.beginArray();
            writer.endArray();
            writer.endObject();
        }

        entityManager.clear();
        List<CanonicalRenderAsset> renderAssets = new ArrayList<>();
        if (includeRenderAssets) {
            renderAssets.addAll(new CanonicalRenderAssetCollector(entityManager, exportDirectory).collectAll());
            Logger.MOD.info(
                    "Collected {} render assets for downstream manifests; skipping inline repository embedding.",
                    renderAssets.size());
        }

        Logger.chatMessage(EnumChatFormatting.GREEN + "NESQL++ canonical snapshot written:");
        Logger.chatMessage(EnumChatFormatting.YELLOW + "  " + out.getAbsolutePath());
        return renderAssets;
    }

    private void writeItems(JsonWriter writer) throws IOException {
        writer.beginArray();
        int offset = 0;
        int written = 0;
        while (true) {
            TypedQuery<com.github.dcysteine.nesql.sql.base.item.Item> query = entityManager.createQuery(
                    "SELECT i FROM Item i ORDER BY i.id",
                    com.github.dcysteine.nesql.sql.base.item.Item.class);
            List<com.github.dcysteine.nesql.sql.base.item.Item> items = query
                    .setFirstResult(offset)
                    .setMaxResults(ITEM_BATCH_SIZE)
                    .getResultList();
            if (items.isEmpty()) {
                break;
            }
            for (com.github.dcysteine.nesql.sql.base.item.Item item : items) {
                CanonicalItem mapped = CanonicalExportMapper.mapItem(item);
                gson.toJson(mapped, CanonicalItem.class, writer);
                written++;
            }
            offset += items.size();
            entityManager.clear();
            Logger.MOD.info("Canonical snapshot streamed {} items", written);
        }
        writer.endArray();
    }

    private void writeFluids(JsonWriter writer) throws IOException {
        writer.beginArray();
        int offset = 0;
        int written = 0;
        while (true) {
            TypedQuery<Fluid> query = entityManager.createQuery(
                    "SELECT f FROM Fluid f ORDER BY f.id",
                    Fluid.class);
            List<Fluid> fluids = query
                    .setFirstResult(offset)
                    .setMaxResults(FLUID_BATCH_SIZE)
                    .getResultList();
            if (fluids.isEmpty()) {
                break;
            }
            for (Fluid fluid : fluids) {
                CanonicalFluid mapped = CanonicalExportMapper.mapFluid(fluid);
                gson.toJson(mapped, CanonicalFluid.class, writer);
                written++;
            }
            offset += fluids.size();
            entityManager.clear();
            Logger.MOD.info("Canonical snapshot streamed {} fluids", written);
        }
        writer.endArray();
    }

    private void writeRecipes(JsonWriter writer) throws IOException {
        writer.beginArray();
        int offset = 0;
        int written = 0;
        while (true) {
            TypedQuery<Recipe> query = entityManager.createQuery(
                    "SELECT r FROM Recipe r LEFT JOIN FETCH r.recipeType ORDER BY r.id",
                    Recipe.class);
            List<Recipe> recipes = query
                    .setFirstResult(offset)
                    .setMaxResults(RECIPE_BATCH_SIZE)
                    .getResultList();
            if (recipes.isEmpty()) {
                break;
            }

            Map<String, GregTechRecipe> gtByRecipeId = loadGregTechRecipeBatch(recipes);
            for (Recipe recipe : recipes) {
                CanonicalRecipe mapped = CanonicalExportMapper.mapRecipe(recipe, gtByRecipeId.get(recipe.getId()));
                gson.toJson(mapped, CanonicalRecipe.class, writer);
                written++;
            }
            offset += recipes.size();
            gtByRecipeId.clear();
            entityManager.clear();
            Logger.MOD.info("Canonical snapshot streamed {} recipes", written);
        }
        writer.endArray();
    }

    private Map<String, GregTechRecipe> loadGregTechRecipeBatch(List<Recipe> recipes) {
        List<String> recipeIds = new ArrayList<>(recipes.size());
        for (Recipe recipe : recipes) {
            recipeIds.add(recipe.getId());
        }
        if (recipeIds.isEmpty()) {
            return new HashMap<>();
        }

        TypedQuery<GregTechRecipe> query = entityManager.createQuery(
                "SELECT DISTINCT gtr FROM com.github.dcysteine.nesql.sql.gregtech.GregTechRecipe gtr "
                        + "LEFT JOIN FETCH gtr.recipe "
                        + "WHERE gtr.recipe.id IN :recipeIds",
                GregTechRecipe.class);
        List<GregTechRecipe> gtRecipes = query
                .setParameter("recipeIds", recipeIds)
                .getResultList();
        Map<String, GregTechRecipe> gtByRecipeId = new HashMap<>();
        for (GregTechRecipe gtRecipe : gtRecipes) {
            if (gtRecipe.getRecipe() != null) {
                gtByRecipeId.put(gtRecipe.getRecipe().getId(), gtRecipe);
            }
        }
        return gtByRecipeId;
    }
}

