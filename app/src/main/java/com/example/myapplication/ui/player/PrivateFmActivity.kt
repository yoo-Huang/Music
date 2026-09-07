package com.example.myapplication.ui.player

import android.animation.ObjectAnimator
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.SeekBar
import com.example.myapplication.R
import com.example.myapplication.base.BaseActivity
import com.example.myapplication.base.Result as AppResult
import com.example.myapplication.data.remote.ApiService
import com.example.myapplication.data.remote.SongInfo
import com.example.myapplication.databinding.ActivityPrivateFmBinding
import com.example.myapplication.manager.AccountManager
import com.example.myapplication.manager.PlayMode
import com.example.myapplication.manager.PlayQueueManager
import com.example.myapplication.service.MusicService
import com.example.myapplication.util.loadImageCircle
import com.example.myapplication.util.showToast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 私人 FM 页面
 *
 * 网易云风味的私人电台，根据用户口味无限推荐歌曲。
 * 核心体验：
 * - 唱片旋转动画
 * - 播放/暂停控制
 * - 垃圾桶：跳过当前歌曲，减分推荐
 * - 红心：收藏当前歌曲
 * - 自动播放下一首（队列空时自动从 API 获取）
 * - 后台服务持续播放，离开页面也不中断
 */
class PrivateFmActivity : BaseActivity<ActivityPrivateFmBinding>() {

    override fun initBinding(): ActivityPrivateFmBinding =
        ActivityPrivateFmBinding.inflate(layoutInflater)

    override val enableMiniPlayer: Boolean = false

    private var musicService: MusicService? = null
    private var bound = false

    /** 唱片旋转动画 */
    private var discRotation: ObjectAnimator? = null

