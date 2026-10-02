import assert from 'node:assert/strict';
import { readFile, writeFile } from 'node:fs/promises';
import { parseArgs } from 'node:util';
import { pathToFileURL } from 'node:url';
import path from 'node:path';
import { digest } from './plan.mjs';

// Exact, reviewed routes in capture/Recipes.java, GtRecipes.java and MagicRecipes.java.
// A shared adapter does not imply identical category semantics or successful capture.
const routes = new Map([
  ...['micdoodle8.mods.galacticraft.core.nei.CircuitFabricatorRecipeHandler', 'de.katzenpapst.amunra.nei.recipehandler.ARCircuitFab']
    .map(name => [name, { adapter: 'CircuitRecipes', registry: 'Galacticraft 3.3.13 CircuitFabricatorRecipes and native machine consumption',
      evidence: 'src/main/java/com/github/dcysteine/nesql/exporter/capture/CircuitRecipes.java' }]),
  ...['core.nei.BuggyRecipeHandler', 'planets.mars.nei.CargoRocketRecipeHandler', 'planets.asteroids.nei.AstroMinerRecipeHandler']
    .map(name => [`micdoodle8.mods.galacticraft.${name}`,
      { adapter: 'SpaceRecipes', registry: 'Galacticraft 3.3.13 native NASA registries and physical slots', evidence: 'src/main/java/com/github/dcysteine/nesql/exporter/capture/SpaceRecipes.java' }]),
  ...['RecipeHandlerAlloying', 'RecipeHandlerMelting', 'RecipeHandlerCastingTable', 'RecipeHandlerCastingBasin'].map(name => [`tconstruct.plugins.nei.${name}`,
    { adapter: 'TinkerRecipes', registry: 'TConstruct 1.13.57 Smeltery registries / Mantle exact metadata keys', evidence: 'src/main/java/com/github/dcysteine/nesql/exporter/capture/TinkerRecipes.java' }]),
  ...['NEIHandlerCentrifuge', 'NEIHandlerStill'].map(name => [`forestry.factory.recipes.nei.${name}`,
    { adapter: 'ForestryRecipes', registry: 'Forestry 4.10.17 RecipeManagers / native factory consumption', evidence: 'src/main/java/com/github/dcysteine/nesql/exporter/capture/ForestryRecipes.java' }]),
  ...['MaceratorRecipeHandler', 'ExtractorRecipeHandler', 'CompressorRecipeHandler', 'MetalFormerRecipeHandlerCutting',
    'MetalFormerRecipeHandlerRolling', 'MetalFormerRecipeHandlerExtruding', 'CentrifugeRecipeHandler', 'OreWashingRecipeHandler']
    .map(name => [`ic2.neiIntegration.core.recipehandler.${name}`,
      { adapter: 'Ic2Recipes', registry: 'IC2 2.2.828 machine recipe registry / IRecipeInput / RecipeOutput', evidence: 'src/main/java/com/github/dcysteine/nesql/exporter/capture/Ic2Recipes.java' }]),
  ...['SmokerRecipeHandler', 'BlastFurnaceRecipeHandler'].map(name => [`ganymedes01.etfuturum.compat.nei.${name}`,
    { adapter: 'SmeltingRecipes', registry: 'Et Futurum 2.6.2.25 inherited smelting, overrides and blacklist', evidence: 'src/main/java/com/github/dcysteine/nesql/exporter/capture/SmeltingRecipes.java' }]),
  ...['ExtremeShapedRecipeHandler', 'ExtremeShapelessRecipeHandler'].map(name => [`fox.spiteful.avaritia.compat.nei.${name}`,
    { adapter: 'ExtremeRecipes', registry: 'Avaritia 1.77 ExtremeCraftingManager', evidence: 'src/main/java/com/github/dcysteine/nesql/exporter/capture/ExtremeRecipes.java' }]),
  ...['gregtech.nei.GTNEIDefaultHandler', 'bartworks.neiHandler.BioLabNEIHandler', 'bartworks.neiHandler.BioVatNEIHandler']
    .map(name => [name, { adapter: 'GtRecipes', registry: 'GTRecipe / RecipeCategory.recipeMap', evidence: 'src/main/java/com/github/dcysteine/nesql/exporter/capture/GtRecipes.java' }]),
  ...[
    ['arcaneworkbench.ArcaneCraftingShapedHandler', 'ShapedArcaneRecipe'],
    ['arcaneworkbench.ArcaneCraftingShapelessHandler', 'ShapelessArcaneRecipe'],
    ['TCNACrucibleRecipeHandler', 'CrucibleRecipe'], ['TCNAInfusionRecipeHandler', 'InfusionRecipe'],
  ].map(([name, kind]) => [`ru.timeconqueror.tcneiadditions.nei.${name}`,
    { adapter: 'MagicRecipes', registry: `ThaumcraftApi.getCraftingRecipes / ${kind}`, evidence: 'src/main/java/com/github/dcysteine/nesql/exporter/capture/MagicRecipes.java' }]),
  ...['ShapedRecipeHandler', 'ShapelessRecipeHandler', 'FurnaceRecipeHandler'].map(name => [`codechicken.nei.recipe.${name}`,
    { adapter: 'Recipes', registry: `NEI ${name} native enumeration`, evidence: 'src/main/java/com/github/dcysteine/nesql/exporter/capture/Recipes.java' }]),
]);

