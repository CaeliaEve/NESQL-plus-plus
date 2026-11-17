# Gadomancy disguise and Thaumic Machina infusion

Implementation notes, 2026-10-21. These changes belong to the pending unified
batch. They have not been compiled, tested, generated into the contracts package,
or exercised in Minecraft. `readyForExport` remains false.

## Disguise invocation protocol

`DisguiseInfusionRecipes` reads the registered exact
`makeo.gadomancy.common.crafting.InfusionDisguiseArmor` before the Runic route.
It does not call `matches`, `getComponents(input)`, `getRecipeOutput(input)` or
`getAspects(input)` on that borrowed recipe. Those methods communicate through
`cachedItems`; evaluating them individually is not a definition reader.

The inspected altar calls them in this order:

1. Find a matching recipe using copied central and pedestal stacks.
2. Read its input-dependent components and copy those components.
3. Read its input-dependent output.
4. Read instability and then aspects. The disguise recipe clears its cache in
   this final aspects call.

The dedicated `disguiseInfusion` process records the fixed four requirements,
the ore mode, observed comparison states and the actual base-pedestal limit.
Its result action carries correlated input tuples. It preserves:

- Exactly five surrounding inputs, with four declaration-order greedy matches.
  Startup compares **required first**, without a wildcard metadata rewrite.
  The one remaining input is the disguise, regardless of its displayed slot.
- The native armor position, including pumpkin and skull headgear. Both center
  and disguise must lack a `gadomancy/disguise` tag of any type or value.
- An invisibility potion or matching armor position. A null potion effect list
  is a native error, not a successful transparent disguise.
- Separate later pedestal matching, in native cached component order, against
  twice-copied surrounding inputs. Startup and cycle first matches are not
  merged into one predicate.
- Full-slot pedestal replacement and direct native container results, capped
  to the observed pedestal inventory limit.
- Copying the center. A potion writes byte true; armor writes the native
  `ItemStack.writeToNBT` result with its numeric short item id, count, damage
  and tag. Registry text is not substituted for that numeric id.
- Research `GADOMANCY.ARMORDISGUISE` and native instability -20.

Native NBT string observations are recorded independently before and after
copies. Canonical JSON cannot reconstruct native compound iteration order.
Definitions, content variants, potion results, comparison observations and
containers are checked again before sealing. The reader owns its samples and
clears them on close and constructor failure.

Potion observations consult an already populated native `effectCache` without
writing it. On a miss, the pure `PotionHelper` path is admitted only for the
reviewed transitive getter owners; unknown overrides fail explicitly. Owned
observations are memoized and re-evaluated at sealing. MCP/SRG reflection names
are resolved on the native Potion class. The visual-only disguise recipe is
recorded as a display projection and never evaluated: its player, clock, RNG
and temporary global internal-method replacement are not execution rules.

Choices are native one-item specimens, not an enumeration of every NBT state.
Each exposed choice has a valid complete tuple. A branch with no valid sample
fails rather than being called an unreachable recipe. Tuple count, observation
bytes and result bytes are bounded. The web UI links the tuple and result and
keeps material totals conditional; Chinese and English notes are separate.

## Machina wand augmentation

`MachinaInfusionSource` accepts the exact
`jcm2606.thaumicmachina.core.crafting.infusion.RecipeInfusionWandAugmentation`
and the five reviewed stateless augmentation classes. The getter owners and
instance shape are checked before reading names and prerequisites. No arbitrary
interface callback or substring class match is used.

The `nativeInfusion` process now distinguishes the `legacy` and `itemAndTags`
startup matchers. Machina's private comparator is called with `fuzzy=false` and
then compares only item identity and the asymmetric required NBT subset. It
does **not** compare metadata, ore groups or stack size at startup. Later
pedestal consumption still uses the altar's ordinary comparison mode and the
registered component copies. Both phases must match.

Native matching checks the **definition's** item is a wand, not the offered
center's class. A center must lack the target augmentation name and contain
every prerequisite name. Taint Capping needs Tainted Core; the other four have
no prerequisites. Owned display specimens complete these prerequisites without
claiming that every state is obtainable.

Output copies the center and appends the target string to `Augmentations`.
The native getter produces a detached empty list for a scalar or wrong list
type, but the output subsequently attaches that list: such malformed values
are replaced. This differs from a generic strict append action.

Both dynamic readers bind the TC4Tweaks output-override registry. Its altar
hook checks API overrides before the custom-output exemption; an unknown API
override must not be silently bypassed merely because the class overrides its
output method. No ClassValue or WeakHashMap cleanup API is invoked.

## Shared ore lookup

The shared infusion context builds an Item-identity membership index once and
does not scan every ore group for every new item/metadata predicate. Unrelated
items' metadata and NBT callbacks remain deferred. Each relevant ore group has
one shared source graph. Sealing rebuilds the membership index and rechecks
the bound predicates and native groups. These are implementation changes, not
a measured throughput claim.

## Static evidence and limits

Gadomancy repository sources: `InfusionDisguiseArmor`,
`InfusionVisualDisguiseArmor`, `NBTHelper` and `SimpleResearchItem`.
Ordinary `javap` inspection also covered:

- Installed `gadomancy-1.4.8.jar`, SHA256
  `90fb7e230a18a0eac65645aedc5c205acbc53844d95154850f90a6161beeaa26`.
- Installed `Thaumic Machina-1.7.10-0.2.1.jar`, SHA256
  `745edc4c412336944198b90bcb4c30a89b8b3181eb3c937244624b1ef7d8d7b6`.
- Existing remapped `build/native-tests/thaumcraft.jar` altar call order and
  the Gradle cached Forge 1.7.10 `forgeBin` classes for EntityLiving, ItemPotion,
  PotionHelper and PotionEffect; MCP stable-12 mappings resolve reflection names.

The earlier `build/source-b1b1a48759974963af839411b43cef63/environment/minecraft.jar`
is currently three bytes and cannot be inspected as a JAR. It was preserved;
these inspections used the existing Forge cache instead. Failed commands are
not execution evidence. None of the above is REA evidence or an observation of
gameplay. Three compact new Rust cases are written for Machina startup/list
semantics and disguise comparison direction; they have not been run.

The final Source/Catalog/contracts version, generated schemas and legacy reader
boundary remain pending with the rest of the unified batch. TC4Tweaks enhanced
ingredients, Runic providers and the remaining project-wide items are not
declared complete by this document.
