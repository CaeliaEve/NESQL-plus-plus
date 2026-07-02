import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'fs';
import path from 'path';

const repoRoot = process.cwd();
const readSource = (relativePath) => fs.readFileSync(path.join(repoRoot, relativePath), 'utf8');

const metadata = readSource('src/main/java/com/github/dcysteine/nesql/exporter/main/ExportStageMetadata.java');
const runner = readSource('src/main/java/com/github/dcysteine/nesql/exporter/main/ExportStageRunner.java');
const debugPlaneWriter = readSource('src/main/java/com/github/dcysteine/nesql/exporter/main/ExportDebugPlaneWriter.java');
const debugFileCatalog = readSource('src/main/java/com/github/dcysteine/nesql/elysium/kernel/ExportDebugFile.java');
const selection = readSource('src/main/java/com/github/dcysteine/nesql/exporter/main/ExportSelection.java');
const executionPlan = readSource('src/main/java/com/github/dcysteine/nesql/exporter/main/ExportExecutionPlan.java');
const exporter = readSource('src/main/java/com/github/dcysteine/nesql/exporter/main/Exporter.java');
const commandModeSpec = readSource('src/main/java/com/github/dcysteine/nesql/exporter/main/ExportCommandModeSpec.java');
const commandParser = readSource('src/main/java/com/github/dcysteine/nesql/exporter/main/ExportCommandParser.java');
const commandDispatcher = readSource('src/main/java/com/github/dcysteine/nesql/exporter/main/ExportCommandDispatcher.java');
const integrity = readSource('src/main/java/com/github/dcysteine/nesql/exporter/main/ExportIntegrityManifestWriter.java');
const templateWriter = readSource('src/main/java/com/github/dcysteine/nesql/exporter/local/RawExportUiTemplateCatalogWriter.java');
const templateLayoutSpecs = readSource('src/main/java/com/github/dcysteine/nesql/exporter/plugin/nei/metadata/NeiUiTemplateLayoutSpecs.java');
const nativeUiAbi = readSource('src/main/java/com/github/dcysteine/nesql/exporter/nativeui/NativeUiExportAbi.java');
const nativeUiValidator = readSource('src/main/java/com/github/dcysteine/nesql/exporter/nativeui/NativeUiExportValidator.java');
const rawFileCatalog = readSource('src/main/java/com/github/dcysteine/nesql/exporter/local/RawExportFileCatalog.java');
const rawManifestBuilder = readSource('src/main/java/com/github/dcysteine/nesql/exporter/local/RawExportManifestBuilder.java');
const rawNeiFactWriter = readSource('src/main/java/com/github/dcysteine/nesql/exporter/local/RawExportNeiFactWriter.java');
const rawValidationSupport = readSource('src/main/java/com/github/dcysteine/nesql/exporter/local/RawExportValidationSupport.java');
const rawValidationAbiCatalog = readSource('src/main/java/com/github/dcysteine/nesql/exporter/local/RawExportValidationAbiCatalog.java');
const validationReportWriter = readSource('src/main/java/com/github/dcysteine/nesql/exporter/main/ExportValidationReportWriter.java');
const validationHealthPolicy = readSource('src/main/java/com/github/dcysteine/nesql/exporter/main/ExportValidationHealthPolicy.java');
const validationAbiCatalog = readSource('src/main/java/com/github/dcysteine/nesql/exporter/main/ExportValidationAbiCatalog.java');
const rawRepositoryFactStreamer = readSource('src/main/java/com/github/dcysteine/nesql/exporter/local/RawExportRepositoryFactStreamer.java');
const rawRenderAssetCatalogWriter = readSource('src/main/java/com/github/dcysteine/nesql/exporter/local/RawExportRenderAssetCatalogWriter.java');

