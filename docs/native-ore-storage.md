# Forge and Hodgepodge ore storage

NESQL++ 0.45.0-semantic.4 retains Source revision 40. It admits the exact original
Forge storage and the installed Hodgepodge 2.6.112 transformation:

| Role | Forge | Hodgepodge |
| --- | --- | --- |
| nameToId | HashMap | Object2IntOpenHashMap, default return value -1 |
| stackToId | HashMap | Int2ObjectOpenHashMap, default return value null |
| Baked reverse IDs | ArrayList of integers | IntArrayList |
| idToName / idToStack / idToStackUn | ArrayList | ArrayList, unchanged |
| Item member lists and unmodifiable views | Original Forge owners | Unchanged |

NativeOreStorage shares this admission across AE2 world/ingredients, Forge
registry references, Botania borrowed ores, IC2 advanced inputs, native/legacy
infusion, Salis wand bindings, native arcane recipes, banners, CreativeCore and
Chisel. It does not accept arbitrary Map/List implementations or subclasses.
Unknown callback owners, invalid IDs and existing size limits still fail.
NativeDefinitionGraph observes the admitted primitive containers and their
default return values, preserving same-instance and same-size drift checks.

Hodgepodge also replaces the temporary HashSet inside getOreIDs with
IntOpenHashSet. Its encounter order can select a different Chisel group where
exact and wildcard ores overlap. Chisel uses an owned set with the actual
primitive addAll operation when reading this storage form. Group selection,
target count/NBT and per-target membership are checked against the real native
Chisel lookup. No item/registry callback is needed for the owned set operation.

The small offline suite applies the installed transformer's original method
body to Forge classes in an isolated LaunchClassLoader. Config and registry
fixtures are owned by the test; no running client/world is used. It covers
both storage variants, absent-name reads without registration, owned copies,
reverse membership, unknown-callback rejection, value/default drift, AE2
registration/world rules, Forge references and Chisel native parity.

These checks do not establish a successful live GET /game or full-pack export.
A newly installed candidate and fresh runtime identity/diagnostic gates are
required; previous failed attempts and captures remain unchanged.
