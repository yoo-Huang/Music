package com.example.myapplication.ui.ranking

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.myapplication.data.Repository
import com.example.myapplication.data.remote.MvItem
import com.example.myapplication.data.remote.NewAlbumItem
import com.example.myapplication.data.remote.TopArtistInfo
import com.example.myapplication.data.remote.ToplistItem
import com.example.myapplication.databinding.FragmentRankingBinding
import com.example.myapplication.databinding.ItemRankingArtistBinding
import com.example.myapplication.databinding.ItemRankingPlaylistBinding
import com.example.myapplication.ui.artist.ArtistActivity
import com.example.myapplication.ui.player.MvPlayerActivity
import com.example.myapplication.ui.player.PlayerActivity
import com.example.myapplication.ui.search.SearchActivity
import com.example.myapplication.util.dp2px
import com.example.myapplication.util.loadImage
import com.example.myapplication.util.loadImageCircle
import com.example.myapplication.util.showToast
import com.example.myapplication.viewmodel.RankingViewModel
import com.google.android.material.tabs.TabLayoutMediator

/**
 * 排行榜页 Fragment
 * 使用 ViewPager2 + TabLayout 实现五栏切换：
 * 1. 全部榜单 - 所有榜单摘要列表
 * 2. 歌手榜 - 歌手排行
 * 3. MV 排行 - MV 排行榜
 * 4. 数字专辑榜 - 数字专辑排行
 * 5. 细分榜单 - 分类音乐榜单
 */
class RankingFragment : Fragment() {

    private var _binding: FragmentRankingBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: RankingViewModel
    private val repository = Repository.getInstance()

    companion object {
        private val TAB_TITLES = arrayOf("全部", "歌手", "MV", "数字专辑", "细分")
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRankingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(requireActivity())[RankingViewModel::class.java]

        // 配置 ViewPager2
        binding.viewPager.adapter = RankingPagerAdapter(this)
        binding.viewPager.offscreenPageLimit = 1

        // 绑定 TabLayout 和 ViewPager2
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = TAB_TITLES[position]
        }.attach()

        // 观察加载状态
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        viewModel.errorMsg.observe(viewLifecycleOwner) { error ->
            error?.let { requireContext().showToast(it) }
        }

