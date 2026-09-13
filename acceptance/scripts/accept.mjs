import assert from 'node:assert/strict';
import { createHash, randomUUID } from 'node:crypto';
import { execFile } from 'node:child_process';
import { createRequire } from 'node:module';
import { readFile, writeFile, mkdir, stat } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { parseArgs, promisify } from 'node:util';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const { values, positionals } = parseArgs({ options: {
  config: { type: 'string', default: path.join(root, 'acceptance.json') },
  key: { type: 'string' }, browser: { type: 'boolean', default: false },
}, allowPositionals: true, strict: true });
const [command = 'prepare', argument] = positionals;
assert.ok(['prepare', 'inspect', 'start', 'status', 'cancel', 'collect', 'verify', 'serve', 'check'].includes(command), 'Unknown acceptance command');
assert.ok(positionals.length <= 2, 'Too many arguments');
const config = JSON.parse(await readFile(values.config, 'utf8'));
for (const name of ['instance', 'mod', 'compiler', 'web', 'reports', 'browser']) assert.ok(path.isAbsolute(config[name]), `${name} must be absolute`);
assert.ok(/^[^/\\\x00-\x1f\x7f]{1,128}$/.test(config.world) && !['.', '..'].includes(config.world), 'Expected a save folder name');
const reports = path.resolve(config.reports);
assert.ok(reports.startsWith(path.join(root, '.refactor-state') + path.sep), 'Reports must stay in this workspace');
await mkdir(reports, { recursive: true });
const json = async file => JSON.parse(await readFile(file, 'utf8'));
const record = async (name, value) => {
  assert.match(name, /^[a-zA-Z0-9_-]+\.json$/);
  await writeFile(path.join(reports, name), JSON.stringify(value, null, 2) + '\n');
  return value;
};
const id = value => { assert.match(value ?? '', /^[a-zA-Z0-9_-]{1,80}$/); return value; };
const hash = async file => createHash('sha256').update(await readFile(file)).digest('hex');
const execute = promisify(execFile);
const binary = path.join(config.compiler, process.platform === 'win32' ? 'elysium-compiler.exe' : 'elysium-compiler');
const compile = async (...args) => JSON.parse((await execute(binary, args, { windowsHide: true, maxBuffer: 4 * 1024 * 1024 })).stdout);

async function artifacts() {
  const packages = {};
  for (const name of ['mod', 'compiler', 'web']) {
    const bundle = config[name], inventory = await json(path.join(bundle, 'files.json'));
    const files = Array.isArray(inventory) ? inventory : inventory.files;
    assert.ok(Array.isArray(files) && files.length, 'Missing bundle inventory');
    for (const file of files) {
      const target = path.resolve(bundle, file.path);
      assert.ok(target.startsWith(path.resolve(bundle) + path.sep), 'Bundle path escapes its directory');
      assert.equal((await stat(target)).size, file.bytes, target);
      assert.equal(await hash(target), file.sha256, target);
    }
    packages[name] = { path: bundle, files: files.length, inventory: await hash(path.join(bundle, 'files.json')) };
  }
  const mod = `NESQL++-${config.version}.jar`;
  const installed = path.join(config.instance, 'mods', mod);
  assert.equal(await hash(installed), await hash(path.join(config.mod, 'mod', mod)), 'Installed mod differs from the checked release');
  assert.ok((await stat(path.join(config.instance, 'saves', config.world, 'level.dat'))).isFile(), 'Test save is missing');
  return { packages, installed, sha256: await hash(installed), version: config.version, revision: config.revision, world: config.world };
}

