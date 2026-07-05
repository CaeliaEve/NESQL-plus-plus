import assert from 'node:assert/strict';
import { existsSync, readFileSync } from 'node:fs';
import test from 'node:test';

const renderFactsUrl = new URL(
  './src/main/java/com/github/dcysteine/nesql/exporter/local/AngelicaRenderFactsWriter.java',
  import.meta.url,
);
const backendFactsUrl = new URL(
  './src/main/java/com/github/dcysteine/nesql/exporter/local/AngelicaRenderBackendFactsWriter.java',
  import.meta.url,
);
const renderFactFileOpsUrl = new URL(
  './src/main/java/com/github/dcysteine/nesql/exporter/local/AngelicaRenderFactFileOps.java',
  import.meta.url,
);
const textureSpriteFactsUrl = new URL(
  './src/main/java/com/github/dcysteine/nesql/exporter/local/AngelicaRenderTextureSpriteFactsWriter.java',
  import.meta.url,
);
const textureSpriteCountsUrl = new URL(
  './src/main/java/com/github/dcysteine/nesql/exporter/local/AngelicaTextureSpriteStreamCounts.java',
  import.meta.url,
);
const itemRendererFactsUrl = new URL(
  './src/main/java/com/github/dcysteine/nesql/exporter/local/AngelicaRenderItemRendererFactsWriter.java',
  import.meta.url,
);
const itemRendererCountsUrl = new URL(
  './src/main/java/com/github/dcysteine/nesql/exporter/local/AngelicaItemRendererStreamCounts.java',
  import.meta.url,
);
const framebufferCaptureFactsUrl = new URL(
  './src/main/java/com/github/dcysteine/nesql/exporter/local/AngelicaFramebufferCaptureFactsWriter.java',
  import.meta.url,
);
const framebufferCaptureCountsUrl = new URL(
  './src/main/java/com/github/dcysteine/nesql/exporter/local/AngelicaFramebufferCaptureStreamCounts.java',
  import.meta.url,
);
const rendererClassificationUrl = new URL(
  './src/main/java/com/github/dcysteine/nesql/exporter/local/AngelicaRendererClassification.java',
  import.meta.url,
);

const renderFacts = readFileSync(renderFactsUrl, 'utf8');
const backendFacts = readFileSync(backendFactsUrl, 'utf8');
const renderFactFileOps = readFileSync(renderFactFileOpsUrl, 'utf8');
const textureSpriteFacts = readFileSync(textureSpriteFactsUrl, 'utf8');
const itemRendererFacts = readFileSync(itemRendererFactsUrl, 'utf8');
const framebufferCaptureFacts = readFileSync(framebufferCaptureFactsUrl, 'utf8');

