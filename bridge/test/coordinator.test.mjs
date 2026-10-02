import assert from 'node:assert/strict';
import { createServer } from 'node:http';
import { mkdtemp, mkdir, rm, writeFile, readFile } from 'node:fs/promises';
import { existsSync } from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { once } from 'node:events';
import { randomUUID, createHash } from 'node:crypto';
import test from 'node:test';
import { AcceptanceCoordinator, isFatalError } from '../src/coordinator.mjs';
import { resolveReport } from '../src/reports.mjs';
import { CheckpointLog, readCheckpoint } from '../src/checkpoint.mjs';

test('checkpoint journal recovers durable deltas and ignores only an unfinished final write', async t => {
  const directory = await mkdtemp(path.join(os.tmpdir(), 'nesql-journal-'));
  t.after(() => rm(directory, { recursive: true, force: true }));
  const file = path.join(directory, 'checkpoint.json'), log = new CheckpointLog(file);
  const state = { handlers: { one: { offset: 0 } }, activeJob: null };
  await log.save(state, true);
  state.handlers.one.offset = 512; state.activeJob = 'next';
  await log.save(state);
  assert.equal(JSON.parse(await readFile(file)).handlers.one.offset, 0, 'Every update still rewrites the whole snapshot');
  await writeFile(file + '.journal', '{"unfinished":', { flag: 'a' });
  const recovered = await readCheckpoint(file, { repairTail: true });
  assert.equal(recovered.handlers.one.offset, 512); assert.equal(recovered.activeJob, 'next');
  const resumed = new CheckpointLog(file, recovered);
  recovered.handlers.one.offset = 1024; await resumed.save(recovered, true);
  assert.equal((await readCheckpoint(file)).handlers.one.offset, 1024);
  assert.equal((await readFile(file + '.journal')).length, 0);
});

test('compact reports resolve only hash-verified shared evidence and preserve their raw identity', async t => {
  const directory = await mkdtemp(path.join(os.tmpdir(), 'nesql-shared-'));
  t.after(() => rm(directory, { recursive: true, force: true }));
  await mkdir(path.join(directory, 'shared'));
  const bytes = Buffer.from('{"locale":"en_US"}'), sha256 = digestForTest(bytes);
  const reference = { path: `shared/${sha256}.json`, bytes: bytes.length, sha256 };
  await writeFile(path.join(directory, reference.path), bytes);
  const raw = { environmentRef: reference, provenance: { environment: sha256 }, rows: [] };
  const resolved = await resolveReport(raw, directory);
  assert.deepEqual(resolved.environment, { locale: 'en_US' });
  assert.equal(raw.environment, undefined);
  await writeFile(path.join(directory, reference.path), '{"locale":"zh_CN"}');
  await assert.rejects(() => resolveReport(raw, directory), /hash|digest|SHA|mismatch/i);
});

const modBytes = Buffer.from('NESQL protocol fixture');
const modSha256 = createHash('sha256').update(modBytes).digest('hex');
async function fixture(prefix) {
  const instance = await mkdtemp(prefix);
  await writeFile(path.join(instance, 'fixture.jar'), modBytes);
  return instance;
}
function gameState(instance, state) {
  return { sources: { valid: true, rows: [{ id: 'nesql-exporter', valid: true, path: path.join(instance, 'fixture.jar') }] }, ...state };
}

test('AcceptanceCoordinator retains and archives native handler-open errors before checking counts', async t => {
  for (const fatal of [false, true]) {
    const instance = await fixture(path.join(os.tmpdir(), 'nesql-open-'));
    t.after(() => rm(instance, { recursive: true, force: true }));
    await mkdir(path.join(instance, 'nesql'));
    const error = { code: 'handler_unsupported', message: 'No recipe loader for: example.NativeHandler', type: 'Jobs$Fault', fatal, cause: null };
    const raw = Buffer.from(JSON.stringify({ rows: [{ handler: 'native', status: 'failed', error }] }));
    const reportPath = path.join(instance, 'nesql', 'report.json');
    await writeFile(reportPath, raw);
    const coordinator = new AcceptanceCoordinator(instance);
    let checks = 0;
    coordinator.client = { session: 'test-session', request: async (method, url) => {
      if (url === '/game') return gameState(instance, { ready: true, world: { folder: 'test-world' }, exporter: '0.15.0', revision: 14 });
      if (url === '/checks') { checks++; return { id: 'native-job' }; }
      if (url === '/jobs/native-job') return { job: { id: 'native-job', state: 'checked', report: { path: reportPath, bytes: raw.length, sha256: digestForTest(raw) } } };
      throw new Error(`Unexpected request ${method} ${url}`);
    } };
    const plan = { world: 'test-world', modSha256, handlers: [{ id: 'native', name: 'Native' }, { id: 'tool', classification: 'tooling', implementationStatus: 'excluded_justified' }] };
    if (fatal) await assert.rejects(() => coordinator.runPlan(plan), /Fatal.*handler/i);
    else await coordinator.runPlan(plan);
    const checkpoint = JSON.parse(await readFile(coordinator.checkpointPath));
    assert.deepEqual(checkpoint.handlers.native.error, error);
    assert.equal(checkpoint.handlers.native.status, 'failed');
    assert.equal(checkpoint.handlers.native.checked, 0);
    assert.equal(checks, 1);
    assert.equal(checkpoint.archivedReports.length, 1);
    assert.deepEqual(await readFile(checkpoint.archivedReports[0].archivePath), raw);
    assert.equal(checkpoint.handlers.tool?.status, fatal ? undefined : 'excluded');
  }
});

