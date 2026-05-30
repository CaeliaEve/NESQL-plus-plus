import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'fs';
import path from 'path';

const repoRoot = process.cwd();
const readSource = (relativePath) => fs.readFileSync(path.join(repoRoot, relativePath), 'utf8');

const metadata = readSource('src/main/java/com/github/dcysteine/nesql/exporter/main/ExportStageMetadata.java');
const runner = readSource('src/main/java/com/github/dcysteine/nesql/exporter/main/ExportStageRunner.java');
const integrity = readSource('src/main/java/com/github/dcysteine/nesql/exporter/main/ExportIntegrityManifestWriter.java');

test('export stages expose stable incremental families', () => {
  for (const family of [
    'data',
    'images',
    'animated-images',
    'render-contracts',
    'browser-layout',
    'multiblocks',
    'eec-models',
    'recipe-layout-contracts',
    'atlas-pack',
  ]) {
    assert.equal(metadata.includes(`"${family}"`), true, `missing stage family ${family}`);
  }
  assert.equal(runner.includes('ExportStageMetadata.family(stage)'), true);
  assert.equal(runner.includes('skippableByChecksum'), true);
  assert.equal(integrity.includes('String family;'), true);
});

test('export checksums carry previous-run reuse signals without mtime dependence', () => {
  assert.equal(integrity.includes('readPreviousArtifacts'), true);
  assert.equal(integrity.includes('previousSha256'), true);
  assert.equal(integrity.includes('boolean unchanged;'), true);
  assert.equal(integrity.includes('boolean skippableByChecksum;'), true);
  assert.equal(integrity.includes('sha256(file)'), true);
  assert.equal(integrity.includes('file.lastModified()'), false);
  assert.equal(integrity.includes('stats.update(relative(root, file), file.length())'), true);
});

test('render outputs are skipped only when item/render/texture signature matches', () => {
  const job = readSource('src/main/java/com/github/dcysteine/nesql/exporter/util/render/RenderJob.java');
  const dispatcher = readSource('src/main/java/com/github/dcysteine/nesql/exporter/util/render/RenderDispatcher.java');
  const renderer = readSource('src/main/java/com/github/dcysteine/nesql/exporter/util/render/Renderer.java');
  assert.equal(job.includes('getRenderSignature()'), true);
  assert.equal(job.includes('getRenderSignatureFilePath()'), true);
  assert.equal(dispatcher.includes('RenderSignatureSupport.matches(imageDirectory, job)'), true);
  assert.equal(renderer.includes('RenderSignatureSupport.write(imageDirectory, job)'), true);
});

test('atlas reuse is guarded by sprite source hash and atlas page hash', () => {
  const atlas = readSource('src/main/java/com/github/dcysteine/nesql/exporter/local/CanonicalAtlasPackWriter.java');
  assert.equal(atlas.includes('sourceSignature'), true);
  assert.equal(atlas.includes('atlasPageSha256'), true);
  assert.equal(atlas.includes('sourceSha256'), true);
  assert.equal(atlas.includes('sourceBytes'), true);
  assert.equal(atlas.includes('oldestOutputModified < newestSourceModified'), false);
});

test('Java 25 unsafe shader renderers are exported through contract-safe icons', () => {
  const job = readSource('src/main/java/com/github/dcysteine/nesql/exporter/util/render/RenderJob.java');
  const contract = readSource('src/main/java/com/github/dcysteine/nesql/exporter/util/render/RenderContractMetadataExtractor.java');
  assert.equal(job.includes('isContractSafeRendererClass(normalized)'), true);
  for (const renderer of [
    'universiumrenderer',
    'infinityrenderer',
    'cosmicneutroniumrenderer',
    'glitcheffectrenderer',
    'wireframetesseractrenderer',
    'rainbowoverlayrenderer',
    'gaiaspiritrenderer',
  ]) {
    assert.equal(job.includes(renderer), true, `missing contract-safe renderer ${renderer}`);
  }
  assert.equal(contract.includes('"gt_universium"'), true);
  assert.equal(contract.includes('"botania_gaia_spirit"'), true);
  assert.equal(contract.includes('"gaia_spirit"'), true);
});

test('Botania flower block items are not forced into framebuffer animation registry', () => {
  const registry = readSource('src/main/java/com/github/dcysteine/nesql/exporter/util/render/AnimatedItemRegistry.java');
  assert.equal(registry.includes('animatedItemIds.add("Botania:flower")'), false);
  assert.equal(registry.includes('Plain Botania flowers are static block-item sprites'), true);
});

