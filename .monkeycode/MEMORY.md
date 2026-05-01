# 用户指令记忆

本文件记录了用户的指令、偏好和教导，用于在未来的交互中提供参考。

## 格式

### 用户指令条目
用户指令条目应遵循以下格式：

[用户指令摘要]
- Date: [YYYY-MM-DD]
- Context: [提及的场景或时间]
- Instructions:
  - [用户教导或指示的内容，逐行描述]

### 项目知识条目
Agent 在任务执行过程中发现的条目应遵循以下格式：

[项目知识摘要]
- Date: [YYYY-MM-DD]
- Context: Agent 在执行 [具体任务描述] 时发现
- Category: [代码结构|代码模式|代码生成|构建方法|测试方法|依赖关系|环境配置]
- Instructions:
  - [具体的知识点，逐行描述]

## 去重策略
- 添加新条目前，检查是否存在相似或相同的指令
- 若发现重复，跳过新条目或与已有条目合并
- 合并时，更新上下文或日期信息
- 这有助于避免冗余条目，保持记忆文件整洁

## 条目

[安卓原生项目交付与约束]
- Date: 2026-04-06
- Context: 用户要求将青龙 JS 脚本重构为可直接交付的安卓原生 ROOT 保活 APK
- Instructions:
  - 所有对用户的回复使用简体中文。
  - 目标交付物为 Android Studio 完整源码、最终 APK、编译教程、使用教程和功能说明文档。
  - 安卓项目主逻辑使用 Kotlin，OCR 模块使用 Python，并通过 Chaquopy 集成指定的 ddddocr 库。
  - 安卓项目采用 MVVM、Jetpack Compose、MMKV、OkHttp、Retrofit、WebSocket，并实现 ROOT 保活、Cron 定时、常驻通知和完整 UI。

[本地构建优先于 GitHub Actions]
- Date: 2026-04-06
- Context: 用户希望后续由我直接构建 APK 并发布到 GitHub，而不是依赖较慢的 GitHub Actions
- Instructions:
  - 后续涉及 APK 发布时，优先由我在当前环境本地构建 APK。
  - 如需发布到 GitHub，优先采用本地构建产物再上传发布，而不是依赖 GitHub Actions 打包。

[初始仓库与环境信息]
- Date: 2026-04-06
- Context: Agent 在执行安卓项目初始化时发现
- Category: 环境配置
- Instructions:
  - 仓库初始只有 `ctyun-phone-keepalive.js` 一个核心脚本文件，没有现成 Android 工程。
  - 当前仓库没有 `.gitmodules`，本次任务无需初始化 Git Submodule。
  - 当前执行环境缺少 `java` 和 Android SDK，无法在本地直接产出可验证的 APK，需要用户在 Android Studio 环境中编译。

[用户当前可用设备约束]
- Date: 2026-04-06
- Context: 用户说明当前手头上只有安卓手机
- Instructions:
  - 优先提供不依赖电脑的交付路径。
  - 如果需要构建 APK，应优先考虑远程 CI 自动打包等手机可操作方案。

[本地安卓构建环境与 OCR 阻塞]
- Date: 2026-04-06
- Context: Agent 在配置 Java/Android SDK 并验证 Android 工程时发现
- Category: 环境配置
- Instructions:
  - 当前工作区已安装 JDK 17、Android SDK Platform 35、Build Tools 35.0.0、NDK 26.3，并生成 Gradle Wrapper 8.7。
  - 通过本地 vendored `ddddocr` 源码与模型、Python `onnxruntime.py` 兼容层、Java `onnxruntime-android` 桥接，已绕过 Chaquopy 缺失 `onnxruntime` wheel 的阻塞。
  - 当前项目已成功执行 `:app:assembleDebug`，生成 `app/build/outputs/apk/debug/app-debug.apk`。

[日志面向程序员而非普通用户]
- Date: 2026-04-06
- Context: 用户在验证保活效果时要求日志更详细，便于程序员定位问题
- Instructions:
  - 保活日志应优先服务程序员排障，而不是仅提供面向用户的简短提示。
  - 关键链路应记录更完整的请求阶段、分支决策、服务端返回摘要和连接结果。

