package com.example.myapplication.data.remote

import com.example.myapplication.base.Result as AppResult
import com.google.gson.annotations.SerializedName

/**
 * API 服务接口
 * 封装所有与 NetEaseCloudMusicApiBackup 的接口调用
 * 全部使用 suspend 函数 + Result 封装
 */
object ApiService {

    // ==================== 推荐 / 首页 ====================

    /**
     * 获取 banner 轮播图
     * @param type 类型：0-pc, 1-android, 2-iphone, 3-ipad
     */
    suspend fun getBanners(type: Int = 1): AppResult<BannerResponse> {
        return ApiClient.get("/banner", mapOf("type" to type.toString()))
    }

    /**
     * 获取推荐歌单
     * @param limit 获取数量
     */
    suspend fun getRecommendPlaylists(limit: Int = 20): AppResult<RecommendPlaylistResponse> {
        return ApiClient.get("/personalized", mapOf("limit" to limit.toString()))
    }

    /**
     * 获取推荐新歌
     */
    suspend fun getNewSongs(limit: Int = 10): AppResult<NewSongResponse> {
        return ApiClient.get("/personalized/newsong", mapOf("limit" to limit.toString()))
    }

    // ==================== 歌单 ====================

    /**
     * 获取歌单详情（包含歌曲列表）
     * @param id 歌单 ID
     */
    suspend fun getPlaylistDetail(id: Long): AppResult<PlaylistDetailResponse> {
        return ApiClient.get("/playlist/detail", mapOf("id" to id.toString()))
    }

    /**
     * 获取精品歌单
     */
    suspend fun getHighQualityPlaylists(
        limit: Int = 20,
        before: Long? = null
    ): AppResult<PlaylistListResponse> {
        val params = mutableMapOf("limit" to limit.toString())
        before?.let { params["before"] = it.toString() }
        return ApiClient.get("/top/playlist/highquality", params)
    }

    // ==================== 排行榜 ====================

    /**
     * 获取所有榜单摘要
     */
    suspend fun getToplistDetail(): AppResult<ToplistDetailResponse> {
        return ApiClient.get("/toplist/detail")
    }

    /**
     * 获取榜单歌曲列表
     * @param id 榜单 ID（如 19723756 云音乐飙升榜）
     */
    suspend fun getToplist(id: Long): AppResult<ToplistSongResponse> {
        return ApiClient.get("/top/list", mapOf("id" to id.toString()))
    }

    /**
     * 获取歌手排行榜
     * @param type 地区类型：1-华语, 2-欧美, 3-韩国, 4-日本
     */
    suspend fun getTopArtists(type: Int = 1): AppResult<TopArtistResponse> {
        return ApiClient.get("/toplist/artist", mapOf("type" to type.toString()))
    }

    /**
     * 获取 MV 排行榜
     * @param limit 数量
     */
    suspend fun getTopMv(limit: Int = 30): AppResult<TopMvResponse> {
        return ApiClient.get("/top/mv", mapOf("limit" to limit.toString()))
    }

    /**
     * 获取新专辑（数字专辑榜数据源）
     * GET /album/newest
     */
    suspend fun getNewAlbums(): AppResult<NewAlbumResponse> {
        return ApiClient.get("/album/newest")
    }

    /**
     * 获取专辑详情（含歌曲列表）
     * GET /album?id={id}
     */
    suspend fun getAlbumDetail(id: Long): AppResult<AlbumDetailResponse> {
        return ApiClient.get("/album", mapOf("id" to id.toString()))
    }

    /**
     * 获取 MV 播放地址
     * @param id MV ID
     * @param r 分辨率：240/480/720/1080
     */
    suspend fun getMvUrl(id: Long, r: Int = 720): AppResult<MvUrlResponse> {
        return ApiClient.get("/mv/url", mapOf("id" to id.toString(), "r" to r.toString()))
    }

    /**
     * 获取歌手详情
     */
    suspend fun getArtistDetail(id: Long): AppResult<ArtistDetailResponse> {
        return ApiClient.get("/artist/detail", mapOf("id" to id.toString()))
    }

    /**
     * 获取歌手热门歌曲
     */
    suspend fun getArtistSongs(id: Long): AppResult<ArtistSongsResponse> {
        return ApiClient.get("/artists", mapOf("id" to id.toString()))
    }

    // ==================== 歌曲 / 播放 ====================

    /**
     * 获取歌曲详情
     * @param ids 歌曲 ID，逗号分隔
     */
    suspend fun getSongDetail(ids: String): AppResult<SongDetailResponse> {
        return ApiClient.get("/song/detail", mapOf("ids" to ids))
    }

    /**
     * 获取歌曲播放 URL
     * @param id 歌曲 ID
     * @param level 音质等级（standard/higher/exhigh/lossless/hires）
     */
    suspend fun getSongUrl(id: Long, level: String = "standard"): AppResult<SongUrlResponse> {
        return ApiClient.get(
            "/song/url/v1",
            mapOf("id" to id.toString(), "level" to level)
        )
    }

    /**
     * 获取歌词
     * @param id 歌曲 ID
     */
    suspend fun getLyric(id: Long): AppResult<LyricResponse> {
        return ApiClient.get("/lyric", mapOf("id" to id.toString()))
    }

    /**
     * 获取歌曲播放 URL (v2 备用接口)
     * 部分歌曲 v1 返回空 URL 时尝试此接口
     */
    suspend fun getSongUrlV2(id: Long): AppResult<SongUrlResponse> {
        return ApiClient.get(
            "/song/url",
            mapOf("id" to id.toString())
        )
    }

    /**
     * 搜索
     * @param keywords 搜索关键词
     * @param type 搜索类型：1-单曲, 10-专辑, 100-歌手, 1000-歌单, 1018-综合
     * @param limit 返回数量
     */
    suspend fun search(
        keywords: String,
        type: Int = 1,
        limit: Int = 30
    ): AppResult<SearchResponse> {
        return ApiClient.get(
            "/search",
            mapOf(
                "keywords" to keywords,
                "type" to type.toString(),
                "limit" to limit.toString()
            )
        )
    }

    /**
     * 获取热搜列表
     * GET /search/hot
     */
    suspend fun getSearchHot(): AppResult<SearchHotResponse> {
        return ApiClient.get("/search/hot")
    }

    /**
     * 获取热搜列表（详细版）
     * GET /search/hot/detail
     * NeteaseCloudMusicApiBackup 版本支持，返回带热度的热搜数据
     */
    suspend fun getSearchHotDetail(): AppResult<SearchHotDetailResponse> {
        return ApiClient.get("/search/hot/detail")
    }

    /**
     * 搜索建议（输入联想）
     * GET /search/suggest
     * @param keywords 搜索关键词
     */
    suspend fun getSearchSuggest(keywords: String): AppResult<SearchSuggestResponse> {
        return ApiClient.get("/search/suggest", mapOf("keywords" to keywords))
    }

