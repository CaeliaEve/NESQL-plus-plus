package com.github.dcysteine.nesql.elysium.kernel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ExportDevice {
    private final String busId;
    private final String id;
    private final boolean required;
    private final List<String> capabilities;

    public ExportDevice(String busId, String id, boolean required, List<String> capabilities) {
        this.busId = requireNonEmpty("Export device bus id", busId);
        this.id = requireNonEmpty("Export device id", id);
        this.required = required;
        this.capabilities = Collections.unmodifiableList(new ArrayList<String>(
                capabilities == null ? Collections.<String>emptyList() : capabilities));
    }

    public static ExportDevice required(String busId, String id, List<String> capabilities) {
        return new ExportDevice(busId, id, true, capabilities);
    }

    public static ExportDevice optional(String busId, String id, List<String> capabilities) {
        return new ExportDevice(busId, id, false, capabilities);
    }

    public String busId() {
        return busId;
    }

    public String id() {
        return id;
    }

    public boolean required() {
        return required;
    }

    public List<String> capabilities() {
        return capabilities;
    }

    static String requireNonEmpty(String label, String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(label + " must be non-empty");
        }
        return value;
    }
}
