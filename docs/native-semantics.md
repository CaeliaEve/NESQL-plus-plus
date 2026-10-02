# Consolidated repair: native magic semantics

Development version 0.16.0 writes source revision 15. It requires Compiler and
contract package 0.15.0 (catalog revision 14). This source version change is not
a game installation or a declaration that all adapters are complete.

## PreserveFilter

Automagy 0.28.2 copies the base output, serializes the first encountered filter
configuration and optionally takes metadata from a later filter. Missing second
filter retains base metadata. Missing all filters retains the fixed output.
The item and count always belong to the base recipe output. Serialization is
observed using copied stacks, not a merge of arbitrary configuration root tags.
The `configurations` facts follow the deduplicated configuration choices.
With a metadata input, divergent configuration results still need separate
branches; mixed filter/ordinary roles remain explicit unsupported cases.

## Impossible enhanced infusion ingredient

TC4Tweaks 1.5.39 RecipeIngredient.ERROR (`RecipeIngredient$5`) always rejects
matches. Its fire icon is a diagnostic placeholder. An impossible required input
excludes the recipe; an impossible OR branch is removed without losing valid
alternatives. Unknown predicates remain unsupported even inside an otherwise
satisfiable OR. The recipe checker retains the excluded native recipe index.

## AE2 crafting

The exact NEIAEShapedRecipeHandler and NEIAEShapelessRecipeHandler identities
read enabled recipes from CraftingManager in the native registry order. The
pinned ShapedRecipe/ShapelessRecipe implementations supply ingredient sets and
matching semantics; arbitrary subclasses are rejected. Each cell consumes one
item, ignores NBT as the native matcher does, and retains wildcard metadata.
Grid holes, dimensions, the mirror flag, shapeless identity, native output count
and crafting container returns are retained. Native NEI caches supply layout.

Those caches call setMaxSize on their inputs, so their recipe projection and
ingredient arrays are independent copies. The offline regression checks two
input alternatives and source immutability, as well as enabled filtering,
mirroring enabled/disabled, holes, NBT matching and output counts.

Run `nativeAeTest -PnativeTestJar=<derived-AE2.jar>`. Original
appliedenergistics2-rv3-beta-695-GTNH.jar SHA256:
`1601d33565f40478a073327ab50f76a127063b8917e3a192e302d54e0ac70a44`.
The colored-cable preference requires AE2's fully initialized API/block registry;
that branch and actual wildcard expansion remain live acceptance requirements.
Local conformance is not a claim that every installed AE2 recipe passed.

## Reproducing offline evidence

The acceptance coordinator optionally accepts `memory` with
`minimumFreeCommitBytes` (positive decimal string) and `maximumCommitPercent`
(integer 1..99). For the consolidated Windows batch use `4294967296` and `95`.
It samples system commit before each new check/export/compile, saves the pending
offset and a resource event, and exits nonzero if either limit is reached or
the counters cannot be read. Re-running the same plan can resume after recovery.
It never kills a running game job, changes the page file, or reuses a different
game environment. This is a batch dispatch guard, not continuous monitoring or
a proof of the previous OS Error 1450 cause. Keep the export agent's resource
monitor running while an individual long game job is in flight.

No client or world is launched. `scripts/remap-native-test.py` accepts an original
mod jar, ForgeGradle's `srg-mcp.srg`, and a **new** derived test-jar path. It emits
an original/mapping/derived SHA256 receipt. Derived jars remain under ignored
`build/native-tests`; do not install or distribute them.

Run `nativeFilterTest -PnativeTestJar=<derived-Automagy.jar>` and
`nativeInfusionTest -PnativeTestJar=<derived-TC4Tweaks.jar>` with Java 8.
The filter suite also writes `build/native-tests/filter.json`, reused by the
Compiler's native conformance test. Tested originals:

- Automagy-1.7.10-0.28.2.jar:
  `4c1d78e0adee1754675d93ffbadc8c9fe205710cb77538b953e57810f9528efc`.
- Thaumcraft4Tweaks-1.5.39.jar:
  `1b411a54c3fccdb3f7259dfa6bdd11bea8701e3f94515ef78245abd85c97c1f4`.

Normal `build` does not silently skip these as passing tests: these are explicitly
invoked suites requiring local original artifacts. All real-game acceptance
remains a separate export-agent step with a new immutable candidate receipt.

# Additional native machine and crafting registries (0.17.0)

These eleven identities have production adapters and local native conformance evidence, **not live acceptance**. Source remains revision 15; Catalog remains revision 14. Existing GT/AE2/Thaumcraft semantics are unchanged.

