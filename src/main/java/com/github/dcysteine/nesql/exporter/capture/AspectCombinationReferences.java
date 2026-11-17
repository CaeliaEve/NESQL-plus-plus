package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.recipe.ICraftingHandler;
import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.source.Identity;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.*;
import thaumcraft.api.aspects.Aspect;
import java.util.*;
import java.util.function.Predicate;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** ARI 1.1.6 registered composition and configured native visibility, no item consumption. */
final class AspectCombinationReferences extends ReaderSnapshot implements NativeReferences {
    static final String HANDLER = "com.gtnewhorizons.aspectrecipeindex.nei.AspectCombinationHandler";
    private AspectRecipeIndexContext context;
    private  JsonObject source;
    private final String signature, domain;
    private  JsonArray records;

    static boolean supports(ICraftingHandler handler) { return handler != null && HANDLER.equals(handler.getClass().getName()); }
    AspectCombinationReferences(ICraftingHandler handler, JsonObject source) {
        if (!supports(handler) || handler.getClass() != type(HANDLER)) throw fault("Unknown aspect combination handler");
        this.source = copy(source);
        context = AspectRecipeIndexContext.open(false);
        records = read(); signature = CanonicalJson.digest(records);
        domain = records.get(0).getAsJsonObject().get("domain").getAsString();
    }
    private JsonArray read() {
        context.verify();
        JsonArray result = snapshot(new ArrayList<>(Aspect.aspects.values()),
                context::discovered, context.showUndiscovered, source);
        context.verify();
        return result;
    }
    public int size() { checkOpen(); return records.size(); }
    public String domain() { checkOpen(); return domain; }
    public JsonObject counts() { checkOpen(); return object("aspects", records.size() - 1); }
    public void verify() { checkOpen();
        if (!signature.equals(CanonicalJson.digest(read())))
            throw new Jobs.Fault("environment_changed", "Aspect definitions or player knowledge changed");
    }
    public void capture(int index, Facts facts) { checkOpen(); facts.row("references", copy(records.get(index).getAsJsonObject())); }

    static JsonArray snapshot(Collection<Aspect> aspects, Predicate<Aspect> discovered, boolean showUndiscovered, JsonObject source) {
        if (aspects.isEmpty() || aspects.size() > 4096) throw fault("Aspect registry is empty or exceeds budget");
        TreeMap<String, Aspect> sorted = new TreeMap<>();
        for (Aspect aspect : aspects) {
            Jobs.checkpoint();
            if (sorted.put(Aspects.id(aspect), aspect) != null) throw fault("Duplicate registered aspect");
        }
        JsonArray data = array();
        data.add(object("kind", "aspectCombinationsDefinition", "source", source, "aspects", sorted.size(),
                "visibility", showUndiscovered ? "allRegisteredAspects" : "nativePlayerKnowledge", "consuming", false));
        for (Map.Entry<String, Aspect> entry : sorted.entrySet()) {
            Jobs.checkpoint(); Aspect aspect = entry.getValue(); JsonArray components = array();
            if (!aspect.isPrimal()) {
                Aspect[] pair = aspect.getComponents();
                if (pair == null || pair.length != 2) throw fault("Compound aspect requires exactly two components");
                for (Aspect component : pair) {
                    String id = Aspects.id(component);
                    if (!sorted.containsKey(id)) throw fault("Aspect component missing from registry snapshot");
                    components.add(value(id));
                }
            }
            data.add(object("kind", "aspectCombination", "aspect", entry.getKey(), "components", components,
                    "visible", showUndiscovered || discovered.test(aspect)));
        }
        String domain = Identity.content("reference", object("kind", "thaumcraftAspectCombinations", "records", data));
        JsonArray records = array();
        for (int index = 0; index < data.size(); index++) {
            JsonObject row = object("domain", domain, "order", index, "data", data.get(index));
            row.addProperty("id", Identity.content("refrow", row)); records.add(row);
        }
        return records;
    }
    private static JsonObject copy(JsonObject value) {
        return new JsonParser().parse(value.toString()).getAsJsonObject();
    }
    private static Jobs.Fault fault(String message) { return new Jobs.Fault("handler_unsupported", message); }

    @Override protected void release(){context=null;source=null;records=null;}
}
