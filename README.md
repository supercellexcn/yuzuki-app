# Yuzuki Android 客户端（全原生）

在 `D:\node\yuzuki` 现有服务**零改动**的前提下提供 Android 客户端：
**单 Activity + Jetpack Compose 全原生 UI + 直连现有 `/api/*` + 原生 Media3 播放内核**，
界面采用 **Material 3 风格**（不透明容器、高对比文字、全圆角）。WebView 与全部 JS 注入已彻底移除。完整实施规格与实现记录见 [方案.md](./方案.md)。

## 硬约束

- `D:\node\yuzuki` 全程只读：不新增接口、不改 schema、不改配置、不改前端代码
- 账号与网页端同源（同一 `users` 表、同一 JWT），收藏 / 历史 / 偏好互通
- 播放走原生 Media3，支持锁屏 / 后台续播 / 通知栏换曲不消失
- 首版交付 web 移动版全部功能（含完整设置页、图片查看器、离线缓存）
- 界面采用 Material 3 风格：不透明容器、高对比文字，不使用模糊 / 半透明叠层 / 壁纸背景
- 全工程不使用 WebView；所有框体一律圆角（`ui/theme/Radius.kt`）
- 验证只用编译 / 单测 / 接口断言 / Logcat，不截图

## 架构

```
ui/          单 Activity + AppNav 返回栈 + RootScreen（底部 3 Tab / 顶栏 / 底部筛选面板 / 迷你条 / 全屏播放器）
             LibraryScreen · WorkDetailScreen · CollectionsScreen · SettingsScreen · PlayerUI
ui/theme/    Material 3 配色 + Surface 组件封装（Glass* 为兼容层）· GlassSpec 纯函数 · 圆角 / 动画令牌
data/        Models（DTO + org.json 解析）· YuzukiApi（全量 REST）· AppContainer（服务定位）
             SettingsRepository · PrefsRepository · LibraryRepository（在线优先 + 离线兜底）
             OfflineCache（JSON 快照 / 封面 / 音频 / 播放记录补报队列）
playback/    PlaybackService（MediaSessionService，唯一持有 ExoPlayer 与 MediaSession）
             PlaybackController（MediaController 客户端 + StateFlow 投影）
             PlaybackAgent（元数据 / 封面 / 播放记录 / 断网重试）
             PlayReporter（播放记录 / 时长 / 离线补报）· QueueMath · ArtworkLoader
```

播放队列整体作为 ExoPlayer playlist 下发，上一曲 / 下一曲 / 自动切歌 / 单曲循环 / 列表循环
全部由 ExoPlayer 自己推进，所以通知栏、锁屏、蓝牙耳机与 App 内看到的永远是同一首歌；
UI 经 `MediaController` 下指令、从 `StateFlow` 读状态，Activity 里不持有播放器实例，
页面重建 / 息屏 / 切 App 都不影响播放。

## 构建环境

| 项 | 版本 |
|---|---|
| JDK | **17**（AGP 8.7.x 要求；本机自带的 JDK 23 不兼容） |
| Gradle | 8.11.1（Wrapper） |
| Android SDK | Platform 35 + Build Tools 35.0.0 |
| compileSdk / targetSdk / minSdk | 35 / 35 / 26 |
| AGP / Kotlin / Compose BOM | 8.7.3 / 2.0.21 / 2024.10.00 |
| Media3 / OkHttp | 1.4.1 / 4.12.0 |

本机工具链位于 `.toolchain/`（JDK 17、Gradle、Android SDK），**不入库**。
SDK 路径写在 `local.properties`（同样不入库）：

```properties
sdk.dir=D:/asmr-app/.toolchain/sdk
```

构建命令：

```bash
export JAVA_HOME=/d/asmr-app/.toolchain/jdk17   # 换成你自己的 JDK 17 亦可
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
# 产物：app/build/outputs/apk/debug/app-debug.apk

./gradlew :app:assembleRelease                  # 日常自用请装这个
# 产物：app/build/outputs/apk/release/app-release.apk（约 10MB）
```

