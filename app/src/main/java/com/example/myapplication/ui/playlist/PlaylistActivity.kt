package com.example.myapplication.ui.playlist

import android.content.Intent
import android.view.View
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.myapplication.adapter.HomeAdapter
import com.example.myapplication.base.BaseActivity
import com.example.myapplication.data.Repository
import com.example.myapplication.data.remote.PlaylistDetail
import com.example.myapplication.data.remote.SongInfo
import com.example.myapplication.base.Result as AppResult
import com.example.myapplication.data.remote.ApiService
import com.example.myapplication.databinding.ActivityPlaylistBinding
import com.example.myapplication.manager.AccountManager
import com.example.myapplication.manager.PlayMode
import com.example.myapplication.manager.PlayQueueManager
import com.example.myapplication.ui.player.PlayerActivity
import com.example.myapplication.util.formatCount
import com.example.myapplication.util.loadImage
import com.example.myapplication.util.showToast
import com.example.myapplication.viewmodel.PlaylistViewModel
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 歌单详情页面（榜单/歌单）
 * 沉浸式头部 + 歌曲列表
 */
class PlaylistActivity : BaseActivity<ActivityPlaylistBinding>() {

    private lateinit var viewModel: PlaylistViewModel
    private var playlistId: Long = 0
    private var albumId: Long = 0
    private var cachedSongs: List<SongInfo> = emptyList()
    private var isLikedPlaylist = false

    override fun initBinding() = ActivityPlaylistBinding.inflate(layoutInflater)

