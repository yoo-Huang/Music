package com.example.myapplication.ui.login

import android.content.Intent
import android.view.View
import androidx.lifecycle.ViewModelProvider
import com.bumptech.glide.Glide
import com.example.myapplication.base.BaseActivity
import com.example.myapplication.databinding.ActivityLoginBinding
import com.example.myapplication.util.showToast
import com.example.myapplication.viewmodel.LoginViewModel

/**
 * 登录页面
 *
 * 支持三种登录方式切换：
 * 1. 手机号 + 密码登录
 * 2. 手机号 + 验证码登录
 * 3. 二维码扫码登录
 *
 * 点击"立即注册"跳转到 RegisterActivity
 */
class LoginActivity : BaseActivity<ActivityLoginBinding>() {

    override val enableMiniPlayer: Boolean = false

    private lateinit var viewModel: LoginViewModel

    /** 当前选中的 Tab：0-密码, 1-验证码, 2-二维码 */
    private var currentTab = 0

    /** 三个 Tab 的引用缓存 */
    private lateinit var tabViews: List<View>

    override fun initBinding() = ActivityLoginBinding.inflate(layoutInflater)

    override fun initView() {
        viewModel = ViewModelProvider(this)[LoginViewModel::class.java]

        // 缓存 Tab 引用
        tabViews = listOf(binding.tabPassword, binding.tabCaptcha, binding.tabQrcode)

        // 返回按钮
        binding.ivBack.setOnClickListener { finish() }

        // ==================== Tab 切换 ====================
        binding.tabPassword.setOnClickListener { switchTab(0) }
        binding.tabCaptcha.setOnClickListener { switchTab(1) }
        binding.tabQrcode.setOnClickListener { switchTab(2) }

        // ==================== 密码登录 ====================
        binding.btnLoginPwd.setOnClickListener {
            val phone = binding.etPhonePwd.text?.toString()?.trim() ?: ""
            val password = binding.etPasswordPwd.text?.toString()?.trim() ?: ""
            viewModel.loginWithPassword(phone, password)
        }

        // ==================== 验证码登录 ====================
        binding.btnSendCaptcha.setOnClickListener {
            val phone = binding.etPhoneCaptcha.text?.toString()?.trim() ?: ""
            viewModel.sendCaptcha(phone)
        }
        binding.btnLoginCaptcha.setOnClickListener {
            val phone = binding.etPhoneCaptcha.text?.toString()?.trim() ?: ""
            val captcha = binding.etCaptcha.text?.toString()?.trim() ?: ""
            viewModel.loginWithCaptcha(phone, captcha)
        }

        // ==================== 二维码登录 ====================
        binding.btnRefreshQr.setOnClickListener {
            viewModel.fetchQrCode()
        }

        // ==================== 跳转注册页面 ====================
        binding.tvRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
            // 注册页面切换动画
            overridePendingTransition(
                com.example.myapplication.R.anim.slide_in_right,
                com.example.myapplication.R.anim.slide_out_left
            )
        }

        // 默认选中密码登录 Tab
        switchTab(0)
    }

    override fun initObserver() {
        // 加载状态
        viewModel.isLoading.observe(this) { loading ->
            binding.loadingOverlay.visibility = if (loading) View.VISIBLE else View.GONE
        }

        // Toast 提示
        viewModel.toastMsg.observe(this) { msg ->
            if (!msg.isNullOrBlank()) showToast(msg)
        }

        // 登录成功 → 关闭页面
        viewModel.loginSuccess.observe(this) { success ->
            if (success) {
                showToast("登录成功")
                finish()
            }
        }

        // 验证码发送状态 → 禁用/启用按钮
        viewModel.captchaSent.observe(this) { sent ->
            binding.btnSendCaptcha.isEnabled = !sent
            binding.btnSendCaptcha.text = if (sent) "60s 后重发" else "获取验证码"
        }

        // 验证码倒计时
        viewModel.captchaCountdown.observe(this) { count ->
            if (count > 0) {
                binding.btnSendCaptcha.text = "${count}s 后重发"
            }
        }

        // 二维码图片
        viewModel.qrImage.observe(this) { base64 ->
            if (!base64.isNullOrBlank()) {
                // Glide 需要 Data URI 格式才能识别 base64：
                // "data:image/png;base64,iVBORw0KGgo..."
                val dataUri = if (base64.startsWith("data:image")) {
                    base64
                } else {
                    "data:image/png;base64,$base64"
                }
                Glide.with(this)
                    .asBitmap()
                    .load(dataUri)
                    .into(binding.ivQrcode)
                binding.layoutQrExpired.visibility = View.GONE
            }
        }

        // 二维码状态文字
        viewModel.qrStatusText.observe(this) { text ->
            binding.tvQrStatus.text = text
        }

        // 二维码过期 → 显示遮罩
        viewModel.qrExpired.observe(this) { expired ->
            if (expired) {
                binding.layoutQrExpired.visibility = View.VISIBLE
            }
        }
    }

    /**
     * 切换登录方式 Tab
     *
     * 同时完成三件事：
     * 1. 更新 Tab 文字颜色/加粗状态
     * 2. 移动红色指示器到当前 Tab 下方
     * 3. 切换对应面板的可见性
     *
     * @param tab 0=密码登录, 1=验证码登录, 2=二维码登录
     */
    private fun switchTab(tab: Int) {
        if (currentTab == tab) return
        currentTab = tab

        // 1. 更新 Tab 文字样式
        tabViews.forEachIndexed { index, tv ->
            val isActive = index == tab
            (tv as android.widget.TextView).apply {
                setTextColor(
                    if (isActive) 0xFFEC4141.toInt()
                    else 0xFF999999.toInt()
                )
                paintFlags = if (isActive) {
                    paintFlags or android.graphics.Paint.FAKE_BOLD_TEXT_FLAG
                } else {
                    paintFlags and android.graphics.Paint.FAKE_BOLD_TEXT_FLAG.inv()
                }
            }
        }

        // 2. 移动指示器（通过修改 ConstraintLayout 约束实现）
        val targetTab = tabViews[tab]
        val indicator = binding.indicatorTab
        indicator.post {
            val params = indicator.layoutParams
                    as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
            params.startToStart = targetTab.id
            params.endToEnd = targetTab.id
            indicator.layoutParams = params
        }

        // 3. 切换面板可见性
        binding.panelPassword.visibility = if (tab == 0) View.VISIBLE else View.GONE
        binding.panelCaptcha.visibility = if (tab == 1) View.VISIBLE else View.GONE
        binding.panelQrcode.visibility = if (tab == 2) View.VISIBLE else View.GONE

        // 切换到二维码 Tab 时自动拉取二维码（内部会自动开始轮询）
        if (tab == 2) {
            viewModel.fetchQrCode()
        } else {
            // 离开二维码 Tab 时停止轮询
            viewModel.stopPolling()
        }
    }
}