    /**
     * 多重匹配搜索建议
     * GET /search/multimatch
     * @param keywords 搜索关键词
     */
    suspend fun getSearchMultimatch(keywords: String): AppResult<SearchMultimatchResponse> {
        return ApiClient.get("/search/multimatch", mapOf("keywords" to keywords))
    }

    // ==================== 登录 / 注册 ====================

    /**
     * 手机号 + 密码登录
     * POST /login/cellphone
     */
    suspend fun loginCellphone(phone: String, password: String): AppResult<LoginResponse> {
        return ApiClient.get("/login/cellphone", mapOf(
            "phone" to phone,
            "password" to password
        ))
    }

    /**
     * 发送验证码
     * GET /captcha/sent
     */
    suspend fun sendCaptcha(phone: String): AppResult<CaptchaResponse> {
        return ApiClient.get("/captcha/sent", mapOf("phone" to phone))
    }

    /**
     * 验证验证码
     * GET /captcha/verify
     */
    suspend fun verifyCaptcha(phone: String, captcha: String): AppResult<CaptchaResponse> {
        return ApiClient.get("/captcha/verify", mapOf(
            "phone" to phone,
            "captcha" to captcha
        ))
    }

    /**
     * 手机号 + 验证码登录
     * POST /login/cellphone 带 captcha 参数
     */
    suspend fun loginWithCaptcha(phone: String, captcha: String): AppResult<LoginResponse> {
        return ApiClient.get("/login/cellphone", mapOf(
            "phone" to phone,
            "captcha" to captcha
        ))
    }

    /**
     * 注册账号（手机号 + 验证码 + 密码）
     * GET /register/cellphone
     * 注意：需要先通过 /captcha/sent 获取验证码
     */
    suspend fun registerCellphone(
        phone: String,
        captcha: String,
        password: String,
        nickname: String = ""
    ): AppResult<LoginResponse> {
        return ApiClient.get("/register/cellphone", mapOf(
            "phone" to phone,
            "captcha" to captcha,
            "password" to password,
            "nickname" to nickname
        ))
    }

    /**
     * 获取二维码 Key（用于二维码登录）
     * GET /login/qr/key
     */
    suspend fun getQrKey(): AppResult<QrKeyResponse> {
        return ApiClient.get("/login/qr/key")
    }

    /**
     * 生成二维码（传入 key 获取二维码 base64 图片）
     * GET /login/qr/create
     */
    suspend fun createQrCode(key: String): AppResult<QrCreateResponse> {
        return ApiClient.get("/login/qr/create", mapOf(
            "key" to key,
            "qrimg" to "true"
        ))
    }

    /**
     * 检查二维码扫描状态
     * GET /login/qr/check
     */
    suspend fun checkQrStatus(key: String): AppResult<QrCheckResponse> {
        return ApiClient.get("/login/qr/check", mapOf("key" to key))
    }

    /**
     * 获取当前登录状态
     * GET /login/status
     */
    suspend fun getLoginStatus(): AppResult<LoginStatusResponse> {
        return ApiClient.get("/login/status")
    }

    /**
     * 刷新登录状态
     * GET /login/refresh
     */
    suspend fun refreshLogin(): AppResult<LoginRefreshResponse> {
        return ApiClient.get("/login/refresh")
    }

    /**
     * 退出登录
     * GET /logout
     */
    suspend fun logout(): AppResult<LogoutResponse> {
        return ApiClient.get("/logout")
    }

    /**
     * 获取用户详情（需要登录）
     * GET /user/detail
     */
    suspend fun getUserDetail(uid: Long): AppResult<UserDetailResponse> {
        return ApiClient.get("/user/detail", mapOf("uid" to uid.toString()))
    }

    /**
     * 更新用户资料（需要登录）
     * GET /user/update
     * @param nickname 昵称
     * @param signature 签名
     * @param gender 性别：0=保密, 1=男, 2=女
     * @param birthday 生日时间戳（毫秒）
     */
    suspend fun updateUserProfile(
        nickname: String? = null,
        signature: String? = null,
        gender: Int? = null,
        birthday: Long? = null
    ): AppResult<UserUpdateResponse> {
        val params = mutableMapOf<String, String>()
        nickname?.let { params["nickname"] = it }
        signature?.let { params["signature"] = it }
        gender?.let { params["gender"] = it.toString() }
        birthday?.let { params["birthday"] = it.toString() }
        params["timestamp"] = (System.currentTimeMillis() / 1000).toString()
        // 核心：MUSIC_U 必须作为查询参数传递，仅靠 HTTP Header 不够
        val account = com.example.myapplication.manager.AccountManager
        val musicU = account.cookie
            ?.split(";")
            ?.firstOrNull { it.trim().startsWith("MUSIC_U=") }
            ?.trim()
        if (!musicU.isNullOrBlank()) {
            params["cookie"] = musicU
        }
        val csrf = account.csrfToken
        if (!csrf.isNullOrBlank()) {
            params["csrf_token"] = csrf
        }
        return ApiClient.get("/user/update", params)
    }

    /**
     * 上传头像（需要登录）
     * POST /avatar/upload（multipart/form-data）
     * @param imgData 图片字节数组
     * @param imgFileName 文件名
     */
    suspend fun uploadAvatar(
        imgData: ByteArray,
        imgFileName: String = "avatar.jpg"
    ): AppResult<AvatarUploadResponse> {
        val extraParams = mutableMapOf<String, String>()
        val account = com.example.myapplication.manager.AccountManager
        val musicU = account.cookie
            ?.split(";")
            ?.firstOrNull { it.trim().startsWith("MUSIC_U=") }
            ?.trim()
        if (!musicU.isNullOrBlank()) {
            extraParams["cookie"] = musicU
        }
        val csrf = account.csrfToken
        if (!csrf.isNullOrBlank()) {
            extraParams["csrf_token"] = csrf
        }
        return ApiClient.postMultipart(
            path = "/avatar/upload",
            fileBytes = imgData,
            fileName = imgFileName,
            formFieldName = "imgFile",
            mimeType = "image/jpeg",
            extraParams = extraParams
        )
    }

    // ==================== 用户歌单 ====================

    /**
     * 获取用户歌单（需要登录）
     * GET /user/playlist
     */
    suspend fun getUserPlaylist(uid: Long): AppResult<UserPlaylistResponse> {
        val params = mutableMapOf("uid" to uid.toString())
        val account = com.example.myapplication.manager.AccountManager
        val cookie = account.cookie
        if (!cookie.isNullOrBlank()) {
            params["cookie"] = cookie
        }
        val csrf = account.csrfToken
        if (!csrf.isNullOrBlank()) {
            params["csrf_token"] = csrf
        }
        params["timestamp"] = (System.currentTimeMillis() / 1000).toString()
        return ApiClient.get("/user/playlist", params)
    }

