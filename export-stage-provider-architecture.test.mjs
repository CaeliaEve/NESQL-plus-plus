import assert from 'node:assert/strict';
import { existsSync, readFileSync } from 'node:fs';
import test from 'node:test';

const registryUrl = new URL('./src/main/java/com/github/dcysteine/nesql/exporter/main/ExportStageActionRegistry.java', import.meta.url);
const kernel = readFileSync(
  new URL('./src/main/java/com/github/dcysteine/nesql/elysium/kernel/ExportKernel.java', import.meta.url),
  'utf8',
);
const catalog = readFileSync(
  new URL('./src/main/java/com/github/dcysteine/nesql/elysium/kernel/ExportModuleCatalog.java', import.meta.url),
  'utf8',
);
const modules = readFileSync(
  new URL('./src/main/java/com/github/dcysteine/nesql/exporter/main/ExportStageModules.java', import.meta.url),
  'utf8',
);
const moduleInterface = readFileSync(
  new URL('./src/main/java/com/github/dcysteine/nesql/elysium/kernel/ExportModule.java', import.meta.url),
  'utf8',
);
const exportDevice = readFileSync(
  new URL('./src/main/java/com/github/dcysteine/nesql/elysium/kernel/ExportDevice.java', import.meta.url),
  'utf8',
);
const exportDriver = readFileSync(
  new URL('./src/main/java/com/github/dcysteine/nesql/elysium/kernel/ExportDriver.java', import.meta.url),
  'utf8',
);
const driverProbeResult = readFileSync(
  new URL('./src/main/java/com/github/dcysteine/nesql/elysium/kernel/DriverProbeResult.java', import.meta.url),
  'utf8',
);
const tracepointCatalog = readFileSync(
  new URL('./src/main/java/com/github/dcysteine/nesql/elysium/kernel/ExportTracepoint.java', import.meta.url),
  'utf8',
);
const schemaCatalog = readFileSync(
  new URL('./src/main/java/com/github/dcysteine/nesql/elysium/kernel/ExportSchemaCatalog.java', import.meta.url),
  'utf8',
);
const controlFileCatalog = readFileSync(
  new URL('./src/main/java/com/github/dcysteine/nesql/elysium/kernel/ExportControlFile.java', import.meta.url),
  'utf8',
);
const debugFileCatalog = readFileSync(
  new URL('./src/main/java/com/github/dcysteine/nesql/elysium/kernel/ExportDebugFile.java', import.meta.url),
  'utf8',
);
const resourceManager = readFileSync(
  new URL('./src/main/java/com/github/dcysteine/nesql/elysium/kernel/ExportResourceManager.java', import.meta.url),
  'utf8',
);
const kernelContext = readFileSync(
  new URL('./src/main/java/com/github/dcysteine/nesql/elysium/kernel/ExportKernelContext.java', import.meta.url),
  'utf8',
);
const actionContext = readFileSync(
  new URL('./src/main/java/com/github/dcysteine/nesql/exporter/main/ExportStageActionContext.java', import.meta.url),
  'utf8',
);
const controlPlaneWriter = readFileSync(
  new URL('./src/main/java/com/github/dcysteine/nesql/exporter/main/ExportControlPlaneWriter.java', import.meta.url),
  'utf8',
);
const debugPlaneWriter = readFileSync(
  new URL('./src/main/java/com/github/dcysteine/nesql/exporter/main/ExportDebugPlaneWriter.java', import.meta.url),
  'utf8',
);
const rawManifestBuilder = readFileSync(
  new URL('./src/main/java/com/github/dcysteine/nesql/exporter/local/RawExportManifestBuilder.java', import.meta.url),
  'utf8',
);
const rawFileCatalog = readFileSync(
  new URL('./src/main/java/com/github/dcysteine/nesql/exporter/local/RawExportFileCatalog.java', import.meta.url),
  'utf8',
);
const integrityManifestWriter = readFileSync(
  new URL('./src/main/java/com/github/dcysteine/nesql/exporter/main/ExportIntegrityManifestWriter.java', import.meta.url),
  'utf8',
);
const validationReportWriter = readFileSync(
  new URL('./src/main/java/com/github/dcysteine/nesql/exporter/main/ExportValidationReportWriter.java', import.meta.url),
  'utf8',
);
const validationHealthPolicy = readFileSync(
  new URL('./src/main/java/com/github/dcysteine/nesql/exporter/main/ExportValidationHealthPolicy.java', import.meta.url),
  'utf8',
);
const validationReportStore = readFileSync(
  new URL('./src/main/java/com/github/dcysteine/nesql/exporter/main/ExportValidationReportStore.java', import.meta.url),
  'utf8',
);
const validationAbiCatalog = readFileSync(
  new URL('./src/main/java/com/github/dcysteine/nesql/exporter/main/ExportValidationAbiCatalog.java', import.meta.url),
  'utf8',
);
const validationEvidenceCatalog = readFileSync(
  new URL('./src/main/java/com/github/dcysteine/nesql/exporter/main/ExportValidationEvidenceCatalog.java', import.meta.url),
  'utf8',
);

