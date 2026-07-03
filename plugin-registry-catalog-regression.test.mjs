import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import test from 'node:test';

const repoRoot = process.cwd();
const readSource = (relativePath) => fs.readFileSync(path.join(repoRoot, relativePath), 'utf8');

const registry = readSource('src/main/java/com/github/dcysteine/nesql/exporter/registry/PluginRegistry.java');
const catalog = readSource('src/main/java/com/github/dcysteine/nesql/exporter/registry/PluginRegistryCatalog.java');

test('plugin registry ordering and construction are descriptor-catalog owned', () => {
  assert.match(catalog, /final class PluginRegistryCatalog/);
  assert.match(catalog, /PLUGINS = validateAndFreeze\(Arrays\.asList/);
  assert.match(catalog, /static ImmutableList<RegistryEntry> entries\(\)/);
  assert.match(catalog, /static ImmutableList<PluginDescriptor> descriptors\(\)/);
  assert.match(catalog, /validateAndFreeze/);
  assert.match(catalog, /Missing plugin registry descriptor/);
  assert.match(catalog, /Duplicate plugin registry descriptor/);
  assert.match(catalog, /Plugin registry constructor must not be null/);
  assert.match(catalog, /Plugin registry catalog action must not be null/);
  assert.match(catalog, /Plugin registry catalog must include the fallback NEI role/);

  for (const plugin of [
    'BASE',
    'MINECRAFT',
    'FORGE',
    'GREGTECH',
    'QUEST',
    'AVARITIA',
    'THAUMCRAFT',
    'BOTANIA',
    'BLOOD_MAGIC',
    'WITCHERY',
    'NEI',
  ]) {
    assert.match(catalog, new RegExp(`Plugin\\.${plugin}`), `catalog missing ${plugin}`);
  }

  assert.match(catalog, /BasePluginExporter::new/);
  assert.match(catalog, /MinecraftPluginExporter::new/);
  assert.match(catalog, /ForgePluginExporter::new/);
  assert.match(catalog, /GregTechPluginExporter::new/);
  assert.match(catalog, /QuestPluginExporter::new/);
  assert.match(catalog, /AvaritiaPluginExporter::new/);
  assert.match(catalog, /ThaumcraftPluginExporter::new/);
  assert.match(catalog, /BotaniaPluginExporter::new/);
  assert.match(catalog, /BloodMagicPluginExporter::new/);
  assert.match(catalog, /WitcheryPluginExporter::new/);
  assert.match(catalog, /NeiPluginExporter::new/);
  assert.match(catalog, /ModDependency\.GREGTECH_5/);
  assert.match(catalog, /ModDependency\.BETTER_QUESTING/);
  assert.match(catalog, /ModDependency\.AVARITIA/);
  assert.match(catalog, /ModDependency\.THAUMCRAFT/);
  assert.match(catalog, /ModDependency\.BOTANIA/);
  assert.match(catalog, /ModDependency\.BLOOD_MAGIC/);
  assert.match(catalog, /ModDependency\.WITCHERY/);
  assert.match(catalog, /makeGTRecipe\(\)/);
  assert.match(catalog, /role\(\)/);
  assert.match(catalog, /"fallback-nei"/);
});

test('PluginRegistry consumes catalog entries without direct exporter construction policy', () => {
  assert.match(registry, /Runtime consumer of the descriptor-owned plugin registry catalog/);
  assert.match(registry, /PluginRegistryCatalog\.entries\(\)\.stream\(\)/);
  assert.match(registry, /activePlugins\.put/);
  assert.match(registry, /base plugin must be enabled!/);

  for (const forbidden of [
    /BasePluginExporter/,
    /MinecraftPluginExporter/,
    /ForgePluginExporter/,
    /GregTechPluginExporter/,
    /QuestPluginExporter/,
    /AvaritiaPluginExporter/,
    /ThaumcraftPluginExporter/,
    /BotaniaPluginExporter/,
    /BloodMagicPluginExporter/,
    /WitcheryPluginExporter/,
    /NeiPluginExporter/,
    /ImmutableList\.Builder<RegistryEntry>/,
    /makeGTRecipe\(\)/,
    /Register new plugins here/,
    /Add new plugins here/,
  ]) {
    assert.doesNotMatch(registry, forbidden);
  }
});
