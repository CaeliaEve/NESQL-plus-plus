import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import test from 'node:test';

const repoRoot = process.cwd();
const readSource = (relativePath) => fs.readFileSync(path.join(repoRoot, relativePath), 'utf8');

test('native UI export skips legacy base indices that raw-export does not consume', () => {
  const exporterState = readSource('src/main/java/com/github/dcysteine/nesql/exporter/plugin/ExporterState.java');
  const baseExporter = readSource('src/main/java/com/github/dcysteine/nesql/exporter/plugin/base/BasePluginExporter.java');
  const strategy = readSource('src/main/java/com/github/dcysteine/nesql/exporter/main/ExportExecutionStrategy.java');

  assert.match(exporterState, /legacyBasePostProcessingEnabled = true/);
  assert.match(exporterState, /isLegacyBasePostProcessingEnabled\(\)/);
  assert.match(exporterState, /setLegacyBasePostProcessingEnabled\(boolean legacyBasePostProcessingEnabled\)/);
  assert.match(baseExporter, /if \(!exporterState\.isLegacyBasePostProcessingEnabled\(\)\)/);
  assert.match(baseExporter, /Skipping legacy base post-process indices/);
  assert.match(strategy, /setLegacyBasePostProcessingEnabled\(false\)/);
  assert.match(strategy, /exportRuntime\.runPluginPipeline\(\)/);
});

test('NEI loader avoids duplicate or informational full item-universe scans', () => {
  const neiBatchLoader = readSource(
    'src/main/java/com/github/dcysteine/nesql/exporter/plugin/nei/NeiRecipeBatchLoader.java',
  );

  assert.match(neiBatchLoader, /shouldSkipCraftingHandler\(baseHandler\)/);
  assert.match(neiBatchLoader, /gregtech\.nei\.GTNEIDefaultHandler/);
  assert.match(neiBatchLoader, /NEIRecipeInfoHandler/);
  assert.match(neiBatchLoader, /tryLoadAllRecipes\(handler\)/);
  assert.match(neiBatchLoader, /getMethod\("loadAllRecipes"\)/);
  assert.match(neiBatchLoader, /loadRecipesFullScan\(handler, itemUniverse, loadedCount\)/);
});

test('raw sidecar and atlas writers prefer throughput settings for native export scale', () => {
  const repositoryFactStreamer = readSource(
    'src/main/java/com/github/dcysteine/nesql/exporter/local/RawExportRepositoryFactStreamer.java',
  );
  const atlasPackingSupport = readSource(
    'src/main/java/com/github/dcysteine/nesql/exporter/local/AtlasPackingSupport.java',
  );

  assert.match(repositoryFactStreamer, /RECIPE_BATCH_SIZE = 2048/);
  assert.match(atlasPackingSupport, /writePngFast\(atlasImage, atlasFile\)/);
  assert.match(atlasPackingSupport, /setCompressionQuality\(1\.0f\)/);
});
