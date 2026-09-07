package com.example.myapplication.ui.search

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.ImageView
import android.widget.TextView
import com.example.myapplication.R
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.adapter.HomeAdapter
import com.example.myapplication.base.BaseActivity
import com.example.myapplication.base.Result as AppResult
import com.example.myapplication.data.Repository
import com.example.myapplication.data.remote.ApiClient
import com.example.myapplication.data.remote.ApiService
import com.example.myapplication.data.remote.SongInfo
import com.example.myapplication.databinding.ActivitySearchBinding
import com.example.myapplication.manager.AccountManager
import com.example.myapplication.manager.PlayQueueManager
import com.example.myapplication.ui.player.PlayerActivity
import com.example.myapplication.util.showToast
import com.google.android.material.tabs.TabLayout
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * 搜索页面
 * 支持：热搜、搜索历史、搜索建议（输入联想）、多类型搜索（综合/歌曲/歌手/专辑/歌单）
 */
class SearchActivity : BaseActivity<ActivitySearchBinding>() {

    private val repository = Repository.getInstance()
    private val prefs by lazy { getSharedPreferences("search_prefs", MODE_PRIVATE) }

    private val searchResults = mutableListOf<SongInfo>()
    private var searchAdapter: HomeAdapter? = null
    private var suggestAdapter: SuggestAdapter? = null

    /** 当前搜索类型 */
    private var currentSearchType = 1
    /** 搜索建议防抖 Job */
    private var suggestJob: Job? = null

    companion object {
        private const val MAX_HISTORY = 15
        private const val KEY_HISTORY = "search_history"
        // 搜索类型：1=单曲, 10=专辑, 100=歌手, 1000=歌单, 1018=综合
        private const val TYPE_COMPREHENSIVE = 1018
        private const val TYPE_SONG = 1
        private const val TYPE_ARTIST = 100
        private const val TYPE_ALBUM = 10
        private const val TYPE_PLAYLIST = 1000
    }

    override fun initBinding() = ActivitySearchBinding.inflate(layoutInflater)

