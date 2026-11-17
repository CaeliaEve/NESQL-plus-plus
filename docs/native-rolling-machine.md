# Railcraft rolling-machine semantic capture

The pinned native manager and its actual ordered recipe ArrayList are bound
before the Template factory. The reader no longer constructs cached NEI rolling
recipes, computes their input/result positions or writes borrowed arecipes.
PositionedStack remains solely for existing finite wildcard expansion.

Both categories retain the shared four-class registry and cross-category
first-match priority. Logical shaped holes/mirrors and native greedy shapeless
matching are preserved. The machine consumes one per occupied cell and does
not return crafting containers. Native definitions bind the input collection,
alternative lists, complete original stack counts/NBT, output instances, grid,
mirroring and result-tag flag. Row checks and sealing reject source, order,
facts, instance or power-mode drift. Close is idempotent; initialized empty and
missing sources differ.

ShapedRecipes' optional result-tag flag is no longer rejected. The same audited
native LAST tagged-cell transfer used by ordinary crafting is attached to the
rolling machine's own process, with copyNbt=true. The helper keeps base item,
metadata and count; candidate projections are not matching tuples or a
Cartesian recipe enumeration. Machine power/priority and absence of container
returns are retained. Rust rejects missing projections, a false flag, wrong
handler, tuple bindings and inconsistent whole-compound results; NeoNEI uses
the selected candidates and actual grid/mirror order for the shown result.

Existing native manager/recipe tests cover selection, GUI-cache preservation,
input/result snapshots, lifecycle and dynamic output. They are controlled API
checks, not game operation or whole-pack acceptance. The independent furnace
fuel audit and the final full information/locale/release work remain separate
mandatory tasks.