function digestForTest(bytes) { return createHash('sha256').update(bytes).digest('hex'); }

test('commit-memory guard persists a resumable stop before dispatch and allows recovery', async t => {
  const instance = await fixture(path.join(os.tmpdir(), 'nesql-memory-'));
  t.after(() => rm(instance, { recursive: true, force: true }));
  let committedBytes = '99';
  let dispatched = 0;
  const coordinator = new AcceptanceCoordinator(instance, {
    readMemory: async () => ({ committedBytes, commitLimitBytes: '100' }),
  });
  coordinator.client = { session: 'memory-session', request: async (method, url) => {
    if (url === '/game') return gameState(instance, { ready: true, world: { folder: 'test-world' }, exporter: '0.15.0', revision: 14 });
    if (url === '/checks') { dispatched++; throw new Error('dispatch after recovery'); }
    throw new Error(`Unexpected request ${method} ${url}`);
  } };
  const plan = { world: 'test-world', modSha256, handlers: [{ id: 'native' }],
    memory: { minimumFreeCommitBytes: '10', maximumCommitPercent: 95 } };
  await assert.rejects(() => coordinator.runPlan(plan), /resource_pressure/);
  assert.equal(dispatched, 0);
  const checkpoint = JSON.parse(await readFile(coordinator.checkpointPath));
  assert.equal(checkpoint.resourceStop.code, 'resource_pressure');
  assert.equal(checkpoint.resourceStop.sample.freeCommitBytes, '1');
  assert.equal(checkpoint.handlers.native.offset, 0);
  assert.equal(checkpoint.handlers.native.status, 'pending');
  assert.equal(checkpoint.activeJob, null);
  committedBytes = '40';
  await assert.rejects(() => coordinator.runPlan(plan), /dispatch after recovery/);
  assert.equal(dispatched, 1);
  assert.equal(JSON.parse(await readFile(coordinator.checkpointPath)).resourceStop, null);
  const events = (await readFile(coordinator.checkpointPath + '.resources.jsonl', 'utf8')).trim().split('\n').map(JSON.parse);
  assert.deepEqual(events.map(event => event.sample.freeCommitBytes), ['1', '60']);
});

test('large failed scans retain every raw detail but bound repeated checkpoint samples', async t => {
  const instance = await fixture(path.join(os.tmpdir(), 'nesql-bounded-errors-'));
  t.after(() => rm(instance, { recursive: true, force: true }));
  const directory = path.join(instance, 'nesql', 'checks');
  await mkdir(directory, { recursive: true });
  const coordinator = new AcceptanceCoordinator(instance, { pageSize: 40 });
  const jobs = new Map();
  coordinator.client = { session: 'bounded', request: async (method, url, body) => {
    if (url === '/game') return gameState(instance, { ready: true, world: { folder: 'test-world' }, exporter: '0.15.0', revision: 14 });
    if (url === '/checks') {
      const id = `page-${body.offset}`;
      const failures = [], failureFiles = [];
      for (let index = body.offset; index < body.offset + 40; index++) {
        const error = { code: 'magic_recipe', type: 'Jobs$Fault', fatal: false, message: `index ${index}: ${'detail'.repeat(1000)}` };
        failures.push({ index, error });
        const raw = Buffer.from(JSON.stringify({ format: 'nesql.failure', job: id, target: { handler: 'native' }, index, error }));
        const name = `${id}-${index}.json`;
        await writeFile(path.join(directory, name), raw);
        failureFiles.push({ path: name, index, bytes: raw.length, sha256: digestForTest(raw) });
      }
      const row = { handler: 'native', status: 'failed', totalRecipes: 80, offset: body.offset,
        end: body.offset + 40, checkedRecipes: 40, unexamined: 40 - body.offset,
        failedRecipes: failures.map(f => f.index), excludedRecipes: [], failures, failureFiles, failuresOmitted: 0 };
      const raw = Buffer.from(JSON.stringify({ rows: [row] }));
      const reportPath = path.join(directory, `${id}.json`);
      await writeFile(reportPath, raw);
      jobs.set(`/jobs/${id}`, { job: { id, state: 'checked', report: { path: reportPath, bytes: raw.length, sha256: digestForTest(raw) } } });
      return { id };
    }
    if (jobs.has(url)) return jobs.get(url);
    throw new Error(`Unexpected request ${method} ${url}`);
  } };
  const checkpoint = await coordinator.runPlan({ world: 'test-world', modSha256, handlers: [{ id: 'native' }] });
  const state = checkpoint.handlers.native;
  assert.equal(state.status, 'failed');
  assert.equal(state.checked, 80);
  assert.deepEqual(state.failed, Array.from({ length: 80 }, (_, i) => i));
  assert.ok(state.failures.length <= 16, 'repeated full error objects must not grow with recipe count');
  assert.equal(state.failures.length + state.failureSamplesOmitted, 80);
  assert.equal(state.failuresOmitted, 0, 'checkpoint sampling is not producer evidence loss');
  assert.ok(checkpoint.failures.length <= 16, 'global checkpoint must reference page archives instead of repeating every stack');
  assert.equal(checkpoint.archivedReports.length, 2);
  let archived = 0;
  for (const report of checkpoint.archivedReports) for (const file of report.files) {
    const raw = await readFile(file.archivePath);
    assert.equal(digestForTest(raw), file.sha256);
    assert.equal(JSON.parse(raw).index, file.index);
    archived++;
  }
  assert.equal(archived, 80);
});

