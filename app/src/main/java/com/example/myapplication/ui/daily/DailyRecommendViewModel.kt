package com.example.myapplication.ui.daily

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.myapplication.base.BaseViewModel
import com.example.myapplication.data.Repository
import com.example.myapplication.data.remote.DailyRecommendSongsResponse
import com.example.myapplication.data.remote.SongInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 每日推荐歌曲 ViewModel
 */
class DailyRecommendViewModel : BaseViewModel() {

    private val repository = Repository.getInstance()

    @PublishedApi internal val _songs = MutableLiveData<List<SongInfo>>()
    val songs: LiveData<List<SongInfo>> = _songs

    @PublishedApi internal val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    @PublishedApi internal val _errorMsg = MutableLiveData<String>()
    val errorMsg: LiveData<String> = _errorMsg

    /**
     * 加载每日推荐歌曲
     */
    fun loadDailySongs() {
        _isLoading.value = true
        launchRequest(
            block = { repository.getDailyRecommendSongs() },
            onSuccess = { response: DailyRecommendSongsResponse ->
                _isLoading.value = false
                val songs = response.data?.dailySongs ?: emptyList()
                // 标记红心状态
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
}
