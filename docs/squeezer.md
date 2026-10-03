# Forestry Squeezer capture

Development 0.37.0 writes Source 34 for Compiler/contracts 0.34.0 and Catalog
33. This is a local implementation checkpoint, not a game release or a claim of
complete Forestry coverage.

The production router recognizes Forestry 4.10.17's exact NEI Squeezer handler.
One owned shared program stores native ordinary recipes, ordered container
rules, Forge fixed-fluid entries and the dynamic callback registry. Recipe
records bind this program and an ordinary/fixed-container selector into their
identity. The program is published once; verification runs at each client-thread
batch boundary so registry changes cannot silently mix rule generations.

Ordinary and fixed-container entries preserve signed i32 requirements, fluid
amounts, remnants, processing time, float probability bits and typed NBT. Null
input holes retain their original slots. Because the native cached constructor
rejects null holes, it receives only the visible copies and their coordinates
are restored to the original 3×3 layout. No source recipe or registered stack is
changed. The native cached class still owns fluid tank, remnant and background
rendering. Input examples do not replace the shared stock/selection semantics.

`forestrySqueezer` records use allocated inputs and conditional `squeezer`
quantities, not independent fixed-output rolls. Remnant chance uses the exact
discrete Java float random probability. Zero chance removes that item from
possible-output lookup while preserving its native space-preflight condition.
Work-step and RF parameters are retained; they are not fixed wall time or EU/t.

The NEI progress capture retains the integer `processingTime * 5` expression.
Positive cycles through 72,000 ticks are compressed by locating pixel
transitions with native float rounding, rather than scanning every tick. Zero
periods (native modulo by zero), negative periods and cycles outside the motion
contract budget remain explicit `view_unsupported`; they are not clamped to a
different animation. Semantic-only capture still retains signed work parameters.

Dynamic `IFluidContainerItem` callbacks remain explicit `recipe_unsupported`
suffix entries. They are not executed during fixed-registry enumeration and
cannot be counted as completed recipes. Callback adapters, Still source
reservation/selection and Centrifuge pending-product lifecycle remain open.

Verification extends existing source/jobs/native-Forestry runners: ordinary and
fixed native records, signed/zero/null projection, input-slot preservation,
registry mutation and unknown callbacks. Compiler fixtures cross-check records
against the shared program and reject altered identity/count/chance/process
fields. Online/offline browser checks use compiled fixtures; live export and
full registry coverage are reserved for the unified external acceptance run.
