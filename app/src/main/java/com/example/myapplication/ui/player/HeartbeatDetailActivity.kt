package com.example.myapplication.ui.player

import android.animation.ObjectAnimator
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.example.myapplication.R
import com.example.myapplication.base.BaseActivity
import com.example.myapplication.base.Result as AppResult
import com.example.myapplication.data.Repository
import com.example.myapplication.data.remote.SongInfo
import com.example.myapplication.databinding.ActivityPlayerBinding
import com.example.myapplication.databinding.PageCoverBinding
import com.example.myapplication.databinding.PageLyricBinding
import com.example.myapplication.manager.AccountManager
import com.example.myapplication.manager.FavoriteManager
import com.example.myapplication.manager.PlayMode
import com.example.myapplication.manager.PlayQueueManager
import com.example.myapplication.service.MusicService
import com.example.myapplication.util.formatDuration
import com.example.myapplication.util.loadImageCircle
import com.example.myapplication.util.showToast
import com.example.myapplication.viewmodel.PlaylistViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 心动模式歌曲详情页
 *
 * 与普通播放器区别：
 * - 无播放模式切换按钮（iv_play_mode 隐藏）
 * - 无迷你播放栏（enableMiniPlayer = false）
 * - 播放模式固定为 HEARTBEAT（第一首来自喜欢歌单，后续由心动 API 拉取）
 * - 评论按钮保留
 */
class HeartbeatDetailActivity : BaseActivity<ActivityPlayerBinding>() {

    /** 心动页不需要迷你播放栏 */
    override val enableMiniPlayer: Boolean = false

    private lateinit var viewModel: PlaylistViewModel
    private var musicService: MusicService? = null
    private var serviceBound = false

    /** 歌词数据 */
    private val lyricEntries = mutableListOf<Pair<Long, String>>()
    private val tlyricEntries = mutableListOf<Pair<Long, String>>()

    private val lyricAdapter = LyricAdapter()
    private var currentLyricIndex = -1
    private var lastScrolledLyricIndex = -1
    private var isUserScrollingLyric = false

    /** ViewPager 相关 */
    private var coverPageBinding: PageCoverBinding? = null
    private var lyricPageBinding: PageLyricBinding? = null
    private var isOnLyricPage = false

    private var touchDownX = 0f
    private var touchDownY = 0f
    private val touchSlop = 20f
    private var lastClickTime = 0L

    /** 唱片旋转动画 */
    private var discRotationAnimator: ObjectAnimator? = null
    private var currentRotation: Float = 0f

    private val repository = Repository.getInstance()

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            musicService = (service as MusicService.MusicBinder).getService()
            serviceBound = true

            musicService?.addProgressListener(progressListener)
            musicService?.addPlayStateListener(playStateListener)

            val requestedSongId = intent.getLongExtra("song_id", 0)
            val current = musicService?.getCurrentSong()

