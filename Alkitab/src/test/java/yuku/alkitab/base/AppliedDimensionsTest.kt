package yuku.alkitab.base

import androidx.compose.runtime.snapshots.Snapshot
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import yuku.afw.App as AfwApp

/**
 * The verse list composes against [S.applied], so a preference change
 * (pinch-to-zoom, the text appearance panel) has to reach the rows that are
 * already on screen, not only the ones composed after it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = AfwApp::class, sdk = [34])
class AppliedDimensionsTest {

    @Before
    fun setUp() {
        AfwApp.initWithAppContext(ApplicationProvider.getApplicationContext())
    }

    @Test
    fun `recalculating notifies the snapshot observers that read the dimensions`() {
        val read = mutableSetOf<Any>()
        Snapshot.observe(readObserver = { read += it }) { S.applied() }
        assertTrue("S.applied() must be readable as snapshot state", read.isNotEmpty())

        val notified = mutableListOf<Set<Any>>()
        val handle = Snapshot.registerApplyObserver { changed, _ -> notified += changed }
        try {
            S.recalculate()
            Snapshot.sendApplyNotifications()
        } finally {
            handle.dispose()
        }

        assertTrue(
            "recalculate must invalidate the readers of S.applied(), notified $notified",
            notified.any { changed -> changed.any(read::contains) },
        )
    }

    @Test
    fun `each recalculation publishes a distinct instance, so it works as a remember key`() {
        val before = S.applied()
        S.recalculate()
        assertNotSame(before, S.applied())
    }
}