    override fun initView() {
        viewModel = ViewModelProvider(this)[PlaylistViewModel::class.java]

        playlistId = intent.getLongExtra("playlist_id", 0)
        albumId = intent.getLongExtra("album_id", 0)

        // Toolbar 返回按钮
        binding.toolbar.setNavigationOnClickListener { finish() }

        // 初始化歌曲列表（先设置空 adapter 避免 "No adapter attached" 警告）
        binding.rvSongs.layoutManager = LinearLayoutManager(this)
        binding.rvSongs.adapter = HomeAdapter()

        // 播放全部按钮
        binding.btnPlayAll.setOnClickListener {
            if (cachedSongs.isEmpty()) {
                showToast("歌单暂无歌曲")
                return@setOnClickListener
            }
            PlayQueueManager.setQueue(cachedSongs, 0)
            if (isLikedPlaylist) {
                PlayQueueManager.heartbeatPlaylistId = playlistId
            }
            val firstSong = cachedSongs[0]
            val intent = Intent(this, PlayerActivity::class.java).apply {
                putExtra("song_id", firstSong.id)
                putExtra("song_name", firstSong.name ?: "未知歌曲")
                putExtra("artist_name", firstSong.artistNames)
                putExtra("album_pic_url", firstSong.al?.picUrl)
                putExtra("duration", firstSong.dt)
                putExtra("playlist_json", Gson().toJson(cachedSongs))
            }
            startActivity(intent)
        }

        // 心动模式按钮（仅"我喜欢的音乐"歌单）
        binding.btnHeartbeat.setOnClickListener {
            if (cachedSongs.isEmpty()) {
                showToast("歌单暂无歌曲")
                return@setOnClickListener
            }
            showToast("正在生成心动推荐...")
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    // 以当前歌单第一首歌为种子，调用心动模式API
                    val firstSong = cachedSongs.first()
                    val intelligenceResult = ApiService.getIntelligenceList(
                        id = playlistId,
                        pid = firstSong.id,
                        count = 30
                    )
                    var songs = if (intelligenceResult is AppResult.Success) {
                        intelligenceResult.data.extractSongs()
                    } else {
                        emptyList()
                    }

                    // 心动 API 不可用时，回退：直接使用歌单歌曲
                    if (songs.isEmpty()) {
                        songs = cachedSongs
                    }

                    if (songs.isEmpty()) {
                        runOnUiThread { showToast("暂无推荐歌曲") }
                        return@launch
                    }
                    runOnUiThread {
                        PlayQueueManager.setQueue(songs, 0)
                        PlayQueueManager.heartbeatPlaylistId = playlistId
                        PlayQueueManager.setPlayMode(PlayMode.HEARTBEAT)
                        val intent = Intent(this@PlaylistActivity, PlayerActivity::class.java).apply {
                            putExtra("song_id", songs[0].id)
                            putExtra("song_name", songs[0].name ?: "未知歌曲")
                            putExtra("artist_name", songs[0].artistNames)
                            putExtra("album_pic_url", songs[0].al?.picUrl)
                            putExtra("duration", songs[0].dt)
                            putExtra("playlist_json", Gson().toJson(songs))
                            putExtra("play_mode", PlayMode.HEARTBEAT.value)
                        }
                        startActivity(intent)
                    }
                } catch (e: Exception) {
                    runOnUiThread { showToast("心动推荐失败: ${e.message}") }
                }
            }
        }
    }

    override fun initObserver() {
        viewModel.playlist.observe(this) { playlist: PlaylistDetail? ->
            if (playlist != null) {
                isLikedPlaylist = playlist.name?.contains(
                    "喜欢的音乐", ignoreCase = true
                ) == true
                binding.btnHeartbeat.visibility = if (isLikedPlaylist) View.VISIBLE else View.GONE
                binding.apply {
                    ivHeaderBg.loadImage(playlist.coverImgUrl)
                    tvPlaylistName.text = playlist.name
                    tvCreator.text = "by ${playlist.creator?.nickname ?: "未知"}"
                    tvPlayCount.text = "播放 ${playlist.playCount.formatCount()} · ${playlist.trackCount}首"
                }
            }
        }

        // 专辑模式：隐藏心动模式和收藏按钮
        viewModel.isAlbumMode.observe(this) { isAlbum ->
            binding.btnHeartbeat.visibility = View.GONE
            if (isAlbum) {
                binding.tvPlayCount.text = "${cachedSongs.size}首"
            }
        }

        viewModel.songs.observe(this) { songs: List<SongInfo> ->
            cachedSongs = songs
            val adapter = createSongAdapter()
            val items = songs.map { s -> HomeAdapter.HomeItem.SongRow(s) }
            adapter.submitList(items)
            binding.rvSongs.adapter = adapter
            // 更新专辑歌曲数
            if (viewModel.isAlbumMode.value == true) {
                binding.tvPlayCount.text = "${songs.size}首"
            }
        }

        viewModel.isLoading.observe(this) { isLoading: Boolean ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        viewModel.errorMsg.observe(this) { error: String? ->
            if (error != null) showToast(error)
        }
    }

    override fun initData() {
        if (albumId != 0L) {
            viewModel.loadAlbumDetail(albumId)
        } else {
            viewModel.loadPlaylistDetail(playlistId)
        }
    }

    /**
     * 创建歌曲列表 Adapter
     */
    private fun createSongAdapter(): HomeAdapter {
        return HomeAdapter().apply {
            onSongClick = { song: SongInfo ->
                PlayQueueManager.setQueue(cachedSongs, cachedSongs.indexOfFirst { s -> s.id == song.id })
                if (isLikedPlaylist) {
                    PlayQueueManager.heartbeatPlaylistId = playlistId
                }
                val intent = Intent(this@PlaylistActivity, PlayerActivity::class.java).apply {
                    putExtra("song_id", song.id)
                    putExtra("song_name", song.name ?: "未知歌曲")
                    putExtra("artist_name", song.artistNames)
                    putExtra("album_pic_url", song.al?.picUrl)
                    putExtra("duration", song.dt)
                    putExtra("playlist_json", Gson().toJson(cachedSongs))
                }
                startActivity(intent)
            }
            onFavoriteClick = { song: SongInfo ->
                if (!AccountManager.isLoggedIn) {
                    showToast("请先登录后再喜欢")
                } else {
                    val repository = Repository.getInstance()
                    val adapter = this
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val nowLiked = repository.toggleFavorite(song)
                            runOnUiThread {
                                if (!nowLiked && isLikedPlaylist) {
                                    // 从喜欢的歌单中取消喜欢：立即移除并重新加载
                                    cachedSongs = cachedSongs.filter { s -> s.id != song.id }
                                    val items = cachedSongs.map { s -> HomeAdapter.HomeItem.SongRow(s) }
                                    adapter.submitList(items)
                                    viewModel.loadPlaylistDetail(playlistId)
                                } else {
                                    val pos = cachedSongs.indexOfFirst { s -> s.id == song.id }
                                    if (pos >= 0) adapter.notifyItemChanged(pos)
                                }
                                showToast(if (nowLiked) "已喜欢" else "已取消喜欢")
                            }
                        } catch (e: Exception) {
                            runOnUiThread {
                                showToast("操作失败，请稍后重试")
                            }
                        }
                    }
                }
            }
        }
    }
}
