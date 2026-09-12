import assert from 'node:assert/strict';
import { createServer } from 'node:http';
import { mkdtemp, mkdir, rm, writeFile } from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { once } from 'node:events';
import { randomUUID } from 'node:crypto';
import test from 'node:test';
import { Client } from '@modelcontextprotocol/sdk/client/index.js';
import { StdioClientTransport } from '@modelcontextprotocol/sdk/client/stdio.js';
import { GameClient } from '../src/client.mjs';

test('stdio MCP exposes the job lifecycle and reconnects after the game restarts', async t => {
  const instance = await mkdtemp(path.join(os.tmpdir(), 'nesql-bridge-'));
  t.after(() => rm(instance, { recursive: true, force: true }));
  await mkdir(path.join(instance, 'nesql'));
  const token = 'a'.repeat(64);
  let session = randomUUID();
  const requests = [];
  const game = createServer(async (request, response) => {
    assert.equal(request.headers.authorization, `Bearer ${token}`);
    assert.equal(request.headers['x-nesql-session'], session);
    let body = '';
    for await (const chunk of request) body += chunk;
    requests.push({ path: request.url, method: request.method, body: body ? JSON.parse(body) : undefined });
    response.setHeader('Content-Type', 'application/json');
    response.setHeader('X-NESQL-Session', session);
    if (request.url === '/game') response.end(JSON.stringify({ ready: true, singleplayer: true }));
    else if (request.url === '/jobs' && request.method === 'POST') response.end(JSON.stringify({ id: 'job-one', state: 'queued' }));
    else if (request.url === '/jobs') response.end(JSON.stringify({ job: { id: 'job-one', state: 'running' } }));
    else if (request.url === '/jobs/job-one/cancel') response.end(JSON.stringify({ id: 'job-one', state: 'cancelling' }));
    else if (request.url === '/jobs/job-one') response.end(JSON.stringify({ job: { id: 'job-one', state: 'cancelled' } }));
    else if (request.url.startsWith('/exports?')) response.end(JSON.stringify({ rows: [], next: null }));
    else { response.statusCode = 404; response.end(JSON.stringify({ error: { code: 'job_missing', message: 'Unknown job' } })); }
  });
  game.listen(0, '127.0.0.1');
  await once(game, 'listening');
  t.after(() => { game.closeAllConnections(); game.close(); });
  const discovery = port => writeFile(path.join(instance, 'nesql', 'connection.json'), JSON.stringify({ protocol: 1, port, token, session }));
  await discovery(game.address().port);

  const transport = new StdioClientTransport({
    command: process.execPath,
    args: [fileURLToPath(new URL('../src/index.mjs', import.meta.url)), '--instance', instance],
    stderr: 'pipe',
  });
  let stderr = '';
  transport.stderr.on('data', chunk => { stderr += chunk.toString(); });
  const client = new Client({ name: 'bridge-check', version: '1' });
  t.after(async () => { await client.close(); assert.ok(!stderr.includes(token)); });
  await client.connect(transport);
  const tools = await client.listTools();
  assert.deepEqual(tools.tools.map(tool => tool.name).sort(), [
    'cancel_export', 'inspect_game', 'list_exports', 'read_export', 'read_job', 'start_export',
  ]);
  const call = async (name, args = {}) => client.callTool({ name, arguments: args });
  assert.equal((await call('inspect_game')).structuredContent.ready, true);
  const args = { key: 'retry-one', name: 'gtnh', profile: 'full', probes: [{ count: 4, channels: { coil: 2 } }, { count: 1 }] };
  assert.equal((await call('start_export', args)).structuredContent.state, 'queued');
  assert.deepEqual(requests.at(-1).body, args);
  assert.equal((await call('cancel_export', { id: 'job-one' })).structuredContent.state, 'cancelling');
  assert.equal(requests.at(-1).method, 'POST');
  assert.equal((await call('read_job', { id: 'job-one' })).structuredContent.job.state, 'cancelled');
  assert.equal((await call('read_job')).structuredContent.job.state, 'running');
  const after = 'c'.repeat(64);
  assert.deepEqual((await call('list_exports', { limit: 2, after })).structuredContent, { rows: [], next: null });
  assert.equal(requests.at(-1).path, `/exports?limit=2&after=${after}`);
  assert.equal((await call('read_job', { id: 'absent' })).isError, true);
  const count = requests.length;
  const invalid = await call('read_job', { id: '../manifest.json' });
  assert.equal(invalid.isError, true);
  assert.equal((await call('start_export', { ...args, profile: 'ui' })).isError, true);
  assert.equal((await call('list_exports', { limit: 101 })).isError, true);
  for (const probes of [[], [{ count: 0 }], [{ count: 65 }], [{ count: 1.5 }], [{ count: '1' }],
    [{ count: 1, channels: { Coil: 2 } }], [{ count: 1, channels: { coil: 0 } }], [{ count: 1, channels: { coil: 65536 } }],
    [{ count: 1, channels: { coil: '2' } }], [{ count: 1, extra: true }], [{ count: 1 }, { count: 1, channels: {} }]]) {
    assert.equal((await call('start_export', { ...args, probes })).isError, true);
  }
  assert.equal(requests.length, count);

  await discovery(1);
  const offline = await call('inspect_game');
  assert.equal(offline.isError, true);
  assert.equal(offline.structuredContent.error.code, 'game_offline');
  session = randomUUID();
  await discovery(game.address().port);
  assert.equal((await call('inspect_game')).structuredContent.ready, true);
});

test('the bridge rejects unknown connection revisions and limits game responses', async t => {
  const instance = await mkdtemp(path.join(os.tmpdir(), 'nesql-connection-'));
  t.after(() => rm(instance, { recursive: true, force: true }));
  await mkdir(path.join(instance, 'nesql'));
  const connection = path.join(instance, 'nesql', 'connection.json');
  const client = new GameClient(instance);
  const session = randomUUID();
  await assert.rejects(client.request('GET', '/game'), { code: 'game_offline' });
  await writeFile(connection, JSON.stringify({ protocol: 99, port: 1234, token: 'a'.repeat(64) }));
  await assert.rejects(client.request('GET', '/game'), { code: 'invalid_connection' });
  await writeFile(connection, ' '.repeat(4097));
  await assert.rejects(client.request('GET', '/game'), { code: 'invalid_connection' });
  let mode = 'large';
  const game = createServer((_request, response) => {
    response.setHeader('X-NESQL-Session', mode === 'stale' ? randomUUID() : session);
    if (mode === 'slow') {
      response.write('{');
      setTimeout(() => response.end('}'), 200).unref();
    } else response.end('x'.repeat(1024 * 1024 + 1));
  });
  game.listen(0, '127.0.0.1');
  await once(game, 'listening');
  t.after(() => { game.closeAllConnections(); game.close(); });
  await writeFile(connection, JSON.stringify({ protocol: 1, port: game.address().port, token: 'a'.repeat(64), session }));
  await assert.rejects(client.request('GET', '/game'), { code: 'response_limit' });
  mode = 'stale';
  await assert.rejects(client.request('GET', '/game'), { code: 'game_changed' });
  mode = 'slow';
  await assert.rejects(new GameClient(instance, { timeout: 30 }).request('GET', '/game'), { code: 'request_cancelled' });
});
