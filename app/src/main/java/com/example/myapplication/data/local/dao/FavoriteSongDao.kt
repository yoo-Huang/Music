package com.example.myapplication.data.local.dao

import androidx.room.*
import com.example.myapplication.data.local.entity.FavoriteSongEntity
import kotlinx.coroutines.flow.Flow

/**
 * 收藏歌曲 DAO
 * 使用 Kotlin Flow 实现响应式数据观察
 */
@Dao
interface FavoriteSongDao {

    /**
     * 获取所有收藏歌曲（按时间倒序）
     * Flow 方式：数据变化时自动通知 UI 更新
     */
    @Query("SELECT * FROM favorite_songs ORDER BY favorite_time DESC")
    fun getAllFavorites(): Flow<List<FavoriteSongEntity>>

    /**
     * 检查歌曲是否已收藏
     */
    @Query("SELECT EXISTS(SELECT 1 FROM favorite_songs WHERE song_id = :songId)")
    suspend fun isFavorite(songId: Long): Boolean

    /**
     * 添加收藏
     * OnConflictStrategy.REPLACE：已存在则更新
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavorite(song: FavoriteSongEntity)

    /**
     * 取消收藏
     */
    @Delete
    suspend fun removeFavorite(song: FavoriteSongEntity)

    /**
     * 根据歌曲 ID 删除收藏
     */
    @Query("DELETE FROM favorite_songs WHERE song_id = :songId")
    suspend fun removeFavoriteById(songId: Long)

    /**
     * 清空收藏
     */
    @Query("DELETE FROM favorite_songs")
    suspend fun clearAll()
}
