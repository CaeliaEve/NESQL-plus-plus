import fs from 'node:fs';
import assert from 'node:assert/strict';

const writer = fs.readFileSync('src/main/java/com/github/dcysteine/nesql/exporter/local/AngelicaRenderFactsWriter.java', 'utf8');
const backendWriter = fs.readFileSync('src/main/java/com/github/dcysteine/nesql/exporter/local/AngelicaRenderBackendFactsWriter.java', 'utf8');
const textureSpriteWriter = fs.readFileSync('src/main/java/com/github/dcysteine/nesql/exporter/local/AngelicaRenderTextureSpriteFactsWriter.java', 'utf8');
const itemRendererWriter = fs.readFileSync('src/main/java/com/github/dcysteine/nesql/exporter/local/AngelicaRenderItemRendererFactsWriter.java', 'utf8');
const framebufferCaptureWriter = fs.readFileSync('src/main/java/com/github/dcysteine/nesql/exporter/local/AngelicaFramebufferCaptureFactsWriter.java', 'utf8');
const renderFactFileOps = fs.readFileSync('src/main/java/com/github/dcysteine/nesql/exporter/local/AngelicaRenderFactFileOps.java', 'utf8');
const rendererClassificationCatalog = fs.readFileSync('src/main/java/com/github/dcysteine/nesql/exporter/local/AngelicaRendererClassificationCatalog.java', 'utf8');
const rawFileCatalog = fs.readFileSync('src/main/java/com/github/dcysteine/nesql/exporter/local/RawExportFileCatalog.java', 'utf8');
const writerSurface = [writer, backendWriter, textureSpriteWriter, itemRendererWriter, framebufferCaptureWriter, rendererClassificationCatalog, renderFactFileOps].join('\n');
const manifestBuilder = fs.readFileSync('src/main/java/com/github/dcysteine/nesql/exporter/local/RawExportManifestBuilder.java', 'utf8');
const validationSupport = fs.readFileSync('src/main/java/com/github/dcysteine/nesql/exporter/local/RawExportValidationSupport.java', 'utf8');
const validationAbiCatalog = fs.readFileSync('src/main/java/com/github/dcysteine/nesql/exporter/local/RawExportValidationAbiCatalog.java', 'utf8');
const reportAssembler = fs.readFileSync('src/main/java/com/github/dcysteine/nesql/exporter/local/RawExportReportAssembler.java', 'utf8');
const renderFactProvider = fs.readFileSync('src/main/java/com/github/dcysteine/nesql/exporter/local/RawEntityAndRenderBackendFactStreamProvider.java', 'utf8');
const rawCounts = fs.readFileSync('src/main/java/com/github/dcysteine/nesql/exporter/local/RawExportCounts.java', 'utf8');
const renderFactSurface = [rawFileCatalog, manifestBuilder, validationSupport, validationAbiCatalog, reportAssembler, renderFactProvider, rawCounts, writer, renderFactFileOps].join('\n');
const renderJob = fs.readFileSync('src/main/java/com/github/dcysteine/nesql/exporter/util/render/RenderJob.java', 'utf8');
const renderer = fs.readFileSync('src/main/java/com/github/dcysteine/nesql/exporter/util/render/Renderer.java', 'utf8');
const glSnapshot = fs.readFileSync('src/main/java/com/github/dcysteine/nesql/exporter/util/render/AngelicaGlStateSnapshot.java', 'utf8');

for (const required of [
  'angelicaNativeRenderFacts',
  'facts/render/backend.json',
  'facts/render/texture-sprites.jsonl.gz',
  'facts/render/item-renderers.jsonl.gz',
  'facts/render/shader-items.jsonl.gz',
  'facts/render/framebuffer-captures.jsonl.gz',
  'angelica-render-facts',
  'renderTextureSpritesMissingTiming',
  'renderUnknownSpecialRenderers',
  'renderShaderItemsMissingCaptureSamples',
  'renderFramebufferCapturesWithoutFramesSamples',
  'spritesMissingTiming',
  'unknownSpecialRenderers',
  'Angelica render facts render assets must not be null',
  'Angelica render fact output parent exists but is not a directory',
  'Angelica render fact output path exists but is not a file',
  'AngelicaRenderFactFileOps.ensureDirectory',
  'new AngelicaRenderFactsWriter(context.entityManager, context.rawDir, context.renderAssets).write()'
]) {
  assert(renderFactSurface.includes(required), `Render fact surface missing ${required}`);
}

for (const required of [
  'com.gtnewhorizons.angelica.glsm.GLStateManager',
  'net.irisshaders.iris.api.v0.IrisApi',
  'mapUploadedSprites',
  'animationMetadata',
  'framesTextureData',
  'MinecraftForgeClient.getItemRenderer',
  'avaritia.cosmic',
  'avaritia.halo',
  'gtnhlib.textured-item',
  'angelica-framebuffer-capture',
  'browserReimplementationAllowed',
  'textureHints',
  'getMaskTexture',
  'getHaloTexture',
  'getOverlayIcon',
  'getMaskIcon',
  'getFrameIcon',
  'framebuffer-capture',
  'existing-render-dispatcher-capture',
  'timelineStatus',
  'knownSpecialRendererUnclassified',
  'native-render-tick',
  'requiresFramebufferCapture',
  'captureRequired',
  'preferredExport',
  'shaderItemsMissingCaptureSamples',
  'framebufferCapturesWithoutFramesSamples'
]) {
  assert(writerSurface.includes(required), `Angelica writer surface missing ${required}`);
}


