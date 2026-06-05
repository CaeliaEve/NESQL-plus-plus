import fs from 'node:fs';
import assert from 'node:assert/strict';

const sidecar = fs.readFileSync('src/main/java/com/github/dcysteine/nesql/exporter/local/RawExportSidecarWriter.java', 'utf8');
const writer = fs.readFileSync('src/main/java/com/github/dcysteine/nesql/exporter/local/AngelicaRenderFactsWriter.java', 'utf8');
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
  'spritesMissingTiming',
  'unknownSpecialRenderers',
  'new AngelicaRenderFactsWriter(entityManager, rawDir, renderAssets).write()'
]) {
  assert(sidecar.includes(required), `Raw sidecar missing ${required}`);
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
  'preferredExport'
]) {
  assert(writer.includes(required), `Angelica writer missing ${required}`);
}

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
  assert(writer.includes(required), `Framebuffer capture fact writer missing ${required}`);
}

assert(
  writer.includes('native_sprite_metadata')
    && writer.includes('return false;')
    && writer.includes('isFramebufferCaptureAsset'),
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
  assert(writer.toLowerCase().includes(rendererClass.toLowerCase()), `Renderer classifier missing ${rendererClass}`);
  assert(writer.includes(family), `Renderer classifier missing ${family}`);
}

assert(
  writer.includes('mapRegisteredSprites') && writer.includes('Map.class.isAssignableFrom(field.getType())'),
  'Texture sprite export must scan TextureMap map fields when MCP names differ at runtime'
);

assert(
  /new RendererClassification\("generic\.iitemrenderer", false, false/.test(writer),
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
  assert(writer.includes(required), `Texture sprite timeline export missing ${required}`);
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