async function game(action) {
  const require = createRequire(path.join(config.mod, 'bridge', 'package.json'));
  const { Client } = await import(pathToFileURL(require.resolve('@modelcontextprotocol/sdk/client/index.js')));
  const { StdioClientTransport } = await import(pathToFileURL(require.resolve('@modelcontextprotocol/sdk/client/stdio.js')));
  const client = new Client({ name: 'nei-acceptance', version: '1.0.0' });
  const transport = new StdioClientTransport({ command: process.execPath,
    args: [path.join(config.mod, 'bridge/src/index.mjs'), '--instance', config.instance], stderr: 'ignore' });
  const call = async (name, args = {}) => {
    const response = await client.callTool({ name, arguments: args });
    const result = response.structuredContent ?? JSON.parse(response.content.find(item => item.type === 'text').text);
    if (response.isError) throw Object.assign(new Error(result.error?.message ?? 'Game tool failed'), { code: result.error?.code });
    return result;
  };
  try { await client.connect(transport); return await action(call, client); }
  finally { await client.close(); }
}

async function inspect(call, required = false) {
  const state = await call('inspect_game');
  await record('game.json', state);
  assert.equal(state.exporter, config.version, 'The running game loaded a different mod version');
  assert.equal(state.revision, config.revision, 'The running game uses a different source format');
  if (state.ready) {
    assert.equal(state.client?.valid, true, state.client?.error?.message ?? 'Client jar preflight is unavailable');
    const failed = (state.sources?.rows ?? []).filter(row => !row.valid).map(row => row.id);
    assert.equal(state.sources?.valid, true, failed.length ? 'Invalid mod sources: ' + failed.join(', ') + '; see game.json' : 'Mod source preflight is unavailable');
    const conflicts = (state.research?.rows ?? []).filter(row => !row.valid).map(row => row.error?.message ?? row.key);
    assert.equal(state.research?.valid, true, conflicts.length ? 'Invalid research registry: ' + conflicts.join('; ') + '; see game.json' : 'Research registry preflight is unavailable');
    assert.equal(state.materials?.valid, true, state.materials?.error?.message ?? 'Material metadata preflight is unavailable');
  }
  if (required) {
    assert.ok(state.ready && state.itemsReady, state.reason ?? 'Wait for NEI to load its item list');
    assert.equal(state.world?.folder, config.world, 'Enter the independent test save');
  }
  return state;
}

function selection(state, phase) {
  assert.ok(['data', 'visuals', 'magic'].includes(phase), 'Choose data, visuals or magic');
  const available = state.handlers.filter(handler => handler.supported);
  let handlers;
  if (phase === 'magic') handlers = available.filter(handler => handler.source.handler.startsWith('ru.timeconqueror.tcneiadditions.nei.'));
  else {
    const furnace = available.find(handler => handler.source.handler === 'codechicken.nei.recipe.FurnaceRecipeHandler');
    const machines = available.filter(handler => handler.source.handler === 'gregtech.nei.GTNEIDefaultHandler');
    const machine = machines.find(handler => handler.source.key === 'gt.recipe.macerator/gt.recipe.macerator')
      ?? machines.find(handler => /macerat/i.test(handler.source.key)) ?? machines[0];
    assert.ok(furnace && machine, 'The acceptance run needs the furnace and one GT handler');
    handlers = [furnace, machine];
  }
  assert.ok(handlers.length > 0, 'No matching supported handlers; an empty selection would request all handlers');
  return { phase, scope: 'selection', complete: false, selected: handlers,
    registered: state.handlers.length, supported: available.length, unsupported: state.handlers.filter(handler => !handler.supported) };
}

