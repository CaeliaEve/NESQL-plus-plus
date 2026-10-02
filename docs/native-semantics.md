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
