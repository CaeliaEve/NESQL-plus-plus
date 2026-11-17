# Railcraft coke oven and blast-furnace semantic readers

The pinned Railcraft 9.16.33 / NEI Integration 1.5.0 readers open before the
Template factory, bind the active native manager and its actual recipe ArrayList,
and preserve native two-pass selection order. They no longer create projection
recipes, CachedCokeOvenRecipe, CachedBlastFurnaceRecipe or PositionedStack, and
do not read or change `arecipes`.

Native count, ignored/exact NBT, synthetic-tag rejection, subtype/wildcard
matching, shadowing/exclusions, item/fluid outputs and cook-time quantization
are retained. Item metadata/subtype/assignment method owners are audited before
invocation. The known pinned GT++ BaseItemBurnable getter remains explicit;
unreviewed getters fail with zero invocations. Full raw input count/tags, flags,
effective metadata, outputs and concrete matching examples are bound separately
from owned display normalization. Capture checks its native definition before
and after; sealing checks every definition and original native list order.
Close is idempotent and preserves borrowed registries/cache. Empty and missing
sources differ; wildcard display enumeration requires the item panel to be ready.

The actual pinned InvTools.isWildcard accepts both -1 and 32767. Plain
ItemStack.copy clamps negative raw damage to zero. The reader therefore retains
the reviewed -1 sentinel on an owned predicate anchor using raw field access,
then chooses a concrete nonnegative item for display. Fixed subtype templates
keep both offered-stack sentinel branches. The literal `metadata` rule permits
exactly -1 as its only negative value; -2 and lower remain invalid. This does
not create a negative-metadata visual item or broaden ordinary item identity.
Source 40 / Catalog 39 keep their shapes; contracts semantic.37 makes the
signed-sentinel bound explicit in the generated schema and shared decoder.

The existing Railcraft and AmunRa native runners check real manager matching,
counter behavior, borrowed cache preservation, source/order/facts drift,
independent registration bindings, ready-empty/close and wildcard examples.
An actual Java row containing both literal sentinels is replayed by Rust and
the shared TS decoder and compiled through the real CLI. Generic fixture names
and missing native icons remain controlled-fixture limitations, not pixel or
whole-pack acceptance.

The blast furnace publishes `railcraftBlast` with registered cook time and an
independent fuel supply. Each template retains its raw metadata (including both
native wildcard sentinels), subtype policy, original amount and NBT-bearing
concrete display. Fuel is not a fixed per-recipe ingredient. The typed policy
preserves native NBT matching, asymmetric last-fuel caching, special burn lookup,
master/server budget decrease of five per world tick (even idle), reload gates,
signed budget accumulation and count-dependent container/decrement handling.
No burn, random container or world/network callback is evaluated during capture.
Native panel burn values remain observations rather than universal burn values.
Rolling-machine, advanced-coke and other handlers have separate audit boundaries.

The production blast reader uses an existing native allowed-fuel table when
present, including an explicitly ready empty table. If that lazy field is null,
it replays the pinned native default definition from read-only dependencies.
Missing required mod/config/item dependencies remain unready, rather than being
mistaken for an empty list. Capture never calls the lazy getFuels builder or
initializes Thaumcraft item caches.
The actual manager, wrapper/backing list, template/member/item instances,
complete raw facts and subtype policy are bound before/after recipe rows and
at sealing; independent registrations also bind this table. Close preserves
borrowed state. Unknown metadata/subtype/hash callbacks fail before invocation.
The existing native runner covers missing/empty, replacements/drift, owned
instances of the actual native anonymous list and unmodifiable wrapper, and
caller mutation of returned snapshots. It does not run the native lazy builder.
Source 40 / Catalog 39 / contracts semantic.42 carry this typed process through
the real Compiler and strict TS decoder. Compilation adds fuel use edges and
search targets using native subtype/metadata/NBT predicates. Shared online and
offline queries hydrate all supply facts. The recipe component paginates fuel
templates and explains the policies; material totals retain an unknown optional
fuel quantity instead of multiplying a fixed quantity by the recipe count.
The actual native transport is checked together with 32 existing Rust transports,
and a compiled controlled Catalog verifies positive and wrong-NBT fuel use edges.
These local checks do not certify live burn callbacks, native pixels, full-pack
coverage, full English or production latency.

Cold replay preserves Thaumcraft's cached loaded/missing/item precedence,
uncached ItemApi field resolution, module/config coke-block gates, vanilla fuels
and optional coke/firestone items in their native order. It reads the actual FML
source roots rather than binding a newly allocated getIndexedModList copy.
Native definition, mod state, config, source/item replacement and late native
table initialization are checked through sealing. Unknown callback/table types
fail before invocation; unavailable required state is explicit. The native runner
compares cold scenarios against getFuels on separate owned managers, restoring
the test-only lazy cache writes after comparison. The production reader leaves
borrowed caches and NEI recipe lists untouched. These checks cover local native
definitions; live item visuals and full-pack readiness still require export-agent
validation.
