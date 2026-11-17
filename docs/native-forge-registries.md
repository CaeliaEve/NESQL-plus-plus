# Forge registry references

The two NEI-Integration Forge handlers describe registrations, rather than consuming crafting recipes. They now open `ForgeRegistryReferences` before any Template handler factory. The retired `ForgeRegistryRecipes` implementation is no longer reachable or compiled.

## Native meanings

- An ore group retains its native numeric ID, alphabetically ordered name and every raw registered ItemStack. Raw amount (including zero), metadata (including 32767), NBT and duplicates are observations. Forge ore lookup uses item ID and metadata, including wildcard membership; amount and NBT do not determine membership.
- Display specimens are separate, concrete item facts. Wildcards use the already completed native ItemList, with its source/member/facts verified. The raw wildcard is never passed to naming, tooltip or rendering callbacks. Missing ItemList readiness is an explicit error.
- A fluid entry is ordered by native numeric fluid ID. It retains native no-argument tooltip properties: temperature, luminosity, density, viscosity, gaseous state and placeability. The registered block identity is retained even if no ItemBlock exists; its item display is optional.
- Container observations retain each registered empty/filled pair, both raw ItemStacks, actual fluid amount and full fluid NBT. They are not a conversion recipe or a claim that any machine can fill/drain them. Fluid facts use their own NBT identity; they need not share a base fluid's stack-sensitive properties.
- The definition records the real config enabled state and entry count. A disabled page, ready-empty registry, registered empty ore group and unavailable registry are distinct states. No fake input/output recipe edges are created.

## Ownership and validation

Borrowed `arecipes` are never read or written. Registration bindings cover crafting, serial crafting, usage and serial usage instances through shared native registry roots/configuration. The reader binds roots, row/list/stack/fluid/container identities and raw facts, checks the selected entry around capture, and checks all entries, reverse lookup facts and wildcard sources before sealing. Close is idempotent and releases owned specimens; cancellation uses the existing checkpoint. Unknown ItemStack/metadata/key callbacks are rejected before invocation.

Raw templates are typed in the Rust Source/Catalog contract. Compiler validates definition origin, disabled/count consistency, native order, content identity, complete display census, template/display metadata/NBT and all fluid facts. The shared API supplies bounded entry/member/variant windows; only visible specimens are hydrated. NeoNEI uses a dedicated read-only page, native item/fluid icons and the existing shared animation player. Ordinary item clicks still navigate to actual recipes/uses.

Local evidence: `E:/codex/NEI/.refactor-state/implementation/semantic-ui-20261012/forge-registry-semantic-evidence.json` (generated after all checks). Controlled sample icons/names are scaffolding. Native pixels, complete English content, production latency and the whole modpack remain subject to later acceptance; local Java tests do not launch Minecraft.
