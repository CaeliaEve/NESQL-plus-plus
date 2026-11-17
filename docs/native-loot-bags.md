# Enhanced Loot Bags semantic reader

EnhancedLootBags 1.2.8 and YAMCore 0.7.1 are pinned by their actual mod IDs.
The reader opens before the Template factory, writes semantic inputs and
conditional outputs directly, and never constructs PositionedStack/CachedRecipe
or modifies borrowed arecipes.

The installed Drop.getItemStack/ItemDescriptor methods allocate owned stacks
and NBT. They do not initialize a group cache, draw RNG, shuffle a group or
read/write persistent player limits. These operations belong to separate
opening/merge methods and are not used by capture. Parsing first runs with
verification disabled, so item callbacks can be audited before the eager native
resolver. Unknown metadata/stack-limit callbacks fail without invocation;
the inherited Item and reviewed pinned GT split-stack paths are explicit.
Malformed/unresolved descriptors remain unresolved native entries rather than
being converted to fake items.

Native registry, configuration, bag-to-manager binding, original group/drop
lists, member instances and buffered keys/groups are retained and verified.
Resolved item instances and full rule/stack facts are checked before/after
capture and at sealing. Null source tables/lists are unready; initialized empty
tables are valid. Close is idempotent and only clears owned records.

The existing native runner retains correlated case-insensitive groups, integer
fortune reduction, retry state, quantity/stack splitting, player limits and
failed-resolution debit. It also checks independent registrations, same-content
source replacement, configuration drift, unknown callbacks, production Cursor,
empty/missing state and close. Original static mod/config/RNG/Loader/locale
entries are restored. Real configuration reading covers 51 groups within row
and output budgets, without executing opening RNG or storage. Mod items absent
from this isolated API test remain unresolved; this is not a whole-pack item,
pixel, full-English or game-runtime claim. New semantic transport rows preserve
the old complete recipe rules with canonical producer/category identities.
