# BiliTVNative 项目结构、依赖与技术方案

本文根据当前仓库源码整理，描述单模块原生 Android 客户端的目录、依赖和实现边界。产品约束与阶段记录仍以根目录 `AGENTS.md`、`DEVELOPMENT_PLAN.md`、`DEVELOPMENT_PROGRESS.md` 为准。

## 项目定位

`BiliTVNative` 是面向 Android TV 与平板的 B 站点播客户端，包名 `com.kirin.bilitv`。技术栈是 Kotlin、Jetpack Compose、Compose for TV 和 Media3。旁边的 Flutter 工程只作为行为参考，本仓库不修改它。

当前版本 `1.0.1`（`versionCode` 101）。直播播放、应用内更新和可扩展插件不在范围内；空降助手是唯一内置的跳过片段能力。

## 仓库布局

```text
BiliTVNative/
  app/                          唯一 Android 应用模块
    src/main/java/com/kirin/bilitv/
    src/main/res/               字符串、图标、主题、TV banner
    src/test/java/              JVM 单测
    src/androidTest/            仪器测试依赖已声明，测试源码按需补充
    build.gradle.kts
    proguard-rules.pro
  gradle/libs.versions.toml    版本目录
  gradle/wrapper/               Gradle 9.4.1
  docs/                         结构与接口说明
  build.gradle.kts              根构建，构建目录外置
  settings.gradle.kts           只 include :app
  build-release.bat             分别打出两个 ARM Release APK
  AGENTS.md
  DEVELOPMENT_PLAN.md
  DEVELOPMENT_PROGRESS.md
```

构建产物不写在仓库内。根脚本把 `layout.buildDirectory` 指到 `%USERPROFILE%\.gradle\bilitv-native-build\`，Release 成品再复制到该目录下的 `release-apks\`。

## 源码包

入口：

| 类型 | 路径 | 职责 |
| --- | --- | --- |
| Application | `BiliTvApplication` | 创建 `AppContainer`，配置 Coil 内存 20%、磁盘 128MB |
| Activity | `MainActivity` | Compose 宿主，沉浸式系统栏 |

`core/` 放与界面无关的数据和播放能力：

| 包 | 职责 |
| --- | --- |
| `core.app` | `AppContainer`，显式组装网络、仓库、存储和登录 |
| `core.auth` | TV 登录签名、WBI 签名、登录与 WBI key 仓库 |
| `core.network` | OkHttp 客户端、接口常量、首页/搜索/动态/历史/空间仓库 |
| `core.player` | 播放地址、弹幕、评论、雪碧图、空降、编码探测、进度 |
| `core.storage` | DataStore：会话、设置、搜索历史、WBI key、播放进度 |
| `core.settings` | 画质、主题、视觉档位、语言等设置模型 |
| `core.model` | `VideoSummary`、`HomeSection` 等展示模型 |
| `core.image` | 封面/头像尺寸和 CDN 裁切参数 |
| `core.cache` | 应用缓存清理 |
| `core.i18n` | OpenCC 简繁转换 |

`ui/` 按页面拆分，不使用 Compose Navigation：

| 包 | 职责 |
| --- | --- |
| `ui.shell` | `AppShell`、左侧导航、目的地、导航 ViewModel、焦点恢复 |
| `ui.home` | 推荐页、分区、TV/Touch 视频网格、卡片 |
| `ui.search` | TV 键盘与平板输入框，共用 `SearchViewModel` |
| `ui.feed` | 动态、历史 |
| `ui.login` / `ui.account` | 二维码登录与账号卡片 |
| `ui.player` | 播放页、控制层、弹幕层、侧栏、完成动作 |
| `ui.settings` | 播放设置、UI/UX、系统设置 |
| `ui.theme` | `BiliTokens`、主页四套主题 |
| `ui.focus` | D-pad 焦点表面 |
| `ui.glass` | 液态玻璃与假玻璃 fallback |
| `ui.input` | TV / 平板交互画像 |
| `ui.transition` | 播放进出场共享元素 |
| `ui.common` | 加载态、时钟、封面预取 |
| `ui.i18n` | 把简繁转换接到 Compose |

资源语言过滤为 `zh`、`zh-rHK`、`zh-rTW`。面向用户的静态文案放在 `res/values/strings.xml`。

## 依赖

版本集中在 `gradle/libs.versions.toml`。仓库源是 Google、Maven Central，以及字节跳动弹幕引擎所用的 `https://artifact.bytedance.com/repository/releases/`。

