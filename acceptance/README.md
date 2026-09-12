# 真实游戏验收

这里保存 NESQL、Elysium Compiler 和 NeoNEI 的联合验收脚本。脚本连接本机单人游戏，通过 MCP 提交导出、收集原始数据，再使用实际发布的编译器与 Web 包进行检查。

## 配置

需要 Java 8 游戏实例、Node.js 24，以及三个项目各自生成的发布目录。发布目录必须保留 `files.json` 清单。在 NESQL 发布包的 `bridge` 中执行 `npm ci --omit=dev`；需要浏览器验证时，先安装 NeoNEI 前端的 Playwright 依赖和 Chromium。

将 `config.example.json` 复制为同目录的 `acceptance.json`，填写实际配置。路径字段均使用绝对路径；`reports` 必须位于本仓库的 `acceptance/.refactor-state/` 内。`world` 是独立测试存档的文件夹名称，导出会在开始采集前校验该存档。`version` 对应模组 jar 文件名，`revision` 对应数据格式。

本机配置、连接令牌、游戏数据和验收报告不提交到仓库。配置中不填写连接令牌，桥接自行读取目标实例的连接文件。

## 安装与导出

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

预检要求客户端、全部 Mod 来源和研究注册表有效。`data` 阶段选择熔炉与 GT 主粉碎机，同时采集共同领域资料，结果明确标记为 `selection`。它不表示全量处理器覆盖已验收。

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
