package com.github.dcysteine.nesql.exporter.semantic;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Versionable semantic rule hints derived from GTNH source/config.
 *
 * <p>The Java {@link SemanticFamily} plugins remain authoritative for parsing
 * real NBT. This pack is intentionally a source-aware contract layer: it tells
 * export validation which family ids are expected to exist, which aliases can
 * be generated later, and whether the bundled rule metadata still matches the
 * active plugin registry.</p>
 */
public final class SemanticRulePack {
    public static final String RESOURCE_PATH = "gtnh-semantic-rules/semantic-rules.json";

    private final JsonObject root;

    private SemanticRulePack(JsonObject root) {
        this.root = root == null ? new JsonObject() : root;
    }

    public static SemanticRulePack loadBundled() {
        InputStream stream = SemanticRulePack.class.getClassLoader().getResourceAsStream(RESOURCE_PATH);
        if (stream == null) {
            return new SemanticRulePack(null);
        }
        try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            JsonElement parsed = new JsonParser().parse(reader);
            return new SemanticRulePack(parsed != null && parsed.isJsonObject() ? parsed.getAsJsonObject() : null);
        } catch (Exception ignored) {
            return new SemanticRulePack(null);
        }
    }

    public String schemaVersion() {
        return string("schemaVersion");
    }

    public String packVersionSource() {
        return string("packVersionSource");
    }

    public int familyCount() {
        JsonArray families = families();
        return families == null ? 0 : families.size();
    }

    public int aliasCount() {
        JsonArray aliases = aliases();
        return aliases == null ? 0 : aliases.size();
    }

    public List<String> familyIds() {
        ArrayList<String> ids = new ArrayList<String>();
        JsonArray families = families();
        if (families == null) {
            return ids;
        }
        for (JsonElement element : families) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject family = element.getAsJsonObject();
            if (family.has("id") && family.get("id").isJsonPrimitive()) {
                String id = family.get("id").getAsString();
                if (id != null && !id.trim().isEmpty()) {
                    ids.add(id.trim());
                }
            }
        }
        return ids;
    }

    public Validation validateAgainstRegistry() {
        Validation validation = new Validation();
        validation.schemaVersion = schemaVersion();
        validation.packVersionSource = packVersionSource();
        validation.familyRules = familyCount();
        validation.aliasRules = aliasCount();

        Set<String> pluginIds = new HashSet<String>();
        for (SemanticFamily family : SemanticFamilyRegistry.families()) {
            pluginIds.add(family.familyId());
        }
        Set<String> ruleIds = new HashSet<String>(familyIds());
        for (String ruleId : ruleIds) {
            if (!pluginIds.contains(ruleId)) {
                validation.ruleIdsWithoutPlugin.add(ruleId);
            }
        }
        for (String pluginId : pluginIds) {
            if (!ruleIds.contains(pluginId)) {
                validation.pluginIdsWithoutRule.add(pluginId);
            }
        }
        validation.status = validation.ruleIdsWithoutPlugin.isEmpty()
                && validation.pluginIdsWithoutRule.isEmpty()
                && validation.familyRules > 0
                ? "ok"
                : "warning";
        return validation;
    }

    private JsonArray families() {
        return root.has("families") && root.get("families").isJsonArray()
                ? root.getAsJsonArray("families")
                : null;
    }

    private JsonArray aliases() {
        return root.has("aliases") && root.get("aliases").isJsonArray()
                ? root.getAsJsonArray("aliases")
                : null;
    }

    private String string(String key) {
        return root.has(key) && root.get(key).isJsonPrimitive() ? root.get(key).getAsString() : null;
    }

    public static final class Validation {
        public String schemaVersion;
        public String packVersionSource;
        public String status = "warning";
        public int familyRules;
        public int aliasRules;
        public List<String> ruleIdsWithoutPlugin = new ArrayList<String>();
        public List<String> pluginIdsWithoutRule = new ArrayList<String>();
    }
}