    /**
     * 获取用户关注列表
     * GET /user/follows
     * @param uid 用户 ID
     * @param limit 返回数量（默认 30）
     * @param offset 偏移量（用于分页）
     */
    suspend fun getUserFollows(
        uid: Long,
        limit: Int = 30,
        offset: Int = 0
    ): AppResult<FollowListResponse> {
        val params = mutableMapOf(
            "uid" to uid.toString(),
            "limit" to limit.toString(),
            "offset" to offset.toString()
        )
        // 附加 cookie/csrf 查询参数，确保需要登录态的接口能通过认证
        val account = com.example.myapplication.manager.AccountManager
        val cookie = account.cookie
        if (!cookie.isNullOrBlank()) {
            params["cookie"] = cookie
        }
        val csrf = account.csrfToken
        if (!csrf.isNullOrBlank()) {
            params["csrf_token"] = csrf
        }
        params["timestamp"] = (System.currentTimeMillis() / 1000).toString()
        return ApiClient.get("/user/follows", params)
    }

    /**
     * 获取用户粉丝列表
     * GET /user/followeds
     * @param uid 用户 ID
     * @param limit 返回数量（默认 30）
     * @param offset 偏移量（用于分页）
     */
    suspend fun getUserFolloweds(
        uid: Long,
        limit: Int = 30,
        offset: Int = 0
    ): AppResult<FollowListResponse> {
        val params = mutableMapOf(
            "uid" to uid.toString(),
            "limit" to limit.toString(),
            "offset" to offset.toString()
        )
        // 附加 cookie/csrf 查询参数，确保需要登录态的接口能通过认证
        val account = com.example.myapplication.manager.AccountManager
        val cookie = account.cookie
        if (!cookie.isNullOrBlank()) {
            params["cookie"] = cookie
        }
        val csrf = account.csrfToken
        if (!csrf.isNullOrBlank()) {
            params["csrf_token"] = csrf
        }
        params["timestamp"] = (System.currentTimeMillis() / 1000).toString()
        return ApiClient.get("/user/followeds", params)
    }

    // ==================== 心动模式 / 智能播放 ====================

    /**
     * 心动模式 / 智能播放
     * 根据歌单 ID 和当前歌曲 ID，返回智能推荐的歌曲列表
     * GET /playmode/intelligence/list
     * @param id 歌单 ID（作为推荐种子）
     * @param pid 当前播放歌曲 ID（可选，不传则为歌单第一首）
     * @param count 返回数量（默认 10）
     */
    suspend fun getIntelligenceList(
        id: Long,
        pid: Long? = null,
        count: Int = 20
    ): AppResult<IntelligenceResponse> {
        val params = mutableMapOf("id" to id.toString(), "count" to count.toString())
        pid?.let { params["pid"] = it.toString() }
        // 附加 cookie/csrf 鉴权参数，确保需要登录态的接口能通过认证
        val account = com.example.myapplication.manager.AccountManager
        val cookie = account.cookie
        if (!cookie.isNullOrBlank()) {
            params["cookie"] = cookie
        }
        val csrf = account.csrfToken
        if (!csrf.isNullOrBlank()) {
            params["csrf_token"] = csrf
        }
        params["timestamp"] = (System.currentTimeMillis() / 1000).toString()
        return ApiClient.get("/playmode/intelligence/list", params)
    }

    // ==================== 每日推荐 ====================

    /**
     * 获取每日推荐歌曲（需要登录）
     * GET /recommend/songs
     * 根据用户的听歌历史，每日推荐个性化歌曲
     */
    suspend fun getDailyRecommendSongs(): AppResult<DailyRecommendSongsResponse> {
        val params = mutableMapOf<String, String>()
        val account = com.example.myapplication.manager.AccountManager
        val cookie = account.cookie
        if (!cookie.isNullOrBlank()) {
            params["cookie"] = cookie
        }
        val csrf = account.csrfToken
        if (!csrf.isNullOrBlank()) {
            params["csrf_token"] = csrf
        }
        params["timestamp"] = (System.currentTimeMillis() / 1000).toString()
        return ApiClient.get("/recommend/songs", params)
    }

    // ==================== 私人 FM ====================

    /**
     * 获取私人 FM 推荐歌曲（需要登录）
     * GET /personal_fm
     * 返回推荐的歌曲列表，用于私人 FM 模式的自动播放
     */
    suspend fun getPersonalFm(): AppResult<PersonalFmResponse> {
        val params = mutableMapOf<String, String>()
        // 附加 cookie/csrf 查询参数，确保需要登录态的接口能通过认证
        val account = com.example.myapplication.manager.AccountManager
        val cookie = account.cookie
        if (!cookie.isNullOrBlank()) {
            params["cookie"] = cookie
        }
        val csrf = account.csrfToken
        if (!csrf.isNullOrBlank()) {
            params["csrf_token"] = csrf
        }
        params["timestamp"] = (System.currentTimeMillis() / 1000).toString()
        return ApiClient.get("/personal_fm", params)
    }

    // ==================== 收藏 / 最近播放 ====================

    /**
     * 获取用户收藏的歌曲列表（需要登录）
     * GET /likelist
     */
    suspend fun getLikedSongs(uid: Long): AppResult<LikedSongsResponse> {
        val params = mutableMapOf("uid" to uid.toString())
        // 附加 cookie/csrf 查询参数，确保需要登录态的接口能通过认证
        val account = com.example.myapplication.manager.AccountManager
        val cookie = account.cookie
        if (!cookie.isNullOrBlank()) {
            params["cookie"] = cookie
        }
        val csrf = account.csrfToken
        if (!csrf.isNullOrBlank()) {
            params["csrf_token"] = csrf
        }
        params["timestamp"] = (System.currentTimeMillis() / 1000).toString()
        return ApiClient.get("/likelist", params)
    }

    /**
     * 获取用户最近播放记录（需要登录）
     * GET /record/recent/song
     *
     * 兼容多版本 API：
     * - NeteaseCloudMusicApi V3+：/record/recent/song（Cookie 鉴权）
     * - 某些分支版本必须通过查询参数 ?cookie=... 传递凭据
     * - uid 参数为非必填，传入后可兼容 /user/record 降级
     */
    suspend fun getRecentSongs(uid: Long = 0L): AppResult<RecentSongsResponse> {
        val params = buildRecordParams(uid)
        return ApiClient.get("/record/recent/song", params)
    }

    /**
     * 获取用户听歌记录（备选端点 /user/record）
     * 某些 API 分支版本不支持 /record/recent/song，需用此端点
     * @param uid  用户 ID
     * @param type 0=最近一周, 1=所有时间
     */
    suspend fun getUserRecord(uid: Long, type: Int = 0): AppResult<RecentSongsResponse> {
        val params = buildRecordParams(uid).toMutableMap().apply {
            put("type", type.toString())
        }
        return ApiClient.get("/user/record", params)
    }

