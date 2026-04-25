import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'fs';

const read = (relativePath) =>
  fs.readFileSync(`E:/codex/ae2/NESQL++/${relativePath}`, 'utf8');

test('base exported image paths stay on png so animated outputs can be an opt-in overlay', () => {
  const rendererSource = read('src/main/java/com/github/dcysteine/nesql/exporter/util/render/Renderer.java');
  const idUtilSource = read('src/main/java/com/github/dcysteine/nesql/exporter/util/IdUtil.java');

  assert.equal(
    rendererSource.includes('return ConfigOptions.EXPORT_GIF.get() ? ".gif" : ".png";'),
    false,
    'Renderer should not globally switch all exported image file names to .gif',
  );

  assert.equal(
    idUtilSource.includes('Renderer.IMAGE_FILE_EXTENSION'),
    false,
    'IdUtil image paths should no longer depend on a global gif extension toggle',
  );
});

test('gif renderer writes gif output instead of renaming png files behind a gif extension', () => {
  const gifRendererSource = read('src/main/java/com/github/dcysteine/nesql/exporter/util/render/GifRenderer.java');

  assert.equal(
    gifRendererSource.includes('replace(".gif", ".png")'),
    false,
    'GifRenderer should not replace gif outputs with png files',
  );

  assert.equal(
    gifRendererSource.includes('ImageWriter') || gifRendererSource.includes('ImageOutputStream'),
    true,
    'GifRenderer should use an actual GIF writer pipeline',
  );
});

test('render-contract rebuild can recover item assets from image outputs when the database is unavailable', () => {
  const collectorSource = read('src/main/java/com/github/dcysteine/nesql/exporter/local/CanonicalRenderAssetCollector.java');

  assert.equal(
    collectorSource.includes('return collectFamilyAssetsFromImageFiles("item");'),
    true,
    'Collector should fall back to scanning image/item outputs when split item exports are unavailable',
  );

  assert.equal(
    collectorSource.includes('return collectFamilyAssetsFromImageFiles("fluid");'),
    true,
    'Collector should use the same family image scan path for fluid rebuilds',
  );
});

test('gif-backed item exports are treated as animated assets based on the actual exported file', () => {
  const collectorSource = read('src/main/java/com/github/dcysteine/nesql/exporter/local/CanonicalRenderAssetCollector.java');
  const atlasWriterSource = read('src/main/java/com/github/dcysteine/nesql/exporter/local/CanonicalAnimatedAtlasPackWriter.java');

  assert.equal(
    collectorSource.includes('GifAnimationInfo gifAnimation = inspectGifAnimation(baseFile);'),
    true,
    'Collector should inspect the resolved export artifact for GIF animation metadata',
  );

  assert.equal(
    collectorSource.includes('if (!directPng.exists() || inspectGifAnimation(directGif).animated)'),
    true,
    'Collector should prefer a real animated GIF over a sibling PNG fallback when both exist',
  );

  assert.equal(
    collectorSource.includes('File alternateGif = replaceExtension(familyRoot, normalizedImagePath, ".gif");'),
    true,
    'Collector should swap a requested .png path over to a sibling .gif artifact when the png output is absent',
  );

  assert.equal(
    collectorSource.includes('asset.renderMode = "captured_final_atlas";'),
    true,
    'Collector should normalize rendered GIF assets to captured_final_atlas playback metadata',
  );

  assert.equal(
    collectorSource.includes('normalizedImagePath.endsWith(".gif")'),
    false,
    'Collector should not key GIF animation detection off the requested image path alone',
  );

  assert.equal(
    atlasWriterSource.includes('loadGifFrames(sourceFile)'),
    true,
    'Animated atlas packing should be able to extract frames from rendered GIF assets',
  );
});

test('native sprite metadata reads real mcmeta timing instead of collapsing every animation to 50ms', () => {
  const metadataSource = read('src/main/java/com/github/dcysteine/nesql/exporter/util/render/NativeSpriteMetadataExtractor.java');

  assert.equal(
    metadataSource.includes('buildAnimationMetadataCandidates'),
    true,
    'Native sprite metadata extraction should resolve texture .png.mcmeta resources',
  );

  assert.equal(
    metadataSource.includes('parseResourceAnimationMetadata'),
    true,
    'Native sprite metadata extraction should parse raw animation JSON from resource packs',
  );

  assert.equal(
    metadataSource.includes('frame.put("durationMs", sourceFrame.timeTicks * 50);'),
    true,
    'Native sprite timeline should preserve per-frame tick durations from mcmeta',
  );
});

test('render jobs classify known time-based custom inventory renderers as animated captures', () => {
  const renderJobSource = read('src/main/java/com/github/dcysteine/nesql/exporter/util/render/RenderJob.java');

  assert.equal(
    renderJobSource.includes('hasAnimatedCustomRenderer'),
    true,
    'RenderJob should detect animated custom inventory renderers separately from atlas sprites',
  );

  assert.equal(
    renderJobSource.includes('itemrenderertier') && renderJobSource.includes('rocket'),
    true,
    'Rocket-style custom renderers should be force-captured as multi-frame animations',
  );

  assert.equal(
    renderJobSource.includes('transcendentalmetaitemrenderer')
      && renderJobSource.includes('glitcheffectmetaitemrenderer')
      && renderJobSource.includes('wireframetesseractrenderer'),
    true,
    'Known GTNH fancy item renderers should be classified as animated captures',
  );
});
