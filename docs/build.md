# 编译教程

## 环境要求

1. Android Studio Koala 或更高版本
2. JDK 17
3. Android SDK Platform 35
4. NDK 26+（Chaquopy 场景建议安装）
5. 一台已 ROOT 的 Android 7.0+ 手机

## 编译步骤

1. 用 Android Studio 打开仓库根目录。
2. 等待 Gradle Sync 完成。
3. 首次同步时，Chaquopy 会自动下载 Python 依赖，包括 `ddddocr`、`onnxruntime`、`numpy`、`Pillow`。
4. 连接真机或启动模拟器后执行 `Run 'app'`。
5. 需要发行包时执行 `Build > Build APK(s)`。

## 只有安卓手机时的打包方案

1. 把仓库上传到 GitHub。
2. 打开 GitHub 仓库的 `Actions` 页面。
3. 选择 `Android APK Build` 工作流。
4. 点击 `Run workflow`。
5. 等待构建完成后，在构建产物 `ctyun-keepalive-debug-apk` 中下载 APK。

## 关键文件

1. `app/build.gradle.kts`
   这里配置了 Compose、MMKV、OkHttp 和 Chaquopy。
2. `app/src/main/AndroidManifest.xml`
   这里配置了前台服务、开机广播和权限。
3. `app/src/main/python/ocr_bridge.py`
   这里是离线 OCR 的 Python 入口。
4. `.github/workflows/android-build.yml`
   这是给只有手机的场景准备的远程自动打包流程。
5. `gradlew` / `gradle/wrapper/gradle-wrapper.properties`
   这是已经生成好的 Gradle Wrapper，目标版本为 8.7。

## 说明

1. 当前工作区已经安装 JDK 17、Android SDK 35、NDK 26，并生成了 Gradle Wrapper。
2. 当前项目已改为：`本地 vendored ddddocr 源码 + 本地模型 + Python onnxruntime 兼容层 + Java onnxruntime-android`。
3. 当前环境已成功执行 `:app:assembleDebug`，APK 产物路径为 `app/build/outputs/apk/debug/app-debug.apk`。