async function web(catalogRoot, screenshot, keep = false) {
  const require = createRequire(path.join(config.web, 'backend/package.json'));
  const { createApp } = require(path.join(config.web, 'backend/dist/app.js'));
  const { decodeTable } = require('@elysium/contracts');
  const app = createApp({ catalog: catalogRoot, web: path.join(config.web, 'frontend/dist') });
  const server = await new Promise((resolve, reject) => { const listener = app.listen(0, '127.0.0.1', () => resolve(listener)); listener.once('error', reject); });
  const url = `http://127.0.0.1:${server.address().port}`;
  const get = async route => { const response = await fetch(url + route, { signal: AbortSignal.timeout(60000) }); assert.equal(response.status, 200, route); return response; };
  let browser;
  try {
    const manifest = await (await get('/api/catalog')).json();
    const api = `/api/catalog/${manifest.id}`;
    const items = await (await get(api + '/items?limit=1')).json();
    assert.ok(items.total > 0, 'Real catalog has no browse entries');
    const recipes = manifest.files.find(file => file.kind === 'recipes');
    let sample;
    if (recipes) {
      const table = decodeTable(new Uint8Array(await readFile(path.join(catalogRoot, 'catalogs', manifest.id, recipes.path))), 'recipes');
      sample = table.records[0];
      assert.ok(sample, 'Recipe table has no records');
      const detail = await (await get(api + '/recipes/' + sample.id)).json();
      assert.equal(detail.recipe.id, sample.id);
    }
    const html = await (await get('/')).text();
    const module = html.match(/src="(\/web\/[^"\s]+\.js)"/)?.[1]; assert.ok(module); await get(module); await get('/sw.js');
    if (values.browser) {
      const playwright = createRequire(config.browser)('@playwright/test');
      browser = await playwright.chromium.launch({ headless: true });
      const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
      const errors = []; page.on('pageerror', error => errors.push(error.message));
      await page.goto(url + (sample ? `/recipe/${sample.id}?catalog=${manifest.id}` : '/'));
      await page.locator(sample ? '.recipe-card' : '.browser-cell').first().waitFor({ state: 'visible', timeout: 60000 });
      await page.screenshot({ path: screenshot, fullPage: true });
      assert.deepEqual(errors, [], 'The real catalog failed in the browser');
    }
    const result = { catalog: manifest.id, counts: manifest.counts, sample: sample?.id ?? null, browser: values.browser, url };
    if (keep) {
      console.log(JSON.stringify(result));
      await new Promise(resolve => { process.once('SIGINT', resolve); process.once('SIGTERM', resolve); });
    }
    return result;
  } finally { await browser?.close(); server.closeAllConnections(); await new Promise(resolve => server.close(resolve)); }
}

async function verify(jobId, toolchain) {
  const transfer = await json(path.join(reports, 'handoff-' + id(jobId) + '.json'));
  const job = await json(path.join(reports, id(jobId) + '-job.json'));
  assert.equal(job.state, 'succeeded', 'Export must succeed before verification');
  assert.equal(job.request.world, config.world, 'Export was not bound to the test save');
  assert.equal(transfer.job, job.id);
  assert.equal(transfer.source, job.result.id);
  assert.match(transfer.source, /^[a-f0-9]{64}$/);
  const source = await json(path.join(reports, transfer.source + '-source.json'));
  const input = path.join(config.instance, 'nesql', 'datasets', source.id);
  assert.equal(path.resolve(transfer.path), path.resolve(input));
  assert.equal(source.revision, config.revision);
  assert.equal(source.scope.mode, 'selection');
  const checked = await compile('inspect', '--input', input);
  assert.equal(checked.id, source.id);
  await record(source.id + '-inspect.json', checked);
  const output = path.join(reports, 'catalogs', source.id);
  const receipt = await compile('compile', '--input', input, '--output', output);
  assert.equal((await compile('check', '--input', output)).id, receipt.id);
  const browser = await web(output, path.join(reports, source.id + '.png'));
  return await record(source.id + '-verified.json', { realGame: true, source: source.id, scope: source.scope,
    exportToolchain: transfer.toolchain, verificationToolchain: toolchain, receipt, web: browser });
}

