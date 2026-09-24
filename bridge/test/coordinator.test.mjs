import assert from 'node:assert/strict';
import { createServer } from 'node:http';
import { mkdtemp, mkdir, rm, writeFile, readFile } from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { once } from 'node:events';
import { randomUUID, createHash } from 'node:crypto';
import test from 'node:test';
import { AcceptanceCoordinator } from '../src/coordinator.mjs';

test('AcceptanceCoordinator handles 73-char IDs, keys <= 80 chars, pagination, and resume', async t => {
  const instance = await mkdtemp(path.join(os.tmpdir(), 'nesql-coord-'));
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
      response.end(JSON.stringify({
        ready: true,
        game: 'Minecraft 1.7.10',
        world: { folder: 'test-world', name: 'Test World' },
        exporter: '0.15.0',
        revision: 14
      }));
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
    world: 'test-world',
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
  const instance = await mkdtemp(path.join(os.tmpdir(), 'nesql-coord-fatal-'));
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
      response.end(JSON.stringify({
        ready: true,
        game: 'Minecraft 1.7.10',
        world: { folder: 'test-world', name: 'Test World' },
        exporter: '0.15.0',
        revision: 14
      }));
    } else if (request.url === '/checks' && request.method === 'POST') {
      response.end(JSON.stringify({ id: 'job-fatal', state: 'queued' }));
    } else if (request.url === '/jobs/job-fatal') {
      response.end(JSON.stringify({
        job: {
          id: 'job-fatal',
          state: 'failed',
          error: { code: 'world_changed', message: 'World instance was replaced during capture' }
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
    world: 'test-world',
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
  const instance = await mkdtemp(path.join(os.tmpdir(), 'nesql-coord-unsupp-'));
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
      response.end(JSON.stringify({
        ready: true,
        game: 'Minecraft 1.7.10',
        world: { folder: 'test-world', name: 'Test World' },
        exporter: '0.15.0',
        revision: 14
      }));
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
    world: 'test-world',
    handlers: [{ id: unsuppHandlerId, name: 'Unsupported Machine' }]
  };

  const checkpoint = await coordinator.runPlan(plan);
  assert.equal(checkpoint.handlers[unsuppHandlerId].status, 'failed');
  assert.equal(checkpoint.handlers[unsuppHandlerId].error.code, 'handler_unsupported');
  assert.notEqual(checkpoint.handlers[unsuppHandlerId].status, 'passed');
});

test('AcceptanceCoordinator strictly rejects corrupted report SHA256 or mismatched bytes (C2)', async t => {
  const instance = await mkdtemp(path.join(os.tmpdir(), 'nesql-coord-sha-'));
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
      response.end(JSON.stringify({
        ready: true,
        game: 'Minecraft 1.7.10',
        world: { folder: 'test-world', name: 'Test World' },
        exporter: '0.15.0',
        revision: 14
      }));
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
  const plan = { world: 'test-world', handlers: [{ id: handlerId, name: 'Corrupt Machine' }] };

  await assert.rejects(
    async () => coordinator.runPlan(plan),
    /Report SHA256 mismatch/
  );
});

test('AcceptanceCoordinator enforces total recipe count stability across pages (C2)', async t => {
  const instance = await mkdtemp(path.join(os.tmpdir(), 'nesql-coord-stability-'));
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
      response.end(JSON.stringify({
        ready: true,
        game: 'Minecraft 1.7.10',
        world: { folder: 'test-world', name: 'Test World' },
        exporter: '0.15.0',
        revision: 14
      }));
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
  const plan = { world: 'test-world', handlers: [{ id: handlerId, name: 'Unstable Total Machine' }] };

  await assert.rejects(
    async () => coordinator.runPlan(plan),
    /Total recipe stability violation/
  );
});

test('AcceptanceCoordinator isolates recipe failures across pages and archives reports (C2)', async t => {
  const instance = await mkdtemp(path.join(os.tmpdir(), 'nesql-coord-fail-iso-'));
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
      response.end(JSON.stringify({
        ready: true,
        game: 'Minecraft 1.7.10',
        world: { folder: 'test-world', name: 'Test World' },
        exporter: '0.15.0',
        revision: 14
      }));
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
  const plan = { world: 'test-world', handlers: [{ id: handlerId, name: 'Failing Machine' }] };

  const checkpoint = await coordinator.runPlan(plan);
  const handlerState = checkpoint.handlers[handlerId];
  assert.equal(handlerState.status, 'failed');
  assert.equal(handlerState.checked, 100);
  assert.equal(handlerState.unexamined, 0);
  assert.deepEqual(handlerState.failed, [10, 20, 75]);
  assert.equal(checkpoint.archivedReports.length, 2, 'Must archive 2 report shards');
});

test('AcceptanceCoordinator restores persisted runId on reload ensuring idempotent job keys (C8)', async t => {
  const instance = await mkdtemp(path.join(os.tmpdir(), 'nesql-coord-runid-'));
  t.after(() => rm(instance, { recursive: true, force: true }));
  await mkdir(path.join(instance, 'nesql'));

  const fixedRunId = 'run-persistent-test-1234';
  const envFingerprint = AcceptanceCoordinator.fingerprint({
    world: 'test-world',
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
