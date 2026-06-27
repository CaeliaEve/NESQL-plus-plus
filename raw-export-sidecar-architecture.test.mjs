import assert from 'node:assert/strict';
import { existsSync, readFileSync } from 'node:fs';
import test from 'node:test';

const sidecarUrl = new URL('./src/main/java/com/github/dcysteine/nesql/exporter/local/RawExportSidecarWriter.java', import.meta.url);
const reportPipelineUrl = new URL('./src/main/java/com/github/dcysteine/nesql/exporter/local/RawExportSidecarReportPipeline.java', import.meta.url);
const validationUrl = new URL('./src/main/java/com/github/dcysteine/nesql/exporter/local/RawExportValidationSupport.java', import.meta.url);
const manifestBuilderUrl = new URL('./src/main/java/com/github/dcysteine/nesql/exporter/local/RawExportManifestBuilder.java', import.meta.url);
const reportWriterUrl = new URL('./src/main/java/com/github/dcysteine/nesql/exporter/local/RawExportReportWriter.java', import.meta.url);
const repositoryFactStreamerUrl = new URL('./src/main/java/com/github/dcysteine/nesql/exporter/local/RawExportRepositoryFactStreamer.java', import.meta.url);
const repositoryFactResultUrl = new URL('./src/main/java/com/github/dcysteine/nesql/exporter/local/RawRepositoryFactStreamResult.java', import.meta.url);
const neiFactWriterUrl = new URL('./src/main/java/com/github/dcysteine/nesql/exporter/local/RawExportNeiFactWriter.java', import.meta.url);
const neiFactCountsUrl = new URL('./src/main/java/com/github/dcysteine/nesql/exporter/local/RawNeiFactCounts.java', import.meta.url);
const neiBrowserContractUrl = new URL('./src/main/java/com/github/dcysteine/nesql/exporter/local/NeiBrowserContract.java', import.meta.url);
const renderAssetCatalogWriterUrl = new URL('./src/main/java/com/github/dcysteine/nesql/exporter/local/RawExportRenderAssetCatalogWriter.java', import.meta.url);
const renderAssetCatalogCountsUrl = new URL('./src/main/java/com/github/dcysteine/nesql/exporter/local/RawRenderAssetCatalogCounts.java', import.meta.url);
const entityModelWriterUrl = new URL('./src/main/java/com/github/dcysteine/nesql/exporter/local/RawExportEntityModelWriter.java', import.meta.url);
const emptyJsonlWriterUrl = new URL('./src/main/java/com/github/dcysteine/nesql/exporter/local/RawExportEmptyJsonlWriter.java', import.meta.url);
const rawFactCountsUrl = new URL('./src/main/java/com/github/dcysteine/nesql/exporter/local/RawFactCounts.java', import.meta.url);
const reportAssemblerUrl = new URL('./src/main/java/com/github/dcysteine/nesql/exporter/local/RawExportReportAssembler.java', import.meta.url);
const semanticRuntimeBuilderUrl = new URL('./src/main/java/com/github/dcysteine/nesql/exporter/local/RawExportSemanticRuntimeBuilder.java', import.meta.url);
const reportFactoryUrl = new URL('./src/main/java/com/github/dcysteine/nesql/exporter/local/RawExportReportFactory.java', import.meta.url);
const sidecarFileOpsUrl = new URL('./src/main/java/com/github/dcysteine/nesql/exporter/local/RawExportSidecarFileOps.java', import.meta.url);
const dtoFiles = [
  'RawExportManifest.java',
  'RawExportReport.java',
  'RawExportCounts.java',
  'RawExportValidation.java',
  'RawValidationGate.java',
  'RawExportFileRef.java',
];

const sidecar = readFileSync(sidecarUrl, 'utf8');
const reportPipeline = readFileSync(reportPipelineUrl, 'utf8');
const validation = readFileSync(validationUrl, 'utf8');
const manifestBuilder = readFileSync(manifestBuilderUrl, 'utf8');
const reportWriter = readFileSync(reportWriterUrl, 'utf8');
const repositoryFactStreamer = readFileSync(repositoryFactStreamerUrl, 'utf8');
const neiFactWriter = readFileSync(neiFactWriterUrl, 'utf8');
const renderAssetCatalogWriter = readFileSync(renderAssetCatalogWriterUrl, 'utf8');
const entityModelWriter = readFileSync(entityModelWriterUrl, 'utf8');
const reportAssembler = readFileSync(reportAssemblerUrl, 'utf8');
const semanticRuntimeBuilder = readFileSync(semanticRuntimeBuilderUrl, 'utf8');
const reportFactory = readFileSync(reportFactoryUrl, 'utf8');
const sidecarFileOps = readFileSync(sidecarFileOpsUrl, 'utf8');

