# Yuzuki Android 客户端（全原生）

远程仓库：<https://github.com/supercellexcn/yuzuki-app.git>（`main` 分支；LICENSE 为 Apache-2.0）。
本地构建工具链（`.toolchain/`、`local.properties`）与 APK 不入库，克隆后按 `方案.md` 配置 JDK17 / Android SDK 即可构建。

在 `D:\node\yuzuki` 现有服务**零改动**的前提下提供 Android 客户端：
**单 Activity + Jetpack Compose 全原生 UI + 直连现有 `/api/*` + 原生 Media3 播放内核**，
界面采用 **Material 3 风格**（不透明容器、高对比文字、全圆角）。WebView 与全部 JS 注入已彻底移除。完整实施规格与实现记录见 [方案.md](./方案.md)。

## 硬约束

- `D:\node\yuzuki` 全程只读：不新增接口、不改 schema、不改配置、不改前端代码
- 账号与网页端同源（同一 `users` 表、同一 JWT），收藏 / 历史互通；
  **库页的搜索 / 筛选 / 排序与网页端不互通，但 App 会记住自己上次的条件（2026-11-24 起）**：
  存在本机 DataStore `app_library_filter`（`LibraryFilterPrefs`），冷启动读回来；
  App 仍不读写服务端 `/api/prefs`，所以既不会带出、也不会覆盖网页端的搜索条件
- 播放走原生 Media3，支持锁屏 / 后台续播 / 通知栏换曲不消失
- 首版交付 web 移动版全部功能（图片查看器、离线缓存等）；App 设置页负责「外观 + 手势 + 本机缓存」，
  **这些设置只读本机 DataStore，不读服务端 `/api/settings`**：网页端改主题色不影响 App，App 改色也不回写网页端
- 界面采用 Material 3 风格：不透明容器、高对比文字，不使用模糊 / 半透明叠层 / 壁纸背景
  （底部导航栏 2026-11-18 曾改为亚克力毛玻璃，实测无法稳定取样，已回退为纯黑，见「交互改版」）。
  唯一例外是浮动圆控件层的遮罩：用户 2026-11-18 明确要求「透明模糊遮罩」，
  做法是给内容层整体 `Modifier.blur` + 压暗，不涉及「从栏后取样」那种失败实现
- 全工程不使用 WebView；所有框体一律圆角（`ui/theme/Radius.kt`）
- 验证只用编译 / 单测 / 接口断言 / Logcat，不截图

## 架构

```
ui/          单 Activity + AppNav 返回栈 + RootScreen（Column：内容 weight + 底部 4 Tab 在下面）
             LibraryScreen · WorkDetailScreen · CollectionsScreen（收藏 / 历史）· SettingsScreen · PlayerUI
             SideDrawer（浮动圆控件：4 筛选圆 + 账号 / 设置圆，固定贴底；DrawerGeometry 为纯数值几何）
             FavableTag（详情 / 收藏共用的标签 + 内嵌收藏加减号）
             LibraryListViewModel（库页数据 / 分页 / 滚动快照；两个 Lazy 滚动 state 由 RootScreen 持有）
             FeedPull（信息流顶部下拉手势 + 纯判定）
ui/theme/    Material 3 配色 + Surface 组件封装（Glass* 为兼容层）· GlassSpec 纯函数 · 圆角 / 动画令牌
data/        Models（DTO + org.json 解析）· YuzukiApi（全量 REST）· AppContainer（服务定位）
             SettingsRepository · LibraryRepository（在线优先 + 离线兜底）
             AppearancePrefs / AppearanceStore（App 本机外观，DataStore `app_appearance`）
             InteractionPrefs / InteractionStore（手势时长等交互偏好，DataStore `app_interaction`）
             FilterPrefs / FilterStore（库页上次的搜索 / 筛选 / 排序，DataStore `app_library_filter`）
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

**队列边界 = 被点文件所在目录**（2026-11-19 起）：`PlaybackController.play(rj, file, work, queue)`
的 `queue` 语义是「候选池」，详情页传的是整棵文件树的可播放项；真正下发给 ExoPlayer 的列表
由 `QueueMath.sameDir()` 收敛为 `file` 的父目录（`MediaFile.dirOf()`）内的可播放文件，
**不含子目录也不含父目录**，同一作品的 Track / 特典 / SE 分目录时不会跨目录连播。
目录算法只有 `MediaFile.dirOf()` 一处；恢复用的 `PlaybackSnapshot.sanitized()` 按同一规则收敛，
旧版本写下的跨目录快照会就地修正。收敛后为空时退化为「只播这一首」。

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

**版本号**：`versionName = 0.<MINOR>.<PATCH>`，`versionCode = 1000 + BUILD`。
`version.properties` 里存三个值：`BUILD`（每成功打出一次 release APK 就 +1，`bumpBuildNumber`
任务挂在 `packageRelease` 之后）、`MINOR`（手工改小版本）、`PATCH_BASE`（起算的 BUILD）。
`PATCH = BUILD − PATCH_BASE`，所以 0.2.0 之后每次打包依次 0.2.1 / 0.2.2 …；
换 0.3 时把 `MINOR` 改 3、`PATCH_BASE` 改成当时的 BUILD，即从 0.3.0 重新起算。
构建失败不产出 APK 时不会跳号；debug 构建不影响计数。

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
4. 底部 Tab 切换媒体库 / 收藏 / 播放器 / 历史（收藏 2026-11-25 起从抽屉圆改为底部星标 Tab）；
   **没有侧栏抽屉**：在库页右滑后浮出一组圆控件 —— 上排 4 个筛选圆（社团 / 系列 / 声优 / 标签），
   下排左圆是账号菜单（改密 / 服务地址 / 退出登录）、右圆是设置；
   圆组**固定贴在底部导航栏上方 18dp**，不论手指从屏幕哪个高度划出都落在同一位置。
   底部导航是纯黑 + 顶部 1px 分割线（亚克力毛玻璃试过四版都失败，已回退）；
   右侧原来的悬浮竖条（MobileDock：主页 / 回顶 / 切视图 / 随机）已移除，功能由「库 Tab 重复点回顶」
   与下面两条下拉手势承担。详见「交互改版」一节。
   四个板块是横向 Pager，翻页与手指位移跟手：库 → 收藏 → 播放器 → 历史（反向同理）；
   库页已在最左，继续向右拖的溢出量把浮动圆控件组拉出来，组向左拖即可收回（都是跟手位移，不是固定时长动画）；
   拉起侧栏要求「向右位移 > 16dp 且明显大于纵向位移」，列表纵向滑动时的手指抖动不会误开；
   松手吸附门槛按方向区分：展开需过 40%，收起只需过 22%（≈57dp），不用一路滑到底。
   设置 / 改密是覆盖层，盖在 Pager 之上，此时不响应翻页；覆盖层自带不透明页面底色，
   不会透出下层的库页卡片或播放器模糊封面。**作品详情从 2026-11-25 起不再是整页界面，也没有顶栏**：
   详情只是盖在 Pager 之上的一层压暗 + 一块从屏幕下方滑上来的圆角弹出框（框顶距屏幕顶部 56dp，
   这段空隙透出被压暗的底层内容），关框靠下滑、点框外空白或系统返回键，没有返回按钮。
   历史页同样没有顶栏（2026-11-25 去掉），标题「播放历史」在列表首行。
   **覆盖层打开期间整层不识别侧栏手势**：
   设置页的滑块、收藏页的横向标签都是「按住横滑」，外层侧栏手势在 Initial pass 旁路观察、
   看不到子级消费，不排除就会出现「拖滑块把侧栏拉出来」。
   设置页分三块：**外观**（主题色 / 声优标签色 / 类型标签色 + 面板、控件、两类标签的不透明度，
   预设色板 + 自定义 `#RRGGBB`，改完即时生效并只存本机 DataStore，带「恢复默认外观」）、
   **手势**（退出随机推荐的按住时长，1–10 秒滑块，默认 3 秒，存 `app_interaction`）
   与**本机缓存**（已用大小、分类明细、二次确认清除）。三块都不读写服务端设置。
   播放页信息区顺序固定为 标题 → 当前文件名 → 社团 → 系列 → 声优 → 类型标签；会还原上次播放的文件、
   整条播放列表与秒数（本机记录，不自动开播，点播放才续上）。
   播放页底部「播放列表」支持**长按拖动排序**：长按后左侧图标变成列表图标，上下拖动即换位，松手生效。
5. 收藏（作品 / 社团 / 声优 / 标签）、随机播放、循环模式切换都会在底部 Tab 上方弹出 App 内轻提示
   （圆角胶囊，1s 后消失，连续触发只保留最新一条；不走系统 Toast）。收藏统一调用
   `FavoritesState.toggleWorkWithToast` / `toggleWithToast`，失败提示「操作失败，请检查网络」。
6. 作品详情不再是一个单独界面，也没有顶栏：压暗层之上是一块从屏幕下方滑上来的圆角弹出框
   （只盖内容区，不盖底部 Tab），没有返回按钮，关框靠**下滑**、点框外空白或系统返回键。
   框内列表没滚到顶部时下拉 = 正常浏览文件，
   到顶后继续下拉 = 关框（跟手位移，松手时拉过 1/4 框高就关，否则 200ms 弹回）。
   这个界面**左右滑动不绑定任何功能**，横滑只归「相关推荐」那一行自己滚动。
   图片查看器打开时返回键先关闭查看器。详情里点音视频文件「播放」会直接进全屏播放器
   （覆盖层自动收起），离开播放器回到库页。历史页同样没有顶栏（标题在列表首行）。
7. 图片查看器：顶栏左 ✕ 关闭、中间只显示当前图片文件大小、右边「当前第几张/同文件夹图片总数」+ ⋮，
   图片区**左右滑动**切换同目录内的图片：拖动时整屏跟手平移、相邻图从屏幕边缘补位，
   松手位移过屏宽 25% 或速度过 900px/s 就滑完翻页，否则弹簧回弹（首尾循环）；
   **双指捏合**放大（1×–4×，以两指质心为不动点）、放大后**单指拖动平移**（边界按 Fit 后的实际显示尺寸算，
   拖不出大片黑边）、**双击**在「自适应 ↔ 2.5× 放大」之间切换（以点击点为中心放大，再双击还原为自适应）；
   ⋮ 菜单目前只有「下载图片」，保存到系统相册 `Pictures/Yuzuki/`（Android 10+ 免权限，8/9 首次会申请存储权限）。
   查看器内不再放「设为封面 / 设为壁纸」，这两个入口仍在文件列表长按菜单里。

## 交互改版（2026-11-18）

**底部导航：回退为纯黑**（`ui/BottomTabs.kt`）

- 曾尝试 4 种亚克力毛玻璃实现（`Modifier.blur`、`GraphicsLayer.renderEffect`、1/8 降采样放大、
  「一次绘制录两份」），真机全部失败：取样层永远是空的，栏身只剩半透明底色。
  判定依据是本地解码的栏区像素统计 —— 栏区 / 栏上方的「1px 相邻差 ÷ 8px 相邻差」比值
  4.3 : 4.5，说明栏后是**未经任何缩放的锐利原图**，连马赛克都没有（`PixelCopy` 取真实屏幕像素）。
- 用户决定不要半透明效果，已回退：`Box` 叠加式布局改回 `Column`（内容 weight + 栏在下面），
  导航栏恢复纯黑 `#000000` + 顶部 1px 分割线，`LocalTabBarInset` 与各页让位内距全部撤掉，
  `ui/Acrylic.kt` / `ui/AcrylicSelfCheck.kt` 删除。

**侧栏取消，改为浮动圆控件**（`ui/SideDrawer.kt`，2026-11-18 已合并进 main，0.1.50）

