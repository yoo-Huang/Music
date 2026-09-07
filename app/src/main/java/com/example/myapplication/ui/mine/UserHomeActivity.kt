package com.example.myapplication.ui.mine

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.R
import com.example.myapplication.base.Result
import com.example.myapplication.data.remote.ApiService
import com.example.myapplication.data.remote.LoginUserProfile
import com.example.myapplication.data.remote.UserPlaylistItem
import com.example.myapplication.manager.AccountManager
import com.example.myapplication.ui.playlist.PlaylistActivity
import com.example.myapplication.ui.login.LoginActivity
import com.example.myapplication.util.loadImageCircle
import com.example.myapplication.util.showToast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 用户主页 - 查看其他用户的信息和歌单
 */
class UserHomeActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_UID = "user_uid"
    }

    private var targetUid: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_home)

        targetUid = intent.getLongExtra(EXTRA_UID, 0)
        if (targetUid <= 0) {
            showToast("用户 ID 无效")
            finish()
            return
        }

        findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar).apply {
            title = "用户主页"
            setNavigationOnClickListener { finish() }
        }

        loadUserData()
    }

    private fun loadUserData() {
        lifecycleScope.launch(Dispatchers.IO) {
            // 并行加载用户详情和歌单
            val detailDeferred = launch { loadUserDetail() }
            val playlistDeferred = launch { loadUserPlaylist() }
            detailDeferred.join()
            playlistDeferred.join()
        }
    }

    private suspend fun loadUserDetail() {
        val result = ApiService.getUserDetail(targetUid)
        withContext(Dispatchers.Main) {
            when (result) {
                is Result.Success -> {
                    val profile = result.data.profile
                    val level = result.data.level
                    val listenSongs = result.data.listenSongs
                    bindUserInfo(profile, level, listenSongs)
                }
                is Result.Error -> {
                    showToast("加载用户信息失败")
                }
            }
        }
    }

    private fun bindUserInfo(profile: LoginUserProfile?, level: Int, listenSongs: Int) {
        // 头像
        findViewById<android.widget.ImageView>(R.id.iv_avatar).apply {
            loadImageCircle(
                profile?.avatarUrl?.takeIf { it.isNotBlank() }
                    ?: profile?.avatarUrl,
                R.drawable.ic_person
            )
        }

        // 昵称
        findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar).title =
            profile?.nickname?.takeIf { it.isNotBlank() } ?: "用户主页"

        findViewById<TextView>(R.id.tv_nickname).text =
            profile?.nickname?.takeIf { it.isNotBlank() } ?: "用户${targetUid}"

        // 签名
        val sig = profile?.signature?.takeIf { it.isNotBlank() }
        findViewById<TextView>(R.id.tv_signature).apply {
            visibility = if (sig != null) View.VISIBLE else View.GONE
            text = sig ?: ""
        }

        // 统计
        findViewById<TextView>(R.id.tv_follows).text = (profile?.follows ?: 0).toString()
        findViewById<TextView>(R.id.tv_fans).text = (profile?.followeds ?: 0).toString()
        findViewById<TextView>(R.id.tv_level).text = if (level > 0) "Lv.$level" else "-"
        findViewById<TextView>(R.id.tv_listen).text = listenSongs.toString()
    }

    private suspend fun loadUserPlaylist() {
        val result = ApiService.getUserPlaylist(targetUid)
        withContext(Dispatchers.Main) {
            findViewById<ProgressBar>(R.id.progress_bar).visibility = View.GONE
            when (result) {
                is Result.Success -> {
                    val playlists = result.data.playlist ?: emptyList()
                    if (playlists.isNotEmpty()) {
                        bindPlaylistList(playlists)
                    } else {
                        findViewById<TextView>(R.id.tv_no_playlist).visibility = View.VISIBLE
                    }
                }
                is Result.Error -> {
                    findViewById<TextView>(R.id.tv_no_playlist).apply {
                        visibility = View.VISIBLE
                        text = "加载歌单失败"
                    }
                }
            }
        }
    }

    private fun bindPlaylistList(playlists: List<UserPlaylistItem>) {
        val rv = findViewById<RecyclerView>(R.id.rv_playlists)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = UserPlaylistAdapter(playlists) { playlist ->
            val intent = Intent(this, PlaylistActivity::class.java).apply {
                putExtra("playlist_id", playlist.id)
            }
            startActivity(intent)
        }
    }
}

/**
 * 用户歌单简单适配器
 */
class UserPlaylistAdapter(
    private val items: List<UserPlaylistItem>,
    private val onItemClick: (UserPlaylistItem) -> Unit
) : RecyclerView.Adapter<UserPlaylistAdapter.VH>() {

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VH {
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_user_playlist, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivCover: android.widget.ImageView = itemView.findViewById(R.id.iv_cover)
        private val tvName: TextView = itemView.findViewById(R.id.tv_name)
        private val tvCount: TextView = itemView.findViewById(R.id.tv_count)

        fun bind(item: UserPlaylistItem) {
            ivCover.loadImageCircle(item.coverImgUrl, R.drawable.ic_default_album)
            tvName.text = item.name ?: "未知歌单"
            tvCount.text = "${item.trackCount}首"
            itemView.setOnClickListener { onItemClick(item) }
        }
    }
}
