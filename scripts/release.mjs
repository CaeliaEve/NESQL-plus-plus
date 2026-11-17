import { execFileSync } from 'node:child_process';
import { constants } from 'node:fs';
import { copyFile, mkdir, readFile, readdir, writeFile } from 'node:fs/promises';
import { createHash } from 'node:crypto';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

if (process.argv.length > 3) throw new Error('Usage: node scripts/release.mjs [output-directory]');
const root = fileURLToPath(new URL('../', import.meta.url));
const output = path.resolve(process.argv[2] ?? path.join(root, 'release'));
const version = (await readFile(path.join(root, 'gradle.properties'), 'utf8')).match(/^modVersion=([0-9A-Za-z.+-]+)$/m)?.[1];
if (!version) throw new Error('Missing modVersion');
const mod = `NESQL++-${version}.jar`;
const modFile = path.resolve(root, process.env.NESQL_RELEASE_JAR ?? path.join('build/libs', mod));
execFileSync(process.execPath, [path.join(root, 'scripts/check-jar.mjs'), modFile], { stdio: 'inherit' });
const files = new Map([
  [`mod/${mod}`, modFile],
  ['README.md', 'README.md'],
  ['docs/mcp.md', 'docs/mcp.md'],
  ['docs/native-semantics.md', 'docs/native-semantics.md'],
  ['docs/native-usage.md', 'docs/native-usage.md'],
  ['docs/batch-diagnostics.md', 'docs/batch-diagnostics.md'],
  ['docs/native-batch-fixes.md', 'docs/native-batch-fixes.md'],
  ['docs/visual-throughput.md', 'docs/visual-throughput.md'],
  ['docs/native-item-animation.md', 'docs/native-item-animation.md'],
  ['docs/recovery.md', 'docs/recovery.md'],
  ['docs/native-ore-storage.md', 'docs/native-ore-storage.md'],
  ['bridge/package.json', 'bridge/package.json'],
  ['bridge/package-lock.json', 'bridge/package-lock.json'],
]);
for (const entry of await readdir(path.join(root, 'bridge/src'), { withFileTypes: true })) {
  if (!entry.isFile() || !entry.name.endsWith('.mjs')) throw new Error(`Unexpected bridge source: ${entry.name}`);
  files.set(`bridge/src/${entry.name}`, `bridge/src/${entry.name}`);
}
// Require a fresh destination; never replace an existing bundle or a game instance.
await mkdir(output);
const manifest = [];
for (const [name, source] of files) {
  const target = path.join(output, name);
  await mkdir(path.dirname(target), { recursive: true });
  await copyFile(path.resolve(root, source), target, constants.COPYFILE_EXCL);
  const bytes = await readFile(target);
  manifest.push({ path: name, bytes: bytes.length, sha256: createHash('sha256').update(bytes).digest('hex') });
}
await writeFile(path.join(output, 'files.json'), `${JSON.stringify(manifest, null, 2)}\n`, { flag: 'wx' });
console.log(`Release bundle: ${output}`);