- **不再有侧栏面板**：没有面板底色、没有右边框线、没有遮罩遮住内容，也没有顶部品牌区。
  在库页右滑超过门槛后，只是一组圆控件浮到内容之上（整体随手指水平位移从左浮入）。
- 排布：第 1 行 4 个筛选圆（**社团 / 系列 / 声优 / 标签**，去掉了「全部」二字）、
  第 2 行第 1 列 1 个收藏圆；底部 **账号** 圆（左）与 **设置** 圆（右），落在底部导航栏上方。
- **圆内只有图标、绝对居中**（圆内不放文字）：图标 `size(圆径 × 0.43) ≈ 24dp`。
  账号圆同理，只有首字母（22sp）居中。
- **尺寸对齐市场成熟做法**（Material 3 FAB 56dp / 24dp 图标 / 16dp 间距，
  AssistiveTouch、Telegram、Google Photos 的浮动圆菜单都是这套）：圆固定 **56dp**，
  不再按屏宽等分（早期版本 360 屏上会到 71dp，整行铺满、又大又散）；
  整组宽 `4×56 + 3×16 = 272dp` 居中，最窄的 320dp 屏两侧仍各留 24dp；圆上带 3dp 阴影浮起。
  筛选生效的圆描边换强调色、右上角显示已选数量（角标画在裁剪层之外，不会被圆裁掉）。
- **圆组位置不固定**：以「起手时手指按下的高度」为垂直中心（`RootScreen` 在起手判定成功那一帧
  记录 `down.position.y / 可用高度`，点右上角菜单按钮打开时沿用上一次的值）。
  `DrawerGeometry.groupTopPx` 把圆组钳制在上下留白之内，手指按在屏幕最上 / 最下沿也不会跑出屏幕。
- 手势位移换算基准为 `屏宽 × 0.8`（`DrawerGeometry.swipeDp`），与圆尺寸无关。
- 关闭方式：点圆外的空白（整层 `detectTapGestures`，圆自己的 clickable 会消费事件所以不会误关），
  或在控件层上向左拖（跟手位移回传）。
- **模糊遮罩**：控件浮出时下层内容整体高斯模糊 + 压暗 —— `RootScreen` 给内容 `Column` 加
  `Modifier.blur(14.dp × drawerFraction)`（半径跟手渐入），`SideDrawer` 手势层再叠一层
  `黑色 45% × fraction` 的压暗。模糊走 Compose 的 `Modifier.blur`（Android 12+ 为 GPU RenderEffect），
  **不涉及「栏内取样」那条失败路线**：它模糊的是整块内容层，不是去抓某一条栏后面的像素；
  API 31 以下 Compose 自动跳过模糊，只剩压暗层，不会崩也不会花屏。
- 这套数值规则抽成 `DrawerGeometry`（纯 Kotlin，不依赖 Compose 运行时），
  由 `DrawerGeometryTest` 钉住：滑入基准宽度、圆直径等分与上下限、圆组宽度、起手高度居中与越界钳制。

**信息流顶部下拉手势**（`ui/FeedPull.kt`）

- 只在**列表停在顶部**（首项 index=0 且 offset=0）时起手，其余时候完全放行。
- 交互是「**拖出提示条 → 松手才生效**」，不是拖到一半就切：
  - 拖动时信息流整体下移（`offset` 在布局阶段读，不触发重组），顶端露出一条空白区；
  - 空白区里居中显示「箭头 + 文案」：左半屏「松开以切换展示模式」，右半屏「松开以获取随机推荐」；
  - 露出高度 = 手指位移 × 0.7（封顶 132dp），超过 **64dp** 门槛时箭头弹簧翻转朝上、文字变强调色，
    此时松手才执行；没到门槛松手就 220ms 弹回去；
  - 文案透明度随露出高度淡入，高度/透明度都在布局 / 绘制阶段读状态，拖动时 60fps 不重组列表。
- 拉出提示条期间**消费**位移：信息流不再滚动，也不会出现系统的 overscroll 拉伸；
  手感上必须先「明显向下」（> 6dp 且 > 横向累计 × 1.4）才开始，中途横向占主导则放弃这次下拉。
- 左半屏松手：列表 → 卡片详情 → 封面模式 循环切换（等价于原悬浮控件的 M 键）；
  右半屏松手：未在随机 → 进入随机推荐；已在随机 → `LibraryState.randomEpoch++` 换一批
  （批次号参与列表条件指纹，条件没变也会重新请求）。生效时轻震一下 + AppToast 提示。
- **随机模式出口**：右半屏下拉越过 64dp 门槛后**按住不动**到设定时长即退出随机
  （`InteractionPrefs.exitRandomHoldSeconds`，默认 3 秒，设置页可在 1–10 秒间调；
  未读设置时兜底 `FeedPull.HOLD_MS`），回到当前搜索 / 筛选 / 排序对应的常规列表。
  悬停计时与「手指移动 / 抬起」赛跑，手指移动超过 4dp 就重新计时（不会边拖边读秒）；
  触发后本次手势不再执行「换一批」，提示条自动回落。已处于随机模式时右半屏文案变成
  「松开换一批 · 按住 N 秒退出随机」（N 取设置值），这是移动端唯一的退出入口。
- **悬停读秒**：开始计时后提示条逐秒显示「按住不动 · N 秒后退出随机」（按设置时长倒数到 0 退出），
  手势最多睡 1 秒就醒一次刷新读秒（`FeedPull.holdSecondsLeft` 向上取整）；只有整数秒变化才回调，
  只重组提示条自身，不带着信息流一起重组。计时被中断（手指移动 / 退出门槛 / 抬手）时立即清掉读秒。

**已选条件改为悬浮胶囊**（`ui/LibraryScreen.kt`，2026-11-20，分支 `feat/floating-filter-chips`，0.1.54）

- 筛选条件行**不再占 `Column` 里的一行**（原来坐在页面底色上，视觉上就是一条黑色横带），
  改放进信息流容器、`align(TopCenter)` 叠在内容之上：没有底色、没有分割线，只有一排胶囊。
- 排布用 `FlowRow`：**宽度装不下就换行**，不做横向滚动（横滑既有跟信息流下拉手势抢事件的风险，
  也会让末尾几条条件看不见）。超长条件文字可缩、省略号收尾，保证 × 始终露在外面。
- 它跟信息流一起受下拉位移影响（`.offset { IntOffset(0, pullReveal) }`），
  拉出提示条时一起下移，不会盖住顶端的「松开以…」提示。
- 列表 / 瀑布流顶部留位不再写死：`ActiveCondRow` 把 `onSizeChanged` 量到的**含内距整块高度**
  回传给 `LibraryScreen`（`condBarPx` → `feedTopPad`），单行时约 40dp（胶囊 24 + 上下各 8），
  换行变高时留位自动跟着涨。静止时首屏不被胶囊压住，一滚动卡片就从胶囊底下穿过 —— 悬浮感来自这里。
- **取消右侧「清除筛选」按钮**：单条胶囊点一下（含右侧 ×）即删除那一条，搜索词胶囊同规则，不再有全清入口。
- 胶囊底色由半透明白 `0x59FFFFFF` 改为**不透明** `WebColors.control(1f)` + 1dp 描边 + 3dp 阴影，
  文字 `WebColors.Text`、× `WebColors.Muted`：它现在浮在封面卡片上，半透明会让文字糊在花哨封面上。

**收藏改成底部 Tab + 圆组固定贴底**（2026-11-25，分支 `feat/favorites-tab`）

- **底部导航变 4 格**：库 / **收藏** / 播放器 / 历史。收藏图标是五角星，未选中画空心描边
  （与「未收藏」同形），选中填实并变紫；外接圆缩到 0.84，与相邻 Tab 的圆环视觉等大。
- 收藏是 **Pager 第 2 页**（`FAVORITES_PAGE = 1`，页面顺序 库 / 收藏 / 播放器 / 历史）：
  左右滑动与点 Tab 都能进出，选中态直接跟随 `pagerState.currentPage`；
  Pager 落位时同步路由（`nav.replace(Route.Favorites)`），从播放页滑到收藏也会先 `setFullscreen(false)`。
  RootScreen 的覆盖层只剩详情 / 设置 / 改密。
- **抽屉里的收藏圆已删除**（入口只留底部 Tab），圆组只剩 4 个筛选圆 + 账号 / 设置圆。
- **圆组固定贴底**：`SideDrawer` 改为 `Alignment.BottomCenter` + `bottomPad = TabBarHeight + 18dp`，
  不再记录起手高度、也不再算「以起手高度为中心」的顶边；`DrawerGeometry.groupTopPx` 与其断言一并删除，
  改为 `bottomPadDp` 由 `DrawerGeometryTest` 钉住。
- **收藏页重做**（`ui/CollectionsScreen.kt`）：
  - 四个切换胶囊（作品 / 标签 / 社团 / 声优）选中 = 主题色底 + onPrimary 字 + 无描边，
    未选中 = control 底 + 1dp 描边；旧版四颗长得一样，看不出当前 Tab。
  - 作品页复用库页 `WorkCard`（**固定详情卡片**，不跟随库页的列表 / 封面模式）：
    两列瀑布流、左右 20 / gap 16 / 顶 14 / 底 24、卡底与标签 / 声优配色全部与库页一致，
    封面左上角的收藏星照常可点。
  - 标签 / 社团 / 声优改为**与详情页同一套胶囊**（`ui/FavableTag.kt` 抽出共用）：
    点胶囊主体按该条件筛选回库页，点右端 22dp 区域取消收藏；标签用类型标签色、
    声优用声优标签色、社团没有独立配色设置，用主题色底 + onPrimary 字。
  - 列表按 `FavoritesState` 实时过滤：取消收藏后该项立即从列表消失；全局收藏态为空时不过滤，
    避免首帧把已拿到的列表过滤成空。

**去掉多余顶栏 + 版本号 0.2.x**（2026-11-25，0.2.0）

- **历史页顶栏删除**：`HistoryScreen` 不再渲染 `WebTopBar`、也不再收 `onOpenDrawer`；
  标题「播放历史」保持在列表首行。抽屉入口只在库页顶栏，历史页原本也拉不出圆组。
- **详情弹出框不再自带顶栏**：`WorkDetailOverlay` = 压暗层 + `DetailSheet`，删掉那根只有菜单按钮的
  `WebTopBar` 与 `onOpenDrawer` 参数。之前从播放页 / 收藏 / 历史点作品会看到一根「不属于当前页面」的顶栏，
  从库页进详情也会把库页的搜索顶栏换成一根空顶栏；现在详情在哪个页面打开都只是压暗 + 弹出框。
- 版本号改为 `0.<MINOR>.<PATCH>`：`MINOR` 与 `PATCH_BASE` 写在 `version.properties`，
  `PATCH = BUILD − PATCH_BASE`；本次 0.2.0（`BUILD=79 / PATCH_BASE=79`），之后每次 release 依次 0.2.1、0.2.2 …
  `versionCode` 仍是 `1000 + BUILD`，保持单调递增可覆盖安装。

## 分阶段状态

| 阶段 | 内容 | 状态 |
|---|---|---|
| N0 | 清除 WebView 层、原生导航骨架、GlassSurface 设计系统 | ✅ |
| N1 | 全量 REST 客户端、DTO、Repository、登录 / 改密 / 401 | ✅ |
| N2 | 媒体库（搜索 / 排序 / 筛选 / 分页 / 图片模式）、抽屉、外观（悬浮控件 MobileDock 已于 2026-11-18 移除；搜索条件 2026-11-18 起不读写 `/api/prefs`，2026-11-24 起改为记在本机 DataStore） | ✅ |
| N3 | 作品详情、文件树、图片查看器、设封面 / 壁纸、相关推荐 | ✅ |
| N4 | 原生播放队列、迷你条 + 全屏播放器、PlayReporter 时长上报 | ✅ |
| N5 | 锁屏 / 通知 / 线控、收藏 4 Tab、历史列表 / 卡片 | ✅ |
| N6 | 设置页（本机外观：主题色 / 标签色 / 不透明度；本机缓存用量与清除；web 式服务端设置项已移除） | ✅ |
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

