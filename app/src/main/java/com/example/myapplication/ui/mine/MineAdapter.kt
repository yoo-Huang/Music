package com.example.myapplication.ui.mine

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import com.example.myapplication.R
import com.example.myapplication.adapter.BaseAdapterDelegate
import com.example.myapplication.adapter.MultiTypeAdapter
import com.example.myapplication.data.remote.LoginUserProfile
import com.example.myapplication.data.remote.UserPlaylistItem
import com.example.myapplication.databinding.ItemMineDividerBinding
import com.example.myapplication.databinding.ItemMineGuestHeaderBinding
import com.example.myapplication.databinding.ItemMineHeaderBinding
import com.example.myapplication.databinding.ItemMineMenuBinding
import com.example.myapplication.databinding.ItemMineMenuGroupBinding
import com.example.myapplication.databinding.ItemMinePlaylistVerticalBinding
import com.example.myapplication.databinding.ItemMineSectionTitleBinding
import com.example.myapplication.data.remote.RecommendPlaylist
import com.example.myapplication.manager.StatusItem
import com.example.myapplication.manager.UserBadge
import com.example.myapplication.util.formatCount
import com.example.myapplication.util.loadImage
import com.example.myapplication.util.loadImageCircle

/**
 * "我的"页面多类型适配器
 *
 * 条目类型：
 * 1. Header       - 用户头部（头像、昵称、等级、关注/粉丝）
 * 2. SectionTitle - 分区标题
 * 3. PlaylistItem - 歌单列表项（纵向，封面+歌名+副标题+心动按钮）
 * 4. MenuGroup    - 功能菜单分组
 * 5. Divider      - 分割线
 */
class MineAdapter : MultiTypeAdapter<MineAdapter.MineItem>(MineItemDiffCallback()) {

    /** 头像/昵称点击 */
    var onHeaderClick: (() -> Unit)? = null

    /** 歌单点击 */
    var onPlaylistClick: ((UserPlaylistItem) -> Unit)? = null

    /** 菜单项点击 */
    var onMenuClick: ((MineMenuItem) -> Unit)? = null

    /** 心动按钮点击（针对"我喜欢的音乐"歌单） */
    var onHeartbeatClick: ((UserPlaylistItem) -> Unit)? = null

    /** 设置图标点击 */
    var onSettingsClick: (() -> Unit)? = null

    /** 关注列表点击 */
    var onFollowsClick: (() -> Unit)? = null

    /** 粉丝列表点击 */
    var onFollowedsClick: (() -> Unit)? = null

    /** 登录按钮点击（未登录状态） */
    var onLoginClick: (() -> Unit)? = null

    /** 未登录推荐歌单点击 */
    var onGuestPlaylistClick: ((RecommendPlaylist) -> Unit)? = null

    /** 状态区域点击 */
    var onStatusClick: (() -> Unit)? = null

    /** 徽章点击 */
    var onBadgeClick: (() -> Unit)? = null

    init {
        addDelegate(HeaderDelegate(
            onHeaderClick = { onHeaderClick?.invoke() },
            onSettingsClick = { onSettingsClick?.invoke() },
            onFollowsClick = { onFollowsClick?.invoke() },
            onFollowedsClick = { onFollowedsClick?.invoke() },
            onStatusClick = { onStatusClick?.invoke() },
            onBadgeClick = { onBadgeClick?.invoke() }
        ))
        addDelegate(GuestHeaderDelegate(
            onLoginClick = { onLoginClick?.invoke() },
            onSettingsClick = { onSettingsClick?.invoke() }
        ))
        addDelegate(GuestPlaylistDelegate { onGuestPlaylistClick?.invoke(it) })
        addDelegate(SectionTitleDelegate())
        addDelegate(PlaylistItemDelegate(
            onPlaylistClick = { onPlaylistClick?.invoke(it) },
            onHeartbeatClick = { onHeartbeatClick?.invoke(it) }
        ))
        addDelegate(MenuGroupDelegate { onMenuClick?.invoke(it) })
        addDelegate(DividerDelegate())
    }

    // ==================== 密封类：数据类型 ====================

    sealed class MineItem {
        /** 用户头部信息 */
        data class Header(
            val profile: LoginUserProfile?,
            val level: Int = 0,
            val listenSongs: Long = 0,
            val status: StatusItem? = null,
            val badges: List<UserBadge> = emptyList()
        ) : MineItem()

        /** 未登录引导头部 */
        object GuestHeader : MineItem()

        /** 未登录推荐歌单项（使用 RecommendPlaylist） */
        data class GuestPlaylistItem(val playlist: RecommendPlaylist) : MineItem()

