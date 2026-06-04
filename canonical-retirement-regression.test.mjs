import { readFileSync } from 'node:fs';

const checks = [];
const gui = readFileSync('src/main/java/com/github/dcysteine/nesql/exporter/main/ExportSelectionGui.java', 'utf8');
if (gui.includes('Debug canonical snapshot')) {
  checks.push('ExportSelectionGui still exposes Debug canonical snapshot');
}
if (!gui.includes('.writeCanonicalSnapshot(false)')) {
  checks.push('ExportSelectionGui must force writeCanonicalSnapshot(false)');
}
const runner = readFileSync('src/main/java/com/github/dcysteine/nesql/exporter/main/ExportStageRunner.java', 'utf8');
if (!runner.includes('if (pipelineCompleted) {\n                ExportWriterSupport.deleteCanonicalStagingDirectory')) {
  checks.push('ExportStageRunner must delete canonical staging after every successful production pipeline');
}
const dataCommand = readFileSync('src/main/java/com/github/dcysteine/nesql/exporter/main/DataExportCommand.java', 'utf8');
if (dataCommand.includes('canonical snapshot')) {
  checks.push('DataExportCommand still advertises canonical snapshot output');
}
if (checks.length) {
  console.error('[canonical-retirement] failed');
  for (const check of checks) console.error(`- ${check}`);
  process.exit(1);
}
console.log('[canonical-retirement] production export no longer exposes canonical selection');
