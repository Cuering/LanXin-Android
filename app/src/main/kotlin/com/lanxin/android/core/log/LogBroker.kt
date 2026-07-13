package com.lanxin.android.core.log

import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 日志代理：缓存最近日志 + Flow 订阅分发（对齐 AstrBot LogBroker，用 Flow 替代 asyncio.Queue）。
 */
@Singleton
class LogBroker @Inject constructor() {

    private val cache = ArrayDeque<LogEntry>(CACHED_SIZE + 1)
    private val cacheLock = Any()

    private val _events = MutableSharedFlow<LogEntry>(
        replay = 0,
        extraBufferCapacity = CACHED_SIZE,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: Flow<LogEntry> = _events.asSharedFlow()

    private val subscribers = CopyOnWriteArrayList<MutableSharedFlow<LogEntry>>()

    fun publish(entry: LogEntry) {
        synchronized(cacheLock) {
            if (cache.size >= CACHED_SIZE) {
                cache.removeFirst()
            }
            cache.addLast(entry)
        }
        _events.tryEmit(entry)
        subscribers.forEach { flow -> flow.tryEmit(entry) }
    }

    /** 获取当前缓存快照（新 → 旧 或 旧 → 新 由调用方决定）。 */
    fun snapshot(): List<LogEntry> = synchronized(cacheLock) { cache.toList() }

    /**
     * 注册一个独立订阅 Flow（带缓存预热）。
     * 调用方应在不再需要时 [unregister]。
     */
    fun register(): MutableSharedFlow<LogEntry> {
        val flow = MutableSharedFlow<LogEntry>(
            replay = 0,
            extraBufferCapacity = CACHED_SIZE + 10,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )
        subscribers.add(flow)
        return flow
    }

    fun unregister(flow: MutableSharedFlow<LogEntry>) {
        subscribers.remove(flow)
    }

    companion object {
        const val CACHED_SIZE = 500
    }
}
