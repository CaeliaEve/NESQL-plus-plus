import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'fs';

const metadata = fs.readFileSync(
  'E:/codex/ae2/NESQL++/src/main/java/com/github/dcysteine/nesql/exporter/main/ExportStageMetadata.java',
  'utf8',
);
const runner = fs.readFileSync(
  'E:/codex/ae2/NESQL++/src/main/java/com/github/dcysteine/nesql/exporter/main/ExportStageRunner.java',
  'utf8',
);
const integrity = fs.readFileSync(
  'E:/codex/ae2/NESQL++/src/main/java/com/github/dcysteine/nesql/exporter/main/ExportIntegrityManifestWriter.java',
  'utf8',
);

test('export stages expose stable incremental families', () => {
  for (const family of [
    'data',
    'images',
    'animated-images',
    'render-contracts',
    'browser-layout',
    'multiblocks',
    'eec-models',
    'recipe-layout-contracts',
    'atlas-pack',
  ]) {
    assert.equal(metadata.includes(`"${family}"`), true, `missing stage family ${family}`);
  }
  assert.equal(runner.includes('ExportStageMetadata.family(stage)'), true);
  assert.equal(runner.includes('skippableByChecksum'), true);
  assert.equal(integrity.includes('String family;'), true);
});
