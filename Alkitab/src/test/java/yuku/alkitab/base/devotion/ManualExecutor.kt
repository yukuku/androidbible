package yuku.alkitab.base.devotion

import java.util.ArrayDeque
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit

class ManualExecutor : AbstractExecutorService() {
    private val tasks = ArrayDeque<Runnable>()
    private var stopped = false
    override fun execute(command: Runnable) { tasks.addLast(command) }
    fun runNext() { tasks.removeFirst().run() }
    fun runAll() { while (tasks.isNotEmpty()) runNext() }
    val size get() = tasks.size
    override fun shutdown() { stopped = true }
    override fun shutdownNow(): MutableList<Runnable> {
        stopped = true
        return tasks.toMutableList().also { tasks.clear() }
    }
    override fun isShutdown() = stopped
    override fun isTerminated() = stopped && tasks.isEmpty()
    override fun awaitTermination(timeout: Long, unit: TimeUnit) = isTerminated
}