        // 搜索按钮 → 跳转搜索页
        binding.ivSearch.setOnClickListener {
            startActivity(Intent(requireContext(), SearchActivity::class.java))
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /**
     * ViewPager2 适配器
     */
    private class RankingPagerAdapter(fragment: RankingFragment) : FragmentStateAdapter(fragment) {

        override fun getItemCount(): Int = 5

        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> AllRankingsFragment()
                1 -> ArtistRankingFragment()
                2 -> MvRankingFragment()
                3 -> AlbumRankingFragment()
                4 -> SubRankingsFragment()
                else -> AllRankingsFragment()
            }
        }
    }

    // ==================== 各 Tab 子 Fragment ====================

    /**
     * Tab 1: 全部榜单
     * 展示所有榜单摘要，点击进入榜单歌曲列表
     */
    class AllRankingsFragment : Fragment() {
        private val viewModel by lazy {
            ViewModelProvider(requireActivity())[RankingViewModel::class.java]
        }
        private val adapter by lazy { ToplistAdapter() }

        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
        ): View {
            return RecyclerView(requireContext()).apply {
                layoutManager = LinearLayoutManager(requireContext())
                this.adapter = this@AllRankingsFragment.adapter
                setBackgroundColor(0xFFFFFFFF.toInt())
                clipToPadding = false
                setPadding(paddingLeft, paddingTop, paddingRight, dp2px(context, 128))
            }
        }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            viewModel.toplists.observe(viewLifecycleOwner) { toplists ->
                adapter.submitList(toplists)
            }
            viewModel.loadToplists()
        }
    }

    /**
     * Tab 2: 歌手榜
     */
    class ArtistRankingFragment : Fragment() {
        private val viewModel by lazy {
            ViewModelProvider(requireActivity())[RankingViewModel::class.java]
        }
        private val adapter by lazy { ArtistRankingAdapter() }

        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
        ): View {
            return RecyclerView(requireContext()).apply {
                layoutManager = LinearLayoutManager(requireContext())
                this.adapter = this@ArtistRankingFragment.adapter
                setBackgroundColor(0xFFFFFFFF.toInt())
                clipToPadding = false
                setPadding(paddingLeft, paddingTop, paddingRight, dp2px(context, 128))
            }
        }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            viewModel.artists.observe(viewLifecycleOwner) { artists ->
                adapter.submitList(artists)
            }
            viewModel.loadArtists()
        }
    }

    /**
     * Tab 3: MV 排行
     */
    class MvRankingFragment : Fragment() {
        private val viewModel by lazy {
            ViewModelProvider(requireActivity())[RankingViewModel::class.java]
        }
        private val adapter by lazy { MvRankingAdapter() }

        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
        ): View {
            return RecyclerView(requireContext()).apply {
                layoutManager = LinearLayoutManager(requireContext())
                this.adapter = this@MvRankingFragment.adapter
                setBackgroundColor(0xFFFFFFFF.toInt())
                clipToPadding = false
                setPadding(paddingLeft, paddingTop, paddingRight, dp2px(context, 128))
            }
        }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            viewModel.topMvs.observe(viewLifecycleOwner) { mvs ->
                adapter.submitList(mvs)
            }
            viewModel.loadTopMvs()
        }
    }

    /**
     * Tab 4: 数字专辑榜
     * 使用 /album/newest API 获取最新数字专辑
     */
    class AlbumRankingFragment : Fragment() {
        private val viewModel by lazy {
            ViewModelProvider(requireActivity())[RankingViewModel::class.java]
        }
        private val adapter by lazy { AlbumListAdapter() }

        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
        ): View {
            return RecyclerView(requireContext()).apply {
                layoutManager = LinearLayoutManager(requireContext())
                this.adapter = this@AlbumRankingFragment.adapter
                setBackgroundColor(0xFFFFFFFF.toInt())
                clipToPadding = false
                setPadding(paddingLeft, paddingTop, paddingRight, dp2px(context, 128))
            }
        }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            viewModel.newAlbums.observe(viewLifecycleOwner) { albums ->
                adapter.submitList(albums)
            }
            if (viewModel.newAlbums.value == null) {
                viewModel.loadNewAlbums()
            }
        }
    }

    /**
     * Tab 5: 细分榜单
     * 筛选展示流派/风格细分榜单（说唱、电音、民谣、摇滚等），与"全部"形成区分
     */
    class SubRankingsFragment : Fragment() {
        private val viewModel by lazy {
            ViewModelProvider(requireActivity())[RankingViewModel::class.java]
        }
        private val adapter by lazy { ToplistAdapter() }

        // 细分榜单关键词：流派 + 地区 + 语种
        private val subKeywords = listOf(
            "说唱", "电音", "民谣", "摇滚", "古风", "爵士", "古典", "雷鬼",
            "嘻哈", "R&B", "KTV", "ACG", "电子", "轻音乐",
            "欧美", "韩国", "日本", "内地", "港台", "华语", "粤语",
            "新歌", "原创"
        )

        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
        ): View {
            return RecyclerView(requireContext()).apply {
                layoutManager = LinearLayoutManager(requireContext())
                this.adapter = this@SubRankingsFragment.adapter
                setBackgroundColor(0xFFFFFFFF.toInt())
                clipToPadding = false
                setPadding(paddingLeft, paddingTop, paddingRight, dp2px(context, 128))
            }
        }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            viewModel.toplists.observe(viewLifecycleOwner) { toplists ->
                // 按关键词匹配：展示细分/流派榜单
                val subList = toplists.filter { item ->
                    val name = item.name ?: ""
                    subKeywords.any { kw -> name.contains(kw) }
                }
                // 如果筛选结果为空，回退显示非通用的榜单（排除飙升、热歌等最通用的榜）
                val result = if (subList.isNotEmpty()) {
                    subList
                } else {
                    toplists.filter { item ->
                        val name = item.name ?: ""
                        !name.contains("飙升") && !name.contains("热歌")
                    }
                }
                adapter.submitList(result)
            }
            if (viewModel.toplists.value == null) {
                viewModel.loadToplists()
            }
        }
    }

    // ==================== 榜单列表 Adapter ====================

    class ToplistAdapter : RecyclerView.Adapter<ToplistAdapter.VH>() {
        private var items = listOf<ToplistItem>()

        fun submitList(newItems: List<ToplistItem>) {
            items = newItems
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val binding = ItemRankingPlaylistBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return VH(binding)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.bind(item, position)
        }

        override fun getItemCount(): Int = items.size

        class VH(private val binding: ItemRankingPlaylistBinding) :
            RecyclerView.ViewHolder(binding.root) {

            fun bind(item: ToplistItem, position: Int) {
                binding.apply {
                    tvName.text = item.name ?: "未知榜单"
                    tvFrequency.text = item.updateFrequency ?: ""
                    tvPlayCount.text = "播放量: ${(item.playCount).formatCount()}"
                    ivCover.loadImage(item.coverImgUrl)
                    root.setOnClickListener {
                        // 点击榜单 → 跳转歌单详情页
                        val context = root.context
                        val intent = Intent(context, com.example.myapplication.ui.playlist.PlaylistActivity::class.java).apply {
                            putExtra("playlist_id", item.id)
                        }
                        context.startActivity(intent)
                    }
                }
            }

            private fun Long.formatCount(): String {
                return when {
                    this >= 100_000_000 -> "${this / 100_000_000}.${(this % 100_000_000) / 10_000_000}亿"
                    this >= 10_000 -> "${this / 10_000}.${(this % 10_000) / 1_000}万"
                    else -> this.toString()
                }
            }
        }
    }

    // ==================== 歌手榜 Adapter ====================

    class ArtistRankingAdapter : RecyclerView.Adapter<ArtistRankingAdapter.VH>() {
        private var items = listOf<TopArtistInfo>()

        fun submitList(newItems: List<TopArtistInfo>) {
            items = newItems
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val binding = ItemRankingArtistBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return VH(binding)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.bind(items[position], position)
        }

        override fun getItemCount(): Int = items.size

        class VH(private val binding: ItemRankingArtistBinding) :
            RecyclerView.ViewHolder(binding.root) {

            fun bind(item: TopArtistInfo, position: Int) {
                binding.apply {
                    tvRank.text = String.format("%02d", position + 1)
                    // 前三名红色高亮
                    if (position < 3) {
                        tvRank.setTextColor(0xFFEC4141.toInt())
                    } else {
                        tvRank.setTextColor(0xFF333333.toInt())
                    }
                    tvName.text = item.name ?: "未知歌手"
                    tvPlayCount.text = "热度: ${item.score.formatCount()}"
                    ivAvatar.loadImageCircle(item.picUrl)
                    root.setOnClickListener {
                        val context = root.context
                        val intent = Intent(context, ArtistActivity::class.java).apply {
                            putExtra("artist_id", item.id)
                        }
                        context.startActivity(intent)
                    }
                }
            }

            private fun Long.formatCount(): String {
                return when {
                    this >= 100_000_000 -> "${this / 100_000_000}.${(this % 100_000_000) / 10_000_000}亿"
                    this >= 10_000 -> "${this / 10_000}.${(this % 10_000) / 1_000}万"
                    else -> this.toString()
                }
            }
        }
    }

    // ==================== MV 排行 Adapter ====================

    class MvRankingAdapter : RecyclerView.Adapter<MvRankingAdapter.VH>() {
        private var items = listOf<MvItem>()

        fun submitList(newItems: List<MvItem>) {
            items = newItems
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val binding = ItemRankingArtistBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return VH(binding)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.bind(items[position], position)
        }

        override fun getItemCount(): Int = items.size

        class VH(private val binding: ItemRankingArtistBinding) :
            RecyclerView.ViewHolder(binding.root) {

            fun bind(item: MvItem, position: Int) {
                binding.apply {
                    tvRank.text = String.format("%02d", position + 1)
                    if (position < 3) {
                        tvRank.setTextColor(0xFFEC4141.toInt())
                    } else {
                        tvRank.setTextColor(0xFF333333.toInt())
                    }
                    tvName.text = item.name ?: "未知MV"
                    val artist = item.artistName ?: ""
                    val duration = if (item.duration > 0) {
                        val min = item.duration / 1000 / 60
                        val sec = item.duration / 1000 % 60
                        " · ${"%02d:%02d".format(min, sec)}"
                    } else ""
                    tvPlayCount.text = "${artist}${duration}"
                    ivAvatar.loadImage(item.cover)

                    // 点击跳转 MV 播放页
                    root.setOnClickListener {
                        val context = root.context
                        val intent = Intent(context, MvPlayerActivity::class.java).apply {
                            putExtra("mv_id", item.id)
                            putExtra("mv_name", item.name)
                            putExtra("mv_artist", item.artistName)
                        }
                        context.startActivity(intent)
                    }
                }
            }
        }
    }

    /**
     * 数字专辑列表 Adapter
     * 复用 ItemRankingPlaylistBinding 展示专辑信息
     */
    class AlbumListAdapter : RecyclerView.Adapter<AlbumListAdapter.VH>() {
        private var items = listOf<NewAlbumItem>()

        fun submitList(newItems: List<NewAlbumItem>) {
            items = newItems
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val binding = ItemRankingPlaylistBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return VH(binding)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.bind(items[position], position)
        }

        override fun getItemCount(): Int = items.size

        class VH(private val binding: ItemRankingPlaylistBinding) :
            RecyclerView.ViewHolder(binding.root) {

            fun bind(item: NewAlbumItem, position: Int) {
                binding.apply {
                    tvName.text = item.name ?: "未知专辑"
                    val artistName = item.artist?.name ?: ""
                    tvFrequency.text = item.company ?: ""
                    tvPlayCount.text = "${artistName} · ${item.size}首"
                    ivCover.loadImage(item.picUrl)
                    root.setOnClickListener {
                        // 点击跳转专辑详情页（使用 album_id）
                        val context = root.context
                        val intent = Intent(context, com.example.myapplication.ui.playlist.PlaylistActivity::class.java).apply {
                            putExtra("album_id", item.id)
                        }
                        context.startActivity(intent)
                    }
                }
            }
        }
    }
}
