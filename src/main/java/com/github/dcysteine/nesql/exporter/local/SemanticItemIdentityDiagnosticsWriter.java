package com.github.dcysteine.nesql.exporter.local;

import com.github.dcysteine.nesql.exporter.semantic.SemanticItemIdentity;
import com.github.dcysteine.nesql.exporter.semantic.SemanticItemIdentityMapper;
import com.github.dcysteine.nesql.sql.base.item.Item;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

import java.io.File;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Diagnostic-only semantic item identity audit.
 *
 * <p>This does not change exported item IDs yet. It gives the raw-export pipeline a stable,
 * evidence-based view of the NBT families that should become publicItemId / variantId /
 * payloadHash in the next phase.</p>
 */
public final class SemanticItemIdentityDiagnosticsWriter {
    private static final String SCHEMA = "nesqlpp/semantic-item-identity/audit-alpha1";
    private static final int ITEM_BATCH_SIZE = 4096;
    private static final int SAMPLE_LIMIT = 30;

    private final EntityManager entityManager;
    private final File rawExportDirectory;

    public SemanticItemIdentityDiagnosticsWriter(EntityManager entityManager, File rawExportDirectory) {
        this.entityManager = entityManager;
        this.rawExportDirectory = rawExportDirectory;
    }

    public SemanticAuditSummary write() throws IOException {
        File outDir = new File(rawExportDirectory, "validation/semantic");
        ensureDirectory(outDir);

        AuditState state = scanItems();
        Gson gson = new GsonBuilder().setPrettyPrinting().serializeNulls().create();

        writeJson(gson, new File(outDir, "parametric-family-audit.json"), buildFamilyAudit(state));
        writeJson(gson, new File(outDir, "nbt-key-distribution.json"), buildNbtKeyDistribution(state));
        writeJson(gson, new File(outDir, "identity-normalization-report.json"), buildNormalizationReport(state));
        SemanticStreamCounts streamCounts = writeSemanticStreams();

        SemanticAuditSummary summary = new SemanticAuditSummary();
        summary.totalItems = state.totalItems;
        summary.taggedItems = state.taggedItems;
        summary.classifiedTaggedItems = state.classifiedTaggedItems;
        summary.unclassifiedTaggedItems = state.unclassifiedTaggedItems;
        summary.estimatedPublicItemsAfterNormalization = estimatePublicItemsAfterNormalization(state);
        summary.familyCount = state.families.size();
        summary.semanticItems = streamCounts.semanticItems;
        summary.variants = streamCounts.variants;
        summary.payloads = streamCounts.payloads;
        summary.identityMapRows = streamCounts.identityMapRows;
        return summary;
    }

    public static SemanticAuditSummary writeFromRawItems(File rawExportDirectory) throws IOException {
        File outDir = new File(rawExportDirectory, "validation/semantic");
        ensureDirectory(outDir);

        File rawItemsFile = new File(rawExportDirectory, "facts/items.jsonl.gz");
        if (!rawItemsFile.exists()) {
            throw new IOException("Missing raw item fact stream: " + rawItemsFile.getAbsolutePath());
        }

        AuditState state = scanRawItems(rawItemsFile);
        if (state.totalItems <= 0L) {
            throw new IOException("Refusing to overwrite semantic streams: raw item fact stream has zero rows.");
        }

        Gson gson = new GsonBuilder().setPrettyPrinting().serializeNulls().create();
        writeJson(gson, new File(outDir, "parametric-family-audit.json"), buildFamilyAudit(state));
        writeJson(gson, new File(outDir, "nbt-key-distribution.json"), buildNbtKeyDistribution(state));
        writeJson(gson, new File(outDir, "identity-normalization-report.json"), buildNormalizationReport(state));
        SemanticStreamCounts streamCounts = writeSemanticStreamsFromRaw(rawExportDirectory, rawItemsFile);

        SemanticAuditSummary summary = new SemanticAuditSummary();
        summary.totalItems = state.totalItems;
        summary.taggedItems = state.taggedItems;
        summary.classifiedTaggedItems = state.classifiedTaggedItems;
        summary.unclassifiedTaggedItems = state.unclassifiedTaggedItems;
        summary.estimatedPublicItemsAfterNormalization = estimatePublicItemsAfterNormalization(state);
        summary.familyCount = state.families.size();
        summary.semanticItems = streamCounts.semanticItems;
        summary.variants = streamCounts.variants;
        summary.payloads = streamCounts.payloads;
        summary.identityMapRows = streamCounts.identityMapRows;
        return summary;
    }