    /**
     * 构建 /record 系列接口的通用查询参数
     * 关键：cookie 必须作为查询参数传递，仅靠 HTTP Header 在部分 API 版本中不生效
     */
    private fun buildRecordParams(uid: Long): Map<String, String> {
        val params = mutableMapOf<String, String>()
        val account = com.example.myapplication.manager.AccountManager
        val cookie = account.cookie
        // 1) cookie 作为查询参数（核心修复：常见于 NeteaseCloudMusicApi 各分支版本）
        if (!cookie.isNullOrBlank()) {
            params["cookie"] = cookie
        }
        // 2) csrf_token（需要 CSRF 校验的接口要求）
        val csrf = account.csrfToken
        if (!csrf.isNullOrBlank()) {
            params["csrf_token"] = csrf
        }
        // 3) timestamp（某些部署要求，不传可能返回空）
        params["timestamp"] = (System.currentTimeMillis() / 1000).toString()
        // 4) uid
        if (uid > 0) {
            params["uid"] = uid.toString()
        }
        return params
    }

    /**
     * 喜欢/取消喜欢歌曲（需要登录）
     * GET /like
     * @param id 歌曲 ID
     * @param like true=喜欢, false=取消喜欢
     */
    suspend fun likeSong(id: Long, like: Boolean = true): AppResult<LikeSongResponse> {
        val params = mutableMapOf(
            "id" to id.toString(),
            "like" to like.toString(),
            "timestamp" to (System.currentTimeMillis() / 1000).toString()
        )
        val account = com.example.myapplication.manager.AccountManager
        // 附上 MUSIC_U（核心 session cookie），确保代理后端能通过鉴权
        val musicU = account.cookie
            ?.split(";")
            ?.firstOrNull { it.trim().startsWith("MUSIC_U=") }
            ?.trim()
        if (!musicU.isNullOrBlank()) {
            params["cookie"] = musicU
        }
        val csrf = account.csrfToken
        if (!csrf.isNullOrBlank()) {
            params["csrf_token"] = csrf
        }
        return ApiClient.get("/like", params)
    }

    /**
     * 收藏/取消收藏歌单（需要登录）
     * GET /playlist/subscribe
     * @param id 歌单 ID
     * @param t 1=收藏, 2=取消收藏
     */
    suspend fun subscribePlaylist(id: Long, t: Int = 1): AppResult<SubscribePlaylistResponse> {
        return ApiClient.get("/playlist/subscribe", buildAuthParams(id, t))
    }

    // ==================== 电台 / 播客 ====================

    /**
     * 获取推荐电台节目
     * GET /dj/recommend
     */
    suspend fun getDjRecommend(): AppResult<DjRecommendResponse> {
        return ApiClient.get("/dj/recommend")
    }

    /**
     * 获取电台节目 24 小时排行榜
     * GET /dj/program/toplist/hours
     */
    suspend fun getDjProgramToplist(limit: Int = 30): AppResult<DjProgramToplistResponse> {
        return ApiClient.get("/dj/program/toplist/hours", mapOf("limit" to limit.toString()))
    }

    /**
     * 获取今日优选电台节目
     * GET /dj/today/perfered
     * @param page 分页
     */
    suspend fun getDjTodayPerfered(page: Int = 0): AppResult<DjTodayPerferedResponse> {
        return ApiClient.get("/dj/today/perfered", mapOf("page" to page.toString()))
    }

    /**
     * 获取电台节目详情（含 mainSong 用于播放）
     * GET /dj/program/detail
     * @param id 节目 ID
     */
    suspend fun getDjProgramDetail(id: Long): AppResult<DjProgramDetailResponse> {
        return ApiClient.get("/dj/program/detail", mapOf("id" to id.toString()))
    }

    /**
     * 获取指定电台站的节目列表
     * GET /dj/program?rid={电台ID}
     * @param rid 电台 ID
     * @param limit 获取数量
     * @param offset 偏移量
     */
    suspend fun getDjRadioPrograms(
        rid: Long,
        limit: Int = 30,
        offset: Int = 0
    ): AppResult<DjRadioProgramsResponse> {
        return ApiClient.get(
            "/dj/program",
            mapOf("rid" to rid.toString(), "limit" to limit.toString(), "offset" to offset.toString())
        )
    }

    // ==================== 评论 ====================

    /**
     * 获取歌单评论
     * @param id 歌单 ID
     * @param limit 获取数量
     * @param offset 偏移量（分页）
     */
    suspend fun getPlaylistComments(
        id: Long,
        limit: Int = 20,
        offset: Int = 0
    ): AppResult<CommentResponse> {
        return ApiClient.get("/comment/playlist", mapOf(
            "id" to id.toString(),
            "limit" to limit.toString(),
            "offset" to offset.toString()
        ))
    }

    /**
     * 获取歌曲评论
     * @param id 歌曲 ID
     * @param limit 获取数量
     * @param offset 偏移量（分页）
     */
    suspend fun getSongComments(
        id: Long,
        limit: Int = 20,
        offset: Int = 0
    ): AppResult<CommentResponse> {
        return ApiClient.get("/comment/music", mapOf(
            "id" to id.toString(),
            "limit" to limit.toString(),
            "offset" to offset.toString()
        ))
    }

    /**
     * 点赞/取消点赞评论
     * @param type 评论类型：0-歌曲, 2-歌单
     * @param id 资源 ID（歌曲 ID 或歌单 ID）
     * @param cid 评论 ID
     * @param t 1=点赞, 0=取消点赞
     */
    suspend fun likeComment(
        type: Int,
        id: Long,
        cid: Long,
        t: Int = 1
    ): AppResult<LikeCommentResponse> {
        return ApiClient.get("/comment/like", buildCommentAuthParams(
            "type" to type.toString(),
            "id" to id.toString(),
            "cid" to cid.toString(),
            "t" to t.toString()
        ))
    }

    /**
     * 发送/回复评论
     * @param type 评论类型：0-歌曲, 2-歌单
     * @param id 资源 ID（歌曲 ID 或歌单 ID）
     * @param content 评论内容
     * @param commentId 回复的评论 ID（回复时传入，发新评论不传）
     */
    suspend fun sendComment(
        type: Int,
        id: Long,
        content: String,
        commentId: Long? = null
    ): AppResult<SendCommentResponse> {
        val params = mutableMapOf(
            "t" to (if (commentId != null) "2" else "1"),
            "type" to type.toString(),
            "id" to id.toString(),
            "content" to content
        )
        commentId?.let { params["commentId"] = it.toString() }
        return ApiClient.get("/comment", buildCommentAuthParams(*params.toList().toTypedArray()))
    }

