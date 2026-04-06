# 天翼云手机保活 APK

这是一个将 `ctyun-phone-keepalive.js` 迁移为 Android 原生 Kotlin 应用的工程骨架。

其中 `ctyun-phone-keepalive.js` 原始脚本来源于妖火 `@YH` 大佬，本项目是在该脚本逻辑基础上的 Android 原生实现与工程化整理。<img width="1782" height="985" alt="1d06bd040f8a8765dbc6ea637aede3fa" src="https://github.com/user-attachments/assets/5c6152d0-0214-4677-a365-fe5b2cac1f04" />


## 当前实现

- Kotlin + Jetpack Compose + MVVM 项目结构
- MMKV 本地加密缓存账号、配置和运行统计
- Chaquopy 集成 Python `ddddocr` 离线 OCR
- ROOT 保活程序、后台服务、常驻通知、20 分钟固定调度
- 首页、账号管理、运行日志、参数配置、系统设置 5 个核心页面
- 启动页状态检测
- JS 对齐的设备码、登录签名、验证码重试、`connect/status/state/strategy` 主流程
- Clink 协议的 Kotlin 编码器已迁移，实机联调层已预留
- 已配置本地 JDK 17、Android SDK 35、NDK 26 和 Gradle Wrapper 8.7

## 目录结构

```text
app/src/main/java/com/monkeycode/ctyunkeepalive/
  app/        应用初始化与依赖容器
  core/       配置、模型、工具函数
  data/       MMKV 存储仓库
  domain/     保活主流程与 Clink 协议
  network/    天翼接口客户端
  ocr/        Chaquopy Python OCR 桥接
  service/    ROOT、调度、通知、前台服务
  ui/         Compose 页面与 ViewModel
app/src/main/python/
  ocr_bridge.py  ddddocr 离线识别入口
```

## 编译

详见 `docs/build.md`。

如果你只有安卓手机，没有电脑，优先使用 GitHub Actions 自动打包：仓库已经包含 `.github/workflows/android-build.yml`，推送后可在 GitHub 的 `Actions` 页面手动触发打包，再下载产物中的 APK。

## 使用

详见 `docs/usage.md`。

## Release 交付

详见 `docs/release.md`。

## 说明

当前工作区已经补齐 `JDK 17`、`Android SDK` 和 `Gradle Wrapper`，并完成了 `Chaquopy + 本地 vendored ddddocr + Java onnxruntime-android 桥接` 方案。

当前已成功生成 Debug APK：`app/build/outputs/apk/debug/app-debug.apk`
