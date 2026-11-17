# CAL imprint semantic reader

The GT 5.09.54.265 reader enumerates `CircuitImprint.VALUES`, filters the cached
source-mod state, and resolves each loaded entry's `ItemList` imprint through the
existing read-only unification binding. It does not enumerate RecipeMap special
items, scan numeric item IDs, construct a CachedShapelessRecipe or alter NEI's
borrowed display cache.

Installation calls the actual `installImprint(ItemStack, CircuitImprint)` method.
The output is a one-item machine copy with integer `id_imprint = entry.id`;
all unrelated machine tags are retained. Both logical inputs consume one unit.
The process and input predicates carry sorted `imprintIds`, which are enum IDs,
not serialized Minecraft item-registry numbers. No crafting-container return
is invented for this operation.

Native lookup still gives legacy `Type` precedence over `id_imprint`. It may
decode items and execute native item-name callbacks. The display machine must
not require that legacy lookup; it is never silently treated as unprinted.
The shared consumer reports the missing native context explicitly and preserves
the legacy tag when calculating an installation descriptor.

Registration identity binds the enum array, both lookup roots, CAL stack,
metatile array/entity and circuit-parts singleton. Batches check those roots,
counts and machine facts. Each capture checks only its touched enum and
ItemList/unification dependencies before and after native execution; exceptions
retain the original failure and suppress subsequent drift. Final sealing checks
every enum, binding, order and lookup identity. Close releases owned references
without clearing native tables or already-emitted rule data.

The isolated runner uses the exact remapped target jar, owned native globals and
startup isolation. CAL installation/lookup, enum bodies and native
`isValidImprint` remain unchanged. It captures one row from 37 real enum
definitions and checks preserved tags, legacy precedence, local drift and final
sealing. The generated small row is replayed by Rust and the shared consumer.
This is bounded conformance below admission, not full-family approval,
full-instance export, rendering, performance or release acceptance.
