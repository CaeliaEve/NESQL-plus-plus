package com.github.dcysteine.nesql.exporter.task;

import static com.github.dcysteine.nesql.exporter.source.Json.*;

import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Successful native dispositions. Failed and cancelled attempts remain exceptions. */
public final class CaptureOutcome {
    public enum Kind {
        CAPTURED,
        EXCLUDED,
        DUPLICATE,
        REFERENCE
    }

    public final Kind kind;
    public final int emitted, duplicates, references;
    private final String snapshot;

    private CaptureOutcome(
            Kind kind, int emitted, int duplicates, int references, JsonObject detail) {
        this.kind = kind;
        this.emitted = emitted;
        this.duplicates = duplicates;
        this.references = references;
        detail.addProperty("kind", kind.name().toLowerCase(java.util.Locale.ROOT));
        detail.addProperty("emitted", emitted);
        detail.addProperty("duplicates", duplicates);
        detail.addProperty("references", references);
        if (CanonicalJson.bytes(detail).length > 1024 * 1024)
            throw new Jobs.Fault(
                    "capture_outcome_limit", "Native disposition exceeds its evidence budget");
        snapshot = detail.toString();
    }

    public static CaptureOutcome recipes(int emitted, JsonArray origins) {
        if (emitted < 0
                || origins == null
                || (long) emitted + origins.size() < 1
                || (long) emitted + origins.size() > 65536)
            throw new Jobs.Fault(
                    "capture_empty", "Native recipe capture did not account for its branches");
        for (JsonElement value : origins) {
            if (!value.isJsonObject())
                throw new Jobs.Fault("capture_provenance", "Duplicate origin is not an object");
            JsonObject origin = value.getAsJsonObject();
            if (origin.entrySet().size() != 4
                    || !origin.has("recipe")
                    || !origin.get("recipe").isJsonPrimitive()
                    || !origin.get("recipe").getAsJsonPrimitive().isString()
                    || !origin.get("recipe").getAsString().matches("recipe_[a-f0-9]{64}"))
                throw new Jobs.Fault("capture_provenance", "Duplicate recipe identity is missing");
            for (String field : new String[] {"firstIndex", "firstBranch", "branch"}) {
                JsonElement number = origin.get(field);
                if (number == null
                        || !number.isJsonPrimitive()
                        || !number.getAsJsonPrimitive().isNumber()
                        || !number.getAsString().matches("0|[1-9][0-9]{0,6}"))
                    throw new Jobs.Fault(
                            "capture_provenance", "Duplicate native origin is missing: " + field);
            }
        }
        return new CaptureOutcome(
                emitted == 0 ? Kind.DUPLICATE : Kind.CAPTURED,
                emitted,
                origins.size(),
                0,
                object("duplicateOrigins", origins));
    }

    public static CaptureOutcome excluded(JsonObject proof) {
        if (proof == null
                || proof.entrySet().isEmpty()
                || !proof.has("reason")
                || !proof.get("reason").isJsonPrimitive()
                || !proof.get("reason").getAsJsonPrimitive().isString()
                || proof.get("reason").getAsString().isEmpty())
            throw new Jobs.Fault("capture_empty", "Native exclusion has no proof");
        if (CanonicalJson.bytes(proof).length > 128 * 1024)
            throw new Jobs.Fault(
                    "recipe_exclusion_limit", "Native exclusion proof exceeds its budget");
        return new CaptureOutcome(Kind.EXCLUDED, 0, 0, 0, object("proof", proof));
    }

    public static CaptureOutcome reference() {
        return new CaptureOutcome(Kind.REFERENCE, 0, 0, 1, object());
    }

    public static CaptureOutcome parse(JsonObject json) {
        if (json == null
                || !json.has("kind")
                || !json.get("kind").isJsonPrimitive()
                || !json.get("kind").getAsJsonPrimitive().isString())
            throw new Jobs.Fault("capture_outcome", "Native disposition kind is missing");
        for (String field : new String[] {"emitted", "duplicates", "references"}) {
            JsonElement count = json.get(field);
            if (count == null
                    || !count.isJsonPrimitive()
                    || !count.getAsJsonPrimitive().isNumber()
                    || !count.getAsString().matches("0|[1-9][0-9]{0,4}")
                    || count.getAsInt() > 65536)
                throw new Jobs.Fault(
                        "capture_outcome", "Native disposition count must be an integer: " + field);
        }
        CaptureOutcome result;
        switch (json.get("kind").getAsString()) {
            case "captured":
            case "duplicate":
                result =
                        recipes(
                                json.get("emitted").getAsInt(),
                                json.getAsJsonArray("duplicateOrigins"));
                break;
            case "excluded":
                result = excluded(json.getAsJsonObject("proof"));
                break;
            case "reference":
                result = reference();
                break;
            default:
                throw new Jobs.Fault("capture_outcome", "Unknown native disposition");
        }
        if (!result.json().equals(json))
            throw new Jobs.Fault(
                    "capture_outcome", "Native disposition counts, fields or evidence changed");
        return result;
    }

    /** Callers cannot mutate evidence after validation or after it enters a hash chain. */
    public JsonObject json() {
        return new JsonParser().parse(snapshot).getAsJsonObject();
    }
}
