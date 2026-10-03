package yuku.alkitab.base.devotion

import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import yuku.alkitab.base.devotion.DevotionDownloader.Key
import yuku.alkitab.base.devotion.DevotionDownloader.State

class DevotionDownloaderTest {
    private val foreground = ManualExecutor()
    private val background = ManualExecutor()
    private val created = mutableListOf<Key>()
    private val cancelled = mutableListOf<Key>()
    private var action: (Key) -> State = { State.READY }
    private val downloader = DevotionDownloader({ key, _ ->
        created.add(key)
        object : DevotionDownloader.Request {
            override fun execute() = action(key)
            override fun cancel() { cancelled.add(key) }
        }
    }, foreground, background)
    private val a = Key("me-en", "20260925")
    private val b = Key("me-en", "20260926")
    private val c = Key("me-en", "20260929")
    private fun select(key: Key) = downloader.select(key.name, key.date, true)
    private fun state(key: Key) = downloader.getState(key.name, key.date)

    @After fun tearDown() = downloader.shutdown()

    @Test fun `a selected date completes while slow prefetch is still active`() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val selectedDone = CountDownLatch(1)
        val realDownloader = DevotionDownloader({ key, _ ->
            object : DevotionDownloader.Request {
                override fun cancel() { release.countDown() }
                override fun execute(): State {
                    if (key == a) {
                        started.countDown()
                        assertTrue(release.await(5, TimeUnit.SECONDS))
                    } else selectedDone.countDown()
                    return State.READY
                }
            }
        })
        try {
            realDownloader.addPrefetch(a.name, a.date)
            assertTrue(started.await(5, TimeUnit.SECONDS))
            realDownloader.select(c.name, c.date, true)
            assertTrue(selectedDone.await(5, TimeUnit.SECONDS))
            assertEquals(State.DOWNLOADING, realDownloader.getState(a.name, a.date))
        } finally {
            release.countDown()
            realDownloader.shutdown()
        }
    }

    @Test fun `queued and in-flight requests deduplicate by source and date`() {
        repeat(5) { downloader.addPrefetch(a.name, a.date) }
        select(a)
        action = {
            assertEquals(State.DOWNLOADING, state(a))
            repeat(5) {
                select(a)
                downloader.retry(a.name, a.date)
                downloader.addPrefetch(a.name, a.date)
            }
            State.READY
        }
        background.runNext()
        assertEquals(listOf(a), created)
        assertEquals(0, foreground.size)
        assertEquals(State.READY, state(a))
    }

    @Test fun `a queued prefetch date is promoted ahead of other background dates`() {
        downloader.addPrefetch(a.name, a.date)
        downloader.addPrefetch(b.name, b.date)
        downloader.addPrefetch(c.name, c.date)
        select(c)
        assertEquals(State.QUEUED, state(c))
        foreground.runNext()
        assertEquals(listOf(c), created)
        background.runAll()
        assertEquals(listOf(c, a, b), created)
    }

    @Test fun `rapid selections cancel obsolete foreground and start the latest date next`() {
        select(a)
        action = { key ->
            if (key == a) {
                select(b)
                select(c)
                assertEquals(listOf(a), cancelled)
                assertEquals(State.QUEUED, state(c))
            }
            State.READY
        }
        foreground.runNext()
        foreground.runNext()
        assertEquals(listOf(a, c), created)
        assertNull(state(a))
        assertEquals(State.READY, state(c))
        background.runAll()
        assertEquals(State.READY, state(c))
    }

    @Test fun `returning to a cancelling request restarts it only after it ends`() {
        select(a)
        action = {
            select(b)
            select(a)
            State.FAILED
        }
        foreground.runNext()
        assertEquals(State.QUEUED, state(a))
        action = { State.READY }
        foreground.runNext()
        assertEquals(listOf(a, a), created)
        assertEquals(State.READY, state(a))
    }

    @Test fun `failure remains terminal until an explicit retry succeeds`() {
        action = { throw IOException("offline") }
        select(a)
        foreground.runNext()
        assertEquals(State.FAILED, state(a))
        repeat(5) { select(a) }
        assertEquals(0, foreground.size)
        action = { State.READY }
        downloader.retry(a.name, a.date)
        assertEquals(State.QUEUED, state(a))
        foreground.runNext()
        assertEquals(State.READY, state(a))
        assertEquals(listOf(a, a), created)
    }

    @Test fun `unavailable content is terminal and may be explicitly retried`() {
        action = { State.UNAVAILABLE }
        select(a)
        foreground.runNext()
        select(a)
        downloader.addPrefetch(a.name, a.date)
        assertEquals(0, foreground.size)
        downloader.retry(a.name, a.date)
        action = { State.READY }
        foreground.runNext()
        assertEquals(State.READY, state(a))
    }

    @Test fun `the same date in different sources creates distinct requests`() {
        downloader.addPrefetch(a.name, a.date)
        downloader.select("rh", a.date, true)
        foreground.runAll()
        background.runAll()
        assertEquals(setOf(a, Key("rh", a.date)), created.toSet())
    }

    @Test fun `selecting cached content cancels an obsolete foreground without downloading the cache`() {
        select(a)
        action = {
            downloader.select(c.name, c.date, false)
            State.READY
        }
        foreground.runNext()
        assertEquals(listOf(a), cancelled)
        assertEquals(listOf(a), created)
        assertEquals(0, foreground.size)
    }

    @Test fun `shutdown terminates queued states and rejects new work`() {
        select(a)
        downloader.addPrefetch(b.name, b.date)
        downloader.shutdown()
        assertEquals(State.FAILED, state(a))
        assertEquals(State.FAILED, state(b))
        select(c)
        assertNull(state(c))
    }
}
