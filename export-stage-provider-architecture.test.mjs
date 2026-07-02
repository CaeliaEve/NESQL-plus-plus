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
  assert.match(kernel, /report\.modules = catalog\.descriptors\(\)/);
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
  assert.match(kernel, /export\.driver\.probe/);
  assert.match(kernel, /driver\.bind\(device, context\)/);
  assert.match(kernel, /export\.driver\.bind/);
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
  assert.match(kernelContext, /export\.resource\.release/);
  assert.match(actionContext, /final ExportKernelContext kernelContext/);
  assert.match(runner, /new ExportStageActionContext\(exportContext, kernelContext, strategy, stageState\)/);
  assert.doesNotMatch(runner, /ExportLifecycleSupport\.closeSession\(stageState\.session/);
  assert.doesNotMatch(runner, /stageState\.runtime\.close\(\)/);

  const lifecycle = providerSources.LifecycleStageActionProvider;
  assert.match(lifecycle, /context\.kernelContext\.resources\(\)\.add\(\s*"export\.runtime"/);
  assert.match(lifecycle, /context\.kernelContext\.resources\(\)\.add\(\s*"export\.session"/);
  assert.match(lifecycle, /ExportLifecycleSupport\.closeSession/);
});
