# Native infusion tool-query effects

This is implementation documentation for an unvalidated batch. No game,
native-method parity, build, contract generation or product test is claimed.

The installed TC4Tweaks 1.5.39 `shouldModify` checks the central item, then the
output, then the central nonempty NBT. These are observable ordered calls.
`Products` now emits `nativeInfusionMerge` instead of using the historical
`merge` action for this current producer path. The base, root whitelist and
tool-only switch remain explicit. A per-center invocation records the input
query and only an output query which actually runs. False input eligibility
short-circuits; false output eligibility still retains that output query's
owned tag/metadata edits. Filtered inheritance into an untagged output fails
only when execution actually reaches the merger.

BloodMagic's spell tool creates an empty compound when untagged and reads the
three `BloodMagicTool/harvestLvl*` keys through native getInteger semantics.
AmunRa reads only Float/Double electricity, resolves native maximum capacity,
applies the original float-operation order and Java narrowing/clamping to the
item damage, then conditionally checks its mode or falls back to Item's map.
Only observed mode entries are inspected, with enum array, map, set identity
and bounded string content checked at sealing. Unrelated collision keys or
mode callbacks are not invoked. The two Tinkers' Defense harvest getters are
explicit constant pickaxe owners.

`NativeBatteryCapacities` reads pinned ItemBattery (15000), ItemBatteryInfinite
(positive infinity), GalaxySpace ItemLeadBattery (50000), AmunRa's capacity
field and recursive ItemAbstractBatteryUser. It never calls an arbitrary
capacity provider. The raw signed numeric item lookup, default GC battery,
actual selected item and field bits are source-bound and rechecked. Native
ItemStack loading uses an owned compound. A present nested `tag` aliases the
loaded child, so a child's maxEnergy cache write must persist there; a newly
created detached child tag must not be inserted into the serialized parent.
Unknown providers, native casts/null sets/invalid modes and budget failures
remain failures, never exclusions or fake constant capacities.

Rust and TS validate the new action, exact center coverage, short circuit,
source observations, capacity recursion and output samples after both query
phases. TS retains raw float bit strings through capacity caching and narrows
NBT long directly to float, avoiding JS double rounding. Ordinary NBT keys,
including `__proto__`, stay data in owned output maps. Queries share a center
identity only because these admitted laws do not inspect its offered count;
result samples are independently keyed by identity AND count. Historical
`merge` fixtures and their contract semantics are not rewritten.

Facts now runs metadata getters on separate owned copies. In particular,
tooltip/tool queries cannot change the identity/metadata/NBT used for the
queued icon or subsequent display/limit/aspect observations. Current output
facts therefore do not silently inherit a tool query's initialization effects.

The existing offline native suite has an `infusion-tools` family for real
installed methods on separate owned stacks. Small Rust/Node mechanisms cover
short circuit, nested aliases, count separation, prototype-like NBT keys and
the long-to-float boundary. They have been authored, not executed. The existing
installed semantic.67 contract package cannot validate this new variant;
generation, actual package installation and concentrated verification remain
mandatory before any export handoff.
