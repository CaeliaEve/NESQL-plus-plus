import assert from 'node:assert/strict';
import test from 'node:test';
import { audit } from '../src/audit.mjs';
import { mkdtemp, readFile, rm, writeFile } from 'node:fs/promises';
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
import { fileURLToPath } from 'node:url';
import os from 'node:os';
import path from 'node:path';
import { createHash } from 'node:crypto';

const id = n => `category_${String(n).padStart(64, '0')}`;
function fixture() {
  const handlers = [
    ['gregtech.nei.GTNEIDefaultHandler', 'recipe', 'passed'],
    ['unverified.SpecialHandler', 'recipe', 'failed'],
    ['example.Structures', 'domain', 'pending'],
    ['codechicken.nei.recipe.ProfilerRecipeHandler', 'tooling', 'excluded'],
  ].map(([handler, classification, status], n) => ({
    id: id(n), name: `handler ${n}`, source: { owner: 'fixture', handler, key: `source-${n}` },
    classification, route: `${classification}:fixture`, implementationStatus: 'implemented', status,
  }));
  return { worklist: { handlers }, checkpoint: { version: '0.15.0', runId: 'fixture', fingerprint: 'a'.repeat(64),
    handlers: Object.fromEntries(handlers.map(h => [h.id, { id: h.id, status: h.status, total: h.status === 'passed' ? 4 : null,
      checked: h.status === 'passed' ? 4 : 0, unexamined: h.status === 'passed' ? 0 : null, failed: [], excluded: [],
      error: h.status === 'failed' ? { code: 'invalid_row_total', message: 'No count' } : undefined }])) } };
}

test('source audit preserves actual coverage and never promotes a mapped implementation', () => {
  const { worklist, checkpoint } = fixture();
  const before = JSON.stringify({ worklist, checkpoint });
  const result = audit(worklist, checkpoint);
  assert.deepEqual(result.summary, { total: 4, passed: 1, failed: 1, pending: 1, excluded: 1, partial: 0 });
  assert.equal(result.handlers[1].observation.status, 'failed');
  assert.equal(result.handlers[1].observation.error.code, 'invalid_row_total');
  assert.equal(result.handlers[1].facts.status, 'unverified');
  assert.equal(result.handlers[0].facts.adapter, 'GtRecipes');
  assert.equal(result.handlers[0].source.key, 'source-0');
  assert.equal(result.handlers[0].display.status, 'unverified');
  assert.equal(result.handlers[2].observation.status, 'pending');
  assert.equal(JSON.stringify({ worklist, checkpoint }), before);
});

test('source audit rejects missing, extra, duplicate and inconsistent handler identities', () => {
  for (const corrupt of [
    ({ checkpoint }) => delete checkpoint.handlers[id(0)],
    ({ checkpoint }) => checkpoint.handlers[id(5)] = { id: id(5), status: 'passed' },
    ({ worklist }) => worklist.handlers.push(worklist.handlers[0]),
    ({ checkpoint }) => checkpoint.handlers[id(0)].id = id(1),
    ({ checkpoint }) => checkpoint.handlers[id(0)].status = 'made-up',
    ({ checkpoint }) => checkpoint.handlers[id(0)].unexamined = 1,
    ({ checkpoint }) => checkpoint.handlers[id(0)].failed = [2],
  ]) {
    const data = fixture(); corrupt(data);
    assert.throws(() => audit(data.worklist, data.checkpoint));
  }
});

test('same handler class retains separate category keys and failure evidence', () => {
  const { worklist, checkpoint } = fixture();
  worklist.handlers[1].source.handler = 'gregtech.nei.GTNEIDefaultHandler';
  checkpoint.handlers[id(1)].error = { code: 'recipe_check_failures', message: 'slot missing' };
  const result = audit(worklist, checkpoint);
  assert.equal(result.handlers[0].facts.adapter, result.handlers[1].facts.adapter);
  assert.notEqual(result.handlers[0].source.key, result.handlers[1].source.key);
  assert.equal(result.handlers[1].observation.status, 'failed');
  assert.equal(result.groups.find(g => g.adapter === 'GtRecipes').handlers.length, 2);
});

test('new native routes are mapped without rewriting old unsupported observations', () => {
  for (const [handler, adapter] of [
    ['ic2.neiIntegration.core.recipehandler.MaceratorRecipeHandler', 'Ic2Recipes'],
    ['ic2.neiIntegration.core.recipehandler.OreWashingRecipeHandler', 'Ic2Recipes'],
    ['ganymedes01.etfuturum.compat.nei.SmokerRecipeHandler', 'SmeltingRecipes'],
    ['fox.spiteful.avaritia.compat.nei.ExtremeShapedRecipeHandler', 'ExtremeRecipes'],
    ['forestry.factory.recipes.nei.NEIHandlerCentrifuge', 'ForestryRecipes'],
    ['forestry.factory.recipes.nei.NEIHandlerStill', 'ForestryRecipes'],
    ['tconstruct.plugins.nei.RecipeHandlerAlloying', 'TinkerRecipes'],
    ['tconstruct.plugins.nei.RecipeHandlerMelting', 'TinkerRecipes'],
    ['tconstruct.plugins.nei.RecipeHandlerCastingTable', 'TinkerRecipes'],
    ['tconstruct.plugins.nei.RecipeHandlerCastingBasin', 'TinkerRecipes'],
  ]) {
    const { worklist, checkpoint } = fixture();
    worklist.handlers[1].source.handler = handler;
    checkpoint.handlers[id(1)].status = 'unsupported';
    const row = audit(worklist, checkpoint).handlers[1];
    assert.equal(row.facts.adapter, adapter);
    assert.equal(row.observation.originalStatus, 'unsupported');
    assert.equal(row.display.status, 'unverified');
  }
});

test('audit CLI hashes original evidence and refuses to overwrite an existing report', async t => {
  const dir = await mkdtemp(path.join(os.tmpdir(), 'nesql-audit-'));
  t.after(() => rm(dir, { recursive: true, force: true }));
  const data = fixture();
  for (const key of ['worklist', 'checkpoint']) await writeFile(path.join(dir, key + '.json'), JSON.stringify(data[key], null, 2));
  const args = [fileURLToPath(new URL('../src/audit.mjs', import.meta.url)), '--worklist', path.join(dir, 'worklist.json'), '--checkpoint', path.join(dir, 'checkpoint.json'), '--output', path.join(dir, 'report.json')];
  await promisify(execFile)(process.execPath, args);
  const bytes = await readFile(path.join(dir, 'report.json'));
  const report = JSON.parse(bytes);
  for (const key of ['worklist', 'checkpoint']) {
    const input = await readFile(path.join(dir, key + '.json'));
    assert.deepEqual(report.evidence[key], { bytes: input.length, sha256: createHash('sha256').update(input).digest('hex') });
  }
  await assert.rejects(() => promisify(execFile)(process.execPath, args), /EEXIST/);
  assert.deepEqual(await readFile(path.join(dir, 'report.json')), bytes);
});