> **Debug 还是 Release**：`debuggable` 的包会让 Compose 的布局/绘制慢一大截 ——
> 同一台机器实测列表滑动 janky 帧 debug 约 12%、release 约 2%。日常安装使用请装 `release` 包
> （已复用 debug 签名，可直接 `install -r` 覆盖安装，不必先卸载）。

**一键装机到手机**（省掉手动传 APK / 点安装）：手机开启 USB 调试并授权后执行

```bash
./run-debug.sh                  # 编译 Debug → 安装 → 启动
./run-debug.sh --release        # 编译 Release → 安装 → 启动（体验最流畅）
./run-debug.sh --fast           # 跳过编译，直接安装已有 APK（最快）
./run-debug.sh --log            # 启动后跟随 Logcat
./run-debug.sh --connect IP:5555  # 无线调试（首次需 USB 执行 adb tcpip 5555）
./run-debug.sh --release --no-publish  # 只装机，不发共享
```

**发布 Release 包到局域网共享**（手机 / 其它电脑直接下载安装）：

```bash
./publish-apk.sh                # 拷 app-release.apk 到 //192.168.5.4/FileTemp
# yuzuki-<版本>-<MMdd-HHmm>.apk   带版本与时间的归档
# yuzuki-latest.apk               固定名字，永远是最新一次发布
```

`./run-debug.sh --release` 会在装完机后自动执行这一步（`--no-publish` 可跳过）。
共享不可达时只警告、不影响装机。

Windows 下也可直接双击 `run-debug.bat`（默认完整流程）。`--help` 查看全部选项。
脚本只做构建 / 安装 / 启动，不改业务代码；无已授权设备时会立即报错，不会白跑一次构建。

依赖走阿里云 Maven 镜像（`settings.gradle.kts`），Gradle 发行版走腾讯镜像
（`gradle/wrapper/gradle-wrapper.properties`）。

> **依赖策略**：为规避 KSP 注解处理器与镜像下载风险，工程只保留 OkHttp / Media3 / DataStore / Compose，
> 未引入 Retrofit、kotlinx-serialization、Room、Coil、Navigation Compose、Paging、ffmpeg decoder，
> 改以等价自实现替代（OkHttp+org.json 解析、JSON 快照离线缓存、自写图片缓存与返回栈）。
> 功能覆盖不变，偏差逐条记录在 `方案.md` §15.2。

## 使用

1. 首次启动填写服务地址（如 `192.168.1.10:8080`），仅允许局域网 IP：
   `192.168.*` / `10.*` / `172.16–31.*` / `127.0.0.1`；公网地址与域名一律拒绝。
2. 用与网页端相同的账号登录；token 存于 Android Keystore 加密的 DataStore。
3. 首次登录强制改密由**原生** `ChangePasswordScreen` 完成（不再依赖 web 端路由）。
4. 底部 Tab 切换媒体库 / 收藏 / 播放历史；主页顶栏「筛选」打开排序与聚合筛选底部面板，「更多」进入设置 / 修改密码 / 服务地址 / 退出登录。
5. 作品详情用顶栏返回箭头或系统返回键退出；图片查看器打开时返回键先关闭查看器。

## 分阶段状态

| 阶段 | 内容 | 状态 |
|---|---|---|
| N0 | 清除 WebView 层、原生导航骨架、GlassSurface 设计系统 | ✅ |
| N1 | 全量 REST 客户端、DTO、Repository、登录 / 改密 / 401 | ✅ |
| N2 | 媒体库（搜索 / 排序 / 筛选 / 分页 / 图片模式 / MobileDock）、抽屉、外观 | ✅ |
| N3 | 作品详情、文件树、图片查看器、设封面 / 壁纸、相关推荐 | ✅ |
| N4 | 原生播放队列、迷你条 + 全屏播放器、PlayReporter 时长上报 | ✅ |
| N5 | 锁屏 / 通知 / 线控、收藏 4 Tab、历史列表 / 卡片 | ✅ |
| N6 | 完整设置页（全量读写、扫描 / 推荐、上传、危险操作） | ✅ |
| N7 | 离线缓存（JSON 快照、音频下载、播放记录补报队列） | ✅ |
| N8 | A1–A5 同源回归、真机锁屏 / 解码 / 上传断言 | ⏳ 需局域网服务与真机 |
| N9 | 系统媒体控制对齐（MediaController 迁移、队列进 playlist、三态循环、封面磁盘缓存、断网退避重试） | ✅ 静态验证通过，真机回归待 N8 |

