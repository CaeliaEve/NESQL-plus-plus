import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'fs';
import path from 'path';

const repoRoot = process.cwd();
const readSource = (relativePath) => fs.readFileSync(path.join(repoRoot, relativePath), 'utf8');

const read = readSource;

test('base exported image paths stay on png so animated outputs can be an opt-in overlay', () => {
  const rendererSource = read('src/main/java/com/github/dcysteine/nesql/exporter/util/render/Renderer.java');
  const idUtilSource = read('src/main/java/com/github/dcysteine/nesql/exporter/util/IdUtil.java');

  assert.equal(
    rendererSource.includes('return ConfigOptions.EXPORT_GIF.get() ? ".gif" : ".png";'),
    false,
    'Renderer should not globally switch all exported image file names to .gif',
  );

  assert.equal(
    idUtilSource.includes('Renderer.IMAGE_FILE_EXTENSION'),
    false,
    'IdUtil image paths should no longer depend on a global gif extension toggle',
  );
});

test('gif renderer writes gif output instead of renaming png files behind a gif extension', () => {
  const gifRendererSource = read('src/main/java/com/github/dcysteine/nesql/exporter/util/render/GifRenderer.java');

  assert.equal(
    gifRendererSource.includes('replace(".gif", ".png")'),
    false,
    'GifRenderer should not replace gif outputs with png files',
  );

  assert.equal(
    gifRendererSource.includes('ImageWriter') || gifRendererSource.includes('ImageOutputStream'),
    true,
    'GifRenderer should use an actual GIF writer pipeline',
  );
});

test('render-contract rebuild can recover item assets from image outputs when the database is unavailable', () => {
  const collectorSource = read('src/main/java/com/github/dcysteine/nesql/exporter/local/CanonicalRenderAssetCollector.java');

  assert.equal(
    collectorSource.includes('return collectFamilyAssetsFromImageFiles("item");'),
    true,
    'Collector should fall back to scanning image/item outputs when split item exports are unavailable',
  );

  assert.equal(
    collectorSource.includes('return collectFamilyAssetsFromImageFiles("fluid");'),
    true,
    'Collector should use the same family image scan path for fluid rebuilds',
  );
});

test('gif-backed item exports are treated as animated assets based on the actual exported file', () => {
  const collectorSource = read('src/main/java/com/github/dcysteine/nesql/exporter/local/CanonicalRenderAssetCollector.java');
  const atlasWriterSource = read('src/main/java/com/github/dcysteine/nesql/exporter/local/CanonicalAnimatedAtlasPackWriter.java');

  assert.equal(
    collectorSource.includes('GifAnimationInfo gifAnimation = inspectGifAnimation(baseFile);'),
    true,
    'Collector should inspect the resolved export artifact for GIF animation metadata',
  );

  assert.equal(
    collectorSource.includes('exactName.endsWith(".png") && alternateGif != null && inspectGifAnimation(alternateGif).animated'),
    true,
    'Collector should prefer a real animated GIF sidecar over an exact PNG when the GIF carries the real timeline',
  );

  assert.equal(
    collectorSource.includes('File alternateGif = replaceExtension(familyIndex, normalizedKey, ".gif");')
      || collectorSource.includes('File alternateGif = replaceExtension(familyIndex, normalizedImagePath, ".gif");'),
    true,
    'Collector should swap a requested .png path over to a sibling .gif artifact when the png output is absent',
  );

  assert.equal(
    collectorSource.includes('asset.renderMode = "captured_final_atlas";'),
    true,
    'Collector should normalize rendered GIF assets to captured_final_atlas playback metadata',
  );

  assert.equal(
    collectorSource.includes('asset.frameDurationSource = "gif_metadata";')
      && collectorSource.includes('asset.frameDurationSource = "minecraft_tick_capture";'),
    true,
    'Collector should label whether animation timing came from an existing GIF or from Minecraft tick capture',
  );

  assert.equal(
    collectorSource.includes('normalizedImagePath.endsWith(".gif")'),
    false,
    'Collector should not key GIF animation detection off the requested image path alone',
  );

  assert.equal(
    atlasWriterSource.includes('loadGifFrames(sourceFile)'),
    true,
    'Animated atlas packing should be able to extract frames from rendered GIF assets',
  );
});