    /** 当前进度更新 */
    private val handler = Handler(Looper.getMainLooper())

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MusicService.MusicBinder
            musicService = binder.getService()
            bound = true
            musicService?.addProgressListener(progressListener)
            musicService?.addPlayStateListener(playStateListener)
            // 如果已经在播放则同步状态
            updatePlayPauseIcon()
            updateSongInfo()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            musicService?.removeProgressListener(progressListener)
            musicService?.removePlayStateListener(playStateListener)
            musicService = null
            bound = false
        }
    }

    private val progressListener = object : MusicService.OnProgressListener {
        override fun onProgress(currentPosition: Int, duration: Int) {
            if (duration > 0) {
                binding.sbProgress.max = duration
                binding.sbProgress.progress = currentPosition
                binding.tvCurrentTime.text = formatTime(currentPosition)
                binding.tvTotalTime.text = formatTime(duration)
            }
        }
    }

    private val playStateListener = object : MusicService.OnPlayStateListener {
        override fun onPlayStateChanged(isPlaying: Boolean, song: SongInfo?) {
            updatePlayPauseIcon()
            if (isPlaying) startDiscRotation() else pauseDiscRotation()
        }

        override fun onSongChanged(song: SongInfo) {
            updateSongInfo()
        }
    }

    // ==================== 生命周期 ====================

    override fun initView() {
        bindService(
            Intent(this, MusicService::class.java),
            serviceConnection,
            Context.BIND_AUTO_CREATE
        )

        binding.ivBack.setOnClickListener { finish() }

        // 播放/暂停
        binding.ivPlayPause.setOnClickListener {
            if (PlayQueueManager.playMode != PlayMode.FM) return@setOnClickListener
            musicService?.togglePlayPause()
        }

        // 垃圾桶（跳过当前歌曲）
        binding.ivTrash.setOnClickListener {
            if (PlayQueueManager.playMode != PlayMode.FM) return@setOnClickListener
            skipCurrent()
        }

        // 红心（收藏）
        binding.ivLike.setOnClickListener {
            toggleLike()
        }

        // 进度条拖动
        binding.sbProgress.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser && bound) {
                    musicService?.seekTo(progress)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    override fun initData() {
        // 检查登录状态
        if (!AccountManager.isLoggedIn) {
            showToast("请先登录")
            finish()
            return
        }
        // 启动 FM
        startFmMode()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        stopDiscRotation()
        if (bound) {
            musicService?.removeProgressListener(progressListener)
            musicService?.removePlayStateListener(playStateListener)
            unbindService(serviceConnection)
            bound = false
        }
        super.onDestroy()
    }

    // ==================== FM 核心逻辑 ====================

    /**
     * 启动 FM 模式：
     * 1. 切换到 FM 播放模式
     * 2. 调用 /personal_fm 获取推荐歌曲
     * 3. 放入队列并开始播放第一首
     */
    private fun startFmMode() {
        PlayQueueManager.setPlayMode(PlayMode.FM)
        binding.tvFmTitle.text = "私人FM · 加载中..."
        binding.tvSongTitle.text = "正在为你推荐..."
        binding.tvArtist.text = ""
        binding.ivPlayPause.visibility = View.GONE

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = ApiService.getPersonalFm()
                if (result is AppResult.Success) {
                    val songs = result.data.data
                    if (!songs.isNullOrEmpty()) {
                        // 设置队列（addAllToQueue 会将 currentIndex 设为 0）
                        PlayQueueManager.addAllToQueue(songs)
                        val firstSong = songs[0]
                        // 获取第一首的播放 URL
                        val urlResult = ApiService.getSongUrl(firstSong.id)
                        val url = if (urlResult is AppResult.Success) {
                            urlResult.data.data?.firstOrNull()?.url
                        } else null
                        if (!url.isNullOrEmpty()) {
                            handler.post {
                                updateSongInfo(firstSong)
                                binding.ivPlayPause.visibility = View.VISIBLE
                                musicService?.play(url, firstSong)
                            }
                            return@launch
                        }
                    }
                }
                // API 返回空或无可用 URL
                handler.post {
                    binding.tvSongTitle.text = "暂无推荐"
                    binding.tvArtist.text = "请稍后再试"
                    showToast("获取推荐失败，请重试")
                }
            } catch (_: Exception) {
                handler.post {
                    binding.tvSongTitle.text = "网络异常"
                    binding.tvArtist.text = "请检查网络后重试"
                    showToast("网络异常，请重试")
                }
            }
        }
    }

    /**
     * 跳过当前歌曲（垃圾桶按钮）
     * 即播放下一首，FM 模式下 MusicService 会自动处理队列和补充
     */
    private fun skipCurrent() {
        val svc = musicService ?: return
        svc.playNext()
    }

    /**
     * 切换收藏状态（红心按钮）
     */
    private fun toggleLike() {
        val song = musicService?.getCurrentSong() ?: return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val like = !song.liked
                ApiService.likeSong(song.id, like)
                song.liked = like
                handler.post {
                    updateLikeIcon()
                    val msg = if (like) "已添加到我喜欢的音乐" else "已取消喜欢"
                    showToast(msg)
                }
            } catch (_: Exception) {
                handler.post { showToast("操作失败") }
            }
        }
    }

    // ==================== UI 更新 ====================

    private fun updateSongInfo(song: SongInfo? = musicService?.getCurrentSong()) {
        val s = song ?: return
        binding.tvSongTitle.text = s.name ?: "未知歌曲"
        binding.tvArtist.text = s.artistNames
        // 加载专辑封面
        s.al?.picUrl?.let { url ->
            binding.ivAlbumArt.loadImageCircle(url, R.drawable.ic_default_album)
        }
        updateLikeIcon()
        startDiscRotation()
        // 加载歌词
        loadLyrics(s.id)
    }

    /**
     * 加载并显示歌词
     */
    private fun loadLyrics(songId: Long) {
        binding.tvLyrics.text = "歌词加载中..."
        binding.tvLyrics.visibility = View.VISIBLE
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = ApiService.getLyric(songId)
                if (result is AppResult.Success) {
                    val lyricText = result.data.lrc?.lyric
                    if (lyricText.isNullOrBlank()) {
                        handler.post {
                            binding.tvLyrics.text = "暂无歌词"
                        }
                    } else {
                        // 解析 LRC 歌词为纯文本
                        val lines = parseLrcToText(lyricText)
                        handler.post {
                            binding.tvLyrics.text = lines
                        }
                    }
                } else {
                    handler.post {
                        binding.tvLyrics.text = "暂无歌词"
                    }
                }
            } catch (_: Exception) {
                handler.post {
                    binding.tvLyrics.text = "暂无歌词"
                }
            }
        }
    }

    /**
     * 将 LRC 格式歌词解析为纯文本（去掉时间标签）
     */
    private fun parseLrcToText(lrc: String): String {
        val regex = Regex("""\[\d{2}:\d{2}[.:]\d{2,3}\]""")
        return lrc.lines()
            .mapNotNull { line ->
                val text = line.replace(regex, "").trim()
                text.ifBlank { null }
            }
            .joinToString("\n")
    }

    private fun updatePlayPauseIcon() {
        val isPlaying = musicService?.isPlaying == true
        binding.ivPlayPause.setImageResource(
            if (isPlaying) android.R.drawable.ic_media_pause
            else android.R.drawable.ic_media_play
        )
    }

    private fun updateLikeIcon() {
        val liked = musicService?.getCurrentSong()?.liked == true
        binding.ivLike.setImageResource(
            if (liked) R.drawable.ic_heart_filled
            else R.drawable.ic_heart_outline
        )
    }

    // ==================== 唱片旋转动画 ====================

    private fun startDiscRotation() {
        if (discRotation?.isRunning == true) return
        discRotation = ObjectAnimator.ofFloat(binding.cvAlbum, "rotation", 0f, 360f).apply {
            duration = 20000 // 20 秒一圈，比播放器慢一些更优雅
            repeatCount = ObjectAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
        }
    }

    private fun pauseDiscRotation() {
        discRotation?.let {
            if (it.isRunning) {
                it.pause()
            }
        }
    }

    private fun stopDiscRotation() {
        discRotation?.cancel()
        discRotation = null
    }

    // ==================== 工具方法 ====================

    private fun formatTime(millis: Int): String {
        val totalSeconds = millis / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%02d:%02d".format(minutes, seconds)
    }
}
