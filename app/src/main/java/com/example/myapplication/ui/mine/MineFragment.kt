package com.example.myapplication.ui.mine

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.myapplication.R
import com.example.myapplication.base.Result as AppResult
import com.example.myapplication.data.remote.ApiService
import com.example.myapplication.data.remote.RecommendPlaylist
import com.example.myapplication.data.remote.SongInfo
import com.example.myapplication.data.remote.UserPlaylistItem
import com.example.myapplication.databinding.FragmentMineBinding
import com.example.myapplication.manager.AccountManager
import com.example.myapplication.manager.PlayMode
import com.example.myapplication.manager.PlayQueueManager
import com.example.myapplication.ui.login.LoginActivity
import com.example.myapplication.ui.mine.MineAdapter.MineItem
import com.example.myapplication.ui.mine.MineAdapter.MineMenuItem
import com.example.myapplication.ui.player.PlayerActivity
import com.example.myapplication.ui.player.PrivateFmActivity
import com.example.myapplication.ui.daily.DailyRecommendActivity
import com.example.myapplication.ui.playlist.PlaylistActivity
import com.example.myapplication.util.showToast
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * "我的"页面 Fragment
 *
 * 页面结构（从上到下）：
 * 1. 用户头部信息（头像、昵称、等级、关注/粉丝）
 * 2. 最近播放入口
 * 3. 我的歌单（纵向列表，封面+歌名+副标题）
 * 4. 功能菜单分组
 */
class MineFragment : Fragment() {

    private var _binding: FragmentMineBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: MineViewModel
    private lateinit var adapter: MineAdapter
    private var cachedPlaylists: List<UserPlaylistItem> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMineBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(this)[MineViewModel::class.java]

        initAdapter()
        observeData()

