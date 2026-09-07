package com.example.myapplication.ui.mine

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.base.Result
import com.example.myapplication.data.Repository
import com.example.myapplication.data.remote.ApiService
import com.example.myapplication.data.remote.LoginUserProfile
import com.example.myapplication.data.remote.RecommendPlaylist
import com.example.myapplication.data.remote.UserPlaylistItem
import com.example.myapplication.manager.AccountManager
import com.example.myapplication.manager.BadgeManager
import com.example.myapplication.manager.StatusItem
import com.example.myapplication.manager.UserBadge
import com.example.myapplication.manager.UserStatusManager
import kotlinx.coroutines.launch

/**
 * "我的"页面 ViewModel
 *
 * 数据源：
 * - 用户信息 → AccountManager（本地缓存）+ /user/detail（网络）
 * - 用户歌单 → /user/playlist（网络）
 *
 * 页面结构：
 * 1. 用户头部信息（头像、昵称、等级、关注/粉丝）
 * 2. 快捷入口（听歌打卡、云贝中心、我的订单 等）
 * 3. 我创建的歌单（横向滑动）
 * 4. 我收藏的歌单（横向滑动）
 * 5. 功能列表入口（播放记录、最近常听、云盘、下载管理 等）
 */
class MineViewModel : ViewModel() {

    private val repository = Repository.getInstance()

    /** 是否已登录 */
    val isLoggedIn: Boolean get() = AccountManager.isLoggedIn

    /** 用户信息 LiveData */
    private val _userProfile = MutableLiveData<LoginUserProfile?>()
    val userProfile: LiveData<LoginUserProfile?> = _userProfile

    /** 用户等级 */
    private val _userLevel = MutableLiveData(0)
    val userLevel: LiveData<Int> = _userLevel

    /** 听歌总数 */
    private val _listenSongs = MutableLiveData(0L)
    val listenSongs: LiveData<Long> = _listenSongs

    /** 用户创建的歌单 */
    private val _createdPlaylists = MutableLiveData<List<UserPlaylistItem>>()
    val createdPlaylists: LiveData<List<UserPlaylistItem>> = _createdPlaylists

    /** 用户收藏的歌单 */
    private val _subscribedPlaylists = MutableLiveData<List<UserPlaylistItem>>()
    val subscribedPlaylists: LiveData<List<UserPlaylistItem>> = _subscribedPlaylists

    /** 加载状态 */
    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    /** 错误消息 */
    private val _errorMsg = MutableLiveData<String?>()
    val errorMsg: LiveData<String?> = _errorMsg

    /** 用户状态 */
    private val _userStatus = MutableLiveData<StatusItem>()
    val userStatus: LiveData<StatusItem> = _userStatus

    /** 用户徽章 */
    private val _userBadges = MutableLiveData<List<UserBadge>>()
    val userBadges: LiveData<List<UserBadge>> = _userBadges

    /** 未登录时推荐的歌单 */
    private val _guestPlaylists = MutableLiveData<List<RecommendPlaylist>>()
    val guestPlaylists: LiveData<List<RecommendPlaylist>> = _guestPlaylists

    /** 未登录时是否正在加载 */
    private val _isGuestLoading = MutableLiveData(false)
    val isGuestLoading: LiveData<Boolean> = _isGuestLoading