test('AcceptanceCoordinator archives resource failures using the resource identity', async t => {
  const instance = await fixture(path.join(os.tmpdir(), 'nesql-resource-detail-'));
  t.after(() => rm(instance, { recursive: true, force: true }));
  const directory = path.join(instance, 'nesql', 'checks'); await mkdir(directory, { recursive: true });
  const detail = Buffer.from(JSON.stringify({ format: 'nesql.failure', job: 'resource-job', target: { resource: 'demo:textures/a.png' }, index: 0, error: { code: 'resource_limit' } }));
  await writeFile(path.join(directory, 'detail.json'), detail);
  const row = { resource: 'demo:textures/a.png', status: 'failed', failureFiles: [{ path: 'detail.json', index: 0, bytes: detail.length, sha256: digestForTest(detail) }] };
  const raw = Buffer.from(JSON.stringify({ rows: [row] }));
  const coordinator = new AcceptanceCoordinator(instance);
  const result = await coordinator.archiveReport({ id: 'resource-job', report: { path: path.join(directory, 'report.json') } }, raw, [row]);
  assert.deepEqual(await readFile(result.files[0].archivePath), detail);
  assert.deepEqual(await readFile(result.archivePath), raw);
});

test('AcceptanceCoordinator dispatches the recorded singular structure route to controller checks', async t => {
  const instance = await fixture(path.join(os.tmpdir(), 'nesql-route-'));
  t.after(() => rm(instance, { recursive: true, force: true }));
  await mkdir(path.join(instance, 'nesql'));
  const raw = Buffer.from(JSON.stringify({ rows: [{ controller: 17000, status: 'passed' }] }));
  const reportPath = path.join(instance, 'nesql', 'structure.json');
  await writeFile(reportPath, raw);
  const coordinator = new AcceptanceCoordinator(instance);
  let request;
  coordinator.client = { session: 'test-session', request: async (method, url, body) => {
    if (url === '/game') return gameState(instance, { ready: true, world: { folder: 'test-world' }, exporter: '0.15.0', revision: 14, structures: [{ controller: 17000 }] });
    if (url === '/checks') { request = body; return { id: 'structure-job' }; }
    if (url === '/jobs/structure-job') return { job: { id: 'structure-job', state: 'checked', report: { path: reportPath, bytes: raw.length, sha256: digestForTest(raw) } } };
    throw new Error(`Unexpected request ${method} ${url}`);
  } };
  const checkpoint = await coordinator.runPlan({ world: 'test-world', modSha256, handlers: [{ id: 'structure', name: 'Structure', classification: 'domain', route: 'domain:structure' }] });
  assert.equal(request.domain, 'structures');
  assert.deepEqual(request.controllers, [17000]);
  assert.equal(request.handlers, undefined);
  assert.equal(checkpoint.handlers.structure.status, 'passed');
});


