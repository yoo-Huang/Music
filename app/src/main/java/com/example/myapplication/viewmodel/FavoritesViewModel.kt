package com.example.myapplication.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.myapplication.base.BaseViewModel
import com.example.myapplication.base.Result as AppResult
import com.example.myapplication.data.remote.ApiService
import com.example.myapplication.data.remote.SongInfo

/**
 * 收藏页面 ViewModel
 * 加载用户收藏歌曲列表
 */
class FavoritesViewModel : BaseViewModel() {

    private val _songs = MutableLiveData<List<SongInfo>>(emptyList())
    val songs: LiveData<List<SongInfo>> = _songs

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMsg = MutableLiveData<String>()
    val errorMsg: LiveData<String> = _errorMsg

    /**
     * 加载用户收藏歌曲
     * 1. 先获取收藏的歌曲 ID 列表 (/likelist)
     * 2. 再根据 ID 获取歌曲详情 (/song/detail)
     */
    fun loadLikedSongs(uid: Long) {
        _isLoading.value = true
        launchRequestRaw(
            block = {
                // 第一步：获取收藏的歌曲 ID
                val likeResult = ApiService.getLikedSongs(uid)
                if (likeResult is AppResult.Success) {
                    val ids = likeResult.data.ids
                    if (ids.isNullOrEmpty()) {
                        return@launchRequestRaw emptyList<SongInfo>()
                    }
                    // 第二步：根据 ID 获取歌曲详情
                    val idsStr = ids.joinToString(",")
                    val detailResult = ApiService.getSongDetail(idsStr)
                    if (detailResult is AppResult.Success) {
                        val songs = detailResult.data.songs ?: emptyList()
                        // 标记所有歌曲为已收藏
                        songs.forEach { it.liked = true }
                        return@launchRequestRaw songs
                    }
                }
                emptyList<SongInfo>()
            },
            onSuccess = { songs: List<SongInfo> ->
                _isLoading.value = false
                _songs.value = songs
            },
            onError = { e ->
                _isLoading.value = false
                _errorMsg.value = e.message ?: "加载收藏失败"
            }
        )
    }
}
