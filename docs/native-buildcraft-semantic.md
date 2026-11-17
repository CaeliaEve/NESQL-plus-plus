# BuildCraft assembly and refinery semantic readers

The pinned BuildCraft 7.1.44 / Compat 7.1.18 readers open before the Template
factory and emit semantics directly. Neither creates a display proxy,
CachedAssemblyTableRecipe, CachedRefineryRecipe or GUI tanks; neither reads or
modifies `arecipes`. Assembly uses PositionedStack only to enumerate bounded
native metadata candidates. The former twelve-slot display limit no longer
affects exported logical requirements.

Assembly binds the machine's `AssemblyRecipeManager.INSTANCE` to the active
API manager and its actual HashMap. It preserves native map order, recipe
identity, fixed-before-alternative allocation, greedy failures, the first
alternative's required count (including ignored later example counts), native
wildcard/NBT/subtype matching and laser-energy budget. A recipe requiring fluid
has a native no-fluid-inventory exclusion proof; its irrelevant item callbacks
are not invoked. No fixed runtime, RF/t or container returns are invented.

Refinery binds `RefineryRecipeManager.INSTANCE`, the active API manager,
its native HashMap, tank capacity and both native fill lists. It preserves
duplicate fluid requirements, map priority, preview versus partial completion,
energy/delay and retained fill eligibility after a recipe is removed. The fill
lists are deliberately not rebuilt from current recipes. Nonpositive energy
retains the native machine's exclusion proof.

Both readers check manager/storage/count and current record definitions around
capture. Sealing checks all definitions and native selection order; refinery
also checks every fill-template identity, position and complete facts. Its
per-batch shared check checks fill list identity/count, while full fill-content
verification occurs at sealing to avoid rescanning a large fill list per row.
Changed or uncertain definitions cannot produce a sealed successful Source.
Close only releases owned state; ready-empty and unavailable sources differ.
Unknown recipe classes and unreviewed item metadata/subtype callbacks fail
explicitly. Assembly signatures retain raw examples plus concrete wildcard
expansions, without discarding the ignored counts.

The existing native BuildCraft runner checks actual FlexibleRecipe matching,
allocation and refinery tank behavior alongside GUI-cache sentinels,
configuration/definition/table/fill drift, independent registration instances,
ready-empty and close. Fixture API fields, singleton storage, Loader versions
and modified facts are restored in finally. These are controlled local native
API tests; game execution, native pixels, complete locale coverage, whole-pack
performance and final release acceptance remain separate work.

The separate integration-table reader has its own audit in
`native-buildcraft-integration.md`. These local reader checks do not certify
all native information, arbitrary third-party callbacks or whole-pack acceptance.
