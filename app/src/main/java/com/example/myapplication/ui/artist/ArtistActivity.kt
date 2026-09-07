package com.example.myapplication.ui.artist

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.myapplication.adapter.HomeAdapter
import com.example.myapplication.base.BaseActivity
import com.example.myapplication.data.Repository
import com.example.myapplication.data.remote.ArtistSongsArtist
import com.example.myapplication.data.remote.SongInfo
import com.example.myapplication.databinding.ActivityArtistBinding
import com.example.myapplication.manager.PlayQueueManager
import com.example.myapplication.ui.player.PlayerActivity
import com.example.myapplication.util.loadImage
import com.example.myapplication.util.showToast
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 歌手主页
 * 展示歌手头像、名称、信息 + 热门歌曲列表
 */
class ArtistActivity : BaseActivity<ActivityArtistBinding>() {

    private val repository = Repository.getInstance()
    private var artistId: Long = 0
    private var cachedSongs: List<SongInfo> = emptyList()
    private var songAdapter: HomeAdapter? = null

    override fun initBinding() = ActivityArtistBinding.inflate(layoutInflater)

    override fun initView() {
        artistId = intent.getLongExtra("artist_id", 0)
        if (artistId == 0L) {
            showToast("歌手信息错误")
            finish()
            return
        }

        // Toolbar 返回
        binding.toolbar.setNavigationOnClickListener { finish() }

        // 歌曲列表：先设置空 adapter 避免 "No adapter attached" 警告
        binding.rvSongs.layoutManager = LinearLayoutManager(this)
        binding.rvSongs.adapter = HomeAdapter()

        // 播放全部
        binding.btnPlayAll.setOnClickListener {
            if (cachedSongs.isEmpty()) {
                showToast("暂无歌曲")
                return@setOnClickListener
            }
            PlayQueueManager.setQueue(cachedSongs, 0)
            val first = cachedSongs[0]
            startPlayer(first, 0)
        }

        loadArtistData()
    }

    private fun loadArtistData() {
        binding.progressBar.visibility = View.VISIBLE

        CoroutineScope(Dispatchers.IO).launch {
            // 同时请求歌手详情和热门歌曲
            val detailResult = repository.getArtistDetail(artistId)
            val songsResult = repository.getArtistSongs(artistId)

            runOnUiThread {
                binding.progressBar.visibility = View.GONE

                // 处理歌手详情（仅用于获取名称，图片等信息由热门歌曲接口提供）
                when (detailResult) {
                    is com.example.myapplication.base.Result.Success -> {
                        val artist = detailResult.data.data?.artist
                        if (artist != null) {
                            binding.tvArtistName.text = artist.name ?: "未知歌手"
                        }
                    }
                    is com.example.myapplication.base.Result.Error -> {
                        // 忽略，使用歌曲接口的数据
                    }
                }

                // 处理热门歌曲
                when (songsResult) {
                    is com.example.myapplication.base.Result.Success -> {
                        val data = songsResult.data
                        val artist = data.artist
                        val hotSongs = data.hotSongs ?: emptyList()

                        if (artist != null) {
                            binding.apply {
                                if (tvArtistName.text.isNullOrEmpty() || tvArtistName.text == "未知歌手") {
                                    tvArtistName.text = artist.name ?: "未知歌手"
                                }
                                if (artist.picUrl != null && ivArtistAvatar.drawable == null) {
                                    ivArtistAvatar.loadImage(artist.picUrl)
                                    ivHeaderBg.loadImage(artist.picUrl)
                                }
                                tvArtistInfo.text = "单曲: ${artist.musicSize} · 专辑: ${artist.albumSize}"
                            }
                        }

                        if (hotSongs.isNotEmpty()) {
                            cachedSongs = hotSongs
                            setupSongList(hotSongs)
                        } else {
                            showToast("暂无热门歌曲")
                        }
                    }
                    is com.example.myapplication.base.Result.Error -> {
                        showToast(songsResult.exception.message ?: "加载失败")
                    }
                }
            }
        }
    }

    private fun setupSongList(songs: List<SongInfo>) {
        val adapter = HomeAdapter().apply {
            onSongClick = { song ->
                val idx = cachedSongs.indexOfFirst { it.id == song.id }
                PlayQueueManager.setQueue(cachedSongs, idx.coerceAtLeast(0))
                startPlayer(song, idx.coerceAtLeast(0))
            }
        }
        val items = songs.map { HomeAdapter.HomeItem.SongRow(it) }
        adapter.submitList(items)
        binding.rvSongs.adapter = adapter
        songAdapter = adapter
    }

    private fun startPlayer(song: SongInfo, index: Int) {
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
