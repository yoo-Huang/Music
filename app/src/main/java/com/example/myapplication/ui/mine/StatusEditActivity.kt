package com.example.myapplication.ui.mine

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.R
import com.example.myapplication.databinding.ActivityStatusEditBinding
import com.example.myapplication.manager.AccountManager
import com.example.myapplication.manager.SameStatusUser
import com.example.myapplication.manager.StatusItem
import com.example.myapplication.manager.UserStatusManager
import com.example.myapplication.util.showToast

/**
 * 用户状态编辑页面
 * 支持选择预设状态和自定义状态输入
 */
class StatusEditActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStatusEditBinding
    private var currentStatus: StatusItem = UserStatusManager.getCurrentStatus()
    private var selectedStatus: StatusItem = currentStatus
    private var isCustomMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStatusEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.ivBack.setOnClickListener { finish() }

        updateCurrentStatusDisplay()

        binding.rvStatus.apply {
            layoutManager = LinearLayoutManager(this@StatusEditActivity)
            adapter = StatusAdapter(
                statuses = UserStatusManager.availableStatuses,
                currentId = currentStatus.id,
                onStatusSelected = { status ->
                    if (status.id == "custom") {
                        // 切换到自定义输入模式
                        isCustomMode = true
                        selectedStatus = status
                        binding.llCustomInput.visibility = View.VISIBLE
                    } else {
                        // 直接选择预设状态
                        isCustomMode = false
                        selectedStatus = status
                        binding.llCustomInput.visibility = View.GONE
                        saveAndFinish(status)
                    }
                },
                onSameStatusClick = { status ->
                    // 跳转到相同状态用户页面
                    val intent = Intent(this@StatusEditActivity, SameStatusUsersActivity::class.java)
                    intent.putExtra("status_id", status.id)
                    intent.putExtra("status_name", status.name)
                    intent.putExtra("status_emoji", status.emoji)
                    startActivity(intent)
                }
            )
        }

        // 自定义状态保存
        binding.btnSaveCustom.setOnClickListener {
            val text = binding.etCustomStatus.text.toString().trim()
            if (text.isBlank()) {
                showToast("请输入状态内容")
                return@setOnClickListener
            }
            val customStatus = StatusItem(
                id = "custom",
                name = "自定义",
                subtitle = text,
                emoji = "✏️"
            )
            UserStatusManager.setStatus(customStatus, customText = text)
            currentStatus = customStatus
            selectedStatus = customStatus
            showToast("状态已更新")
            updateCurrentStatusDisplay()
            binding.llCustomInput.visibility = View.GONE
        }
    }

    private fun updateCurrentStatusDisplay() {
        val status = UserStatusManager.getCurrentStatus()
        val customText = UserStatusManager.getCustomStatusText()
        val display = if (status.id == "custom" && !customText.isNullOrBlank()) {
            "${status.emoji} 自定义 · $customText"
        } else {
            "${status.emoji} ${status.name} · ${status.subtitle}"
        }
        binding.tvCurrentStatus.text = display
    }

    private fun saveAndFinish(status: StatusItem) {
        UserStatusManager.setStatus(status)
        showToast("状态已更新")
        updateCurrentStatusDisplay()
    }

    // ==================== Adapter ====================

    private class StatusAdapter(
        private val statuses: List<StatusItem>,
        private val currentId: String,
        private val onStatusSelected: (StatusItem) -> Unit,
        private val onSameStatusClick: (StatusItem) -> Unit
    ) : RecyclerView.Adapter<StatusAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvEmoji: TextView = view.findViewById(R.id.tv_status_emoji)
            val tvName: TextView = view.findViewById(R.id.tv_status_name)
            val tvSubtitle: TextView = view.findViewById(R.id.tv_status_subtitle)
            val tvSameStatus: TextView = view.findViewById(R.id.tv_same_status)
            val ivSelected: View = view.findViewById(R.id.iv_selected)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_status, parent, false)
            return ViewHolder(view)
        }

        override fun getItemCount() = statuses.size

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val status = statuses[position]

            holder.tvEmoji.text = status.emoji
            holder.tvName.text = status.name
            holder.tvSubtitle.text = status.subtitle

            // 选中状态
            val isSelected = status.id == currentId || (status.id == "custom" && currentId == "custom")
            holder.ivSelected.visibility = if (isSelected) View.VISIBLE else View.GONE

            // 相同状态按钮
            holder.tvSameStatus.setOnClickListener { onSameStatusClick(status) }

            // 点击选择
            holder.itemView.setOnClickListener { onStatusSelected(status) }
        }
    }
}
