package yuku.alkitab.base.devotion

import java.util.ArrayDeque
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class DevotionDownloader(
    private val backend: Backend,
    private val foregroundDispatcher: CoroutineDispatcher,
    private val prefetchDispatcher: CoroutineDispatcher,
) {
    data class Key(val name: String, val date: String)
    enum class State { QUEUED, DOWNLOADING, READY, FAILED, UNAVAILABLE }

    interface Request {
        fun execute(): State
        fun cancel()
    }

    fun interface Backend {
        fun createRequest(key: Key, refresh: Boolean): Request
    }

    private class Work(val key: Key, val refresh: Boolean) {
        var request: Request? = null
        var cancelled = false
    }

    private val scope = CoroutineScope(SupervisorJob() + foregroundDispatcher)
    private val pending = ArrayDeque<Work>()
    private val workByKey = mutableMapOf<Key, Work>()
    private val states = linkedMapOf<Key, State>()
    private val revision = MutableStateFlow(0L)
    val changes: StateFlow<Long> = revision
    private var selected: Key? = null
    private var foreground: Work? = null
    private var prefetch: Work? = null
    private var stopped = false

    @Synchronized
    fun getState(name: String, date: String): State? = states[Key(name, date)]

    @Synchronized
    fun select(name: String, date: String, needsDownload: Boolean) {
        if (stopped) return
        val key = Key(name, date)
        selected = key
        foreground?.takeIf { it.key != key && !it.cancelled }?.let {
            it.cancelled = true
            it.request?.cancel()
        }
        if (needsDownload) enqueue(key, false)
        pump()
    }

    @Synchronized
    fun retry(name: String, date: String) {
        if (stopped) return
        select(name, date, false)
        enqueue(Key(name, date), true)
        pump()
    }

    @Synchronized
    fun addPrefetch(name: String, date: String) {
        if (stopped) return
        enqueue(Key(name, date), false)
        pump()
    }

    private fun enqueue(key: Key, refresh: Boolean) {
        if (workByKey.containsKey(key)) return
        if (!refresh && states[key] in listOf(State.FAILED, State.UNAVAILABLE)) return
        val work = Work(key, refresh)
        workByKey[key] = work
        pending.addLast(work)
        setState(key, State.QUEUED)
    }

    private fun setState(key: Key, state: State?) {
        if (state == null) states.remove(key) else states[key] = state
        // Active keys must remain observable; terminal history is bounded for long app sessions.
        while (states.size > 128) {
            val evict = states.keys.firstOrNull { it != selected && !workByKey.containsKey(it) } ?: break
            states.remove(evict)
        }
        revision.value += 1
    }

    private fun pump() {
        if (stopped) return
        if (foreground == null) {
            val next = pending.firstOrNull { it.key == selected }
            if (next != null) {
                pending.remove(next)
                foreground = next
                scope.launch(foregroundDispatcher) { download(next, true) }
            }
        }
        if (prefetch == null) {
            val next = pending.firstOrNull { it.key != selected }
            if (next != null) {
                pending.remove(next)
                prefetch = next
                scope.launch(prefetchDispatcher) { download(next, false) }
            }
        }
    }

    private fun download(work: Work, isForeground: Boolean) {
        var result = State.FAILED
        try {
            val request = synchronized(this) {
                if (work.cancelled || stopped) null else {
                    backend.createRequest(work.key, work.refresh).also {
                        work.request = it
                        setState(work.key, State.DOWNLOADING)
                    }
                }
            }
            if (request != null) result = request.execute()
        } catch (_: Exception) {
            result = State.FAILED
        } finally {
            synchronized(this) {
                workByKey.remove(work.key)
                if (isForeground) foreground = null else prefetch = null
                setState(work.key, if (stopped) State.FAILED else if (work.cancelled) null else result)
                // A rapid A -> B -> A selection can return before A's cancellation completes.
                if (!stopped && work.cancelled && selected == work.key) enqueue(work.key, work.refresh)
                pump()
            }
        }
    }

    @Synchronized
    fun shutdown() {
        stopped = true
        workByKey.values.forEach {
            it.cancelled = true
            it.request?.cancel()
            setState(it.key, State.FAILED)
        }
        workByKey.clear()
        pending.clear()
        scope.cancel()
    }

    companion object {
        fun create(): DevotionDownloader {
            // Two IO slots keep a blocking prefetch transfer from occupying the selected reading's slot.
            val dispatcher = Dispatchers.IO.limitedParallelism(2)
            return DevotionDownloader(DevotionDownloadBackend.create(), dispatcher, dispatcher)
        }
    }
}
