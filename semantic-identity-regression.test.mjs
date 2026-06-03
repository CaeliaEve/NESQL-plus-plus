import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'fs';
import path from 'path';

const repoRoot = process.cwd();
const readSource = (relativePath) => fs.readFileSync(path.join(repoRoot, relativePath), 'utf8');

test('semantic mapper covers high-volume NBT families from GTNH exports', () => {
  const mapper = readSource('src/main/java/com/github/dcysteine/nesql/exporter/semantic/SemanticItemIdentityMapper.java');

  for (const family of [
    '"facade.buildcraft"',
    '"facade.ae2"',
    '"thaumcraft.wand"',
    '"tool.gregtech"',
    '"tool.tconstruct"',
    '"toolpart.tconstruct"',
    '"toolpart.tgregworks"',
    '"genetics.forestry"',
    '"genetics.binnie-gendustry"',
    '"entity_capture.generic"',
    '"charge.gregtech"',
    '"charge.generic"',
    '"cosmetic.color"',
    '"crop.ic2"',
    '"fluid.container"',
  ]) {
    assert.equal(mapper.includes(family), true, `missing semantic family ${family}`);
  }

  for (const highVolumeSignal of [
    'isGregTechLikeTool',
    'internal.contains("metatool")',
    'payload.contains("gt.toolstats")',
    'payload.contains("primarymaterial") && payload.contains("secondarymaterial")',
    'isTConstructPart',
    'payload.contains("dualmat")',
    'payload.contains("material2")',
    'isTGregworksPart',
    'internal.contains("tgregtoolpart")',
    'isEntityCaptureVariant',
    'internal.contains("mobsoul")',
    'internal.contains("mobcrystal")',
    'isChargedStateVariant',
    'isCosmeticColorVariant',
    'isIc2CropSeed',
    'isFluidContainerVariant',
    'semanticFacets',
    'variantLabel',
    'facetSummary',
  ]) {
    assert.equal(mapper.includes(highVolumeSignal), true, `missing high-volume classifier signal ${highVolumeSignal}`);
  }
});

test('semantic stream writer validates one identity row per raw item', () => {
  const writer = readSource('src/main/java/com/github/dcysteine/nesql/exporter/local/SemanticItemIdentityDiagnosticsWriter.java');

  assert.equal(writer.includes('validateMappedIdentity(item, identity, legacyItemIds);'), true);
  assert.equal(writer.includes('duplicate legacy item id'), true);
  assert.equal(writer.includes('missing publicItemId'), true);
  assert.equal(writer.includes('payload/variant mismatch'), true);
  assert.equal(writer.includes('missing family/classification'), true);
  assert.equal(writer.includes('summary.identityMapRows = streamCounts.identityMapRows;'), true);
  assert.equal(writer.includes('topUnclassifiedFamilies'), true);
  assert.equal(writer.includes('unclassifiedFamilyCounts'), true);
  assert.equal(writer.includes('unclassifiedFamilySamples'), true);
});
