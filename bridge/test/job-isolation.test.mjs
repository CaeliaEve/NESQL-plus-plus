import assert from 'node:assert/strict';
import { createServer } from 'node:http';
import { once } from 'node:events';
import { mkdtemp, mkdir, readFile, writeFile, rm } from 'node:fs/promises';
import { randomUUID } from 'node:crypto';
import os from 'node:os';
import path from 'node:path';
import test from 'node:test';
import { AcceptanceCoordinator } from '../src/coordinator.mjs';
import { digest } from '../src/plan.mjs';

const handler = 'category_' + 'a'.repeat(64);

// Real local HTTP and files; only the game work is simulated. This models the
// producer's durable retry map, not a mock of the coordinator under test.
async function fixture(t) {
  const root = await mkdtemp(path.join(os.tmpdir(), 'nesql-job-isolation-'));
  t.after(() => rm(root, { recursive: true, force: true }));
  const mod = path.join(root, 'candidate.jar');
  await writeFile(mod, 'candidate-one');
  let session = randomUUID(), serial = 0, transform = job => job;
  const jobs = new Map(), byKey = new Map();
  const game = createServer(async (request, response) => {
    const chunks = [];
    for await (const chunk of request) chunks.push(chunk);
    const body = chunks.length ? JSON.parse(Buffer.concat(chunks)) : null;
    response.setHeader('Content-Type', 'application/json');
    response.setHeader('X-NESQL-Session', session);
    if (request.url === '/game') {
      response.end(JSON.stringify({ ready: true, exporter: 'test', revision: 40, world: { folder: 'world' },
        sources: { valid: true, rows: [{ id: 'nesql-exporter', valid: true, path: mod }] } }));
    } else if (request.url === '/checks' || request.url === '/jobs' && request.method === 'POST') {
      let job = byKey.get(body.key);
      if (!job) {
        const id = randomUUID(), created = new Date().toISOString(); serial++;
        const check = request.url === '/checks';
        const nativeRequest = { key: body.key, name: check ? 'check' : body.name, profile: check ? 'data' : body.profile, world: body.world,
          handlers: body.handlers ?? [], probes: body.probes ?? [{ count: 1, channels: {} }],
          ...(check ? { check: { domain: body.domain, offset: body.offset ?? 0, limit: body.limit ?? 128,
            controllers: body.controllers ?? [], resources: body.resources ?? [], render: body.render ?? false, contentAudit: body.contentAudit ?? false } } : {}),
          ...(body.scope ? { scope: body.scope } : {}) };
        const provenance = { revision: 1, environment: 'b'.repeat(64), runtime: 'c'.repeat(64), session: 'd'.repeat(64), selection: 'e'.repeat(64) };
        const file = path.join(root, id + '.json');
        const bytes = Buffer.from(JSON.stringify({ format: 'nesql.check', job: id, created, status: 'complete',
          request: nativeRequest, provenance,
          environment: { mods: [{ id: 'nesql-exporter', version: 'test', sha256: digest(await readFile(mod)) }] },
          rows: [{ handler, status: 'passed', totalRecipes: 1, checkedRecipes: 1, offset: 0, end: 1,
            failedRecipes: [], failures: [], excludedRecipes: [], ...(body.render ? {
              visualSamples: [{ index: 0, icons: 0, pictures: 0, pixels: '0', visiblePixels: '0', programs: '0', programNodes: '0', programImages: '0', programPixels: '0' }]
            } : {}) }] }));
        await writeFile(file, bytes);
        job = { id, created, request: nativeRequest, provenance, state: check ? 'checked' : 'succeeded',
          ...(check ? { report: { path: file, bytes: bytes.length, sha256: digest(bytes) } } : { result: { path: path.join(root, 'source') } }) };
        jobs.set(id, job); byKey.set(body.key, job);
      }
      response.end(JSON.stringify({ id: job.id }));
    } else if (request.url.startsWith('/jobs/')) {
      response.end(JSON.stringify({ job: await transform(structuredClone(jobs.get(request.url.slice(6)))) }));
    } else {
      response.statusCode = 404; response.end('{}');
    }
  });
  game.listen(0, '127.0.0.1'); await once(game, 'listening');
  t.after(() => { game.closeAllConnections(); game.close(); });
  async function runner(runId) {
    const instance = await mkdtemp(path.join(root, 'instance-'));
    await mkdir(path.join(instance, 'nesql'));
    await writeFile(path.join(instance, 'nesql/connection.json'), JSON.stringify({ protocol: 1, port: game.address().port, token: 'a'.repeat(64), session }));
    const coordinator = new AcceptanceCoordinator(instance, { runId });
    const plan = { expectedExporter: 'test', expectedRevision: 40, world: 'world', modSha256: digest(await readFile(mod)), handlers: [{ id: handler, classification: 'recipe' }],
      stages: { source: { handlers: [handler], preflight: true, execute: false } } };
    return { coordinator, plan };
  }
  return { root, mod, runner, get serial() { return serial; }, get jobs() { return [...jobs.values()]; },
    newSession() { session = randomUUID(); return session; }, transform(fn) { transform = fn; } };
}

