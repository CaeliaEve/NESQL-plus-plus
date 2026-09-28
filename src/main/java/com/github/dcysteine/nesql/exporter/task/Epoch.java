package com.github.dcysteine.nesql.exporter.task;

import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Process-local world/player/resource lifetime. No world names or paths are persisted. */
final class Epoch {
    private Object world, player;
    private String id;

    synchronized String observe(Object world, Object player) {
        if (id == null || this.world != world || this.player != player) {
            id = CanonicalJson.digest(UUID.randomUUID().toString().getBytes(StandardCharsets.US_ASCII));
            this.world = world; this.player = player;
        }
        return id;
    }

    synchronized void reload() { id = null; }
}
