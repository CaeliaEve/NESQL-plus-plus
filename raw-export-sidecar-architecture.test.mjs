import assert from 'node:assert/strict';
import { existsSync, readFileSync } from 'node:fs';
import test from 'node:test';

const sidecarUrl = new URL('./src/main/java/com/github/dcysteine/nesql/exporter/local/RawExportSidecarWriter.java', import.meta.url);
const factStreamPipelineUrl = new URL('./src/main/java/com/github/dcysteine/nesql/exporter/local/RawExportFactStreamPipeline.java', import.meta.url);
const factStreamContextUrl = new URL('./src/main/java/com/github/dcysteine/nesql/exporter/local/RawExportFactStreamContext.java', import.meta.url);
const factStreamProviderUrl = new URL('./src/main/java/com/github/dcysteine/nesql/exporter/local/RawExportFactStreamProvider.java', import.meta.url);
const factStreamRegistryUrl = new URL('./src/main/java/com/github/dcysteine/nesql/exporter/local/RawExportFactStreamRegistry.java', import.meta.url);
const factStreamDescriptorUrl = new URL('./src/main/java/com/github/dcysteine/nesql/exporter/local/RawExportFactStreamDescriptor.java', import.meta.url);
const factStreamDescriptorWriterUrl = new URL('./src/main/java/com/github/dcysteine/nesql/exporter/local/RawExportFactStreamDescriptorWriter.java', import.meta.url);
const repositoryProviderUrl = new URL('./src/main/java/com/github/dcysteine/nesql/exporter/local/RawRepositoryFactStreamProvider.java', import.meta.url);
const neiProviderUrl = new URL('./src/main/java/com/github/dcysteine/nesql/exporter/local/RawNeiFactStreamProvider.java', import.meta.url);
const renderAssetProviderUrl = new URL('./src/main/java/com/github/dcysteine/nesql/exporter/local/RawRenderAssetFactStreamProvider.java', import.meta.url);
const entityRenderProviderUrl = new URL('./src/main/java/com/github/dcysteine/nesql/exporter/local/RawEntityAndRenderBackendFactStreamProvider.java', import.meta.url);
const reportPipelineUrl = new URL('./src/main/java/com/github/dcysteine/nesql/exporter/local/RawExportSidecarReportPipeline.java', import.meta.url);
const validationUrl = new URL('./src/main/java/com/github/dcysteine/nesql/exporter/local/RawExportValidationSupport.java', import.meta.url);
const validationCatalogUrl = new URL('./src/main/java/com/github/dcysteine/nesql/exporter/local/RawExportValidationAbiCatalog.java', import.meta.url);
const manifestBuilderUrl = new URL('./src/main/java/com/github/dcysteine/nesql/exporter/local/RawExportManifestBuilder.java', import.meta.url);
const reportWriterUrl = new URL('./src/main/java/com/github/dcysteine/nesql/exporter/local/RawExportReportWriter.java', import.meta.url);
const rawFileCatalogUrl = new URL('./src/main/java/com/github/dcysteine/nesql/exporter/local/RawExportFileCatalog.java', import.meta.url);
const repositoryFactStreamerUrl = new URL('./src/main/java/com/github/dcysteine/nesql/exporter/local/RawExportRepositoryFactStreamer.java', import.meta.url);
const repositoryFactResultUrl = new URL('./src/main/java/com/github/dcysteine/nesql/exporter/local/RawRepositoryFactStreamResult.java', import.meta.url);
const neiFactWriterUrl = new URL('./src/main/java/com/github/dcysteine/nesql/exporter/local/RawExportNeiFactWriter.java', import.meta.url);
const nativeUiAbiUrl = new URL('./src/main/java/com/github/dcysteine/nesql/exporter/nativeui/NativeUiExportAbi.java', import.meta.url);
const nativeUiValidatorUrl = new URL('./src/main/java/com/github/dcysteine/nesql/exporter/nativeui/NativeUiExportValidator.java', import.meta.url);
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
const exportWriterSupportUrl = new URL('./src/main/java/com/github/dcysteine/nesql/exporter/main/ExportWriterSupport.java', import.meta.url);
const dtoFiles = [
  'RawExportManifest.java',
  'RawExportReport.java',
  'RawExportCounts.java',
  'RawExportValidation.java',
  'RawValidationGate.java',
];

