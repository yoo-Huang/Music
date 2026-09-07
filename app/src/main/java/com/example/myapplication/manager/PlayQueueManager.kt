package com.example.myapplication.manager

import android.content.Context
import com.example.myapplication.data.remote.SongInfo

/**
 * 播放模式枚举
 */
enum class PlayMode(val value: Int) {
    /** 单曲循环 */
    SINGLE(0),
    /** 顺序播放 */
    SEQUENTIAL(1),
    /** 随机播放 */
    SHUFFLE(2),
    /** 心动模式 / 智能推荐 */
    HEARTBEAT(3),
    /** 私人FM */
    FM(4);

    companion object {
        fun fromValue(value: Int): PlayMode = entries.firstOrNull { it.value == value } ?: SEQUENTIAL
    }
}

/**
 * 播放队列管理器（单例）
 * 管理全局播放队列、当前播放位置、播放模式，支持上一首/下一首切换
 * 播放模式通过 SharedPreferences 持久化
 */
object PlayQueueManager {

    private const val PREFS_NAME = "play_mode_prefs"
    private const val KEY_PLAY_MODE = "play_mode"

    /** 播放队列 */
    private val _queue = mutableListOf<SongInfo>()

    /** 随机播放时的打乱顺序映射 */
    private val shuffleOrder = mutableListOf<Int>()
    private var shuffleIndex = 0

    /** 当前播放索引 */
    var currentIndex: Int = -1
        private set

    /** 当前播放模式 */
    @Volatile
    var playMode: PlayMode = PlayMode.SEQUENTIAL
        private set

    /**
     * 设置播放模式（不持久化，用于从外部指定模式如心动模式）
     */
    fun setPlayMode(mode: PlayMode) {
        playMode = mode
        if (mode == PlayMode.SHUFFLE) rebuildShuffleOrder()
        if (mode == PlayMode.FM) {
            // FM 模式清空旧队列，由 FM 专用流程管理
            _queue.clear()
            currentIndex = -1
        }
        notifyQueueChanged()
    }

    /** 队列是否为空 */
    val isEmpty: Boolean get() = _queue.isEmpty()

    /** 队列歌曲数量 */
    val size: Int get() = _queue.size

    /** 获取当前歌曲 */
    val currentSong: SongInfo?
        get() {
            val idx = getEffectiveIndex()
            return if (idx in _queue.indices) _queue[idx] else null
        }

    /** 是否有上一首 */
    val hasPrevious: Boolean get() = _queue.size > 1

    /** 是否有下一首 */
    val hasNext: Boolean get() {
        return when (playMode) {
            PlayMode.SINGLE -> _queue.isNotEmpty()
            PlayMode.SEQUENTIAL ->
                isHeartbeatSource || getEffectiveIndex() < _queue.size - 1
            PlayMode.HEARTBEAT -> true // 心动模式由 MusicService 持续获取推荐
            PlayMode.SHUFFLE -> shuffleIndex < shuffleOrder.size - 1
            PlayMode.FM -> true // FM 总是可以获取下一首（由 MusicService 调用 API 补充）
        }
    }

    /** 队列变更监听器列表 */
    private val listeners = mutableListOf<OnQueueChangeListener>()

    /** 获取当前有效索引（随机模式使用打乱后的索引） */
    private fun getEffectiveIndex(): Int {
        return when (playMode) {
            PlayMode.SHUFFLE -> if (shuffleIndex in shuffleOrder.indices) shuffleOrder[shuffleIndex] else -1
            else -> currentIndex
        }
    }

    /** 心动模式关联的歌单 ID（用于请求更多推荐） */
    var heartbeatPlaylistId: Long = 0

    /** 心动模式缓存的喜欢歌曲（智能推荐 API 不可用时的 fallback） */
    var heartbeatLikedSongs: List<SongInfo> = emptyList()

    /** 当前是否来自心动页播放（限制播放模式仅单曲/循环） */
    @Volatile
    var isHeartbeatSource: Boolean = false

    /** 心动 tab 页面是否可见（仅用于控制迷你播放栏显示/隐藏） */
    @Volatile
    var isHeartbeatPageVisible: Boolean = false

    /**
     * 从 SharedPreferences 恢复播放模式
     * 心动模式不持久化恢复：仅在喜欢歌单中显式触发，避免普通歌单也进入心动模式
     */
    fun restorePlayMode(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val modeValue = prefs.getInt(KEY_PLAY_MODE, PlayMode.SEQUENTIAL.value)
        val mode = PlayMode.fromValue(modeValue)
        playMode = if (mode == PlayMode.HEARTBEAT || mode == PlayMode.FM) PlayMode.SEQUENTIAL else mode
    }

    /**
     * 切换播放模式并持久化
     * 循环：SINGLE → SEQUENTIAL → SHUFFLE → SINGLE
     * 心动模式不在循环中，仅在心动页内部生效
     */
    fun togglePlayMode(context: Context): PlayMode {
        playMode = when (playMode) {
            PlayMode.SINGLE -> PlayMode.SEQUENTIAL
            PlayMode.SEQUENTIAL -> PlayMode.SHUFFLE
            PlayMode.SHUFFLE -> PlayMode.SINGLE
            PlayMode.HEARTBEAT -> PlayMode.SINGLE
            PlayMode.FM -> PlayMode.SINGLE
        }
        // 持久化
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_PLAY_MODE, playMode.value)
            .apply()

        // 切换到随机模式时重新打乱
        if (playMode == PlayMode.SHUFFLE) {
            rebuildShuffleOrder()
        }

