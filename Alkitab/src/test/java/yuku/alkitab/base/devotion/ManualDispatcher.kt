package yuku.alkitab.base.devotion

import java.util.ArrayDeque
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher

class ManualDispatcher : CoroutineDispatcher() {
    private val tasks = ArrayDeque<Runnable>()
    override fun dispatch(context: CoroutineContext, block: Runnable) { tasks.addLast(block) }
    fun runNext() { tasks.removeFirst().run() }
    fun runAll() { while (tasks.isNotEmpty()) runNext() }
    val size get() = tasks.size
}
