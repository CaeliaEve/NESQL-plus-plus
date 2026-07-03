package com.github.dcysteine.nesql.exporter.main;

import com.github.dcysteine.nesql.elysium.kernel.ExportDevice;
import com.github.dcysteine.nesql.elysium.kernel.ExportControlFile;
import com.github.dcysteine.nesql.elysium.kernel.ExportDebugFile;
import com.github.dcysteine.nesql.elysium.kernel.ExportDriver;
import com.github.dcysteine.nesql.elysium.kernel.ExportModuleCatalog;
import com.github.dcysteine.nesql.elysium.kernel.ExportSchemaCatalog;
import com.github.dcysteine.nesql.elysium.kernel.ExportTracepoint;
import com.github.dcysteine.nesql.exporter.local.RawExportFileCatalog;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Writes stable ControlFS-style export descriptors for downstream tooling. */
final class ExportControlPlaneWriter {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final List<ControlReportDescriptor> CONTROL_REPORTS = validateAndFreeze(Arrays.asList(
            new ControlReportDescriptor(ExportControlFile.INDEX, (exportContext, catalog) ->
                    indexReport(exportContext)),
            new ControlReportDescriptor(ExportControlFile.ABI, (exportContext, catalog) ->
                    abiReport(exportContext)),
            new ControlReportDescriptor(ExportControlFile.CAPABILITIES, (exportContext, catalog) ->
                    capabilitiesReport(catalog)),
            new ControlReportDescriptor(ExportControlFile.MODULES, (exportContext, catalog) ->
                    modulesReport(catalog)),
            new ControlReportDescriptor(ExportControlFile.DRIVERS, (exportContext, catalog) ->
                    driversReport(catalog)),
            new ControlReportDescriptor(ExportControlFile.VALIDATION_PROBES, (exportContext, catalog) ->
                    validationProbesReport()),
            new ControlReportDescriptor(ExportControlFile.HEALTH, (exportContext, catalog) ->
                    healthReport(exportContext)),
            new ControlReportDescriptor(ExportControlFile.VERSION, (exportContext, catalog) ->
                    versionReport(exportContext))));

    private ExportControlPlaneWriter() {}

    static void write(ExportContext exportContext, ExportModuleCatalog catalog) throws Exception {
        File controlDir = controlDirectory(exportContext);
        ensureDirectory(controlDir);
        for (ControlReportDescriptor descriptor : CONTROL_REPORTS) {
            writeJson(controlFile(controlDir, descriptor.file()), descriptor.build(exportContext, catalog));
        }
    }

    private static ControlIndex indexReport(ExportContext exportContext) {
        ControlIndex report = new ControlIndex();
        report.schemaVersion = ExportControlFile.INDEX.schemaVersion();
        report.repository = exportContext.paths.repositoryName;
        report.profile = exportContext.profile.profileId;
        report.selection = exportContext.selection.describe();
        report.stability = ExportControlFile.STABILITY_STABLE;
        report.files.putAll(ExportControlFile.indexedFiles());
        return report;
    }

    private static AbiReport abiReport(ExportContext exportContext) {
        AbiReport report = new AbiReport();
        report.schemaVersion = ExportControlFile.ABI.schemaVersion();
        report.repository = exportContext.paths.repositoryName;
        report.profile = exportContext.profile.profileId;
        report.selection = exportContext.selection.describe();
        report.rawExportAbi = ExportSchemaCatalog.RAW_EXPORT_ABI;
        report.exportValidationSchema = ExportSchemaCatalog.EXPORT_VALIDATION;
        report.validationProbeControlSchema = ExportControlFile.VALIDATION_PROBES.schemaVersion();
        report.kernelTraceDebugSchema = ExportDebugFile.KERNEL_TRACE.schemaVersion();
        report.stageTimingDebugSchema = ExportDebugFile.STAGE_TIMING.schemaVersion();
        report.stageCheckpointDebugSchema = ExportDebugFile.STAGE_CHECKPOINT.schemaVersion();
        report.controlSchemas = ExportSchemaCatalog.controlSchemas();
        report.tracepoints = ExportTracepoint.all();
        return report;
    }

