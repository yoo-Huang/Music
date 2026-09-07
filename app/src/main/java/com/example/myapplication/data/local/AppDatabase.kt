package com.example.myapplication.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.myapplication.data.local.dao.FavoriteSongDao
import com.example.myapplication.data.local.dao.PlayHistoryDao
import com.example.myapplication.data.local.entity.FavoriteSongEntity
import com.example.myapplication.data.local.entity.PlayHistoryEntity

/**
 * Room 数据库类
 * 使用单例模式，确保全局只有一个数据库实例
 *
 * @Database 注解声明实体类和数据库版本
 * @param entities 所有实体类列表
 * @param version 数据库版本号
 */
@Database(
    entities = [
        FavoriteSongEntity::class,
        PlayHistoryEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun favoriteSongDao(): FavoriteSongDao
    abstract fun playHistoryDao(): PlayHistoryDao

    companion object {
        private const val DATABASE_NAME = "netease_cloud_music.db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * 获取数据库实例（线程安全双重检查锁）
         */
        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                DATABASE_NAME
            )
                .fallbackToDestructiveMigration() // 开发阶段：版本变化时重建数据库
                .build()
        }
    }
}