test('export stages expose stable incremental families', () => {
  for (const family of [
    'data',
    'ui-census',
    'ui-template-catalog',
    'images',
    'animated-images',
    'render-contracts',
    'browser-layout',
    'multiblocks',
    'eec-models',
    'recipe-layout-contracts',
    'atlas-pack',
  ]) {
    assert.equal(metadata.includes(`"${family}"`), true, `missing stage family ${family}`);
  }
  assert.equal(metadata.includes('WRITE_UI_FAMILY_CENSUS'), true);
  assert.equal(metadata.includes('WRITE_UI_TEMPLATE_CATALOG'), true);
  assert.equal(debugPlaneWriter.includes('ExportStageMetadata.family(stage)'), true);
  assert.equal(debugPlaneWriter.includes('skippableByChecksum'), true);
  assert.equal(selection.includes('writeUiFamilyCensus'), true);
  assert.equal(selection.includes('writeUiTemplateCatalog'), true);
  assert.equal(templateWriter.includes('NativeUiExportAbi.UI_TEMPLATE_CATALOG_FILE'), true);
  assert.equal(templateWriter.includes('computeTemplateSignature'), true);
  assert.equal(templateWriter.includes('templateSignature'), true);
  assert.equal(templateWriter.includes('List<UiTemplateRect> hotspots'), true);
  assert.equal(templateWriter.includes('List<UiTemplateRect> viewports'), true);
  assert.equal(templateWriter.includes('String action;'), false);
  assert.equal(templateWriter.includes('String itemId;'), false);
  assert.equal(templateWriter.includes('String payloadKey;'), false);
  assert.equal(templateWriter.includes('JsonArray dynamicPrimitives'), true);
  assert.equal(templateWriter.includes('primitiveCount'), true);
  assert.equal(templateLayoutSpecs.includes('defaultLayoutSlotsJson'), true);
  assert.equal(integrity.includes('String family;'), true);
});

