# 本机单人游戏 MCP 导出

游戏端、Node 桥接和 stdio 工具已实现，并通过本机协议与任务行为检查。运行中的 GTNH 握手、OpenGL 捕获及全量性能仍待游戏验收。

## 连接

目标为 GT New Horizons 2.8.4 Java 8。模组只服务本机单人世界，不支持专用服务器。MCP 客户端启动独立的 Node 进程，桥接通过受限的 loopback HTTP 接口调用游戏任务服务；Java 8 模组不嵌入 MCP SDK。

模组 0.10.9 写出 source 修订 10。MCP 连接协议保持不变；数据修订与传输协议是不同的元数据。编译器拒绝旧数据修订，不提供旧格式兜底读取。

```json
{
  "command": "node",
  "args": [
    "E:/path/to/nesql/bridge/src/index.mjs",
    "--instance",
    "E:/GTNH/.minecraft/versions/GT New Horizons 2.8.4-java8"
  ]
}
```

安装发布包后，在 `bridge` 执行 `npm ci --omit=dev`。`--instance` 必须是明确的绝对路径。桥接每次请求读取该实例的 `nesql/connection.json`，不扫描其他实例、不自动重新导出。SDK 与其依赖固定于锁文件。

模组绑定 `127.0.0.1` 的系统分配端口。连接文件包含 `protocol`、`port`、`token`、`session`；令牌和路径不写入公开 source。HTTP 请求验证 token、session、Host、Origin、方法、内容类型和大小。响应也携带 session，游戏重启或端口复用会被识别。正常退出仅清理当前会话的连接文件。

## 工具

| 工具 | 参数 | 结果 |
| --- | --- | --- |
| `inspect_game` | 无 | 单人世界状态、NEI 是否就绪、profile、handler 清单及支持状态、模组来源、研究注册表诊断、客户端队列与最长调用耗时 |
| `start_export` | `key`、`name`、`profile`，可选 `handlers`、`probes` | 立即返回 Job；文件留在磁盘 |
| `read_job` | 可选 `id` | `{job}`；省略 ID 时返回活动或最近任务，没有任务时为 `null` |
| `cancel_export` | `id` | 请求协作取消，返回 Job；轮询至终态 |
| `list_exports` | 可选 `after`、`limit` | `{rows,next}`；按内容 ID 排序，limit 默认 20、范围 1–100 |
| `read_export` | 内容 ID `id` | 清单摘要、scope、磁盘位置和声明的文件/字节/记录数 |

`key` 和 `name` 为 1–80 个字母、数字、下划线或连字符。`handlers` 最多 512 个不重复的 category ID，来源于 `inspect_game`。选定的 handler 不存在或不支持时明确失败。`list_exports` 的 `next` 用作下一页 `after`；新导出的内容 ID 可能排在已有游标之前，需要重新从第一页查询。

`inspect_game` 同时报告 exporter 版本、source revision 和当前 world.folder/name。`start_export` 可传 `world` 指定预期单人存档文件夹；实际采集开始前在游戏线程核对，切到其他存档会报 world_changed。该条件参与任务幂等比较和日志恢复。验收工具使用它将任务绑定到独立测试存档；它不修改存档。

物品和方块的 registry 直接读取 Forge 保存的完整注册键，并核对该键仍指向原对象。注册键属于身份数据：保留大小写、空格、Unicode、`|` 和额外冒号，不经过会截断多重冒号的 UniqueIdentifier。物品/方块要求第一个冒号两侧非空；流体使用非空的全局注册键，不要求命名空间。完整原文进入身份哈希，不能通过改名或替换空格合并物品。数据记录的大小限制仍有效；资源文件路径和导出器属性键分别校验，不使用注册键作磁盘路径。

`inspect_game.client` 报告客户端 jar 的 `path`、定位方式 `via`、识别到的客户端类 `entry` 和 `valid`；失败时返回 `valid:false` 与具体 error。定位优先读取启动器 JVM 属性 `minecraft.client.jar`，它必须是绝对本地路径；未设置时使用本地 CodeSource（含 jar URL）或客户端类资源。显式属性无效会报错。客户端归档需包含目标 1.7.10 的命名或混淆主类及 class 文件标识。