    /**
     * 构建评论相关接口的认证参数
     */
    private fun buildCommentAuthParams(vararg pairs: Pair<String, String>): Map<String, String> {
        val params = mutableMapOf<String, String>()
        pairs.forEach { (key, value) -> params[key] = value }
        params["timestamp"] = (System.currentTimeMillis() / 1000).toString()
        val account = com.example.myapplication.manager.AccountManager
        val musicU = account.cookie
            ?.split(";")
            ?.firstOrNull { it.trim().startsWith("MUSIC_U=") }
            ?.trim()
        if (!musicU.isNullOrBlank()) {
            params["cookie"] = musicU
        }
        val csrf = account.csrfToken
        if (!csrf.isNullOrBlank()) {
            params["csrf_token"] = csrf
        }
        return params
    }

    /**
     * 构建需要登录态接口的通用查询参数
     * cookie 已通过 ApiClient 拦截器以 HTTP Header 传递，
     * 但 NetEaseCloudMusicApi 部分端点还需要 MUSIC_U 作为查询参数
     */
    private fun buildAuthParams(id: Long, t: Int): Map<String, String> {
        val params = mutableMapOf(
            "id" to id.toString(),
            "t" to t.toString(),
            "timestamp" to (System.currentTimeMillis() / 1000).toString()
        )
        val account = com.example.myapplication.manager.AccountManager
        // 只传 MUSIC_U（核心 session cookie），不传完整的属性指令，避免 URL 过长
        val musicU = account.cookie
            ?.split(";")
            ?.firstOrNull { it.trim().startsWith("MUSIC_U=") }
            ?.trim()
        if (!musicU.isNullOrBlank()) {
            params["cookie"] = musicU
        }
        val csrf = account.csrfToken
        if (!csrf.isNullOrBlank()) {
            params["csrf_token"] = csrf
        }
        return params
    }
}

// ==================== 数据模型 ====================

/**
 * Banner 响应
 */
data class BannerResponse(
    val code: Int,
    val banners: List<BannerItem>? = null
)

data class BannerItem(
    val imageUrl: String? = null,
    val targetId: Long = 0,
    val targetType: Int = 0,
    val titleColor: String? = null,
    val typeTitle: String? = null,
    val url: String? = null,
    @SerializedName("pic") val pic: String? = null
)

/**
 * 推荐歌单响应
 */
data class RecommendPlaylistResponse(
    val code: Int,
    val result: List<RecommendPlaylist>? = null,
    val hasTaste: Boolean = false
)

data class RecommendPlaylist(
    val id: Long = 0,
    val name: String? = null,
    val picUrl: String? = null,
    val playCount: Long = 0,
    val trackCount: Int = 0,
    val copywriter: String? = null,
    val creator: CreatorInfo? = null
)

data class CreatorInfo(
    val nickname: String? = null,
    val avatarUrl: String? = null
)

/**
 * 新歌推荐响应
 */
data class NewSongResponse(
    val code: Int,
    val result: List<NewSongItem>? = null
)

data class NewSongItem(
    val id: Long = 0,
    val name: String? = null,
    val song: SongInfo? = null,
    @SerializedName("picUrl") val picUrl: String? = null
)

/**
 * 歌单详情响应
 */
data class PlaylistDetailResponse(
    val code: Int,
    val playlist: PlaylistDetail? = null
)

data class PlaylistDetail(
    val id: Long = 0,
    val name: String? = null,
    val coverImgUrl: String? = null,
    val description: String? = null,
    val playCount: Long = 0,
    val trackCount: Int = 0,
    val creator: CreatorInfo? = null,
    val tracks: List<SongInfo>? = null,
    val tags: List<String>? = null,
    val subscribed: Boolean = false
)

data class PlaylistListResponse(
    val code: Int,
    val playlists: List<PlaylistDetail>? = null,
    val total: Int = 0
)

/**
 * 歌曲详情响应
 */
data class SongDetailResponse(
    val code: Int,
    val songs: List<SongInfo>? = null
)

data class SongInfo(
    val id: Long = 0,
    val name: String? = null,
    val duration: Long = 0,
    @SerializedName(value = "al", alternate = ["album"]) val al: AlbumInfo? = null,
    @SerializedName(value = "ar", alternate = ["artists"]) val ar: List<ArtistInfo>? = null,
    @SerializedName("dt") val dt: Long = 0
) {
    /**
     * 是否已收藏（运行时标记，不参与 JSON 序列化）
     * 由 Repository 层在 toggleFavorite / 批量查询后设置
     */
    @kotlin.jvm.Transient
    var liked: Boolean = false
    /**
     * 歌手列表是否有效（非 null 且非空）
     */
    val hasArtist: Boolean get() = !ar.isNullOrEmpty()

    /**
     * 获取主歌手名称（第一位歌手）
     * 当 ar 为 null 或空时返回 "未知歌手"
     */
    val primaryArtist: String
        get() = ar?.firstOrNull()?.name?.takeIf { it.isNotBlank() } ?: "未知歌手"

    /**
     * 获取所有歌手名称，用 " / " 分隔
     * 当 ar 为 null 或空时返回 "未知歌手"
     */
    val artistNames: String
        get() {
            val names = ar?.mapNotNull { it.name?.takeIf { n -> n.isNotBlank() } }
            return if (names.isNullOrEmpty()) "未知歌手" else names.joinToString(" / ")
        }
}

data class AlbumInfo(
    val id: Long = 0,
    val name: String? = null,
    val picUrl: String? = null
) {
    companion object {
        /** 未知专辑名称 */
        const val UNKNOWN = "未知专辑"
    }
}

data class ArtistInfo(
    val id: Long = 0,
    val name: String? = null
) {
    companion object {
        /** 未知歌手名称 */
        const val UNKNOWN = "未知歌手"
    }
}

/**
 * 歌曲 URL 响应
 */
data class SongUrlResponse(
    val code: Int,
    val data: List<SongUrlData>? = null
)

data class SongUrlData(
    val id: Long = 0,
    val url: String? = null,
    val level: String? = null,
    val time: Long = 0,
    val type: String? = null
)

/**
 * 歌词响应
 */
data class LyricResponse(
    val code: Int,
    val lrc: LyricInfo? = null,
    val tlyric: LyricInfo? = null // 翻译歌词
)

data class LyricInfo(
    val version: Int = 0,
    val lyric: String? = null
)

/**
 * 搜索响应
 */
data class SearchResponse(
    val code: Int,
    val result: SearchResult? = null
)

data class SearchResult(
    val songs: List<SongInfo>? = null,
    val songCount: Int = 0,
    val playlists: List<PlaylistDetail>? = null,
    val playlistCount: Int = 0,
    val albums: List<AlbumSearchItem>? = null,
    val albumCount: Int = 0,
    val artists: List<ArtistSearchItem>? = null,
    val artistCount: Int = 0
)