test('AcceptanceCoordinator handles 73-char IDs, keys <= 80 chars, pagination, and resume', async t => {
  const instance = await fixture(path.join(os.tmpdir(), 'nesql-coord-'));
  t.after(() => rm(instance, { recursive: true, force: true }));
  await mkdir(path.join(instance, 'nesql'));
  const token = 'a'.repeat(64);
  const session = randomUUID();
  let requestedKeys = [];
  let requestedOffsets = [];
  const realHandlerId = 'category_000c7fb73efe2db4c011955bd10e5ec5f5bbc2ea18fe1062a24bd28ae073f381';

  const game = createServer(async (request, response) => {
    let body = '';
    for await (const chunk of request) body += chunk;
    const jsonBody = body ? JSON.parse(body) : undefined;
    response.setHeader('Content-Type', 'application/json');
    response.setHeader('X-NESQL-Session', session);

    if (request.url === '/game') {
      response.end(JSON.stringify(gameState(instance, {
        ready: true,
        game: 'Minecraft 1.7.10',
        world: { folder: 'test-world', name: 'Test World' },
        exporter: '0.15.0',
        revision: 14
      })));
    } else if (request.url === '/checks' && request.method === 'POST') {
      requestedKeys.push(jsonBody.key);
      requestedOffsets.push(jsonBody.offset);
      assert.ok(jsonBody.key.length <= 80, `Job key must be <= 80 chars: ${jsonBody.key}`);

      const currentOffset = jsonBody.offset;
      const reportFile = path.join(instance, 'nesql', `report-${currentOffset}.json`);
      const isFirst = currentOffset === 0;
      const checked = isFirst ? 4000 : 2000;
      const reportData = {
        rows: [{
          handler: realHandlerId,
          status: 'partial',
          totalRecipes: 6000,
          offset: currentOffset,
          end: currentOffset + checked,
          checkedRecipes: checked,
          unexamined: 6000 - (currentOffset + checked),
          failedRecipes: [],
          excludedRecipes: [],
          failures: [],
          failuresOmitted: 0
        }]
      };
      const rawReport = Buffer.from(JSON.stringify(reportData));
      await writeFile(reportFile, rawReport);
      response.end(JSON.stringify({ id: `job-${currentOffset}`, state: 'queued' }));
    } else if (request.url.startsWith('/jobs/job-')) {
      const offset = Number(request.url.replace('/jobs/job-', ''));
      const reportFile = path.join(instance, 'nesql', `report-${offset}.json`);
      const bytes = (await readFile(reportFile)).length;
      const sha256 = createHash('sha256').update(await readFile(reportFile)).digest('hex');
      response.end(JSON.stringify({
        job: {
          id: `job-${offset}`,
          state: 'checked',
          report: { path: reportFile, total: 6000, bytes, sha256 }
        }
      }));
    } else {
      response.statusCode = 404;
      response.end(JSON.stringify({ error: { code: 'not_found' } }));
    }
  });

  game.listen(0, '127.0.0.1');
  await once(game, 'listening');
  t.after(() => { game.closeAllConnections(); game.close(); });

  await writeFile(
    path.join(instance, 'nesql', 'connection.json'),
    JSON.stringify({ protocol: 1, port: game.address().port, token, session })
  );

  const coordinator = new AcceptanceCoordinator(instance, { pageSize: 4000 });
  const plan = {
    world: 'test-world', modSha256,
    handlers: [{ id: realHandlerId, name: 'Wiremill' }]
  };

  // Run 1: Verify pagination from 0 to 6000
  const checkpoint = await coordinator.runPlan(plan);
  assert.equal(checkpoint.handlers[realHandlerId].status, 'passed');
  assert.equal(checkpoint.handlers[realHandlerId].checked, 6000);
  assert.equal(checkpoint.handlers[realHandlerId].unexamined, 0);
  assert.deepEqual(requestedOffsets, [0, 4000]);
  for (const k of requestedKeys) {
    assert.ok(k.length <= 80, `Key length must be <= 80: ${k}`);
  }

  // Run 2: Verify resume skips completed handler
  requestedOffsets = [];
  const checkpoint2 = await coordinator.runPlan(plan);
  assert.equal(checkpoint2.handlers[realHandlerId].status, 'passed');
  assert.equal(requestedOffsets.length, 0, 'Completed handler must be skipped without re-requesting');
});

test('AcceptanceCoordinator halts whole plan on fatal error and isolates environment', async t => {
  const instance = await fixture(path.join(os.tmpdir(), 'nesql-coord-fatal-'));
  t.after(() => rm(instance, { recursive: true, force: true }));
  await mkdir(path.join(instance, 'nesql'));
  const token = 'b'.repeat(64);
  const session = randomUUID();

  const game = createServer(async (request, response) => {
    let body = '';
    for await (const chunk of request) body += chunk;
    response.setHeader('Content-Type', 'application/json');
    response.setHeader('X-NESQL-Session', session);

    if (request.url === '/game') {
      response.end(JSON.stringify(gameState(instance, {
        ready: true,
        game: 'Minecraft 1.7.10',
        world: { folder: 'test-world', name: 'Test World' },
        exporter: '0.15.0',
        revision: 14
      })));
    } else if (request.url === '/checks' && request.method === 'POST') {
      response.end(JSON.stringify({ id: 'job-fatal', state: 'queued' }));
    } else if (request.url === '/jobs/job-fatal') {
      response.end(JSON.stringify({
        job: {
          id: 'job-fatal',
          state: 'failed',
          error: { code: 'world_changed', message: 'World instance was replaced during capture', fatal: true, details: { type: 'com.github.dcysteine.nesql.exporter.task.Jobs$Fault', code: 'world_changed', fatal: true, suppressed: [], suppressedOmitted: 0 } }
        }
      }));
    }
  });

  game.listen(0, '127.0.0.1');
  await once(game, 'listening');
  t.after(() => { game.closeAllConnections(); game.close(); });

  await writeFile(
    path.join(instance, 'nesql', 'connection.json'),
    JSON.stringify({ protocol: 1, port: game.address().port, token, session })
  );

  const coordinator = new AcceptanceCoordinator(instance);
  const plan = {
    world: 'test-world', modSha256,
    handlers: [{ id: 'handler-1', name: 'Machine 1' }, { id: 'handler-2', name: 'Machine 2' }]
  };

  await assert.rejects(
    async () => coordinator.runPlan(plan),
    /Fatal execution failure/
  );

  const cp = JSON.parse(await readFile(coordinator.checkpointPath, 'utf8'));
  assert.equal(cp.handlers['handler-1'].status, 'failed');
  assert.equal(cp.handlers['handler-2'], undefined, 'Handler 2 must not be run after fatal failure');
});

