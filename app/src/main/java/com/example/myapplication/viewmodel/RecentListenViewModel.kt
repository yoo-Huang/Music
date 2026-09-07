package com.example.myapplication.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.myapplication.data.local.AppDatabase
import com.example.myapplication.MyApp
import com.example.myapplication.base.AppException
import com.example.myapplication.base.BaseViewModel
import com.example.myapplication.base.Result as AppResult
import com.example.myapplication.data.remote.ApiService
import com.example.myapplication.data.remote.SongInfo
import com.example.myapplication.manager.AccountManager
import com.google.gson.Gson
import org.json.JSONObject

/**
 * 最近常听页面 ViewModel
 * 数据来源（结果会合并去重）：
 *   1. /record/recent/song（主端点，Gson 类型化解析）
 *   2. /user/record（降级端点，Gson 类型化解析）
 *   3. 原始 JSON 手动提取（绕过体系限制）
 *   4. 本地 Room 数据库（App 内播放记录，始终合并到结果中）
 */
class RecentListenViewModel : BaseViewModel() {

    private val _songs = MutableLiveData<List<SongInfo>>(emptyList())
    val songs: LiveData<List<SongInfo>> = _songs

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMsg = MutableLiveData<String>()
    val errorMsg: LiveData<String> = _errorMsg

    /**
     * 加载最近播放歌曲列表
     * 兼容不同 NetEase API 版本的响应格式
     */
    fun loadRecentListenSongs() {
        _isLoading.value = true
        launchRequestRaw(
            block = {
                val uid = AccountManager.userId
                val cookie = AccountManager.cookie
                Log.d("RecentListenVM", "Loading recent songs, uid=$uid, cookieLen=${cookie?.length ?: 0}, hasCsrf=${AccountManager.csrfToken != null}")

                // 用于收集网络层返回的歌曲
                var networkSongs: List<SongInfo> = emptyList()

                // ---- 第 1 层：主端点 /record/recent/song（Gson 解析） ----
                val result = ApiService.getRecentSongs(uid)
                when (result) {
                    is AppResult.Success -> {
                        val resp = result.data
                        val songs = resp.extractSongs()
                        Log.d("RecentListenVM", "1) /record/recent/song → code=${resp.code}, songs=${songs.size}, msg=${resp.message}")
                        if (resp.isBizSuccess() && songs.isNotEmpty()) {
                            networkSongs = songs
                        } else if (resp.isBizSuccess() && songs.isEmpty()) {
                            Log.w("RecentListenVM", "1) code=200 but empty list, will try fallback")
                        } else {
                            Log.w("RecentListenVM", "1) failed: ${resp.bizMessage()}")
                        }
                    }
                    is AppResult.Error -> {
                        Log.w("RecentListenVM", "1) network error: ${result.exception.message}")
                    }
                }

                // ---- 第 2 层：降级端点 /user/record（Gson 解析） ----
                if (networkSongs.isEmpty()) {
                    val fallback = ApiService.getUserRecord(uid, type = 0)
                    when (fallback) {
                        is AppResult.Success -> {
                            val fbResp = fallback.data
                            val fbSongs = fbResp.extractSongs()
                            Log.d("RecentListenVM", "2) /user/record → code=${fbResp.code}, songs=${fbSongs.size}, msg=${fbResp.message}")
                            if (fbResp.isBizSuccess() && fbSongs.isNotEmpty()) {
                                networkSongs = fbSongs
                            } else if (fbResp.isBizSuccess() && fbSongs.isEmpty()) {
                                Log.w("RecentListenVM", "2) code=200 but empty list, will try raw parse")
                            } else {
                                Log.w("RecentListenVM", "2) failed: ${fbResp.bizMessage()}")
                            }
                        }
                        is AppResult.Error -> {
                            Log.w("RecentListenVM", "2) network error: ${fallback.exception.message}")
                        }
                    }
                }

                // ---- 第 3 层：原始 JSON 手动提取（兜底，绕过 Gson 类型映射） ----
                if (networkSongs.isEmpty()) {
                    val rawSongs = tryFetchFromRawJson(uid)
                    if (rawSongs.isNotEmpty()) {
                        Log.d("RecentListenVM", "3) raw JSON → extracted ${rawSongs.size} songs")
                        networkSongs = rawSongs
                    }
                }

                // ---- 第 4 层：本地数据库（始终读取，与网络结果合并去重） ----
                var localSongs: List<SongInfo> = emptyList()
                try {
                    localSongs = tryFetchFromLocalDb()
                    Log.d("RecentListenVM", "4) local DB → ${localSongs.size} songs")
                } catch (e: Exception) {
                    Log.w("RecentListenVM", "4) local DB error: ${e.message}")
                }

                // ---- 合并网络与本地结果（去重，本地在前作为补充） ----
                val networkIds = networkSongs.map { it.id }.toSet()
                val localOnlySongs = localSongs.filter { it.id !in networkIds }
                val finalSongs = networkSongs + localOnlySongs
                Log.d("RecentListenVM", "Final: network=${networkSongs.size} + localOnly=${localOnlySongs.size} = total=${finalSongs.size}")

                if (finalSongs.isNotEmpty()) {
                    return@launchRequestRaw finalSongs
                }

                // 所有方式都失败
                throw AppException.ServerException(
                    code = -1,
                    message = "未获取到播放记录，请确认已在网易云音乐App中播放过歌曲"
                )
            },
            onSuccess = { songs: List<SongInfo> ->
                _isLoading.value = false
                _songs.value = songs
                if (songs.isEmpty()) {
                    _errorMsg.value = "还没有播放记录"
                }
            },
            onError = { e ->
                _isLoading.value = false
                _errorMsg.value = e.message ?: "加载最近播放失败"
                Log.e("RecentListenVM", "All strategies failed: ${e.message}", e)
            }
        )
    }

