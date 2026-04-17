package com.github.dcysteine.nesql.exporter.plugin.nei.metadata;

import com.google.gson.Gson;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Loads the bundled NEI handler metadata extracted from GTNH's NotEnoughItems handler registry.
 *
 * <p>This repository is deliberately independent from the current exporter execution path. It gives us a safe
 * bridge for later NESQL++ improvements without changing data export behavior yet.</p>
 */
public final class NeiHandlerMetadataRepository {
    private static final String RESOURCE_PATH = "/nesql/nei/handler-metadata.json";
    private static final NeiHandlerMetadataRepository INSTANCE = new NeiHandlerMetadataRepository();

    private final NeiHandlerMetadataIndex index;
    private final Map<String, NeiHandlerMetadataEntry> byHandler;

    private NeiHandlerMetadataRepository() {
        this.index = loadIndex();
        this.byHandler = buildHandlerIndex(index.getEntries());
    }

    public static NeiHandlerMetadataRepository getInstance() {
        return INSTANCE;
    }

    public NeiHandlerMetadataIndex getIndex() {
        return index;
    }

    public List<NeiHandlerMetadataEntry> getEntries() {
        return index.getEntries();
    }

    public NeiHandlerMetadataEntry findByHandler(String handler) {
        if (handler == null) {
            return null;
        }
        return byHandler.get(handler);
    }

    public List<NeiHandlerMetadataEntry> findByHandlerPrefix(String prefix) {
        if (prefix == null || prefix.trim().isEmpty()) {
            return Collections.emptyList();
        }
        String normalized = prefix.trim().toLowerCase(Locale.ROOT);
        List<NeiHandlerMetadataEntry> matches = new ArrayList<>();
        for (NeiHandlerMetadataEntry entry : index.getEntries()) {
            String handler = entry.getHandler();
            if (handler != null && handler.toLowerCase(Locale.ROOT).startsWith(normalized)) {
                matches.add(entry);
            }
        }
        return matches;
    }

    public List<NeiHandlerMetadataEntry> findByModId(String modId) {
        if (modId == null || modId.trim().isEmpty()) {
            return Collections.emptyList();
        }
        String normalized = modId.trim().toLowerCase(Locale.ROOT);
        List<NeiHandlerMetadataEntry> matches = new ArrayList<>();
        for (NeiHandlerMetadataEntry entry : index.getEntries()) {
            String entryModId = entry.getModId();
            if (entryModId != null && entryModId.toLowerCase(Locale.ROOT).equals(normalized)) {
                matches.add(entry);
            }
        }
        return matches;
    }

    private static NeiHandlerMetadataIndex loadIndex() {
        InputStream stream = NeiHandlerMetadataRepository.class.getResourceAsStream(RESOURCE_PATH);
        if (stream == null) {
            throw new IllegalStateException("Missing NEI handler metadata resource: " + RESOURCE_PATH);
        }
        try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            NeiHandlerMetadataIndex result = new Gson().fromJson(reader, NeiHandlerMetadataIndex.class);
            if (result == null) {
                throw new IllegalStateException("Empty NEI handler metadata resource: " + RESOURCE_PATH);
            }
            return result;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load NEI handler metadata resource: " + RESOURCE_PATH, e);
        }
    }

    private static Map<String, NeiHandlerMetadataEntry> buildHandlerIndex(List<NeiHandlerMetadataEntry> entries) {
        Map<String, NeiHandlerMetadataEntry> result = new HashMap<>();
        for (NeiHandlerMetadataEntry entry : entries) {
            if (entry.getHandler() != null && !entry.getHandler().trim().isEmpty()) {
                result.put(entry.getHandler(), entry);
            }
        }
        return Collections.unmodifiableMap(result);
    }
}
