import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';

const repoRoot = process.cwd();
const readSource = (relativePath) => fs.readFileSync(path.join(repoRoot, relativePath), 'utf8');
const validationMain = 'src/main/java/com/github/dcysteine/nesql/exporter/main';

const evidenceCatalog = readSource(`${validationMain}/ExportValidationEvidenceCatalog.java`);
const rawCountProbe = readSource(`${validationMain}/ExportValidationRawCountProbe.java`);
const semanticProbe = readSource(`${validationMain}/ExportValidationSemanticProbe.java`);
const browserAtlasProbe = readSource(`${validationMain}/ExportValidationBrowserAtlasProbe.java`);
const renderAssetProbe = readSource(`${validationMain}/ExportValidationRenderAssetProbe.java`);
const healthSectionBuilder = readSource(`${validationMain}/ExportValidationHealthSectionBuilder.java`);

test('validation evidence JSON vocabulary is projected from descriptor catalogs', () => {
  for (const catalog of [
    'OBJECT_DESCRIPTORS',
    'MEMBER_DESCRIPTORS',
    'ERROR_FIELD_DESCRIPTORS',
    'PATH_TOKEN_DESCRIPTORS',
    'SINGULARITY_TOKEN_DESCRIPTORS',
    'ANIMATION_TOKEN_DESCRIPTORS',
    'RawCount',
    'SemanticDiagnostics',
    'BrowserAtlas',
    'RenderAsset',
    'RecipeAnomaly',
  ]) {
    assert.match(evidenceCatalog, new RegExp(catalog));
  }
  assert.match(evidenceCatalog, /validateFieldDescriptors/);
  assert.match(evidenceCatalog, /Unknown " \+ label \+ " descriptor/);
  assert.match(evidenceCatalog, /Duplicate " \+ label \+ " descriptor/);
  assert.match(evidenceCatalog, /Duplicate " \+ label \+ " descriptor value/);
  assert.match(evidenceCatalog, /Missing " \+ label \+ " descriptor/);
  assert.match(evidenceCatalog, /OBJECT_COUNTS = descriptorValue\(OBJECT_DESCRIPTORS, "counts"\)/);
  assert.match(evidenceCatalog, /MEMBER_ITEM_ID = descriptorValue\(MEMBER_DESCRIPTORS, "itemId"\)/);
  assert.match(
    evidenceCatalog,
    /ERROR_FIELD_SCHEMA_VERSION = descriptorValue\(ERROR_FIELD_DESCRIPTORS, "schemaVersion"\)/,
  );
  assert.match(
    evidenceCatalog,
    /IMAGE_DIRECTORY_PREFIX = descriptorValue\(PATH_TOKEN_DESCRIPTORS, "imageDirectoryPrefix"\)/,
  );
  assert.match(
    evidenceCatalog,
    /SINGULARITY_TOKEN_UNIVERSIUM =\s*descriptorValue\(SINGULARITY_TOKEN_DESCRIPTORS, "universium"\)/,
  );
  assert.match(
    evidenceCatalog,
    /ANIMATION_TOKEN_TIMELINE = descriptorValue\(ANIMATION_TOKEN_DESCRIPTORS, "timeline"\)/,
  );
  assert.doesNotMatch(evidenceCatalog, /OBJECT_COUNTS = "counts"/);
  assert.doesNotMatch(evidenceCatalog, /ERROR_FIELD_SCHEMA_VERSION = "schemaVersion"/);
});

test('validation evidence consumers continue to depend on catalog constants only', () => {
  for (const source of [rawCountProbe, semanticProbe, browserAtlasProbe, renderAssetProbe, healthSectionBuilder]) {
    assert.match(source, /ExportValidationEvidenceCatalog\./);
  }
  assert.doesNotMatch(rawCountProbe, /"rawItems"/);
  assert.doesNotMatch(semanticProbe, /"topUnclassifiedFamilyActions"/);
  assert.doesNotMatch(browserAtlasProbe, /"itemCount"/);
  assert.doesNotMatch(renderAssetProbe, /"primaryArtifact"/);
  assert.doesNotMatch(healthSectionBuilder, /"handlersWithLoadedRecipes"/);
});