test('AcceptanceCoordinator refuses unsupported handler status without marking as passed', async t => {
  const instance = await fixture(path.join(os.tmpdir(), 'nesql-coord-unsupp-'));
  t.after(() => rm(instance, { recursive: true, force: true }));
  await mkdir(path.join(instance, 'nesql'));
  const token = 'c'.repeat(64);
  const session = randomUUID();
  const unsuppHandlerId = 'category_unsupported_example';

  const game = createServer(async (request, response) => {
    let body = '';
    for await (const chunk of request) body += chunk;
    response.setHeader('Content-Type', 'application/json');
    response.setHeader('X-NESQL-Session', session);

    if (request.url === '/game') {
      response.end(JSON.stringify(gameState(instance, {
        ready: true,
        game: 'Minecraft 1.7.10',
        world: { folder: 'test-world', name: 'Test World' },
        exporter: '0.15.0',
        revision: 14
      })));
    } else if (request.url === '/checks' && request.method === 'POST') {
      const reportFile = path.join(instance, 'nesql', 'report-unsupp.json');
      const reportData = {
        rows: [{
          handler: unsuppHandlerId,
          status: 'unsupported',
          reason: 'No recipe adapter for handler'
        }]
      };
      await writeFile(reportFile, JSON.stringify(reportData));
      response.end(JSON.stringify({ id: 'job-unsupp', state: 'queued' }));
    } else if (request.url === '/jobs/job-unsupp') {
      const reportFile = path.join(instance, 'nesql', 'report-unsupp.json');
      const raw = await readFile(reportFile);
      const bytes = raw.length;
      const sha256 = createHash('sha256').update(raw).digest('hex');
      response.end(JSON.stringify({
        job: {
          id: 'job-unsupp',
          state: 'checked',
          report: { path: reportFile, bytes, sha256 }
        }
      }));
    }
  });

  game.listen(0, '127.0.0.1');
  await once(game, 'listening');
  t.after(() => { game.closeAllConnections(); game.close(); });

  await writeFile(
    path.join(instance, 'nesql', 'connection.json'),
    JSON.stringify({ protocol: 1, port: game.address().port, token, session })
  );

  const coordinator = new AcceptanceCoordinator(instance);
  const plan = {
    world: 'test-world', modSha256,
    handlers: [{ id: unsuppHandlerId, name: 'Unsupported Machine' }]
  };

  const checkpoint = await coordinator.runPlan(plan);
  assert.equal(checkpoint.handlers[unsuppHandlerId].status, 'failed');
  assert.equal(checkpoint.handlers[unsuppHandlerId].error.code, 'handler_unsupported');
  assert.notEqual(checkpoint.handlers[unsuppHandlerId].status, 'passed');
});

test('AcceptanceCoordinator strictly rejects corrupted report SHA256 or mismatched bytes (C2)', async t => {
  const instance = await fixture(path.join(os.tmpdir(), 'nesql-coord-sha-'));
  t.after(() => rm(instance, { recursive: true, force: true }));
  await mkdir(path.join(instance, 'nesql'));
  const token = 'd'.repeat(64);
  const session = randomUUID();
  const handlerId = 'category_corrupt_sha_test';

  const game = createServer(async (request, response) => {
    let body = '';
    for await (const chunk of request) body += chunk;
    response.setHeader('Content-Type', 'application/json');
    response.setHeader('X-NESQL-Session', session);

    if (request.url === '/game') {
      response.end(JSON.stringify(gameState(instance, {
        ready: true,
        game: 'Minecraft 1.7.10',
        world: { folder: 'test-world', name: 'Test World' },
        exporter: '0.15.0',
        revision: 14
      })));
    } else if (request.url === '/checks' && request.method === 'POST') {
      const reportFile = path.join(instance, 'nesql', 'report-corrupt.json');
      await writeFile(reportFile, JSON.stringify({ rows: [{ handler: handlerId, status: 'passed', totalRecipes: 10, offset: 0, end: 10, checkedRecipes: 10, unexamined: 0, failedRecipes: [], excludedRecipes: [], failures: [] }] }));
      response.end(JSON.stringify({ id: 'job-corrupt', state: 'queued' }));
    } else if (request.url === '/jobs/job-corrupt') {
      const reportFile = path.join(instance, 'nesql', 'report-corrupt.json');
      const bytes = (await readFile(reportFile)).length;
      // Intentionally provide incorrect SHA256
      response.end(JSON.stringify({
        job: {
          id: 'job-corrupt',
          state: 'checked',
          report: { path: reportFile, bytes, sha256: '0'.repeat(64) }
        }
      }));
    }
  });

  game.listen(0, '127.0.0.1');
  await once(game, 'listening');
  t.after(() => { game.closeAllConnections(); game.close(); });

  await writeFile(
    path.join(instance, 'nesql', 'connection.json'),
    JSON.stringify({ protocol: 1, port: game.address().port, token, session })
  );

  const coordinator = new AcceptanceCoordinator(instance);
  const plan = { world: 'test-world', modSha256, handlers: [{ id: handlerId, name: 'Corrupt Machine' }] };

  await assert.rejects(
    async () => coordinator.runPlan(plan),
    /Report SHA256 mismatch/
  );
});

