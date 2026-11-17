package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.recipe.ICraftingHandler;
import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.source.Identity;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.*;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;
import java.util.*;
import java.util.function.Function;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Botania's NEI lexicon index, with native text facts instead of a consuming book recipe. */
final class BotaniaLexiconReferences extends ReaderSnapshot implements NativeReferences {
    static final String HANDLER = "vazkii.botania.client.integration.nei.recipe.RecipeHandlerLexicaBotania";
    private  JsonObject source;
    private  List<?> nativeEntries;
    private  JsonArray records;
    private final Map<String, ItemStack> specimens = new HashMap<>();
    private final String signature, domain;

    static boolean supports(ICraftingHandler handler) { return handler != null && HANDLER.equals(handler.getClass().getName()); }
    BotaniaLexiconReferences(ICraftingHandler handler, JsonObject source) {
        if (!supports(handler)) throw fault("Unknown Botania lexicon handler");
        this.source = copy(source); nativeEntries = entries();
        records = snapshot(nativeEntries, this.source, StatCollector::translateToLocal, specimens);
        signature = CanonicalJson.digest(records); domain = records.get(0).getAsJsonObject().get("domain").getAsString();
    }
    private static List<?> entries() {
        Object value = invoke(type("vazkii.botania.api.BotaniaAPI"), null, "getAllEntries", new Class<?>[0]);
        if (!(value instanceof List) || ((List<?>) value).isEmpty())
            throw new Jobs.Fault("handler_not_ready", "Native Botania lexicon registry is not initialized");
        return (List<?>) value;
    }
    public int size() { checkOpen(); return records.size(); }
    public String domain() { checkOpen(); return domain; }
    public JsonObject counts() { checkOpen(); return object("entries", records.size() - 1, "items", specimens.size()); }
    public void verify() { checkOpen(); if (entries() != nativeEntries || nativeEntries.size() != records.size() - 1) throw drift(); }
    public void finishVerification() { checkOpen();
        if (!signature.equals(CanonicalJson.digest(snapshot(entries(), source, StatCollector::translateToLocal, new HashMap<>())))) throw drift();
    }
    public void capture(int index, Facts facts) { checkOpen();
        JsonObject row = copy(records.get(index).getAsJsonObject()), data = row.getAsJsonObject("data");
        if (data.has("items")) for (JsonElement raw : data.getAsJsonArray("items")) {
            String id = raw.getAsJsonObject().get("item").getAsString();
            ItemStack specimen = specimens.get(id);
            if (specimen == null || !facts.item(specimen.copy()).equals(id)) throw drift();
        }
        facts.row("references", row);
    }
    static JsonArray snapshot(List<?> entries, JsonObject source, Function<String, String> translate, Map<String, ItemStack> specimens) {
        if (entries.isEmpty() || entries.size() > 16384) throw fault("Uninitialized or oversized lexicon registry");
        JsonArray data = array(); Set<String> seen = new HashSet<>(); int pages = 0;
        for (Object entry : entries) {
            Jobs.checkpoint();
            String key = string(call(entry, "getUnlocalizedName"), "entry key");
            if (!seen.add(key)) throw fault("Duplicate lexicon entry key: " + key);
            Object knowledge = call(entry, "getKnowledgeType");
            if (knowledge == null) throw fault("Lexicon knowledge type absent");
            String knowledgeKey = string(call(knowledge, "getUnlocalizedName"), "knowledge key");
            String color = string(field(knowledge, "color").toString(), "knowledge color");
            Object auto = field(knowledge, "autoUnlock");
            if (!(auto instanceof Boolean)) throw fault("Invalid native knowledge unlock policy");
            Object rawTagline = call(entry, "getTagline");
            JsonElement tagline = rawTagline == null ? JsonNull.INSTANCE : text(string(rawTagline, "tagline key"), translate, true);
            Object rawItems = call(entry, "getDisplayedRecipes");
            if (!(rawItems instanceof List)) throw fault("Missing native displayed lexicon items");
            JsonArray items = array();
            for (Object raw : (List<?>) rawItems) {
                if (++pages > 262144 || !(raw instanceof ItemStack)) throw fault("Invalid or oversized lexicon display list");
                ItemStack stack = ((ItemStack) raw).copy(); stack.stackSize = 1;
                String registry = Item.itemRegistry.getNameForObject(stack.getItem());
                if (registry == null || Item.itemRegistry.getObject(registry) != stack.getItem()) throw fault("Unregistered lexicon item");
                String id = Identity.item(registry, net.minecraft.init.Items.feather.getDamage(stack), TypedNbt.encode(stack.getTagCompound()));
                String nativeKey = string(invoke(type("vazkii.botania.api.lexicon.LexiconRecipeMappings"), null,
                        "stackToString", new Class<?>[]{ItemStack.class}, stack.copy()), "native item key");
                String quickKey = "botania.nei.quickInfo:" + nativeKey;
                String quickValue = string(translate.apply(quickKey), "quick info text");
                boolean hasQuickInfo = !quickKey.equals(quickValue);
                JsonObject quickInfo = hasQuickInfo ? localized(quickKey, quickValue, true)
                        : text("botania.nei.lexicaNoInfo", translate, true);
                items.add(object("item", id, "nativeKey", nativeKey, "quickInfo", quickInfo, "hasQuickInfo", hasQuickInfo));
                specimens.put(id, stack);
            }
            data.add(object("kind", "lexiconEntry", "entry", key, "name", text(key, translate, false),
                    "knowledge", object("name", localized(knowledgeKey, string(translate.apply(knowledgeKey), "knowledge text").replaceAll("\\&.", ""), false),
                            "color", color, "autoUnlock", auto), "tagline", tagline, "items", items));
        }
        JsonArray full = array(); full.add(object("kind", "lexiconDefinition", "source", source, "entries", data.size(), "pages", pages, "consuming", false));
        for (JsonElement row : data) full.add(row);
        String domain = Identity.content("reference", object("kind", "botaniaLexiconIndex", "records", full));
        JsonArray rows = array();
        for (int i = 0; i < full.size(); i++) {
            JsonObject row = object("domain", domain, "order", i, "data", full.get(i));
            row.addProperty("id", Identity.content("refrow", row)); rows.add(row);
        }
        return rows;
    }
    private static Object call(Object target, String method) { return invoke(target.getClass(), target, method, new Class<?>[0]); }
    private static String string(Object value, String field) {
        if (!(value instanceof String) || ((String) value).isEmpty() || ((String) value).length() > 262144) throw fault("Invalid lexicon " + field);
        return (String) value;
    }
    private static JsonObject text(String key, Function<String, String> translate, boolean pageText) {
        return localized(key, string(translate.apply(key), "localized text"), pageText);
    }
    private static JsonObject localized(String key, String value, boolean pageText) {
        // Native PageText treats & as section formatting and <br> as paragraph breaks.
        return object("key", key, "value", pageText ? value.replace('&', '\u00a7').replace("<br>", "\n") : value);
    }
    private static JsonObject copy(JsonObject value) { return new JsonParser().parse(value.toString()).getAsJsonObject(); }
    private static Jobs.Fault fault(String message) { return new Jobs.Fault("handler_unsupported", message); }
    private static Jobs.Fault drift() { return new Jobs.Fault("environment_changed", "Native lexicon definitions or text changed"); }

    @Override protected void release(){specimens.clear();source=null;nativeEntries=null;records=null;}
}
