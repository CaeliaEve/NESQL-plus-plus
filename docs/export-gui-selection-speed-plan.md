# NESQL++ 导出 GUI 与提速开发清单

目标：修复 `/nesql [文件名]` 不弹 GUI 的问题，并把默认命令升级为“先选择导出模块，再启动导出”；同时在不改变现有 NeoNEI 输出结构的前提下，先加入可观测耗时与安全跳过能力，为后续增量/并行提速铺路。

## 约束
- 不改现有导出目录结构与字段语义。
- `/nesql [文件名]` 默认全选，行为等价于旧全量导出。
- 选择器只控制现有阶段是否执行，不引入不兼容新格式。
- 渲染/NEI/MC 注册表读取保持安全线程模型；先优化 GUI 生命周期与耗时可观测性。

## 阶段清单
- [x] 评估命令、GUI、ExportProfile、ExportExecutionPlan、ExportStageRunner 链路。
- [x] 增加 ClientGuiScheduler：所有 GUI 打开/关闭走客户端 tick 安全调度。
- [x] 增加 ExportSelection：描述本次导出启用的输出阶段。
- [x] 增加 ExportSelectionGui：`/nesql [文件名]` 先弹勾选页面，默认全选。
- [x] 将 ExportContext / ExportExecutionPlan 接入 ExportSelection，不破坏旧 profile。
- [x] ExportStageRunner 增加每阶段耗时日志，用于定位 5 小时导出瓶颈。
- [x] 构建验证：`./gradlew.bat build -x test` 通过。

## 后续提速路线
- [x] 增量渲染缓存第一轮：已有 output + render contract + sprite sidecar 时跳过重新渲染。
- [x] 渲染队列去重：同一轮导出内相同输出路径只渲染一次。
- [x] Atlas 打包并行化第一轮：静态/动画 atlas 按 group 使用有界 IO worker 并行打包。
- [x] 部分导出不再强制新目录：只有默认全量导出保持旧的“目录存在则停止”保护。
- [x] Atlas 增量写入第一轮：未变化 atlas group 复用旧 atlas PNG 与 group shard。
- 有界并行 IO：继续扩大到 manifest/压缩写入，但不并行访问 MC/NEI 世界对象。
- [x] 阶段耗时报告：导出 `canonical/export-stage-timings.json`。
- [x] 导出后校验报告第一轮：items/recipes/images/atlas 基础计数与 warning。
- [x] 导出后校验报告历史对比第一轮：记录 previous/delta 关键计数。
- [x] 导出后校验报告 missing texture 第一轮：render-assets 主产物与 timeline frame 缺失计数。
- 后续强化：用 content hash 判断纹理/renderer 变化，减少误复用。
## Browser layout-only export
- [x] Added `/nesql-browser-layout [repository suffix]` to rebuild only `canonical/browser-layout-index.json`.
- [x] Reuses the existing `nesql-db`; skips recipe collection, image rendering, atlas writes, snapshots, multiblocks, and entity models.
- [x] Intended for fast NEI item order / collapsible group refresh after a previous full export.
