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
