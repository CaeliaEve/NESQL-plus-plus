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
  assert.match(runner, /ExportModuleCatalog moduleCatalog = ExportStageModules\.defaultCatalog\(\)/);
  assert.match(runner, /new ExportKernel\(moduleCatalog\)/);
  assert.doesNotMatch(runner, /List<ExportModule> modules/);
  assert.doesNotMatch(runner, /ExportStageModules\.defaultModules\(\)/);
});