**已完成的验证（2026-11-10）**：

- `assembleDebug` 成功，`app-debug.apk` 约 19MB；`compileDebugKotlin` / `compileDebugUnitTestKotlin` 无错误。
- `testDebugUnitTest` 共 **36 例全过、0 失败 0 错误**：`HostPolicy` 6 + `Models` 5 + `QueueMath` 4（队列边界 / 可播放过滤 / 循环三态 / mediaId）+ `GlassSpec` 4 + `NavLogic` 8 + `WebColors` 9。
- 静态断言：源码中 `WebView` / `addJavascriptInterface` / `evaluateJavascript` 零命中；
  `androidx.webkit` 依赖已移除，`web/` 目录与 `yuzuki-bridge.js` 已删除。
- 界面清晰度静态断言：`blur(` / `Color.White` / `copy(alpha` / 壁纸背景调用 / 高光渐变 全部零命中。
- 移动端交互静态断言：`MobileDock` / 随 `loading` 条件销毁的分页 `LaunchedEffect` 零命中；返回统一走 `AppNav.backOrHome()`。
- 媒体控制静态断言：`PlayerHolder` 零命中（播放器实例只存在于 `PlaybackService`）；`NotificationCompat` /
  `NotificationChannel` 零命中（通知与锁屏由 `MediaSessionService` 自动生成，未自绘）；`MediaController`
  为 UI 唯一指令入口；`setTitle` / `setArtist` / `setAlbumTitle` / `setDurationMs` / `setArtworkData` 全部命中。
- `git -C D:\node\yuzuki status --porcelain` 为空 → web 端 / 后端零改动。

**仍待验证**：A1–A5 接口同源断言、锁屏 metadata 与 `artworkData`、连续 `next()` 3 次通知不消失、
息屏 5 分钟进度增长、蓝牙耳机按键、通知点击直达播放页、flac/mkv 解码、base64 图片上传返回码、
断网离线回放与补报去重。

## 安全边界

- 所有出站口（API、封面、壁纸、媒体）统一走 `HostPolicy` / `ServerConfig.isAllowedUrl()`，
  要求 host 与端口和已保存地址**完全一致**；外链一律拦截（媒体 URL 带 `?token=`）。
- `usesCleartextTraffic=true` 仅用于局域网 HTTP，叠加运行时 host 白名单；
  不使用编译期 `network_security_config.xml`（无法按运行时 IP 通配）。
- token 不进入日志、崩溃上报与外链 URL；日志脱敏 `token=***`。
- 退出登录清 Keystore token、停播放服务；离线音频缓存默认保留。
- 设置页 `PUT /api/settings` 是全局写入，会影响 PC 浏览器；清空媒体库等危险操作已加二次确认。

## 已知限制

- 仅家中 WiFi 可用；离开 WiFi 只能看离线缓存内容；明文 HTTP 的 token 在局域网可被嗅探。
- 冷门格式（wma 等）需后续接入 `media3-decoder-ffmpeg`；flac/mkv/mp4/wav/m4a 由 Media3 默认解码器覆盖。
- 界面采用 Material 3 风格：不再渲染液态玻璃 / 壁纸 / 模糊 / 半透明叠层；
  服务端的外观设置（模糊、透明度、壁纸启停）仍可读写，但只影响网页端，设置页已注明。
- 管理后台 `/admin` 未实现（web 移动端亦由路由守卫跳回媒体库）。
- web 端后续新增功能不会自动同步到 App，两端独立发版。

## 约定

- 提交信息中文，格式 `feat: ...` / `fix: ...`；每阶段完成后立即提交，只提交本工程。
- 验证使用 typecheck / 断言 / 接口返回码，不截图、不逐屏人工确认。
