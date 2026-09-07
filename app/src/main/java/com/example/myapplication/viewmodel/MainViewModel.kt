package com.example.myapplication.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.myapplication.base.BaseViewModel
import com.example.myapplication.base.Result as AppResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import com.example.myapplication.data.Repository
import com.example.myapplication.data.remote.AlbumInfo
import com.example.myapplication.data.remote.BannerItem
import com.example.myapplication.data.remote.BannerResponse
import com.example.myapplication.data.remote.NewSongResponse
import com.example.myapplication.data.remote.DjProgramItem
import com.example.myapplication.data.remote.DjRadioItem
import com.example.myapplication.data.remote.RecommendPlaylist
import com.example.myapplication.data.remote.RecommendPlaylistResponse
import com.example.myapplication.data.remote.SongInfo

/**
 * 首页 ViewModel
 * 使用 LiveData 驱动 UI 更新
 */
class MainViewModel : BaseViewModel() {

    /**
     * 电台页面组合数据（原子更新，避免三次 postValue 导致的 UI 闪烁）
     */
    data class RadioPageData(
        val recommend: List<DjRadioItem>,
        val programs: List<DjProgramItem>,
        val categoryRecommend: List<DjProgramItem>
    )

    private val repository = Repository.getInstance()

    /** Banner 数据 */
    @PublishedApi internal val _banners = MutableLiveData<List<BannerItem>>()
    val banners: LiveData<List<BannerItem>> = _banners

    /** 推荐歌单 */
    @PublishedApi internal val _playlists = MutableLiveData<List<RecommendPlaylist>>()
    val playlists: LiveData<List<RecommendPlaylist>> = _playlists

    /** 新歌列表 */
    @PublishedApi internal val _newSongs = MutableLiveData<List<SongInfo>>()
    val newSongs: LiveData<List<SongInfo>> = _newSongs

    /** 加载状态 */
    @PublishedApi internal val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    /** 错误信息 */
    @PublishedApi internal val _errorMsg = MutableLiveData<String>()
    val errorMsg: LiveData<String> = _errorMsg

    /** 电台 - 推荐（电台站） */
    @PublishedApi internal val _radioRecommend = MutableLiveData<List<DjRadioItem>>()
    val radioRecommend: LiveData<List<DjRadioItem>> = _radioRecommend

    /** 电台 - 分类（24小时排行榜） */
    @PublishedApi internal val _radioPrograms = MutableLiveData<List<DjProgramItem>>()
    val radioPrograms: LiveData<List<DjProgramItem>> = _radioPrograms

    /** 电台 - 分类推荐（今日优选） */
    @PublishedApi internal val _radioCategoryRecommend = MutableLiveData<List<DjProgramItem>>()
    val radioCategoryRecommend: LiveData<List<DjProgramItem>> = _radioCategoryRecommend

    /** 电台加载状态 */
    @PublishedApi internal val _radioLoading = MutableLiveData(false)
    val radioLoading: LiveData<Boolean> = _radioLoading

    /** 电台组合数据（原子更新，避免闪烁） */
    @PublishedApi internal val _radioPageData = MutableLiveData<RadioPageData>()
    val radioPageData: LiveData<RadioPageData> = _radioPageData