const sidecar = readFileSync(sidecarUrl, 'utf8');
const factStreamPipeline = readFileSync(factStreamPipelineUrl, 'utf8');
const factStreamContext = readFileSync(factStreamContextUrl, 'utf8');
const factStreamProvider = readFileSync(factStreamProviderUrl, 'utf8');
const factStreamRegistry = readFileSync(factStreamRegistryUrl, 'utf8');
const factStreamDescriptor = readFileSync(factStreamDescriptorUrl, 'utf8');
const factStreamDescriptorWriter = readFileSync(factStreamDescriptorWriterUrl, 'utf8');
const repositoryProvider = readFileSync(repositoryProviderUrl, 'utf8');
const neiProvider = readFileSync(neiProviderUrl, 'utf8');
const renderAssetProvider = readFileSync(renderAssetProviderUrl, 'utf8');
const entityRenderProvider = readFileSync(entityRenderProviderUrl, 'utf8');
const reportPipeline = readFileSync(reportPipelineUrl, 'utf8');
const validation = readFileSync(validationUrl, 'utf8');
const validationCatalog = readFileSync(validationCatalogUrl, 'utf8');
const manifestBuilder = readFileSync(manifestBuilderUrl, 'utf8');
const reportWriter = readFileSync(reportWriterUrl, 'utf8');
const rawFileCatalog = readFileSync(rawFileCatalogUrl, 'utf8');
const repositoryFactStreamer = readFileSync(repositoryFactStreamerUrl, 'utf8');
const neiFactWriter = readFileSync(neiFactWriterUrl, 'utf8');
const nativeUiAbi = readFileSync(nativeUiAbiUrl, 'utf8');
const nativeUiValidator = readFileSync(nativeUiValidatorUrl, 'utf8');
const renderAssetCatalogWriter = readFileSync(renderAssetCatalogWriterUrl, 'utf8');
const entityModelWriter = readFileSync(entityModelWriterUrl, 'utf8');
const reportAssembler = readFileSync(reportAssemblerUrl, 'utf8');
const semanticRuntimeBuilder = readFileSync(semanticRuntimeBuilderUrl, 'utf8');
const reportFactory = readFileSync(reportFactoryUrl, 'utf8');
const sidecarFileOps = readFileSync(sidecarFileOpsUrl, 'utf8');
const exportWriterSupport = readFileSync(exportWriterSupportUrl, 'utf8');

test('raw export validation logic lives outside RawExportSidecarWriter', () => {
  assert.match(reportPipeline, /RawExportValidationSupport\.apply\(report\)/);
  assert.doesNotMatch(sidecar, /RawExportValidationSupport\.apply\(report\)/);
  assert.doesNotMatch(sidecar, /buildRawValidationGates/);
  assert.doesNotMatch(sidecar, /rawValidationReady/);
  assert.doesNotMatch(sidecar, /addCountMismatch/);
  assert.match(validation, /RawExportValidationAbiCatalog\.buildGates\(counts, issues\)/);
  assert.match(validation, /rawValidationReady/);
  assert.doesNotMatch(validation, /buildRawValidationGates/);
});