| 依赖 | 版本 | 用途 |
| --- | --- | --- |
| Android Gradle Plugin | 9.2.1 | 应用构建 |
| Kotlin | 2.3.21 | 语言、Compose 编译器、serialization 插件 |
| Gradle Wrapper | 9.4.1 | 构建入口 |
| Compose BOM | 2026.05.00 | Compose UI、Foundation、Material 3 |
| AndroidX Activity Compose | 1.13.0 | `setContent` |
| AndroidX Lifecycle | 2.9.4 | ViewModel、runtime、SavedState |
| AndroidX TV Material | 1.0.0 | TV 组件基础 |
| AndroidX Core KTX | 1.17.0 | Android KTX |
| DataStore Preferences | 1.1.7 | 小型本地状态 |
| Media3 | 1.10.0 | ExoPlayer、DASH、UI、OkHttp DataSource |
| OkHttp / okhttp-brotli | 4.12.0 | HTTP；API 响应支持 Brotli |
| Coil | 2.7.0 | 图片 |
| kotlinx-coroutines | 1.10.2 | 异步 |
| kotlinx-serialization-json | 1.9.0 | JSON |
| danmaku-render-engine | 0.1.0 | 字节跳动原生弹幕 |
| OpenCC4J | 1.14.0 | 简繁转换 |
| backdrop | 2.0.0-alpha03 | Android 13+ 液态玻璃 |
| ZXing | 3.5.4 | 登录二维码 |
| JUnit 4 | 4.13.2 | JVM 单测 |

当前没有 Room、Koin、Hilt 或 Compose Navigation。依赖注入就是 `AppContainer` 在 `Application.onCreate` 里 new 出来，页面用显式 ViewModel factory 取仓库。

## 构建参数

| 项 | 值 |
| --- | --- |
| `compileSdk` / `targetSdk` | 36 |
| `minSdk` | 23 |
| JDK | 17 字节码；本机可用 JDK 21 运行 Gradle |
| 默认 ABI | `armeabi-v7a`、`arm64-v8a` |
| 模拟器 ABI | 仅当 `-PemulatorValidationAbi=true -PtargetAbi=x86` 或 `x86_64` |
| Debug | 不混淆、不裁资源 |
| Release | R8、资源裁剪、语言过滤；签名暂用 debug keystore |
| 权限 | 网络、网络状态、WakeLock；Leanback 与触摸屏都不是必需特性 |

`gradle.properties` 里的 `org.gradle.java.home` 指向 Android Studio 自带 JBR。没有安装 Android Studio 时，用本机 JDK 覆盖该属性，否则 Gradle 会因为找不到这个目录而无法启动。Gradle 9 可以跑在 JDK 17 或 JDK 21 上，工程字节码目标仍是 17。

Debug：

```powershell
.\gradlew.bat :app:assembleDebug "-Dorg.gradle.java.home=$env:JAVA_HOME"
```

只打一个 ABI 的 Release：

```powershell
.\gradlew.bat :app:assembleRelease -PtargetAbi=armeabi-v7a
.\build-release.bat
```

## 技术方案

### 组装与状态

数据从 `BiliApiClient` 进入各 Repository，解析成 `VideoSummary` 或播放模型，再由 ViewModel 暴露给页面。

已经进 ViewModel 的状态：

- `RecommendViewModel`：分区、分页、刷新。
- `SearchViewModel`：关键词、建议、历史、排序、结果分页。
- `DynamicFeedViewModel`、`HistoryFeedViewModel`：游标分页。
- `PlaybackSessionViewModel`：用 `SavedStateHandle` 记住当前播放请求，配置变化后恢复会话。
- `AppShellNavigationViewModel`：目的地和播放来源卡片；冷启动仍进推荐页。

