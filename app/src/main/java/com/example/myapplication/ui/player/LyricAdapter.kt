package com.example.myapplication.ui.player

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.databinding.ItemLyricLineBinding

/**
 * 歌词列表 RecyclerView 适配器
 *
 * 每条数据为 LyricLine，包含文本、时间戳、是否高亮、是否为翻译行等信息。
 *
 * 高亮策略：
 * - 当前播放行使用纯白 + 18sp + 加粗，视觉上突出
 * - 非高亮普通行使用 99FFFFFF + 15sp
 * - 翻译行使用 66FFFFFF + 12sp，更低对比度
 *
 * 为什么用 RecyclerView 而不是 ScrollView + SpannableString？
 * - 每条歌词独立 ViewHolder，状态切换清晰：高亮/非高亮只需 notifyItemChanged
 * - 居中滚动更精确：通过 scrollToPositionWithOffset 可精确控制高亮行位置
 * - 性能更好：列表很长时 RecyclerView 会回收 View，不会一次性创建所有行
 * - 翻译歌词作为独立行显示在原文下方，用小号字体区分
 */
class LyricAdapter : RecyclerView.Adapter<LyricAdapter.LyricViewHolder>() {

    /** 歌词行数据 */
    private val lines = mutableListOf<LyricLine>()

    /** 当前高亮行索引（-1 表示无高亮） */
    private var highlightedIndex = -1

    /**
     * 歌词行数据模型
     * @param text 歌词文本
     * @param timestampMs 时间戳（毫秒），用于播放进度匹配
     * @param isTranslation 是否为翻译行（翻译行用小号字体）
     */
    data class LyricLine(
        val text: String,
        val timestampMs: Long,
        val isTranslation: Boolean = false
    )

    /**
     * 设置歌词数据
     * 将原始歌词和翻译歌词按时间戳合并排序
     */
    fun setData(lyricEntries: List<Pair<Long, String>>, tlyricEntries: List<Pair<Long, String>>) {
        lines.clear()
        highlightedIndex = -1

        // 将翻译歌词按时间戳建立索引，方便 O(1) 查找
        val tlyricMap = tlyricEntries.associateBy { it.first }

        for ((timestamp, text) in lyricEntries) {
            // 先添加原歌词行
            lines.add(LyricLine(text, timestamp, isTranslation = false))
            // 如果同一时间戳有翻译，紧接其后添加翻译行
            tlyricMap[timestamp]?.let { (_, tText) ->
                lines.add(LyricLine(tText, timestamp, isTranslation = true))
            }
        }
        notifyDataSetChanged()
    }

    /**
     * 更新高亮行索引
     * @param newIndex 新高亮的歌词行位置
     */
    fun setHighlightedIndex(newIndex: Int) {
        if (newIndex == highlightedIndex) return
        if (newIndex < 0 || newIndex >= lines.size) return

        val oldIndex = highlightedIndex
        highlightedIndex = newIndex

        // 取消旧行高亮
        if (oldIndex >= 0 && oldIndex < lines.size) {
            notifyItemChanged(oldIndex)
        }
        // 点亮新行
        notifyItemChanged(newIndex)
    }

    /** 获取行数 */
    val lineCount: Int get() = lines.size

    /** 获取指定位置的数据 */
    fun getItem(position: Int): LyricLine? =
        lines.getOrNull(position)

    /**
     * 根据播放进度查找对应的歌词行索引
     *
     * 策略：遍历所有非翻译行，找到时间戳 <= currentMs 的最后一行。
     * 翻译行跟随其前面的原歌词行，不参与时间匹配。
     *
     * @param currentMs 当前播放进度（毫秒）
     * @return 当前时间对应的歌词行在 adapter 中的 position，-1 表示未找到
     */
    fun findIndexByTime(currentMs: Long): Int {
        if (lines.isEmpty()) return -1
        var result = -1
        for (i in lines.indices) {
            // 翻译行不参与时间戳比较
            if (lines[i].isTranslation) continue
            if (lines[i].timestampMs <= currentMs) {
                result = i
            } else {
                break // 列表按时间戳排序，后面的时间戳更大，无需继续
            }
        }
        return result
    }

    override fun getItemCount(): Int = lines.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LyricViewHolder {
        val binding = ItemLyricLineBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return LyricViewHolder(binding)
    }

    override fun onBindViewHolder(holder: LyricViewHolder, position: Int) {
        val line = lines[position]
        val isHighlighted = (position == highlightedIndex)
        holder.bind(line, isHighlighted)
    }

    inner class LyricViewHolder(
        private val binding: ItemLyricLineBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(line: LyricLine, isHighlighted: Boolean) {
            binding.tvLyricText.apply {
                text = line.text

                when {
                    isHighlighted -> {
                        // 当前播放行：纯白色、加粗、大号字
                        textSize = 18f
                        setTextColor(android.graphics.Color.WHITE)
                        setTypeface(Typeface.DEFAULT_BOLD)
                        alpha = 1.0f
                    }
                    line.isTranslation -> {
                        // 翻译行：更小的字号、更低的对比度
                        textSize = 12f
                        setTextColor(0x66FFFFFF.toInt())
                        setTypeface(Typeface.DEFAULT)
                        alpha = 1.0f
                    }
                    else -> {
                        // 普通未高亮歌词行
                        textSize = 15f
                        setTextColor(0x99FFFFFF.toInt())
                        setTypeface(Typeface.DEFAULT)
                        alpha = 1.0f
                    }
                }
            }
        }
    }
}
