# 天翼云手机保活 APK

这是一个将 `ctyun-phone-keepalive.js` 迁移为 Android 原生 Kotlin 应用的工程骨架。

其中 `ctyun-phone-keepalive.js` 原始脚本来源于妖火 `@YH` 大佬，本项目是在该脚本逻辑基础上的 Android 原生实现与工程化整理。

## deviceCode 获取方法

如果某些账号在 APK 中登录后仍然卡在鉴权或设备列表阶段，可以先由用户手动获取 `deviceCode`，再填入 APK 的账号设置中。

操作步骤：

1. 用电脑上的 Edge 浏览器或 Chrome 浏览器打开 `pm.ctyun.cn`
2. 登录账号，并先完成短信验证码验证
3. 按 `F12` 打开开发者工具
4. 刷新页面
5. 在 `Network` / `Headers` 里找到 `getServData` 请求
6. 在请求头中找到 `ctg-devicecode`
7. 复制这个值，通常格式为 `web_phone_xxx`
8. 打开 APK，在账号设置里启用“自定义 deviceCode”，把复制到的值填进去
  <img width="" alt="1d06bd040f8a8765dbc6ea637aede3fa" src="https://github.com/user-attachments/assets/52291577-ee0d-4425-b904-f44842cda28b" />

  <img width="300" alt="074be9777bdde91a37e3eeeef11ff23e" src="https://github.com/user-attachments/assets/a597b64d-ff97-44af-b55d-ce261ad25ac9" />

说明：

- `deviceCode` 一般不要在不同账号之间混用
- 如果服务端风控状态变化，可能需要重新抓取一次最新的 `deviceCode`

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

## 打赏
<img width="240" alt="5762fb4431be7951f6f55d529545efd9" src="https://github.com/user-attachments/assets/ad9ea26e-75b2-4edf-8971-aade7ef117e6" />

<img width="240" alt="f6acf0283e60f03657151b2526bfb753" src="https://github.com/user-attachments/assets/e35fdb61-14a4-44b3-a4e8-c7d0ae782db3" />
