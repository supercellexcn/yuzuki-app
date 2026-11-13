# Yuzuki Android 客户端（全原生）

在 `D:\node\yuzuki` 现有服务**零改动**的前提下提供 Android 客户端：
**单 Activity + Jetpack Compose 全原生 UI + 直连现有 `/api/*` + 原生 Media3 播放内核**，
界面采用 **Material 3 风格**（不透明容器、高对比文字、全圆角）。WebView 与全部 JS 注入已彻底移除。完整实施规格与实现记录见 [方案.md](./方案.md)。

## 硬约束

- `D:\node\yuzuki` 全程只读：不新增接口、不改 schema、不改配置、不改前端代码
- 账号与网页端同源（同一 `users` 表、同一 JWT），收藏 / 历史 / 偏好互通
- 播放走原生 Media3，支持锁屏 / 后台续播 / 通知栏换曲不消失
- 首版交付 web 移动版全部功能（图片查看器、离线缓存等）；App 设置页只管本机缓存，服务端设置项由网页端承担
- 界面采用 Material 3 风格：不透明容器、高对比文字，不使用模糊 / 半透明叠层 / 壁纸背景
- 全工程不使用 WebView；所有框体一律圆角（`ui/theme/Radius.kt`）
- 验证只用编译 / 单测 / 接口断言 / Logcat，不截图

## 架构