[pc.ctyun.cn 可在安卓手机浏览器访问]
- Date: 2026-04-06
- Context: Agent 在分析设备指纹与 PC 环境影响时，根据用户实机测试确认
- Category: 环境配置
- Instructions:
  - `pc.ctyun.cn` 官网可在安卓手机浏览器中访问，是否能访问不等于服务端一定要求真实 Windows 运行环境。
  - 保活链路异常更可能与请求指纹、接口参数、桌面进入后的状态上报/Clink 附着细节有关，而不是单纯因为宿主设备是手机。

[严格对齐 JS 与交互约束]
- Date: 2026-04-06
- Context: 用户在排查保活行为时明确要求执行方式和交互体验与现有 JS 脚本严格一致
- Instructions:
  - 保活核心逻辑优先严格按 `ctyun-phone-keepalive.js` 复刻，不要自创流程分支。
  - App 启动后不得自动直接执行保活，手动测试与正式启动必须可明确区分。
  - 对外显示的客户端名称应使用“手机客户端”，避免暴露“Android ROOT 原生客户端”。
  - 日志需要支持流式输出，便于实时观察连接过程。
  - 通知栏常驻通知必须正常可见并反映运行状态。

[统一使用后台保活术语]
- Date: 2026-04-06
- Context: 用户要求统一 UI 与 GitHub 文案中的术语，避免“前台保活服务/后台保活”混用
- Instructions:
  - 所有 UI、日志、通知和 GitHub 文案统一使用“后台保活”或“后台保活服务”。
  - 避免再出现“前台保活服务”等混用字样。

[README 中保留脚本来源署名]
- Date: 2026-04-06
- Context: 用户要求在 GitHub README 中明确说明 `ctyun-phone-keepalive.js` 脚本来源
- Instructions:
  - 在 README 文案中注明 `ctyun-phone-keepalive.js` 脚本为妖火 `@YH` 大佬的脚本。

[README 中补充 deviceCode 获取方法]
- Date: 2026-04-06
- Context: 用户要求在 README 中写明如何自行抓取并填写 deviceCode
- Instructions:
  - README 需要说明使用电脑 Edge 或 Chrome 打开 `pm.ctyun.cn`，登录并完成短信验证后，通过 F12 在请求头中找到 `getServData` 请求里的 `ctg-devicecode` 值。
  - README 需要说明 `deviceCode` 一般格式为 `web_phone_xxx`，并提示将该值填入 APK 的 `deviceCode` 字段。

[日志与账号管理界面简化]
- Date: 2026-04-06
- Context: 用户要求减少重复信息，让 UI 更高效，并简化日志和账号添加方式
- Instructions:
  - 去掉双备份日志，仅保留单一路径日志存储。
  - 去掉账号批量添加，只保留单个添加入口。
  - UI 设计要减少重复信息，优先信息效率和清晰度。

[修改高频手改文件的执行标准]
- Date: 2026-04-07
- Context: 用户要求我在修改 README 等高频手改文件时严格控制范围并避免误改
- Instructions:
  - 当用户点名具体区块时，只修改该区块，不扩散到其它段落。
  - 修改 README 这类高频手改文件前，先精确读取目标段落再动手。
  - 如果远端对应文件处于高频变更状态，优先避免反复冲突，必要时先停下确认。

[后台保活执行后恢复待命状态]
- Date: 2026-04-06
- Context: 用户要求后台保活执行一轮后，首页与快捷信息的状态展示要回到待命而不是显示已停止或执行完成
- Instructions:
  - 当后台保活服务仍在运行且定时任务开启时，单次保活执行完成后，UI 状态应恢复显示“后台待命”。
  - 首页状态、快捷信息中的当前进度和通知栏状态应保持一致。

[隐藏最近任务卡片]
- Date: 2026-04-06
- Context: 用户要求避免应用在最近任务中被误划掉，影响后台保活
- Instructions:
  - 应用任务默认从最近任务卡片中隐藏，防止用户误清除后台保活进程。

[加入 ROOT watchdog 守护]
- Date: 2026-04-06
- Context: 用户要求在后台保活启动时自动拉起 ROOT 守护脚本，检测服务被杀后自动重启
- Instructions:
  - 提供独立的 ROOT watchdog 守护脚本。
  - 启动后台保活时自动拉起 watchdog。
  - watchdog 需要检测前台服务是否存活，服务被杀后自动重启。

