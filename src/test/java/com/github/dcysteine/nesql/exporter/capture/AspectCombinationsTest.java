package com.github.dcysteine.nesql.exporter.capture;

import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.google.gson.*;
import thaumcraft.api.aspects.Aspect;
import java.lang.reflect.*;
import java.util.*;
import java.util.function.Predicate;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Native aspect definitions are knowledge references, not consuming item recipes. */
final class AspectCombinationsTest {
    static void run() throws Exception {
        Class<?> reader = Class.forName("com.github.dcysteine.nesql.exporter.capture.AspectCombinationReferences");
        Method snapshot = reader.getDeclaredMethod("snapshot", Collection.class, Predicate.class, boolean.class, JsonObject.class);
        snapshot.setAccessible(true);
        JsonObject source = object("owner", "aspectrecipeindex", "handler",
                "com.gtnewhorizons.aspectrecipeindex.nei.AspectCombinationHandler", "key", "aspect-combinations");
        List<Aspect> aspects = Arrays.asList(Aspect.LIGHT, Aspect.AIR, Aspect.FIRE);
        Predicate<Aspect> known = aspect -> aspect != Aspect.LIGHT;
        JsonArray records = (JsonArray) snapshot.invoke(null, aspects, known, false, source);
        if (records.size() != 4) throw new AssertionError("Missing aspect definition or rows");
        JsonArray definitions = array(); boolean compound = false;
        for (Aspect aspect : aspects) {
            JsonArray components = array();
            if (!aspect.isPrimal()) for (Aspect part : aspect.getComponents()) components.add(value(Aspects.id(part)));
            definitions.add(object("id", Aspects.id(aspect), "source", Aspects.source(aspect),
                    "components", components, "discovered", known.test(aspect)));
        }
        for (JsonElement entry : records) {
            JsonObject data = entry.getAsJsonObject().getAsJsonObject("data");
            if (!data.get("kind").getAsString().equals("aspectCombination")) continue;
            if (data.get("aspect").getAsString().equals(Aspects.id(Aspect.LIGHT))) {
                compound = true;
                if (data.get("visible").getAsBoolean() || data.getAsJsonArray("components").size() != 2)
                    throw new AssertionError("Undiscovered compound lost its knowledge gate or native components");
            }
        }
        if (!compound) throw new AssertionError("Compound omitted instead of explicit knowledge gate");
        JsonArray reordered = (JsonArray) snapshot.invoke(null, Arrays.asList(Aspect.FIRE, Aspect.LIGHT, Aspect.AIR), known, false, source);
        if (!records.equals(reordered)) throw new AssertionError("Registry iteration order changed stable reference");
        JsonArray discovered = (JsonArray) snapshot.invoke(null, aspects, (Predicate<Aspect>) aspect -> true, false, source);
        if (records.equals(discovered)) throw new AssertionError("Player knowledge drift invisible");
        JsonArray configured = (JsonArray) snapshot.invoke(null, aspects, known, true, source);
        if (!configured.get(0).getAsJsonObject().getAsJsonObject("data").get("visibility").getAsString().equals("allRegisteredAspects"))
            throw new AssertionError("Configured visibility policy was lost");
        for (int index = 1; index < configured.size(); index++)
            if (!configured.get(index).getAsJsonObject().getAsJsonObject("data").get("visible").getAsBoolean())
                throw new AssertionError("Configured visibility hid an undiscovered aspect");
        if (known.test(Aspect.LIGHT) || records.equals(configured))
            throw new AssertionError("Visibility configuration changed player knowledge or was omitted from identity");
        try {
            snapshot.invoke(null, Arrays.asList(Aspect.AIR, Aspect.AIR), known, false, source);
            throw new AssertionError("Duplicate aspect admitted");
        } catch (InvocationTargetException expected) {
            if (!(expected.getCause() instanceof com.github.dcysteine.nesql.exporter.task.Jobs.Fault)) throw expected;
        }
        java.nio.file.Path target = java.nio.file.Paths.get("build/native-tests/aspect-combinations-contract.json");
        java.nio.file.Files.createDirectories(target.getParent());
        java.nio.file.Files.write(target, CanonicalJson.bytes(object("records", records, "configuredRecords", configured, "aspects", definitions)));
        System.out.println("Aspect references: composition, independent knowledge/configured visibility, ordering and duplicate guards passed");
    }
}
