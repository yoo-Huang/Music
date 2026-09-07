package com.example.myapplication.ui.search

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.remote.ApiClient
import com.example.myapplication.data.remote.SearchHotItem
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * 搜索页热搜 ViewModel
 * 使用 getRaw 获取原始 JSON，手动解析兼容多版本 API 结构
 */
class SearchHotViewModel : ViewModel() {

    companion object {
        private const val TAG = "SearchHotVM"
    }

    /** 热搜数据列表 */
    private val _hotList = MutableLiveData<List<SearchHotItem>>()
    val hotList: LiveData<List<SearchHotItem>> = _hotList

    /** 加载状态 */
    private val _loading = MutableLiveData<Boolean>()
    val loading: LiveData<Boolean> = _loading

    /** 是否加载失败 */
    private val _loadFailed = MutableLiveData<Boolean>()
    val loadFailed: LiveData<Boolean> = _loadFailed

    fun loadHotSearch() {
        if (_loading.value == true) return
        _loading.value = true
        _hotList.value = emptyList()
        _loadFailed.value = false

        viewModelScope.launch {
            val result = parseHotSearch()
            if (result.isNotEmpty()) {
                _hotList.value = result
                _loadFailed.value = false
            } else {
                _loadFailed.value = true
            }
            _loading.value = false
        }
    }

    /**
     * 手动解析 /search/hot 原始响应
     * 兼容两种格式：
     *   A) {"code":200, "result":{"hots":[{"first":"关键词","second":1},...]}}
     *   B) {"code":200, "data":[{"searchWord":"关键词","score":123},...]}
     */
    private suspend fun parseHotSearch(): List<SearchHotItem> {
        return try {
            // 先 POST（标准 NeteaseCloudMusicApiBackup 实现），失败再 GET 兜底
            var raw = ApiClient.postRaw("/search/hot")
            if (raw == null) {
                Log.w(TAG, "POST /search/hot 失败，尝试 GET 兜底")
                raw = ApiClient.getRaw("/search/hot")
            }
            if (raw == null) {
                Log.e(TAG, "POST 和 GET 均失败，网络不通或接口不存在")
                return emptyList()
            }
            Log.d(TAG, "/search/hot 原始响应: ${raw.take(500)}")

            val root = JSONObject(raw)
            val code = root.optInt("code")
            Log.d(TAG, "业务 code = $code")
            if (code != 200) {
                Log.w(TAG, "业务 code != 200，跳过解析")
                return emptyList()
            }

            // 格式 A：result.hots
            val resultObj = root.optJSONObject("result")
            if (resultObj != null) {
                val hots = resultObj.optJSONArray("hots")
                Log.d(TAG, "格式A result.hots 长度 = ${hots?.length() ?: 0}")
                if (hots != null && hots.length() > 0) {
                    return (0 until hots.length()).map { i ->
                        val item = hots.getJSONObject(i)
                        SearchHotItem(
                            searchWord = item.optString("first"),
                            score = item.optInt("second"),
                            content = item.optString("third", "")
                        )
                    }
                }
            } else {
                Log.d(TAG, "result 对象为 null，尝试格式B")
            }

            // 格式 B：data 数组
            val dataArr = root.optJSONArray("data")
            Log.d(TAG, "格式B data 数组长度 = ${dataArr?.length() ?: 0}")
            if (dataArr != null && dataArr.length() > 0) {
                return (0 until dataArr.length()).map { i ->
                    val item = dataArr.getJSONObject(i)
                    SearchHotItem(
                        searchWord = item.optString("searchWord"),
                        score = item.optInt("score"),
                        content = item.optString("content", ""),
                        iconType = item.optInt("iconType"),
                        iconUrl = item.optString("iconUrl", "")
                    )
                }
            }

            Log.w(TAG, "未能匹配任何格式，返回空列表")
            emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "解析热搜异常: ${e.message}", e)
            emptyList()
        }
    }
}
