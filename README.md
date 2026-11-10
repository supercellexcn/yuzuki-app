# Yuzuki Android 客户端

在 `D:\node\yuzuki` 现有服务**零改动**的前提下提供 Android 客户端：
**专用 WebView 壳 + 原生 Media3 播放内核 + 原生底部 Tab 栏**。
完整实施规格见 [方案.md](./方案.md)。

## 硬约束

- `D:\node\yuzuki` 全程只读：不新增接口、不改 schema、不改配置、不改前端代码
- 除底部三个 Tab 外，界面全部由 WebView 内的现有前端渲染，App 不重画
- 账号与网页端同源（同一 `users` 表、同一 JWT），收藏 / 历史 / 偏好互通
- 播放走原生 Media3，支持锁屏 / 后台续播 / 通知栏换曲不消失

## 构建环境

| 项 | 版本 |
|---|---|
| JDK | **17**（AGP 8.7.x 要求；本机自带的 JDK 23 不兼容） |
| Gradle | 8.11.1（Wrapper） |
| Android SDK | Platform 35 + Build Tools 35.0.0 |
| compileSdk / targetSdk / minSdk | 35 / 35 / 26 |
| AGP / Kotlin / Compose BOM | 8.7.3 / 2.0.21 / 2024.10.00 |

本机工具链位于 `.toolchain/`（JDK 17、Gradle、Android SDK），**不入库**。
SDK 路径写在 `local.properties`（同样不入库）：

```properties
sdk.dir=D:/asmr-app/.toolchain/sdk
```

构建命令：

```bash
export JAVA_HOME=/d/asmr-app/.toolchain/jdk17   # 换成你自己的 JDK 17 亦可
./gradlew :app:assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

依赖走阿里云 Maven 镜像（`settings.gradle.kts`），Gradle 发行版走腾讯镜像
（`gradle/wrapper/gradle-wrapper.properties`）。

## 使用

1. 首次启动填写服务地址（如 `192.168.1.10:8080`），仅允许局域网 IP：
   `192.168.*` / `10.*` / `172.16–31.*` / `127.0.0.1`；公网地址与域名一律拒绝。
2. 用与网页端相同的账号登录；token 存于 Android Keystore 加密的 DataStore。
3. 首次登录强制改密由 WebView 内现有的 `/change-password` 页完成，App 不重复实现。

## 分阶段状态

| 阶段 | 内容 | 状态 |
|---|---|---|
| S0 | 工程骨架、主题圆角令牌、构建工具链 | ✅ 完成 |
| S1 | 服务器设置、host 白名单、TokenStore、YuzukiApi、WebView + token 注入 | ✅ 完成 |
| S2 | RootScreen / BottomTabs / 导航注入 | ⏳ |
| S3 | bridge.js 双代理 / JsBridge / PlaybackService | ⏳ |
| S4 | 锁屏封面 / 前台通知 / AudioFocus | ⏳ |
| S5 | web 端零改动回归与 A1–A5 断言 | ⏳ |
| S6 | 离线缓存（P1） | ⏳ |

> 验证结果（S0/S1）：`testDebugUnitTest` 6 个白名单断言全部通过（0 失败、0 错误），
> `assembleDebug` 构建成功，产物 `app/build/outputs/apk/debug/app-debug.apk`（约 11 MB）；
> `git -C D:\node\yuzuki status --porcelain` 为空，web 端零改动。

> 实现说明：方案 §4 明确 App 调用 `POST /api/auth/login`，因此 S1 增加了一个原生登录页
> `ui/LoginScreen.kt`（§8 文件清单未列出）。首次登录强制改密仍完全交给 WebView 内现有的
> `/change-password` 路由，App 不重复实现改密页。

## 安全边界

- 所有出站口（WebView 导航 / 资源请求、OkHttp，后续还包括封面加载）统一走
  `ServerConfig.isAllowedUrl()`，要求 host 与端口和已保存地址**完全一致**。
- 外链一律拦截、不跳系统浏览器：媒体 URL 带 `?token=`，外跳有泄露风险。
- token 不进入日志、崩溃上报与外链 URL。
- `usesCleartextTraffic=true` 仅用于局域网 HTTP，叠加运行时 host 白名单；
  不使用编译期 `network_security_config.xml`（无法按运行时 IP 通配）。

## 已知限制（P0）

- 仅家中 WiFi 可用：界面由 WebView 直连服务，离开 WiFi 只能打开壳，不能浏览与播放未缓存内容。
- 明文 HTTP 的 token 在局域网可被嗅探；若要公网访问，必须先给 nginx 加 HTTPS。
- WebView 退到后台被冻结时，前端 `navigator.sendBeacon` 的时长回写可能延迟或丢失，由 P1 原生补报队列兜底。
- 服务地址是运行时配置，工程内不内置任何固定 IP。
- S1 尚未接入 JS bridge：在网页端点「退出登录」后，原生 Keystore 中的 token 不会同步清除，
  WebView 重新加载时可能被重新注入；S3 接入 `JsBridge.logout()` 后补齐（方案 §4）。

## 约定

- 提交信息中文，格式 `feat: ...` / `fix: ...`；每阶段完成后立即提交，只提交本工程。
- 验证使用 typecheck / 断言 / 接口返回码，不截图、不逐屏人工确认。
