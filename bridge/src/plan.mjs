import assert from 'node:assert/strict';
import { memoryPolicy } from './resources.mjs';
import { createHash } from 'node:crypto';
import { readFile, stat, realpath } from 'node:fs/promises';
import path from 'node:path';

export const digest = bytes => createHash('sha256').update(bytes).digest('hex');
export const sha = value => typeof value === 'string' && /^[a-f0-9]{64}$/.test(value);
export const json = async file => JSON.parse(await readFile(file, 'utf8'));

export function handlerDomain(item) {
  return item.domain === 'structures' || ['domain:structure', 'domain:structures'].includes(item.route)
    || item.route?.startsWith('structure:') ? 'structures' : 'recipes';
}

export function validateHandlerRoute(item) {
  const domain = handlerDomain(item);
  if (domain === 'recipes' && ['domain', 'interactive'].includes(item.classification)) {
    assert.ok(item.route?.startsWith(`${item.classification}:`), `Invalid ${item.classification} route`);
  }
  return domain;
}

export function canonical(value) {
  if (Array.isArray(value)) return value.map(canonical);
  if (value && typeof value === 'object') return Object.fromEntries(Object.keys(value).sort().map(key => [key, canonical(value[key])]));
  return value;
}

export async function fileWithin(root, name) {
  assert.equal(typeof name, 'string', 'Artifact path must be a string');
  assert.ok(name && !path.isAbsolute(name) && !name.includes('\\'), 'Artifact path must be relative with forward slashes');
  const base = await realpath(root);
  const file = await realpath(path.resolve(base, name));
  assert.ok(file.startsWith(base + path.sep), 'Artifact path escapes its directory');
  assert.ok((await stat(file)).isFile(), `Artifact is not a file: ${file}`);
  return file;
}

export async function artifact(root, entry, limit = Infinity) {
  assert.ok(entry && sha(entry.sha256) && Number.isSafeInteger(entry.bytes) && entry.bytes >= 0 && entry.bytes <= limit, 'Invalid artifact digest or byte count');
  const file = await fileWithin(root, entry.path);
  assert.equal((await stat(file)).size, entry.bytes, `Artifact byte count mismatch: ${file}`);
  const bytes = await readFile(file);
  assert.equal(digest(bytes), entry.sha256, `Artifact SHA256 mismatch: ${file}`);
  return { file, bytes };
}

export async function bundle(root) {
  assert.ok(typeof root === 'string' && path.isAbsolute(root), 'Bundle directory must be absolute');
  const manifest = await readFile(path.join(root, 'files.json'));
  const inventory = JSON.parse(manifest.toString('utf8'));
  const files = Array.isArray(inventory) ? inventory : inventory.files;
  assert.ok(Array.isArray(files) && files.length, `Empty bundle inventory: ${root}`);
  const names = new Set();
  for (const entry of files) {
    assert.ok(!names.has(entry.path), 'Duplicate bundle artifact'); names.add(entry.path);
    await artifact(root, entry);
  }
  return { path: root, sha256: digest(manifest), files };
}