test('raw export validation ABI vocabulary is catalog-owned', () => {
  assert.equal(existsSync(validationCatalogUrl), true, 'RawExportValidationAbiCatalog must exist');
  assert.match(validationCatalog, /STATUS_DESCRIPTORS = validateStringDescriptors\(/);
  assert.match(validationCatalog, /ISSUE_DESCRIPTORS = validateStringDescriptors\(/);
  assert.match(validationCatalog, /RAW_GATE_DESCRIPTORS = validateGateDescriptors\(Arrays\.asList\(/);
  for (const token of [
    'descriptor("ok", "ok")',
    'descriptor("warning", "warning")',
    'descriptor("ready", "ready")',
    'descriptor("blocked", "blocked")',
    'gateDescriptor(',
    '"core-counts"',
    '"browser-order"',
    '"nei-browser-contract"',
    '"semantic-identity"',
    '"native-ui-abi"',
    '"angelica-render-facts"',
    'descriptor("missingRenderCapture", "missing-render-capture")',
    'descriptor("renderCaptureWithoutFrames", "render-capture-without-frames")',
    'descriptor("nativeUiMissingSurfaces", "native-ui-missing-surfaces")',
    'descriptor("nativeUiSlotBounds", "native-ui-slot-bounds")',
    'descriptor("nativeUiCoordinateContract", "native-ui-coordinate-contract")',
  ]) {
    assert.equal(validationCatalog.includes(token), true, `validation catalog missing ${token}`);
  }
  assert.match(validationCatalog, /STATUS_OK = descriptorValue\(STATUS_DESCRIPTORS, "ok"\)/);
  assert.match(validationCatalog, /GATE_NATIVE_UI_ABI = gateName\("nativeUiAbi"\)/);
  assert.match(
    validationCatalog,
    /ISSUE_NATIVE_UI_COORDINATE_CONTRACT =\s*\r?\n?\s*descriptorValue\(ISSUE_DESCRIPTORS, "nativeUiCoordinateContract"\)/,
  );
  assert.match(validationCatalog, /addCountMismatch/);
  assert.match(validationCatalog, /private static RawValidationGate nativeUiAbiGate\(RawExportCounts counts\)/);
  assert.match(validationCatalog, /static List<RawValidationGate> buildGates\(RawExportCounts counts, List<String> issues\)/);
  assert.match(validationCatalog, /allGatesReady\(RawExportValidation validation\)/);
  assert.match(validation, /RawExportValidationAbiCatalog\.addCountMismatch/);
  assert.match(validation, /RawExportValidationAbiCatalog\.addNativeUiIssues\(issues, counts\)/);
  assert.match(validation, /RawExportValidationAbiCatalog\.buildGates\(counts, issues\)/);
  assert.match(validation, /RawExportValidationAbiCatalog\.allGatesReady\(validation\)/);
  assert.doesNotMatch(validation, /RawExportValidationAbiCatalog\.nativeUiAbiGate\(counts\)/);
  assert.doesNotMatch(validation, /"native-ui-abi"/);
  assert.doesNotMatch(validation, /"semantic-identity"/);
  assert.doesNotMatch(validation, /"angelica-render-facts"/);
  assert.doesNotMatch(validation, /"ready"/);
  assert.doesNotMatch(validation, /"blocked"/);
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
  assert.match(manifestBuilder, /RawExportFileCatalog\.manifestCapabilities/);
  assert.match(manifestBuilder, /RawExportFileCatalog\.putManifestFiles/);
  assert.match(rawFileCatalog, /CAPABILITY_FACTS = "facts"/);
  assert.match(rawFileCatalog, /new ManifestFile\("exportReport", VALIDATION_EXPORT_REPORT_FILE\)/);
  assert.match(reportWriter, /RawExportFileCatalog\.VALIDATION_EXPORT_REPORT_FILE/);
  assert.match(reportWriter, /writeSizeReport\(gson, schemaVersion, generatedAt, rawDir\)/);
});


test('raw repository fact streaming is split from sidecar orchestration', () => {
  assert.equal(existsSync(factStreamPipelineUrl), true, 'RawExportFactStreamPipeline must exist');
  assert.equal(existsSync(factStreamContextUrl), true, 'RawExportFactStreamContext must exist');
  assert.equal(existsSync(factStreamProviderUrl), true, 'RawExportFactStreamProvider must exist');
  assert.equal(existsSync(factStreamRegistryUrl), true, 'RawExportFactStreamRegistry must exist');
  assert.equal(existsSync(factStreamDescriptorUrl), true, 'RawExportFactStreamDescriptor must exist');
  assert.equal(existsSync(factStreamDescriptorWriterUrl), true, 'RawExportFactStreamDescriptorWriter must exist');
  assert.equal(existsSync(repositoryProviderUrl), true, 'RawRepositoryFactStreamProvider must exist');
  assert.equal(existsSync(repositoryFactStreamerUrl), true, 'RawExportRepositoryFactStreamer must exist');
  assert.equal(existsSync(repositoryFactResultUrl), true, 'RawRepositoryFactStreamResult must exist');
  assert.match(sidecar, /new RawExportFactStreamPipeline\(/);
  assert.match(sidecar, /\.write\(\)/);
  assert.match(factStreamPipeline, /RawExportFactStreamRegistry\.defaultProviders\(\)/);
  assert.match(factStreamPipeline, /for \(RawExportFactStreamProvider provider : providers\)/);
  assert.match(factStreamProvider, /interface RawExportFactStreamProvider/);
  assert.match(factStreamProvider, /String id\(\)/);
  assert.match(factStreamProvider, /List<String> capabilities\(\)/);
  assert.match(factStreamProvider, /List<String> outputFamilies\(\)/);
  assert.match(factStreamProvider, /void write\(RawExportFactStreamContext context, RawFactCounts counts\) throws IOException/);
  assert.match(factStreamProvider, /static List<String> list\(String\.\.\. values\)/);
  assert.match(factStreamContext, /final EntityManager entityManager/);
  assert.match(factStreamContext, /final File repositoryDirectory/);
  assert.match(factStreamContext, /final File rawDir/);
  assert.match(factStreamContext, /final String schemaVersion/);
  assert.match(repositoryProvider, /new RawExportRepositoryFactStreamer\(context\.entityManager, context\.rawDir, context\.schemaVersion\)\.write\(\)/);
  assert.match(repositoryProvider, /RawRepositoryFactStreamResult repository =/);
  assert.doesNotMatch(factStreamPipeline, /new RawExportRepositoryFactStreamer/);
  assert.doesNotMatch(sidecar, /new RawExportRepositoryFactStreamer/);
  assert.doesNotMatch(sidecar, /RawRepositoryFactStreamResult repository/);
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
  assert.equal(existsSync(neiProviderUrl), true, 'RawNeiFactStreamProvider must exist');
  assert.equal(existsSync(neiFactWriterUrl), true, 'RawExportNeiFactWriter must exist');
  assert.equal(existsSync(neiFactCountsUrl), true, 'RawNeiFactCounts must exist');
  assert.equal(existsSync(neiBrowserContractUrl), true, 'NeiBrowserContract must exist as a top-level DTO');
  assert.match(neiProvider, /new RawExportNeiFactWriter\(context\.repositoryDirectory, context\.rawDir, context\.schemaVersion\)\.write\(\)/);
  assert.match(neiProvider, /RawNeiFactCounts nei =/);
  assert.doesNotMatch(factStreamPipeline, /new RawExportNeiFactWriter/);
  assert.doesNotMatch(sidecar, /new RawExportNeiFactWriter/);
  assert.doesNotMatch(sidecar, /RawNeiFactCounts nei =/);
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

test('native UI ABI validator owns bounds and missing-surface validation', () => {
  assert.equal(existsSync(nativeUiAbiUrl), true, 'NativeUiExportAbi must exist as a public ABI catalog');
  assert.equal(existsSync(nativeUiValidatorUrl), true, 'NativeUiExportValidator must exist');
  assert.match(nativeUiAbi, /NATIVE_UI_VALIDATION_SCHEMA/);
  assert.match(nativeUiAbi, /NATIVE_UI_VALIDATION_FILE = "validation\/native-ui-abi\.json"/);
  assert.match(nativeUiAbi, /COORDINATE_SPACE = "nei_pixels"/);
  assert.match(nativeUiAbi, /SCALE_MODE = "uniform-scale"/);
  assert.match(nativeUiAbi, /SLOT_SIZE = 18/);
  assert.match(nativeUiValidator, /Validates Native UI export ABI geometry/);
  assert.match(nativeUiValidator, /NEI_HANDLER_LAYOUTS_FILE/);
  assert.match(nativeUiValidator, /validateSlot/);
  assert.match(nativeUiValidator, /validateBackground/);
  assert.match(nativeUiValidator, /missingSurfaceCount/);
  assert.match(nativeUiValidator, /slotBoundsViolationCount/);
  assert.match(nativeUiValidator, /backgroundBoundsViolationCount/);
  assert.match(nativeUiValidator, /coordinateContractViolationCount/);
  assert.match(nativeUiValidator, /NATIVE_UI_VALIDATION_FILE/);
  assert.match(neiProvider, /NativeUiExportValidator\.validate\(context\.rawDir\)/);
  assert.match(neiProvider, /counts\.nativeUiMissingSurfaces = nativeUi\.missingSurfaceCount/);
  assert.match(validationCatalog, /GATE_NATIVE_UI_ABI = gateName\("nativeUiAbi"\)/);
  assert.match(validationCatalog, /gateDescriptor\(\s*"nativeUiAbi",\s*"native-ui-abi"/);
  assert.match(validationCatalog, /nativeUiMissingSurfaces == 0/);
  assert.match(validationCatalog, /nativeUiSlotBoundsViolations == 0/);
  assert.match(validationCatalog, /nativeUiBackgroundBoundsViolations == 0/);
  assert.match(validationCatalog, /nativeUiCoordinateContractViolations == 0/);
  assert.match(validation, /RawExportValidationAbiCatalog\.buildGates\(counts, issues\)/);
  assert.doesNotMatch(validation, /RawExportValidationAbiCatalog\.nativeUiAbiGate\(counts\)/);
  assert.doesNotMatch(sidecar, /NativeUiExportValidator/);
});


test('raw render asset catalog and entity model facts are split from sidecar orchestration', () => {
  assert.equal(existsSync(renderAssetProviderUrl), true, 'RawRenderAssetFactStreamProvider must exist');
  assert.equal(existsSync(entityRenderProviderUrl), true, 'RawEntityAndRenderBackendFactStreamProvider must exist');
  assert.equal(existsSync(renderAssetCatalogWriterUrl), true, 'RawExportRenderAssetCatalogWriter must exist');
  assert.equal(existsSync(renderAssetCatalogCountsUrl), true, 'RawRenderAssetCatalogCounts must exist');
  assert.equal(existsSync(entityModelWriterUrl), true, 'RawExportEntityModelWriter must exist');
  assert.equal(existsSync(emptyJsonlWriterUrl), true, 'RawExportEmptyJsonlWriter must exist');
  assert.match(renderAssetProvider, /new RawExportRenderAssetCatalogWriter\(/);
  assert.match(renderAssetProvider, /context\.repositoryDirectory/);
  assert.match(renderAssetProvider, /context\.renderAssets/);
  assert.match(entityRenderProvider, /new RawExportEntityModelWriter\(/);
  assert.match(entityRenderProvider, /context\.repositoryDirectory/);
  assert.match(entityRenderProvider, /context\.schemaVersion/);
  assert.match(entityRenderProvider, /RawExportEmptyJsonlWriter\.write\(new File\(context\.rawDir, "models\/multiblocks\/index\.jsonl\.gz"\)\)/);
  assert.match(entityRenderProvider, /new AngelicaRenderFactsWriter\(context\.entityManager, context\.rawDir, context\.renderAssets\)\.write\(\)/);
  assert.doesNotMatch(factStreamPipeline, /new RawExportRenderAssetCatalogWriter/);
  assert.doesNotMatch(factStreamPipeline, /new RawExportEntityModelWriter/);
  assert.doesNotMatch(factStreamPipeline, /new AngelicaRenderFactsWriter/);
  assert.doesNotMatch(sidecar, /new RawExportRenderAssetCatalogWriter/);
  assert.doesNotMatch(sidecar, /new RawExportEntityModelWriter/);
  assert.doesNotMatch(sidecar, /RawExportEmptyJsonlWriter\.write/);
  assert.doesNotMatch(sidecar, /new AngelicaRenderFactsWriter/);
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

test('raw fact stream registry owns provider order and identity validation', () => {
  for (const provider of [
    'RawRepositoryFactStreamProvider',
    'RawNeiFactStreamProvider',
    'RawRenderAssetFactStreamProvider',
    'RawEntityAndRenderBackendFactStreamProvider',
  ]) {
    assert.match(factStreamRegistry, new RegExp(`new ${provider}\\(\\)`));
  }
  assert.match(factStreamRegistry, /validateAndFreeze/);
  assert.match(factStreamRegistry, /static List<RawExportFactStreamDescriptor> describe/);
  assert.match(factStreamRegistry, /Raw export fact stream provider id must be non-empty/);
  assert.match(factStreamRegistry, /Raw export fact stream provider capabilities must be non-empty/);
  assert.match(factStreamRegistry, /Raw export fact stream provider output families must be non-empty/);
  assert.match(factStreamRegistry, /Duplicate raw export fact stream provider id/);
  assert.match(factStreamPipeline, /private final List<RawExportFactStreamDescriptor> descriptors/);
  assert.match(factStreamPipeline, /this\.descriptors = RawExportFactStreamRegistry\.describe\(providers\)/);
  assert.match(factStreamPipeline, /List<RawExportFactStreamDescriptor> descriptors\(\)/);
  assert.match(factStreamPipeline, /RawExportFactStreamDescriptorWriter\.write\(context\.rawDir, descriptors\)/);
  assert.match(factStreamDescriptor, /final String id/);
  assert.match(factStreamDescriptor, /final List<String> capabilities/);
  assert.match(factStreamDescriptor, /final List<String> outputFamilies/);
  assert.match(factStreamDescriptorWriter, /nesqlpp\/raw-fact-stream-providers\/v1/);
  assert.match(factStreamDescriptorWriter, /raw_fact_stream_providers\.json/);
  assert.match(factStreamDescriptorWriter, /report\.providerCount = descriptors\.size\(\)/);
  assert.match(factStreamDescriptorWriter, /report\.providers = descriptors/);
  assert.match(factStreamDescriptorWriter, /RawExportSidecarFileOps\.ensureDirectory\(validationDir\)/);
  for (const [source, id] of [
    [repositoryProvider, 'raw.repository-facts'],
    [neiProvider, 'raw.nei-facts'],
    [renderAssetProvider, 'raw.render-asset-facts'],
    [entityRenderProvider, 'raw.entity-render-backend-facts'],
  ]) {
    assert.match(source, /implements RawExportFactStreamProvider/);
    assert.equal(source.includes(`return "${id}";`), true, `provider missing id ${id}`);
    assert.match(source, /public List<String> capabilities\(\)/);
    assert.match(source, /public List<String> outputFamilies\(\)/);
  }
  assert.match(repositoryProvider, /raw\.items/);
  assert.match(repositoryProvider, /facts\/recipes/);
  assert.match(neiProvider, /raw\.native-ui\.handler-layouts/);
  assert.match(neiProvider, /facts\/native-ui/);
  assert.match(renderAssetProvider, /raw\.render\.browser-atlas-assets/);
  assert.match(renderAssetProvider, /assets\/browser-atlas/);
  assert.match(entityRenderProvider, /raw\.render\.framebuffer-captures/);
  assert.match(entityRenderProvider, /facts\/render-backend/);
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
  assert.equal(existsSync(exportWriterSupportUrl), true, 'ExportWriterSupport must exist');
  assert.match(reportPipeline, /new RawExportSemanticRuntimeBuilder\(exportContext\)\.build\(\)/);
  assert.match(reportPipeline, /new RawExportReportFactory\(/);
  assert.doesNotMatch(sidecar, /new RawExportSemanticRuntimeBuilder/);
  assert.doesNotMatch(sidecar, /new RawExportReportFactory/);
  assert.match(sidecar, /RawExportSidecarFileOps\.purgeLegacyRawExportOutputs\(rawDir\)/);
  assert.match(sidecar, /FINAL_REPORT_COPIES =/);
  assert.match(sidecar, /for \(FinalReportCopy report : FINAL_REPORT_COPIES\)/);
  assert.match(sidecar, /RawExportSidecarFileOps\.copyRequired\(/);
  assert.match(sidecar, /throw new IllegalArgumentException\("Raw-export sidecar requires precollected render assets"\)/);
  assert.doesNotMatch(sidecar, /Collections\.<CanonicalRenderAsset>emptyList\(\)/);
  assert.doesNotMatch(sidecar, /copyIfPresent/);
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
  assert.match(sidecarFileOps, /Raw-export path exists but is not a directory/);
  assert.match(sidecarFileOps, /copyRequired\(File source, File target, String label\) throws IOException/);
  assert.match(sidecarFileOps, /Missing required raw-export file for /);
  assert.doesNotMatch(sidecarFileOps, /copyIfPresent/);
  assert.match(
    exportWriterSupport,
    /public static void syncRawExportFinalReports\(File repositoryDirectory\) throws Exception \{\s*RawExportSidecarWriter\.syncFinalReports\(repositoryDirectory\);\s*\}/,
  );
  assert.doesNotMatch(exportWriterSupport, /Failed to sync raw-export final reports/);
});
