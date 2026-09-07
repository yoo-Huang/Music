package com.example.myapplication.manager

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * 用户徽章管理器
 *
 * 本地计算徽章，根据用户等级、听歌数、关注/粉丝数等自动授予。
 * 数据存储在 SharedPreferences 中。
 */
object BadgeManager {

    private const val PREFS_NAME = "badge_prefs"
    private const val KEY_UNLOCKED_BADGES = "unlocked_badges"

    private var prefs: SharedPreferences? = null
    private val gson = Gson()

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun requirePrefs(): SharedPreferences {
        return prefs ?: throw IllegalStateException("BadgeManager not initialized. Call init() first.")
    }

    /**
     * 已解锁的徽章 ID 集合
     */
    fun getUnlockedBadges(): Set<String> {
        val json = requirePrefs().getString(KEY_UNLOCKED_BADGES, null) ?: return emptySet()
        return try {
            val type = object : TypeToken<Set<String>>() {}.type
            gson.fromJson(json, type) ?: emptySet()
        } catch (_: Exception) { emptySet() }
    }

    /**
     * 根据用户数据计算并解锁新徽章，返回本次新解锁的徽章列表
     */
    fun computeAndUnlock(
        level: Int,
        listenSongs: Long,
        followeds: Long,
        follows: Long,
        playlistCount: Int
    ): List<UserBadge> {
        val unlocked = getUnlockedBadges().toMutableSet()
        val newlyUnlocked = mutableListOf<UserBadge>()

        // 遍历所有徽章，检查条件
        allBadges.forEach { badge ->
            if (badge.id in unlocked) return@forEach // 已解锁

            val conditionMet = when (badge.id) {
                "badge_newcomer" -> level >= 1
                "badge_level5" -> level >= 5
                "badge_level8" -> level >= 8
                "badge_level10" -> level >= 10
                "badge_songs100" -> listenSongs >= 100
                "badge_songs1000" -> listenSongs >= 1000
                "badge_songs10000" -> listenSongs >= 10000
                "badge_followers10" -> followeds >= 10
                "badge_followers50" -> followeds >= 50
                "badge_followers100" -> followeds >= 100
                "badge_follows20" -> follows >= 20
                "badge_follows50" -> follows >= 50
                "badge_playlist3" -> playlistCount >= 3
                "badge_playlist10" -> playlistCount >= 10
                "badge_social" -> followeds >= 20 && follows >= 20
                "badge_allrounder" -> level >= 8 && listenSongs >= 1000 && followeds >= 50
                else -> false
            }

            if (conditionMet) {
                unlocked.add(badge.id)
                newlyUnlocked.add(badge)
            }
        }

        // 保存
        if (newlyUnlocked.isNotEmpty()) {
            requirePrefs().edit()
                .putString(KEY_UNLOCKED_BADGES, gson.toJson(unlocked))
                .apply()
        }

        return newlyUnlocked
    }

    /**
     * 获取用户已解锁的徽章详情列表
     */
    fun getUnlockedBadgeDetails(): List<UserBadge> {
        val ids = getUnlockedBadges()
        return allBadges.filter { it.id in ids }
    }

    /**
     * 获取所有徽章（包含是否解锁的状态）
     */
    fun getAllBadgesWithStatus(): List<Pair<UserBadge, Boolean>> {
        val ids = getUnlockedBadges()
        return allBadges.map { it to (it.id in ids) }
    }

    /**
     * 清空数据（退出登录时调用）
     */
    fun clear() {
        requirePrefs().edit().clear().apply()
    }

    // ==================== 徽章定义 ====================

    /** 所有可用徽章 */
    val allBadges: List<UserBadge> = listOf(
        UserBadge("badge_newcomer", "初来乍到", "等级达到 Lv.1", "🎵", category = "等级"),
        UserBadge("badge_level5", "音乐学徒", "等级达到 Lv.5", "🎶", category = "等级"),
        UserBadge("badge_level8", "音乐达人", "等级达到 Lv.8", "🎸", category = "等级"),
        UserBadge("badge_level10", "骨灰级乐迷", "等级达到 Lv.10", "🎹", category = "等级"),
        UserBadge("badge_songs100", "百首聆听", "累计听歌 100 首", "🎧", category = "听歌"),
        UserBadge("badge_songs1000", "千首聆听", "累计听歌 1000 首", "🔊", category = "听歌"),
        UserBadge("badge_songs10000", "万首聆听", "累计听歌 10000 首", "📀", category = "听歌"),
        UserBadge("badge_followers10", "人气新人", "粉丝达到 10 人", "⭐", category = "粉丝"),
        UserBadge("badge_followers50", "小有名气", "粉丝达到 50 人", "🌟", category = "粉丝"),
        UserBadge("badge_followers100", "万人迷", "粉丝达到 100 人", "💫", category = "粉丝"),
        UserBadge("badge_follows20", "社交新人", "关注 20 人", "👋", category = "关注"),
        UserBadge("badge_follows50", "社交达人", "关注 50 人", "🤝", category = "关注"),
        UserBadge("badge_playlist3", "歌单收藏家", "创建 3 个歌单", "📋", category = "歌单"),
        UserBadge("badge_playlist10", "歌单大师", "创建 10 个歌单", "📚", category = "歌单"),
        UserBadge("badge_social", "社交之星", "粉丝≥20 且 关注≥20", "👑", category = "综合"),
        UserBadge("badge_allrounder", "全能音乐人", "Lv.8+ 且 听歌≥1000 且 粉丝≥50", "🏆", category = "综合")
    )
}

/**
 * 用户徽章数据类
 */
data class UserBadge(
    val id: String,
    val name: String,
    val desc: String,
    val icon: String,
    val category: String = ""
)
