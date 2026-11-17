# 原生物品动画捕获（0.44.2）

## 已定位的问题

普通 `TextureAtlasSprite` 的动画可以提取，但多渲染层和自定义 `IItemRenderer` 过去回退为一次 GL 截图。前端收到单帧就只能静态展示。`Capture.Sink` 还曾把全部资产的 `interpolate` 强制设为 false。

## 本轮实现

- 普通 Sprite：识别 Angelica/NotFine 的实际 `jss.notfine.render.InterpolatedIcon` 类型（含子类），保留插值标记到资产身份及 Source；不对原版 1.7.10 不存在的字段做假设。
- Avaritia 1.77 `ItemSingularity`：保留原生 `FancyHaloRenderer`，切换实际 Sprite 帧后调用 NEI 库存渲染。前景六帧 × 1 tick、背景三帧 × 2 ticks 的组合周期是 6 ticks。逐层色调、halo、透明度由原渲染器决定，不通过 CSS 重造。资源包可改变原生帧和时长，组合周期使用实际时间线的最小公倍数。
- GT 5.09.51.482：解析 `MetaGeneratedItemRenderer.getRendererForItemStack`，只对实际选中的 `TranscendentalMetaItemRenderer` / `TranscendentMetalRenderer` 采样。保留 `renderTransMetalFancy` 设置。安装版本使用 `GTClient.mAnimationTick` / `getAnimationTicks()`，每 tick 3.5°，严格重复周期为 720 ticks（36 秒）。临时设置原生时钟后调用原生渲染器，最后恢复。
- 捕获按客户端 slice 推进；尚未完成的图集不发布、不推进所属配方。每个 slice 都恢复精确的原 atlas 区域、mipmap 和 GT 时钟；异常也执行恢复。
- 资产使用现有 Source `frames` / `ticks` / `interpolate` 契约；无需给前端增加静态图旋转特效。组合捕获已烘焙每 tick 的原生画面，因此 `interpolate=false`，不会对 3D 轮廓做跨帧淡入。单帧仍沿用原路径。
- 组合时间线最多 4096 ticks / 16M 像素，超出明确失败，不能悄悄截短后宣称完整动画。

## NASA 与相似库存渲染器

GTNH 源码和本机安装 JAR 都已核对。火箭模型的旋转来自 `Sys.getTime()`，不是 Sprite 的 `.mcmeta`。新增精确类名白名单 CorePlugin，只把被审查渲染器的时钟读取接到 ThreadLocal 采集时钟；原来的模型、渲染辅助器、货运火箭反向旋转、箱体覆盖和缩放全部由原生代码执行。游戏正常绘制继续读真实时间。安装新包必须完整重启客户端。

| 原生渲染器 | 审查版本 | 库存动画采集 |
| --- | --- | --- |
| Galacticraft T1/T2/T3 火箭、T2 货运火箭、AstroMiner | 3.3.13-GTNH | `Sys.getTime()/30F%360`，10800 原生计时单位 |
| GalaxySpace 共用各级火箭渲染器 | 1.1.121-GTNH | 同一原生时钟旋转 |
| AmunRa Shuttle | 0.8.2 | 同一原生时钟旋转 |
| Draconic Evolution 地球仪 | 1.4.27-GTNH | 64ms 步进 × 360，2304 ticks 严格闭合 |
| Witching Gadgets Primordial Gauntlet | 1.7.25-GTNH | 手指色彩、符文亮度的 32 ticks 原生循环 |
| AE Sky Compass | rv3-beta-695-GTNH | NEI 独立 ItemStack 走原生非持有分支，保留 100000ms 的完整取模区间（2000 ticks） |

`Sys.getTimerResolution()` 决定原生单位，不能一律假设为毫秒。按 20 ticks/s 采样，严格闭合周期为 `units*20/gcd(units*20,resolution)`；1000Hz 时火箭为 216 ticks（10.8 秒），超过预算显式失败。Gauntlet 保留原 `GETFIELD` 读取，只在捕获期间替换其消费值；不改玩家、世界或全局时间。

普通多层物品现在合并实际 base/overlay Sprite 时间线；GT `GeneratedMaterialRenderer`、`GeneratedItemRenderer` 及超时空渲染路径按其实际使用的图元读取。GT 容器的流体图元可以来自另一张图集，分别保留、上传、恢复 item/block atlas 区域，再调用原生 NEI 绘制，保留原生颜色、容器裁剪和覆盖层。未审查的自定义渲染器不推断成此模型。

Galacticraft 光束接收器、反射器、抓钩只有 ENTITY 分支旋转，INVENTORY 分支原生静态；Buggy 与 AmunRa Jet 库存同样不追加动画。

## 同类扫描和程序动画

对安装 JAR 的时钟/IItemRenderer 引用扫描只能产生候选，不能代替逐分支证明。Galacticraft 钥匙的连续 sine 摆动和 Forestry 活蜂/蝴蝶目前已接独立库存程序：显式记录原生几何、姿态、纹理采样、时钟与必要光照，不截取短循环冒充等价。相关新实现仍须统一构建、原生及像素验证；不能据接线称实机动画已修复。AE Sky Compass 只覆盖 NEI 独立 ItemStack 分支，不能据此声称已模拟玩家持有时的位置/方向服务。其他时间调用可能只在实体/世界分支使用，扫描不能据此判为库存动画错误。

## Shader 库存程序

`EternalItemRenderer`、Avaritia Cosmic 和 GT Universium 不等价于普通 Sprite 动画。本地最新 GTNH 源码使用 GTNHLib UniversiumShader，但实际安装的 Eternal Singularity 1.2.3 使用自己的 `CosmicRenderStuffs`、`time2` 和 mask.r；Avaritia 使用 mask.a。Shader 原生周期为 12000 ticks，并依赖投影、原生 cosmicUVs、颜色、光照、遮罩和视角。

`CosmicInventory` / `CosmicShaders` 当前已接显式有序 draw、原生 shader 版本与绑定、mesh、sampler/mip、颜色和光照；Eternal 随机 pulse 使用独立 owned JDK Random 状态，保留缓存与消耗顺序。前端只执行固定受审的程序类型，不执行导出器携带的任意 shader。短时间截图拼接不作为周期证明。这些生产接线属于当前集中验证候选，GLSL、像素、原生运行与全包表现仍待实际验证。

未知 renderer/callback、未建模固定功能状态、稀疏 sprite 的线性邻域或跨 sprite mip、anisotropy>1 明确拒绝。不会用静态图、level 0、clamp 或通用占位图隐藏这些边界。完整资源图的结构前检、来源复核与计数见 [视觉前检](visual-throughput.md)；同会话的来源和 Random 延续见 [恢复协议](recovery.md)。

## 轻量验证

`visualThroughputTest` 检查双层时间线、组合周期、预算、未完成捕获、GT 时钟成功/异常恢复和插值资产身份。`glTest` 用真实不可见 OpenGL 上下文检查原生 Sprite 帧上传和异常后的精确 RGBA 恢复。前端已有 `browser-animation.test.mjs` 检查时间线、离屏取消、迟到帧和插值资源释放。

新增轻量检查：9 个本机安装渲染器字节码的时钟替换前后指令核对、重复变换幂等、线程/嵌套/异常作用域、不同 Sys 计时精度；真实 GL 旋转帧差与闭合检查，以及 item/block 两张图集的独立上传和异常恢复。模型与整合包实机结果仍交给导出 Agent 验证。

这些是本地验证，不代表整合包实机动画已验收。导出后还需检查同一物品在浏览区、历史区、候选弹窗和配方槽位的连续帧，以及完全停服、断网重开后的播放。
