package com.example.myapplication.ui.home

import android.animation.ObjectAnimator
import android.util.Log
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Typeface
import android.os.Bundle
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.viewpager2.widget.ViewPager2
import com.example.myapplication.R
import com.example.myapplication.adapter.HomeAdapter
import com.example.myapplication.base.Result as AppResult
import com.example.myapplication.data.Repository
import com.example.myapplication.data.remote.ApiService
import com.example.myapplication.data.remote.SongInfo
import com.example.myapplication.databinding.FragmentHomeBinding
import com.example.myapplication.databinding.PageCoverBinding
import com.example.myapplication.databinding.PageHomeHeartbeatBinding
import com.example.myapplication.databinding.PageHomeRecommendBinding
import com.example.myapplication.databinding.PageHomeSonglistBinding
import com.example.myapplication.databinding.PageLyricBinding
import com.example.myapplication.manager.AccountManager
import com.example.myapplication.manager.FavoriteManager
import com.example.myapplication.manager.PlayMode
import com.example.myapplication.manager.PlayQueueManager
import com.example.myapplication.service.MusicService
import com.example.myapplication.ui.login.LoginActivity
import com.example.myapplication.ui.playlist.PlaylistActivity
import com.example.myapplication.ui.player.LyricAdapter
import com.example.myapplication.ui.player.PlayerActivity
import com.example.myapplication.util.formatDuration
import com.example.myapplication.util.loadImageCircle
import com.example.myapplication.util.showToast
import com.example.myapplication.viewmodel.MainViewModel
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