data class AlbumSearchItem(
    val id: Long = 0,
    val name: String? = null,
    val picUrl: String? = null,
    val artist: ArtistInfo? = null,
    val size: Int = 0
)

data class ArtistSearchItem(
    val id: Long = 0,
    val name: String? = null,
    val picUrl: String? = null,
    val albumSize: Int = 0,
    val mvSize: Int = 0,
    val trans: String? = null
)

/**
 * 热搜列表响应
 */
data class SearchHotResponse(
    val code: Int,
    val data: List<SearchHotItem>? = null
)

data class SearchHotItem(
    @SerializedName("searchWord") val searchWord: String? = null,
    val score: Int = 0,
    val content: String? = null,
    val iconType: Int = 0,
    val iconUrl: String? = null
)

/**
 * 热搜详情响应（/search/hot/detail）
 */
data class SearchHotDetailResponse(
    val code: Int,
    val data: List<SearchHotDetailItem>? = null
)

data class SearchHotDetailItem(
    @SerializedName("searchWord") val searchWord: String? = null,
    val score: Int = 0,
    val content: String? = null,
    val source: Int = 0,
    val iconType: Int = 0,
    val iconUrl: String? = null,
    val url: String? = null,
    val alg: String? = null
)

/**
 * 搜索建议响应
 */
data class SearchSuggestResponse(
    val code: Int,
    val result: SearchSuggestResult? = null
)

data class SearchSuggestResult(
    val songs: List<SongInfo>? = null,
    val albums: List<AlbumSearchItem>? = null,
    val artists: List<ArtistSearchItem>? = null,
    val playlists: List<PlaylistDetail>? = null,
    val order: List<String>? = null
)

/**
 * 多重匹配搜索建议响应
 */
data class SearchMultimatchResponse(
    val code: Int,
    val result: SearchMultimatchResult? = null
)

data class SearchMultimatchResult(
    val artist: List<ArtistSearchItem>? = null,
    val album: List<AlbumSearchItem>? = null,
    val ordeR: List<String>? = null
)

/**
 * 用户资料更新响应
 */
data class UserUpdateResponse(
    val code: Int,
    val message: String? = null,
    val profile: LoginUserProfile? = null
)

/**
 * 头像上传响应
 */
data class AvatarUploadResponse(
    val code: Int,
    val message: String? = null,
    val data: AvatarUploadData? = null
)

data class AvatarUploadData(
    val url: String? = null
)

// ==================== 登录 / 用户 数据模型 ====================

/**
 * 手机号登录响应
 */
data class LoginResponse(
    val code: Int,
    val message: String? = null,
    val token: String? = null,
    val cookie: String? = null,
    val account: LoginAccountInfo? = null,
    val profile: LoginUserProfile? = null,
    val bindings: List<LoginBindingInfo>? = null
)

data class LoginAccountInfo(
    val id: Long = 0,
    val userName: String? = null,
    val type: Int = 0,
    val status: Int = 0
)

data class LoginUserProfile(
    val userId: Long = 0,
    val nickname: String? = null,
    val avatarUrl: String? = null,
    val birthday: Long = 0,
    val gender: Int = 0,
    val signature: String? = null,
    val followeds: Long = 0,
    val follows: Long = 0,
    val playlistCount: Int = 0,
    val eventCount: Int = 0
)

data class LoginBindingInfo(
    val userId: Long = 0,
    val url: String? = null,
    val type: Int = 0
)

/**
 * 验证码响应
 */
data class CaptchaResponse(
    val code: Int,
    val message: String? = null,
    val data: Boolean = false
)

/**
 * 二维码 Key 响应
 */
data class QrKeyResponse(
    val code: Int,
    val data: QrKeyData? = null
)

data class QrKeyData(
    val code: Int = 0,
    val unikey: String? = null
)

/**
 * 二维码创建响应
 */
data class QrCreateResponse(
    val code: Int,
    val data: QrCreateData? = null
)

data class QrCreateData(
    val qrurl: String? = null,
    val qrimg: String? = null
)

/**
 * 二维码状态检查响应
 */
data class QrCheckResponse(
    val code: Int,
    val message: String? = null,
    val cookie: String? = null,
    val nickname: String? = null,
    val avatarUrl: String? = null
)

/**
 * 登录状态响应
 */
data class LoginStatusResponse(
    val code: Int,
    val data: LoginStatusData? = null,
    val account: LoginAccountInfo? = null,
    val profile: LoginUserProfile? = null
)

data class LoginStatusData(
    val code: Int = 0,
    val account: LoginAccountInfo? = null,
    val profile: LoginUserProfile? = null
)

/**
 * 刷新登录响应
 */
data class LoginRefreshResponse(
    val code: Int,
    val message: String? = null
)

/**
 * 退出登录响应
 */
data class LogoutResponse(
    val code: Int,
    val message: String? = null
)

/**
 * 用户详情响应
 */
data class UserDetailResponse(
    val code: Int,
    val profile: LoginUserProfile? = null,
    val level: Int = 0,
    val listenSongs: Int = 0
)

/**
 * 用户歌单响应
 */
data class UserPlaylistResponse(
    val code: Int,
    val playlist: List<UserPlaylistItem>? = null
)

data class UserPlaylistItem(
    val id: Long = 0,
    val name: String? = null,
    val coverImgUrl: String? = null,
    val trackCount: Int = 0,
    val playCount: Long = 0,
    val creator: CreatorInfo? = null,
    val subscribed: Boolean = false
)

/**
 * 收藏歌曲列表响应
 */
data class LikedSongsResponse(
    val code: Int,
    val ids: List<Long>? = null,
    val checkPoint: Long = 0
)

/**
 * 最近播放记录响应
 *
 * 兼容多种 API 版本/端点返回格式：
 * 1. /record/recent/song → { code, data: { list: [...] } }
 * 2. /user/record       → { code, allData: [...], weekData: [...] }
 */
data class RecentSongsResponse(
    val code: Int,
    val data: RecentSongsData? = null,
    // /user/record 端点返回的顶层字段（无 data 包裹）
    @SerializedName("allData")
    val allData: List<RecentSongItem>? = null,
    @SerializedName("weekData")
    val weekData: List<RecentSongItem>? = null,
    val message: String? = null
) {
    /** 兼容不同 API 版本：从多种结构中提取歌曲列表 */
    fun extractSongs(): List<SongInfo> {
        // 优先取 /record/recent/song 的 data.list
        val fromDataWrapper = data?.list
        // 再取 /user/record 的顶层 allData / weekData
        val items = fromDataWrapper
            ?: data?.weekData
            ?: data?.allData
            ?: allData
            ?: weekData
            ?: emptyList()
        return items.mapNotNull { it.data ?: it.song }
    }

    /** 检查业务码是否成功 */
    fun isBizSuccess(): Boolean = code == 200

    /** 友好的错误消息 */
    fun bizMessage(): String {
        if (isBizSuccess()) return ""
        return message.takeIf { !it.isNullOrBlank() }
            ?: when (code) {
                301 -> "需要登录"
                302 -> "无权限访问"
                else -> "请求失败 (code=$code)"
            }
    }
}