    private static CapabilitiesReport capabilitiesReport(ExportModuleCatalog catalog) {
        CapabilitiesReport report = new CapabilitiesReport();
        report.schemaVersion = ExportControlFile.CAPABILITIES.schemaVersion();
        Set<String> capabilities = new LinkedHashSet<String>();
        for (ExportModuleCatalog.ModuleDescriptor module : catalog.descriptors()) {
            capabilities.addAll(module.capabilities);
        }
        for (ExportValidationProbeDescriptor probe : ExportValidationProbeCatalog.descriptors()) {
            capabilities.addAll(probe.capabilities);
        }
        report.capabilities.addAll(capabilities);
        report.moduleCount = catalog.descriptors().size();
        report.deviceCount = catalog.devices().size();
        report.driverCount = catalog.drivers().size();
        report.validationProbeCount = ExportValidationProbeCatalog.descriptors().size();
        return report;
    }

    private static ModulesReport modulesReport(ExportModuleCatalog catalog) {
        ModulesReport report = new ModulesReport();
        report.schemaVersion = ExportControlFile.MODULES.schemaVersion();
        report.modules = catalog.descriptors();
        return report;
    }

    private static DriversReport driversReport(ExportModuleCatalog catalog) {
        DriversReport report = new DriversReport();
        report.schemaVersion = ExportControlFile.DRIVERS.schemaVersion();
        for (ExportDevice device : catalog.devices()) {
            report.devices.add(new DeviceRecord(device));
        }
        for (ExportDriver driver : catalog.drivers()) {
            report.drivers.add(new DriverRecord(driver));
        }
        return report;
    }

    private static ValidationProbesReport validationProbesReport() {
        ValidationProbesReport report = new ValidationProbesReport();
        report.schemaVersion = ExportControlFile.VALIDATION_PROBES.schemaVersion();
        report.policy = ExportControlFile.VALIDATION_PROBE_POLICY;
        report.probes = ExportValidationProbeCatalog.descriptors();
        report.probeCount = report.probes.size();
        return report;
    }

    private static HealthReport healthReport(ExportContext exportContext) {
        HealthReport report = new HealthReport();
        report.schemaVersion = ExportControlFile.HEALTH.schemaVersion();
        report.repository = exportContext.paths.repositoryName;
        report.profile = exportContext.profile.profileId;
        report.selection = exportContext.selection.describe();
        report.status = "control-plane-ready";
        return report;
    }

    private static VersionReport versionReport(ExportContext exportContext) {
        VersionReport report = new VersionReport();
        report.schemaVersion = ExportControlFile.VERSION.schemaVersion();
        report.repository = exportContext.paths.repositoryName;
        report.exporter = Main.MOD_NAME;
        report.profile = exportContext.profile.profileId;
        report.generatedAtEpochMs = System.currentTimeMillis();
        return report;
    }

    private static File controlDirectory(ExportContext exportContext) {
        return new File(
                RawExportFileCatalog.rawExportDirectory(exportContext.paths.repositoryDirectory),
                RawExportFileCatalog.CONTROL_DIRECTORY);
    }

    private static File controlFile(File controlDir, ExportControlFile file) {
        return new File(controlDir, file.fileName());
    }

    private static void ensureDirectory(File directory) throws Exception {
        if (directory == null) {
            throw new java.io.IOException("ControlFS directory must not be null");
        }
        if (directory.exists()) {
            if (!directory.isDirectory()) {
                throw new java.io.IOException("ControlFS path exists but is not a directory: " + directory.getAbsolutePath());
            }
            return;
        }
        if (!directory.mkdirs()) {
            throw new java.io.IOException("Failed to create directory: " + directory.getAbsolutePath());
        }
    }

