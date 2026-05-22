# NeoNEI / NESQL++ 最终开发清单

> 目标：把 NeoNEI 从“运行时拼数据的网站”推进成“网页形式的 NEI 本地索引器”：NESQL++ 在游戏内导出完整契约，NeoNEI 编译成内容寻址发布包，前端基于 Manifest / Atlas / Bundle 秒开，并支持未来公共网站 CDN 分发。

## 总体原则

- 不为“技术高级感”迁移大框架；优先解决真实瓶颈：导出慢、缓存错乱、贴图/动图缺失、配方丢失、快速翻页卡顿。
- 保留 Express、SQLite、Vue、Canvas/WebGL 主线；补强契约、报告、内容寻址、增量构建、前端资源调度。
- NESQL++ 能解决的数据/导出问题优先在 NESQL++ 解决；NeoNEI 只消费稳定契约并做发布包优化。
- 每个阶段必须有可验证产物，不能靠人工翻页面猜测成功。

## 暂不作为主线

- [x] 不全面迁移 NestJS / Fastify（已确认为非主线）。
- [x] 不用 GraphQL 替代当前 REST + 静态 publish bundle（已确认为非主线）。
- [x] 不引入 Redis 作为当前主缓存层（已确认为非主线）。
- [x] 不把主页右侧浏览区回退成 DOM 虚拟列表（已确认为非主线）。
- [x] 不把 WebGPU 放入近期主线（已确认为非主线）。

## P0：完整性报告与版本契约

### NESQL++
- [x] 输出 `canonical/export-manifest.json`：记录仓库名、profile、selection、NESQL++版本、生成时间、关键产物路径。
- [x] 输出 `canonical/export-health-report.json`：兼容/升级现有 validation report，包含缺图、动图、Atlas、奇点动图可疑项、实体模型、多方块等统计。
- [x] 输出 `canonical/export-stage-timings.json`：记录每个导出阶段耗时。
- [x] 输出 `canonical/stage-checksums.json`：记录关键产物大小与 SHA-256，用于二次导出/编译跳过。
- [x] 输出缺失资源样本：贴图缺失、动画缺失、timeline frame 缺失、空 manifest。

### NeoNEI
- [x] 输出 `publish/build-report.json` 和可读 `publish/build-report.html`。
- [x] 检查 source manifest、browser layout、atlas、animated atlas、recipe bundle、search pack 是否匹配。
- [x] 报告基础发布包覆盖率、产物数量、压缩率与 warnings；缺图率/问号率/空配方率后续继续细化。

## P1：内容寻址发布系统 CAS

- [x] 所有核心 publish 产物按内容 hash 命名。
- [x] `publish/manifest.json` 记录 datasetVersion、sourceSignature、itemsHash、recipesHash、browserLayoutHash、atlasHash、animatedAtlasHash。
- [x] 前端 runtime cache key 完全绑定 manifest identity。
- [x] CDN 静态资源使用 immutable hash URL。
- [x] 旧缓存污染时能够自动失效。

## P2：浏览区 NEI 原生体感

- [x] NESQL++ 导出 NEI 浏览排序与折叠分组。
- [x] NeoNEI 前端消费 browser layout 并显示分组。
- [x] browser layout 与 atlas index 强绑定。
- [x] 分组展开结果预计算，避免运行时大计算。
- [x] 每页资源依赖预计算：当前页、前一页、后一页需要哪些 atlas/animated atlas。
- [x] 快速翻页取消旧页资源请求，只保留当前目标页最高优先级。
- [x] atlas miss 不阻塞主线程，缺图异步补齐。

## P3：配方页秒开 Bundle

- [x] 生成 `publish/item-recipe-bundles/shard-*.json`。
- [x] 生成 `publish/recipe-ui-bundles/shard-*.json`。
- [x] 每个 item bundle 包含 producedBy、usedIn、summaryGroups、machineGroups、firstPageRecipes、uiPayloadRefs、assetRefs。
- [x] 前端打开配方优先读取 static item bundle + IndexedDB runtime cache，后端 API 只作 fallback；OPFS 留给 P6 大资源层。
- [x] 常见物品配方摘要目标 <100ms，完整配方组目标 <300ms（NeoNEI 已建立 recipe-open-budget / recipe-bootstrap-resolved Gate；全量数据实测继续由 Gate C 追踪）。

## P4：导出 / 编译增量化

- [x] NESQL++ 阶段拆分：data、images、animated-images、render-contracts、browser-layout、multiblocks、eec-models、recipe-layout-contracts、atlas-pack（stage timing/checksum 现在输出 stable family/skippable contract）。
- [x] 每阶段有 checksum，可跳过未变化输出（stage-checksums 现在记录 sha256 / previousSha256 / changed / unchanged / skippableByChecksum；大目录摘要使用路径+大小结构签名，避免为数十万贴图重复全量读盘）。
- [x] 图片渲染按 item/render/texture signature 跳过（RenderJob 输出 render-signature sidecar，已有 PNG/GIF 只有签名匹配时才复用）。
- [x] Atlas packer 按 sprite hash / atlas page hash 跳过（static atlas shard 记录 sourceSignature、sourceSha256/sourceBytes、atlasPageSha256，复用时按 hash 校验）。
- [x] NeoNEI 编译按 item shard、recipe shard、search shard、atlas page、recipe bundle shard 增量跳过（NeoNEI 静态 bundle 已改为 write-if-changed，build report 输出 written/skipped 计数；atlas page 增量继续沿用内容 hash/manifest 校验）。

## P5：搜索体系升级

- [x] NeoNEI 编译阶段引入 SQLite FTS5 作为搜索包构建/后端兜底索引。
- [x] 字段覆盖中文名、英文名、拼音、首字母、模组名、item id、tooltip 关键词。
- [x] 前端继续使用 browserSearchWorker + hot/full shard，不回退实时 SQL 搜索。

## P6：前端大资源缓存升级

- [x] IndexedDB 存 manifest、search pack、browser layout、item bundle、小 JSON。
- [x] OPFS 试点存大 atlas、animated atlas、大 recipe bundle、entity preview、multiblock preview。
- [x] 缓存层级：Memory LRU → IndexedDB metadata → OPFS blob → HTTP/CDN → backend fallback。

## P7：Worker / OffscreenCanvas 渲染增强

- [x] Worker 计算浏览区 layout、draw commands、资源依赖。
- [x] 主线程只负责交互和最终绘制。
- [x] OffscreenCanvas 作为增强路径，必须保留 fallback。

## P8：公共网站工程化

- [x] publish 静态资源 CDN 化，hash 文件长期 immutable 缓存。
- [x] manifest 短缓存 / no-cache。
- [x] 后端只负责 fallback API、health report、admin rebuild、数据版本切换、诊断接口。
- [x] 管理接口增加 token、rate limit、请求校验、结构化日志、OpenAPI。

## 当前推进顺序

1. P0 NESQL++ 报告与 checksum 小闭环。
2. P0 NeoNEI build report 小闭环。
3. P1 manifest identity / cache key 强绑定。
4. P2 browser layout 与 atlas 资源依赖绑定。
5. P3 item-centric recipe bundle。
6. P4 增量导出与增量编译。