test('Angelica render backend facts live outside the render fact coordinator', () => {
  assert.equal(existsSync(backendFactsUrl), true, 'AngelicaRenderBackendFactsWriter must exist');
  assert.match(renderFacts, /new AngelicaRenderBackendFactsWriter\(SCHEMA_ROOT\)\s*\.write\(/);
  assert.doesNotMatch(renderFacts, /private\s+String\s+writeBackendFacts\(/);
  assert.doesNotMatch(renderFacts, /private\s+static\s+boolean\s+detectAngelica\(/);
  assert.doesNotMatch(renderFacts, /private\s+static\s+boolean\s+detectOptifine\(/);
  assert.doesNotMatch(renderFacts, /private\s+static\s+boolean\s+detectShaderPackInUse\(/);
  assert.doesNotMatch(renderFacts, /private\s+static\s+boolean\s+detectShadersEnabled\(/);
  assert.doesNotMatch(renderFacts, /private\s+static\s+Object\s+irisApi\(/);
  assert.doesNotMatch(renderFacts, /private\s+static\s+String\s+glString\(/);
});

test('Angelica render backend writer owns backend schema, probes, and GL evidence', () => {
  assert.match(backendFacts, /final class AngelicaRenderBackendFactsWriter/);
  assert.match(backendFacts, /root\.addProperty\("schemaVersion", schemaRoot \+ "\/backend"\)/);
  assert.match(backendFacts, /private\s+static\s+boolean\s+detectAngelica\(/);
  assert.match(backendFacts, /private\s+static\s+boolean\s+detectShaderPackInUse\(/);
  assert.match(backendFacts, /private\s+static\s+String\s+glString\(/);
  assert.match(backendFacts, /GL11\.glGetString\(name\)/);
  assert.match(backendFacts, /AngelicaTweaker/);
  assert.match(backendFacts, /net\.irisshaders\.iris\.api\.v0\.IrisApi/);
});

test('Angelica texture sprite facts live outside the render fact coordinator', () => {
  assert.equal(existsSync(textureSpriteFactsUrl), true, 'AngelicaRenderTextureSpriteFactsWriter must exist');
  assert.equal(existsSync(textureSpriteCountsUrl), true, 'AngelicaTextureSpriteStreamCounts must exist');
  assert.match(renderFacts, /new AngelicaRenderTextureSpriteFactsWriter\(SCHEMA_ROOT\)\s*\.write\(/);
  assert.match(renderFacts, /AngelicaTextureSpriteStreamCounts textureSpriteCounts/);
  assert.doesNotMatch(renderFacts, /writeTextureSpriteFacts\(/);
  assert.doesNotMatch(renderFacts, /collectTextureMaps\(/);
  assert.doesNotMatch(renderFacts, /readUploadedSprites\(/);
  assert.doesNotMatch(renderFacts, /animationTimeline\(/);
  assert.doesNotMatch(renderFacts, /fallbackTimeline\(/);
  assert.doesNotMatch(renderFacts, /readAnimationMetadata\(/);
  assert.doesNotMatch(renderFacts, /private\s+static\s+final\s+class\s+TextureSpriteStreamCounts/);
});

test('Angelica texture sprite writer owns atlas discovery and timeline reconstruction', () => {
  assert.match(textureSpriteFacts, /final class AngelicaRenderTextureSpriteFactsWriter/);
  assert.match(textureSpriteFacts, /schemaRoot \+ "\/texture-sprite"/);
  assert.match(textureSpriteFacts, /collectTextureMaps\(/);
  assert.match(textureSpriteFacts, /readUploadedSprites\(/);
  assert.match(textureSpriteFacts, /animationTimeline\(/);
  assert.match(textureSpriteFacts, /fallbackTimeline\(/);
  assert.match(textureSpriteFacts, /readAnimationMetadata\(/);
  assert.match(textureSpriteFacts, /Minecraft\.getMinecraft\(\)/);
  assert.match(textureSpriteFacts, /TextureAtlasSprite/);
});

test('Angelica item renderer and shader facts live outside the render fact coordinator', () => {
  assert.equal(existsSync(itemRendererFactsUrl), true, 'AngelicaRenderItemRendererFactsWriter must exist');
  assert.equal(existsSync(itemRendererCountsUrl), true, 'AngelicaItemRendererStreamCounts must exist');
  assert.equal(existsSync(rendererClassificationUrl), true, 'AngelicaRendererClassification must exist');
  assert.match(renderFacts, /new AngelicaRenderItemRendererFactsWriter\(entityManager, SCHEMA_ROOT\)\s*\.write\(/);
  assert.match(renderFacts, /AngelicaItemRendererStreamCounts itemRendererCounts/);
  assert.doesNotMatch(renderFacts, /writeItemRendererFacts\(/);
  assert.doesNotMatch(renderFacts, /toItemRendererRow\(/);
  assert.doesNotMatch(renderFacts, /toShaderItemRow\(/);
  assert.doesNotMatch(renderFacts, /classifyRenderer\(/);
  assert.doesNotMatch(renderFacts, /shaderTextureHints\(/);
  assert.doesNotMatch(renderFacts, /MinecraftForgeClient\.getItemRenderer/);
});

test('Angelica item renderer writer owns renderer registry probing and shader stream rows', () => {
  assert.match(itemRendererFacts, /final class AngelicaRenderItemRendererFactsWriter/);
  assert.match(itemRendererFacts, /TypedQuery<Item>/);
  assert.match(itemRendererFacts, /toItemRendererRow\(/);
  assert.match(itemRendererFacts, /toShaderItemRow\(/);
  assert.match(itemRendererFacts, /AngelicaRendererClassificationCatalog\.classifyItemRenderer\(item, rendererClass\)/);
  assert.match(itemRendererFacts, /shaderTextureHints\(/);
  assert.match(itemRendererFacts, /MinecraftForgeClient\.getItemRenderer/);
  assert.match(itemRendererFacts, /preferredExport", "angelica-framebuffer-capture"/);
});

test('Angelica framebuffer capture facts live outside the render fact coordinator', () => {
  assert.equal(existsSync(framebufferCaptureFactsUrl), true, 'AngelicaFramebufferCaptureFactsWriter must exist');
  assert.equal(existsSync(framebufferCaptureCountsUrl), true, 'AngelicaFramebufferCaptureStreamCounts must exist');
  assert.match(renderFacts, /new AngelicaFramebufferCaptureFactsWriter\(SCHEMA_ROOT, renderAssets\)\s*\.write\(/);
  assert.match(renderFacts, /AngelicaFramebufferCaptureStreamCounts captureCounts/);
  assert.doesNotMatch(renderFacts, /writeFramebufferCaptureFacts\(/);
  assert.doesNotMatch(renderFacts, /isFramebufferCaptureAsset\(/);
  assert.doesNotMatch(renderFacts, /existing-render-dispatcher-capture/);
  assert.doesNotMatch(renderFacts, /rendererContract/);
});

test('Angelica render facts writer owns strict input and delegates output contracts', () => {
  assert.match(renderFacts, /Angelica render facts entity manager must not be null/);
  assert.match(renderFacts, /Angelica render facts raw-export directory must not be null/);
  assert.match(renderFacts, /Angelica render facts render assets must not be null/);
  assert.match(renderFacts, /Collections\.unmodifiableList\(new ArrayList<CanonicalRenderAsset>\(renderAssets\)\)/);
  assert.match(renderFacts, /AngelicaRenderFactFileOps\.ensureDirectory\(new File\(rawDir, "facts\/render"\)\)/);
  assert.doesNotMatch(renderFacts, /renderAssets == null\s*\?\s*Collections\.<CanonicalRenderAsset>emptyList\(\)/);
  assert.doesNotMatch(renderFacts, /Collections\.<CanonicalRenderAsset>emptyList\(\)/);
  assert.doesNotMatch(renderFacts, /private\s+static\s+void\s+ensureDirectory\(/);
  assert.doesNotMatch(renderFacts, /directory != null && !directory\.exists\(\) && !directory\.mkdirs\(\)/);
});

test('Angelica render fact file ops own fail-closed artifact creation', () => {
  assert.equal(existsSync(renderFactFileOpsUrl), true, 'AngelicaRenderFactFileOps must exist');
  assert.match(renderFactFileOps, /final class AngelicaRenderFactFileOps/);
  assert.match(renderFactFileOps, /static OutputStreamWriter createUtf8JsonWriter\(File out\) throws IOException/);
  assert.match(renderFactFileOps, /static OutputStreamWriter createUtf8JsonlWriter\(File out\) throws IOException/);
  assert.match(renderFactFileOps, /static void ensureOutputFile\(File out\) throws IOException/);
  assert.match(renderFactFileOps, /Angelica render fact output file must not be null/);
  assert.match(renderFactFileOps, /Angelica render fact output parent directory must not be null/);
  assert.match(renderFactFileOps, /Angelica render fact output parent exists but is not a directory/);
  assert.match(renderFactFileOps, /Angelica render fact output path exists but is not a file/);
  assert.match(renderFactFileOps, /Failed to create Angelica render fact output directory/);
  assert.match(renderFactFileOps, /new FileOutputStream\(out, false\)/);
  assert.match(renderFactFileOps, /new RawExportFastGzipOutputStream\(fos\)/);
  assert.match(renderFactFileOps, /failure\.addSuppressed\(closeFailure\)/);
});

test('Angelica render fact writers share the fail-closed file ops boundary', () => {
  assert.match(backendFacts, /AngelicaRenderFactFileOps\.createUtf8JsonWriter\(out\)/);
  for (const source of [textureSpriteFacts, itemRendererFacts, framebufferCaptureFacts]) {
    assert.match(source, /AngelicaRenderFactFileOps\.createUtf8JsonlWriter\(out\)/);
    assert.doesNotMatch(source, /private\s+static\s+OutputStreamWriter\s+createUtf8JsonlWriter\(/);
    assert.doesNotMatch(source, /private\s+static\s+void\s+ensureDirectory\(/);
    assert.doesNotMatch(source, /new FileOutputStream\(out, false\)/);
    assert.doesNotMatch(source, /directory != null && !directory\.exists\(\) && !directory\.mkdirs\(\)/);
  }
  assert.match(itemRendererFacts, /AngelicaRenderFactFileOps\.ensureOutputFile\(shaderOut\)/);
  assert.doesNotMatch(backendFacts, /private\s+static\s+void\s+ensureDirectory\(/);
  assert.doesNotMatch(backendFacts, /new FileOutputStream\(out, false\)/);
});

test('Angelica framebuffer capture writer owns capture filtering and capture stream rows', () => {
  assert.match(framebufferCaptureFacts, /final class AngelicaFramebufferCaptureFactsWriter/);
  assert.match(framebufferCaptureFacts, /Angelica framebuffer capture schema root must not be null/);
  assert.match(framebufferCaptureFacts, /Angelica framebuffer capture render assets must not be null/);
  assert.match(framebufferCaptureFacts, /Collections\.unmodifiableList\(new ArrayList<CanonicalRenderAsset>\(renderAssets\)\)/);
  assert.doesNotMatch(framebufferCaptureFacts, /Collections\.<CanonicalRenderAsset>emptyList\(\)/);
  assert.match(framebufferCaptureFacts, /isFramebufferCaptureAsset\(/);
  assert.match(framebufferCaptureFacts, /schemaRoot \+ "\/framebuffer-capture"/);
  assert.match(framebufferCaptureFacts, /existing-render-dispatcher-capture/);
  assert.match(framebufferCaptureFacts, /rendererContract/);
  assert.match(framebufferCaptureFacts, /captureAssetIds/);
  assert.match(framebufferCaptureFacts, /framebufferCapturesWithoutFramesSamples/);
});
