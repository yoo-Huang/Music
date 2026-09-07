package com.example.myapplication.ui.comment

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.R
import com.example.myapplication.base.BaseActivity
import com.example.myapplication.data.remote.CommentItem
import com.example.myapplication.databinding.ActivityCommentBinding
import com.example.myapplication.databinding.ItemCommentBinding
import com.example.myapplication.manager.AccountManager
import com.example.myapplication.util.loadImageCircle
import com.example.myapplication.util.showToast
import com.example.myapplication.viewmodel.CommentViewModel

/**
 * 评论页面
 * 支持查看评论、点赞评论、发送/回复评论
 *
 * Intent 参数：
 * - "comment_type": Int 评论类型 (0=歌曲, 2=歌单)
 * - "resource_id": Long 资源 ID（歌曲 ID 或歌单 ID）
 * - "resource_name": String 资源名称（用于标题）
 */
class CommentActivity : BaseActivity<ActivityCommentBinding>() {

    private lateinit var viewModel: CommentViewModel
    private var replyTarget: CommentItem? = null

    override fun initBinding() = ActivityCommentBinding.inflate(layoutInflater)

    override fun initView() {
        viewModel = ViewModelProvider(this)[CommentViewModel::class.java]

        val commentType = intent.getIntExtra("comment_type", 0)
        val resourceId = intent.getLongExtra("resource_id", 0)
        val resourceName = intent.getStringExtra("resource_name") ?: ""

        // Toolbar
        binding.toolbar.title = if (resourceName.isNotEmpty()) "评论 · $resourceName" else "评论"
        binding.toolbar.setNavigationOnClickListener { finish() }

        // RecyclerView 设置
        binding.rvComments.layoutManager = LinearLayoutManager(this)
        binding.rvComments.adapter = CommentAdapter(::onReplyClick)

        binding.rvHotComments.layoutManager = LinearLayoutManager(this)
        binding.rvHotComments.adapter = CommentAdapter(::onReplyClick)

        // 加载更多（滚动到底部时触发）
        binding.rvComments.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
                val lastVisiblePos = layoutManager.findLastVisibleItemPosition()
                val totalCount = layoutManager.itemCount
                if (lastVisiblePos >= totalCount - 3 && totalCount > 0) {
                    viewModel.loadMoreComments()
                }
            }
        })

        // 发送按钮
        binding.btnSend.setOnClickListener { sendComment() }

        // 输入框监听（回复提示时清空）
        binding.etComment.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    override fun initObserver() {
        viewModel.comments.observe(this) { comments ->
            (binding.rvComments.adapter as? CommentAdapter)?.submitList(comments)
        }

        viewModel.hotComments.observe(this) { hotComments ->
            if (hotComments.isNotEmpty()) {
                binding.tvHotTitle.visibility = View.VISIBLE
                binding.rvHotComments.visibility = View.VISIBLE
                binding.viewHotDivider.visibility = View.VISIBLE
                (binding.rvHotComments.adapter as? CommentAdapter)?.submitList(hotComments)
            } else {
                binding.tvHotTitle.visibility = View.GONE
                binding.rvHotComments.visibility = View.GONE
                binding.viewHotDivider.visibility = View.GONE
            }
        }

        viewModel.totalCount.observe(this) { total ->
            binding.tvCommentCount.text = "全部评论 ($total)"
        }

        viewModel.isLoading.observe(this) { loading ->
            binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        }

        viewModel.errorMsg.observe(this) { error ->
            if (error != null) showToast(error)
        }

        viewModel.sendResult.observe(this) { success ->
            if (success != null) {
                if (success) {
                    showToast(if (replyTarget != null) "回复成功" else "评论成功")
                    binding.etComment.text.clear()
                    replyTarget = null
                    updateReplyHint()
                    // 隐藏键盘
                    val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
                    imm?.hideSoftInputFromWindow(binding.etComment.windowToken, 0)
                    // 刷新评论列表
                    viewModel.loadComments(viewModel.commentType, viewModel.resourceId)
                } else {
                    showToast("操作失败")
                }
                viewModel.clearSendResult()
            }
        }


    }

    override fun initData() {
        val commentType = intent.getIntExtra("comment_type", 0)
        val resourceId = intent.getLongExtra("resource_id", 0)
        viewModel.loadComments(commentType, resourceId)
    }

    /**
     * 发送评论
     */
    private fun sendComment() {
        if (!AccountManager.isLoggedIn) {
            showToast("请先登录后再评论")
            return
        }
        val content = binding.etComment.text.toString().trim()
        if (content.isEmpty()) {
            showToast("评论内容不能为空")
            return
        }
        viewModel.sendComment(content, replyTarget)
    }

    /**
     * 回复评论
     */
    private fun onReplyClick(comment: CommentItem) {
        if (!AccountManager.isLoggedIn) {
            showToast("请先登录后再回复")
            return
        }
        replyTarget = comment
        binding.etComment.requestFocus()
        binding.etComment.hint = "回复 ${comment.user?.nickname ?: "用户"}："
        val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.showSoftInput(binding.etComment, InputMethodManager.SHOW_IMPLICIT)
    }

    /**
     * 更新回复提示
     */
    private fun updateReplyHint() {
        if (replyTarget != null) {
            binding.etComment.hint = "回复 ${replyTarget?.user?.nickname ?: "用户"}："
        } else {
            binding.etComment.hint = "说点什么..."
        }
    }

    // ==================== Comment Adapter ====================

    /**
     * 评论列表适配器
     */
    inner class CommentAdapter(
        private val onReplyClick: (CommentItem) -> Unit
    ) : RecyclerView.Adapter<CommentAdapter.VH>() {

        private val items = mutableListOf<CommentItem>()

        fun submitList(newItems: List<CommentItem>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VH {
            val inflater = android.view.LayoutInflater.from(parent.context)
            val binding = ItemCommentBinding.inflate(inflater, parent, false)
            return VH(binding)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.bind(items[position])
        }

        inner class VH(private val binding: ItemCommentBinding) : RecyclerView.ViewHolder(binding.root) {

            fun bind(comment: CommentItem) {
                val ctx = binding.root.context

                // 头像
                binding.ivAvatar.loadImageCircle(comment.user?.avatarUrl, android.R.drawable.ic_menu_gallery)

                // 昵称
                binding.tvNickname.text = comment.user?.nickname ?: "匿名用户"

                // VIP 标签
                binding.tvVipTag.visibility = if (comment.user?.vipType == 11) View.VISIBLE else View.GONE

                // 时间
                binding.tvTime.text = comment.displayTime

                // IP 属地
                val location = comment.ipLocation?.location
                if (!location.isNullOrBlank()) {
                    binding.tvLocation.visibility = View.VISIBLE
                    binding.tvLocation.text = location
                } else {
                    binding.tvLocation.visibility = View.GONE
                }

                // 评论内容
                binding.tvContent.text = comment.content ?: ""

                // 被回复的评论
                val beReplied = comment.beReplied?.firstOrNull()
                if (beReplied != null && !beReplied.content.isNullOrBlank()) {
                    binding.layoutReplied.visibility = View.VISIBLE
                    binding.tvRepliedUser.text = "@${beReplied.user?.nickname ?: "用户"}"
                    binding.tvRepliedContent.text = beReplied.content
                } else {
                    binding.layoutReplied.visibility = View.GONE
                }

                // 回复点击
                binding.tvReplyBtn.setOnClickListener {
                    onReplyClick(comment)
                }
            }
        }
    }


}