        /** 分区标题 */
        data class SectionTitle(
            val title: String,
            val count: Int = 0,
            val showArrow: Boolean = true
        ) : MineItem()

        /** 歌单列表项（纵向） */
        data class PlaylistItem(
            val playlist: UserPlaylistItem,
            val isLikedMusic: Boolean = false
        ) : MineItem()

        /** 功能菜单分组 */
        data class MenuGroup(val items: List<MineMenuItem>) : MineItem()

        /** 分割线 */
        object Divider : MineItem()
    }

    // ==================== 辅助数据类 ====================

    /** 菜单项 */
    data class MineMenuItem(
        val name: String,
        val iconRes: Int,
        val subtitle: String? = null
    )

    // ==================== DiffCallback ====================

    private class MineItemDiffCallback : DiffUtil.ItemCallback<MineItem>() {
        override fun areItemsTheSame(oldItem: MineItem, newItem: MineItem): Boolean {
            return when {
                oldItem is MineItem.Header && newItem is MineItem.Header -> true
                oldItem is MineItem.GuestHeader && newItem is MineItem.GuestHeader -> true
                oldItem is MineItem.SectionTitle && newItem is MineItem.SectionTitle ->
                    oldItem.title == newItem.title
                oldItem is MineItem.PlaylistItem && newItem is MineItem.PlaylistItem ->
                    oldItem.playlist.id == newItem.playlist.id
                oldItem is MineItem.GuestPlaylistItem && newItem is MineItem.GuestPlaylistItem ->
                    oldItem.playlist.id == newItem.playlist.id
                oldItem is MineItem.MenuGroup && newItem is MineItem.MenuGroup -> true
                oldItem is MineItem.Divider && newItem is MineItem.Divider -> true
                else -> false
            }
        }

        override fun areContentsTheSame(oldItem: MineItem, newItem: MineItem): Boolean {
            return oldItem == newItem
        }
    }

    // ==================== Delegate 实现 ====================

    /**
     * 用户头部委托（右上角设置图标）
     */
    class HeaderDelegate(
        private val onHeaderClick: () -> Unit,
        private val onSettingsClick: () -> Unit,
        private val onFollowsClick: () -> Unit,
        private val onFollowedsClick: () -> Unit,
        private val onStatusClick: () -> Unit,
        private val onBadgeClick: () -> Unit
    ) : BaseAdapterDelegate<MineItem, ItemMineHeaderBinding>(
        ItemMineHeaderBinding::inflate
    ) {
        override fun isForViewType(item: MineItem) = item is MineItem.Header

        override fun bindViewHolder(
            holder: MultiTypeAdapter.ViewHolder<MineItem, ItemMineHeaderBinding>,
            item: MineItem
        ) {
            val header = item as MineItem.Header
            val binding = holder.binding
            val profile = header.profile

            // 头像
            binding.ivAvatar.loadImageCircle(
                profile?.avatarUrl,
                R.drawable.ic_person
            )

            // 昵称
            binding.tvNickname.text = profile?.nickname?.takeIf { it.isNotBlank() } ?: "未登录"

            // 等级标签（昵称旁边小标签）
            if (header.level > 0) {
                binding.tvLevel.visibility = View.VISIBLE
                binding.tvLevel.text = "Lv.${header.level}"
            } else {
                binding.tvLevel.visibility = View.GONE
            }

            // VIP 标识
            binding.tvVip.visibility = View.GONE

            // ========== 用户状态 ==========
            val status = header.status
            if (status != null) {
                binding.llStatus.visibility = View.VISIBLE
                binding.tvStatusEmoji.text = status.emoji
                val customText = com.example.myapplication.manager.UserStatusManager.getCustomStatusText()
                val displayText = if (status.id == "custom" && !customText.isNullOrBlank()) {
                    customText
                } else {
                    "${status.name} · ${status.subtitle}"
                }
                binding.tvStatusText.text = displayText
                binding.llStatus.setOnClickListener { onStatusClick() }
            } else {
                binding.llStatus.visibility = View.GONE
            }

            // ========== 用户徽章（精简为入口链接） ==========
            val badges = header.badges
            if (badges.isNotEmpty()) {
                binding.tvBadgeEntry.visibility = View.VISIBLE
                binding.tvBadgeEntry.text = "🏅 徽章 (${badges.size}) ›"
                binding.tvBadgeEntry.setOnClickListener { onBadgeClick() }
            } else {
                binding.tvBadgeEntry.visibility = View.GONE
            }

            // 个性签名
            val sig = profile?.signature?.takeIf { it.isNotBlank() }
            if (sig != null) {
                binding.tvSignature.visibility = View.VISIBLE
                binding.tvSignature.text = sig
            } else {
                binding.tvSignature.visibility = View.GONE
            }

            // 关注 / 粉丝
            val follows = profile?.follows ?: 0
            val followeds = profile?.followeds ?: 0
            binding.tvFollowsCount.text = follows.toString()
            binding.tvFollowedsCount.text = followeds.toString()

            // 听歌总数
            binding.tvListenCount.text = header.listenSongs.toString()

            // 关注/粉丝点击
            binding.llFollows.isClickable = true
            binding.llFollows.isFocusable = false
            binding.llFollows.setOnClickListener { onFollowsClick() }

            binding.llFolloweds.isClickable = true
            binding.llFolloweds.isFocusable = false
            binding.llFolloweds.setOnClickListener { onFollowedsClick() }

            // 设置图标点击
            binding.ivSettings.isClickable = true
            binding.ivSettings.isFocusable = false
            binding.ivSettings.setOnClickListener { onSettingsClick() }

            // 点击事件（头像 + 昵称 → 编辑资料）
            binding.cvAvatar.isClickable = true
            binding.cvAvatar.isFocusable = false
            binding.cvAvatar.setOnClickListener { onHeaderClick() }
            binding.tvNickname.isClickable = true
            binding.tvNickname.isFocusable = false
            binding.tvNickname.setOnClickListener { onHeaderClick() }
        }
    }

