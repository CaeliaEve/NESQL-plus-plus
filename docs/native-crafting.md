# Native crafting migration

The development shaped/shapeless route now reads the original `CraftingManager`
list, before the Template factory. It does not call the NEI loader, create
`CachedShapedRecipe`, compute visuals or recover cells from `relx/rely`.

For vanilla and Forge definitions whose matching/result/size/input methods are
the audited native implementations, it retains encounter order, logical width
and height, every empty cell, native mirroring, shapeless multiplicity,
consume-one semantics, ignored-NBT/exact-or-wildcard metadata predicates,
container returns and original result quantity/NBT. Metadata enumeration still
uses the pinned `PositionedStack` helper; its coordinates are never consumed.
The enumerated specimens and container results are also drift-checked.

`RecipesMapExtending` is a dedicated native case. Its zero-count declaration is
not a finished item. The result is observed on an owned inventory, preserving
the offered filled map and adding `map_is_scaling`. The existing mapScaling
process retains world-dependent eligibility and final ID allocation. Capture
never calls matches/getMapData/onCreated for this case.

Registration equivalence binds the same manager/list within this process. Each
record checks its original native index and complete definition before/after
capture; sealing verifies the full list order and all selected definitions.
Ready empty and unavailable are distinct. Closing only drops owned snapshots.
This is not a cross-session/version/locale reuse proof.

## Vanilla tagged result

The installed Minecraft bytecode confirms `field_92101_f` copies every tagged
offered inventory cell in row-major order, without breaking. The LAST tagged
cell wins, including a present empty compound. This differs from GT's FIRST
tagged input. With no tagged input the base result's tags remain unchanged.
Horizontal mirroring can change the winning cell; translating the grid cannot.

The dedicated `craftingNbt` process and action retain the original base item,
metadata and count, and whole-compound replacement. Samples are the untagged
base followed by independent native projections for each exported input choice.
They are linear in candidate count, not a Cartesian matching table. Each call
uses an owned inventory and copied stack; no matches/world/GUI callback runs.
Compiler validates every projection against raw typed NBT. NeoNEI chooses the
last tagged current candidate in the displayed orientation. Empty compounds
are distinct from absent tags. A result projection is not a claim that the
remaining exported candidates can form a complete matching tuple.

## Installed GT result process

The exact installed GT shaped/shapeless classes now use the typed `gtCrafting`
process, rather than a fixed superclass result. See
[native-gt-crafting-stages.md](native-gt-crafting-stages.md) for concrete callback
bindings, native normalization/electric/enchantment stages, bounded state
coverage, candidate scope, untagged representatives and reader-sealing checks.
Unknown subclasses or callback owners still require a dedicated adapter before
they can execute. Local implementation is not whole-pack crafting acceptance.

The small native tests use real Minecraft/Forge matching with owned definitions
and inventories, including a controlled metadata panel. They do not establish
live-pack counts, graphics, localization, global crafting-manager precedence,
or production performance. Compiler family checks and actual Java transport
fixtures verify the fixed-result, pending-map and vanilla tag-transfer boundary.