            if (current != null && current.id == requestedSongId && musicService?.isPlaying == true) {
                displaySongInfo(current)
                updatePlayButton(true)
                startDiscRotation()
                binding.seekbar.max = musicService?.getDuration() ?: 0
                binding.seekbar.progress = musicService?.getCurrentPosition() ?: 0
                val dur = musicService?.getDuration() ?: 0
                val pos = musicService?.getCurrentPosition() ?: 0
                binding.tvTotalTime.text = (dur / 1000).formatDuration()
                binding.tvCurrentTime.text = (pos / 1000).formatDuration()
                initFavoriteState(current.id)
                loadLyric(current.id)
            } else {
                loadSongFromIntent()
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            musicService = null
            serviceBound = false
        }
    }

    private val progressListener = object : MusicService.OnProgressListener {
        override fun onProgress(currentPosition: Int, duration: Int) {
            binding.seekbar.progress = currentPosition
            binding.seekbar.max = duration
            binding.tvCurrentTime.text = (currentPosition / 1000).formatDuration()
            binding.tvTotalTime.text = (duration / 1000).formatDuration()
            updateLyricHighlight(currentPosition.toLong())
        }
    }

    private val playStateListener = object : MusicService.OnPlayStateListener {
        override fun onPlayStateChanged(isPlaying: Boolean, song: SongInfo?) {
            updatePlayButton(isPlaying)
            if (isPlaying) startDiscRotation() else pauseDiscRotation()
            if (song != null) displaySongInfo(song)
        }

        override fun onSongChanged(song: SongInfo) {
            displaySongInfo(song)
            loadLyric(song.id)
            initFavoriteState(song.id)
            updateNavButtons()
            resetDiscRotation()
        }
    }

    override fun initBinding() = ActivityPlayerBinding.inflate(layoutInflater)

    override fun initView() {
        viewModel = ViewModelProvider(this)[PlaylistViewModel::class.java]

        // 隐藏播放模式按钮
        binding.ivPlayMode.visibility = View.GONE

        val serviceIntent = Intent(this, MusicService::class.java)
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)

        // 强制设置为心动模式（首首歌来自喜欢歌单，后续由心动 API 补给）
        PlayQueueManager.setPlayMode(PlayMode.HEARTBEAT)

        binding.ivBack.setOnClickListener { finish() }
        binding.ivPlayPause.setOnClickListener { musicService?.togglePlayPause() }
        binding.ivPrev.setOnClickListener { musicService?.playPrevious() }
        binding.ivNext.setOnClickListener { musicService?.playNext() }
        binding.ivFavorite.setOnClickListener { toggleFavorite() }
        binding.ivComment.setOnClickListener { openComments() }

        // ViewPager2（封面 / 歌词）
        binding.vpCoverLyric.adapter = CoverLyricPagerAdapter()
        binding.vpCoverLyric.orientation = ViewPager2.ORIENTATION_HORIZONTAL

        binding.vpCoverLyric.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                isOnLyricPage = (position == 1)
                updateIndicator(position)
                if (isOnLyricPage) {
                    lyricPageBinding?.rvLyric?.post { setupLyricPadding() }
                    if (lyricEntries.isNotEmpty()) {
                        val currentPos = musicService?.getCurrentPosition()?.toLong() ?: 0L
                        currentLyricIndex = -1
                        lastScrolledLyricIndex = -1
                        updateLyricHighlight(currentPos)
                    }
                }
            }
        })

        // 进度条拖动
        binding.seekbar.setOnSeekBarChangeListener(
            object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: android.widget.SeekBar, progress: Int, fromUser: Boolean) {
                    if (fromUser) musicService?.seekTo(progress)
                }
                override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
            }
        )

        updateNavButtons()
    }

    private fun updateIndicator(page: Int) {
        if (page == 0) {
            binding.dotCover.setBackgroundResource(R.drawable.dot_active)
            binding.dotLyric.setBackgroundResource(R.drawable.dot_inactive)
            binding.tvPageHint.text = "滑动查看歌词"
        } else {
            binding.dotCover.setBackgroundResource(R.drawable.dot_inactive)
            binding.dotLyric.setBackgroundResource(R.drawable.dot_active)
            binding.tvPageHint.text = "滑动查看封面"
        }
    }

    private fun loadSongFromIntent() {
        val songId = intent.getLongExtra("song_id", 0)
        val songName = intent.getStringExtra("song_name") ?: ""
        val artistName = intent.getStringExtra("artist_name") ?: ""
        val albumPicUrl = intent.getStringExtra("album_pic_url") ?: ""
        val duration = intent.getLongExtra("duration", 0)

        val song = SongInfo(
            id = songId, name = songName, dt = duration,
            al = com.example.myapplication.data.remote.AlbumInfo(picUrl = albumPicUrl),
            ar = listOf(com.example.myapplication.data.remote.ArtistInfo(name = artistName))
        )

        val playlistJson = intent.getStringExtra("playlist_json")
        if (!playlistJson.isNullOrEmpty()) {
            try {
                val gson = com.google.gson.Gson()
                val type = object : com.google.gson.reflect.TypeToken<List<SongInfo>>() {}.type
                val songs: List<SongInfo> = gson.fromJson(playlistJson, type)
                val startIndex = songs.indexOfFirst { it.id == songId }.coerceAtLeast(0)
                PlayQueueManager.setQueue(songs, startIndex)
            } catch (_: Exception) {
                PlayQueueManager.playSong(song)
            }
        } else {
            PlayQueueManager.playSong(song)
        }

        displaySongInfo(song)
        val autoUrl = intent.getStringExtra("auto_play_url")
        if (!autoUrl.isNullOrEmpty()) {
            musicService?.play(autoUrl, song)
        } else {
            loadAndPlay(songId)
        }
        loadLyric(songId)
        initFavoriteState(songId)
    }

    private fun displaySongInfo(song: SongInfo) {
        binding.apply {
            tvSongTitle.text = song.name ?: "未知歌曲"
            tvArtist.text = song.artistNames
        }
        coverPageBinding?.ivAlbumArt?.loadImageCircle(song.al?.picUrl)
    }

    private fun initFavoriteState(songId: Long) {
        isFavorite = FavoriteManager.isLiked(songId)
        if (!FavoriteManager.isLoaded) {
            CoroutineScope(Dispatchers.IO).launch {
                isFavorite = repository.isFavorite(songId)
                runOnUiThread { updateFavoriteIcon() }
            }
            return
        }
        updateFavoriteIcon()
    }

    private fun loadAndPlay(songId: Long) {
        viewModel.loadSongUrl(songId) { url, audioTime ->
            if (!url.isNullOrEmpty()) {
                val song = PlayQueueManager.currentSong ?: return@loadSongUrl
                musicService?.play(url, song)
                val fullDuration = song.dt
                if (audioTime > 0L && fullDuration > 0L && audioTime < fullDuration * 0.8) {
                    showToast("该歌曲仅提供试听片段（${(audioTime / 1000).toInt().formatDuration()}）")
                }
            } else {
                showToast("无法获取播放地址，该歌曲可能无版权")
            }
        }
    }

    private fun loadLyric(songId: Long) {
        lyricEntries.clear()
        tlyricEntries.clear()
        currentLyricIndex = -1
        lastScrolledLyricIndex = -1

        lyricAdapter.setData(listOf(0L to "加载歌词中..."), emptyList())

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = repository.getLyric(songId)
                if (result is AppResult.Success) {
                    val lyricText = result.data.lrc?.lyric ?: ""
                    val tlyricText = result.data.tlyric?.lyric ?: ""
                    runOnUiThread {
                        val entries = parseLrc(lyricText)
                        if (entries.isNotEmpty()) lyricEntries.addAll(entries)
                        val tEntries = parseLrc(tlyricText)
                        if (tEntries.isNotEmpty()) tlyricEntries.addAll(tEntries)

                        if (lyricEntries.isNotEmpty()) {
                            lyricAdapter.setData(lyricEntries, tlyricEntries)
                        } else {
                            lyricAdapter.setData(listOf(0L to "纯音乐，请欣赏"), emptyList())
                        }
                    }
                } else {
                    runOnUiThread {
                        lyricAdapter.setData(listOf(0L to "暂无歌词"), emptyList())
                    }
                }
            } catch (_: Exception) {
                runOnUiThread {
                    lyricAdapter.setData(listOf(0L to "暂无歌词"), emptyList())
                }
            }
        }
    }

    private fun parseLrc(lrcText: String): List<Pair<Long, String>> {
        if (lrcText.isBlank()) return emptyList()
        val regex = Regex("\\[(\\d{2}):(\\d{2})(?:[.:](\\d{2,3}))?\\](.*)")
        return lrcText.split("\n").mapNotNull { line ->
            val match = regex.find(line)
            if (match != null) {
                val min = match.groupValues[1].toLongOrNull() ?: 0
                val sec = match.groupValues[2].toLongOrNull() ?: 0
                val ms = match.groupValues[3].takeIf { it.isNotEmpty() }
                    ?.let { (it.toLongOrNull() ?: 0) * if (it.length == 2) 10 else 1 } ?: 0
                val timestamp = min * 60000 + sec * 1000 + ms
                val text = match.groupValues[4].trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                timestamp to text
            } else null
        }.sortedBy { it.first }
    }

    private fun updateLyricHighlight(currentMs: Long) {
        if (lyricEntries.isEmpty()) return
        val newIndex = lyricAdapter.findIndexByTime(currentMs)
        if (newIndex < 0 || newIndex == currentLyricIndex) return
        currentLyricIndex = newIndex
        lyricAdapter.setHighlightedIndex(newIndex)
        if (isOnLyricPage && !isUserScrollingLyric && newIndex != lastScrolledLyricIndex) {
            lastScrolledLyricIndex = newIndex
            scrollLyricToCenter(newIndex)
        }
    }

    private fun setupLyricPadding() {
        val rv = lyricPageBinding?.rvLyric ?: return
        if (rv.height <= 0) { rv.post { setupLyricPadding() }; return }
        val halfHeight = rv.height / 2
        rv.setPadding(rv.paddingLeft, halfHeight, rv.paddingRight, halfHeight)
    }

    private fun scrollLyricToCenter(position: Int) {
        val rv = lyricPageBinding?.rvLyric ?: return
        val layoutManager = rv.layoutManager as? LinearLayoutManager ?: return
        if (rv.height <= 0) { rv.post { scrollLyricToCenter(position) }; return }
        val targetView = layoutManager.findViewByPosition(position)
        val itemHeight = targetView?.height
            ?: (48 * rv.resources.displayMetrics.density).toInt()
        val visibleHeight = rv.height - rv.paddingTop - rv.paddingBottom
        val offset = (visibleHeight / 2) - (itemHeight / 2)
        layoutManager.scrollToPositionWithOffset(position, offset)
    }

    private fun toggleFavorite() {
        if (!AccountManager.isLoggedIn) {
            showToast("请先登录后再喜欢")
            return
        }
        val song = musicService?.getCurrentSong() ?: return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val nowFav = repository.toggleFavorite(song)
                runOnUiThread {
                    isFavorite = nowFav
                    showToast(if (nowFav) "已喜欢" else "已取消喜欢")
                    updateFavoriteIcon()
                }
            } catch (e: Exception) {
                runOnUiThread { showToast("操作失败，请稍后重试") }
            }
        }
    }

    private fun openComments() {
        val song = musicService?.getCurrentSong() ?: return
        val intent = Intent(this, com.example.myapplication.ui.comment.CommentActivity::class.java).apply {
            putExtra("comment_type", 0)
            putExtra("resource_id", song.id)
            putExtra("resource_name", song.name ?: "")
        }
        startActivity(intent)
    }

    @JvmField var isFavorite = false

    private fun updateFavoriteIcon() {
        binding.ivFavorite.setImageResource(
            if (isFavorite) R.drawable.ic_heart_filled
            else R.drawable.ic_heart_outline
        )
    }

    private fun updatePlayButton(playing: Boolean) {
        binding.ivPlayPause.setImageResource(
            if (playing) android.R.drawable.ic_media_pause
            else android.R.drawable.ic_media_play
        )
    }

    private fun updateNavButtons() {
        binding.ivPrev.alpha = if (PlayQueueManager.size > 1) 1.0f else 0.3f
        binding.ivNext.alpha = if (PlayQueueManager.size > 1) 1.0f else 0.3f
    }

    private fun startDiscRotation() {
        val disc = coverPageBinding?.rotatingDisc ?: return
        discRotationAnimator?.cancel()
        discRotationAnimator = ObjectAnimator.ofFloat(
            disc, "rotation", currentRotation, currentRotation + 360f
        ).apply {
            duration = 8000L
            repeatCount = ObjectAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
        }
    }

    private fun pauseDiscRotation() {
        val disc = coverPageBinding?.rotatingDisc ?: return
        currentRotation = disc.rotation
        discRotationAnimator?.cancel()
    }

    private fun resetDiscRotation() {
        val disc = coverPageBinding?.rotatingDisc ?: return
        discRotationAnimator?.cancel()
        currentRotation = 0f
        disc.rotation = 0f
    }

    private inner class CoverLyricPagerAdapter : RecyclerView.Adapter<CoverLyricPagerAdapter.VH>() {
        inner class VH(val view: View) : RecyclerView.ViewHolder(view)

        override fun getItemCount(): Int = 2

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val inflater = LayoutInflater.from(parent.context)
            return if (viewType == 0) {
                val b = PageCoverBinding.inflate(inflater, parent, false)
                coverPageBinding = b
                VH(b.root)
            } else {
                val b = PageLyricBinding.inflate(inflater, parent, false)
                lyricPageBinding = b
                b.rvLyric.apply {
                    layoutManager = LinearLayoutManager(this@HeartbeatDetailActivity)
                    adapter = lyricAdapter
                    itemAnimator = null
                    addOnScrollListener(object : RecyclerView.OnScrollListener() {
                        override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                            isUserScrollingLyric = (newState == RecyclerView.SCROLL_STATE_DRAGGING
                                    || newState == RecyclerView.SCROLL_STATE_SETTLING)
                            if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                                recyclerView.postDelayed({
                                    isUserScrollingLyric = false
                                    val currentPos = musicService?.getCurrentPosition()?.toLong() ?: 0L
                                    currentLyricIndex = -1
                                    updateLyricHighlight(currentPos)
                                }, 1500L)
                            }
                        }
                    })
                }
                VH(b.root)
            }
        }

        override fun getItemViewType(position: Int): Int = position

        override fun onBindViewHolder(holder: VH, position: Int) {
            val pageView = holder.view
            pageView.setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    touchDownX = event.rawX
                    touchDownY = event.rawY
                }
                false
            }
            pageView.setOnClickListener {
                val now = System.currentTimeMillis()
                if (now - lastClickTime < 300L) return@setOnClickListener
                lastClickTime = now
                if (position == 0) {
                    binding.vpCoverLyric.setCurrentItem(1, true)
                } else {
                    binding.vpCoverLyric.setCurrentItem(0, true)
                }
            }
        }
    }

    override fun onDestroy() {
        discRotationAnimator?.cancel()
        discRotationAnimator = null
        if (serviceBound) {
            musicService?.removeProgressListener(progressListener)
            musicService?.removePlayStateListener(playStateListener)
            unbindService(serviceConnection)
            serviceBound = false
        }
        super.onDestroy()
    }
}