const validationJsonSupport = readFileSync(
  new URL('./src/main/java/com/github/dcysteine/nesql/exporter/main/ExportValidationJsonSupport.java', import.meta.url),
  'utf8',
);
const validationRawCountProbe = readFileSync(
  new URL('./src/main/java/com/github/dcysteine/nesql/exporter/main/ExportValidationRawCountProbe.java', import.meta.url),
  'utf8',
);
const validationHealthSectionBuilder = readFileSync(
  new URL('./src/main/java/com/github/dcysteine/nesql/exporter/main/ExportValidationHealthSectionBuilder.java', import.meta.url),
  'utf8',
);
const validationSemanticProbe = readFileSync(
  new URL('./src/main/java/com/github/dcysteine/nesql/exporter/main/ExportValidationSemanticProbe.java', import.meta.url),
  'utf8',
);
const validationPathHygieneProbe = readFileSync(
  new URL('./src/main/java/com/github/dcysteine/nesql/exporter/main/ExportValidationPathHygieneProbe.java', import.meta.url),
  'utf8',
);
const validationBrowserAtlasProbe = readFileSync(
  new URL('./src/main/java/com/github/dcysteine/nesql/exporter/main/ExportValidationBrowserAtlasProbe.java', import.meta.url),
  'utf8',
);
const validationRenderAssetProbe = readFileSync(
  new URL('./src/main/java/com/github/dcysteine/nesql/exporter/main/ExportValidationRenderAssetProbe.java', import.meta.url),
  'utf8',
);
const validationProbeInterface = readFileSync(
  new URL('./src/main/java/com/github/dcysteine/nesql/exporter/main/ExportValidationProbe.java', import.meta.url),
  'utf8',
);
const validationProbeContext = readFileSync(
  new URL('./src/main/java/com/github/dcysteine/nesql/exporter/main/ExportValidationProbeContext.java', import.meta.url),
  'utf8',
);
const validationProbeCatalog = readFileSync(
  new URL('./src/main/java/com/github/dcysteine/nesql/exporter/main/ExportValidationProbeCatalog.java', import.meta.url),
  'utf8',
);
const validationProbeDescriptor = readFileSync(
  new URL('./src/main/java/com/github/dcysteine/nesql/exporter/main/ExportValidationProbeDescriptor.java', import.meta.url),
  'utf8',
);
const validationRepositoryProbe = readFileSync(
  new URL('./src/main/java/com/github/dcysteine/nesql/exporter/main/ExportValidationRepositoryProbe.java', import.meta.url),
  'utf8',
);
const validationBrowserProbe = readFileSync(
  new URL('./src/main/java/com/github/dcysteine/nesql/exporter/main/ExportValidationBrowserValidationProbe.java', import.meta.url),
  'utf8',
);
const validationPreviousDeltaProbe = readFileSync(
  new URL('./src/main/java/com/github/dcysteine/nesql/exporter/main/ExportValidationPreviousDeltaProbe.java', import.meta.url),
  'utf8',
);
const validationHealthPolicyProbe = readFileSync(
  new URL('./src/main/java/com/github/dcysteine/nesql/exporter/main/ExportValidationHealthPolicyProbe.java', import.meta.url),
  'utf8',
);
const validationHealthSectionProbe = readFileSync(
  new URL('./src/main/java/com/github/dcysteine/nesql/exporter/main/ExportValidationHealthSectionProbe.java', import.meta.url),
  'utf8',
);
const providerInterface = readFileSync(
  new URL('./src/main/java/com/github/dcysteine/nesql/exporter/main/ExportStageActionProvider.java', import.meta.url),
  'utf8',
);
const actionModule = readFileSync(
  new URL('./src/main/java/com/github/dcysteine/nesql/exporter/main/ExportStageActionModule.java', import.meta.url),
  'utf8',
);
const providerSources = Object.fromEntries(
  [
    'LifecycleStageActionProvider',
    'RawFactStageActionProvider',
    'RenderStageActionProvider',
    'NativeUiStageActionProvider',
  ].map((provider) => [
    provider,
    readFileSync(
      new URL(`./src/main/java/com/github/dcysteine/nesql/exporter/main/${provider}.java`, import.meta.url),
      'utf8',
    ),
  ]),
);
const runner = readFileSync(
  new URL('./src/main/java/com/github/dcysteine/nesql/exporter/main/ExportStageRunner.java', import.meta.url),
  'utf8',
);

test('export stage modules own subsystem providers', () => {
  for (const provider of [
    'LifecycleStageActionProvider',
    'RawFactStageActionProvider',
    'RenderStageActionProvider',
    'NativeUiStageActionProvider',
  ]) {
    assert.match(modules, new RegExp(`new ${provider}\\(\\)`));
  }
  assert.match(modules, /new ExportStageActionModule/);
  assert.match(modules, /static ExportModuleCatalog defaultCatalog\(\)/);
  assert.match(modules, /ExportModuleCatalog\.builder\(\)/);
  assert.doesNotMatch(modules, /new ArrayList/);
  assert.doesNotMatch(modules, /Collections\.unmodifiableList/);
});

test('export kernel directly dispatches stage registrars', () => {
  assert.equal(existsSync(registryUrl), false, 'ExportStageActionRegistry must stay deleted');
  assert.match(kernel, /buildStageActions/);
  assert.match(kernel, /module\.stageActionRegistrar\(\)/);
  assert.match(kernel, /ExportModuleCatalog catalog/);
  assert.match(kernel, /this\.modules = catalog\.modules\(\)/);
  assert.match(debugPlaneWriter, /report\.modules = catalog\.descriptors\(\)/);
  assert.match(kernel, /validateDeclaredStageActions/);
  assert.match(kernel, /registered undeclared export stage/);
  assert.match(kernel, /declared export stage without registering action/);
  assert.doesNotMatch(kernel, /Comparator\.comparing/);
  assert.doesNotMatch(kernel, /new LifecycleStageActionProvider/);
  assert.doesNotMatch(kernel, /ExportWriterSupport\./);
  assert.doesNotMatch(kernel, /RenderLifecycleSupport\./);
});

