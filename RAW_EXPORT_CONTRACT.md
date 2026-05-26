# NESQL++ Raw Export Contract

Status: approved mainline contract
Rule: Raw Export naming must not include path-level version suffixes in directories, public stage families, scripts, or primary documentation. Schema evolution is represented by `schemaVersion` and manifest capabilities, not by path names.

This contract aligns NESQL++ with the approved NeoNEI rebuild. NESQL++ is the in-game truth collector. It exports complete raw facts, not frontend-specific runtime structures.

## 1. Exporter Role

NESQL++ owns:

- collecting real GTNH / NEI / mod-handler data inside Minecraft
- preserving game-native ordering and grouping
- capturing item/fluid/recipe/entity/multiblock/texture facts
- producing validation and stage timing reports
- producing stable checksums for incremental compiler decisions

NESQL++ should not own:

- frontend search index shape
- browser page-pack shape
- web-specific atlas packing policy
- Vue recipe UI logic

Those belong to the NeoNEI data compiler.

## 2. Target Raw Export Layout

```text
raw-export/
  manifest.json

  facts/
    items.jsonl
    fluids.jsonl
    recipes/
      index.json
      by-handler/
        <handler-id>.jsonl
    nei/
      handlers.jsonl
      order.jsonl
      groups.jsonl

  assets/
    textures/
      index.jsonl
      items/
      fluids/
      aspects/
      entities/
    animations/
      index.jsonl
      native-sprites.jsonl
      rendered-gifs.jsonl

  models/
    entities/
      index.jsonl
      by-entity/
    multiblocks/
      index.jsonl
      by-id/

  special/
    gregtech/
    thaumcraft/
    botania/
    bloodmagic/
    forestry/
    eec/

  validation/
    export_report.json
    export_stage_timings.json
    stage_checksums.json
    export-health-report.json
```

During migration, root-level compatibility files may remain under `raw-export/`:

```text
items.jsonl
fluids.jsonl
recipes.jsonl
groups.jsonl
nei_order.jsonl
textures.jsonl
animations.jsonl
browser_atlas_index.json
```

They are compatibility targets, not the final mature layout.

## 3. Manifest Rule

`raw-export/manifest.json` is authoritative. NeoNEI Compiler must read declared paths from the manifest before trying legacy guesses.

Minimum manifest fields:

```json
{
  "schemaVersion": "nesqlpp/raw-export/alpha1",
  "repositoryName": "example",
  "profile": "full-v104",
  "generatedAt": "2026-05-26T00:00:00Z",
  "capabilities": ["facts", "assets", "validation"],
  "files": {
    "items": "facts/items.jsonl",
    "fluids": "facts/fluids.jsonl",
    "recipeIndex": "facts/recipes/index.json",
    "neiGroups": "facts/nei/groups.jsonl",
    "textureIndex": "assets/textures/index.jsonl",
    "animationIndex": "assets/animations/index.jsonl",
    "validationReport": "validation/export-health-report.json"
  }
}
```

## 4. Search-Relevant Item Fields

`facts/items.jsonl` must eventually provide enough data for the compiler to build the resident browser search core:

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

## 5. Validation Requirements

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
- checksum changes compared with the previous run when available

## 6. Migration Rule

Keep the current canonical export working while adding Raw Export sidecars. The compiler migration consumes Raw Export first and falls back to canonical only for compatibility until parity checks are stable.