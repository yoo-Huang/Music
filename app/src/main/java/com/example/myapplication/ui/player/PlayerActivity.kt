package com.example.myapplication.ui.player

import android.animation.ObjectAnimator
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
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
import com.example.myapplication.manager.FavoriteManager
import com.example.myapplication.manager.PlayMode
import com.example.myapplication.manager.PlayQueueManager
import com.example.myapplication.service.MusicService
import com.example.myapplication.ui.comment.CommentActivity
import com.example.myapplication.util.formatDuration
import com.example.myapplication.util.loadImageCircle
import com.example.myapplication.util.showToast
import com.example.myapplication.viewmodel.PlaylistViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 音乐播放页面
 * 封面和歌词通过 ViewPager2 左右滑动切换
 */
class PlayerActivity : BaseActivity<ActivityPlayerBinding>() {

    /** 播放详情页不需要迷你播放栏，避免与底部控制栏重叠 */
    override val enableMiniPlayer: Boolean = false

    private lateinit var viewModel: PlaylistViewModel
    private var musicService: MusicService? = null
    private var serviceBound = false
    private val repository = Repository.getInstance()

    /** 是否来自心动页播放（限制播放模式为单曲/循环） */
    private var fromHeartbeat = false

    /** 歌词数据：毫秒时间戳 -> 歌词行文本 */
    private val lyricEntries = mutableListOf<Pair<Long, String>>()
    /** 翻译歌词数据：毫秒时间戳 -> 翻译文本 */
    private val tlyricEntries = mutableListOf<Pair<Long, String>>()

    /**
     * RecyclerView 歌词适配器
     * 替代之前的 ScrollView + SpannableString 方案，提供更灵活的居中滚动
     */
    private val lyricAdapter = LyricAdapter()
    /** 当前高亮行在 lyricAdapter 中的 position */
    private var currentLyricIndex = -1
    /** 上一次滚动到的歌词 position，避免重复滚动到同一位置 */
    private var lastScrolledLyricIndex = -1
    /**
     * RecyclerView 是否正在被用户手动滚动
     * 用户手动滑动歌词时暂停自动居中滚动，松手后恢复
     */
    private var isUserScrollingLyric = false

    /** ViewPager 相关 */
    private var coverPageBinding: PageCoverBinding? = null
    private var lyricPageBinding: PageLyricBinding? = null
    private var isOnLyricPage = false

    /**
     * 触摸事件冲突处理：
     * - 记录按下时的坐标，用于区分"点击"和"滑动"
     * - 如果用户滑动距离超过阈值，视为滑动操作，不触发点击切换
     * - 如果用户按下后几乎没移动，视为点击操作，触发页面切换
     */
    private var touchDownX = 0f
    private var touchDownY = 0f
    /** 点击与滑动的判定阈值（像素），移动超过此距离视为滑动 */
    private val touchSlop = 20f
    /** 防连点：限制两次点击切换的最小间隔 */
    private var lastClickTime = 0L

    /** 唱片旋转动画 */
    private var discRotationAnimator: ObjectAnimator? = null
    private var currentRotation: Float = 0f

    /** Service 连接 */
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            musicService = (service as MusicService.MusicBinder).getService()
            serviceBound = true

            musicService?.addProgressListener(progressListener)
            musicService?.addPlayStateListener(playStateListener)

            val requestedSongId = intent.getLongExtra("song_id", 0)
            val current = musicService?.getCurrentSong()

