import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';

const repoRoot = process.cwd();
const readSource = (relativePath) => fs.readFileSync(path.join(repoRoot, relativePath), 'utf8');

const exportValidationAbi = readSource('src/main/java/com/github/dcysteine/nesql/exporter/main/ExportValidationAbiCatalog.java');
const rawValidationAbi = readSource('src/main/java/com/github/dcysteine/nesql/exporter/local/RawExportValidationAbiCatalog.java');
const rawValidationSupport = readSource('src/main/java/com/github/dcysteine/nesql/exporter/local/RawExportValidationSupport.java');

test('export validation ABI constants are projected from fail-closed descriptor catalogs', () => {
  for (const catalog of [
    'SCHEMA_DESCRIPTORS',
    'STATUS_DESCRIPTORS',
    'HEALTH_STATUS_DESCRIPTORS',
    'COMPILE_READINESS_DESCRIPTORS',
    'ERROR_STAGE_DESCRIPTORS',
    'ACTION_DESCRIPTORS',
    'WARNING_DESCRIPTORS',
    'BLOCKED_DESCRIPTORS',
    'PATH_HYGIENE_RULES',
  ]) {
    assert.match(exportValidationAbi, new RegExp(catalog));
  }
  assert.match(exportValidationAbi, /validateStringDescriptors/);
  assert.match(exportValidationAbi, /validateCodeMessageDescriptors/);
  assert.match(exportValidationAbi, /validatePathHygieneRules/);
  assert.match(exportValidationAbi, /Unknown " \+ label \+ " descriptor/);
  assert.match(exportValidationAbi, /Duplicate " \+ label \+ " descriptor/);
  assert.match(exportValidationAbi, /Missing " \+ label \+ " descriptor/);
  assert.match(exportValidationAbi, /descriptor\("exportValidation", ExportSchemaCatalog\.EXPORT_VALIDATION\)/);
  assert.match(exportValidationAbi, /descriptor\("exportError", ExportSchemaCatalog\.EXPORT_ERROR\)/);
  assert.match(exportValidationAbi, /EXPORT_VALIDATION_SCHEMA = descriptorValue\(SCHEMA_DESCRIPTORS, "exportValidation"\)/);
  assert.match(exportValidationAbi, /EXPORT_ERROR_SCHEMA = descriptorValue\(SCHEMA_DESCRIPTORS, "exportError"\)/);
  assert.match(exportValidationAbi, /PATH_HYGIENE_ERROR_CODE = PATH_HYGIENE_ERROR_DESCRIPTOR\.code/);
  assert.match(exportValidationAbi, /PATH_HYGIENE_ERROR_MESSAGE = PATH_HYGIENE_ERROR_DESCRIPTOR\.message/);
  assert.doesNotMatch(exportValidationAbi, /EXPORT_VALIDATION_SCHEMA = ExportSchemaCatalog\.EXPORT_VALIDATION/);
  assert.doesNotMatch(exportValidationAbi, /EXPORT_ERROR_SCHEMA = ExportSchemaCatalog\.EXPORT_ERROR/);
  assert.doesNotMatch(exportValidationAbi, /PATH_HYGIENE_ERROR_CODE = "export-path-hygiene"/);
});

test('raw validation gates are built by a descriptor-owned operation table', () => {
  assert.match(rawValidationAbi, /RAW_GATE_DESCRIPTORS = validateGateDescriptors\(Arrays\.asList\(/);
  assert.match(rawValidationAbi, /private interface GateFactory/);
  assert.match(rawValidationAbi, /static List<RawValidationGate> buildGates\(RawExportCounts counts, List<String> issues\)/);
  assert.match(rawValidationAbi, /for \(RawGateDescriptor descriptor : RAW_GATE_DESCRIPTORS\)/);
  assert.match(rawValidationAbi, /descriptor\.factory\.build\(counts, issues\)/);
  assert.match(rawValidationAbi, /GATE_NATIVE_UI_ABI = gateName\("nativeUiAbi"\)/);
  assert.match(rawValidationAbi, /STATUS_READY = descriptorValue\(STATUS_DESCRIPTORS, "ready"\)/);
  assert.match(rawValidationAbi, /ISSUE_NATIVE_UI_COORDINATE_CONTRACT =\s*descriptorValue\(ISSUE_DESCRIPTORS, "nativeUiCoordinateContract"\)/);
  assert.match(rawValidationAbi, /requireExpectedKey\("raw validation gate", expected, descriptor\.key\)/);
  assert.match(rawValidationAbi, /Duplicate raw validation gate descriptor/);
  assert.match(rawValidationAbi, /requireCompleteCoverage\("raw validation gate", expected, seenKeys\)/);
  assert.match(rawValidationSupport, /RawExportValidationAbiCatalog\.buildGates\(counts, issues\)/);
  assert.doesNotMatch(rawValidationSupport, /buildRawValidationGates/);
  assert.doesNotMatch(rawValidationSupport, /RawExportValidationAbiCatalog\.nativeUiAbiGate\(counts\)/);
  assert.doesNotMatch(rawValidationAbi, /GATE_NATIVE_UI_ABI = "native-ui-abi"/);
});
