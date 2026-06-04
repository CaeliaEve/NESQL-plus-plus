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

test('semantic family plugin foundation is present for native NBT semantics', () => {
  const semanticFamily = readSource('src/main/java/com/github/dcysteine/nesql/exporter/semantic/SemanticFamily.java');
  const registry = readSource('src/main/java/com/github/dcysteine/nesql/exporter/semantic/SemanticFamilyRegistry.java');
  const parsedNbt = readSource('src/main/java/com/github/dcysteine/nesql/exporter/semantic/ParsedNbt.java');
  const mapper = readSource('src/main/java/com/github/dcysteine/nesql/exporter/semantic/SemanticItemIdentityMapper.java');

  for (const method of [
    'boolean matches(Item item, ParsedNbt nbt)',
    'String publicIdentity(Item item, ParsedNbt nbt)',
    'String variantIdentity(Item item, ParsedNbt nbt, String payloadHash)',
    'Map<String, String> facets(Item item, ParsedNbt nbt)',
    'String sortKey(Item item, ParsedNbt nbt, Map<String, String> facets)',
    'int representativePriority(Item item, ParsedNbt nbt)',
  ]) {
    assert.equal(semanticFamily.includes(method), true, `missing SemanticFamily contract method ${method}`);
  }

  assert.equal(parsedNbt.includes('public static ParsedNbt parse(String raw)'), true);
  assert.equal(parsedNbt.includes('public String first(String... keys)'), true);
  assert.equal(parsedNbt.includes('KEY_VALUE_PATTERN'), true);
  assert.equal(registry.includes('public static SemanticFamily match(Item item, ParsedNbt nbt)'), true);
  assert.equal(registry.includes('BuildCraftFacadeFamily'), true);
  assert.equal(registry.includes('ThaumcraftWandFamily'), true);
  assert.equal(registry.includes('TConstructPartFamily'), true);
  assert.equal(registry.includes('TGregworksPartFamily'), true);
  assert.equal(registry.includes('GregTechToolFamily'), true);
  assert.equal(registry.includes('ForestryGeneticsFamily'), true);
  assert.equal(registry.includes('Ic2CropSeedFamily'), true);
  assert.equal(registry.includes('GenericEntityCaptureFamily'), true);
  assert.equal(registry.includes('FluidContainerFamily'), true);
  assert.equal(registry.includes('EncodedPatternFamily'), true);
  assert.equal(registry.includes('CosmeticColorFamily'), true);
  assert.equal(mapper.includes('ParsedNbt.parse(nbt)'), true);
  assert.equal(mapper.includes('SemanticFamilyRegistry.match(item, parsedNbt)'), true);
  assert.equal(mapper.includes('semanticFacets(String family, Item item, ParsedNbt parsedNbt)'), true);
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
  assert.equal(writer.includes('topUnclassifiedFamilyActions'), true);
  assert.equal(writer.includes('inspect-samples-before-classifying'), true);
  assert.equal(writer.includes('SemanticItemIdentityMapper.map(item)'), true);
  assert.equal(writer.includes('"classified".equals(identity.classification)'), true);
});

test('raw export health gates include semantic identity readiness', () => {
  const sidecar = readSource('src/main/java/com/github/dcysteine/nesql/exporter/local/RawExportSidecarWriter.java');

  assert.equal(sidecar.includes('"semantic-identity"'), true);
  assert.equal(sidecar.includes('semanticIdentityMapRows'), true);
  assert.equal(sidecar.includes('semanticUnclassifiedTaggedItems'), true);
});
test('quick semantic check refreshes raw export readiness reports', () => {
  const quickCheck = readSource('src/main/java/com/github/dcysteine/nesql/exporter/main/SemanticIdentityQuickCheckRunner.java');

  assert.equal(quickCheck.includes('refreshRawExportReports(rawExportDirectory, summary);'), true);
  assert.equal(quickCheck.includes('export_report.json'), true);
  assert.equal(quickCheck.includes('semantic-identity'), true);
  assert.equal(quickCheck.includes('semanticIdentityMapRows'), true);
});
