package com.example.myapplication.ui.mine

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.base.Result
import com.example.myapplication.data.Repository
import com.example.myapplication.data.remote.FollowUserItem
import com.example.myapplication.manager.AccountManager
import kotlinx.coroutines.launch

/**
 * 关注/粉丝列表 ViewModel
 */
class FollowListViewModel : ViewModel() {

    private val repository = Repository.getInstance()

    private val _followList = MutableLiveData<List<FollowUserItem>>()
    val followList: LiveData<List<FollowUserItem>> = _followList

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMsg = MutableLiveData<String?>()
    val errorMsg: LiveData<String?> = _errorMsg

    private val _hasMore = MutableLiveData(true)
    val hasMore: LiveData<Boolean> = _hasMore

    private var currentOffset = 0
    private var isFollows = true // true=关注列表, false=粉丝列表
    private var targetUid = 0L

    fun init(isFollows: Boolean, uid: Long) {
        this.isFollows = isFollows
        this.targetUid = uid
        loadFirstPage()
    }

    fun loadFirstPage() {
        currentOffset = 0
        _followList.value = emptyList()
        _hasMore.value = true
        loadData()
    }

    fun loadNextPage() {
        if (_isLoading.value == true || _hasMore.value == false) return
        loadData()
    }

    private fun loadData() {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                val uid = targetUid.let { if (it > 0) it else AccountManager.userId }
                if (uid <= 0) {
                    _errorMsg.value = "用户信息无效"
                    _isLoading.value = false
                    return@launch
                }

                val result = if (isFollows) {
                    repository.getUserFollows(uid, 30, currentOffset)
                } else {
                    repository.getUserFolloweds(uid, 30, currentOffset)
                }

                when (result) {
                    is Result.Success -> {
                        val newItems = result.data.users
                        val currentList = _followList.value?.toMutableList() ?: mutableListOf()
                        if (currentOffset == 0) {
                            _followList.value = newItems
                        } else {
                            currentList.addAll(newItems)
                            _followList.value = currentList
                        }
                        _hasMore.value = result.data.more
                        if (newItems.isNotEmpty()) {
                            currentOffset += newItems.size
                        }
                    }
                    is Result.Error -> {
                        if (currentOffset == 0) {
                            _errorMsg.value = result.exception.message ?: "加载失败"
                        }
                    }
                }
            } catch (e: Exception) {
                if (currentOffset == 0) {
                    _errorMsg.value = e.message ?: "加载失败"
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearError() {
        _errorMsg.value = null
    }
}
