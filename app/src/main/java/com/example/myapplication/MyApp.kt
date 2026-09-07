package com.example.myapplication

import android.app.Application
import android.util.Log
import com.bumptech.glide.Glide
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.load.engine.executor.GlideExecutor
import com.example.myapplication.manager.AccountManager
import com.example.myapplication.manager.BadgeManager
import com.example.myapplication.manager.FavoriteManager
import com.example.myapplication.manager.UserStatusManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 全局 Application 类
 * 用于初始化全局配置，如网络客户端、数据库等
 */
class MyApp : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this

        // 初始化账户管理器
        AccountManager.init(this)

        // 初始化徽章和状态管理器
        BadgeManager.init(this)
        UserStatusManager.init(this)

        // App 启动时若已登录，拉取全局红心 ID 集合
        if (AccountManager.isLoggedIn) {
            CoroutineScope(Dispatchers.IO).launch {
                FavoriteManager.refreshFromServer(AccountManager.userId)
            }
        }

        // 配置 Glide 日志级别，减少非必要日志输出
        Glide.init(this, GlideBuilder()
            .setLogLevel(Log.ERROR)
            .setSourceExecutor(GlideExecutor.newSourceBuilder().setThreadTimeoutMillis(5000).build())
            .setDiskCacheExecutor(GlideExecutor.newDiskCacheBuilder().build())
        )
    }

    companion object {
        lateinit var instance: MyApp
            private set
    }
}
