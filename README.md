# 仿网易云音乐客户端（Android）

一个从零开发的**高还原度仿网易云音乐 Android 客户端**，使用 Kotlin 完成，覆盖「发现页、播放器、排行榜、歌单、搜索、评论、个人中心、登录」等 20+ 页面，代码量约 2 万行。项目对接真实网易云 API 与网易云账号体系（支持密码 / 验证码 / 二维码登录），并支持后台播放、歌词、MV、私人 FM、心动模式等完整听歌链路。

> 意向岗位：**AI Native 开发工程师（实习生）**
>
> 这个项目本身就是一次 **AI Native 开发流程的实践**：借助 CodeBuddy AI 编程助手，通过 Prompt 工程把需求描述直接转化为可运行代码，两周内完成核心功能落地。整个开发过程（需求梳理、Prompt 驱动编码、真实接口对接、缺陷排查与迭代优化）与目标岗位的日常工作内容重合，详见[开发过程与岗位工作内容的对应](#开发过程与岗位工作内容的对应)。

---

## 目录

- [功能特性](#功能特性)
- [核心页面导览](#核心页面导览)
- [界面预览](#界面预览)
- [技术栈](#技术栈)
- [整体架构](#整体架构)
- [核心实现亮点](#核心实现亮点)
- [开发过程与岗位工作内容的对应](#开发过程与岗位工作内容的对应)
- [项目结构](#项目结构)
- [快速开始](#快速开始)
- [后续规划](#后续规划)
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

## 核心页面导览

### 三大主 Tab（`MainActivity`）
底部导航（`BottomNavigationView`）切换 **首页 / 排行榜 / 我的** 三个 Fragment；登录后各 Tab 会根据 `AccountManager` 的登录态展示个人数据（红心、收藏、动态等），退出登录后状态整体重置。

### 首页 · 发现页（`HomeFragment`）
- 顶部为可横向滑动的分类标签（推荐 / 每日推荐 / 电台 / 心动），由 `ViewPager2` 承载对应分页，支持左右滑动切换。
- **推荐分页**：`BannerPagerAdapter` 轮播图 → 推荐歌单横滑区 → 由 `HomeAdapter` 组合的多类型信息流（封面卡、横滑专辑、榜单入口等），下拉刷新并带 loading / error / empty 三态。
- **每日推荐 / 心动**：分别进入每日推荐页与心动模式相关内容，心动歌曲可一键红心与直接播放。
- 点击推荐图 / 歌单 / 歌曲卡片时，按数据类型分发跳转到歌单详情（`PlaylistActivity`）、歌手页（`ArtistActivity`）或直接入队播放。

### 排行榜页（`RankingFragment`）
顶部横向分类展示**巅峰榜 / 歌手榜 / MV 榜 / 数字专辑榜 / 细分榜**，数据来自 `/toplist/detail`；点击榜单进入对应歌曲/内容列表，可整榜播放、点击进播放器、红心收藏。

### 播放器（`PlayerActivity`）
- **唱片页**：封面 + `ObjectAnimator` 无限旋转黑胶唱片动画，播放/暂停同步停转。
- **歌词页**：拉取 `lyric` 与 `tlyric`（翻译歌词），RecyclerView 实现逐行居中滚动、当前行高亮，点击歌词行可跳转到对应时间点。
- 唱片页与歌词页通过 `ViewPager2` 左右滑动切换。
- 底部迷你播放栏（`layout_mini_player.xml`，由 `MiniPlayerManager` 驱动）常驻于各页面底部，点击展开播放器、显示当前歌曲与播放进度。
- 播放模式在 单曲循环 / 顺序 / 随机 / 心动模式 / 私人 FM 间切换，队列由 `PlayQueueManager` 单例统一维护。

### 歌单详情（`PlaylistActivity`）
展示歌单封面、标题、播放数与歌曲列表；支持整单播放、逐曲播放/收藏、查看与发表评论。

### 搜索页（`SearchActivity`）
- 热搜关键词列表（`SearchHotAdapter`）点击即搜索；输入联想；搜索结果按歌曲 / 歌单 / 歌手等类型展示。
- 搜索历史本地保存（SharedPreferences），可再次点击快速搜索；歌曲结果可直接播放或加入队列。

### 登录 / 注册（`LoginActivity` / `RegisterActivity`）
支持**手机号 + 密码 / 短信验证码 / 扫码**三种登录方式；登录成功后由 `AccountManager` 保存会话，Cookie 全局注入所有网络请求与播放取流；登录后可进入资料编辑（头像、昵称、签名等）。

### 个人中心（`MineFragment` + 一系列子页面）
- 顶部展示用户信息与关注/粉丝数；中部为功能入口：**我收藏的歌单、最近播放、最近收听、心动、关注与粉丝列表、个人主页、徽章墙、同状态用户**等，点击分别进入独立 Activity。
- **动态**：可发布状态（`StatusEditActivity`）、浏览自己与他人主页动态。
- **设置**（`SettingsActivity`）：账号信息、主题、播放偏好等。

### 更多内容页
- **私人 FM**（`PrivateFmActivity`）：随机推荐歌曲的电台式连续播放。
- **心动模式**（`HeartbeatDetailActivity` 等）：基于当前歌曲由服务端智能推荐相似歌曲并连续播放。
- **MV 播放**（`MvPlayerActivity`）：`VideoView + MediaController`，进入自动横屏沉浸播放。
- **评论**（`CommentActivity`）：歌曲/歌单评论区，支持点赞、回复、分页加载更多。
- **每日推荐**（`DailyRecommendActivity`）：按日生成的个性化歌单。

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

## 开发过程与岗位工作内容的对应

我申请的是 **AI Native 开发工程师（实习生）**。下面的内容不是"能力描述"，而是按该岗位日常做的工作，列出本项目开发中**已经实际做过的事**：

### 1. 使用 AI 编程助手 + Prompt 工程，把需求描述直接转化为可运行代码

- 全程基于 **CodeBuddy AI 编程助手**开发，采用「结构化 Prompt → AI 生成代码 → 编译运行 → 验证修正」的循环工作流，而不是把 AI 当搜索引擎。
- Prompt 中会写清：功能与页面结构、交互与状态归属、数据来源与接口契约、技术约束（如 minSdk 24、Kotlin/ViewBinding、不引入多余依赖），使一次生成即可编译运行，减少返工。
- 典型 Prompt 形式（节选）：

```text
用 Kotlin + ViewBinding 实现一个「排行榜」页面：
- 顶部横向分类（巅峰榜/歌手榜/MV榜/数字专辑），数据来自 ApiService#getTopList，
  返回结构为 Result<TopListData>，异常统一抛 AppException；
- 列表用 RecyclerView 多类型 item，点击跳转 PlaylistActivity 并传入 playlistId；
- 网络状态在 ViewModel 中管理，loading/error/empty 三态都要有布局……
```

- 产出：**首页信息流、全功能播放器、排行榜、歌单、搜索、登录注册等核心功能在约两周内落地**，随后以同一节奏迭代至 20+ 页面。

### 2. 对接开源 NeteaseCloudMusicApi，实现网络请求、数据解析与页面渲染

- 自部署开源 Node 服务 `NeteaseCloudMusicApi`（端口 3000）作为后端，**非 Mock 数据**。
- App 端基于 OkHttp 手写 `ApiClient` / `ApiService`，覆盖 `banner`、`personalized`、`playlist/detail`、`toplist/detail`、`search`、`song/url`、`lyric`、`comment`、`login/qr` 等真实接口（歌曲 / 歌单 / 榜单 / 搜索 / 评论 / 登录全链路）。
- 网络层用协程桥接回调并统一 `Result` / `AppException` 封装，数据用 Gson 解析为模型，再经多类型 RecyclerView、Banner 轮播、歌词滚动等页面完成真实数据的渲染展示。

### 3. 独立完成需求梳理、调试、BUG 修复，排查 AI 生成代码的兼容性缺陷

需求先拆成「页面结构 / 接口契约 / 交互状态 / 异常分支」再交给 AI；对 AI 首版代码逐段 review，重点排查边界与兼容性，问题独立定位后修复并回灌给后续 Prompt。开发中实际处理过的几类问题：

| 典型问题（多为 AI 首版代码引入） | 定位与修复 |
|---|---|
| 网络代码用回调链 + 直接在主线程更新 UI，偶发崩溃与数据竞态 | 重构为协程 suspend + 统一 `Result`，网络状态收敛到 ViewModel，消除竞态 |
| 默认所有歌曲都可直接播放，VIP/试听歌曲取流为空致播放器空转 | 增加音质分级请求与失败自动降级兜底，播放成功率显著提升 |
| 登录后接口带 Cookie 正常，但 `MediaPlayer` 播放仍被限制 | 定位到 OkHttp 与系统播放器属于双网络栈、Cookie 不通，注入 `java.net.CookieManager` 解决 |
| 长列表一次性刷新导致卡顿、item 复用错位 | 改用多类型 Adapter 差异化刷新 + Glide 异步占位，避免卡顿与图片错位 |
| 后台播放生命周期边界问题（退出页面/来电/切后台） | 抽出前台 `MusicService` 统一管理播放器生命周期与媒体通知 |

> 小结：AI 显著提升了编码速度，但「需求表达、架构判断、缺陷定位、兼容性验证」这些环节仍需要人去完成。在上述开发过程中沉淀的这套「人机协作」方法，正是我想在 AI Native 开发岗位上持续深入的方向。

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

### 关键类速览

| 职责 | 主要类 |
|---|---|
| 网络请求 | `data/remote/ApiClient.kt`、`ApiService.kt` |
| 本地数据库 | `data/local/AppDatabase.kt` + `entity/*` + `dao/*`（收藏、播放历史） |
| 仓库层（数据统一入口） | `data/Repository.kt` |
| 前台播放服务 | `service/MusicService.kt` |
| 全局状态（单例） | `manager/`：`AccountManager`、`PlayQueueManager`、`FavoriteManager`、`BadgeManager`、`MiniPlayerManager`、`UserStatusManager` |
| 三大主页面 | `ui/home/HomeFragment.kt`、`ui/ranking/RankingFragment.kt`、`ui/mine/MineFragment.kt` |
| 播放相关页面 | `ui/player/PlayerActivity.kt`、`PrivateFmActivity.kt`、`HeartbeatDetailActivity.kt`、`MvPlayerActivity.kt` |
| 内容页面 | `ui/playlist/PlaylistActivity.kt`、`ui/artist/ArtistActivity.kt`、`ui/search/SearchActivity.kt`、`ui/comment/CommentActivity.kt`、`ui/daily/DailyRecommendActivity.kt` |
| 账号页面 | `ui/login/LoginActivity.kt`、`RegisterActivity.kt` |
| 个人中心子页面 | `ui/mine/`：`FavoritesActivity`、`RecentPlayActivity`、`RecentListenActivity`、`HeartbeatActivity`、`SettingsActivity`、`ProfileEditActivity`、`FollowListActivity`、`UserHomeActivity`、`BadgeWallActivity`、`StatusEditActivity`、`SameStatusUsersActivity` |
| 公共 Adapter | `adapter/`：`BannerPagerAdapter`、`HomeAdapter`、`MultiTypeAdapter`、`SearchHotAdapter` |
| 基类与工具 | `base/`：`BaseActivity`、`BaseViewModel`、`Result`；`util/`：`BlurTransformation`、`Extensions` |

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

## 后续规划

沿用「AI 编程助手 + Prompt 工程 + 人工校验」的同一套开发方式继续迭代本项目：

- [ ] **接入大模型 API，打造 AI 音乐助手**：自然语言点歌、歌单智能总结与转文案、按心情/场景生成歌单——把 Prompt 工程从"开发环节"进一步延伸到"产品功能本身"
- [ ] **心动模式增强**：基于本地收听行为 + Embedding 做端侧轻量相似歌曲召回，替代纯服务端推荐
- [ ] **语音交互**：结合系统语音识别，把「下一首 / 单曲循环 / 播放我收藏的歌单」等指令接入现有播放队列
- [ ] **数据驱动的个性化**：把 Room 中的播放/收藏行为沉淀为用户画像，参与推荐与每日推荐生成
- [ ] 架构升级：迁移 Compose + 单 Activity 多模块，便于后续以模块化方式承载 AI 能力

---

## 免责声明

- 本项目**仅用于个人学习与交流**，界面与功能仅作技术还原参考。
- 所有音乐内容、图片与数据均来自第三方服务，版权归原作者/版权方所有。
- 涉及网易云账号登录与 Cookie 仅为本地客户端功能所需，请勿用于任何商业用途或非法目的。
- 请勿将本项目用作任何商业产品，请遵守相关服务条款与法律法规。

---

## License

本项目仅供学习交流，不提供任何开源许可授权；如需使用请联系作者。

---

**作者：** <你的姓名 / GitHub / 邮箱>
**意向岗位：** AI Native 开发工程师（实习生）
**更多作品：** <个人主页 / 简历链接>
