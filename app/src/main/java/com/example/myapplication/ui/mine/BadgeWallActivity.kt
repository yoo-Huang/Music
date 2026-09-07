package com.example.myapplication.ui.mine

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.databinding.ActivityBadgeWallBinding
import com.example.myapplication.manager.BadgeManager

/**
 * 徽章墙页面
 * 展示用户所有已解锁和未解锁的徽章
 */
class BadgeWallActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBadgeWallBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBadgeWallBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.ivBack.setOnClickListener { finish() }

        val badges = BadgeManager.getAllBadgesWithStatus()
        val unlockedCount = badges.count { it.second }
        binding.tvBadgeCount.text = "$unlockedCount/${badges.size}"

        binding.rvBadges.apply {
            layoutManager = GridLayoutManager(this@BadgeWallActivity, 3)
            adapter = BadgeAdapter(badges)
        }
    }

    // ==================== Adapter ====================

    private class BadgeAdapter(
        private val badges: List<Pair<com.example.myapplication.manager.UserBadge, Boolean>>
    ) : RecyclerView.Adapter<BadgeAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvIcon: TextView = view.findViewById(com.example.myapplication.R.id.tv_badge_icon)
            val tvName: TextView = view.findViewById(com.example.myapplication.R.id.tv_badge_name)
            val tvDesc: TextView = view.findViewById(com.example.myapplication.R.id.tv_badge_desc)
            val ivLocked: View = view.findViewById(com.example.myapplication.R.id.iv_locked)
            val tvUnlocked: View = view.findViewById(com.example.myapplication.R.id.tv_unlocked)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(com.example.myapplication.R.layout.item_badge, parent, false)
            return ViewHolder(view)
        }

        override fun getItemCount() = badges.size

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val (badge, unlocked) = badges[position]

            holder.tvIcon.text = badge.icon
            holder.tvName.text = badge.name
            holder.tvDesc.text = badge.desc

            if (unlocked) {
                holder.itemView.alpha = 1.0f
                holder.ivLocked.visibility = View.GONE
                holder.tvUnlocked.visibility = View.VISIBLE
            } else {
                holder.itemView.alpha = 0.45f
                holder.ivLocked.visibility = View.VISIBLE
                holder.tvUnlocked.visibility = View.GONE
            }
        }
    }
}
