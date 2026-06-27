import assert from 'node:assert/strict';
import { existsSync, readFileSync } from 'node:fs';
import test from 'node:test';

const sidecarUrl = new URL('./src/main/java/com/github/dcysteine/nesql/exporter/local/RawExportSidecarWriter.java', import.meta.url);
const validationUrl = new URL('./src/main/java/com/github/dcysteine/nesql/exporter/local/RawExportValidationSupport.java', import.meta.url);
const manifestBuilderUrl = new URL('./src/main/java/com/github/dcysteine/nesql/exporter/local/RawExportManifestBuilder.java', import.meta.url);
const reportWriterUrl = new URL('./src/main/java/com/github/dcysteine/nesql/exporter/local/RawExportReportWriter.java', import.meta.url);
const repositoryFactStreamerUrl = new URL('./src/main/java/com/github/dcysteine/nesql/exporter/local/RawExportRepositoryFactStreamer.java', import.meta.url);
const repositoryFactResultUrl = new URL('./src/main/java/com/github/dcysteine/nesql/exporter/local/RawRepositoryFactStreamResult.java', import.meta.url);
const dtoFiles = [
  'RawExportManifest.java',
  'RawExportReport.java',
  'RawExportCounts.java',
  'RawExportValidation.java',
  'RawValidationGate.java',
  'RawExportFileRef.java',
];

const sidecar = readFileSync(sidecarUrl, 'utf8');
const validation = readFileSync(validationUrl, 'utf8');
const manifestBuilder = readFileSync(manifestBuilderUrl, 'utf8');
const reportWriter = readFileSync(reportWriterUrl, 'utf8');
const repositoryFactStreamer = readFileSync(repositoryFactStreamerUrl, 'utf8');

test('raw export validation logic lives outside RawExportSidecarWriter', () => {
  assert.match(sidecar, /RawExportValidationSupport\.apply\(report\)/);
  assert.doesNotMatch(sidecar, /buildRawValidationGates/);
  assert.doesNotMatch(sidecar, /rawValidationReady/);
  assert.doesNotMatch(sidecar, /addCountMismatch/);
  assert.match(validation, /buildRawValidationGates/);
  assert.match(validation, /rawValidationReady/);
});

test('raw export report DTOs are top-level local components', () => {
  for (const file of dtoFiles) {
    assert.equal(
      existsSync(new URL(`./src/main/java/com/github/dcysteine/nesql/exporter/local/${file}`, import.meta.url)),
      true,
      `${file} must exist as a top-level local component`,
    );
  }
  assert.doesNotMatch(sidecar, /class RawExportManifest/);
  assert.doesNotMatch(sidecar, /class RawExportReport/);
  assert.doesNotMatch(sidecar, /class RawExportCounts/);
  assert.doesNotMatch(sidecar, /class RawExportValidation/);
  assert.doesNotMatch(sidecar, /class RawValidationGate/);
});


test('raw export manifest and report output are split from sidecar orchestration', () => {
  assert.equal(existsSync(manifestBuilderUrl), true, 'RawExportManifestBuilder must exist');
  assert.equal(existsSync(reportWriterUrl), true, 'RawExportReportWriter must exist');
  assert.match(sidecar, /RawExportManifestBuilder\.build\(SCHEMA_VERSION, generatedAt, exportContext, report\)/);
  assert.match(sidecar, /RawExportReportWriter\.write\(SCHEMA_VERSION, generatedAt, rawDir, manifest, report\)/);
  assert.doesNotMatch(sidecar, /private\s+RawExportManifest\s+buildManifest/);
  assert.doesNotMatch(sidecar, /writeSizeReport\(/);
  assert.doesNotMatch(sidecar, /createEmptyJsonlIfMissing\(/);
  assert.match(manifestBuilder, /manifest\.capabilities\.add\("facts"\)/);
  assert.match(manifestBuilder, /manifest\.files\.put\("exportReport", "validation\/export_report\.json"\)/);
  assert.match(reportWriter, /writeJson\(gson, new File\(rawDir, "validation\/export_report\.json"\), report\)/);
  assert.match(reportWriter, /writeSizeReport\(gson, schemaVersion, generatedAt, rawDir\)/);
});


test('raw repository fact streaming is split from sidecar orchestration', () => {
  assert.equal(existsSync(repositoryFactStreamerUrl), true, 'RawExportRepositoryFactStreamer must exist');
  assert.equal(existsSync(repositoryFactResultUrl), true, 'RawRepositoryFactStreamResult must exist');
  assert.match(sidecar, /new RawExportRepositoryFactStreamer\(entityManager, rawDir, SCHEMA_VERSION\)\.write\(\)/);
  assert.match(sidecar, /RawRepositoryFactStreamResult repository = streamRepositoryFacts\(rawDir\)/);
  assert.doesNotMatch(sidecar, /streamDatabaseRepositoryFacts/);
  assert.doesNotMatch(sidecar, /streamDatabaseItems/);
  assert.doesNotMatch(sidecar, /streamDatabaseFluids/);
  assert.doesNotMatch(sidecar, /streamDatabaseRecipes/);
  assert.doesNotMatch(sidecar, /class RecipeShardState/);
  assert.doesNotMatch(sidecar, /class SpecialDomainStreamState/);
  assert.doesNotMatch(sidecar, /class JsonlWriter/);
  assert.doesNotMatch(sidecar, /loadGregTechRecipeBatch/);
  assert.match(repositoryFactStreamer, /streamDatabaseItems/);
  assert.match(repositoryFactStreamer, /streamDatabaseFluids/);
  assert.match(repositoryFactStreamer, /streamDatabaseRecipes/);
  assert.match(repositoryFactStreamer, /class RecipeShardState/);
  assert.match(repositoryFactStreamer, /class SpecialDomainStreamState/);
  assert.match(repositoryFactStreamer, /class JsonlWriter/);
});
