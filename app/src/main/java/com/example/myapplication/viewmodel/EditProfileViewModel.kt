package com.example.myapplication.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.base.Result
import com.example.myapplication.data.Repository
import com.example.myapplication.data.remote.LoginUserProfile
import com.example.myapplication.manager.AccountManager
import kotlinx.coroutines.launch

/**
 * 个人资料编辑 ViewModel
 */
class EditProfileViewModel : ViewModel() {

    private val repository = Repository.getInstance()

    /** 用户资料 */
    private val _profile = MutableLiveData<LoginUserProfile?>()
    val profile: LiveData<LoginUserProfile?> = _profile

    /** 保存状态 */
    private val _isSaving = MutableLiveData(false)
    val isSaving: LiveData<Boolean> = _isSaving

    /** 头像上传状态 */
    private val _isUploading = MutableLiveData(false)
    val isUploading: LiveData<Boolean> = _isUploading

    /** 保存结果消息 */
    private val _saveResult = MutableLiveData<String?>()
    val saveResult: LiveData<String?> = _saveResult

    /** 头像上传结果 */
    private val _avatarUploadResult = MutableLiveData<Boolean?>()
    val avatarUploadResult: LiveData<Boolean?> = _avatarUploadResult

    /** 错误信息 */
    private val _errorMsg = MutableLiveData<String?>()
    val errorMsg: LiveData<String?> = _errorMsg

    init {
        loadProfile()
    }

    /** 加载本地缓存的用户资料 */
    fun loadProfile() {
        _profile.value = AccountManager.profile
    }

    /**
     * 上传头像
     */
    fun uploadAvatar(imgData: ByteArray) {
        if (!AccountManager.isLoggedIn) {
            _errorMsg.value = "请先登录"
            return
        }

        _isUploading.value = true
        viewModelScope.launch {
            try {
                val result = com.example.myapplication.data.remote.ApiService.uploadAvatar(imgData)
                when (result) {
                    is Result.Success -> {
                        if (result.data.code == 200) {
                            // 获取服务端返回的新头像 URL
                            val newUrl = result.data.data?.url
                            if (!newUrl.isNullOrBlank()) {
                                // 更新本地缓存的头像 URL
                                val currentProfile = AccountManager.profile
                                val updatedProfile = LoginUserProfile(
                                    userId = currentProfile?.userId ?: AccountManager.userId,
                                    nickname = currentProfile?.nickname,
                                    avatarUrl = newUrl,
                                    birthday = currentProfile?.birthday ?: 0L,
                                    gender = currentProfile?.gender ?: 0,
                                    signature = currentProfile?.signature,
                                    followeds = currentProfile?.followeds ?: 0L,
                                    follows = currentProfile?.follows ?: 0L,
                                    playlistCount = currentProfile?.playlistCount ?: 0,
                                    eventCount = currentProfile?.eventCount ?: 0
                                )
                                AccountManager.saveLoginInfo(
                                    cookie = AccountManager.cookie,
                                    profile = updatedProfile
                                )
                            }
                            _avatarUploadResult.postValue(true)
                        } else {
                            _errorMsg.postValue(result.data.message ?: "头像上传失败")
                            _avatarUploadResult.postValue(false)
                        }
                    }
                    is Result.Error -> {
                        _errorMsg.postValue(result.exception.message ?: "上传失败，请检查网络")
                        _avatarUploadResult.postValue(false)
                    }
                }
            } catch (e: Exception) {
                _errorMsg.postValue("上传失败: ${e.message}")
                _avatarUploadResult.postValue(false)
            } finally {
                _isUploading.postValue(false)
            }
        }
    }

    /**
     * 保存用户资料
     * @param nickname 昵称（null=不修改）
     * @param signature 签名（null=不修改）
     * @param gender 性别：0-保密, 1-男, 2-女（null=不修改）
     * @param birthday 生日时间戳毫秒（null=不修改）
     */
    fun saveProfile(
        nickname: String?,
        signature: String?,
        gender: Int?,
        birthday: Long?
    ) {
        if (!AccountManager.isLoggedIn) {
            _errorMsg.value = "请先登录"
            return
        }

        _isSaving.value = true
        viewModelScope.launch {
            try {
                val result = repository.updateUserProfile(nickname, signature, gender, birthday)
                when (result) {
                    is Result.Success -> {
                        if (result.data.code == 200) {
                            val currentProfile = AccountManager.profile
                            val serverProfile = result.data.profile

                            // 优先级：用户编辑值 > 本地缓存 > 服务端返回
                            // 这样即使服务端返回旧数据，也不会覆盖用户刚编辑的内容
                            val mergedProfile = LoginUserProfile(
                                userId = serverProfile?.userId?.takeIf { it > 0 }
                                    ?: currentProfile?.userId ?: AccountManager.userId,
                                nickname = nickname
                                    ?: currentProfile?.nickname
                                    ?: serverProfile?.nickname,
                                avatarUrl = serverProfile?.avatarUrl
                                    ?: currentProfile?.avatarUrl,
                                birthday = birthday
                                    ?: currentProfile?.birthday
                                    ?: serverProfile?.birthday?.takeIf { it > 0 }
                                    ?: 0L,
                                gender = gender
                                    ?: currentProfile?.gender
                                    ?: serverProfile?.gender
                                    ?: 0,
                                signature = signature
                                    ?: currentProfile?.signature
                                    ?: serverProfile?.signature,
                                followeds = serverProfile?.followeds
                                    ?: currentProfile?.followeds ?: 0L,
                                follows = serverProfile?.follows
                                    ?: currentProfile?.follows ?: 0L,
                                playlistCount = serverProfile?.playlistCount
                                    ?: currentProfile?.playlistCount ?: 0,
                                eventCount = serverProfile?.eventCount
                                    ?: currentProfile?.eventCount ?: 0
                            )
                            // 同步本地缓存
                            AccountManager.saveLoginInfo(
                                cookie = AccountManager.cookie,
                                profile = mergedProfile
                            )
                            _profile.postValue(mergedProfile)
                            _saveResult.postValue("保存成功")
                        } else {
                            _saveResult.postValue(result.data.message ?: "保存失败")
                        }
                    }
                    is Result.Error -> {
                        _saveResult.postValue(result.exception.message ?: "保存失败，请检查网络")
                    }
                }
            } catch (e: Exception) {
                _saveResult.postValue("保存失败: ${e.message}")
            } finally {
                _isSaving.postValue(false)
            }
        }
    }

    /** 清除消息 */
    fun clearMessage() {
        _saveResult.value = null
        _errorMsg.value = null
    }

    /** 清除头像上传结果 */
    fun clearAvatarResult() {
        _avatarUploadResult.value = null
    }
}
