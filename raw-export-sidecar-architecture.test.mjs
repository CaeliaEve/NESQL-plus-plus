import assert from 'node:assert/strict';
import { existsSync, readFileSync } from 'node:fs';
import test from 'node:test';

const sidecarUrl = new URL('./src/main/java/com/github/dcysteine/nesql/exporter/local/RawExportSidecarWriter.java', import.meta.url);
const validationUrl = new URL('./src/main/java/com/github/dcysteine/nesql/exporter/local/RawExportValidationSupport.java', import.meta.url);
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
