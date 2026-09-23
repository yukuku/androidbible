package yuku.alkitab.base.util

import android.os.Handler
import android.os.Message
import java.lang.ref.WeakReference
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Delays processing of a payload, and if another payload is submitted
 * afterwards, the earlier ones are not processed nor delivered any more.
 * @param RequestType
 * @param ResultType
 *
 * @constructor Call this on the main thread.
 * @param defaultDelay The default delay in ms before [process] is performed.
 */
abstract class Debouncer<RequestType, ResultType>(private val defaultDelay: Long) {
    private val handler = DebounceHandler(this)
    private val sched: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()

    private val serial = AtomicInteger()

    @Suppress("DEPRECATION")
    private class DebounceHandler<RequestType, ResultType>(debouncer: Debouncer<RequestType, ResultType>) : Handler() {
        private val ref = WeakReference(debouncer)

        override fun handleMessage(msg: Message) {
            val debouncer = ref.get() ?: return

            // check again one more time
            if (isOutdated(3, msg.arg1, debouncer.serial.get())) return

            @Suppress("UNCHECKED_CAST")
            debouncer.onResult(msg.obj as ResultType)
        }
    }

    /**
     * Schedule processing after the default delay.
     * @param payload Payload to be sent to the [process] method.
     */
    fun submit(payload: RequestType) {
        submit(payload, defaultDelay)
    }

    /**
     * Schedule processing after the specified delay.
     * @param payload Payload to be sent to the [process] method.
     */
    fun submit(payload: RequestType, delay: Long) {
        val id = serial.incrementAndGet()

        sched.schedule(Runnable {
            // check if this is still needed
            if (isOutdated(1, id, serial.get())) return@Runnable

            val result = process(payload)

            // check again if this is still needed
            if (isOutdated(2, id, serial.get())) return@Runnable

            // we are okay, deliver result in main thread
            val msg = Message.obtain()
            msg.what = MSG_ON_RESULT
            msg.arg1 = id
            msg.obj = result

            handler.sendMessage(msg)
        }, delay, TimeUnit.MILLISECONDS)
    }

    /**
     * Called in non-UI thread.
     * Override this to process the payload submitted in [submit].
     */
    abstract fun process(payload: RequestType): ResultType

    /**
     * Called in the UI thread.
     * Override this to receive the process result and e.g. update UI.
     */
    abstract fun onResult(result: ResultType)

    companion object {
        private val TAG: String = Debouncer::class.java.simpleName

        const val MSG_ON_RESULT = 1

        private fun isOutdated(phase: Int, thisId: Int, currentId: Int): Boolean {
            if (thisId == currentId) return false

            when (phase) {
                1 -> AppLog.d(TAG, "outdated task ($thisId < $currentId) found before process")
                2 -> AppLog.d(TAG, "outdated task ($thisId < $currentId) found after process")
                3 -> AppLog.d(TAG, "outdated task ($thisId < $currentId) found before onResult")
            }

            return true
        }
    }
}