`inspect_game.sources` 包含整体 valid、count 和每个已加载 mod 的 rows；逐项给出 id/name、容器类、匹配的核心插件、实际 path、via 与检查结果。核心插件通过目标 Forge CoreModManager 的 loadPlugins 记录，按 getModContainerClass 关联实际容器与 wrapper.location；内置插件没有 location 时取插件实现类所在归档。其他注入容器根据自身实现类归属定位，验证声明的 class 确实位于归档中；普通 mod 保留其声明文件。仅 Minecraft/MCP 使用客户端 jar，CoFH 等核心模组保留自身宿主文件的独立摘要。冲突、无归属和缺失来源仍拒绝。

`inspect_game.research` 包含 valid、解析成功的全局研究数 count、原始登记数 entries、附加空虚拟引用数 references、同对象重复登记数 aliases 和 conflicts。rows 列出重复或不一致的登记：研究 key、原生 getResearch 选中的对象分类/类型、各登记位置与是否选中；错误优先展示，最多 32 组、每组 8 个位置，omitted 明示省略数量。验收脚本要求 research.valid=true 后才提交任务；直接导出也会在环境哈希之前执行 registry 检查。

`inspect_game.materials` 在物品采集之前检查材料身份、成分引用、正数量、公式字段和四通道颜色结构。成功时返回 valid/count，以及需要颜色钳位的 adjusted 数量和最多32条 rows，包含材料 key、原始 rgba 和编码后的 color；omitted 明示省略数量。失败时返回带材料名的 error，验收脚本不提交任务。直接游戏导出也会在 registry 阶段检查这些元数据。

GT材料的原始颜色是short数组，并不保证每个通道都落在0–255。导出按目标BWColorUtil.correctCorlorArray的通道修正规则编码ARGB：低于0取0，高于255取255；这也与GT普通材质的浮点RGB调制范围相符。原数组保持不变，合法通道及原有alpha值保持，错误长度或null仍拒绝。该字段是规范化材料颜色，不代替特殊渲染器的实际贴图或动画。数据修订与Material.color的u32布局不变。

来源解析集中在 Sources，Environment 只组合配置、资源和知识指纹。插件 metadata 在游戏线程读取，文件/归档验证和 SHA 在后台执行；不会调用插件 injectData、setup 或 transformer 安装入口。预检汇总每项文件问题，正式导出再次校验，失败不输出部分 mods 指纹。物理文件的哈希按路径共享计算，但每个真实 mod ID 独立保留。来源路径仅用于本机诊断，公开 source 不记录这些绝对路径；来源校验不会被更改为对客户端 jar 的通用豁免。

三个 profile 均采集物品、流体、分组、GT 材料、电路和 Forestry 遗传定义。`full` 还采集配方、图标和静态配方视图；`data` 省略图像和视图；`images` 采集图标，不枚举配方、不接受 handler 筛选。只有 `full` 且未筛选 handler 才申请完整快照，其他任务的快照 scope 为 `selection`。电路适配按目标 NEICustomDiagram 1.7.5 的实际定义执行，不维护另一份物品清单。

`probes` 为应用于每个控制器的 1–16 组不重复构建参数。每组 `count` 为全息投影物品数量（1–64），`channels` 为可选的通道值映射，最多 32 项；名称使用 1–64 个小写字母、数字、下划线或连字符，值为 1–65535 的整数。省略整个列表等同于 `[{"count":1,"channels":{}}]`。未知通道不代表控制器一定会使用该通道，实际可用名称由对应机器的 StructureLib 定义决定。

例如 `start_export` 参数为 `{"key":"structure-study","name":"gtnh","profile":"data","probes":[{"count":1},{"count":4,"channels":{"coil":2}}]}`。列表按数量、通道键和值的数字顺序规范化，参数顺序不影响重试；改变参数会触发同一 key 的冲突。参数原样保存在任务、环境指纹和每组构建记录中。游戏内 `/nesql` 的 Parameters 编辑页共用相同校验，关闭编辑页可丢弃草稿，Done 才应用；调整窗口尺寸保留草稿。

