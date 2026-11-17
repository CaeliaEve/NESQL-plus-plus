# Native disabled identities

Railcraft 9.16.33 rock crushing is disabled by `Game.isGTNH`; Avaritia 1.77
compression lookup is disabled by `Avaritia.isDreamCraftLoaded`. These are
current-environment exclusions, not active-machine adapters or successful
recipe exports. Changing either guard invalidates every affected proof,
including an empty handler.

The sole production constructor reads the real static manager and admitted
`ArrayList` backing field. It does not call manager or recipe getters and has
no collection-injection variant. Rock enumeration skips native null entries
and accepts native `IRockCrusherRecipe` identity leaves without executing
their callbacks. Compression admits the two reviewed recipe owners and reads
`CompressOreRecipe.oreID` directly for the native `safeOre` filter. It observes
existing ore table/backing/view identities without calling `getOres` or
registering a missing name. An invalid ID or malformed source is a failure,
not a fabricated disable proof.

Batch guards check the disable flag, version, manager/list identity and size,
plus ore roots where needed. Seal verifies native entry identity/order, the
relevant ore IDs and complete observed ore membership/view associations.
Unused cost, output or input callbacks are deliberately not evaluated: the
native disable condition makes them irrelevant to these exclusion records.
Close only releases references and owned proof arrays/maps.

Both families now declare semantic source anchors for the four-registration
comparison. The existing Railcraft/compression tests use real scoped static
sources and check guard drift, equal-source replacement or member drift,
unknown-storage zero calls, and empty exclusions. They remain queued for the
final combined lightweight verification; this document is not a pass receipt.
