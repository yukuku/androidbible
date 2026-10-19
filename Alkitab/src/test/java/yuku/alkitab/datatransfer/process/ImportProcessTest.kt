package yuku.alkitab.datatransfer.process

import android.app.Application
import io.mockk.mockk
import java.io.File
import java.util.Date
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import yuku.afw.storage.Preferences
import yuku.alkitab.base.App
import yuku.alkitab.base.model.ReadingPlan
import yuku.alkitab.base.services.AppServices
import yuku.alkitab.base.services.StorageProvider
import yuku.alkitab.base.storage.InternalDb
import yuku.alkitab.base.storage.InternalDbHelper
import yuku.alkitab.base.storage.InternalDbTxWrapper
import yuku.alkitab.base.storage.Prefkey
import yuku.alkitab.base.storage.SongDb
import yuku.alkitab.base.util.History
import yuku.alkitab.datatransfer.model.*
import yuku.alkitab.model.Marker

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class ImportProcessTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var helper: InternalDbHelper
    private lateinit var db: InternalDb
    private lateinit var oldServices: AppServices
    private val storage = ReadWriteStorageImpl()
    private val log = LogInterface { }
    private val originalHistory = listOf(History.Entry("original", 257, 1000L, "local"))
    private val incomingHistory = listOf(History.Entry("incoming", 258, 2000L, "other"))

    @Before
    fun setUp() {
        val app = RuntimeEnvironment.getApplication()
        yuku.afw.App.context = app
        Preferences.invalidate()
        helper = InternalDbHelper(app)
        db = InternalDb(helper)
        oldServices = App.services
        App.services = AppServices(object : StorageProvider {
            override val db = this@ImportProcessTest.db
            override val songDb: SongDb get() = error("Songs are not part of data transfer")
        }, mockk(), mockk())
        History.replaceAllEntries(originalHistory)
        History.save()
    }

    @After
    fun tearDown() {
        while (helper.writableDatabase.inTransaction()) helper.writableDatabase.endTransaction()
        helper.close()
        App.services = oldServices
    }

    @Test
    fun `simulating then cancelling preserves live and saved history and rolls back markers`() {
        runImport(root(listOf(marker())), isActualRun = false)

        assertEquals(originalHistory, History.listAllEntries())
        History.save()
        assertEquals(originalHistory, savedHistory())
        assertTrue(db.listAllMarkers().isEmpty())
        assertFalse(helper.writableDatabase.inTransaction())
    }

    @Test
    fun `actual import saves history immediately and commits database rows`() {
        runImport(root(listOf(marker())))

        assertEquals(incomingHistory, History.listAllEntries())
        assertEquals(incomingHistory, savedHistory())
        assertEquals("marker", db.listAllMarkers().single().gid)
        assertFalse(helper.writableDatabase.inTransaction())
    }

    @Test
    fun `an invalid marker kind rolls back preceding rows and leaves history untouched`() {
        assertThrows(Exception::class.java) {
            runImport(root(listOf(marker(), marker("invalid", kind = 99))))
        }

        assertFalse("A failed import must end its transaction", helper.writableDatabase.inTransaction())
        assertTrue(db.listAllMarkers().isEmpty())
        assertEquals(originalHistory, History.listAllEntries())
        assertEquals(originalHistory, savedHistory())
        val label = db.insertLabel("Writable after failure", null)
        assertNotNull(db.getLabelById(label._id))
    }

    @Test
    fun `a failure after requesting commit still rolls back and releases the transaction`() {
        assertThrows(IllegalStateException::class.java) {
            InternalDbTxWrapper.transact(db) { handle ->
                db.insertLabel("Partial", null)
                handle.commit()
                error("Injected failure")
            }
        }

        assertFalse(helper.writableDatabase.inTransaction())
        assertTrue(db.listAllLabels().isEmpty())
        InternalDbTxWrapper.transact(db) { handle ->
            db.insertLabel("Committed", null)
            handle.commit()
        }
        assertEquals("Committed", db.listAllLabels().single().title)
    }

    @Test
    fun `dangling marker or label references reject the whole import in either mode`() {
        for (isActualRun in listOf(false, true)) {
            for ((markerGid, labelGid) in listOf("missing" to "label", "marker" to "missing")) {
                val entities = listOf(marker(), label(), link(markerGid, labelGid))
                assertThrows(IllegalArgumentException::class.java) {
                    runImport(root(entities), isActualRun)
                }
                assertFalse(helper.writableDatabase.inTransaction())
                assertTrue(db.listAllMarkers().isEmpty())
                assertTrue(db.listAllLabels().isEmpty())
                assertTrue(db.listAllMarker_Labels().isEmpty())
                assertEquals(originalHistory, History.listAllEntries())
                assertEquals(originalHistory, savedHistory())
            }
        }
    }

    @Test
    fun `label assignments can reference entities later in the file`() {
        runImport(root(listOf(link(), label(), marker())))

        val assignment = db.listAllMarker_Labels().single()
        assertEquals("marker", assignment.marker_gid)
        assertEquals("label", assignment.label_gid)
    }

    @Test
    fun `label assignments can reference existing database entities`() {
        val existingMarker = db.insertMarker(257, Marker.Kind.bookmark, "Existing", 1, Date(0), Date(0))
        val existingLabel = db.insertLabel("Existing", null)
        runImport(root(listOf(link(existingMarker.gid, existingLabel.gid))))

        assertEquals(existingMarker.gid, db.listAllMarker_Labels().single().marker_gid)
        assertEquals(existingLabel.gid, db.listAllMarker_Labels().single().label_gid)
    }

    @Test
    fun `empty incoming history clears and saves history on actual import`() {
        runImport(root().let { it.copy(snapshots = it.snapshots.copy(history = Snapshot(entities = emptyList()))) })

        assertTrue(History.listAllEntries().isEmpty())
        assertTrue(savedHistory().isEmpty())
    }

    @Test
    fun `deselecting history preserves it during actual import`() {
        val file = writeRoot(root(listOf(marker())))
        ImportProcess(storage, log).import(file, options(true).copy(importHistory = false))

        assertEquals(originalHistory, History.listAllEntries())
        assertEquals(originalHistory, savedHistory())
        assertEquals(1, db.listAllMarkers().size)
    }

    @Test
    fun `export and import retain progress for deleted plans and plans without completed readings`() {
        val plan = ReadingPlan.ReadingPlanInfo().apply {
            name = "deleted-plan"
            title = "Deleted plan"
            description = ""
            startTime = 1000L
        }
        val id = db.insertReadingPlan(plan, byteArrayOf())
        val gid = ReadingPlan.gidFromName(plan.name)
        db.insertOrUpdateReadingPlanProgress(gid, 1, 1000L)
        db.deleteReadingPlanById(id)
        val emptyPlan = ReadingPlan.ReadingPlanInfo().apply {
            name = "empty-plan"
            title = "Empty plan"
            description = ""
            startTime = 2000L
        }
        db.insertReadingPlan(emptyPlan, byteArrayOf())
        val file = temporaryFolder.newFile()
        ExportProcess(storage, log, "local").export(file)

        val exported = Serializer.importJson.decodeFromString(Root.serializer(), file.readText())
        val progresses = exported.snapshots.rp.entities.associateBy { it.gid }
        assertEquals(listOf(1), progresses[gid]!!.content.done)
        assertEquals(0L, progresses[gid]!!.content.startTime)
        assertEquals(emptyList<Int>(), progresses[ReadingPlan.gidFromName(emptyPlan.name)]!!.content.done)
        assertEquals(2000L, progresses[ReadingPlan.gidFromName(emptyPlan.name)]!!.content.startTime)
        db.deleteReadingPlanProgress(gid, 1)
        ImportProcess(storage, log).import(file, options(true))
        assertEquals(setOf(1), db.readingPlanProgressSummaryForSync[gid])
    }

    private fun savedHistory(): List<History.Entry> = App.getDefaultGson()
        .fromJson(Preferences.getString(Prefkey.history), History.HistoryJson::class.java).entries

    private fun options(isActualRun: Boolean) = ImportProcess.Options(true, true, true, true, isActualRun)

    private fun runImport(root: Root, isActualRun: Boolean = true) {
        ImportProcess(storage, log).import(writeRoot(root), options(isActualRun))
    }

    private fun writeRoot(root: Root): File = temporaryFolder.newFile().apply {
        writeText(Serializer.exportJson.encodeToString(Root.serializer(), root))
    }

    private fun root(entities: List<MabelEntity> = emptyList()) = Root(true, Snapshots(
        history = Snapshot(entities = incomingHistory.map {
            HistoryEntity(it.gid, "HistoryEntry", it.creator_id, HistoryContent(it.ari, it.timestamp))
        }),
        mabel = Snapshot(entities = entities),
        pins = Snapshot(entities = emptyList()),
        rp = Snapshot(entities = emptyList()),
    ))

    private fun marker(gid: String = "marker", kind: Int = 1) = MarkerEntity(
        gid, "other", MarkerContent(kind, 258, 1, 1, 2, "Imported"),
    )

    private fun label() = LabelEntity("label", "other", LabelContent("Imported", 1))

    private fun link(markerGid: String = "marker", labelGid: String = "label") = MarkerLabelEntity(
        "link", "other", MarkerLabelContent(markerGid, labelGid),
    )
}