test('canonical repository snapshot is streamed in bounded pages', () => {
  const snapshot = readSource('src/main/java/com/github/dcysteine/nesql/exporter/local/CanonicalRepositorySnapshotWriter.java');
  assert.equal(snapshot.includes('JsonWriter'), true);
  assert.equal(snapshot.includes('setFirstResult(offset)'), true);
  assert.equal(snapshot.includes('setMaxResults(RECIPE_BATCH_SIZE)'), true);
  assert.equal(snapshot.includes('entityManager.clear()'), true);
  assert.equal(snapshot.includes('new CanonicalRepositoryModel()'), false);
  assert.equal(snapshot.includes('gson.toJson(model, writer)'), false);
  assert.equal(snapshot.includes('List<Recipe> recipes =\n                entityManager.createQuery'), false);
  assert.equal(snapshot.includes('List<GregTechRecipe> gtRecipes =\n                entityManager.createQuery'), false);
});

test('GTNH 2.8.4 export hardening keeps edge recipes instead of dropping them', () => {
  const idUtil = readSource('src/main/java/com/github/dcysteine/nesql/exporter/util/IdUtil.java');
  const itemFactory = readSource('src/main/java/com/github/dcysteine/nesql/exporter/plugin/base/factory/ItemFactory.java');
  const avaritia = readSource('src/main/java/com/github/dcysteine/nesql/exporter/plugin/avaritia/ExtremeCraftingProcessor.java');
  const botaniaBrew = readSource('src/main/java/com/github/dcysteine/nesql/exporter/plugin/botania/BrewRecipeProcessor.java');
  const recipeBuilder = readSource('src/main/java/com/github/dcysteine/nesql/exporter/plugin/base/factory/RecipeBuilder.java');
  assert.equal(idUtil.includes('findUniqueIdentifierSafely'), true);
  assert.equal(idUtil.includes('catch (Throwable ignored)'), true);
  assert.equal(itemFactory.includes('IdUtil.findUniqueIdentifierSafely'), true);
  assert.equal(avaritia.includes('readRecipeOutput(recipe)'), true);
  assert.equal(avaritia.includes('getRecipeOutput'), true);
  assert.equal(avaritia.includes('readRecipeInputs(recipe)'), true);
  assert.equal(botaniaBrew.includes('readBrewRecipesReflectively'), true);
  assert.equal(botaniaBrew.includes('"brewRecipes"'), true);
  assert.equal(botaniaBrew.includes('getManaUsage'), true);
  assert.equal(recipeBuilder.includes('Skipping item input index for shapeless recipe!'), false);
  assert.equal(recipeBuilder.includes('Skipping item output index for shapeless recipe!'), false);
});

test('block face export skips known invalid metadata before noisy mod icon lookups', () => {
  const blockFaces = readSource('src/main/java/com/github/dcysteine/nesql/exporter/main/BlockFaceExporter.java');
  assert.equal(blockFaces.includes('shouldSkipBlockFaceMeta(blockName, meta)'), true);
  assert.equal(blockFaces.includes('"compactkineticgenerators:BlockCkg"'), true);
  assert.equal(blockFaces.includes('return meta < 0 || meta > 11;'), true);
});

test('special facts keep domain aliases needed by runtime coverage gates', () => {
  const sidecar = readSource('src/main/java/com/github/dcysteine/nesql/exporter/local/RawExportSidecarWriter.java');
  const nei = readSource('src/main/java/com/github/dcysteine/nesql/exporter/plugin/nei/NeiRecipeExportProcessor.java');
  assert.equal(sidecar.includes('copyFirstNumber(facts, "altarTier"'), true);
  assert.equal(sidecar.includes('copyElement(facts, "entityId", recipe, "metadata.mobName")'), true);
  assert.equal(sidecar.includes('copyElement(facts, "species", recipe, "metadata.beeSpecies")'), true);
  assert.equal(sidecar.includes('copyElement(facts, "temperature", recipe, "metadata.temperature")'), true);
  assert.equal(sidecar.includes('copyElement(facts, "humidity", recipe, "metadata.humidity")'), true);
  assert.equal(nei.includes('copyFirstExistingForestryFact(metadata, recipe, "allele"'), true);
  assert.equal(nei.includes('extractForestryEnvironmentFromRequirements(metadata, requirements)'), true);
});
