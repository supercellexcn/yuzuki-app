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
| Media3 / webkit / OkHttp | 1.4.1 / 1.12.1 / 4.12.0 |

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
| S2 | RootScreen / BottomTabs / 导航注入 | ✅ 完成 |
| S3 | bridge.js 双代理 / JsBridge / PlaybackService | ✅ 完成 |
| S4 | 锁屏封面 / 前台通知 / AudioFocus | ✅ 完成 |
| S5 | web 端零改动回归与 A1–A5 断言 | ⏳ |
| S6 | 离线缓存（P1） | ⏳ |

> 验证结果（S0–S4）：`testDebugUnitTest` 共 24 例（`HostPolicy` 6 + `NavScript` 5 +
> `NativeEvents` 4 + `BridgeScriptContract` 5 + `ViewportPolicy` 2 + `PlayerCommands` 2）全部通过、
> 0 失败 0 错误；`assembleDebug` 构建成功，产物 `app/build/outputs/apk/debug/app-debug.apk`；
> `git -C D:\node\yuzuki status --porcelain` 为空，web 端零改动。

> 实现说明：方案 §4 明确 App 调用 `POST /api/auth/login`，因此 S1 增加了一个原生登录页
> `ui/LoginScreen.kt`（§8 文件清单未列出）。首次登录强制改密仍完全交给 WebView 内现有的
> `/change-password` 路由，App 不重复实现改密页。
>
> S2 的 `__yuzukiNav` / `__yuzukiOpenPlayer` 由独立的 `NavScript` 做文档级注入；S3 的
> `yuzuki-bridge.js` 用 `if (!window.__yuzukiNav)` 守卫，确实不覆盖 S2 实现（有静态断言保证）。
> 底部 Tab 栏通过 WebView `padding(bottom = 56dp)` 避让，不叠加覆盖。
>
> S3 注入顺序为 token → nav → bridge，三者都在页面自身 JS 执行前生效。`navigator.mediaSession`
> 是**代理而非删除**：前端 `mediaSessionSupported()` 用 `'mediaSession' in navigator` 判定，删除后
> 就不再写元数据，锁屏会拿不到封面 / 标题 / 声优。ExoPlayer 由 `PlaybackService` 持有，
> `PlayerHolder` 单例桥接 WebView 与 Service 并每 250ms 回推 `timeupdate`；文档级注入时页面尚未
> `new Audio()`，状态快照先暂存、`NativeAudio` 构造后回放一次。前端 `logout()` / 401 清 token 会经
> `Storage.prototype.removeItem` hook 调 `JsBridge.logout()`，同步清 Keystore 并停播放服务。
>
> S4：`ArtworkLoader` 用 OkHttp 下载封面 bytes 后交给 `setArtworkData`（HTTP URI 不直接给系统）；
> 前端 `updateMediaMetadata()` 早于 `a.src` 赋值，故元数据在 `setSource` 换 MediaItem 时一并写入，
> 封面异步到达后二次 `replaceMediaItem` 更新。`ForwardingPlayer` 把系统上一曲 / 下一曲转发到
> `__yuzukiPlayerCommand`（点击 BottomPlayer 现有按钮，队列仍归前端），`SessionCallback.onConnect`
> 显式开放这两个 player command；另有 `POST_NOTIFICATIONS` 运行时申请与一次性电池优化引导对话框。
>
> **本轮修复的两个界面 bug（同一根因）**：WebView 原设 `useWideViewPort=false`，会忽略页面
> viewport meta 按约 980px 宽视口渲染，导致 `matchMedia('(max-width:760px)')=false` 走桌面分支 ——
> 这正是「侧栏功能被挤在很小空间（admin 本应隐藏却出现）」与「PC 停用移动端壁纸、App 仍有壁纸
> （实际读的是 desktop 开关）」的共同原因。改为 `useWideViewPort=true` 即命中移动断点；同时把
> `cacheMode` 从 `LOAD_CACHE_ELSE_NETWORK`（过期也用缓存）改为 `LOAD_DEFAULT`，保证 `/api/settings`
> 等设置变更实时生效，静态资源仍靠 nginx `expires 30d` 缓存（P0 离线壳不变）。全程**未改 yuzuki
> 一行、未注入任何 CSS**，并用 `YuzukiViewport` 的 Logcat 断言 `viewport(mobile|WxH)=true|宽x高`
> 供真机做确定性验证。

## 安全边界

- 所有出站口（WebView 导航 / 资源请求、OkHttp，后续还包括封面加载）统一走
  `ServerConfig.isAllowedUrl()`，要求 host 与端口和已保存地址**完全一致**。
- 外链一律拦截、不跳系统浏览器：媒体 URL 带 `?token=`，外跳有泄露风险。
- `addJavascriptInterface("YuzukiNative")` 仅在 host 校验通过时启用；`setSource(url)` 再次校验，
  并禁止跨协议重定向，媒体 URL 的 `?token=` 不会外带。
- bridge 日志脱敏（`token=` 替换为 `token=***`），bridge 方法均有 try/catch 与参数校验。
- token 不进入日志、崩溃上报与外链 URL。
- `usesCleartextTraffic=true` 仅用于局域网 HTTP，叠加运行时 host 白名单；
  不使用编译期 `network_security_config.xml`（无法按运行时 IP 通配）。

## 已知限制（P0）

- 仅家中 WiFi 可用：界面由 WebView 直连服务，离开 WiFi 只能打开壳，不能浏览与播放未缓存内容。
- 明文 HTTP 的 token 在局域网可被嗅探；若要公网访问，必须先给 nginx 加 HTTPS。
- WebView 退到后台被冻结时，前端 `navigator.sendBeacon` 的时长回写可能延迟或丢失，由 P1 原生补报队列兜底。
- 服务地址是运行时配置，工程内不内置任何固定 IP。
- S4 的锁屏 / 通知行为（`MediaController` 读到 `title/artist/artworkData`、连续 `next()` 3 次通知不消失、
  息屏 5 分钟进度仍增长、AudioFocus 与拔耳机暂停）需真机验证；本轮只做编译与静态断言。
- S3 的播放链路断言（`setSource` 带 token、ExoPlayer `BUFFERING→READY`、`/api/plays` 新增同 userId
  记录、flac/mkv 解码）同样需真机 + 局域网服务。
- 电池优化引导只在 App 启动时检查并弹一次；用户选「稍后」后不再提醒（可在系统设置里自行修改）。

## 约定

- 提交信息中文，格式 `feat: ...` / `fix: ...`；每阶段完成后立即提交，只提交本工程。
- 验证使用 typecheck / 断言 / 接口返回码，不截图、不逐屏人工确认。
