package yuku.alkitab.base.util

import android.os.Looper
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DebouncerTest {
    private class RecordingDebouncer(
        defaultDelay: Long,
        expectedProcessCount: Int = 1,
        private val processGate: CountDownLatch? = null,
    ) : Debouncer<String, String>(defaultDelay) {
        val processed: MutableList<String> = Collections.synchronizedList(mutableListOf())
        val allProcessed = CountDownLatch(expectedProcessCount)
        val processStarted = CountDownLatch(1)
        @Volatile var processThread: Thread? = null

        val results = mutableListOf<String>()
        var resultThread: Thread? = null

        override fun process(payload: String): String {
            processThread = Thread.currentThread()
            processStarted.countDown()
            processGate?.await(5, TimeUnit.SECONDS)
            processed.add(payload)
            allProcessed.countDown()
            return payload.uppercase()
        }

        override fun onResult(result: String) {
            resultThread = Thread.currentThread()
            results.add(result)
        }
    }

    private fun awaitProcessingThenRunMainLooper(debouncer: RecordingDebouncer) {
        assertTrue(debouncer.allProcessed.await(5, TimeUnit.SECONDS))
        awaitMainLooperMessage()
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun awaitMainLooperMessage() {
        val deadline = System.currentTimeMillis() + 5000
        while (shadowOf(Looper.getMainLooper()).isIdle && System.currentTimeMillis() < deadline) {
            Thread.sleep(5)
        }
    }

    @Test
    fun `a submitted payload is processed off the main thread and its result delivered on the main thread`() {
        val debouncer = RecordingDebouncer(0)

        debouncer.submit("a")
        awaitProcessingThenRunMainLooper(debouncer)

        assertEquals(listOf("a"), debouncer.processed)
        assertEquals(listOf("A"), debouncer.results)
        assertNotSame(Looper.getMainLooper().thread, debouncer.processThread)
        assertSame(Looper.getMainLooper().thread, debouncer.resultThread)
    }

    @Test
    fun `a payload superseded before its delay elapses is never processed`() {
        val debouncer = RecordingDebouncer(100)

        debouncer.submit("a")
        debouncer.submit("b")
        awaitProcessingThenRunMainLooper(debouncer)

        assertEquals(listOf("b"), debouncer.processed)
        assertEquals(listOf("B"), debouncer.results)
    }

    @Test
    fun `a payload superseded while it is being processed does not deliver its result`() {
        val gate = CountDownLatch(1)
        val debouncer = RecordingDebouncer(0, expectedProcessCount = 2, processGate = gate)

        debouncer.submit("a")
        assertTrue(debouncer.processStarted.await(5, TimeUnit.SECONDS))
        debouncer.submit("b")
        gate.countDown()
        awaitProcessingThenRunMainLooper(debouncer)

        assertEquals(listOf("a", "b"), debouncer.processed)
        assertEquals(listOf("B"), debouncer.results)
    }

    @Test
    fun `a result superseded before the main thread handles it is dropped`() {
        val debouncer = RecordingDebouncer(0)

        debouncer.submit("a")
        assertTrue(debouncer.allProcessed.await(5, TimeUnit.SECONDS))
        awaitMainLooperMessage()
        debouncer.submit("b", 60_000)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(listOf("a"), debouncer.processed)
        assertEquals(emptyList<String>(), debouncer.results)
    }
}
