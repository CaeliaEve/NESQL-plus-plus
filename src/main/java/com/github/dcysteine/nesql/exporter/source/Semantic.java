package com.github.dcysteine.nesql.exporter.source;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.security.MessageDigest;
import java.util.Arrays;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Ordered native facts before graphics are attached. A fresh data capture must match. */
public final class Semantic {
    private final MessageDigest digest = CanonicalJson.sha256();
    private long records;
    private JsonObject result;

    public void add(String kind, JsonObject source) {
        if (result != null) throw new IllegalStateException("Semantic proof is sealed");
        if (!Arrays.asList("items", "fluids", "aspects", "research", "categories", "recipes", "programs").contains(kind)) return;
        JsonObject row = new JsonParser().parse(source.toString()).getAsJsonObject();
        if (kind.equals("items") || kind.equals("fluids") || kind.equals("aspects")) row.add("icon", value(null));
        if (kind.equals("categories") || kind.equals("recipes")) row.add("view", value(null));
        if (kind.equals("research")) { row.add("icon", value(null)); row.add("texture", value(null)); }
        digest.update(CanonicalJson.bytes(object("kind", kind, "row", row)));
        digest.update((byte) '\n'); records++;
    }

    public JsonObject finish() {
        if (result == null) result = object("sha256", CanonicalJson.hex(digest.digest()), "records", Long.toString(records));
        return new JsonParser().parse(result.toString()).getAsJsonObject();
    }
}