test('native NEI handler layouts export GT dynamic primitives and background regions at the source', () => {
  assert.equal(templateLayoutSpecs.includes('defaultProgressBarsJson'), true);
  assert.equal(templateLayoutSpecs.includes('gtnh-basic-ui-properties-default'), true);
  assert.equal(templateLayoutSpecs.includes('"gt-progress"'), true);
  assert.equal(templateWriter.includes('template.dynamicPrimitives = NeiUiTemplateLayoutSpecs.defaultProgressBarsJson'), true);
  assert.equal(templateWriter.includes('size(template.dynamicPrimitives)'), false);
  assert.equal(templateLayoutSpecs.includes('bar.addProperty("coordinateSpace", NativeUiExportAbi.COORDINATE_SPACE)'), true);
  assert.equal(templateLayoutSpecs.includes('bar.addProperty("anchor", NativeUiExportAbi.ANCHOR)'), true);
  assert.equal(rawNeiFactWriter.includes('layout.add("dynamicPrimitives"'), false);
  assert.equal(rawNeiFactWriter.includes('layout.add("progressBars"'), false);
  assert.equal(rawNeiFactWriter.includes('layout.add("fluidBars"'), false);
  assert.equal(rawNeiFactWriter.includes('layout.add("energyBars"'), false);
  assert.equal(rawNeiFactWriter.includes('layout.add("hotspots"'), true);
  assert.equal(rawNeiFactWriter.includes('layout.add("viewports"'), true);
  assert.equal(rawNeiFactWriter.includes('layout.addProperty("canonicalMachineFamily", family)'), true);
  assert.equal(rawNeiFactWriter.includes('layout.addProperty("coordinateSpace", NativeUiExportAbi.COORDINATE_SPACE)'), true);
  assert.equal(rawNeiFactWriter.includes('layout.addProperty("scaleMode", NativeUiExportAbi.SCALE_MODE)'), true);
  assert.equal(rawNeiFactWriter.includes('layout.addProperty("anchor", NativeUiExportAbi.ANCHOR)'), true);
  assert.equal(rawNeiFactWriter.includes('addImageRegion(layout, source)'), true);
  assert.equal(rawNeiFactWriter.includes('handler.addProperty("imageResource", imageResource)'), true);
  assert.equal(nativeUiAbi.includes('GT_NEI_BACKGROUND_ASSET_REF'), true);
  assert.equal(nativeUiAbi.includes('GT_NEI_BACKGROUND_RESOURCE'), true);
  assert.equal(nativeUiAbi.includes('COORDINATE_SPACE = "nei_pixels"'), true);
  assert.equal(nativeUiAbi.includes('SCALE_MODE = "uniform-scale"'), true);
  assert.equal(nativeUiAbi.includes('SLOT_SIZE = 18'), true);
  assert.equal(nativeUiAbi.includes('NATIVE_UI_VALIDATION_FILE = "validation/native-ui-abi.json"'), true);
  assert.equal(rawNeiFactWriter.includes('materializeGtNeiBackgroundAsset(rawDir)'), true);
  assert.equal(rawNeiFactWriter.includes('background.addProperty("kind", NativeUiExportAbi.BACKGROUND_KIND_GT_MODULAR_UI)'), true);
  assert.equal(rawNeiFactWriter.includes('background.addProperty("scaling", NativeUiExportAbi.BACKGROUND_SCALING_NINE_SLICE)'), true);
  assert.equal(templateLayoutSpecs.includes('json.addProperty("coordinateSpace", slot.coordinateSpace)'), true);
  assert.equal(templateLayoutSpecs.includes('json.addProperty("slotWidth", slot.slotWidth)'), true);
  assert.equal(rawNeiFactWriter.includes('background.addProperty("captureRequired", false)'), true);
  assert.equal(rawNeiFactWriter.includes('GTNEIDefaultHandler.drawUI(ModularWindow.getBackground)'), true);
  assert.equal(rawNeiFactWriter.includes('String imageResource = trimToEmpty(readString(source, "imageResource", null));'), true);
  assert.equal(rawNeiFactWriter.includes('String imageResource = firstNonBlank(readString(source, "imageResource", null), "");'), false);
  assert.equal(rawNeiFactWriter.includes('throw new IOException("Failed to materialize required GT NEI ModularUI background asset: " + location, e)'), true);
  assert.equal(rawNeiFactWriter.includes('Logger.MOD.warn("Could not materialize GT NEI ModularUI background asset'), false);
  assert.equal(rawNeiFactWriter.includes('StandardCopyOption.ATOMIC_MOVE'), true);
  assert.equal(rawFileCatalog.includes('new ManifestFile("uiBackgrounds", NativeUiExportAbi.UI_BACKGROUNDS_DIRECTORY)'), true);
  assert.equal(rawFileCatalog.includes('new ManifestFile("nativeUiValidation", NativeUiExportAbi.NATIVE_UI_VALIDATION_FILE)'), true);
  assert.equal(integrity.includes('RawExportFileCatalog.NATIVE_UI_VALIDATION_FILE'), true);
  assert.equal(integrity.includes('"native-ui-validation"'), true);
  assert.equal(templateWriter.includes('template.nativeBackground.assetRef'), true);
  assert.equal(templateWriter.includes('template.coordinateSpace = NativeUiExportAbi.COORDINATE_SPACE'), true);
  assert.equal(templateWriter.includes('template.scaleMode = NativeUiExportAbi.SCALE_MODE'), true);
  assert.equal(nativeUiValidator.includes('validateSlot'), true);
  assert.equal(nativeUiValidator.includes('validateBackground'), true);
  assert.equal(nativeUiValidator.includes('finish(rawDir, result)'), true);
  assert.equal(rawValidationAbiCatalog.includes('GATE_NATIVE_UI_ABI = gateName("nativeUiAbi")'), true);
  assert.equal(rawValidationAbiCatalog.includes('nativeUiMissingSurfaces == 0'), true);
  assert.equal(rawValidationSupport.includes('RawExportValidationAbiCatalog.buildGates(counts, issues)'), true);
  assert.equal(validationReportWriter.includes('nativeUiTotals'), true);
  assert.equal(validationHealthPolicy.includes('ExportValidationAbiCatalog.BLOCKED_NATIVE_UI_ABI'), true);
  assert.equal(validationAbiCatalog.includes('Native UI ABI validation is blocked'), true);
});

test('export checksums carry previous-run reuse signals without mtime dependence', () => {
  assert.equal(integrity.includes('readPreviousArtifacts'), true);
  assert.equal(integrity.includes('previousSha256'), true);
  assert.equal(integrity.includes('boolean unchanged;'), true);
  assert.equal(integrity.includes('boolean skippableByChecksum;'), true);
  assert.equal(integrity.includes('sha256(file)'), true);
  assert.equal(integrity.includes('file.lastModified()'), false);
  assert.equal(integrity.includes('stats.update(relative(root, file), file.length())'), true);
});

