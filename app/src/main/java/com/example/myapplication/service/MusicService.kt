package com.example.myapplication.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaPlayer
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.bumptech.glide.Glide
import com.example.myapplication.MainActivity
import com.example.myapplication.R
import com.example.myapplication.data.Repository
import com.example.myapplication.data.remote.ApiService
import com.example.myapplication.data.remote.SongInfo
import com.example.myapplication.base.Result as AppResult
import com.example.myapplication.data.remote.SongUrlResponse
import com.example.myapplication.manager.AccountManager
import com.example.myapplication.manager.PlayMode
import com.example.myapplication.manager.PlayQueueManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.CookieHandler
import java.net.CookieManager
import java.net.HttpCookie
import java.net.URI

/**
 * 后台音乐播放服务
 * 将 MediaPlayer 从 Activity 中分离，实现后台持续播放
 * 自动切歌逻辑在 Service 内部完成，不依赖 Activity
 */
class MusicService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var currentSong: SongInfo? = null
    @Volatile
    var isPlaying = false
        private set

    /** MediaPlayer 是否已 prepare 完成，防止在非法状态调用 pause/resume */
    @Volatile
    private var prepared = false

    private val repository = Repository.getInstance()
    private val serviceScope = CoroutineScope(Dispatchers.IO)

    private fun setPlaying(playing: Boolean) {
        isPlaying = playing
    }

    /** 播放进度回调 */
    private val progressListeners = mutableListOf<OnProgressListener>()
    private val stateListeners = mutableListOf<OnPlayStateListener>()

    private val handler = Handler(Looper.getMainLooper())
    private val progressRunnable = object : Runnable {
        override fun run() {
            mediaPlayer?.let { player ->
                if (player.isPlaying) {
                    val pos = player.currentPosition
                    val dur = player.duration
                    progressListeners.forEach { it.onProgress(pos, dur) }
                }
            }
            handler.postDelayed(this, 300)
        }
    }

    inner class MusicBinder : Binder() {
        fun getService(): MusicService = this@MusicService
    }

    override fun onBind(intent: Intent?): IBinder = MusicBinder()

    /**
     * 系统级 Cookie 管理器缓存，只创建一次
     */
    private val mediaCookieManager: CookieManager by lazy { CookieManager() }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        CookieHandler.setDefault(mediaCookieManager)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.getStringExtra("action")
        when (action) {
            "pause" -> pause()
            "play" -> resume()
            "next" -> playNext()
            "prev" -> playPrevious()
        }
        return START_STICKY
    }

    /**
     * 播放指定歌曲 URL
     * 播放前将 Cookie 注入系统 CookieManager，确保 MediaPlayer 的 HTTP 请求携带鉴权信息
     */
    fun play(url: String, song: SongInfo) {
        currentSong = song
        currentSongUrl = url
        prepared = false
        try {
            mediaPlayer?.release()
            mediaPlayer = null
            injectCookiesForMediaPlayer()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(url)
                setOnPreparedListener { mp ->
                    prepared = true
                    mp.start()
                    setPlaying(true)
                    handler.post(progressRunnable)
                    notifyStateChanged()
                    startForeground(NOTIFICATION_ID, buildNotification())
                    // 记录播放历史到本地数据库
                    serviceScope.launch {
                        try { repository.addPlayHistory(song) } catch (_: Exception) { }
                    }
                }
                setOnCompletionListener { mp ->
                    onSongComplete(mp)
                }
                setOnErrorListener { _, _, _ ->
                    setPlaying(false)
                    notifyStateChanged()
                    false
                }
                prepareAsync()
            }
            // 异步加载专辑封面
            loadAlbumArtAndUpdate()
        } catch (_: Exception) { }
    }

    /**
     * 歌曲播放完毕时的处理
     * Service 内部自己加载下一首的 URL 并播放，不依赖外部 Activity
     */
    private fun onSongComplete(mp: MediaPlayer) {
        when (PlayQueueManager.playMode) {
            PlayMode.SINGLE -> {
                // 单曲循环：直接重新播放当前歌曲
                prepared = false
                mp.reset()
                val url = currentSongUrl ?: return
                injectCookiesForMediaPlayer()
                mp.setDataSource(url)
                mp.setOnPreparedListener { p ->
                    prepared = true
                    p.start()
                    notifyStateChanged()
                }
                mp.prepareAsync()
            }
            PlayMode.HEARTBEAT -> {
                // 心动模式：先从队列取下一首，队列播完则调用 API 获取更多推荐
                val next = PlayQueueManager.next()
                if (next != null) {
                    notifySongChange(next)
                    loadAndPlayNext(next)
                } else {
                    // 队列已空，调用智能推荐 API 获取更多歌曲
                    fetchHeartbeatSongs()
                }
            }
            PlayMode.FM -> {
                // 私人 FM：先从队列取下一首，队列播完则调用 API 获取更多推荐
                val next = PlayQueueManager.next()
                if (next != null) {
                    notifySongChange(next)
                    loadAndPlayNext(next)
                } else {
                    // 队列已空，调用私人 FM API 获取更多歌曲
                    fetchFmSongs()
                }
            }
            else -> {
                val next = PlayQueueManager.next()
                if (next != null) {
                    // 通知监听器（PlayerActivity / MiniPlayer 等）歌曲已切换
                    notifySongChange(next)
                    // Service 自己加载 URL 并播放
                    loadAndPlayNext(next)
                } else {
                    // 顺序播放：播完最后一首停止
                    setPlaying(false)
                    notifyStateChanged()
                    stopForeground(STOP_FOREGROUND_DETACH)
                    stopSelf()
                }
            }
        }
    }

    /**
     * 心动模式下获取更多推荐歌曲
     * 推荐链路：AI 智能推荐 → 每日推荐 → 私人 FM，确保推荐新歌而非循环喜欢歌单
     */
    private fun fetchHeartbeatSongs() {
        val playlistId = PlayQueueManager.heartbeatPlaylistId
        val currentSongId = currentSong?.id ?: 0L
        if (playlistId <= 0 || currentSongId <= 0) {
            handler.post {
                setPlaying(false)
                notifyStateChanged()
                stopForeground(STOP_FOREGROUND_DETACH)
                stopSelf()
            }
            return
        }
        serviceScope.launch {
            try {
                val newSongs = fetchHeartbeatRecommendations(playlistId, currentSongId)
                if (newSongs.isNotEmpty()) {
                    PlayQueueManager.addAllToQueue(newSongs)
                    handler.post {
                        val next = PlayQueueManager.next()
                        if (next != null) {
                            notifySongChange(next)
                            loadAndPlayNext(next)
                        }
                    }
                    return@launch
                }
                handler.post {
                    setPlaying(false)
                    notifyStateChanged()
                    stopForeground(STOP_FOREGROUND_DETACH)
                    stopSelf()
                }
            } catch (_: Exception) {
                kotlinx.coroutines.delay(3000)
                handler.post {
                    val retry = PlayQueueManager.next()
                    if (retry != null) {
                        notifySongChange(retry)
                        loadAndPlayNext(retry)
                    } else {
                        fetchHeartbeatSongsRetry(playlistId, currentSongId)
                    }
                }
            }
        }
    }

    /**
     * 心动模式推荐链路
     * ① AI 智能推荐（基于喜欢歌单 + 当前歌曲）
     * ② 每日推荐（基于用户听歌历史）
     * ③ 私人 FM（个性化流式推荐）
     */
    private suspend fun fetchHeartbeatRecommendations(playlistId: Long, songId: Long): List<SongInfo> {
        // ① AI 智能推荐 —— 基于喜欢歌单 + 当前歌曲种子
        val intelligence = ApiService.getIntelligenceList(id = playlistId, pid = songId, count = 20)
        if (intelligence is AppResult.Success) {
            val songs = intelligence.data.extractSongs()
            if (songs.isNotEmpty()) return songs
        }

        // ② 每日推荐 —— 基于用户听歌历史
        val daily = ApiService.getDailyRecommendSongs()
        if (daily is AppResult.Success) {
            val songs = daily.data.data?.dailySongs ?: emptyList()
            if (songs.isNotEmpty()) return songs.take(20)
        }

        // ③ 私人 FM —— 个性化流式推荐
        val fm = ApiService.getPersonalFm()
        if (fm is AppResult.Success) {
            val songs = fm.data.data ?: emptyList()
            if (songs.isNotEmpty()) return songs.take(20)
        }

        return emptyList()
    }

    /**
     * 心动模式重试（仅重试一次，避免无限循环）
     */
    private fun fetchHeartbeatSongsRetry(playlistId: Long, songId: Long) {
        serviceScope.launch {
            try {
                val newSongs = fetchHeartbeatRecommendations(playlistId, songId)
                if (newSongs.isNotEmpty()) {
                    PlayQueueManager.addAllToQueue(newSongs)
                    handler.post {
                        val next = PlayQueueManager.next()
                        if (next != null) {
                            notifySongChange(next)
                            loadAndPlayNext(next)
                        }
                    }
                    return@launch
                }
            } catch (_: Exception) { }
            handler.post {
                setPlaying(false)
                notifyStateChanged()
                stopForeground(STOP_FOREGROUND_DETACH)
                stopSelf()
            }
        }
    }

    /**
     * 私人 FM：获取更多推荐歌曲
     * 调用 /personal_fm API 获取推荐，追加到队列并继续播放
     */
    private fun fetchFmSongs() {
        serviceScope.launch {
            try {
                val result = ApiService.getPersonalFm()
                if (result is AppResult.Success) {
                    val newSongs = result.data.data
                    if (!newSongs.isNullOrEmpty()) {
                        // 追加到队列并播放第一首
                        PlayQueueManager.addAllToQueue(newSongs)
                        handler.post {
                            val next = PlayQueueManager.next()
                            if (next != null) {
                                notifySongChange(next)
                                loadAndPlayNext(next)
                            }
                        }
                        return@launch
                    }
                }
                // API 返回空或无新推荐，重试一次
                handler.post { fetchFmSongsRetry() }
            } catch (_: Exception) {
                // 网络异常，等待 3 秒后重试
                kotlinx.coroutines.delay(3000)
                handler.post {
                    val retry = PlayQueueManager.next()
                    if (retry != null) {
                        notifySongChange(retry)
                        loadAndPlayNext(retry)
                    } else {
                        fetchFmSongsRetry()
                    }
                }
            }
        }
    }

    /**
     * 私人 FM 重试
     */
    private fun fetchFmSongsRetry() {
        serviceScope.launch {
            try {
                val result = ApiService.getPersonalFm()
                if (result is AppResult.Success) {
                    val newSongs = result.data.data
                    if (!newSongs.isNullOrEmpty()) {
                        PlayQueueManager.addAllToQueue(newSongs)
                        handler.post {
                            val next = PlayQueueManager.next()
                            if (next != null) {
                                notifySongChange(next)
                                loadAndPlayNext(next)
                            }
                        }
                        return@launch
                    }
                }
            } catch (_: Exception) { }
            // 两次都失败，停止播放
            handler.post {
                setPlaying(false)
                notifyStateChanged()
                stopForeground(STOP_FOREGROUND_DETACH)
                stopSelf()
            }
        }
    }

    /** 缓存当前正在播放的歌曲 URL，用于单曲循环时复用 */
    @Volatile
    private var currentSongUrl: String? = null

    /** 缓存专辑封面 Bitmap，用于通知栏展示及 pause/resume 时复用 */
    @Volatile
    private var cachedAlbumArt: Bitmap? = null

    /**
     * 加载下一首歌曲的 URL 并播放
     * 不依赖外部 Activity，Service 自己完成网络请求
     */
    private fun loadAndPlayNext(song: SongInfo) {
        currentSong = song
        prepared = false
        serviceScope.launch {
            try {
                val url = fetchSongUrl(song.id)
                if (!url.isNullOrEmpty()) {
                    currentSongUrl = url
                    handler.post {
                        try {
                            mediaPlayer?.release()
                            injectCookiesForMediaPlayer()
                            mediaPlayer = MediaPlayer().apply {
                                setDataSource(url)
                                setOnPreparedListener { mp ->
                                    prepared = true
                                    mp.start()
                                    setPlaying(true)
                                    handler.post(progressRunnable)
                                    notifyStateChanged()
                                    startForeground(NOTIFICATION_ID, buildNotification())
                                    // 记录播放历史到本地数据库
                                    serviceScope.launch {
                                        try { repository.addPlayHistory(song) } catch (_: Exception) { }
                                    }
                                }
                                setOnCompletionListener { mp ->
                                    onSongComplete(mp)
                                }
                                setOnErrorListener { _, _, _ ->
                                    setPlaying(false)
                                    notifyStateChanged()
                                    false
                                }
                                prepareAsync()
                            }
                            // 异步加载专辑封面
                            loadAlbumArtAndUpdate()
                        } catch (_: Exception) { }
                    }
                } else {
                    // URL 获取失败，尝试下一首
                    handler.post {
                        val next = PlayQueueManager.next()
                        if (next != null) {
                            notifySongChange(next)
                            loadAndPlayNext(next)
                        }
                    }
                }
            } catch (_: Exception) {
                handler.post {
                    // 网络异常，尝试下一首
                    val next = PlayQueueManager.next()
                    if (next != null) {
                        notifySongChange(next)
                        loadAndPlayNext(next)
                    }
                }
            }
        }
    }

    /**
     * 获取歌曲播放 URL（多级兜底）
     * standard → exhigh → v2备用 → lossless
     */
    private suspend fun fetchSongUrl(songId: Long): String? {
        // 第1层：标准音质
        var resp = ApiService.getSongUrl(songId, "standard")
        if (resp is AppResult.Success) {
            val url = resp.data.data?.firstOrNull()?.url
            if (!url.isNullOrEmpty()) return url
        }
        // 第2层：高品质
        resp = ApiService.getSongUrl(songId, "exhigh")
        if (resp is AppResult.Success) {
            val url = resp.data.data?.firstOrNull()?.url
            if (!url.isNullOrEmpty()) return url
        }
        // 第3层：v2 备用接口
        val v2Resp = repository.getSongUrlV2(songId)
        if (v2Resp is AppResult.Success) {
            val url = v2Resp.data.data?.firstOrNull()?.url
            if (!url.isNullOrEmpty()) return url
        }
        // 第4层：无损音质
        resp = ApiService.getSongUrl(songId, "lossless")
        if (resp is AppResult.Success) {
            val url = resp.data.data?.firstOrNull()?.url
            if (!url.isNullOrEmpty()) return url
        }
        return null
    }

    /**
     * 将登录 Cookie 注入系统 CookieManager
     * Android MediaPlayer 的 HTTP 请求使用 java.net.CookieManager，
     * 而 App 的 OkHttp 使用独立的 Cookie 头，两者不互通。
     * 必须同步 Cookie，否则 MediaPlayer 请求音频流时缺乏鉴权信息导致播放失败。
     */
    /**
     * Cookie 属性指令（不是独立的 cookie，需跳过）
     * 登录响应 Set-Cookie 可能附带 Max-Age、Expires、Path、Domain 等属性
     */
    private val cookieAttrIgnore = setOf(
        "max-age", "expires", "path", "domain", "secure", "httponly", "samesite",
        "version", "comment", "commenturl", "port", "discard"
    )

    private fun injectCookiesForMediaPlayer() {
        val cookie = AccountManager.cookie ?: return
        try {
            val store = mediaCookieManager.cookieStore
            val uri = URI("https://music.163.com")
            var count = 0
            cookie.split(";").forEach { pair ->
                val eq = pair.indexOf('=')
                if (eq <= 0) return@forEach
                val name = pair.substring(0, eq).trim()
                // 跳过 cookie 属性指令（如 Max-Age、Expires 等）
                if (name.lowercase() in cookieAttrIgnore) return@forEach
                val value = pair.substring(eq + 1).trim()
                try {
                    val httpCookie = HttpCookie(name, value).apply {
                        domain = ".163.com"
                        path = "/"
                        maxAge = -1
                    }
                    store.add(uri, httpCookie)
                    count++
                } catch (_: IllegalArgumentException) {
                    // 名称或值含非法字符，跳过
                }
            }
            Log.d("MusicService", "Cookie injected for MediaPlayer, $count cookies")
        } catch (e: Exception) {
            Log.e("MusicService", "Failed to inject cookie: ${e.message}")
        }
    }

    fun pause() {
        if (!prepared) return
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
            }
        }
        setPlaying(false)
        notifyStateChanged()
        updateNotification()
    }

    fun resume() {
        if (!prepared) return
        mediaPlayer?.let {
            if (!it.isPlaying) {
                it.start()
            }
        }
        setPlaying(true)
        handler.post(progressRunnable)
        notifyStateChanged()
        updateNotification()
    }

    fun togglePlayPause() {
        if (isPlaying) pause() else resume()
    }

    fun seekTo(position: Int) {
        mediaPlayer?.seekTo(position)
    }

    fun playNext() {
        val song = PlayQueueManager.next()
        if (song != null) {
            notifySongChange(song)
            loadAndPlayNext(song)
        } else if (PlayQueueManager.playMode == PlayMode.HEARTBEAT) {
            // 心动模式队列已空，拉取更多推荐
            fetchHeartbeatSongs()
        }
    }

    fun playPrevious() {
        val song = PlayQueueManager.previous()
        if (song != null) {
            notifySongChange(song)
            loadAndPlayNext(song)
        }
    }

    fun getCurrentSong(): SongInfo? = currentSong
    fun getCurrentPosition(): Int = mediaPlayer?.currentPosition ?: 0
    fun getDuration(): Int = mediaPlayer?.duration ?: 0

    fun addProgressListener(listener: OnProgressListener) {
        progressListeners.add(listener)
    }

    fun removeProgressListener(listener: OnProgressListener) {
        progressListeners.remove(listener)
    }

    fun addPlayStateListener(listener: OnPlayStateListener) {
        stateListeners.add(listener)
    }

    fun removePlayStateListener(listener: OnPlayStateListener) {
        stateListeners.remove(listener)
    }

    interface OnProgressListener {
        fun onProgress(currentPosition: Int, duration: Int)
    }

    interface OnPlayStateListener {
        fun onPlayStateChanged(isPlaying: Boolean, song: SongInfo?)
        fun onSongChanged(song: SongInfo)
    }

    private fun notifyStateChanged() {
        stateListeners.forEach { it.onPlayStateChanged(isPlaying, currentSong) }
    }

    private fun notifySongChange(song: SongInfo) {
        stateListeners.forEach { it.onSongChanged(song) }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "音乐播放", NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "音乐播放控制通知"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(albumArt: Bitmap? = cachedAlbumArt): android.app.Notification {
        val playPauseAction = if (isPlaying) {
            NotificationCompat.Action(
                android.R.drawable.ic_media_pause, "暂停",
                createActionPendingIntent("pause")
            )
        } else {
            NotificationCompat.Action(
                android.R.drawable.ic_media_play, "播放",
                createActionPendingIntent("play")
            )
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_music_note)
            .setContentTitle(currentSong?.name ?: "未知歌曲")
            .setContentText(currentSong?.artistNames ?: "未知歌手")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0,
                    Intent(this, MainActivity::class.java).apply {
                        putExtra("open_player", true)
                        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                    },
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            .addAction(android.R.drawable.ic_media_previous, "上一首", createActionPendingIntent("prev"))
            .addAction(playPauseAction)
            .addAction(android.R.drawable.ic_media_next, "下一首", createActionPendingIntent("next"))
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setShowActionsInCompactView(0, 1, 2)
            )

        // 设置专辑封面大图
        albumArt?.let { builder.setLargeIcon(it) }

        return builder.build()
    }

    private fun createActionPendingIntent(action: String): PendingIntent {
        return PendingIntent.getService(
            this, action.hashCode(),
            Intent(this, MusicService::class.java).putExtra("action", action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    /**
     * 异步加载专辑封面并更新通知栏大图
     */
    private fun loadAlbumArtAndUpdate() {
        val picUrl = currentSong?.al?.picUrl?.takeIf { it.isNotBlank() } ?: return
        cachedAlbumArt = null
        serviceScope.launch {
            try {
                val bitmap = Glide.with(this@MusicService)
                    .asBitmap()
                    .load(picUrl)
                    .submit(256, 256)
                    .get()
                cachedAlbumArt = bitmap
                handler.post {
                    val notification = buildNotification(albumArt = bitmap)
                    val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    manager.notify(NOTIFICATION_ID, notification)
                }
            } catch (_: Exception) {
                // 图片加载失败，不影响播放
            }
        }
    }

    private fun updateNotification() {
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onDestroy() {
        handler.removeCallbacks(progressRunnable)
        mediaPlayer?.release()
        mediaPlayer = null
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "music_playback_channel"
        const val NOTIFICATION_ID = 1
    }
}
