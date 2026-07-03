import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';

const repoRoot = process.cwd();
const readSource = (relativePath) => fs.readFileSync(path.join(repoRoot, relativePath), 'utf8');

const entityExporter = readSource(
  'src/main/java/com/github/dcysteine/nesql/exporter/main/IndustrialSlaughterhouseEntityExporter.java',
);
const modelExporter = readSource(
  'src/main/java/com/github/dcysteine/nesql/exporter/util/render/EntityModelContractExporter.java',
);

test('entity model contract exporter exposes explicit result and fail-closed IO boundary', () => {
  assert.match(modelExporter, /public static EntityModelExportResult export\([\s\S]*\) throws Exception/);
  assert.match(modelExporter, /requireExportInput\(repositoryDirectory, imageDirectory, mobName, entity, usedRelativeModelPaths\)/);
  assert.match(modelExporter, /Entity model repository directory must not be null/);
  assert.match(modelExporter, /Entity model image directory must not be null/);
  assert.match(modelExporter, /Entity model mob name must be non-empty/);
  assert.match(modelExporter, /Entity model source entity must not be null/);
  assert.match(modelExporter, /Entity model path ownership set must not be null/);
  assert.match(modelExporter, /return EntityModelExportResult\.skipped\("no-model-components"\)/);
  assert.match(modelExporter, /return EntityModelExportResult\.exported\(/);
  assert.match(modelExporter, /public static final class EntityModelExportResult/);
  assert.match(modelExporter, /public boolean exported\(\)/);
  assert.match(modelExporter, /public ExportedEntityModel model\(\)/);
  assert.match(modelExporter, /public String skipReason\(\)/);
  assert.doesNotMatch(modelExporter, /Failed to export 3D entity model/);
  assert.doesNotMatch(modelExporter, /catch \(Exception e\) \{\s*Logger\.MOD\.warn/);
});

test('entity model contract exporter validates file-system ownership before writes', () => {
  assert.match(modelExporter, /private static void ensureOutputFile\(File outputFile\) throws IOException/);
  assert.match(modelExporter, /Entity model output file must not be null/);
  assert.match(modelExporter, /Entity model output path exists but is not a file/);
  assert.match(modelExporter, /Entity model output parent must not be null/);
  assert.match(modelExporter, /Entity model output parent exists but is not a directory/);
  assert.match(modelExporter, /Failed to create entity model output parent/);
  assert.match(modelExporter, /Entity model texture output path exists but is not a file/);
  assert.match(modelExporter, /try \(InputStream inputStream = resource\.getInputStream\(\);\s*FileOutputStream outputStream = new FileOutputStream/);
  assert.doesNotMatch(modelExporter, /ensureParentDirectory/);
  assert.doesNotMatch(modelExporter, /parentDir\.mkdirs\(\);/);
  assert.doesNotMatch(modelExporter, /catch \(IOException ignored\)/);
});

test('EEC entity preview exporter records model export results without null sentinels', () => {
  assert.match(entityExporter, /ENTITY_PREVIEW_SCHEMA_VERSION = "nesqlpp\/entity-previews\/v1"/);
  assert.match(entityExporter, /ENTITY_MODEL_MANIFEST_SCHEMA_VERSION = "nesqlpp\/entity-models\/v1"/);
  assert.match(entityExporter, /ENTITY_MODEL_EXPORT_STATUS_EXPORTED = "exported"/);
  assert.match(entityExporter, /ENTITY_MODEL_EXPORT_STATUS_SKIPPED = "skipped"/);
  assert.match(entityExporter, /EntityModelContractExporter\.EntityModelExportResult modelResult =\s*EntityModelContractExporter\.export/);
  assert.match(entityExporter, /if \(modelResult\.exported\(\)\)/);
  assert.match(entityExporter, /modelManifest\.skippedEntries\.add\(buildModelSkippedEntry\(request, modelResult\.skipReason\(\)\)\)/);
  assert.match(entityExporter, /static final class EntityModelSkippedEntry/);
  assert.match(entityExporter, /int exportedCount/);
  assert.match(entityExporter, /int skippedCount/);
  assert.match(entityExporter, /List<EntityModelSkippedEntry> skippedEntries/);
  assert.doesNotMatch(entityExporter, /exportedEntityModel != null/);
  assert.doesNotMatch(entityExporter, /schemaVersion = "nesqlpp\/entity-previews\/v1"/);
  assert.doesNotMatch(entityExporter, /schemaVersion = "nesqlpp\/entity-models\/v1"/);
});

test('EEC entity preview manifest output fails closed on invalid paths', () => {
  assert.match(entityExporter, /private static void ensureDirectory\(File directory, String label\) throws IOException/);
  assert.match(entityExporter, /EEC entity preview .* directory must not be null/);
  assert.match(entityExporter, /path exists but is not a directory/);
  assert.match(entityExporter, /Failed to create EEC entity preview/);
  assert.match(entityExporter, /writeJsonManifest\(manifestFile, manifest\)/);
  assert.match(entityExporter, /EEC entity preview manifest file must not be null/);
  assert.match(entityExporter, /EEC entity preview manifest path exists but is not a file/);
  assert.match(entityExporter, /ensureDirectory\(manifestFile\.getParentFile\(\), "manifest parent"\)/);
  assert.doesNotMatch(entityExporter, /parent\.mkdirs\(\);/);
});
