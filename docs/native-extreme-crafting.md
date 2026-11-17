# Avaritia extreme crafting

The 1.77 reader uses the actual `ExtremeCraftingManager.instance.recipes`
ArrayList, shared by shaped and shapeless registrations. It does not create
CachedRecipe or PositionedStack objects, ask for GUI geometry, or write the
prototype's NEI cache. Four-registration equivalence binds the actual manager
and table, not a fresh API projection. An initialized empty table is valid;
a missing manager/table is unready.

Only the four pinned native definition classes are admitted. Foreign recipe,
input-list, stack, metadata and container callbacks fail before execution.
The snapshot binds registry order and every selected and unselected definition,
input storage, alternative group, stack/item/tag instance, raw amount/NBT,
dimensions/mirror flag and output. Owned copies carry quantities and predicates;
native stack counts never become the per-cell consumption quantity. Capture
checks its definition before and after work; sealing checks all definitions.
Cancellation remains cooperative, and close drops owned snapshots without
clearing borrowed registries/caches. A callback failure stays primary if the
post-check also finds drift.

## Matching and results

`extremeCrafting` preserves the native registry index, preceding non-impossible
selectors and the two-stack repair intersection. Shaped matching allows native
translations within 9x9 and only the declared mirror; sparse holes remain empty.
Shapeless matching uses native offered-cell order and first remaining matching
requirement. Direct Avaritia inputs require template NBT only when present;
ore-list branches and Forge shapeless recipes ignore NBT. Every occupied cell
consumes one item, independent of its raw template count.

Before checking the registry, the manager repairs exactly two nonempty, unit
stacks of the same item when `Item.isRepairable` permits it. The reader audits
the native repair/damageability and relevant maximum/display-damage owners, then
reads `canRepair`, raw maximum damage and subtype flags without invoking those
callbacks. Shared item types include disabled cases, so omitted repair rules
cannot silently imply eligibility. Enabled repair combines remaining durability
and the native integer `maximum * 5 / 100` bonus, caps negative result damage at
zero, and creates a new untagged result. Registered outputs describe only the
ordinary branch after repair and all preceding selectors have failed. The UI
and material calculator must keep such gated results conditional.

## Content candidates and consumption

Wildcard visuals require a finished native item map. The reader binds its actual
ArrayListMultimap backing map/list/member identities and complete facts, and
copies its concrete variants. A finished map with no variants follows the pinned
native metadata-zero branch; an unfinished map never uses that branch. Required
NBT comes from the recipe template, not an unrelated display permutation.
No item render/name/permutation callbacks are needed to enumerate candidates.

Container inputs use the audited ItemCallbacks path. Fixed ItemList-dependent
containers retain the read-only GT binding. GT tools retain normalization/stats
and emit conditional native wear without invoking wear, RNG or energy use.
Container dependencies and native candidate drift are checked through sealing.
The exported return facts describe returned items; player inventory capacity,
placement/drop decisions and arbitrary player tags are not simulated.

The existing native Extreme family runner checks these boundaries with owned
native managers and restores its globals. Its records are conformance fixtures,
not a live full-pack, pixel or language acceptance result.
