package com.github.dcysteine.nesql.elysium.kernel;

public final class DriverProbeResult {
    public enum Status {
        SUPPORTED,
        UNSUPPORTED,
        DEFERRED
    }

    private final Status status;
    private final String reason;

    private DriverProbeResult(Status status, String reason) {
        if (status == null) {
            throw new IllegalArgumentException("Driver probe status is required");
        }
        this.status = status;
        this.reason = reason == null ? "" : reason;
    }

    public static DriverProbeResult supported(String reason) {
        return new DriverProbeResult(Status.SUPPORTED, reason);
    }

    public static DriverProbeResult unsupported(String reason) {
        return new DriverProbeResult(Status.UNSUPPORTED, reason);
    }

    public static DriverProbeResult deferred(String reason) {
        return new DriverProbeResult(Status.DEFERRED, reason);
    }

    public Status status() {
        return status;
    }

    public String reason() {
        return reason;
    }

    public boolean supported() {
        return status == Status.SUPPORTED;
    }
}
