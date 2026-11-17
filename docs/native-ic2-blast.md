# IC2 blast-furnace semantic reader

The pinned IC2 2.2.828 blast furnace opens before the NEI Template factory.
It reads the real `Recipes.blastfurance` manager and its underlying recipe
HashMap, not a new anonymous view's identity. Independent registration
instances bind that same manager, storage and actual selection cache.
It constructs no CachedIORecipe or positions, and never changes `arecipes`.
The final shared IC2 GUI projection proxy is removed.

The existing `ic2Blast` contract keeps the input-count admission gate separate
from staged unit consumption, six air/cell events per uninterrupted hot cycle,
retained progress, the heat requirement, container observations and the native
second-product space/loss behavior. Native `work()` uses only the first two
products and ignores recipe metadata. Extra registered products/metadata are
bound for drift detection, not invented as machine products. It remains a
stateful process, with no fabricated fixed EU/t or duration.

Ordinary `javap` output from the installed-version derived IC2 JAR establishes
that `BasicMachineRecipeManager.getRecipe` only reads its cache: wildcard
metadata precedes concrete metadata, then it tries uncacheable callbacks.
Cache writes/event subscription occur in registration, not this lookup.
The reader retains that real native lookup on owned input copies. It requires
the exact native map/list/tuple classes and an empty uncacheable list before
lookup; it never clones/registers a manager or triggers event subscription.
These are shell static observations and local native API tests, not REA
Evidence or a game execution claim.

Per-record guards bind concrete candidates, predicates, container observations,
all declared products and metadata. The selection-cache snapshot preserves
actual nested map/tuple/key/value identities. Captures check wildcard and
concrete selection entries; sealing checks every cache entry and native recipe
order. Shared guards check manager/storage/cache/list replacements, heat,
air/cell identities and complete stack facts, air metadata policy and returned
container. Unknown metadata-policy callbacks fail before invocation. Ready-empty
and uninitialized managers differ. Close is idempotent and releases owned
snapshots without changing native tables or borrowed NEI cache.

The existing native blast runner checks actual work behavior alongside borrowed
cache preservation, wildcard precedence, same-content cache replacement,
ignored-product drift, configuration/source drift, independent registrations,
empty/missing states and close. Controlled global fixture state is restored in
finally. Other IC2 families, arbitrary third-party callbacks, bilingual content,
native pixels and full-pack export readiness require the remaining project work.
