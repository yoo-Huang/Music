package com.example.myapplication.data.local.dao

import androidx.room.*
import com.example.myapplication.data.local.entity.PlayHistoryEntity
import kotlinx.coroutines.flow.Flow

/**
 * 播放历史 DAO
 */
@Dao
interface PlayHistoryDao {

    /**
     * 获取播放历史（最近 100 条）
     */
    @Query("SELECT * FROM play_history ORDER BY play_time DESC LIMIT 100")
    fun getPlayHistory(): Flow<List<PlayHistoryEntity>>

    /**
     * 获取播放历史（suspend 版本，最近 100 条）
     */
    @Query("SELECT * FROM play_history ORDER BY play_time DESC LIMIT 100")
    suspend fun getPlayHistorySync(): List<PlayHistoryEntity>

    /**
     * 插入播放记录
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: PlayHistoryEntity)

    /**
     * 清空播放历史
     */
    @Query("DELETE FROM play_history")
    suspend fun clearHistory()
}
