# Witching Gadgets spinning wheel

The implementation targets the installed `WitchingGadgets-1.7.25-GTNH.jar` and its
`SpinningRecipe` / `common.blocks.tiles.TileEntitySpinningWheel` classes. It is in
the unified implementation batch and has not yet passed its queued local checks.

The native selector packs the five occupied input slots in physical order and
chooses the first registered match. Offered count and NBT do not affect matching;
template metadata 32767 is an OreDictionary wildcard. `inputsMatch` retains a hit
flag across offers. A direct stack match removes one requirement and breaks;
a matching ArrayList requirement is removed and scanning continues. One offered
stack can therefore remove several list requirements, and later unmatched offers
can be accepted. Replacing this with ordinary bipartite/shapeless matching, or
rejecting all overlapping ore lists, changes the installed behavior.

`witchingSpinning` keeps the ordered native definitions, null input arrays and
unmatchable requirement branches. The physical five-slot bound, null array or a
requirement which cannot be removed provides an explicit exclusion proof. A bad
output or an unknown callback/storage owner remains a fault. Candidate tuples
which are claimed by an earlier registration remain conditional examples; they
do not authorize excluding the whole selected registration.

At completion the machine decrements every occupied input slot by one, including
offers which did not remove a requirement. It does not invoke crafting container
logic. Default progress from zero requires 121 consecutive valid work calls,
but progress/maxProgress and the output inventory are machine state. Accordingly
the new process does not publish a guaranteed elapsed duration. The fixed shown
output is the empty-slot prototype. Merging compares item/metadata, keeps the
existing NBT and checks the native stack limits of both stacks.

The reader uses the sole registered production constructor, checks native storage
before iteration/copying, binds nested input/output objects and NBT at sealing,
reuses concrete wildcard examples and releases its owned state on close. Batch
verification checks roots/counts; it no longer rebuilds all records or performs
exponential overlap searches on every batch. Four-register equivalence uses the
same native list anchor rather than a NEI display cache.

Compiler and NeoNEI have dedicated process gates. Detail/list/hydration paths
retain the selector, the page exposes physical order changes, and an invalid or
earlier-claimed combination hides the selected output. Materials and separate
Chinese/English notes preserve the conditional result. Native matcher examples,
two small Node cases and cross-language transport are queued for the final
verification batch; no game/export acceptance is claimed here.
