import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';

const registry = readFileSync(
  new URL('./src/main/java/com/github/dcysteine/nesql/exporter/main/ExportStageActionRegistry.java', import.meta.url),
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

test('export stage registry delegates through module registrars only', () => {
  assert.match(registry, /module\.stageActionRegistrar\(\)/);
  assert.doesNotMatch(registry, /new LifecycleStageActionProvider/);
  assert.doesNotMatch(registry, /new RawFactStageActionProvider/);
  assert.doesNotMatch(registry, /new RenderStageActionProvider/);
  assert.doesNotMatch(registry, /new NativeUiStageActionProvider/);
  assert.doesNotMatch(registry, /ExportWriterSupport\./);
  assert.doesNotMatch(registry, /RenderLifecycleSupport\./);
  assert.doesNotMatch(registry, /EnumChatFormatting/);
});
