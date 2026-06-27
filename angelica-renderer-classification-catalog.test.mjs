import assert from 'node:assert/strict';
import { existsSync, readFileSync } from 'node:fs';
import test from 'node:test';

const catalogUrl = new URL(
  './src/main/java/com/github/dcysteine/nesql/exporter/local/AngelicaRendererClassificationCatalog.java',
  import.meta.url,
);
const classificationUrl = new URL(
  './src/main/java/com/github/dcysteine/nesql/exporter/local/AngelicaRendererClassification.java',
  import.meta.url,
);
const itemRendererWriterUrl = new URL(
  './src/main/java/com/github/dcysteine/nesql/exporter/local/AngelicaRenderItemRendererFactsWriter.java',
  import.meta.url,
);

const catalog = readFileSync(catalogUrl, 'utf8');
const classification = readFileSync(classificationUrl, 'utf8');
const itemRendererWriter = readFileSync(itemRendererWriterUrl, 'utf8');

test('Angelica renderer classification uses a table-driven catalog', () => {
  assert.equal(existsSync(catalogUrl), true, 'AngelicaRendererClassificationCatalog must exist');
  assert.match(catalog, /final class AngelicaRendererClassificationCatalog/);
  assert.match(catalog, /RENDERER_RULES = Collections\.unmodifiableList\(Arrays\.asList\(/);
  assert.match(catalog, /RendererRule/);
  assert.match(catalog, /CLASSIFICATIONS_BY_KIND = buildClassificationIndex\(\)/);
  assert.match(catalog, /SPECIAL_RENDERER_GAP_TOKENS/);
  for (const token of [
    'cosmicitemrenderer',
    'fancyhalorenderer',
    'fracturedorerenderer',
    'appeng.client.render.itemrenderer',
    'textureditemrenderer',
    'modelisbrh',
  ]) {
    assert.match(catalog, new RegExp(token.replaceAll('.', '\\.')));
  }
});

test('Angelica renderer classification carries shader export metadata', () => {
  assert.match(classification, /final boolean shaderExportEligible/);
  assert.match(classification, /final String shaderFamily/);
  assert.match(classification, /final String shaderTimeSource/);
  assert.match(catalog, /"avaritia\.cosmic", true, true, "avaritia\.cosmic", "native-render-tick"/);
  assert.match(catalog, /"gtnhlib\.textured-item", false, true, "gtnhlib\.textured-item", "native-renderer"/);
  assert.match(catalog, /"ae2\.native-sprite-item-renderer"/);
  assert.match(catalog, /false,\s*"unknown",\s*"native-renderer"/);
});

test('Angelica item renderer writer delegates classification to the catalog', () => {
  assert.match(itemRendererWriter, /AngelicaRendererClassificationCatalog\.classifyItemRenderer\(item, rendererClass\)/);
  assert.match(itemRendererWriter, /AngelicaRendererClassificationCatalog\.isKnownSpecialRendererGap\(item, rendererClass, classification\)/);
  assert.match(itemRendererWriter, /AngelicaRendererClassificationCatalog\.byKind\(rendererKind\)/);
  assert.match(itemRendererWriter, /classification\.shaderExportEligible/);
  assert.match(itemRendererWriter, /classification\.shaderFamily/);
  assert.match(itemRendererWriter, /classification\.shaderTimeSource/);
  assert.doesNotMatch(itemRendererWriter, /lower\.contains\("cosmicitemrenderer"\)/);
  assert.doesNotMatch(itemRendererWriter, /lower\.contains\("appeng\.client\.render\.itemrenderer"\)/);
  assert.doesNotMatch(itemRendererWriter, /rendererKind\.startsWith\("avaritia\."\)/);
  assert.doesNotMatch(itemRendererWriter, /private\s+static\s+String\s+shaderFamily\(/);
  assert.doesNotMatch(itemRendererWriter, /private\s+static\s+String\s+shaderTimeSource\(/);
});
