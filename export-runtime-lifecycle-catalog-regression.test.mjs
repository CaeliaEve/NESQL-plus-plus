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
const pluginResult = readSource('src/main/java/com/github/dcysteine/nesql/exporter/plugin/PluginExportResult.java');
const pluginPolicy = readSource('src/main/java/com/github/dcysteine/nesql/exporter/main/PluginExportPolicy.java');
const neiExporter = readSource('src/main/java/com/github/dcysteine/nesql/exporter/plugin/nei/NeiPluginExporter.java');
const neiProcessor = readSource('src/main/java/com/github/dcysteine/nesql/exporter/plugin/nei/NeiRecipeExportProcessor.java');
const neiBatch = readSource('src/main/java/com/github/dcysteine/nesql/exporter/plugin/nei/NeiRecipeBatchLoader.java');
const neiBatchOutcome = readSource('src/main/java/com/github/dcysteine/nesql/exporter/plugin/nei/NeiRecipeBatchOutcome.java');

test('export plugin lifecycle phases are descriptor-owned', () => {
  assert.match(lifecycleCatalog, /final class ExportPluginLifecycleCatalog/);
  assert.match(lifecycleCatalog, /PHASE_INITIALIZE = "initialize"/);
  assert.match(lifecycleCatalog, /PHASE_PROCESS = "process"/);
  assert.match(lifecycleCatalog, /PHASE_POST_PROCESS = "postProcess"/);
  assert.match(lifecycleCatalog, /PHASES = validateAndFreeze\(Arrays\.asList/);
  assert.match(lifecycleCatalog, /PluginExporter::initializeResult/);
  assert.match(lifecycleCatalog, /PluginExporter::processResult/);
  assert.match(lifecycleCatalog, /PluginExporter::postProcessResult/);
  assert.match(lifecycleCatalog, /static List<PhaseDescriptor> phases\(\)/);
  assert.match(lifecycleCatalog, /Duplicate plugin lifecycle phase id/);
  assert.match(lifecycleCatalog, /Plugin lifecycle phase invoker must not be null/);
  assert.match(lifecycleCatalog, /Plugin lifecycle phase catalog must not be empty/);
  assert.match(lifecycleCatalog, /interface PhaseInvoker/);
  assert.match(lifecycleCatalog, /PluginExportResult invoke\(PluginExporter exporter\)/);

  assert.match(pluginExporter, /public void initialize\(\) \{\}/);
  assert.match(pluginExporter, /public void process\(\) \{\}/);
  assert.match(pluginExporter, /public void postProcess\(\) \{\}/);
  assert.match(pluginExporter, /public PluginExportResult processResult\(\)/);
});

test('plugin results and NEI core failures are explicit and fail closed', () => {
  assert.match(pluginResult, /SUCCESS\("success"\)/);
  assert.match(pluginResult, /PARTIAL\("partial"\)/);
  assert.match(pluginResult, /SKIPPED\("skipped"\)/);
  assert.match(pluginResult, /FAILED\("failed"\)/);
  assert.match(pluginResult, /public final Map<String, Long> counts/);
  assert.match(pluginResult, /public final List<Error> errors/);
  assert.match(pluginPolicy, /partial result is not allowed/);
  assert.match(runtime, /PluginExportPolicy\.requirePublishable/);
  assert.match(runtime, /PluginExportResult\.failed\(/);
  assert.match(neiExporter, /public PluginExportResult processResult\(\)/);
  assert.doesNotMatch(neiExporter, /catch \(Exception e\)/);
  assert.match(neiProcessor, /public PluginExportResult process\(\)/);
  assert.doesNotMatch(neiProcessor.slice(neiProcessor.indexOf('public PluginExportResult process()'), neiProcessor.indexOf('private PluginExportResult exportKnownBotaniaCoreRecipes')), /catch \(/);
  assert.match(neiBatchOutcome, /nei-handler-export-failed/);
  assert.match(neiProcessor, /NEI recipe row export failed for handler/);
  assert.match(neiProcessor, /NEI crafting handler export failed/);
  assert.match(neiBatch, /Non-template NEI recipe resolution failed/);
  assert.match(neiBatch, /NEI crafting handler recipe load failed/);
  assert.doesNotMatch(neiBatch, /catch \(Exception e\) \{\s*\}/);
  assert.match(neiBatch, /NeiRecipeBatchOutcome outcome = new NeiRecipeBatchOutcome\(\)/);
  assert.match(neiBatch, /outcome\.recordFailure/);
  assert.match(neiBatchOutcome, /PluginExportResult\.Status\.PARTIAL/);
  assert.doesNotMatch(neiBatch, /streamExportAllUsageRecipes/);
  assert.doesNotMatch(neiProcessor, /exportSingleUsageHandler|exportUsageRecipesFromHandler/);
  assert.match(neiProcessor, /optional-adapter-unavailable/);
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
