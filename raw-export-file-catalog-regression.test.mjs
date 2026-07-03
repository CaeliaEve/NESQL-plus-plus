import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';

const repoRoot = process.cwd();
const readSource = (relativePath) => fs.readFileSync(path.join(repoRoot, relativePath), 'utf8');
const rawFileCatalog = readSource(
  'src/main/java/com/github/dcysteine/nesql/exporter/local/RawExportFileCatalog.java',
);

test('raw-export manifest file ABI is owned by a validated descriptor catalog', () => {
  assert.match(rawFileCatalog, /MANIFEST_FILE_CATALOG = ManifestFileCatalog\.validateAndFreeze\(/);
  assert.match(rawFileCatalog, /private static final class ManifestFileCatalog/);
  assert.match(rawFileCatalog, /EXPECTED_OPTIONAL_MANIFEST_KEYS/);
  assert.match(rawFileCatalog, /Raw-export manifest " \+ group \+ " file catalog must not be empty/);
  assert.match(rawFileCatalog, /Raw-export manifest file descriptor must not be null/);
  assert.match(rawFileCatalog, /requireNonEmpty\("Raw-export manifest key", descriptor\.manifestKey, group\)/);
  assert.match(rawFileCatalog, /requireNonEmpty\("Raw-export manifest path", descriptor\.relativePath, descriptor\.manifestKey\)/);
  assert.match(rawFileCatalog, /must be a runtime-relative raw-export path/);
  assert.match(rawFileCatalog, /Duplicate raw-export manifest key/);
  assert.match(rawFileCatalog, /Duplicate raw-export manifest path/);
  assert.match(rawFileCatalog, /Missing raw-export optional manifest file descriptor/);
  assert.match(rawFileCatalog, /"Unknown " \+ label \+ " descriptor: " \+ actualKey/);
  assert.match(rawFileCatalog, /putOptionalManifestFile\(files, "uiFamilyCensus", includeUiFamilyCensus\)/);
  assert.match(rawFileCatalog, /putOptionalManifestFile\(files, "uiTemplateCatalog", includeUiTemplateCatalog\)/);
  assert.match(rawFileCatalog, /new ManifestFile\("neiHandlers", NativeUiExportAbi\.NEI_HANDLERS_FILE\)/);
  assert.doesNotMatch(rawFileCatalog, /files\.put\("uiFamilyCensus", NativeUiExportAbi\.UI_FAMILY_CENSUS_FILE\)/);
  assert.doesNotMatch(rawFileCatalog, /files\.put\("uiTemplateCatalog", NativeUiExportAbi\.UI_TEMPLATE_CATALOG_FILE\)/);
  assert.doesNotMatch(rawFileCatalog, /new ManifestFile\("neiHandlers", "facts\/nei\/handlers\.jsonl\.gz"\)/);
});

test('raw-export manifest capabilities are descriptor-table validated and appended by lookup', () => {
  assert.match(rawFileCatalog, /CAPABILITY_CATALOG = CapabilityCatalog\.validateAndFreeze\(/);
  assert.match(rawFileCatalog, /private static final class CapabilityCatalog/);
  assert.match(rawFileCatalog, /private static final class CapabilityDescriptor/);
  assert.match(rawFileCatalog, /EXPECTED_OPTIONAL_CAPABILITIES/);
  assert.match(rawFileCatalog, /Raw-export capability " \+ group \+ " catalog must not be empty/);
  assert.match(rawFileCatalog, /Raw-export capability descriptor must not be null/);
  assert.match(rawFileCatalog, /requireNonEmpty\("Raw-export capability", descriptor\.capability, group\)/);
  assert.match(rawFileCatalog, /Duplicate raw-export capability descriptor/);
  assert.match(rawFileCatalog, /Missing raw-export optional capability descriptor/);
  assert.match(rawFileCatalog, /"Unknown " \+ label \+ " descriptor: " \+ actualKey/);
  assert.match(rawFileCatalog, /addOptionalCapability\(capabilities, CAPABILITY_UI_FAMILY_CENSUS, includeUiFamilyCensus\)/);
  assert.match(rawFileCatalog, /addOptionalCapability\(capabilities, CAPABILITY_UI_TEMPLATE_CATALOG, includeUiTemplateCatalog\)/);
  assert.match(rawFileCatalog, /addCapabilities\(capabilities, CAPABILITIES_AFTER_OPTIONAL\)/);
  assert.doesNotMatch(rawFileCatalog, /new ArrayList<String>\(BASE_CAPABILITIES_BEFORE_OPTIONAL\)/);
  assert.doesNotMatch(rawFileCatalog, /capabilities\.add\(CAPABILITY_UI_FAMILY_CENSUS\)/);
});

test('raw-export catalog notes and prohibited root outputs fail closed', () => {
  assert.match(rawFileCatalog, /MANIFEST_NOTES =\s*\r?\n\s*validateStringCatalog\(/);
  assert.match(rawFileCatalog, /PROHIBITED_ROOT_OUTPUTS =\s*\r?\n\s*validatePathCatalog\(/);
  assert.match(rawFileCatalog, /label \+ " catalog must not be empty"/);
  assert.match(rawFileCatalog, /label \+ " descriptor must not be null"/);
  assert.match(rawFileCatalog, /"Duplicate " \+ label \+ " descriptor: " \+ value/);
  assert.match(rawFileCatalog, /validatePathCatalog\(/);
  assert.match(rawFileCatalog, /requireRuntimeRelativePath\(label, value, value\)/);
  assert.match(rawFileCatalog, /Duplicate " \+ label \+ " descriptor/);
});