    private static SemanticStreamCounts writeSemanticStreamsFromRaw(
            File rawExportDirectory,
            File rawItemsFile) throws IOException {
        File itemDir = new File(rawExportDirectory, "facts/items");
        ensureDirectory(itemDir);
        Gson jsonlGson = new GsonBuilder().serializeNulls().create();

        SemanticStreamCounts counts = new SemanticStreamCounts();
        Set<String> publicItemIds = new LinkedHashSet<String>();
        Set<String> variantIds = new LinkedHashSet<String>();
        Set<String> payloadHashes = new LinkedHashSet<String>();
        Set<String> legacyItemIds = new LinkedHashSet<String>();

        JsonlWriter semanticItems = null;
        JsonlWriter variants = null;
        JsonlWriter payloads = null;
        JsonlWriter identityMap = null;
        try {
            semanticItems = new JsonlWriter(new File(itemDir, "semantic-items.jsonl.gz"), jsonlGson);
            variants = new JsonlWriter(new File(itemDir, "variants.jsonl.gz"), jsonlGson);
            payloads = new JsonlWriter(new File(itemDir, "payloads.jsonl.gz"), jsonlGson);
            identityMap = new JsonlWriter(new File(itemDir, "identity-map.jsonl.gz"), jsonlGson);

            com.google.gson.JsonParser parser = new com.google.gson.JsonParser();
            try (BufferedReader reader = openGzipUtf8(rawItemsFile)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.trim().length() == 0) {
                        continue;
                    }
                    Item item = rawItemFromJson(parser.parse(line).getAsJsonObject());
                    SemanticItemIdentity identity = SemanticItemIdentityMapper.map(item);
                    validateMappedIdentity(item, identity, legacyItemIds);
                    if (publicItemIds.add(identity.publicItemId)) {
                        semanticItems.write(toSemanticItemRow(item, identity));
                        counts.semanticItems++;
                    }
                    if (identity.variantId != null && variantIds.add(identity.variantId)) {
                        variants.write(toVariantRow(item, identity));
                        counts.variants++;
                    }
                    if (identity.payloadHash != null && payloadHashes.add(identity.payloadHash)) {
                        payloads.write(toPayloadRow(item, identity));
                        counts.payloads++;
                    }
                    identityMap.write(toIdentityMapRow(item, identity));
                    counts.identityMapRows++;
                }
            }
        } finally {
            closeQuietly(semanticItems);
            closeQuietly(variants);
            closeQuietly(payloads);
            closeQuietly(identityMap);
        }
        return counts;
    }

    private SemanticStreamCounts writeSemanticStreams() throws IOException {
        File itemDir = new File(rawExportDirectory, "facts/items");
        ensureDirectory(itemDir);
        Gson jsonlGson = new GsonBuilder().serializeNulls().create();

        SemanticStreamCounts counts = new SemanticStreamCounts();
        Set<String> publicItemIds = new LinkedHashSet<String>();
        Set<String> variantIds = new LinkedHashSet<String>();
        Set<String> payloadHashes = new LinkedHashSet<String>();
        Set<String> legacyItemIds = new LinkedHashSet<String>();

        JsonlWriter semanticItems = null;
        JsonlWriter variants = null;
        JsonlWriter payloads = null;
        JsonlWriter identityMap = null;
        try {
            semanticItems = new JsonlWriter(new File(itemDir, "semantic-items.jsonl.gz"), jsonlGson);
            variants = new JsonlWriter(new File(itemDir, "variants.jsonl.gz"), jsonlGson);
            payloads = new JsonlWriter(new File(itemDir, "payloads.jsonl.gz"), jsonlGson);
            identityMap = new JsonlWriter(new File(itemDir, "identity-map.jsonl.gz"), jsonlGson);

            int offset = 0;
            while (true) {
                TypedQuery<Item> query = entityManager.createQuery(
                        "SELECT i FROM Item i ORDER BY i.id",
                        Item.class);
                List<Item> items = query
                        .setFirstResult(offset)
                        .setMaxResults(ITEM_BATCH_SIZE)
                        .getResultList();
                if (items.isEmpty()) {
                    break;
                }
                for (Item item : items) {
                    SemanticItemIdentity identity = SemanticItemIdentityMapper.map(item);
                    validateMappedIdentity(item, identity, legacyItemIds);
                    if (publicItemIds.add(identity.publicItemId)) {
                        semanticItems.write(toSemanticItemRow(item, identity));
                        counts.semanticItems++;
                    }
                    if (identity.variantId != null && variantIds.add(identity.variantId)) {
                        variants.write(toVariantRow(item, identity));
                        counts.variants++;
                    }
                    if (identity.payloadHash != null && payloadHashes.add(identity.payloadHash)) {
                        payloads.write(toPayloadRow(item, identity));
                        counts.payloads++;
                    }
                    identityMap.write(toIdentityMapRow(item, identity));
                    counts.identityMapRows++;
                }
                offset += items.size();
                entityManager.clear();
            }
        } finally {
            closeQuietly(semanticItems);
            closeQuietly(variants);
            closeQuietly(payloads);
            closeQuietly(identityMap);
        }
        return counts;
    }

    private static void validateMappedIdentity(
            Item item,
            SemanticItemIdentity identity,
            Set<String> legacyItemIds) {
        String legacyItemId = safe(item.getId());
        if (legacyItemId.trim().length() == 0) {
            throw new IllegalStateException("Semantic identity validation failed: empty legacy item id.");
        }
        if (!legacyItemIds.add(legacyItemId)) {
            throw new IllegalStateException(
                    "Semantic identity validation failed: duplicate legacy item id " + legacyItemId);
        }
        if (identity == null || safe(identity.publicItemId).trim().length() == 0) {
            throw new IllegalStateException(
                    "Semantic identity validation failed: missing publicItemId for " + legacyItemId);
        }
        boolean hasPayload = safe(identity.payloadHash).trim().length() > 0;
        boolean hasVariant = safe(identity.variantId).trim().length() > 0;
        if (hasPayload != hasVariant) {
            throw new IllegalStateException(
                    "Semantic identity validation failed: payload/variant mismatch for " + legacyItemId);
        }
        if (safe(identity.family).trim().length() == 0 || safe(identity.classification).trim().length() == 0) {
            throw new IllegalStateException(
                    "Semantic identity validation failed: missing family/classification for " + legacyItemId);
        }
    }

    private static JsonObject toSemanticItemRow(Item item, SemanticItemIdentity identity) {
        JsonObject row = new JsonObject();
        row.addProperty("schemaVersion", SCHEMA + "/semantic-item");
        row.addProperty("publicItemId", identity.publicItemId);
        row.addProperty("family", identity.family);
        row.addProperty("classification", identity.classification);
        row.addProperty("modId", item.getModId());
        row.addProperty("internalName", item.getInternalName());
        row.addProperty("localizedName", item.getLocalizedName());
        row.addProperty("unlocalizedName", item.getUnlocalizedName());
        row.addProperty("damage", item.getItemDamage());
        row.addProperty("representativeLegacyItemId", item.getId());
        row.addProperty("renderAssetRef", "nesqlpp:item/" + item.getId());
        row.addProperty("facetSummary", identity.facetSummary);
        row.addProperty("sortKey", identity.sortKey);
        if (identity.facets != null) {
            row.add("facets", identity.facets);
        }
        return row;
    }

    private static JsonObject toVariantRow(Item item, SemanticItemIdentity identity) {
        JsonObject row = new JsonObject();
        row.addProperty("schemaVersion", SCHEMA + "/variant");
        row.addProperty("variantId", identity.variantId);
        row.addProperty("publicItemId", identity.publicItemId);
        row.addProperty("family", identity.family);
        row.addProperty("legacyItemId", item.getId());
        row.addProperty("payloadHash", identity.payloadHash);
        row.addProperty("variantKey", variantKey(item, safe(item.getNbt())));
        row.addProperty("localizedName", item.getLocalizedName());
        row.addProperty("renderAssetRef", "nesqlpp:item/" + item.getId());
        row.addProperty("variantLabel", identity.variantLabel);
        row.addProperty("facetSummary", identity.facetSummary);
        row.addProperty("sortKey", identity.sortKey);
        if (identity.facets != null) {
            row.add("facets", identity.facets);
        }
        return row;
    }

    private static JsonObject toPayloadRow(Item item, SemanticItemIdentity identity) {
        JsonObject row = new JsonObject();
        row.addProperty("schemaVersion", SCHEMA + "/payload");
        row.addProperty("payloadHash", identity.payloadHash);
        row.addProperty("encoding", "minecraft-nbt-toString");
        row.addProperty("legacyItemId", item.getId());
        row.addProperty("nbt", item.getNbt());
        return row;
    }

    private static JsonObject toIdentityMapRow(Item item, SemanticItemIdentity identity) {
        JsonObject row = new JsonObject();
        row.addProperty("schemaVersion", SCHEMA + "/identity-map");
        row.addProperty("legacyItemId", item.getId());
        row.addProperty("publicItemId", identity.publicItemId);
        row.addProperty("variantId", identity.variantId);
        row.addProperty("payloadHash", identity.payloadHash);
        row.addProperty("family", identity.family);
        row.addProperty("classification", identity.classification);
        row.addProperty("facetSummary", identity.facetSummary);
        row.addProperty("sortKey", identity.sortKey);
        return row;
    }

    private AuditState scanItems() {
        AuditState state = new AuditState();
        int offset = 0;
        while (true) {
            TypedQuery<Item> query = entityManager.createQuery(
                    "SELECT i FROM Item i ORDER BY i.id",
                    Item.class);
            List<Item> items = query
                    .setFirstResult(offset)
                    .setMaxResults(ITEM_BATCH_SIZE)
                    .getResultList();
            if (items.isEmpty()) {
                break;
            }
            for (Item item : items) {
                acceptItem(state, item);
            }
            offset += items.size();
            entityManager.clear();
        }
        return state;
    }

    private static AuditState scanRawItems(File rawItemsFile) throws IOException {
        AuditState state = new AuditState();
        com.google.gson.JsonParser parser = new com.google.gson.JsonParser();
        try (BufferedReader reader = openGzipUtf8(rawItemsFile)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().length() == 0) {
                    continue;
                }
                acceptItem(state, rawItemFromJson(parser.parse(line).getAsJsonObject()));
            }
        }
        return state;
    }

    private static Item rawItemFromJson(JsonObject row) {
        return new Item(
                stringValue(row, "itemId"),
                stringValue(row, "renderAssetRef"),
                stringValue(row, "modId"),
                stringValue(row, "internalName"),
                stringValue(row, "unlocalizedName"),
                stringValue(row, "localizedName"),
                0,
                intValue(row, "damage"),
                stringValue(row, "nbtDescriptor"),
                stringValue(row, "tooltip"),
                intValue(row, "maxStackSize"),
                intValue(row, "maxDamage"),
                Collections.<String, Integer>emptyMap());
    }

    private static String stringValue(JsonObject row, String key) {
        if (row == null || !row.has(key) || row.get(key).isJsonNull()) {
            return "";
        }
        return row.get(key).getAsString();
    }

    private static int intValue(JsonObject row, String key) {
        if (row == null || !row.has(key) || row.get(key).isJsonNull()) {
            return 0;
        }
        try {
            return row.get(key).getAsInt();
        } catch (Exception ignored) {
            return 0;
        }
    }

    private static BufferedReader openGzipUtf8(File file) throws IOException {
        return new BufferedReader(
                new InputStreamReader(
                        new GZIPInputStream(new FileInputStream(file)),
                        StandardCharsets.UTF_8));
    }

    private static void acceptItem(AuditState state, Item item) {
        state.totalItems++;
        increment(state.modCounts, safe(item.getModId()));

        String nbt = safe(item.getNbt());
        boolean tagged = nbt.trim().length() > 0;
        if (!tagged) {
            return;
        }

        state.taggedItems++;
        for (String key : extractNbtKeys(nbt)) {
            increment(state.nbtKeyCounts, key);
        }

        String family = SemanticItemIdentityMapper.classify(item, nbt);
        if (family == null) {
            state.unclassifiedTaggedItems++;
            String unclassifiedKey = unclassifiedFamilyKey(item);
            increment(state.unclassifiedFamilyCounts, unclassifiedKey);
            addSample(state.unclassifiedSamples, sampleItem(item));
            addSample(state.unclassifiedFamilySamples, unclassifiedKey + " => " + sampleItem(item));
            return;
        }

        state.classifiedTaggedItems++;
        FamilyStats stats = state.families.get(family);
        if (stats == null) {
            stats = new FamilyStats(family);
            state.families.put(family, stats);
        }
        stats.itemCount++;
        stats.baseKeys.add(baseKey(item));
        stats.variantKeys.add(variantKey(item, nbt));
        addSample(stats.sampleItemIds, item.getId());
        addSample(stats.sampleItems, sampleItem(item));
    }

    private static JsonObject buildFamilyAudit(AuditState state) {
        JsonObject root = rootObject();
        root.addProperty("status", "diagnostic-only");
        root.addProperty("totalItems", state.totalItems);
        root.addProperty("taggedItems", state.taggedItems);
        root.addProperty("classifiedTaggedItems", state.classifiedTaggedItems);
        root.addProperty("unclassifiedTaggedItems", state.unclassifiedTaggedItems);
        root.addProperty("familyCount", state.families.size());
        root.add("families", familyArray(state));
        root.add("topMods", topMap(state.modCounts, 40, "modId"));
        root.add("topUnclassifiedFamilies", topMap(state.unclassifiedFamilyCounts, 80, "familyKey"));
        root.add("unclassifiedSamples", stringArray(state.unclassifiedSamples));
        root.add("unclassifiedFamilySamples", stringArray(state.unclassifiedFamilySamples));
        return root;
    }

    private static JsonObject buildNbtKeyDistribution(AuditState state) {
        JsonObject root = rootObject();
        root.addProperty("totalTaggedItems", state.taggedItems);
        root.add("topKeys", topMap(state.nbtKeyCounts, 120, "key"));
        root.add("knownSemanticKeys", knownSemanticKeys(state));
        return root;
    }

    private static JsonObject buildNormalizationReport(AuditState state) {
        JsonObject root = rootObject();
        long estimatedAfter = estimatePublicItemsAfterNormalization(state);
        long collapsed = Math.max(0L, state.totalItems - estimatedAfter);
        root.addProperty("status", "diagnostic-only");
        root.addProperty("beforePublicItems", state.totalItems);
        root.addProperty("estimatedAfterSemanticItems", estimatedAfter);
        root.addProperty("estimatedCollapsedVariants", collapsed);
        root.addProperty(
                "classificationCoverage",
                state.taggedItems == 0
                        ? 1.0D
                        : ((double) state.classifiedTaggedItems) / ((double) state.taggedItems));
        root.addProperty("taggedItems", state.taggedItems);
        root.addProperty("classifiedTaggedItems", state.classifiedTaggedItems);
        root.addProperty("unclassifiedTaggedItems", state.unclassifiedTaggedItems);
        root.add("families", familyArray(state));
        root.add("topUnclassifiedFamilies", topMap(state.unclassifiedFamilyCounts, 40, "familyKey"));
        root.add("nextActions", nextActions());
        return root;
    }

    private static JsonArray familyArray(AuditState state) {
        List<FamilyStats> families = new ArrayList<FamilyStats>(state.families.values());
        Collections.sort(families, new Comparator<FamilyStats>() {
            @Override
            public int compare(FamilyStats left, FamilyStats right) {
                int byCount = Long.compare(right.itemCount, left.itemCount);
                return byCount != 0 ? byCount : left.family.compareTo(right.family);
            }
        });

        JsonArray array = new JsonArray();
        for (FamilyStats stats : families) {
            JsonObject object = new JsonObject();
            object.addProperty("family", stats.family);
            object.addProperty("items", stats.itemCount);
            object.addProperty("baseItems", stats.baseKeys.size());
            object.addProperty("variantKeys", stats.variantKeys.size());
            object.addProperty("estimatedCollapsedVariants", Math.max(0, stats.variantKeys.size() - stats.baseKeys.size()));
            object.add("exampleItemIds", stringArray(stats.sampleItemIds));
            object.add("sampleItems", stringArray(stats.sampleItems));
            array.add(object);
        }
        return array;
    }

    private static JsonArray knownSemanticKeys(AuditState state) {
        String[] keys = new String[] {
                "GT.ToolStats",
                "InfiTool",
                "Head",
                "Handle",
                "Accessory",
                "Extra",
                "RenderHead",
                "Damage",
                "Broken",
                "rod",
                "cap",
                "sceptre",
                "gene",
                "genes",
                "species",
                "chromosome",
                "allele",
                "mobType",
                "id",
                "block",
                "metadata",
                "modid",
                "itemname",
                "x"
        };
        JsonArray array = new JsonArray();
        for (String key : keys) {
            JsonObject object = new JsonObject();
            object.addProperty("key", key);
            object.addProperty("count", countForKey(state.nbtKeyCounts, key));
            array.add(object);
        }
        return array;
    }

    private static JsonArray nextActions() {
        JsonArray array = new JsonArray();
        array.add(new com.google.gson.JsonPrimitive("Promote high-confidence families into facts/items/semantic-items.jsonl.gz."));
        array.add(new com.google.gson.JsonPrimitive("Write identity-map rows from legacy itemId to publicItemId/variantId/payloadHash."));
        array.add(new com.google.gson.JsonPrimitive("Make NeoNEI browser consume publicItemId first and variants lazily from the variant index."));
        array.add(new com.google.gson.JsonPrimitive("Keep raw NBT payloads addressable by payloadHash; do not discard original payload data."));
        return array;
    }

    private static long estimatePublicItemsAfterNormalization(AuditState state) {
        long classifiedBaseItems = 0L;
        for (FamilyStats stats : state.families.values()) {
            classifiedBaseItems += stats.baseKeys.size();
        }
        long untaggedItems = state.totalItems - state.taggedItems;
        return untaggedItems + state.unclassifiedTaggedItems + classifiedBaseItems;
    }

    private static Set<String> extractNbtKeys(String nbt) {
        Set<String> keys = new LinkedHashSet<String>();
        int tokenStart = -1;
        for (int i = 0; i < nbt.length(); i++) {
            char c = nbt.charAt(i);
            if (tokenStart < 0) {
                if (isKeyStart(c)) {
                    tokenStart = i;
                }
                continue;
            }
            if (c == ':') {
                String key = nbt.substring(tokenStart, i).trim();
                if (isReasonableKey(key)) {
                    keys.add(key);
                }
                tokenStart = -1;
            } else if (!isKeyChar(c)) {
                tokenStart = isKeyStart(c) ? i : -1;
            }
        }
        return keys;
    }

    private static boolean isKeyStart(char c) {
        return Character.isLetter(c) || c == '_' || c == '$';
    }

    private static boolean isKeyChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '.' || c == '|' || c == '$' || c == '-';
    }

    private static boolean isReasonableKey(String key) {
        return key.length() > 0 && key.length() <= 80 && key.indexOf(' ') < 0 && key.indexOf('=') < 0;
    }

    private static String baseKey(Item item) {
        return SemanticItemIdentityMapper.baseKey(item);
    }

    private static String variantKey(Item item, String nbt) {
        return baseKey(item) + "|" + Integer.toHexString(nbt.hashCode());
    }

    private static String sampleItem(Item item) {
        return safe(item.getId()) + " | " + safe(item.getModId()) + ":" + safe(item.getInternalName()) + ":" + item.getItemDamage();
    }

    private static String unclassifiedFamilyKey(Item item) {
        return safe(item.getModId()) + "::" + safe(item.getInternalName());
    }

    private static JsonObject rootObject() {
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", SCHEMA);
        root.addProperty("generatedAt", utcNow());
        return root;
    }

    private static JsonArray topMap(Map<String, Long> counts, int limit, String keyName) {
        List<Map.Entry<String, Long>> entries = new ArrayList<Map.Entry<String, Long>>(counts.entrySet());
        Collections.sort(entries, new Comparator<Map.Entry<String, Long>>() {
            @Override
            public int compare(Map.Entry<String, Long> left, Map.Entry<String, Long> right) {
                int byCount = Long.compare(right.getValue(), left.getValue());
                return byCount != 0 ? byCount : left.getKey().compareTo(right.getKey());
            }
        });
        JsonArray array = new JsonArray();
        int count = 0;
        for (Map.Entry<String, Long> entry : entries) {
            if (count >= limit) {
                break;
            }
            JsonObject object = new JsonObject();
            object.addProperty(keyName, entry.getKey());
            object.addProperty("count", entry.getValue());
            array.add(object);
            count++;
        }
        return array;
    }

    private static JsonArray stringArray(Set<String> values) {
        JsonArray array = new JsonArray();
        for (String value : values) {
            array.add(new com.google.gson.JsonPrimitive(value));
        }
        return array;
    }

    private static long countForKey(Map<String, Long> counts, String key) {
        Long exact = counts.get(key);
        if (exact != null) {
            return exact;
        }
        String lower = key.toLowerCase(Locale.ROOT);
        long total = 0L;
        for (Map.Entry<String, Long> entry : counts.entrySet()) {
            if (entry.getKey().toLowerCase(Locale.ROOT).equals(lower)) {
                total += entry.getValue();
            }
        }
        return total;
    }

    private static void increment(Map<String, Long> counts, String key) {
        String normalized = key == null || key.trim().length() == 0 ? "<empty>" : key.trim();
        Long current = counts.get(normalized);
        counts.put(normalized, current == null ? 1L : current + 1L);
    }

    private static void addSample(Set<String> samples, String value) {
        if (samples.size() < SAMPLE_LIMIT && value != null && value.trim().length() > 0) {
            samples.add(value);
        }
    }

    private static void writeJson(Gson gson, File out, Object value) throws IOException {
        File parent = out.getParentFile();
        if (parent != null) {
            ensureDirectory(parent);
        }
        try (FileOutputStream fos = new FileOutputStream(out);
             OutputStreamWriter writer = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
            gson.toJson(value, writer);
        }
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

    private static void ensureDirectory(File directory) throws IOException {
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("Failed to create directory: " + directory.getAbsolutePath());
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String utcNow() {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(new Date());
    }

    public static final class SemanticAuditSummary {
        public long totalItems;
        public long taggedItems;
        public long classifiedTaggedItems;
        public long unclassifiedTaggedItems;
        public long estimatedPublicItemsAfterNormalization;
        public long familyCount;
        public long semanticItems;
        public long variants;
        public long payloads;
        public long identityMapRows;
    }

    private static final class SemanticStreamCounts {
        long semanticItems;
        long variants;
        long payloads;
        long identityMapRows;
    }

    private static final class AuditState {
        long totalItems;
        long taggedItems;
        long classifiedTaggedItems;
        long unclassifiedTaggedItems;
        final Map<String, FamilyStats> families = new LinkedHashMap<String, FamilyStats>();
        final Map<String, Long> nbtKeyCounts = new LinkedHashMap<String, Long>();
        final Map<String, Long> modCounts = new LinkedHashMap<String, Long>();
        final Map<String, Long> unclassifiedFamilyCounts = new LinkedHashMap<String, Long>();
        final Set<String> unclassifiedSamples = new LinkedHashSet<String>();
        final Set<String> unclassifiedFamilySamples = new LinkedHashSet<String>();
    }

    private static final class FamilyStats {
        final String family;
        long itemCount;
        final Set<String> baseKeys = new LinkedHashSet<String>();
        final Set<String> variantKeys = new LinkedHashSet<String>();
        final Set<String> sampleItemIds = new LinkedHashSet<String>();
        final Set<String> sampleItems = new LinkedHashSet<String>();

        FamilyStats(String family) {
            this.family = family;
        }
    }

    private static final class JsonlWriter implements java.io.Closeable {
        private final Gson gson;
        private final OutputStreamWriter writer;

        JsonlWriter(File out, Gson gson) throws IOException {
            this.gson = gson;
            File parent = out.getParentFile();
            if (parent != null) {
                ensureDirectory(parent);
            }
            FileOutputStream fos = new FileOutputStream(out);
            this.writer = out.getName().endsWith(".gz")
                    ? new OutputStreamWriter(new GZIPOutputStream(fos), StandardCharsets.UTF_8)
                    : new OutputStreamWriter(fos, StandardCharsets.UTF_8);
        }

        void write(JsonObject object) throws IOException {
            gson.toJson(object, writer);
            writer.write('\n');
        }

        @Override
        public void close() throws IOException {
            writer.close();
        }
    }
}