Forestry 固定使用 4.10.17 API。读取已注册的蜜蜂、树木默认模板和全部突变，不调用世界育种、玩家研究或知识修改接口。物种染色体由 `getKaryotypeKey()` 决定。每个成员物品使用新建个体，避免蜂后构造时的自交影响共享模板。

物种保存显隐性、隐藏/黑名单状态、原生温湿度、成员物品、已填充的默认染色体。数值、耐受、领地和植物类型保留 API 值；花朵、果实、生长、效果等行为 provider 不编造数值。空的废弃染色体不显示为可用基因。树木记录默认果实家族是否与物种匹配，可能产物没有概率时保持 `null`。

蜜蜂产物率来自 `[0,1]`，突变基础率来自百分数 `[0,100]`；两者按 API 的十进制精度转换为约分分数，不代入蜂箱、模式、研究或环境加成。突变保留描述性条件、实际结果基因及实现类。相同登记用连续 `occurrence` 区分，防止去重抹掉独立尝试；不把重复项转换为猜测的综合概率。缺失根、默认模板、引用或条件读取失败均使任务失败，不发布伪造的空遗传集合。

错误返回 `isError` 和 `{error:{code,message}}`。常见 code 包括 `game_changed`、`world_unavailable`、`export_busy`、`key_conflict`、`handler_unsupported`。`read_export` 验证清单摘要和元数据，但其计数仍是清单声明值；完整文件及领域校验由编译器执行。

## 结构定义

所有 profile 还采集 Thaumcraft 要素和研究定义。物品要素来自目标 API 的 getObjectTags/getBonusTags，使用物品和基础标签副本。图标经原生绘制函数捕获。研究的普通引用必须存在；未注册的 `@` 标记按目标 ResearchManager 语义保留。读取 researchCompleted/aspectsDiscovered 的现有缓存，不调用会初始化知识的 getter，也不修改研究或扫描状态。缓存缺失时保存未知，并将缓存存在性纳入环境指纹。

Thaumcraft 4 的空 AspectList 会返回 `[null]`；原生 copy/add/merge 还可能把它写成 `null → 0` 条目。物品、研究、配方和知识指纹统一忽略这个无数量的哨兵，空物品要素保存为 `[]`；API 未返回物品要素时仍保存 null。已注册要素的零数量保留，非零 null、负配方数量或未登记的实际要素仍明确失败，不改写原生列表。要素错误包含 tag/类型/数量等适用信息；物品采集额外报告 registry、meta、内容 ID 和从0开始的 NEI index。进度按批次更新，不能把最后的 completed 值当作失败物品索引。

研究身份沿用原始全局 key，定义、分类和研究/配方引用以已安装游戏的 ResearchCategories.getResearch 结果为准。Gadomancy 1.4.8 在其他分类中注册的同名空虚拟项只作为引用，归并到原生选中的定义并保留预检登记记录；单独存在的虚拟研究仍导出。只有普通 ResearchItem、无内容/触发/解锁行为的空虚拟引用可归并；其他不同对象重名、原生解析落到已注销对象或会遮蔽实质定义时明确报出键与登记位置。登记 map 的键或位置不同于对象字段时记录诊断，保留原生对象的 key/category，不创建新研究别名或改写游戏注册表。source/catalog 修订及研究 ID 不变。

研究的 `itemTriggers` 是当前资料库中可作为线索的具体物品 ID。原生触发项可含 metadata=32767 的通配条件，不能作为普通物品调用名称、提示或绘制 API。Clues 从 NEI 的具体物品、具体触发项及其首个矿辞组的具体登记项中产生候选，并调用与 ResearchManager.createClue 相同的 InventoryUtils.areItemStacksEqual(trigger, candidate, true, true, false) 筛选，保留原生矿辞替代、耐久、metadata 和 NBT 判断。只按首个矿辞组扩展，不能合并全部矿辞组扩大匹配范围；重复物品 ID 去重。匹配和物品读取使用副本，不修改研究、矿辞或 NEI 堆栈，不调用实际扫描或解锁方法。

