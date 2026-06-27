package com.github.dcysteine.nesql.elysium.kernel;

import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;

public final class ExportKernel {
    private final List<ExportModule> modules;

    public ExportKernel(List<ExportModule> modules) {
        this.modules = new ArrayList<ExportModule>(modules);
        this.modules.sort(Comparator.comparing(ExportModule::level).thenComparing(ExportModule::id));
    }

    public void init(ExportKernelContext context) throws Exception {
        for (ExportModule module : modules) {
            long startedAt = System.currentTimeMillis();
            module.init(context);
            context.trace("export.module.init", module.id(), "ok", System.currentTimeMillis() - startedAt);
        }
    }

    public void exit(ExportKernelContext context) throws Exception {
        Exception failure = null;
        for (int index = modules.size() - 1; index >= 0; index--) {
            ExportModule module = modules.get(index);
            long startedAt = System.currentTimeMillis();
            try {
                module.exit(context);
                context.trace("export.module.exit", module.id(), "ok", System.currentTimeMillis() - startedAt);
            } catch (Exception e) {
                context.trace("export.module.exit", module.id(), "failed", System.currentTimeMillis() - startedAt);
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
                register(registrar, actions, stageActionContext);
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
        List<ExportTraceEvent> events;
    }
}