test('native sprite metadata reads real mcmeta timing instead of collapsing every animation to 50ms', () => {
  const metadataSource = read('src/main/java/com/github/dcysteine/nesql/exporter/util/render/NativeSpriteMetadataExtractor.java');

  assert.equal(
    metadataSource.includes('buildAnimationMetadataCandidates'),
    true,
    'Native sprite metadata extraction should resolve texture .png.mcmeta resources',
  );

  assert.equal(
    metadataSource.includes('parseResourceAnimationMetadata'),
    true,
    'Native sprite metadata extraction should parse raw animation JSON from resource packs',
  );

  assert.equal(
    metadataSource.includes('frame.put("durationMs", sourceFrame.timeTicks * 50);'),
    true,
    'Native sprite timeline should preserve per-frame tick durations from mcmeta',
  );
});

test('custom inventory renderers still export native sprite sidecars as auxiliary timing metadata', () => {
  const renderJobSource = read('src/main/java/com/github/dcysteine/nesql/exporter/util/render/RenderJob.java');
  const collectorSource = read('src/main/java/com/github/dcysteine/nesql/exporter/local/CanonicalRenderAssetCollector.java');

  assert.equal(
    renderJobSource.includes('return getNativeSpriteMetadata() != null;'),
    true,
    'custom renderer items should still emit sprite sidecars so NeoNEI can recover native sprite timelines',
  );

  assert.equal(
    collectorSource.indexOf('applyRenderContractMetadata(asset, renderContractFile);')
      < collectorSource.indexOf('applyNativeSpriteMetadata(asset, baseFile);'),
    true,
    'render contract metadata should be loaded before sprite sidecars so auxiliary native timelines do not override primary renderer-family playback',
  );

  assert.equal(
    collectorSource.includes('asset.animationMode = "native_sprite_aux";'),
    true,
    'collector should preserve auxiliary native sprite timelines without misclassifying custom renderers as primary native animations',
  );
});

test('gregtech multiblock export discovers runtime controllers from the full GT registry', () => {
  const exporterSource = read('src/main/java/com/github/dcysteine/nesql/exporter/main/GregTechMultiblockExporter.java');

  assert.equal(
    exporterSource.includes('GregTechAPI') && exporterSource.includes('METATILEENTITIES'),
    true,
    'GregTech multiblock export should inspect the runtime GregTech registry instead of only relying on a curated whitelist',
  );

  assert.equal(
    exporterSource.includes('discoverRuntimeBlueprints'),
    true,
    'GregTech multiblock export should build a runtime blueprint catalog before merging curated overrides',
  );
});

test('render jobs classify known time-based custom inventory renderers as animated captures', () => {
  const renderJobSource = read('src/main/java/com/github/dcysteine/nesql/exporter/util/render/RenderJob.java');

  assert.equal(
    renderJobSource.includes('hasAnimatedCustomRenderer'),
    true,
    'RenderJob should detect animated custom inventory renderers separately from atlas sprites',
  );

  assert.equal(
    renderJobSource.includes('itemrenderertier') && renderJobSource.includes('rocket'),
    true,
    'Rocket-style custom renderers should be force-captured as multi-frame animations',
  );

  assert.equal(
    renderJobSource.includes('transcendentalmetaitemrenderer')
      && renderJobSource.includes('glitcheffectmetaitemrenderer')
      && renderJobSource.includes('wireframetesseractrenderer'),
    true,
    'Known GTNH fancy item renderers should be classified as animated captures',
  );
});

test('atlas-backed custom renderer captures can explicitly tick sprite animations between frames', () => {
  const renderJobSource = read('src/main/java/com/github/dcysteine/nesql/exporter/util/render/RenderJob.java');
  const rendererSource = read('src/main/java/com/github/dcysteine/nesql/exporter/util/render/Renderer.java');
  const inspectorSource = read('src/main/java/com/github/dcysteine/nesql/exporter/util/render/TextureAnimationInspector.java');

  assert.equal(
    renderJobSource.includes('shouldAdvanceTextureAtlasBetweenFrames'),
    true,
    'RenderJob should expose when atlas-backed animation frames need manual advancement during GIF capture',
  );

  assert.equal(
    renderJobSource.includes('markAnimatedTexturesForUpdate'),
    true,
    'RenderJob should be able to mark layered custom-renderer sprite inputs as active before the next frame',
  );

  assert.equal(
    rendererSource.includes('advanceTextureAnimations(job);'),
    true,
    'Renderer should advance animated texture atlases between framebuffer captures for qualifying jobs',
  );

  assert.equal(
    inspectorSource.includes('markNeedsAnimationUpdate'),
    true,
    'TextureAnimationInspector should support marking patched atlas sprites as needing a real animation upload',
  );
});

