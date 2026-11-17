# AE2 facade semantic reader

This adapter targets the installed AE2 rv3-beta-695-GTNH artifact, not the newer
GTNH source checkout. In particular, the installed FacadeConfig does not contain
the newer IFacadeControl callback. Native JAR inspection and the existing offline
native API runner establish the rules below; these are not game-session or REA
runtime evidence.

The reader opens before the Template handler factory. It never constructs a
CachedShapedRecipe/PositionedStack, writes arecipes, invokes getFacades (which lazily
initializes borrowed examples), or invokes createFacadeForItem/Configuration.get
(which may create/update native configuration). It reads the original, already
initialized ArrayList of facade examples. A null list or absent configuration
value is unready, while an initialized empty list is a valid empty domain.

The actual Api/ApiDefinitions/ApiItems/ApiParts tree, facade ItemDefinition, cable
anchor DamagedItemDefinition and immutable ItemStackSrc are bound by identity.
Arbitrary definition/stack-source proxies are rejected without invoking them.
Configuration, category/property storage and property identities, case policy,
replacement pattern, default Locale, anchor item/metadata, example identity/order,
texture registry objects and facts are checked before/after rows and at sealing.
Closing releases owned guards and maps, preserving borrowed lists/config/cache.
An original callback/fact failure remains primary, with later drift suppressed.

The recipe keeps the native 3x3 cross: four cable anchors, an untagged central
block texture, empty corners and four new facades. Anchor matching ignores NBT;
even an empty root NBT is forbidden on the texture. No per-recipe container
callback is invented. The output uses the native item numeric ID, registered
modid/itemname and item-to-block metadata transformation.

The pinned metadata resource declares reviewed integer mappings for 304 distinct
instance-method owners found in the installed artifacts (including vanilla),
plus 19 EtFuturum getTypes field getters. This is an owner inventory, not a count
of handlers or game-validated examples. Simple integer mappings never execute
the item callback. AE2/XU flags, EtFuturum instance/static type arrays, native
Shulker default block metadata and IronChest enum sizes retain their native
meaning and bind mutable dependencies. Mod class resources must match reviewed
installed/mechanically remapped artifact SHA256 values. Vanilla mappings use
exact declaring classes from the pinned Minecraft 1.7.10 environment. Unknown
owners/resources/getters fail explicitly; this is not an arbitrary bytecode
interpreter. These resource audits do not prove arbitrary runtime transformer
changes, and formal game preflight remains required.

Some mappings are non-idempotent. The real Galacticraft enclosed-item callback
maps metadata 0 to 4 and 4 to 0. The input remains its original decoded texture;
process.textureMetadata records a different normalized output value when needed.
The Compiler checks canonical facade output NBT against that value and rejects
negative/wildcard output metadata. Native Java observations are replayed through
the Rust validator and the shared strict frontend decoder.