test('render outputs are skipped only when item/render/texture signature matches', () => {
  const job = readSource('src/main/java/com/github/dcysteine/nesql/exporter/util/render/RenderJob.java');
  const dispatcher = readSource('src/main/java/com/github/dcysteine/nesql/exporter/util/render/RenderDispatcher.java');
  const renderer = readSource('src/main/java/com/github/dcysteine/nesql/exporter/util/render/Renderer.java');
  assert.equal(job.includes('getRenderSignature()'), true);
  assert.equal(job.includes('getRenderSignatureFilePath()'), true);
  assert.equal(dispatcher.includes('RenderSignatureSupport.matches(imageDirectory, job)'), true);
  assert.equal(renderer.includes('RenderSignatureSupport.write(imageDirectory, job)'), true);
});

test('atlas reuse is guarded by sprite source hash and atlas page hash', () => {
  const atlas = readSource('src/main/java/com/github/dcysteine/nesql/exporter/local/CanonicalAtlasPackWriter.java');
  assert.equal(atlas.includes('sourceSignature'), true);
  assert.equal(atlas.includes('atlasPageSha256'), true);
  assert.equal(atlas.includes('sourceSha256'), true);
  assert.equal(atlas.includes('sourceBytes'), true);
  assert.equal(atlas.includes('oldestOutputModified < newestSourceModified'), false);
});

test('Java 25 unsafe shader renderers are exported through contract-safe icons', () => {
  const job = readSource('src/main/java/com/github/dcysteine/nesql/exporter/util/render/RenderJob.java');
  const contract = readSource('src/main/java/com/github/dcysteine/nesql/exporter/util/render/RenderContractMetadataExtractor.java');
  assert.equal(job.includes('isContractSafeRendererClass(normalized)'), true);
  for (const renderer of [
    'universiumrenderer',
    'infinityrenderer',
    'cosmicneutroniumrenderer',
    'glitcheffectrenderer',
    'wireframetesseractrenderer',
    'rainbowoverlayrenderer',
    'gaiaspiritrenderer',
  ]) {
    assert.equal(job.includes(renderer), true, `missing contract-safe renderer ${renderer}`);
  }
  assert.equal(contract.includes('"gt_universium"'), true);
  assert.equal(contract.includes('"botania_gaia_spirit"'), true);
  assert.equal(contract.includes('"gaia_spirit"'), true);
});

test('Botania flower block items are not forced into framebuffer animation registry', () => {
  const registry = readSource('src/main/java/com/github/dcysteine/nesql/exporter/util/render/AnimatedItemRegistry.java');
  assert.equal(registry.includes('animatedItemIds.add("Botania:flower")'), false);
  assert.equal(registry.includes('Plain Botania flowers are static block-item sprites'), true);
});

test('canonical repository snapshot is streamed in bounded pages', () => {
  const snapshot = readSource('src/main/java/com/github/dcysteine/nesql/exporter/local/CanonicalRepositorySnapshotWriter.java');
  assert.equal(snapshot.includes('JsonWriter'), true);
  assert.equal(snapshot.includes('setFirstResult(offset)'), true);
  assert.equal(snapshot.includes('setMaxResults(RECIPE_BATCH_SIZE)'), true);
  assert.equal(snapshot.includes('entityManager.clear()'), true);
  assert.equal(snapshot.includes('new CanonicalRepositoryModel()'), false);
  assert.equal(snapshot.includes('gson.toJson(model, writer)'), false);
  assert.equal(snapshot.includes('List<Recipe> recipes =\n                entityManager.createQuery'), false);
  assert.equal(snapshot.includes('List<GregTechRecipe> gtRecipes =\n                entityManager.createQuery'), false);
});

test('GTNH 2.8.4 export hardening keeps edge recipes instead of dropping them', () => {
  const idUtil = readSource('src/main/java/com/github/dcysteine/nesql/exporter/util/IdUtil.java');
  const itemFactory = readSource('src/main/java/com/github/dcysteine/nesql/exporter/plugin/base/factory/ItemFactory.java');
  const avaritia = readSource('src/main/java/com/github/dcysteine/nesql/exporter/plugin/avaritia/ExtremeCraftingProcessor.java');
  const botaniaBrew = readSource('src/main/java/com/github/dcysteine/nesql/exporter/plugin/botania/BrewRecipeProcessor.java');
  const recipeBuilder = readSource('src/main/java/com/github/dcysteine/nesql/exporter/plugin/base/factory/RecipeBuilder.java');
  assert.equal(idUtil.includes('findUniqueIdentifierSafely'), true);
  assert.equal(idUtil.includes('catch (Throwable ignored)'), true);
  assert.equal(itemFactory.includes('IdUtil.findUniqueIdentifierSafely'), true);
  assert.equal(avaritia.includes('readRecipeOutput(recipe)'), true);
  assert.equal(avaritia.includes('getRecipeOutput'), true);
  assert.equal(avaritia.includes('readRecipeInputs(recipe)'), true);
  assert.equal(botaniaBrew.includes('readBrewRecipesReflectively'), true);
  assert.equal(botaniaBrew.includes('"brewRecipes"'), true);
  assert.equal(botaniaBrew.includes('getManaUsage'), true);
  assert.equal(recipeBuilder.includes('Skipping item input index for shapeless recipe!'), false);
  assert.equal(recipeBuilder.includes('Skipping item output index for shapeless recipe!'), false);
});

