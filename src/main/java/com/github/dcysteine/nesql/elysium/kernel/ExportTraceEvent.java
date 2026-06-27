package com.github.dcysteine.nesql.elysium.kernel;

public final class ExportTraceEvent {
    public final String event;
    public final String subject;
    public final String status;
    public final long elapsedMs;
    public final long epochMs;

    public ExportTraceEvent(String event, String subject, String status, long elapsedMs) {
        this.event = event;
        this.subject = subject;
        this.status = status;
        this.elapsedMs = Math.max(0L, elapsedMs);
        this.epochMs = System.currentTimeMillis();
    }
}
