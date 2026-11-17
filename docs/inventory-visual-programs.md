# 原生库存程序动画（实施中，未统一验证）

当前工作树新增 typed `visuals` 集合，区分真实物品程序动画、有限 sprite 时间轴与配方语义 `programs`。旧的 Source/Catalog 修订号和 semantic.67 生成包尚未统一升级，不能将当前工作树作为已验证候选发布。

生产接线包括 Galacticraft 3.3.13-GTNH 的 `ItemRendererKey`，实装 Avaritia 1.77、Eternal 1.2.3 与 GT 5.09.51.482 的专用 cosmic 库存程序，以及 Forestry 4.10.17 的实际注册 3D 蜜蜂/蝴蝶。这些新增链路仍未统一验证。

## 采集的数据

- `mesh`：真实 `ModelKey.keyParts` 的五个 `ModelRenderer`、真实 ModelBox quads、原生 UV、Tessellator byte-normal 与各 part 的 offset/pivot/ZYX 旋转。没有世界几何的 y/z 翻转，不编造模型盒。
- `mathHelper`：实装 `MathHelper.SIN_TABLE` 的 65536 个 float32 原始 IEEE 位。每个值用八个小写十六进制字符，整张表作为共享内容资源，不塞进 PNG 透明像素或逐个物品复制表记录。
- `galacticraftKey`：明确版本、mesh/trig/sampler 引用、Sys timer resolution，以及真实库存 renderer 入口的 model-view、projection、材质、方向光、深度、混合、透明测试和剔除状态。
- 模型纹理从当前已上传的 GL texture 读取，包含当前资源包的实际像素及原生 sampler。它与 64×64 首帧预览是两个独立用途。

`InventoryTrace` 包含 key、cosmic/bow、Eternal、GT dispatcher 与 Universium 的精确入口/正常返回观察。`InventoryDrawTrace` 在 FML 去混淆之后观察 Tessellator 的 start/addVertex/draw 语义输入，不按 Angelica rawBuffer 的固定 stride 解码。无 scope 时不读 GL、不载入模型或可选模组。普通游戏仍使用原始时钟和 Random。

采集 metadata 的拒绝信息先留在 observer 内，完成原生绘制后再向 Job 抛出，避免 NEI 把采集器的准入错误登记成坏物品。真正未正常返回的 renderer 不会封装成成功的程序动画。

## 发布与播放

`VisualGraph` 先校验有界资源 DAG，按依赖顺序声明内容图像和 visual 行，再发布预览资产的 optional `visual` 引用。共享依赖按对象去重，取消或依赖失败不发布根。记录和 PNG 进入 checkpoint/fragment 流；`writing` 分片仍不能冒充已封存 Source。

Compiler 校验资源 ID、类型、依赖、真实五 part、float bits、数量/几何/光照预算、版本、timer 和 sampler；拒绝孤立资源、缺失引用、非有限值和与 sprite timeline 混用的预览。GUI 裁剪会保留内容程序使用的共享像素。visual 表页目标为 1 MiB/64 行，模型采样纹理独立 lossless WebP，避免小纹理解码无关 4096×4096 图集。

NeoNEI 的配方 Icon、浏览与历史 ResidentIcon 都接同一个程序入口，使用现有可见性和共享时钟。先画原生首帧预览，再加载模型资源；单一共享 WebGL 2 surface 按真实模型重新计算变换与方向光。Sys 单位转换保留 long→float 的舍入顺序，MathHelper 查表与 float 除法/乘法顺序明确，没有人为制造短周期。无抗锯齿、无平滑放大。

geometry、lookup、GPU texture 和共享 surface 计入现有 Atlas 全局预算；相同资源共享引用。取消、失败、切 Catalog 和不可见卸载都释放持有；迟到结果不能留下一份没有 owner 的资源。GPU context 丢失后不把静态图当成动画成功。

## 尚未证明的边界

新代码尚未执行 Java/Rust/Node 测试、typecheck、build、契约生成、网页或实机像素比对；不能宣称与游戏逐像素通过或实际提速。

key 程序接已审查的固定功能子集：方向光、无 specular 材质、普通加法混合和实际完整材质 mip 链。未建模的 lightmap、fog、texture matrix 等仍明确拒绝。

## Cosmic 库存与材质

`CosmicInventory` 保留真实基底、halo、随机 pulse 与 shader draw 的原始顺序、顶点、UV、法线、顶点颜色、矩阵、深度/混合与光照。已加载 shader 必须匹配审查过的源 SHA；前端只编译内置 GLSL port，不执行 Source 提供的代码。Eternal 每次 draw 推进自己的 Java Random 流，保留双精度缩放、平移以及 quad 顶点的 offset 扩张。它不是按纹理/时间合并的伪循环。

