# Space pump and gas siphon semantic capture

The pinned GregTech 5.09.51.482 native RECIPES tables are read before the NEI
Template factory. Capture creates no ModularWindow, PositionedStack or
CachedRecipe and does not mutate borrowed arecipes. Original planet/depth
selectors, fluid identity/NBT, nominal quantities, module capabilities, energy,
batching, orbit/structure conditions, output and selection laws remain intact.
Nominal fluid output is conditional; reusable equipment is not consumed.

Registration instances bind the same actual HashMap. Native table/key/value
instances, nested planet tables, structural modification counters and complete
fluid facts are checked. Only the pinned ImmutablePair/MutablePair fields are
read; unknown Pair callbacks are rejected without invocation. Capture checks
its definition before and after, and sealing checks the entire registry.
Ready-empty is valid; a missing table is unready. Close is idempotent and only
clears owned records.

The existing native runner verifies both readers, the production Cursor path,
borrowed-cache preservation, instance/fact drift, mutable selectors, empty
tables, close and independent registration sources. It configures an owned
empty client locale and restores native tables and Loader entries. No machine
operation, world/orbit, GL rendering or game export is simulated. New semantic
transport samples preserve the old recipe/process/fluid facts, with corrected
canonical producer/category identities. These are controlled API checks, not
whole-pack, pixel, full-English or performance acceptance.
