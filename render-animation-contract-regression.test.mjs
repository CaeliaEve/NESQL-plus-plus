import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import test from 'node:test';

const repoRoot = process.cwd();
const readSource = (relativePath) => fs.readFileSync(path.join(repoRoot, relativePath), 'utf8');

test('native sprite animation treats runtime multi-frame sprites as animated', () => {
  const source = readSource(
    'src/main/java/com/github/dcysteine/nesql/exporter/util/render/NativeSpriteMetadataExtractor.java',
  );

  assert.match(
    source,
    /metadata\.animated = sprite\.hasAnimationMetadata\(\)\s*\|\|\s*\(metadata\.frameCount != null && metadata\.frameCount > 1\);/,
    'sprites with runtime frameCount > 1 must not be exported as static snapshots',
  );
  assert.match(
    source,
    /if \(runtimeFrameCount != null && runtimeFrameCount > 1\) \{\s*return createSequentialTimeline\(runtimeFrameCount, 1\);/s,
    'multi-frame sprites need a fallback sequential timeline even when mcmeta metadata is unavailable',
  );
});

test('composite item animations use real framebuffer render identity', () => {
  const source = readSource('src/main/java/com/github/dcysteine/nesql/exporter/util/render/RenderJob.java');

  assert.match(
    source,
    /hasAnimatedCompositeTexture\(getItem\(\)\)/,
    'native sprite playback must not override composite item renderers',
  );
  assert.match(
    source,
    /return "animated-contained-fluid";/,
    'filled cells and other fluid containers with animated fluids should be captured as animated items',
  );
  assert.match(
    source,
    /return "animated-render-pass-texture";/,
    'non-base animated render passes should trigger multi-frame capture',
  );
  assert.match(
    source,
    /return "animated-auxiliary-texture";/,
    'mask/halo/overlay animations should trigger multi-frame capture',
  );
  assert.match(
    source,
    /GTUtility\.getFluidForFilledItem\(stack, true\)/,
    'GregTech filled-item fluid icons should be inspected for animation',
  );
  assert.match(
    source,
    /markContainedFluidTexture\(stack\);/,
    'animated contained-fluid sprites should be marked for atlas updates between captured frames',
  );
});

test('animated material classifier includes localized and spacetime material names', () => {
  const source = readSource('src/main/java/com/github/dcysteine/nesql/exporter/util/render/AnimatedItemRegistry.java');

  assert.match(
    source,
    /safeDisplayName\(stack\)/,
    'runtime display names must participate in the high-energy animated material classifier',
  );
  for (const keyword of ['pyrotheum', 'cryotheum', 'spacetime', '烈焰', '极寒', '超时空']) {
    assert.match(source, new RegExp(keyword), `expected animated material keyword ${keyword}`);
  }
});
