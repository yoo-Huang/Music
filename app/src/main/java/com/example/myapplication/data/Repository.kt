package com.example.myapplication.data

import com.example.myapplication.data.local.AppDatabase
import com.example.myapplication.data.local.entity.FavoriteSongEntity
import com.example.myapplication.data.local.entity.PlayHistoryEntity
import com.example.myapplication.data.remote.ApiService
import com.example.myapplication.data.remote.SongInfo
import com.example.myapplication.manager.FavoriteManager
import kotlinx.coroutines.flow.Flow

/**
 * 数据仓库层
 * 统一管理网络请求和本地数据库操作
 * 作为 ViewModel 和底层数据源之间的桥梁
 */
class Repository {

    // ==================== 网络数据 ====================

    /** 获取 Banner */
    suspend fun getBanners() = ApiService.getBanners()

    /** 获取推荐歌单 */
    suspend fun getRecommendPlaylists(limit: Int = 20) =
        ApiService.getRecommendPlaylists(limit)

    /** 获取歌单详情 */
    suspend fun getPlaylistDetail(id: Long) = ApiService.getPlaylistDetail(id)

    /** 获取精品歌单 */
    suspend fun getHighQualityPlaylists(limit: Int = 20, before: Long? = null) =
        ApiService.getHighQualityPlaylists(limit, before)

    /** 获取歌曲详情 */
    suspend fun getSongDetail(ids: String) = ApiService.getSongDetail(ids)

    /** 获取歌曲播放 URL (v1) */
    suspend fun getSongUrl(id: Long) = ApiService.getSongUrl(id)

    /** 获取歌曲播放 URL (v2 备用) */
    suspend fun getSongUrlV2(id: Long) = ApiService.getSongUrlV2(id)

    /** 获取歌词 */
    suspend fun getLyric(id: Long) = ApiService.getLyric(id)

    /** 搜索 */
    suspend fun search(keywords: String, type: Int = 1) = ApiService.search(keywords, type)

    /** 获取热搜列表 */
    suspend fun getSearchHot() = ApiService.getSearchHot()

    /** 获取热搜详情列表（带热度值） */
    suspend fun getSearchHotDetail() = ApiService.getSearchHotDetail()

    /** 搜索建议 */
    suspend fun getSearchSuggest(keywords: String) = ApiService.getSearchSuggest(keywords)

    /** 多重匹配搜索 */
    suspend fun getSearchMultimatch(keywords: String) = ApiService.getSearchMultimatch(keywords)

    /** 更新用户资料 */
    suspend fun updateUserProfile(
        nickname: String? = null,
        signature: String? = null,
        gender: Int? = null,
        birthday: Long? = null
    ) = ApiService.updateUserProfile(nickname, signature, gender, birthday)

    /** 获取推荐新歌 */
    suspend fun getNewSongs(limit: Int = 10) = ApiService.getNewSongs(limit)

    /** 获取每日推荐歌曲（需登录） */
    suspend fun getDailyRecommendSongs() = ApiService.getDailyRecommendSongs()

    // ==================== 排行榜 ====================

    /** 获取所有榜单摘要 */
    suspend fun getToplistDetail() = ApiService.getToplistDetail()

    /** 获取榜单歌曲列表 */
    suspend fun getToplist(id: Long) = ApiService.getToplist(id)

    /** 获取歌手排行榜 */
    suspend fun getTopArtists(type: Int = 1) = ApiService.getTopArtists(type)

    /** 获取 MV 排行榜 */
    suspend fun getTopMv(limit: Int = 30) = ApiService.getTopMv(limit)

    /** 获取新专辑（数字专辑榜） */
    suspend fun getNewAlbums() = ApiService.getNewAlbums()

    /** 获取专辑详情 */
    suspend fun getAlbumDetail(id: Long) = ApiService.getAlbumDetail(id)

    /** 获取用户关注列表 */
    suspend fun getUserFollows(uid: Long, limit: Int = 30, offset: Int = 0) =
        ApiService.getUserFollows(uid, limit, offset)