这份列表表示已知候选的实际匹配结果，不枚举任意 NBT 或玩家可能构造的全部物品状态。每个声明的触发项都必须找到具体候选；找不到、空触发项或超出预算会报 `research_trigger`，不会静默丢弃条件或把32767改成0。研究按最多16次匹配成功或2ms分批处理，单次模组API调用不可抢占。错误附研究key/分类/零基index、触发项位置、registry/meta及物品读取调用位置；进度仍按批次汇报，不能用completed推断精确失败研究。

已迁移要素与研究关系，魔法配方按下述明确适配范围采集；完整研究正文页面仍待继续。

所有 profile 采集 GT 注册控制器提供的 StructureLib 定义。适配器固定 StructureLib 1.4.23、BlockRenderer6343 1.3.17，按真实导航指令还原 A/B/C 坐标；控制器标记来自该版本保留的 occupiedSpaces。每次客户端调用最多处理 256 步，并使用 2 ms 调度预算。单个模组 API 调用仍不可抢占。

`structures` 保存控制器、说明、首组探测参数、命名片段与 `variants`；每个变体对应一个请求参数，恰有 `build` 或 `problem`。`shapes` 保存每块最多 2048 格的几何，Cell.index 指向父级片段规则或构建调色板。片段与建议部件使用首组规范化参数读取，建议部件来自 `getBlocksToPlace`，未枚举的谓词保持未知。

`builds` 保存独立预览世界中的整体构建参数、坐标框架、控制器、调色板、几何和原生返回值，`blocks` 保存实际方块注册名、metadata、完整 typed NBT 和可选 pick-block 物品。预览使用全新控制器、独立玩家和物品副本，按原生 construct/survivalConstruct 放置；不会把命名片段拼猜为整机，也不会写入玩家世界。整体构建不等于运行时成型判定。

每组参数均创建全新的控制器、预览世界和供应物品，通过目标 ChannelDataAccessor 设置通道；上一组清理完成后才开始下一组。完整请求遇到不可枚举定义或任何构建变体失败会失败。selection 可保留定义的 `problem` 和逐变体的失败原因；初始化失败也不会静默遗漏参数。开始序列化后出现错误仍使整个任务失败，避免留下未引用的方块记录。参数化代码与共享样本已接通；实际游戏与全部成型条件仍待验收或迁移。

Build.palette 的每项为 `block/model/problem`，Build.rendered 表明是否请求外观。data 为 false，模型与问题均为空；full/images 为 true，每项至少提供模型或明确失败原因。Model 保存最多1024个四边形，每面有纹理、solid/blend通道和四个顶点；三角形重复末顶点。坐标与UV使用有限float32的十进制字符串，顶点颜色为RRGGBBAA无符号整数。模型使用局部东/下/北坐标，UV以贴图顶行为v=0，纹理复用现有asset与动画时间线。

采集固定 GTNHLib 0.7.10 API，世界渲染当前覆盖原版类型、GTRendererBlock/GTRendererCasing，以及CommonMetaTileEntity、MetaPipeEntity和QuantumForceTransformer的已核实回调。先在每个实际放置位置采集，再按最终模型与方块身份去重；不按注册名猜测外观。普通/陷阱箱、双箱和末影箱已有明确适配；其他未适配渲染器在selection中记录原因，complete拒绝。纹理编码在后台完成后才确定调色板和构建ID；渲染改变方块或保存NBT时整个任务失败。

绘制使用独立framebuffer和可恢复的GTNHLib采集会话。GT的DummyWorld会强制合并通道，因此使用委托的IBlockAccess读取同一预览世界，并保存/恢复已核实的Forge世界通道字段；不触发要求ChunkCache的世界渲染事件。原始方块边界、AO、Forge通道、Tessellator状态及GL状态恢复后才离开客户端调用。实际游戏中的隔离和GL兼容性仍需验收。

