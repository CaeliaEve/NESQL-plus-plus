import fs from 'node:fs';
import assert from 'node:assert/strict';

const registry = fs.readFileSync(
  'src/main/java/com/github/dcysteine/nesql/exporter/main/ExportStageActionRegistry.java',
  'utf8',
);

const renderStage = registry.match(/actions\.put\(ExportStage\.RENDER_IMAGES,[\s\S]*?\n        \}\);/);
assert(renderStage, 'RENDER_IMAGES stage action must exist');

assert(
  renderStage[0].includes('RenderLifecycleSupport.awaitRenderCompletion();'),
  'render assets must be indexed only after render completion is awaited',
);
assert(
  renderStage[0].includes('stageState.renderAssets =\n                        ExportWriterSupport.collectRenderAssets'),
  'render assets must be recollected from completed image outputs after rendering',
);
assert(
  !renderStage[0].includes('Reusing '),
  'pre-render asset snapshots must not be reused after slow NBT render jobs complete',
);
