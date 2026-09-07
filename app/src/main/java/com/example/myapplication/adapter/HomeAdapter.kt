package com.example.myapplication.adapter

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.R
import com.example.myapplication.data.remote.BannerItem
import com.example.myapplication.data.remote.DjProgramItem
import com.example.myapplication.data.remote.DjRadioItem
import com.example.myapplication.data.remote.RecommendPlaylist
import com.example.myapplication.data.remote.SongInfo
import com.example.myapplication.databinding.ItemBannerCarouselBinding
import com.example.myapplication.databinding.ItemPlaylistCardBinding
import com.example.myapplication.databinding.ItemPlaylistRowBinding
import com.example.myapplication.databinding.ItemRadioProgramBinding
import com.example.myapplication.databinding.ItemSectionTitleBinding
import com.example.myapplication.databinding.ItemSongRowBinding
import com.example.myapplication.util.loadImage

/**
 * 首页列表多条目适配器
 *
 * 支持的条目类型：
 * 1. Banner 轮播区
 * 2. 分类标题（"推荐歌单"、"新歌推荐"）
 * 3. 推荐歌单卡片
 * 4. 新歌行（含收藏按钮）
 *
 * 使用密封类统一管理多种数据类型
 */
class HomeAdapter : MultiTypeAdapter<HomeAdapter.HomeItem>(HomeItemDiffCallback()) {

    /** 歌曲点击回调 */
    var onSongClick: ((SongInfo) -> Unit)? = null
    /** 歌单点击回调 */
    var onPlaylistClick: ((RecommendPlaylist) -> Unit)? = null
    /** Banner 点击回调 */
    var onBannerClick: ((BannerItem) -> Unit)? = null
    /** 收藏/取消收藏回调 */
    var onFavoriteClick: ((SongInfo) -> Unit)? = null
    /** 电台节目点击回调 */
    var onRadioClick: ((DjProgramItem) -> Unit)? = null
    /** 电台推荐（电台站）点击回调 */
    var onRadioStationClick: ((DjRadioItem) -> Unit)? = null

    init {
        // 注册七种类型的委托
        addDelegate(BannerDelegate { onBannerClick?.invoke(it) })
        addDelegate(SectionTitleDelegate())
        addDelegate(PlaylistRowDelegate { onPlaylistClick?.invoke(it) })
        addDelegate(PlaylistCardDelegate { onPlaylistClick?.invoke(it) })
        addDelegate(SongRowDelegate(
            onSongClick = { onSongClick?.invoke(it) },
            onFavoriteClick = { onFavoriteClick?.invoke(it) }
        ))
        addDelegate(RadioRowDelegate { onRadioClick?.invoke(it) })
        addDelegate(RadioStationDelegate { onRadioStationClick?.invoke(it) })
    }

    // ==================== 密封类：统一数据类型 ====================

    /**
     * 使用 Kotlin 密封类封装多种条目类型
     * 利用 when 表达式实现类型安全的模式匹配
     */
    sealed class HomeItem {
        data class BannerList(val banners: List<BannerItem>) : HomeItem()
        data class SectionTitle(val title: String) : HomeItem()
        data class PlaylistRow(val playlists: List<RecommendPlaylist>) : HomeItem()
        data class PlaylistCard(val playlist: RecommendPlaylist) : HomeItem()
        data class SongRow(val song: SongInfo) : HomeItem()
        data class RadioRow(val program: DjProgramItem) : HomeItem()
        data class RadioStationRow(val station: DjRadioItem) : HomeItem()
    }

    private class HomeItemDiffCallback : DiffUtil.ItemCallback<HomeItem>() {
        override fun areItemsTheSame(oldItem: HomeItem, newItem: HomeItem): Boolean {
            return when {
                oldItem is HomeItem.BannerList && newItem is HomeItem.BannerList -> true
                oldItem is HomeItem.SectionTitle && newItem is HomeItem.SectionTitle ->
                    oldItem.title == newItem.title
                oldItem is HomeItem.PlaylistRow && newItem is HomeItem.PlaylistRow -> true
                oldItem is HomeItem.PlaylistCard && newItem is HomeItem.PlaylistCard ->
                    oldItem.playlist.id == newItem.playlist.id
                oldItem is HomeItem.SongRow && newItem is HomeItem.SongRow ->
                    oldItem.song.id == newItem.song.id
                oldItem is HomeItem.RadioRow && newItem is HomeItem.RadioRow ->
                    oldItem.program.id == newItem.program.id
                oldItem is HomeItem.RadioStationRow && newItem is HomeItem.RadioStationRow ->
                    oldItem.station.id == newItem.station.id
                else -> false
            }
        }