test('raw export validation logic lives outside RawExportSidecarWriter', () => {
  assert.match(reportPipeline, /RawExportValidationSupport\.apply\(report\)/);
  assert.doesNotMatch(sidecar, /RawExportValidationSupport\.apply\(report\)/);
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
  assert.equal(existsSync(reportPipelineUrl), true, 'RawExportSidecarReportPipeline must exist');
  assert.equal(existsSync(manifestBuilderUrl), true, 'RawExportManifestBuilder must exist');
  assert.equal(existsSync(reportWriterUrl), true, 'RawExportReportWriter must exist');
  assert.match(sidecar, /new RawExportSidecarReportPipeline\(/);
  assert.match(sidecar, /\.write\(factCounts\)/);
  assert.match(reportPipeline, /RawExportManifestBuilder\.build\(schemaVersion, generatedAt, exportContext, report\)/);
  assert.match(reportPipeline, /RawExportReportWriter\.write\(schemaVersion, generatedAt, rawDir, manifest, report\)/);
  assert.doesNotMatch(sidecar, /RawExportManifestBuilder\.build/);
  assert.doesNotMatch(sidecar, /RawExportReportWriter\.write/);
  assert.doesNotMatch(sidecar, /utcNow\(/);
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


test('raw NEI browser and handler facts are split from sidecar orchestration', () => {
  assert.equal(existsSync(neiFactWriterUrl), true, 'RawExportNeiFactWriter must exist');
  assert.equal(existsSync(neiFactCountsUrl), true, 'RawNeiFactCounts must exist');
  assert.equal(existsSync(neiBrowserContractUrl), true, 'NeiBrowserContract must exist as a top-level DTO');
  assert.match(sidecar, /new RawExportNeiFactWriter\(repositoryDirectory, rawDir, SCHEMA_VERSION\)\.write\(\)/);
  assert.match(sidecar, /RawNeiFactCounts nei =/);
  assert.doesNotMatch(sidecar, /buildNeiBrowserContract/);
  assert.doesNotMatch(sidecar, /writeGuidFilterRules/);
  assert.doesNotMatch(sidecar, /writeHiddenItemRules/);
  assert.doesNotMatch(sidecar, /writeHandlerMetadata/);
  assert.doesNotMatch(sidecar, /loadBundledHandlerMetadata/);
  assert.doesNotMatch(sidecar, /materializeGtNeiBackgroundAsset/);
  assert.doesNotMatch(sidecar, /class HandlerMetadataCounts/);
  assert.doesNotMatch(sidecar, /class UiFamilyCensusCounts/);
  assert.doesNotMatch(sidecar, /class UiTemplateCatalogCounts/);
  assert.doesNotMatch(sidecar, /class BrowserContractMismatch/);
  assert.match(neiFactWriter, /buildNeiBrowserContract/);
  assert.match(neiFactWriter, /writeGuidFilterRules/);
  assert.match(neiFactWriter, /writeHiddenItemRules/);
  assert.match(neiFactWriter, /writeHandlerMetadata/);
  assert.match(neiFactWriter, /materializeGtNeiBackgroundAsset/);
});


test('raw render asset catalog and entity model facts are split from sidecar orchestration', () => {
  assert.equal(existsSync(renderAssetCatalogWriterUrl), true, 'RawExportRenderAssetCatalogWriter must exist');
  assert.equal(existsSync(renderAssetCatalogCountsUrl), true, 'RawRenderAssetCatalogCounts must exist');
  assert.equal(existsSync(entityModelWriterUrl), true, 'RawExportEntityModelWriter must exist');
  assert.equal(existsSync(emptyJsonlWriterUrl), true, 'RawExportEmptyJsonlWriter must exist');
  assert.match(sidecar, /new RawExportRenderAssetCatalogWriter\(repositoryDirectory, rawDir, renderAssets\)\.write\(\)/);
  assert.match(sidecar, /new RawExportEntityModelWriter\(repositoryDirectory, rawDir, SCHEMA_VERSION\)\.write\(\)/);
  assert.match(sidecar, /RawExportEmptyJsonlWriter\.write\(new File\(rawDir, "models\/multiblocks\/index\.jsonl\.gz"\)\)/);
  assert.doesNotMatch(sidecar, /writeBrowserAtlasIndexAndAssets/);
  assert.doesNotMatch(sidecar, /materializeBrowserAtlasAsset/);
  assert.doesNotMatch(sidecar, /rewriteBrowserAtlasPlacement/);
  assert.doesNotMatch(sidecar, /toRenderAssetRow/);
  assert.doesNotMatch(sidecar, /writeEntityModelIndex/);
  assert.doesNotMatch(sidecar, /entityRow/);
  assert.match(renderAssetCatalogWriter, /writeBrowserAtlasIndexAndAssets/);
  assert.match(renderAssetCatalogWriter, /materializeBrowserAtlasAsset/);
  assert.match(renderAssetCatalogWriter, /toRenderAssetRow/);
  assert.match(entityModelWriter, /long write\(\) throws IOException/);
  assert.match(entityModelWriter, /entityRow/);
});


test('raw export aggregate report mapping is split from sidecar orchestration', () => {
  assert.equal(existsSync(rawFactCountsUrl), true, 'RawFactCounts must exist as a top-level orchestration DTO');
  assert.equal(existsSync(reportAssemblerUrl), true, 'RawExportReportAssembler must exist');
  assert.match(reportPipeline, /RawExportReportAssembler\.apply\(report, factCounts, semanticRuleRuntime, semanticAudit\)/);
  assert.doesNotMatch(sidecar, /RawExportReportAssembler\.apply/);
  assert.doesNotMatch(sidecar, /class RawFactCounts/);
  assert.doesNotMatch(sidecar, /report\.counts\.rawItems\s*=/);
  assert.doesNotMatch(sidecar, /report\.counts\.renderBackendFacts\s*=/);
  assert.doesNotMatch(sidecar, /report\.counts\.semanticTotalItems\s*=/);
  assert.match(reportAssembler, /report\.counts\.rawItems = factCounts\.items/);
  assert.match(reportAssembler, /report\.counts\.renderBackendFacts = factCounts\.renderBackendFacts/);
  assert.match(reportAssembler, /report\.counts\.semanticTotalItems = semanticAudit\.totalItems/);
  assert.match(reportAssembler, /report\.neiBrowserContract = factCounts\.neiBrowserContract/);
});


test('raw export semantic runtime report factory and file ops are split from sidecar', () => {
  assert.equal(existsSync(semanticRuntimeBuilderUrl), true, 'RawExportSemanticRuntimeBuilder must exist');
  assert.equal(existsSync(reportFactoryUrl), true, 'RawExportReportFactory must exist');
  assert.equal(existsSync(sidecarFileOpsUrl), true, 'RawExportSidecarFileOps must exist');
  assert.match(reportPipeline, /new RawExportSemanticRuntimeBuilder\(exportContext\)\.build\(\)/);
  assert.match(reportPipeline, /new RawExportReportFactory\(/);
  assert.doesNotMatch(sidecar, /new RawExportSemanticRuntimeBuilder/);
  assert.doesNotMatch(sidecar, /new RawExportReportFactory/);
  assert.match(sidecar, /RawExportSidecarFileOps\.purgeLegacyRawExportOutputs\(rawDir\)/);
  assert.match(sidecar, /RawExportSidecarFileOps\.copyIfPresent\(/);
  assert.doesNotMatch(sidecar, /buildSemanticRuleRuntimeMetadata/);
  assert.doesNotMatch(sidecar, /safeMinecraftVersion/);
  assert.doesNotMatch(sidecar, /safeForgeVersion/);
  assert.doesNotMatch(sidecar, /safeModVersion/);
  assert.doesNotMatch(sidecar, /semanticFingerprint\(/);
  assert.doesNotMatch(sidecar, /RawExportReport buildReport/);
  assert.doesNotMatch(sidecar, /countQuery/);
  assert.doesNotMatch(sidecar, /countFiles/);
  assert.doesNotMatch(sidecar, /deleteIfExists/);
  assert.match(semanticRuntimeBuilder, /SemanticRulePack\.RuntimeMetadata/);
  assert.match(reportFactory, /RawExportReport build\(\)/);
  assert.match(sidecarFileOps, /purgeLegacyRawExportOutputs/);
});