    /**
     * 加载用户数据（用户信息 + 歌单）
     */
    fun loadUserData() {
        if (!AccountManager.isLoggedIn) {
            // 未登录时从本地缓存加载基本信息
            _userProfile.value = AccountManager.profile
            _createdPlaylists.value = emptyList()
            _subscribedPlaylists.value = emptyList()
            return
        }

        _isLoading.value = true
        var uid = AccountManager.userId

        // 先从本地加载缓存数据，快速展示
        _userProfile.value = AccountManager.profile
        // 加载本地状态和徽章
        _userStatus.value = UserStatusManager.getCurrentStatus()
        _userBadges.value = BadgeManager.getUnlockedBadgeDetails()

        viewModelScope.launch {
            try {
                // 如果 userId 为 0（如二维码登录后 profile 未获取到），先通过 /login/status 获取
                if (uid == 0L) {
                    uid = fixMissingUserId()
                }

                // 并行请求用户详情和歌单
                // uid=0 时跳过 /user/detail（接口会返回 404），只加载歌单
                if (uid > 0) {
                    val detailDeferred = launch { loadUserDetail(uid) }
                    val playlistDeferred = launch { loadUserPlaylists(uid) }
                    detailDeferred.join()
                    playlistDeferred.join()
                } else {
                    android.util.Log.w("MineVM", "loadUserData: uid=0, skip /user/detail")
                    _userProfile.postValue(AccountManager.profile)
                    val playlistDeferred = launch { loadUserPlaylists(uid) }
                    playlistDeferred.join()
                }
            } catch (_: Exception) {
                _errorMsg.value = "加载失败，请下拉刷新重试"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * 补救缺失的 userId（二维码登录后可能 profile 未成功获取）
     * 策略：
     * 1. /login/status Gson 类型化解析
     * 2. /login/status 原始 JSON 手动提取
     * @return 修复后的 userId，失败返回 0
     */
    private suspend fun fixMissingUserId(): Long {
        // ---- 第一步：类型化解析 ----
        val result = ApiService.getLoginStatus()
        if (result is Result.Success) {
            val data = result.data
            val profile: LoginUserProfile? = data.data?.profile ?: data.profile
            val account = data.data?.account ?: data.account

            if (profile != null && profile.userId > 0) {
                android.util.Log.d("MineVM", "fixMissingUserId typed: userId=${profile.userId}")
                AccountManager.saveLoginInfo(AccountManager.cookie, profile)
                return profile.userId
            }
            if (account != null && account.id > 0) {
                android.util.Log.d("MineVM", "fixMissingUserId typed: accountId=${account.id}")
                AccountManager.saveLoginInfo(
                    AccountManager.cookie,
                    LoginUserProfile(userId = account.id)
                )
                return account.id
            }
        }

        // ---- 第二步：原始 JSON 手动提取 ----
        val rawJson = com.example.myapplication.data.remote.ApiClient.getRaw("/login/status")
        if (!rawJson.isNullOrBlank()) {
            try {
                val root = org.json.JSONObject(rawJson)
                val inner = root.optJSONObject("data")
                val source = inner ?: root

                val profileObj = source.optJSONObject("profile")
                val accountObj = source.optJSONObject("account")

                val uid = profileObj?.optLong("userId", 0L) ?: 0L
                val aid = accountObj?.optLong("id", 0L) ?: 0L

                if (uid > 0) {
                    val nick = profileObj?.optString("nickname", null)
                    val avatar = profileObj?.optString("avatarUrl", null)
                    android.util.Log.d("MineVM", "fixMissingUserId raw: userId=$uid")
                    AccountManager.saveLoginInfo(
                        AccountManager.cookie,
                        LoginUserProfile(userId = uid, nickname = nick, avatarUrl = avatar)
                    )
                    return uid
                }
                if (aid > 0) {
                    android.util.Log.d("MineVM", "fixMissingUserId raw: accountId=$aid")
                    AccountManager.saveLoginInfo(
                        AccountManager.cookie,
                        LoginUserProfile(userId = aid)
                    )
                    return aid
                }
            } catch (e: Exception) {
                android.util.Log.e("MineVM", "fixMissingUserId raw parse failed: ${e.message}")
            }
        }

        android.util.Log.w("MineVM", "fixMissingUserId: unable to resolve userId")
        return 0L
    }

    /**
     * 加载用户详情
     * 合并策略：本地缓存优先（用户可能在资料编辑页刚保存过），
     * 服务端仅补充统计数据（followeds, follows, level, listenSongs 等）。
     */
    private suspend fun loadUserDetail(uid: Long) {
        when (val result = ApiService.getUserDetail(uid)) {
            is Result.Success -> {
                val serverProfile = result.data.profile
                val cachedProfile = AccountManager.profile

                if (cachedProfile != null && serverProfile != null) {
                    // 可编辑字段：本地缓存优先；统计字段：服务端优先
                    val merged = LoginUserProfile(
                        userId = cachedProfile.userId.takeIf { it > 0 } ?: serverProfile.userId,
                        nickname = cachedProfile.nickname ?: serverProfile.nickname,
                        avatarUrl = cachedProfile.avatarUrl ?: serverProfile.avatarUrl,
                        birthday = cachedProfile.birthday.takeIf { it > 0 } ?: serverProfile.birthday,
                        gender = cachedProfile.gender.takeIf { it != 0 } ?: serverProfile.gender,
                        signature = cachedProfile.signature ?: serverProfile.signature,
                        followeds = serverProfile.followeds,
                        follows = serverProfile.follows,
                        playlistCount = serverProfile.playlistCount,
                        eventCount = serverProfile.eventCount
                    )
                    _userProfile.postValue(merged)
                    AccountManager.saveLoginInfo(
                        cookie = AccountManager.cookie,
                        profile = merged
                    )
                } else if (serverProfile != null) {
                    _userProfile.postValue(serverProfile)
                    AccountManager.saveLoginInfo(
                        cookie = AccountManager.cookie,
                        profile = serverProfile
                    )
                } else {
                    _userProfile.postValue(cachedProfile)
                }

                _userLevel.postValue(result.data.level)
                _listenSongs.postValue(result.data.listenSongs.toLong())

                // 计算并解锁徽章
                val profile = _userProfile.value
                if (profile != null) {
                    BadgeManager.computeAndUnlock(
                        level = result.data.level,
                        listenSongs = result.data.listenSongs.toLong(),
                        followeds = profile.followeds,
                        follows = profile.follows,
                        playlistCount = profile.playlistCount
                    )
                    _userBadges.postValue(BadgeManager.getUnlockedBadgeDetails())
                }
            }
            is Result.Error -> {
                _userProfile.postValue(AccountManager.profile)
            }
        }
    }

    /**
     * 加载用户歌单列表
     * 通过 subscribed 字段区分创建/收藏（该字段由网易云 API 直接提供，比昵称比较更可靠）
     */
    private suspend fun loadUserPlaylists(uid: Long) {
        when (val result = ApiService.getUserPlaylist(uid)) {
            is Result.Success -> {
                val allPlaylists = result.data.playlist ?: emptyList()

                // subscribed=false → 用户创建的歌单
                // subscribed=true  → 用户收藏的歌单
                val created = allPlaylists.filter { !it.subscribed }
                val subscribed = allPlaylists.filter { it.subscribed }

                _createdPlaylists.postValue(created)
                _subscribedPlaylists.postValue(subscribed)
            }
            is Result.Error -> {
                // 失败时保持空列表
            }
        }
    }

    /**
     * 加载未登录时的展示数据（推荐歌单）
     */
    fun loadGuestData() {
        _isGuestLoading.value = true
        viewModelScope.launch {
            try {
                when (val result = ApiService.getRecommendPlaylists(limit = 12)) {
                    is Result.Success -> {
                        _guestPlaylists.postValue(result.data.result ?: emptyList())
                    }
                    is Result.Error -> {
                        _guestPlaylists.postValue(emptyList())
                    }
                }
            } catch (_: Exception) {
                _guestPlaylists.postValue(emptyList())
            } finally {
                _isGuestLoading.postValue(false)
            }
        }
    }

    /**
     * 清除错误消息
     */
    fun clearError() {
        _errorMsg.value = null
    }
}
