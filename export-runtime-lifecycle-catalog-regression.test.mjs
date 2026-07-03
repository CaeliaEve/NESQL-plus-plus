import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import test from 'node:test';

const repoRoot = process.cwd();
const readSource = (relativePath) => fs.readFileSync(path.join(repoRoot, relativePath), 'utf8');

const runtime = readSource('src/main/java/com/github/dcysteine/nesql/exporter/main/ExportRuntime.java');
const lifecycleCatalog = readSource(
  'src/main/java/com/github/dcysteine/nesql/exporter/main/ExportPluginLifecycleCatalog.java',
);
const pluginExporter = readSource('src/main/java/com/github/dcysteine/nesql/exporter/plugin/PluginExporter.java');

test('export plugin lifecycle phases are descriptor-owned', () => {
  assert.match(lifecycleCatalog, /final class ExportPluginLifecycleCatalog/);
  assert.match(lifecycleCatalog, /PHASE_INITIALIZE = "initialize"/);
  assert.match(lifecycleCatalog, /PHASE_PROCESS = "process"/);
  assert.match(lifecycleCatalog, /PHASE_POST_PROCESS = "postProcess"/);
  assert.match(lifecycleCatalog, /PHASES = validateAndFreeze\(Arrays\.asList/);
  assert.match(lifecycleCatalog, /PluginExporter::initialize/);
  assert.match(lifecycleCatalog, /PluginExporter::process/);
  assert.match(lifecycleCatalog, /PluginExporter::postProcess/);
  assert.match(lifecycleCatalog, /static List<PhaseDescriptor> phases\(\)/);
  assert.match(lifecycleCatalog, /Duplicate plugin lifecycle phase id/);
  assert.match(lifecycleCatalog, /Plugin lifecycle phase invoker must not be null/);
  assert.match(lifecycleCatalog, /Plugin lifecycle phase catalog must not be empty/);
  assert.match(lifecycleCatalog, /interface PhaseInvoker/);
  assert.match(lifecycleCatalog, /void invoke\(PluginExporter exporter\)/);

  assert.match(pluginExporter, /public void initialize\(\) \{\}/);
  assert.match(pluginExporter, /public void process\(\) \{\}/);
  assert.match(pluginExporter, /public void postProcess\(\) \{\}/);
});

test('export runtime consumes lifecycle descriptors without string dispatch branches', () => {
  assert.match(runtime, /ExportPluginLifecycleCatalog\.phases\(\)/);
  assert.match(runtime, /runPluginPhase\(ExportPluginLifecycleCatalog\.PhaseDescriptor phase\)/);
  assert.match(runtime, /phase\.invoke\(entry\.getValue\(\)\)/);
  assert.match(runtime, /phase\.id\(\)/);
  assert.doesNotMatch(runtime, /runPluginPhase\("initialize"\)/);
  assert.doesNotMatch(runtime, /runPluginPhase\("process"\)/);
  assert.doesNotMatch(runtime, /runPluginPhase\("postProcess"\)/);
  assert.doesNotMatch(runtime, /"initialize"\.equals\(phase\)/);
  assert.doesNotMatch(runtime, /"process"\.equals\(phase\)/);
  assert.doesNotMatch(runtime, /"postProcess"\.equals\(phase\)/);
  assert.doesNotMatch(runtime, /Unknown plugin phase/);
});
