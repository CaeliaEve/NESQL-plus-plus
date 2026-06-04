import fs from 'node:fs';
import assert from 'node:assert/strict';

const sidecar = fs.readFileSync('src/main/java/com/github/dcysteine/nesql/exporter/local/RawExportSidecarWriter.java', 'utf8');
const writer = fs.readFileSync('src/main/java/com/github/dcysteine/nesql/exporter/local/AngelicaRenderFactsWriter.java', 'utf8');

for (const required of [
  'angelicaNativeRenderFacts',
  'facts/render/backend.json',
  'facts/render/texture-sprites.jsonl.gz',
  'facts/render/item-renderers.jsonl.gz',
  'facts/render/shader-items.jsonl.gz',
  'facts/render/framebuffer-captures.jsonl.gz',
  'angelica-render-facts',
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
  'framebuffer-capture',
  'existing-render-dispatcher-capture'
]) {
  assert(writer.includes(required), `Angelica writer missing ${required}`);
}

console.log('Angelica render facts regression checks passed.');
