# GalaxySpace assembly semantics

The reader is pinned to GalaxySpace 1.1.121-GTNH and GalacticraftCore
3.3.13-GTNH. It reads the actual `AssemblyRecipes.RECIPES` ArrayList and binds
`ConfigManagerCore.hardMode`; it does not construct a GUI cache, PositionedStack
or viewport. Four-registration bindings include both roots.

The pinned native manager first repairs two occupied, same-item, unit stacks
when Item.isRepairable allows it. Its signed Java int calculation combines
remaining durability and `maximum * 5 / 100`, clamps negative result damage to
zero and creates one new untagged stack. Larger offered stacks skip repair.
The reader audits callback owners and reads native fields; unknown repair,
damageability, maximum or display-damage callbacks fail without being executed.
Shared NativeCraftingRepair also serves the independently checked Avaritia
manager with the same native branch.

Every registered row includes the complete item-type intersection in
`process.repair`, including disabled types. Registered fixed output is conditional
on not entering repair and not matching an earlier registered selector. It is
not a repair output sample. Compiler requires that intersection and binds the
process to this native handler; NeoNEI hydrates the repair items, explains the
condition and does not multiply a conditional result as guaranteed production.

The native registry then chooses its first matching ShapedRecipes or
ShapelessOreRecipe; other recipe kinds are explicitly classified as ignored by
this native selector. The machine ignores the shaped copy-NBT flag and copies
the registered output. Each occupied cell loses one, with no container callback.
Power tier determines completion threshold, not a fixed per-recipe duration.

NativeDefinitions binds selected and earlier native definitions, storage/member/
stack/item/tag identity, raw quantities/NBT, repair fields, wildcard content
variants and energy mode before/after capture and at sealing. The reader owns
copies, never the registry or borrowed NEI cache. Empty ready tables are valid;
missing, drift, cancellation and closed state have explicit outcomes.

The existing native-machine family test runs against the locally derived real
mod JAR, compares manager matching and native tile consumption, restores borrowed
roots, emits native-galaxy-repair-contract.json and guards unknown callbacks.
This is local conformance, not full-pack game export acceptance.
