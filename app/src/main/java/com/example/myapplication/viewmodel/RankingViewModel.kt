package com.example.myapplication.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.myapplication.base.BaseViewModel
import com.example.myapplication.base.Result as AppResult
import com.example.myapplication.data.Repository
import com.example.myapplication.data.remote.MvItem
import com.example.myapplication.data.remote.NewAlbumItem
import com.example.myapplication.data.remote.SongInfo
import com.example.myapplication.data.remote.TopArtistInfo
import com.example.myapplication.data.remote.ToplistItem

/**
 * 排行榜 ViewModel
 * 管理五个 Tab 的数据：全部榜单、歌手榜、MV 排行、数字专辑榜、细分榜单
 */
class RankingViewModel : BaseViewModel() {

    private val repository = Repository.getInstance()

    // ==================== 全部榜单 ====================

    @PublishedApi internal val _toplists = MutableLiveData<List<ToplistItem>>()
    val toplists: LiveData<List<ToplistItem>> = _toplists

    // ==================== 歌手榜 ====================

    @PublishedApi internal val _artists = MutableLiveData<List<TopArtistInfo>>()
    val artists: LiveData<List<TopArtistInfo>> = _artists

    // ==================== MV 排行 ====================

    @PublishedApi internal val _topMvs = MutableLiveData<List<MvItem>>()
    val topMvs: LiveData<List<MvItem>> = _topMvs

    // ==================== 数字专辑 ====================

    @PublishedApi internal val _newAlbums = MutableLiveData<List<NewAlbumItem>>()
    val newAlbums: LiveData<List<NewAlbumItem>> = _newAlbums

    // ==================== 榜单歌曲（点击榜单后的歌曲列表） ====================

    @PublishedApi internal val _toplistSongs = MutableLiveData<List<SongInfo>>()
    val toplistSongs: LiveData<List<SongInfo>> = _toplistSongs

    @PublishedApi internal val _toplistName = MutableLiveData<String>()
    val toplistName: LiveData<String> = _toplistName

    // ==================== 加载状态 ====================

    @PublishedApi internal val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    @PublishedApi internal val _errorMsg = MutableLiveData<String>()
    val errorMsg: LiveData<String> = _errorMsg

    // ==================== 当前选中的 Tab 位置 ====================

    @PublishedApi internal val _currentTab = MutableLiveData(0)
    val currentTab: LiveData<Int> = _currentTab

    /**
     * 加载全部榜单摘要
     */
    fun loadToplists() {
        _isLoading.value = true
        launchRequestRaw(
            block = { repository.getToplistDetail() },
            onSuccess = { result ->
                _isLoading.value = false
                when (result) {
                    is AppResult.Success -> _toplists.value = result.data.list ?: emptyList()
                    is AppResult.Error -> _errorMsg.value = result.exception.message
                }
            },
            onError = { e ->
                _isLoading.value = false
                _errorMsg.value = e.message
            }
        )
    }

    /**
     * 加载歌手排行榜（默认华语）
     */
    fun loadArtists(type: Int = 1) {
        _isLoading.value = true
        launchRequestRaw(
            block = { repository.getTopArtists(type) },
            onSuccess = { result ->
                _isLoading.value = false
                when (result) {
                    is AppResult.Success -> _artists.value = result.data.list?.artists ?: emptyList()
                    is AppResult.Error -> _errorMsg.value = result.exception.message
                }
            },
            onError = { e ->
                _isLoading.value = false
                _errorMsg.value = e.message
            }
        )
    }

    /**
     * 加载 MV 排行榜
     */
    fun loadTopMvs() {
        _isLoading.value = true
        launchRequestRaw(
            block = { repository.getTopMv() },
            onSuccess = { result ->
                _isLoading.value = false
                when (result) {
                    is AppResult.Success -> _topMvs.value = result.data.data ?: emptyList()
                    is AppResult.Error -> _errorMsg.value = result.exception.message
                }
            },
            onError = { e ->
                _isLoading.value = false
                _errorMsg.value = e.message
            }
        )
    }

    /**
     * 加载榜单歌曲列表
     */
    fun loadToplistSongs(toplistId: Long, name: String) {
        _isLoading.value = true
        _toplistName.value = name
        launchRequestRaw(
            block = { repository.getToplist(toplistId) },
            onSuccess = { result ->
                _isLoading.value = false
                when (result) {
                    is AppResult.Success -> _toplistSongs.value = result.data.playlist?.tracks ?: emptyList()
                    is AppResult.Error -> _errorMsg.value = result.exception.message
                }
            },
            onError = { e ->
                _isLoading.value = false
                _errorMsg.value = e.message
            }
        )
    }

    /**
     * 加载新专辑（数字专辑榜）
     */
    fun loadNewAlbums() {
        _isLoading.value = true
        launchRequestRaw(
            block = { repository.getNewAlbums() },
            onSuccess = { result ->
                _isLoading.value = false
                when (result) {
                    is AppResult.Success -> _newAlbums.value = result.data.albums ?: emptyList()
                    is AppResult.Error -> _errorMsg.value = result.exception.message
                }
            },
            onError = { e ->
                _isLoading.value = false
                _errorMsg.value = e.message
            }
        )
    }

    /**
     * 切换 Tab
     */
    fun selectTab(position: Int) {
        _currentTab.value = position
    }
}
