package com.example.myapplication.ui.mine

import android.app.DatePickerDialog
import android.content.ContentResolver
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import androidx.lifecycle.ViewModelProvider
import com.bumptech.glide.Glide
import com.example.myapplication.base.BaseActivity
import com.example.myapplication.databinding.ActivityProfileEditBinding
import com.example.myapplication.manager.AccountManager
import com.example.myapplication.util.dp2px
import com.example.myapplication.util.showToast
import com.example.myapplication.viewmodel.EditProfileViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * 个人资料编辑页面
 * 支持编辑：头像、昵称、签名、性别、生日
 */
class ProfileEditActivity : BaseActivity<ActivityProfileEditBinding>() {

    private lateinit var viewModel: EditProfileViewModel

    /** 修改后的值（null=未修改） */
    private var nickname: String? = null
    private var signature: String? = null
    private var gender: Int? = null
    private var birthday: Long? = null

    /** 当前选中的生日时间戳 */
    private var selectedBirthday: Long = 0L

    private val dateFormat = SimpleDateFormat("yyyy年MM月dd日", Locale.CHINA)

    /** 图片选择器 */
    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let { uploadAvatar(it) }
        }

    override fun initBinding() = ActivityProfileEditBinding.inflate(layoutInflater)

    override fun initView() {
        viewModel = ViewModelProvider(this)[EditProfileViewModel::class.java]

        // 返回按钮
        binding.ivBack.setOnClickListener { finish() }

        // 保存按钮
        binding.tvSave.setOnClickListener { saveProfile() }

        // 加载头像
        loadAvatar()

        // 头像点击 → 打开图片选择器
        binding.tvAvatarHint.setOnClickListener { pickImage() }
        binding.ivAvatar.setOnClickListener { pickImage() }

        // 性别选择
        binding.layoutGender.setOnClickListener { showGenderDialog() }

        // 生日选择
        binding.layoutBirthday.setOnClickListener { showBirthdayDialog() }
    }

    override fun initData() {
        val profile = AccountManager.profile
        if (profile != null) {
            binding.etNickname.setText(profile.nickname ?: "")
            binding.etSignature.setText(profile.signature ?: "")

            // 性别
            gender = profile.gender
            binding.tvGender.text = when (profile.gender) {
                1 -> "男"
                2 -> "女"
                else -> "保密"
            }

            // 生日
            birthday = profile.birthday
            selectedBirthday = profile.birthday
            binding.tvBirthday.text = if (profile.birthday > 0) {
                dateFormat.format(java.util.Date(profile.birthday))
            } else {
                "未设置"
            }

            // 加载头像
            loadAvatar()
        }
    }

    override fun initObserver() {
        viewModel.isSaving.observe(this) { saving ->
            binding.progressBar.visibility = if (saving) View.VISIBLE else View.GONE
        }

        viewModel.isUploading.observe(this) { uploading ->
            binding.progressBar.visibility = if (uploading) View.VISIBLE else View.GONE
        }

        viewModel.saveResult.observe(this) { msg ->
            msg?.let {
                showToast(it)
                if (it == "保存成功") {
                    finish()
                }
                viewModel.clearMessage()
            }
        }

        viewModel.errorMsg.observe(this) { msg ->
            msg?.let {
                showToast(it)
                viewModel.clearMessage()
            }
        }

        // 监听头像上传结果
        viewModel.avatarUploadResult.observe(this) { result ->
            result?.let { success ->
                if (success) {
                    showToast("头像更新成功")
                    loadAvatar()
                }
                viewModel.clearAvatarResult()
            }
        }
    }

    /** 加载当前头像 */
    private fun loadAvatar() {
        val avatarUrl = AccountManager.avatarUrl
        if (!avatarUrl.isNullOrBlank()) {
            Glide.with(this)
                .load(avatarUrl)
                .placeholder(com.example.myapplication.R.mipmap.ic_launcher_round)
                .into(binding.ivAvatar)
        }
    }

    /** 打开图片选择器 */
    private fun pickImage() {
        try {
            pickImageLauncher.launch("image/*")
        } catch (e: Exception) {
            showToast("无法打开相册: ${e.message}")
        }
    }

    /** 上传头像 */
    private fun uploadAvatar(uri: Uri) {
        try {
            val bytes = readBytesFromUri(uri)
            if (bytes.isEmpty()) {
                showToast("无法读取图片，请重试")
                return
            }
            viewModel.uploadAvatar(bytes)
        } catch (e: Exception) {
            showToast("读取图片失败: ${e.message}")
        }
    }

    /** 从 Uri 读取字节数组 */
    private fun readBytesFromUri(uri: Uri): ByteArray {
        return contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
    }

    /**
     * 保存修改
     */
    private fun saveProfile() {
        // 收集输入值
        val inputNickname = binding.etNickname.text?.toString()?.trim()
        val inputSignature = binding.etSignature.text?.toString()?.trim()

        val originalProfile = AccountManager.profile
        val originalNickname = originalProfile?.nickname
        val originalSignature = originalProfile?.signature

        // 比较是否有变化
        val nickChanged = inputNickname != originalNickname
        val signChanged = inputSignature != originalSignature
        val genderChanged = gender != null && gender != originalProfile?.gender
        val birthdayChanged = birthday != null && birthday != originalProfile?.birthday

        if (!nickChanged && !signChanged && !genderChanged && !birthdayChanged) {
            showToast("未做任何修改")
            return
        }

        viewModel.saveProfile(
            nickname = if (nickChanged) inputNickname else null,
            signature = if (signChanged) inputSignature else null,
            gender = if (genderChanged) gender else null,
            birthday = if (birthdayChanged) birthday else null
        )
    }

    /**
     * 性别选择弹窗
     */
    private fun showGenderDialog() {
        val items = arrayOf("保密", "男", "女")
        android.app.AlertDialog.Builder(this)
            .setTitle("选择性别")
            .setItems(items) { _, which ->
                gender = which // 0=保密, 1=男, 2=女
                binding.tvGender.text = items[which]
            }
            .show()
    }

    /**
     * 生日选择弹窗
     */
    private fun showBirthdayDialog() {
        val calendar = Calendar.getInstance()
        if (selectedBirthday > 0) {
            calendar.timeInMillis = selectedBirthday
        } else {
            // 默认显示 1998年1月1日
            calendar.set(1998, 0, 1)
        }

        DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                calendar.set(year, month, dayOfMonth)
                selectedBirthday = calendar.timeInMillis
                birthday = selectedBirthday
                binding.tvBirthday.text = dateFormat.format(calendar.time)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).apply {
            // 限制最大日期为今天
            datePicker.maxDate = System.currentTimeMillis()
            // 限制最小日期为 1900年
            calendar.set(1900, 0, 1)
            datePicker.minDate = calendar.timeInMillis
        }.show()
    }
}
