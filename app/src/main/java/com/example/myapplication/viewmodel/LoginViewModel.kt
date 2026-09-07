package com.example.myapplication.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.myapplication.base.BaseViewModel
import com.example.myapplication.data.remote.ApiService
import com.example.myapplication.data.remote.CaptchaResponse
import com.example.myapplication.data.remote.LoginUserProfile
import com.example.myapplication.manager.AccountManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 登录页面 ViewModel
 *
 * 支持三种登录方式：
 * 1. 手机号 + 密码
 * 2. 手机号 + 验证码
 * 3. 二维码扫码登录
 */
class LoginViewModel : BaseViewModel() {

    // ==================== 通用状态 ====================

    /** 加载中 */
    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    /** Toast 提示消息（一次性事件） */
    private val _toastMsg = MutableLiveData<String>()
    val toastMsg: LiveData<String> = _toastMsg

    /** 登录成功事件（一次性事件） */
    private val _loginSuccess = MutableLiveData(false)
    val loginSuccess: LiveData<Boolean> = _loginSuccess

    // ==================== 验证码登录 ====================

    /** 验证码已发送（用于 UI 显示倒计时） */
    private val _captchaSent = MutableLiveData(false)
    val captchaSent: LiveData<Boolean> = _captchaSent

    /** 验证码倒计时秒数 */
    private val _captchaCountdown = MutableLiveData(0)
    val captchaCountdown: LiveData<Int> = _captchaCountdown

    // ==================== 二维码登录 ====================

    /** 二维码 key */
    private val _qrKey = MutableLiveData<String?>()
    val qrKey: LiveData<String?> = _qrKey

    /** 二维码图片 base64 */
    private val _qrImage = MutableLiveData<String?>()
    val qrImage: LiveData<String?> = _qrImage

    /** 二维码状态文字 */
    private val _qrStatusText = MutableLiveData("请使用网易云音乐 App 扫码登录")
    val qrStatusText: LiveData<String> = _qrStatusText

    /** 二维码是否过期 */
    private val _qrExpired = MutableLiveData(false)
    val qrExpired: LiveData<Boolean> = _qrExpired

    // ==================== 手机号 + 密码登录 ====================

    /**
     * 手机号 + 密码登录
     */
    fun loginWithPassword(phone: String, password: String) {
        if (!validatePhone(phone)) return
        if (password.isBlank()) {
            _toastMsg.value = "请输入密码"
            return
        }
        _isLoading.value = true
        launchRequest(
            block = { ApiService.loginCellphone(phone, password) },
            onSuccess = { response ->
                _isLoading.value = false
                if (response.code == 200) {
                    onLoginSuccess(response.cookie, response.profile)
                } else {
                    _toastMsg.value = response.message ?: "登录失败，请检查账号密码"
                }
            },
            onError = { e ->
                _isLoading.value = false
                _toastMsg.value = e.message ?: "网络错误，请重试"
            }
        )
    }

    // ==================== 验证码登录 ====================

    /**
     * 发送验证码
     */
    fun sendCaptcha(phone: String) {
        if (!validatePhone(phone)) return
        _isLoading.value = true
        launchRequest(
            block = { ApiService.sendCaptcha(phone) },
            onSuccess = { response: com.example.myapplication.data.remote.CaptchaResponse ->
                _isLoading.value = false
                if (response.code == 200) {
                    _captchaSent.value = true
                    _toastMsg.value = "验证码已发送"
                    startCountdown()
                } else {
                    _toastMsg.value = response.message ?: "验证码发送失败"
                }
            },
            onError = { e ->
                _isLoading.value = false
                _toastMsg.value = e.message ?: "网络错误，请重试"
            }
        )
    }

    /**
     * 验证码登录
     */
    fun loginWithCaptcha(phone: String, captcha: String) {
        if (!validatePhone(phone)) return
        if (captcha.isBlank()) {
            _toastMsg.value = "请输入验证码"
            return
        }
        _isLoading.value = true
        launchRequest(
            block = { ApiService.loginWithCaptcha(phone, captcha) },
            onSuccess = { response ->
                _isLoading.value = false
                if (response.code == 200) {
                    onLoginSuccess(response.cookie, response.profile)
                } else {
                    _toastMsg.value = response.message ?: "验证码错误或已过期"
                }
            },
            onError = { e ->
                _isLoading.value = false
                _toastMsg.value = e.message ?: "网络错误，请重试"
            }
        )
    }

    // ==================== 二维码登录 ====================

    /** 是否正在轮询，防止重复启动 */
    private var pollingActive = false

