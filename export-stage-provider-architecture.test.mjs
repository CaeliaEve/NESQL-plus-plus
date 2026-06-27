import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';

const registry = readFileSync(
  new URL('./src/main/java/com/github/dcysteine/nesql/exporter/main/ExportStageActionRegistry.java', import.meta.url),
  'utf8',
);

test('export stage registry delegates to subsystem providers', () => {
  for (const provider of [
    'LifecycleStageActionProvider',
    'RawFactStageActionProvider',
    'RenderStageActionProvider',
    'NativeUiStageActionProvider',
  ]) {
    assert.match(registry, new RegExp(`new ${provider}\\(\\)`));
  }
  assert.doesNotMatch(registry, /ExportWriterSupport\./);
  assert.doesNotMatch(registry, /RenderLifecycleSupport\./);
  assert.doesNotMatch(registry, /EnumChatFormatting/);
});
