package com.example.myapplication.ui.player

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.MediaController
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication.base.Result
import com.example.myapplication.data.remote.ApiService
import com.example.myapplication.databinding.ActivityMvPlayerBinding
import com.example.myapplication.util.showToast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * MV 播放页
 * 使用 VideoView + MediaController 播放 MV
 */
class MvPlayerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMvPlayerBinding

    companion object {
        const val EXTRA_MV_ID = "mv_id"
        const val EXTRA_MV_NAME = "mv_name"
        const val EXTRA_MV_ARTIST = "mv_artist"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMvPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val mvId = intent.getLongExtra(EXTRA_MV_ID, 0)
        val mvName = intent.getStringExtra(EXTRA_MV_NAME) ?: "MV"
        val mvArtist = intent.getStringExtra(EXTRA_MV_ARTIST) ?: ""

        // 标题
        binding.tvMvTitle.text = if (mvArtist.isNotEmpty()) "$mvName - $mvArtist" else mvName

        // 返回按钮
        binding.ivBack.setOnClickListener { finish() }

        // 加载 MV 播放地址
        if (mvId > 0) {
            loadMvUrl(mvId)
        } else {
            showError("无效的 MV ID")
        }
    }

    private fun loadMvUrl(mvId: Long) {
        binding.progressLoading.visibility = View.VISIBLE
        binding.tvError.visibility = View.GONE

        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (val result = ApiService.getMvUrl(mvId)) {
                    is Result.Success -> {
                        val url = result.data.data?.url
                        if (!url.isNullOrBlank()) {
                            runOnUiThread { playMv(url) }
                        } else {
                            // 720p 不可用时尝试 480p
                            val result480 = ApiService.getMvUrl(mvId, r = 480)
                            if (result480 is Result.Success && !result480.data.data?.url.isNullOrBlank()) {
                                runOnUiThread { playMv(result480.data.data!!.url!!) }
                            } else {
                                runOnUiThread { showError("无法获取MV播放地址") }
                            }
                        }
                    }
                    is Result.Error -> {
                        runOnUiThread { showError("获取MV失败: ${result.exception.message}") }
                    }
                }
            } catch (e: Exception) {
                runOnUiThread { showError("加载失败: ${e.message}") }
            }
        }
    }

    private fun playMv(url: String) {
        binding.progressLoading.visibility = View.GONE

        val videoView = binding.videoView

        // 设置 MediaController
        val mediaController = MediaController(this)
        mediaController.setAnchorView(videoView)
        videoView.setMediaController(mediaController)

        videoView.setVideoURI(Uri.parse(url))

        videoView.setOnPreparedListener { mp: MediaPlayer ->
            mp.isLooping = false
            // 自动横屏
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            videoView.start()
        }

        videoView.setOnErrorListener { _, what, extra ->
            showError("播放出错 (what=$what, extra=$extra)")
            true
        }

        videoView.setOnCompletionListener {
            // 播放完毕，恢复竖屏
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }

        videoView.requestFocus()
    }

    private fun showError(msg: String) {
        binding.progressLoading.visibility = View.GONE
        binding.tvError.visibility = View.VISIBLE
        binding.tvError.text = msg
        showToast(msg)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // 横竖屏切换时调整布局
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.videoView.stopPlayback()
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
}
