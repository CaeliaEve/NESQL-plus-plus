# Native NEI fuel table ownership

The fuel reference reader observes the table already exposed by the native
NEI lifecycle. It never calls findFuelsOnce or starts a replacement scan. A
missing table/catalogue is unready; an initialized empty table stays valid and
retains its original identity. This avoids GUI-config initialization through
the fuel builder during semantic capture.

Independent registration instances bind the actual item panel and fuel table.
The reader checks their identity/readiness/count, original pair order, stack
and candidate-array/member instances, raw amounts/NBT/metadata and cached burn
values. Candidate emission checks before and after; sealing checks all native
definitions. Close is idempotent, rejects subsequent use and releases owned
snapshots without changing borrowed tables. The common NativeDefinitions guard
retains environment_changed for this reference domain and recipe_changed for
existing recipe readers.

The existing source runner checks the actual handler class with isolated
native tables, missing/empty state, independent registrations, source and
same-content member replacements, drift, close and restoration.

Burn ticks are observations in the native NEI sample table. Capture and sealing
do not re-run furnace fuel handlers: a fresh random/world-dependent result is
not a valid comparison with an earlier native observation. The existing
nativePanelFuelSamples scope and positive-duration guards remain explicit; the
page labels the value as a sample rather than a universal burn guarantee.

Fixed exhausted-container paths are audited before invocation, including the
base Item callback's transitive legacy getters/predicates and the existing
pinned IC2/GT policies. Unknown container callbacks are rejected with zero
invocations. The old anonymous test callback is no longer accepted as a native
policy. New native samples use the real Item base container and a distinct
fuel-reference-semantic fixture; the old fixture stays unchanged and still
crosses the strict Rust reader, including its preserved historical quantity.

GT spray/food returns now read the pinned ItemList and unification fields
without calling sanityCheck or get. Exact association precedes wildcard;
persistent/pending blacklists, an existing cached target, original NBT and the
native amount-one rule are preserved. Only audited native hash storage and its
actual strategy are read; unknown item hash/stack callbacks fail before calls.
The fuel reader retains these bindings through row checks and sealing, including
same-content source/target replacement. This shared fixed-container resolution
does not turn dynamic fuel/resource callbacks into fixed returns.

Refined/cracked firestones now declare both native container branches without
drawing RNG. The threshold uses the concrete sample damage and native maximum;
normal returns keep tags and obey Unbreakable, damage one point, and break only
strictly above maximum. Refined degradation starts from the empty cracked stone,
keeps only a custom display name and then applies damage; cracked degradation
returns a fresh raw stone. Branch items and the degradation base are normal item
facts, bound through reader verification and sealing. They are not guaranteed
outputs of every burn.

Lava crystals declare the native signed energy cost and the ownerName network
policy: use server world zero, create a missing owner network, debit only with
enough essence, and return one unchanged crystal regardless of debit success.
Capture never opens the world/network or executes the resource callback. The
cached burn observation does not guarantee fuel eligibility in another state.
Java branch records pass strict Rust validation and the shared TS/HTTP/offline
query; both return branches are hydrated and visible. The isolated native test
compares seeded firestone branches and checks unchanged RNG during capture,
resource/durability drift, retained NBT and zero-count exhausted inputs.

This is not complete fuel adaptation. Other conditional/RNG/resource paths need
an explicit reviewed rule, never one callback draw presented as a fixed return.
Independent Railcraft blast supply now has its typed producer/Rust/TS policy,
native-matched use edges and bounded recipe component; see native-rail-furnaces.md.
This does not certify all common-fuel providers or arbitrary live burn callbacks.
No game, whole-pack, pixel or full-English acceptance is claimed.