/**
 * 发现页 Fragment
 * 顶部横向可滑动分类标签 + ViewPager2 切换内容 + 私人FM独立按钮
 * 标签：推荐 | 每日推荐 | 电台
 */
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: MainViewModel
    private var homeAdapter: HomeAdapter = HomeAdapter()
    private val repository = Repository.getInstance()

    private var cachedBanners: List<com.example.myapplication.data.remote.BannerItem>? = null
    private var cachedPlaylists: List<com.example.myapplication.data.remote.RecommendPlaylist>? = null
    private var cachedNewSongs: List<com.example.myapplication.data.remote.SongInfo>? = null

    // 标签相关（私人FM已改为独立按钮）
    private val tabTitles = listOf("推荐", "每日推荐", "电台", "心动")
    private val pageCount = 4
    private val tabViews = mutableListOf<TextView>()
    private var currentTabIndex = 0 // 默认选中"推荐"

    // ViewPager2 页面绑定
    private var recommendPageBinding: PageHomeRecommendBinding? = null
    private var swipeRefresh: SwipeRefreshLayout? = null
    private var rvHome: RecyclerView? = null

    // "每日推荐"和"私人FM"标签页
    private val tabPageBindings = mutableMapOf<Int, PageHomeSonglistBinding>()
    private val tabPageAdapters = mutableMapOf<Int, HomeAdapter>()
    private val tabPageData = mutableMapOf<Int, MutableList<SongInfo>>()

    // 心动页绑定
    private var heartbeatPageBinding: PageHomeHeartbeatBinding? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(requireActivity())[MainViewModel::class.java]

        // 初始化标签栏
        initTabs()

        // 初始化 ViewPager2
        initViewPager()

        // 搜索按钮
        binding.ivSearch.setOnClickListener {
            startActivity(Intent(requireContext(), com.example.myapplication.ui.search.SearchActivity::class.java))
        }

        // 私人FM按钮
        binding.btnPrivateFm.setOnClickListener { launchPrivateFm() }

        // 观察数据
        viewModel.banners.observe(viewLifecycleOwner) { banners ->
            cachedBanners = banners
            refreshHomeList()
        }

        viewModel.playlists.observe(viewLifecycleOwner) { playlists ->
            cachedPlaylists = playlists
            refreshHomeList()
        }

        viewModel.newSongs.observe(viewLifecycleOwner) { songs ->
            cachedNewSongs = songs
            refreshHomeList()
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            swipeRefresh?.isRefreshing = isLoading
        }

        viewModel.errorMsg.observe(viewLifecycleOwner) { error ->
            error?.let { requireContext().showToast(it) }
        }

        // 首次加载
        viewModel.loadHomeData()
    }

    // ==================== 标签栏初始化 ====================

    private fun initTabs() {
        val container = binding.llTabContainer
        container.removeAllViews()
        tabViews.clear()

        for ((index, title) in tabTitles.withIndex()) {
            val tv = TextView(requireContext()).apply {
                text = title
                textSize = 15f
                gravity = Gravity.CENTER
                setPadding(dp2px(12), 0, dp2px(12), 0)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
                )
                setOnClickListener { selectTab(index) }
            }
            tabViews.add(tv)
            container.addView(tv)
        }

        updateTabStyle(currentTabIndex)
    }

    private fun selectTab(index: Int) {
        if (index == currentTabIndex && binding.vpContent.currentItem == index) return

        val wasHeartbeatPage = PlayQueueManager.isHeartbeatPageVisible
        currentTabIndex = index
        // 仅心动 tab 可见时隐藏迷你播放栏
        PlayQueueManager.isHeartbeatPageVisible = (index == 3)
        // 切入心动tab时立即隐藏迷你播放栏（不等播放状态回调，避免闪现）
        if (!wasHeartbeatPage && PlayQueueManager.isHeartbeatPageVisible) {
            (requireActivity() as? com.example.myapplication.base.BaseActivity<*>)?.hideMiniPlayer()
        }
        // 离开心动tab时主动恢复迷你播放栏
        if (wasHeartbeatPage && !PlayQueueManager.isHeartbeatPageVisible) {
            (requireActivity() as? com.example.myapplication.base.BaseActivity<*>)?.refreshMiniPlayer()
        }
        updateTabStyle(index)
        scrollTabToVisible(index)
        binding.vpContent.setCurrentItem(index, false)

        when (index) {
            0 -> ensureRecommendPageBound()
            1 -> loadDailyRecommend()
            // "电台"页的加载在 setupRadioPage() 中触发，避免时序问题
            3 -> loadHeartbeatContent()
        }
    }

    /**
     * 更新所有标签样式：选中=红色加粗+下划线，未选中=浅灰色常规
     */
    private fun updateTabStyle(selectedIndex: Int) {
        for ((i, tv) in tabViews.withIndex()) {
            if (i == selectedIndex) {
                tv.setTextColor(0xFFCC3333.toInt())
                tv.setTypeface(Typeface.DEFAULT_BOLD)
                tv.background = resources.getDrawable(R.drawable.tab_indicator, null)
            } else {
                tv.setTextColor(0xFF999999.toInt())
                tv.setTypeface(Typeface.DEFAULT)
                tv.background = null
            }
        }
    }

    /**
     * 让 HorizontalScrollView 滚动到选中的标签可见
     */
    private fun scrollTabToVisible(index: Int) {
        if (index < tabViews.size) {
            val target = tabViews[index]
            binding.hsvTabs.post {
                val scrollX = target.left - (binding.hsvTabs.width - target.width) / 2
                binding.hsvTabs.smoothScrollTo(scrollX.coerceAtLeast(0), 0)
            }
        }
    }

    // ==================== ViewPager2 初始化 ====================

    private fun initViewPager() {
        binding.vpContent.adapter = HomePagerAdapter()
        binding.vpContent.offscreenPageLimit = 1
        binding.vpContent.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                selectTab(position)
            }
        })
        binding.vpContent.setCurrentItem(currentTabIndex, false)
    }

    /**
     * ViewPager2 适配器
     * 第0页("推荐") = 推荐页内容
     * 第1页("每日推荐") = 每日推荐歌曲列表
     * 第2页("电台") = 电台节目列表
     * 第3页("心动") = 内嵌心动播放器
     */
    private inner class HomePagerAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        private val TYPE_RECOMMEND = 0
        private val TYPE_SONGLIST = 1
        private val TYPE_HEARTBEAT = 2

        override fun getItemViewType(position: Int): Int {
            return when (position) {
                0 -> TYPE_RECOMMEND
                3 -> TYPE_HEARTBEAT
                else -> TYPE_SONGLIST
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return when (viewType) {
                TYPE_RECOMMEND -> {
                    val binding = PageHomeRecommendBinding.inflate(
                        LayoutInflater.from(parent.context), parent, false
                    )
                    RecommendViewHolder(binding)
                }
                TYPE_HEARTBEAT -> {
                    val binding = PageHomeHeartbeatBinding.inflate(
                        LayoutInflater.from(parent.context), parent, false
                    )
                    HeartbeatViewHolder(binding)
                }
                else -> {
                    val binding = PageHomeSonglistBinding.inflate(
                        LayoutInflater.from(parent.context), parent, false
                    )
                    SongListViewHolder(binding)
                }
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            holder.itemView.tag = position
            if (holder is RecommendViewHolder && recommendPageBinding == null) {
                recommendPageBinding = holder.binding
                swipeRefresh = holder.binding.swipeRefresh
                rvHome = holder.binding.rvHome
                setupRecommendPage()
            }
            if (holder is SongListViewHolder) {
                tabPageBindings[position] = holder.binding
                setupSongListPage(position, holder.binding)
            }
            if (holder is HeartbeatViewHolder) {
                heartbeatPageBinding = holder.binding
                setupHeartbeatPage()
            }
        }

        override fun getItemCount(): Int = pageCount
    }

    private class RecommendViewHolder(val binding: PageHomeRecommendBinding) :
        RecyclerView.ViewHolder(binding.root)

    private class SongListViewHolder(val binding: PageHomeSonglistBinding) :
        RecyclerView.ViewHolder(binding.root)

    private class HeartbeatViewHolder(val binding: PageHomeHeartbeatBinding) :
        RecyclerView.ViewHolder(binding.root)

    // ==================== 推荐页（原有内容） ====================

    private fun ensureRecommendPageBound() {
        if (swipeRefresh == null) return
        if (rvHome == null || rvHome?.adapter !== homeAdapter) {
            setupRecommendPage()
        } else {
            // 已经绑定但确保数据是最新的
            refreshHomeList()
        }
    }

    private fun setupRecommendPage() {
        val refresh = swipeRefresh ?: return
        val recycler = rvHome ?: return

        homeAdapter = HomeAdapter().apply {
            onSongClick = { song ->
                cachedNewSongs?.let { songs ->
                    PlayQueueManager.setQueue(songs, songs.indexOfFirst { it.id == song.id })
                }
                val intent = Intent(requireContext(), PlayerActivity::class.java).apply {
                    putExtra("song_id", song.id)
                    putExtra("song_name", song.name ?: "未知歌曲")
                    putExtra("artist_name", song.artistNames)
                    putExtra("album_pic_url", song.al?.picUrl)
                    putExtra("duration", song.dt)
                    cachedNewSongs?.let { putExtra("playlist_json", Gson().toJson(it)) }
                }
                startActivity(intent)
            }
            onPlaylistClick = { playlist ->
                val intent = Intent(requireContext(), PlaylistActivity::class.java).apply {
                    putExtra("playlist_id", playlist.id)
                }
                startActivity(intent)
            }
            onFavoriteClick = { song ->
                toggleFavorite(song)
            }
        }

        recycler.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = homeAdapter
        }

        refresh.setColorSchemeResources(R.color.red_primary)
        refresh.setOnRefreshListener {
            cachedBanners = null
            cachedPlaylists = null
            cachedNewSongs = null
            viewModel.loadHomeData()
        }

        recycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                val lm = recyclerView.layoutManager as? LinearLayoutManager ?: return
                refresh.isEnabled = lm.findFirstVisibleItemPosition() == 0
            }
        })

        refreshHomeList()
    }

    // ==================== 歌曲列表标签页（每日推荐） ====================

    /**
     * 初始化歌曲列表标签页
     */
    private fun setupSongListPage(pageIndex: Int, pageBinding: PageHomeSonglistBinding) {
        if (tabPageAdapters.containsKey(pageIndex)) return

        // 电台页使用独立的 Adapter 和设置
        if (pageIndex == 2) {
            setupRadioPage(pageIndex, pageBinding)
            return
        }

        val adapter = HomeAdapter().apply {
            onSongClick = { song ->
                val songs = tabPageData[pageIndex]
                if (songs != null) {
                    PlayQueueManager.setQueue(songs, songs.indexOfFirst { it.id == song.id })
                    val intent = Intent(requireContext(), PlayerActivity::class.java).apply {
                        putExtra("song_id", song.id)
                        putExtra("song_name", song.name ?: "未知歌曲")
                        putExtra("artist_name", song.artistNames)
                        putExtra("album_pic_url", song.al?.picUrl)
                        putExtra("duration", song.dt)
                        putExtra("playlist_json", Gson().toJson(songs))
                    }
                    startActivity(intent)
                }
            }
            onFavoriteClick = { song ->
                toggleFavorite(song)
            }
        }

        tabPageAdapters[pageIndex] = adapter
        tabPageData[pageIndex] = mutableListOf()

        pageBinding.rvSongs.apply {
            layoutManager = LinearLayoutManager(requireContext())
            this.adapter = adapter
        }

        pageBinding.swipeRefresh.setColorSchemeResources(R.color.red_primary)
        pageBinding.swipeRefresh.setOnRefreshListener {
            if (pageIndex == 1) loadDailyRecommend()
        }
    }

    /**
     * 加载每日推荐歌曲
     */
    private fun loadDailyRecommend() {
        val pageIndex = 1
        val binding = tabPageBindings[pageIndex] ?: return
        val adapter = tabPageAdapters[pageIndex] ?: return
        val data = tabPageData[pageIndex] ?: return

        // 已加载过不重复请求
        if (data.isNotEmpty()) {
            binding.tvEmpty.visibility = View.GONE
            return
        }

        if (!AccountManager.isLoggedIn) {
            binding.tvEmpty.text = "请先登录"
            binding.tvEmpty.visibility = View.VISIBLE
            binding.progressBar.visibility = View.GONE
            return
        }

        binding.progressBar.visibility = View.VISIBLE
        binding.tvEmpty.text = "加载中..."

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = repository.getDailyRecommendSongs()
                when (result) {
                    is AppResult.Success -> {
                        val songs = result.data.data?.dailySongs ?: emptyList()
                        repository.markLikedStatus(songs)
                        activity?.runOnUiThread {
                            binding.progressBar.visibility = View.GONE
                            data.clear()
                            data.addAll(songs)
                            val items = data.map { HomeAdapter.HomeItem.SongRow(it) }
                            adapter.submitList(items)
                            if (songs.isEmpty()) {
                                binding.tvEmpty.text = "暂无推荐歌曲"
                                binding.tvEmpty.visibility = View.VISIBLE
                            } else {
                                binding.tvEmpty.visibility = View.GONE
                            }
                            binding.swipeRefresh.isRefreshing = false
                        }
                    }
                    is AppResult.Error -> {
                        activity?.runOnUiThread {
                            binding.progressBar.visibility = View.GONE
                            binding.tvEmpty.text = "加载失败：${result.exception.message}"
                            binding.tvEmpty.visibility = View.VISIBLE
                            binding.swipeRefresh.isRefreshing = false
                        }
                    }
                }
            } catch (e: Exception) {
                activity?.runOnUiThread {
                    binding.progressBar.visibility = View.GONE
                    binding.tvEmpty.text = "加载失败：${e.message}"
                    binding.tvEmpty.visibility = View.VISIBLE
                    binding.swipeRefresh.isRefreshing = false
                }
            }
        }
    }

    // ==================== 电台标签页 ====================

    private fun loadRadioPage() {
        val pageIndex = 2
        val binding = tabPageBindings[pageIndex] ?: return
        val adapter = tabPageAdapters[pageIndex] ?: return

        binding.progressBar.visibility = View.VISIBLE
        binding.tvEmpty.text = "正在加载电台..."
        viewModel.loadRadioData()
    }

    /** 电台子分类在列表中的起始位置（item index） */
    private val radioSectionPositions = mutableMapOf<String, Int>()

    /** 当前电台页所有可播放的节目列表（用于构建播放队列） */
    private var radioPrograms: List<com.example.myapplication.data.remote.DjProgramItem> = emptyList()

    /** 用户点击子分类按钮后，待滚动到的目标 section（数据加载完成后执行） */
    private var pendingRadioScrollSection: String? = null

    /** 执行子分类滚动（在数据就位后调用） */
    private fun performRadioScroll(pageBinding: PageHomeSonglistBinding, selected: String) {
        val pos = radioSectionPositions[selected] ?: return
        val lm = pageBinding.rvSongs.layoutManager as? LinearLayoutManager ?: return
        updateRadioSubTabStyle(pageBinding, selected)
        pageBinding.rvSongs.post {
            lm.scrollToPositionWithOffset(pos, 0)
        }
    }

    private fun setupRadioPage(pageIndex: Int, pageBinding: PageHomeSonglistBinding) {
        if (tabPageAdapters.containsKey(pageIndex)) return

        val adapter = HomeAdapter().apply {
            onRadioClick = { program ->
                // 找到节目在 radioPrograms 中的索引，传入全部列表构建播放队列
                val index = radioPrograms.indexOfFirst { it.id == program.id }.coerceAtLeast(0)
                playRadioProgram(program, radioPrograms, index)
            }
            onRadioStationClick = { station ->
                // 电台站点击：获取该电台节目列表后播放第一首
                playRadioStation(station)
            }
        }

        tabPageAdapters[pageIndex] = adapter

        pageBinding.rvSongs.apply {
            layoutManager = LinearLayoutManager(requireContext())
            this.adapter = adapter
        }

        // 数据加载前隐藏子按钮，避免闪烁
        pageBinding.radioSubTabs.visibility = View.VISIBLE
        pageBinding.radioTabRecommend.visibility = View.GONE
        pageBinding.radioTabCategory.visibility = View.GONE
        pageBinding.radioTabCategoryRec.visibility = View.GONE

        // 子分类按钮点击：记录目标，数据就位后滚动
        pageBinding.radioTabRecommend.setOnClickListener {
            pendingRadioScrollSection = "recommend"
            performRadioScroll(pageBinding, "recommend")
        }
        pageBinding.radioTabCategory.setOnClickListener {
            pendingRadioScrollSection = "category"
            performRadioScroll(pageBinding, "category")
        }
        pageBinding.radioTabCategoryRec.setOnClickListener {
            pendingRadioScrollSection = "categoryRec"
            performRadioScroll(pageBinding, "categoryRec")
        }

        // 观察电台组合数据（原子更新，只触发一次 rebuild，避免闪烁）
        viewModel.radioPageData.observe(viewLifecycleOwner) { data ->
            rebuildRadioPage(pageBinding, adapter, data)
        }

        viewModel.radioLoading.observe(viewLifecycleOwner) { loading ->
            if (loading) {
                pageBinding.progressBar.visibility = View.VISIBLE
                pageBinding.tvEmpty.visibility = View.GONE
            } else {
                pageBinding.progressBar.visibility = View.GONE
            }
        }

        pageBinding.swipeRefresh.setColorSchemeResources(R.color.red_primary)
        pageBinding.swipeRefresh.setOnRefreshListener {
            viewModel._radioRecommend.value = null
            viewModel._radioPrograms.value = null
            viewModel._radioCategoryRecommend.value = null
            viewModel._radioPageData.value = null
            radioSectionPositions.clear()
            viewModel.loadRadioData()
        }

        // 立即触发加载（在页面绑定完成后执行，解决时序问题）
        loadRadioPage()
    }

    /** 更新子分类按钮选中样式 */
    private fun updateRadioSubTabStyle(binding: PageHomeSonglistBinding, selected: String) {
        val redBg = android.graphics.drawable.ColorDrawable(0xFFE85043.toInt())
        val grayBg = android.graphics.drawable.ColorDrawable(0xFFF0F0F0.toInt())

        binding.radioTabRecommend.background = if (selected == "recommend") redBg else grayBg
        binding.radioTabRecommend.setTextColor(if (selected == "recommend") 0xFFFFFFFF.toInt() else 0xFF666666.toInt())

        binding.radioTabCategory.background = if (selected == "category") redBg else grayBg
        binding.radioTabCategory.setTextColor(if (selected == "category") 0xFFFFFFFF.toInt() else 0xFF666666.toInt())

        binding.radioTabCategoryRec.background = if (selected == "categoryRec") redBg else grayBg
        binding.radioTabCategoryRec.setTextColor(if (selected == "categoryRec") 0xFFFFFFFF.toInt() else 0xFF666666.toInt())
    }

    /**
     * 根据三类数据重建电台页列表，每个分类加上标题
     */
    private fun rebuildRadioPage(
        pageBinding: PageHomeSonglistBinding,
        adapter: HomeAdapter,
        data: com.example.myapplication.viewmodel.MainViewModel.RadioPageData? = null
    ) {
        val recommend = data?.recommend ?: viewModel.radioRecommend.value ?: emptyList()
        val programs = data?.programs ?: viewModel.radioPrograms.value ?: emptyList()
        val categoryRecommend = data?.categoryRecommend ?: viewModel.radioCategoryRecommend.value ?: emptyList()

        // 收集所有 DjProgramItem 用于构建播放队列
        radioPrograms = programs + categoryRecommend

        pageBinding.swipeRefresh.isRefreshing = false

        val items = mutableListOf<HomeAdapter.HomeItem>()
        radioSectionPositions.clear()

        // 1. 电台 - 推荐（电台站）
        if (recommend.isNotEmpty()) {
            radioSectionPositions["recommend"] = items.size
            items.add(HomeAdapter.HomeItem.SectionTitle("电台 - 推荐"))
            recommend.forEach { items.add(HomeAdapter.HomeItem.RadioStationRow(it)) }
        }

        // 2. 电台 - 分类（24小时排行榜）
        if (programs.isNotEmpty()) {
            radioSectionPositions["category"] = items.size
            items.add(HomeAdapter.HomeItem.SectionTitle("电台 - 分类"))
            programs.forEach { items.add(HomeAdapter.HomeItem.RadioRow(it)) }
        }

        // 3. 电台 - 分类推荐（今日优选）
        if (categoryRecommend.isNotEmpty()) {
            radioSectionPositions["categoryRec"] = items.size
            items.add(HomeAdapter.HomeItem.SectionTitle("电台 - 分类推荐"))
            categoryRecommend.forEach { items.add(HomeAdapter.HomeItem.RadioRow(it)) }
        }

        if (items.isEmpty()) {
            pageBinding.tvEmpty.text = "暂无电台节目"
            pageBinding.tvEmpty.visibility = View.VISIBLE
        } else {
            pageBinding.tvEmpty.visibility = View.GONE
        }

        // 只显示有数据的分类按钮
        pageBinding.radioTabRecommend.visibility = if (recommend.isNotEmpty()) View.VISIBLE else View.GONE
        pageBinding.radioTabCategory.visibility = if (programs.isNotEmpty()) View.VISIBLE else View.GONE
        pageBinding.radioTabCategoryRec.visibility = if (categoryRecommend.isNotEmpty()) View.VISIBLE else View.GONE

        adapter.submitList(items)

        // 数据刷新后，检查是否有待滚动的 section（用户点击子按钮但数据未就位时）
        val pending = pendingRadioScrollSection
        if (pending != null) {
            pendingRadioScrollSection = null
            pageBinding.rvSongs.post {
                performRadioScroll(pageBinding, pending)
            }
        }
    }

    /**
     * 播放电台站：先获取该电台的节目列表，再播放第一首
     */
    private fun playRadioStation(station: com.example.myapplication.data.remote.DjRadioItem) {
        requireContext().showToast("正在加载 ${station.name ?: "电台"}...")
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = repository.getDjRadioPrograms(station.id, limit = 50)
                when (result) {
                    is AppResult.Success -> {
                        val programs = result.data.programs ?: emptyList()
                        if (programs.isEmpty()) {
                            activity?.runOnUiThread {
                                if (isAdded) requireContext().showToast("${station.name ?: "该电台"}暂无节目")
                            }
                            return@launch
                        }
                        Log.d("HomeFragment", "Radio station ${station.name} has ${programs.size} programs")
                        playRadioProgram(programs.first(), programs, 0)
                    }
                    is AppResult.Error -> {
                        val msg = result.exception.message ?: "获取节目列表失败"
                        activity?.runOnUiThread {
                            if (isAdded) requireContext().showToast(if (msg.contains("301")) "登录已过期，请重新登录" else msg)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("HomeFragment", "playRadioStation error: ", e)
                activity?.runOnUiThread {
                    if (isAdded) requireContext().showToast("电台加载失败：${e.message}")
                }
            }
        }
    }

    /**
     * 播放电台节目（支持队列播放 + 多级 URL 获取兜底）
     * 并行预取所有节目的 mainSong，构建完整可切歌的播放队列
     * @param program 当前点击的节目
     * @param allPrograms 全部可播放节目列表（用于构建播放队列）
     * @param index 当前节目在列表中的索引
     */
    private fun playRadioProgram(
        program: com.example.myapplication.data.remote.DjProgramItem,
        allPrograms: List<com.example.myapplication.data.remote.DjProgramItem>,
        index: Int
    ) {
        // 注意：此函数可能被 IO 线程调用，Toast 必须在主线程
        activity?.runOnUiThread { if (isAdded) requireContext().showToast("正在加载 ${program.name ?: "电台节目"}...") }
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 1. 获取当前节目详情（含 mainSong）
                val mainSong = fetchProgramMainSong(program.id, program.name)
                    ?: return@launch

                // 2. 多级获取播放 URL
                val url = fetchPlayUrl(mainSong.id)
                    ?: return@launch

                // 3. 构建当前播放歌曲
                val currentSong = mainSong.copy(
                    name = program.name ?: mainSong.name,
                    dt = program.duration
                )

                // 4. 并行预取其他节目的 mainSong，构建完整播放队列
                val allSongs = fetchAllProgramSongs(allPrograms, index, currentSong)

                // 5. 设置播放队列并跳转
                PlayQueueManager.setQueue(allSongs, index)
                activity?.runOnUiThread {
                    if (isAdded) {
                        val gson = com.google.gson.Gson()
                        val intent = Intent(requireContext(), PlayerActivity::class.java).apply {
                            putExtra("song_id", currentSong.id)
                            putExtra("song_name", currentSong.name ?: "未知节目")
                            putExtra("artist_name", program.dj?.nickname ?: "电台节目")
                            putExtra("album_pic_url", program.coverUrl ?: currentSong.al?.picUrl)
                            putExtra("duration", program.duration)
                            putExtra("auto_play_url", url)
                            putExtra("playlist_json", gson.toJson(allSongs))
                        }
                        startActivity(intent)
                    }
                }
            } catch (e: Exception) {
                Log.e("HomeFragment", "playRadioProgram error: ", e)
                activity?.runOnUiThread {
                    if (isAdded) requireContext().showToast("播放失败：${e.message}")
                }
            }
        }
    }

    /**
     * 并行预取所有电台节目的 mainSong，构建 SongInfo 列表
     * 当前节目直接使用已获取的 mainSong，其余并行请求
     */
    private suspend fun fetchAllProgramSongs(
        allPrograms: List<com.example.myapplication.data.remote.DjProgramItem>,
        currentIndex: Int,
        currentSong: com.example.myapplication.data.remote.SongInfo
    ): List<com.example.myapplication.data.remote.SongInfo> {
        val results = arrayOfNulls<com.example.myapplication.data.remote.SongInfo>(allPrograms.size)
        results[currentIndex] = currentSong

        // 并行请求所有其他节目的 mainSong
        val deferredList = allPrograms.mapIndexed { i, prog ->
            if (i == currentIndex) null // 跳过当前
            else CoroutineScope(Dispatchers.IO).async {
                try {
                    val detailResult = repository.getDjProgramDetail(prog.id)
                    if (detailResult is AppResult.Success) {
                        val ms = detailResult.data.program?.mainSong
                        if (ms != null && ms.id > 0) {
                            ms.copy(
                                name = prog.name ?: ms.name,
                                dt = prog.duration
                            )
                        } else {
                            // mainSong 不可用，使用节目信息作为占位（后续通过 fetchPlayUrl 获取时可能失败）
                            com.example.myapplication.data.remote.SongInfo(
                                id = prog.id,
                                name = prog.name,
                                dt = prog.duration,
                                al = com.example.myapplication.data.remote.AlbumInfo(picUrl = prog.coverUrl),
                                ar = listOf(
                                    com.example.myapplication.data.remote.ArtistInfo(
                                        name = prog.dj?.nickname ?: "电台节目"
                                    )
                                )
                            )
                        }
                    } else null
                } catch (_: Exception) { null }
            }
        }

        // 等待所有并行请求完成（最长等待 5 秒）
        for (i in allPrograms.indices) {
            if (i == currentIndex) continue
            val deferred = deferredList[i] ?: continue
            try {
                results[i] = deferred.await()
            } catch (_: Exception) { /* 跳过失败的 */ }
        }

        // 构建最终列表，优先使用获取到的 mainSong，否则用节目信息占位
        return allPrograms.mapIndexed { i, prog ->
            results[i] ?: com.example.myapplication.data.remote.SongInfo(
                id = prog.id,
                name = prog.name,
                dt = prog.duration,
                al = com.example.myapplication.data.remote.AlbumInfo(picUrl = prog.coverUrl),
                ar = listOf(
                    com.example.myapplication.data.remote.ArtistInfo(
                        name = prog.dj?.nickname ?: "电台节目"
                    )
                )
            )
        }
    }

    /**
     * 获取电台节目的 mainSong
     * 失败时在 UI 线程提示并返回 null
     */
    private suspend fun fetchProgramMainSong(programId: Long, programName: String?): com.example.myapplication.data.remote.SongInfo? {
        val detailResult = repository.getDjProgramDetail(programId)
        when (detailResult) {
            is AppResult.Success -> {
                val mainSong = detailResult.data.program?.mainSong
                if (mainSong == null || mainSong.id == 0L) {
                    activity?.runOnUiThread {
                        if (isAdded) requireContext().showToast("该节目暂无播放资源")
                    }
                    return null
                }
                return mainSong
            }
            is AppResult.Error -> {
                val msg = detailResult.exception.message ?: "获取节目信息失败"
                Log.e("HomeFragment", "getDjProgramDetail failed: $msg")
                activity?.runOnUiThread {
                    if (isAdded) requireContext().showToast(if (msg.contains("301")) "登录已过期，请重新登录" else "获取节目信息失败")
                }
                return null
            }
        }
    }

    /**
     * 多级获取播放 URL（含试听降级）
     * 第1层: getSongUrl (standard)
     * 第2层: getSongUrl (exhigh 高品质)
     * 第3层: getSongUrlV2 (备用接口)
     * 第4层: getSongUrl (lossless 无损 - 部分歌曲仅高音质有版权)
     */
    private suspend fun fetchPlayUrl(songId: Long): String? {
        // 第1层：标准音质
        val r1 = ApiService.getSongUrl(songId, "standard")
        if (r1 is AppResult.Success) {
            val url = r1.data.data?.firstOrNull()?.url
            if (!url.isNullOrEmpty()) return url
        }

        // 第2层：高品质
        val r2 = ApiService.getSongUrl(songId, "exhigh")
        if (r2 is AppResult.Success) {
            val url = r2.data.data?.firstOrNull()?.url
            if (!url.isNullOrEmpty()) return url
        }

        // 第3层：备用接口
        val r3 = ApiService.getSongUrlV2(songId)
        if (r3 is AppResult.Success) {
            val url = r3.data.data?.firstOrNull()?.url
            if (!url.isNullOrEmpty()) return url
        }

        // 第4层：无损音质（部分歌曲仅高码率有版权）
        val r4 = ApiService.getSongUrl(songId, "lossless")
        if (r4 is AppResult.Success) {
            val url = r4.data.data?.firstOrNull()?.url
            if (!url.isNullOrEmpty()) return url
        }

        // 全部失败
        activity?.runOnUiThread {
            if (isAdded) requireContext().showToast("无法获取播放地址，该节目可能无版权或需VIP")
        }
        return null
    }

    /**
     * 后台预取其他电台节目的 mainSong ID
     * 成功后更新 PlayQueueManager 队列中对应歌曲的 id 为真实 mainSong.id
     */

    // ==================== 心动页（内嵌播放器） ====================

    private var heartbeatService: MusicService? = null
    private var heartbeatServiceBound = false

    /** 唱片旋转 */
    private var discRotationAnimator: ObjectAnimator? = null
    private var currentDiscRotation: Float = 0f

    /** 歌词 */
    private val hbLyricEntries = mutableListOf<Pair<Long, String>>()
    private val hbTlyricEntries = mutableListOf<Pair<Long, String>>()
    private var hbCurrentLyricIndex = -1
    private var hbLastScrolledLyricIndex = -1
    private var hbIsUserScrollingLyric = false
    private var hbIsOnLyricPage = false
    private val hbLyricAdapter = LyricAdapter()

    /** ViewPager 页面 */
    private var hbCoverPageBinding: PageCoverBinding? = null
    private var hbLyricPageBinding: PageLyricBinding? = null

    private var hbLastClickTime = 0L
    private var hbTouchDownX = 0f
    private var hbTouchDownY = 0f
    private val hbTouchSlop = 20f

    private var heartbeatContentLoaded = false
    private var hbIsFavorite = false
    private var lastHeartbeatBinding: PageHomeHeartbeatBinding? = null  // 跟踪 ViewPager 重建

    private val heartbeatServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            heartbeatService = (service as MusicService.MusicBinder).getService()
            heartbeatServiceBound = true
            heartbeatService?.addProgressListener(hbProgressListener)
            heartbeatService?.addPlayStateListener(hbPlayStateListener)
            // 如果已经在播放中，刷新 UI
            heartbeatService?.getCurrentSong()?.let { displayHbSongInfo(it) }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            heartbeatService = null
            heartbeatServiceBound = false
        }
    }

    private val hbProgressListener = object : MusicService.OnProgressListener {
        override fun onProgress(currentPosition: Int, duration: Int) {
            val b = heartbeatPageBinding ?: return
            b.seekbar.progress = currentPosition
            b.seekbar.max = duration
            b.tvCurrentTime.text = (currentPosition / 1000).formatDuration()
            b.tvTotalTime.text = (duration / 1000).formatDuration()
            updateHbLyricHighlight(currentPosition.toLong())
        }
    }

    private val hbPlayStateListener = object : MusicService.OnPlayStateListener {
        override fun onPlayStateChanged(isPlaying: Boolean, song: SongInfo?) {
            updateHbPlayButton(isPlaying)
            if (isPlaying) startHbDiscRotation() else pauseHbDiscRotation()
            song?.let { displayHbSongInfo(it) }
        }

        override fun onSongChanged(song: SongInfo) {
            displayHbSongInfo(song)
            loadHbLyric(song.id)
            initHbFavorite(song.id)
            updateHbNavButtons()
            resetHbDiscRotation()
        }
    }

    private fun setupHeartbeatPage() {
        val b = heartbeatPageBinding ?: return
        // ViewPager2 可能重建页面，通过 binding 引用判断是否需要重新初始化
        if (b == lastHeartbeatBinding && heartbeatContentLoaded) return
        lastHeartbeatBinding = b

        // 检测心动是否已在播放中（ViewPager2 页面回收后重新创建的场景）
        val isAlreadyPlaying = PlayQueueManager.isHeartbeatSource &&
                PlayQueueManager.playMode == PlayMode.HEARTBEAT

        if (isAlreadyPlaying) {
            // 已播放中：直接显示播放器，不显示加载提示
            b.tvEmpty.visibility = View.GONE
            b.progressBar.visibility = View.GONE
            setHbPlayerVisibility(b, true)
            PlayQueueManager.currentSong?.let { displayHbSongInfo(it) }
        } else {
            // 首次进入：隐藏播放器 UI，显示加载提示
            setHbPlayerVisibility(b, false)
            b.tvEmpty.text = "正在为你生成心动推荐..."
            b.tvEmpty.visibility = View.VISIBLE
            b.progressBar.visibility = View.GONE
        }

        // 绑定 MusicService
        val serviceIntent = Intent(requireContext(), MusicService::class.java)
        requireContext().bindService(serviceIntent, heartbeatServiceConnection, Context.BIND_AUTO_CREATE)

        // ViewPager2（封面 / 歌词）
        b.vpCoverLyric.adapter = HbCoverLyricPagerAdapter()
        b.vpCoverLyric.orientation = ViewPager2.ORIENTATION_HORIZONTAL
        b.vpCoverLyric.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                hbIsOnLyricPage = (position == 1)
                updateHbIndicator(position)
                if (hbIsOnLyricPage) {
                    hbLyricPageBinding?.rvLyric?.post { setupHbLyricPadding() }
                    if (hbLyricEntries.isNotEmpty()) {
                        val pos = heartbeatService?.getCurrentPosition()?.toLong() ?: 0L
                        hbCurrentLyricIndex = -1
                        hbLastScrolledLyricIndex = -1
                        updateHbLyricHighlight(pos)
                    }
                }
            }
        })

        // 播放控制
        b.ivPlayPause.setOnClickListener { heartbeatService?.togglePlayPause() }
        b.ivPrev.setOnClickListener { heartbeatService?.playPrevious() }
        b.ivNext.setOnClickListener { heartbeatService?.playNext() }
        b.ivFavorite.setOnClickListener { toggleHbFavorite() }
        b.ivComment.setOnClickListener { openHbComments() }

        // 进度条
        b.seekbar.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) heartbeatService?.seekTo(progress)
            }
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
        })

        updateHbNavButtons()
        heartbeatContentLoaded = true
    }

    /** 控制播放器 UI 的显示/隐藏 */
    private fun setHbPlayerVisibility(b: com.example.myapplication.databinding.PageHomeHeartbeatBinding, visible: Boolean) {
        val vis = if (visible) View.VISIBLE else View.INVISIBLE
        b.tvSongTitle.visibility = vis
        b.tvArtist.visibility = vis
        b.vpCoverLyric.visibility = vis
        b.indicatorDots.visibility = vis
        b.tvPageHint.visibility = vis
        b.seekbar.visibility = vis
        b.tvCurrentTime.visibility = vis
        b.tvTotalTime.visibility = vis
        b.controlBar.visibility = vis
    }

    private fun loadHeartbeatContent() {
        val b = heartbeatPageBinding ?: return
        if (!AccountManager.isLoggedIn) {
            b.tvEmpty.text = "请先登录后再使用心动模式"
            b.tvEmpty.visibility = View.VISIBLE
            return
        }

        // 如果已有心跳歌单在播放，直接显示播放器 UI（skip 重复加载）
        if (PlayQueueManager.isHeartbeatSource && PlayQueueManager.playMode == PlayMode.HEARTBEAT) {
            b.tvEmpty.visibility = View.GONE
            setHbPlayerVisibility(b, true)
            PlayQueueManager.currentSong?.let { displayHbSongInfo(it) }
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val playlistResult = ApiService.getUserPlaylist(AccountManager.userId)
                if (playlistResult !is AppResult.Success) {
                    activity?.runOnUiThread {
                        b.tvEmpty.text = "加载歌单失败，请稍后重试"
                        b.tvEmpty.visibility = View.VISIBLE
                    }
                    return@launch
                }
                val playlists = playlistResult.data.playlist ?: emptyList()
                val likedPlaylist = playlists.firstOrNull {
                    it.name?.contains("喜欢的音乐", ignoreCase = true) == true
                }
                if (likedPlaylist == null) {
                    activity?.runOnUiThread {
                        b.tvEmpty.text = "未找到喜欢的音乐歌单，请先收藏歌曲"
                        b.tvEmpty.visibility = View.VISIBLE
                    }
                    return@launch
                }

                val likedResult = ApiService.getPlaylistDetail(likedPlaylist.id)
                val likedSongs = if (likedResult is AppResult.Success && likedResult.data.playlist != null) {
                    likedResult.data.playlist.tracks ?: emptyList()
                } else emptyList()
                if (likedSongs.isEmpty()) {
                    activity?.runOnUiThread {
                        b.tvEmpty.text = "暂无喜欢歌曲，请先收藏歌曲"
                        b.tvEmpty.visibility = View.VISIBLE
                    }
                    return@launch
                }

                // 收藏标记
                repository.markLikedStatus(likedSongs)

                activity?.runOnUiThread {
                    b.tvEmpty.visibility = View.GONE
                    setHbPlayerVisibility(b, true)
                    // 基于喜欢的歌单做 AI 推荐，而非随机普通歌单
                    startHeartbeatPlayback(likedSongs.first(), likedPlaylist.id, likedSongs)
                }
            } catch (e: Exception) {
                activity?.runOnUiThread {
                    b.tvEmpty.text = "加载失败：${e.message}"
                    b.tvEmpty.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun startHeartbeatPlayback(firstSong: SongInfo, normalPlaylistId: Long, likedSongs: List<SongInfo>) {
        PlayQueueManager.heartbeatPlaylistId = normalPlaylistId
        PlayQueueManager.heartbeatLikedSongs = likedSongs
        // 先设队列（setQueue 内部会重置 isHeartbeatSource=false 和 playMode=SEQUENTIAL）
        PlayQueueManager.setQueue(listOf(firstSong), 0)
        // 必须在 setQueue 之后重新设置，否则标志位被覆盖导致再次进入心动时误判为"未在播"
        PlayQueueManager.isHeartbeatSource = true
        PlayQueueManager.setPlayMode(PlayMode.HEARTBEAT)

        displayHbSongInfo(firstSong)
        loadHbLyric(firstSong.id)
        initHbFavorite(firstSong.id)

        // 加载 URL 并播放
        CoroutineScope(Dispatchers.IO).launch {
            val urlResult = ApiService.getSongUrl(firstSong.id)
            val url = if (urlResult is AppResult.Success) {
                urlResult.data.data?.firstOrNull()?.url
            } else null
            activity?.runOnUiThread {
                if (!url.isNullOrEmpty()) {
                    heartbeatService?.play(url, firstSong)
                } else {
                    requireContext().showToast("无法获取播放地址，该歌曲可能无版权")
                }
            }
        }
    }

    private fun displayHbSongInfo(song: SongInfo) {
        val b = heartbeatPageBinding ?: return
        b.tvSongTitle.text = song.name ?: "未知歌曲"
        b.tvArtist.text = song.artistNames
        hbCoverPageBinding?.ivAlbumArt?.loadImageCircle(song.al?.picUrl)
    }

    private fun initHbFavorite(songId: Long) {
        hbIsFavorite = FavoriteManager.isLiked(songId)
        updateHbFavoriteIcon()
        if (!FavoriteManager.isLoaded) {
            CoroutineScope(Dispatchers.IO).launch {
                hbIsFavorite = repository.isFavorite(songId)
                activity?.runOnUiThread { updateHbFavoriteIcon() }
            }
        }
    }

    private fun updateHbFavoriteIcon() {
        heartbeatPageBinding?.ivFavorite?.setImageResource(
            if (hbIsFavorite) R.drawable.ic_heart_filled
            else R.drawable.ic_heart_outline
        )
    }

    private fun toggleHbFavorite() {
        if (!AccountManager.isLoggedIn) {
            requireContext().showToast("请先登录后再喜欢")
            return
        }
        val song = heartbeatService?.getCurrentSong() ?: return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                hbIsFavorite = repository.toggleFavorite(song)
                activity?.runOnUiThread {
                    updateHbFavoriteIcon()
                    requireContext().showToast(if (hbIsFavorite) "已喜欢" else "已取消喜欢")
                }
            } catch (e: Exception) {
                activity?.runOnUiThread { requireContext().showToast("操作失败，请稍后重试") }
            }
        }
    }

    private fun openHbComments() {
        val song = heartbeatService?.getCurrentSong() ?: return
        val intent = Intent(requireContext(), com.example.myapplication.ui.comment.CommentActivity::class.java).apply {
            putExtra("comment_type", 0)
            putExtra("resource_id", song.id)
            putExtra("resource_name", song.name ?: "")
        }
        startActivity(intent)
    }

    private fun updateHbPlayButton(playing: Boolean) {
        heartbeatPageBinding?.ivPlayPause?.setImageResource(
            if (playing) android.R.drawable.ic_media_pause
            else android.R.drawable.ic_media_play
        )
    }

    private fun updateHbNavButtons() {
        val b = heartbeatPageBinding ?: return
        b.ivPrev.alpha = if (PlayQueueManager.size > 1) 1.0f else 0.3f
        b.ivNext.alpha = 1.0f // 心动模式始终可下一首
    }

    private fun updateHbIndicator(page: Int) {
        val b = heartbeatPageBinding ?: return
        if (page == 0) {
            b.dotCover.setBackgroundResource(R.drawable.dot_active)
            b.dotLyric.setBackgroundResource(R.drawable.dot_inactive)
            b.tvPageHint.text = "滑动查看歌词"
        } else {
            b.dotCover.setBackgroundResource(R.drawable.dot_inactive)
            b.dotLyric.setBackgroundResource(R.drawable.dot_active)
            b.tvPageHint.text = "滑动查看封面"
        }
    }

    // ===== 唱片旋转 =====

    private fun startHbDiscRotation() {
        val disc = hbCoverPageBinding?.rotatingDisc ?: return
        discRotationAnimator?.cancel()
        discRotationAnimator = ObjectAnimator.ofFloat(
            disc, "rotation", currentDiscRotation, currentDiscRotation + 360f
        ).apply {
            duration = 8000L
            repeatCount = ObjectAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
        }
    }

    private fun pauseHbDiscRotation() {
        val disc = hbCoverPageBinding?.rotatingDisc ?: return
        currentDiscRotation = disc.rotation
        discRotationAnimator?.cancel()
    }

    private fun resetHbDiscRotation() {
        discRotationAnimator?.cancel()
        currentDiscRotation = 0f
        hbCoverPageBinding?.rotatingDisc?.rotation = 0f
    }

    // ===== 歌词 =====

    private fun loadHbLyric(songId: Long) {
        hbLyricEntries.clear()
        hbTlyricEntries.clear()
        hbCurrentLyricIndex = -1
        hbLastScrolledLyricIndex = -1
        hbLyricAdapter.setData(listOf(0L to "加载歌词中..."), emptyList())

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = repository.getLyric(songId)
                if (result is AppResult.Success) {
                    val lrc = result.data.lrc?.lyric ?: ""
                    val tlrc = result.data.tlyric?.lyric ?: ""
                    activity?.runOnUiThread {
                        val entries = parseHbLrc(lrc)
                        if (entries.isNotEmpty()) hbLyricEntries.addAll(entries)
                        val tEntries = parseHbLrc(tlrc)
                        if (tEntries.isNotEmpty()) hbTlyricEntries.addAll(tEntries)
                        hbLyricAdapter.setData(
                            if (hbLyricEntries.isNotEmpty()) hbLyricEntries else listOf(0L to "纯音乐，请欣赏"),
                            hbTlyricEntries
                        )
                    }
                } else {
                    activity?.runOnUiThread {
                        hbLyricAdapter.setData(listOf(0L to "暂无歌词"), emptyList())
                    }
                }
            } catch (_: Exception) {
                activity?.runOnUiThread {
                    hbLyricAdapter.setData(listOf(0L to "暂无歌词"), emptyList())
                }
            }
        }
    }

    private fun parseHbLrc(lrcText: String): List<Pair<Long, String>> {
        if (lrcText.isBlank()) return emptyList()
        val regex = Regex("\\[(\\d{2}):(\\d{2})(?:[.:](\\d{2,3}))?\\](.*)")
        return lrcText.split("\n").mapNotNull { line ->
            val match = regex.find(line) ?: return@mapNotNull null
            val min = match.groupValues[1].toLongOrNull() ?: 0
            val sec = match.groupValues[2].toLongOrNull() ?: 0
            val ms = match.groupValues[3].takeIf { it.isNotEmpty() }
                ?.let { (it.toLongOrNull() ?: 0) * if (it.length == 2) 10 else 1 } ?: 0
            val ts = min * 60000 + sec * 1000 + ms
            val text = match.groupValues[4].trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            ts to text
        }.sortedBy { it.first }
    }

    private fun updateHbLyricHighlight(currentMs: Long) {
        if (hbLyricEntries.isEmpty()) return
        val newIdx = hbLyricAdapter.findIndexByTime(currentMs)
        if (newIdx < 0 || newIdx == hbCurrentLyricIndex) return
        hbCurrentLyricIndex = newIdx
        hbLyricAdapter.setHighlightedIndex(newIdx)
        if (hbIsOnLyricPage && !hbIsUserScrollingLyric && newIdx != hbLastScrolledLyricIndex) {
            hbLastScrolledLyricIndex = newIdx
            scrollHbLyricToCenter(newIdx)
        }
    }

    private fun setupHbLyricPadding() {
        val rv = hbLyricPageBinding?.rvLyric ?: return
        if (rv.height <= 0) { rv.post { setupHbLyricPadding() }; return }
        val half = rv.height / 2
        rv.setPadding(rv.paddingLeft, half, rv.paddingRight, half)
    }

    private fun scrollHbLyricToCenter(position: Int) {
        val rv = hbLyricPageBinding?.rvLyric ?: return
        val lm = rv.layoutManager as? LinearLayoutManager ?: return
        if (rv.height <= 0) { rv.post { scrollHbLyricToCenter(position) }; return }
        val itemHeight = lm.findViewByPosition(position)?.height
            ?: (48 * rv.resources.displayMetrics.density).toInt()
        val visibleH = rv.height - rv.paddingTop - rv.paddingBottom
        val offset = (visibleH / 2) - (itemHeight / 2)
        lm.scrollToPositionWithOffset(position, offset)
    }

    // ===== ViewPager2 适配器（封面/歌词） =====

    private inner class HbCoverLyricPagerAdapter : RecyclerView.Adapter<HbCoverLyricPagerAdapter.VH>() {
        inner class VH(val view: View) : RecyclerView.ViewHolder(view)
        override fun getItemCount() = 2

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val inflater = LayoutInflater.from(parent.context)
            return if (viewType == 0) {
                val b = PageCoverBinding.inflate(inflater, parent, false)
                hbCoverPageBinding = b
                VH(b.root)
            } else {
                val b = PageLyricBinding.inflate(inflater, parent, false)
                hbLyricPageBinding = b
                b.rvLyric.apply {
                    layoutManager = LinearLayoutManager(this@HomeFragment.requireContext())
                    adapter = hbLyricAdapter
                    itemAnimator = null
                    addOnScrollListener(object : RecyclerView.OnScrollListener() {
                        override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                            hbIsUserScrollingLyric = (newState == RecyclerView.SCROLL_STATE_DRAGGING
                                    || newState == RecyclerView.SCROLL_STATE_SETTLING)
                            if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                                recyclerView.postDelayed({
                                    hbIsUserScrollingLyric = false
                                    val pos = heartbeatService?.getCurrentPosition()?.toLong() ?: 0L
                                    hbCurrentLyricIndex = -1
                                    updateHbLyricHighlight(pos)
                                }, 1500L)
                            }
                        }
                    })
                }
                VH(b.root)
            }
        }

        override fun getItemViewType(position: Int) = position

        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.view.setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    hbTouchDownX = event.rawX
                    hbTouchDownY = event.rawY
                }
                false
            }
            holder.view.setOnClickListener {
                val now = System.currentTimeMillis()
                if (now - hbLastClickTime < 300L) return@setOnClickListener
                hbLastClickTime = now
                heartbeatPageBinding?.vpCoverLyric?.setCurrentItem(
                    if (position == 0) 1 else 0, true
                )
            }
        }
    }

    // ===== 生命周期 =====

    private fun releaseHeartbeatResources() {
        discRotationAnimator?.cancel()
        discRotationAnimator = null
        if (heartbeatServiceBound) {
            heartbeatService?.removeProgressListener(hbProgressListener)
            heartbeatService?.removePlayStateListener(hbPlayStateListener)
            requireContext().unbindService(heartbeatServiceConnection)
            heartbeatServiceBound = false
        }
        heartbeatService = null
        heartbeatPageBinding = null
        lastHeartbeatBinding = null
        hbCoverPageBinding = null
        hbLyricPageBinding = null
        heartbeatContentLoaded = false
    }

    // ==================== 私人FM ====================


    private fun launchPrivateFm() {
        if (!AccountManager.isLoggedIn) {
            startActivity(Intent(requireContext(), LoginActivity::class.java))
            return
        }

        requireContext().showToast("正在为你推荐...")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = ApiService.getPersonalFm()
                if (result is AppResult.Success) {
                    val songs = result.data.data
                    if (!songs.isNullOrEmpty()) {
                        PlayQueueManager.setPlayMode(PlayMode.FM)
                        PlayQueueManager.addAllToQueue(songs)
                        val firstSong = songs[0]
                        val urlResult = ApiService.getSongUrl(firstSong.id)
                        val url = if (urlResult is AppResult.Success) {
                            urlResult.data.data?.firstOrNull()?.url
                        } else null

                        if (!url.isNullOrEmpty()) {
                            val intent = Intent(requireContext(), PlayerActivity::class.java).apply {
                                putExtra("song_id", firstSong.id)
                                putExtra("song_name", firstSong.name ?: "未知歌曲")
                                putExtra("artist_name", firstSong.artistNames)
                                putExtra("album_pic_url", firstSong.al?.picUrl)
                                putExtra("duration", firstSong.dt)
                                putExtra("playlist_json", Gson().toJson(songs))
                                putExtra("play_mode", PlayMode.FM.value)
                                putExtra("auto_play_url", url)
                            }
                            activity?.runOnUiThread { startActivity(intent) }
                            return@launch
                        }
                    }
                }
                requireActivity().runOnUiThread {
                    requireActivity().showToast("获取推荐失败，请重试")
                }
            } catch (_: Exception) {
                requireActivity().runOnUiThread {
                    requireActivity().showToast("网络异常，请重试")
                }
            }
        }
    }

    // ==================== 收藏切换 ====================

    private fun toggleFavorite(song: SongInfo) {
        if (!AccountManager.isLoggedIn) {
            requireContext().showToast("请先登录后再收藏")
            return
        }
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val nowFav = repository.toggleFavorite(song)
                requireActivity().runOnUiThread {
                    requireContext().showToast(if (nowFav) "已收藏" else "已取消收藏")
                    homeAdapter.notifyDataSetChanged()
                    tabPageAdapters.forEach { (_, adp) -> adp.notifyDataSetChanged() }
                }
            } catch (e: Exception) {
                requireActivity().runOnUiThread {
                    requireContext().showToast("操作失败，请稍后重试")
                }
            }
        }
    }

    // ==================== 刷新推荐页列表 ====================

    private fun refreshHomeList() {
        if (rvHome == null || swipeRefresh == null) return
        val items = mutableListOf<HomeAdapter.HomeItem>()

        cachedBanners?.takeIf { it.isNotEmpty() }?.let {
            items.add(HomeAdapter.HomeItem.BannerList(it))
        }

        cachedPlaylists?.takeIf { it.isNotEmpty() }?.let {
            items.add(HomeAdapter.HomeItem.SectionTitle("推荐歌单"))
            items.add(HomeAdapter.HomeItem.PlaylistRow(it))
        }

        cachedNewSongs?.takeIf { it.isNotEmpty() }?.let {
            items.add(HomeAdapter.HomeItem.SectionTitle("最新音乐"))
            it.forEachIndexed { index, song ->
                items.add(HomeAdapter.HomeItem.SongRow(song))
            }
        }

        homeAdapter.submitList(items)
    }

    private fun dp2px(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (hidden) {
            // HomeFragment 被隐藏（切换到排行/我的），恢复迷你播放栏
            val wasHeartbeatPage = PlayQueueManager.isHeartbeatPageVisible
            PlayQueueManager.isHeartbeatPageVisible = false
            if (wasHeartbeatPage) {
                (requireActivity() as? com.example.myapplication.base.BaseActivity<*>)?.refreshMiniPlayer()
            }
        } else {
            // HomeFragment 重新显示，检查当前是否在心动 tab
            val isHeartbeat = (currentTabIndex == 3)
            PlayQueueManager.isHeartbeatPageVisible = isHeartbeat
            if (isHeartbeat) {
                // 在心动tab时立即隐藏迷你播放栏（避免闪现）
                (requireActivity() as? com.example.myapplication.base.BaseActivity<*>)?.hideMiniPlayer()
            } else {
                (requireActivity() as? com.example.myapplication.base.BaseActivity<*>)?.refreshMiniPlayer()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        releaseHeartbeatResources()
        recommendPageBinding = null
        swipeRefresh = null
        rvHome = null
        homeAdapter = HomeAdapter() // 防止旧 adapter 被复用
        cachedBanners = null
        cachedPlaylists = null
        cachedNewSongs = null
        tabPageBindings.clear()
        tabPageAdapters.clear()
        tabPageData.clear()
        _binding = null
    }
}