    /**
     * 第 3 层兜底：通过 getRaw 获取原始 JSON，手动提取歌曲列表
     * 绕过 Gson 类型映射，兼容各种不规范的响应结构
     */
    private suspend fun tryFetchFromRawJson(uid: Long): List<SongInfo> {
        val account = AccountManager
        val cookie = account.cookie ?: return emptyList()
        val paramMap = mutableMapOf<String, String>().apply {
            put("cookie", cookie)
            if (uid > 0) put("uid", uid.toString())
            put("type", "0")
            val csrf = account.csrfToken
            if (!csrf.isNullOrBlank()) put("csrf_token", csrf)
        }

        // 尝试 /record/recent/song 原始响应
        val raw1 = com.example.myapplication.data.remote.ApiClient.getRaw("/record/recent/song", paramMap)
        if (!raw1.isNullOrBlank()) {
            val extracted = parseRawRecordJson(raw1)
            if (extracted.isNotEmpty()) return extracted
        }

        // 尝试 /user/record 原始响应
        val raw2 = com.example.myapplication.data.remote.ApiClient.getRaw("/user/record", paramMap)
        if (!raw2.isNullOrBlank()) {
            val extracted = parseRawRecordJson(raw2)
            if (extracted.isNotEmpty()) return extracted
        }

        return emptyList()
    }

    /**
     * 从原始 JSON 字符串中手动提取 SongInfo 列表
     * 兼容各种嵌套层级和不规范字段名
     */
    private fun parseRawRecordJson(rawJson: String): List<SongInfo> {
        return try {
            val root = JSONObject(rawJson)
            val code = root.optInt("code", -1)
            if (code != 200) {
                Log.d("RecentListenVM", "raw JSON code=$code, skip parse")
                return emptyList()
            }

            val gson = Gson()

            // 查找可能的记录数组容器
            val candidates = listOf(
                root.optJSONObject("data")?.optJSONArray("list"),          // /record/recent/song
                root.optJSONArray("allData"),                               // /user/record 顶层 allData
                root.optJSONArray("weekData"),                              // /user/record 顶层 weekData
                root.optJSONObject("data")?.optJSONArray("allData"),        // /user/record data.allData
                root.optJSONObject("data")?.optJSONArray("weekData"),       // /user/record data.weekData
            )

            for (arr in candidates) {
                if (arr == null || arr.length() == 0) continue
                val songs = mutableListOf<SongInfo>()
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    // 每项可能是 { data: {...} } 或 { song: {...} } 或直接是 {...}
                    val songObj = item.optJSONObject("data")
                        ?: item.optJSONObject("song")
                        ?: item
                    try {
                        val song = gson.fromJson(songObj.toString(), SongInfo::class.java)
                        if (song.id > 0) {
                            songs.add(song)
                        }
                    } catch (_: Exception) {
                        // 单个条目解析失败，跳过
                    }
                }
                if (songs.isNotEmpty()) return songs
            }

            // 最后一招：直接在根层查找任意歌曲数组
            Log.d("RecentListenVM", "raw JSON: none of the known containers matched")
            Log.d("RecentListenVM", "raw JSON keys: ${root.keys().asSequence().toList()}")
            emptyList()
        } catch (e: Exception) {
            Log.e("RecentListenVM", "parseRawRecordJson error: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * 第 4 层兜底：从本地 Room 数据库读取播放历史
     * 当所有网络 API 都不可用时，展示 App 本地记录的最近播放歌曲
     */
    private suspend fun tryFetchFromLocalDb(): List<SongInfo> {
        return try {
            val entities = AppDatabase.getInstance(MyApp.instance)
                .playHistoryDao()
                .getPlayHistorySync()
            if (entities.isEmpty()) return emptyList()
            // 转换为 SongInfo
            entities.map { entity ->
                SongInfo(
                    id = entity.songId,
                    name = entity.songName,
                    dt = entity.duration,
                    al = com.example.myapplication.data.remote.AlbumInfo(picUrl = entity.albumPicUrl),
                    ar = listOf(
                        com.example.myapplication.data.remote.ArtistInfo(name = entity.artistName)
                    )
                )
            }
        } catch (e: Exception) {
            Log.e("RecentListenVM", "tryFetchFromLocalDb error: ${e.message}", e)
            emptyList()
        }
    }
}