**本次增量验证（2026-11-17，卡片 / 列表补播放次数 · 分支 `feat/card-play-count`）**：

- 背景：`Work.playCount`（`play_count`）一直被解析但卡片从不展示，只有收藏管理页的行用到
  （`CollectionsScreen` 的「播放 N 次」），首页 / 详情 / 列表卡片只显示 `salesCount`。
- 改法（仅 `ui/WorkCards.kt` 两处，不动数据层、不新增字段）：
  - `WorkCard` 详情模式信息区：在「销量」下方新增一行 `CardInfoRow("播放", "%,d".format(playCount))`，
    与销量同字号（13sp）/同色（label muted、value 正文色）。
  - `WorkRow` 列表行：在「社团 · 销量」之后同行追加 `· N`（12sp muted），
    前面两项都没显示时省掉分隔点，避免行首出现孤零零的「·」。
- 取舍：`playCount > 0` 才渲染，0 次不占位（否则整屏「播放 0」噪声且凭空撑高瀑布流卡片）；
  图片模式（只有封面）与播放历史卡片不改。
- `assembleDebug` / `assembleRelease` 通过；`testDebugUnitTest` **93 例全过、0 失败 0 错误**。未截图验证。
- Release `0.1.27` 已发布共享：`yuzuki-0.1.27-1117-1923.apk` + `yuzuki-latest.apk`，
  sha256 与本地 `app-release.apk` 三方一致 `3ddef123…878e71`；本次 `adb devices` 无设备，未装机，观感由用户自测。

**本次增量验证（2026-11-17，升降序图标重绘）**：

- 现象：首页排序胶囊右侧的升降序图标「很奇怪」——不像箭头加横线。
- 根因（`ui/UiIcons.kt` `SortOrderIcon`）：箭头只画了左撇，右半边误写成**水平线段**
  （`Offset(10.5f, 19f) → (7f, 19f)`，`y` 没跟着改成 15.5；升序分支同理 8.5），
  于是箭头呈「一撇一横」的畸形，右侧三条线又紧贴竖杆，整体读不出「排序」语义。
- 重画：箭头改为**左右对称的两撇**（半宽 3.2 / 高 3.6，竖杆 x=6.6，desc 向下、asc 向上）；
  三条横线统一左对齐 x=12.6，长度 8.4 / 5.6 / 2.8，desc 上长下短、asc 上短下长；
  线宽 1.7→**1.9**，绘制尺寸 `LibraryScreen` 16→**18dp**（26dp 点击区内），小尺寸下线条才分得开。
- `assembleDebug` / `assembleRelease` 通过。未截图验证，观感由用户自测。
- Release `0.1.28` 已发布共享 `yuzuki-0.1.28-1117-1932.apk` + `yuzuki-latest.apk`，
  三方 sha256 一致 `137a5170…961b20`；已装 `192.168.5.16:35225`（`versionCode=1028 / versionName=0.1.28`）。

**本次增量验证（2026-11-18，去悬浮控件 + 顶部下拉手势 + 亚克力导航栏 · 分支 `feat/pull-gesture-acrylic-bar`）**：

- 悬浮控件：删除 `ui/MobileDock.kt`（237 行）与 `DockState`，库页 / 历史页 / 详情页里
  `LaunchedEffect(DockState.scrollTopTicket)` 三处一并移除。回顶改由「重复点库 Tab」承担
  （`ScrollRestore.shouldScrollTopOnLibraryTab` 未改）。**侧栏抽屉（SideDrawer / DrawerGate）保持原样不动**。
- 顶部下拉（0.1.29 初版）：新增 `ui/FeedPull.kt`；`LibraryScreen` 的列表容器与瀑布流容器各挂一份
  `Modifier.feedPullAtTop(...)`，`atTop` 按当前形态查 `listState` / `gridState` 的首项与偏移。
  左半屏走 `LibraryState.cycleView()`，右半屏走新增的 `LibraryState.pullRandom()`
  （`randomMode = true` + `randomEpoch++`，批次号已加入指纹）。
- 底部导航：`RootScreen` 改为叠加式 Box 布局，`MainNavigationBar` 压在内容之上；
  新增 `ui/Acrylic.kt`（`rememberAcrylicBackdrop` / `acrylicSource` / `AcrylicBarSurface`）
  与 `LocalTabBarInset`，五处页面底部各让出 65dp。
- `compileDebugKotlin` / `assembleRelease` 通过；`testDebugUnitTest` **98 例全过、0 失败 0 错误**
  （新增 `FeedPullTest` 5 例：阻尼露出高度与封顶、松手门槛、横滑取消、左右半屏落点、
  右下拉进入随机并累加批次号）。
- 静态断言：`MobileDock` / `DockState` / `dockTop` 全库零命中；`acrylicSource` 只在 `RootScreen`
  出现一次（内容层），`MainNavigationBar` 是唯一 `backdrop` 消费方。
- Release `0.1.29` 已发布共享：`yuzuki-0.1.29-1118-1924.apk` + `yuzuki-latest.apk`，
  与本地 `app-release.apk` 三方 sha256 一致 `335dde92…2d776a`；`versionCode=1029`。
- 本次 `adb devices` 无设备（mDNS 只闪现过一次随即消失，历史地址 `192.168.5.16:35225` 已拒绝连接），
  **未装机**；真机观感（毛玻璃强度、下拉手感）由用户自测。

**本次增量验证（2026-11-18 二改，下拉改「松手生效 + 提示条」、导航栏模糊改为显式层 · Release `0.1.30`）**：

- 真机反馈两条：①下拉「拖到一半直接切换」太生硬，要「顶端露出空白提示区 + 箭头/文案 + 松手才生效」；
  ②导航栏「有透明但没有模糊」。第 ② 条的修法：不再用 `Modifier.blur` + `drawBehind` 的组合
  （顺序 / 父层不确定性太大），改成**给小层自己挂 `BlurEffect`**：
  `strip.renderEffect = BlurEffect(26dp, TileMode.Clamp)`（`SideEffect` 里设一次），
  `record` 时顶端多录一个模糊半径（`layerH = 栏高 + 半径`）保证模糊有源可采，`clipRect` 后 `drawLayer`。
  栏身深色层从 65% 降到 **55%**（`TabBarTint = 0x8C0A0A12`），磨砂才看得出来。
- 下拉改成三段式：拖动 → `feedPullGesture` 在 Initial pass **消费**位移（信息流不再滚动、也没有系统
  overscroll 拉伸），`pullReveal = 位移 × 0.7`（封顶 132dp）驱动信息流整体下移、顶端露出
  `FeedPullHint`（箭头 + 「松开以切换展示模式 / 松开以获取随机推荐」）；越过 **64dp** 箭头弹簧翻转朝上
  （`animateFloatAsState` + `spring`），松手才执行；未到门槛 220ms `tween` 弹回。
- 拖动期间不重组列表：露出高度在**布局阶段**（`Modifier.layout`）读、文字透明度在**绘制阶段**
  （`graphicsLayer { alpha }`）读，只有「是否越过门槛」（`derivedStateOf`）与落点半边会触发重组。
- `assembleRelease` 通过；`testDebugUnitTest` **98 例全过、0 失败 0 错误**。
- Release `0.1.30` 已发布共享：`yuzuki-0.1.30-1118-1938.apk` + `yuzuki-latest.apk`，
  与本地 `app-release.apk` 三方 sha256 一致 `e79f1cf8…0ec93b4`；`versionCode=1030`。
  已装到 `192.168.5.16:37557`（`versionName=0.1.30`），测试由用户自测。

**本次增量验证（2026-11-18 三改，放弃亚克力、导航栏回退纯黑 · Release `0.1.37`）**：

- 亚克力毛玻璃一共试了 4 版，全部失败，最终确认**取不到内容层像素**：
  1. `Modifier.blur` 包住「已录制的层」；
  2. 栏身自建层 + `renderEffect = BlurEffect`；
  3. 降采样 1/8 放大（不依赖任何平台特性，理论上必然出现马赛克）；
  4. 一次绘制录两份（正常画面直接画、栏身条第二次 `drawContent()`）。
- 判定手段（不截图）：`PixelCopy` 取真实屏幕像素 → 导出栏区 / 栏上方两张 PNG →
  本地解码统计「1px 相邻差 / 8px 相邻差」：栏区 2.24 / 9.69，栏上方 5.79 / 26.07，
  比值 4.3 与 4.5 基本相同 —— 说明栏后是**未经缩放的锐利原图**，连 8 倍马赛克都没有。
  `toImageBitmap()` 读回取样层是全 0（该方法走软件画布回放 `RenderNode`，不可作为判据）。
- 用户决定不要半透明，直接回退：
  - 删 `ui/Acrylic.kt` / `ui/AcrylicSelfCheck.kt`；
  - `RootScreen` 从叠加式 `Box`（内容铺满 + 栏压在上面）改回 `Column`（内容 weight + 栏在下面），
    `LocalTabBarInset` 及其在库页 / 历史页 / 播放器 / 详情 / 收藏 / 设置里的 6 处让位内距全部撤掉；
  - `ui/BottomTabs.kt` 恢复纯黑 `#000000` + 顶部 1px 分割线（`TabBarBackground` / `TabBarDivider`）；
  - 手机侧临时诊断文件（`acrylic.log` / `bar.png` / `above.png`）已从应用目录删除。
- 保留：信息流顶部下拉手势（`ui/FeedPull.kt`，露出提示条 + 松手生效）、悬浮控件已移除。
- `assembleRelease` 通过；`testDebugUnitTest` **98 例全过、0 失败 0 错误**。未截图验证。
- Release `0.1.37` 已发布共享 `yuzuki-0.1.37-1118-2020.apk` + `yuzuki-latest.apk`，
  已装到 `192.168.5.16:37557`（`versionName=0.1.37`）。

**本次增量验证（2026-11-18 四改，库页搜索条件不再记忆 · Release `0.1.38`）**：

- 需求：App 端不记忆自己上次的搜索条件，也不带出 / 不覆盖网页端保存的搜索条件。
- 根因：`/api/prefs` 是 App 与网页端**共用的一份**「搜索关键词 + 筛选 + 排序」。App 启动会恢复它
  （于是把网页上次的搜索词带进搜索框），改筛选 / 排序时又 `PUT` 覆盖它 —— 两个方向互相污染。
- 改动（分支 `feat/no-search-prefs`，已于 2026-11-18 快进合并进 `main`）：
  - `ui/AppStates.kt`：`LibraryState` 删除 `restore()` / `persist()` / `persistAsync()`，
    `addCond` / `removeCond` / `clearConds` / `updateSort` / `toggleOrder` / `home` 去掉落盘调用
    → 关键词与条件只存内存，冷启动恒为「空关键词 + 无筛选 + added/desc」；
  - `ui/LibraryScreen.kt`：搜索框 400ms 防抖后只 `updateQuery()`，不再 `persist()`；
  - `ui/AppViewModel.kt`：`enterMain` 只等 `settings.load()`，不再 `prefs.load()` / `restore()`；
  - `ui/RootScreen.kt`：首帧只剩 `FavoritesState.refresh()`；
  - `data/AppContainer.kt` 删除 `PrefsRepository`；`data/YuzukiApi.kt` 删除 `prefs()` / `savePrefs()`；
    `Models.prefs()` 与 `LibraryPrefs` 保留 —— 登录与 `/api/me` 返回体仍带 `prefs` 字段，协议不动。
