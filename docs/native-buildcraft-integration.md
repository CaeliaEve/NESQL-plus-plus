# BuildCraft integration semantic reader

The pinned BuildCraft 7.1.44 / Compat 7.1.18 reader opens before the NEI Template
factory. It uses the active IntegrationRecipeManager and its actual LinkedList,
preserving native order and shared registration identity. Ready-empty and
unavailable sources differ. It reads no ContainerIntegrationTable coordinates,
constructs no PositionedStack or CachedRecipe and does not change `arecipes`.

The existing typed integration rule keeps nine logical positions (one primary,
eight expansion positions), optional holes, correlated candidate tuples and
their actual native preview/completion results. Gate, facade and robot rules
retain their own semantics, energy budget, migration and consumption behavior.
Examples remain bounded batches of at most 128, with facade coverage linear
in primary/expansion samples instead of a full facade Cartesian product.
The native family classes are disjoint; duplicate family rules still fail
explicitly because their priority/active-recipe retention needs separate rules.

Before/after observations, guards check mutable rule facts and actual static
item, map, entry, board-wrapper and callback-owner identities. Changed maps
are rejected before their entries or replacement callbacks are traversed.
Native failures remain primary, with later verification errors suppressed.
Owned copies preserve input graphs without modifying borrowed native samples.

Reader guards check native manager/list identity, count and recipe order.
Sealing additionally checks relevant item/block registry identities, the full
rule environment and regenerated finite examples in native order. This avoids
rebuilding every large example list per 128-record batch. Sample changes cannot
produce a successfully sealed Source. Close is idempotent and releases only
owned snapshots/families; it preserves borrowed lists and NEI cache.

The existing native integration runner checks rule behavior plus GUI sentinels,
actual API/list source bindings, same-content map-entry replacement, example
drift, native list/API replacement, pure-GUI coordinate independence, ready-empty
and close. Its Java observation and adapter-record files remain byte-identical
to the existing Compiler contract fixtures. No protocol change, live game
diagnostic, whole-pack/native-pixel/locale/performance or final release result
is claimed by this local stage.