留在 UI 运行时的状态：D-pad `FocusRequester`、列表滚动、Touch 手势、`ExoPlayer`、`PlayerView`、`SurfaceView`、WakeLock，以及播放位置循环。这些和控件生命周期绑在一起，不放进宽的 `StateFlow`。

本地状态全部走同一个 DataStore `bili_settings`：设置、登录会话、搜索历史、WBI key、弹幕设置和播放进度。播放进度保留最近约 200 条索引。

### 双端界面

`resolveInteractionProfile` 综合 TV `uiMode`、触摸屏和输入方式，得到设备形态与 Remote/Touch 输入模式。TV `uiMode` 优先于错误的触摸屏声明，避免电视被当成平板。

导航壳已经收成统一的左侧栏，电视、平板和手机横屏都走 `TvAppScaffold`。差异留在内容层：

- Remote：D-pad 网格、焦点恢复、TV 播放控制栏。侧栏和内容区用 `FocusRequester` 交接，不依赖默认最近邻搜索。
- Touch：独立网格，按宽度 2/3/4 列，支持触底分页和下拉刷新；搜索用系统输入框；播放器支持单击显隐、双击暂停、长按倍速、横滑 seek、左右半屏亮度/音量。

两套网格不共用对方的焦点或手势代码，数据层不复制。

### 视觉

颜色、间距、圆角、焦点缩放和动画时长来自 `BiliTokens.kt`，品牌粉是 `#FB7299`。主页主题有默认粉、深黑、高级灰、蓝灰四种，只作用于主页、搜索、动态、历史、设置和导航。播放器视频层使用独立配色。

视觉性能三档通过 DataStore 保存：

- 流畅：关平滑滚动、焦点阴影、封面模糊、流光、重动画和封面预取；列表封面用较小尺寸和 `RGB_565`，并关闭图片内存缓存。内存低于 1GB 的设备默认这一档。
- 均衡：默认档。主题色、假玻璃、轻缩放、边框和平滑滚动，不做实时模糊。
- 精致：手动开启。更强玻璃、高光、更高质量封面，以及主题色流光。Android 13 及以上可以在这一档打开实验液态玻璃；更低版本读写都会关掉。

视频 `SurfaceView` 不参与圆角、透明、缩放或玻璃采样。控制层、弹幕和面板叠在它上面。

### 播放

播放信息请求见 `docs/bilibili-api.md`。选定 DASH 地址后，`BiliMediaDataSourceFactory` 用单独的 OkHttp 客户端把 User-Agent、Referer、Origin 和 Cookie 交给 Media3 `OkHttpDataSource`。默认渲染是 `SurfaceView`。

选编码前会探测 `MediaCodec`。探测只作参考：用户强制 H.264，或设备不支持当前偏好时，回退到 H.264 / Auto。`fnval` 默认要 DASH（16）；设备支持且偏好允许时再加 H.265（64）和 AV1（1024）。

播放位置、缓冲、时钟分钟、在线人数节流和迷你进度条共用 `BiliMotion.PlayerProgressUpdateMs` 的生命周期循环。控制层自动隐藏和 seek 预览确认是可取消的一次性延迟，不另开周期 Timer。进度在 `onPause` 写入本地，并在已登录时上报心跳。

进入播放后，主页组合应释放或暂停，避免背景、阴影和封面预取在播放期间继续跑。

### 弹幕与空降

弹幕 XML 在 IO 线程下载并解析，最多保留 5000 条，再交给 `DanmakuView`。应用层不做轨道碰撞，也不用 `delay` 重绘。空降片段来自第三方 SponsorBlock 兼容接口，只作为进度条标记和跳过提示。

### 图片

Coil 全局限制内存和磁盘缓存。海报、头像请求带目标尺寸，列表封面可走 B 站 CDN 的 `@宽w_高h_1c.webp`。详情图、头像、透明图和雪碧图不强制 `RGB_565`。只预取可见窗口附近的封面。

## 明确不做

- 直播播放和直播弹幕。
- 应用内更新。
- 插件系统。
- 在当前单模块稳定前拆多 Gradle 模块。
- 为了形式引入 Room、Koin 或 Navigation。
- 常驻播放器诊断 HUD。临时日志不能打印 Cookie、SESSDATA 或 token。