export function audit(worklist, checkpoint) {
  assert.ok(Array.isArray(worklist?.handlers) && worklist.handlers.length, 'A nonempty worklist is required');
  assert.ok(checkpoint?.handlers && !Array.isArray(checkpoint.handlers), 'A checkpoint handler map is required');
  assert.match(checkpoint.fingerprint ?? '', /^[a-f0-9]{64}$/, 'Missing environment fingerprint');
  const ids = new Set();
  const summary = { total: 0, passed: 0, failed: 0, pending: 0, excluded: 0, partial: 0 };
  const groups = new Map();
  const handlers = worklist.handlers.map(item => {
    assert.match(item.id ?? '', /^category_[a-f0-9]{64}$/);
    assert.ok(!ids.has(item.id), `Duplicate handler ${item.id}`); ids.add(item.id);
    assert.ok(item.source?.handler && item.source?.key && item.source?.owner, `Missing native identity ${item.id}`);
    const row = checkpoint.handlers[item.id];
    assert.equal(row?.id, item.id, `Missing or mismatched observation ${item.id}`);
    const status = row.status === 'unsupported' ? 'failed' : row.status;
    assert.ok(['passed', 'failed', 'pending', 'excluded', 'partial'].includes(status), `Unknown status ${row.status}`);
    if (status === 'passed') {
      assert.ok(Number.isSafeInteger(row.total) && row.total >= 0 && row.checked === row.total
        && row.unexamined === 0 && Array.isArray(row.failed) && row.failed.length === 0
        && !row.hasFailures && !row.error, `Incomplete passed observation ${item.id}`);
    }
    summary.total++; summary[status]++;
    const route = item.source.handler === 'galaxyspace.core.nei.RocketRecipeHandler'
      && /^galaxyspace\.core\.nei\.rocket\.RocketT[1-8]RecipeHandler$/.test(item.source.key)
      ? { adapter: 'SpaceRecipes', registry: 'GalaxySpace 1.1.121 RocketRecipes and physical workbench slots', evidence: 'src/main/java/com/github/dcysteine/nesql/exporter/capture/SpaceRecipes.java' }
      : routes.get(item.source.handler);
    const facts = route ? { status: 'code_mapped', ...route }
      : { status: 'unverified', adapter: null, registry: null, evidence: null };
    const groupKey = route?.adapter ?? `review:${item.source.handler}`;
    if (!groups.has(groupKey)) groups.set(groupKey, { key: groupKey, adapter: facts.adapter, handlers: [] });
    groups.get(groupKey).handlers.push(item.id);
    return {
      id: item.id, name: item.name, source: { ...item.source },
      declared: { classification: item.classification, route: item.route, implementation: item.implementationStatus },
      facts, display: { status: 'unverified', reason: 'Recipe diagnostics do not validate rendered views or assets' },
      observation: { status, originalStatus: row.status, total: row.total ?? null, checked: row.checked ?? 0,
        unexamined: row.unexamined ?? null, failed: row.failed?.length ?? 0, excluded: row.excluded?.length ?? 0,
        error: row.error ?? null },
    };
  });
  assert.equal(Object.keys(checkpoint.handlers).length, ids.size, 'Checkpoint has extra handlers');
  return { format: 'nesql.source-audit', revision: 1,
    baseline: { version: checkpoint.version, run: checkpoint.runId, fingerprint: checkpoint.fingerprint },
    summary, handlers, groups: [...groups.values()],
    complete: false, reason: 'Source mapping and recipe diagnostics are not a complete source export or visual acceptance' };
}

async function main() {
  const { values } = parseArgs({ options: { worklist: { type: 'string' }, checkpoint: { type: 'string' }, output: { type: 'string' } } });
  for (const key of ['worklist', 'checkpoint', 'output']) assert.ok(values[key], `--${key} is required`);
  const inputs = {};
  const evidence = {};
  for (const key of ['worklist', 'checkpoint']) {
    const bytes = await readFile(values[key]);
    inputs[key] = JSON.parse(bytes.toString('utf8'));
    evidence[key] = { sha256: digest(bytes), bytes: bytes.length };
  }
  const result = { ...audit(inputs.worklist, inputs.checkpoint), evidence };
  const bytes = Buffer.from(`${JSON.stringify(result, null, 2)}\n`);
  await writeFile(values.output, bytes, { flag: 'wx' });
  console.log(JSON.stringify({ output: path.resolve(values.output), sha256: digest(bytes), bytes: bytes.length, summary: result.summary }));
}

if (process.argv[1] && import.meta.url === pathToFileURL(path.resolve(process.argv[1])).href) {
  main().catch(error => { console.error(error.message); process.exitCode = 1; });
}
