# 真实游戏验收

这里保存 NESQL、Elysium Compiler 和 NeoNEI 的联合验收脚本。脚本连接本机单人游戏，通过 MCP 提交导出、收集原始数据，再使用实际发布的编译器与 Web 包进行检查。

## 配置

需要 Java 8 游戏实例、Node.js 24，以及三个项目各自生成的发布目录。发布目录必须保留 `files.json` 清单。在 NESQL 发布包的 `bridge` 中执行 `npm ci --omit=dev`；需要浏览器验证时，先安装 NeoNEI 前端的 Playwright 依赖和 Chromium。

将 `config.example.json` 复制为同目录的 `acceptance.json`，填写实际配置。路径字段均使用绝对路径；`reports` 必须位于本仓库的 `acceptance/.refactor-state/` 内。`world` 是独立测试存档的文件夹名称，导出会在开始采集前校验该存档。`version` 对应模组 jar 文件名，`revision` 对应数据格式。

本机配置、连接令牌、游戏数据和验收报告不提交到仓库。配置中不填写连接令牌，桥接自行读取目标实例的连接文件。

## 安装与导出

定位问题时先用诊断。仍需正常退出游戏、安装当前包，进入独立存档并等待NEI加载。

```powershell
node acceptance/scripts/accept.mjs scan structures --controllers 17000 --key check-dam
node acceptance/scripts/accept.mjs status <job-id>
node acceptance/scripts/accept.mjs report <job-id>
node acceptance/scripts/accept.mjs scan structures --key check-structures
node acceptance/scripts/accept.mjs retry <job-id> --key check-retry
```

controllers省略即检查全部结构，多个ID以逗号分隔。retry按原报告failed/pending/running目标创建新任务，保留原probes；失败任务的key不可用于新的重试。

```powershell
node acceptance/scripts/accept.mjs scan recipes --handlers <category-id> --offset 0 --limit 128 --key check-recipes
node acceptance/scripts/accept.mjs scan recipes --offset 0 --limit 128 --key check-handlers
```

recipe范围应用于每个处理器；省略handlers检查全部适配器并列出未支持项。报告的partial不是全覆盖，后续按offset分段；单条配方使用对应offset和limit1。retry重查失败处理器的同一范围，精确单条使用scan。完整处理器清单保存在game.json和配方报告。

status包含operation与report摘要；checked是报告结束，必须看failed/unsupported/partial/pending。report在终态读取本机nesql/checks文件并校验大小/SHA，保存<job-id>-check.json。诊断不可collect/verify为数据集。cancel须等待终态，慢调用期间不得并发启动其他游戏任务。诊断复用生产读取/排序，完整Compiler语义与GL仍在正式验收阶段执行。

以下命令从仓库根目录运行。先正常退出 Minecraft，再安装新模组；安装脚本会校验摘要，备份并停用之前的 jar。

```powershell
& .\acceptance\scripts\install.ps1 -Check
& .\acceptance\scripts\install.ps1
```

启动游戏并进入配置指定的测试存档，等待 NEI 加载完成后执行：

```powershell
node acceptance/scripts/accept.mjs prepare
node acceptance/scripts/accept.mjs inspect
node acceptance/scripts/accept.mjs start data --key research-check
node acceptance/scripts/accept.mjs status <job-id>
```

预检要求客户端、全部 Mod 来源、研究注册表和材料元数据有效。材料颜色的原始值与钳位结果保存在预检诊断中。`data` 阶段选择熔炉与 GT 主粉碎机，同时采集共同领域资料，结果明确标记为 `selection`。它不表示全量处理器覆盖已验收。

同一操作响应不确定时，使用原 key 和原参数重试；代码修复后重跑已失败任务必须使用新 key。检查失败时读取报告，不连续提交新任务。需要取消时执行 `cancel <job-id>`，并等待终态。

## 交接与验收

导出成功后，执行方收集原始数据和运行记录：

```powershell
node acceptance/scripts/accept.mjs collect <job-id>
```

验收方随后执行：

```powershell
node acceptance/scripts/accept.mjs verify <job-id> --browser
```

`collect` 保存 job、source 和交接报告。`verify` 从这些报告定位数据，运行编译器的 inspect/compile/check，再检查实际 Web 包；游戏关闭后也可执行。数据阶段通过后，可使用新 key 依次运行 `start visuals` 和 `start magic`。

浏览器检查覆盖这份数据的基本加载和配方展示。完整离线行为由 NeoNEI 自身的浏览器测试继续验证；一次真实数据导出成功不代表全部模组业务完成验收。