    private static void writeJson(File file, Object value) throws Exception {
        ensureDirectory(file.getParentFile());
        try (FileOutputStream fos = new FileOutputStream(file);
             OutputStreamWriter writer = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
            GSON.toJson(value, writer);
        }
    }

    private static List<ControlReportDescriptor> validateAndFreeze(List<ControlReportDescriptor> descriptors) {
        EnumSet<ExportControlFile> seen = EnumSet.noneOf(ExportControlFile.class);
        for (ControlReportDescriptor descriptor : descriptors) {
            if (descriptor == null) {
                throw new IllegalStateException("ControlFS report descriptor must not be null");
            }
            if (!seen.add(descriptor.file())) {
                throw new IllegalStateException("Duplicate ControlFS report descriptor: " + descriptor.file().name());
            }
        }
        for (ExportControlFile file : ExportControlFile.values()) {
            if (!seen.contains(file)) {
                throw new IllegalStateException("Missing ControlFS report descriptor: " + file.name());
            }
        }
        return Collections.unmodifiableList(new ArrayList<ControlReportDescriptor>(descriptors));
    }

    private interface ControlReportFactory {
        Object build(ExportContext exportContext, ExportModuleCatalog catalog);
    }

    private static final class ControlReportDescriptor {
        private final ExportControlFile file;
        private final ControlReportFactory factory;

        private ControlReportDescriptor(ExportControlFile file, ControlReportFactory factory) {
            if (file == null) {
                throw new IllegalArgumentException("ControlFS report file must be non-null");
            }
            if (factory == null) {
                throw new IllegalArgumentException("ControlFS report factory must be non-null: " + file.name());
            }
            this.file = file;
            this.factory = factory;
        }

        private ExportControlFile file() {
            return file;
        }

        private Object build(ExportContext exportContext, ExportModuleCatalog catalog) {
            return factory.build(exportContext, catalog);
        }
    }

    private static final class ControlIndex {
        String schemaVersion;
        String repository;
        String profile;
        String selection;
        String stability;
        Map<String, String> files = new LinkedHashMap<String, String>();
    }

    private static final class AbiReport {
        String schemaVersion;
        String repository;
        String profile;
        String selection;
        String rawExportAbi;
        String exportValidationSchema;
        String validationProbeControlSchema;
        String kernelTraceDebugSchema;
        String stageTimingDebugSchema;
        String stageCheckpointDebugSchema;
        List<String> controlSchemas = Collections.emptyList();
        List<String> tracepoints = Collections.emptyList();
    }

    private static final class CapabilitiesReport {
        String schemaVersion;
        int moduleCount;
        int deviceCount;
        int driverCount;
        int validationProbeCount;
        List<String> capabilities = new ArrayList<String>();
    }

    private static final class ModulesReport {
        String schemaVersion;
        List<ExportModuleCatalog.ModuleDescriptor> modules;
    }

    private static final class DriversReport {
        String schemaVersion;
        List<DeviceRecord> devices = new ArrayList<DeviceRecord>();
        List<DriverRecord> drivers = new ArrayList<DriverRecord>();
    }

    private static final class ValidationProbesReport {
        String schemaVersion;
        String policy;
        int probeCount;
        List<ExportValidationProbeDescriptor> probes;
    }

    private static final class DeviceRecord {
        String busId;
        String id;
        boolean required;
        List<String> capabilities;

        DeviceRecord(ExportDevice device) {
            this.busId = device.busId();
            this.id = device.id();
            this.required = device.required();
            this.capabilities = device.capabilities();
        }
    }

    private static final class DriverRecord {
        String busId;
        String id;
        List<String> capabilities;

        DriverRecord(ExportDriver driver) {
            this.busId = driver.busId();
            this.id = driver.id();
            this.capabilities = driver.capabilities();
        }
    }

    private static final class HealthReport {
        String schemaVersion;
        String repository;
        String profile;
        String selection;
        String status;
    }

    private static final class VersionReport {
        String schemaVersion;
        String repository;
        String exporter;
        String profile;
        long generatedAtEpochMs;
    }
}
