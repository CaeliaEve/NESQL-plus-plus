import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';

const repoRoot = process.cwd();
const readSource = (relativePath) => readFileSync(join(repoRoot, relativePath), 'utf8').replace(/^\uFEFF/, '');

const abi = readSource('src/main/java/com/github/dcysteine/nesql/exporter/nativeui/NativeUiExportAbi.java');
const rawNeiFactWriter = readSource('src/main/java/com/github/dcysteine/nesql/exporter/local/RawExportNeiFactWriter.java');
const uiFamilyCensusWriter = readSource('src/main/java/com/github/dcysteine/nesql/exporter/local/RawExportUiFamilyCensusWriter.java');
const templateLayoutSpecs = readSource('src/main/java/com/github/dcysteine/nesql/exporter/plugin/nei/metadata/NeiUiTemplateLayoutSpecs.java');
const nativeFrameRegistry = readSource('src/main/java/com/github/dcysteine/nesql/exporter/nativeui/NativeNeiFrameExportRegistry.java');
const nativeFrameRenderer = readSource('src/main/java/com/github/dcysteine/nesql/exporter/util/render/NativeNeiFrameRenderer.java');
const neiRecipeExportProcessor = readSource('src/main/java/com/github/dcysteine/nesql/exporter/plugin/nei/NeiRecipeExportProcessor.java');
const handlerMetadata = JSON.parse(readSource('src/main/resources/nesql/nei/handler-metadata.json')).entries;

function valueText(value) {
  return `${value ?? ''}`.trim();
}

function parseIntOr(value, fallback) {
  const parsed = Number.parseInt(valueText(value), 10);
  return Number.isFinite(parsed) ? parsed : fallback;
}

function descriptor(entry) {
  return `${valueText(entry.handler)} ${valueText(entry.itemName)} ${valueText(entry.modId)}`.toLowerCase();
}

function classifyFamily(entry) {
  const text = descriptor(entry);
  if (text.includes('shaped') || text.includes('shapeless') || text.includes('crafting')) return 'crafting-table';
  if (text.includes('furnace') || text.includes('smelting')) return 'furnace';
  if (text.includes('brewing')) return 'brewing';
  if (text.includes('gregtech') || text.includes('gt.')) return 'gregtech-machine';
  if (text.includes('thaum') || text.includes('arcane') || text.includes('crucible') || text.includes('infusion')) return 'thaumcraft';
  if (text.includes('botania') || text.includes('mana')) return 'botania';
  if (text.includes('fluid') || text.includes('liquid') || text.includes('chemical')) return 'fluid-machine';
  return 'native-nei';
}

function inferLayoutKind(entry, family) {
  const text = `${valueText(entry.handler)} ${valueText(entry.itemName)} ${family}`.toLowerCase();
  if (text.includes('crafting') || text.includes('shaped') || text.includes('shapeless')) return 'crafting-grid';
  if (text.includes('furnace') || text.includes('smelting')) return 'furnace';
  if (text.includes('fluid') || text.includes('liquid') || text.includes('chemical')) return 'fluid-machine';
  if (text.includes('gregtech') || text.includes('machine')) return 'machine';
  return 'native-nei';
}

function slots(layoutKind) {
  if (layoutKind === 'crafting-grid') return [
    { role: 'item-input', columns: 3, rows: 3, x: 30, y: 12 },
    { role: 'item-output', columns: 1, rows: 1, x: 124, y: 30 },
  ];
  if (layoutKind === 'furnace') return [
    { role: 'item-input', columns: 1, rows: 1, x: 45, y: 24 },
    { role: 'item-output', columns: 1, rows: 1, x: 115, y: 24 },
    { role: 'fuel', columns: 1, rows: 1, x: 45, y: 46 },
  ];
  if (layoutKind === 'fluid-machine') return [
    { role: 'item-input', columns: 3, rows: 2, x: 18, y: 16 },
    { role: 'fluid-input', columns: 3, rows: 2, x: 72, y: 16 },
    { role: 'item-output', columns: 3, rows: 2, x: 126, y: 16 },
  ];
  if (layoutKind === 'machine') return [
    { role: 'item-input', columns: 3, rows: 3, x: 18, y: 12 },
    { role: 'fluid-input', columns: 1, rows: 3, x: 76, y: 12 },
    { role: 'item-output', columns: 2, rows: 2, x: 112, y: 21 },
  ];
  return [
    { role: 'item-input', columns: 3, rows: 2, x: 24, y: 18 },
    { role: 'item-output', columns: 2, rows: 2, x: 116, y: 20 },
  ];
}

function slotBounds(slot) {
  const slotSize = 18;
  const pitch = 18;
  return {
    right: slot.x + Math.max(0, slot.columns - 1) * pitch + slotSize,
    bottom: slot.y + Math.max(0, slot.rows - 1) * pitch + slotSize,
  };
}

