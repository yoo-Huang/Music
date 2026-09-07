package com.example.myapplication.ui.search

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.myapplication.R
import com.example.myapplication.adapter.SearchHotAdapter
import com.example.myapplication.databinding.FragmentSearchBinding
import com.google.gson.Gson

/**
 * 搜索页 Fragment — 搜索落地页
 * 搜索栏 + 搜索历史 + 热搜榜（RecyclerView + ViewModel + LiveData）
 * 点击搜索栏进入 SearchActivity
 */
class SearchFragment : Fragment() {

    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: SearchHotViewModel
    private lateinit var hotAdapter: SearchHotAdapter

    private val prefs by lazy {
        requireActivity().getSharedPreferences("search_prefs", android.content.Context.MODE_PRIVATE)
    }

    companion object {
        private const val KEY_HISTORY = "search_history"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 初始化 ViewModel
        viewModel = ViewModelProvider(this)[SearchHotViewModel::class.java]

        // 初始化 RecyclerView
        initRecyclerView()

        // 观察 LiveData
        observeViewModel()

        // 点击搜索栏 → 打开 SearchActivity
        binding.layoutSearchBar.setOnClickListener {
            startActivity(Intent(requireContext(), SearchActivity::class.java))
        }

        // 清空搜索历史
        binding.tvClearHistory.setOnClickListener {
            prefs.edit().remove(KEY_HISTORY).apply()
            binding.layoutHistory.visibility = View.GONE
            binding.flexHistory.removeAllViews()
        }

        // 点击重试热搜
        binding.tvRetry.setOnClickListener {
            viewModel.loadHotSearch()
        }

        // 加载搜索历史
        loadSearchHistory()

        // 触发 ViewModel 拉取热搜
        viewModel.loadHotSearch()
    }

    override fun onResume() {
        super.onResume()
        loadSearchHistory()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ==================== RecyclerView ====================

    private fun initRecyclerView() {
        hotAdapter = SearchHotAdapter(
            items = emptyList(),
            onItemClick = { keyword -> navigateToSearch(keyword) }
        )

        binding.rvHotList.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = hotAdapter
            addItemDecoration(
                DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL)
            )
        }
    }

    // ==================== ViewModel 观察 ====================

    private fun observeViewModel() {
        // 热搜列表数据变化 → 更新 Adapter
        viewModel.hotList.observe(viewLifecycleOwner) { list ->
            if (list.isNotEmpty()) {
                hotAdapter.updateData(list)
                binding.rvHotList.visibility = View.VISIBLE
                binding.layoutHotError.visibility = View.GONE
            } else {
                binding.rvHotList.visibility = View.GONE
            }
        }

        // 加载状态 → 控制 ProgressBar
        viewModel.loading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        // 加载失败 → 显示重试入口
        viewModel.loadFailed.observe(viewLifecycleOwner) { failed ->
            binding.layoutHotError.visibility = if (failed) View.VISIBLE else View.GONE
        }
    }

    // ==================== 搜索历史 ====================

    private fun loadSearchHistory() {
        val history = getSearchHistory()
        if (history.isEmpty()) {
            binding.layoutHistory.visibility = View.GONE
            return
        }
        binding.layoutHistory.visibility = View.VISIBLE
        binding.flexHistory.removeAllViews()

        val density = resources.displayMetrics.density
        history.take(10).forEach { keyword ->
            val tag = TextView(requireContext()).apply {
                text = keyword
                textSize = 13f
                setTextColor(0xFF666666.toInt())
                setBackgroundResource(R.drawable.bg_search_tag)
                val padding = (8 * density).toInt()
                setPadding(padding * 2, padding, padding * 2, padding)
                gravity = android.view.Gravity.CENTER
                maxLines = 1
                isSingleLine = true

                val lp = ViewGroup.MarginLayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                lp.setMargins(0, 0, (10 * density).toInt(), (10 * density).toInt())
                layoutParams = lp

                setOnClickListener { navigateToSearch(keyword) }
            }
            binding.flexHistory.addView(tag)
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

    private fun navigateToSearch(keyword: String) {
        startActivity(
            Intent(requireContext(), SearchActivity::class.java).apply {
                putExtra("keyword", keyword)
            }
        )
    }
}
