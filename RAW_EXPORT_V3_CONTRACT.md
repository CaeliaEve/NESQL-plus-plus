# NESQL++ Raw Export V3 Contract

Branch: `rebuild/neonei-runtime-v3`

This contract aligns NESQL++ with the approved NeoNEI rebuild. NESQL++ should become the in-game truth collector. It should export complete raw facts, not frontend-specific runtime structures.

## 1. Exporter Role

NESQL++ owns:

- collecting real GTNH / NEI / mod-handler data inside Minecraft
- preserving game-native ordering and grouping
- capturing item/fluid/recipe/entity/multiblock/texture facts
- producing validation and stage timing reports

NESQL++ should not own:

- frontend search index shape
- browser page-pack shape
- web-specific atlas packing policy
- Vue recipe UI logic

Those belong to the future NeoNEI data compiler.

## 2. Target Raw Export Layout

```text
raw-export/
  manifest.json
  items.jsonl
  fluids.jsonl
  recipes.jsonl
  groups.jsonl
  nei_order.jsonl
  textures.jsonl
  animations.jsonl
  nei_handlers.jsonl
  multiblocks.jsonl
  entities.jsonl
  export_report.json
  export_stage_timings.json
  stage_checksums.json
```

## 3. Search-Relevant Fields

`items.jsonl` must eventually provide enough data for the compiler to build Search Core V3:

```json
{
  "itemId": "i~mod~name~damage",
  "modId": "gregtech",
  "internalName": "gt.metaitem.01",
  "localizedName": "Example Item",
  "unlocalizedName": "item.example.name",
  "damage": 0,
  "tooltip": "...",
  "searchTerms": "...",
  "browserGroupKey": "...",
  "browserGroupLabel": "...",
  "browserGroupSize": 1,
  "neiOrder": 12345,
  "renderAssetRef": "...",
  "renderHint": {
    "hasAnimation": false,
    "frameCount": 1,
    "playbackHint": "minecraft-native"
  }
}
```

## 4. Validation Requirements

Every raw export must report:

- item count
- fluid count
- recipe count
- NEI handler count
- missing texture count
- missing animation metadata count
- missing group/order count
- failed stages
- stage timings
- stage checksums

## 5. Migration Rule

Keep the current canonical export working while adding V3 raw-export sidecars. The compiler migration can then consume the new sidecars without breaking the existing NeoNEI import path.

