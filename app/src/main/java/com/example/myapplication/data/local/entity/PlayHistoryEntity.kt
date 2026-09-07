package com.example.myapplication.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 播放历史实体类
 */
@Entity(tableName = "play_history")
data class PlayHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "song_id")
    val songId: Long,

    @ColumnInfo(name = "song_name")
    val songName: String,

    @ColumnInfo(name = "artist_name")
    val artistName: String? = null,

    @ColumnInfo(name = "album_pic_url")
    val albumPicUrl: String? = null,

    @ColumnInfo(name = "duration")
    val duration: Long = 0,

    /** 播放时间戳 */
    @ColumnInfo(name = "play_time")
    val playTime: Long = System.currentTimeMillis()
)
