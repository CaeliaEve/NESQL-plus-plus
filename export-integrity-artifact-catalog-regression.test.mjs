import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';

const repoRoot = process.cwd();
const readSource = (relativePath) => fs.readFileSync(path.join(repoRoot, relativePath), 'utf8');
const integrity = readSource(
  'src/main/java/com/github/dcysteine/nesql/exporter/main/ExportIntegrityManifestWriter.java',
);
const integrityOutputCatalog = readSource(
  'src/main/java/com/github/dcysteine/nesql/exporter/main/ExportIntegrityOutputFileCatalog.java',
);

test('export integrity artifact checksums are emitted from a validated descriptor catalog', () => {
  assert.match(integrity, /static void write\(ExportContext exportContext\) throws Exception/);
  assert.match(integrity, /ARTIFACT_CATALOG =\s*\r?\n\s*IntegrityArtifactCatalog\.validateAndFreeze\(/);
  assert.match(integrity, /private static final class IntegrityArtifactCatalog/);
  assert.match(integrity, /private static final class FileArtifactDescriptor/);
  assert.match(integrity, /private static final class DirectoryArtifactDescriptor/);
  assert.match(integrity, /private enum ArtifactRoot/);
  assert.match(integrity, /FILE_ARTIFACTS_BEFORE_DYNAMIC =\s*\r?\n\s*ARTIFACT_CATALOG\.beforeDynamicFiles\(\)/);
  assert.match(integrity, /FILE_ARTIFACTS_AFTER_DYNAMIC =\s*\r?\n\s*ARTIFACT_CATALOG\.afterDynamicFiles\(\)/);
  assert.match(integrity, /DIRECTORY_ARTIFACTS =\s*\r?\n\s*ARTIFACT_CATALOG\.directories\(\)/);
  assert.match(integrity, /addFileArtifacts\(artifacts, repositoryDirectory, rawDir, FILE_ARTIFACTS_BEFORE_DYNAMIC\)/);
  assert.match(integrity, /addFileArtifacts\(artifacts, repositoryDirectory, rawDir, FILE_ARTIFACTS_AFTER_DYNAMIC\)/);
  assert.match(integrity, /addDirectoryArtifacts\(artifacts, repositoryDirectory, rawDir\)/);

  const beforeIndex = integrity.indexOf('addFileArtifacts(artifacts, repositoryDirectory, rawDir, FILE_ARTIFACTS_BEFORE_DYNAMIC)');
  const controlIndex = integrity.indexOf('for (ExportControlFile file : ExportControlFile.values())');
  const debugIndex = integrity.indexOf('for (ExportDebugFile file : ExportDebugFile.values())');
  const afterIndex = integrity.indexOf('addFileArtifacts(artifacts, repositoryDirectory, rawDir, FILE_ARTIFACTS_AFTER_DYNAMIC)');
  assert.equal(beforeIndex < controlIndex && controlIndex < debugIndex && debugIndex < afterIndex, true);
});

test('export integrity manifest and checksum persistence fails closed', () => {
  assert.match(integrity, /ExportIntegrityOutputFileCatalog\.ensureDirectory\(validationDir\)/);
  assert.match(integrity, /ExportIntegrityOutputFileCatalog\.checksumFile\(validationDir\)/);
  assert.match(integrity, /ExportIntegrityOutputFileCatalog\.outputFiles\(validationDir, manifest, checksumReport\)/);
  assert.match(integrityOutputCatalog, /OUTPUTS = validateAndFreeze\(Arrays\.asList\(/);
  assert.match(integrityOutputCatalog, /RawExportFileCatalog\.EXPORT_MANIFEST_FILE_NAME/);
  assert.match(integrityOutputCatalog, /RawExportFileCatalog\.EXPORT_MANIFEST_DASH_FILE_NAME/);
  assert.match(integrityOutputCatalog, /RawExportFileCatalog\.STAGE_CHECKSUMS_FILE_NAME/);
  assert.match(integrityOutputCatalog, /RawExportFileCatalog\.STAGE_CHECKSUMS_DASH_FILE_NAME/);
  assert.match(integrityOutputCatalog, /Export integrity output directory must not be null/);
  assert.match(integrityOutputCatalog, /Export integrity output path exists but is not a directory/);
  assert.match(integrityOutputCatalog, /Failed to create export integrity output directory/);
  assert.match(integrityOutputCatalog, /Export integrity output file catalog must not be empty/);
  assert.match(integrity, /readPreviousArtifacts\(File checksumFile\) throws Exception/);
  assert.match(integrity, /Export integrity checksum file must not be null/);
  assert.match(integrity, /Export integrity checksum path exists but is not a file/);
  assert.match(integrity, /Export integrity checksum report is empty or null JSON/);
  assert.match(integrity, /Export integrity checksum report artifacts must not be null/);
  assert.match(integrity, /sha256\(File file\) throws Exception/);
  assert.match(integrity, /collectDirectoryStats\(File root, File file, DirectoryStats stats\) throws Exception/);
  assert.match(integrity, /Failed to list export integrity artifact directory/);
  assert.doesNotMatch(integrity, /new File\(validationDir, RawExportFileCatalog\.EXPORT_MANIFEST_FILE_NAME\)/);
  assert.doesNotMatch(integrity, /new File\(validationDir, RawExportFileCatalog\.STAGE_CHECKSUMS_DASH_FILE_NAME\)/);
  assert.doesNotMatch(integrity, /Failed to write NESQL\+\+ export manifest\/checksums/);
  assert.doesNotMatch(integrity, /Failed to read previous NESQL\+\+ stage checksums/);
  assert.doesNotMatch(integrity, /Failed to hash export artifact/);
});

test('export integrity artifact catalog fails closed on malformed descriptors', () => {
  assert.match(integrity, /Export integrity " \+ group \+ " artifact catalog must not be empty/);
  assert.match(integrity, /Export integrity file artifact descriptor must not be null/);
  assert.match(integrity, /Export integrity directory artifact descriptor must not be null/);
  assert.match(integrity, /Export integrity artifact stage must be non-empty/);
  assert.match(integrity, /Export integrity artifact stage must be kebab-case/);
  assert.match(integrity, /Export integrity file artifact path/);
  assert.match(integrity, /Export integrity directory artifact path/);
  assert.match(integrity, /must be a runtime-relative artifact path/);
  assert.match(integrity, /Export integrity directory artifact root must be non-null/);
  assert.match(integrity, /Duplicate export integrity artifact path/);
  assert.match(integrity, /Duplicate export integrity artifact descriptor/);
});

test('export integrity catalog consumes raw-export ABI constants instead of inline artifact paths', () => {
  for (const constant of [
    'RawExportFileCatalog.RECIPE_INDEX_FILE',
    'RawExportFileCatalog.BROWSER_ATLAS_INDEX_FILE',
    'RawExportFileCatalog.RENDER_BACKEND_FILE',
    'RawExportFileCatalog.SPECIAL_INDEX_FILE',
    'RawExportFileCatalog.NATIVE_UI_VALIDATION_FILE',
    'ExportDebugFile.STAGE_TIMING.validationAliasPath()',
  ]) {
    assert.equal(integrity.includes(constant), true, `missing descriptor-owned constant ${constant}`);
  }
  assert.doesNotMatch(integrity, /RawExportFileCatalog\.rawExportFile\(rawDir, "facts\/recipes\/index\.json"\)/);
  assert.doesNotMatch(
    integrity,
    /RawExportFileCatalog\.rawExportFile\(rawDir, "assets\/textures\/browser_atlas_index\.json"\)/,
  );
  assert.doesNotMatch(integrity, /RawExportFileCatalog\.rawExportFile\(rawDir, "facts\/render\/backend\.json"\)/);
  assert.doesNotMatch(integrity, /RawExportFileCatalog\.rawExportFile\(rawDir, "special\/index\.json"\)/);
  assert.doesNotMatch(integrity, /addDirectorySummary\(artifacts, repositoryDirectory, new File\(rawDir, "facts"\)/);
  assert.match(
    integrity,
    /new DirectoryArtifactDescriptor\(\s*"raw-control",\s*ArtifactRoot\.RAW_EXPORT,\s*RawExportFileCatalog\.CONTROL_DIRECTORY\)/,
  );
  assert.match(
    integrity,
    /new DirectoryArtifactDescriptor\(\s*"raw-debug",\s*ArtifactRoot\.RAW_EXPORT,\s*RawExportFileCatalog\.DEBUG_DIRECTORY\)/,
  );
});