- IC2 2.2.828-experimental: macerator, extractor, compressor, metal former cutting/rolling/extruding, thermal centrifuge. `MachineRecipeHandler.getRecipeList()` supplies the actual machine registry. Exact `RecipeInputItemStack` and `RecipeInputOreDict` predicates ignore NBT, retain input quantities and ore meta overrides. The private `CachedIORecipe` receives copies only. Unknown predicates/metadata fail explicitly. Duration and EU/t are the native unupgraded machine baselines (300/2, 200/10, 500/48); centrifuge minimum heat is retained. Other IC2 machines do not inherit this adapter. The reviewed 0.17.1 candidate captures the native foreground at steady-state tick 20 (energy/heat indicators full, progress empty), restoring the clock even on failure; the native 20-tick progress texture cycles independently. Startup warmup is not replayed on each cycle. Pixel/animation correctness still requires live visual acceptance.
- Et Futurum 2.6.2.25-GTNH: smoker and blast furnace. The reviewed 0.17.1 candidate resolves each concrete NEI input candidate through the native blacklist, custom `ItemStackMap` (including wildcard precedence), then the first matching inherited furnace recipe and native `canAdd`. It keeps exact metadata matching after expansion so removed/overridden subtypes cannot reappear through a wildcard rule. Lookup does not mutate the native result cache; inherited keys are indexed per item rather than rescanning the full registry per candidate. Avoid the handler's repeated `basecache.addAll(arecipes)` allocation. Retain 100 tick processing, output quantities and experience. Fuel is not a result. Reuse the native furnace background and flame/arrow tracks.
- Avaritia 1.77: exact extreme shaped, shaped ore, shapeless and Forge shapeless ore recipes from `ExtremeCraftingManager`. Preserve 9x9 grids, holes, mirror flags and one-item consumption per cell. Direct Avaritia stacks with tags require those tags; ore-list branches and Forge shapeless ignore them. Empty ore predicates are impossible and excluded. Unknown overrides fail explicitly. Native layout caches receive copied stacks.

`RegistryRecipes` only shares cursor lifetime/dispatch; each adapter owns its semantics. No arbitrary `TemplateRecipeHandler`, `IRecipe` or subclass gains support.

Optional local conformance reuses the isolated `GameTest` bootstrap, at 256 MiB:

```text
gradlew.bat nativeMachinesTest -PnativeTestJar=build/native-tests/ic2.jar -PnativeExtraTestJar=build/native-tests/etfuturum.jar -PnativeCraftingTestJar=build/native-tests/avaritia.jar
```

Derive each test jar with `scripts/remap-native-test.py` using the same MCP mapping as existing native tests. Original SHA256: IC2 `de1d4597972be036eccd1c3b37e9980c3c9d9cdb92f52df2bf470971873893f6`; Et Futurum `4925bb676e66a43d50c84f6f8e627fdc486e062b84e5e6b963e124b24840701a`; Avaritia `ef4ad2efcbbe88dfa2673921781988781f106f52a0be4e1d80edcfeb0609f656`. Derived jars stay under ignored `build/native-tests`, never distributed or installed. Standard CI runs the existing build/bridge/GL checks; this optional suite requires the original local jars.

## Forestry factories (consolidated development, not a released/live result)

Forestry 4.10.17 centrifuge and still enumerate their `RecipeManagers` registries.
Only the exact native `CentrifugeRecipe` and `StillRecipe` implementations are
adapted; unknown implementations fail explicitly. Owned native cache projections
receive copied items, fluids and maps. Static foreground/tanks and the native
progress texture are captured separately; actual GL appearance remains untested
until the consolidated live run.

