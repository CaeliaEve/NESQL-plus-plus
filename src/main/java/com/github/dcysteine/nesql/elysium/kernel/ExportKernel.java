package com.github.dcysteine.nesql.elysium.kernel;

import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class ExportKernel {
    private final ExportModuleCatalog catalog;
    private final List<ExportModule> modules;

    public ExportKernel(ExportModuleCatalog catalog) {
        if (catalog == null) {
            throw new IllegalArgumentException("Export module catalog is required");
        }
        this.catalog = catalog;
        this.modules = catalog.modules();
    }

    public void init(ExportKernelContext context) throws Exception {
        for (ExportModule module : modules) {
            long startedAt = System.currentTimeMillis();
            module.init(context);
            context.trace(ExportTracepoint.MODULE_INIT, module.id(), "ok", System.currentTimeMillis() - startedAt);
        }
        bindDrivers(context);
    }

    private void bindDrivers(ExportKernelContext context) throws Exception {
        for (ExportDevice device : catalog.devices()) {
            boolean bound = false;
            for (ExportDriver driver : catalog.drivers()) {
                if (!device.busId().equals(driver.busId())) {
                    continue;
                }
                long startedAt = System.currentTimeMillis();
                DriverProbeResult probe = driver.probe(device, context);
                if (probe == null) {
                    throw new IllegalStateException("Export driver returned null probe result: " + driver.id());
                }
                String subject = driver.id() + "->" + device.busId() + ":" + device.id();
                context.trace(
                        ExportTracepoint.DRIVER_PROBE,
                        subject,
                        probe.status().name().toLowerCase(),
                        System.currentTimeMillis() - startedAt);
                if (!probe.supported()) {
                    continue;
                }
                long bindStartedAt = System.currentTimeMillis();
                driver.bind(device, context);
                context.trace(ExportTracepoint.DRIVER_BIND, subject, "ok", System.currentTimeMillis() - bindStartedAt);
                bound = true;
            }
            if (device.required() && !bound) {
                throw new IllegalStateException(
                        "Required export device has no bound driver: " + device.busId() + ":" + device.id());
            }
        }
    }

    public void exit(ExportKernelContext context) throws Exception {
        Exception failure = null;
        for (int index = modules.size() - 1; index >= 0; index--) {
            ExportModule module = modules.get(index);
            long startedAt = System.currentTimeMillis();
            try {
                module.exit(context);
                context.trace(ExportTracepoint.MODULE_EXIT, module.id(), "ok", System.currentTimeMillis() - startedAt);
            } catch (Exception e) {
                context.trace(ExportTracepoint.MODULE_EXIT, module.id(), "failed", System.currentTimeMillis() - startedAt);
                if (failure == null) {
                    failure = e;
                } else {
                    failure.addSuppressed(e);
                }
            }
        }
        context.closeResources();
        if (failure != null) {
            throw failure;
        }
    }

    public <S extends Enum<S>, A, C> EnumMap<S, A> buildStageActions(
            Class<S> stageType,
            C stageActionContext) {
        EnumMap<S, A> actions = new EnumMap<S, A>(stageType);
        for (ExportModule module : modules) {
            ExportStageActionRegistrar<?, ?> registrar = module.stageActionRegistrar();
            if (registrar != null) {
                Set<S> beforeStages = new LinkedHashSet<S>(actions.keySet());
                register(registrar, actions, stageActionContext);
                validateDeclaredStageActions(stageType, module, beforeStages, actions);
            }
        }
        return actions;
    }

    @SuppressWarnings("unchecked")
    private static <S extends Enum<S>, A, C> void register(
            ExportStageActionRegistrar<?, ?> registrar,
            EnumMap<S, A> actions,
            C context) {
        ((ExportStageActionRegistrar<EnumMap<S, A>, C>) registrar).register(actions, context);
    }

    private static <S extends Enum<S>, A> void validateDeclaredStageActions(
            Class<S> stageType,
            ExportModule module,
            Set<S> beforeStages,
            EnumMap<S, A> actions) {
        Set<String> declaredStages = new LinkedHashSet<String>(module.stageIds());
        Set<S> registeredStages = new LinkedHashSet<S>(actions.keySet());
        registeredStages.removeAll(beforeStages);

        for (S stage : registeredStages) {
            if (!declaredStages.contains(stage.name())) {
                throw new IllegalStateException(
                        "Export module " + module.id() + " registered undeclared export stage: " + stage.name());
            }
        }

        for (String stageId : declaredStages) {
            S stage;
            try {
                stage = Enum.valueOf(stageType, stageId);
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException(
                        "Export module " + module.id() + " declares unknown export stage: " + stageId, e);
            }
            if (!registeredStages.contains(stage)) {
                throw new IllegalStateException(
                        "Export module " + module.id() + " declared export stage without registering action: " + stageId);
            }
        }
    }

    public void writeTrace(ExportKernelContext context) {
        try {
            File validationDir = new File(context.exportContext().paths.repositoryDirectory, "raw-export" + File.separator + "validation");
            if (!validationDir.exists()) {
                validationDir.mkdirs();
            }
            File traceFile = new File(validationDir, "export_kernel_trace.json");
            TraceReport report = new TraceReport();
            report.schemaVersion = "nesqlpp/export-kernel-trace/v1";
            report.profile = context.exportContext().profile.profileId;
            report.selection = context.exportContext().selection.describe();
            report.tracepoints = ExportTracepoint.all();
            report.modules = catalog.descriptors();
            report.events = context.traceEvents();
            try (FileOutputStream fos = new FileOutputStream(traceFile);
                 OutputStreamWriter writer = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
                new GsonBuilder().setPrettyPrinting().create().toJson(report, writer);
            }
        } catch (Exception ignored) {
            // Trace output is diagnostic-only and must not mask the export result.
        }
    }

    private static final class TraceReport {
        String schemaVersion;
        String profile;
        String selection;
        List<String> tracepoints;
        List<ExportModuleCatalog.ModuleDescriptor> modules;
        List<ExportTraceEvent> events;
    }
}
