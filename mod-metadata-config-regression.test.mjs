import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';

const repoRoot = process.cwd();
const readSource = (relativePath) => fs.readFileSync(path.join(repoRoot, relativePath), 'utf8');

const mcmod = JSON.parse(readSource('src/main/resources/mcmod.info'))[0];
const configOptions = readSource(
  'src/main/java/com/github/dcysteine/nesql/exporter/main/config/ConfigOptions.java',
);
const main = readSource('src/main/java/com/github/dcysteine/nesql/exporter/main/Main.java');

test('Forge mod metadata points at the maintained NESQL++ fork and Elysium export purpose', () => {
  assert.equal(mcmod.name, 'NESQL++');
  assert.equal(mcmod.url, 'https://github.com/CaeliaEve/NESQL-plus-plus');
  assert.deepEqual(mcmod.authorList, ['CaeliaEve']);
  assert.match(mcmod.description, /native UI facts/);
  assert.match(mcmod.description, /browser atlas artifacts/);
  assert.match(mcmod.description, /Elysium\/NeoNEI compiler pipeline/);
  assert.doesNotMatch(mcmod.url, /D-Cysteine|nesql-exporter/);
  assert.doesNotMatch(mcmod.description, /H2 SQL database/);
});

test('in-game config comments describe the current native UI compiler export lane', () => {
  assert.match(configOptions, /"repository_name", "elysium-dev"/);
  assert.match(configOptions, /\/nesql --native-ui-export elysium-dev/);
  assert.match(configOptions, /GTNH raw-export/);
  assert.match(configOptions, /native UI compiler export/);
  assert.match(configOptions, /browser atlas assets and render manifests/);
  assert.match(configOptions, /animated atlas ABI/);
  assert.match(configOptions, /64 is the Elysium\/NeoNEI compiler ABI default/);
  assert.match(configOptions, /512 is the current high-throughput GTNH default/);
  assert.match(configOptions, /NESQL\+\+ raw-export and native UI compiler export options/);
  assert.doesNotMatch(configOptions, /nesql-repository/);
  assert.doesNotMatch(configOptions, /DISABLING THIS OPTION WILL DELETE YOUR CONFIG FILE!/);
});

test('startup command hints show native UI export as the final validation path', () => {
  assert.match(main, /Guided raw-export selection/);
  assert.match(main, /\/nesql --native-ui-export elysium-dev - Native UI compiler export/);
  assert.match(main, /ExportProfile\.NATIVE_UI_V104\.profileId/);
  assert.doesNotMatch(main, /Complete export \(data \+ images\)/);
});
