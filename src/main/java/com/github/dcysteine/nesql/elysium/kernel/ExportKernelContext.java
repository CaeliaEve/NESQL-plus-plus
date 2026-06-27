package com.github.dcysteine.nesql.elysium.kernel;

import com.github.dcysteine.nesql.exporter.main.ExportContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ExportKernelContext {
    private final ExportContext exportContext;
    private final ExportResourceManager resources = new ExportResourceManager();
    private final List<ExportTraceEvent> traceEvents = new ArrayList<ExportTraceEvent>();

    public ExportKernelContext(ExportContext exportContext) {
        this.exportContext = exportContext;
    }

    public ExportContext exportContext() {
        return exportContext;
    }

    public ExportResourceManager resources() {
        return resources;
    }

    public void trace(String event, String subject, String status, long elapsedMs) {
        traceEvents.add(new ExportTraceEvent(event, subject, status, elapsedMs));
    }

    public List<ExportTraceEvent> traceEvents() {
        return Collections.unmodifiableList(traceEvents);
    }

    public void closeResources() throws Exception {
        resources.close();
    }
}
