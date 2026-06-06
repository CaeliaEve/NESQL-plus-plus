import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const args = process.argv.slice(2);
const gtnhRootArg = args.includes('--gtnh-root') ? args[args.indexOf('--gtnh-root') + 1] : null;
const gtnhRoot = path.resolve(gtnhRootArg ?? process.env.GTNH_SOURCE_ROOT ?? path.join(repoRoot, '..', 'GTNH'));

function read(relativePath) {
  return fs.readFileSync(path.join(repoRoot, relativePath), 'utf8').replace(/^\uFEFF/, '');
}

function listJavaFiles(dir) {
  const out = [];
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) out.push(...listJavaFiles(full));
    if (entry.isFile() && entry.name.endsWith('.java')) out.push(full);
  }
  return out;
}

function sourceExists(...segments) {
  return fs.existsSync(path.join(gtnhRoot, ...segments));
}

const rulePath = 'src/main/resources/gtnh-semantic-rules/semantic-rules.json';
const rules = JSON.parse(read(rulePath));
const familyRules = Array.isArray(rules.families) ? rules.families : [];
const ruleIds = new Set(familyRules.map((family) => family.id));

const semanticSourceDir = path.join(repoRoot, 'src/main/java/com/github/dcysteine/nesql/exporter/semantic');
const semanticJava = listJavaFiles(semanticSourceDir).map((file) => fs.readFileSync(file, 'utf8')).join('\n');
const pluginIds = new Set(Array.from(semanticJava.matchAll(/return\s+"([a-z0-9_.-]+)";/g), (match) => match[1])
  .filter((id) => id.includes('.') || id.includes('_')));

const sourceHints = [
  { family: 'facade.ae2', roots: ['Applied-Energistics-2-Unofficial'] },
  { family: 'facade.buildcraft', roots: ['BuildCraft'] },
  { family: 'facade.enderio.paint', roots: ['EnderIO'] },
  { family: 'thaumcraft.wand', roots: ['Thaumcraft', 'thaumcraft-api', 'ThaumicTinkerer'] },
  { family: 'genetics.forestry', roots: ['ForestryMC'] },
  { family: 'genetics.binnie-gendustry', roots: ['Binnie', 'Gendustry'] },
  { family: 'tool.gregtech', roots: ['GT5-Unofficial'] },
  { family: 'charge.gregtech', roots: ['GT5-Unofficial'] },
  { family: 'tool.tconstruct', roots: ['TinkersConstruct'] },
  { family: 'toolpart.tconstruct', roots: ['TinkersConstruct'] },
  { family: 'entity_capture.enderio', roots: ['EnderIO'] },
  { family: 'fluid.container', roots: ['GT5-Unofficial', 'EnderIO', 'BuildCraft'] },
  { family: 'data_carrier.encoded-pattern', roots: ['Applied-Energistics-2-Unofficial'] },
];

const missingRuleIds = [...pluginIds].filter((id) => !ruleIds.has(id)).sort();
const missingPluginIds = [...ruleIds].filter((id) => !pluginIds.has(id)).sort();
const missingSourceHints = sourceHints
  .filter((hint) => ruleIds.has(hint.family))
  .filter((hint) => !hint.roots.some((root) => sourceExists(root)))
  .map((hint) => hint.family);

assert.equal(rules.schemaVersion, 'nesqlpp/gtnh-semantic-rules/alpha1');
assert.equal(rules.packVersionSource, 'gtnh-profile-metadata');
assert.ok(rules.generatedFrom?.refreshCommand?.includes('validate-semantic-rule-pack.mjs'));
assert.equal(missingRuleIds.length, 0, `plugin family ids missing from rule pack: ${missingRuleIds.join(', ')}`);
assert.equal(missingPluginIds.length, 0, `rule family ids missing Java plugin: ${missingPluginIds.join(', ')}`);

if (fs.existsSync(gtnhRoot)) {
  assert.equal(missingSourceHints.length, 0, `GTNH source hint roots missing: ${missingSourceHints.join(', ')}`);
}

const report = {
  schemaVersion: 'nesqlpp/semantic-rule-pack-validation/current',
  gtnhRoot,
  ruleFamilyCount: ruleIds.size,
  pluginFamilyCount: pluginIds.size,
  sourceHintCount: sourceHints.length,
  checkedGtnhSourceHints: fs.existsSync(gtnhRoot),
  status: 'ok',
};

console.log(JSON.stringify(report, null, 2));