test('AcceptanceCoordinator enforces total recipe count stability across pages (C2)', async t => {
  const instance = await fixture(path.join(os.tmpdir(), 'nesql-coord-stability-'));
  t.after(() => rm(instance, { recursive: true, force: true }));
  await mkdir(path.join(instance, 'nesql'));
  const token = 'e'.repeat(64);
  const session = randomUUID();
  const handlerId = 'category_unstable_total_test';

  let page = 0;
  const game = createServer(async (request, response) => {
    let body = '';
    for await (const chunk of request) body += chunk;
    response.setHeader('Content-Type', 'application/json');
    response.setHeader('X-NESQL-Session', session);

    if (request.url === '/game') {
      response.end(JSON.stringify(gameState(instance, {
        ready: true,
        game: 'Minecraft 1.7.10',
        world: { folder: 'test-world', name: 'Test World' },
        exporter: '0.15.0',
        revision: 14
      })));
    } else if (request.url === '/checks' && request.method === 'POST') {
      const offset = JSON.parse(body).offset;
      const reportFile = path.join(instance, 'nesql', `report-stab-${offset}.json`);
      // Page 0 reports total 100, page 1 unexpectedly reports total 120!
      const total = offset === 0 ? 100 : 120;
      const reportData = {
        rows: [{
          handler: handlerId,
          status: 'partial',
          totalRecipes: total,
          offset,
          end: offset + 50,
          checkedRecipes: 50,
          unexamined: total - (offset + 50),
          failedRecipes: [],
          excludedRecipes: [],
          failures: [],
          failuresOmitted: 0
        }]
      };
      const raw = Buffer.from(JSON.stringify(reportData));
      await writeFile(reportFile, raw);
      response.end(JSON.stringify({ id: `job-stab-${offset}`, state: 'queued' }));
    } else if (request.url.startsWith('/jobs/job-stab-')) {
      const offset = Number(request.url.replace('/jobs/job-stab-', ''));
      const reportFile = path.join(instance, 'nesql', `report-stab-${offset}.json`);
      const raw = await readFile(reportFile);
      response.end(JSON.stringify({
        job: {
          id: `job-stab-${offset}`,
          state: 'checked',
          report: { path: reportFile, bytes: raw.length, sha256: createHash('sha256').update(raw).digest('hex') }
        }
      }));
    }
  });

  game.listen(0, '127.0.0.1');
  await once(game, 'listening');
  t.after(() => { game.closeAllConnections(); game.close(); });

  await writeFile(
    path.join(instance, 'nesql', 'connection.json'),
    JSON.stringify({ protocol: 1, port: game.address().port, token, session })
  );

  const coordinator = new AcceptanceCoordinator(instance, { pageSize: 50 });
  const plan = { world: 'test-world', modSha256, handlers: [{ id: handlerId, name: 'Unstable Total Machine' }] };

  await assert.rejects(
    async () => coordinator.runPlan(plan),
    /Total recipe stability violation/
  );
});

test('AcceptanceCoordinator isolates recipe failures across pages and archives reports (C2)', async t => {
  const instance = await fixture(path.join(os.tmpdir(), 'nesql-coord-fail-iso-'));
  t.after(() => rm(instance, { recursive: true, force: true }));
  await mkdir(path.join(instance, 'nesql'));
  const token = 'f'.repeat(64);
  const session = randomUUID();
  const handlerId = 'category_multi_fail_isolated';

  const game = createServer(async (request, response) => {
    let body = '';
    for await (const chunk of request) body += chunk;
    response.setHeader('Content-Type', 'application/json');
    response.setHeader('X-NESQL-Session', session);

    if (request.url === '/game') {
      response.end(JSON.stringify(gameState(instance, {
        ready: true,
        game: 'Minecraft 1.7.10',
        world: { folder: 'test-world', name: 'Test World' },
        exporter: '0.15.0',
        revision: 14
      })));
    } else if (request.url === '/checks' && request.method === 'POST') {
      const offset = JSON.parse(body).offset;
      const reportFile = path.join(instance, 'nesql', `report-fail-${offset}.json`);
      // Page 0 has 2 failures, Page 1 has 1 failure
      const isFirst = offset === 0;
      const failedIndices = isFirst ? [10, 20] : [75];
      const failures = failedIndices.map(idx => ({ index: idx, error: { code: 'test_err', message: `Failure at ${idx}` } }));
      const reportData = {
        rows: [{
          handler: handlerId,
          status: 'failed',
          totalRecipes: 100,
          offset,
          end: offset + 50,
          checkedRecipes: 50,
          unexamined: 100 - (offset + 50),
          failedRecipes: failedIndices,
          excludedRecipes: [],
          failures,
          failuresOmitted: 0
        }]
      };
      const failureFiles = [];
      for (const failure of failures) {
        const entry = `failure-${offset}-${failure.index}.json`;
        const detail = Buffer.from(JSON.stringify({ format: 'nesql.failure', job: `job-fail-${offset}`, target: { handler: handlerId }, ...failure }));
        await writeFile(path.join(instance, 'nesql', entry), detail);
        failureFiles.push({ path: entry, index: failure.index, bytes: detail.length, sha256: createHash('sha256').update(detail).digest('hex') });
      }
      reportData.rows[0].failureFiles = failureFiles;
      const raw = Buffer.from(JSON.stringify(reportData));
      await writeFile(reportFile, raw);
      response.end(JSON.stringify({ id: `job-fail-${offset}`, state: 'queued' }));
    } else if (request.url.startsWith('/jobs/job-fail-')) {
      const offset = Number(request.url.replace('/jobs/job-fail-', ''));
      const reportFile = path.join(instance, 'nesql', `report-fail-${offset}.json`);
      const raw = await readFile(reportFile);
      response.end(JSON.stringify({
        job: {
          id: `job-fail-${offset}`,
          state: 'checked',
          report: { path: reportFile, bytes: raw.length, sha256: createHash('sha256').update(raw).digest('hex') }
        }
      }));
    }
  });

  game.listen(0, '127.0.0.1');
  await once(game, 'listening');
  t.after(() => { game.closeAllConnections(); game.close(); });

  await writeFile(
    path.join(instance, 'nesql', 'connection.json'),
    JSON.stringify({ protocol: 1, port: game.address().port, token, session })
  );

  const coordinator = new AcceptanceCoordinator(instance, { pageSize: 50 });
  const plan = { world: 'test-world', modSha256, handlers: [{ id: handlerId, name: 'Failing Machine' }] };

  const checkpoint = await coordinator.runPlan(plan);
  const handlerState = checkpoint.handlers[handlerId];
  assert.equal(handlerState.status, 'failed');
  assert.equal(handlerState.checked, 100);
  assert.equal(handlerState.unexamined, 0);
  assert.deepEqual(handlerState.failed, [10, 20, 75]);
  assert.equal(checkpoint.archivedReports.length, 2, 'Must archive 2 report shards');
  assert.ok(checkpoint.archivedReports[0].archivePath);
  assert.equal(existsSync(checkpoint.archivedReports[0].archivePath), true, 'Archived report shard must exist on disk');
  assert.equal(checkpoint.archivedReports[0].files.length, 2);
  assert.equal(checkpoint.archivedReports[1].files.length, 1);
});

