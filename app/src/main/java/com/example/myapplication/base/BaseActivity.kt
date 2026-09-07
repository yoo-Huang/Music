package com.example.myapplication.base

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.viewbinding.ViewBinding
import com.example.myapplication.R
import com.example.myapplication.manager.MiniPlayerManager

/**
 * Activity 基类
 * 封装通用逻辑：ViewBinding 绑定、页面切换动画、全局迷你播放栏
 */
abstract class BaseActivity<VB : ViewBinding> : AppCompatActivity() {

    /** 子类必须实现，返回 ViewBinding 实例 */
    protected lateinit var binding: VB

    /** 子类实现，初始化 ViewBinding */
    protected abstract fun initBinding(): VB

    /** 迷你播放栏管理器 */
    private var miniPlayerManager: MiniPlayerManager? = null

    /**
     * 是否启用迷你播放栏（默认启用）
     * 子类可重写为 false 来禁用（如 LoginActivity）
     */
    protected open val enableMiniPlayer: Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = initBinding()
        setContentView(binding.root)
        initView()
        initData()
        initObserver()

        // 全局迷你播放栏
        if (enableMiniPlayer) {
            miniPlayerManager = MiniPlayerManager(this).also { it.attach() }
        }
    }

    /** 初始化控件 */
    open fun initView() {}

    /** 初始化数据 */
    open fun initData() {}

    /** 观察 LiveData */
    open fun initObserver() {}

    /**
     * 启动 Activity 并添加页面切换动画
     * 子类可重写以自定义动画
     */
    override fun finish() {
        super.finish()
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }

    /** 显示迷你播放栏（供 Fragment 调用，如离开心动页时恢复） */
    fun showMiniPlayer() {
        miniPlayerManager?.show()
    }

    /** 隐藏迷你播放栏（供 Fragment 调用，如进入心动页时隐藏） */
    fun hideMiniPlayer() {
        miniPlayerManager?.hide()
    }

    /** 刷新迷你播放栏状态（供 Fragment 调用，如离开心动tab时恢复播放栏显示） */
    fun refreshMiniPlayer() {
        miniPlayerManager?.refreshPlayer()
    }

    override fun onDestroy() {
        miniPlayerManager?.detach()
        miniPlayerManager = null
        super.onDestroy()
    }
}