    /**
     * 加载首页数据
     * 并行请求 Banner、推荐歌单、新歌
     * 新歌数据获取后通过 song/detail 补全歌手信息
     */
    fun loadHomeData() {
        _isLoading.value = true
        launchRequestRaw(
            block = {
                val bannerResult = repository.getBanners()
                val playlistResult = repository.getRecommendPlaylists()
                val newSongResult = repository.getNewSongs()

                // 在协程上下文中处理新歌结果（包含可能 suspend 的 getSongDetail 调用）
                val resolvedSongs: List<SongInfo>? = when (newSongResult) {
                    is AppResult.Success -> {
                        val rawSongs = newSongResult.data.result?.mapNotNull { item ->
                            val picUrl = item.picUrl
                                ?: item.song?.al?.picUrl

                            item.song?.let { song ->
                                if (song.al?.picUrl.isNullOrEmpty() && !picUrl.isNullOrEmpty()) {
                                    SongInfo(
                                        id = song.id,
                                        name = song.name,
                                        duration = song.duration,
                                        al = AlbumInfo(
                                            id = song.al?.id ?: 0,
                                            name = song.al?.name,
                                            picUrl = picUrl
                                        ),
                                        ar = song.ar,
                                        dt = song.dt
                                    )
                                } else {
                                    song
                                }
                            } ?: SongInfo(
                                id = item.id,
                                name = item.name,
                                al = AlbumInfo(picUrl = picUrl)
                            )
                        } ?: emptyList()

                        // 收集需要补全歌手信息的歌曲 ID（ar 为空或 null 的）
                        val idsNeedArtist = rawSongs
                            .filter { it.ar.isNullOrEmpty() }
                            .map { it.id }

                        if (idsNeedArtist.isNotEmpty()) {
                            val detailResult = repository.getSongDetail(
                                idsNeedArtist.joinToString(",")
                            )
                            val detailMap = when (detailResult) {
                                is AppResult.Success -> detailResult.data.songs
                                    ?.associateBy { it.id } ?: emptyMap()
                                is AppResult.Error -> emptyMap()
                            }
                            rawSongs.map { song ->
                                val detail = detailMap[song.id]
                                if (detail != null && !detail.ar.isNullOrEmpty()) {
                                    song.copy(ar = detail.ar)
                                } else {
                                    song
                                }
                            }
                        } else {
                            rawSongs
                        }
                    }
                    is AppResult.Error -> null
                }

                // 返回封装结果，onSuccess 仅负责更新 LiveData
                Triple(bannerResult, playlistResult, resolvedSongs)
            },
            onSuccess = { triple: Triple<AppResult<BannerResponse>, AppResult<RecommendPlaylistResponse>, List<SongInfo>?> ->
                _isLoading.value = false
                val bannerResult = triple.first
                val playlistResult = triple.second
                val songs = triple.third

                when (bannerResult) {
                    is AppResult.Success -> _banners.value = bannerResult.data.banners
                    is AppResult.Error -> _errorMsg.value = bannerResult.exception.message
                }
                when (playlistResult) {
                    is AppResult.Success -> _playlists.value = playlistResult.data.result
                    is AppResult.Error -> _errorMsg.value = playlistResult.exception.message
                }
                if (songs != null) {
                    _newSongs.value = songs
                } else {
                    _errorMsg.value = "新歌数据加载失败"
                }
            },
            onError = { e ->
                _isLoading.value = false
                _errorMsg.value = e.message
            }
        )
    }

    /**
     * 加载电台数据（并行请求三类）
     * 推荐电台站 | 24小时排行榜 | 今日优选
     * 所有结果收集完成后原子更新，避免多次 postValue 导致的 UI 闪烁
     */
    fun loadRadioData() {
        if (_radioPrograms.value?.isNotEmpty() == true) return
        _radioLoading.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 并行请求三类电台数据
                val recommendDeferred = async { repository.getDjRecommend() }
                val toplistDeferred = async { repository.getDjProgramToplist(30) }
                val todayDeferred = async { repository.getDjTodayPerfered(0) }

                val recommendResult = recommendDeferred.await()
                val toplistResult = toplistDeferred.await()
                val todayResult = todayDeferred.await()

                // 提取结果
                val recommend = when (recommendResult) {
                    is AppResult.Success -> recommendResult.data.djRadios ?: emptyList()
                    is AppResult.Error -> emptyList()
                }
                val programs = when (toplistResult) {
                    is AppResult.Success -> toplistResult.data.data?.list?.mapNotNull { it.program } ?: emptyList()
                    is AppResult.Error -> emptyList()
                }
                val categoryRecommend = when (todayResult) {
                    is AppResult.Success -> todayResult.data.data ?: emptyList()
                    is AppResult.Error -> emptyList()
                }

                // 原子更新：一次 postValue 包含所有数据，observer 只触发一次
                val combined = RadioPageData(recommend, programs, categoryRecommend)
                _radioPageData.postValue(combined)

                // 同步更新旧 LiveData（供 refresh 清空等兼容用途）
                _radioRecommend.postValue(recommend)
                _radioPrograms.postValue(programs)
                _radioCategoryRecommend.postValue(categoryRecommend)
            } catch (e: Exception) {
                val emptyData = RadioPageData(emptyList(), emptyList(), emptyList())
                _radioPageData.postValue(emptyData)
                _radioRecommend.postValue(emptyList())
                _radioPrograms.postValue(emptyList())
                _radioCategoryRecommend.postValue(emptyList())
                _errorMsg.postValue("电台数据加载失败")
            }
            _radioLoading.postValue(false)
        }
    }
}