    /**
     * 分区标题委托
     */
    class SectionTitleDelegate : BaseAdapterDelegate<MineItem, ItemMineSectionTitleBinding>(
        ItemMineSectionTitleBinding::inflate
    ) {
        override fun isForViewType(item: MineItem) = item is MineItem.SectionTitle

        override fun bindViewHolder(
            holder: MultiTypeAdapter.ViewHolder<MineItem, ItemMineSectionTitleBinding>,
            item: MineItem
        ) {
            val section = item as MineItem.SectionTitle
            holder.binding.apply {
                tvSectionTitle.text = section.title
                if (section.count > 0) {
                    tvSectionCount.visibility = View.VISIBLE
                    tvSectionCount.text = "(${section.count})"
                } else {
                    tvSectionCount.visibility = View.GONE
                }
                tvSectionArrow.visibility = if (section.showArrow) View.VISIBLE else View.GONE
            }
        }
    }

    /**
     * 歌单列表项委托（纵向）
     * 封面 + 歌名 + 副标题（N首·播放N次·作者） + 心动按钮（仅"我喜欢的音乐"）
     */
    class PlaylistItemDelegate(
        private val onPlaylistClick: (UserPlaylistItem) -> Unit,
        private val onHeartbeatClick: (UserPlaylistItem) -> Unit
    ) : BaseAdapterDelegate<MineItem, ItemMinePlaylistVerticalBinding>(
        ItemMinePlaylistVerticalBinding::inflate
    ) {
        override fun isForViewType(item: MineItem) = item is MineItem.PlaylistItem

        override fun bindViewHolder(
            holder: MultiTypeAdapter.ViewHolder<MineItem, ItemMinePlaylistVerticalBinding>,
            item: MineItem
        ) {
            val playlistItem = item as MineItem.PlaylistItem
            val playlist = playlistItem.playlist
            val binding = holder.binding

            // 封面
            binding.ivCover.loadImage(playlist.coverImgUrl)

            // 歌单名
            binding.tvName.text = playlist.name ?: "未命名歌单"

            // 副标题：N首·播放N次·作者名
            val trackText = "${playlist.trackCount}首"
            val playText = if (playlist.playCount > 0) "·播放${playlist.playCount.formatCount()}" else ""
            val creatorText = playlist.creator?.nickname?.let {
                if (it.isNotBlank()) "·$it" else ""
            } ?: ""
            binding.tvSubtitle.text = "$trackText$playText$creatorText"

            // 心动按钮："我喜欢的音乐"显示
            if (playlistItem.isLikedMusic) {
                binding.btnHeartbeat.visibility = View.VISIBLE
                binding.btnHeartbeat.isClickable = true
                binding.btnHeartbeat.isFocusable = false
                binding.btnHeartbeat.setOnClickListener { onHeartbeatClick(playlist) }
            } else {
                binding.btnHeartbeat.visibility = View.GONE
            }

            // 整行点击
            binding.root.isClickable = true
            binding.root.isFocusable = false
            binding.root.setOnClickListener { onPlaylistClick(playlist) }
        }
    }