test('export module catalog owns module ordering, identity validation, and trace descriptors', () => {
  assert.match(catalog, /public final class ExportModuleCatalog/);
  assert.match(catalog, /sortedModules\.sort\(Comparator\.comparing\(ExportModule::level\)\.thenComparing\(ExportModule::id\)\)/);
  assert.match(catalog, /validateModules\(sortedModules\)/);
  assert.match(catalog, /Duplicate export module id/);
  assert.match(catalog, /Export module id must be non-empty/);
  assert.match(catalog, /Export module level must be non-null/);
  assert.match(catalog, /public List<ModuleDescriptor> descriptors\(\)/);
  assert.match(catalog, /public final boolean stageActionRegistrar/);
  assert.match(catalog, /public final List<String> capabilities/);
  assert.match(catalog, /public final List<String> stages/);
  assert.match(catalog, /public List<ExportDevice> devices\(\)/);
  assert.match(catalog, /public List<ExportDriver> drivers\(\)/);
  assert.match(catalog, /validateDevices\(this\.devices\)/);
  assert.match(catalog, /validateDrivers\(this\.drivers\)/);
  assert.match(catalog, /Duplicate export device id/);
  assert.match(catalog, /Duplicate export driver id/);
  assert.match(catalog, /public final List<DeviceDescriptor> devices/);
  assert.match(catalog, /public final List<DriverDescriptor> drivers/);
  assert.match(runner, /ExportModuleCatalog moduleCatalog = ExportStageModules\.defaultCatalog\(\)/);
  assert.match(runner, /new ExportKernel\(moduleCatalog\)/);
  assert.doesNotMatch(runner, /List<ExportModule> modules/);
  assert.doesNotMatch(runner, /ExportStageModules\.defaultModules\(\)/);
});

test('stage action modules expose declared stages and capabilities through the module manifest', () => {
  assert.match(moduleInterface, /default List<String> capabilities\(\)/);
  assert.match(moduleInterface, /default List<String> stageIds\(\)/);
  assert.match(moduleInterface, /default List<ExportDevice> devices\(\)/);
  assert.match(moduleInterface, /default List<ExportDriver> drivers\(\)/);
  assert.match(providerInterface, /List<ExportStage> stages\(\)/);
  assert.match(providerInterface, /List<String> capabilities\(\)/);
  assert.match(providerInterface, /static List<ExportStage> stageList/);
  assert.match(providerInterface, /static List<String> capabilityList/);
  assert.match(actionModule, /public List<String> capabilities\(\)/);
  assert.match(actionModule, /provider\.capabilities\(\)/);
  assert.match(actionModule, /public List<String> stageIds\(\)/);
  assert.match(actionModule, /for \(ExportStage stage : provider\.stages\(\)\)/);

  const expectedStages = {
    LifecycleStageActionProvider: [
      'INITIALIZE_REPOSITORY',
      'INITIALIZE_DATABASE',
      'INITIALIZE_PLUGINS',
      'COLLECT_PLUGIN_DATA',
      'COMMIT_DATABASE',
      'ROLLBACK_DATABASE',
      'COMPLETE',
    ],
    RawFactStageActionProvider: [
      'WRITE_UI_FAMILY_CENSUS',
      'WRITE_UI_TEMPLATE_CATALOG',
      'WRITE_MOD_BASED_ITEMS',
      'WRITE_MOD_BASED_RECIPES',
      'WRITE_MULTIBLOCK_BLUEPRINTS',
      'WRITE_BLOCK_FACE_METADATA',
      'WRITE_CANONICAL_SNAPSHOT',
    ],
    RenderStageActionProvider: [
      'RENDER_IMAGES',
      'WRITE_RENDER_ASSET_MANIFEST',
      'WRITE_ANIMATION_MANIFEST',
      'WRITE_ATLAS_PACKS',
      'WRITE_ANIMATED_ATLAS_PACKS',
      'WRITE_ATLAS_REGISTRY',
      'WRITE_RENDER_INDEX',
      'WRITE_BROWSER_ATLAS_INDEX',
    ],
    NativeUiStageActionProvider: [
      'WRITE_BROWSER_LAYOUT_INDEX',
      'WRITE_RAW_EXPORT_SIDECAR',
    ],
  };

  for (const [provider, stages] of Object.entries(expectedStages)) {
    const source = providerSources[provider];
    assert.match(source, /private static final List<ExportStage> STAGES/);
    assert.match(source, /private static final List<String> CAPABILITIES/);
    assert.match(source, /public List<ExportStage> stages\(\)/);
    assert.match(source, /public List<String> capabilities\(\)/);
    for (const stage of stages) {
      assert.equal(source.includes(`ExportStage.${stage}`), true, `${provider} missing declared stage ${stage}`);
    }
  }
  assert.match(providerSources.NativeUiStageActionProvider, /export\.native-ui\.capture/);
  assert.match(providerSources.RawFactStageActionProvider, /export\.raw\.item-facts/);
  assert.match(providerSources.RenderStageActionProvider, /export\.render\.atlas-packs/);
  assert.match(providerSources.LifecycleStageActionProvider, /export\.lifecycle\.transaction/);
});

