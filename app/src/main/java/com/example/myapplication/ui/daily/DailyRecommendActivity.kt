package com.example.myapplication.ui.daily

import android.content.Intent
import android.view.View
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.myapplication.adapter.HomeAdapter
import com.example.myapplication.base.BaseActivity
import com.example.myapplication.data.Repository
import com.example.myapplication.data.remote.SongInfo
import com.example.myapplication.databinding.ActivityDailyRecommendBinding
import com.example.myapplication.manager.AccountManager
import com.example.myapplication.manager.PlayQueueManager
import com.example.myapplication.ui.player.PlayerActivity
import com.example.myapplication.util.showToast
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 每日推荐歌曲页面
 * 展示根据用户听歌历史生成的个性化每日推荐歌曲列表
 */
class DailyRecommendActivity : BaseActivity<ActivityDailyRecommendBinding>() {

    private lateinit var viewModel: DailyRecommendViewModel
    private var cachedSongs: List<SongInfo> = emptyList()

    override fun initBinding() = ActivityDailyRecommendBinding.inflate(layoutInflater)

    override fun initView() {
        viewModel = ViewModelProvider(this)[DailyRecommendViewModel::class.java]

        // Toolbar 返回按钮
        binding.toolbar.setNavigationOnClickListener { finish() }

        // 初始化歌曲列表
        binding.rvSongs.layoutManager = LinearLayoutManager(this)
        binding.rvSongs.adapter = HomeAdapter()

        // 播放全部按钮
        binding.btnPlayAll.setOnClickListener {
            if (cachedSongs.isEmpty()) {
                showToast("暂无推荐歌曲")
                return@setOnClickListener
            }
            PlayQueueManager.setQueue(cachedSongs, 0)
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
    }

    override fun initObserver() {
        viewModel.songs.observe(this) { songs: List<SongInfo> ->
            cachedSongs = songs
            val adapter = createSongAdapter()
            val items = songs.map { s -> HomeAdapter.HomeItem.SongRow(s) }
            adapter.submitList(items)
            binding.rvSongs.adapter = adapter
            binding.tvTrackCount.text = "共${songs.size}首"
        }

        viewModel.isLoading.observe(this) { isLoading: Boolean ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        viewModel.errorMsg.observe(this) { error: String? ->
            if (error != null) showToast(error)
        }
    }

    override fun initData() {
        viewModel.loadDailySongs()
    }

    /**
     * 创建歌曲列表 Adapter
     */
    private fun createSongAdapter(): HomeAdapter {
        return HomeAdapter().apply {
            onSongClick = { song: SongInfo ->
                PlayQueueManager.setQueue(cachedSongs, cachedSongs.indexOfFirst { s -> s.id == song.id })
                val intent = Intent(this@DailyRecommendActivity, PlayerActivity::class.java).apply {
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
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val nowLiked = repository.toggleFavorite(song)
                            runOnUiThread {
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
