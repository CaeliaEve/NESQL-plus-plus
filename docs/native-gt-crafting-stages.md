# Installed GT crafting result stages

`GtElectricBindings`, `GtCraftingResult` and `GtToolConsumption` target installed
GT 5.09.51.482 and IC2 2.2.828. Current upstream GT source has different electric
input exclusions; the installed JAR remains the authority.

The concrete result stages are: copy the parent result, native normalization,
FIRST-tagged whole-compound inheritance, first matching tagged shapeless input
overwrite with output-root keys winning, clear native output charge, simulate
all offered inputs, signed Java-int accumulation, charge a positive sum, ordered
native enchantment additions, and native normalization again.

Electric bindings preserve actual offered count. They bind the gateway and raw
or special manager, audited callback owners, IC2 configuration and battery
variants, GT stat table and array instances, and tool metadata/normalization
dependencies. No manager is required for a native inactive/non-electric branch.
Input discharge ignores external provision restrictions, as the installed
crafting callback does. Output item and metadata transitions are retained.

Every native operation uses owned stacks. IC2 armor additionally writes its
item-owned `allowDamaging` ThreadLocal; the caller's prior value is restored,
including on failures. The first native failure stays primary, with post-call
configuration drift retained as a suppressed exception. Unknown owners fail
before invocation. These bindings are same-process concrete-state observations,
not cross-restart/version reuse proofs.

`GtCraftingResult.evaluate` is a bounded concrete specimen evaluator. It binds
the reached stages before executing the original callback on an independent
owned inventory and compares complete result facts. It does not call matching,
world/player methods, tool container wear or RNG. A specimen does not assert
crafting-manager first-match reachability or arbitrary-input equivalence.

Native tool consumption is now connected to `NativeCraftingRecipes` through
the explicit `gtTool` contract. It never samples an electric tool's 1/25 wear
draw. The 43 installed tool classes were reviewed: 24 declared constant crafting
cost owners and two null broken-result owners. Normalization remains separately
bound, and unknown overrides are rejected.

`GtCraftingCapturePlan` now prepares bounded concrete result states. Shaped
recipes use logical cells and native mirroring; shapeless recipes cover offered
permutations through subset states, preserving native first-remaining greedy
allocation. The state retains first inherited tags, first overwrite tags and
signed-int transfer. Native NBT equality admits signed zeros without merging
their distinct result identities. Limits are 65536 candidates/states and
8388608 transitions per placement search; excess fails explicitly.

`GtCraftingTransport` and the typed `gtCrafting` process/action are connected to
`NativeCraftingRecipes`. Compiler independently checks matching, witness
positions, all finite result-state coverage, count and item availability. Native
normalization/electric callback outputs remain audited native observations,
not a second implementation of Java or a result function for arbitrary player
tags/count/charge. The contract explicitly scopes them to exported candidates.
NeoNEI selects by current candidates and actual order, supports mirror/reorder,
and shows an unmatched combination without inventing a result.

Keeping-NBT templates can declare unequal tags even though the matcher ignores
those template tags. Their original candidates remain present; owned untagged
representatives are also admitted by the exact matcher. These provide a valid
neutral witness without declaring the recipe unreachable or changing native
templates. Original and generated specimens participate in definition checks.

`GtCraftingBindings` coalesces mutable concrete native dependencies by item
identity and specimen until reader sealing, after each short-lived plan closes.
Constant identity/non-electric branches retain nothing. It does not retain full
inventories/plans/result traces, and has a 262144-binding reader budget. Repeated
bindings recheck earlier dependencies; sealing rechecks the complete collection.

The local route is implemented; unified candidates, whole-pack counts, native
pixels, production performance and final export handoff remain separate work.
