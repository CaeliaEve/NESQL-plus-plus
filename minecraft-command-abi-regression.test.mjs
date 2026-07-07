import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { execFileSync } from 'node:child_process';

const repoRoot = process.cwd();
const readSource = (relativePath) => fs.readFileSync(path.join(repoRoot, relativePath), 'utf8');

const buildGradle = readSource('build.gradle.kts');
const exportCommand = readSource(
  'src/main/java/com/github/dcysteine/nesql/exporter/main/ExportCommand.java',
);
const builtJar = path.join(repoRoot, 'build/libs/NESQL++-1.04.jar');

test('jar builds are finalized by ForgeGradle reobf for GTNH runtime command ABI', () => {
  assert.match(buildGradle, /tasks\.named\("jar"\)\s*\{[\s\S]*finalizedBy\("reobf"\)/);
  assert.match(buildGradle, /AbstractMethodError/);
});

test('command source stays MCP-only and lets reobf own production method naming', () => {
  assert.match(exportCommand, /Collections\.emptyList\(\)/);
  assert.doesNotMatch(exportCommand, /\bfunc_715/);
  assert.doesNotMatch(exportCommand, /\bfunc_82358_a/);
});

test(
  'built production jar exposes SRG ICommand methods after reobf',
  { skip: fs.existsSync(builtJar) ? false : 'run ./gradlew build or ./gradlew jar first' },
  () => {
    const javap = execFileSync(
      'javap',
      ['-classpath', builtJar, '-p', 'com.github.dcysteine.nesql.exporter.main.ExportCommand'],
      { cwd: repoRoot, encoding: 'utf8' },
    );

    for (const method of [
      'func_71517_b',
      'func_71518_a',
      'func_71514_a',
      'func_71515_b',
      'func_71519_b',
      'func_71516_a',
      'func_82358_a',
    ]) {
      assert.match(javap, new RegExp(`\\b${method}\\s*\\(`), `missing ${method}`);
    }

    assert.doesNotMatch(javap, /\bgetCommandAliases\s*\(/);
  },
);
