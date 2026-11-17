# Native capture dispositions

Development Source 40 / Catalog 39 keeps recipe GUI capture disabled. This
document describes the diagnostic and recovery accounting protocol; it does
not declare full-pack acceptance or a new release candidate.

Each attempted native entry returns one successful disposition or an exception:

- `captured`: at least one new recipe branch; additional identical branches
  include the first native index/branch and exact recipe identity.
- `duplicate`: no new branch, with complete duplicate provenance.
- `excluded`: an explicit reason and native predicate/configuration evidence.
- `reference`: exactly one domain reference, without pretending it is a recipe.
- Failures and cancellation remain exceptions. An empty branch set without
  evidence fails with `capture_empty`; it is never inferred to be an exclusion.

Native entries and emitted branches use different denominators. In check rows:

```text
checkedRecipes = passedRecipes + duplicateRecipes.length
               + excludedRecipes.length + referenceRows + failedRecipes.length
```

`passedRecipes` counts captured native entries, not output branches.
`emittedBranches` and `duplicateBranches` describe recipe branches. Legacy
boolean reports cannot provide these distinctions: the coordinator labels
them `legacy-boolean`; a producer advertising `diagnosticAccountingRevision: 1`
must return current rows with `accountingRevision: 1`.

Each current diagnostic row pins a `nesql.capture-outcomes` revision 1 gzip
JSONL sidecar through `captureOutcomeFile`. Decimal-string byte and row counts,
the exact compressed SHA-256, logical byte count and `rangeComplete` are
included. Every successful native disposition is retained, including full
exclusion evidence and duplicate origins. Failure detail files remain separate.
Inline `captureOutcomes` is only a bounded preview (32 entries / 32 KiB).
Handler exclusion previews are limited to 16 entries / 8 KiB; omitted previews
do not change counts or discard the full sidecar/checkpoint evidence.

The bridge streams and verifies the ledger, including decompression limits,
indices, outcome fields, counts and duplicate origins. It archives exact bytes
with the raw report and records them in `archivedReports[].outcomes`. Include
these files in handoff receipts alongside `files` and shared `evidence`.
Incomplete diagnostic ledgers may be preserved for failed rows; they cannot
pass a completed diagnostic gate.

Facts use per-attempt addition journals and pending-output bounds. A failed
attempt rolls back new item/fluid/text/program identities and queued records,
icons, pictures and models; previous committed facts are retained. Checks stage
independent captures in the existing game-thread slice, then validate rendering,
detached snapshots and local Rows before committing recipe dedupe. A rejected
earlier staged entry cannot turn a later valid entry into a false exclusion.
Commit is sequential after the slice returns, never concurrent with native
capture. Equal IDs with different native information fail explicitly rather
than silently dropping notes or samples.

Selected-handler checkpoints use envelope revision 5, with semantic
proof revision 3 and continuation revision 1. Each Handler unit stores ordered `outcome` events, an exact
constant-space `nesql.capture-accounting` count/hash proof, ordinary records
and assets. Sealing compares the complete ledger to that proof; recovery checks
the journal and reruns native semantics/accounting. Outcome events never become
Source rows. Prefixes with the older semantic proof revision cannot be reused.
Units classify inventory program resources through `nativeVisuals`. Native
prefixes require the exact released process-local chain, per-unit owned Random
positions and verified renderer/model/texture/shader source bindings. A shorter
replayed prefix never inherits its old chain's terminal Random position. Hashes
or visual JSON alone do not provide that authority. This new path still awaits
the unified validation batch; it is not native acceptance evidence.
Unsealed `writing` fragments remain evidence, not inputs to a successful Source.

This protocol preserves the same-session, same-build, same-environment, same-
language and same-capability recovery boundary. Compiler assembly still accepts
only a complete capture envelope. These hashes are integrity receipts, not
cryptographic signatures.
