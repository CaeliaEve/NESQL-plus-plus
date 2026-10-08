package com.github.dcysteine.nesql.exporter.source;

import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import static com.github.dcysteine.nesql.exporter.source.Json.object;

/** Comparison keys supplement the full environment; they never replace Source identity. */
public final class Provenance {
    private Provenance() {}

    public static String runtime(JsonObject environment) {
        JsonObject runtime = new JsonParser().parse(environment.toString()).getAsJsonObject();
        // Remove only known request fields. New settings remain identity-bearing.
        runtime.remove("probes");
        JsonObject settings = runtime.getAsJsonObject("settings");
        if (settings != null) { settings.remove("profile"); settings.remove("handlers"); settings.remove("scope"); }
        return CanonicalJson.digest(runtime);
    }

    public static JsonObject capture(JsonObject environment, Jobs.Request request, String session) {
        if (session == null || !session.matches("[a-f0-9]{64}")) throw new IllegalArgumentException("A live session identity is required");
        JsonObject selection = request.captureRequest();
        selection.remove("key"); selection.remove("name");
        return object("revision", 1, "environment", CanonicalJson.digest(environment), "runtime", runtime(environment),
                "session", session, "selection", CanonicalJson.digest(selection));
    }
}