```
ui/          单 Activity + AppNav 返回栈 + RootScreen（底部 3 Tab / 顶栏 / 底部筛选面板 / 迷你条 / 全屏播放器）
             LibraryScreen · WorkDetailScreen · CollectionsScreen · SettingsScreen（本机缓存管理）· PlayerUI
             LibraryListViewModel（库页数据 / 分页 / 滚动快照；两个 Lazy 滚动 state 由 RootScreen 持有）
ui/theme/    Material 3 配色 + Surface 组件封装（Glass* 为兼容层）· GlassSpec 纯函数 · 圆角 / 动画令牌
data/        Models（DTO + org.json 解析）· YuzukiApi（全量 REST）· AppContainer（服务定位）
             SettingsRepository · PrefsRepository · LibraryRepository（在线优先 + 离线兜底）
             OfflineCache（JSON 快照 / 封面 / 音频 / 播放记录补报队列）
             CacheUsage（缓存分类统计 / 清理 / 格式化，纯 JVM 可测）
             ImageSave（图片下载落相册：MIME / 文件名清洗 / MediaStore 写入，纯逻辑可测）
             PlaybackSnapshot / PlaybackSnapshotFile / PlaybackStore（本机播放位置，纯 Kotlin 可测）
playback/    PlaybackService（MediaSessionService，唯一持有 ExoPlayer 与 MediaSession）
             PlaybackController（MediaController 客户端 + StateFlow 投影 / 30s 快照 / 进播放页恢复）
             PlaybackAgent（元数据 / 封面 / 播放记录 / 断网重试）
             PlayReporter（播放记录 / 时长 / 离线补报）· QueueMath · RestoreLogic · ArtworkLoader
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

**版本号**：`versionName = 0.1.<BUILD>`，`versionCode = 1000 + BUILD`。
`BUILD` 存在根目录 `version.properties`，**每成功打出一次 release APK 就 +1**（`bumpBuildNumber`
任务挂在 `packageRelease` 之后），所以每次打包的版本号都不同，共享目录里能一眼分出新旧。
构建失败不产出 APK 时不会跳号；debug 构建不影响计数。需要重置版本号就手工改这个文件
（`BUILD=1` → 下次打包出 `0.1.1`）。

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

**Android 11+ 无线调试（配对码方式）**：开发者选项 →「无线调试」→「使用配对码配对设备」，
这里给的是**配对端口**（每次随机）和 6 位码；配对后的**连接端口是另一个端口**，也通常不是 5555：

```bash
ADB=.toolchain/sdk/platform-tools/adb.exe
"$ADB" pair <手机IP:配对端口> <6位配对码>   # 配对一次即可，凭据持久化
"$ADB" mdns services                        # 找 _adb-tls-connect._tcp 的 IP:端口
"$ADB" connect <手机IP:连接端口>            # 用连接端口，不是配对端口
./run-debug.sh --release                    # 设备已在线，不要再传 --connect
```

`--connect IP:5555` 只适用于「先 USB 执行 `adb tcpip 5555`」的老方式，与配对码模式不通用。
部分机型（荣耀 / 华为等）通过无线调试安装时会弹「是否允许安装此应用」，需**解锁并亮屏手动点允许**；
否则报 `INSTALL_FAILED_ABORTED: User rejected permissions`。此时不必重新编译，用
`adb install -r app/build/outputs/apk/release/app-release.apk` 重试即可；
若构建脚本因安装失败中断，记得补跑一次 `./publish-apk.sh`（APK 已产出）。

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
4. 底部 Tab 切换媒体库 / 播放器 / 历史；侧栏抽屉进入设置 / 收藏管理 / 修改密码 / 服务地址 / 退出登录。
   三个板块是横向 Pager，翻页与手指位移跟手：库页向左拖 → 播放器 → 历史，历史页向右拖 → 播放器 → 库；
   库页已在最左，继续向右拖的溢出量把侧栏拉出来，侧栏本身向左拖即可收回（都是跟手位移，不是固定时长动画）；
   拉起侧栏要求「向右位移 > 16dp 且明显大于纵向位移」，列表纵向滑动时的手指抖动不会误开；
   松手吸附门槛按方向区分：展开需过 40%，收起只需过 22%（≈57dp），不用一路滑到底。
   详情 / 收藏 / 设置 / 改密是覆盖层，盖在 Pager 之上，此时不响应翻页；覆盖层自带不透明页面底色，
   不会透出下层的库页卡片或播放器模糊封面。
   设置页只管理本机缓存：显示已使用缓存大小与分类明细，可二次确认后一键清除；不读写服务端设置。
   播放页信息区顺序固定为 标题 → 当前文件名 → 社团 → 系列 → 声优 → 类型标签；会还原上次播放的文件、
   整条播放列表与秒数（本机记录，不自动开播，点播放才续上）。
5. 收藏（作品 / 社团 / 声优 / 标签）、随机播放、循环模式切换都会在底部 Tab 上方弹出 App 内轻提示
   （圆角胶囊，1s 后消失，连续触发只保留最新一条；不走系统 Toast）。收藏统一调用
   `FavoritesState.toggleWorkWithToast` / `toggleWithToast`，失败提示「操作失败，请检查网络」。
6. 作品详情用顶栏返回箭头或系统返回键退出；图片查看器打开时返回键先关闭查看器。
   详情里点音视频文件「播放」会直接进全屏播放器（覆盖层自动收起），离开播放器回到库页。
   详情页**空白处右滑仍会拉出侧栏**，但「相关推荐」这一横向滚动行按下即认领手势
   （`Modifier.claimHorizontalGesture()`），在行内左右滑动（含滚到最左端再右滑）不会把侧栏拽出来。
7. 图片查看器：顶栏左 ✕ 关闭、中间只显示当前图片文件大小、右边「当前第几张/同文件夹图片总数」+ ⋮，
   图片区**左右滑动**切换同目录内的图片：拖动时整屏跟手平移、相邻图从屏幕边缘补位，
   松手位移过屏宽 25% 或速度过 900px/s 就滑完翻页，否则弹簧回弹（首尾循环）；
   ⋮ 菜单目前只有「下载图片」，保存到系统相册 `Pictures/Yuzuki/`（Android 10+ 免权限，8/9 首次会申请存储权限）。
   查看器内不再放「设为封面 / 设为壁纸」，这两个入口仍在文件列表长按菜单里。

## 分阶段状态

| 阶段 | 内容 | 状态 |
|---|---|---|
| N0 | 清除 WebView 层、原生导航骨架、GlassSurface 设计系统 | ✅ |
| N1 | 全量 REST 客户端、DTO、Repository、登录 / 改密 / 401 | ✅ |
| N2 | 媒体库（搜索 / 排序 / 筛选 / 分页 / 图片模式 / MobileDock）、抽屉、外观 | ✅ |
| N3 | 作品详情、文件树、图片查看器、设封面 / 壁纸、相关推荐 | ✅ |
| N4 | 原生播放队列、迷你条 + 全屏播放器、PlayReporter 时长上报 | ✅ |
| N5 | 锁屏 / 通知 / 线控、收藏 4 Tab、历史列表 / 卡片 | ✅ |
| N6 | 设置页（本机缓存用量与清除；web 式服务端设置项已移除） | ✅ |
| N7 | 离线缓存（JSON 快照、音频下载、播放记录补报队列） | ✅ |
| N8 | A1–A5 同源回归、真机锁屏 / 解码 / 上传断言 | ⏳ 需局域网服务与真机 |
| N9 | 系统媒体控制对齐（MediaController 迁移、队列进 playlist、三态循环、封面磁盘缓存、断网退避重试） | ✅ 静态验证通过，真机回归待 N8 |
| N10 | 本机播放位置恢复（30s / 暂停 / 切歌落盘、进播放页还原曲目 + 列表 + 进度、不自动开播） | ✅ 编译 + 73 例单测 + 真机落盘 / 冷启动恢复验证；划掉恢复与通知栏不覆盖待测 |

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

**本次增量验证（2026-11-11，库页滚动位置保持）**：

- `assembleDebug` / `testDebugUnitTest` 通过，**44 例全过、0 失败 0 错误**
  （`NavLogicTest` 由 8 例增至 16 例，新增 `ScrollRestore` 断言：冷启动必须 reset、条件变化重载、
  指纹碰撞、条件变化才置顶、置顶票守卫、恢复判定、下标夹紧）。
- 静态断言：`LibraryScreen` 中 `rememberLazy*State` 与「进页面先回顶部」零命中；
  `total = response.total` / `page` / `endReached` 赋值完整保留在 `LibraryListViewModel`。
- 冷启动防回归：`loadedFingerprint == null` 时必然发起一次 reset 请求，首屏「在 N 个作品中搜索」
  的数量不受影响（对应 `5cc8e04` 修过的「首帧用默认条件请求全库」问题）。
- 本机工具链已内置 `.toolchain/`；`local.properties` 的 `sdk.dir` 需指向当前工程目录（该文件不入库）。
- 2026-11-11 22:22：Release `0.1.4` 已通过无线调试装到 BVL-AN16，`versionName=0.1.4`、
  `am start` 后进程正常；共享目录已有 `yuzuki-0.1.4-1111-2222.apk` 与 `yuzuki-latest.apk`（11MB）。
- 2026-11-11 22:32：补齐「从全屏播放器点库 Tab 返回」路径 —— 播放器是叠加层，库页未离开组合，
  Tab 分支里的 `scrollToTop()` 会被仍挂载的库页立即消费。新增 `shouldScrollTopOnLibraryTab` 断言，
  `testDebugUnitTest` **45 例全过**；Release `0.1.5` 已装机（`versionName=0.1.5`、PID 22011）并发布
  `yuzuki-0.1.5-1111-2232.apk`。当前 `BUILD=6`，下次构建为 `0.1.6`。

**本次增量验证（2026-11-12，设置页重写为缓存管理）**：

- `assembleDebug` / `testDebugUnitTest` 通过，**53 例全过、0 失败 0 错误**
  （新增 `CacheUsageTest` 8 例：空目录 / 目录缺失、分类统计与求和、单位格式化分档、嵌套文件、
  清理归零与释放字节数、`pending_plays.json` 与 token 保留、目录骨架保留、重复清理幂等）。
- 静态断言：`SettingsScreen` 中 `saveSettings` / `scanStatus` / `uploadImage` / `scanReset` /
  `redownloadCovers` / `Slider` / `Switch` / `AppSettings` / `pendingImage` 全部零命中；
  统计与清理只走 `CacheUsage.measure/clear`，顶栏返回统一经 `AppNav.backOrHome()`。
- 缓存口径：`measure` 计入的文件 = `clear` 删除的文件（含 offline 的 JSON 快照 / 封面 / 音频、
  `cacheDir` 的 `img_*` 与 `artwork/`），`pending_plays.json` 与 `filesDir` 其它数据保留，清完显示 0 B。
- 未截图验证。
- Release `0.1.6` 已通过无线调试装到 BVL-AN16（配对端口 38873 / 连接端口 37525）：
  `versionName 0.1.5 → 0.1.6`、`versionCode 1005 → 1006`，启动后进程存活、Logcat 无崩溃命中。

**本次增量验证（2026-11-12，本机播放位置恢复）**：

- 功能：播放中每 **30s** 记一次、点暂停再记一次，写入 `filesDir/playback/state.json`
  （`rj / fileId / positionMs / durationMs / 可播放队列 / 标题 / 社团 / 声优 / 封面`），
  **不写服务端任何接口、不新增 `/api/*`**。下次打开播放页还原上次曲目、整条播放列表与进度并滚到该文件，
  **只还原 UI、不自动开播**；用户点播放 / 上一曲 / 下一曲时才用 `setMediaItems(items, index, startPositionMs)` 下发，
  因此恢复动作本身不会触发 `POST /api/plays` 误报。
- 后台续播 / 通知栏进来时服务里已有 playlist，`RestoreLogic.shouldRestore` 直接跳过，旧快照不覆盖正在播放的曲目；
  controller 未连接时等 `connect()` 回调再判定，避免点通知时误判成冷启动。
- 快照放 `filesDir/playback/`，**不在** `offline/` 与 `cacheDir` 内，设置页「清除缓存」不会删掉它，
  `CacheUsage` 口径与其 8 例单测无需改动；退出登录（`stop()` / `disconnect()`）清快照。
- `assembleDebug` / `testDebugUnitTest` 通过，**73 例全过、0 失败 0 错误**
  （新增 `PlaybackSnapshotTest` 12 例：往返编解码、最小 Work、脏数据、进度夹紧、不可播放项过滤、文件读写 / 覆盖 / 清除 / 损坏；
  `RestoreLogicTest` 8 例：冷启动恢复、未连接、已有队列、播放中、已有当前曲目、通知栏组合、下标夹紧、空队列）。
- 静态断言：`PlaybackSnapshot.kt` / `PlaybackStore.kt` / `PlaybackController.kt` 中
  `YuzukiApi` / `recordPlay` / `saveSettings` / `savePrefs` 零命中；`tryRestore()` 内无 `withController` / `setMediaItems`。
- 未截图验证。

**本次增量验证（2026-11-12，封面类型标签紧凑化）**：

- 封面标签多时（3~4 行）会压掉近半张封面，在**完全展示、不截断**的前提下收紧尺寸：
  `CoverTagChip` 行高 13→**12sp**、上下 padding 3→**2dp**、左右 9→**7dp**；
  封面标签行距 4→**2dp**（声优 chip 仍走 4dp，两处共用 `ChipFlow`、各自传参）；
  标签容器 padding `top 14 / bottom 6` → `top 10 / bottom 4`，底部渐变高度 64→**52dp**。
- 单行 chip 高 19→16dp，3 行 65→**52dp**（-20%），4 行 88→70dp；左右内边距收窄后每行可多容一个标签，
  实际常能少换一行。字号仍为 11sp，未引入省略号截断。
- `assembleRelease` 通过；`testDebugUnitTest` **78 例全过、0 失败 0 错误**。未截图验证。
- Release `0.1.10` 已发布共享：`yuzuki-0.1.10-1112-1833.apk` + `yuzuki-latest.apk`，
  sha256 `9f128bea…4ef0` 与本地 `app-release.apk` 一致。
- 已装到 BVL-AN16（无线调试配对端口 37101 → 连接端口 35727）：`versionName 0.1.9 → 0.1.10`、
  `versionCode 1010`，`am start` 后 PID 21892 存活、crash buffer 无 `FATAL`。真机标签观感由用户自测。

**本次增量验证（2026-11-12，图片模式网格错位修复）**：

- 现象：图片模式明明全是 1:1 封面，滚动后左右两列越往下越错开（"对不齐"）。
- 根因（`uiautomator dump` 实测，未截图）：屏宽 1280px / density 476dpi(2.975) 下 `PAGE_PAD_H=20dp`
  是 59.5px，网格左右两侧取整成 60/59，可用宽 1161px，扣掉 16dp(48px) 间距后 1113px **不能被 2 整除**；
  `StaggeredGridCells.Fixed` 把余数 1px 补给了第一列 → 实测**左列卡片 557×557、右列 556×556**。
  `LazyVerticalStaggeredGrid` 是两列各自累加 y（不是按行对齐），每张卡高度差 1px 就会一路累积，
  二三十张后肉眼可见。Detail 模式本来卡片不等高（真瀑布流），所以从没暴露。
- 修法：图片模式封面高度不再跟列宽走，由 `BoxWithConstraints` 按「可用宽 − 间距×(列数−1) ÷ 列数」
  算出整数列宽并统一：
  `WorkCard` 新增可选参数 `coverHeight: Dp?`，非空时封面用 `.height(coverHeight)` 取代 `.aspectRatio(1f)`；
  `LibraryScreen` / `CollectionsScreen`（历史卡片视图）两处瀑布流各包一层 `BoxWithConstraints` 计算并传入，
  仅在 `LibraryState.imageMode` 时生效。宽度仍差 1px（0.2%），肉眼仍是正方形。
- 实测验证（`0.1.11`，BVL-AN16）：图片模式四行卡片 **y=353 / 957 / 1561 / 2165 完全一致**，
  左 557×556、右 556×556，行距恒为 48px；切回 Detail 模式卡片高度仍为 1054 / 1053 / 1063（瀑布流自然高度，未受影响）。
- `assembleRelease` 通过；`testDebugUnitTest` 78 例全过。Release `0.1.11` 已装机并发布共享：
  `yuzuki-0.1.11-1112-1847.apk` + `yuzuki-latest.apk`，三方 sha256 一致 `15e12e56…1ced93`。

**本次增量验证（2026-11-12，声优标签收紧）**：

- 现象：卡片信息区的声优 chip 五个字就换行，两个五字声优排不进一行。
- 实测口径：卡片内容宽 485px（556px 卡 − 信息区左右各 12dp），旧 chip 五字宽 **241px**
  （文字 182px + 左右 10dp×2），两枚 241×2 + 行距 4dp(12px) = **494px > 485px** → 被挤到第二行，只差 9px。
- 改法：`ActorChip` 左右 10→**7dp**、上下 3→**2dp**、行高 14→**13sp**；声优 chip 行距 4→**3dp**
  （封面类型标签行距仍是 2dp，两者共用 `ChipFlow` 各自传参）。字号保持 12sp。
- 结果（`0.1.12` 真机 `uiautomator dump` 实测）：五字 chip 宽 241→**224px**，两枚 224×2+9 = **457px < 485px**，
  稳定同行还留 28px 余量；chip 高度同步变矮。列表模式共用同一组件，宽度富余不受影响。
- `assembleRelease` 通过；`testDebugUnitTest` 78 例全过。Release `0.1.12` 已装机并发布共享
  `yuzuki-0.1.12-1112-1852.apk` + `yuzuki-latest.apk`。

**本次增量验证（2026-11-12，播放页去下拉 + 状态栏沉浸）**：

- 播放页不再复刻 web 的「下滑收起」：删除 `dragY` / `detectVerticalDragGestures` 的 `dragToClose`
  与根 `graphicsLayer.translationY`，页面作为常驻功能板块不响应垂直拖动；退出统一走底部「库」Tab 与系统返回键
  （`BackHandler` 里 `playState.fullscreen` 分支未变）。
- 状态栏沉浸：`RootScreen` 的 `statusBarsPadding()` 改为**仅非全屏时**应用；
  `FullscreenPlayer` 的内容 `Column` 自己加 `statusBarsPadding()`。
  效果是封面模糊背景 + 渐变一路铺到屏幕顶端（状态栏图标白色，`themes.xml` 的 `windowLightStatusBar=false`），
  而标题 / 文件名 / 控件仍在状态栏下方，不再出现一条与播放页背景割裂的色带。
- 真机实测（`0.1.13`，BVL-AN16，`uiautomator dump` 节点坐标，未截图）：
  播放页打开时库页搜索框 `y 189 → 48`（根布局不再避让状态栏，证明背景可铺到 y=0），播放器标题 / 文件名
  `y=1705 / 1795` 位置正常；在封面区下拉 `900→1900` 后文件名仍在 `y=1795`（下拉关闭已失效）；
  切回「库」Tab 后搜索框恢复 `y=189`（其它页面避让照旧）。
- `assembleRelease` 通过；`testDebugUnitTest` 78 例全过。Release `0.1.13` 已装机并发布共享
  `yuzuki-0.1.13-1112-1908.apk` + `yuzuki-latest.apk`，三方 sha256 一致 `ba3bf71b…19dc5a9`。

**真机验证（2026-11-12，BVL-AN16 无线调试，端口 37169 配对 → 35727 连接）**：

- `0.1.7` / `0.1.8` 均安装成功、启动后进程存活、Logcat 无 `FATAL` / `AndroidRuntime` 命中。
- **30s 节拍落盘**：播放中读取 `run-as com.yuzuki.app cat files/playback/state.json`，
  首份为 `position_ms=0`，约 35s 后被重写为 `file_id` 跟随切歌、`position_ms=37038`（与系统 `dumpsys media_session`
  的 position 同向增长），确认按节拍写盘且切歌后 `file_id` 同步。
- **冷启动恢复**：覆盖安装（进程被杀）后重新启动 → 打开播放页，界面层级出现同一作品标题、
  `Chapter5.flac`、社团 / 声优与 `1:07 / 7:21`，与快照的 `position_ms=67240 / duration_ms=441756` 完全对应，
  且未自动出声。
- **本轮修掉两个落盘缺口**（真机暴露）：
  1. 通知栏 / 锁屏 / 蓝牙 / 耳机键的暂停不经过 `pause()`，且暂停后 ticker 的非强制保存因 `playing=false` 直接返回，
     进度会滞后一整个节拍 → 在 `onIsPlayingChanged(before.playing && !isPlaying)` 时强写一次；
  2. 切歌只靠 30s 节拍，`onMediaItemTransition` 时强写一次，否则「刚切下一首就被划掉」会恢复到上一首。
- 同时把 `sanitized()` 的夹紧语义改为「进度 ≥ 时长即按从头开始」，避免播完后恢复停在结尾、点播放直接跳下一首
  （单测 `进度到结尾按从头开始` 覆盖）。
- 仍未验证：多任务划掉后恢复（真机本轮只覆盖覆盖安装冷启动）、通知栏进入不覆盖（`RestoreLogicTest` 已断言规则，
  真机未跑）。
- **交互修复（真机反馈）**：冷启动 → 点播放时，进度条左侧已播放数字会先闪成 `0` 再跳回历史位置。
  根因是 `startRestored()` 在 `setMediaItems` 前就解除了 `pendingStart`，prepare 完成前 250ms ticker 与
  `onIsPlayingChanged` 都在抄 `currentPosition`（此时为 0）。改为新增 `awaitingStartPosition`：READY 且起始位置
  生效前，进度一律沿用快照值；判定抽到 `RestoreLogic.shouldUsePlayerPosition`（含 3s 超时兜底，避免永久冻结），
  并补 5 例断言（未就绪 / 就绪未生效 / 就绪已生效 / 快照为 0 / 超时兜底）。

**本次增量验证（2026-11-13，播放器三个 BUG 修复）**：

- **封面不换（冷启动最明显）**：`RemoteImage` 原本用
  `produceState(initialValue = cached, url, targetPx)`，而 `produceState` 内部的 state 是 `remember`（无 key）的
  `mutableStateOf` —— key 变化只重启 producer、**不会把 value 复位**，producer 里的 `if (value == null)`
  读到的仍是上一张非空位图，于是新 url 被整个跳过加载。冷启动时 `syncFromController()` 会把服务里残留的
  上一首投影进 state，播放器页作为邻页（`beyondViewportPageCount = 1`）已渲染旧封面，再点新作品播放就停在旧图。
  改为 `remember(url, targetPx) { mutableStateOf(peek(...)) }` + `LaunchedEffect(url, targetPx)`：
  url 一变立即复位、命中内存缓存则下一帧直接出图；同源修好「详情页设封面后 `&v=` 不刷新」。
- **详情点文件不跳播放器**：`setFullscreen(true)` 只让 Pager 滚到播放器页，而详情是**画在 Pager 之上的覆盖层**，
  仍盖在最上层。新增 `WorkDetailScreen(onOpenPlayer)`，由 `RootScreen.openPlayerFromOverlay()` 统一做
  「置全屏 → 收起覆盖层（`nav.replace(Route.Library)`，与底部播放器 Tab 同语义）→ 翻到播放器页」；
  「nav 变化 → Pager 归位」的 effect 加全屏守卫（`PlayerNav.shouldSyncPager`），否则「播放页点 (i) 进详情
  再点文件」会被 nav 同步又拽回库页。返回语义：离开播放器回到**库页**（不回详情），与点 Tab 一致。
- **播放器信息区补当前文件名**：标题下方一行 `item.file.filename`（12sp / Muted / 单行省略），
  队列换曲与冷启动恢复都能一眼看出在放哪个文件（文件名在 `PlaybackSnapshot` 里本来就有落盘）。
- `assembleRelease` 通过；`testDebugUnitTest` **80 例全过、0 失败 0 错误**（新增 `PlayerNav` 2 例：
  详情点文件必须收起覆盖层、全屏时 nav 不同步 Pager）。静态断言：`import ...produceState` 与
  `by produceState` 均零命中，`onOpenPlayer` / `item.file.filename` 命中。未截图验证。
- Release `0.1.23` 已装机（`versionCode 1023`，`lastUpdateTime 2026-11-13 12:27`），并发布共享
  `yuzuki-0.1.23-1113-1225.apk` + `yuzuki-latest.apk`，三方 sha256 一致 `5919fd5f…7c6e01c2`。
  无线调试：配对端口 40917（`pair` 报 protocol fault，旧凭据仍有效）→ mDNS 发现连接端口 43483。

**本次增量验证（2026-11-13，图片查看器改版 · 分支 `feat/image-viewer`）**：

- 布局按参考稿重排为「顶栏三件套 + 全屏图」：左 ✕ 关闭（40dp 半透明圆底）、
  中「当前图片文件大小」（参考图的「原画表示」不显示，`formatSize` 顺带补到 GB 档）、
  右「当前第几张/同文件夹图片总数」+ ⋮。所有顶栏元素自带半透明深色胶囊底，
  亮图上也能读清（项目仍禁用 blur / 整屏叠层遮罩）。
- 去掉原先的底栏「上一张 / 下一张」「N / M」与居中「操作」面板，改为**左右滑动**切换同一文件夹内的图片（首尾循环）。
  滑动与系统横滑同手感：`Modifier.draggable`（自带 touchSlop 与松手速度）+ `Animatable` 跟手平移，
  相邻图提前摆在 `位移 ± 屏宽` 处补位；松手由 `ViewerSwipe.decide(offset, velocity, pagePx)` 判定——
  位移过屏宽 **25%** 或速度过 **900px/s**（同向）→ 220ms `tween` 滑完整页后提交下标，否则 `spring(0.85)` 回弹。
  拖动开始即按 1440 把相邻图预热进 `ImageCache`，翻页首帧不黑闪。长按图片仍可叫出 ⋮ 菜单。
- ⋮ 菜单目前只有「下载图片」：`data/ImageSave.kt` 取 `mediaUrl` 字节后写入相册 `Pictures/Yuzuki/`。
  Android 10+ 走 MediaStore（`RELATIVE_PATH` + `IS_PENDING`，免权限）；8/9 走公共目录 + `MediaScannerConnection`，
  需 `WRITE_EXTERNAL_STORAGE`（清单带 `maxSdkVersion="28"`，首用时运行时申请）。同名文件追加 ` (n)` 不覆盖。
  结果用查看器自绘的底部提示条反馈（Dialog 是独立窗口，根布局的 `AppToast` 会被盖住）。
- 查看器解码长边 720 → **1440**（全屏 720 明显发软，仍降采样避免 OOM）。
- 查看器内的「设为作品封面 / 移动壁纸 / 桌面壁纸」已移除，这三项仍在**文件列表长按菜单**，功能不丢。
- `assembleRelease` 通过；`testDebugUnitTest` **91 例全过、0 失败 0 错误**（`ImageSaveTest` 7 例 +
  `ViewerSwipe` 4 例：未过阈值回弹、过阈值翻页、快速甩动、反向甩不翻页、首尾循环、屏宽 0 不判定）。
- 静态断言：`上一张` / `下一张` / `ViewerAction` / `图片操作` 在 `WorkDetailScreen.kt` 仅剩注释一处；
  `detectHorizontalDragGestures` / `dragX` 零命中；`draggable(` / `Animatable(0f)` / `ViewerSwipe.decide` /
  `warmUp(target)` / `graphicsLayer { translationX = offsetX.value }` 均命中。
- Release `0.1.24` 发布共享 `yuzuki-0.1.24-1113-1248.apk`（sha256 `a8d0e2e2…11fc60e`），已装到手机。
- Release `0.1.25`（跟手滑动版）发布 `yuzuki-0.1.25-1113-1308.apk` + `yuzuki-latest.apk`，
  与本地 `app-release.apk` 三方 sha256 一致 `48a98bf1…10ac3d5`；装机 `versionCode=1025`、启动无崩溃。
- 装机备注：设备同时存在 mDNS 与 IP 两个 adb 条目时报 `more than one device`，
  且 `-s` 必须写在 `install` 之前（`adb -s <serial> install -r`），写在后面会被当成「装到 SD 卡」而失败。

**本次增量验证（2026-11-13，详情页相关推荐不再误开侧栏）**：

- 现象：详情页「相关推荐」横向滚动行滚到最左端后再右滑，会把左侧抽屉拉出来。
- 根因：`RootScreen` 的侧栏手势是 Pager 外层 `pointerInput` 的 **Initial pass 旁路观察**——
  刻意不消费事件、也看不到子级是否消费。横向滚动行到边界后没人消费这段位移，
  外层照样累计 `sumX` 并起手。
- 修法：新增 `ui/DrawerGate.kt`
  - `DrawerGate`：本轮手势是否已被横向容器认领（`@Volatile` 标记）。
  - `Modifier.claimHorizontalGesture()`：`awaitEachGesture` + `awaitFirstDown(pass = Initial)`，
    **按下那一帧**就认领（此时外层 `sumX` 还是 0，必定未起手），抬手/取消时 `finally` 释放。
  - `DrawerDrag.canStart(...)`：把外层起手条件纯函数化并加 `innerClaimed` 守卫，JVM 可测。
- 作用于「相关推荐」的 `Row`（唯一横向滚动容器）；详情页其它区域右滑仍照常拉侧栏，库页/历史页手感不变。
- `testDebugUnitTest` **93 例全过**（新增 2 例：认领后不拉起、原起手条件保持）。
- Release `0.1.26` 发布 `yuzuki-0.1.26-1113-1317.apk` + `yuzuki-latest.apk`，
  与本地 `app-release.apk` 三方 sha256 一致 `e049dbf4…5391e0b`；装机 `versionCode=1026`、启动无崩溃。

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
- 设置页只统计 / 删除本机缓存目录，不再调用 `PUT /api/settings`，也不触发清空媒体库等服务端危险操作；清除缓存需二次确认。
- 本机播放位置只写 `filesDir/playback/state.json`：不调用任何服务端接口、不参与缓存统计与清除；退出登录时清掉。
- 「下载图片」只把当前图片写入系统相册 `Pictures/Yuzuki/`（Android 10+ 免权限；8/9 的存储权限仅
  `maxSdkVersion=28` 生效），不上传、不改服务端数据。

## 已知限制

- 仅家中 WiFi 可用；离开 WiFi 只能看离线缓存内容；明文 HTTP 的 token 在局域网可被嗅探。
- 冷门格式（wma 等）需后续接入 `media3-decoder-ffmpeg`；flac/mkv/mp4/wav/m4a 由 Media3 默认解码器覆盖。
- 界面采用 Material 3 风格：不再渲染液态玻璃 / 壁纸 / 模糊 / 半透明叠层；
  服务端的外观设置（模糊、透明度、壁纸启停）由网页端管理，App 设置页不提供入口，也不参与本机渲染
  （作品详情页的「设为壁纸」仍会上传图片到服务端，属于独立功能）。
- 管理后台 `/admin` 未实现（web 移动端亦由路由守卫跳回媒体库）。
- web 端后续新增功能不会自动同步到 App，两端独立发版。

## 约定

- 提交信息中文，格式 `feat: ...` / `fix: ...`；每阶段完成后立即提交，只提交本工程。
- 验证使用 typecheck / 断言 / 接口返回码，不截图、不逐屏人工确认。
- 每次改动完成后默认打 release 包并发布到 `//192.168.5.4/FileTemp`，不再逐次确认；
  同步结果给出产物路径与哈希校验结论；共享不可达时只警告、不阻断构建。
