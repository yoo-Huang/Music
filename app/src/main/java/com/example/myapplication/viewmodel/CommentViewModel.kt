package com.example.myapplication.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.myapplication.base.BaseViewModel
import com.example.myapplication.data.Repository
import com.example.myapplication.data.remote.CommentItem
import com.example.myapplication.data.remote.CommentResponse

/**
 * 评论 ViewModel
 * 管理评论列表、点赞和发送/回复
 */
class CommentViewModel : BaseViewModel() {

    private val repository = Repository.getInstance()

    /** 评论类型：0-歌曲, 2-歌单 */
    var commentType: Int = 0
    /** 资源 ID（歌曲 ID 或歌单 ID） */
    var resourceId: Long = 0

    @PublishedApi internal val _comments = MutableLiveData<List<CommentItem>>()
    val comments: LiveData<List<CommentItem>> = _comments

    @PublishedApi internal val _hotComments = MutableLiveData<List<CommentItem>>()
    val hotComments: LiveData<List<CommentItem>> = _hotComments

    @PublishedApi internal val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    @PublishedApi internal val _isLoadingMore = MutableLiveData(false)
    val isLoadingMore: LiveData<Boolean> = _isLoadingMore

    @PublishedApi internal val _errorMsg = MutableLiveData<String>()
    val errorMsg: LiveData<String> = _errorMsg

    @PublishedApi internal val _totalCount = MutableLiveData(0L)
    val totalCount: LiveData<Long> = _totalCount

    @PublishedApi internal val _hasMore = MutableLiveData(true)
    val hasMore: LiveData<Boolean> = _hasMore

    /** 发送评论结果 */
    @PublishedApi internal val _sendResult = MutableLiveData<Boolean>()
    val sendResult: LiveData<Boolean> = _sendResult

    /** 点赞结果 */
    @PublishedApi internal val _likeResult = MutableLiveData<Pair<Long, Boolean>?>() // commentId -> liked
    val likeResult: LiveData<Pair<Long, Boolean>?> = _likeResult

    private var currentOffset = 0
    private val pageSize = 20

    /**
     * 初始化并加载评论
     * @param type 0-歌曲, 2-歌单
     * @param id 资源 ID
     */
    fun loadComments(type: Int, id: Long) {
        commentType = type
        resourceId = id
        currentOffset = 0
        _isLoading.value = true
        launchRequest(
            block = {
                if (type == 2) repository.getPlaylistComments(id, pageSize, 0)
                else repository.getSongComments(id, pageSize, 0)
            },
            onSuccess = { response: CommentResponse ->
                _isLoading.value = false
                _totalCount.value = response.total
                _hasMore.value = response.more
                _comments.value = response.comments ?: emptyList()
                _hotComments.value = response.hotComments ?: emptyList()
                currentOffset = (response.comments?.size ?: 0)
            },
            onError = { e ->
                _isLoading.value = false
                _errorMsg.value = e.message
            }
        )
    }

    /**
     * 加载更多评论
     */
    fun loadMoreComments() {
        if (_isLoadingMore.value == true || _hasMore.value != true) return
        _isLoadingMore.value = true
        launchRequest(
            block = {
                if (commentType == 2) repository.getPlaylistComments(resourceId, pageSize, currentOffset)
                else repository.getSongComments(resourceId, pageSize, currentOffset)
            },
            onSuccess = { response: CommentResponse ->
                _isLoadingMore.value = false
                _hasMore.value = response.more
                val newComments = response.comments ?: emptyList()
                val merged = _comments.value.orEmpty() + newComments
                _comments.value = merged
                currentOffset = merged.size
            },
            onError = { e ->
                _isLoadingMore.value = false
                _errorMsg.value = e.message
            }
        )
    }

    /**
     * 点赞/取消点赞评论
     */
    fun toggleLike(comment: CommentItem) {
        if (comment.commentId == 0L) return
        val t = if (comment.liked) 0 else 1
        val newLiked = !comment.liked
        launchRequestRaw(
            block = { repository.likeComment(commentType, resourceId, comment.commentId, t) },
            onSuccess = {
                comment.liked = newLiked
                _likeResult.value = comment.commentId to newLiked
            },
            onError = { e ->
                _errorMsg.value = e.message
            }
        )
    }

    /**
     * 发送评论
     */
    fun sendComment(content: String, replyTo: CommentItem? = null) {
        if (content.isBlank()) {
            _errorMsg.value = "评论内容不能为空"
            return
        }
        launchRequestRaw(
            block = { repository.sendComment(commentType, resourceId, content, replyTo?.commentId) },
            onSuccess = { success ->
                _sendResult.value = true
            },
            onError = { e ->
                _errorMsg.value = e.message
                _sendResult.value = false
            }
        )
    }

    /**
     * 清除发送结果
     */
    fun clearSendResult() {
        _sendResult.value = null
    }

    /**
     * 清除点赞结果
     */
    fun clearLikeResult() {
        _likeResult.value = null
    }
}