- 后端 `D:\node\yuzuki` 零改动，网页端自己的记忆完全不受影响（App 只是不再读、不再写）。
- 新增 `ui/LibraryStateTest.kt` 2 例：冷启动默认值；改条件 / 改排序 / `home()` 全程不触达持久层
  （单测里 `AppContainer` 未初始化，若残留 `persist` 会直接抛 lateinit 异常）。
- `assembleRelease` 通过；`testDebugUnitTest` **100 例全过、0 失败 0 错误**（原 98 + 新增 2）。
- Release `0.1.38` 已发布共享：`yuzuki-0.1.38-1118-2123.apk` + `yuzuki-latest.apk`，
  与本地 `app-release.apk` 三方 sha256 一致 `b966fb52…06c910a89a`；已装到 `192.168.5.16:37557`
  （`versionCode=1038`、`versionName=0.1.38`），未启动、测试由用户自测。

**本次增量验证（2026-11-18 五改，App 外观改为本机设置 · 分支 `feat/local-appearance`，已于 2026-11-18 快进合并进 `main`）**：

- 需求：①App 界面颜色不再受网页版设置影响；②设置页新增 App 外观设置，数据存本地、不再从 yuzuki 同步。
- 根因：`MainActivity` 直接把 `AppContainer.settings`（`GET /api/settings`）映射成 `Appearance`，
  主题色 / 声优色 / 标签色 / 三种透明度全部来自服务端，网页端一改 App 立刻跟着变。
- 改动：
  - 新增 `data/AppearancePrefs.kt`（纯 Kotlin）：`navColor / actorColor / tagColor /
    panelOpacity / controlOpacity / actorTagOpacity / genreTagOpacity` + `sanitized()`
    （色值统一 `#RRGGBB`、非法回退、透明度夹 0–100）+ 预设色板 + `isValidHex`；
  - 新增 `data/AppearanceStore.kt`：独立 DataStore（`app_appearance`），`StateFlow` + `load/save/reset`，
    退出登录不清除（本机偏好而非账号数据）；
  - `ui/theme/Glass.kt`：`Appearance.from(AppSettings)` → `Appearance.from(AppearancePrefs)`，
    删除 `cardSize / cardWidth / animationMs / wallpaper*` 字段；文件列表透明度与面板共用一项；
  - `YuzukiApp.onCreate` 同步读一次本地外观（避免冷启动先闪默认紫），`MainActivity` 只订阅该 Flow；
  - `SettingsScreen` 新增「外观」卡片：3 组颜色（6 色预设 + 「自定义颜色…」HEX 弹窗，非法值禁用确定）、
    4 个不透明度滑块（拖动只改本地值、松手才落盘）、「恢复默认外观」。
- 服务端设置仍保留给「每页数量 / 快进秒数 / 相关推荐开关」这类非配色项，`/api/settings` 写入点仍在 App 侧无调用。
- `assembleRelease` 通过；`testDebugUnitTest` **109 例全过、0 失败 0 错误**
  （新增 `AppearancePrefsTest` 9 例：默认值、hex 规范化、非法回退、`isValidHex`、透明度夹紧、
  坏存储值回退、暗色只提亮强调色、面板与文件列表同透明度、预设色板合法性）。
- 静态断言：`Appearance.from(AppSettings)` 零命中；`ui/` 层不再出现 `themeColorNav / themeColorActor /
  themeColorTag`；`AppContainer.appearance` 只在 `YuzukiApp` / `MainActivity` / `SettingsScreen` 出现。
- Release `0.1.39` 已发布共享：`yuzuki-0.1.39-1118-2157.apk` + `yuzuki-latest.apk`；
  已装到 `192.168.5.16:37557`（`versionCode=1039`、`versionName=0.1.39`），未启动、测试由用户自测。


**本次增量验证（2026-11-18 六改，随机模式出口：右半屏下拉悬停 3 秒退出 · 分支 `feat/pull-exit-random`，已于 2026-11-18 快进合并进 `main`）**：

- 需求：移动端要有一个退出随机模式的方式 —— 右半屏下拉、悬停 3 秒退出随机。
- 改动：
  - `ui/FeedPull.kt`：新增 `HOLD_MS = 3000`、`holdEligible(side, dragging, armed, enabled)`
    （只认右半屏 + 已过门槛 + 当前确实在随机模式）与 `holdRemainingMs(elapsed)`（已满返回 0）；
    `feedPullGesture` 增加 `holdEnabled / holdMs / onHold` 三个参数，循环里用
    `withTimeoutOrNull(剩余时间) { awaitPointerEvent() }` 让「悬停计时」与「手指移动 / 抬起」赛跑；
    手指单次移动 > 4dp 就重置计时；触发后本次手势**不执行** `onRelease`（不会顺带换一批），
    但位移继续被吃掉直到抬手。
  - `ui/FeedPull.kt`：`FeedPullHint` 增加 `exitRandomHint`，随机模式下右半屏文案改为
    「松开换一批 · 按住 3 秒退出随机」，让出口可发现。
  - `ui/AppStates.kt`：新增 `LibraryState.exitRandom()`（不在随机时返回 false，避免重复提示）；
    `randomMode` 参与条件指纹，退出后列表自动回到当前搜索 / 筛选 / 排序的常规第一页。
  - `ui/LibraryScreen.kt`：接线 `holdEnabled = { LibraryState.randomMode }`，
    `onHold` 退出 + 轻震 + AppToast「已退出随机推荐」+ 提示条回落。
- `assembleRelease` 通过；`testDebugUnitTest` **112 例全过、0 失败 0 错误**
  （新增 3 例：悬停资格判定、3 秒剩余时间边界、`exitRandom` 只生效一次）。
- Release `0.1.40` 已发布共享（`yuzuki-0.1.40-1118-2206.apk` + `yuzuki-latest.apk`，三方 sha256 一致
  `f85368cb…b22161e`）；
  已装到 `192.168.5.16:37557`（`versionCode=1040`、`versionName=0.1.40`）。首次 `install -r` 返回
  `INSTALL_FAILED_ABORTED: User rejected permissions`（设备端确认弹窗），原样重试后 Success。

**本次增量验证（2026-11-18 七改，悬停读秒 · 同上分支 · Release `0.1.41`）**：

- 反馈：悬停时没有任何读秒，不知道还要按多久。
- 改动：
  - `ui/FeedPull.kt`：`holdRemainingMs()` → `holdSecondsLeft()`（向上取整，0 = 到点）；
    `feedPullGesture` 新增 `onHoldTick(remainingSec)`，计时期间最多睡 1 秒就醒一次，
    整数秒变化时才回调（3 → 2 → 1 → 触发），计时中断（移动 > 4dp / 掉出门槛 / 抬手）回调 0。
  - `ui/FeedPull.kt`：`FeedPullHint` 新增 `holdRemaining: () -> Int`，读秒中显示
    「按住不动 · N 秒后退出随机」；lambda 传参让每秒只重组提示条自身。
  - `ui/LibraryScreen.kt`：`holdSec`（`mutableIntStateOf`）接线 `onHoldTick` / `holdRemaining`。
- `assembleRelease` 通过；`testDebugUnitTest` **112 例全过、0 失败 0 错误**
  （原「悬停满 3 秒」用例改写为「悬停读秒按整秒递减到 0 触发」，覆盖 0 / 999 / 1000 / 2999 / 3000ms 边界）。
- Release `0.1.41` 已发布共享（`yuzuki-0.1.41-1118-2211.apk` + `yuzuki-latest.apk`，三方 sha256 一致
  `f2d3fc1d…2203c47`）；已装到 `192.168.5.16:37557`（`versionCode=1041`、`versionName=0.1.41`），未启动。

**本次增量验证（2026-11-18 八改，退出随机的按住时长改为可配置 · 同上分支 · Release `0.1.42`）**：

- 需求：退出随机推荐的这个时长，要能在设置里配。
- 改动：
  - 新增 `data/InteractionPrefs.kt`（纯 Kotlin：默认 3 秒、范围 1–10 秒、`holdMillis()` 秒转毫秒）
    与 `data/InteractionStore.kt`（DataStore `app_interaction`，`StateFlow + load/save/reset`）；
  - `AppContainer` 暴露 `interaction`，`YuzukiApp.onCreate` 与外观一起同步加载（冷启动即生效）；
  - `SettingsScreen` 新增「手势」卡片：1–10 秒滑块（松手才落盘）+ 说明文案；
  - `LibraryScreen` 订阅 `AppContainer.interaction`，把 `holdMs` 传给 `feedPullGesture`
    （`pointerInput` 以 `holdMs` 为 key，改完设置不用重启页面就生效），
    提示条文案的总秒数同步取设置值（`FeedPullHint(holdTotalSec = …)`）。
- `assembleRelease` 通过；`testDebugUnitTest` **115 例全过、0 失败 0 错误**
  （新增 `InteractionPrefsTest` 3 例：默认值与 `FeedPull.HOLD_MS` 对齐、范围夹紧、秒转毫秒夹紧）。
- Release `0.1.42` 已发布共享（`yuzuki-0.1.42-1118-2217.apk` + `yuzuki-latest.apk`，sha256 一致
  `2a94ae22…a3f20d`）；已装到 `192.168.5.16:37557`（`versionCode=1042`、`versionName=0.1.42`），未启动。

**本次增量验证（2026-11-18 九改，覆盖层不识别侧栏手势 · 同上分支 · Release `0.1.43`）**：

- 反馈：设置页拖不透明度滑块时会被识别成右滑，直接把侧栏抽屉拉出来。
- 根因：`RootScreen` 的侧栏手势用 `PointerEventPass.Initial`「旁路观察」右拖位移，刻意不消费事件，
  因此看不到子级是否消费；`Slider` 正常消费了横滑，但外层仍然照常累计 `sumX` 并过门槛。
- 改动：
  - `ui/DrawerGate.kt`：`DrawerDrag.canStart()` 增加 `overlayOpen` 参数（默认 false），
    覆盖层打开时一律不认侧栏手势；
  - `ui/RootScreen.kt`：`pointerInput` 增加 `overlayRoute` 作为 key（进出覆盖层即重启手势协程，
    避免闭包里读到旧的 `overlayRoute`），按下即判定 `overlayRoute != null` 直接放弃本轮。
- 影响面：设置（滑块）、收藏（横向标签行）、详情（相关推荐已有 `claimHorizontalGesture` 兜底）、
  改密页全部不再触发侧栏；库页 / 历史页 / 播放器的侧栏手势行为不变。
- `assembleRelease` 通过；`testDebugUnitTest` **116 例全过、0 失败 0 错误**
  （新增「覆盖层打开时不识别侧栏手势」，并在原侧栏判定用例里补了 `overlayOpen` 分支）。
- Release `0.1.43` 已发布共享（`yuzuki-0.1.43-1118-2226.apk` + `yuzuki-latest.apk`，sha256 一致
  `7c359250…09d03d`）；已装到 `192.168.5.16:37557`（`versionCode=1043`、`versionName=0.1.43`），未启动。

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

**本次增量验证（2026-11-19，播放队列按目录收口）**：

- 修复：播放列表原先取「整个作品文件夹」的全部可播放文件，改为只取**被点文件所在目录**的同层文件
  （`QueueMath.sameDir` + `MediaFile.dirOf`），跨 Track / 特典 / SE 子目录不再连播；恢复快照同规则收敛。