        override fun areContentsTheSame(oldItem: HomeItem, newItem: HomeItem): Boolean {
            return oldItem == newItem
        }
    }

    // ==================== 各类型委托实现 ====================

    /**
     * Banner 轮播委托
     * 使用 ViewPager2 实现自动轮播 + 指示器
     */
    class BannerDelegate(
        private val onBannerClick: ((BannerItem) -> Unit)? = null
    ) : BaseAdapterDelegate<HomeItem, ItemBannerCarouselBinding>(
        ItemBannerCarouselBinding::inflate
    ) {
        private var autoScrollHandler: Handler? = null
        private var autoScrollRunnable: Runnable? = null
        private var currentPage = 0

        override fun isForViewType(item: HomeItem) = item is HomeItem.BannerList

        override fun bindViewHolder(
            holder: MultiTypeAdapter.ViewHolder<HomeItem, ItemBannerCarouselBinding>,
            item: HomeItem
        ) {
            val bannerItem = item as HomeItem.BannerList
            val banners = bannerItem.banners
            if (banners.isEmpty()) return

            val binding = holder.binding

            // 创建 BannerPagerAdapter
            val pagerAdapter = BannerPagerAdapter(banners, onBannerClick)
            binding.vpBanner.adapter = pagerAdapter

            // 设置初始位置为中间，实现无限循环
            val startPos = if (banners.size > 1) Int.MAX_VALUE / 2 - (Int.MAX_VALUE / 2 % banners.size) else 0
            binding.vpBanner.setCurrentItem(startPos, false)

            // 添加指示器圆点
            setupIndicators(binding, banners.size)
            currentPage = 0

            // 监听页面切换更新指示器
            binding.vpBanner.registerOnPageChangeCallback(object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    val realPos = position % banners.size
                    currentPage = realPos
                    updateIndicators(binding, banners.size, realPos)
                }
            })