/** The same preflight runs before a rehearsal and an actual execution. */
export async function prepare(configFile) {
  const configPath = path.resolve(configFile);
  const config = await json(configPath);
  for (const field of ['instance', 'mod', 'compiler', 'web', 'worklist', 'reports']) {
    assert.ok(typeof config[field] === 'string' && path.isAbsolute(config[field]), `${field} must be an absolute path`);
  }
  assert.match(config.world ?? '', /^[^/\\\x00-\x1f\x7f]{1,128}$/, 'A test save folder is required');
  assert.ok(!['.', '..'].includes(config.world), 'Invalid save folder');
  assert.ok((await stat(path.join(config.instance, 'saves', config.world, 'level.dat'))).isFile(), 'Test save is missing');
  assert.ok(typeof config.version === 'string' && config.version && Number.isSafeInteger(config.revision) && config.revision > 0, 'Candidate version and revision are required');
  const packages = {};
  for (const name of ['mod', 'compiler', 'web']) packages[name] = await bundle(config[name]);
  const mod = packages.mod.files.find(file => file.path === `mod/NESQL++-${config.version}.jar`);
  assert.ok(mod, 'Candidate mod jar is absent from the bundle manifest');
  assert.ok(sha(config.modSha256), 'Candidate modSha256 is required');
  assert.equal(mod.sha256, config.modSha256, 'Candidate mod SHA256 mismatch');
  const binary = process.platform === 'win32' ? 'elysium-compiler.exe' : 'elysium-compiler';
  assert.ok(packages.compiler.files.some(file => file.path === binary), 'Compiler executable is absent from its manifest');
  assert.ok(packages.web.files.some(file => file.path === 'frontend/dist/index.html'), 'Web entry point is absent from its manifest');
  const worklistBytes = await readFile(config.worklist);
  const worklist = JSON.parse(worklistBytes.toString('utf8'));
  assert.ok(Array.isArray(worklist.handlers) && worklist.handlers.length, 'A nonempty handler worklist is required');
  const ids = new Set();
  for (const item of worklist.handlers) {
    assert.match(item.id ?? '', /^category_[a-f0-9]{64}$/, 'Invalid handler identity');
    assert.ok(!ids.has(item.id), 'Duplicate handler identity'); ids.add(item.id);
    assert.ok(['recipe', 'domain', 'interactive', 'tooling'].includes(item.classification), `Missing classification for ${item.id}`);
    assert.equal(typeof item.route, 'string', `Missing route for ${item.id}`);
    validateHandlerRoute(item);
  }
  const stageManifest = config.stages ?? {
    source: { required: true, reports: config.reports },
    compiler: { required: true, executable: path.join(config.compiler, binary) },
    web: { required: true, entry: path.join(config.web, 'frontend', 'dist', 'index.html') }
  };
  if(config.diagnostics!==undefined){
    assert.ok(config.diagnostics&&typeof config.diagnostics==='object'&&!Array.isArray(config.diagnostics)&&Object.keys(config.diagnostics).every(key=>['collectVisuals','visualCoverage'].includes(key))&&config.diagnostics.collectVisuals===true&&[undefined,'representative','all'].includes(config.diagnostics.visualCoverage),'Invalid diagnostic collection options');
    assert.ok(stageManifest.source?.execute===false&&stageManifest.compiler?.execute===false&&stageManifest.source?.preflight,'Diagnostic collection requires exports disabled and rendering preflight enabled');
  }
  assert.ok(stageManifest && typeof stageManifest === 'object', 'Stage manifest is required');
  assert.ok(stageManifest.source?.required === true, 'Source export stage is required');
  // Catch stale export selections during dry-run, before complete native diagnostics.
  if (stageManifest.source.handlers !== undefined || stageManifest.source.execute) {
    const selection = stageManifest.source.handlers;
    assert.ok(Array.isArray(selection) && selection.length, 'Source selection must be explicit and nonempty');
    for (const id of selection) assert.match(id, /^category_[a-f0-9]{64}$/, 'Source selection has an invalid handler identity');
    assert.equal(new Set(selection).size, selection.length, 'Duplicate source handler');
    assert.deepEqual([...selection].sort(), worklist.handlers.filter(item => item.classification !== 'tooling').map(item => item.id).sort(),
      'Source selection differs from the diagnostic gameplay worklist');
  }
  assert.ok(stageManifest.compiler?.required === true && path.isAbsolute(stageManifest.compiler.executable), 'Compiler stage is not configured');
  assert.ok(stageManifest.web?.required === true && path.isAbsolute(stageManifest.web.entry), 'Web stage is not configured');
  assert.ok((await stat(stageManifest.compiler.executable)).isFile(), 'Compiler stage executable is missing');
  assert.ok((await stat(stageManifest.web.entry)).isFile(), 'Web stage entry point is missing');
  assert.equal(await realpath(stageManifest.compiler.executable), await realpath(path.join(config.compiler, binary)),
    'Compiler stage must execute the verified compiler bundle');
  assert.equal(await realpath(stageManifest.web.entry), await realpath(path.join(config.web, 'frontend/dist/index.html')),
    'Web stage must use the verified web bundle');
  const plan = {
    world: config.world, modSha256: mod.sha256, expectedExporter: config.version, expectedRevision: config.revision,
    handlers: worklist.handlers, probes: config.probes ?? [{ count: 1, channels: {} }],
    stages: stageManifest,
    ...(config.diagnostics?{diagnostics:config.diagnostics}:{}),
    ...(memoryPolicy(config.memory) ? { memory: config.memory } : {}),
    dependencies: { packages: Object.fromEntries(Object.entries(packages).map(([name, pkg]) => [name, pkg.sha256])), worklist: digest(worklistBytes) },
  };
  return { config, plan, packages, binary: path.join(config.compiler, binary) };
}
