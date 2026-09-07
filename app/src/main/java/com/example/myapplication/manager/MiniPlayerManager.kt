package com.example.myapplication.manager

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.example.myapplication.R
import com.example.myapplication.data.remote.SongInfo
import com.example.myapplication.databinding.LayoutMiniPlayerBinding
import com.example.myapplication.manager.PlayQueueManager
import com.example.myapplication.service.MusicService
import com.example.myapplication.ui.player.PlayerActivity
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.example.myapplication.util.dp2px

/**
 * 迷你播放栏管理器
 * 全局管理底部悬浮播放栏的显示/隐藏、Service 绑定、进度更新
 *
 * 布局策略（与 activity_main.xml 配合）：
 * activity_main.xml 中预留了 mini_player_placeholder 容器，
 * 通过 ConstraintLayout 垂直链将 FragmentContainer → Placeholder → BottomNavigation 串联。
 * 本管理器将 LayoutMiniPlayerBinding 的 root inflate 到该容器中，
 * 通过控制 Placeholder 的 visibility 实现显示/隐藏，避免遮挡底部导航栏。
 */
class MiniPlayerManager(
    private val activity: Activity
) {
    private var musicService: MusicService? = null
    private var serviceBound = false
    private var attached = false

    /** 布局内预留的占位容器（activity_main.xml 中的 mini_player_placeholder） */
    private val placeholder: FrameLayout? by lazy {
        activity.findViewById(R.id.mini_player_placeholder)
    }

    private val binding: LayoutMiniPlayerBinding by lazy {
        LayoutMiniPlayerBinding.inflate(LayoutInflater.from(activity))
    }

    /** Service 连接 */
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            musicService = (service as MusicService.MusicBinder).getService()
            serviceBound = true
            musicService?.addProgressListener(progressListener)
            musicService?.addPlayStateListener(playStateListener)
            refreshPlayer()
        }

        override fun onServiceDisconnected(name: ComponentName) {
            musicService = null
            serviceBound = false
        }
    }

    /** 进度回调 */
    private val progressListener = object : MusicService.OnProgressListener {
        override fun onProgress(currentPosition: Int, duration: Int) {
            if (duration > 0) {
                val fraction = currentPosition.toFloat() / duration.toFloat()
                val totalWidth = binding.root.width
                val lp = binding.miniProgress.layoutParams
                lp.width = (totalWidth * fraction).toInt().coerceAtLeast(0)
                binding.miniProgress.layoutParams = lp
            }
        }
    }

    /** 播放状态回调 */
    private val playStateListener = object : MusicService.OnPlayStateListener {
        override fun onPlayStateChanged(isPlaying: Boolean, song: SongInfo?) {
            // 心动 tab 可见时由内嵌播放器接管，不显示迷你播放栏
            if (PlayQueueManager.isHeartbeatPageVisible) {
                hide()
                return
            }
            if (song != null) {
                show()
                updateInfo(song)
                updatePlayButton(isPlaying)
            } else {
                hide()
            }
        }

        override fun onSongChanged(song: SongInfo) {
            // 心动 tab 可见时由内嵌播放器接管，不显示迷你播放栏
            if (PlayQueueManager.isHeartbeatPageVisible) {
                hide()
                return
            }
            show()
            updateInfo(song)
        }
    }

    /**
     * 绑定 Service 并将迷你播放栏放入布局内的 placeholder 容器
     *
     * 不再使用 Window overlay 方式，而是直接 inflate 到 activity_main.xml
     * 中预留的 mini_player_placeholder FrameLayout 中。
     * ConstraintLayout 会自动处理剩余空间的分配，底部导航栏不会被遮挡。
     */
    fun attach() {
        if (attached) return

        val container = placeholder ?: return
        if (container.childCount > 0) return // 已添加过

        // 将迷你播放栏 inflate 到布局内的占位容器
        val rootView = binding.root.apply {
            visibility = View.GONE
        }
        container.addView(
            rootView,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dpToPx(68)
            )
        )
        attached = true

        // 设置点击事件
        binding.root.setOnClickListener {
            val currentSong = musicService?.getCurrentSong()
            val intent = Intent(activity, PlayerActivity::class.java).apply {
                currentSong?.let { song ->
                    putExtra("song_id", song.id)
                    putExtra("song_name", song.name ?: "")
                    putExtra("artist_name", song.artistNames)
                    putExtra("album_pic_url", song.al?.picUrl ?: "")
                    putExtra("duration", song.dt)
                }
                // 传递当前播放模式，防止 FM/心动模式被 SharedPreferences 覆盖
                putExtra("play_mode", PlayQueueManager.playMode.value)
                // 标记来自心动页，限制播放模式为单曲/循环
                putExtra("from_heartbeat", PlayQueueManager.isHeartbeatSource)
            }
            activity.startActivity(intent)
        }
        binding.miniBtnPlay.setOnClickListener {
            musicService?.togglePlayPause()
        }
        binding.miniBtnPrev.setOnClickListener {
            musicService?.playPrevious()
        }
        binding.miniBtnNext.setOnClickListener {
            musicService?.playNext()
        }

        // 绑定 Service
        val serviceIntent = Intent(activity, MusicService::class.java)
        activity.bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    /**
     * 解绑 Service 并移除播放栏
     */
    fun detach() {
        if (serviceBound) {
            musicService?.removeProgressListener(progressListener)
            musicService?.removePlayStateListener(playStateListener)
            activity.unbindService(serviceConnection)
            serviceBound = false
        }
        if (attached) {
            placeholder?.removeView(binding.root)
            attached = false
        }
    }

    /** 刷新播放栏状态（用于恢复已有播放状态，如离开心动tab时主动恢复） */
    fun refreshPlayer() {
        val service = musicService ?: return
        // 心动 tab 可见时由内嵌播放器接管，不显示迷你播放栏
        if (PlayQueueManager.isHeartbeatPageVisible) return
        val song = service.getCurrentSong()
        if (song != null) {
            show()
            updateInfo(song)
            updatePlayButton(service.isPlaying)
            // 恢复进度条
            val dur = service.getDuration()
            val pos = service.getCurrentPosition()
            if (dur > 0) {
                binding.root.post {
                    val fraction = pos.toFloat() / dur.toFloat()
                    val totalWidth = binding.root.width
                    val lp = binding.miniProgress.layoutParams
                    lp.width = (totalWidth * fraction).toInt().coerceAtLeast(0)
                    binding.miniProgress.layoutParams = lp
                }
            }
        } else {
            hide()
        }
    }

    /**
     * 显示迷你播放栏
     * 同时设置 placeholder 容器可见，让 ConstraintLayout 重新分配空间
     * fragment_container 会自动缩小高度来腾出位置
     */
    fun show() {
        if (binding.root.visibility != View.VISIBLE) {
            binding.root.visibility = View.VISIBLE
        }
        if (placeholder?.visibility != View.VISIBLE) {
            placeholder?.visibility = View.VISIBLE
        }
    }

    /**
     * 隐藏迷你播放栏
     * placeholder 设为 GONE 后 ConstraintLayout 会将其高度视为 0，
     * fragment_container 自动扩展到底部导航栏顶部
     */
    fun hide() {
        if (binding.root.visibility != View.GONE) {
            binding.root.visibility = View.GONE
        }
        if (placeholder?.visibility != View.GONE) {
            placeholder?.visibility = View.GONE
        }
    }

    private fun updateInfo(song: SongInfo) {
        // Glide CircleCrop 裁剪为圆形，与迷你播放栏 48dp 封面匹配
        Glide.with(activity)
            .load(song.al?.picUrl)
            .placeholder(android.R.drawable.ic_menu_gallery)
            .error(android.R.drawable.ic_menu_gallery)
            .diskCacheStrategy(DiskCacheStrategy.DATA)
            .dontAnimate()
            .override(dp2px(activity, 48), dp2px(activity, 48))
            .transform(CircleCrop())
            .into(binding.miniCover)

        binding.miniSongName.text = song.name?.takeIf { it.isNotBlank() } ?: "未知歌曲"
        binding.miniArtist.text = song.artistNames
    }

    private fun updatePlayButton(isPlaying: Boolean) {
        binding.miniBtnPlay.setImageResource(
            if (isPlaying) android.R.drawable.ic_media_pause
            else android.R.drawable.ic_media_play
        )
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * activity.resources.displayMetrics.density).toInt()
    }
}
