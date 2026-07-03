package com.github.dcysteine.nesql.exporter.local;

import com.github.dcysteine.nesql.exporter.canonical.CanonicalExportMapper;
import com.github.dcysteine.nesql.exporter.canonical.CanonicalFluid;
import com.github.dcysteine.nesql.exporter.canonical.CanonicalItem;
import com.github.dcysteine.nesql.exporter.canonical.CanonicalRecipe;
import com.github.dcysteine.nesql.exporter.main.Logger;
import com.github.dcysteine.nesql.sql.base.fluid.Fluid;
import com.github.dcysteine.nesql.sql.base.recipe.Recipe;
import com.github.dcysteine.nesql.sql.gregtech.GregTechRecipe;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

import java.io.File;
import java.io.IOException;

import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class RawExportRepositoryFactStreamer {
    private static final int ITEM_BATCH_SIZE = 4096;
    private static final int FLUID_BATCH_SIZE = 2048;
    private static final int RECIPE_BATCH_SIZE = 512;

    private final EntityManager entityManager;
    private final File rawDir;
    private final String schemaVersion;

    RawExportRepositoryFactStreamer(EntityManager entityManager, File rawDir, String schemaVersion) {
        this.entityManager = entityManager;
        this.rawDir = rawDir;
        this.schemaVersion = schemaVersion;
    }

    static String[] specialDomainIds() {
        String[][] specs = specialDomainSpecs();
        String[] ids = new String[specs.length];
        for (int index = 0; index < specs.length; index++) {
            ids[index] = specs[index][0];
        }
        return ids;
    }

    RawRepositoryFactStreamResult write() throws IOException {
        RawRepositoryFactStreamResult result = streamDatabaseRepositoryFacts();
        Logger.MOD.info(
                "Raw-export facts streamed directly from database: items={}, fluids={}, recipes={}",
                result.items,
                result.fluids,
                result.recipes);
        return result;
    }

    private RawRepositoryFactStreamResult streamDatabaseRepositoryFacts() throws IOException {
        createEmptyJsonl(new File(rawDir, "facts/items.jsonl.gz"));
        createEmptyJsonl(new File(rawDir, "facts/fluids.jsonl.gz"));

        RawRepositoryFactStreamResult result = new RawRepositoryFactStreamResult();
        Gson gson = new GsonBuilder().serializeNulls().create();
        Map<String, RecipeShardState> shards = new LinkedHashMap<String, RecipeShardState>();
        Set<String> usedShardFileNames = new LinkedHashSet<String>();
        Map<String, SpecialDomainStreamState> domains = createSpecialDomainStreamStates();
        JsonlWriter itemFacts = null;
        JsonlWriter fluidFacts = null;
        try {
            itemFacts = new JsonlWriter(new File(rawDir, "facts/items.jsonl.gz"), gson);
            fluidFacts = new JsonlWriter(new File(rawDir, "facts/fluids.jsonl.gz"), gson);

            result.items = streamDatabaseItems(itemFacts, gson);
            result.fluids = streamDatabaseFluids(fluidFacts, gson);
            result.recipes =
                    streamDatabaseRecipes(
                            shards,
                            usedShardFileNames,
                            domains,
                            gson);
        } finally {
            closeQuietly(itemFacts);
            closeQuietly(fluidFacts);
            for (RecipeShardState shard : shards.values()) {
                closeQuietly(shard.writer);
            }
            for (SpecialDomainStreamState domain : domains.values()) {
                closeQuietly(domain.payloadWriter);
            }
        }

        writeRecipeIndex(shards, result.recipes);
        writeSpecialIndexes(domains);
        return result;
    }

    private long streamDatabaseItems(JsonlWriter primary, Gson gson) throws IOException {
        long written = 0L;
        int offset = 0;
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
                JsonElement element = gson.toJsonTree(mapped, CanonicalItem.class);
                primary.write(element);
                written++;
            }
            offset += items.size();
            entityManager.clear();
        }
        return written;
    }

    private long streamDatabaseFluids(JsonlWriter primary, Gson gson) throws IOException {
        long written = 0L;
        int offset = 0;
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
                JsonElement element = gson.toJsonTree(mapped, CanonicalFluid.class);
                primary.write(element);
                written++;
            }
            offset += fluids.size();
            entityManager.clear();
        }
        return written;
    }

    private long streamDatabaseRecipes(
            Map<String, RecipeShardState> shards,
            Set<String> usedShardFileNames,
            Map<String, SpecialDomainStreamState> domains,
            Gson gson) throws IOException {
        long written = 0L;
        int offset = 0;
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
                JsonElement element = gson.toJsonTree(mapped, CanonicalRecipe.class);
                writeRecipeElement(element, shards, usedShardFileNames, domains, gson);
                written++;
            }
            offset += recipes.size();
            gtByRecipeId.clear();
            entityManager.clear();
        }
        return written;
    }

    private Map<String, GregTechRecipe> loadGregTechRecipeBatch(List<Recipe> recipes) {
        List<String> recipeIds = new ArrayList<String>(recipes.size());
        for (Recipe recipe : recipes) {
            recipeIds.add(recipe.getId());
        }
        if (recipeIds.isEmpty()) {
            return new LinkedHashMap<String, GregTechRecipe>();
        }
        TypedQuery<GregTechRecipe> query = entityManager.createQuery(
                "SELECT DISTINCT gtr FROM com.github.dcysteine.nesql.sql.gregtech.GregTechRecipe gtr "
                        + "LEFT JOIN FETCH gtr.recipe "
                        + "WHERE gtr.recipe.id IN :recipeIds",
                GregTechRecipe.class);
        List<GregTechRecipe> gtRecipes = query
                .setParameter("recipeIds", recipeIds)
                .getResultList();
        Map<String, GregTechRecipe> gtByRecipeId = new LinkedHashMap<String, GregTechRecipe>();
        for (GregTechRecipe gtRecipe : gtRecipes) {
            if (gtRecipe.getRecipe() != null) {
                gtByRecipeId.put(gtRecipe.getRecipe().getId(), gtRecipe);
            }
        }
        return gtByRecipeId;
    }

    private void writeRecipeElement(
            JsonElement element,
            Map<String, RecipeShardState> shards,
            Set<String> usedShardFileNames,
            Map<String, SpecialDomainStreamState> domains,
            Gson gson) throws IOException {
        String handlerId = inferRecipeHandlerId(element);
        RecipeShardState shard = shards.get(handlerId);
        if (shard == null) {
            String fileName = uniqueShardFileName(handlerId, usedShardFileNames);
            String path = "facts/recipes/by-handler/" + fileName;
            shard = new RecipeShardState(handlerId, path, new JsonlWriter(new File(rawDir, path), gson));
            shards.put(handlerId, shard);
        }
        shard.writer.write(element);
        shard.recipeCount++;

        if (element != null && element.isJsonObject()) {
            JsonObject recipe = element.getAsJsonObject();
            String descriptor = recipeDescriptor(element);
            for (SpecialDomainStreamState domain : domains.values()) {
                if (matchesAny(descriptor, domain.needles)) {
                    JsonObject payload = buildSpecialDomainPayload(domain.domainId, recipe, domain.payloadOrdinal++);
                    domain.payloadWriter.write(payload);
                    domain.accept(recipe);
                }
            }
        }
    }

    private void writeRecipeIndex(Map<String, RecipeShardState> shardsByHandler, long recipeCount)
            throws IOException {
        Gson gson = new GsonBuilder().setPrettyPrinting().serializeNulls().create();
        JsonArray shards = new JsonArray();
        for (RecipeShardState entry : shardsByHandler.values()) {
            JsonObject shard = new JsonObject();
            shard.addProperty("handlerId", entry.handlerId);
            shard.addProperty("path", entry.path);
            shard.addProperty("recipeCount", entry.recipeCount);
            shards.add(shard);
        }

        JsonObject index = new JsonObject();
        index.addProperty("schemaVersion", schemaVersion + "/recipe-index");
        index.addProperty("strategy", "by-handler");
        index.addProperty("recipeCount", recipeCount);
        index.addProperty("shardCount", shards.size());
        index.add("shards", shards);
        writeJson(gson, new File(rawDir, "facts/recipes/index.json"), index);
    }

    private void writeSpecialIndexes(Map<String, SpecialDomainStreamState> domains) throws IOException {
        Gson gson = new GsonBuilder().setPrettyPrinting().serializeNulls().create();
        JsonArray domainIndex = new JsonArray();
        for (SpecialDomainStreamState domain : domains.values()) {
            JsonObject summary = domain.summary();
            JsonObject index = new JsonObject();
            index.addProperty("schemaVersion", schemaVersion + "/special-domain");
            index.addProperty("domain", domain.domainId);
            index.addProperty("recipeCount", domain.recipeCount);
            index.addProperty("payloadCount", domain.payloadCount);
            index.addProperty("payloads", "special/" + domain.domainId + "/payloads.jsonl.gz");
            index.addProperty("summary", "special/" + domain.domainId + "/summary.json");
            index.add("stats", summary);
            File domainDir = new File(rawDir, "special/" + domain.domainId);
            writeJson(gson, new File(domainDir, "index.json"), index);
            writeJson(gson, new File(domainDir, "summary.json"), summary);

            JsonObject entry = new JsonObject();
            entry.addProperty("domain", domain.domainId);
            entry.addProperty("recipeCount", domain.recipeCount);
            entry.addProperty("payloadCount", domain.payloadCount);
            entry.addProperty("index", "special/" + domain.domainId + "/index.json");
            entry.addProperty("payloads", "special/" + domain.domainId + "/payloads.jsonl.gz");
            entry.addProperty("summary", "special/" + domain.domainId + "/summary.json");
            entry.add("stats", summary);
            domainIndex.add(entry);
        }

        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", schemaVersion + "/special-index");
        root.add("domains", domainIndex);
        writeJson(gson, new File(rawDir, "special/index.json"), root);
    }

    private Map<String, SpecialDomainStreamState> createSpecialDomainStreamStates() throws IOException {
        Gson gson = new GsonBuilder().serializeNulls().create();
        Map<String, SpecialDomainStreamState> states = new LinkedHashMap<String, SpecialDomainStreamState>();
        for (String[] spec : specialDomainSpecs()) {
            File domainDir = new File(rawDir, "special/" + spec[0]);
            RawExportSidecarFileOps.ensureDirectory(domainDir);
            states.put(spec[0], new SpecialDomainStreamState(
                    spec[0],
                    spec[1],
                    new JsonlWriter(new File(domainDir, "payloads.jsonl.gz"), gson)));
        }
        return states;
    }

    private static String[][] specialDomainSpecs() {
        return new String[][] {
                {"gregtech", "gregtech|gt_|gt |assembler|assembly line|chemical reactor|blast furnace|macerator|fluid solidifier|alloy smelter|research station"},
                {"thaumcraft", "thaumcraft|thaumic|arcane|infusion|crucible|aspect|research"},
                {"botania", "botania|mana pool|rune altar|runic altar|terra plate|pure daisy|elven trade|petal apothecary"},
                {"bloodmagic", "bloodmagic|blood magic|blood altar|alchemy array|binding ritual|blood orb|lp"},
                {"forestry", "forestry|bee|alveary|centrifuge|squeezer|carpenter"},
                {"eec", "extreme entity crusher|industrial slaughter|infernal drops|mobsinfo|kubatech|entity crusher"}
        };
    }

    private static JsonObject buildSpecialDomainPayload(String domainId, JsonObject recipe, int ordinal) {
        JsonObject payload = new JsonObject();
        payload.addProperty("domain", domainId);
        payload.addProperty("ordinal", ordinal);
        copyString(payload, "recipeId", recipe, "recipeId");
        copyString(payload, "family", recipe, "family");
        copyString(payload, "sourcePlugin", recipe, "sourcePlugin");
        copyString(payload, "sourceMod", recipe, "sourceMod");
        copyString(payload, "recipeType", recipe, "recipeType");
        copyString(payload, "displayName", recipe, "displayName");
        copyString(payload, "handlerId", recipe, "metadata.handlerId");
        copyString(payload, "handlerName", recipe, "metadata.handlerName");
        copyString(payload, "handlerClass", recipe, "metadata.handlerClass");
        copyString(payload, "machineId", recipe, "machine.machineId");
        copyString(payload, "machineName", recipe, "machine.displayName");
        copyString(payload, "layoutClass", recipe, "layout.layoutClass");

        JsonObject slotStats = buildSpecialSlotStats(recipe);
        if (slotStats.entrySet().size() > 0) {
            payload.add("slotStats", slotStats);
        }
        JsonObject primaryRefs = buildSpecialPrimaryRefs(recipe);
        if (primaryRefs.entrySet().size() > 0) {
            payload.add("primaryRefs", primaryRefs);
        }
        JsonObject facts = new JsonObject();
        addDomainFacts(facts, domainId, recipe);
        if (facts.entrySet().size() > 0) {
            payload.add("domainFacts", facts);
        }
        return payload;
    }

    private static void increment(Map<String, Integer> counts, String value) {
        if (value == null || value.trim().length() == 0) {
            return;
        }
        String normalized = value.trim();
        Integer current = counts.get(normalized);
        counts.put(normalized, current == null ? 1 : current + 1);
    }

    private static JsonArray topStrings(Map<String, Integer> counts, int limit) {
        List<Map.Entry<String, Integer>> entries = new ArrayList<Map.Entry<String, Integer>>(counts.entrySet());
        Collections.sort(entries, new Comparator<Map.Entry<String, Integer>>() {
            @Override
            public int compare(Map.Entry<String, Integer> left, Map.Entry<String, Integer> right) {
                int byCount = right.getValue().compareTo(left.getValue());
                return byCount != 0 ? byCount : left.getKey().compareTo(right.getKey());
            }
        });
        JsonArray out = new JsonArray();
        int count = 0;
        for (Map.Entry<String, Integer> entry : entries) {
            if (count >= limit) {
                break;
            }
            JsonObject object = new JsonObject();
            object.addProperty("value", entry.getKey());
            object.addProperty("count", entry.getValue());
            out.add(object);
            count++;
        }
        return out;
    }

    private static JsonArray sampleStrings(Set<String> samples) {
        JsonArray out = new JsonArray();
        for (String sample : samples) {
            out.add(new com.google.gson.JsonPrimitive(sample));
        }
        return out;
    }

    private static void closeQuietly(JsonlWriter writer) {
        if (writer == null) {
            return;
        }
        try {
            writer.close();
        } catch (IOException ignored) {
            // best-effort cleanup after export failure
        }
    }

    private static final class JsonlWriter implements java.io.Closeable {
        private final Gson gson;
        private final Writer writer;

        JsonlWriter(File out, Gson gson) throws IOException {
            this.gson = gson;
            this.writer = RawExportSidecarFileOps.createUtf8JsonlWriter(out);
        }

        void write(JsonElement element) throws IOException {
            gson.toJson(element, writer);
            writer.write('\n');
        }

        @Override
        public void close() throws IOException {
            writer.close();
        }
    }

    private static final class RepositoryStreamResult {
        long items;
        long fluids;
        long recipes;
    }

    private static final class RecipeShardState {
        final String handlerId;
        final String path;
        final JsonlWriter writer;
        long recipeCount;

        RecipeShardState(String handlerId, String path, JsonlWriter writer) {
            this.handlerId = handlerId;
            this.path = path;
            this.writer = writer;
        }
    }

    private static final class SpecialDomainStreamState {
        final String domainId;
        final String needles;
        final JsonlWriter payloadWriter;
        final Map<String, Integer> families = new LinkedHashMap<String, Integer>();
        final Map<String, Integer> recipeTypes = new LinkedHashMap<String, Integer>();
        final Map<String, Integer> sourcePlugins = new LinkedHashMap<String, Integer>();
        final Map<String, Integer> machineIds = new LinkedHashMap<String, Integer>();
        final Map<String, Integer> machineNames = new LinkedHashMap<String, Integer>();
        final Set<String> sampleRecipeIds = new LinkedHashSet<String>();
        long recipeCount;
        long payloadCount;
        int payloadOrdinal;

        SpecialDomainStreamState(String domainId, String needles, JsonlWriter payloadWriter) {
            this.domainId = domainId;
            this.needles = needles;
            this.payloadWriter = payloadWriter;
        }

        void accept(JsonObject recipe) {
            recipeCount++;
            payloadCount++;
            increment(families, stringAt(recipe, "family"));
            increment(recipeTypes, stringAt(recipe, "recipeType"));
            increment(sourcePlugins, stringAt(recipe, "sourcePlugin"));
            increment(machineIds, stringAt(recipe, "machine.machineId"));
            increment(machineNames, stringAt(recipe, "machine.displayName"));
            String recipeId = stringAt(recipe, "recipeId");
            if (recipeId != null && recipeId.trim().length() > 0 && sampleRecipeIds.size() < 50) {
                sampleRecipeIds.add(recipeId.trim());
            }
        }

        JsonObject summary() {
            JsonObject summary = new JsonObject();
            summary.addProperty("domain", domainId);
            summary.addProperty("recipeCount", recipeCount);
            summary.add("families", topStrings(families, 50));
            summary.add("recipeTypes", topStrings(recipeTypes, 50));
            summary.add("sourcePlugins", topStrings(sourcePlugins, 50));
            summary.add("machineIds", topStrings(machineIds, 80));
            summary.add("machineNames", topStrings(machineNames, 80));
            summary.add("sampleRecipeIds", sampleStrings(sampleRecipeIds));
            return summary;
        }
    }
    private static JsonObject buildSpecialSlotStats(JsonObject recipe) {
        JsonObject stats = new JsonObject();
        addCount(stats, "itemInputCount", recipe, "itemInputs");
        addCount(stats, "itemOutputCount", recipe, "itemOutputs");
        addCount(stats, "fluidInputCount", recipe, "fluidInputs");
        addCount(stats, "fluidOutputCount", recipe, "fluidOutputs");
        addCount(stats, "layoutItemSlotCount", recipe, "layout.itemSlots");
        addCount(stats, "layoutFluidSlotCount", recipe, "layout.fluidSlots");
        return stats;
    }

    private static JsonObject buildSpecialPrimaryRefs(JsonObject recipe) {
        JsonObject refs = new JsonObject();
        addCollectedStrings(refs, "itemInputIds", recipe, "itemInputs", "variants", "itemId", 24);
        addCollectedStrings(refs, "itemOutputIds", recipe, "itemOutputs", null, "itemId", 24);
        addCollectedStrings(refs, "fluidInputIds", recipe, "fluidInputs", "variants", "fluidId", 24);
        addCollectedStrings(refs, "fluidOutputIds", recipe, "fluidOutputs", null, "fluidId", 24);
        return refs;
    }

    private static void addDomainFacts(JsonObject facts, String domainId, JsonObject recipe) {
        if ("gregtech".equals(domainId)) {
            copyFirstNumber(facts, "duration", recipe, "metadata.duration", "metadata.ticks");
            copyFirstNumber(facts, "voltage", recipe, "metadata.voltage");
            copyFirstNumber(facts, "amperage", recipe, "metadata.amperage");
            copyFirstNumber(facts, "totalEU", recipe, "metadata.totalEU");
            copyString(facts, "voltageTier", recipe, "metadata.voltageTier");
            copyElement(facts, "requiresCleanroom", recipe, "metadata.requiresCleanroom");
            copyElement(facts, "requiresLowGravity", recipe, "metadata.requiresLowGravity");
            copyElement(facts, "specialItems", recipe, "metadata.specialItems");
        } else if ("thaumcraft".equals(domainId)) {
            copyElement(facts, "aspects", recipe, "metadata.aspects");
            copyElement(facts, "aspects", recipe, "layout.bindings.aspects");
            copyElement(facts, "aspectItems", recipe, "metadata.aspectItems");
            copyElement(facts, "aspectItems", recipe, "layout.bindings.aspectItems");
            copyElement(facts, "research", recipe, "metadata.research");
            copyElement(facts, "research", recipe, "layout.bindings.research");
            copyElement(facts, "instability", recipe, "metadata.instability");
            copyElement(facts, "instability", recipe, "layout.bindings.instability");
            copyElement(facts, "centralItemId", recipe, "metadata.centralItemId");
            copyElement(facts, "centralItemId", recipe, "layout.bindings.centralItemId");
            copyElement(facts, "centerInputSlotIndex", recipe, "metadata.centerInputSlotIndex");
            copyElement(facts, "centerInputSlotIndex", recipe, "layout.bindings.centerInputSlotIndex");
            copyElement(facts, "componentSlotOrder", recipe, "metadata.componentSlotOrder");
            copyElement(facts, "componentSlotOrder", recipe, "layout.bindings.componentSlotOrder");
        } else if ("botania".equals(domainId)) {
            copyFirstNumber(facts, "manaCost", recipe, "metadata.manaCost", "layout.bindings.manaCost", "metadata.mana");
            copyFirstNumber(facts, "ticks", recipe, "metadata.ticks", "layout.bindings.ticks", "metadata.duration");
            copyElement(facts, "catalyst", recipe, "metadata.catalyst");
            copyElement(facts, "catalystItemId", recipe, "metadata.catalystItemId");
            copyString(facts, "recipeKind", recipe, "metadata.recipeKind");
            copyString(facts, "recipeKind", recipe, "metadata.specialRecipeType");
        } else if ("bloodmagic".equals(domainId)) {
            copyFirstNumber(facts, "bloodCost", recipe, "metadata.bloodCost", "layout.bindings.bloodCost", "metadata.lpCost", "layout.bindings.lpCost", "metadata.requiredLP", "metadata.lp");
            copyFirstNumber(facts, "lpCost", recipe, "metadata.lpCost", "metadata.requiredLP", "metadata.bloodCost", "layout.bindings.lpCost");
            copyFirstNumber(facts, "requiredLP", recipe, "metadata.requiredLP", "metadata.lpCost", "metadata.bloodCost", "layout.bindings.lpCost", "layout.bindings.bloodCost");
            copyFirstNumber(facts, "tier", recipe, "metadata.tier", "metadata.altarTier", "layout.bindings.tier");
            copyFirstNumber(facts, "altarTier", recipe, "metadata.altarTier", "metadata.tier", "layout.bindings.altarTier", "layout.bindings.tier");
            copyFirstNumber(facts, "consumptionRate", recipe, "metadata.consumptionRate", "layout.bindings.consumptionRate");
            copyFirstNumber(facts, "drainRate", recipe, "metadata.drainRate", "layout.bindings.drainRate");
            copyFirstNumber(facts, "tartaricCost", recipe, "metadata.tartaricCost", "layout.bindings.tartaricCost");
            copyElement(facts, "orb", recipe, "metadata.orb");
            copyElement(facts, "ritual", recipe, "metadata.ritual");
            copyElement(facts, "isWeakActivation", recipe, "metadata.isWeakActivation");
            copyElement(facts, "isWeakActivation", recipe, "layout.bindings.isWeakActivation");
        } else if ("forestry".equals(domainId)) {
            copyElement(facts, "beeSpecies", recipe, "metadata.beeSpecies");
            copyElement(facts, "species", recipe, "metadata.species");
            copyElement(facts, "species", recipe, "metadata.beeSpecies");
            copyElement(facts, "allele", recipe, "metadata.allele");
            copyElement(facts, "alleles", recipe, "metadata.alleles");
            copyElement(facts, "temperature", recipe, "metadata.temperature");
            copyElement(facts, "humidity", recipe, "metadata.humidity");
            copyElement(facts, "chance", recipe, "metadata.chance");
            copyElement(facts, "mutations", recipe, "metadata.mutations");
        } else if ("eec".equals(domainId)) {
            copyElement(facts, "entity", recipe, "metadata.entity");
            copyElement(facts, "entityId", recipe, "metadata.entityId");
            copyElement(facts, "entityId", recipe, "metadata.mobName");
            copyElement(facts, "entityId", recipe, "metadata.entityName");
            copyElement(facts, "entityName", recipe, "metadata.entityName");
            copyElement(facts, "mobName", recipe, "metadata.mobName");
            copyElement(facts, "entityHealth", recipe, "metadata.entityHealth");
            copyElement(facts, "entityHealth", recipe, "metadata.maxHealth");
            copyElement(facts, "maxHealth", recipe, "metadata.maxHealth");
            copyElement(facts, "drops", recipe, "metadata.drops");
            copyElement(facts, "fluidDrops", recipe, "metadata.fluidDrops");
            copyFirstNumber(facts, "normalOutputsCount", recipe, "metadata.normalOutputsCount");
            copyFirstNumber(facts, "rareOutputsCount", recipe, "metadata.rareOutputsCount");
            copyFirstNumber(facts, "additionalOutputsCount", recipe, "metadata.additionalOutputsCount");
            copyFirstNumber(facts, "infernalOutputsCount", recipe, "metadata.infernalOutputsCount");
            copyFirstNumber(facts, "outputCount", recipe, "metadata.outputCount");
            copyFirstNumber(facts, "eliteChance", recipe, "metadata.eliteChance");
            copyFirstNumber(facts, "ultraChance", recipe, "metadata.ultraChance");
            copyFirstNumber(facts, "infernoChance", recipe, "metadata.infernoChance");
            copyElement(facts, "modelRef", recipe, "metadata.modelRef");
            copyElement(facts, "previewImage", recipe, "metadata.previewImage");
        }
    }

    private static void copyString(JsonObject target, String to, JsonObject source, String dottedPath) {
        String value = stringAt(source, dottedPath);
        if (value != null && value.trim().length() > 0) {
            target.addProperty(to, value.trim());
        }
    }

    private static void copyElement(JsonObject target, String to, JsonObject source, String dottedPath) {
        JsonElement value = elementAt(source, dottedPath);
        if (value != null && !value.isJsonNull()) {
            target.add(to, cloneJson(value));
        }
    }

    private static void copyFirstNumber(JsonObject target, String to, JsonObject source, String... dottedPaths) {
        for (String dottedPath : dottedPaths) {
            JsonElement value = elementAt(source, dottedPath);
            if (value == null || value.isJsonNull() || !value.isJsonPrimitive()) {
                continue;
            }
            try {
                target.add(to, cloneJson(value));
                return;
            } catch (Exception ignored) {
                // Try the next candidate.
            }
        }
    }

    private static JsonElement cloneJson(JsonElement value) {
        return value == null ? null : new JsonParser().parse(value.toString());
    }

    private static void addCount(JsonObject target, String to, JsonObject source, String dottedPath) {
        JsonElement value = elementAt(source, dottedPath);
        if (value != null && value.isJsonArray()) {
            target.addProperty(to, value.getAsJsonArray().size());
        }
    }

    private static void addCollectedStrings(
            JsonObject target,
            String to,
            JsonObject source,
            String arrayPath,
            String nestedArrayName,
            String valueKey,
            int limit) {
        JsonElement value = elementAt(source, arrayPath);
        if (value == null || !value.isJsonArray()) {
            return;
        }
        JsonArray out = new JsonArray();
        Set<String> seen = new LinkedHashSet<String>();
        for (JsonElement row : value.getAsJsonArray()) {
            if (out.size() >= limit || row == null || !row.isJsonObject()) {
                continue;
            }
            if (nestedArrayName == null) {
                addStringIfPresent(out, seen, row.getAsJsonObject().get(valueKey));
            } else {
                JsonElement nested = row.getAsJsonObject().get(nestedArrayName);
                if (nested == null || !nested.isJsonArray()) {
                    continue;
                }
                for (JsonElement nestedRow : nested.getAsJsonArray()) {
                    if (out.size() >= limit || nestedRow == null || !nestedRow.isJsonObject()) {
                        continue;
                    }
                    addStringIfPresent(out, seen, nestedRow.getAsJsonObject().get(valueKey));
                }
            }
        }
        if (out.size() > 0) {
            target.add(to, out);
        }
    }

    private static void addStringIfPresent(JsonArray target, Set<String> seen, JsonElement value) {
        if (value == null || value.isJsonNull() || !value.isJsonPrimitive()) {
            return;
        }
        try {
            String text = value.getAsString();
            if (text != null && text.trim().length() > 0 && !seen.contains(text.trim())) {
                seen.add(text.trim());
                target.add(new com.google.gson.JsonPrimitive(text.trim()));
            }
        } catch (Exception ignored) {
            // Ignore non-string primitives.
        }
    }

    private static JsonElement elementAt(JsonObject object, String dottedPath) {
        if (dottedPath == null || dottedPath.length() == 0) {
            return object;
        }
        JsonElement current = object;
        for (String part : dottedPath.split("\\.")) {
            if (current == null || !current.isJsonObject()) {
                return null;
            }
            current = current.getAsJsonObject().get(part);
        }
        return current;
    }

    private static String recipeDescriptor(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return "";
        }
        JsonObject recipe = element.getAsJsonObject();
        StringBuilder builder = new StringBuilder();
        appendDescriptor(builder, stringAt(recipe, "metadata.handlerId"));
        appendDescriptor(builder, stringAt(recipe, "metadata.handlerName"));
        appendDescriptor(builder, stringAt(recipe, "additionalData.handlerId"));
        appendDescriptor(builder, stringAt(recipe, "additionalData.handlerName"));
        appendDescriptor(builder, stringAt(recipe, "machine.machineId"));
        appendDescriptor(builder, stringAt(recipe, "machine.displayName"));
        appendDescriptor(builder, stringAt(recipe, "family"));
        appendDescriptor(builder, stringAt(recipe, "sourcePlugin"));
        appendDescriptor(builder, stringAt(recipe, "recipeType"));
        appendDescriptor(builder, stringAt(recipe, "displayName"));
        appendDescriptor(builder, stringAt(recipe, "category"));
        return builder.toString().toLowerCase(java.util.Locale.ROOT);
    }

    private static void appendDescriptor(StringBuilder builder, String value) {
        if (value != null && value.trim().length() > 0) {
            builder.append(' ').append(value.trim());
        }
    }

    private static boolean matchesAny(String descriptor, String pipeSeparatedNeedles) {
        if (descriptor == null || descriptor.length() == 0) {
            return false;
        }
        for (String needle : pipeSeparatedNeedles.split("\\|")) {
            String normalized = needle.trim().toLowerCase(java.util.Locale.ROOT);
            if (normalized.length() > 0 && descriptor.contains(normalized)) {
                return true;
            }
        }
        return false;
    }
    private static String inferRecipeHandlerId(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return "unknown";
        }
        JsonObject recipe = element.getAsJsonObject();
        String value = firstNonBlank(
                stringAt(recipe, "metadata.handlerId"),
                stringAt(recipe, "metadata.handlerName"),
                stringAt(recipe, "additionalData.handlerId"),
                stringAt(recipe, "additionalData.handlerName"),
                stringAt(recipe, "machine.machineId"),
                stringAt(recipe, "machine.displayName"),
                stringAt(recipe, "family"),
                stringAt(recipe, "sourcePlugin"),
                stringAt(recipe, "recipeType"),
                stringAt(recipe, "displayName"));
        return value == null ? "unknown" : value;
    }

    private static String stringAt(JsonObject object, String dottedPath) {
        JsonElement current = object;
        for (String part : dottedPath.split("\\.")) {
            if (current == null || !current.isJsonObject()) {
                return null;
            }
            current = current.getAsJsonObject().get(part);
        }
        if (current == null || current.isJsonNull()) {
            return null;
        }
        try {
            return current.isJsonPrimitive() ? current.getAsString() : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && value.trim().length() > 0) {
                return value.trim();
            }
        }
        return null;
    }

    private static String uniqueShardFileName(String handlerId, Set<String> usedFileNames) {
        String base = safeShardFileName(handlerId);
        String candidate = base + ".jsonl.gz";
        int suffix = 2;
        while (usedFileNames.contains(candidate)) {
            candidate = base + "-" + suffix + ".jsonl.gz";
            suffix++;
        }
        usedFileNames.add(candidate);
        return candidate;
    }

    private static String safeShardFileName(String handlerId) {
        String normalized = handlerId == null ? "unknown" : handlerId.trim().toLowerCase(java.util.Locale.ROOT);
        normalized = normalized.replaceAll("[^a-z0-9._-]+", "-").replaceAll("^-+|-+$", "");
        return normalized.length() == 0 ? "unknown" : normalized;
    }

    private static void writeJson(Gson gson, File out, Object value) throws IOException {
        RawExportSidecarFileOps.writeJson(gson, out, value);
    }

    private static void createEmptyJsonl(File out) throws IOException {
        RawExportEmptyJsonlWriter.write(out);
    }

}
