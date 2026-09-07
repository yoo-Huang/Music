package com.example.myapplication.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.data.remote.BannerItem
import com.example.myapplication.databinding.ItemBannerImageBinding
import com.example.myapplication.util.loadImageNoCorner

/**
 * Banner ViewPager2 适配器
 * 支持无限循环滚动（使用 Int.MAX_VALUE 作为 count）
 */
class BannerPagerAdapter(
    private val banners: List<BannerItem>,
    private val onBannerClick: ((BannerItem) -> Unit)? = null
) : RecyclerView.Adapter<BannerPagerAdapter.BannerViewHolder>() {

    /** 实际 banner 数量 */
    private val realCount: Int get() = banners.size

    /** 返回 Int.MAX_VALUE 实现无限循环 */
    override fun getItemCount(): Int = if (realCount > 0) Int.MAX_VALUE else 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BannerViewHolder {
        val binding = ItemBannerImageBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        // 确保 ImageView 填满 ViewPager2 的每个页面
        binding.root.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        return BannerViewHolder(binding)
    }

    override fun onBindViewHolder(holder: BannerViewHolder, position: Int) {
        val realPosition = position % realCount
        val banner = banners[realPosition]
        holder.binding.ivBannerImage.loadImageNoCorner(banner.pic ?: banner.imageUrl)
        holder.binding.root.setOnClickListener {
            onBannerClick?.invoke(banner)
        }
    }

    class BannerViewHolder(
        val binding: ItemBannerImageBinding
    ) : RecyclerView.ViewHolder(binding.root)
}
