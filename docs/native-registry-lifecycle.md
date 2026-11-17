# Native semantic registry ownership

The development capture path separates borrowed NEI registrations from owned
semantic readers. GT, AE2 world, EnderIO VAT/alloy/splice and NASA readers are
adopted before the generic Template factory. Their borrowed prototypes are used
for category identity; capture and close do not modify the prototypes' caches.

`RegistrationBindings` compares native same-process source anchors for GT,
NEI diagram groups, EnderIO VAT/alloy/splice and the audited NASA families.
The exact vanilla FurnaceRecipeHandler also binds to the shared native
FurnaceRecipes singleton and smelting map.
The source comes from the actual native recipe category, diagram matcher or
registry. Separate instances with the same class/key alone are **not** a proof;
remaining families report `unverified` until independently audited. Missing
anchors are `handler_not_ready`, differing anchors are `handler_conflict`, and
later anchor drift is `environment_changed`. These anchors never authorize
reuse across game restarts, builds or language changes.

`NativeDefinitions` verifies ordered native records without display names,
Tooltips, GUI dimensions, render state or arbitrary callback reflection.
EnderIO uses exact audited definition classes, input predicates and ore
memberships, energy, outputs, bonus/experience fields and VAT fluid tables.
NASA uses native input/output definitions, explicit empty slots and the real
workbench admission predicates; it does not read GUI coordinates. Physical
inventory predicates remain gameplay semantics. Borrowed sources are never
cleared or modified.

Each captured native definition is checked before and after its fact reads.
Batch checks inspect readiness/counts; sealing checks every native definition
and ordered instance. Splice sealing also rechecks the complete native tool
class registry. Changed definitions fail with `recipe_changed`. Original
capture errors survive a second verification failure, which is suppressed.
This is a local consistency guard, not a world freeze or cross-version proof.

The shared `Recipes.Cursor` owns pending fact transactions and readers. Close
rolls back uncommitted facts, closes each owned reader once, clears its owned
Template cache and releases references to snapshots/dedupe state. Cleanup
continues after an error and preserves the first error plus suppressed failures.
Repeated close is safe, and querying/capturing/committing after close fails with
`capture_closed`. Completed accounting and bounded exclusion summaries remain
available for reporting. A default no-op reader `close` does not prove a family
has no native resources; that remains a per-family audit obligation.

Controlled installed-JAR tests demonstrate these boundaries. They do not
establish running-pack acceptance, full Handler equivalence, native pixels,
full information coverage, bilingual content or production performance.

Vanilla smelting now has a direct reader retaining native map order, one-item
consumption, exact/wildcard metadata with ignored NBT, complete output NBT and
amount, 200-tick base time and native experience per output item. Native first
match precedence produces explicit shadow exclusions or an `except` predicate
for a remaining wildcard domain. The precedence index is prepared once instead
of rescanning all earlier recipes for every row. Experience still uses the
actual native getter and is included in drift checks; no full-pack speed claim
is inferred. Missing registry and ready empty are distinct. Unknown metadata
getters and experience overrides fail explicitly pending a dedicated rule.
