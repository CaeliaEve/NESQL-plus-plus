import assert from 'node:assert/strict';
import { existsSync, readFileSync } from 'node:fs';
import test from 'node:test';

const registryUrl = new URL('./src/main/java/com/github/dcysteine/nesql/exporter/main/ExportStageActionRegistry.java', import.meta.url);
const kernel = readFileSync(
  new URL('./src/main/java/com/github/dcysteine/nesql/elysium/kernel/ExportKernel.java', import.meta.url),
  'utf8',
);
const modules = readFileSync(
  new URL('./src/main/java/com/github/dcysteine/nesql/exporter/main/ExportStageModules.java', import.meta.url),
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
});

test('export kernel directly dispatches stage registrars', () => {
  assert.equal(existsSync(registryUrl), false, 'ExportStageActionRegistry must stay deleted');
  assert.match(kernel, /buildStageActions/);
  assert.match(kernel, /module\.stageActionRegistrar\(\)/);
  assert.doesNotMatch(kernel, /new LifecycleStageActionProvider/);
  assert.doesNotMatch(kernel, /ExportWriterSupport\./);
  assert.doesNotMatch(kernel, /RenderLifecycleSupport\./);
});
