# AE2 in-world acquisition

The semantic reader targets installed AE2 rv3-beta-695-GTNH. It reads actual
Api/ApiDefinitions/ApiMaterials/ApiItems and AEConfig definitions, without calling
NEI getRecipeHandler/getResultStack or constructing PositionedStack/GUI selections.
It remains a RegistryRecipes implementation even though its registered native
handler directly implements ICraftingHandler/IUsageHandler.

Native ItemDefinition, DamagedItemDefinition with ItemStackSrc or MaterialStackSrc,
and the relevant MaterialType item/metadata fields are read without invoking an
unknown provider. Identities, optional/source objects, feature-set identity and
contents, item objects/metadata, native ore tables, member identity/order/facts,
and any required finite metadata catalogue are bound through sealing. Disabled
feature domains can be ready-empty; missing active configuration/definitions are
unready. Irrelevant disabled input providers are not traversed. Closing clears
only owned pages/bindings. Original failures remain primary, with later drift
suppressed. Independent registration instances bind the same native source.

The nine actions retain charged-ore and meteorite acquisition, three fluix
ingredients, singularity+ender dust, and the three native seed progress bands.
Growth accepts liquid material, with world-dependent timing. Native descriptions
are resolved through GuiText/Localization, not reconstructed from rendered text.
Outputs remain conditional potential observations, not guaranteed free crafts.
Localized explanations now use a stable `ae2:world/<material>` note field with
the exact GuiText enum source. They no longer enter the typed process or semantic
recipe hash. All acquisition actions, item/NBT identities and native matching
remain in the process; explanations remain ordinary localized string records.
Changing language during one capture is still rejected by its sealed text
snapshot. Separate language jobs do not acquire cross-language resume permission.

EntitySingularity iterates getOres(dustEnder/dustEnderPearl) and calls
itemMatches(offered, registered, false). This exact argument direction is unlike
generic ore membership. The typed ae2EnderDust predicate records the native ore
source and registered metadata; same-item offered metadata must equal that value
or 32767. NBT and registered stack count are ignored. A registered wildcard does
not admit every concrete metadata. A symbolic wildcard can have a concrete
native display representative, which must not gain a positive use link when it
is not accepted. NeoNEI labels this rule and keeps such material requirements
conditional. Truly empty native ore tables have an explicit unreachable proof.
Absent ore names are read without registering new entries.

An installed-JAR resource scan found no external EntitySingularity/ore-matcher
transformer candidate beyond the ordinary AE2 MaterialType reference. That scan
is not proof of live transformed bytecode. Ordinary native API tests are neither
game execution nor REA runtime evidence. The existing runner isolates API/enum
startup and blocks GUI selectors; instance material/definition/growth methods and
OreDictionary.itemMatches remain native. Twenty-four direct matcher observations
are replayed by the Rust matching test. Source/Catalog and actual frontend
strict-decoder checks cover ten complete Java rows, including a symbolic input.
The installed JAR's actual en_US.lang and zh_CN.lang resources now cross the
native Localization callback in the same runner: all nine recipe IDs match
across languages, their text references differ, and source/field bindings remain
intact. New ae-world-locale-0..8-en_US/zh_CN and ae-world-ender-notes files are
the current transport fixtures. Older raw-description fixtures stay historical.
The Compiler requires the exact note source, rejects missing explanations and
the retired process.description field, and the shared API hydrates the text for
the existing webpage notes component. This is contracts semantic.44 on the
unreleased Source 40 / Catalog 39 development chain, not the unified release.
Whole-system entity/locale association, UI, search and offline language bundles
remain mandatory. No whole-pack, pixel, complete English or production latency
result is claimed.