- Centrifuge consumes one input in `TileCentrifuge.workCycle`, independently of
  the display count. `ItemStackUtil.isCraftingEquivalent` ignores input NBT only
  when the recipe's tags are absent/empty, and honors wildcard metadata. Duration
  is `getProcessingTime`, with baseline energy `time * 160` RF. Independent output
  rolls retain native float probabilities (not GT's 1/10000 grid); NaN/negative
  means never, at least one means always. All products survive the nine-slot NEI
  limit. Canonical item identity/count/probability order stabilizes recipe facts;
  separate duplicate rolls remain separate. Display association follows native
  descending probability so a rare hidden duplicate cannot take a guaranteed
  duplicate's visible slot.
- Still's API amounts are per cycle: the machine drains `input * cycles` and
  produces `output * cycles`. Duration is cycles, baseline energy `cycles * 200`
  RF. Retain exact fluid NBT and reject overflowing native batch arithmetic.
  RF is a named property; the EU/t field is not reused for another energy unit.

Regression command (same existing lightweight native entry point):

```text
gradlew.bat nativeMachinesTest -PnativeFamily=forestry -PnativeTestJar=build/native-tests/forestry.jar
```

Original `Forestry-4.10.17.jar` SHA256:
`b537738e29c242726ce7f356985bfd21214f7c8948ea0526e951dbe2f419a3fd`.
Real-jar tests cover one-item consumption, required tags, rare probability,
hidden/duplicate outputs, map-order invariance, source ownership, scaled fluid
amounts, overflow and rejection of unknown recipe implementations. Tests for the
shared reflection boundary also ensure native cancellation, fatal faults and
Errors retain their identity instead of becoming ordinary recipe failures.

## TConstruct melting and alloying (consolidated development)

The exact TConstruct 1.13.57-GTNH handlers read `Smeltery` registries, with
Mantle 0.5.1 metadata keys. Melting consumes one item (native inventory limit),
ignores NBT and matches exact metadata, including a literal 32767 key. NEI's
generic wildcard expansion must not broaden that predicate. Retain fluid amount,
NBT and melting temperature; fuel-dependent heating is not a constant duration.

Exact `AlloyMix` recipes retain per-unit fluid ratios and the native maximum
integer batch policy. `mix()` matches fluid and NBT; `SmelteryLogic` merges equal
fluid stacks, so repeated equal ingredients cannot be represented as a normal
matchable alloy. Such malformed recipes fail explicitly. Empty mixers are native
NEI exclusions. Native tank projections use deep copies; no game registry amount
is modified by display construction. No arbitrary `AlloyMix` subclass is trusted.
Both handlers retain native backgrounds, fluid tanks and melting temperature
text; these handlers contain no progress animation to replace. Native rendering
still needs consolidated live validation.

Run `nativeMachinesTest -PnativeFamily=tconstruct
-PnativeTestJar=build/native-tests/tconstruct.jar
-PnativeExtraTestJar=build/native-tests/mantle.jar`. Tests compare native mixing
at three batches (including remainders and NBT rejection), input/output ratios,
owned display stacks, temperature, one-item consumption and literal 32767 keys.
Original SHA256: TConstruct `f2cb53b94f135b7523bcb7afea5ef53b9afb40c0bc034fc5ec5018ad0502bc61`;
Mantle `6a48d327d3442235a697b1cb57447e50e136374706c7a650262a533f16edd360`.

## IC2 ore washing and exact-match encoding

Ore washing now reads `Recipes.oreWashing` through the pinned machine handler.
`TileEntityOreWashing` uses 500 ticks and 16 EU/t, and drains the output metadata's
`amount` of water per operation. Water admission checks its registry ID only, so
the fluid choice ignores NBT. A nonpositive/absent native amount consumes no water;
it must not become a keep-one fluid requirement. Null metadata cannot operate and
is rejected. Ordinary item predicates, multiple outputs and copied metadata reuse
the existing reviewed IC2 adapter. Preserve native background/tank/labels and its
20-tick progress track; do not invoke the mouse tooltip's live GuiRecipe lookup
during offscreen rendering. Test coverage remains in `nativeMachinesTest`.

The same development pass corrects required-NBT Forestry/Avaritia inputs to emit
`kind: exact` when neither metadata nor NBT is ignored. A wildcard with both flags
false violates the existing Compiler contract. Native matching is unchanged;
regressions require the valid exact representation as well as native behavior.

## TConstruct table and basin casting

Read the native table/basin `LiquidCasting` registry, reject unknown recipe
subclasses, and retain exact fluid/NBT, cooling time, output quantity and mould
consumption. A wildcard mould ignores NBT/count in the native early match branch;
an ordinary exact mould needs exactly one item because the machine's slot limit
is one. `ignoreNBT` preserves metadata. Null mould with `ignoreNBT` and nonpositive
cooling time cannot complete natively and are excluded. The empty-mould condition
is explicit for cast-free recipes. Preserve native layouts and fluid flow artwork.

The native machine posts `SmelteryCastEvent` (may deny) and `SmelteryCastedEvent`
(may change consumption/output). Capture runs the actual active listener snapshot
on deep copied recipe/event data, after validating **every** callback. Allow only
the reviewed TConstruct `WeaponryHandler.weaponryPartCast` and Iguana 2.6.6
`PartRestrictionHandler.onPartCasting` / `CastHandler.onCasted`. Verify the exact
target class and FML's Method-to-generated-wrapper cache, not a method-name string
alone. Unknown callbacks fail before any listener runs. Callback Errors/faults
propagate normally. Original recipes, stacks and global event registration remain
unchanged. Full initialized material restrictions still need real-game acceptance.

The existing TConstruct native test command now additionally takes
`-PnativeCraftingTestJar=build/native-tests/iguana.jar`. Its isolated classloader
uses FML's native event transformer. Tests cover both casting layouts, wildcard
and exact moulds, unchanged source objects, a real registered Iguana callback that
consumes the mould, and refusal to run any callback when an unknown listener is
present. Iguana original SHA256:
`a46788d0d3d94e1c0d455dd937b42cd80434d894fa07da4671fbebffe42c975a`.

## NASA workbenches (Galacticraft 3.3.13 / GalaxySpace 1.1.121)

`SpaceRecipes` reads GalaxySpace `RocketRecipes` for tiers 1–8 and
`GalacticraftRegistry` for buggy, cargo rocket and Astro Miner. Only the exact
`NasaWorkbenchRecipe` implementation is admitted. Native `matches` ignores count
and NBT, requires explicit null slots empty, and accepts metadata 32767 on the
**offered** stack, not on the recipe template. Each predicate is intersected with
the native physical slot's `isItemValid`: ordinary GalaxySpace slots enforce exact
item/meta, cargo slots have disabled positions, and chest slots restrict grades.
Source counts are not consumption: `SlotRocketBenchResult` consumes one item per
occupied slot and returns `new ItemStack(item.getContainerItem())`. It does not
use Forge's stack-sensitive container getter. Unadapted stack-sensitive container
presence callbacks fail explicitly. Exact first-match shadowing is excluded;
wildcard alternatives with conditional earlier-recipe overlap fail explicitly.

An empty owned native container supplies slot indices/coordinates and predicates.
All registry recipe classes are checked before its constructor can query them.
Its player inventory is fresh; the capture never installs the container, populates
its matrix, clicks, closes it, or invokes its pickup/network methods. Inaccessible
or unconstrained usable slots fail instead of being silently discarded. Native
indices remain input slot numbers; required empty positions are retained in
`galacticraft:emptySlots`. The native NEI background is preserved and cropped
coordinates come from the real workbench. This also avoids GalaxySpace's native
NEI chest-list aliasing: that display list is not the source of crafting facts.

Existing `nativeMachinesTest -PnativeFamily=space` takes
`-PnativeTestJar=build/native-tests/galacticraft.jar` and
`-PnativeExtraTestJar=build/native-tests/galaxyspace.jar`. It covers all eleven
native container constructors, slot counts, matcher boundaries, consume-one,
container return, output counts and display ownership. The isolated loader uses
FML's own annotation parser/`ModAPITransformer` for absent optional APIs; it does
not replace the NASA predicates. No game world, GL or full initialized registry
coverage is claimed. Original JAR SHA256:
- Galacticraft: `e382339878a3dea2ab9ccb359bc37096f129802fff4e3a88de8db9fb7e1d4fa9`
- GalaxySpace: `bb0ccf7f54cd73cee83ea8a8ce9e63e1d20ba754a5fb4ebb342d1e81498b7ca8`

## Galacticraft / AmunRa circuit fabricator

`CircuitRecipes` snapshots the actual `CircuitFabricatorRecipes` registry in its
native encounter order. Five fixed inputs use exact item/metadata (including a
literal 32767), ignore NBT/count, and retain required empty slots. Only the first
entry for a matching tuple can operate. AmunRa's category is selected by its
native `RecipeHelper` registration object references after precedence is applied;
it never exports unregistered combinations from the NEI grouped display. The GC
category covers the full runtime machine registry, including add-on recipes.
Actual slot admission is checked before capture. Removed registrations remain
absent; unknown AmunRa registration classes fail explicitly.

`TileEntityCircuitFabricator.compressItems` consumes one per occupied input and
does not return containers. Output NBT/count is retained, including quick-mode
wafer metadata 13 => 5 and 14 => 2. Duration is 300 ticks. The native hard-mode
energy extraction setting (20/40 gJ per tick) is an explicitly labelled property,
not an EU quantity. Owned snapshots and display copies do not mutate the registry.
The native background is captured with its clock at zero and restored on error;
three clipped texture layers retain the actual 70-tick width/texture-phase cycle.

`nativeMachinesTest -PnativeFamily=circuits` with Galacticraft in `nativeTestJar`
and AmunRa in `nativeExtraTestJar` verifies native matching, actual machine
consumption/quick output on a fresh inventory, precedence, AmunRa linkage, owned
stacks, empty slots, admission, and every animation tick. No world or GL context
is used. Full runtime coverage/visual acceptance is still pending the unified
export. AmunRa 0.8.2 original JAR SHA256:
`bcd0ca636d545a0f361f6583fd667bd40ca4b79198fc06d1552038310a7b1978`.

The same native test family also covers AmunRa's shuttle. `SpaceRecipes` reads
`RecipeHelper.getAllRecipesFor(ARItems.shuttleItem)`, verifies exact native NASA
recipes, and constructs the owned 21-input `ContainerSchematicShuttle`. Its
`ItemDamagePair` predicates require exact metadata (and compare blocks for block
items); a slot with an empty type set permits any item but does not remove an
explicit recipe-empty condition. The existing native matcher, precedence,
consume-one and legacy container-return rules are reused. Coordinates use the
shuttle's unshifted Y layout and X minus four. Unknown slot predicate classes or
usable slots absent from the recipe fail explicitly. The native background is
unchanged. No shuttle inventory is filled or linked to a player's live container.
