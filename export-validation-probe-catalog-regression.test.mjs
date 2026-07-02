import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';

const repoRoot = process.cwd();
const readSource = (relativePath) => fs.readFileSync(path.join(repoRoot, relativePath), 'utf8');
const validationMain = 'src/main/java/com/github/dcysteine/nesql/exporter/main';

const probeCatalog = readSource(`${validationMain}/ExportValidationProbeCatalog.java`);
const reportWriter = readSource(`${validationMain}/ExportValidationReportWriter.java`);
const controlPlaneWriter = readSource(`${validationMain}/ExportControlPlaneWriter.java`);

test('validation probe catalog uses a descriptor and factory table with explicit order', () => {
  assert.match(probeCatalog, /DEFAULT_PROBE_DESCRIPTORS =\s*validateProbeDescriptors\(Arrays\.asList\(/);
  assert.match(probeCatalog, /private interface ProbeFactory/);
  assert.match(probeCatalog, /private static final class ProbeCatalogDescriptor/);
  assert.match(probeCatalog, /private static List<ExportValidationProbe> instantiateAndFreeze/);
  assert.match(probeCatalog, /descriptor\.factory\.build\(\)/);
  assert.match(probeCatalog, /Export validation probe id does not match descriptor/);
  assert.match(probeCatalog, /Unknown export validation probe descriptor/);
  assert.match(probeCatalog, /Duplicate export validation probe descriptor/);
  assert.match(probeCatalog, /Missing export validation probe descriptor/);
  assert.match(probeCatalog, /Duplicate export validation probe capability/);
  const expectedOrder = [
    'validation.repository-summary',
    'validation.raw-counts',
    'validation.browser-layout',
    'validation.render-assets',
    'validation.semantic',
    'validation.path-hygiene',
    'validation.derived-metrics',
    'validation.previous-delta',
    'validation.health-policy',
    'validation.health-sections',
  ];
  let cursor = -1;
  for (const id of expectedOrder) {
    const index = probeCatalog.indexOf(`probeDescriptor(\n                            "${id}"`, cursor + 1);
    assert.notEqual(index, -1, `missing probe descriptor ${id}`);
    assert.equal(index > cursor, true, `probe descriptor order drifted: ${id}`);
    cursor = index;
  }
});

test('validation probe consumers still dispatch through the catalog', () => {
  assert.match(reportWriter, /for \(ExportValidationProbe probe : ExportValidationProbeCatalog\.defaultProbes\(\)\)/);
  assert.match(reportWriter, /report\.validationProbes = ExportValidationProbeCatalog\.descriptors\(\)/);
  assert.match(controlPlaneWriter, /ExportValidationProbeCatalog\.descriptors\(\)/);
  assert.doesNotMatch(reportWriter, /new ExportValidationRepositoryProbe\(\)/);
  assert.doesNotMatch(controlPlaneWriter, /new ExportValidationRepositoryProbe\(\)/);
});
