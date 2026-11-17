package com.github.dcysteine.nesql.exporter.task;

import static com.github.dcysteine.nesql.exporter.source.Json.*;

import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.google.gson.JsonObject;

/** Constant-space ordered native disposition proof; output row counts are separate. */
public final class CaptureAccounting {
    private long captured, excluded, duplicate, reference, emitted, duplicates;
    private String hash =
            CanonicalJson.digest(object("format", "nesql.capture-accounting", "revision", 1));

    public void append(int index, CaptureOutcome outcome) {
        if (index < 0 || outcome == null)
            throw new IllegalArgumentException("Native disposition needs an index and outcome");
        String next =
                CanonicalJson.digest(
                        object("previous", hash, "index", index, "outcome", outcome.json()));
        switch (outcome.kind) {
            case CAPTURED:
                captured++;
                break;
            case EXCLUDED:
                excluded++;
                break;
            case DUPLICATE:
                duplicate++;
                break;
            case REFERENCE:
                reference++;
                break;
            default:
                throw new AssertionError(outcome.kind);
        }
        emitted += outcome.emitted;
        duplicates += outcome.duplicates;
        hash = next;
    }

    public long entries() {
        return captured + excluded + duplicate + reference;
    }

    public JsonObject json() {
        return object(
                "format",
                "nesql.capture-accounting",
                "revision",
                1,
                "committedEntries",
                Long.toString(entries()),
                "captured",
                Long.toString(captured),
                "excluded",
                Long.toString(excluded),
                "duplicate",
                Long.toString(duplicate),
                "reference",
                Long.toString(reference),
                "emittedBranches",
                Long.toString(emitted),
                "duplicateBranches",
                Long.toString(duplicates),
                "evidenceHash",
                hash);
    }
}
