import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';

const readSource = (relativePath) => readFileSync(new URL(relativePath, import.meta.url), 'utf8');

const censusWriter = readSource(
  './src/main/java/com/github/dcysteine/nesql/exporter/local/RawExportUiFamilyCensusWriter.java',
);
const templateWriter = readSource(
  './src/main/java/com/github/dcysteine/nesql/exporter/local/RawExportUiTemplateCatalogWriter.java',
);
const nativeUiAbi = readSource(
  './src/main/java/com/github/dcysteine/nesql/exporter/nativeui/NativeUiExportAbi.java',
);

test('captureKey raw UI schemas are explicit v2 contracts', () => {
  assert.match(nativeUiAbi, /UI_FAMILY_CENSUS_SCHEMA = RAW_EXPORT_SCHEMA \+ "\/ui-family-census\/v2"/);
  assert.match(nativeUiAbi, /UI_TEMPLATE_CATALOG_SCHEMA = RAW_EXPORT_SCHEMA \+ "\/ui-template-catalog\/v2"/);
  assert.match(templateWriter, /validateCensusSchemaVersion\(census\.schemaVersion\)/);
  assert.match(
    templateWriter,
    /NativeUiExportAbi\.UI_FAMILY_CENSUS_SCHEMA\.equals\(schemaVersion\)/,
  );
});

test('raw native UI producer names the composite capture fingerprint captureKey end to end', () => {
  assert.match(censusWriter, /String captureKey = buildCaptureKey\(/);
  assert.match(censusWriter, /bucket\.captureKey = captureKey/);
  assert.match(censusWriter, /captures\.put\(captureKey, bucket\)/);
  assert.match(censusWriter, /return left\.captureKey\.compareTo\(right\.captureKey\)/);
  assert.match(censusWriter, /String captureKey;/);
  assert.doesNotMatch(censusWriter, /buildFamilyKey/);
  assert.doesNotMatch(censusWriter, /String familyKey;/);

  assert.match(templateWriter, /template\.captureKey = requireCaptureKey\(family\.captureKey\)/);
  assert.match(templateWriter, /canonical\.append\(template\.captureKey\)\.append\('\\n'\)/);
  assert.match(templateWriter, /String captureKey;/);
  assert.doesNotMatch(templateWriter, /template\.familyKey/);
  assert.doesNotMatch(templateWriter, /family\.familyKey/);
  assert.doesNotMatch(templateWriter, /String familyKey;/);
});

test('template catalog producer rejects missing and blank captureKey without fallback', () => {
  assert.match(templateWriter, /private static String requireCaptureKey\(String value\) throws IOException/);
  assert.match(templateWriter, /value == null \? "" : value\.trim\(\)/);
  assert.match(templateWriter, /if \(captureKey\.isEmpty\(\)\)/);
  assert.match(templateWriter, /throw new IOException\("UI family census entry missing required captureKey"\)/);
  assert.doesNotMatch(templateWriter, /captureKey\s*=\s*family\./);
  assert.doesNotMatch(templateWriter, /familyKey.*captureKey|captureKey.*familyKey/i);
});