    /**
     * 功能菜单分组委托
     */
    class MenuGroupDelegate(
        private val onMenuClick: (MineMenuItem) -> Unit
    ) : BaseAdapterDelegate<MineItem, ItemMineMenuGroupBinding>(
        ItemMineMenuGroupBinding::inflate
    ) {
        override fun isForViewType(item: MineItem) = item is MineItem.MenuGroup

        override fun bindViewHolder(
            holder: MultiTypeAdapter.ViewHolder<MineItem, ItemMineMenuGroupBinding>,
            item: MineItem
        ) {
            val menuItems = (item as MineItem.MenuGroup).items
            val container = holder.binding.llMenuContainer
            val context = container.context

            container.removeAllViews()

            menuItems.forEachIndexed { index, menuItem ->
                val menuView = LayoutInflater.from(context)
                    .inflate(R.layout.item_mine_menu, container, false)
                val menuBinding = ItemMineMenuBinding.bind(menuView)

                menuBinding.ivIcon.setImageResource(menuItem.iconRes)
                menuBinding.tvName.text = menuItem.name
                if (!menuItem.subtitle.isNullOrBlank()) {
                    menuBinding.tvSubtitle.visibility = View.VISIBLE
                    menuBinding.tvSubtitle.text = menuItem.subtitle
                } else {
                    menuBinding.tvSubtitle.visibility = View.GONE
                }
                menuView.isClickable = true
                menuView.isFocusable = false
                menuView.setOnClickListener { onMenuClick(menuItem) }
                container.addView(menuView)

                if (index < menuItems.size - 1) {
                    val divider = LayoutInflater.from(context)
                        .inflate(R.layout.item_mine_divider, container, false)
                    container.addView(divider)
                }
            }
        }
    }

    /**
     * 分割线委托
     */
    class DividerDelegate : BaseAdapterDelegate<MineItem, ItemMineDividerBinding>(
        ItemMineDividerBinding::inflate
    ) {
        override fun isForViewType(item: MineItem) = item is MineItem.Divider

        override fun bindViewHolder(
            holder: MultiTypeAdapter.ViewHolder<MineItem, ItemMineDividerBinding>,
            item: MineItem
        ) {
            // 无需绑定数据
        }
    }

    /**
     * 未登录引导头部委托
     */
    class GuestHeaderDelegate(
        private val onLoginClick: () -> Unit,
        private val onSettingsClick: () -> Unit
    ) : BaseAdapterDelegate<MineItem, ItemMineGuestHeaderBinding>(
        ItemMineGuestHeaderBinding::inflate
    ) {
        override fun isForViewType(item: MineItem) = item is MineItem.GuestHeader

        override fun bindViewHolder(
            holder: MultiTypeAdapter.ViewHolder<MineItem, ItemMineGuestHeaderBinding>,
            item: MineItem
        ) {
            val binding = holder.binding

            // 设置图标点击
            binding.ivSettings.isClickable = true
            binding.ivSettings.isFocusable = false
            binding.ivSettings.setOnClickListener { onSettingsClick() }

            // 头像点击 → 登录
            binding.cvAvatar.isClickable = true
            binding.cvAvatar.isFocusable = false
            binding.cvAvatar.setOnClickListener { onLoginClick() }

            // "立即登录"文字点击 → 登录
            binding.tvWelcome.isClickable = true
            binding.tvWelcome.isFocusable = false
            binding.tvWelcome.setOnClickListener { onLoginClick() }
        }
    }

    /**
     * 未登录推荐歌单项委托
     */
    class GuestPlaylistDelegate(
        private val onClick: (RecommendPlaylist) -> Unit
    ) : BaseAdapterDelegate<MineItem, ItemMinePlaylistVerticalBinding>(
        ItemMinePlaylistVerticalBinding::inflate
    ) {
        override fun isForViewType(item: MineItem) = item is MineItem.GuestPlaylistItem

        override fun bindViewHolder(
            holder: MultiTypeAdapter.ViewHolder<MineItem, ItemMinePlaylistVerticalBinding>,
            item: MineItem
        ) {
            val playlistItem = item as MineItem.GuestPlaylistItem
            val playlist = playlistItem.playlist
            val binding = holder.binding

            binding.ivCover.loadImage(playlist.picUrl)
            binding.tvName.text = playlist.name ?: "推荐歌单"
            val playText = if (playlist.playCount > 0) {
                "播放${playlist.playCount.formatCount()}"
            } else ""
            binding.tvSubtitle.text = playText
            binding.btnHeartbeat.visibility = View.GONE

            binding.root.isClickable = true
            binding.root.isFocusable = false
            binding.root.setOnClickListener { onClick(playlist) }
        }
    }
}