test('export kernel owns Linux-style bus device driver probe and bind lifecycle', () => {
  assert.match(exportDevice, /public final class ExportDevice/);
  assert.match(exportDevice, /public static ExportDevice required/);
  assert.match(exportDevice, /String busId\(\)/);
  assert.match(exportDevice, /boolean required\(\)/);
  assert.match(exportDriver, /public interface ExportDriver/);
  assert.match(exportDriver, /DriverProbeResult probe\(ExportDevice device, ExportKernelContext context\)/);
  assert.match(exportDriver, /default void bind\(ExportDevice device, ExportKernelContext context\) throws Exception/);
  assert.match(driverProbeResult, /enum Status/);
  assert.match(driverProbeResult, /SUPPORTED/);
  assert.match(driverProbeResult, /UNSUPPORTED/);
  assert.match(driverProbeResult, /DEFERRED/);
  assert.match(kernel, /bindDrivers\(context\)/);
  assert.match(kernel, /for \(ExportDevice device : catalog\.devices\(\)\)/);
  assert.match(kernel, /for \(ExportDriver driver : catalog\.drivers\(\)\)/);
  assert.match(kernel, /driver\.probe\(device, context\)/);
  assert.match(kernel, /ExportTracepoint\.DRIVER_PROBE/);
  assert.match(kernel, /driver\.bind\(device, context\)/);
  assert.match(kernel, /ExportTracepoint\.DRIVER_BIND/);
  assert.match(kernel, /Required export device has no bound driver/);

  assert.match(modules, /ExportDevice\.required/);
  assert.match(modules, /STAGE_ACTION_BUS_ID/);
  assert.match(modules, /STAGE_ACTION_DEVICE_ID/);
  assert.match(actionModule, /implements ExportDriver/);
  assert.match(actionModule, /StageActionProviderDriver/);
  assert.match(actionModule, /public List<ExportDriver> drivers\(\)/);
  assert.match(actionModule, /DriverProbeResult\.supported/);
  assert.match(actionModule, /DriverProbeResult\.unsupported/);
  assert.match(actionModule, /provider\.stages\(\)\.isEmpty\(\)/);
});

