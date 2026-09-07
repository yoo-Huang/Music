package com.example.myapplication.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.myapplication.data.local.AppDatabase
import com.example.myapplication.MyApp
import com.example.myapplication.base.AppException
import com.example.myapplication.base.BaseViewModel
import com.example.myapplication.base.Result as AppResult
import com.example.myapplication.data.remote.ApiClient
import com.example.myapplication.data.remote.ApiService
import com.example.myapplication.data.remote.SongInfo
import com.example.myapplication.manager.AccountManager
import com.google.gson.Gson
import org.json.JSONObject

/**
 * 最近播放页面 ViewModel
 * 三层兜底：/record/recent/song → /user/record → 原始 JSON 手动提取
 */
class RecentPlayViewModel : BaseViewModel() {

    private val _songs = MutableLiveData<List<SongInfo>>(emptyList())
    val songs: LiveData<List<SongInfo>> = _songs

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMsg = MutableLiveData<String>()
    val errorMsg: LiveData<String> = _errorMsg

    fun loadRecentSongs() {
        _isLoading.value = true
        launchRequestRaw(
            block = {
                val uid = AccountManager.userId
                Log.d("RecentPlayVM", "Loading recent songs, uid=$uid, hasCookie=${AccountManager.cookie != null}")

                var networkSongs: List<SongInfo> = emptyList()

                // 第 1 层
                val result = ApiService.getRecentSongs(uid)
                when (result) {
                    is AppResult.Success -> {
                        val resp = result.data
                        val songs = resp.extractSongs()
                        Log.d("RecentPlayVM", "1) /record/recent/song → code=${resp.code}, songs=${songs.size}")
                        if (resp.isBizSuccess() && songs.isNotEmpty()) networkSongs = songs
                        if (!resp.isBizSuccess()) Log.w("RecentPlayVM", "1) ${resp.bizMessage()}")
                    }
                    is AppResult.Error -> Log.w("RecentPlayVM", "1) error: ${result.exception.message}")
                }

                // 第 2 层
                if (networkSongs.isEmpty()) {
                    val fallback = ApiService.getUserRecord(uid, type = 0)
                    when (fallback) {
                        is AppResult.Success -> {
                            val fbResp = fallback.data
                            val fbSongs = fbResp.extractSongs()
                            Log.d("RecentPlayVM", "2) /user/record → code=${fbResp.code}, songs=${fbSongs.size}")
                            if (fbResp.isBizSuccess() && fbSongs.isNotEmpty()) networkSongs = fbSongs
                        }
                        is AppResult.Error -> Log.w("RecentPlayVM", "2) error: ${fallback.exception.message}")
                    }
                }

                // 第 3 层：原始 JSON 兜底
                if (networkSongs.isEmpty()) {
                    val rawSongs = tryFetchFromRawJson(uid)
                    if (rawSongs.isNotEmpty()) {
                        Log.d("RecentPlayVM", "3) raw JSON → ${rawSongs.size} songs")
                        networkSongs = rawSongs
                    }
                }

                // 第 4 层：本地数据库（始终合并）
                var localSongs: List<SongInfo> = emptyList()
                try {
                    localSongs = tryFetchFromLocalDb()
                    Log.d("RecentPlayVM", "4) local DB → ${localSongs.size} songs")
                } catch (e: Exception) {
                    Log.w("RecentPlayVM", "4) local DB error: ${e.message}")
                }

                // 合并去重
                val networkIds = networkSongs.map { it.id }.toSet()
                val localOnlySongs = localSongs.filter { it.id !in networkIds }
                val finalSongs = networkSongs + localOnlySongs
                Log.d("RecentPlayVM", "Final: network=${networkSongs.size} + localOnly=${localOnlySongs.size} = total=${finalSongs.size}")

                if (finalSongs.isNotEmpty()) {
                    return@launchRequestRaw finalSongs
                }

                throw AppException.ServerException(
                    code = -1,
                    message = "未获取到播放记录，请确认已在网易云音乐App中播放过歌曲"
                )
            },
            onSuccess = { songs: List<SongInfo> ->
                _isLoading.value = false
                _songs.value = songs
                if (songs.isEmpty()) _errorMsg.value = "还没有播放记录"
            },
            onError = { e ->
                _isLoading.value = false
                _errorMsg.value = e.message ?: "加载最近播放失败"
                Log.e("RecentPlayVM", "All strategies failed: ${e.message}", e)
            }
        )
    }

    private suspend fun tryFetchFromRawJson(uid: Long): List<SongInfo> {
        val account = AccountManager
        val cookie = account.cookie ?: return emptyList()
        val paramMap = mutableMapOf(
            "cookie" to cookie,
            "uid" to (if (uid > 0) uid.toString() else "").ifBlank { "0" },
            "type" to "0"
        )
        val csrf = account.csrfToken
        if (!csrf.isNullOrBlank()) paramMap["csrf_token"] = csrf

        val raw1 = ApiClient.getRaw("/record/recent/song", paramMap)
        if (!raw1.isNullOrBlank()) {
            val songs = parseRawRecordJson(raw1)
            if (songs.isNotEmpty()) return songs
        }
        val raw2 = ApiClient.getRaw("/user/record", paramMap)
        if (!raw2.isNullOrBlank()) {
            val songs = parseRawRecordJson(raw2)
            if (songs.isNotEmpty()) return songs
        }
        return emptyList()
    }

    private fun parseRawRecordJson(rawJson: String): List<SongInfo> {
        return try {
            val root = JSONObject(rawJson)
            if (root.optInt("code", -1) != 200) return emptyList()
            val gson = Gson()
            val containers = listOf(
                root.optJSONObject("data")?.optJSONArray("list"),
                root.optJSONArray("allData"),
                root.optJSONArray("weekData"),
                root.optJSONObject("data")?.optJSONArray("allData"),
                root.optJSONObject("data")?.optJSONArray("weekData"),
            )
            for (arr in containers) {
                if (arr == null || arr.length() == 0) continue
                val songs = mutableListOf<SongInfo>()
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    val songObj = item.optJSONObject("data") ?: item.optJSONObject("song") ?: item
                    try {
                        val song = gson.fromJson(songObj.toString(), SongInfo::class.java)
                        if (song.id > 0) songs.add(song)
                    } catch (_: Exception) {}
                }
                if (songs.isNotEmpty()) return songs
            }
            emptyList()
        } catch (e: Exception) {
            Log.e("RecentPlayVM", "parseRawRecordJson: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * 第 4 层兜底：从本地 Room 数据库读取播放历史
     * 捕捉 App 内播放但服务器尚未同步的记录
     */
    private suspend fun tryFetchFromLocalDb(): List<SongInfo> {
        return try {
            val entities = AppDatabase.getInstance(MyApp.instance)
                .playHistoryDao()
                .getPlayHistorySync()
            if (entities.isEmpty()) return emptyList()
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
            Log.e("RecentPlayVM", "tryFetchFromLocalDb error: ${e.message}", e)
            emptyList()
        }
    }
}
