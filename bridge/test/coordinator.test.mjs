import assert from 'node:assert/strict';
import { createServer } from 'node:http';
import { mkdtemp, mkdir, rm, writeFile, readFile } from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { once } from 'node:events';
import { randomUUID } from 'node:crypto';
import test from 'node:test';
import { AcceptanceCoordinator } from '../src/coordinator.mjs';

test('AcceptanceCoordinator automatically advances pagination windows and recovers from checkpoint', async t => {
  const instance = await mkdtemp(path.join(os.tmpdir(), 'nesql-coord-'));
  t.after(() => rm(instance, { recursive: true, force: true }));
  await mkdir(path.join(instance, 'nesql'));
  const token = 'a'.repeat(64);
  const session = randomUUID();
  let requestedOffsets = [];

  const game = createServer(async (request, response) => {
    let body = '';
    for await (const chunk of request) body += chunk;
    const jsonBody = body ? JSON.parse(body) : undefined;
    response.setHeader('Content-Type', 'application/json');
    response.setHeader('X-NESQL-Session', session);

    if (request.url === '/game') {
      response.end(JSON.stringify({ ready: true, singleplayer: true, world: { folder: 'test-world' } }));
    } else if (request.url === '/checks' && request.method === 'POST') {
      requestedOffsets.push(jsonBody.offset);
      const currentOffset = jsonBody.offset;
      const reportFile = path.join(instance, 'nesql', `report-${currentOffset}.json`);
      // Simulate a handler with 6000 total recipes, returning in chunks of 4000
      const isFirst = currentOffset === 0;
      const checked = isFirst ? 4000 : 2000;
      const reportData = {
        rows: [{
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
      await writeFile(reportFile, JSON.stringify(reportData));
      response.end(JSON.stringify({ id: `job-${currentOffset}`, state: 'queued' }));
    } else if (request.url.startsWith('/jobs/job-')) {
      const offset = Number(request.url.replace('/jobs/job-', ''));
      const reportFile = path.join(instance, 'nesql', `report-${offset}.json`);
      response.end(JSON.stringify({
        job: {
          id: `job-${offset}`,
          state: 'checked',
          report: { path: reportFile, total: 6000 }
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
    handlers: [{ id: 'category_test', name: 'Large Machine' }]
  };

  const checkpoint = await coordinator.runPlan(plan);
  assert.equal(checkpoint.handlers['category_test'].status, 'passed');
  assert.equal(checkpoint.handlers['category_test'].checked, 6000);
  assert.equal(checkpoint.handlers['category_test'].unexamined, 0);
  assert.deepEqual(requestedOffsets, [0, 4000]);

  // Verify checkpoint was written to disk
  const onDisk = JSON.parse(await readFile(coordinator.checkpointPath, 'utf8'));
  assert.equal(onDisk.handlers['category_test'].status, 'passed');
});
