package com.example.myapplication.util

import android.graphics.Bitmap
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation
import java.security.MessageDigest

/**
 * Glide 模糊变换 - 通过缩小再放大实现近似高斯模糊效果
 *
 * @param radius 模糊强度，值越大越模糊，默认 20
 */
class BlurTransformation(private val radius: Int = 20) : BitmapTransformation() {

    override fun transform(
        pool: BitmapPool,
        toTransform: Bitmap,
        outWidth: Int,
        outHeight: Int
    ): Bitmap {
        val width = toTransform.width
        val height = toTransform.height

        val scaledWidth = (width / radius).coerceAtLeast(1)
        val scaledHeight = (height / radius).coerceAtLeast(1)

        // 缩小
        val scaled = Bitmap.createScaledBitmap(toTransform, scaledWidth, scaledHeight, false)
        // 放大回原尺寸（产生模糊效果）
        return Bitmap.createScaledBitmap(scaled, width, height, false)
    }

    override fun updateDiskCacheKey(messageDigest: MessageDigest) {
        messageDigest.update("blur_transformation_$radius".toByteArray())
    }
}
