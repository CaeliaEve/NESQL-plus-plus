# Handler checkpoints

NESQL 0.43.0 can resume an explicitly selected recipe export in the same live
game session. It seals an ordered prefix: the dependency seed (`base`), then each
complete Handler in native order. A Handler is sealed only after its encoding
queue drains, its native resources close and the environment guard passes.
This is not arbitrary-range stitching or recovery across a game restart.

The job's `performance.checkpoint` contains its job ID, original SHA-256,
completed Handler count and checkpoint directory. Keep this original receipt.
On a terminal attempt, `released.json` authorizes the latest prefix only if all
native cleanup succeeded. Cancellation waits for running native work and keeps
any factory/cleanup failure. Abrupt process termination does not create this
release marker.

Start a **new** key with the original scope, world, profile, handlers and probes:

```json
{
  "key": "recipes-retry-02",
  "name": "recipes",
  "world": "test-world",
  "profile": "full",
  "scope": "recipes",
  "handlers": ["category_<original-64-hex-id>"],
  "resume": { "job": "<previous-job-uuid>", "sha256": "<original-checkpoint-sha256>" }
}
```

The MCP `start_export` tool and ordinary job HTTP endpoint accept this request.
For the acceptance coordinator, set `stages.source.resume` to the same reference
and change `stages.source.key`. The coordinator retains terminal attempts and
requires its recorded failed Source attempt and digest to match. Recovery is
explicit; it never loops on a failed export automatically.

Recovery validates producer, session, environment, runtime, selection and exact
Handler order. It then uses **fresh Facts** to recapture every completed unit's
native data. Ordered semantic hashes cover recipes, categories, item/fluid
properties, aspects, programs, research and native exclusions. Images and views are
excluded from this projection; referenced text IDs remain content hashes.
Same-ID changes to ore tags, NBT-related properties or quantities are therefore
not hidden by restored memoization. Data recapture still takes time; the saved
work is rendering, animation extraction and PNG encoding for the sealed prefix.

Records and assets replay through normal Rows/Dataset writers into a fresh,
unpublished attempt. Partial unit logs are ignored. Digests, lengths and event
counts are checked first, so damaged evidence fails before a long native data
recheck. A replayed unit becomes eligible for reuse only after that recheck also
passes. Any remaining Handlers are captured normally, followed by ordinary
Source sealing and publication.

On disk, `nesql/checkpoints/<job>` contains immutable per-unit JSONL logs,
content-addressed blobs, hashed manifests and the terminal release marker.
Blobs may be hard links, so all original evidence must remain immutable.
Checkpoint files are not Source files and are not Compiler inputs. Old
`nesql/captures/<job>` archives in `writing` state remain invalid for assembly.

The base contains aspects and, when selected Magic recipes require research,
the complete registered research closure (including prerequisites, sibling
links and triggers). Recovery obtains a fresh native research snapshot after
checkpoint integrity checks, then compares knowledge state and dependency data.
Only research artwork is omitted from the semantic projection; actual item
triggers and their facts remain part of validation.

Scope limits: full-domain exports are not checkpointable here.
Changing the mod, resources, configuration, selection or game session rejects
reuse. A code fix requiring a new JAR needs a new capture; cross-build reuse
requires a separate compatibility proof and is not implemented.

Local tests cover replay byte identity, corruption, incomplete units, semantic
drift and cancellation/cleanup failures. A real-game interruption/recovery pilot
must validate native data determinism and pixel parity before a full acceptance
run can claim recovery passed.
