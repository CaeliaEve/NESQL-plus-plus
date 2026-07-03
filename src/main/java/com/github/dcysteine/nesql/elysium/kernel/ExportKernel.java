package com.github.dcysteine.nesql.elysium.kernel;

import java.util.ArrayList;
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
            List<String> rejectedProbes = new ArrayList<String>();
            for (ExportDriver driver : catalog.drivers()) {
                if (!device.busId().equals(driver.busId())) {
                    continue;
                }
                long startedAt = System.currentTimeMillis();
                DriverProbeResult probe = probeDriver(driver, device, context);
                if (probe == null) {
                    throw new IllegalStateException("Export driver returned null probe result: " + driver.id());
                }
                String subject = driver.id() + "->" + device.busId() + ":" + device.id();
                context.trace(
                        ExportTracepoint.DRIVER_PROBE,
                        subject,
                        probe.status().name().toLowerCase(),
                        System.currentTimeMillis() - startedAt);
                if (probe.failed()) {
                    rejectedProbes.add(describeProbeRejection(driver, probe));
                    if (device.required()) {
                        throw probeFailure(device, driver, probe);
                    }
                    continue;
                }
                if (!probe.supported()) {
                    rejectedProbes.add(describeProbeRejection(driver, probe));
                    continue;
                }
                List<String> missingCapabilities = missingRequiredCapabilities(device, driver, probe);
                if (!missingCapabilities.isEmpty()) {
                    rejectedProbes.add(driver.id()
                            + " missing required capabilities "
                            + missingCapabilities
                            + " after supported probe");
                    context.trace(
                            ExportTracepoint.DRIVER_PROBE,
                            subject,
                            "capability-missing",
                            0L);
                    continue;
                }
                long bindStartedAt = System.currentTimeMillis();
                try {
                    driver.bind(device, context);
                    context.trace(
                            ExportTracepoint.DRIVER_BIND,
                            subject,
                            "ok",
                            System.currentTimeMillis() - bindStartedAt);
                } catch (Exception e) {
                    context.trace(
                            ExportTracepoint.DRIVER_BIND,
                            subject,
                            "failed",
                            System.currentTimeMillis() - bindStartedAt);
                    throw e;
                }
                bound = true;
            }
            if (device.required() && !bound) {
                throw new IllegalStateException(
                        "Required export device has no bound driver: "
                                + device.busId()
                                + ":"
                                + device.id()
                                + "; rejected probes="
                                + rejectedProbes);
            }
        }
    }

    private static DriverProbeResult probeDriver(
            ExportDriver driver,
            ExportDevice device,
            ExportKernelContext context) {
        try {
            return driver.probe(device, context);
        } catch (RuntimeException e) {
            return DriverProbeResult.failed("driver probe threw before bind", e);
        }
    }

    private static IllegalStateException probeFailure(
            ExportDevice device,
            ExportDriver driver,
            DriverProbeResult probe) {
        String message = "Required export device probe failed: "
                + device.busId()
                + ":"
                + device.id()
                + " via "
                + driver.id()
                + " - "
                + probe.reason();
        Throwable cause = probe.cause();
        return cause == null ? new IllegalStateException(message) : new IllegalStateException(message, cause);
    }

    private static String describeProbeRejection(ExportDriver driver, DriverProbeResult probe) {
        return driver.id()
                + "="
                + probe.status().name().toLowerCase()
                + (probe.reason().isEmpty() ? "" : "(" + probe.reason() + ")");
    }

    private static List<String> missingRequiredCapabilities(
            ExportDevice device,
            ExportDriver driver,
            DriverProbeResult probe) {
        Set<String> available = new LinkedHashSet<String>();
        available.addAll(driver.capabilities());
        available.addAll(probe.capabilities());
        List<String> missing = new ArrayList<String>();
        for (String requiredCapability : device.capabilities()) {
            if (!available.contains(requiredCapability)) {
                missing.add(requiredCapability);
            }
        }
        return missing;
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

}