    /** 当前轮询协程的 Job */
    private var pollingJob: kotlinx.coroutines.Job? = null

    /**
     * 获取二维码 Key 并生成二维码图片
     * 生成成功后自动开始轮询扫描状态
     */
    fun fetchQrCode() {
        _isLoading.value = true
        _qrExpired.value = false
        // 停止旧轮询
        stopPolling()
        viewModelScope.launch {
            try {
                // ---- 1. 获取 unikey（原始 JSON 手动解析，兼容外层包裹） ----
                val keyRaw = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    com.example.myapplication.data.remote.ApiClient.getRaw("/login/qr/key")
                }
                val keyRoot = org.json.JSONObject(keyRaw ?: "")
                val keyInner = keyRoot.optJSONObject("data")
                val keySrc: org.json.JSONObject = if (keyInner != null && keyInner.has("unikey")) keyInner else keyRoot
                val key = keySrc.optString("unikey", "")
                if (key.isBlank()) {
                    _isLoading.postValue(false)
                    _toastMsg.postValue("获取二维码失败：unikey 为空")
                    return@launch
                }
                _qrKey.value = key

                // ---- 2. 生成二维码图片（原始 JSON 手动解析） ----
                val qrRaw = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    com.example.myapplication.data.remote.ApiClient.getRaw(
                        "/login/qr/create", mapOf("key" to key, "qrimg" to "true")
                    )
                }
                val qrRoot = org.json.JSONObject(qrRaw ?: "")
                val qrInner = qrRoot.optJSONObject("data")
                val qrSrc: org.json.JSONObject = if (qrInner != null && qrInner.has("qrimg")) qrInner else qrRoot
                val qrimg = qrSrc.optString("qrimg", "")
                if (qrimg.isBlank()) {
                    _isLoading.postValue(false)
                    _toastMsg.postValue("获取二维码失败：qrimg 为空")
                    return@launch
                }
                _qrImage.value = qrimg
                // 二维码生成成功后立即开始轮询
                startPollingLoop(key)

                _isLoading.postValue(false)
                _qrStatusText.postValue("请使用网易云音乐 App 扫码登录")
            } catch (e: kotlinx.coroutines.CancellationException) {
                _isLoading.postValue(false)
                throw e
            } catch (e: Exception) {
                _isLoading.postValue(false)
                android.util.Log.e("LoginVM", "fetchQrCode failed: ${e.message}", e)
                _toastMsg.postValue("获取二维码失败：${e.message}")
            }
        }
    }

    /**
     * 开始轮询二维码扫描状态（供外部调用，如切换 Tab 时）
     * 每 2 秒检查一次，直到登录成功或过期
     */
    fun startQrPolling() {
        val key = _qrKey.value
        if (!key.isNullOrBlank()) {
            startPollingLoop(key)
        }
        // 如果 key 还没拿到，fetchQrCode 拿到后会自动开始轮询
    }

    /**
     * 停止二维码轮询
     */
    fun stopPolling() {
        pollingActive = false
        pollingJob?.cancel()
        pollingJob = null
    }

    /** 轮询超时时间（秒），超时后自动刷新二维码。与网易云服务端约 180s 对齐 */
    private val POLLING_TIMEOUT_SEC = 180

    /**
     * 实际的轮询循环（私有方法）
     *
     * 优化要点：
     * - 轮询间隔 1.5s，快速响应扫码
     * - 使用 QR 专用客户端（5s 超时 + 无日志），避免 IO 卡顿
     * - 803 优先处理，拿到 cookie 立刻停止轮询并触发登录成功
     * - 120s 超时自动停止并刷新二维码
     */
    private fun startPollingLoop(key: String) {
        if (pollingActive) {
            android.util.Log.w("LoginVM", "startPollingLoop: already active, ignored")
            return
        }
        pollingActive = true
        val startTime = System.currentTimeMillis()
        android.util.Log.d("LoginVM", "QR polling started, key=${key.take(8)}...")
        pollingJob = viewModelScope.launch {
            var lastCode = 0
            var nullCount = 0
            while (pollingActive) {
                try {
                    delay(1500L)

                    // 超时保护：超过 180s 自动刷新
                    val elapsed = (System.currentTimeMillis() - startTime) / 1000
                    if (elapsed > POLLING_TIMEOUT_SEC) {
                        android.util.Log.w("LoginVM", "QR polling timeout (${elapsed}s), auto-refresh")
                        pollingActive = false
                        _qrExpired.postValue(true)
                        _qrStatusText.postValue("二维码已过期，正在刷新...")
                        fetchQrCode()
                        return@launch
                    }

                    // 剩余不足 60s 时提示用户尽快扫码
                    val remaining = POLLING_TIMEOUT_SEC - elapsed
                    if (remaining in 1..60 && lastCode == 801) {
                        _qrStatusText.postValue("请使用网易云音乐 App 扫码登录（${remaining}s 后过期）")
                    }

                    // QR 专用快速客户端：5s 超时 + 无日志
                    val rawResp = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        com.example.myapplication.data.remote.ApiClient.qrPollGet(
                            "/login/qr/check", mapOf("key" to key)
                        )
                    }
                    if (rawResp == null) {
                        nullCount++
                        if (nullCount == 3) android.util.Log.w("LoginVM", "QR poll null×$nullCount")
                        continue
                    }
                    nullCount = 0

                    val root = org.json.JSONObject(rawResp.body)
                    val inner = root.optJSONObject("data")
                    val source: org.json.JSONObject =
                        if (inner != null && inner.has("code")) inner else root
                    val statusCode = source.optInt("code", -1)

                    // 状态未变时跳过（仅在 801 时优化），802/803/800 每次都处理
                    if (statusCode == lastCode && statusCode == 801) continue
                    lastCode = statusCode
                    android.util.Log.d("LoginVM", "QR poll status=$statusCode key=${key.take(8)}")

                    when (statusCode) {
                        // 803 优先：登录成功 → 立刻停止 + 保存 cookie + 触发跳转
                        803 -> {
                            _qrStatusText.postValue("登录成功！")
                            pollingActive = false

                            val jsonCookie = source.optString("cookie", "")
                                .takeIf { it.isNotBlank() }
                            val headerCookie = rawResp.extractCookies()
                            val bestCookie = jsonCookie ?: headerCookie
                            android.util.Log.d("LoginVM", "QR login OK, cookieLen=${bestCookie?.length ?: 0}")

                            AccountManager.saveLoginInfo(bestCookie, null)
                            _loginSuccess.postValue(true)
                            fetchAndSaveProfile()
                            return@launch
                        }
                        802 -> _qrStatusText.postValue("已扫码，请在手机上确认登录")
                        800 -> {
                            // 服务端二维码过期 → 立即自动刷新，无需用户手动点击
                            android.util.Log.w("LoginVM", "QR expired (server 800), auto-refresh")
                            _qrExpired.postValue(true)
                            _qrStatusText.postValue("二维码已过期，正在自动刷新...")
                            pollingActive = false
                            fetchQrCode()
                            return@launch
                        }
                        // 801 及未知状态 → 静默等待，不频繁更新 UI
                        else -> { /* 等待扫码，不重复刷新 UI */ }
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    android.util.Log.e("LoginVM", "QR poll exception: ${e.message}")
                }
            }
        }
    }

    // ==================== 退出登录 ====================

    /**
     * 退出登录（调用 API + 清除本地状态）
     */
    fun doLogout() {
        _isLoading.value = true
        launchRequest(
            block = { ApiService.logout() },
            onSuccess = {
                _isLoading.value = false
                AccountManager.clearLoginState()
                _toastMsg.value = "已退出登录"
            },
            onError = { e ->
                _isLoading.value = false
                AccountManager.clearLoginState()
                _toastMsg.value = "已退出登录"
            }
        )
    }

    // ==================== 注册 ====================

    /**
     * 发送注册验证码
     */
    fun sendRegisterCaptcha(phone: String) {
        if (!validatePhone(phone)) return
        _isLoading.value = true
        launchRequest(
            block = { ApiService.sendCaptcha(phone) },
            onSuccess = { response: CaptchaResponse ->
                _isLoading.value = false
                if (response.code == 200) {
                    _captchaSent.value = true
                    _toastMsg.value = "验证码已发送"
                    startCountdown()
                } else {
                    _toastMsg.value = response.message ?: "验证码发送失败"
                }
            },
            onError = { e ->
                _isLoading.value = false
                _toastMsg.value = e.message ?: "网络错误，请重试"
            }
        )
    }

    /**
     * 执行注册
     */
    fun doRegister(phone: String, captcha: String, password: String, nickname: String = "") {
        if (!validatePhone(phone)) return
        if (captcha.isBlank()) {
            _toastMsg.value = "请输入验证码"
            return
        }
        if (password.length < 6) {
            _toastMsg.value = "密码至少 6 位"
            return
        }
        _isLoading.value = true
        launchRequest(
            block = { ApiService.registerCellphone(phone, captcha, password, nickname) },
            onSuccess = { response ->
                _isLoading.value = false
                if (response.code == 200) {
                    onLoginSuccess(response.cookie, response.profile)
                } else {
                    _toastMsg.value = response.message ?: "注册失败，请重试"
                }
            },
            onError = { e ->
                _isLoading.value = false
                _toastMsg.value = e.message ?: "网络错误，请重试"
            }
        )
    }

    /**
     * 二维码登录成功后后台获取完整用户信息（不阻塞登录流程）
     *
     * 策略（逐级兜底）：
     * 1. /login/status Gson 类型化解析 → 提取 profile / account
     * 2. /login/status 原始 JSON 手动提取 → 兼容意外的响应结构
     * 3. 以上都失败 → 由 MineViewModel 后续通过 fixMissingUserId 补救
     */
    private fun fetchAndSaveProfile() {
        viewModelScope.launch {
            try {
                val savedCookie = AccountManager.cookie
                android.util.Log.d("LoginVM", "fetchAndSaveProfile: cookie=${savedCookie?.take(50)}...")
                resolveProfileFromLoginStatus()
            } catch (_: Exception) {
                // 网络异常不阻塞
            }
        }
    }

    /**
     * 从 /login/status 解析用户信息并保存
     */
    private suspend fun resolveProfileFromLoginStatus() {
        // ---- 第一步：类型化 Gson 解析 ----
        val typedResult = ApiService.getLoginStatus()
        if (typedResult is com.example.myapplication.base.Result.Success) {
            val data = typedResult.data
            val profile: LoginUserProfile? = data.data?.profile ?: data.profile
            val account = data.data?.account ?: data.account

            if (profile != null && profile.userId > 0) {
                android.util.Log.d("LoginVM", "Profile found via typed parse: userId=${profile.userId}")
                AccountManager.saveLoginInfo(AccountManager.cookie, profile)
                return
            }
            if (account != null && account.id > 0) {
                android.util.Log.d("LoginVM", "Account found via typed parse: id=${account.id}")
                AccountManager.saveLoginInfo(
                    AccountManager.cookie,
                    LoginUserProfile(userId = account.id)
                )
                return
            }
        }

        // ---- 第二步：原始 JSON 手动提取 ----
        val rawJson = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            com.example.myapplication.data.remote.ApiClient.getRaw("/login/status")
        }
        if (!rawJson.isNullOrBlank()) {
            try {
                val root = org.json.JSONObject(rawJson)
                // 兼容可能嵌套在 "data" 里的结构
                val inner = root.optJSONObject("data")
                val source = inner ?: root

                val profileObj = source.optJSONObject("profile")
                val accountObj = source.optJSONObject("account")

                val uid = profileObj?.optLong("userId", 0L) ?: 0L
                val aid = accountObj?.optLong("id", 0L) ?: 0L

                if (uid > 0) {
                    val nick = profileObj?.optString("nickname", null)
                    val avatar = profileObj?.optString("avatarUrl", null)
                    android.util.Log.d("LoginVM", "Profile found via raw JSON: userId=$uid, nick=$nick")
                    AccountManager.saveLoginInfo(
                        AccountManager.cookie,
                        LoginUserProfile(userId = uid, nickname = nick, avatarUrl = avatar)
                    )
                    return
                }
                if (aid > 0) {
                    android.util.Log.d("LoginVM", "Account found via raw JSON: id=$aid")
                    AccountManager.saveLoginInfo(
                        AccountManager.cookie,
                        LoginUserProfile(userId = aid)
                    )
                    return
                }
            } catch (e: Exception) {
                android.util.Log.e("LoginVM", "Raw JSON parse failed: ${e.message}", e)
            }
        }

        // ---- 第三步：无可用信息，由 MineViewModel 补救 ----
        android.util.Log.w("LoginVM", "Could not resolve profile from /login/status")
    }

    // ==================== 私有方法 ====================

    /**
     * 登录成功后保存信息
     */
    private fun onLoginSuccess(cookie: String?, profile: LoginUserProfile?) {
        AccountManager.saveLoginInfo(cookie, profile)
        _loginSuccess.value = true
    }

    /**
     * 校验手机号格式
     */
    private fun validatePhone(phone: String): Boolean {
        if (phone.isBlank()) {
            _toastMsg.value = "请输入手机号"
            return false
        }
        if (phone.length != 11 || !phone.all { it.isDigit() }) {
            _toastMsg.value = "请输入正确的 11 位手机号"
            return false
        }
        return true
    }

    /**
     * 验证码倒计时（60 秒）
     */
    private fun startCountdown() {
        viewModelScope.launch {
            for (count in 60 downTo 1) {
                _captchaCountdown.value = count
                delay(1000)
            }
            _captchaSent.value = false
        }
    }
}
