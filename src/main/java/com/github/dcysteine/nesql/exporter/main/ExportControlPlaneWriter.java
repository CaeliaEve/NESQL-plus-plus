package com.github.dcysteine.nesql.exporter.main;

import com.github.dcysteine.nesql.elysium.kernel.ExportDevice;
import com.github.dcysteine.nesql.elysium.kernel.ExportDriver;
import com.github.dcysteine.nesql.elysium.kernel.ExportModuleCatalog;
import com.github.dcysteine.nesql.elysium.kernel.ExportTracepoint;
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
    private static final String SCHEMA_ROOT = "nesqlpp/export-control-plane/v1";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private ExportControlPlaneWriter() {}

    static void write(ExportContext exportContext, ExportModuleCatalog catalog) {
        try {
            File controlDir = controlDirectory(exportContext);
            ensureDirectory(controlDir);
            writeJson(new File(controlDir, "index.json"), indexReport(exportContext));
            writeJson(new File(controlDir, "abi.json"), abiReport(exportContext));
            writeJson(new File(controlDir, "capabilities.json"), capabilitiesReport(catalog));
            writeJson(new File(controlDir, "modules.json"), modulesReport(catalog));
            writeJson(new File(controlDir, "drivers.json"), driversReport(catalog));
            writeJson(new File(controlDir, "health.json"), healthReport(exportContext));
            writeJson(new File(controlDir, "version.json"), versionReport(exportContext));
        } catch (Exception e) {
            Logger.MOD.warn("Failed to write NESQL++ export control plane", e);
        }
    }

    private static ControlIndex indexReport(ExportContext exportContext) {
        ControlIndex report = new ControlIndex();
        report.schemaVersion = SCHEMA_ROOT + "/index";
        report.repository = exportContext.paths.repositoryName;
        report.profile = exportContext.profile.profileId;
        report.selection = exportContext.selection.describe();
        report.stability = "stable";
        report.files.put("abi", "control/abi.json");
        report.files.put("capabilities", "control/capabilities.json");
        report.files.put("modules", "control/modules.json");
        report.files.put("drivers", "control/drivers.json");
        report.files.put("health", "control/health.json");
        report.files.put("version", "control/version.json");
        return report;
    }

    private static AbiReport abiReport(ExportContext exportContext) {
        AbiReport report = new AbiReport();
        report.schemaVersion = SCHEMA_ROOT + "/abi";
        report.repository = exportContext.paths.repositoryName;
        report.profile = exportContext.profile.profileId;
        report.selection = exportContext.selection.describe();
        report.rawExportAbi = "nesqlpp/raw-export/alpha1";
        report.kernelTraceDebugSchema = "nesqlpp/export-debug-kernel-trace/v1";
        report.stageTimingDebugSchema = "nesqlpp/export-debug-stage-timing/v1";
        report.stageCheckpointDebugSchema = "nesqlpp/export-debug-stage-checkpoint/v1";
        report.tracepoints = ExportTracepoint.all();
        return report;
    }

    private static CapabilitiesReport capabilitiesReport(ExportModuleCatalog catalog) {
        CapabilitiesReport report = new CapabilitiesReport();
        report.schemaVersion = SCHEMA_ROOT + "/capabilities";
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
        report.schemaVersion = SCHEMA_ROOT + "/modules";
        report.modules = catalog.descriptors();
        return report;
    }

    private static DriversReport driversReport(ExportModuleCatalog catalog) {
        DriversReport report = new DriversReport();
        report.schemaVersion = SCHEMA_ROOT + "/drivers";
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
        report.schemaVersion = SCHEMA_ROOT + "/health";
        report.repository = exportContext.paths.repositoryName;
        report.profile = exportContext.profile.profileId;
        report.selection = exportContext.selection.describe();
        report.status = "control-plane-ready";
        return report;
    }

    private static VersionReport versionReport(ExportContext exportContext) {
        VersionReport report = new VersionReport();
        report.schemaVersion = SCHEMA_ROOT + "/version";
        report.repository = exportContext.paths.repositoryName;
        report.exporter = Main.MOD_NAME;
        report.profile = exportContext.profile.profileId;
        report.generatedAtEpochMs = System.currentTimeMillis();
        return report;
    }

    private static File controlDirectory(ExportContext exportContext) {
        return new File(exportContext.paths.repositoryDirectory, "raw-export" + File.separator + "control");
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
