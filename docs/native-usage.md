# Input-driven usage references

NESQL++ 0.45.0-semantic.7 supports four exact usage-only routes: AE2 and AE2FC cell
contents, and OpenComputers manual and Callback documentation. They are
interactive reference sources, not crafting handlers. Pair with Compiler
0.40.0-semantic.5 and contracts 0.40.0-semantic.71; Source/Catalog remain revision 40.

The observation scope is the completed NEI item catalogue. This includes the NBT
actually present on those stacks, not arbitrary inventories in a player's world.
An empty catalogue result is still accounted for as matched, unmatched or
duplicate. The four sources remain in the complete runtime diagnostic/export
selection. The Profiler tool is recorded separately.

Cell queries use the native registry and storage channel on owned input copies,
without a save provider. Long counts and capacity counters are decimal strings.
No storage insertion, extraction, callback environment creation, GUI or world
action is performed. Exact pinned roles are admitted; unknown providers fail.

Manual capture follows native content/path providers, tabs, redirects and English
fallback, preserving raw text, typed links, item image references and original PNG
bytes. PNG data is a typed `usageResource` leaf in the references collection; it
is part of the sealed Source/Catalog, not a sidecar. Missing native resources are
explicit warning records. Custom Markdown extensions and external providers are
not assumed compatible. Native missing-language fallback is marked explicitly.

API documents read valid native Callback annotations and inherited environment
classes; their functions are not invoked. Provider classes and operation roles
are pinned by the original target class bytes in `native-usage-admission.json`.
The remapped test admission resource is isolated to the native-test classpath and
must never be included in the production JAR.

Registration discovery binds registry, member and configuration identities without
executing unknown providers. It does not claim that those providers are supported.
Opening, reading and verifying each cursor still require the exact pinned operation
roles; unsupported providers become a diagnostic failure of that source rather
than preventing the entire registration inventory from being observed. Unknown
registry shapes and unsafe state still stop discovery. Source/input/provider/locale/content drift fails. Unmatched inputs keep
only identity keys, avoiding retained copies of every scanned NBT payload.

The target extension audit covers 230 mod JARs and their class hierarchies; this is
static coverage, not proof of the current registered runtime union. The admission
policy now includes the target Thaumic Energistics cell provider and nine AE2FC,
OpenSecurity and Computronics OC provider/driver implementations. Cell item roles
include the AE2FC infinity-water and extra-cells proxy storage types. These roles
are pinned to original class bytes and retain their native selection predicates.

Thaumic Energistics is not converted into an AE item/fluid inventory. The native
factory runs on an owned copy; a returned inventory outside the native viewer's
accepted types is rejected as a non-match. Its original exceptions remain visible.
No essentia extraction, storage mutation of source items or environment creation
is introduced. OC API capture reads class and Callback metadata without invoking
the documented functions.

Local native fixtures, Compiler checks and offline browser tests do not establish
whole-pack acceptance. A new live session must reconcile the four registration
lists, diagnose every gameplay source and pass content-visual gates before export.
The coordinator rejects different diagnostic and Source selections even in dry-run.
No old writing fragments, cross-version diagnostics or language sessions are reused.

For one complete diagnostic collection before another export attempt, use
[batch diagnostics](batch-diagnostics.md). The four routes use the
`interactive:` prefix and `recordKind: references`; dry-run checks the routing.
