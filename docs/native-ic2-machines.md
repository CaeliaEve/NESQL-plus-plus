# IC2 ordinary machine semantic reader

Nine pinned IC2 2.2.828 handlers (macerator, extractor, compressor, three metal
former modes, centrifuge, washing and block cutter) now capture native
predicates/results directly. They open before the Template factory, do not
construct CachedIORecipe, do not recover slots from its positions, and do not
read or modify `arecipes`. `PositionedStack` remains solely for finite native
metadata enumeration. The separately audited blast-furnace reader has now
removed its last projection use, so the old shared GUI projection proxy is gone.

Each native BasicMachineRecipeManager returns a fresh anonymous getRecipes
view. Its view identity is not source identity. Registration/readers bind the
actual manager and its underlying HashMap, and check that the handler's exact
native view belongs to that manager. Each record binds input/output identity
and full native facts, metadata and expanded candidates. Capture checks its
record before/after; sealing checks all definitions and native iteration order.
Reader close releases owned wrappers, not the registry. Empty and unavailable
sources differ; manager/storage replacement is detected.

The existing record contract retains native input amounts and ignored-NBT /
metadata predicates, all outputs, base time/power, centrifuge heat, washing
water and block-cutter blade thresholds. Known exact IC2 input classes are
checked before invocation. Their returned concrete items require the audited
default metadata getter before it or native matching executes; unknown item
callbacks fail explicitly. Native API tests cover both item and metadata-filtered
ore inputs, with zero calls to the deliberately unknown getter.

The same existing native runner covers all nine actual handler-to-API manager
paths, independent registration instances, manager replacement, semantic
captures, metadata/count drift, ready-empty and close, alongside the prior
quantity/heat/water/blade assertions. Manager/Loader test configuration is
restored in finally. No per-handler test framework or game export was added.

This closes the ordinary family's known GUI scaffolding and lifecycle gap. It
does not certify every possible mod callback, global first-match reachability,
all native information/locale fields, native pixels or whole-pack acceptance.
Those remain in the full implementation audit.
