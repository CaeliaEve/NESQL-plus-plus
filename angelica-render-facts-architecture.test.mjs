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
const textureSpriteFactsUrl = new URL(
  './src/main/java/com/github/dcysteine/nesql/exporter/local/AngelicaRenderTextureSpriteFactsWriter.java',
  import.meta.url,
);
const textureSpriteCountsUrl = new URL(
  './src/main/java/com/github/dcysteine/nesql/exporter/local/AngelicaTextureSpriteStreamCounts.java',
  import.meta.url,
);

const renderFacts = readFileSync(renderFactsUrl, 'utf8');
const backendFacts = readFileSync(backendFactsUrl, 'utf8');
const textureSpriteFacts = readFileSync(textureSpriteFactsUrl, 'utf8');

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