- `assembleDebug` / `testDebugUnitTest` 通过，**127 例全过、0 失败 0 错误**
  （新增 `QueueMathTest` 3 例：按目录收敛 / 根目录同组 / 保持入库顺序跳过目录项；
  `PlaybackSnapshotTest` 2 例：旧跨目录队列收敛 / 独占目录只剩一首）。
- 静态断言：`dirOf` 只在 `MediaFile` 定义一次（`WorkDetailScreen` 的私有扩展已删），
  `PlaybackController.play` 内 `QueueMath.sameDir` 为唯一队列收敛点。

**本次增量验证（2026-11-20，卡片 RJ 号胶囊 + 全年龄标签配色 · 分支 `feat/rj-badge-allages`）**：

- 参考 yuzuki 前端（`WorkCard.vue` / `FavoriteStar.vue` / `.tag-custom`）复刻两个纯前端功能，数据层与后端零改动。
- 作品网格卡（`WorkCard`）封面左上角：原 30dp 圆形收藏钮改为 **RJ 号 + 收藏星胶囊**
  （黑底 `#000000`、RJ 号 11sp 白字单行省略 + 后置 14dp 白星）。胶囊彻底贴住卡片左上角 (0,0)，
  圆角取卡片圆角 **10dp**，左上圆弧与卡片边缘同心贴合。点星切换收藏（子 `clickable` 消费事件），
  点 RJ 号冒泡走卡片 `onOpen`；图片模式卡 / 播放历史卡共用该封面区，自动覆盖。
- 列表模式（`WorkRow`，64dp 小封面）不挂胶囊，改为标题前缀 **`[RJ号]标题`**。
- 全年龄（内置 `custom` 标签）视觉对齐 web `.tag-custom`：深绿底 `#1D3A33` + 薄荷绿字 `#8EE0C0` + 深绿描边 `#2A5A4D`，
  应用到卡片封面标签（`CoverTagChip`）、详情页标签（`FavableTag`）、播放页标签（`FavChip`）三处；
  颜色集中在 `WebColors.CustomTagBg/Fg/Border`，`CUSTOM_TAG` 常量为同包共用。
- 验证：`:app:compileDebugKotlin`、`:app:testDebugUnitTest` 通过；静态断言上述三处标签按 `category == "custom"` 取自定义配色。
  真机观感由用户自测（不截图）。

**本次增量验证（2026-11-20，图片查看器支持捏合缩放 / 双击缩放 · 分支 `feat/viewer-zoom`，0.1.56）**：

- 需求：照片查看器要能「双指捏合张开缩小放大」与「双击照片放大 / 还原」。
- 新增纯逻辑 `ViewerZoom`（`ui/NavLogic.kt`，JVM 可测）：倍率夹紧（1×–4×，NaN 回落到 1×）、
  `isZoomed` 阈值（1.01，捏合停在 1.005 不挡翻页）、`doubleTapTarget`（自适应 ↔ 2.5×）、
  **`panKeeping`**（让屏幕上某点在缩放前后停在原地的平移量，公式 `panNew = p − (p − panOld) × scaleNew/scaleOld`，
  捏合与双击共用）、`panLimit` / `clampPan`（可平移边界）、`fittedSize`（ContentScale.Fit 后的实际显示尺寸）。
- 手势分工写在 `WorkDetailScreen.kt` 的 `viewerZoomGesture`（**Initial pass 旁路观察 → 需要时接管**）：
  - 未放大 + 单指：完全不消费，翻页仍由原 `draggable` 负责，双击仍由 `detectTapGestures` 负责；
  - 已放大 + 单指：平移；先攒 touchSlop，过门槛才消费 —— 放大状态下双击还原不会被这层提前吃掉；
  - 双指（任何状态）：以两指质心为不动点捏合缩放，质心移动即双指平移，并且**接管第一帧就把正在翻页的位移收回**
    （`offsetX.snapTo(0f)` + `incoming = null`），松手不会误翻页（`draggable` 的 onDelta / onDragStopped 都看
    `pinching` 与 `isZoomed` 双重条件直接跳过）。
- 渲染：查看器主图 `graphicsLayer` 里叠 `translationX = offsetX + panX / translationY = panY / scaleX = scaleY = zoomScale`；
  双击与松手复位走 220ms 补间（`animate`），双指接管时先 cancel 掉补间，避免和手指抢帧。
- 边界依据真实图片尺寸：`RemoteImage` 新增可选回调 `onBitmapSize`（默认 null，其它调用点零影响），
  解码完成后把原图宽高回传，查看器按 `fittedSize` 算出 Fit 后的显示尺寸再算可平移上限 —— 竖图放大后
  不会被拖出大片黑边。
- `:app:compileDebugKotlin` / `:app:testDebugUnitTest` 通过，**135 例全过、0 失败 0 错误**
  （新增 `ViewerZoomTest` 8 例：倍率夹紧含 NaN / Infinity、isZoomed 阈值、双击目标、点击点不动点平移与还原、
  中心缩放零平移、Fit 后尺寸的边界、边界夹紧、Fit 尺寸按短边对齐与非法尺寸退化）。
- Release `0.1.56` 已发布共享：`yuzuki-0.1.56-1120-1719.apk` + `yuzuki-latest.apk`，
  与本地 `app-release.apk` 三方 sha256 一致 `d27749e1…9c79bd3`；已装到 `192.168.5.16:42847`
  （`versionCode=1056`、`versionName=0.1.56`），未启动、测试由用户自测。

**本次增量验证（2026-11-20，图片模式移除热度三角 · 0.1.57）**：

- 用户反馈「图片模式里右上角的热度标签是个三角形，看着很奇怪」，要求 App 暂时不展示热度。
- 改法：删除 `ui/WorkCards.kt` 里 `if (imageMode)` 分支中按 `GlassSpec.flame(salesCount)` 拼的
  `"▲".repeat(count)` 文本（灰 / 黄 / 红 / 彩虹四档配色随之不再渲染），并移除随之失效的 `GlassSpec` 导入。
  热度分档函数本身（`ui/theme/Glass.kt` 的 `GlassSpec.flame` 与 `GlassSpecTest` 9 例）保留不动，
  只是 App 侧暂无调用点，后续要恢复只需加回这一段。库页 / 收藏 / 历史 / 详情相关推荐共用 `WorkCard`，一处删除即全部生效。
- 验证：`:app:testDebugUnitTest` + `:app:assembleRelease` 通过（**135 例全过**），未截图。
- Release `0.1.57` 已发布共享：`yuzuki-0.1.57-1120-1810.apk` + `yuzuki-latest.apk`，
  与本地 `app-release.apk` 三方 sha256 一致 `c22a76df…ff1748e7`；
  已装到 `192.168.5.16:42847`（`versionCode=1057`、`versionName=0.1.57`），未启动、测试由用户自测。

**本次增量验证（2026-11-20，播放列表长按拖动排序 · 分支 `feat/playlist-reorder`，0.1.58）**：

- 需求：播放页底部弹出的播放列表要能长按拖动、上下调整顺序。
- 交互（`ui/PlayerUI.kt`）：长按行内任意位置（`detectDragGesturesAfterLongPress`，约 500ms）→ 该行左侧图标
  由「♪ / ▶」换成**列表图标**（复用 `PlaylistIcon`、强调色）+ 轻震；被拖行 `zIndex` 抬到最上层、
  拖动时补不透明底、`graphicsLayer.translationY` 跟手；中心越过相邻行就**实时换位**并补回位移差
  （手指下面的行不会跳）；松手一次提交，手势被打断则按播放器里的真实顺序还原。
- `combinedClickable(onClick = 播放并关闭, onLongClick = {})` 的 `onLongClick` 只用来吃掉这次点击 ——
  没有它就退化成 `clickable`，长按后松手会被当成点击直接开播。拖拽检测器挂在同一行、比 clickable 更内层，
  起拖后消费事件，点击自然取消。
- 顺序必须两处一起改：新增 `PlaybackController.reorderQueue()` —— 先按新顺序写本机 `state.queue`
  （`queueIndex` 跟着当前曲目走），再用 `QueueMath.movePlan()` 算出 `(from, to)` 序列逐项 `moveMediaItem`。
  **不重建队列**：播放不中断、进度不丢、不多报一次播放记录。恢复态（`pendingStart`，队列还没下发给播放器）
  只改 UI，点播放时按新顺序下发；排序完 `maybeSave(force = true)` 落盘，冷启动恢复看到的也是排好的顺序。
  拖动期间只在本地镜像列表里换位（`mutableStateListOf`），松手才提交，不会每越过一行就打一次播放器。
- 「打开列表 / 切歌自动滚到当前曲目」的 `LaunchedEffect` 键由 `queueIndex + queue.size` 改为**当前文件 id**，
  否则排完序下标变化会立刻把列表拽回当前曲目。
- 验证：`compileDebugKotlin` / `testDebugUnitTest` 通过，**138 例全过、0 失败 0 错误**
  （`QueueMathTest` 新增 3 例：手动排序搬位与越界 / 原地不动、`movePlan` 逐项重放后与目标顺序一致且步数不超过队列长度、
  顺序没变或元素集合不同时不下发搬位）。未截图验证，真机手感由用户自测。
- Release `0.1.58` 已发布共享：`yuzuki-0.1.58-1120-1826.apk` + `yuzuki-latest.apk`，
  与本地 `app-release.apk` 三方 sha256 一致 `0b72ce20…9531a`；已装到 `192.168.5.16:42847`
  （`versionCode=1058`、`versionName=0.1.58`），未启动、测试由用户自测。

**本次修复（2026-11-20，长列表拖不到上面 · 0.1.59）**：

- 用户实测：列表很长时往上拖，拖到顶部「播放列表」那一栏，被拖的行直接弹回原位。
- 根因两条：
  1. **没有边缘自动滚动**，被拖行只能一路移出 viewport，而换位只发生在「可见行」之间，
     所以永远爬不到上面去；
  2. `graphicsLayer.translationY` 不参与布局，行被拖出 viewport 后会被 LazyColumn 裁掉甚至回收，
     行一消失手势就断了，看起来就是「刚拖到顶上就弹回原位」。
- 修复（`ui/PlayerUI.kt`）：
  - **位移夹在列表可视区内**（`viewportStartOffset … viewportEndOffset - rowHeight`），行贴边就停住不再外移；
  - **边缘自动滚动**：新增 `ui/DragAutoScroll.step(center, viewportStart, viewportEnd, edge, maxStep)` 纯函数，
    被拖行中心进入上下 56dp 感应区后按越界深度线性加速（上限 9dp/帧），
    `LaunchedEffect(dragId)` 每帧 `withFrameNanos` + `LazyListState.scrollBy`，
    滚完把 `dragOffset` 补回滚动量（行继续跟手、贴边不动），再重算一次落点 ——
    所以「按住不动只看列表滚」时也会继续换位，不会让上面的行从被拖行上方越过；
  - 手势被打断（`onDragCancel`）改为**提交已排好的顺序**而不是还原，避免「白拖一场」的回弹。
- 验证：`compileDebugKotlin` / `testDebugUnitTest` 通过，**142 例全过、0 失败 0 错误**
  （新增 `DragAutoScrollTest` 4 例：中间区不滚、贴顶向上滚且越贴越快并封顶、贴底向下滚、感应区/上限为 0 不滚）。
- Release `0.1.59` 已发布共享：`yuzuki-0.1.59-1120-1837.apk` + `yuzuki-latest.apk`，
  与本地 `app-release.apk` 三方 sha256 一致 `fa0ebab4…8aea691`；已装到 `192.168.5.16:42847`
  （`versionCode=1059`、`versionName=0.1.59`），未启动、测试由用户自测。

**本次修复（2026-11-20，拖动抖动 + 没松手就被放下 · 0.1.60）**：