test('multi-frame framebuffer capture advances atlas animations before rendering the next frame', () => {
  const rendererSource = read('src/main/java/com/github/dcysteine/nesql/exporter/util/render/Renderer.java');
  const frameLoopStart = rendererSource.indexOf('RenderDiagnosticsSupport.writeCurrentRenderJob(imageDirectory, job);');
  const advanceIndex = rendererSource.indexOf('advanceTextureAnimations(job);', frameLoopStart);
  const renderIndex = rendererSource.indexOf('BufferedImage image = renderIsolatedFrame(job);', frameLoopStart);

  assert.equal(
    advanceIndex > frameLoopStart && advanceIndex < renderIndex,
    true,
    'Renderer must advance atlas-backed animation textures before framebuffer readback for frame 1+',
  );

  assert.equal(
    rendererSource.includes('if (job.getFrameIndex() <= 0)'),
    true,
    'Frame 0 should remain the baseline capture; atlas advancement starts before subsequent frames',
  );
});

test('render contracts prefer captured atlas playback for custom inventory renderers even when a renderer family is known', () => {
  const contractSource = read('src/main/java/com/github/dcysteine/nesql/exporter/util/render/RenderContractMetadataExtractor.java');

  const renderModeBody = contractSource.slice(
    contractSource.indexOf('private static String determineRenderMode'),
    contractSource.indexOf('private static String determineCaptureSource'),
  );
  assert.equal(
    renderModeBody.indexOf('if (hasCustomInventoryRenderer)') >= 0
      && renderModeBody.indexOf('return "captured_final_atlas"') > renderModeBody.indexOf('if (hasCustomInventoryRenderer)')
      && renderModeBody.indexOf('if (rendererFamily != null && !rendererFamily.isEmpty())') > renderModeBody.indexOf('return "captured_final_atlas"'),
    true,
    'Render contracts should make captured atlas playback primary for custom inventory renderers before renderer-family emulation',
  );

  assert.equal(
    contractSource.includes('? "inventory_renderer_family_capture"')
      && contractSource.includes(': "inventory_renderer_capture";'),
    true,
    'Capture source should distinguish inventory-renderer captures from pure family metadata playback',
  );
});

test('data-only export profile is named for v1.04 instead of the removed v14 chain', () => {
  const profileSource = read('src/main/java/com/github/dcysteine/nesql/exporter/main/ExportProfile.java');
  const dataExporterSource = read('src/main/java/com/github/dcysteine/nesql/exporter/main/DataExporter.java');

  assert.equal(
    profileSource.includes('DATA_ONLY_V104'),
    true,
    'The data-only export profile should use the v1.04-era name in code as well as user-facing IDs',
  );

  assert.equal(
    dataExporterSource.includes('ExportProfile.DATA_ONLY_V104'),
    true,
    'DataExporter should route through the renamed v1.04 data-only profile',
  );
});

test('canonical recipe mapping emits a structured layout contract for downstream generic renderers', () => {
  const mapperSource = read('src/main/java/com/github/dcysteine/nesql/exporter/canonical/CanonicalExportMapper.java');
  const validatorSource = read('src/main/java/com/github/dcysteine/nesql/exporter/canonical/CanonicalContractValidator.java');

  assert.equal(
    mapperSource.includes('layout.put("contractVersion", 1);')
      && mapperSource.includes('layout.put("fallbackGrid", fallbackGrid);')
      && mapperSource.includes('layout.put("legacyHints", legacyHints);')
      && mapperSource.includes('layout.put("itemSlots", itemSlots);')
      && mapperSource.includes('layout.put("fluidSlots", fluidSlots);')
      && mapperSource.includes('layout.put("bindings", bindings);'),
    true,
    'Canonical layout should now expose structured fallback grid, legacy hints, slot geometry, and bindings blocks',
  );

  assert.equal(
    mapperSource.includes('private static Map<String, Object> buildCanvas(Map<String, Object> metadata)')
      && mapperSource.includes('private static Map<String, Object> buildLegacyHints(Map<String, Object> metadata)')
      && mapperSource.includes('private static List<Map<String, Object>> buildItemSlots(Map<String, Object> metadata)')
      && mapperSource.includes('private static List<Map<String, Object>> buildFluidSlots(Recipe recipe, RecipeType recipeType)')
      && mapperSource.includes('private static Map<String, Object> buildBindings(Map<String, Object> metadata)'),
    true,
    'Canonical mapper should keep the richer layout contract assembled from dedicated helpers',
  );

  assert.equal(
    validatorSource.includes('if (recipe.layout == null) issues.add("recipe.layout:missing");'),
    true,
    'Canonical contract validation should fail fast when a recipe loses its layout contract',
  );
});

