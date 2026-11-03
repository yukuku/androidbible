package yuku.alkitab.base.audio

import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.Looper
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = yuku.afw.App::class, sdk = [34])
class BaseAudioServiceTest {
    class Service : BaseAudioService() {
        override val localBindAction = "local"
        public override val serviceBinder: IBinder = Binder()
        override val isAudioPlaying = false
        var isWaiting = true
        var timeouts = 0
        var releases = 0
        override fun releasePlayer() { releases++ }
        override fun stop() { cancelDiagnosticTimer() }
        fun schedule() = scheduleDiagnostic({ isWaiting }, { timeouts++ })
    }

    @Test
    fun `repeated scheduling keeps the original deadline and binding returns the local binder`() {
        val owner = Robolectric.buildService(Service::class.java).create()
        val service = owner.get()
        assertSame(service.serviceBinder, service.onBind(Intent("local")))
        service.schedule()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(4))
        service.schedule()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        assertEquals(1, service.timeouts)
        owner.destroy()
        assertEquals(1, service.releases)
    }

    @Test
    fun `destruction cancels pending diagnostics and releases the player`() {
        val owner = Robolectric.buildService(Service::class.java).create()
        val service = owner.get()
        service.schedule()
        owner.destroy()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(6))
        assertEquals(0, service.timeouts)
        assertEquals(1, service.releases)
    }
}