    override fun initView() {
        binding.ivBack.setOnClickListener { finish() }

        // RecyclerView
        binding.rvSearchResult.layoutManager = LinearLayoutManager(this)
        binding.rvSuggest.layoutManager = LinearLayoutManager(this)

        // 搜索按钮
        binding.btnSearch.setOnClickListener { performSearch() }

        // 键盘搜索键
        binding.etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch()
                true
            } else false
        }

        // 输入监听 → 搜索建议
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val keyword = s?.toString()?.trim() ?: ""
                if (keyword.isNotEmpty()) {
                    // 防抖：300ms 后请求搜索建议
                    suggestJob?.cancel()
                    suggestJob = CoroutineScope(Dispatchers.Main).launch {
                        delay(300)
                        loadSuggestions(keyword)
                    }
                } else {
                    // 清空回到热搜页面
                    hideSuggestions()
                    binding.layoutHint.visibility = View.VISIBLE
                    binding.rvSearchResult.visibility = View.GONE
                    binding.tabSearchType.visibility = View.GONE
                    binding.tvEmpty.visibility = View.GONE
                }
            }
        })

        // 搜索类型 Tab 切换
        binding.tabSearchType.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                val keyword = binding.etSearch.text?.toString()?.trim() ?: ""
                if (keyword.isNotEmpty()) {
                    currentSearchType = when (tab?.position) {
                        0 -> TYPE_COMPREHENSIVE
                        1 -> TYPE_SONG
                        2 -> TYPE_ARTIST
                        3 -> TYPE_ALBUM
                        4 -> TYPE_PLAYLIST
                        else -> TYPE_SONG
                    }
                    performSearch()
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        // 清空搜索历史
        binding.tvClearHistory.setOnClickListener {
            clearSearchHistory()
        }
    }

    override fun initData() {
        // 加载热搜和搜索历史
        loadHotSearch()
        loadSearchHistory()

        // 如果从外部传入关键字，自动填入并搜索
        val keyword = intent.getStringExtra("keyword")
        if (!keyword.isNullOrBlank()) {
            binding.etSearch.setText(keyword)
            binding.etSearch.setSelection(keyword.length)
            performSearch()
        }
    }

    override fun initObserver() {}

    // ==================== 搜索历史 ====================

    private fun loadSearchHistory() {
        val history = getSearchHistory()
        if (history.isEmpty()) {
            binding.layoutHistory.visibility = View.GONE
            return
        }
        binding.layoutHistory.visibility = View.VISIBLE
        binding.flexHistory.removeAllViews()
        history.take(10).forEach { keyword ->
            val tag = createHistoryTag(keyword)
            binding.flexHistory.addView(tag)
        }
    }

    private fun createHistoryTag(keyword: String): TextView {
        return TextView(this).apply {
            text = keyword
            textSize = 13f
            setTextColor(0xFF666666.toInt())
            setBackgroundResource(R.drawable.bg_search_tag)
            val padding = (8 * resources.displayMetrics.density).toInt()
            setPadding(padding * 2, padding, padding * 2, padding)
            gravity = android.view.Gravity.CENTER
            maxLines = 1
            isSingleLine = true

            val lp = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(0, 0, (10 * resources.displayMetrics.density).toInt(), (10 * resources.displayMetrics.density).toInt())
            layoutParams = lp

            setOnClickListener {
                binding.etSearch.setText(keyword)
                binding.etSearch.setSelection(keyword.length)
                performSearch()
            }
        }
    }

    private fun getSearchHistory(): List<String> {
        val json = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        return try {
            Gson().fromJson(json, Array<String>::class.java).toList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun addToSearchHistory(keyword: String) {
        val history = getSearchHistory().toMutableList()
        history.remove(keyword) // 去重
        history.add(0, keyword) // 放最前面
        if (history.size > MAX_HISTORY) {
            history.removeAt(history.lastIndex)
        }
        prefs.edit().putString(KEY_HISTORY, Gson().toJson(history)).apply()
    }

    private fun clearSearchHistory() {
        prefs.edit().remove(KEY_HISTORY).apply()
        binding.layoutHistory.visibility = View.GONE
        binding.flexHistory.removeAllViews()
    }

    // ==================== 热搜 ====================

    private fun loadHotSearch() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // /search/hot 需要 POST 请求
                var raw = ApiClient.postRaw("/search/hot")
                if (raw == null) {
                    raw = ApiClient.getRaw("/search/hot")
                }
                if (raw == null) return@launch

                val root = JSONObject(raw)
                if (root.optInt("code") != 200) return@launch

                val hotItems = mutableListOf<com.example.myapplication.data.remote.SearchHotItem>()

                // 格式 A：result.hots（标准 NeteaseCloudMusicApiBackup）
                val resultObj = root.optJSONObject("result")
                if (resultObj != null) {
                    val hots = resultObj.optJSONArray("hots")
                    if (hots != null && hots.length() > 0) {
                        for (i in 0 until hots.length()) {
                            val item = hots.getJSONObject(i)
                            hotItems.add(
                                com.example.myapplication.data.remote.SearchHotItem(
                                    searchWord = item.optString("first"),
                                    score = item.optInt("second"),
                                    content = item.optString("third", "")
                                )
                            )
                        }
                    }
                }

                // 格式 B：data 数组
                if (hotItems.isEmpty()) {
                    val dataArr = root.optJSONArray("data")
                    if (dataArr != null && dataArr.length() > 0) {
                        for (i in 0 until dataArr.length()) {
                            val item = dataArr.getJSONObject(i)
                            hotItems.add(
                                com.example.myapplication.data.remote.SearchHotItem(
                                    searchWord = item.optString("searchWord"),
                                    score = item.optInt("score"),
                                    content = item.optString("content", ""),
                                    iconType = item.optInt("iconType"),
                                    iconUrl = item.optString("iconUrl", "")
                                )
                            )
                        }
                    }
                }

                if (hotItems.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        showHotSearch(hotItems)
                    }
                }
            } catch (_: Exception) { }
        }
    }

    private fun showHotSearch(items: List<com.example.myapplication.data.remote.SearchHotItem>) {
        if (items.isEmpty()) {
            binding.layoutHot.visibility = View.GONE
            return
        }
        binding.layoutHot.visibility = View.VISIBLE
        binding.layoutEmptyHint.visibility = View.GONE
        binding.layoutHotList.removeAllViews()

        items.take(20).forEachIndexed { index, item ->
            val keyword = item.searchWord ?: return@forEachIndexed
            val row = layoutInflater.inflate(R.layout.item_search_suggest, binding.layoutHotList, false)
            val tvKeyword = row.findViewById<TextView>(R.id.tv_keyword)
            val ivArrow = row.findViewById<ImageView>(R.id.iv_arrow)

            // 显示排名
            val rankText = "${index + 1}.  $keyword"
            tvKeyword.text = rankText
            if (index < 3) {
                tvKeyword.setTextColor(0xFFEC4141.toInt())
            }
            ivArrow.visibility = View.GONE

            row.setOnClickListener {
                binding.etSearch.setText(keyword)
                binding.etSearch.setSelection(keyword.length)
                performSearch()
            }
            binding.layoutHotList.addView(row)
        }
    }

    // ==================== 搜索建议 ====================

    private fun loadSuggestions(keyword: String) {
        suggestJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = repository.getSearchSuggest(keyword)
                when (result) {
                    is AppResult.Success -> {
                        val suggestResult = result.data.result
                        val suggestions = mutableListOf<String>()

                        // 优先显示有"最佳匹配"的关键词
                        suggestResult?.songs?.firstOrNull()?.name?.let { suggestions.add(it) }
                        suggestResult?.artists?.firstOrNull()?.name?.let { suggestions.add(it) }
                        suggestResult?.albums?.firstOrNull()?.name?.let { suggestions.add(it) }
                        suggestResult?.playlists?.firstOrNull()?.name?.let { suggestions.add(it) }

                        if (suggestions.isNotEmpty()) {
                            withContext(Dispatchers.Main) {
                                showSuggestions(keyword, suggestions)
                            }
                        }
                    }
                    is AppResult.Error -> { /* 忽略 */ }
                }
            } catch (_: Exception) { }
        }
    }

    private fun showSuggestions(keyword: String, suggestions: List<String>) {
        val keywordLower = keyword.lowercase()
        val filtered = suggestions.filter { it.lowercase().contains(keywordLower) }
        if (filtered.isEmpty()) {
            hideSuggestions()
            return
        }

        binding.rvSuggest.visibility = View.VISIBLE
        binding.layoutHint.visibility = View.GONE
        binding.rvSearchResult.visibility = View.GONE
        binding.tabSearchType.visibility = View.GONE
        binding.tvEmpty.visibility = View.GONE

        suggestAdapter = SuggestAdapter(filtered) { text ->
            binding.etSearch.setText(text)
            binding.etSearch.setSelection(text.length)
            performSearch()
        }
        binding.rvSuggest.adapter = suggestAdapter
    }

    private fun hideSuggestions() {
        binding.rvSuggest.visibility = View.GONE
        suggestAdapter = null
    }

    // ==================== 执行搜索 ====================

    private fun performSearch() {
        val keyword = binding.etSearch.text?.toString()?.trim() ?: ""
        if (keyword.isEmpty()) {
            showToast("请输入搜索关键词")
            return
        }

        // 取消搜索建议防抖，避免建议结果覆盖搜索结果
        suggestJob?.cancel()
        hideSuggestions()

        // 保存到搜索历史
        addToSearchHistory(keyword)

        // 隐藏输入法
        val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.hideSoftInputFromWindow(binding.etSearch.windowToken, 0)

        // 切换 visible，避免在 layout pass 中 requestLayout
        binding.progressBar.post {
            binding.progressBar.visibility = View.VISIBLE
            binding.layoutHint.visibility = View.GONE
            binding.rvSearchResult.visibility = View.GONE
            binding.tabSearchType.visibility = View.GONE
            binding.tvEmpty.visibility = View.GONE
        }

        CoroutineScope(Dispatchers.IO).launch {
            val type = currentSearchType
            val result = repository.search(keyword, type)
            when (result) {
                is AppResult.Success -> {
                    val searchResult = result.data.result
                    val rawSongs = searchResult?.songs ?: emptyList()

                    // 如果是综合搜索（type=1018），歌曲列表可能为空，尝试用 type=1 再次搜索
                    val songsToShow = if (rawSongs.isEmpty() && type == TYPE_COMPREHENSIVE) {
                        // 综合搜索没返回歌曲时，尝试单曲搜索补充
                        try {
                            val fallback = repository.search(keyword, TYPE_SONG)
                            if (fallback is AppResult.Success) {
                                fallback.data.result?.songs ?: emptyList()
                            } else emptyList()
                        } catch (_: Exception) { emptyList() }
                    } else rawSongs

                    // 用 /song/detail 补全歌手和专辑封面信息
                    val enrichedSongs = enrichSongs(songsToShow)
                    repository.markLikedStatus(enrichedSongs)

                    withContext(Dispatchers.Main) {
                        binding.progressBar.post {
                            binding.progressBar.visibility = View.GONE
                            binding.layoutHint.visibility = View.GONE

                            if (type == TYPE_COMPREHENSIVE && shouldShowTabs(searchResult)) {
                                // 综合搜索显示 Tab
                                setupTabs()
                                binding.tabSearchType.visibility = View.VISIBLE
                                binding.tabSearchType.getTabAt(0)?.select()
                            } else {
                                binding.tabSearchType.visibility = View.GONE
                            }

                            searchResults.clear()
                            searchResults.addAll(enrichedSongs)

                            if (enrichedSongs.isEmpty()) {
                                binding.tvEmpty.visibility = View.VISIBLE
                                binding.rvSearchResult.visibility = View.GONE
                            } else {
                                binding.tvEmpty.visibility = View.GONE
                                binding.rvSearchResult.visibility = View.VISIBLE
                                showSearchResults(enrichedSongs)
                            }
                        }
                    }
                }
                is AppResult.Error -> {
                    withContext(Dispatchers.Main) {
                        binding.progressBar.post {
                            binding.progressBar.visibility = View.GONE
                            binding.layoutHint.visibility = View.VISIBLE
                        }
                        showToast(result.exception.message ?: "搜索失败")
                    }
                }
            }
        }
    }

    private fun shouldShowTabs(searchResult: com.example.myapplication.data.remote.SearchResult?): Boolean {
        if (searchResult == null) return false
        return (searchResult.songCount > 0 || searchResult.artistCount > 0 ||
                searchResult.albumCount > 0 || searchResult.playlistCount > 0)
    }

    private fun setupTabs() {
        if (binding.tabSearchType.tabCount > 0) return
        binding.tabSearchType.addTab(binding.tabSearchType.newTab().setText("综合"))
        binding.tabSearchType.addTab(binding.tabSearchType.newTab().setText("单曲"))
        binding.tabSearchType.addTab(binding.tabSearchType.newTab().setText("歌手"))
        binding.tabSearchType.addTab(binding.tabSearchType.newTab().setText("专辑"))
        binding.tabSearchType.addTab(binding.tabSearchType.newTab().setText("歌单"))
    }

    // ==================== 结果增强 ====================

    private suspend fun enrichSongs(songs: List<SongInfo>): List<SongInfo> {
        if (songs.isEmpty()) return songs
        val ids = songs.map { it.id }.joinToString(",")
        return try {
            val detailResult = ApiService.getSongDetail(ids)
            when (detailResult) {
                is AppResult.Success -> {
                    val detailedSongs = detailResult.data.songs ?: return songs
                    val detailMap = detailedSongs.associateBy { it.id }
                    songs.map { song ->
                        detailMap[song.id]?.let { detailed ->
                            SongInfo(
                                id = song.id,
                                name = song.name ?: detailed.name,
                                duration = if (song.dt > 0) song.dt else detailed.dt,
                                al = if (song.al?.picUrl != null) song.al else detailed.al,
                                ar = if (!song.ar.isNullOrEmpty()) song.ar else detailed.ar,
                                dt = if (song.dt > 0) song.dt else detailed.dt
                            ).also { it.liked = song.liked }
                        } ?: song
                    }
                }
                is AppResult.Error -> songs
            }
        } catch (_: Exception) {
            songs
        }
    }

    // ==================== 结果展示 ====================

    private fun showSearchResults(songs: List<SongInfo>) {
        val adapter = HomeAdapter().apply {
            onSongClick = { song ->
                PlayQueueManager.setQueue(searchResults.toList(), searchResults.indexOfFirst { it.id == song.id })
                val intent = Intent(this@SearchActivity, PlayerActivity::class.java).apply {
                    putExtra("song_id", song.id)
                    putExtra("song_name", song.name ?: "未知歌曲")
                    putExtra("artist_name", song.artistNames)
                    putExtra("album_pic_url", song.al?.picUrl)
                    putExtra("duration", song.dt)
                    putExtra("playlist_json", Gson().toJson(searchResults.toList()))
                }
                startActivity(intent)
            }
            onFavoriteClick = { song -> toggleFavorite(song) }
        }
        val items = songs.map { HomeAdapter.HomeItem.SongRow(it) }
        adapter.submitList(items)
        binding.rvSearchResult.adapter = adapter
        searchAdapter = adapter
    }

    private fun toggleFavorite(song: SongInfo) {
        if (!AccountManager.isLoggedIn) {
            showToast("请先登录后再收藏")
            return
        }
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val nowFav = repository.toggleFavorite(song)
                withContext(Dispatchers.Main) {
                    showToast(if (nowFav) "已收藏" else "已取消收藏")
                    searchAdapter?.notifyDataSetChanged()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showToast("操作失败，请稍后重试")
                }
            }
        }
    }

    // ==================== 搜索建议适配器 ====================

    inner class SuggestAdapter(
        private val items: List<String>,
        private val onClick: (String) -> Unit
    ) : RecyclerView.Adapter<SuggestAdapter.VH>() {

        inner class VH(parent: android.view.ViewGroup) : RecyclerView.ViewHolder(
            layoutInflater.inflate(R.layout.item_search_suggest, parent, false)
        ) {
            val tvKeyword: TextView = itemView.findViewById(R.id.tv_keyword)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(parent)
        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val keyword = items[position]
            holder.tvKeyword.text = keyword
            holder.tvKeyword.setTextColor(0xFF333333.toInt())
            holder.itemView.setOnClickListener { onClick(keyword) }
        }
    }
}
