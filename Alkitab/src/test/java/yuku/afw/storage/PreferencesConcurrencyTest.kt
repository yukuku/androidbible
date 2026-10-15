package yuku.afw.storage

import android.app.Application
import android.content.SharedPreferences
import android.preference.PreferenceManager
import java.lang.reflect.Proxy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class PreferencesConcurrencyTest {
    private lateinit var realPreferences: SharedPreferences

    @Before
    fun setUp() {
        yuku.afw.App.context = RuntimeEnvironment.getApplication()
        Preferences.invalidate()
        realPreferences = PreferenceManager.getDefaultSharedPreferences(yuku.afw.App.context)
    }

    @After
    fun tearDown() {
        Preferences.invalidate()
    }

    @Test
    fun `an editor cannot be applied and detached while a writer is still adding its value`() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val realEditor = realPreferences.edit()
        val editor = Proxy.newProxyInstance(
            SharedPreferences.Editor::class.java.classLoader, arrayOf(SharedPreferences.Editor::class.java),
        ) { _, method, args ->
            if (method.name == "putString" && args[0] == "first") {
                entered.countDown()
                check(release.await(10, TimeUnit.SECONDS))
            }
            method.invoke(realEditor, *(args ?: emptyArray()))
        } as SharedPreferences.Editor
        val preferences = Proxy.newProxyInstance(
            SharedPreferences::class.java.classLoader, arrayOf(SharedPreferences::class.java),
        ) { _, method, args ->
            if (method.name == "edit") editor else method.invoke(realPreferences, *(args ?: emptyArray()))
        } as SharedPreferences
        Preferences::class.java.getDeclaredField("cache").apply { isAccessible = true }.set(null, preferences)
        Preferences::class.java.getDeclaredField("dirty").apply { isAccessible = true }.setBoolean(null, false)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val first = executor.submit { Preferences.setString("first", "one") }
            assertTrue(entered.await(10, TimeUnit.SECONDS))
            val secondStarted = CountDownLatch(1)
            val second = executor.submit {
                secondStarted.countDown()
                Preferences.setString("second", "two")
            }
            assertTrue(secondStarted.await(10, TimeUnit.SECONDS))
            // Give the second writer an opportunity to reach the shared editor.
            try { second.get(200, TimeUnit.MILLISECONDS) } catch (_: java.util.concurrent.TimeoutException) { }
            release.countDown()
            first.get(10, TimeUnit.SECONDS)
            second.get(10, TimeUnit.SECONDS)
            assertEquals("one", realPreferences.getString("first", null))
            assertEquals("two", realPreferences.getString("second", null))
        } finally {
            release.countDown()
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS))
        }
    }

    @Test
    fun `nested transactions batch writes and release the editor after an exception`() {
        try {
            Preferences.withTransaction {
                Preferences.setInt("count", 7)
                Preferences.withTransaction { Preferences.setBoolean("isReady", true) }
                throw IllegalStateException("test")
            }
        } catch (_: IllegalStateException) { }
        Preferences.setString("after", "saved")
        assertEquals(7, Preferences.getInt("count", 0))
        assertTrue(Preferences.getBoolean("isReady", false))
        assertEquals("saved", Preferences.getString("after"))
    }
}