test('AcceptanceCoordinator restores persisted runId on reload ensuring idempotent job keys (C8)', async t => {
  const instance = await fixture(path.join(os.tmpdir(), 'nesql-coord-runid-'));
  t.after(() => rm(instance, { recursive: true, force: true }));
  await mkdir(path.join(instance, 'nesql'));

  const fixedRunId = 'run-persistent-test-1234';
  const envFingerprint = AcceptanceCoordinator.fingerprint({
    world: 'test-world', modSha256,
    exporter: '0.15.0',
    revision: 14,
    planHash: 'abc',
    modSha256: null,
    probes: null,
    dependencies: null
  });

  const initialCheckpoint = {
    version: '0.15.0',
    runId: fixedRunId,
    fingerprint: envFingerprint,
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
    activeJob: null,
    handlers: {},
    summary: { total: 0, checked: 0, failed: 0, excluded: 0, unexamined: 0 },
    failures: [],
    archivedReports: []
  };

  const coordinator1 = new AcceptanceCoordinator(instance);
  await coordinator1.saveCheckpoint(initialCheckpoint);

  // New coordinator instance without explicit runId
  const coordinator2 = new AcceptanceCoordinator(instance);
  assert.notEqual(coordinator2.runId, fixedRunId, 'Initial runId should be newly generated');

  const loaded = await coordinator2.loadCheckpoint(envFingerprint);
  assert.equal(loaded.runId, fixedRunId);
  assert.equal(coordinator2.runId, fixedRunId, 'runId must be restored to coordinator instance');
});

test('job severity is authoritative and missing severity prevents unsafe continuation', () => {
  assert.equal(isFatalError({ code: 'export_failed', type: 'java.io.FileNotFoundException', fatal: true }), true);
  assert.equal(isFatalError({ code: 'recipe_capture', fatal: true }), true);
  assert.equal(isFatalError({ code: 'recipe_capture', fatal: false }), false);
  assert.equal(isFatalError({ code: 'export_failed', message: 'java.io.IOException: read failed' }), true);
  assert.equal(isFatalError({ code: 'recipe_capture', fatal: false, suppressed: [{ code: 'recipe_capture', fatal: false }] }), true);
  assert.equal(isFatalError({ code: 'recipe_capture', fatal: false, suppressedOmitted: 1 }), true);
  assert.equal(isFatalError(undefined), true);
});

test('AcceptanceCoordinator archives structure failures by controller identity', async t => {
  const instance = await fixture(path.join(os.tmpdir(), 'nesql-coord-structure-'));
  t.after(() => rm(instance, { recursive: true, force: true }));
  const reportDir = path.join(instance, 'nesql', 'checks');
  await mkdir(reportDir, { recursive: true });
  const detailsDir = path.join(reportDir, 'job-structure-details');
  await mkdir(detailsDir, { recursive: true });
  const detail = Buffer.from(JSON.stringify({ format: 'nesql.failure', job: 'job-structure', target: { controller: 17000 }, index: 0, error: { code: 'structure_capture' } }));
  const detailPath = path.join(detailsDir, 'detail.json');
  await writeFile(detailPath, detail);
  const entry = { path: 'job-structure-details/detail.json', index: 0, bytes: detail.length, sha256: createHash('sha256').update(detail).digest('hex') };
  const report = Buffer.from(JSON.stringify({ rows: [{ controller: 17000, type: 'example.Controller', status: 'failed', failedStructures: [17000], failureFiles: [entry] }] }));
  const reportPath = path.join(reportDir, 'job-structure.json');
  await writeFile(reportPath, report);
  const coordinator = new AcceptanceCoordinator(instance);
  const archived = await coordinator.archiveReport({ id: 'job-structure', report: { path: reportPath } }, report, [{ controller: 17000, status: 'failed', failedStructures: [17000], failureFiles: [entry] }]);
  assert.equal(archived.files.length, 1);
  assert.equal(existsSync(archived.files[0].archivePath), true);
});

