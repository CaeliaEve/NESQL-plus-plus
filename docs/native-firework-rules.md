# Fireworks without GUI caches

The firework reader binds the real `CraftingManager` rule and native dye-color
array. It no longer reads `FireworkRecipeHandler.mfireworks`, creates a cached
GUI recipe, requires the NEI item panel, or validates the 19 cycling preview
entries. The `nativeTemplates` field is retired from the current three-project
contract: a GUI preview count is not a gameplay rule.

The three native rules, grid order, color/effect/shape identities, conditional
Flight, retained fade tags and malformed-NBT behavior stay unchanged. Examples
run an owned `RecipeFireworks` instance against an owned crafting grid. They do
not mutate the registered native recipe's scratch output. The definition and
examples describe this specific native rule; they do not prove that an earlier
custom crafting rule cannot claim a matching inventory.

Batch guards check the admitted manager, rule/list identity and cardinality,
and the dye array identity. Seal also checks crafting-member identity/order and
replays the bounded owned examples to check colors and outputs. The four NEI
registrations share these semantic source anchors without comparing their
independent GUI preview caches. Closure releases borrowed references.

The existing Java case now includes an actual registered rule and an allocated
handler whose GUI fields are absent. New schema/contracts are still awaiting
the final combined generation and verification. Historical fixtures are kept
byte-for-byte; any test migration is explicit and in memory, not a production
fallback or a rewritten historical receipt. No pass is asserted here.