    /** 获取用户粉丝列表 */
    suspend fun getUserFolloweds(uid: Long, limit: Int = 30, offset: Int = 0) =
        ApiService.getUserFolloweds(uid, limit, offset)

    /** 获取 MV 播放地址 */
    suspend fun getMvUrl(id: Long, r: Int = 720) = ApiService.getMvUrl(id, r)

    // ==================== 电台 / 播客 ====================

    /** 获取推荐电台节目 */
    suspend fun getDjRecommend() = ApiService.getDjRecommend()

    /** 获取电台节目 24 小时排行榜 */
    suspend fun getDjProgramToplist(limit: Int = 30) = ApiService.getDjProgramToplist(limit)

    /** 获取今日优选电台节目 */
    suspend fun getDjTodayPerfered(page: Int = 0) = ApiService.getDjTodayPerfered(page)

    /** 获取电台节目详情（含 mainSong 用于获取播放 URL） */
    suspend fun getDjProgramDetail(id: Long) = ApiService.getDjProgramDetail(id)

    /** 获取指定电台站的节目列表 */
    suspend fun getDjRadioPrograms(rid: Long, limit: Int = 30, offset: Int = 0) =
        ApiService.getDjRadioPrograms(rid, limit, offset)

    // ==================== 歌手详情 ====================
    suspend fun getArtistDetail(id: Long) = ApiService.getArtistDetail(id)

    /** 获取歌手热门歌曲 */
    suspend fun getArtistSongs(id: Long) = ApiService.getArtistSongs(id)

    // ==================== 评论 ====================

    /** 获取歌单评论 */
    suspend fun getPlaylistComments(id: Long, limit: Int = 20, offset: Int = 0) =
        ApiService.getPlaylistComments(id, limit, offset)

    /** 获取歌曲评论 */
    suspend fun getSongComments(id: Long, limit: Int = 20, offset: Int = 0) =
        ApiService.getSongComments(id, limit, offset)

    /** 点赞/取消点赞评论 */
    suspend fun likeComment(type: Int, id: Long, cid: Long, t: Int = 1): Boolean {
        val result = ApiService.likeComment(type, id, cid, t)
        return when (result) {
            is com.example.myapplication.base.Result.Success -> result.data.code == 200
            is com.example.myapplication.base.Result.Error -> throw result.exception
        }
    }

    /** 发送/回复评论 */
    suspend fun sendComment(
        type: Int,
        id: Long,
        content: String,
        commentId: Long? = null
    ): Boolean {
        val result = ApiService.sendComment(type, id, content, commentId)
        return when (result) {
            is com.example.myapplication.base.Result.Success -> {
                if (result.data.code != 200) {
                    throw com.example.myapplication.base.AppException.ServerException(
                        code = result.data.code,
                        message = result.data.message ?: "评论失败"
                    )
                }
                true
            }
            is com.example.myapplication.base.Result.Error -> throw result.exception
        }
    }

    // ==================== 本地数据库 ====================

    /**
     * 获取收藏歌曲列表（Flow 响应式）
     */
    fun getFavoriteSongs(): Flow<List<FavoriteSongEntity>> {
        return AppDatabase.getInstance(com.example.myapplication.MyApp.instance)
            .favoriteSongDao()
            .getAllFavorites()
    }

    /**
     * 检查歌曲是否已收藏
     */
    suspend fun isFavorite(songId: Long): Boolean {
        return AppDatabase.getInstance(com.example.myapplication.MyApp.instance)
            .favoriteSongDao()
            .isFavorite(songId)
    }

    /**
     * 批量标记歌曲的收藏状态
     * 优先使用全局内存集合（O(1)），未加载时回退到本地 DB 查询
     * @param songs 歌曲列表，会原地修改 liked 字段
     */
    suspend fun markLikedStatus(songs: List<SongInfo>) {
        if (songs.isEmpty()) return
        if (FavoriteManager.isLoaded) {
            // 全局集合已加载，直接内存查询，无需 DB
            songs.forEach { song -> song.liked = FavoriteManager.isLiked(song.id) }
        } else {
            // 集合未加载（网络未完成），回退到本地 DB
            val dao = AppDatabase.getInstance(com.example.myapplication.MyApp.instance)
                .favoriteSongDao()
            songs.forEach { song -> song.liked = dao.isFavorite(song.id) }
        }
    }

