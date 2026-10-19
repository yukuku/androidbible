package yuku.alkitab.base.sync

import android.app.Application
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import yuku.afw.storage.Preferences
import yuku.alkitab.base.App
import yuku.alkitab.base.model.SyncShadow
import yuku.alkitab.base.services.AppServices
import yuku.alkitab.base.services.StorageProvider
import yuku.alkitab.base.storage.InternalDb
import yuku.alkitab.base.storage.InternalDbHelper
import yuku.alkitab.base.storage.Prefkey
import yuku.alkitab.model.Marker

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class SyncApplierTest {
    private lateinit var helper: InternalDbHelper
    private lateinit var db: InternalDb
    private lateinit var originalServices: AppServices

    @Before
    fun setUp() {
        yuku.afw.App.context = RuntimeEnvironment.getApplication()
        Preferences.invalidate()
        helper = InternalDbHelper(yuku.afw.App.context)
        db = InternalDb(helper)
        originalServices = App.services
        val storage = mockk<StorageProvider>()
        every { storage.db } returns db
        App.services = AppServices(storage, mockk(), mockk())
        Preferences.setString(Prefkey.sync_simpleToken, "test-token")
    }

    @After
    fun tearDown() {
        App.services = originalServices
        helper.close()
    }

    private fun apply(vararg operations: Sync.Operation<Sync_Mabel.Content>): Sync.ApplyAppendDeltaResult {
        val delta = Sync.Delta<Sync_Mabel.Content>().apply { this.operations.addAll(operations) }
        val client = Sync.ClientState<Sync_Mabel.Content>(0, Sync.Delta())
        return db.syncApplier.applyMabelAppendDelta(5, emptyList(), client, delta, emptyList(), "test-token")
    }

    @Test
    fun `missing content for each mabel entity kind rejects the delta and rolls back preceding writes`() {
        for (kind in listOf(Sync.Entity.KIND_MARKER, Sync.Entity.KIND_LABEL, Sync.Entity.KIND_MARKER_LABEL)) {
            for (opkind in listOf(Sync.Opkind.add, Sync.Opkind.mod)) {
                assertEquals(Sync.ApplyAppendDeltaResult.unknown_kind, apply(
                    Sync.Operation(Sync.Opkind.add, Sync.Entity.KIND_LABEL, "valid-label", Sync_Mabel.Content().apply { title = "valid"; ordering = 0 }),
                    Sync.Operation(opkind, kind, "malformed", null),
                ))
                assertNull(db.getLabelByGid("valid-label"))
                assertNull(db.getSyncShadowBySyncSetName(SyncShadow.SYNC_SET_MABEL))
            }
        }
    }

    @Test
    fun `an unknown marker subtype rejects the delta without advancing its shadow`() {
        assertEquals(Sync.ApplyAppendDeltaResult.unknown_kind, apply(
            Sync.Operation(Sync.Opkind.add, Sync.Entity.KIND_MARKER, "bad-marker", Sync_Mabel.Content().apply {
                ari = 123
                kind = 999
                verseCount = 1
                createTime = 0
                modifyTime = 0
            }),
        ))
        assertNull(db.getMarkerByGid("bad-marker"))
        assertNull(db.getSyncShadowBySyncSetName(SyncShadow.SYNC_SET_MABEL))
    }

    @Test
    fun `a missing marker subtype rejects the delta without throwing`() {
        assertEquals(Sync.ApplyAppendDeltaResult.unknown_kind, apply(
            Sync.Operation(Sync.Opkind.mod, Sync.Entity.KIND_MARKER, "bad-marker", Sync_Mabel.Content()),
        ))
        assertNull(db.getSyncShadowBySyncSetName(SyncShadow.SYNC_SET_MABEL))
    }

    @Test
    fun `valid marker content and contentless deletion remain supported`() {
        assertEquals(Sync.ApplyAppendDeltaResult.ok, apply(
            Sync.Operation(Sync.Opkind.add, Sync.Entity.KIND_MARKER, "marker", Sync_Mabel.Content().apply {
                kind = Marker.Kind.bookmark.code
                ari = 123
                verseCount = 1
                createTime = 0
                modifyTime = 0
                caption = "bookmark"
            }),
            Sync.Operation(Sync.Opkind.del, Sync.Entity.KIND_LABEL, "missing-label", null),
        ))
        assertNotNull(db.getMarkerByGid("marker"))
        assertEquals(5, db.getSyncShadowBySyncSetName(SyncShadow.SYNC_SET_MABEL)!!.revno)
    }
}
