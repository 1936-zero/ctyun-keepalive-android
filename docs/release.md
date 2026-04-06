# Release 交付流程

## 本地产物

当前我已经在工作区成功构建出：

1. Debug APK: `app/build/outputs/apk/debug/app-debug.apk`
2. Release APK: `app/build/outputs/apk/release/app-release-unsigned.apk` 或 `app-release.apk`

说明：

1. 如果没有提供 `keystore.properties`，release 通常是未签名或使用默认流程生成的不可直接分发包。
2. 如果提供正式签名信息，则可以直接生成可交付的 release APK。

## 推荐交付方案

最适合你当前场景的是：

1. 用 GitHub Actions 自动构建
2. 用 GitHub Secrets 注入正式 keystore
3. 打 tag 后自动发布到 GitHub Release

## 需要的 Secrets

在 GitHub 仓库设置以下 Secrets：

1. `RELEASE_KEYSTORE_BASE64`
2. `RELEASE_STORE_PASSWORD`
3. `RELEASE_KEY_ALIAS`
4. `RELEASE_KEY_PASSWORD`

## 生成 keystore 的方式

如果你还没有 keystore，可以在有 JDK 的环境执行：

```bash
# 生成 release keystore
keytool -genkeypair -v -keystore release-keystore.jks -alias release -keyalg RSA -keysize 2048 -validity 10000
```

再把 keystore 转成 Base64：

```bash
# 转成 Base64 供 GitHub Secrets 使用
base64 -w 0 release-keystore.jks
```

## GitHub 发布方式

### 方式 1：手动触发

1. 打开 GitHub 仓库的 `Actions`
2. 运行 `Android Build And Release`
3. 下载 `ctyun-keepalive-release-apk`

### 方式 2：打 tag 自动发版

1. 创建 tag，例如 `v1.0.0`
2. push tag 到 GitHub
3. Actions 自动执行 release 构建
4. 自动把 APK 挂到 GitHub Release

## 当前最好的方案

如果你想稳定交付给手机用户，优先顺序建议是：

1. GitHub Actions 构建 signed release APK
2. GitHub Release 托管 APK
3. 手机直接从 Release 页面下载安装