- 用户实测：往上拖到某个位置列表会抖；而且没松手就被「放下」了。
- 根因：换位是**在 `pointerInput` 的 `onDrag` 里按当时拿到的 `layoutInfo` 算的**。一帧内常有多个 move 事件，
  第一次换位后 LazyColumn 还没重新布局，第二、三次读到的还是「行在旧槽位」的旧几何，于是算出反方向的落点再换回来 ——
  这就是抖；抖的过程里行还会被顶出可视区 / 触发多余的换位提交，手势被打断，看着就像被放下了。
- 修复：
  - `ui/PlayerUI.kt`：换位 / 夹位移 / 滚动全部收进 `LaunchedEffect(dragId) + withFrameNanos` 的**每帧单次调度**，
    `onDrag` 只累加位移、不做任何判断；每帧最多换一位，换完立刻 `continue` 等下一帧布局，绝不在同一份旧布局上换第二次。
    换位后位移按「新槽位 = target 原来的 offset」直接重算，不必等下一帧（等就会有一帧偏移，看着也像抖）。
  - 新增 `ui/PlaylistDrag.kt`（`active` 标记）：拖动期间 `RootScreen` 的 `HorizontalPager` 设 `userScrollEnabled = false` ——
    手指斜着拖时横向分量超过 touch slop，Pager 会抢走手势并消费事件，拖拽当场 cancel，表现就是「没松手却被放下」。
    列表关闭 / 页面销毁时用 `DisposableEffect` 兜底复位。
- 验证：`compileDebugKotlin` / `testDebugUnitTest` 通过，**142 例全过、0 失败 0 错误**。
- Release `0.1.60` 已发布共享：`yuzuki-0.1.60-1120-1845.apk` + `yuzuki-latest.apk`，
  与本地 `app-release.apk` 三方 sha256 一致 `b8ad5642…ed223f`；已装到 `192.168.5.16:42847`
  （`versionCode=1060`、`versionName=0.1.60`），未启动、测试由用户自测。

**本次重构（2026-11-20，拖动改用「顺序不动 + 让位」 · 0.1.61）**：

- 用户实测：拖到最上部仍然抖，且会莫名被放下。
- 判定根因（上两版都没跳出同一个坑）：**拖动中实时改列表顺序**。
  每换一次位 LazyColumn 就要重新布局，而位移补偿依赖「layoutInfo 已反映这次换位」的时序；
  贴边自动滚动时每帧都在写位移，时序一错开就是「跳一行再回来」；
  换位下标配错时行会被搬到别处，帧循环 `firstOrNull { it.key == id }` 找不到它 → 旧代码 `break`，拖动当场结束。
  拖到最上部时换位与滚动最密集，所以那里最先炸。
- 改法（`ui/PlayerUI.kt`）：**拖动期间队列顺序一个字都不改**
  - 记 `dragFrom`（起始下标，拖动期恒定）/ `dragTo`（落点）/ `dragOffset`（跟手位移）/ `dragRowSize`（实测行高）；
  - 每帧只做：夹位移 → 用视觉中心算 `dragTo` → 贴边自动滚动。布局恒定 ⇒ 判定天然稳定，不会来回；
  - 渲染：被拖行 `translationY = dragOffset`，`dragFrom`→`dragTo` 之间的行整体让位一个行高
    （新增 `ui/ReorderSlide.shiftOf`，纯函数 + 4 例单测钉住方向）；
  - 松手 / 手势被打断时才 `QueueMath.reorder(from, to)` 提交一次（本地镜像先换成新顺序再清状态，避免闪一帧旧顺序）；
  - 帧循环里找不到被拖行改成 `continue`（原来 `break`）——偶发一帧异常不再等于「放下」。
- 验证：`compileDebugKotlin` / `testDebugUnitTest` 通过，**146 例全过、0 失败 0 错误**。
- Release `0.1.61` 已发布共享：`yuzuki-0.1.61-1120-1851.apk` + `yuzuki-latest.apk`，
  与本地 `app-release.apk` 三方 sha256 一致 `84c25a4c…0de4b86`；已装到 `192.168.5.16:42847`
  （`versionCode=1061`、`versionName=0.1.61`），未启动、测试由用户自测。

**本次改动（2026-11-20，播放器 Tab 图标重绘 · 分支 `feat/player-note-icon` · 0.1.63）**：

- 反馈：底部「播放器」Tab 原来是「外圈 + 内圈 + 12 点连线」，像两个套在一起的圆，读不出播放语义；
  用户给了手绘参考图（圆里一个斜置八分音符），要求照图重绘。
- 参数**不是目测**：对参考图（593×627 PNG）先做像素级测量，再最小二乘拟合，得到（以圆半径 R 为单位）
  - 圆：`R = 172.7px`，圆心 `(288.6, 383.1)`，线宽仅 `0.029R`；
  - 音符头：椭圆（空心）中心 `(−0.046, +0.030)R`、半轴 `0.377R × 0.223R`、倾角 `−26.8°`；
  - 杆：`x = 0.317y`（离竖直 17.6°，上端偏左），过圆周顶点，下端收在音符头右端尖点；
  - 旗尖：`(+0.454, −2.097)R`，整体高 `≈3.08R`。
- 实现（`ui/UiIcons.kt` 的 `TabBarIcon` → `MainTab.Player` 分支）：24 单位视口取 `R = 7.4`、圆心 `(12, 16)`，
  正好装下「圆 + 挑出圆外的旗」。音符头用 `rotate(-26.8°)` + `drawOval` 画空心椭圆；
  旗是**两段三次贝塞尔**、接点切线连续：`(10,2.5) → cubic(12.4,2.75 / 14.5,1.85) → 尖(15.36,0.48) → cubic(13.9,2.1 / 12.2,3.5) → (10.56,4.43)`。
- **线宽取 0.8（相邻 Tab 是 1.7）**：参考图的线只有 `0.029R`，若等比放到 1.7，音符的空心与曲线会全部糊成一坨；
  0.8 是「23dp 下看得清、又不失参考图轻细感」的折中，调粗只需改 `val thin = 0.8f * u` 一处。
- 中间走过的弯路（留档，避免重犯）：①曾按「头在左下、杆右倾、旗向右下」画，与参考图完全相反；
  ②按 1.7 线宽还原，音符细节被线宽吃掉；③旗用「直边 + 硬折角」画成三角形，用户指出「线条应是流畅曲线」，
  最终改为一条顺下来的曲线（V2）。**图标类改动先出渲染图给用户定稿，再改代码**。
- 验证：`compileDebugKotlin` / `testDebugUnitTest` 通过，**146 例全过、0 失败 0 错误**。
- Release `0.1.63` 已发布共享：`yuzuki-0.1.63-1120-1927.apk` + `yuzuki-latest.apk`，
  与本地 `app-release.apk` 三方 sha256 一致 `815845cf…ab0f1c`；已装到 `192.168.5.16:42847`
  （`versionCode=1063`、`versionName=0.1.63`），未启动、测试由用户自测。

**本次调整（2026-11-20，图标放大 · 0.1.64）**：

- 真机反馈「播放器图标比相邻 Tab 小」：原因是音符整体高 `3.08R`，为了把旗塞进 24 视口，
  圆环只能占 62% 宽度（「库 / 历史」的环占 70%），视觉上就小一圈。
- 改法：`MainTab.Player` 分支整体套 `scale(1.135f)`，缩放基点取图形自身 bbox 中心 `(12, 11.94)`
  （取画布中心会让上下留白不均）。放大后圆环直径 ≈ `16.1dp`，与「历史」的 `r=8.4` 环等大；
  代价是图形超出 24 视口约 `0.9` 单位（≈0.9dp），落在图标与标签之间的空隙里。
  线宽随画布变换一起放大（0.8 → 0.91），仍是细线观感。
- 验证：`compileDebugKotlin` / `testDebugUnitTest` 通过，146 例全过。
- Release `0.1.64` 已发布共享：`yuzuki-0.1.64-1120-1932.apk` + `yuzuki-latest.apk`，
  与本地一致 `774476d1…ad01f7`；已装到 `192.168.5.16:42847`（`versionCode=1064`），未启动、测试由用户自测。

**本次增量验证（2026-11-24，标签文字居中 + 筛选条件记本机 · 分支 `feat/local-filter-recall`）**：

- 需求：①标签（胶囊）里的文字没有上下居中；②App 要记住自己上次的筛选条件（存设备、下次进入读回），
  但**不记网页端的**。
- 居中根因（`ui/theme/PillText.kt`）：Compose 的 `Text` 默认 `includeFontPadding = true`，
  按字体 top / bottom 指标在上下补白，top 留白通常比 bottom 多 ≈0.2em，11–14sp 的文字整体偏下 1–2dp；
  封面标签 / 声优 chip 还把 `lineHeight` 压得比字号更小（11sp 配 12sp、12sp 配 13sp），字形被行盒挤住，
  偏移更明显。
- 改法：新增 `pillTextStyle(fontSize, weight)`（`includeFontPadding = false`、不再压行高，由容器
  对称内距 / 居中排布保证居中），替换所有标签 / 胶囊文字：`CoverTagChip`、`ActorChip`、RJ 号胶囊、
  详情 `FavableTag`（含 +/−）、播放页 `FavChip`（含 +/−）与社团 +/− 圆钮、排序胶囊、已选条件胶囊、
  搜索框占位与输入文字、抽屉筛选 chip。容器高度与内距不动，单行封面标签约 16 → 17dp。
- 本机记忆：新增 `data/FilterPrefs.kt`（`LibraryFilterPrefs(query/conds/sort/order)`，
  `sanitized()` 走排序 / 条件类型白名单 + 去重 + 5 条上限，`encode/decode` 坏数据一律退回默认）
  与 `data/FilterStore.kt`（独立 DataStore `app_library_filter`，`load` / `save` / `saveAsync`）；
  `AppContainer.filter` 暴露，`AppViewModel.enterMain` 在 `state = Main` 之前读回并
  `LibraryState.restore(...)`（首帧就是上次条件，不会先按空条件拉一遍列表）。
- `LibraryState` 新增 `persistHook`（默认 null，单测不触达存储）：`updateQuery` / `addCond` /
  `removeCond` / `clearConds` / `updateSort` / `toggleOrder` / `home` 都会把 `snapshot()` 交出去；
  随机模式仍属临时状态，恢复时收回常规列表。服务端 `/api/prefs` 依旧零读写，网页端记忆不受影响。
- 验证：`:app:compileDebugKotlin` / `:app:testDebugUnitTest` 通过，**156 例全过、0 失败 0 错误**
  （新增 `FilterPrefsTest` 8 例：默认值、编解码往返、空串 / 坏 JSON / `[]` 退回默认、字段缺失、
  排序与升降序白名单、去重与 5 条上限、未知类型与空值丢弃、关键词去空白；
  `LibraryStateTest` 由 2 例增至 4 例：每次改条件都交一次快照且与 `snapshot()` 一致、
  `restore` 应用条件并退出随机）。未截图验证。
- Release `0.1.65` 已构建：`app/build/outputs/apk/release/app-release.apk`（10.6MB，
  sha256 `9ee03b2c…b5a537`，`versionCode=1065` / `versionName=0.1.65`）。
  本次 `//192.168.5.4/FileTemp` 不可访问（`publish-apk.sh` 只警告、返回 0），
  `adb devices` 无设备、上次的无线地址 `192.168.5.16:42847` 连接失败 —— **未发布共享、未装机**，由用户自测。

**本次修复（2026-11-25，图片查看器放大后单指拖动不平移 · 0.1.66）**：