data class RecentSongsData(
    val list: List<RecentSongItem>? = null,
    val weekData: List<RecentSongItem>? = null,
    val allData: List<RecentSongItem>? = null
)

data class RecentSongItem(
    val data: SongInfo? = null,
    val song: SongInfo? = null,
    val playTime: Long = 0,
    val score: Int = 0
)

/**
 * 心动模式 / 智能播放响应
 * /playmode/intelligence/list 返回的歌曲以平铺 SongInfo 数组直接存储在 data 字段，
 * 而非嵌套在 songInfo/song/songData 包装器中。
 */
data class IntelligenceResponse(
    val code: Int,
    val data: List<SongInfo>? = null
) {
    fun extractSongs(): List<SongInfo> {
        return data ?: emptyList()
    }
}

/**
 * 每日推荐歌曲响应
 * /recommend/songs 返回 { code, data: { dailySongs: [...] } }
 */
data class DailyRecommendSongsResponse(
    val code: Int,
    val data: DailyRecommendData? = null
)

data class DailyRecommendData(
    val dailySongs: List<SongInfo>? = null
)

/**
 * 私人 FM 响应
 * /personal_fm 直接返回歌曲列表
 */
data class PersonalFmResponse(
    val code: Int,
    val data: List<SongInfo>? = null
)

/**
 * 喜欢/取消喜欢歌曲响应
 */
data class LikeSongResponse(
    val code: Int,
    val message: String? = null,
    val playlistId: Long = 0
)

data class SubscribePlaylistResponse(
    val code: Int,
    val message: String? = null
)


// ==================== 排行榜 ====================

/**
 * 所有榜单摘要响应
 */
data class ToplistDetailResponse(
    val code: Int,
    val list: List<ToplistItem>? = null,
    val artistToplist: ArtistToplist? = null
)

data class ToplistItem(
    val id: Long = 0,
    val name: String? = null,
    val coverImgUrl: String? = null,
    val description: String? = null,
    val updateFrequency: String? = null,
    val playCount: Long = 0,
    val trackCount: Int = 0,
    val tracks: List<SongInfo>? = null
)

data class ArtistToplist(
    val coverUrl: String? = null,
    val name: String? = null,
    val artists: List<TopArtistInfo>? = null
)

data class TopArtistInfo(
    val id: Long = 0,
    val name: String? = null,
    val picUrl: String? = null,
    val score: Long = 0,
    val alias: List<String>? = null
)

/**
 * 榜单歌曲列表响应
 */
data class ToplistSongResponse(
    val code: Int,
    val playlist: ToplistPlaylist? = null
)

data class ToplistPlaylist(
    val id: Long = 0,
    val name: String? = null,
    val coverImgUrl: String? = null,
    val description: String? = null,
    val playCount: Long = 0,
    val tracks: List<SongInfo>? = null
)

/**
 * 歌手排行榜响应
 */
data class TopArtistResponse(
    val code: Int,
    val list: TopArtistList? = null
)

data class TopArtistList(
    val artists: List<TopArtistInfo>? = null
)

/**
 * MV 排行榜响应
 */
data class TopMvResponse(
    val code: Int,
    val data: List<MvItem>? = null
)

data class MvItem(
    val id: Long = 0,
    val name: String? = null,
    val cover: String? = null,
    val artistName: String? = null,
    val playCount: Long = 0,
    val duration: Long = 0,
    val briefDesc: String? = null
)

/**
 * MV 播放地址响应
 */
data class MvUrlResponse(
    val code: Int,
    val data: MvUrlData? = null
)

data class MvUrlData(
    val id: Long = 0,
    val url: String? = null,
    val r: Int = 0,
    val size: Long = 0,
    val md5: String? = null
)

// ==================== 歌手详情 ====================

data class ArtistDetailResponse(
    val code: Int,
    val data: ArtistDetailData? = null
)

data class ArtistDetailData(
    val artist: ArtistInfo? = null,
    val user: ArtistUser? = null
)

data class ArtistUser(
    val userId: Long = 0
)

data class ArtistSongsResponse(
    val code: Int,
    val artist: ArtistSongsArtist? = null,
    val hotSongs: List<SongInfo>? = null
)

data class ArtistSongsArtist(
    val id: Long = 0,
    val name: String? = null,
    val picUrl: String? = null,
    val albumSize: Int = 0,
    val musicSize: Int = 0,
    val briefDesc: String? = null
)

// ==================== 评论 ====================

/**
 * 评论列表响应
 */
data class CommentResponse(
    val code: Int,
    val total: Long = 0,
    val comments: List<CommentItem>? = null,
    val hotComments: List<CommentItem>? = null,
    @com.google.gson.annotations.SerializedName("topComments")
    val topComments: List<CommentItem>? = null,
    val more: Boolean = false,
    val moreHot: Boolean = false,
    val isMusician: Boolean = false,
    val userId: Long = 0
)

/**
 * 单条评论
 */
data class CommentItem(
    val commentId: Long = 0,
    val content: String? = null,
    val time: Long = 0,
    val timeStr: String? = null,
    val likedCount: Int = 0,
    @com.google.gson.annotations.SerializedName("liked")
    private val _liked: Boolean = false,
    val commentId_parent: Long = 0,
    val user: CommentUser? = null,
    val beReplied: List<BeRepliedItem>? = null,
    val showFloorComment: ShowFloorComment? = null,
    val ipLocation: IpLocation? = null
) {
    /**
     * 是否已点赞（运行时标记，可被 ViewModel 修改）
     * 初始值来自服务端返回的 "liked" 字段
     */
    @kotlin.jvm.Transient
    var liked: Boolean = _liked
    /**
     * 格式化时间显示
     */
    val displayTime: String
        get() = timeStr ?: formatTimestamp(time)

    private fun formatTimestamp(ts: Long): String {
        if (ts <= 0) return ""
        val now = System.currentTimeMillis()
        val diff = now - ts
        return when {
            diff < 60_000 -> "刚刚"
            diff < 3_600_000 -> "${diff / 60_000}分钟前"
            diff < 86_400_000 -> "${diff / 3_600_000}小时前"
            diff < 604_800_000 -> "${diff / 86_400_000}天前"
            else -> {
                val sdf = java.text.SimpleDateFormat("yyyy年MM月dd日", java.util.Locale.CHINA)
                sdf.format(java.util.Date(ts))
            }
        }
    }
}

/**
 * 评论用户信息
 */