test('durable check keys separate complete run IDs, candidate/session and fresh checkpoints', async t => {
  const f = await fixture(t);
  for (const [index, runId] of ['first-batch-zh-03', 'second-batch-zh-03', 'second-batch-zh-03', 'second-batch-zh-03', 'second-batch-zh-03'].entries()) {
    if (index === 2) f.newSession();
    if (index === 3) await writeFile(f.mod, 'candidate-two');
    const { coordinator, plan } = await f.runner(runId);
    const checkpoint = await coordinator.runPlan(plan);
    assert.equal(checkpoint.handlers[handler].status, 'passed');
    assert.equal(f.serial, index + 1, 'A new scope must execute new work despite a matching run suffix');
    assert.ok(f.jobs.at(-1).request.key.length <= 80);
    await new AcceptanceCoordinator(coordinator.instance).runPlan(plan);
    assert.equal(f.serial, index + 1, 'Same-checkpoint resume must reuse completed work');
  }
  const { coordinator, plan } = await f.runner('explicit-first-run');
  await coordinator.runPlan(plan);
  const before = f.serial;
  await new AcceptanceCoordinator(coordinator.instance, { runId: 'explicit-new-run' }).runPlan(plan);
  assert.equal(f.serial, before + 1, 'An explicitly new run must not silently become the persisted old run');
});

test('stale checked, failed and cancelled jobs stop before business accounting; current retries remain usable', async t => {
  const f = await fixture(t);
  for (const state of ['checked', 'failed', 'cancelled']) {
    const { coordinator, plan } = await f.runner('stale-' + state);
    f.transform(job => ({ ...job, created: '2020-01-01T00:00:00Z', state,
      error: { code: 'native_oom', fatal: true, details: { type: 'java.lang.OutOfMemoryError', fatal: true } } }));
    await assert.rejects(() => coordinator.runPlan(plan), /Job scope/);
    const checkpoint = JSON.parse(await readFile(coordinator.checkpointPath));
    assert.equal(checkpoint.handlers[handler].checked, 0);
    assert.equal(checkpoint.handlers[handler].status, 'pending');
    assert.deepEqual(checkpoint.failures, []);
    assert.equal(checkpoint.scopeStop.code, 'job_scope_mismatch');
    assert.ok(checkpoint.activeJob, 'Keep the rejected job reference for audit');
    const before = f.serial;
    f.transform(job => job);
    const resumed = await new AcceptanceCoordinator(coordinator.instance).runPlan(plan);
    assert.equal(resumed.handlers[handler].status, 'passed');
    assert.equal(f.serial, before, 'A valid interrupted Job should not be submitted twice');
  }
});

test('hash-valid reports cannot substitute a different job, request, timestamp, provenance or candidate', async t => {
  const f = await fixture(t);
  const mutations = [
    report => { report.job = randomUUID(); },
    report => { report.request.key = 'old-key'; },
    report => { report.created = '2020-01-01T00:00:00Z'; },
    report => { report.provenance.session = 'f'.repeat(64); },
    report => { report.environment.mods[0].sha256 = 'f'.repeat(64); }
  ];
  for (const [index, mutate] of mutations.entries()) {
    const { coordinator, plan } = await f.runner('wrong-report-' + index);
    f.transform(async job => {
      const report = JSON.parse(await readFile(job.report.path)); mutate(report);
      const bytes = Buffer.from(JSON.stringify(report)); await writeFile(job.report.path, bytes);
      return { ...job, report: { ...job.report, bytes: bytes.length, sha256: digest(bytes) } };
    });
    await assert.rejects(() => coordinator.runPlan(plan), /Job scope/);
    const checkpoint = JSON.parse(await readFile(coordinator.checkpointPath));
    assert.equal(checkpoint.handlers[handler].checked, 0);
    assert.equal(checkpoint.handlers[handler].status, 'pending');
    assert.deepEqual(checkpoint.archivedReports, []);
  }
});

test('visual and Source scopes reject old terminal results and never publish a false success', async t => {
  const f = await fixture(t), { coordinator, plan } = await f.runner('phase-check');
  const checkpoint = await coordinator.runPlan(plan);
  f.transform(job => job.request.key.startsWith('vis-') ? { ...job, created: '2020-01-01T00:00:00Z' } : job);
  await assert.rejects(() => coordinator.runVisualStage(plan, checkpoint), /Job scope/);
  assert.equal(checkpoint.stages.preflight.status, 'stopped');
  delete checkpoint.stages.preflight;
  f.transform(job => job);
  assert.equal((await coordinator.runVisualStage(plan, checkpoint)).status, 'passed');
  f.transform(job => job.request.key.startsWith('src-') ? { ...job, created: '2020-01-01T00:00:00Z' } : job);
  await assert.rejects(() => coordinator.runSourceStage(plan, checkpoint), /Job scope/);
  assert.notEqual(checkpoint.stages.source?.status, 'passed');
  assert.ok(checkpoint.activeExport, 'Retain rejected Source Job evidence');
});

test('automatic transport rediscovery cannot move an active acceptance run into a new game session', async t => {
  const f = await fixture(t), { coordinator, plan } = await f.runner('session-drift');
  const send = coordinator.client.request.bind(coordinator.client);
  coordinator.client.request = async (...args) => {
    const result = await send(...args);
    if (args[0] === 'POST') {
      const file = path.join(coordinator.instance, 'nesql/connection.json');
      const connection = JSON.parse(await readFile(file)); connection.session = f.newSession();
      await writeFile(file, JSON.stringify(connection));
    }
    return result;
  };
  await assert.rejects(() => coordinator.runPlan(plan), /Job scope/);
  const checkpoint = JSON.parse(await readFile(coordinator.checkpointPath));
  assert.equal(checkpoint.handlers[handler].checked, 0);
});