        notifyQueueChanged()
        return playMode
    }

    /**
     * 心动来源播放模式切换（仅在 SINGLE 和 SEQUENTIAL 之间循环）
     * 心动页播放时不提供随机/心动/FM 模式
     */
    fun toggleHeartbeatMode(context: Context): PlayMode {
        playMode = when (playMode) {
            PlayMode.SINGLE -> PlayMode.SEQUENTIAL
            else -> PlayMode.SINGLE // SEQUENTIAL / SHUFFLE / HEARTBEAT / FM → SINGLE
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_PLAY_MODE, playMode.value)
            .apply()
        notifyQueueChanged()
        return playMode
    }

    /**
     * 重建随机播放顺序
     */
    private fun rebuildShuffleOrder() {
        shuffleOrder.clear()
        if (_queue.isEmpty()) return
        val indices = (0 until _queue.size).toMutableList()
        indices.shuffle()
        // 确保当前播放的歌曲在随机列表首位
        if (currentIndex in indices.indices) {
            indices.remove(currentIndex)
            shuffleOrder.add(currentIndex)
            shuffleOrder.addAll(indices)
        } else {
            shuffleOrder.addAll(indices)
        }
        shuffleIndex = 0
    }

    /**
     * 设置播放队列并开始播放第一首
     */
    fun setQueue(songs: List<SongInfo>, startIndex: Int = 0) {
        _queue.clear()
        _queue.addAll(songs)
        currentIndex = startIndex.coerceIn(0, _queue.size - 1)
        isHeartbeatSource = false // 新队列默认非心动来源
        // 退出心动/FM 模式，恢复为顺序播放
        if (playMode == PlayMode.HEARTBEAT || playMode == PlayMode.FM) {
            playMode = PlayMode.SEQUENTIAL
        }
        if (playMode == PlayMode.SHUFFLE) {
            rebuildShuffleOrder()
        }
        notifyQueueChanged()
    }

    /**
     * 添加到队列末尾
     */
    fun addToQueue(song: SongInfo) {
        _queue.add(song)
        if (currentIndex == -1) currentIndex = 0
        if (playMode == PlayMode.SHUFFLE) rebuildShuffleOrder()
    }

    /**
     * 添加多首到队列
     */
    fun addAllToQueue(songs: List<SongInfo>) {
        _queue.addAll(songs)
        if (currentIndex == -1 && _queue.isNotEmpty()) currentIndex = 0
        if (playMode == PlayMode.SHUFFLE) rebuildShuffleOrder()
    }

    /**
     * 播放指定歌曲
     */
    fun playSong(song: SongInfo) {
        val existingIndex = _queue.indexOfFirst { it.id == song.id }
        if (existingIndex >= 0) {
            currentIndex = existingIndex
        } else {
            _queue.add(currentIndex + 1, song)
            currentIndex++
        }
        if (playMode == PlayMode.SHUFFLE) rebuildShuffleOrder()
        notifyQueueChanged()
    }

    /**
     * 获取上一首
     */
    fun previous(): SongInfo? {
        if (_queue.isEmpty()) return null

        when (playMode) {
            PlayMode.SINGLE, PlayMode.FM -> {
                // 单曲循环 / FM：保持当前歌曲
            }
            PlayMode.SEQUENTIAL, PlayMode.HEARTBEAT -> {
                currentIndex = if (currentIndex > 0) currentIndex - 1 else _queue.size - 1
            }
            PlayMode.SHUFFLE -> {
                shuffleIndex = if (shuffleIndex > 0) shuffleIndex - 1 else shuffleOrder.size - 1
            }
        }
        notifyQueueChanged()
        return currentSong
    }

    /**
     * 获取下一首
     */
    fun next(): SongInfo? {
        if (_queue.isEmpty()) return null

        when (playMode) {
            PlayMode.SINGLE -> {
                // 单曲循环：保持当前歌曲不变
            }
            PlayMode.HEARTBEAT -> {
                if (currentIndex < _queue.size - 1) {
                    currentIndex++
                } else {
                    return null  // 队列播完，交给 MusicService.fetchHeartbeatSongs()
                }
            }
            PlayMode.SEQUENTIAL, PlayMode.FM -> {
                if (currentIndex < _queue.size - 1) {
                    currentIndex++
                } else if (isHeartbeatSource) {
                    // 心动来源下循环回到第一首
                    currentIndex = 0
                } else {
                    return null  // 队列播完，交给 MusicService 获取更多（FM）
                }
            }
            PlayMode.SHUFFLE -> {
                if (shuffleIndex < shuffleOrder.size - 1) {
                    shuffleIndex++
                } else {
                    // 随机列表播完，重新打乱
                    rebuildShuffleOrder()
                }
            }
        }
        notifyQueueChanged()
        return currentSong
    }

    /**
     * 清空队列
     */
    fun clear() {
        _queue.clear()
        currentIndex = -1
        shuffleOrder.clear()
        shuffleIndex = 0
        notifyQueueChanged()
    }

    /**
     * 获取队列列表
     */
    fun getQueue(): List<SongInfo> = _queue.toList()

    fun addOnQueueChangeListener(listener: OnQueueChangeListener) {
        listeners.add(listener)
    }

    fun removeOnQueueChangeListener(listener: OnQueueChangeListener) {
        listeners.remove(listener)
    }

    private fun notifyQueueChanged() {
        listeners.forEach { it.onQueueChanged() }
    }

    interface OnQueueChangeListener {
        fun onQueueChanged()
    }
}
