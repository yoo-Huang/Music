package com.example.myapplication.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding

/**
 * 多类型 RecyclerView Adapter 基类
 * 使用 Kotlin 泛型 + ViewBinding 实现类型安全的多条目适配器
 *
 * 设计思路：
 * - 通过 Delegate 模式，每种 ViewType 对应一个 Delegate
 * - Delegate 负责判断类型、创建 ViewHolder、绑定数据
 * - ListAdapter + DiffUtil 自动处理增量更新，提升性能
 *
 * @param T 数据类型（通常是一个密封类，包含多种子类型）
 * @param VB ViewBinding 类型
 */
open class MultiTypeAdapter<T : Any>(
    diffCallback: DiffUtil.ItemCallback<T>
) : ListAdapter<T, MultiTypeAdapter.ViewHolder<T, *>>(diffCallback) {

    /** 存储所有类型委托 */
    private val delegates = mutableListOf<AdapterDelegate<T, *>>()

    /**
     * 注册类型委托
     */
    fun addDelegate(delegate: AdapterDelegate<T, *>): MultiTypeAdapter<T> {
        delegates.add(delegate)
        return this
    }

    override fun getItemViewType(position: Int): Int {
        val item = getItem(position)
        return delegates.indexOfFirst { it.isForViewType(item) }
            .also { if (it == -1) throw IllegalStateException("No delegate for item: $item") }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder<T, *> {
        val delegate = delegates[viewType]
        return delegate.onCreateViewHolder(parent)
    }

    override fun onBindViewHolder(holder: ViewHolder<T, *>, position: Int) {
        @Suppress("UNCHECKED_CAST")
        (holder as ViewHolder<T, Any>).bind(getItem(position))
    }

    override fun onViewRecycled(holder: ViewHolder<T, *>) {
        val viewType = holder.itemViewType
        if (viewType in delegates.indices) {
            @Suppress("UNCHECKED_CAST")
            (delegates[viewType] as AdapterDelegate<T, Any>).onViewRecycled(
                holder as ViewHolder<T, Any>
            )
        }
    }

    /**
     * ViewHolder 基类
     */
    abstract class ViewHolder<T, VB : ViewBinding>(
        val binding: VB
    ) : RecyclerView.ViewHolder(binding.root) {
        abstract fun bind(item: T)
    }
}

/**
 * 类型委托接口
 * 每种列表条目类型对应一个实现类
 *
 * @param T 数据类型
 * @param VB ViewBinding 类型
 */
interface AdapterDelegate<T, VB : ViewBinding> {

    /** 判断是否处理该类型数据 */
    fun isForViewType(item: T): Boolean

    /** 创建 ViewHolder */
    fun onCreateViewHolder(parent: ViewGroup): MultiTypeAdapter.ViewHolder<T, VB>

    /** 绑定数据到 ViewHolder */
    fun bindViewHolder(holder: MultiTypeAdapter.ViewHolder<T, VB>, item: T)

    /** ViewHolder 回收时的回调（默认空实现） */
    fun onViewRecycled(holder: MultiTypeAdapter.ViewHolder<T, VB>) {}
}

/**
 * 类型委托基类（简化实现）
 */
abstract class BaseAdapterDelegate<T, VB : ViewBinding>(
    private val viewBindingInflater: (LayoutInflater, ViewGroup, Boolean) -> VB
) : AdapterDelegate<T, VB> {

    override fun onCreateViewHolder(parent: ViewGroup): MultiTypeAdapter.ViewHolder<T, VB> {
        val inflater = LayoutInflater.from(parent.context)
        val binding = viewBindingInflater(inflater, parent, false)
        return object : MultiTypeAdapter.ViewHolder<T, VB>(binding) {
            override fun bind(item: T) {
                bindViewHolder(this, item)
            }
        }
    }
}
