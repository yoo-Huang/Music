package com.example.myapplication.util

import android.content.Context
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions
import com.example.myapplication.R

// ==================== Kotlin 扩展函数 ====================

/**
 * Context 扩展：显示短 Toast
 */
fun Context.showToast(message: String) {
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}

/**
 * View 扩展：设置可见性
 */
fun View.visible() {
    visibility = View.VISIBLE
}

fun View.gone() {
    visibility = View.GONE
}

fun View.invisible() {
    visibility = View.INVISIBLE
}

/**
 * ImageView 扩展：使用 Glide 加载图片
 *
 * 优化要点：
 * - 使用自定义 ic_default_album 作为占位图和错误图，风格统一
 * - DiskCacheStrategy.ALL 缓存原始图和变换后的图，下次加载更快
 * - override(400, 400) 限制解码尺寸，减少内存占用（列表项封面较小）
 * - dontAnimate() 避免列表滚动时动画闪烁
 *
 * @param url 图片 URL
 * @param placeholder 占位图资源 ID，默认使用 ic_default_album
 * @param cornerRadius 圆角半径（dp），默认 8dp
 */
fun ImageView.loadImage(
    url: String?,
    placeholder: Int = R.drawable.ic_default_album,
    cornerRadius: Int = 8
) {
    Glide.with(context)
        .load(url)
        .placeholder(placeholder)
        .error(placeholder)
        .fallback(placeholder) // URL 为 null 或空时显示
        .diskCacheStrategy(DiskCacheStrategy.ALL)
        .dontAnimate()
        .override(400, 400)
        .apply(
            RequestOptions.bitmapTransform(
                RoundedCorners(dp2px(context, cornerRadius))
            )
        )
        .into(this)
}

/**
 * 图片加载（不带圆角）- 用于 Banner 等需要完整尺寸的场景
 */
fun ImageView.loadImageNoCorner(
    url: String?,
    placeholder: Int = R.drawable.ic_default_album
) {
    Glide.with(context)
        .load(url)
        .placeholder(placeholder)
        .error(placeholder)
        .fallback(placeholder) // URL 为 null 或空时显示
        .diskCacheStrategy(DiskCacheStrategy.ALL)
        .dontAnimate()
        .into(this)
}

/**
 * 图片加载（模糊背景）- 用于头像背景等需要模糊效果的场景
 * 加载原图并通过 BlurTransformation 缩小再放大实现近似高斯模糊
 */
fun ImageView.loadImageBlurred(
    url: String?,
    blurRadius: Int = 25,
    placeholder: Int = R.drawable.ic_default_album
) {
    Glide.with(context)
        .load(url)
        .placeholder(placeholder)
        .error(placeholder)
        .fallback(placeholder)
        .diskCacheStrategy(DiskCacheStrategy.ALL)
        .dontAnimate()
        .override(800, 800)
        .apply(RequestOptions.bitmapTransform(BlurTransformation(blurRadius)))
        .into(this)
}

/**
 * 图片加载（圆形裁剪）- 用于播放页唱片封面
 * 使用 Glide 的 CircleCrop 确保封面是完美正圆，不会被拉伸变形
 * CardView 的 cardCornerRadius 作为兜底裁剪
 */
fun ImageView.loadImageCircle(
    url: String?,
    placeholder: Int = R.drawable.ic_default_album
) {
    Glide.with(context)
        .load(url)
        .placeholder(placeholder)
        .error(placeholder)
        .fallback(placeholder)
        .diskCacheStrategy(DiskCacheStrategy.ALL)
        .apply(RequestOptions.bitmapTransform(CircleCrop()))
        .into(this)
}

/**
 * dp 转 px
 */
fun dp2px(context: Context, dp: Int): Int {
    return (dp * context.resources.displayMetrics.density).toInt()
}

/**
 * 将秒数格式化为 mm:ss 格式
 */
fun Int.formatDuration(): String {
    val minutes = this / 60
    val seconds = this % 60
    return "%02d:%02d".format(minutes, seconds)
}

/**
 * 数字格式化（万为单位）
 * 例如：123456 -> "12.3万"
 */
fun Long.formatCount(): String {
    return when {
        this >= 100_000_000 -> "${this / 100_000_000}.${(this % 100_000_000) / 10_000_000}亿"
        this >= 10_000 -> "${this / 10_000}.${(this % 10_000) / 1_000}万"
        else -> this.toString()
    }
}
