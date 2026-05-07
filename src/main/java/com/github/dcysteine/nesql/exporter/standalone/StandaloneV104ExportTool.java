package com.github.dcysteine.nesql.exporter.standalone;

import com.github.dcysteine.nesql.exporter.local.ModBasedRecipeExporter;
import com.github.dcysteine.nesql.exporter.local.ModBasedItemExporter;
import org.hibernate.jpa.HibernatePersistenceProvider;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * NESQL++ Standalone v1.04 export tool (without Minecraft runtime)
 *
 * Usage:
 * java -cp NESQL-Exporter-0.5.0.jar;NESQL-Exporter-0.5.0-deps.jar;
 *      com.github.dcysteine.nesql.exporter.standalone.StandaloneV104ExportTool
 */
public class StandaloneV104ExportTool {

    public static void main(String[] args) {
        System.out.println("============================================================");
        System.out.println("NESQL++ Standalone v1.04 Export Tool");
        System.out.println("============================================================");
        System.out.println();

        // Configuration
        String minecraftDir = "C:\\Users\\CaeliaEve\\AppData\\Roaming\\PrismLauncher\\instances\\GT_New_Horizons_2.8.4_Java_8\\.minecraft";
        File nesqlRepoDir = new File(new File(minecraftDir), "nesql/nesql-repository");
        File dbPropsFile = new File(nesqlRepoDir, "nesql-db.properties");
        File exportDir = new File(new File(minecraftDir), "nesql/nesql-repository/v1.04-export");

        // Check database exists (HSQLDB creates multiple files, check .properties)
        if (!dbPropsFile.exists()) {
            System.err.println("ERROR: Database not found: " + dbPropsFile);
            System.err.println();
            System.err.println("Please run the game and execute /nesql-data command first");
            System.exit(1);
        }

        System.out.println("Database: " + nesqlRepoDir);
        System.out.println("Export to: " + exportDir);
        System.out.println();

        // Create export directory
        exportDir.mkdirs();

        EntityManagerFactory emf = null;
        EntityManager em = null;

        try {
            // Initialize database connection
            System.out.println("Connecting to database...");

            Map<String, Object> properties = new HashMap<>();
            properties.put("hibernate.connection.url",
                    "jdbc:hsqldb:file:" + new File(nesqlRepoDir, "nesql-db").getAbsolutePath() + ";shutdown=true");
            properties.put("hibernate.hbm2ddl.auto", "update");
            properties.put("hibernate.show_sql", "false");

            emf = new HibernatePersistenceProvider()
                    .createEntityManagerFactory("NESQL", properties);
            em = emf.createEntityManager();

            System.out.println("Database connected successfully");
            System.out.println();

            // ========== Step 1: Export Items ==========
            System.out.println("=== Step 1/2: Exporting Items ===");
            System.out.println("Structure: items/{modId}/items.json");

            long itemStartTime = System.currentTimeMillis();

            ModBasedItemExporter itemExporter = new ModBasedItemExporter(em, exportDir);
            itemExporter.exportItems();

            long itemEndTime = System.currentTimeMillis();
            System.out.println("Items exported in " + ((itemEndTime - itemStartTime) / 1000.0) + " seconds");
            System.out.println();

            // ========== Step 2: Export Recipes ==========
            System.out.println("=== Step 2/2: Exporting Recipes ===");
            System.out.println("Structure: recipes/crafting/{modId}/recipes.json.gz");

            long recipeStartTime = System.currentTimeMillis();

            ModBasedRecipeExporter recipeExporter = new ModBasedRecipeExporter(em, exportDir);
            recipeExporter.exportRecipes();

            long recipeEndTime = System.currentTimeMillis();
            System.out.println("Recipes exported in " + ((recipeEndTime - recipeStartTime) / 1000.0 / 60.0) + " minutes");
            System.out.println();

            // ========== Done ==========
            System.out.println("============================================================");
            System.out.println("Export Complete!");
            System.out.println("============================================================");
            System.out.println();
            System.out.println("Export directory:");
            System.out.println("  " + exportDir);
            System.out.println();
            System.out.println("File structure:");
            System.out.println("  " + exportDir + "/");
            System.out.println("    items/");
            System.out.println("      <modId>/");
            System.out.println("        items.json");
            System.out.println("    recipes/");
            System.out.println("      crafting/");
            System.out.println("        <modId>/");
            System.out.println("          recipes.json.gz");

        } catch (Exception e) {
            System.err.println("Export failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        } finally {
            if (em != null) {
                em.close();
            }
            if (emf != null) {
                emf.close();
            }
        }
    }
}