test('native UI background ABI has semantic canonical non-GT surface contract', () => {
  assert.match(abi, /BACKGROUND_STATUS_SEMANTIC = "semantic"/);
  assert.match(abi, /BACKGROUND_KIND_CANONICAL_NEI_TEMPLATE = "canonical-nei-template"/);
  for (const source of [rawNeiFactWriter, uiFamilyCensusWriter]) {
    assert.match(source, /BACKGROUND_STATUS_SEMANTIC/);
    assert.match(source, /BACKGROUND_KIND_CANONICAL_NEI_TEMPLATE/);
    assert.match(source, /captureRequired", false|captureRequired = false/);
    assert.doesNotMatch(source, /BACKGROUND_STATUS_MISSING/);
    assert.doesNotMatch(source, /BACKGROUND_KIND_UNKNOWN/);
  }
});

test('native NEI frame export is the required recipe display authority', () => {
  assert.match(abi, /NATIVE_NEI_FRAMES_DIRECTORY = "assets\/nei-native-frames"/);
  assert.match(abi, /NATIVE_FRAME_SOURCE_IN_GAME_NEI_RENDER = "in-game-nei-render"/);
  assert.match(nativeFrameRegistry, /nativeFrame\.put\("handlerClass", handler\.getClass\(\)\.getName\(\)\)/);
  assert.match(nativeFrameRegistry, /nativeFrame\.put\("recipeIndex", recipeIndex\)/);
  assert.match(nativeFrameRenderer, /request\.getHandler\(\)\.drawBackground\(request\.getRecipeIndex\(\)\)/);
  assert.match(nativeFrameRenderer, /GuiContainerManager\.drawItem\(stack\.relx, stack\.rely, stack\.item\)/);
  assert.match(nativeFrameRenderer, /request\.getHandler\(\)\.drawForeground\(request\.getRecipeIndex\(\)\)/);
  assert.match(nativeFrameRenderer, /installRecipeScreenContext\(request, width, height\)/);
  assert.match(nativeFrameRenderer, /Minecraft\.getMinecraft\(\)\.currentScreen = previousScreen/);
  assert.match(nativeFrameRenderer, /instantiateRecipeScreen\(\s*GuiCraftingRecipe\.class/);
  assert.match(nativeFrameRenderer, /instantiateRecipeScreen\(\s*GuiUsageRecipe\.class/);
  assert.match(nativeFrameRenderer, /recipeScreenClass\.getDeclaredConstructor\(ArrayList\.class\)/);
  assert.match(nativeFrameRenderer, /catch \(NoSuchMethodException ignored\)/);
  assert.match(nativeFrameRenderer, /parameterTypes\[i \+ 1\] = argument instanceof Boolean \? Boolean\.TYPE : argument\.getClass\(\)/);
  assert.match(nativeFrameRenderer, /tryLimitToOneRecipe\(recipeScreen\)/);
  assert.match(nativeFrameRenderer, /getMethod\("limitToOneRecipe"\)\.invoke\(recipeScreen\)/);
  assert.match(nativeFrameRenderer, /prepareCachedRecipeForNativeDraw\(request\)/);
  assert.match(nativeFrameRenderer, /PurificationUnitParticleExtractorFrontend/);
  assert.match(nativeFrameRenderer, /ensureListFieldSize\(recipe, "mInputs", 2\)/);
  assert.match(nativeFrameRenderer, /readFieldValue\(request\.getHandler\(\), "frontend"\)/);
  assert.match(neiRecipeExportProcessor, /registerNativeFrameMetadata\(builtRecipe, handler, i\)/);
  assert.match(neiRecipeExportProcessor, /Native NEI frame export did not produce nativeFrame ABI/);
});

test('native UI template surfaces are expanded to contain every default slot in bundled metadata', () => {
  assert.match(templateLayoutSpecs, /boundedSurfaceWidth/);
  assert.match(templateLayoutSpecs, /boundedSurfaceHeight/);
  const violations = [];
  for (const entry of handlerMetadata) {
    const family = classifyFamily(entry);
    const layoutKind = inferLayoutKind(entry, family);
    const layoutSlots = slots(layoutKind);
    const minWidth = Math.max(1, ...layoutSlots.map((slot) => slotBounds(slot).right));
    const minHeight = Math.max(1, ...layoutSlots.map((slot) => slotBounds(slot).bottom));
    const width = Math.max(parseIntOr(entry.handlerWidth, 166), minWidth);
    const height = Math.max(parseIntOr(entry.handlerHeight, 65), minHeight);
    for (const slot of layoutSlots) {
      const bounds = slotBounds(slot);
      if (bounds.right > width || bounds.bottom > height) {
        violations.push({ handler: entry.handler, layoutKind, slot: slot.role, bounds, surface: `${width}x${height}` });
      }
    }
  }
  assert.deepEqual(violations, []);
});
