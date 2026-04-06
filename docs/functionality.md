# 功能说明文档

## 与原 JS 脚本的对应关系

1. `buildDeviceCode(username)`
   与 JS `buildDeviceCode` 保持一致，算法为 `md5("ctyun_phone_keepalive:" + lowercase(username))`，前缀为 `web_phone_`。
2. `CtyunApiClient.login`
   对齐 JS 的 `buildLoginPayload` 和 `/api/auth/client/login` 调用逻辑，保留 challenge、`sha256Password`、`captchaCode` 和设备字段。
3. `KeepAliveEngine.loginWithCaptchaRetry`
   对齐 JS 的 `login()` 循环，兼容 `51040`、`51030`、`51031`、`51085`，失败时调用离线 OCR 重试。
4. `KeepAliveEngine.keepAliveOne`
   对齐 JS 的 `connect -> state -> waitUntilReady -> finishDesktopEntry` 核心流程。
5. `ClinkProtocol`
   已将 hello、link、auth、mouse mode、custom json、login info、display init 等二进制编码逻辑迁移到 Kotlin。

## 目前状态

1. UI、存储、ROOT、通知、调度和 OCR 已经全部落到 Android 工程。
2. 主业务 HTTP 状态机已落地。
3. Clink 协议编码已迁移，并补了 `OkHttp WebSocket` 的 MAIN 通道探测入口。
4. Clink 多通道握手已经按 `MAIN -> MAIN_INIT -> DISPLAY -> INPUTS/CURSOR -> optional channels -> hold -> closeAll` 顺序补到 Kotlin。
5. 多通道附着仍需要在有 ROOT 真机和证书数据的环境下继续验证帧级兼容性。

## 当前技术阻塞

1. 原始 `ddddocr` 的 pip 依赖链会卡在 `onnxruntime` Android wheel 缺失。
2. 现已改为本地集成方案，绕过该问题，APK 可以正常构建。
3. 当前剩余风险主要是 ROOT 真机上的 Clink 帧级联调与 OCR 实际识别率验证，而不是构建链路问题。

## 后续建议

1. 在 ROOT 真机上抓取一次真实 `desktopInfo`，验证 `wss://.../clinkProxy/{desktopId}` 地址选择。
2. 将 `ClinkProtocol` 接入 `OkHttp WebSocket`，按 `MAIN -> DISPLAY -> INPUTS/CURSOR -> 其他可选通道` 顺序完成联调。
3. 根据真机日志继续对齐 `MAIN_INIT(103)`、`ACK_SYNC(1)`、`PING/PONG` 的帧级行为。