for (const [name, source, fileOp] of [
  ['backend', backendWriter, 'AngelicaRenderFactFileOps.createUtf8JsonWriter(out)'],
  ['texture-sprite', textureSpriteWriter, 'AngelicaRenderFactFileOps.createUtf8JsonlWriter(out)'],
  ['item-renderer', itemRendererWriter, 'AngelicaRenderFactFileOps.createUtf8JsonlWriter(out)'],
  ['framebuffer-capture', framebufferCaptureWriter, 'AngelicaRenderFactFileOps.createUtf8JsonlWriter(out)'],
]) {
  assert(source.includes(fileOp), `${name} writer must use shared Angelica render fact file ops`);
  assert(!/private\s+static\s+void\s+ensureDirectory\(/.test(source), `${name} writer must not keep local directory fallbacks`);
  assert(!/new FileOutputStream\(out, false\)/.test(source), `${name} writer must not open render fact artifacts directly`);
  assert(!source.includes('directory != null && !directory.exists() && !directory.mkdirs()'), `${name} writer must fail closed through shared file ops`);
}

assert(
  framebufferCaptureWriter.includes('Angelica framebuffer capture render assets must not be null')
    && framebufferCaptureWriter.includes('Collections.unmodifiableList(new ArrayList<CanonicalRenderAsset>(renderAssets))')
    && !framebufferCaptureWriter.includes('Collections.<CanonicalRenderAsset>emptyList()'),
  'Framebuffer capture writer must own and freeze render assets without null-to-empty fallback'
);

for (const required of [
  'asset.framePattern',
  'asset.frameCount',
  'asset.capturedFrameCount',
  'asset.configuredFrameCount',
  'asset.frameDurationMs',
  'asset.frameDurationSource',
  'GSON.toJsonTree(asset.frames)',
  'GSON.toJsonTree(asset.timeline)',
  'GSON.toJsonTree(asset.captureContract)',
]) {
  assert(writerSurface.includes(required), `Framebuffer capture fact writer missing ${required}`);
}

assert(
  writerSurface.includes('native_sprite_metadata')
    && writerSurface.includes('return false;')
    && writerSurface.includes('isFramebufferCaptureAsset'),
  'Framebuffer capture fact writer must exclude native_sprite_metadata assets from the framebuffer stream'
);

for (const [rendererClass, family] of [
  ['CosmicItemRenderer', 'avaritia.cosmic'],
  ['CosmicBowRenderer', 'avaritia.cosmic-bow'],
  ['FancyHaloRenderer', 'avaritia.halo'],
  ['FracturedOreRenderer', 'avaritia.fractured-ore'],
  ['EternalItemRenderer', 'eternalsingularity.combined'],
  ['ItemRendererCompressedChest', 'avaritiaddons.compressed-chest'],
  ['ItemRendererInfinityChest', 'avaritiaddons.infinity-chest'],
  ['appeng.client.render.ItemRenderer', 'ae2.item-renderer'],
  ['RendererTrophy', 'amazingtrophies.trophy'],
  ['TexturedItemRenderer', 'gtnhlib.textured-item'],
  ['ModelISBRH', 'gtnhlib.model-isbrh'],
]) {
  assert(writerSurface.toLowerCase().includes(rendererClass.toLowerCase()), `Renderer classifier missing ${rendererClass}`);
  assert(writerSurface.includes(family), `Renderer classifier missing ${family}`);
}

assert(
  writerSurface.includes('mapRegisteredSprites') && writerSurface.includes('Map.class.isAssignableFrom(field.getType())'),
  'Texture sprite export must scan TextureMap map fields when MCP names differ at runtime'
);

assert(
  /classification\(\s*"generic\.iitemrenderer",\s*false,\s*false/.test(writerSurface),
  'Generic IItemRenderer must not force massive framebuffer capture without a known native animation family'
);

for (const required of [
  'metadata.getFrameTime()',
  'metadata.getFrameCount()',
  'getFrameIndex',
  'getFrameTimeSingle',
  'durationTicks',
  'durationMs',
]) {
  assert(writerSurface.includes(required), `Texture sprite timeline export missing ${required}`);
}

assert(
  /public boolean shouldUseContractStaticRenderOnly\(\)\s*\{\s*return false;\s*\}/.test(renderJob),
  'RenderJob must not bypass known special renderer framebuffer capture'
);

for (const required of [
  'AngelicaGlStateSnapshot.capture()',
  'glStateSnapshot.restore(job)',
]) {
  assert(renderer.includes(required), `Renderer missing ${required}`);
}

for (const required of [
  'GL_DRAW_FRAMEBUFFER_BINDING',
  'GL_READ_FRAMEBUFFER_BINDING',
  'GL_CURRENT_PROGRAM',
  'GL_ACTIVE_TEXTURE',
  'OpenGlHelper.setActiveTexture',
  'glBindTexture',
  'glViewport',
  'glScissor',
]) {
  assert(glSnapshot.includes(required), `Angelica GL snapshot missing ${required}`);
}

console.log('Angelica render facts regression checks passed.');
