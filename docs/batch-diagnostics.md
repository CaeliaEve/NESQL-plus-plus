# 集中诊断

0.45.0-semantic.7 提供只诊断模式，用来在一次安全可执行的完整轮次中收集独立问题，再集中修复。它不生成 Source 或 Catalog，也不把部分通过写成整包验收通过。Compiler、Web 和 Source/Catalog revision 40 不变。

在已核验的完整配置中设置：

```json
{
  "diagnostics": { "collectVisuals": true, "visualCoverage": "all" },
  "stages": {
    "source": { "required": true, "execute": false, "preflight": true, "handlers": ["category_<完整玩法身份>"] },
    "compiler": { "required": true, "execute": false, "executable": "<已核验绝对路径>" },
    "web": { "required": true, "entry": "<已核验绝对路径>" }
  }
}
```

以上是配置片段。候选包、世界、内存门禁、完整工作单等字段仍须提供。`--dry-run` 不联系游戏，校验候选清单、路由、阶段路径、诊断与 Source 声明的玩法身份完全一致。用途来源使用 `interactive:` 路由，不能使用未经运行器支持的自定义前缀。

运行器先完整执行数据诊断，按原生总数连续分页。可隔离的条目失败继续后续条目和独立 Handler；open 失败保留原始错误、堆栈、cause、suppressed 与 severity，不被缺少 recordKind 或总数的次生错误覆盖。未知总数和未检查范围仍明确保留。

随后，完整通过数据检查的 Handler 按最多 4096 条的连续范围执行 `render: true, contentAudit: true`。每个非排除原生下标都必须有有效内容视觉记录，排除集合必须与数据轮一致，类别图像也校验像素与程序依赖。此过程检查原生内容绘制路径，不编码 PNG、不发布 Source，不采集配方 GUI 背景、边框、箭头或进度装饰。零条来源单独记录；未完整通过数据检查的来源记为 blocked，不强行渲染。

结果写入独立 `stages.diagnosticVisual`，包含 coverage、失败范围、原始 Job 或归档报告、blocked 和空集。`all-native-entries-with-passed-data` 描述覆盖方式；还必须检查 status 和 blocked，不能单凭这个字符串宣称全量通过。representative 模式只检查代表性下标，不能替代 full 模式。

仅当生产端明确证明错误为可隔离的非 fatal，才继续独立检查。VM/Error、IO、cleanup、环境或源漂移、未知隔离程度、取消与资源门禁均停止后续游戏工作。Job 级失败要求外层和 Java details 的 severity 都明确为 false，且 cause/suppressed 未暴露不安全状态。禁止在同一批运行中修改代码、换包、改语言碰运气、缩范围或吞错。

诊断模式禁止 Source 与 Compiler 执行。关闭该配置也不能绕过检查点中已记录的失败或未检内容；后续统一导出仍需真实完整门禁。退出码非零可能代表已完成问题收集，也可能代表安全中止，须依据检查点和原始错误区分。

中文和英文各使用独立工作目录、runId、key 与检查点，等待语言资源重载和新环境就绪。旧版本、旧会话或另一语言结果不可复用，writing 分片不可拼成成功 Source。收据只引用最终固定的日志副本，活跃日志不能在封卷后继续改变被记账字节。

Bridge Job 隔离修订 1 将完整 runId、检查点环境指纹、检查点创建时间、阶段与完整请求一起哈希为 68 字符键。数据检查、内容视觉和 Source 使用各自阶段；`stages.source.key` 是参与哈希的显式尝试标签，不再直接用作最终 HTTP key。Java 的持久幂等记录不变：同一检查点的相同请求仍复用原 Job，新批次、候选或会话不会碰撞。明确指定不同 runId 会创建新检查点；没有显式 runId 时可恢复已记录的当前轮次。旧隔离版本检查点不被恢复为当前成功结果，原始 Job 仍可读取为历史证据。

消费任何 Job 状态前核验其 ID、请求、创建时间与固定传输会话；报告还须对应相同 Job/请求、有效时间、原生 provenance 和已验证的导出器 JAR 摘要。同一批报告的原生 session/runtime 也必须一致。传输 UUID 与原生世界 epoch SHA 是不同身份，不直接比较。来源不符写 `scopeStop.code=job_scope_mismatch` 并停止；旧 failed/cancelled 同样不会进入当前 Handler 成败统计。资源与其他独立检查可使用 `bridge/src/job-scope.mjs` 的相同键和验证函数，不能只给手工键换一个日期就将历史结果算作本轮实测。

完整内容诊断能提前暴露更多动态绘制问题，但不覆盖任意玩家 NBT、所有世界状态、PNG 编码/落盘、Compiler 全量解释或最终网页验收。每次都如实交回安全范围、未检范围与原始证据，修复后才另行统一验收和导出。