- 现象：双击 / 捏合放大后，单指拖动画面纹丝不动（捏合缩放本身正常），不像正常看图软件。
- 根因：`viewerZoomGesture` 的单指平移复用了 `ViewerZoom.panKeeping(cx = 上一帧质心, …)`，
  这条公式的语义只是「同一个屏幕点在缩放前后保持不动」；单指拖动时 `scaleNew == scaleOld`（k = 1），
  式子退化为 `panNew = panOld` —— 手指的位移根本没进公式，画面当然不动。
  也就是说 0.1.56 写的「放大后单指拖动平移」实际从未生效，只有双指（k ≠ 1）才会移动。
- 改法（`ui/NavLogic.kt`）：新增 `ViewerZoom.panFollowing(prevCx, prevCy, curCx, curCy, panX, panY, from, to)`，
  公式 `panNew = cur − (prev − panOld) × (to / from)` —— k = 1 时严格跟手；质心不动时退化为 `panKeeping`；
  边捏边移（双指质心漂移）时缩放与平移合成为一次结果。`panKeeping` 改为委托 `panFollowing(prev == cur)`，
  双击路径与既有 8 例断言零变化。
- 手势层（`ui/WorkDetailScreen.kt`）：单指越过 `touchSlop` 的那一帧把阈值当**死区**吃掉
  （`lastCentroid = centroid` 后 `continue`，本帧不产生位移），否则起手第一帧会跳一下；
  `onSettle` 越界由「直接赋值」改为 `animateZoomTo` 220ms 补间回边界。
  按用户要求**不做松手惯性滑行**；放大状态下拖到边界也不接翻页（保持 0.1.56 行为，先双击还原再切图）。
- 验证：`:app:testDebugUnitTest` 通过，**158 例全过、0 失败 0 错误**
  （`ViewerZoomTest` 新增 2 例：单指拖动严格跟手且在既有 pan 上叠加、边捏边移合成 + 质心不动退化为
  `panKeeping` + 非法倍率不动画面）。未截图验证。
- Release `0.1.66` 已构建并发布共享：`yuzuki-0.1.66-1125-1529.apk` + `yuzuki-latest.apk`，
  与本地 `app-release.apk` 三方 sha256 一致 `b407fe3e…bd1da08`（10.6MB，`versionCode=1066`）；
  已装到 `192.168.5.16:45195`（`versionCode=1066`、`versionName=0.1.66`、`lastUpdateTime=15:33:15`），
  未启动、测试由用户自测。

**本次改版（2026-11-25，详情改底部弹出框、历史页去返回按钮 · 分支 `feat/detail-sheet`，0.1.67）**：

- 需求：历史界面的返回按钮没有必要，去掉；作品详情不再作为单独界面，改成和「播放页点播放列表」
  一样从屏幕下方弹出的框（框内不要返回按钮），支持下滑关闭；框内文件列表滑动仍是正常浏览，
  相关推荐横向滑动照常，这个界面左右滑动不绑定任何功能。参考图形状：顶栏下方一整块、上部两角圆滑。
- 结构（新增 `ui/DetailSheet.kt`）：`WorkDetailOverlay` = 原顶栏（`WebTopBar`，侧栏入口保留）+
  `DetailSheet`。框 `fillMaxSize`、顶部留 `SheetTopGap = 56dp`（留白处透出被压暗的底层内容）、
  `RoundedCornerShape(topStart/topEnd = 24.dp)`、底边贴内容区（不盖底部 Tab）、
  不透明面板底 `WebColors.panel(nav, 1f)`，**不画描边**（0.1.69 按用户要求去掉那圈灰线）。
  进场：首帧先摆到屏幕下方再 220ms 滑到就位。关闭统一走 `requestClose()`：250ms 滑出后回调 `onDismiss`。
  **层级硬约束（0.1.73）**：`pointerInput` 不能挂进带 `graphicsLayer{ translationY }` 的子树，
  必须留在它外面的稳定坐标层（`padding(top = SheetTopGap)` 之后那一层），否则手势坐标与位移互为输入，
  增益 −1 会导致下滑时框上下抖动（详见 0.1.73 条目）。
  框顶另有一条 iPhone 式抓手 `SheetGrabber()`（0.1.74），见下方条目。
- 下滑关闭（新增 `ui/DetailSheetDrag.kt`，纯 Kotlin）：`canStart` 要求「框内列表已在顶部
  （`firstVisibleItemIndex == 0 && firstVisibleItemScrollOffset == 0`）+ 纵向下拉过 6dp 门槛 +
  纵向位移大于横向」，接管后 `change.consume()`（否则列表会同时 overscroll）；
  `offsetOf` 扣掉门槛后夹在 0..框高；`shouldDismiss` 用 25% 框高。列表没到顶时完全不接管，
  文件列表 / 头部卡片照常滚。横向不做任何识别（相关推荐行保留 `claimHorizontalGesture`）。
- 详情内容（`ui/WorkDetailScreen.kt`）：删掉 `onBack` 参数、列表首项返回按钮与自带 `WebTopBar`
  （顶栏移到 `WorkDetailOverlay`）；`LazyListState` 改为由外层注入（弹框要读「是否在顶部」）。
  其余头卡 / 文件树 / 长按菜单 / 图片查看器（独立 Dialog，返回键优先级更高）不变。
- 历史页（`ui/CollectionsScreen.kt`）：卡片视图与列表视图各删一个 `item(key = "back")` 与
  `WebBackButton`，`onBackToLibrary` 参数一并移除（`RootScreen` 传参同步删除）。
- 验证：`:app:testDebugUnitTest` **166 例全过、0 失败 0 错误**（`DetailSheetDragTest` 新增 8 例：
  顶部/非顶部、门槛、横竖主导、关闭动画中不接管、跟手位移夹紧、25% 判定、高度未量到）。未截图验证。
- Release `0.1.67` 已构建并发布共享：`yuzuki-0.1.67-1125-1603.apk` + `yuzuki-latest.apk`，
  与本地 `app-release.apk` 三方 sha256 一致 `d54e27dc…2656a9`（10.6MB，`versionCode=1067`）；
  已装到 `192.168.5.16:45195`（`versionCode=1067`、`versionName=0.1.67`），未启动、测试由用户自测。

**本次修复（2026-11-25，点作品后详情一片黑 · 0.1.68）**：

- 现象：0.1.67 点作品卡片后，详情页只剩顶栏和页面底色，弹出框整块看不见（截图采样中心列全是
  `#121417` 页面底色，节点树里也没有详情内容）。
- 根因：`DetailSheet` 原先靠 `Modifier.onSizeChanged` 回调拿框高，并用 `offsetPx < 0` 当「还没量到」
  的隐藏标志（`alpha = 0`）；实测这条时序在详情首次组合时没有把 `offsetPx` 从初始值推到有效值，
  框一直停在「隐藏」状态 —— 页面看上去就是全黑。另外 `Modifier.weight(1f)` 直接透传到自定义
  composable 内部的最外层节点，高度也不够可靠。
- 改法（`ui/DetailSheet.kt`）：换成 `BoxWithConstraints`，在**布局期**直接算框高
  （`maxHeight − SheetTopGap`），首帧 `offsetPx` 就等于框高（摆在屏幕下方），`LaunchedEffect(Unit)`
  滑到 0；彻底删掉 `alpha` 与「未初始化」分支，不存在一直隐藏的死状态。
  同时 `WorkDetailOverlay` 里把 `weight(1f)` 落在一层显式 `Box` 上，弹框自己 `fillMaxSize()`
  撑满这层 Box，高度来源唯一。
- 其余交互（下滑关闭、列表滚动、相关推荐横滑、系统返回键）不变。
- 验证：`:app:testDebugUnitTest` **166 例全过、0 失败 0 错误**。
- Release `0.1.68` 已构建并发布共享：`yuzuki-0.1.68-1125-1611.apk` + `yuzuki-latest.apk`，
  与本地 `app-release.apk` 三方 sha256 一致 `000f3552…749ba0`（10.6MB，`versionCode=1068`）；
  已装到 `192.168.5.16:45195`（`versionCode=1068`、`versionName=0.1.68`），未启动、测试由用户自测。

**本次调整（2026-11-25，弹出框观感修正 · 0.1.69）**：

- 用户反馈：① 框顶到顶栏的距离太小；② 看着不像弹出框，像换了一个新界面（顶部空白处看不到后面的内容）；
  ③ 多了一圈莫名其妙的灰色框线，不要，增大空间利用率。
- 改法（`ui/DetailSheet.kt` + `ui/RootScreen.kt`）：
  - `SheetTopGap` 8 → **56dp**，留白明显；
  - `RootScreen` 的 Work 分支不再套「不透明页面底色 + statusBarsPadding」的外壳，改由
    `WorkDetailOverlay` 自己铺一层 `#99000000` 压暗层（底层库页 / 播放器仍可见），
    框顶留白处透出的就是被压暗的后面内容；点压暗层空白直接关框（无涟漪）；
    顶栏下面垫一层页底色，避免压暗层与底层内容透上来干扰文字；
  - 删掉 `1dp` 描边（`WebColors.Border`），框就是纯圆角不透明面板，不再有灰线。
- 验证：`:app:testDebugUnitTest` **166 例全过、0 失败 0 错误**；未做界面测试。
- Release `0.1.69` 已构建并发布共享：`yuzuki-0.1.69-1125-1618.apk` + `yuzuki-latest.apk`，
  sha256 `6618a791…09aca3b`，`versionCode=1069`；
  已装到 `192.168.5.16:45195`（`versionCode=1069`、`versionName=0.1.69`），未启动、测试由用户自测。

**本次修复（2026-11-25，下滑抖动 / 头卡框线 / 点空白收回动画 · 0.1.70）**：

- 用户反馈：① 下滑关框时内容会抖，抖出阴影；② 作品详情（头卡）还留着一圈和文件列表一样的矩形框线；
  ③ 点上方空白区域关闭时也要有收回动画，时长 0.25s。
- ① 抖动（`ui/DetailSheet.kt`）：原来要等下拉过 6dp 门槛才接管并 `consume()`，在那之前列表已经接到
  一两帧 move 起 overscroll 拉伸，回弹动画叠在跟手位移上就是「抖 + 阴影」。改为**按下时若列表已在顶部，
  整轮手势归弹框**：第一个超过半个 `touchSlop`（约 4dp）的 move 就开始消费，列表一帧都收不到；
  半个 slop 以内不消费，点击 / 轻点不受影响。
- ② 头卡框线（`ui/WorkDetailScreen.kt`）：`HeaderCard` 去掉 `border(1.dp, WebColors.Border, HeaderShape)`，
  只留圆角面板底（文件列表那一圈框线保留，用户要留的是它）。
- ③ 收回动画：`requestClose()` 时长 180 → **250ms**；框外空白点击从 `WorkDetailOverlay` 的压暗层
  移到 `DetailSheet` 内部的一层透明空白层，点它和下滑 / 返回键走同一条 250ms 收回动画
  （压暗层本身不再可点，避免出现「瞬关」）。
- 验证：`:app:testDebugUnitTest` **166 例全过、0 失败 0 错误**；未做界面测试。
- Release `0.1.70` 已构建并发布共享：`yuzuki-0.1.70-1125-1628.apk` + `yuzuki-latest.apk`，
  sha256 `43552321…327d18`，`versionCode=1070`；
  已装到 `192.168.5.16:45195`（`versionCode=1070`、`versionName=0.1.70`），未启动、测试由用户自测。

**本次修复（2026-11-25，下滑手势吃掉列表滚动 / 头卡不占空间 · 0.1.71）**：

- 用户反馈：① 只做视觉上去掉了框线，元素并没有获得更多空间；② 详情页滑不动了，很多内容看不到。
- ② 是 0.1.70 引入的回归：为了消掉 overscroll 抖动，按下时只要列表在顶部就把**所有** move 都吃掉，
  包括向上滑 —— 列表一帧都收不到，自然滚不动。