test('block face export skips known invalid metadata before noisy mod icon lookups', () => {
  const blockFaces = readSource('src/main/java/com/github/dcysteine/nesql/exporter/main/BlockFaceExporter.java');
  assert.equal(blockFaces.includes('shouldSkipBlockFaceMeta(blockName, meta)'), true);
  assert.equal(blockFaces.includes('"compactkineticgenerators:BlockCkg"'), true);
  assert.equal(blockFaces.includes('return meta < 0 || meta > 11;'), true);
});

test('special facts keep domain aliases needed by runtime coverage gates', () => {
  const nei = readSource('src/main/java/com/github/dcysteine/nesql/exporter/plugin/nei/NeiRecipeExportProcessor.java');
  assert.equal(rawRepositoryFactStreamer.includes('copyFirstNumber(facts, "altarTier"'), true);
  assert.equal(rawRepositoryFactStreamer.includes('copyElement(facts, "entityId", recipe, "metadata.mobName")'), true);
  assert.equal(rawRepositoryFactStreamer.includes('copyElement(facts, "species", recipe, "metadata.beeSpecies")'), true);
  assert.equal(rawRepositoryFactStreamer.includes('copyElement(facts, "temperature", recipe, "metadata.temperature")'), true);
  assert.equal(rawRepositoryFactStreamer.includes('copyElement(facts, "humidity", recipe, "metadata.humidity")'), true);
  assert.equal(nei.includes('copyFirstExistingForestryFact(metadata, recipe, "allele"'), true);
  assert.equal(nei.includes('extractForestryEnvironmentFromRequirements(metadata, requirements)'), true);
});

test('runtime command surface is limited to guided export and Thaumcraft aspect unlock', () => {
  const main = readSource('src/main/java/com/github/dcysteine/nesql/exporter/main/Main.java');
  const exportCommand = readSource('src/main/java/com/github/dcysteine/nesql/exporter/main/ExportCommand.java');
  assert.equal(main.includes('new ExportCommand()'), true);
  assert.equal(main.includes('new ThaumcraftUnlockAspectsCommand()'), true);
  assert.equal(exportCommand.includes('return "nesql";'), true);
  assert.equal(exportCommand.includes('ExportCommandParser.USAGE'), true);
  assert.equal(exportCommand.includes('ExportCommandDispatcher.dispatch(sender, args)'), true);
  assert.equal(exportCommand.includes('ClientGuiScheduler.open('), false);
  assert.equal(exportCommand.includes('"--full-export".equalsIgnoreCase(arg)'), false);
  assert.equal(exportCommand.includes('"--native-ui-export".equalsIgnoreCase(arg)'), false);
  assert.equal(commandModeSpec.includes('ExportCommandMode.FULL_EXPORT'), true);
  assert.equal(commandModeSpec.includes('"--full-export"'), true);
  assert.equal(commandModeSpec.includes('"--native-ui-export"'), true);
  assert.equal(commandModeSpec.includes('"--semantic-check"'), true);
  assert.equal(commandParser.includes('ExportCommandModeSpec.fromFlag(arg)'), true);
  assert.equal(commandParser.includes('MODE_CONFLICT_MESSAGE'), true);
  assert.equal(commandDispatcher.includes('ClientGuiScheduler.open(new ExportSelectionGui(request.repositoryName))'), true);
  assert.equal(commandDispatcher.includes('startSelectedExport(request.repositoryName, ExportSelection.full())'), true);
  assert.equal(commandDispatcher.includes('startNativeUiExport(request.repositoryName)'), true);
  for (const legacyCommand of [
    'new DataExportCommand()',
    'new ImageExportCommand()',
    'new BotaniaDataExportCommand()',
    'new BrowserLayoutExportCommand()',
    'new AnimatedAtlasExportCommand()',
    'new GregTechMultiblockExportCommand()',
    'new BlockFaceExportCommand()',
    'new IndustrialSlaughterhouseEntityExportCommand()',
  ]) {
    assert.equal(main.includes(legacyCommand), false, `legacy command should not be registered: ${legacyCommand}`);
  }
});

