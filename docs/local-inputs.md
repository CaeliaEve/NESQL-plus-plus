# Local input audit

The local workflow combines static resources from the actual GTNH instance with
NESQL runtime facts and native rendering. The website UI and existing Source /
Catalog contracts remain unchanged in this first step. Game export and live
acceptance are performed separately by the user-appointed export agent.

Run the offline audit against a recorded worklist and its complete checkpoint:

```powershell
node bridge/src/audit.mjs --worklist <worklist.json> --checkpoint <checkpoint.json> --output <new-report.json>
```

The report records input byte counts and SHA256, all category identities, exact
source mappings already implemented in the reviewed adapters, independent game
observations, and outstanding source reviews. Unrecognized classes have no
inferred registry. Categories sharing a handler/adapter retain their native keys.
`display.status` stays unverified because data diagnostics do not capture GL
views or prove texture completeness. The tool does not modify a checkpoint or
call the game. Existing report destinations are refused.

The 2026-09-28 checkpoint remains the baseline: 330 targets, 153 passed, 149 failed,
27 pending, 1 justified tooling exclusion, 0 partial. A code mapping is not an
acceptance result. Several inherited crafting handlers declared support without
having a loader; the original failure remains in the audit rather than being
rewritten to success. Full source publication remains blocked until the required
facts, semantic rules and rendering coverage are actually verified.

The coordinator accepts the recorded `domain:structure` route as well as
`domain:structures` and dispatches native controller checks. Handler-open errors
are archived with their original Java code, message, cause and severity before
pagination validation. Recoverable failures remain failed; fatal or unknown
severity stops the plan. They are no longer replaced by `invalid_row_total`.

Compiler's offline `resources` command imports explicitly pinned archives.
It keeps every same-path candidate and records `resolution: unverified`; it does
not yet replace NESQL rendering or attach resource bindings to Source records.
NESQL 0.15.1 now supplies native byte observations through the
[resource diagnostic](runtime-resources.md); Compiler's `resolve` matches them to
verified loaded archive candidates. Source assembly and pixel-equivalence checks
remain outstanding. No fresh full-game run is warranted solely to test the
offline utilities.
