# 仿网易云音乐客户端（Android）

一个从零开发的**高还原度仿网易云音乐 Android 客户端**，使用 Kotlin 完成，覆盖「发现页、播放器、排行榜、歌单、搜索、评论、个人中心、登录」等 20+ 页面，代码量约 2 万行。项目对接真实网易云 API 与网易云账号体系（支持密码 / 验证码 / 二维码登录），并支持后台播放、歌词、MV、私人 FM、心动模式等完整听歌链路。

---

## 目录

- [功能特性](#功能特性)
- [界面预览](#界面预览)
- [技术栈](#技术栈)
- [整体架构](#整体架构)
- [核心实现亮点](#核心实现亮点)
- [项目结构](#项目结构)
- [快速开始](#快速开始)
- [Roadmap 与 AI Native 探索](#roadmap-与-ai-native-探索)
- [免责声明](#免责声明)
- [License](#license)

---

## 功能特性

### 音乐播放全链路
- 独立前台服务 `MusicService` 后台播放，`androidx.media` 通知栏媒体控制（播放/暂停/切歌），应用退出后仍可播放
- 播放队列与多种播放模式：单曲循环 / 顺序播放 / 随机播放 / **心动模式（智能推荐）** / **私人 FM**
- 旋转黑胶唱片动画 + 底部迷你播放栏，支持封面、进度、状态全局联动
- 支持歌曲与**翻译歌词**，RecyclerView 实现逐行居中滚动与高亮
- 音质分级请求与取流兜底：standard / exhigh / lossless 等自动降级，提升播放成功率
- 通过 `ViewPager2` 在封面、歌词、歌手信息间左右滑切

### 内容发现与社区
- 首页发现页：Banner 轮播、推荐歌单、每日推荐、私人 FM、心动模式、多类型混合信息流
- 排行榜：巅峰榜 / 歌手榜 / MV 榜 / 数字专辑榜 / 细分榜
- 歌单详情、歌手详情、MV 播放（自动横屏沉浸）
- 评论系统：歌曲/歌单评论、点赞、回复、更多评论分页加载
- 搜索：热搜关键词、搜索联想，歌曲/歌单/歌手/专辑/用户多类型结果

### 账号与个人中心
- 网易云账号体系：**手机号+密码 / 短信验证码 / 扫码登录**，Cookie 会话统一注入
- 个人中心：我收藏的歌单、最近播放、最近收听、心动页、关注/粉丝、个人主页、徽章墙、状态动态发布与浏览、同状态用户、资料编辑、设置
- 收藏与播放历史使用 **Room** 本地持久化；登录态、播放模式、搜索历史使用 SharedPreferences

---

## 界面预览

> TODO：将运行截图放入 `docs/screenshots/` 后在此补充，建议包含：首页、播放器（歌词页）、排行榜、搜索、我的。

```
docs/screenshots/
├── home.png          # 首页发现页（Banner + 推荐流）
├── player_lyric.png  # 播放器歌词滚动页
├── player_disc.png   # 播放器黑胶唱片页
├── ranking.png       # 排行榜页
├── search.png        # 搜索页（热搜 + 结果）
└── mine.png          # 个人中心页
```

---

## 技术栈

| 分类 | 选型 |
|---|---|
| 语言 | Kotlin |
| UI | 传统 View + XML，`ViewBinding`，ViewPager2，SwipeRefreshLayout，Material Components |
| 架构 | MVVM（ViewModel + LiveData）+ Repository 仓库层 |
| 网络 | OkHttp 4.12（`ApiClient` 手写封装，Logging 拦截器），Kotlin 协程桥接回调 |
| 本地存储 | Room 2.7.1（KSP）、SharedPreferences |
| 图片加载 | Glide 4.16（自定义模糊 Transform） |
| 媒体播放 | `android.media.MediaPlayer` + 前台 Service + `androidx.media` 媒体通知 |
| 导航 | Navigation 2.8.7 |
| 序列化 | Gson |
| 构建 | AGP 9.0.0 / Kotlin 2.2.10 / KSP，Gradle 版本目录，compileSdk 36、minSdk 24、targetSdk 36 |

---

## 整体架构

项目采用清晰分层，页面代码与数据访问完全解耦：

```
┌────────────────────────────────────────────────────────┐
│  UI 层（Activity / Fragment）                            │
│   · 20+ Activity：首页 / 播放器 / 歌单 / 搜索 / 排行榜 / 我的…  │
├────────────────────────────────────────────────────────┤
│  MVVM 层（ViewModel + LiveData）                         │
│   · 十余个 ViewModel，单向数据流，处理页面业务状态            │
├────────────────────────────────────────────────────────┤
│  Repository 仓库层                                       │
│   · 统一数据入口，封装网络/本地来源，返回统一 Result 模型       │
│   · Result 封装含 AppException，统一异常处理                │
├───────────────────┬────────────────────────────────────┤
│  Data - 网络       │  Data - 本地                        │
│  ApiService/ApiClient │  Room（收藏、播放历史）             │
│  OkHttp + 协程 + Cookie│  SharedPreferences（登录态/偏好）    │
├───────────────────┴────────────────────────────────────┤
│  全局状态 Manager（单例）                                 │
│   Account / PlayQueue / Favorite / Badge /              │
│   MiniPlayer / UserStatus                               │
│  前台播放服务 MusicService（MediaPlayer + MediaStyle 通知）│
└────────────────────────────────────────────────────────┘
```

全局单例（`Manager`）负责登录态、播放队列、红心收藏、迷你播放器、徽章等跨页面状态，页面之间通过它完成「一处操作、处处同步」；播放进度与歌曲变更通过接口回调与 `LiveData` 双向联动。

---

## 核心实现亮点

1. **完整的前台音乐播放链路**
   `MusicService`（`foregroundServiceType="mediaPlayback"`）独立管理播放器生命周期，支持自动连播、异常自动切歌、多种播放模式切换；通知栏通过 MediaStyle 提供媒体控制；封面使用 Glide 异步加载同步到通知大图。

2. **Cookie 双通道问题与解法**
   网易云取流对登录态敏感。项目中 OkHttp 请求统一携带 Cookie，而 `MediaPlayer` 走系统网络栈、与 OkHttp 的 Cookie 机制互不相通——因此将登录 Cookie 同步注入系统 `java.net.CookieManager`，解决「接口已登录、播放仍受限」的经典问题。

3. **音质分级与取流兜底**
   播放前按 `standard → 更高音质` 逐级请求可播链接，失败自动降级，显著提升 VIP/试听歌曲的播放成功率。

4. **歌词逐行高亮与翻译**
   拉取 `lyric` + `tlyric`（翻译歌词），RecyclerView 实现居中滚动、当前行高亮与手势自由滑动。

5. **黑胶唱片旋转 + 迷你播放器全局联动**
   使用 `ObjectAnimator` 驱动唱片无限旋转，暂停时同步停转；底部迷你播放栏与播放页、通知栏多入口状态一致。

6. **图片模糊与沉浸式视觉还原**
   基于 Glide 自定义 `BlurTransformation`（降采样近似高斯模糊）实现页面底部模糊封面；配合大量渐变蒙层还原网易云红主题的沉浸观感。

7. **大型信息流与多类型列表**
   首页/我的等复杂页面使用多类型 Adapter 组合（Banner、卡片流、横向专辑流等），配合 ViewPager2 顶部滑动分类（推荐/每日推荐/电台/心动）。

8. **统一异常与加载状态**
   自研 `Result`（含 `AppException`）统一封装网络结果，`BaseViewModel` / `BaseActivity` 收敛公共逻辑，业务层只关注数据本身。

---

## 项目结构

```
app/src/main/java/com/example/myapplication/
├── MyApp.kt / MainActivity.kt        # Application 初始化、底部导航主界面
├── adapter/                          # 轮播、多类型列表、热搜等 Adapter
├── base/                             # BaseActivity / BaseViewModel / Result 封装
├── data/
│   ├── Repository.kt                 # 仓库层
│   ├── remote/                       # OkHttp 客户端 + 网易云 API 封装
│   └── local/                        # Room（收藏、播放历史）entity / dao
├── manager/                          # 全局单例：账号 / 队列 / 收藏 / 迷你播放器…
├── service/MusicService.kt           # 前台播放服务
├── ui/
│   ├── home / ranking / mine         # 三大主 Tab 页面
│   ├── player/                       # 播放器、心动详情、私人FM、MV
│   ├── playlist / artist / daily     # 歌单 / 歌手 / 每日推荐
│   ├── search / comment / login      # 搜索 / 评论 / 登录注册
│   └── mine/                         # 我的（收藏、动态、徽章墙、资料…20+ 子页）
├── viewmodel/                        # 各页面 ViewModel
└── util/                             # Glide 模糊变换、扩展函数等
```

---

## 快速开始

### 1. 启动后端 API 服务

本项目对接网易云音乐的社区开源 API（`NetEaseCloudMusicApi`，Node.js 服务，默认端口 `3000`），**不是本地 Mock 数据**。请先自行部署并保持该服务运行（仅用于学习交流）：

```bash
git clone https://github.com/Binaryify/NeteaseCloudMusicApi.git
cd NeteaseCloudMusicApi
npm install
node app.js        # 默认监听 0.0.0.0:3000
```

### 2. 配置 API 地址

打开 `app/src/main/java/com/example/myapplication/data/remote/ApiClient.kt`，修改 `BASE_URL`：

```kotlin
// 真机调试：填写电脑的局域网 IP
private const val BASE_URL = "http://192.168.55.245:3000"

// Android 模拟器：宿主机即本机
// private const val BASE_URL = "http://10.0.2.2:3000"
```

### 3. 运行 App

使用 Android Studio（建议新版，AGP 9 / JDK 17+）打开工程根目录，等待 Gradle 同步完成后直接运行 `app` 模块即可。

> 提示：真机与电脑需处于同一局域网；Android 9+ 明文 HTTP 已由 Manifest 中 `usesCleartextTraffic="true"` 放开，登录与音乐播放均需在 API 服务可访问的前提下进行。


---


