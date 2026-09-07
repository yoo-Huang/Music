package com.example.myapplication.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.myapplication.base.AppException
import com.example.myapplication.base.BaseViewModel
import com.example.myapplication.data.Repository
import com.example.myapplication.data.remote.AlbumDetail
import com.example.myapplication.data.remote.CreatorInfo
import com.example.myapplication.data.remote.PlaylistDetail
import com.example.myapplication.data.remote.PlaylistDetailResponse
import com.example.myapplication.data.remote.SongInfo
import com.example.myapplication.data.remote.SongUrlResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 歌单详情 ViewModel
 */
class PlaylistViewModel : BaseViewModel() {

    private val repository = Repository.getInstance()
    private var playlistId: Long = 0

    @PublishedApi internal val _playlist = MutableLiveData<PlaylistDetail>()
    val playlist: LiveData<PlaylistDetail> = _playlist

    @PublishedApi internal val _songs = MutableLiveData<List<SongInfo>>()
    val songs: LiveData<List<SongInfo>> = _songs

    @PublishedApi internal val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    @PublishedApi internal val _errorMsg = MutableLiveData<String>()
    val errorMsg: LiveData<String> = _errorMsg

    @PublishedApi internal val _subscribed = MutableLiveData(false)
    val subscribed: LiveData<Boolean> = _subscribed

    // 专辑模式相关
    @PublishedApi internal val _album = MutableLiveData<AlbumDetail>()
    val album: LiveData<AlbumDetail> = _album

    @PublishedApi internal val _isAlbumMode = MutableLiveData(false)
    val isAlbumMode: LiveData<Boolean> = _isAlbumMode

    /**
     * 加载歌单详情
     * @param playlistId 歌单 ID
     */
    fun loadPlaylistDetail(id: Long) {
        playlistId = id
        _isAlbumMode.value = false
        _isLoading.value = true
        launchRequest(
            block = { repository.getPlaylistDetail(playlistId) },
            onSuccess = { response: PlaylistDetailResponse ->
                _isLoading.value = false
                _playlist.value = response.playlist
                val tracks = response.playlist?.tracks ?: emptyList()
                _subscribed.value = response.playlist?.subscribed ?: false
                val isLikedPlaylist = response.playlist?.name?.contains(
                    "喜欢的音乐", ignoreCase = true
                ) == true
                // 标记红心状态
                viewModelScope.launch(Dispatchers.IO) {
                    if (isLikedPlaylist) {
                        // 我喜欢的音乐：歌单内所有歌曲天然是已喜欢状态
                        tracks.forEach { it.liked = true }
                    } else {
                        repository.markLikedStatus(tracks)
                    }
                    _songs.postValue(tracks)
                }
            },
            onError = { e ->
                _isLoading.value = false
                _errorMsg.value = e.message
            }
        )
    }

    /**
     * 加载专辑详情（含歌曲列表）
     * @param albumId 专辑 ID
     */
    fun loadAlbumDetail(albumId: Long) {
        playlistId = albumId
        _isAlbumMode.value = true
        _isLoading.value = true
        launchRequest(
            block = { repository.getAlbumDetail(albumId) },
            onSuccess = { response ->
                _isLoading.value = false
                val album = response.album
                val songs = album?.let {
                    // Album detail response may return songs at top level too
                    response.songs ?: emptyList()
                } ?: emptyList()
                _album.value = album
                // 将专辑信息模拟为 PlaylistDetail 以复用 UI
                _playlist.value = PlaylistDetail(
                    id = album?.id ?: albumId,
                    name = album?.name,
                    coverImgUrl = album?.picUrl,
                    creator = album?.artist?.let { artist ->
                        CreatorInfo(
                            nickname = artist.name,
                            avatarUrl = artist.picUrl
                        )
                    },
                    trackCount = songs.size,
                    playCount = 0,
                    tracks = songs
                )
                viewModelScope.launch(Dispatchers.IO) {
                    repository.markLikedStatus(songs)
                    _songs.postValue(songs)
                }
            },
            onError = { e ->
                _isLoading.value = false
                _errorMsg.value = e.message
            }
        )
    }

    /**
     * 切换歌单收藏状态
     */
    fun toggleSubscribe() {
        if (_playlist.value == null) return
        val current = _subscribed.value ?: false
        val newSubscribed = !current
        launchRequestRaw(
            block = { repository.togglePlaylistSubscribe(playlistId, newSubscribed) },
            onSuccess = { success: Boolean ->
                if (success) _subscribed.value = newSubscribed
            },
            onError = { e ->
                _errorMsg.value = e.message
            }
        )
    }

    /**
     * 加载歌曲播放 URL
     * @param onUrlReady 回调：(url, 实际音频时长毫秒)，url 为 null 表示获取失败
     */
    fun loadSongUrl(songId: Long, onUrlReady: (url: String?, audioTime: Long) -> Unit) {
        launchRequest(
            block = { repository.getSongUrl(songId) },
            onSuccess = { response: SongUrlResponse ->
                val data = response.data?.firstOrNull()
                val url = data?.url
                val time = data?.time ?: 0L
                if (url.isNullOrEmpty()) {
                    // 尝试备用的歌曲 URL 接口
                    loadSongUrlFallback(songId, onUrlReady)
                } else {
                    onUrlReady(url, time)
                }
            },
            onError = {
                _errorMsg.value = it.message
                // 失败也回调 null，避免 onUrlReady 永远不被调用
                loadSongUrlFallback(songId, onUrlReady)
            }
        )
    }

    /**
     * 备用歌曲 URL 加载（使用 /song/url 接口）
     */
    private fun loadSongUrlFallback(songId: Long, onUrlReady: (url: String?, audioTime: Long) -> Unit) {
        launchRequest(
            block = { repository.getSongUrlV2(songId) },
            onSuccess = { response: SongUrlResponse ->
                val data = response.data?.firstOrNull()
                val url = data?.url
                val time = data?.time ?: 0L
                onUrlReady(url, time)
            },
            onError = {
                onUrlReady(null, 0L)
            }
        )
    }
}
