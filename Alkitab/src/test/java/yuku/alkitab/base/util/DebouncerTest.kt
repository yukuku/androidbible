package yuku.alkitab.base.util

import android.os.Handler
import android.os.Looper
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@LooperMode(LooperMode.Mode.PAUSED)
class DebouncerTest {
    private class RecordingDebouncer(
        defaultDelay: Long,
        expectedProcessCount: Int,
        private val processGate: CountDownLatch?,
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
            if (processGate != null) assertTrue(processGate.await(10, TimeUnit.SECONDS))
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
        awaitMainLooperMessage(debouncer)
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun awaitMainLooperMessage(debouncer: Debouncer<*, *>) {
        // A process latch can fire before its result is posted, while unrelated handlers already have queued messages.
        val handler = ReflectionHelpers.getField<Handler>(debouncer, "handler")
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (!handler.hasMessages(Debouncer.MSG_ON_RESULT) && System.nanoTime() < deadline) {
            Thread.sleep(5)
        }
        assertTrue("The debouncer did not enqueue its result", handler.hasMessages(Debouncer.MSG_ON_RESULT))
    }

    @Test
    fun `multiple debouncer instances deliver independent results`() {
        val debouncers = List(12) { RecordingDebouncer(0, 1, null) }

        debouncers.forEachIndexed { index, debouncer ->
            debouncer.submit(index.toString())
            awaitProcessingThenRunMainLooper(debouncer)
        }

        debouncers.forEachIndexed { index, debouncer ->
            assertEquals(listOf(index.toString()), debouncer.results)
        }
    }

    @Test
    fun `a blocked debouncer does not delay another instance`() {
        val gate = CountDownLatch(1)
        val blocked = RecordingDebouncer(0, 1, gate)
        val independent = RecordingDebouncer(0, 1, null)

        try {
            blocked.submit("blocked")
            assertTrue(blocked.processStarted.await(5, TimeUnit.SECONDS))
            independent.submit("independent")
            awaitProcessingThenRunMainLooper(independent)
            assertEquals(listOf("INDEPENDENT"), independent.results)
            assertEquals(emptyList<String>(), blocked.processed)
        } finally {
            gate.countDown()
            awaitProcessingThenRunMainLooper(blocked)
        }
    }

    @Test
    fun `one instance processes serially while another instance makes progress`() {
        val firstStarted = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val secondStarted = CountDownLatch(1)
        val results = mutableListOf<String>()
        val debouncer = object : Debouncer<String, String>(0) {
            override fun process(payload: String): String {
                if (payload == "first") {
                    firstStarted.countDown()
                    assertTrue(releaseFirst.await(5, TimeUnit.SECONDS))
                } else {
                    secondStarted.countDown()
                }
                return payload
            }

            override fun onResult(result: String) {
                results.add(result)
            }
        }
        val independent = RecordingDebouncer(0, 1, null)

        try {
            debouncer.submit("first")
            assertTrue(firstStarted.await(5, TimeUnit.SECONDS))
            debouncer.submit("second")
            independent.submit("independent")
            awaitProcessingThenRunMainLooper(independent)
            assertEquals(listOf("INDEPENDENT"), independent.results)
            assertFalse(secondStarted.await(200, TimeUnit.MILLISECONDS))
        } finally {
            releaseFirst.countDown()
        }

        assertTrue(secondStarted.await(5, TimeUnit.SECONDS))
        awaitMainLooperMessage(debouncer)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf("second"), results)
    }

    @Test
    fun `a failed process does not prevent later submissions from completing`() {
        val failed = CountDownLatch(1)
        val recovered = CountDownLatch(1)
        val results = mutableListOf<String>()
        val debouncer = object : Debouncer<String, String>(0) {
            override fun process(payload: String): String {
                if (payload == "fail") {
                    failed.countDown()
                    throw IllegalStateException("Expected test failure")
                }
                recovered.countDown()
                return payload
            }

            override fun onResult(result: String) {
                results.add(result)
            }
        }

        debouncer.submit("fail")
        assertTrue(failed.await(5, TimeUnit.SECONDS))
        debouncer.submit("recover")
        assertTrue(recovered.await(5, TimeUnit.SECONDS))
        awaitMainLooperMessage(debouncer)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf("recover"), results)
    }

    @Test
    fun `a submitted payload is processed off the main thread and its result delivered on the main thread`() {
        val debouncer = RecordingDebouncer(0, 1, null)

        debouncer.submit("a")
        awaitProcessingThenRunMainLooper(debouncer)

        assertEquals(listOf("a"), debouncer.processed)
        assertEquals(listOf("A"), debouncer.results)
        assertNotSame(Looper.getMainLooper().thread, debouncer.processThread)
        assertSame(Looper.getMainLooper().thread, debouncer.resultThread)
    }

    @Test
    fun `a payload superseded before its delay elapses is never processed`() {
        val debouncer = RecordingDebouncer(100, 1, null)

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
        val debouncer = RecordingDebouncer(0, 1, null)

        debouncer.submit("a")
        assertTrue(debouncer.allProcessed.await(5, TimeUnit.SECONDS))
        awaitMainLooperMessage(debouncer)
        debouncer.submit("b", 60_000)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(listOf("a"), debouncer.processed)
        assertEquals(emptyList<String>(), debouncer.results)
    }
}
