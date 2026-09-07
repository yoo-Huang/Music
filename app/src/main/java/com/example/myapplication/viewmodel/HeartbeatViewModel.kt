package com.example.myapplication.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.myapplication.base.BaseViewModel
import com.example.myapplication.base.Result as AppResult
import com.example.myapplication.data.remote.ApiService
import com.example.myapplication.data.remote.SongInfo
import com.example.myapplication.data.remote.UserPlaylistItem

/**
 * 心动模式 / 智能播放 ViewModel
 *
 * 工作流程：
 * 1. 加载用户所有歌单供选择
 * 2. 选中歌单后，加载歌单详情获取第一首歌曲作为种子
 * 3. 调用心动模式 API 获取智能推荐的歌曲列表
 */
class HeartbeatViewModel : BaseViewModel() {

    /** 用户歌单列表 */
    private val _playlists = MutableLiveData<List<UserPlaylistItem>>(emptyList())
    val playlists: LiveData<List<UserPlaylistItem>> = _playlists

    /** 智能推荐的歌曲列表 */
    private val _songs = MutableLiveData<List<SongInfo>>(emptyList())
    val songs: LiveData<List<SongInfo>> = _songs

    /** 加载状态 */
    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    /** 错误消息 */
    private val _errorMsg = MutableLiveData<String>()
    val errorMsg: LiveData<String> = _errorMsg

    /** 当前选中的歌单 ID */
    private val _selectedPlaylistId = MutableLiveData(0L)
    val selectedPlaylistId: LiveData<Long> = _selectedPlaylistId

    /**
     * 加载用户所有歌单
     */
    fun loadUserPlaylists(uid: Long) {
        _isLoading.value = true
        launchRequestRaw(
            block = {
                val result = ApiService.getUserPlaylist(uid)
                if (result is AppResult.Success) {
                    result.data.playlist ?: emptyList()
                } else {
                    emptyList()
                }
            },
            onSuccess = { playlists: List<UserPlaylistItem> ->
                _isLoading.value = false
                _playlists.value = playlists
            },
            onError = { e ->
                _isLoading.value = false
                _errorMsg.value = e.message ?: "加载歌单失败"
            }
        )
    }

    /**
     * 加载心动模式推荐歌曲
     * 先获取歌单详情拿到第一首歌曲的 ID 作为种子
     * 再调用心动模式 API 获取推荐
     */
    fun loadIntelligenceSongs(playlistId: Long) {
        _selectedPlaylistId.value = playlistId
        _isLoading.value = true

        launchRequestRaw(
            block = {
                // 步骤1：获取歌单详情，拿到第一首歌曲 ID
                val detailResult = ApiService.getPlaylistDetail(playlistId)
                if (detailResult !is AppResult.Success || detailResult.data.playlist == null) {
                    throw Exception("无法获取歌单详情")
                }

                val tracks = detailResult.data.playlist.tracks
                if (tracks.isNullOrEmpty()) {
                    throw Exception("歌单中没有歌曲")
                }

                // 步骤2：以歌单第一首歌为种子，调用心动模式 API
                val firstSong = tracks.first()
                val intelligenceResult = ApiService.getIntelligenceList(
                    id = playlistId,
                    pid = firstSong.id,
                    count = 30
                )

                if (intelligenceResult is AppResult.Success) {
                    val songs = intelligenceResult.data.extractSongs()
                    if (songs.isNotEmpty()) songs else tracks
                } else {
                    // 心动 API 不可用，回退使用歌单歌曲
                    tracks
                }
            },
            onSuccess = { songs: List<SongInfo> ->
                _isLoading.value = false
                _songs.value = songs
            },
            onError = { e ->
                _isLoading.value = false
                _errorMsg.value = e.message ?: "加载心动推荐失败"
            }
        )
    }
}
