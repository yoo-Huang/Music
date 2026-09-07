package com.example.myapplication.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 收藏歌曲实体类
 * 使用 Kotlin 数据类，Room 会自动生成 getter/setter
 */
@Entity(tableName = "favorite_songs")
data class FavoriteSongEntity(
    @PrimaryKey
    @ColumnInfo(name = "song_id")
    val songId: Long,

    @ColumnInfo(name = "song_name")
    val songName: String,

    @ColumnInfo(name = "artist_name")
    val artistName: String? = null,

    @ColumnInfo(name = "album_name")
    val albumName: String? = null,

    @ColumnInfo(name = "album_pic_url")
    val albumPicUrl: String? = null,

    @ColumnInfo(name = "duration")
    val duration: Long = 0,

    /** 收藏时间戳 */
    @ColumnInfo(name = "favorite_time")
    val favoriteTime: Long = System.currentTimeMillis()
)
