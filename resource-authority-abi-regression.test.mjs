import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import test from "node:test";

const root = path.dirname(new URL(import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, "$1"));
const read = (relative) => fs.readFileSync(path.join(root, relative), "utf8");

test("facade authority uses runtime API and exact database stack identity", () => {
  const source = read("src/main/java/com/github/dcysteine/nesql/exporter/local/RawExportFacadeResolutionWriter.java");
  assert.match(source, /FacadeAPI\.facadeItem/);
  assert.match(source, /getBlocksForFacade\(facadeStack\)/);
  assert.match(source, /getMetaValuesForFacade\(facadeStack\)/);
  assert.match(source, /Item\.getItemById\(item\.getItemId\(\)\)/);
  assert.match(source, /JsonToNBT\.func_150315_a\(nbtText\)/);
  assert.match(source, /IdUtil\.itemId\(sourceStack\)/);
  assert.match(source, /ResourceAuthorityContract\.canonicalItemId/);
  assert.match(source, /ResourceAuthorityContract\.canonicalItemAssetId/);
  assert.doesNotMatch(source, /localizedName|getLocalizedName/);
});

test("animation ABI is physical-frame gated and manifest-addressable", () => {
  const extractor = read("src/main/java/com/github/dcysteine/nesql/exporter/util/render/NativeSpriteMetadataExtractor.java");
  const collector = read("src/main/java/com/github/dcysteine/nesql/exporter/local/CanonicalRenderAssetCollector.java");
  const writerSupport = read("src/main/java/com/github/dcysteine/nesql/exporter/main/ExportWriterSupport.java");
  const renderStages = read("src/main/java/com/github/dcysteine/nesql/exporter/main/RenderStageActionProvider.java");
  const renderContracts = read("src/main/java/com/github/dcysteine/nesql/exporter/main/RenderContractsExporter.java");
  const catalog = read("src/main/java/com/github/dcysteine/nesql/exporter/local/RawExportFileCatalog.java");
  const writer = read("src/main/java/com/github/dcysteine/nesql/exporter/local/RawExportAnimationFrameMaterializationWriter.java");
  assert.match(extractor, /materializedFrameCount/);
  assert.match(extractor, /distinctFrameCount/);
  assert.match(extractor, /materializationStatus/);
  assert.match(collector, /ResourceAuthorityContract\.isNativeSpriteAnimation/);
  assert.match(collector, /backfillLegacyNativeSpriteMaterialization/);
  assert.match(collector, /inspectVerticalAtlas/);
  assert.match(collector, /canonicalImageFilePath/);
  assert.match(collector, /expectedPrefix = "i~"/);
  assert.match(collector, /expectedPrefix = "f~"/);
  assert.match(collector, /Render asset collection requires a live EntityManager/);
  assert.match(collector, /public static List<CanonicalRenderAsset> collectFromExportedFiles/);
  assert.doesNotMatch(collector, /falling back to exported file scan/);
  assert.match(
    writerSupport,
    /collectRenderAssets\(\s*EntityManager entityManager,\s*File repositoryDirectory\)/,
  );
  assert.match(writerSupport, /new CanonicalRenderAssetCollector\(entityManager, repositoryDirectory\)\.collectAll\(\)/);
  assert.match(writerSupport, /collectRenderAssetsFromFiles\(File repositoryDirectory\)/);
  assert.match(writerSupport, /CanonicalRenderAssetCollector\.collectFromExportedFiles\(repositoryDirectory\)/);
  assert.doesNotMatch(writerSupport, /new CanonicalRenderAssetCollector\(null/);
  assert.match(
    renderStages,
    /collectRenderAssets\(\s*context\.stageState\.runtime\.entityManager,\s*context\.exportContext\.paths\.repositoryDirectory\)/,
  );
  assert.match(renderContracts, /collectRenderAssetsFromFiles\(exportPaths\.repositoryDirectory\)/);
  assert.match(catalog, /facadeResolutions/);
  assert.match(catalog, /animationFrameMaterializations/);
  assert.match(catalog, /frame-materializations\.jsonl\.gz/);
  assert.match(writer, /row\.add\("atlasFile", atlasFile\)/);
  assert.doesNotMatch(writer, /row\.add\("nativeSpriteAtlasFile"/);
});