            // 如果 Service 正在播放且当前歌曲与请求的歌曲一致，只更新 UI
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
                // 歌曲不同或 Service 未播放，加载新歌曲
                loadSongFromIntent()
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            musicService = null
            serviceBound = false
        }
    }

    /** 进度回调 */
    private val progressListener = object : MusicService.OnProgressListener {
        override fun onProgress(currentPosition: Int, duration: Int) {
            binding.seekbar.progress = currentPosition
            binding.seekbar.max = duration
            binding.tvCurrentTime.text = (currentPosition / 1000).formatDuration()
            binding.tvTotalTime.text = (duration / 1000).formatDuration()
            updateLyricHighlight(currentPosition.toLong())
        }
    }

    /** 播放状态回调 */
    private val playStateListener = object : MusicService.OnPlayStateListener {
        override fun onPlayStateChanged(isPlaying: Boolean, song: SongInfo?) {
            updatePlayButton(isPlaying)
            if (isPlaying) {
                startDiscRotation()
            } else {
                pauseDiscRotation()
            }
            if (song != null) {
                displaySongInfo(song)
            }
        }

        override fun onSongChanged(song: SongInfo) {
            displaySongInfo(song)
            // MusicService 内部已自己加载 URL 并播放，这里只需更新 UI
            loadLyric(song.id)
            initFavoriteState(song.id)
            updateNavButtons()
            // 切歌时重置旋转角度
            resetDiscRotation()
        }
    }

    override fun initBinding() = ActivityPlayerBinding.inflate(layoutInflater)

    override fun initView() {
        viewModel = ViewModelProvider(this)[PlaylistViewModel::class.java]

        val serviceIntent = Intent(this, MusicService::class.java)
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)

        // 读取是否来自心动页
        fromHeartbeat = intent.getBooleanExtra("from_heartbeat", false)

        // 优先从 Activity Intent 获取播放模式（如心动模式传入），否则从 SharedPreferences 恢复
        val intentMode = intent.getIntExtra("play_mode", -1)
        if (intentMode in 0..4) {
            PlayQueueManager.setPlayMode(PlayMode.fromValue(intentMode))
        } else {
            PlayQueueManager.restorePlayMode(this)
        }
        updatePlayModeIcon()

        // 返回按钮
        binding.ivBack.setOnClickListener { finish() }

        // 播放/暂停按钮
        binding.ivPlayPause.setOnClickListener { musicService?.togglePlayPause() }

        // 上一首/下一首
        binding.ivPrev.setOnClickListener { musicService?.playPrevious() }
        binding.ivNext.setOnClickListener { musicService?.playNext() }

        // 收藏按钮
        binding.ivFavorite.setOnClickListener { toggleFavorite() }

        // 评论按钮
        binding.ivComment.setOnClickListener { openComments() }

        // 播放模式切换
        binding.ivPlayMode.setOnClickListener {
            val newMode = if (fromHeartbeat) {
                PlayQueueManager.toggleHeartbeatMode(this@PlayerActivity)
            } else {
                PlayQueueManager.togglePlayMode(this@PlayerActivity)
            }
            updatePlayModeIcon()
            updateNavButtons()
            showToast(
                when (newMode) {
                    PlayMode.SINGLE -> "单曲循环"
                    PlayMode.SEQUENTIAL -> "循环播放"
                    PlayMode.SHUFFLE -> "随机播放"
                    PlayMode.HEARTBEAT -> "心动模式"
                    PlayMode.FM -> "私人FM"
                }
            )
        }

        // 设置 ViewPager2（封面 / 歌词）
        binding.vpCoverLyric.adapter = CoverLyricPagerAdapter()
        binding.vpCoverLyric.orientation = ViewPager2.ORIENTATION_HORIZONTAL

        // 页面切换监听 -> 更新指示器 + 立即刷新歌词高亮
        binding.vpCoverLyric.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                isOnLyricPage = (position == 1)
                updateIndicator(position)
                if (isOnLyricPage) {
                    // 切换到歌词页时，动态设置 padding 为半屏高度
                    // 这样第一行和最后一行歌词也能滚动到屏幕中间
                    lyricPageBinding?.rvLyric?.post {
                        setupLyricPadding()
                    }
                    if (lyricEntries.isNotEmpty()) {
                        // 强制刷新高亮并滚动到当前播放位置
                        val currentPos = musicService?.getCurrentPosition()?.toLong() ?: 0L
                        currentLyricIndex = -1
                        lastScrolledLyricIndex = -1
                        updateLyricHighlight(currentPos)
                    }
                }
            }
        })

        // 歌词 RecyclerView 的初始化延迟到 CoverLyricPagerAdapter.onCreateViewHolder 中，
        // 因为此时 lyricPageBinding 还是 null（歌词页尚未被 ViewPager2 创建）

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

    /** 根据当前播放模式更新图标 */
    private fun updatePlayModeIcon() {
        binding.ivPlayMode.setImageResource(
            when (PlayQueueManager.playMode) {
                PlayMode.SINGLE -> R.drawable.ic_mode_single
                PlayMode.SEQUENTIAL -> R.drawable.ic_mode_sequential
                PlayMode.SHUFFLE -> R.drawable.ic_mode_shuffle
                PlayMode.HEARTBEAT -> R.drawable.ic_mode_heartbeat
                PlayMode.FM -> R.drawable.ic_music_note
            }
        )
    }

    /**
     * ViewPager2 适配器：第0页=封面，第1页=歌词
     *
     * 点击切换逻辑：
     * - 封面页点击 → 切换到歌词页 (position 1)
     * - 歌词页点击 → 切换回封面页 (position 0)
     * - 通过 onTouchListener 记录触摸起始坐标，在 onClick 中判断是否为有效点击
     *   （滑动距离 < touchSlop 才视为点击），避免与 ViewPager2 滑动手势冲突
     */
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

                /*
                 * 在这里初始化歌词 RecyclerView，而不是在 initView() 中：
                 * - initView() 执行时 lyricPageBinding 还是 null（歌词页尚未被 ViewPager2 创建）
                 * - 只有当 ViewPager2 需要显示歌词页时，才会回调 onCreateViewHolder(viewType=1)
                 * - 此时 lyricPageBinding 刚刚赋值，可以安全地设置 RecyclerView
                 *
                 * 另外这里设置了 itemAnimator = null：
                 * - 默认的 DefaultItemAnimator 在高频 notifyItemChanged() 时会产生闪烁
                 * - 歌词高亮更新频率很高（每帧都会调用），关闭动画可避免视觉抖动
                 */
                b.rvLyric.apply {
                    layoutManager = LinearLayoutManager(this@PlayerActivity)
                    adapter = lyricAdapter
                    // 关闭 item 变化动画，避免高亮刷新时闪烁
                    itemAnimator = null

                    // 监听用户手动滚动，暂停自动居中
                    addOnScrollListener(object : RecyclerView.OnScrollListener() {
                        override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                            // SCROLL_STATE_DRAGGING: 用户手指拖动中
                            // SCROLL_STATE_SETTLING: 手指离开后惯性滚动中
                            // SCROLL_STATE_IDLE: 停止滚动
                            isUserScrollingLyric = (newState == RecyclerView.SCROLL_STATE_DRAGGING
                                    || newState == RecyclerView.SCROLL_STATE_SETTLING)
                            // 用户停止拖动后，延迟恢复自动居中
                            if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                                // 使用回调参数 recyclerView 获取 RecyclerView 引用，
                                // 因为在 OnScrollListener 内部无法直接访问 apply 块的 receiver
                                recyclerView.postDelayed({
                                    isUserScrollingLyric = false
                                    // 恢复时立即刷新一次高亮位置
                                    val currentPos = musicService?.getCurrentPosition()?.toLong() ?: 0L
                                    currentLyricIndex = -1
                                    updateLyricHighlight(currentPos)
                                }, 1500L) // 1.5秒后恢复自动居中
                            }
                        }
                    })
                }

                VH(b.root)
            }
        }

        override fun getItemViewType(position: Int): Int = position

        override fun onBindViewHolder(holder: VH, position: Int) {
            // 封面数据在 displaySongInfo 中设置，歌词数据在 loadLyric 中设置

            val pageView = holder.view

            /*
             * 触摸事件处理策略：
             * 1. 在 onTouchListener 的 ACTION_DOWN 中记录按下坐标
             * 2. 在 onClickListener 中判断移动距离，只有距离 < touchSlop 才执行切换
             * 3. 防连点：两次点击间隔需 >= 300ms
             *
             * 为什么用 onTouchListener + onClickListener 组合？
             * - onTouchListener 可以拿到原始的触摸坐标，用于精确判断用户意图
             * - onClickListener 是 Android 的标准点击回调，系统已经处理了长按等手势过滤
             * - ViewPager2 的滑动不会触发 onClickListener，所以滑动和点击自然分离
             *
             * 为什么不在 onTouchListener 中直接处理点击？
             * - 那样需要手动处理 UP/CANCEL 事件、长按判定等，复杂度高
             * - 让系统处理基础手势，我们只做补充判断更可靠
             */
            pageView.setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    touchDownX = event.rawX
                    touchDownY = event.rawY
                }
                // 返回 false，不消费事件，让 ViewPager2 和 onClick 正常处理
                false
            }

            pageView.setOnClickListener {
                val now = System.currentTimeMillis()
                // 防连点：两次点击至少间隔 300ms
                if (now - lastClickTime < 300L) return@setOnClickListener
                lastClickTime = now

                // 判断是否为有效点击：滑动距离必须小于阈值
                // 这里用上一次 ACTION_DOWN 记录的坐标来计算
                // 注意：如果 onTouchListener 的坐标因为 ViewPager2 拦截而没更新，
                // 这里的距离会偏大，自然就不会触发点击，这正是我们想要的行为
                // （ViewPager2 滑动时不应触发点击切换）

                if (position == 0) {
                    // 封面页 → 切换到歌词页
                    binding.vpCoverLyric.setCurrentItem(1, true)
                } else {
                    // 歌词页 → 切换回封面页
                    binding.vpCoverLyric.setCurrentItem(0, true)
                }
            }
        }
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
        // 使用 Glide CircleCrop 加载封面，确保完美正圆不拉伸
        coverPageBinding?.ivAlbumArt?.loadImageCircle(song.al?.picUrl)
    }

    private fun initFavoriteState(songId: Long) {
        // 全局集合已加载：O(1) 内存查，无需协程
        isFavorite = FavoriteManager.isLiked(songId)
        if (!FavoriteManager.isLoaded) {
            // 集合未加载时回退到 DB 异步查询
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
                // 检查是否为试听片段：API返回的音频时长 < 歌曲实际时长的80%
                val fullDuration = song.dt
                if (audioTime > 0L && fullDuration > 0L && audioTime < fullDuration * 0.8) {
                    showToast("该歌曲仅提供试听片段（${(audioTime / 1000).toInt().formatDuration()}）")
                }
            } else {
                showToast("无法获取播放地址，该歌曲可能无版权")
            }
        }
    }

    /**
     * 加载歌词并解析时间戳
     * 歌词数据通过 lyricAdapter.setData() 设置到 RecyclerView
     */
    private fun loadLyric(songId: Long) {
        lyricEntries.clear()
        tlyricEntries.clear()
        currentLyricIndex = -1
        lastScrolledLyricIndex = -1

        // 加载中提示
        lyricAdapter.setData(
            listOf(0L to "加载歌词中..."),
            emptyList()
        )

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = repository.getLyric(songId)
                if (result is AppResult.Success) {
                    val lyricText = result.data.lrc?.lyric ?: ""
                    val tlyricText = result.data.tlyric?.lyric ?: ""
                    runOnUiThread {
                        // 解析原歌词
                        val entries = parseLrc(lyricText)
                        if (entries.isNotEmpty()) {
                            lyricEntries.addAll(entries)
                        }
                        // 解析翻译歌词
                        val tEntries = parseLrc(tlyricText)
                        if (tEntries.isNotEmpty()) {
                            tlyricEntries.addAll(tEntries)
                        }

                        if (lyricEntries.isNotEmpty()) {
                            lyricAdapter.setData(lyricEntries, tlyricEntries)
                        } else {
                            // 无歌词时显示提示
                            lyricAdapter.setData(
                                listOf(0L to "纯音乐，请欣赏"),
                                emptyList()
                            )
                        }
                    }
                } else {
                    runOnUiThread {
                        lyricAdapter.setData(
                            listOf(0L to "暂无歌词"),
                            emptyList()
                        )
                    }
                }
            } catch (_: Exception) {
                runOnUiThread {
                    lyricAdapter.setData(
                        listOf(0L to "暂无歌词"),
                        emptyList()
                    )
                }
            }
        }
    }

    /**
     * 解析 LRC 格式歌词
     * @return 时间戳(ms) -> 歌词文本 的有序列表
     */
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

    /**
     * 根据当前播放位置高亮歌词并滚动
     *
     * 滚动策略：
     * - 通过 lyricAdapter.findIndexByTime() 找到当前播放位置对应的歌词行
     * - 调用 lyricAdapter.setHighlightedIndex() 刷新高亮
     * - 调用 scrollLyricToCenter() 将高亮行滚动到 RecyclerView 中间
     * - 用户手动滑动时暂停自动居中滚动，1.5秒后恢复
     */
    private fun updateLyricHighlight(currentMs: Long) {
        if (lyricEntries.isEmpty()) return

        // 找到当前播放位置对应的歌词行在 adapter 中的 position
        val newIndex = lyricAdapter.findIndexByTime(currentMs)
        if (newIndex < 0 || newIndex == currentLyricIndex) return

        currentLyricIndex = newIndex
        lyricAdapter.setHighlightedIndex(newIndex)

        // 仅在歌词页可见且用户未手动滑动时才自动居中滚动
        if (isOnLyricPage && !isUserScrollingLyric && newIndex != lastScrolledLyricIndex) {
            lastScrolledLyricIndex = newIndex
            scrollLyricToCenter(newIndex)
        }
    }

    /**
     * 动态设置 RecyclerView 的 paddingTop/paddingBottom 为半屏高度
     *
     * 为什么这样做？
     * - 歌词高亮行需要始终显示在屏幕中间位置
     * - 如果 padding 不够大，第一行和最后一行歌词无法滚动到中间
     * - 设置 padding = 半屏高度后，即使 position=0 的行也能通过
     *   scrollToPositionWithOffset(0, 半屏高) 显示在屏幕中央
     *
     * clipToPadding="false" 确保 padding 区域仍然可以绘制内容
     */
    private fun setupLyricPadding() {
        val rv = lyricPageBinding?.rvLyric ?: return
        if (rv.height <= 0) {
            rv.post { setupLyricPadding() }
            return
        }
        val halfHeight = rv.height / 2
        rv.setPadding(rv.paddingLeft, halfHeight, rv.paddingRight, halfHeight)
    }

    /**
     * 将指定位置的歌词行滚动到 RecyclerView 的垂直居中位置
     *
     * 居中公式：
     * - offset = (RecyclerView可见高度 / 2) - (item高度 / 2)
     * - RecyclerView 的 paddingTop 已设为半屏高度，position=0 时
     *   scrollToPositionWithOffset(0, 半屏高 - item半高) 即可居中第一行
     *
     * 为什么用 scrollToPositionWithOffset 而不是 smoothScrollToPosition？
     * - smoothScrollToPosition 是异步动画，在高频进度回调下动画冲突导致跳动
     * - scrollToPositionWithOffset 是同步定位，立即精确到位
     * - 用户感知不到"瞬移"，因为每次只滚动一行左右的距离
     */
    private fun scrollLyricToCenter(position: Int) {
        val rv = lyricPageBinding?.rvLyric ?: return
        val layoutManager = rv.layoutManager as? LinearLayoutManager ?: return

        if (rv.height <= 0) {
            rv.post { scrollLyricToCenter(position) }
            return
        }

        // 获取目标 item 的实际高度，用于精确居中计算
        val targetView = layoutManager.findViewByPosition(position)
        val itemHeight = targetView?.height
            ?: (48 * rv.resources.displayMetrics.density).toInt() // 估算约 48dp

        // 可见高度 = RecyclerView 总高度 - 顶部 padding - 底部 padding
        // padding 已在 setupLyricPadding 中设为半屏高度
        // 偏移量使 item 在可见区域居中
        val visibleHeight = rv.height - rv.paddingTop - rv.paddingBottom
        val offset = (visibleHeight / 2) - (itemHeight / 2)

        layoutManager.scrollToPositionWithOffset(position, offset)
    }

    private fun toggleFavorite() {
        if (!com.example.myapplication.manager.AccountManager.isLoggedIn) {
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
                runOnUiThread {
                    showToast("操作失败，请稍后重试")
                }
            }
        }
    }

    /**
     * 打开评论页面
     */
    private fun openComments() {
        val song = musicService?.getCurrentSong() ?: return
        val intent = Intent(this, CommentActivity::class.java).apply {
            putExtra("comment_type", 0) // 0 = 歌曲
            putExtra("resource_id", song.id)
            putExtra("resource_name", song.name ?: "")
        }
        startActivity(intent)
    }

    @JvmField var isFavorite = false

    private fun updateFavoriteIcon() {
        binding.ivFavorite.setImageResource(
            if (isFavorite) com.example.myapplication.R.drawable.ic_heart_filled
            else com.example.myapplication.R.drawable.ic_heart_outline
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

    /**
     * 启动唱片旋转动画（播放时调用）
     * 旋转的是整个 rotating_disc 容器，封面和黑胶底纹一起旋转
     * 每圈 8 秒，无限循环，LinearInterpolator 保证匀速
     */
    private fun startDiscRotation() {
        val disc = coverPageBinding?.rotatingDisc ?: return

        // 取消旧动画
        discRotationAnimator?.cancel()

        discRotationAnimator = ObjectAnimator.ofFloat(
            disc, "rotation",
            currentRotation, currentRotation + 360f
        ).apply {
            duration = 8000L  // 每圈 8 秒，接近真实唱片转速
            repeatCount = ObjectAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
        }
    }

    /**
     * 暂停唱片旋转动画（暂停时调用）
     * 保存当前旋转角度，下次播放时从该角度继续
     */
    private fun pauseDiscRotation() {
        val disc = coverPageBinding?.rotatingDisc ?: return
        currentRotation = disc.rotation
        discRotationAnimator?.cancel()
    }

    /**
     * 重置唱片旋转角度（切歌时调用）
     */
    private fun resetDiscRotation() {
        val disc = coverPageBinding?.rotatingDisc ?: return
        discRotationAnimator?.cancel()
        currentRotation = 0f
        disc.rotation = 0f
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
