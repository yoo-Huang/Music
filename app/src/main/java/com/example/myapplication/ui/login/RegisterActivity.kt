package com.example.myapplication.ui.login

import android.content.Intent
import android.view.View
import androidx.lifecycle.ViewModelProvider
import com.example.myapplication.base.BaseActivity
import com.example.myapplication.databinding.ActivityRegisterBinding
import com.example.myapplication.util.showToast
import com.example.myapplication.viewmodel.LoginViewModel

/**
 * 注册页面
 *
 * 流程：
 * 1. 输入手机号 → 获取验证码
 * 2. 输入验证码 + 设置密码 + 确认密码
 * 3. 点击注册 → 注册成功后自动登录并返回
 *
 * 点击"已有账号？立即登录"可返回登录页
 */
class RegisterActivity : BaseActivity<ActivityRegisterBinding>() {

    override val enableMiniPlayer: Boolean = false

    private lateinit var viewModel: LoginViewModel

    override fun initBinding() = ActivityRegisterBinding.inflate(layoutInflater)

    override fun initView() {
        // 与登录页共享同一个 ViewModel（可复用验证码倒计时等逻辑）
        viewModel = ViewModelProvider(this)[LoginViewModel::class.java]

        // 返回按钮
        binding.ivBack.setOnClickListener { finish() }

        // 获取验证码
        binding.btnSendCaptcha.setOnClickListener {
            val phone = binding.etPhone.text?.toString()?.trim() ?: ""
            viewModel.sendRegisterCaptcha(phone)
        }

        // 注册按钮
        binding.btnRegister.setOnClickListener {
            val phone = binding.etPhone.text?.toString()?.trim() ?: ""
            val captcha = binding.etCaptcha.text?.toString()?.trim() ?: ""
            val password = binding.etPassword.text?.toString()?.trim() ?: ""
            val confirmPassword = binding.etConfirmPassword.text?.toString()?.trim() ?: ""

            // 本地校验
            if (password.length < 6) {
                showToast("密码至少 6 位")
                return@setOnClickListener
            }
            if (password != confirmPassword) {
                showToast("两次输入的密码不一致")
                return@setOnClickListener
            }

            viewModel.doRegister(phone, captcha, password)
        }

        // 跳转回登录页
        binding.tvGoLogin.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            overridePendingTransition(
                com.example.myapplication.R.anim.slide_in_right,
                com.example.myapplication.R.anim.slide_out_left
            )
            finish()
        }
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

        // 注册成功 → 返回登录页（登录页会收到登录成功回调）
        viewModel.loginSuccess.observe(this) { success ->
            if (success) {
                showToast("注册成功，已自动登录")
                // 跳转到 MainActivity 或直接返回
                startActivity(Intent(this, com.example.myapplication.MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                })
                finish()
            }
        }

        // 验证码发送状态
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
    }
}