- 改法（`ui/DetailSheet.kt` + `ui/DetailSheetDrag.kt`）：只截获「明确往下拉」的帧
  （新增 `shouldCapture(dragY, dragX, slop)` = 下拉过半个 touchSlop 且纵向占主导）；
  位移够大但方向不是往下（向上滑 / 横滑）就把**整轮手势放行**（`abandoned = true`），
  列表正常滚、相关推荐正常横滑；位移不足半个 slop 时既不截获也不放行，保证点击不被吞。
- ① 头卡真正让出空间（`ui/WorkDetailScreen.kt`）：`HeaderCard` 不再套卡片容器 ——
  去掉 `clip + background + padding(16dp)`，内容直接铺在弹出框底色上，
  可用宽度比原来多 32dp；`HeaderShape` 常量随之删除。文件列表那一圈框线保持不变。
- 验证：`:app:testDebugUnitTest` **167 例全过、0 失败 0 错误**（`DetailSheetDragTest` 新增 1 例
  「只截获明确向下的拉动」，含上滑 / 横滑 / 小位移三条断言）；未做界面测试。
- Release `0.1.71` 已构建并发布共享：`yuzuki-0.1.71-1125-1632.apk` + `yuzuki-latest.apk`，
  sha256 `4c516847…fec759`，`versionCode=1071`；
  已装到 `192.168.5.16:45195`（`versionCode=1071`、`versionName=0.1.71`），未启动、测试由用户自测。

**本次修复（2026-11-25，下拉时内容抖动 / 拖动发虚 · 0.1.72）**：

- 现象：0.1.71 之后下滑关框时内容还在抖，快速拖时文字糊成一片。
- 原因分析（`ui/DetailSheet.kt`）：
  1. 平移层和「圆角裁剪 + 底色」写在**同一条 modifier 链**上（`graphicsLayer{translationY} → clip → background`），
     每帧改矩阵时整块（含 clip 的圆角 path）都要跟着失效重绘，边缘抗锯齿逐帧变化，看着就是抖；
  2. `dragY - slopPx` 是浮点（slop 12px ≈ 16.5px），`translationY` 一直是**小数像素**，
     layer 纹理落在半像素上被双线性采样，文字发虚 —— 快速拖动时就是「抖到模糊」。
- 改法：
  1. 拆层：外层 `Box` 只负责 `padding(top=SheetTopGap)` + `graphicsLayer{ translationY }`，
     圆角裁剪与底色放到里层 `Column`。矩阵变化不再牵动裁剪 layer 的重绘；
  2. 位移 `translationY = offsetPx.roundToInt().toFloat()`，始终落在整数像素上；
  3. 顺手确认手势侧逻辑不变（只截获明确向下的帧，上滑 / 横滑整轮放行）。
- 验证：`:app:testDebugUnitTest` **167 例全过、0 失败 0 错误**；未做界面测试。
- Release `0.1.72` 已构建并发布共享：`yuzuki-0.1.72-1125-1638.apk` + `yuzuki-latest.apk`，
  sha256 `5735f318…13b4fc75`，`versionCode=1072`；
  已装到 `192.168.5.16:45195`（`versionCode=1072`、`versionName=0.1.72`），未启动、测试由用户自测。

**本次修复（2026-11-25，下滑抖动真因：手势读在被平移的图层里 · 0.1.73）**：

- 现象：0.1.72 之后下滑关框仍然上下抖。**0.1.70 / 0.1.71 / 0.1.72 三次都修在「什么时候消费事件」
  和「绘制精度」上，从未动过坐标系，所以都没治本**（0.1.72 里「半像素采样」的判断不成立，予以推翻）。
- 真因（`ui/DetailSheet.kt`）：`graphicsLayer{ translationY = offsetPx }` 会参与指针坐标转换
  （`NodeCoordinator.fromParentPosition` 走 layer 逆矩阵），而 `pointerInput` 当时挂在这块被平移的
  子树里的 `Column` 上 —— 框往下挪多少，`change.position` 就被反向减掉多少：
  `dragY = 手指位移 d − offsetPx`，而 `offsetPx = dragY − slop`。闭环增益正好 **−1**（特征值 −1，
  临界振荡）：手指一停 offset 就逐帧在两值间来回跳（幅度≈slop），匀速拖动也只跟到手指的一半。
- 改法（A 方案，结构）：
  1. 手势层搬到**被平移层的外面** —— `Box(fillMaxSize.padding(top=SheetTopGap))` 里再套一层
     `Box(fillMaxSize.pointerInput{…})`，位移 `graphicsLayer` 与 `clip+background` 收进它内部的
     `Box → Column`。padding 是常量布局偏移、不含 `offsetPx`，坐标系因此稳定；命中区也随之收在框内，
     顶部 56dp 空隙仍归下层空白层，点空白关框不受影响；
  2. 手势逻辑零改动（`capturing / canStart / shouldCapture / consume` 原样），`offsetOf` 也不用改；
  3. 进场 / 回弹 / 收回统一走 `launchSlide()` 的单个 `slideJob`，启动前取消上一个；手势接管瞬间也
     `cancel()`，避免「回弹没跑完又按下」时动画与手势交替写 `offsetPx`。
- 回归用例（`DetailSheetDragTest` +2）：「稳定坐标系下手指停住框也停住且全程单调」+「手势层跟着位移层
  移动会形成负反馈回跳」（后者把真因的数学形态钉死，禁止再把手势放回被平移的子树）。
- 验证：`:app:testDebugUnitTest` **169 例全过、0 失败 0 错误**；未做界面测试（真机由用户自测）。
- Release `0.1.73` 已构建并发布共享：`yuzuki-0.1.73-1125-1649.apk` + `yuzuki-latest.apk`，
  sha256 `b03217a6…8625ff37`（10 621 430 字节），`versionCode=1073`；
  已装到 `192.168.5.16:45195`（`dumpsys` 核对 `versionCode=1073`、`versionName=0.1.73`），
  未启动、测试由用户自测。
- 代码提交 `b3ee565`（分支 `feat/detail-sheet`）。当时推送失败：直连 github:443 超时
  （`Failed to connect after 21s`）、走代理报 `CONNECT tunnel failed, response 502`，
  期间用增量 bundle 备份到共享目录兜底；**17:09 网络恢复，已补推**
  `7d3fb53..bd44ad1`，`origin/feat/detail-sheet` 与本地 HEAD 一致。

**本次改动（2026-11-25，详情框顶部加 iPhone 式抓手 · 0.1.74）**：

- 用户要求：作品详情弹框顶部补一条 iPhone 那样的手势条。
- 实现（`ui/DetailSheet.kt` 新增私有 `SheetGrabber()`）：`36×5dp` 圆角 `2.5dp` 胶囊
  （**0.1.75 起加宽为 `48dp`**），
  颜色 `WebColors.Muted.copy(alpha = 0.5f)`（面板底 #23262E 上的浅灰条），水平居中，
  外层占 `18dp` 高（条距框顶约 6.5dp）。
- 位置：放在平移层里 `Column(clip + background)` 的最前面、`content()` 之前 —— 跟着框一起滑，
  不会跟列表滚走；**没有**放进 `WorkDetailScreen`。
- 交互：**不加点击 / 拖动逻辑**，纯视觉提示。抓住它下拉能关框，是因为手势层本来就覆盖框内
  全部区域（`DetailSheet` 的手势层在平移层外面），走的还是 `DetailSheetDrag` 那套判定。
- 内容整体下移 18dp；头卡原本 `LazyColumn` 顶部 `contentPadding = 14dp`，与条之间实际留白约 20dp。
- 验证：`:app:testDebugUnitTest` **169 例全过、0 失败 0 错误**（纯 UI 改动，无新增用例）；
  未做界面测试（真机由用户自测）。
- Release `0.1.74` 已构建并发布共享：`yuzuki-0.1.74-1125-1704.apk` + `yuzuki-latest.apk`，
  sha256 `b50edf40…5ebc93`（10 621 430 字节），`versionCode=1074`；
  已装到 `192.168.5.16:45195`（`dumpsys` 核对 `versionCode=1074`、`versionName=0.1.74`），
  未启动、测试由用户自测。

**本次改动（2026-11-25，抓手加宽 · 0.1.75）**：

- 用户觉得 0.1.74 的抓手偏窄，要求加宽。`GrabberWidth` 由 `36.dp` 改为 `48.dp`，
  高度 `5dp`、圆角 `2.5dp`、颜色与占位高度（18dp）不变。
- 验证：`:app:testDebugUnitTest` **169 例全过、0 失败 0 错误**；未做界面测试（真机由用户自测）。
- Release `0.1.75` 已构建并发布共享：`yuzuki-0.1.75-1125-1708.apk` + `yuzuki-latest.apk`，
  sha256 `b3019310…acd2db2`，`versionCode=1075`；
  已装到 `192.168.5.16:45195`（`dumpsys` 核对 `versionCode=1075`、`versionName=0.1.75`），
  未启动、测试由用户自测。

**本次改动（2026-11-25，抓手再加宽 · 0.1.76）**：

- 用户反馈 48dp 仍不协调。`GrabberWidth` 改为 **`64.dp`**（高度 5dp / 圆角 2.5dp / 占位 18dp 不变），
  约为屏宽 1/6，观感接近 iPhone 大屏上的条长。
- 验证：`:app:testDebugUnitTest` **169 例全过、0 失败 0 错误**；未做界面测试（真机由用户自测）。
- Release `0.1.76` 已构建并发布共享：`yuzuki-0.1.76-1125-1711.apk` + `yuzuki-latest.apk`，
  sha256 `c382bd70…e632e9`，`versionCode=1076`；
  已装到 `192.168.5.16:45195`（`dumpsys` 核对 `versionCode=1076`、`versionName=0.1.76`），
  未启动、测试由用户自测。
- 分支合并：2026-11-25 17:26 `feat/detail-sheet` 以 **fast-forward** 并入 `main`
  （`c6ab802..c5e4b9d`，0.1.67~0.1.76 共 14 笔一次性进主干），合并后 169 例单测仍全过，
  `origin/main` 与本地一致；源分支保留未删。

## 已知限制

- 仅家中 WiFi 可用；离开 WiFi 只能看离线缓存内容；明文 HTTP 的 token 在局域网可被嗅探。
- 冷门格式（wma 等）需后续接入 `media3-decoder-ffmpeg`；flac/mkv/mp4/wav/m4a 由 Media3 默认解码器覆盖。
- 界面采用 Material 3 风格：不再渲染液态玻璃 / 壁纸 / 模糊 / 半透明叠层（所有框体都是不透明容器）；
  App 的配色（主题色、标签色、不透明度）由**本机设置页**管理并只存本机，不读服务端 `/api/settings`，
  网页端改配色不会影响 App，App 改色也不会回写网页端
  （作品详情页的「设为壁纸」仍会上传图片到服务端，属于独立功能）。
- 底部导航不做背景模糊（见「交互改版」）：Compose 1.7.4 在这台机器上取不到内容层像素，
  四版自研实现均无效，最终回退纯黑。
- 管理后台 `/admin` 未实现（web 移动端亦由路由守卫跳回媒体库）。
- web 端后续新增功能不会自动同步到 App，两端独立发版。

## 约定

- 提交信息中文，格式 `feat: ...` / `fix: ...`；每阶段完成后立即提交，只提交本工程。
- 验证使用 typecheck / 断言 / 接口返回码，不截图、不逐屏人工确认。
- 每次改动完成后默认打 release 包并发布到 `//192.168.5.4/FileTemp`，不再逐次确认；
  同步结果给出产物路径与哈希校验结论；共享不可达时只警告、不阻断构建。
