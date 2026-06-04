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
  'framebuffer-capture',
  'existing-render-dispatcher-capture',
  'timelineStatus',
  'knownSpecialRendererUnclassified'
]) {
  assert(writer.includes(required), `Angelica writer missing ${required}`);
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
