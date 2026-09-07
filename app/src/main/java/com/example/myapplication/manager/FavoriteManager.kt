package com.example.myapplication.manager

import android.util.Log
import com.example.myapplication.base.Result as AppResult
import com.example.myapplication.data.remote.ApiService

/**
 * 全局红心歌曲 ID 集合管理器（单例）
 *
 * 在内存中维护一份所有已喜欢歌曲的 Long ID 集合：
 * - 登录成功后调用 [refreshFromServer] 从 /likelist 接口拉取全量 ID
 * - App 启动时若已登录也会拉取
 * - [isLiked] / [setLiked] 提供 O(1) 的读写
 * - [Repository.toggleFavorite] 操作时同步更新此集合
 */
object FavoriteManager {

    private const val TAG = "FavoriteManager"

    @Volatile
    private var _likedSongIds: MutableSet<Long> = mutableSetOf()

    /** 是否已从服务端完成过一次拉取 */
    @Volatile
    var isLoaded: Boolean = false
        private set

    // ==================== 读取 ====================

    /** O(1) 判断歌曲是否已喜欢 */
    fun isLiked(songId: Long): Boolean {
        if (!isLoaded) return false
        return _likedSongIds.contains(songId)
    }

    // ==================== 写入 ====================

    /**
     * 添加或移除红心 ID
     * @param songId 歌曲 ID
     * @param liked  true = 喜欢, false = 取消喜欢
     */
    fun setLiked(songId: Long, liked: Boolean) {
        synchronized(_likedSongIds) {
            if (liked) _likedSongIds.add(songId) else _likedSongIds.remove(songId)
        }
    }

    /**
     * 从服务端拉取用户所有红心歌曲 ID 并替换内存集合
     * 调用时机：登录成功后 / App 启动时已登录
     */
    suspend fun refreshFromServer(uid: Long) {
        try {
            val result = ApiService.getLikedSongs(uid)
            if (result is AppResult.Success) {
                val ids = result.data.ids
                if (!ids.isNullOrEmpty()) {
                    synchronized(_likedSongIds) {
                        _likedSongIds = ids.toMutableSet()
                    }
                    isLoaded = true
                    Log.d(TAG, "refreshFromServer success: ${ids.size} liked songs")
                } else {
                    Log.w(TAG, "refreshFromServer: ids is null or empty, code=${result.data.code}")
                }
            } else {
                Log.e(TAG, "refreshFromServer failed: ${(result as AppResult.Error).exception.message}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "refreshFromServer exception: ${e.message}", e)
        }
    }

    /**
     * 清除所有数据（退出登录时调用）
     */
    fun clear() {
        synchronized(_likedSongIds) {
            _likedSongIds.clear()
        }
        isLoaded = false
        Log.d(TAG, "FavoriteManager cleared")
    }
}