            // 启动自动轮播
            startAutoScroll(binding, banners.size)
        }

        private fun setupIndicators(binding: ItemBannerCarouselBinding, count: Int) {
            val container = binding.indicatorContainer
            container.removeAllViews()
            if (count <= 1) return

            val dotSize = 8
            val dotMargin = 6
            for (i in 0 until count) {
                val dot = android.view.View(container.context).apply {
                    val params = android.widget.LinearLayout.LayoutParams(
                        dp2px(dotSize), dp2px(dotSize)
                    )
                    params.setMargins(dp2px(dotMargin), 0, dp2px(dotMargin), 0)
                    layoutParams = params
                    setBackgroundResource(android.R.drawable.radiobutton_off_background)
                }
                container.addView(dot)
            }
            // 第一个点亮
            updateIndicators(binding, count, 0)
        }

        private fun updateIndicators(binding: ItemBannerCarouselBinding, count: Int, selected: Int) {
            val container = binding.indicatorContainer
            for (i in 0 until container.childCount) {
                val dot = container.getChildAt(i)
                dot.setBackgroundResource(
                    if (i == selected) android.R.drawable.presence_online
                    else android.R.drawable.radiobutton_off_background
                )
            }
        }

        private fun startAutoScroll(binding: ItemBannerCarouselBinding, count: Int) {
            if (count <= 1) return
            stopAutoScroll()
            autoScrollHandler = Handler(Looper.getMainLooper())
            autoScrollRunnable = object : Runnable {
                override fun run() {
                    val nextPos = binding.vpBanner.currentItem + 1
                    binding.vpBanner.setCurrentItem(nextPos, true)
                    autoScrollHandler?.postDelayed(this, 3000)
                }
            }
            autoScrollHandler?.postDelayed(autoScrollRunnable!!, 3000)
        }

        private fun stopAutoScroll() {
            autoScrollHandler?.removeCallbacks(autoScrollRunnable ?: return)
            autoScrollHandler = null
            autoScrollRunnable = null
        }

        private fun dp2px(dp: Int): Int {
            return (dp * android.content.res.Resources.getSystem().displayMetrics.density).toInt()
        }
    }

    /**
     * 横向滚动歌单行委托
     * 将多个歌单卡片放在一个横向 RecyclerView 中，实现左右滑动
     */
    class PlaylistRowDelegate(
        private val onPlaylistClick: (RecommendPlaylist) -> Unit
    ) : BaseAdapterDelegate<HomeItem, ItemPlaylistRowBinding>(
        ItemPlaylistRowBinding::inflate
    ) {
        override fun isForViewType(item: HomeItem) = item is HomeItem.PlaylistRow

        override fun bindViewHolder(
            holder: MultiTypeAdapter.ViewHolder<HomeItem, ItemPlaylistRowBinding>,
            item: HomeItem
        ) {
            val playlists = (item as HomeItem.PlaylistRow).playlists
            val binding = holder.binding
            val context = binding.root.context

            binding.rvPlaylistRow.apply {
                layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
                adapter = PlaylistRowInnerAdapter(playlists, onPlaylistClick)
            }
        }
    }

    /**
     * 横向歌单行内部适配器（复用 item_playlist_card 布局）
     */
    private class PlaylistRowInnerAdapter(
        private val playlists: List<RecommendPlaylist>,
        private val onPlaylistClick: (RecommendPlaylist) -> Unit
    ) : RecyclerView.Adapter<PlaylistRowInnerAdapter.VH>() {

        class VH(val binding: ItemPlaylistCardBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val inflater = LayoutInflater.from(parent.context)
            val binding = ItemPlaylistCardBinding.inflate(inflater, parent, false)
            return VH(binding)
        }

        override fun getItemCount(): Int = playlists.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val playlist = playlists[position]
            holder.binding.apply {
                ivCover.loadImage(playlist.picUrl)
                // 使用 copywriter 推荐语，没有则用歌单名
                tvName.text = playlist.copywriter?.takeIf { it.isNotBlank() } ?: playlist.name
                tvName.maxLines = 1
                tvPlayCount.text = "${playlist.playCount.formatCount()}"
                root.setOnClickListener { onPlaylistClick(playlist) }
            }
        }
    }

    /**
     * 分类标题委托
     */
    class SectionTitleDelegate : BaseAdapterDelegate<HomeItem, ItemSectionTitleBinding>(
        ItemSectionTitleBinding::inflate
    ) {
        override fun isForViewType(item: HomeItem) = item is HomeItem.SectionTitle

        override fun bindViewHolder(
            holder: MultiTypeAdapter.ViewHolder<HomeItem, ItemSectionTitleBinding>,
            item: HomeItem
        ) {
            val section = item as HomeItem.SectionTitle
            holder.binding.tvSectionTitle.text = section.title
        }
    }

    /**
     * 歌单卡片委托
     */
    class PlaylistCardDelegate(
        private val onClick: (RecommendPlaylist) -> Unit
    ) : BaseAdapterDelegate<HomeItem, ItemPlaylistCardBinding>(
        ItemPlaylistCardBinding::inflate
    ) {
        override fun isForViewType(item: HomeItem) = item is HomeItem.PlaylistCard

        override fun bindViewHolder(
            holder: MultiTypeAdapter.ViewHolder<HomeItem, ItemPlaylistCardBinding>,
            item: HomeItem
        ) {
            val playlist = (item as HomeItem.PlaylistCard).playlist
            holder.binding.apply {
                ivCover.loadImage(playlist.picUrl)
                tvName.text = playlist.copywriter?.takeIf { it.isNotBlank() } ?: playlist.name
                tvName.maxLines = 1
                tvPlayCount.text = "${playlist.playCount.formatCount()}"
                root.setOnClickListener { onClick(playlist) }
            }
        }
    }

    /**
     * 歌曲行委托（含收藏按钮）
     */
    class SongRowDelegate(
        private val onSongClick: (SongInfo) -> Unit,
        private val onFavoriteClick: (SongInfo) -> Unit
    ) : BaseAdapterDelegate<HomeItem, ItemSongRowBinding>(
        ItemSongRowBinding::inflate
    ) {
        override fun isForViewType(item: HomeItem) = item is HomeItem.SongRow

        override fun bindViewHolder(
            holder: MultiTypeAdapter.ViewHolder<HomeItem, ItemSongRowBinding>,
            item: HomeItem
        ) {
            val song = (item as HomeItem.SongRow).song
            val pos = holder.layoutPosition
            holder.binding.apply {
                // 显示序号（从 adapterPosition 推断，跳过非 SongRow 的条目）
                tvIndex.text = if (pos >= 0) String.format("%02d", pos + 1) else ""
                ivAlbumCover.loadImage(song.al?.picUrl)
                tvSongName.text = song.name ?: "未知歌曲"
                tvArtistName.text = song.artistNames

                // 收藏按钮图标
                ivFavorite.setImageResource(
                    if (song.liked) R.drawable.ic_heart_filled
                    else R.drawable.ic_heart_outline
                )

                // 整行点击 → 播放
                root.setOnClickListener { onSongClick(song) }

                // 收藏按钮点击
                ivFavorite.setOnClickListener { onFavoriteClick(song) }
            }
        }

        override fun onViewRecycled(holder: MultiTypeAdapter.ViewHolder<HomeItem, ItemSongRowBinding>) {
            super.onViewRecycled(holder)
            // 清除点击事件避免泄漏
            holder.binding.ivFavorite.setOnClickListener(null)
            holder.binding.root.setOnClickListener(null)
        }
    }

    /**
     * 电台节目委托
     */
    class RadioRowDelegate(
        private val onClick: (DjProgramItem) -> Unit
    ) : BaseAdapterDelegate<HomeItem, ItemRadioProgramBinding>(
        ItemRadioProgramBinding::inflate
    ) {
        override fun isForViewType(item: HomeItem) = item is HomeItem.RadioRow

        override fun bindViewHolder(
            holder: MultiTypeAdapter.ViewHolder<HomeItem, ItemRadioProgramBinding>,
            item: HomeItem
        ) {
            val program = (item as HomeItem.RadioRow).program
            val binding = holder.binding

            binding.apply {
                ivCover.loadImage(program.coverUrl)
                tvName.text = program.name ?: "未知节目"

                // 电台名 + 主播名
                val radioName = program.radio?.name ?: ""
                val djName = program.dj?.nickname ?: ""
                tvRadioName.text = if (radioName.isNotEmpty() && djName.isNotEmpty()) {
                    "DJ $djName · $radioName"
                } else {
                    radioName.ifEmpty { djName }
                }

                // 播放次数
                val count = program.listenerCount
                tvListenCount.text = when {
                    count >= 10_000 -> "${count / 10_000}.${(count % 10_000) / 1_000}万"
                    else -> "$count"
                }

                root.setOnClickListener { onClick(program) }
            }
        }

        override fun onViewRecycled(holder: MultiTypeAdapter.ViewHolder<HomeItem, ItemRadioProgramBinding>) {
            super.onViewRecycled(holder)
            holder.binding.root.setOnClickListener(null)
        }
    }

    /**
     * 电台推荐（电台站）委托
     * DjRadioItem 是电台频道信息，复用 item_radio_program 布局展示
     */
    class RadioStationDelegate(
        private val onClick: (DjRadioItem) -> Unit
    ) : BaseAdapterDelegate<HomeItem, ItemRadioProgramBinding>(
        ItemRadioProgramBinding::inflate
    ) {
        override fun isForViewType(item: HomeItem) = item is HomeItem.RadioStationRow

        override fun bindViewHolder(
            holder: MultiTypeAdapter.ViewHolder<HomeItem, ItemRadioProgramBinding>,
            item: HomeItem
        ) {
            val station = (item as HomeItem.RadioStationRow).station
            val binding = holder.binding

            binding.apply {
                ivCover.loadImage(station.picUrl)
                tvName.text = station.name ?: "未知电台"

                // 主播名 + 推荐语
                val djName = station.dj?.nickname ?: ""
                val rcmd = station.rcmdText ?: ""
                tvRadioName.text = when {
                    djName.isNotEmpty() && rcmd.isNotEmpty() -> "DJ $djName · $rcmd"
                    djName.isNotEmpty() -> "DJ $djName"
                    rcmd.isNotEmpty() -> rcmd
                    else -> "${station.programCount}期节目"
                }

                // 订阅人数
                val count = station.subCount
                tvListenCount.text = when {
                    count >= 10_000 -> "${count / 10_000}.${(count % 10_000) / 1_000}万订阅"
                    else -> "$count 订阅"
                }

                root.setOnClickListener { onClick(station) }
            }
        }

        override fun onViewRecycled(holder: MultiTypeAdapter.ViewHolder<HomeItem, ItemRadioProgramBinding>) {
            super.onViewRecycled(holder)
            holder.binding.root.setOnClickListener(null)
        }
    }
}

/**
 * 数字格式化扩展函数（Adapter 内部使用）
 */
private fun Long.formatCount(): String = when {
    this >= 100_000_000 -> "${this / 100_000_000}.${(this % 100_000_000) / 10_000_000}亿"
    this >= 10_000 -> "${this / 10_000}.${(this % 10_000) / 1_000}万"
    else -> this.toString()
}
