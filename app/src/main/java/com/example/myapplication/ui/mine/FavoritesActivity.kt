package com.example.myapplication.ui.mine

import android.content.Intent
import android.view.View
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.myapplication.adapter.HomeAdapter
import com.example.myapplication.base.BaseActivity
import com.example.myapplication.data.Repository
import com.example.myapplication.data.remote.SongInfo
import com.example.myapplication.databinding.ActivityFavoritesBinding
import com.example.myapplication.manager.AccountManager
import com.example.myapplication.manager.PlayQueueManager
import com.example.myapplication.ui.player.PlayerActivity
import com.example.myapplication.util.showToast
import com.example.myapplication.viewmodel.FavoritesViewModel
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 收藏歌曲列表页面
 * 展示用户喜欢的歌曲，点击可播放，支持取消收藏
 */
class FavoritesActivity : BaseActivity<ActivityFavoritesBinding>() {

    override val enableMiniPlayer: Boolean = true

    private lateinit var viewModel: FavoritesViewModel
    private val repository = Repository.getInstance()
    private var cachedSongs: List<SongInfo> = emptyList()
    private var songAdapter: HomeAdapter? = null

    override fun initBinding() = ActivityFavoritesBinding.inflate(layoutInflater)

    override fun initView() {
        viewModel = ViewModelProvider(this)[FavoritesViewModel::class.java]

        // 返回按钮
        binding.ivBack.setOnClickListener { finish() }

        // 初始化 RecyclerView
        binding.rvSongs.layoutManager = LinearLayoutManager(this)
        binding.rvSongs.adapter = HomeAdapter()
    }

    override fun initObserver() {
        // 歌曲列表
        viewModel.songs.observe(this) { songs ->
            cachedSongs = songs
            if (songs.isEmpty()) {
                binding.layoutEmpty.visibility = View.VISIBLE
                binding.rvSongs.visibility = View.GONE
            } else {
                binding.layoutEmpty.visibility = View.GONE
                binding.rvSongs.visibility = View.VISIBLE
                binding.tvCount.text = "共${songs.size}首"

                val adapter = HomeAdapter().apply {
                    onSongClick = { song: SongInfo ->
                        playSong(song)
                    }
                    onFavoriteClick = { song: SongInfo ->
                        toggleFavorite(song)
                    }
                }
                val items = songs.map { HomeAdapter.HomeItem.SongRow(it) }
                adapter.submitList(items)
                binding.rvSongs.adapter = adapter
                songAdapter = adapter
            }
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
        viewModel.loadLikedSongs(AccountManager.userId)
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
                    // 取消收藏后从列表移除
                    if (!nowFav) {
                        val updated = cachedSongs.filter { it.id != song.id }
                        cachedSongs = updated
                        if (updated.isEmpty()) {
                            binding.layoutEmpty.visibility = View.VISIBLE
                            binding.rvSongs.visibility = View.GONE
                        } else {
                            binding.tvCount.text = "共${updated.size}首"
                            val adapter = HomeAdapter().apply {
                                onSongClick = { s -> playSong(s) }
                                onFavoriteClick = { s -> toggleFavorite(s) }
                            }
                            val items = updated.map { HomeAdapter.HomeItem.SongRow(it) }
                            adapter.submitList(items)
                            binding.rvSongs.adapter = adapter
                            songAdapter = adapter
                        }
                    } else {
                        // 刷新当前 adapter 图标
                        songAdapter?.notifyDataSetChanged()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    showToast("操作失败，请稍后重试")
                }
            }
        }
    }

    /**
     * 播放歌曲：将当前列表加入播放队列并跳转播放器
     */
    private fun playSong(song: SongInfo) {
        val index = cachedSongs.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
        PlayQueueManager.setQueue(cachedSongs, index)
        val intent = Intent(this, PlayerActivity::class.java).apply {
            putExtra("song_id", song.id)
            putExtra("song_name", song.name ?: "未知歌曲")
            putExtra("artist_name", song.artistNames)
            putExtra("album_pic_url", song.al?.picUrl)
            putExtra("duration", song.dt)
            putExtra("playlist_json", Gson().toJson(cachedSongs))
        }
        startActivity(intent)
    }
}