test('guided export GUI exposes selectable lanes and keeps command entrypoint concise', () => {
  const gui = readSource('src/main/java/com/github/dcysteine/nesql/exporter/main/ExportSelectionGui.java');
  const command = readSource('src/main/java/com/github/dcysteine/nesql/exporter/main/ExportCommand.java');
  for (const label of [
    'Core items',
    'Recipes',
    'GT blueprints',
    'Block faces',
    'Item rendering',
    'Render manifests',
    'Static atlas',
    'Animated atlas',
    'Browser layout',
    'Database commit',
  ]) {
    assert.equal(gui.includes(`new Option("${label}"`), true, `missing selectable lane ${label}`);
  }
  assert.equal(gui.includes('Begin Export'), true);
  assert.equal(gui.includes('Data Only'), true);
  assert.equal(gui.includes('raw-export is authoritative'), true);
  assert.equal(gui.includes('normalizeDependencies();'), true);
  assert.equal(command.includes('ClientGuiScheduler.open('), false);
  assert.equal(command.includes('Choose only one of --semantic-check, --full-export, or --native-ui-export.'), false);
  assert.equal(command.includes('new Exporter(repositoryName, selection)'), false);
  assert.equal(commandDispatcher.includes('ClientGuiScheduler.open(new ExportSelectionGui(request.repositoryName))'), true);
  assert.equal(commandParser.includes('Choose only one of --semantic-check, --full-export, or --native-ui-export.'), true);
  assert.equal(commandDispatcher.includes('new Exporter(repositoryName, selection)'), true);
  assert.equal(gui.includes('selection.isNativeUiExport()'), true);
  assert.equal(gui.includes('ExportCommandDispatcher.startNativeUiExport(repositoryName)'), true);
  assert.equal(gui.includes('ExportCommandDispatcher.startSelectedExport(repositoryName, selection)'), true);
});

test('native UI export has an explicit fast stage plan while full export remains complete', () => {
  assert.equal(selection.includes('ExportSelection nativeUiExport()'), true);
  assert.equal(selection.includes('boolean isNativeUiExport()'), true);
  assert.equal(selection.includes('.renderImages(false)'), true);
  assert.equal(selection.includes('.writeAtlasPacks(false)'), true);
  assert.equal(selection.includes('.writeAnimatedAtlasPacks(false)'), true);
  assert.equal(selection.includes('.commitDatabase(false)'), true);
  assert.equal(exporter.includes('static Exporter nativeUiExport(String repositoryName)'), true);
  assert.equal(exporter.includes('ExportProfile.DATA_ONLY_V104'), true);
  assert.equal(commandDispatcher.includes('Native UI Fast Export / v1.04-data /'), true);
  assert.equal(commandDispatcher.includes('Skipping render/atlas/database-commit lanes'), true);

  assert.equal(executionPlan.includes('if (profile.renderImages && selection.includesStage(ExportStage.RENDER_IMAGES, profile))'), true);
  assert.equal(executionPlan.includes('addIfSelected(stages, ExportStage.WRITE_ATLAS_PACKS, profile, selection)'), true);
  assert.equal(executionPlan.includes('addIfSelected(stages, ExportStage.WRITE_ANIMATED_ATLAS_PACKS, profile, selection)'), true);
  assert.equal(selection.includes('public static ExportSelection full()'), true);
  assert.equal(selection.includes('return new Builder().build();'), true);
  assert.equal(selection.includes('&& renderImages\n                && writeRenderManifests'), true);
  assert.equal(selection.includes('&& writeAtlasPacks\n                && writeAnimatedAtlasPacks'), true);
});

