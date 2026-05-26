# Raw Export 成熟化地基开发计划

日期：2026-05-26
状态：已批准，开始推进
硬性约束：Raw Export 目录、契约、阶段名、脚本名不再带路径级版本号；版本演进通过 `schemaVersion`、`manifest.capabilities` 与兼容策略表达，不把版本号写进目录名或主线命名。

## 目标

把 NESQL++ 的导出从“canonical 杂物箱 + 多处硬编码路径”升级为 Manifest 驱动的 Raw Export 地基：

```text
NESQL++ raw-export
  -> NeoNEI Data Compiler
  -> Runtime 发布包
  -> 前端常驻索引 / Atlas / Canvas 展示
```

NESQL++ 只负责游戏内真实数据采集和原始事实导出；NeoNEI Compiler 负责搜索索引、浏览分页、配方 UI payload、Atlas 与发布包。

## 不做的事

- 不再把新结构命名为带版本号的 Raw Export 目录。
- 不把前端搜索索引、网页分页包、Vue UI 结构塞回 NESQL++。
- 不直接删除旧 `canonical`；它先作为兼容层保留。
- 不靠低性能 fallback 修新链路；新 Raw Export 链路有问题就修新链路。

## 目标目录结构

```text
<export-name>/
  manifest.json
  export_report.json
  export_stage_timings.json
  stage_checksums.json

  raw-export/
    manifest.json

    facts/
      items.jsonl
      fluids.jsonl
      recipes/
        index.json
        by-handler/
          minecraft.crafting.jsonl
          minecraft.furnace.jsonl
          gregtech.assembler.jsonl
          gregtech.chemical_reactor.jsonl
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
      export-health-report.json
      missing-textures.json
      missing-animations.json
      missing-recipes.json

  compatibility/
    canonical/
```

## 阶段清单

### P0：命名与契约收口
- [x] 把 Raw Export 计划固定为无版本号主线。
- [x] 将 NESQL++ 旧 Raw Export 契约文件改为 `RAW_EXPORT_CONTRACT.md`。
- [x] 将 NESQL++ Raw Export writer / stage / 日志改成无版本号命名。
- [x] 将 NeoNEI Raw Export 编译脚本改成 `compile-raw-export.mjs`，npm 脚本改成 `compile:raw-export` / `test:raw-export`。

### P1：Manifest 驱动
- [x] NESQL++ `raw-export/manifest.json` 增加 `files` map，所有核心路径由 manifest 声明。
- [x] NeoNEI Compiler 优先读取 `raw-export/manifest.json`，旧路径仅作为兼容。
- [x] 增加 manifest schema 校验：缺文件、空文件、未知 capability 直接进入 validation 报告。

### P2：目录分域
- [x] NESQL++ 将首批 flat JSONL 双写到 `facts/`、`assets/`、`models/`、`validation/`。
- [x] 保留 root-level JSONL 兼容输出一轮，NeoNEI 验证通过后降级。
- [x] NeoNEI 编译器支持新路径与旧 flat sidecar 双读，优先新路径。

### P3：配方按 handler 分片
- [x] 输出 `facts/recipes/index.json`。
- [x] 输出 `facts/recipes/by-handler/*.jsonl`。
- [x] NeoNEI Compiler 支持按 handler 增量读取和校验。
- [x] 输出重复分类/分裂分类报告，避免熔炉、工作台、工业屠宰场等重复分裂。

### P4：资源与动画契约
- [x] 输出 `assets/textures/index.jsonl`。
- [x] 输出 `assets/animations/index.jsonl`。
- [ ] 原生 MC 动画 timing、frame、sprite metadata 全部进入 Raw Export。
- [ ] NeoNEI Compiler 基于资源索引生成全局 Atlas，不再猜单图 fallback。

### P5：特殊系统拆分
- [ ] GT 数据进入 `special/gregtech/`。
- [ ] 神秘时代要素、坩埚、奥数、注魔进入 `special/thaumcraft/`。
- [ ] 植物魔法 mana、花药台、魔力池、符文祭坛、泰拉凝聚板进入 `special/botania/`。
- [ ] 血魔法 LP、祭坛、法阵、绑定仪式进入 `special/bloodmagic/`。
- [ ] 工业屠宰场实体掉落和模型进入 `special/eec/` 与 `models/entities/`。

### P6：验证和切换
- [ ] 新旧导出数量对比：items / fluids / recipes / groups / textures / animations。
- [ ] 新旧 NeoNEI 编译结果对比：搜索、浏览区、配方、贴图、动画。
- [ ] 连续导出验证稳定后，把 `canonical` 降级为 compatibility。

## 验收标准

- NESQL++ 构建通过。
- NeoNEI Raw Export self-test 通过。
- NeoNEI typecheck/build 通过。
- 新结构不破坏现有 `canonical` 导入。
- 新 plan / contract / scripts 不再出现带路径级版本号的 Raw Export 命名。