## 任务和文件

普通/陷阱箱和末影箱适配按目标原版渲染器选择实际ResourceLocation，包括普通、陷阱、节日和双箱贴图。独立ModelChest/ModelLargeChest通过模型盒及层级变换生成顶点，支持offset、旋转支点、Z/Y/X旋转和隐藏部件；不调用GL display list，也不改共享渲染模型。姿态固定partialTicks=0，保留已有盖子角度。双箱只由原生规则指定的一侧绘制，另一侧返回Model.hidden=true的空几何，物理方块仍保留。普通箱子尚未确定朝向时明确失败，采集阶段不偷偷改变方块metadata。

独立实体纹理通过当前游戏资源管理器读取，先限制文件字节，再检查PNG尺寸后在worker解码；复用原asset和catalog图集。Model.hidden与空faces必须一致，未适配不能使用hidden冒充成功。其余TESR与跨模组专用渲染仍待继续。

任务状态为 `queued → running → succeeded`，取消经过 `cancelling → cancelled`，执行错误进入 `failed`。具体采集阶段在 `stage` 中表达。只有文件封存和原子发布成功后才返回结果。

- 游戏内 `/nesql` 和 MCP 共用 `Exports`、`Jobs`、`Capture`。界面关闭不终止已接受的导出，重开后恢复观察当前任务。
- 同一目录有文件锁，同一服务最多一个活动任务。重复 key 和相同参数返回原任务，参数冲突则拒绝。key 在游戏重启后仍有效。
- Job 保存于 `nesql/jobs/<任务 ID>.json`；事件最多 32 条，报告最多 256 KiB。恢复时重新校验参数并使用不可变对象；历史报告按需读取，不全部保留在堆内。
- 开始、取消、终态强制写盘；进度原子更新。崩溃留下的非终态任务在下次打开 journal 时标记 `game_stopped`，不会自动重跑。
- 临时数据位于 `nesql/work/`，完成的数据位于 `nesql/datasets/<内容 ID>/`。MCP 不接受任意输出路径、Java 执行或游戏命令。
- 请求超时、MCP 请求取消和桥接断开都不会取消持久任务；需要调用 `cancel_export`。取消后必须等待游戏调用和临时文件清理完成。
- HTTP 请求体上限 128 KiB，可容纳最大 handler 与构建参数列表；响应上限 1 MiB。纹理和完整清单通过磁盘交付，不能放进工具返回值。

## 线程和一致性

`Output.change` 为 null 时是固定产物；否则 `id/amount` 表示首个输入候选的产物示例。change 保存输入槽、action 和按输入 choices 顺序排列的 samples。patch 复制输入物品及 metadata/数量/NBT，再替换 set 中的根标签，并按 limits 裁剪整数；merge 保存原始模板、根键筛选和护甲/工具条件。merge 的已有标量优先保留，同类型列表按原顺序追加，compound 递归合并；模板无 NBT 时会沿用输入 metadata/数量。输出默认示例和 samples 不进入语义 ID，变换规则进入。原生示例计算只接收副本，不运行实际注魔。

Salis Arcana 1.1.33-GTNH 的换芯/换端配方通过独立库存调用 getCraftingResult/getAspects，不调用依赖玩家研究的 matches。按最终 WandRod/WandCap 注册表、实际 WandHelper 部件映射与法杖形态逐组合处理，保留 NEI 和已注册配方的具体法杖示例。未带 rod/cap 标签的原版默认状态另用缺失条件表达；sceptre 只检查存在性。诊断用 UNKNOWN 部件映射到基岩且无可获得研究，不作为新材料；注册的旧哨兵状态可以参与修复。未知处理器和无法准确表达的重叠材料谓词明确报错。

螺丝、导体和杖端数量按各自占用的槽位记录。换芯保留配置决定按新容量裁剪或清空 Vis；新 StaffRod 的原生 AttributeModifiers 整表替换也进入 patch。普通容器归还沿用槽位逻辑，不虚构旧杖芯或杖端返还。原生结果与声明的 patch 在导出时逐样本核对。组合惰性展开，总预算 200 万次，每个状态最多 128 个示例；尚需真实整合包验证耗时。