test('canonical output is opt-in debug staging after raw-export migration', () => {
  const selection = readSource('src/main/java/com/github/dcysteine/nesql/exporter/main/ExportSelection.java');
  const runner = readSource('src/main/java/com/github/dcysteine/nesql/exporter/main/ExportStageRunner.java');
  const support = readSource('src/main/java/com/github/dcysteine/nesql/exporter/main/ExportWriterSupport.java');
  assert.equal(selection.includes('private boolean writeCanonicalSnapshot = false;') || selection.includes('private final boolean writeCanonicalSnapshot;'), true);
  assert.equal(selection.includes('&& writeCanonicalSnapshot\n                && writeMultiblocks'), false);
  assert.equal(runner.includes('deleteCanonicalStagingDirectory'), true);
  assert.equal(runner.includes('deleteCanonicalStagingDirectory'), true);
  assert.equal(support.includes('Removed legacy canonical staging output; raw-export is authoritative.'), true);
  assert.equal(rawFileCatalog.includes('EXPORT_MANIFEST_FILE_NAME = "export_manifest.json"'), true);
  assert.equal(rawRenderAssetCatalogWriter.includes('objectAt(item, "staticAtlas")'), true);
  assert.equal(rawRenderAssetCatalogWriter.includes('objectAt(item, "animatedAtlas")'), true);
  assert.equal(rawRenderAssetCatalogWriter.includes('element == null || !element.isJsonObject()'), true);
});

test('export progress noise is curated into English preparation summaries', () => {
  const itemExporter = readSource('src/main/java/com/github/dcysteine/nesql/exporter/local/ModBasedItemExporter.java');
  const logger = readSource('src/main/java/com/github/dcysteine/nesql/exporter/main/Logger.java');
  const progressGui = readSource('src/main/java/com/github/dcysteine/nesql/exporter/main/ExportProgressGui.java');
  assert.equal(itemExporter.includes('"Preparation stage"'), true);
  assert.equal(itemExporter.includes('"Initializing item index..."'), true);
  assert.equal(itemExporter.includes('"Scanning NEI item index: " + dataset.items.size() + " entries"'), true);
  assert.equal(itemExporter.includes('"Building export context, please wait"'), true);
  assert.equal(itemExporter.includes('"Found " + dataset.items.size() + " items"'), false);
  assert.equal(logger.includes('clean.contains("scanning nei item index")'), true);
  assert.equal(progressGui.includes('lower.contains("scanning nei item index")'), true);
});

test('stage diagnostics write machine-readable checkpoint and error records', () => {
  const runner = readSource('src/main/java/com/github/dcysteine/nesql/exporter/main/ExportStageRunner.java');
  const diagnostics = readSource('src/main/java/com/github/dcysteine/nesql/exporter/main/ExportDiagnosticsSupport.java');
  assert.equal(runner.includes('ExportDebugPlaneWriter.writeStageCheckpointReport'), true);
  assert.equal(runner.includes('stage_checkpoint.json'), false);
  assert.equal(debugPlaneWriter.includes('ExportDebugFile.STAGE_CHECKPOINT'), true);
  assert.equal(debugPlaneWriter.includes('DEBUG_REPORTS = validateAndFreeze'), true);
  assert.equal(debugPlaneWriter.includes('debugFile(exportContext, descriptor.file())'), true);
  assert.equal(debugPlaneWriter.includes('writeDebugReport('), true);
  assert.equal(debugPlaneWriter.includes('ExportDebugFile.KERNEL_TRACE'), true);
  assert.equal(debugFileCatalog.includes('"validation/stage_checkpoint.json"'), true);
  assert.equal(debugFileCatalog.includes('"export/checkpoint.json"'), true);
  assert.equal(debugFileCatalog.includes('"trace/latest.json"'), true);
  assert.equal(runner.includes('ExportValidationReportWriter.write(exportContext)'), true);
  assert.equal(diagnostics.includes('RawExportFileCatalog.VALIDATION_ERRORS_FILE'), true);
  assert.equal(diagnostics.includes('ExportValidationAbiCatalog.EXPORT_ERROR_SCHEMA'), true);
  assert.equal(validationAbiCatalog.includes('descriptor("exportError", ExportSchemaCatalog.EXPORT_ERROR)'), true);
  assert.equal(diagnostics.includes('"stage"'), true);
  assert.equal(diagnostics.includes('ExportValidationAbiCatalog.EXPORT_ERROR_STAGE_UNKNOWN'), true);
});
