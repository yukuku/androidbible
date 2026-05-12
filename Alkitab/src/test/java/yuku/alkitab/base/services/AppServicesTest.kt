package yuku.alkitab.base.services

import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Test
import yuku.alkitab.base.S
import yuku.alkitab.base.model.MVersion
import yuku.alkitab.base.model.MVersionInternal
import yuku.alkitab.base.storage.InternalDb
import yuku.alkitab.base.storage.SongDb
import yuku.alkitab.model.Version

/**
 * Sanity tests for the service interfaces extracted from [S] as part of REM-24.
 *
 * These exist to prove the new contracts are decoupled from the [S] service locator —
 * a test can construct fake implementations and wire them through [AppServices]
 * without needing an Android `Context`, the real `InternalDb`, or any of the
 * preference machinery.
 */
class AppServicesTest {

    @Test
    fun `AppServices wires its three interfaces to the implementations passed in`() {
        val storage = FakeStorageProvider()
        val versions = FakeVersionManager()
        val uiDimensions = FakeUiDimensionsProvider()

        val services = AppServices(storage, versions, uiDimensions)

        assertSame(storage, services.storage)
        assertSame(versions, services.versions)
        assertSame(uiDimensions, services.uiDimensions)
    }

    @Test
    fun `a fake VersionManager can be used without touching the S singleton`() {
        val mv = MVersionInternal().apply {
            locale = "en"
            shortName = "TST"
            longName = "Test Version"
        }
        val version = mockk<Version>()
        val versions = FakeVersionManager(activeMVersion = mv, activeVersion = version, activeVersionId = "internal")

        assertEquals("internal", versions.activeVersionId())
        assertSame(mv, versions.activeMVersion())
        assertSame(version, versions.activeVersion())

        val replacement = MVersionInternal().apply { shortName = "X" }
        versions.setActiveVersion(replacement)
        assertSame(replacement, versions.activeMVersion())
    }

    @Test
    fun `a fake UiDimensionsProvider can return a pre-built CalculatedDimensions`() {
        val dims = S.CalculatedDimensions().apply {
            fontSize2dp = 21f
            fontColor = 0xff112233.toInt()
        }
        val ui = FakeUiDimensionsProvider(applied = dims)

        assertSame(dims, ui.applied())

        // recalculate is a no-op on the fake but must be callable through the interface
        ui.recalculate()
        assertSame(dims, ui.applied())
    }

    @Test
    fun `the production S singleton exposes adapter properties for all three interfaces`() {
        val storage: StorageProvider = S.storage
        val versions: VersionManager = S.versions
        val uiDimensions: UiDimensionsProvider = S.uiDimensions

        // The assignments above are the test: they would not compile if any of
        // the adapter properties on S were dropped or had the wrong type.
        assertNotNull(storage)
        assertNotNull(versions)
        assertNotNull(uiDimensions)
    }

    private class FakeStorageProvider(
        override val db: InternalDb = mockk(relaxed = true),
        override val songDb: SongDb = mockk(relaxed = true),
    ) : StorageProvider

    private class FakeVersionManager(
        private var activeMVersion: MVersion = MVersionInternal(),
        private val activeVersion: Version = mockk(relaxed = true),
        private val activeVersionId: String = "internal",
    ) : VersionManager {
        override fun activeVersion(): Version = activeVersion
        override fun activeMVersion(): MVersion = activeMVersion
        override fun activeVersionId(): String = activeVersionId
        override fun setActiveVersion(mv: MVersion) {
            activeMVersion = mv
        }

        override fun getVersionFromVersionId(versionId: String?): MVersion? = null
        override fun getAvailableVersions(): List<MVersion> = listOf(activeMVersion)
        override fun getMVersionInternal(): MVersionInternal = MVersionInternal()
    }

    private class FakeUiDimensionsProvider(
        private val applied: S.CalculatedDimensions = S.CalculatedDimensions(),
    ) : UiDimensionsProvider {
        override fun applied(): S.CalculatedDimensions = applied
        override fun recalculate() = Unit
    }
}
