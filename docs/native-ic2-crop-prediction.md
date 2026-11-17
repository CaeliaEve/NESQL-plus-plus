# IC2 crop prediction

The reader targets the published tables in the installed IC2 Crop Plugin 1.3.1.
It never calls `Breeder.load/run`, constructs a GUI recipe, writes a cache, or
calls a crop's `owner/name/stat` methods. An unpublished `craft` or `usage` table
is `handler_not_ready`; published empty tables remain a valid empty result.
Four-registration discovery binds the shared breeder, display API and crop
registry authorities without awaiting their prediction tables. The reader's
open/seal binds the actual published tables. This keeps an unfinished predictor
from preventing unrelated handler discovery, without treating it as ready.

Crop identities come from the actual IC2 `cropMap` registration keys. Crops are
opaque identity leaves. Display specimens come from the published
`CropPluginAPI.displayItems` map, exactly the map used by `getDisplayItem`, but
the reader audits and copies its stacks without invoking that method or a
`BreedResult` callback. Unknown table/list/result owners are rejected before
any collection or result callback executes. Missing native display data is a
failure, not a transparent placeholder or an excluded prediction.

Both lookup directions are checked. Crafting groups must refer to their result
crop, usage groups must refer to an input parent and an equivalent published
crafting prediction, and no crafting parent may be missing from usage. Parent
order, two through four distinct parents, exact `points/total`, and the original
denominator remain intact. Parent plants are retained rather than consumed
seeds. A localized note preserves the native `drawExtras` warning: the predictor
assumes impossible perfect conditions, so its weights are not measured in-world
success rates. No native GUI image is needed to carry this limitation.

Each batch checks admitted root identities and table cardinalities. Final seal
also checks group/list/member identities and order, result/parent-array
identities, individual parent references, weights, registry keys, and complete
display stack/NBT graphs. Equal-value replacement still invalidates the seal.
Sorting and display specimen creation happen once per reader. Close releases
owned snapshots and borrowed references without clearing native tables. These
bindings are same-session evidence, not a cross-restart reuse proof.

Limits are explicit: 65,536 groups/crop identities, 262,144 unique predictions,
1,310,720 table memberships, 64 MiB of prediction snapshots, and the shared native-definition graph's byte and
identity limits for the crop/display registry. Breaches are reported as limits;
no partial table is silently presented as complete.

`CropPredictionTest` now uses real native classes and scoped published static
registries through the existing `crop-prediction` family. It covers exact
retained-parent transport, equal-value source replacement, unknown storage
with zero callback execution, unpublished/empty distinction, and closure.
The current implementation and these migrated cases await the single final
lightweight verification batch; no native or game pass is claimed here.