test('split recipe export carries the layout contract and slot indexes through dto assembly', () => {
  const exporterSource = read('src/main/java/com/github/dcysteine/nesql/exporter/local/ModBasedRecipeExporter.java');
  const assemblerSource = read('src/main/java/com/github/dcysteine/nesql/exporter/local/ModBasedRecipeDtoAssembler.java');

  assert.equal(
    exporterSource.includes('public java.util.Map<String, Object> layout;')
      && exporterSource.includes('public Integer slotIndex;'),
    true,
    'Split recipe DTOs should expose layout plus explicit output slot indexes',
  );

  assert.equal(
    assemblerSource.includes('dto.layout = canonicalRecipe.layout == null || canonicalRecipe.layout.isEmpty()')
      && assemblerSource.includes('dto.slotIndex = slotIndex;')
      && assemblerSource.includes('for (Map.Entry<Integer, ItemStackWithProbability> entry : sortedEntries(recipe.getItemOutputs()))')
      && assemblerSource.includes('for (Map.Entry<Integer, com.github.dcysteine.nesql.sql.base.fluid.FluidStackWithProbability> entry : sortedEntries(recipe.getFluidOutputs()))'),
    true,
    'DTO assembly should preserve deterministic slot ordering and carry the layout contract to split export files',
  );
});

test('nei recipe export captures raw positioned slot geometry for the layout contract', () => {
  const neiSource = read('src/main/java/com/github/dcysteine/nesql/exporter/plugin/nei/NeiRecipeExportProcessor.java');

  assert.equal(
    neiSource.includes('addSlotLayout(data, "inputSlotLayout", buildPositionedSlotLayout(ingredients, recipeType, "input"));')
      && neiSource.includes('addSlotLayout(data, "outputSlotLayout", buildPositionedSlotLayout(singletonPositionedStack(result), null, "output"));')
      && neiSource.includes('List<Map<String, Object>> otherSlotLayout = buildPositionedSlotLayout(others, null, "other");')
      && neiSource.includes('addSlotLayout(data, "otherSlotLayout", otherSlotLayout);'),
    true,
    'NEI export should persist input/output/other positioned stack geometry into recipe metadata',
  );

  assert.equal(
    neiSource.includes('private List<Map<String, Object>> buildPositionedSlotLayout(')
      && neiSource.includes('slot.put("coordinateSpace", "nei_pixels");')
      && neiSource.includes('slot.put("source", "positioned_stack");'),
    true,
    'Positioned stack layout extraction should preserve raw NEI pixel-space slot geometry',
  );
});

// P0 export integrity/report contract guardrails.
test('export pipeline writes manifest, checksums, and health report aliases', () => {
  const runnerSource = read('src/main/java/com/github/dcysteine/nesql/exporter/main/ExportStageRunner.java');
  const validationStoreSource = read('src/main/java/com/github/dcysteine/nesql/exporter/main/ExportValidationReportStore.java');
  const manifestSource = read('src/main/java/com/github/dcysteine/nesql/exporter/main/ExportIntegrityManifestWriter.java');
  const rawFileCatalogSource = read('src/main/java/com/github/dcysteine/nesql/exporter/local/RawExportFileCatalog.java');

  assert.equal(
    runnerSource.includes('ExportIntegrityManifestWriter.write(exportContext);'),
    true,
    'Export runner should write export-manifest/stage-checksums after validation',
  );
  assert.equal(
    validationStoreSource.includes('RawExportFileCatalog.EXPORT_HEALTH_REPORT_FILE_NAME')
      && rawFileCatalogSource.includes('EXPORT_HEALTH_REPORT_FILE_NAME = "export-health-report.json"'),
    true,
    'Validation report store should emit the canonical export-health-report alias',
  );
  assert.equal(
    manifestSource.includes('RawExportFileCatalog.EXPORT_MANIFEST_DASH_FILE_NAME')
      && rawFileCatalogSource.includes('EXPORT_MANIFEST_DASH_FILE_NAME = "export-manifest.json"'),
    true,
    'Integrity writer should emit export-manifest.json',
  );
  assert.equal(
    manifestSource.includes('RawExportFileCatalog.STAGE_CHECKSUMS_DASH_FILE_NAME')
      && rawFileCatalogSource.includes('STAGE_CHECKSUMS_DASH_FILE_NAME = "stage-checksums.json"'),
    true,
    'Integrity writer should emit stage-checksums.json',
  );
});

