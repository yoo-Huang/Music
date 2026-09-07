package com.example.myapplication.manager

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * 用户状态管理器
 *
 * 管理用户当前状态（在线/忙碌/听歌中等），数据存储在 SharedPreferences 中。
 * 同时维护一个 "相同状态的用户" 本地记录。
 */
object UserStatusManager {

    private const val PREFS_NAME = "user_status_prefs"
    private const val KEY_CURRENT_STATUS = "current_status"
    private const val KEY_CUSTOM_STATUS = "custom_status"
    private const val KEY_SAME_STATUS_USERS = "same_status_users"

    private var prefs: SharedPreferences? = null
    private val gson = Gson()

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun requirePrefs(): SharedPreferences {
        return prefs ?: throw IllegalStateException("UserStatusManager not initialized. Call init() first.")
    }

    /**
     * 获取当前用户状态
     */
    fun getCurrentStatus(): StatusItem {
        val json = requirePrefs().getString(KEY_CURRENT_STATUS, null) ?: return defaultStatus
        return try {
            gson.fromJson(json, StatusItem::class.java) ?: defaultStatus
        } catch (_: Exception) { defaultStatus }
    }

    /**
     * 获取用户自定义状态文本（编辑后的状态内容）
     */
    fun getCustomStatusText(): String? {
        return requirePrefs().getString(KEY_CUSTOM_STATUS, null)
    }

    /**
     * 设置用户状态
     */
    fun setStatus(status: StatusItem, customText: String? = null) {
        requirePrefs().edit()
            .putString(KEY_CURRENT_STATUS, gson.toJson(status))
            .apply()
        if (customText != null) {
            requirePrefs().edit()
                .putString(KEY_CUSTOM_STATUS, customText)
                .apply()
        }
    }

    /**
     * 获取 "相同状态" 的用户列表（本地模拟数据）
     */
    fun getSameStatusUsers(statusId: String): List<SameStatusUser> {
        val json = requirePrefs().getString(KEY_SAME_STATUS_USERS, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<Map<String, List<SameStatusUser>>>() {}.type
            val map: Map<String, List<SameStatusUser>> = gson.fromJson(json, type) ?: emptyMap()
            map[statusId] ?: emptyList()
        } catch (_: Exception) { emptyList() }
    }

    /**
     * 记录用户浏览了某个状态的"相同状态用户"（用于本地记录）
     */
    fun addSameStatusUser(statusId: String, user: SameStatusUser) {
        val json = requirePrefs().getString(KEY_SAME_STATUS_USERS, null)
        val map: MutableMap<String, MutableList<SameStatusUser>> = try {
            val type = object : TypeToken<Map<String, MutableList<SameStatusUser>>>() {}.type
            (gson.fromJson<Map<String, MutableList<SameStatusUser>>>(json, type)?.toMutableMap()
                ?: mutableMapOf()) as MutableMap<String, MutableList<SameStatusUser>>
        } catch (_: Exception) { mutableMapOf() }

        val list = map.getOrPut(statusId) { mutableListOf() }
        if (list.none { it.userId == user.userId }) {
            list.add(0, user) // 添加到最前面
        }
        if (list.size > 20) list.removeAt(list.size - 1) // 最多保留20条

        requirePrefs().edit()
            .putString(KEY_SAME_STATUS_USERS, gson.toJson(map))
            .apply()
    }

    /**
     * 清空数据
     */
    fun clear() {
        requirePrefs().edit().clear().apply()
    }

    // ==================== 预设状态列表 ====================

    /**
     * 可设置的状态列表
     */
    val availableStatuses: List<StatusItem> = listOf(
        StatusItem("online", "在线", "我在听歌", "🟢"),
        StatusItem("busy", "忙碌", "在忙别的事", "🔴"),
        StatusItem("listening", "听歌中", "正在享受音乐", "🎧"),
        StatusItem("daze", "发呆中", "什么都不想做", "😶"),
        StatusItem("quiet", "想静静", "请勿打扰", "🤫"),
        StatusItem("energetic", "元气满满", "今天充满活力", "⚡"),
        StatusItem("waiting", "在线等", "等一个懂我的人", "⏳"),
        StatusItem("slacking", "摸鱼中", "偷偷听歌中", "🐟"),
        StatusItem("custom", "自定义", "设置自己的状态", "✏️")
    )

    val defaultStatus = StatusItem("online", "在线", "我在听歌", "🟢")
}

/**
 * 用户状态数据类
 */
data class StatusItem(
    val id: String = "online",
    val name: String = "在线",
    val subtitle: String = "我在听歌",
    val emoji: String = "🟢"
)

/**
 * 相同状态用户数据类
 */
data class SameStatusUser(
    val userId: Long = 0,
    val nickname: String = "",
    val avatarUrl: String? = null,
    val statusText: String = ""
)
