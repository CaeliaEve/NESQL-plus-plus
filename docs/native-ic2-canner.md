# IC2 canner semantic reader

The installed 2.2.828 bottle/enrichment managers are read directly. The reader is
opened before the NEI handler factory, never constructs a CachedRecipe/universal
cell illustration, and never reads or modifies `arecipes`.

Solid input container/fill counts are consumed separately; all native output
stacks are retained, including outputs not shown by NEI. Enrichment consumes the
native FluidStack and additive, preserving fluid tags and exact amounts. A cell
illustration does not replace the fluid with a 1000-mB stack or add container
inputs/returns. Existing base duration, power and enrichment-mode/tank notes keep
their current record contract.

Owned parsed records bind the native manager/map, key/value identities,
predicate instances and complete input/output facts (including RecipeOutput
metadata). Each capture checks its own record and relevant overlap siblings;
sealing checks the full native map iteration order and all definitions. Known
overlapping selectors still fail explicitly rather than become unconditional
independent recipes. Unknown predicate classes fail before invoking them.

Ready-empty maps and missing managers differ. Close releases owned records,
preserves the borrowed registry/cache, and rejects subsequent operations. The
four-registration source comparison can bind independent canner handler
instances to their common native manager/map. It is a same-process proof only.

Existing native tests cover quantities, candidate alternatives, fluid tags,
hidden outputs, borrowed-cache preservation, drift/close, ready-empty/missing
states and registration identity. Test configuration is restored in finally;
the Loader's public immutable map is not mutated. No game export or native-pixel
acceptance is implied. The wider shared IC2 predicate/callback and machine
information audit remains part of the full implementation goal.
