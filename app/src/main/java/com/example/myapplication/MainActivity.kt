package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.example.myapplication.base.BaseActivity
import com.example.myapplication.databinding.ActivityMainBinding
import com.example.myapplication.ui.home.HomeFragment
import com.example.myapplication.ui.mine.MineFragment
import com.example.myapplication.ui.player.PlayerActivity
import com.example.myapplication.ui.ranking.RankingFragment
/**
 * 主页面 Activity
 * 双层底部布局：悬浮播放栏 + BottomNavigationView
 * 迷你播放栏由 BaseActivity 全局管理
 */
class MainActivity : BaseActivity<ActivityMainBinding>() {

    private val homeFragment = HomeFragment()
    private val rankingFragment = RankingFragment()
    private val mineFragment = MineFragment()
    private var isFirstCreate = true

    override fun initBinding() = ActivityMainBinding.inflate(layoutInflater)

    override fun initView() {
        // 处理系统窗口 insets（状态栏 + 导航栏区域）
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            // 底部导航栏留出系统手势指示条空间
            val navParams = binding.bottomNavigation.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
            navParams.bottomMargin = systemBars.bottom
            binding.bottomNavigation.layoutParams = navParams
            insets
        }

        if (isFirstCreate) {
            isFirstCreate = false
            supportFragmentManager.beginTransaction()
                .add(R.id.fragment_container, homeFragment, "home")
                .add(R.id.fragment_container, rankingFragment, "ranking")
                .add(R.id.fragment_container, mineFragment, "mine")
                .hide(rankingFragment)
                .hide(mineFragment)
                .commit()
        }

        // 底部导航切换：首页 / 排行榜 / 我的
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_home -> { showFragment(homeFragment); true }
                R.id.navigation_ranking -> { showFragment(rankingFragment); true }
                R.id.navigation_mine -> { showFragment(mineFragment); true }
                else -> false
            }
        }

        // 通知点击直接打开播放页
        if (intent.getBooleanExtra("open_player", false)) {
            startActivity(Intent(this, PlayerActivity::class.java))
        }

        // 监听登录状态变化（登录过期/退出登录时不销毁 MainActivity）
    }

    private fun showFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .hide(homeFragment)
            .hide(rankingFragment)
            .hide(mineFragment)
            .show(fragment)
            .commit()
    }
}
