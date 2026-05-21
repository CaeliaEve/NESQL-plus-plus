# NeoNEI / NESQL++ 最终开发清单

> 目标：把 NeoNEI 从“运行时拼数据的网站”推进成“网页形式的 NEI 本地索引器”：NESQL++ 在游戏内导出完整契约，NeoNEI 编译成内容寻址发布包，前端基于 Manifest / Atlas / Bundle 秒开，并支持未来公共网站 CDN 分发。

## 总体原则

- 不为“技术高级感”迁移大框架；优先解决真实瓶颈：导出慢、缓存错乱、贴图/动图缺失、配方丢失、快速翻页卡顿。
- 保留 Express、SQLite、Vue、Canvas/WebGL 主线；补强契约、报告、内容寻址、增量构建、前端资源调度。
- NESQL++ 能解决的数据/导出问题优先在 NESQL++ 解决；NeoNEI 只消费稳定契约并做发布包优化。
- 每个阶段必须有可验证产物，不能靠人工翻页面猜测成功。

## 暂不作为主线

- [ ] 不全面迁移 NestJS / Fastify。
- [ ] 不用 GraphQL 替代当前 REST + 静态 publish bundle。
- [ ] 不引入 Redis 作为当前主缓存层。
- [ ] 不把主页右侧浏览区回退成 DOM 虚拟列表。
- [ ] 不把 WebGPU 放入近期主线。

## P0：完整性报告与版本契约

### NESQL++
- [x] 输出 `canonical/export-manifest.json`：记录仓库名、profile、selection、NESQL++版本、生成时间、关键产物路径。
- [x] 输出 `canonical/export-health-report.json`：兼容/升级现有 validation report，包含缺图、动图、Atlas、奇点动图可疑项、实体模型、多方块等统计。
- [x] 输出 `canonical/export-stage-timings.json`：记录每个导出阶段耗时。
- [x] 输出 `canonical/stage-checksums.json`：记录关键产物大小与 SHA-256，用于二次导出/编译跳过。
- [x] 输出缺失资源样本：贴图缺失、动画缺失、timeline frame 缺失、空 manifest。

### NeoNEI
- [x] 输出 `publish/build-report.json` 和可读 `publish/build-report.html`。
- [ ] 检查 source manifest、browser layout、atlas、animated atlas、recipe bundle、search pack 是否匹配。
- [x] 报告基础发布包覆盖率、产物数量、压缩率与 warnings；缺图率/问号率/空配方率后续继续细化。

## P1：内容寻址发布系统 CAS

- [ ] 所有核心 publish 产物按内容 hash 命名。
- [x] `publish/manifest.json` 记录 datasetVersion、sourceSignature、itemsHash、recipesHash、browserLayoutHash、atlasHash、animatedAtlasHash。
- [x] 前端 runtime cache key 完全绑定 manifest identity。
- [ ] CDN 静态资源使用 immutable hash URL。
- [x] 旧缓存污染时能够自动失效。

## P2：浏览区 NEI 原生体感

- [x] NESQL++ 导出 NEI 浏览排序与折叠分组。
- [x] NeoNEI 前端消费 browser layout 并显示分组。
- [x] browser layout 与 atlas index 强绑定。
- [ ] 分组展开结果预计算，避免运行时大计算。
- [x] 每页资源依赖预计算：当前页、前一页、后一页需要哪些 atlas/animated atlas。
- [x] 快速翻页取消旧页资源请求，只保留当前目标页最高优先级。
- [x] atlas miss 不阻塞主线程，缺图异步补齐。

## P3：配方页秒开 Bundle

- [ ] 生成 `publish/item-recipe-bundles/shard-*.json`。
- [ ] 生成 `publish/recipe-ui-bundles/shard-*.json`。
- [ ] 每个 item bundle 包含 producedBy、usedIn、summaryGroups、machineGroups、firstPageRecipes、uiPayloadRefs、assetRefs。
- [ ] 前端打开配方优先读取 bundle / IndexedDB / OPFS，后端 API 只作 fallback。
- [ ] 常见物品配方摘要目标 <100ms，完整配方组目标 <300ms。

## P4：导出 / 编译增量化

- [ ] NESQL++ 阶段拆分：data、images、animated-images、render-contracts、browser-layout、multiblocks、eec-models、recipe-layout-contracts、atlas-pack。
- [ ] 每阶段有 checksum，可跳过未变化输出。
- [ ] 图片渲染按 item/render/texture signature 跳过。
- [ ] Atlas packer 按 sprite hash / atlas page hash 跳过。
- [ ] NeoNEI 编译按 item shard、recipe shard、search shard、atlas page、recipe bundle shard 增量跳过。

## P5：搜索体系升级

- [ ] NeoNEI 编译阶段引入 SQLite FTS5 作为搜索包构建/后端兜底索引。
- [ ] 字段覆盖中文名、英文名、拼音、首字母、模组名、item id、tooltip 关键词。
- [ ] 前端继续使用 browserSearchWorker + hot/full shard，不回退实时 SQL 搜索。

## P6：前端大资源缓存升级

- [ ] IndexedDB 存 manifest、search pack、browser layout、item bundle、小 JSON。
- [ ] OPFS 试点存大 atlas、animated atlas、大 recipe bundle、entity preview、multiblock preview。
- [ ] 缓存层级：Memory LRU → IndexedDB metadata → OPFS blob → HTTP/CDN → backend fallback。

## P7：Worker / OffscreenCanvas 渲染增强

- [ ] Worker 计算浏览区 layout、draw commands、资源依赖。
- [ ] 主线程只负责交互和最终绘制。
- [ ] OffscreenCanvas 作为增强路径，必须保留 fallback。

## P8：公共网站工程化

- [ ] publish 静态资源 CDN 化，hash 文件长期 immutable 缓存。
- [ ] manifest 短缓存 / no-cache。
- [ ] 后端只负责 fallback API、health report、admin rebuild、数据版本切换、诊断接口。
- [ ] 管理接口增加 token、rate limit、请求校验、结构化日志、OpenAPI。

## 当前推进顺序

1. P0 NESQL++ 报告与 checksum 小闭环。
2. P0 NeoNEI build report 小闭环。
3. P1 manifest identity / cache key 强绑定。
4. P2 browser layout 与 atlas 资源依赖绑定。
5. P3 item-centric recipe bundle。
6. P4 增量导出与增量编译。