已核对目标包额外的两个 ItemWandCasting 子类。Thaumic Bases 1.8.13 施法手环按注册的 13 种 metadata 及 NEI 已知变体枚举，忽略 NBT 匹配，恒为 Staff、不是 Sceptre；有效部件和容量由 metadata 决定，Salis 替换只写标签而不会改变有效部件，配方属性明确注明此效果。Thaumic Horizons 1.7.9 一次性法杖保留普通部件语义，工坊配方上下文容量固定为 25000；在其特殊充电调用上下文观察到不同容量会拒绝导出。没有调用玩家佩戴、充电或实际合成方法。

MagicRecipe.payment 保存允许以待替换法杖付款的配置、输入槽、要素/NBT 键、保留开关和新容量，容量以百分之一 Vis 计。仅非 Staff 输入可用，供能槽须空置。扣费按原部件及玩家装备折扣，之后保留并裁剪剩余充能或清空；不使用当前玩家去制造固定付款结果。样本以额外法杖供能为基准。creative 显式保存 infiniteCreativeVis：空成本组合不能在生存模式付款，开启豁免时仍可在创造模式使用；六项零成本不等于空列表。

`ClientThread` 在 client tick 执行有界队列，tick 循环预算为 4 ms；单个不可分割的模组调用仍可能超预算，实际最长耗时会记录。读取游戏对象、NEI 和 OpenGL 都经该入口；编码、排序、压缩、摘要和写盘在后台执行。

导出固定世界、玩家和资源重载代数，并在采集前后核对环境指纹。离开世界、更换玩家、资源重载或影响结果的环境变化会中止任务。数据在发布前检查，旧格式没有任何兜底读取。

图标与配方场景分别复用 framebuffer 和读回缓冲区。每次捕获恢复 GL 状态；关闭时在游戏线程释放资源。任务关闭等待最多 5 秒；如果清理仍在进行，journal 锁继续保留并报告错误，不能让另一个导出器并发写入。

## 已验证与待验证

视图中的 `clip` 元素由纹理、位置、尺寸、层级和 `track` 引用构成。`tracks` 为内容寻址的共享记录，保存 `frames`；每步包含正整数 `ticks` 及互不重叠的 `[left,top,right,bottom]` 裁剪区域。坐标为 `[0,1]` 内的 float32 十进制字符串，同时裁剪源纹理和目标区域；空区域表示该步不绘制。每轨迹最多 4096 步，每步最多 16 个区域，总周期最多 72000 tick；相邻相同状态必须合并。轨迹不包含脚本，也不代表配方时长。

GT 捕获使用独立窗口和注入的 200 tick 进度源，不改动全局 drawTicks。静态图层按尺寸/项目复用，背景、动态裁剪、槽位与文字覆盖层保持顺序。取消及异常路径也在客户端线程销毁拥有的窗口；不调用关闭玩家当前界面的入口。普通 GT 方块模型与附加实体模型合并后再确定隐藏状态，激光不会改变 renderer 的偏移字段或 tile 的 counter。

已验证：两组 Java 行为入口、真实 loopback HTTP、SDK stdio 握手与工具调用、幂等和分页、重连会话隔离、超时和响应上限、生产 jar 的 SRG 方法与打包边界。旧 SQL/JPA、Protobuf、stubs 和双 jar 构建已退休。

待验证：实际游戏安装与 MCP 客户端握手，资源重载/离开世界/取消期间的 GL 恢复，所有目标 handler 的领域语义，全量耗时、帧占用、内存与磁盘用量。当前明确适配原版工作台/熔炉、GT 默认 handler、BartWorks BioLab/BioVat、TCNEI 四种魔法配方与 Salis 换芯/换端；未知 handler 不会被跳过。完整领域迁移与全量验收完成前，此分支仍是开发状态。
