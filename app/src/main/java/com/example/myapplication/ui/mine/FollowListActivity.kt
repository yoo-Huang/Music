package com.example.myapplication.ui.mine

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.R
import com.example.myapplication.data.remote.FollowUserItem
import com.example.myapplication.manager.AccountManager
import com.example.myapplication.util.loadImageCircle
import com.example.myapplication.util.showToast

/**
 * 关注/粉丝列表页面
 *
 * 通过 Intent extra 区分：
 * - type: "follows" = 关注列表, "followeds" = 粉丝列表
 * - uid: 目标用户 ID（可选，默认当前登录用户）
 */
class FollowListActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TYPE = "follow_type"
        const val EXTRA_UID = "follow_uid"
    }

    private lateinit var viewModel: FollowListViewModel
    private lateinit var adapter: FollowListAdapter
    private var isFollows = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_follow_list)

        isFollows = intent.getStringExtra(EXTRA_TYPE) != "followeds"
        val uid = intent.getLongExtra(EXTRA_UID, AccountManager.userId)

        setupToolbar()
        setupRecyclerView()
        initViewModel(isFollows, uid)
    }

    private fun setupToolbar() {
        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        toolbar.title = if (isFollows) "我的关注" else "我的粉丝"
        toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupRecyclerView() {
        adapter = FollowListAdapter(
            onItemClick = { user ->
                // 点击跳转到用户主页
                if (user.userId > 0) {
                    val intent = Intent(this@FollowListActivity, UserHomeActivity::class.java).apply {
                        putExtra(UserHomeActivity.EXTRA_UID, user.userId)
                    }
                    startActivity(intent)
                }
            }
        )

        findViewById<RecyclerView>(R.id.rv_follow_list).apply {
            layoutManager = LinearLayoutManager(this@FollowListActivity)
            this.adapter = this@FollowListActivity.adapter

            // 滚动到底部加载更多
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    if (dy <= 0) return
                    val lm = recyclerView.layoutManager as? LinearLayoutManager ?: return
                    if (lm.findLastVisibleItemPosition() >= (recyclerView.adapter?.itemCount ?: 0) - 3) {
                        viewModel.loadNextPage()
                    }
                }
            })
        }
    }

    private fun initViewModel(isFollows: Boolean, uid: Long) {
        viewModel = ViewModelProvider(this)[FollowListViewModel::class.java]

        viewModel.followList.observe(this) { list ->
            adapter.submitList(list)
        }

        viewModel.isLoading.observe(this) { loading ->
            findViewById<View>(R.id.progress_bar).visibility =
                if (loading && adapter.itemCount == 0) View.VISIBLE else View.GONE
        }

        viewModel.errorMsg.observe(this) { msg ->
            msg?.let {
                showToast(it)
                viewModel.clearError()
            }
        }

        viewModel.init(isFollows, uid)
    }
}

/**
 * 关注/粉丝列表 Adapter
 */
class FollowListAdapter(
    private val onItemClick: (FollowUserItem) -> Unit
) : RecyclerView.Adapter<FollowListAdapter.VH>() {

    private var items = listOf<FollowUserItem>()

    fun submitList(newItems: List<FollowUserItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_follow_user, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivAvatar: ImageView = itemView.findViewById(R.id.iv_avatar)
        private val tvNickname: TextView = itemView.findViewById(R.id.tv_nickname)
        private val tvSignature: TextView = itemView.findViewById(R.id.tv_signature)
        private val ivGender: ImageView = itemView.findViewById(R.id.iv_gender)
        private val btnFollow: View = itemView.findViewById(R.id.btn_follow)

        fun bind(user: FollowUserItem) {
            // 头像
            ivAvatar.loadImageCircle(user.avatarUrl, R.drawable.ic_person)

            // 昵称
            tvNickname.text = user.nickname?.takeIf { it.isNotBlank() } ?: "用户${user.userId}"

            // 个性签名
            val sig = user.signature?.takeIf { it.isNotBlank() }
            tvSignature.visibility = if (sig != null) View.VISIBLE else View.GONE
            tvSignature.text = sig ?: ""

            // 性别
            ivGender.visibility = View.GONE

            // 关注按钮（粉丝列表中未关注的人显示）
            btnFollow.visibility = View.GONE

            // 整行点击
            itemView.setOnClickListener { onItemClick(user) }
        }
    }
}