try {
  let result;
  if (command === 'check') {
    assert.ok(argument && path.isAbsolute(argument), 'check requires an absolute compiled catalog directory');
    result = { synthetic: true, ...await web(argument, path.join(reports, 'harness-check.png')) };
    await record('harness-check.json', result);
  } else if (command === 'serve') {
    assert.match(argument ?? '', /^[a-f0-9]{64}$/);
    result = await web(path.join(reports, 'catalogs', argument), path.join(reports, argument + '.png'), true);
  } else if (command === 'verify') {
    result = await verify(argument, await artifacts());
  } else {
    const toolchain = await artifacts();
    result = await game(async (call, client) => {
      if (command === 'prepare') {
        const tools = (await client.listTools()).tools;
        assert.ok(tools.find(tool => tool.name === 'start_export')?.inputSchema.properties.world, 'The bridge does not expose the world guard');
        return await record('ready.json', { ...toolchain, tools: tools.map(tool => tool.name), gameVerified: false });
      }
      if (command === 'inspect') {
        const state = await inspect(call);
        return { ready: state.ready, itemsReady: state.itemsReady, world: state.world, version: state.exporter, client: state.client,
          sources: { valid: state.sources?.valid, count: state.sources?.count },
          research: { valid: state.research?.valid, count: state.research?.count, entries: state.research?.entries,
            references: state.research?.references, aliases: state.research?.aliases, conflicts: state.research?.conflicts },
          materials: { valid: state.materials?.valid, count: state.materials?.count, adjusted: state.materials?.adjusted },
          registered: state.handlers?.length ?? 0, supported: state.handlers?.filter(handler => handler.supported).length ?? 0 };
      }
      if (command === 'start') {
        const phase = argument ?? 'data', state = await inspect(call, true), plan = selection(state, phase);
        await record(phase + '-coverage.json', plan);
        let previous;
        try { previous = await json(path.join(reports, phase + '-request.json')); } catch (error) { if (error.code !== 'ENOENT') throw error; }
        const key = id(values.key ?? previous?.key ?? `accept-${phase}-${randomUUID().slice(0, 8)}`);
        const request = { key, name: 'gtnh-' + phase, world: config.world, profile: phase === 'data' ? 'data' : 'full',
          handlers: plan.selected.map(handler => handler.id).sort(), probes: [{ count: 1, channels: {} }] };
        if (previous?.key === key) assert.deepEqual(request, previous, 'Retry parameters changed; use a new --key after reviewing the new selection');
        await record(key + '-request.json', request); await record(phase + '-request.json', request);
        const job = await call('start_export', request); await record(id(job.id) + '-job.json', job);
        return { job: job.id, state: job.state, scope: 'selection', selected: plan.selected.map(handler => handler.name), registered: plan.registered };
      }
      if (command === 'cancel') { const job = await call('cancel_export', { id: id(argument) }); return await record(id(job.id) + '-job.json', job); }
      const { job } = await call('read_job', argument ? { id: id(argument) } : {});
      assert.ok(job, 'No export job exists'); await record(id(job.id) + '-job.json', job);
      if (command === 'status') return { id: job.id, state: job.state, stage: job.stage, completed: job.completed, total: job.total, error: job.error, result: job.result };
      assert.equal(job.state, 'succeeded', 'Export must succeed before verification');
      assert.equal(job.request.world, config.world, 'This job was not bound to the test save');
      const source = await call('read_export', { id: job.result.id });
      assert.match(source.id, /^[a-f0-9]{64}$/);
      const input = path.join(config.instance, 'nesql', 'datasets', source.id);
      assert.equal(path.resolve(source.path), path.resolve(input));
      assert.equal(source.revision, config.revision);
      assert.equal(source.producer.version, config.version, 'Export was produced by a different mod build');
      assert.equal(source.scope.mode, 'selection', 'Acceptance must retain its explicit selection scope');
      await record(source.id + '-source.json', source);
      return await record('handoff-' + id(job.id) + '.json', {
        job: job.id, source: source.id, path: input, manifest: path.join(input, 'manifest.json'),
        scope: source.scope, counts: source.counts, toolchain, verification: 'pending',
        jobReport: path.join(reports, job.id + '-job.json'), sourceReport: path.join(reports, source.id + '-source.json'),
      });
    });
  }
  console.log(JSON.stringify(result));
} catch (error) {
  const failure = { command, error: { code: error.code ?? 'acceptance_failed', message: error.message } };
  await record('last-error.json', failure);
  console.error(JSON.stringify(failure)); process.exitCode = 1;
}
