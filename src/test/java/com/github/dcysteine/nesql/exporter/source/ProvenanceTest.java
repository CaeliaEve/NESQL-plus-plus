package com.github.dcysteine.nesql.exporter.source;

import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Collections;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

final class ProvenanceTest {
    static void run() {
        JsonObject data = object("game", "Minecraft 1.7.10", "loader", "Forge", "locale", "en_US",
                "mods", array(object("id", "fixture", "version", "1", "sha256", CanonicalJson.digest(new byte[]{1}))),
                "inputs", array(), "resources", array("a.zip", "b.zip"), "knowledge", object(),
                "probes", array(Probe.defaults().json()), "settings", object("profile", "data", "handlers", "a", "iconPixels", "64"));
        JsonObject full = copy(data);
        full.getAsJsonObject("settings").addProperty("profile", "full");
        full.getAsJsonObject("settings").addProperty("handlers", "b");
        full.getAsJsonObject("settings").addProperty("scope", "recipes");
        full.add("probes", array(new Probe(4, Collections.emptyMap()).json()));
        String runtime = Provenance.runtime(data);
        require(runtime.equals(Provenance.runtime(full)), "Capture scope changed the runtime identity");
        require(!CanonicalJson.digest(data).equals(CanonicalJson.digest(full)), "Original environment identity was erased");
        for (String field : new String[]{"locale", "mods", "inputs", "resources", "knowledge", "settings", "future"}) {
            JsonObject changed = copy(data);
            if (field.equals("settings")) changed.getAsJsonObject(field).addProperty("iconPixels", "128");
            else if (field.equals("resources")) changed.add(field, array("b.zip", "a.zip"));
            else changed.addProperty(field, "changed");
            require(!runtime.equals(Provenance.runtime(changed)), "Ignored runtime change: " + field);
        }
        String session = CanonicalJson.digest(new byte[]{2});
        Jobs.Request first = new Jobs.Request("one", "first", "data");
        Jobs.Request retry = new Jobs.Request("two", "second", "data");
        JsonObject evidence = Provenance.capture(data, first, session);
        require(evidence.equals(Provenance.capture(data, retry, session)), "Task label changed reusable capture identity");
        require(!evidence.equals(Provenance.capture(data, new Jobs.Request("one", "first", "images"), session)), "Ignored capture profile");
        require(!evidence.equals(Provenance.capture(data, first, CanonicalJson.digest(new byte[]{3}))), "Ignored session change");
        require(data.getAsJsonObject("settings").has("profile") && data.has("probes"), "Mutated source environment");
        JsonObject request = object("key", "resume-one", "name", "fixture", "profile", "full", "scope", "recipes",
                "handlers", array("category_" + CanonicalJson.digest(new byte[]{4})), "world", "test-copy");
        Jobs.Request original = Jobs.Request.parse(request);
        request.add("resume", object("job", "12345678-1234-1234-1234-123456789abc", "sha256", CanonicalJson.digest(new byte[]{5})));
        Jobs.Request resumed = Jobs.Request.parse(request);
        require(Provenance.capture(data, original, session).equals(Provenance.capture(data, resumed, session)),
                "Recovery transport changed captured fact selection");
        request.getAsJsonObject("resume").addProperty("job", "../other");
        try { Jobs.Request.parse(request); throw new AssertionError("Accepted a recovery path as job ID"); }
        catch (Jobs.Fault expected) { }
    }
    private static JsonObject copy(JsonObject value) { return new JsonParser().parse(value.toString()).getAsJsonObject(); }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