test('export health report surfaces missing contracts and samples', () => {
  const validationSource = read('src/main/java/com/github/dcysteine/nesql/exporter/main/ExportValidationReportWriter.java');
  const renderAssetProbeSource = read('src/main/java/com/github/dcysteine/nesql/exporter/main/ExportValidationRenderAssetProbe.java');
  const healthPolicySource = read('src/main/java/com/github/dcysteine/nesql/exporter/main/ExportValidationHealthPolicy.java');
  const validationAbiCatalogSource = read('src/main/java/com/github/dcysteine/nesql/exporter/main/ExportValidationAbiCatalog.java');

  assert.equal(
    validationAbiCatalogSource.includes('Missing raw-export/assets/textures/index.jsonl.gz.'),
    true,
    'Health report catalog should warn when render-assets is absent',
  );
  assert.equal(
    healthPolicySource.includes('ExportValidationAbiCatalog.WARNING_RENDER_ASSET_MANIFEST_MISSING'),
    true,
    'Health policy should consume the catalog-owned render-assets warning',
  );
  assert.equal(
    validationAbiCatalogSource.includes('Missing raw-export NEI browser group/order streams.'),
    true,
    'Health report catalog should warn when browser layout is absent',
  );
  assert.equal(
    renderAssetProbeSource.includes('renderAssetMissingPrimaryArtifactSamples'),
    true,
    'Health report should include missing primary artifact samples',
  );
  assert.equal(
    renderAssetProbeSource.includes('renderAssetMissingTimelineFrameSamples'),
    true,
    'Health report should include missing timeline frame samples',
  );
});

test('export health report audits browser layout atlas residency', () => {
  const validationSource = read('src/main/java/com/github/dcysteine/nesql/exporter/main/ExportValidationReportWriter.java');
  const browserAtlasProbeSource = read('src/main/java/com/github/dcysteine/nesql/exporter/main/ExportValidationBrowserAtlasProbe.java');
  const healthPolicySource = read('src/main/java/com/github/dcysteine/nesql/exporter/main/ExportValidationHealthPolicy.java');
  const validationAbiCatalogSource = read('src/main/java/com/github/dcysteine/nesql/exporter/main/ExportValidationAbiCatalog.java');

  assert.equal(
    validationSource.includes('ExportValidationBrowserAtlasProbe.inspect(rawDir, report);')
      && browserAtlasProbeSource.includes('static void inspect(File rawDir, ExportValidationReportWriter.ValidationReport report)'),
    true,
    'Validation writer should delegate browser atlas coverage to the browser atlas probe',
  );
  assert.equal(
    browserAtlasProbeSource.includes('browserAtlasLayoutCoverageRatio'),
    true,
    'Health report should include atlas coverage ratio for layout-visible browser items',
  );
  assert.equal(
    browserAtlasProbeSource.includes('browserAtlasLayoutMissingSamples'),
    true,
    'Health report should include bounded missing atlas samples for export repair',
  );
  assert.equal(
    validationAbiCatalogSource.includes('Browser layout items missing atlas coverage'),
    true,
    'Health report catalog should warn when layout-visible items cannot draw from the atlas',
  );
  assert.equal(
    healthPolicySource.includes('ExportValidationAbiCatalog.browserLayoutMissingAtlasCoverageWarning'),
    true,
    'Health policy should consume the catalog-owned browser atlas warning',
  );
});
