package com.example.myapplication.ui.mine

import android.os.Environment
import com.example.myapplication.base.BaseActivity
import com.example.myapplication.databinding.ActivitySettingsBinding
import com.example.myapplication.manager.AccountManager
import com.example.myapplication.util.showToast
import java.io.File

/**
 * 设置页面
 * 包含：播放音质、清除缓存、检查更新、关于、退出登录
 */
class SettingsActivity : BaseActivity<ActivitySettingsBinding>() {

    override val enableMiniPlayer: Boolean = true

    override fun initBinding() = ActivitySettingsBinding.inflate(layoutInflater)

    override fun initView() {
        // 返回按钮
        binding.ivBack.setOnClickListener { finish() }

        // 计算缓存大小
        binding.tvCacheSize.text = getCacheSize()

        // 未登录时隐藏退出登录按钮
        if (!AccountManager.isLoggedIn) {
            binding.btnLogout.visibility = android.view.View.GONE
        }

        // 音质选择
        binding.btnQuality.setOnClickListener {
            // 简单循环切换：标准 → 较高 → 极高
            val qualities = listOf("标准", "较高", "极高")
            val current = binding.tvQuality.text.toString()
            val nextIndex = (qualities.indexOf(current) + 1) % qualities.size
            binding.tvQuality.text = qualities[nextIndex]
            showToast("已切换为${qualities[nextIndex]}音质")
        }

        // 清除缓存
        binding.btnClearCache.setOnClickListener {
            clearAppCache()
            binding.tvCacheSize.text = "0 MB"
            showToast("缓存已清除")
        }

        // 检查更新
        binding.btnCheckUpdate.setOnClickListener {
            showToast("已是最新版本")
        }

        // 关于
        binding.btnAbout.setOnClickListener {
            showToast("仿网易云音乐 v1.0.0\n基于 NetEaseCloudMusicApi")
        }

        // 退出登录
        binding.btnLogout.setOnClickListener {
            AccountManager.clearLoginState()
            showToast("已退出登录")
            finish()
        }
    }

    override fun initObserver() {}

    override fun initData() {}

    /**
     * 获取应用缓存大小
     */
    private fun getCacheSize(): String {
        return try {
            val cacheDir = cacheDir
            val externalCacheDir = externalCacheDir
            var size = getFolderSize(cacheDir)
            if (externalCacheDir != null) {
                size += getFolderSize(externalCacheDir)
            }
            // 也计算 Glide 图片缓存
            val glideCache = File(cacheDir.parentFile, "image_manager_disk_cache")
            if (glideCache.exists()) {
                size += getFolderSize(glideCache)
            }
            formatSize(size)
        } catch (_: Exception) {
            "0 MB"
        }
    }

    /**
     * 递归计算文件夹大小
     */
    private fun getFolderSize(dir: File): Long {
        var size = 0L
        try {
            dir.listFiles()?.forEach { file ->
                size += if (file.isDirectory) {
                    getFolderSize(file)
                } else {
                    file.length()
                }
            }
        } catch (_: Exception) { }
        return size
    }

    /**
     * 清除应用缓存
     */
    private fun clearAppCache() {
        try {
            deleteDir(cacheDir)
            deleteDir(externalCacheDir)
            // 清除 Glide 缓存
            val glideCache = File(cacheDir.parentFile, "image_manager_disk_cache")
            if (glideCache.exists()) deleteDir(glideCache)
        } catch (_: Exception) { }
    }

    private fun deleteDir(dir: File?) {
        if (dir == null || !dir.exists()) return
        try {
            dir.listFiles()?.forEach { file ->
                if (file.isDirectory) deleteDir(file)
                file.delete()
            }
        } catch (_: Exception) { }
    }

    /**
     * 格式化文件大小
     */
    private fun formatSize(size: Long): String {
        return when {
            size < 1024 -> "${size}B"
            size < 1024 * 1024 -> String.format("%.1f KB", size / 1024.0)
            size < 1024 * 1024 * 1024 -> String.format("%.1f MB", size / (1024.0 * 1024))
            else -> String.format("%.1f GB", size / (1024.0 * 1024 * 1024))
        }
    }
}
