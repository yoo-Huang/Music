package com.example.myapplication.ui.mine

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.R
import com.example.myapplication.adapter.HomeAdapter
import com.example.myapplication.base.BaseActivity
import com.example.myapplication.data.Repository
import com.example.myapplication.data.remote.SongInfo
import com.example.myapplication.data.remote.UserPlaylistItem
import com.example.myapplication.databinding.ActivityHeartbeatBinding
import com.example.myapplication.manager.AccountManager
import com.example.myapplication.manager.PlayMode
import com.example.myapplication.manager.PlayQueueManager
import com.example.myapplication.ui.player.PlayerActivity
import com.example.myapplication.util.formatCount
import com.example.myapplication.util.loadImage
import com.example.myapplication.util.showToast
import com.example.myapplication.viewmodel.HeartbeatViewModel
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 心动模式 / 智能播放页面
 *
 * 工作流程：
 * 1. 展示用户所有歌单供选择
 * 2. 点击歌单 → 调用心动模式 API 获取智能推荐
 * 3. 显示推荐歌曲列表，支持播放
 * 4. 进入播放器后以 HEARTBEAT 模式播放
 *
 * Intent 参数：
 * - preselected_playlist_id (Long, 可选) → 直接从"我喜欢的音乐"跳转，跳过歌单选择
 */
class HeartbeatActivity : BaseActivity<ActivityHeartbeatBinding>() {

    override val enableMiniPlayer: Boolean = true

    private lateinit var viewModel: HeartbeatViewModel
    private val repository = Repository.getInstance()
    private var cachedSongs: List<SongInfo> = emptyList()
    private var songAdapter: HomeAdapter? = null
    private var playlistAdapter: PlaylistSelectionAdapter? = null
    private var preselectedPlaylistId: Long = 0L

    override fun initBinding() = ActivityHeartbeatBinding.inflate(layoutInflater)

    override fun initView() {
        viewModel = ViewModelProvider(this)[HeartbeatViewModel::class.java]

        // 预选歌单 ID（从"我喜欢的音乐"直接跳转）
        preselectedPlaylistId = intent.getLongExtra("preselected_playlist_id", 0L)

        // 返回按钮
        binding.ivBack.setOnClickListener { finish() }

        // 初始化歌单列表
        binding.rvPlaylists.layoutManager = LinearLayoutManager(this)
        playlistAdapter = PlaylistSelectionAdapter { playlist ->
            viewModel.loadIntelligenceSongs(playlist.id)
        }
        binding.rvPlaylists.adapter = playlistAdapter

        // 初始化推荐歌曲列表
        binding.rvSongs.layoutManager = LinearLayoutManager(this)
        binding.rvSongs.adapter = HomeAdapter()
    }

    override fun initObserver() {
        // 歌单列表
        viewModel.playlists.observe(this) { playlists ->
            if (playlists.isEmpty()) {
                binding.layoutEmpty.visibility = View.VISIBLE
                binding.rvPlaylists.visibility = View.GONE
            } else {
                binding.layoutEmpty.visibility = View.GONE
                binding.rvPlaylists.visibility = View.VISIBLE
                playlistAdapter?.submitList(playlists)
            }
        }

        // 推荐歌曲
        viewModel.songs.observe(this) { songs ->
            cachedSongs = songs
            if (songs.isEmpty()) {
                showToast("暂无推荐歌曲，试试其他歌单")
                return@observe
            }
            // 切换到结果视图
            binding.rvPlaylists.visibility = View.GONE
            binding.rvSongs.visibility = View.VISIBLE
            binding.tvSectionTitle.text = "为你推荐"

            val adapter = HomeAdapter().apply {
                onSongClick = { song: SongInfo -> playSong(song) }
                onFavoriteClick = { song: SongInfo -> toggleFavorite(song) }
            }
            val items = songs.map { HomeAdapter.HomeItem.SongRow(it) }
            adapter.submitList(items)
            binding.rvSongs.adapter = adapter
            songAdapter = adapter
        }

        // 加载状态
        viewModel.isLoading.observe(this) { loading ->
            binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        }

        // 错误提示
        viewModel.errorMsg.observe(this) { msg ->
            if (!msg.isNullOrBlank()) showToast(msg)
        }
    }

    override fun initData() {
        if (!AccountManager.isLoggedIn) {
            showToast("请先登录")
            finish()
            return
        }

        // 如果预选了歌单，直接加载推荐歌曲，跳过歌单选择页
        if (preselectedPlaylistId > 0) {
            binding.tvSubtitle.text = "根据\"我喜欢的音乐\"为你智能推荐"
            viewModel.loadIntelligenceSongs(preselectedPlaylistId)
        } else {
            viewModel.loadUserPlaylists(AccountManager.userId)
        }
    }

    /**
     * 播放歌曲（心动模式）
     */
    private fun playSong(song: SongInfo) {
        val index = cachedSongs.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
        // 设置为心动模式
        PlayQueueManager.setQueue(cachedSongs, index)
        PlayQueueManager.heartbeatPlaylistId = viewModel.selectedPlaylistId.value ?: 0
        PlayQueueManager.setPlayMode(PlayMode.HEARTBEAT)

        val intent = Intent(this, PlayerActivity::class.java).apply {
            putExtra("song_id", song.id)
            putExtra("song_name", song.name ?: "未知歌曲")
            putExtra("artist_name", song.artistNames)
            putExtra("album_pic_url", song.al?.picUrl)
            putExtra("duration", song.dt)
            putExtra("playlist_json", Gson().toJson(cachedSongs))
            putExtra("play_mode", PlayMode.HEARTBEAT.value)
        }
        startActivity(intent)
    }

    /**
     * 切换收藏状态
     */
    private fun toggleFavorite(song: SongInfo) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val nowFav = repository.toggleFavorite(song)
                runOnUiThread {
                    showToast(if (nowFav) "已收藏" else "已取消收藏")
                    songAdapter?.notifyDataSetChanged()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    showToast("操作失败，请稍后重试")
                }
            }
        }
    }

    /**
     * 歌单选择适配器（内联）
     */
    private class PlaylistSelectionAdapter(
        private val onItemClick: (UserPlaylistItem) -> Unit
    ) : RecyclerView.Adapter<PlaylistSelectionAdapter.ViewHolder>() {

        private var items: List<UserPlaylistItem> = emptyList()

        fun submitList(list: List<UserPlaylistItem>) {
            items = list
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_heartbeat_playlist, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val playlist = items[position]
            holder.tvName.text = playlist.name ?: "未知歌单"
            holder.tvTrackCount.text = "${playlist.trackCount}首"
            holder.ivCover.loadImage(playlist.coverImgUrl)
            holder.itemView.setOnClickListener { onItemClick(playlist) }
        }

        override fun getItemCount(): Int = items.size

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val ivCover: ImageView = view.findViewById(R.id.iv_cover)
            val tvName: TextView = view.findViewById(R.id.tv_name)
            val tvTrackCount: TextView = view.findViewById(R.id.tv_track_count)
        }
    }
}
