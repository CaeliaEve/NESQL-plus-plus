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
  assert.match(runner, /ExportModuleCatalog moduleCatalog = ExportStageModules\.defaultCatalog\(\)/);
  assert.match(runner, /new ExportKernel\(moduleCatalog\)/);
  assert.doesNotMatch(runner, /List<ExportModule> modules/);
  assert.doesNotMatch(runner, /ExportStageModules\.defaultModules\(\)/);
});

test('stage action modules expose declared stages and capabilities through the module manifest', () => {
  assert.match(moduleInterface, /default List<String> capabilities\(\)/);
  assert.match(moduleInterface, /default List<String> stageIds\(\)/);
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