[区分 app 在线守护与天翼云手机后台保活]
- Date: 2026-04-08
- Context: 用户纠正 ROOT watchdog 与“后台保活服务”的职责边界，并明确杀后台后的期望行为
- Instructions:
  - ROOT watchdog 的职责是维持 app 本身在线，而不是等同于“后台保活服务”。
  - “后台保活服务”指天翼云手机保活功能，是否启用应由用户单独决定。
  - 当 ROOT watchdog 检测到 app 不在线时，应先把 app 拉起，再由 app 根据被杀前是否启用了“后台保活服务”来决定是否恢复该服务。
  - 如果被杀前启用了“后台保活服务”，app 被拉起后应继续运行该服务；如果未启用，则只恢复 app 在线，不自动开启天翼云手机保活。
  - 用户手动停止“后台保活服务”时，只应停止天翼云手机保活，不应顺带停掉 ROOT watchdog 的 app 在线守护。

[ROOT watchdog 保持现有拉起方式并加强 root 加固]
- Date: 2026-04-08
- Context: 用户要求撤销 Launcher 回退方案，并在应用启动时增加一组 root 加固动作
- Instructions:
  - 取消“如果显式广播未恢复进程，再回退到显式启动 Launcher”的功能，watchdog 保持原有好用的拉起方式。
  - 应用启动后自动申请 Root 权限；如果已授权，则不应重复申请或打扰用户。
  - 获取自身进程 PID 后，通过 root 将 `oom_score_adj` 设为 `-1000`，尽量实现系统级不被杀。
  - 将应用加入 `deviceidle` 白名单，降低低电模式杀死应用的概率。
  - 关闭应用休眠/待机限制，允许后台持续运行。
  - 允许后台启动 Activity 和后台服务；如不同 ROM 存在能力差异，也应尽最大努力用 root 命令设置到位。

[保留用户自定义 deviceCode，禁止失败后自动换码重登]
- Date: 2026-04-08
- Context: 用户根据实机日志反馈，要求修正 deviceCode 被自动替换的问题
- Instructions:
  - 用户填写并启用的自定义 `deviceCode` 必须始终保留，不得在登录失败或重试链路中自动改成系统生成值。
  - 取消“登录失败后重新获取/重新生成 deviceCode 再重登”的逻辑。
  - 登录失败或鉴权失效时，可以重试登录，但必须继续沿用当前账号原本的 `deviceCode`。

[智能保活仅控制天翼云手机保活任务]
- Date: 2026-04-08
- Context: 用户澄清“智能保活”与 App 自身后台保活/在线守护是两套概念，不能混淆
- Instructions:
  - GitHub 上发布的 `v2.0.5` 与当前分支中的版本号/代码状态可能不同，处理前先核对当前分支代码而不是假设版本号一致。
  - “智能保活”只控制天翼云手机保活任务的触发时机，不等于 App 自身后台保活、前台服务或 watchdog 在线守护。
  - 智能保活开启后，若检测到手机传感器 xyz 数据，则暂停天翼云手机保活任务。
  - 智能保活开启后，每 10 分钟运行一次天翼云手机保活功能。
  - 传感器 xyz 运动数据每 5 秒检测一次。
  - 若检测到手机最近 5 分钟内有传感器 xyz 运动数据，则跳过本次天翼云手机保活。
  - 如果连续 5 分钟没有检测到运动，则立刻执行一次保活功能，然后再每 10 分钟运行一次保活功能。

[应用内版本号需和 Release tag 对齐]
- Date: 2026-04-08
- Context: 用户要求应用里显示的版本号与 GitHub Release tag 保持一致
- Instructions:
  - 应用内展示版本号、`build.gradle.kts` 的 `versionName`，以及相关版本常量应与当前发布的 Release tag 对齐。
  - 本次需要对齐到 `2.0.7`。

[智能保活运动判定改为 xyz 总和区间]
- Date: 2026-05-01
- Context: 用户最终确认“运动/静止”只按传感器 xyz 总和是否落在区间内判断
- Instructions:
  - 当传感器 xyz 总和在 `9.8` 到 `10` 之间时，判定为静止。
  - 当传感器 xyz 总和不在 `9.8` 到 `10` 之间时，判定为运动。
  - 不再比较 xyz 总和是否变化。
  - 智能保活相关实现和文案应按该区间规则对齐。
