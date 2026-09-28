# Native resource observations

NESQL 0.15.1 adds a resource diagnostic to the existing single-job queue and MCP
`start_check` tool. No Source revision changes: this report is evidence for the
local static-resource workflow and cannot publish a dataset.

Example request (execute only as part of a separately arranged game validation):

```json
{
  "key": "resource-sample",
  "world": "NEI-refactor-20260908",
  "domain": "resources",
  "resources": ["minecraft:textures/blocks/stone.png"]
}
```

The selection requires 1–128 unique canonical `namespace:path` locations.
Namespaces use lowercase ASCII letters, digits, underscore, hyphen and dot,
excluding `.` and `..`. Paths preserve case and UTF-8, reject traversal and
control characters, and must name a PNG, PNG metadata, or language file under
`textures/` or `lang/`. Resource checks do not accept recipe handlers, controller
ids or non-default recipe ranges. An empty selection never means “scan everything”.

`ResourceAudit` obtains each stream from Minecraft's active resource manager on
the client thread. `Resources.read` hashes exact bytes with a bounded buffer,
checks cancellation, enforces 64 MiB per resource and always closes the stream.
No image decoder or GL renderer runs. Each row includes `resource`, logical
`path`, exact decimal-string `bytes`, `sha256` and its check status.

Successful rows are resolved again before report completion; changed bytes fail
with `resource_changed`. World/session and environment changes also prevent a
complete report. Failed rows retain ordinary structured errors and per-target
detail files. `checked` means the diagnostic finished; the consumer still must
require every selected row to pass. The bridge archive helper validates resource
identity when preserving failure details.

The ordinary check report contains the environment and request. Pin its original
SHA256 from the completed job and the canonical environment digest before using
Compiler's `resolve` command. Do not substitute a different report or turn this
report into Source data. Byte equivalence does not prove tint, geometry, animation,
custom renderers or final item icon appearance.

Offline tests exercise the original `SimpleReloadableResourceManager` and
`FileResourcePack` lookup code using two ZIPs with the same resource path. Reversed
registration order returns different expected bytes; the resulting Java evidence
also passes the Rust resolver. The full Forge reload wrapper depends on a live
loading screen and is not invoked in those tests. Actual game reload, environment
drift and resource checks still need user-arranged live validation.

`sourceTest -PsourceFixture=<fresh-directory>` writes the small ZIP inputs,
`resources/input.json`, `resources/check.json` and `resources/evidence.json` beside
the ordinary Java fixture. These are explicitly test inputs, not real GTNH data.
The old diagnostic retry identity treats an absent resource selection as empty,
so adding this capability does not invalidate previous structure/recipe history.

## Capture provenance

NESQL 0.15.2 records `provenance` on export jobs and diagnostics before work
begins, and includes it in native check reports. Revision 1 contains:

- `environment`: the full canonical environment digest, unchanged.
- `runtime`: that environment with only `probes`, `settings.profile` and
  `settings.handlers` removed. All other settings, inputs, loaded mods, resource
  order, language and knowledge remain identity-bearing.
- `selection`: canonical serialized request without the retry `key` or display
  `name`. World, profile, handlers, probes and diagnostic range remain covered.
- `session`: opaque process-local digest for the world/player/resource lifetime.
  A reload, world exit, player replacement or new process invalidates it. Two
  jobs in one unchanged lifetime share it; stored completed jobs retain their
  original identity but cannot establish a new live session.

Provenance supplements the Source identity; it does not change Source revision
14 or put random session identifiers into domain records. A full export and a
resource check can share runtime/session while retaining different complete
environment and selection digests. This is a prerequisite for fragment recovery,
not permission to reuse arbitrary recipe ranges or cross-restart game facts.

The Compiler can now compare a resource report with a succeeded export job and
its verified Source using `resolve --source ... --capture ... --capture-sha256 ...`.
Both tasks must explicitly target the same world, share runtime/session, and have
valid complete provenance. Old reports remain usable as byte observations only;
missing provenance cannot be used to attach them to a Source.

The offline fixture now produces `resources/capture.json` from the actual job
journal and a small formal Source under `resources/datasets`. It checks persisted
provenance, independent request scopes and a native-resource PNG alongside the
language override. It does not exercise a live world, rendering or game reload.
