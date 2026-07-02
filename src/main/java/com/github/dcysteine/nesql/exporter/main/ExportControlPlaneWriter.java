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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Writes stable ControlFS-style export descriptors for downstream tooling. */
final class ExportControlPlaneWriter {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private ExportControlPlaneWriter() {}

    static void write(ExportContext exportContext, ExportModuleCatalog catalog) {
        try {
            File controlDir = controlDirectory(exportContext);
            ensureDirectory(controlDir);
            writeJson(controlFile(controlDir, ExportControlFile.INDEX), indexReport(exportContext));
            writeJson(controlFile(controlDir, ExportControlFile.ABI), abiReport(exportContext));
            writeJson(controlFile(controlDir, ExportControlFile.CAPABILITIES), capabilitiesReport(catalog));
            writeJson(controlFile(controlDir, ExportControlFile.MODULES), modulesReport(catalog));
            writeJson(controlFile(controlDir, ExportControlFile.DRIVERS), driversReport(catalog));
            writeJson(controlFile(controlDir, ExportControlFile.HEALTH), healthReport(exportContext));
            writeJson(controlFile(controlDir, ExportControlFile.VERSION), versionReport(exportContext));
        } catch (Exception e) {
            Logger.MOD.warn("Failed to write NESQL++ export control plane", e);
        }
    }

    private static ControlIndex indexReport(ExportContext exportContext) {
        ControlIndex report = new ControlIndex();
        report.schemaVersion = ExportControlFile.INDEX.schemaVersion();
        report.repository = exportContext.paths.repositoryName;
        report.profile = exportContext.profile.profileId;
        report.selection = exportContext.selection.describe();
        report.stability = "stable";
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
        report.kernelTraceDebugSchema = ExportDebugFile.KERNEL_TRACE.schemaVersion();
        report.stageTimingDebugSchema = ExportDebugFile.STAGE_TIMING.schemaVersion();
        report.stageCheckpointDebugSchema = ExportDebugFile.STAGE_CHECKPOINT.schemaVersion();
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
        report.capabilities.addAll(capabilities);
        report.moduleCount = catalog.descriptors().size();
        report.deviceCount = catalog.devices().size();
        report.driverCount = catalog.drivers().size();
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
        if (directory != null && !directory.exists() && !directory.mkdirs()) {
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
        String kernelTraceDebugSchema;
        String stageTimingDebugSchema;
        String stageCheckpointDebugSchema;
        List<String> tracepoints = Collections.emptyList();
    }

    private static final class CapabilitiesReport {
        String schemaVersion;
        int moduleCount;
        int deviceCount;
        int driverCount;
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