test('export lifecycle resources are owned by the kernel managed-resource stack', () => {
  assert.match(resourceManager, /Deque<ManagedResource> resources/);
  assert.match(resourceManager, /resources\.push\(new ManagedResource/);
  assert.match(resourceManager, /while \(!resources\.isEmpty\(\)\)/);
  assert.match(resourceManager, /public interface ReleaseObserver/);
  assert.match(kernelContext, /resources\.close\(new ExportResourceManager\.ReleaseObserver/);
  assert.match(kernelContext, /ExportTracepoint\.RESOURCE_RELEASE/);
  assert.match(actionContext, /final ExportKernelContext kernelContext/);
  assert.match(runner, /new ExportStageActionContext\(exportContext, kernelContext, strategy, stageState\)/);
  assert.doesNotMatch(runner, /ExportLifecycleSupport\.closeSession\(stageState\.session/);
  assert.doesNotMatch(runner, /stageState\.runtime\.close\(\)/);

  const lifecycle = providerSources.LifecycleStageActionProvider;
  assert.match(lifecycle, /context\.kernelContext\.resources\(\)\.add\(\s*"export\.runtime"/);
  assert.match(lifecycle, /context\.kernelContext\.resources\(\)\.add\(\s*"export\.session"/);
  assert.match(lifecycle, /ExportLifecycleSupport\.closeSession/);
});

test('export tracepoints are declared through a stable kernel catalog', () => {
  for (const [constant, event] of [
    ['MODULE_INIT', 'export.module.init'],
    ['MODULE_EXIT', 'export.module.exit'],
    ['DRIVER_PROBE', 'export.driver.probe'],
    ['DRIVER_BIND', 'export.driver.bind'],
    ['STAGE_RUN', 'export.stage.run'],
    ['RESOURCE_RELEASE', 'export.resource.release'],
  ]) {
    assert.match(
      tracepointCatalog,
      new RegExp(`public static final String ${constant} = "${event.replaceAll('.', '\\.')}"`),
    );
    assert.match(tracepointCatalog, new RegExp(constant));
  }
  assert.match(tracepointCatalog, /public static List<String> all\(\)/);
  assert.match(debugPlaneWriter, /report\.tracepoints = ExportTracepoint\.all\(\)/);
  assert.match(runner, /ExportTracepoint\.STAGE_RUN/);
  assert.doesNotMatch(kernel, /trace\("export\./);
  assert.doesNotMatch(runner, /trace\("export\./);
  assert.doesNotMatch(kernelContext, /trace\("export\./);
});

test('export control and debug planes have explicit filesystem ownership', () => {
  assert.match(schemaCatalog, /public final class ExportSchemaCatalog/);
  assert.match(schemaCatalog, /RAW_EXPORT_ABI = "nesqlpp\/raw-export\/alpha1"/);
  assert.match(schemaCatalog, /CONTROL_ROOT = "nesqlpp\/export-control-plane\/v1"/);
  assert.match(schemaCatalog, /CONTROL_VALIDATION_PROBES = CONTROL_ROOT \+ "\/validation-probes"/);
  assert.match(schemaCatalog, /DEBUG_KERNEL_TRACE = "nesqlpp\/export-debug-kernel-trace\/v1"/);
  assert.match(schemaCatalog, /EXPORT_VALIDATION = "nesqlpp\/export-validation\/v1"/);
  assert.match(schemaCatalog, /EXPORT_ERROR = "nesqlpp\/export-error\/v1"/);
  assert.match(schemaCatalog, /EXPORT_MANIFEST = "nesqlpp\/export-manifest\/v1"/);
  assert.match(schemaCatalog, /STAGE_CHECKSUMS = "nesqlpp\/stage-checksums\/v1"/);
  assert.match(schemaCatalog, /public static List<String> controlSchemas\(\)/);
  assert.match(schemaCatalog, /public static List<String> debugSchemas\(\)/);

  for (const [constant, manifestKey, fileName] of [
    ['INDEX', 'controlIndex', 'index.json'],
    ['ABI', 'controlAbi', 'abi.json'],
    ['CAPABILITIES', 'controlCapabilities', 'capabilities.json'],
    ['MODULES', 'controlModules', 'modules.json'],
    ['DRIVERS', 'controlDrivers', 'drivers.json'],
    ['VALIDATION_PROBES', 'controlValidationProbes', 'validation-probes.json'],
    ['HEALTH', 'controlHealth', 'health.json'],
    ['VERSION', 'controlVersion', 'version.json'],
  ]) {
    assert.match(controlFileCatalog, new RegExp(`${constant}\\("${manifestKey}"`));
    assert.match(controlFileCatalog, new RegExp(`"${fileName.replace('.', '\\.')}"`));
  }
  assert.match(controlFileCatalog, /public String rawExportPath\(\)/);
  assert.match(controlFileCatalog, /STABILITY_STABLE = "stable"/);
  assert.match(controlFileCatalog, /VALIDATION_PROBE_POLICY = "ordered-fail-closed-validation-probe-catalog"/);
  assert.match(controlFileCatalog, /public static Map<String, String> indexedFiles\(\)/);

  for (const [constant, manifestKey, path, alias] of [
    ['STAGE_TIMING', 'debugStageTimings', 'export/timing.json', 'validation/export_stage_timings.json'],
    ['STAGE_CHECKPOINT', 'debugStageCheckpoint', 'export/checkpoint.json', 'validation/stage_checkpoint.json'],
    ['KERNEL_TRACE', 'debugTraceLatest', 'trace/latest.json', 'validation/export_kernel_trace.json'],
  ]) {
    assert.match(debugFileCatalog, new RegExp(`${constant}\\(`));
    assert.match(debugFileCatalog, new RegExp(`"${manifestKey}"`));
    assert.match(debugFileCatalog, new RegExp(`"${path.replaceAll('/', '\\/').replace('.', '\\.')}"`));
    assert.match(debugFileCatalog, new RegExp(`"${alias.replaceAll('/', '\\/').replace('.', '\\.')}"`));
  }
  assert.match(debugFileCatalog, /public String rawExportDebugPath\(\)/);
  assert.match(rawFileCatalog, /RAW_EXPORT_DIRECTORY = "raw-export"/);
  assert.match(rawFileCatalog, /CONTROL_DIRECTORY = "control"/);
  assert.match(rawFileCatalog, /DEBUG_DIRECTORY = "debug"/);

  assert.match(controlPlaneWriter, /Writes stable ControlFS-style export descriptors/);
  assert.match(controlPlaneWriter, /RawExportFileCatalog\.rawExportDirectory/);
  assert.match(controlPlaneWriter, /RawExportFileCatalog\.CONTROL_DIRECTORY/);
  assert.match(controlPlaneWriter, /CONTROL_REPORTS = validateAndFreeze\(Arrays\.asList/);
  assert.match(controlPlaneWriter, /new ControlReportDescriptor\(ExportControlFile\.INDEX/);
  assert.match(controlPlaneWriter, /new ControlReportDescriptor\(ExportControlFile\.ABI/);
  assert.match(controlPlaneWriter, /new ControlReportDescriptor\(ExportControlFile\.CAPABILITIES/);
  assert.match(controlPlaneWriter, /new ControlReportDescriptor\(ExportControlFile\.MODULES/);
  assert.match(controlPlaneWriter, /new ControlReportDescriptor\(ExportControlFile\.DRIVERS/);
  assert.match(controlPlaneWriter, /new ControlReportDescriptor\(ExportControlFile\.VALIDATION_PROBES/);
  assert.match(controlPlaneWriter, /new ControlReportDescriptor\(ExportControlFile\.HEALTH/);
  assert.match(controlPlaneWriter, /new ControlReportDescriptor\(ExportControlFile\.VERSION/);
  assert.match(controlPlaneWriter, /for \(ControlReportDescriptor descriptor : CONTROL_REPORTS\)/);
  assert.match(controlPlaneWriter, /writeJson\(controlFile\(controlDir, descriptor\.file\(\)\), descriptor\.build\(exportContext, catalog\)\)/);
  assert.match(controlPlaneWriter, /Duplicate ControlFS report descriptor/);
  assert.match(controlPlaneWriter, /Missing ControlFS report descriptor/);
  assert.match(controlPlaneWriter, /private interface ControlReportFactory/);
  assert.match(controlPlaneWriter, /private static final class ControlReportDescriptor/);
  assert.match(controlPlaneWriter, /ExportControlFile\.indexedFiles\(\)/);
  assert.match(controlPlaneWriter, /ExportSchemaCatalog\.RAW_EXPORT_ABI/);
  assert.match(controlPlaneWriter, /validationProbeControlSchema = ExportControlFile\.VALIDATION_PROBES\.schemaVersion\(\)/);
  assert.match(controlPlaneWriter, /validationProbesReport\(\)/);
  assert.match(controlPlaneWriter, /ExportValidationProbeCatalog\.descriptors\(\)/);
  assert.match(controlPlaneWriter, /report\.validationProbeCount = ExportValidationProbeCatalog\.descriptors\(\)\.size\(\)/);
  assert.match(controlPlaneWriter, /ExportDebugFile\.KERNEL_TRACE\.schemaVersion\(\)/);
  assert.match(controlPlaneWriter, /stability = ExportControlFile\.STABILITY_STABLE/);
  assert.match(controlPlaneWriter, /policy = ExportControlFile\.VALIDATION_PROBE_POLICY/);
  assert.match(controlPlaneWriter, /catalog\.descriptors\(\)/);
  assert.match(controlPlaneWriter, /catalog\.drivers\(\)/);
  assert.doesNotMatch(controlPlaneWriter, /writeJson\(controlFile\(controlDir, ExportControlFile\.INDEX\)/);
  assert.doesNotMatch(controlPlaneWriter, /writeJson\(controlFile\(controlDir, ExportControlFile\.VERSION\)/);
  assert.doesNotMatch(controlPlaneWriter, /stability = "stable"/);
  assert.doesNotMatch(controlPlaneWriter, /ordered-fail-closed-validation-probe-catalog/);
  assert.doesNotMatch(controlPlaneWriter, /nesqlpp\/export-control-plane\/v1/);
  assert.doesNotMatch(controlPlaneWriter, /nesqlpp\/raw-export\/alpha1/);

  assert.match(debugPlaneWriter, /Writes DebugFS-style export diagnostics/);
  assert.match(debugPlaneWriter, /RawExportFileCatalog\.RAW_EXPORT_DIRECTORY \+ "\/" \+ file\.validationAliasPath\(\)/);
  assert.match(debugPlaneWriter, /RawExportFileCatalog\.DEBUG_DIRECTORY/);
  assert.match(debugPlaneWriter, /ExportDebugFile\.STAGE_TIMING\.schemaVersion\(\)/);
  assert.match(debugPlaneWriter, /ExportDebugFile\.STAGE_CHECKPOINT\.schemaVersion\(\)/);
  assert.match(debugPlaneWriter, /ExportDebugFile\.KERNEL_TRACE\.schemaVersion\(\)/);
  assert.match(debugPlaneWriter, /validationAliasFile\(exportContext, ExportDebugFile\.STAGE_TIMING\)/);
  assert.match(debugPlaneWriter, /debugFile\(exportContext, ExportDebugFile\.KERNEL_TRACE\)/);
  assert.doesNotMatch(debugPlaneWriter, /nesqlpp\/export-debug-/);

  assert.match(runner, /ExportControlPlaneWriter\.write\(exportContext, moduleCatalog\)/);
  assert.match(runner, /ExportDebugPlaneWriter\.writeStageTimingReport/);
  assert.match(runner, /ExportDebugPlaneWriter\.writeStageCheckpointReport/);
  assert.match(runner, /ExportDebugPlaneWriter\.writeKernelTrace/);
  assert.doesNotMatch(runner, /new GsonBuilder\(\)/);
  assert.doesNotMatch(runner, /new FileOutputStream/);
  assert.doesNotMatch(kernel, /new FileOutputStream/);
  assert.doesNotMatch(kernel, /writeTrace/);

  assert.match(rawManifestBuilder, /for \(ExportControlFile file : ExportControlFile\.values\(\)\)/);
  assert.match(rawManifestBuilder, /file\.manifestKey\(\), file\.rawExportPath\(\)/);
  assert.match(rawManifestBuilder, /for \(ExportDebugFile file : ExportDebugFile\.values\(\)\)/);
  assert.match(rawManifestBuilder, /file\.manifestKey\(\), file\.rawExportDebugPath\(\)/);
  assert.match(integrityManifestWriter, /ExportSchemaCatalog\.EXPORT_MANIFEST/);
  assert.match(integrityManifestWriter, /ExportSchemaCatalog\.STAGE_CHECKSUMS/);
  assert.match(integrityManifestWriter, /for \(ExportControlFile file : ExportControlFile\.values\(\)\)/);
  assert.match(integrityManifestWriter, /RawExportFileCatalog\.controlArtifactStage\(file\)/);
  assert.match(integrityManifestWriter, /for \(ExportDebugFile file : ExportDebugFile\.values\(\)\)/);
  assert.match(integrityManifestWriter, /RawExportFileCatalog\.debugArtifactStage\(file\)/);
});

test('export validation probe catalog owns report collection order and capabilities', () => {
  assert.match(validationAbiCatalog, /final class ExportValidationAbiCatalog/);
  assert.match(validationAbiCatalog, /EXPORT_VALIDATION_SCHEMA = ExportSchemaCatalog\.EXPORT_VALIDATION/);
  assert.match(validationAbiCatalog, /EXPORT_ERROR_SCHEMA = ExportSchemaCatalog\.EXPORT_ERROR/);
  assert.match(validationAbiCatalog, /WARNING_RENDER_ASSET_MANIFEST_MISSING/);
  assert.match(validationAbiCatalog, /BLOCKED_MACHINE_PATHS/);
  assert.match(validationAbiCatalog, /COMPILE_READINESS_READY_WITH_WARNINGS = "ready-with-warnings"/);
  assert.match(validationAbiCatalog, /PATH_HYGIENE_RULES/);
  assert.match(validationAbiCatalog, /PATH_HYGIENE_ERROR_CODE = "export-path-hygiene"/);
  assert.match(validationHealthPolicy, /Owns validation warning, blocked-state, and compile-readiness policy/);
  assert.match(validationHealthPolicy, /static void evaluate\(ExportValidationReportWriter\.ValidationReport report\)/);
  assert.match(validationHealthPolicy, /collectWarnings\(report\)/);
  assert.match(validationHealthPolicy, /determineHealthStatus\(report\)/);
  assert.match(validationHealthPolicy, /ExportValidationAbiCatalog\.WARNING_RENDER_ASSET_MANIFEST_MISSING/);
  assert.match(validationHealthPolicy, /ExportValidationAbiCatalog\.BLOCKED_MACHINE_PATHS/);
  assert.match(validationHealthPolicy, /ExportValidationAbiCatalog\.COMPILE_READINESS_READY_WITH_WARNINGS/);
  assert.match(validationProbeInterface, /Kernel-style validation probe boundary/);
  assert.match(validationProbeInterface, /String id\(\)/);
  assert.match(validationProbeInterface, /List<String> capabilities\(\)/);
  assert.match(validationProbeInterface, /void inspect\(/);
  assert.match(validationProbeContext, /Immutable filesystem and export context shared by validation probes/);
  assert.match(validationProbeContext, /RawExportFileCatalog\.rawExportDirectory\(repositoryDirectory\)/);
  assert.match(validationProbeCatalog, /Owns validation probe ordering, identity checks, and probe capability descriptors/);
  assert.match(validationProbeCatalog, /new ExportValidationRepositoryProbe\(\)/);
  assert.match(validationProbeCatalog, /new ExportValidationRawCountsValidationProbe\(\)/);
  assert.match(validationProbeCatalog, /new ExportValidationBrowserValidationProbe\(\)/);
  assert.match(validationProbeCatalog, /new ExportValidationRenderAssetValidationProbe\(\)/);
  assert.match(validationProbeCatalog, /new ExportValidationSemanticValidationProbe\(\)/);
  assert.match(validationProbeCatalog, /new ExportValidationPathValidationProbe\(\)/);
  assert.match(validationProbeCatalog, /new ExportValidationDerivedMetricsProbe\(\)/);
  assert.match(validationProbeCatalog, /new ExportValidationPreviousDeltaProbe\(\)/);
  assert.match(validationProbeCatalog, /new ExportValidationHealthPolicyProbe\(\)/);
  assert.match(validationProbeCatalog, /new ExportValidationHealthSectionProbe\(\)/);
  assert.match(validationProbeCatalog, /validateAndFreeze/);
  assert.match(validationProbeCatalog, /Duplicate export validation probe id/);
  assert.match(validationProbeCatalog, /Export validation probe capabilities must be non-empty/);
  assert.match(validationProbeDescriptor, /final String id/);
  assert.match(validationProbeDescriptor, /final List<String> capabilities/);
  assert.match(validationReportWriter, /ExportValidationProbeContext\.from\(exportContext\)/);
  assert.match(validationReportWriter, /for \(ExportValidationProbe probe : ExportValidationProbeCatalog\.defaultProbes\(\)\)/);
  assert.match(validationReportWriter, /probe\.inspect\(context, report\)/);
  assert.match(validationReportWriter, /report\.validationProbes = ExportValidationProbeCatalog\.descriptors\(\)/);
  assert.match(validationReportWriter, /report\.validationProbeCount = report\.validationProbes\.size\(\)/);
  assert.match(validationReportWriter, /static final class ValidationReport/);
  assert.match(validationRepositoryProbe, /ExportValidationAbiCatalog\.EXPORT_VALIDATION_SCHEMA/);
  assert.match(validationRepositoryProbe, /new File\(context\.repositoryDirectory, "items"\)/);
  assert.match(validationBrowserProbe, /ExportValidationBrowserAtlasProbe\.inspect\(context\.rawDir, report\)/);
  assert.match(validationBrowserProbe, /RawExportFileCatalog\.NEI_ORDER_FILE/);
  assert.match(validationPreviousDeltaProbe, /ExportValidationReportStore\.applyPreviousDelta\(context\.validationDir, report\)/);
  assert.match(validationHealthPolicyProbe, /ExportValidationHealthPolicy\.evaluate\(report\)/);
  assert.match(validationHealthSectionProbe, /ExportValidationHealthSectionBuilder\.populate\(context\.repositoryDirectory, report\)/);
  assert.match(validationJsonSupport, /Shared low-level JSON, counting, and ratio helpers/);
  assert.match(validationEvidenceCatalog, /Stable ABI\/catalog surface for validation evidence JSON member names/);
  assert.match(validationEvidenceCatalog, /OBJECT_COUNTS = "counts"/);
  assert.match(validationEvidenceCatalog, /RAW_ITEMS = "rawItems"/);
  assert.match(validationEvidenceCatalog, /TOP_UNCLASSIFIED_FAMILY_ACTIONS = "topUnclassifiedFamilyActions"/);
  assert.match(validationEvidenceCatalog, /PRIMARY_ARTIFACT = "primaryArtifact"/);
  assert.match(validationEvidenceCatalog, /ITEM_COUNT = "itemCount"/);
  assert.match(validationEvidenceCatalog, /HANDLERS_WITH_LOADED_RECIPES = "handlersWithLoadedRecipes"/);
  assert.match(validationEvidenceCatalog, /ERROR_FIELD_SCHEMA_VERSION = "schemaVersion"/);
  assert.match(validationRawCountProbe, /Owns raw-export report count extraction/);
  assert.match(validationRawCountProbe, /ExportValidationEvidenceCatalog\.RawCount\.RAW_ITEMS/);
  assert.match(validationHealthSectionBuilder, /Builds aggregate health sections/);
  assert.match(validationHealthSectionBuilder, /ExportValidationEvidenceCatalog\.RecipeAnomaly\.HANDLERS_WITH_LOADED_RECIPES/);
  assert.match(validationSemanticProbe, /Owns semantic diagnostics and bundled rule-pack evidence collection/);
  assert.match(validationSemanticProbe, /ExportValidationEvidenceCatalog\.SemanticDiagnostics\.TOP_UNCLASSIFIED_FAMILY_ACTIONS/);
  assert.match(validationPathHygieneProbe, /ExportValidationAbiCatalog\.pathHygieneRules\(\)/);
  assert.match(validationPathHygieneProbe, /RawExportFileCatalog\.VALIDATION_ERRORS_FILE/);
  assert.match(validationPathHygieneProbe, /ExportValidationEvidenceCatalog\.ERROR_FIELD_SCHEMA_VERSION/);
  assert.match(validationBrowserAtlasProbe, /Owns browser atlas residency and layout coverage evidence collection/);
  assert.match(validationBrowserAtlasProbe, /ExportValidationEvidenceCatalog\.BrowserAtlas\.ITEM_COUNT/);
  assert.match(validationRenderAssetProbe, /Owns render asset manifest, artifact residency, timeline, and singularity animation probes/);
  assert.match(validationRenderAssetProbe, /ExportValidationEvidenceCatalog\.RenderAsset\.PRIMARY_ARTIFACT/);
  assert.doesNotMatch(validationRawCountProbe, /"rawItems"/);
  assert.doesNotMatch(validationSemanticProbe, /"topUnclassifiedFamilyActions"/);
  assert.doesNotMatch(validationHealthSectionBuilder, /"handlersWithLoadedRecipes"/);
  assert.doesNotMatch(validationBrowserAtlasProbe, /"itemCount"/);
  assert.doesNotMatch(validationRenderAssetProbe, /"primaryArtifact"/);
  assert.doesNotMatch(validationReportWriter, /private static void collectWarnings/);
  assert.doesNotMatch(validationReportWriter, /private static void determineHealthStatus/);
  assert.doesNotMatch(validationReportWriter, /private static void addBlockedIf/);
  assert.doesNotMatch(validationReportWriter, /private static void addActionableIssue/);
  assert.doesNotMatch(validationReportWriter, /private static void inspectRawExportCounts/);
  assert.doesNotMatch(validationReportWriter, /private static void inspectRenderAssets/);
  assert.doesNotMatch(validationReportWriter, /private static void inspectExportPathHygiene/);
  assert.doesNotMatch(validationReportWriter, /ExportValidationRawCountProbe\.inspect/);
  assert.doesNotMatch(validationReportWriter, /ExportValidationBrowserAtlasProbe\.inspect/);
  assert.doesNotMatch(validationReportWriter, /ExportValidationRenderAssetProbe\.inspect/);
  assert.doesNotMatch(validationReportWriter, /ExportValidationSemanticProbe\.inspectDiagnostics/);
  assert.doesNotMatch(validationReportWriter, /ExportValidationPathHygieneProbe\.inspect/);
  assert.doesNotMatch(validationReportWriter, /ExportValidationHealthPolicy\.evaluate/);
  assert.doesNotMatch(validationReportWriter, /ExportValidationHealthSectionBuilder\.populate/);
  assert.doesNotMatch(validationReportWriter, /ExportValidationAbiCatalog\.pathHygieneRules\(\)/);
  assert.doesNotMatch(validationReportWriter, /RawExportFileCatalog\.VALIDATION_ERRORS_FILE/);
  assert.doesNotMatch(validationReportWriter, /nesqlpp\/export-error\/v1/);
  assert.doesNotMatch(validationReportWriter, /new File\(repositoryDirectory, "raw-export"/);
});

test('export validation report persistence and delta metadata are store-owned', () => {
  assert.match(validationReportStore, /Owns validation report persistence, aliases, previous-run snapshot, and delta metadata/);
  assert.match(validationReportStore, /static void applyPreviousDelta\(File validationDirectory, ValidationReport report\)/);
  assert.match(validationReportStore, /static ReportFiles write\(File validationDirectory, ValidationReport report\) throws Exception/);
  assert.match(validationReportStore, /readPreviousReport\(reportFile\(validationDirectory\)\)/);
  assert.match(validationReportStore, /previousSnapshot\(previousReport\)/);
  assert.match(validationReportStore, /deltaSnapshot\(report, previousReport\)/);
  assert.match(validationReportStore, /RawExportFileCatalog\.EXPORT_VALIDATION_REPORT_FILE_NAME/);
  assert.match(validationReportStore, /RawExportFileCatalog\.EXPORT_HEALTH_REPORT_FILE_NAME/);
  assert.match(rawFileCatalog, /EXPORT_VALIDATION_REPORT_FILE_NAME = "export_validation_report\.json"/);
  assert.match(rawFileCatalog, /EXPORT_HEALTH_REPORT_FILE_NAME = "export-health-report\.json"/);
  assert.match(validationReportStore, /WRITE_GSON\.toJson\(value, writer\)/);
  assert.match(validationPreviousDeltaProbe, /ExportValidationReportStore\.applyPreviousDelta\(context\.validationDir, report\)/);
  assert.match(validationReportWriter, /ExportValidationReportStore\.write\(context\.validationDir, report\)/);
  assert.match(validationReportWriter, /reportFiles\.reportFile\.getAbsolutePath\(\)/);
  assert.match(validationReportWriter, /static class PreviousSnapshot/);
  assert.match(validationReportWriter, /static final class DeltaSnapshot extends PreviousSnapshot/);
  assert.doesNotMatch(validationReportWriter, /readPreviousReport/);
  assert.doesNotMatch(validationReportWriter, /new File\(validationDir, "export_validation_report\.json"\)/);
  assert.doesNotMatch(validationReportWriter, /new File\(validationDir, "export-health-report\.json"\)/);
});