    /**
     * 切换收藏状态（同步服务端 + 本地数据库 + 全局集合）
     * @return 切换后的收藏状态（true=已收藏, false=未收藏）
     * @throws AppException 服务端同步失败时抛出
     */
    suspend fun toggleFavorite(song: SongInfo): Boolean {
        val dao = AppDatabase.getInstance(com.example.myapplication.MyApp.instance)
            .favoriteSongDao()

        // 1. 获取当前状态：优先全局集合，未加载时回退 DB
        val currentFavorite = if (FavoriteManager.isLoaded) {
            FavoriteManager.isLiked(song.id)
        } else {
            dao.isFavorite(song.id)
        }
        val nowFavorite = !currentFavorite

        // 2. 同步服务端（必须成功，否则抛异常）
        val result = try {
            ApiService.likeSong(song.id, nowFavorite)
        } catch (e: Exception) {
            android.util.Log.e("Repository", "likeSong exception: ${e.message}", e)
            throw com.example.myapplication.base.AppException.NetworkException(
                message = "网络异常，操作失败",
                cause = e
            )
        }

        // 检查 API 响应
        if (result !is com.example.myapplication.base.Result.Success) {
            throw com.example.myapplication.base.AppException.ServerException(
                code = -1,
                message = "服务器无响应，请稍后重试"
            )
        }
        if (result.data.code != 200) {
            val msg = result.data.message ?: "未知错误"
            android.util.Log.e("Repository", "likeSong API returned code=${result.data.code}: $msg")
            throw com.example.myapplication.base.AppException.ServerException(
                code = result.data.code,
                message = "操作失败: $msg"
            )
        }

        // 3-5. API 成功 → 更新本地状态
        FavoriteManager.setLiked(song.id, nowFavorite)

        if (currentFavorite) {
            dao.removeFavoriteById(song.id)
        } else {
            dao.addFavorite(
                FavoriteSongEntity(
                    songId = song.id,
                    songName = song.name ?: "未知歌曲",
                    artistName = song.artistNames.replace(" / ", "/"),
                    albumName = song.al?.name,
                    albumPicUrl = song.al?.picUrl,
                    duration = song.dt
                )
            )
        }

        song.liked = nowFavorite
        return nowFavorite
    }

    /**
     * 切换歌单收藏状态
     * @param playlistId 歌单 ID
     * @param subscribe true=收藏, false=取消收藏
     * @return 是否成功
     */
    suspend fun togglePlaylistSubscribe(playlistId: Long, subscribe: Boolean): Boolean {
        val t = if (subscribe) 1 else 2
        val result = ApiService.subscribePlaylist(playlistId, t)
        when (result) {
            is com.example.myapplication.base.Result.Success -> {
                if (result.data.code != 200) {
                    throw com.example.myapplication.base.AppException.ServerException(
                        code = result.data.code,
                        message = result.data.message ?: "操作失败"
                    )
                }
                return true
            }
            is com.example.myapplication.base.Result.Error -> throw result.exception
        }
    }

    /**
     * 添加播放历史
     */
    suspend fun addPlayHistory(song: SongInfo) {
        AppDatabase.getInstance(com.example.myapplication.MyApp.instance)
            .playHistoryDao()
            .insertHistory(
                PlayHistoryEntity(
                    songId = song.id,
                    songName = song.name ?: "未知歌曲",
                    artistName = song.artistNames.replace(" / ", "/"),
                    albumPicUrl = song.al?.picUrl,
                    duration = song.dt
                )
            )
    }

    /**
     * 获取播放历史（Flow）
     */
    fun getPlayHistory(): Flow<List<PlayHistoryEntity>> {
        return AppDatabase.getInstance(com.example.myapplication.MyApp.instance)
            .playHistoryDao()
            .getPlayHistory()
    }

    companion object {
        @Volatile
        private var INSTANCE: Repository? = null

        fun getInstance(): Repository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Repository().also { INSTANCE = it }
            }
        }
    }
}
