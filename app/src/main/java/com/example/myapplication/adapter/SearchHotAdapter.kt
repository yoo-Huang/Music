package com.example.myapplication.adapter

import android.graphics.Color
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.R
import com.example.myapplication.data.remote.SearchHotItem

/**
 * 热搜榜 RecyclerView Adapter
 * 每条显示排名序号、搜索关键词、热度值
 * 前 3 名高亮红色显示排名
 */
class SearchHotAdapter(
    private var items: List<SearchHotItem> = emptyList(),
    private val onItemClick: (String) -> Unit
) : RecyclerView.Adapter<SearchHotAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvRank: TextView = view.findViewById(R.id.tv_rank)
        val tvKeyword: TextView = view.findViewById(R.id.tv_keyword)
        val tvHeat: TextView = view.findViewById(R.id.tv_heat)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_search_hot, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val rank = position + 1
        val keyword = item.searchWord ?: ""

        // 排名序号，前 3 名红色高亮
        holder.tvRank.text = rank.toString()
        val isTop3 = rank <= 3
        holder.tvRank.setTextColor(
            if (isTop3) Color.parseColor("#EC4141") else Color.parseColor("#999999")
        )
        // 前 3 名加粗
        holder.tvRank.paint.isFakeBoldText = isTop3

        // 搜索关键词
        holder.tvKeyword.text = keyword
        holder.tvKeyword.maxLines = 1
        holder.tvKeyword.ellipsize = TextUtils.TruncateAt.END

        // 热度值，格式化为易读数字
        holder.tvHeat.text = formatHeat(item.score)

        // 点击整行跳转搜索
        holder.itemView.setOnClickListener {
            onItemClick(keyword)
        }
    }

    override fun getItemCount(): Int = items.size

    /** 更新数据并刷新 UI */
    fun updateData(newItems: List<SearchHotItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    /**
     * 将热度数字格式化为可读字符串
     * > 1 亿 → "1.2亿"
     * > 1 万 → "12.3万"
     * 否则 → "8,888"
     */
    private fun formatHeat(num: Int): String {
        if (num <= 0) return ""
        return when {
            num >= 100_000_000 -> String.format("%.1f亿", num / 100_000_000.0)
            num >= 10_000 -> String.format("%.1f万", num / 10_000.0)
            else -> String.format("%,d", num)
        }
    }
}
