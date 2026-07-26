import { readdirSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const repoRoot = dirname(dirname(fileURLToPath(import.meta.url)));
const testFiles = readdirSync(repoRoot)
  .filter((name) => name.endsWith('.test.mjs'))
  .sort()
  .map((name) => join(repoRoot, name));

if (testFiles.length === 0) {
  throw new Error(`No root regression tests found in ${repoRoot}`);
}

const result = spawnSync(
  process.execPath,
  ['--test', '--test-concurrency=1', ...testFiles],
  {
    cwd: repoRoot,
    stdio: 'inherit',
  },
);

if (result.error) {
  throw result.error;
}

process.exitCode = result.status ?? 1;
