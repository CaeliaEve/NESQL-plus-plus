package com.github.dcysteine.nesql.elysium.kernel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class DriverProbeResult {
    public enum Status {
        SUPPORTED,
        UNSUPPORTED,
        DEFERRED,
        FAILED
    }

    private final Status status;
    private final String reason;
    private final List<String> capabilities;
    private final Throwable cause;

    private DriverProbeResult(Status status, String reason, List<String> capabilities, Throwable cause) {
        if (status == null) {
            throw new IllegalArgumentException("Driver probe status is required");
        }
        this.status = status;
        this.reason = reason == null ? "" : reason;
        this.capabilities = Collections.unmodifiableList(new ArrayList<String>(
                capabilities == null ? Collections.<String>emptyList() : capabilities));
        this.cause = cause;
    }

    public static DriverProbeResult supported(String reason) {
        return supported(reason, Collections.<String>emptyList());
    }

    public static DriverProbeResult supported(String reason, List<String> capabilities) {
        return new DriverProbeResult(Status.SUPPORTED, reason, capabilities, null);
    }

    public static DriverProbeResult unsupported(String reason) {
        return new DriverProbeResult(Status.UNSUPPORTED, reason, Collections.<String>emptyList(), null);
    }

    public static DriverProbeResult deferred(String reason) {
        return new DriverProbeResult(Status.DEFERRED, reason, Collections.<String>emptyList(), null);
    }

    public static DriverProbeResult failed(String reason, Throwable cause) {
        return new DriverProbeResult(Status.FAILED, reason, Collections.<String>emptyList(), cause);
    }

    public Status status() {
        return status;
    }

    public String reason() {
        return reason;
    }

    public List<String> capabilities() {
        return capabilities;
    }

    public Throwable cause() {
        return cause;
    }

    public boolean supported() {
        return status == Status.SUPPORTED;
    }

    public boolean failed() {
        return status == Status.FAILED;
    }
}