data class CommentUser(
    val userId: Long = 0,
    val nickname: String? = null,
    val avatarUrl: String? = null,
    val authStatus: Int = 0,
    val userType: Int = 0,
    val vipType: Int = 0,
    val expertTags: List<String>? = null,
    val experts: ExpertInfo? = null,
    val liveInfo: Any? = null
)

data class ExpertInfo(
    @com.google.gson.annotations.SerializedName("1") val tag1: String? = null,
    @com.google.gson.annotations.SerializedName("2") val tag2: String? = null
)

/**
 * 被回复的评论
 */
data class BeRepliedItem(
    val user: CommentUser? = null,
    val beRepliedCommentId: Long = 0,
    val content: String? = null,
    val ipLocation: IpLocation? = null
)

/**
 * 精彩评论楼层
 */
data class ShowFloorComment(
    val replyCount: Int = 0,
    val showReplyCount: Boolean = false
)

/**
 * IP 属地
 */
data class IpLocation(
    val location: String? = null,
    val ip: String? = null
)

/**
 * 点赞评论响应
 */
data class LikeCommentResponse(
    val code: Int,
    val message: String? = null
)

/**
 * 发送/回复评论响应
 */
data class SendCommentResponse(
    val code: Int,
    val message: String? = null,
    val comment: CommentItem? = null
)

// ==================== 数字专辑 ====================

data class NewAlbumResponse(
    val code: Int,
    val albums: List<NewAlbumItem>? = null,
    val total: Int = 0
)

data class NewAlbumItem(
    val id: Long = 0,
    val name: String? = null,
    val picUrl: String? = null,
    val artist: NewAlbumArtist? = null,
    val publishTime: Long = 0,
    val size: Int = 0,
    val company: String? = null,
    val type: String? = null
)

data class NewAlbumArtist(
    val id: Long = 0,
    val name: String? = null,
    val picUrl: String? = null
)

// ==================== 专辑详情 ====================

data class AlbumDetailResponse(
    val code: Int,
    val album: AlbumDetail? = null,
    val songs: List<SongInfo>? = null
)

data class AlbumDetail(
    val id: Long = 0,
    val name: String? = null,
    val picUrl: String? = null,
    val company: String? = null,
    val publishTime: Long = 0,
    val size: Int = 0,
    val artist: AlbumDetailArtist? = null,
    val description: String? = null
)

data class AlbumDetailArtist(
    val id: Long = 0,
    val name: String? = null,
    val picUrl: String? = null
)

// ==================== 关注 / 粉丝列表 ====================

data class FollowListResponse(
    val code: Int,
    val follow: List<FollowUserItem>? = null,
    val followeds: List<FollowUserItem>? = null,
    val more: Boolean = false,
    val size: Int = 0
) {
    /** 统一获取用户列表（关注列表用 follow，粉丝列表用 followeds） */
    val users: List<FollowUserItem>
        get() = follow ?: followeds ?: emptyList()
}

data class FollowUserItem(
    val userId: Long = 0,
    val nickname: String? = null,
    val avatarUrl: String? = null,
    val signature: String? = null,
    val gender: Int = 0,
    val followeds: Long = 0,
    val follows: Long = 0,
    val playlistCount: Int = 0,
    val eventCount: Int = 0,
    val followed: Boolean = false
)

// ==================== 电台 / 播客 ====================

/**
 * 推荐电台节目响应
 */
data class DjRecommendResponse(
    val code: Int,
    val msg: String? = null,
    val djRadios: List<DjRadioItem>? = null
)

/**
 * 电台节目 24 小时排行榜响应
 */
data class DjProgramToplistResponse(
    val code: Int,
    val data: DjProgramToplistData? = null
)

data class DjProgramToplistData(
    val list: List<DjProgramWrapper>? = null
)

/**
 * 电台站节目列表响应 (GET /dj/program?rid={id})
 */
data class DjRadioProgramsResponse(
    val code: Int,
    val programs: List<DjProgramItem>? = null,
    val more: Boolean = false
)

/**
 * 今日优选电台节目响应
 * 注意：/dj/today/perfered 返回的 data 是扁平的节目对象列表，不是 {program: {...}} 包裹的
 */
data class DjTodayPerferedResponse(
    val code: Int,
    val data: List<DjProgramItem>? = null
)

/**
 * /dj/program/toplist/hours 返回的 data.list 里每项是 {program: {...}} 包裹的
 */
data class DjProgramWrapper(
    val program: DjProgramItem? = null
)

/**
 * 电台节目
 */
data class DjProgramItem(
    val id: Long = 0,
    val name: String? = null,
    val coverUrl: String? = null,
    val description: String? = null,
    val createTime: Long = 0,
    val duration: Long = 0,
    val listenerCount: Long = 0,
    val likedCount: Long = 0,
    val commentCount: Long = 0,
    val programFeeType: Int = 0,
    val subTitle: String? = null,
    val serialNum: Int = 0,
    val dj: DjUserInfo? = null,
    val radio: DjRadioBrief? = null
)

/**
 * 电台 DJ 用户信息
 */
data class DjUserInfo(
    val id: Long = 0,
    val nickname: String? = null,
    val avatarUrl: String? = null,
    val description: String? = null,
    val gender: Int = 0,
    val signature: String? = null,
    val backgroundUrl: String? = null,
    val avatarDetail: Any? = null
)

/**
 * 电台简要信息
 */
data class DjRadioBrief(
    val id: Long = 0,
    val name: String? = null,
    val picUrl: String? = null,
    val desc: String? = null,
    val subCount: Long = 0,
    val programCount: Int = 0,
    val playCount: Long = 0,
    val category: String? = null,
    val categoryId: Int = 0,
    val dj: DjUserInfo? = null
)

/**
 * 电台节目详情响应 (GET /dj/program/detail)
 * 包含 mainSong 用于获取播放 URL
 */
data class DjProgramDetailResponse(
    val code: Int,
    val program: DjProgramDetail? = null
)

/**
 * 电台节目详情
 * API 返回的 program 包含 mainSong 等完整信息
 */
data class DjProgramDetail(
    val id: Long = 0,
    val name: String? = null,
    val coverUrl: String? = null,
    val description: String? = null,
    val duration: Long = 0,
    val listenerCount: Long = 0,
    val likedCount: Long = 0,
    val commentCount: Long = 0,
    @SerializedName("mainSong") val mainSong: SongInfo? = null,
    val dj: DjUserInfo? = null,
    val radio: DjRadioBrief? = null
)

/**
 * 电台分类
 */
data class DjRadioItem(
    val id: Long = 0,
    val name: String? = null,
    val picUrl: String? = null,
    val dj: DjUserInfo? = null,
    val rcmdText: String? = null,
    val subCount: Long = 0,
    val programCount: Int = 0,
    val playCount: Long = 0,
    val lastProgramName: String? = null,
    val feeScope: Int = 0,
    val categoryId: Int = 0,
    val desc: String? = null
)
