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
          status: 'checked',
          totalRecipes: 6000,
          offset: currentOffset,
          end: currentOffset + checked,
          checkedRecipes: checked,
          unexamined: 6000 - (currentOffset + checked),
          failedRecipes: [],
          excludedRecipes: [],
          failures: []
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
      response.end(JSON.stringify({
        job: {
          id: 'job-unsupp',
          state: 'checked',
          report: { path: reportFile }
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
