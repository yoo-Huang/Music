package com.example.myapplication.manager

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.myapplication.data.remote.LoginUserProfile
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 账户管理单例
 *
 * 统一管理登录状态、Cookie、用户信息，替代之前分散在各处的 SharedPreferences 直接操作。
 *
 * 存储结构（SharedPreferences "account_prefs"）：
 * - is_logged_in  : Boolean  是否已登录
 * - cookie        : String   登录后服务器返回的 Cookie
 * - user_id       : Long     用户 ID
 * - nickname      : String   用户昵称
 * - avatar_url    : String   用户头像 URL
 * - account_json  : String   用户完整信息 JSON（LoginUserProfile 序列化）
 */
object AccountManager {

    private const val PREFS_NAME = "account_prefs"
    private const val KEY_IS_LOGGED_IN = "is_logged_in"
    private const val KEY_COOKIE = "cookie"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_NICKNAME = "nickname"
    private const val KEY_AVATAR_URL = "avatar_url"
    private const val KEY_ACCOUNT_JSON = "account_json"

    private var prefs: SharedPreferences? = null
    private val gson = Gson()

    /** 登录状态变化事件 */
    private val _loginStateEvent = MutableLiveData<LoginEvent>()
    val loginStateEvent: LiveData<LoginEvent> = _loginStateEvent

    /** 初始化（应在 Application.onCreate 中调用） */
    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun requirePrefs(): SharedPreferences {
        return prefs ?: throw IllegalStateException("AccountManager not initialized. Call init() first.")
    }

    // ==================== 登录状态 ====================

    /** 是否已登录 */
    val isLoggedIn: Boolean
        get() = requirePrefs().getBoolean(KEY_IS_LOGGED_IN, false)

    /** 当前 Cookie */
    val cookie: String?
        get() = requirePrefs().getString(KEY_COOKIE, null)

    /** 从 Cookie 字符串中提取 __csrf token，用于需要 CSRF 校验的接口 */
    val csrfToken: String?
        get() {
            val c = cookie ?: return null
            // Cookie 格式: "MUSIC_U=xxx; __csrf=yyy; ..."
            return c.split(";")
                .map { it.trim() }
                .firstOrNull { it.startsWith("__csrf=") }
                ?.substringAfter("__csrf=")
                ?.takeIf { it.isNotBlank() }
        }

    /** 用户 ID */
    val userId: Long
        get() = requirePrefs().getLong(KEY_USER_ID, 0L)

    /** 用户昵称 */
    val nickname: String
        get() = requirePrefs().getString(KEY_NICKNAME, "") ?: ""

    /** 头像 URL */
    val avatarUrl: String?
        get() = requirePrefs().getString(KEY_AVATAR_URL, null)

    /** 完整用户信息 */
    val profile: LoginUserProfile?
        get() {
            val json = requirePrefs().getString(KEY_ACCOUNT_JSON, null) ?: return null
            return try {
                gson.fromJson(json, LoginUserProfile::class.java)
            } catch (_: Exception) {
                null
            }
        }

    // ==================== 写入方法 ====================

    /**
     * 保存登录信息
     * @param cookie  服务器返回的 Cookie
     * @param profile 用户资料
     */
    fun saveLoginInfo(cookie: String?, profile: LoginUserProfile?) {
        val editor = requirePrefs().edit()
        editor.putBoolean(KEY_IS_LOGGED_IN, true)
        if (!cookie.isNullOrBlank()) {
            editor.putString(KEY_COOKIE, cookie)
        }
        profile?.let { p ->
            editor.putLong(KEY_USER_ID, p.userId)
            editor.putString(KEY_NICKNAME, p.nickname ?: "")
            editor.putString(KEY_AVATAR_URL, p.avatarUrl ?: "")
            editor.putString(KEY_ACCOUNT_JSON, gson.toJson(p))
        }
        editor.apply()

        _loginStateEvent.postValue(LoginEvent.LoggedIn)

        // 登录成功后异步拉取全局红心 ID 集合
        val uid = profile?.userId ?: userId
        if (uid > 0) {
            CoroutineScope(Dispatchers.IO).launch {
                FavoriteManager.refreshFromServer(uid)
            }
        }
    }

    /**
     * 清除登录状态（退出登录/登录过期）
     */
    fun clearLoginState() {
        requirePrefs().edit().clear().apply()
        FavoriteManager.clear()
        BadgeManager.clear()
        UserStatusManager.clear()
        _loginStateEvent.postValue(LoginEvent.LoggedOut)
    }

    /**
     * 更新 Cookie（用于刷新登录后）
     */
    fun updateCookie(cookie: String?) {
        if (!cookie.isNullOrBlank()) {
            requirePrefs().edit().putString(KEY_COOKIE, cookie).apply()
        }
    }
}

/** 登录状态变化事件 */
sealed class LoginEvent {
    /** 已登录 */
    data object LoggedIn : LoginEvent()
    /** 已退出（含登录过期） */
    data object LoggedOut : LoginEvent()
}