test('AcceptanceCoordinator enforces real mod SHA256 and protocol revision (U4)', async t => {
  const instance = await fixture(path.join(os.tmpdir(), 'nesql-coord-sha256-'));
  t.after(() => rm(instance, { recursive: true, force: true }));
  await mkdir(path.join(instance, 'nesql'));
  const token = 'f'.repeat(64);
  const session = randomUUID();

  // Create a dummy mod jar on disk
  const dummyJarPath = path.join(instance, 'dummy-mod.jar');
  await writeFile(dummyJarPath, Buffer.from('dummy mod jar content for sha test'));
  const dummyJarSha = createHash('sha256').update(await readFile(dummyJarPath)).digest('hex');

  const game = createServer(async (request, response) => {
    let body = '';
    for await (const chunk of request) body += chunk;
    response.setHeader('Content-Type', 'application/json');
    response.setHeader('X-NESQL-Session', session);

    if (request.url === '/game') {
      response.end(JSON.stringify(gameState(instance, {
        ready: true,
        game: 'Minecraft 1.7.10',
        world: { folder: 'test-world', name: 'Test World' },
        exporter: '0.15.0',
        revision: 14,
        sources: {
          valid: true,
          rows: [
            { id: 'nesql', valid: true, name: 'NESQL++', version: '0.15.0', path: dummyJarPath }
          ]
        }
      })));
    } else {
      response.statusCode = 404;
      response.end(JSON.stringify({ error: { code: 'not_found' } }));
    }
  });

  game.listen(0, '127.0.0.1');
  await once(game, 'listening');
  t.after(() => { game.closeAllConnections(); game.close(); });

  await writeFile(
    path.join(instance, 'nesql', 'connection.json'),
    JSON.stringify({ protocol: 1, port: game.address().port, token, session })
  );

  const coordinator = new AcceptanceCoordinator(instance);

  // Mismatched modSha256 must reject
  await assert.rejects(
    async () => coordinator.runPlan({
      world: 'test-world', modSha256,
      modSha256: '0'.repeat(64),
      handlers: []
    }),
    /Loaded mod SHA256 mismatch/
  );

  // Mismatched protocol revision must reject
  await assert.rejects(
    async () => coordinator.runPlan({
      world: 'test-world', modSha256,
      expectedRevision: 99,
      handlers: []
    }),
    /Game exporter protocol mismatch/
  );
});

test('AcceptanceCoordinator routes tooling exclusions and updates summary (U2, U3)', async t => {
  const instance = await fixture(path.join(os.tmpdir(), 'nesql-coord-tooling-'));
  t.after(() => rm(instance, { recursive: true, force: true }));
  await mkdir(path.join(instance, 'nesql'));
  const token = '9'.repeat(64);
  const session = randomUUID();
  let checksCalled = 0;

  const game = createServer(async (request, response) => {
    let body = '';
    for await (const chunk of request) body += chunk;
    response.setHeader('Content-Type', 'application/json');
    response.setHeader('X-NESQL-Session', session);

    if (request.url === '/game') {
      response.end(JSON.stringify(gameState(instance, {
        ready: true,
        game: 'Minecraft 1.7.10',
        world: { folder: 'test-world', name: 'Test World' },
        exporter: '0.15.0',
        revision: 14
      })));
    } else if (request.url === '/checks') {
      checksCalled++;
      response.end(JSON.stringify({ id: 'job-unexpected', state: 'queued' }));
    } else {
      response.statusCode = 404;
      response.end(JSON.stringify({ error: { code: 'not_found' } }));
    }
  });

  game.listen(0, '127.0.0.1');
  await once(game, 'listening');
  t.after(() => { game.closeAllConnections(); game.close(); });

  await writeFile(
    path.join(instance, 'nesql', 'connection.json'),
    JSON.stringify({ protocol: 1, port: game.address().port, token, session })
  );

  const coordinator = new AcceptanceCoordinator(instance);
  const plan = {
    world: 'test-world', modSha256,
    handlers: [
      {
        id: 'category_profiler_exclusion_test_ProfilerRecipeHandler',
        name: 'Profiler',
        classification: 'tooling',
        implementationStatus: 'excluded_justified',
        reason: 'Approved tooling exclusion'
      }
    ]
  };

  const checkpoint = await coordinator.runPlan(plan);
  assert.equal(checksCalled, 0, 'Tooling exclusion must not be dispatched to /checks');
  assert.equal(checkpoint.handlers['category_profiler_exclusion_test_ProfilerRecipeHandler'].status, 'excluded');
  assert.equal(checkpoint.summary.total, 1);
  assert.equal(checkpoint.summary.excluded, 1);
  assert.equal(checkpoint.summary.checked, 0);
  assert.equal(checkpoint.summary.failed, 0);
});
