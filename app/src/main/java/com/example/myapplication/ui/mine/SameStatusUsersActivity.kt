package com.example.myapplication.ui.mine

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.R
import com.example.myapplication.databinding.ActivitySameStatusUsersBinding
import com.example.myapplication.manager.AccountManager
import com.example.myapplication.manager.SameStatusUser
import com.example.myapplication.manager.UserStatusManager
import com.example.myapplication.util.loadImageCircle

/**
 * 相同状态用户页面
 * 展示与当前用户有相同状态的其他用户列表（本地记录）
 */
class SameStatusUsersActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySameStatusUsersBinding
    private var statusId: String = ""
    private var statusName: String = ""
    private var statusEmoji: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySameStatusUsersBinding.inflate(layoutInflater)
        setContentView(binding.root)

        statusId = intent.getStringExtra("status_id") ?: "online"
        statusName = intent.getStringExtra("status_name") ?: "在线"
        statusEmoji = intent.getStringExtra("status_emoji") ?: "🟢"

        binding.ivBack.setOnClickListener { finish() }
        binding.tvStatusEmoji.text = statusEmoji

        // 显示当前状态描述
        val currentStatus = UserStatusManager.getCurrentStatus()
        val customText = UserStatusManager.getCustomStatusText()
        if (currentStatus.id == statusId) {
            val display = if (statusId == "custom" && !customText.isNullOrBlank()) {
                "自定义 · $customText"
            } else {
                "${currentStatus.name} · ${currentStatus.subtitle}"
            }
            binding.tvStatusDesc.text = display
        } else {
            binding.tvStatusDesc.text = "${statusName}状态"
        }

        loadUsers()
    }

    private fun loadUsers() {
        // 获取本地记录的相同状态用户
        val users = UserStatusManager.getSameStatusUsers(statusId)
        binding.tvUserCount.text = "${users.size}人"

        if (users.isEmpty()) {
            binding.rvUsers.visibility = View.GONE
            binding.tvEmpty.visibility = View.VISIBLE
        } else {
            binding.rvUsers.visibility = View.VISIBLE
            binding.tvEmpty.visibility = View.GONE
            binding.rvUsers.apply {
                layoutManager = LinearLayoutManager(this@SameStatusUsersActivity)
                adapter = SameStatusAdapter(users)
            }
        }
    }

    // ==================== Adapter ====================

    private class SameStatusAdapter(
        private val users: List<SameStatusUser>
    ) : RecyclerView.Adapter<SameStatusAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val ivAvatar: ImageView = view.findViewById(R.id.iv_avatar)
            val tvNickname: TextView = view.findViewById(R.id.tv_nickname)
            val tvStatusText: TextView = view.findViewById(R.id.tv_status_text)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_same_status_user, parent, false)
            return ViewHolder(view)
        }

        override fun getItemCount() = users.size

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val user = users[position]

            holder.ivAvatar.loadImageCircle(user.avatarUrl, R.drawable.ic_person)
            holder.tvNickname.text = user.nickname.ifBlank { "用户${user.userId}" }
            holder.tvStatusText.text = user.statusText.ifBlank { "-" }
        }
    }
}