`NativeSamplers` 读取实际 mip、filter、LOD 和动画相位。稀疏 sprite 只读使用的区域，不下载全图集；当前支持每级 nearest 过滤，原生 linear 邻域、跨 sprite 的 mip 或 anisotropy>1 需要独立依赖，不能用 clamp 或 level0 伪装等价。NotFine 插值按实装 beta66b 的 Java double 顺序预计算 RGB，保留源 alpha；空显式 metadata 时不假造其已被原生吞掉的插值。GPU 尚未上传当前相位时，额外保存实际已上传首帧，等下一次原生上传后再进入循环。

UV 索引按客户端切片构造，读取字段而不遍历调用 getMinU，避免激活整张图集的动画。前端 frame sheet 与采样 mip 分开，GPU copy 切帧保留透明像素中的 RGB；没有 canvas 反读、生成 mip 或每帧网络解码。全部资源继续受 Atlas 全局预算和取消/关闭管理。

临时 helper 的 inventory/opacity/light 数组、已有 uniforms/lighting attribute 和 RenderItem.renderWithColor 有恢复路径。真实 GLSL 编译、native 像素、首帧、资源耗时和失败恢复均未验证；不能据接线宣布视觉修复通过。

## Forestry 模型与原生模型缓存

`ForestryTrace` 观察实际 item、entity renderer、texture、model 与每个 part 的原生调用/正常返回。遗传 getter 的 alive/hash/partial 结果从原有调用栈转发，不额外调用 genetics 回调。蜜蜂和蝴蝶分别保留个体 hash、蝴蝶 state/species hash、缓存展示实体的 ticks 与原生 wing 数学；不把未正常 tick 的展示实体伪装为网页自增实体。模型组缩放/平移已在实际 part 调用前的 model-view 中，wing Z 单独按原生 MathHelper 更新，保留其他轴、pivot、offset 和绘制顺序。

`forestryInventory` 引用 `mathHelper` 和有序 `forestryPart`；每个 part 引用真实 mesh、完整模型 texture sampler，以及原生 lightmap 启用时的 `inventoryLightmap`。后者保存实际 DynamicTexture 的 16×16 上传像素、当前坐标、texture matrix、border 与 wrap。GL_CLAMP 的边缘混合由固定功能 shader port 明确表达，没有偷偷换成 CLAMP_TO_EDGE。读取不重新绑定预期模型纹理来掩盖实际绑定差异。

`ModelDisplayLists` 在原生 ModelRenderer 编译时记录来源模型/列表、几何摘要、scale、线程和实际 handle。正常 pose 改动不使几何失效；源码几何改变但旧 list 未更新时拒绝。此记录在 scope 外只跟踪精确的已审查模型，不读取 GL 或加载可选模组。采集 metadata 错误仍在 NEI 正常返回后上抛。

Angelica beta66b 将部分 compileDisplayList 改为 GTNHLib 0.7.10 VBO；负 handle 不能当作 GL list 不存在。`NativeModelBuffers` 绑定真实 VBO registry/object、format、实际 buffer、count 和 draw mode，读取原生 POSITION_TEXTURE_NORMAL 上传数据。`BufferUploads` 的进程内 upload/close epoch 使小型缓存失效，避免为每个物品反复 GPU readback。普通正 handle 仍按原生 display-list 来源验证。未知格式/state callback、失效 buffer 或来源漂移明确失败，不重建/修改游戏缓存。CPU mesh cache 随 Images 关闭，不删除借用的模型或 VBO。

本地 `render.bees.fancy=false` 只是配置文件值，不是加载注册证明。2D 蜜蜂继续原 sprite 路径；实际注册了精确 3D renderer 时才选择该模块，不能据此省略蝴蝶或宣称注册覆盖已通过。Compiler 的版本/类型/部件顺序/预算检查、网页与图库/历史/配方的共同播放器均已接入，仍待统一生成契约、验证及最终候选实机证据。

## 恢复边界

新的 Random in-process state 保存源对象、seed-root、初始/当前 seed、Gaussian cache 和 draw 数。恢复要求原客户端线程、空闲新 session 和显式 renderer binding validator，失败不会部分安装状态。同 session 的嵌套 scope 也检查实际栈顺序。

该状态现已写入 Handler native authority/seal/replay 接线，尚未统一验证。Checkpoint envelope 5 / continuation 1 要求精确的 released path/hash/unit count 和进程内 capability；验证真实 Forge/GT 分派、model/list/VBO、texture/sprite 原件、linked shader 与 lookup 来源后才重放，并在语义重查后再次核对及恢复 owned Random。每个 Handler 保留自己的边界状态，重放再次中断时不会借用旧末尾状态。原始记录也不能靠修改标记绕过。仍不能只 replay visual JSON 后把新随机会话从头开始，跨重启/版本恢复没有开放。

共享 MathHelper Node 在 Images 中只构造一次。Sink 的 Publisher 只对已冻结、无 image/依赖的常量使用 weak identity memo，并按内容 ID 有界去重 visual 行；不会持有整批 frame sheets。这样可避免为每个遗传物品再次拷贝、散列并写入同一张 512 KiB 查表。没有新的实测提速倍数。

先收齐这些程序及其余计划中的生产类型，再统一协议、生成包和现有少量验证；游戏采集/正式导出仍交导出 Agent。readyForExport=false。