        if (AccountManager.isLoggedIn) {
            viewModel.loadUserData()
        } else {
            showNotLoggedIn()
        }
    }

    private fun initAdapter() {
        adapter = MineAdapter().apply {
            // 头像/昵称点击 → 个人资料编辑页
            onHeaderClick = {
                if (AccountManager.isLoggedIn) {
                    startActivity(Intent(requireContext(), ProfileEditActivity::class.java))
                } else {
                    startActivity(Intent(requireContext(), LoginActivity::class.java))
                }
            }

            // 歌单点击 → 歌单详情页
            onPlaylistClick = { playlist ->
                val intent = Intent(requireContext(), PlaylistActivity::class.java)
                intent.putExtra("playlist_id", playlist.id)
                startActivity(intent)
            }

            // 心动按钮点击（"我喜欢的音乐" → 直接调用心动API并播放）
            onHeartbeatClick = { playlist ->
                if (!AccountManager.isLoggedIn) {
                    requireContext().showToast("请先登录")
                } else {
                    requireContext().showToast("正在生成心动推荐...")
                    CoroutineScope(Dispatchers.IO).launch {
                    try {
                        // "喜欢的音乐"歌单是 specialType=5，心动 API 不支持，需找普通歌单
                        val targetPlaylistId = if (playlist.name?.contains("喜欢的音乐", ignoreCase = true) == true) {
                            cachedPlaylists.firstOrNull {
                                it.name?.contains("喜欢的音乐", ignoreCase = true) != true
                            }?.id ?: playlist.id
                        } else {
                            playlist.id
                        }

                        // 步骤1：获取歌单详情，拿到第一首歌ID作为种子
                        val detailResult = ApiService.getPlaylistDetail(playlist.id)
                        if (detailResult !is AppResult.Success || detailResult.data.playlist == null) {
                            activity?.runOnUiThread { requireContext().showToast("无法获取歌单详情") }
                            return@launch
                        }
                        val tracks = detailResult.data.playlist.tracks
                        if (tracks.isNullOrEmpty()) {
                            activity?.runOnUiThread { requireContext().showToast("歌单中没有歌曲") }
                            return@launch
                        }

                        // 步骤2：调用心动模式API获取智能推荐
                        val firstSong = tracks.first()
                        val intelligenceResult = ApiService.getIntelligenceList(
                            id = targetPlaylistId,
                            pid = firstSong.id,
                            count = 30
                        )
                        var songs: List<SongInfo> = if (intelligenceResult is AppResult.Success) {
                            intelligenceResult.data.extractSongs()
                        } else {
                            emptyList()
                        }

                        // 心动 API 不可用时，回退：直接使用歌单歌曲
                        if (songs.isEmpty()) {
                            songs = tracks
                        }

                        if (songs.isEmpty()) {
                            activity?.runOnUiThread { requireContext().showToast("暂无推荐歌曲") }
                            return@launch
                        }

                        // 步骤3：设置播放队列并跳转播放器
                        // 回退模式下使用原始歌单 ID，方便 MusicService 续播时用歌单详情获取歌曲
                        val heartbeatId = if (songs === tracks) playlist.id else targetPlaylistId
                        activity?.runOnUiThread {
                            PlayQueueManager.setQueue(songs, 0)
                            PlayQueueManager.heartbeatPlaylistId = heartbeatId
                            PlayQueueManager.setPlayMode(PlayMode.HEARTBEAT)
                            val intent = Intent(requireContext(), PlayerActivity::class.java).apply {
                                putExtra("song_id", songs[0].id)
                                putExtra("song_name", songs[0].name ?: "未知歌曲")
                                putExtra("artist_name", songs[0].artistNames)
                                putExtra("album_pic_url", songs[0].al?.picUrl)
                                putExtra("duration", songs[0].dt)
                                putExtra("playlist_json", Gson().toJson(songs))
                                putExtra("play_mode", PlayMode.HEARTBEAT.value)
                                putExtra("show_mini_player", true)
                            }
                            startActivity(intent)
                        }
                    } catch (e: Exception) {
                        activity?.runOnUiThread {
                            requireContext().showToast("心动推荐失败: ${e.message}")
                        }
                    }
                }
            }
            }

            // 菜单项点击
            onMenuClick = { menuItem ->
                handleMenuClick(menuItem)
            }

            // 设置图标点击（右上角）
            onSettingsClick = {
                startActivity(Intent(requireContext(), com.example.myapplication.ui.mine.SettingsActivity::class.java))
            }

            // 登录按钮点击
            onLoginClick = {
                startActivity(Intent(requireContext(), LoginActivity::class.java))
            }

            // 未登录推荐歌单点击 → 跳转到歌单详情页
            onGuestPlaylistClick = { playlist ->
                val intent = Intent(requireContext(), PlaylistActivity::class.java)
                intent.putExtra("playlist_id", playlist.id)
                startActivity(intent)
            }

            // 关注列表点击
            onFollowsClick = {
                if (!AccountManager.isLoggedIn) {
                    startActivity(Intent(requireContext(), LoginActivity::class.java))
                } else {
                    val intent = Intent(requireContext(), FollowListActivity::class.java).apply {
                        putExtra(FollowListActivity.EXTRA_TYPE, "follows")
                        putExtra(FollowListActivity.EXTRA_UID, AccountManager.userId)
                    }
                    startActivity(intent)
                }
            }

            // 粉丝列表点击
            onFollowedsClick = {
                if (!AccountManager.isLoggedIn) {
                    startActivity(Intent(requireContext(), LoginActivity::class.java))
                } else {
                    val intent = Intent(requireContext(), FollowListActivity::class.java).apply {
                        putExtra(FollowListActivity.EXTRA_TYPE, "followeds")
                        putExtra(FollowListActivity.EXTRA_UID, AccountManager.userId)
                    }
                    startActivity(intent)
                }
            }

            // 状态区域点击 → 状态编辑页
            onStatusClick = {
                if (AccountManager.isLoggedIn) {
                    startActivity(Intent(requireContext(), StatusEditActivity::class.java))
                }
            }

            // 徽章点击 → 徽章墙
            onBadgeClick = {
                if (AccountManager.isLoggedIn) {
                    startActivity(Intent(requireContext(), BadgeWallActivity::class.java))
                }
            }
        }

        binding.rvMine.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@MineFragment.adapter
        }
    }

    private fun handleMenuClick(menuItem: MineMenuItem) {
        when (menuItem.name) {
            "最近播放" -> {
                startActivity(Intent(requireContext(), RecentListenActivity::class.java))
            }
            "私人FM" -> {
                if (!AccountManager.isLoggedIn) {
                    startActivity(Intent(requireContext(), LoginActivity::class.java))
                } else {
                    startActivity(Intent(requireContext(), PrivateFmActivity::class.java))
                }
            }
            "开通黑胶VIP", "免费听歌" -> {
                if (!AccountManager.isLoggedIn) {
                    startActivity(Intent(requireContext(), LoginActivity::class.java))
                }
            }
            "每日推荐" -> {
                if (!AccountManager.isLoggedIn) {
                    startActivity(Intent(requireContext(), LoginActivity::class.java))
                } else {
                    startActivity(Intent(requireContext(), DailyRecommendActivity::class.java))
                }
            }
            "设置" -> {
                startActivity(Intent(requireContext(), SettingsActivity::class.java))
            }
        }
    }

    private fun observeData() {
        viewModel.isLoading.observe(viewLifecycleOwner) { }

        viewModel.userProfile.observe(viewLifecycleOwner) { profile ->
            if (AccountManager.isLoggedIn && profile != null) {
                buildDataList()
            }
        }

        viewModel.userLevel.observe(viewLifecycleOwner) {
            if (AccountManager.isLoggedIn) buildDataList()
        }

        viewModel.listenSongs.observe(viewLifecycleOwner) {
            if (AccountManager.isLoggedIn) buildDataList()
        }

        viewModel.createdPlaylists.observe(viewLifecycleOwner) {
            if (AccountManager.isLoggedIn) buildDataList()
        }

        viewModel.subscribedPlaylists.observe(viewLifecycleOwner) {
            if (AccountManager.isLoggedIn) buildDataList()
        }

        viewModel.userStatus.observe(viewLifecycleOwner) {
            if (AccountManager.isLoggedIn) buildDataList()
        }

        viewModel.userBadges.observe(viewLifecycleOwner) {
            if (AccountManager.isLoggedIn) buildDataList()
        }

        viewModel.errorMsg.observe(viewLifecycleOwner) { msg ->
            msg?.let {
                requireContext().showToast(it)
                viewModel.clearError()
            }
        }

        // 未登录时推荐歌单数据观察
        viewModel.guestPlaylists.observe(viewLifecycleOwner) {
            if (!AccountManager.isLoggedIn) {
                buildGuestDataList()
            }
        }
    }

    /**
     * 构建列表数据
     */
    private fun buildDataList() {
        val items = mutableListOf<MineItem>()

        // 1. 用户头部
        val profile = viewModel.userProfile.value
        val level = viewModel.userLevel.value ?: 0
        val listenSongs = viewModel.listenSongs.value ?: 0L
        val status = viewModel.userStatus.value
        val badges = viewModel.userBadges.value ?: emptyList()
        items.add(MineItem.Header(
            profile = profile,
            level = level,
            listenSongs = listenSongs,
            status = status,
            badges = badges
        ))

        // 分割线
        items.add(MineItem.Divider)

        // 2. 最近播放入口
        items.add(MineItem.MenuGroup(
            listOf(
                MineMenuItem("最近播放", R.drawable.ic_history)
            )
        ))

        // 3. 我的歌单（合并创建+收藏，统一为列表）
        val createdPlaylists = viewModel.createdPlaylists.value ?: emptyList()
        val subscribedPlaylists = viewModel.subscribedPlaylists.value ?: emptyList()

        // 合并歌单：创建的歌单在前，收藏的在后面
        val allPlaylists = createdPlaylists + subscribedPlaylists
        cachedPlaylists = allPlaylists

        if (allPlaylists.isNotEmpty()) {
            items.add(MineItem.Divider)

            // 将"我喜欢的音乐"排在首位
            val likedMusicIndex = allPlaylists.indexOfFirst {
                it.name?.contains("喜欢的音乐", ignoreCase = true) == true
            }
            val orderedPlaylists = if (likedMusicIndex >= 0) {
                val list = mutableListOf<UserPlaylistItem>()
                list.add(allPlaylists[likedMusicIndex])
                list.addAll(allPlaylists.filterIndexed { i, _ -> i != likedMusicIndex })
                list
            } else {
                allPlaylists
            }

            items.add(MineItem.SectionTitle("我的歌单", orderedPlaylists.size))
            orderedPlaylists.forEach { playlist ->
                val isLikedMusic = playlist.name?.contains("喜欢的音乐", ignoreCase = true) == true
                items.add(MineItem.PlaylistItem(playlist = playlist, isLikedMusic = isLikedMusic))
            }
        }

        // 4. 更多功能
        items.add(MineItem.Divider)
        items.add(MineItem.MenuGroup(
            listOf(
                MineMenuItem("每日推荐", R.drawable.ic_calendar_today, "根据口味，每天推荐好音乐"),
                MineMenuItem("私人FM", R.drawable.ic_music_note, "无限推荐好音乐"),
                MineMenuItem("设置", R.drawable.ic_settings_mine, "音质/深色模式等")
            )
        ))

        adapter.submitList(items)
    }

    /**
     * 显示未登录状态
     * 包含：登录引导头部 + 功能菜单 + 推荐歌单
     */
    private fun showNotLoggedIn() {
        buildGuestDataList()
        viewModel.loadGuestData()
    }

    /**
     * 构建未登录状态列表
     */
    private fun buildGuestDataList() {
        val items = mutableListOf<MineItem>()

        // 1. 登录引导头部
        items.add(MineItem.GuestHeader)

        // 2. 功能菜单
        items.add(MineItem.MenuGroup(
            listOf(
                MineMenuItem("开通黑胶VIP", R.drawable.ic_vip_gold, "享VIP特权"),
                MineMenuItem("私人FM", R.drawable.ic_music_note, "无限推荐好音乐"),
                MineMenuItem("每日推荐", R.drawable.ic_favorite_playlist, "发现好音乐"),
                MineMenuItem("设置", R.drawable.ic_settings_mine, "音质/深色模式等")
            )
        ))

        // 3. 推荐歌单
        val guestPlaylists = viewModel.guestPlaylists.value ?: emptyList()
        if (guestPlaylists.isNotEmpty()) {
            items.add(MineItem.Divider)
            items.add(MineItem.SectionTitle("推荐歌单", guestPlaylists.size, showArrow = false))
            guestPlaylists.forEach { playlist ->
                items.add(MineItem.GuestPlaylistItem(playlist))
            }
        }

        adapter.submitList(items)
    }

    override fun onResume() {
        super.onResume()
        if (AccountManager.isLoggedIn) {
            viewModel.loadUserData()
        } else {
            showNotLoggedIn()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
