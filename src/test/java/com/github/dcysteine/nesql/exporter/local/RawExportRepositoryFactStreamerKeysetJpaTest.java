package com.github.dcysteine.nesql.exporter.local;

import com.github.dcysteine.nesql.sql.Identifiable;
import com.github.dcysteine.nesql.sql.base.fluid.Fluid;
import com.github.dcysteine.nesql.sql.base.item.Item;
import com.github.dcysteine.nesql.sql.base.recipe.Dimension;
import com.github.dcysteine.nesql.sql.base.recipe.Recipe;
import com.github.dcysteine.nesql.sql.base.recipe.RecipeType;
import org.hibernate.jpa.HibernatePersistenceProvider;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** Real HSQLDB/JPA regression coverage for the repository fact streamer's keyset queries. */
public final class RawExportRepositoryFactStreamerKeysetJpaTest {
    private static final int TEST_BATCH_SIZE = 2;

    private RawExportRepositoryFactStreamerKeysetJpaTest() {}

    public static void main(String[] args) throws Exception {
        File rawDir = Files.createTempDirectory("nesql-keyset-jpa-").toFile();
        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put("jakarta.persistence.jdbc.url", "jdbc:hsqldb:mem:keyset-pagination;shutdown=true");
        properties.put("hibernate.hbm2ddl.auto", "create-drop");
        properties.put("hibernate.show_sql", "false");

        EntityManagerFactory factory = new HibernatePersistenceProvider()
                .createEntityManagerFactory("NESQL", properties);
        EntityManager entityManager = factory.createEntityManager();
        try {
            RawExportRepositoryFactStreamer streamer =
                    new RawExportRepositoryFactStreamer(entityManager, rawDir, "test");

            assertEmptyPages(streamer);
            insertFixtures(entityManager);

            assertPagination(
                    "items",
                    entityManager.createQuery("SELECT i FROM Item i ORDER BY i.id", Item.class).getResultList(),
                    new BatchLoader<Item>() {
                        @Override
                        public List<Item> load(String lastId) {
                            return streamer.loadItemBatch(lastId, TEST_BATCH_SIZE);
                        }
                    });
            assertPagination(
                    "fluids",
                    entityManager.createQuery("SELECT f FROM Fluid f ORDER BY f.id", Fluid.class).getResultList(),
                    new BatchLoader<Fluid>() {
                        @Override
                        public List<Fluid> load(String lastId) {
                            return streamer.loadFluidBatch(lastId, TEST_BATCH_SIZE);
                        }
                    });
            assertPagination(
                    "recipes",
                    entityManager.createQuery(
                            "SELECT r FROM Recipe r LEFT JOIN FETCH r.recipeType ORDER BY r.id", Recipe.class)
                            .getResultList(),
                    new BatchLoader<Recipe>() {
                        @Override
                        public List<Recipe> load(String lastId) {
                            return streamer.loadRecipeBatch(lastId, TEST_BATCH_SIZE);
                        }
                    });
        } finally {
            entityManager.close();
            factory.close();
            deleteRecursively(rawDir);
        }
    }

    private static void assertEmptyPages(RawExportRepositoryFactStreamer streamer) {
        assertEquals("empty item page", 0, streamer.loadItemBatch(null, TEST_BATCH_SIZE).size());
        assertEquals("empty fluid page", 0, streamer.loadFluidBatch(null, TEST_BATCH_SIZE).size());
        assertEquals("empty recipe page", 0, streamer.loadRecipeBatch(null, TEST_BATCH_SIZE).size());
    }

    private static void insertFixtures(EntityManager entityManager) {
        List<String> ids = Arrays.asList("z-last", "Alpha", "alpha", "中间", "beta");
        entityManager.getTransaction().begin();
        for (int index : Arrays.asList(3, 0, 4, 1, 2)) {
            String id = ids.get(index);
            entityManager.persist(item(id, index));
            entityManager.persist(fluid(id, index));
        }
        RecipeType recipeType = new RecipeType(
                "fixture-type", "fixture", "fixture", null, "", true,
                new Dimension(0, 0), new Dimension(0, 0),
                new Dimension(0, 0), new Dimension(0, 0));
        entityManager.persist(recipeType);
        for (int index : Arrays.asList(3, 0, 4, 1, 2)) {
            entityManager.persist(new Recipe(
                    ids.get(index), recipeType,
                    Collections.emptyMap(), Collections.emptyMap(),
                    Collections.emptyMap(), Collections.emptyMap()));
        }
        entityManager.getTransaction().commit();
        entityManager.clear();
    }

    private static Item item(String id, int index) {
        return new Item(
                id, "images/" + index + ".png", "fixture", "item-" + index,
                "item." + index, "Item " + index, index, 0, "", "", 64, 0,
                Collections.<String, Integer>emptyMap());
    }

    private static Fluid fluid(String id, int index) {
        return new Fluid(
                id, "images/fluid-" + index + ".png", "fixture", "fluid-" + index,
                "fluid." + index, "Fluid " + index, index, "", 0, 1000, 300, 1000, false);
    }

    private static <T extends Identifiable<String>> void assertPagination(
            String label, List<T> expectedRows, BatchLoader<T> loader) {
        List<String> expectedIds = ids(expectedRows);
        List<String> actualIds = new ArrayList<String>();
        List<Integer> pageSizes = new ArrayList<Integer>();
        String lastId = null;
        while (true) {
            List<T> page = loader.load(lastId);
            pageSizes.add(page.size());
            if (page.isEmpty()) {
                break;
            }
            actualIds.addAll(ids(page));
            lastId = page.get(page.size() - 1).getId();
        }

        assertEquals(label + " ordered IDs", expectedIds, actualIds);
        assertEquals(label + " page sizes", Arrays.asList(2, 2, 1, 0), pageSizes);
        assertEquals(label + " unique count", actualIds.size(), new LinkedHashSet<String>(actualIds).size());
        assertTrue(label + " case-sensitive fixture", actualIds.contains("Alpha") && actualIds.contains("alpha"));
        assertTrue(label + " non-ASCII fixture", actualIds.contains("中间"));
    }

    private static <T extends Identifiable<String>> List<String> ids(List<T> rows) {
        List<String> ids = new ArrayList<String>(rows.size());
        for (T row : rows) {
            ids.add(row.getId());
        }
        return ids;
    }

    private static void assertTrue(String label, boolean condition) {
        if (!condition) {
            throw new AssertionError(label);
        }
    }

    private static void assertEquals(String label, Object expected, Object actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError(label + ": expected=" + expected + ", actual=" + actual);
        }
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        if (!file.delete()) {
            file.deleteOnExit();
        }
    }

    private interface BatchLoader<T> {
        List<T> load(String lastId);
    }
}
