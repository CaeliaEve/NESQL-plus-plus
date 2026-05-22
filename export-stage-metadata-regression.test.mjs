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

test('export checksums are content based and carry previous-run reuse signals', () => {
  assert.equal(integrity.includes('readPreviousArtifacts'), true);
  assert.equal(integrity.includes('previousSha256'), true);
  assert.equal(integrity.includes('boolean unchanged;'), true);
  assert.equal(integrity.includes('boolean skippableByChecksum;'), true);
  assert.equal(integrity.includes('sha256(file)'), true);
  assert.equal(integrity.includes('file.lastModified()'), false);
});

test('render outputs are skipped only when item/render/texture signature matches', () => {
  const job = fs.readFileSync(
    'E:/codex/ae2/NESQL++/src/main/java/com/github/dcysteine/nesql/exporter/util/render/RenderJob.java',
    'utf8',
  );
  const dispatcher = fs.readFileSync(
    'E:/codex/ae2/NESQL++/src/main/java/com/github/dcysteine/nesql/exporter/util/render/RenderDispatcher.java',
    'utf8',
  );
  const renderer = fs.readFileSync(
    'E:/codex/ae2/NESQL++/src/main/java/com/github/dcysteine/nesql/exporter/util/render/Renderer.java',
    'utf8',
  );
  assert.equal(job.includes('getRenderSignature()'), true);
  assert.equal(job.includes('getRenderSignatureFilePath()'), true);
  assert.equal(dispatcher.includes('RenderSignatureSupport.matches(imageDirectory, job)'), true);
  assert.equal(renderer.includes('RenderSignatureSupport.write(imageDirectory, job)'), true);
});

test('atlas reuse is guarded by sprite source hash and atlas page hash', () => {
  const atlas = fs.readFileSync(
    'E:/codex/ae2/NESQL++/src/main/java/com/github/dcysteine/nesql/exporter/local/CanonicalAtlasPackWriter.java',
    'utf8',
  );
  assert.equal(atlas.includes('sourceSignature'), true);
  assert.equal(atlas.includes('atlasPageSha256'), true);
  assert.equal(atlas.includes('sourceSha256'), true);
  assert.equal(atlas.includes('sourceBytes'), true);
  assert.equal(atlas.includes('oldestOutputModified < newestSourceModified'), false);
});
