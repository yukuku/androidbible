package yuku.alkitab.base.util

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import yuku.alkitab.base.App
import yuku.alkitab.base.model.ReadingPlan
import yuku.alkitab.base.services.AppServices
import yuku.alkitab.base.services.StorageProvider
import yuku.alkitab.base.storage.InternalDb
import yuku.alkitab.util.IntArrayList

@RunWith(RobolectricTestRunner::class)
@Config(application = yuku.afw.App::class, sdk = [34])
class CurrentReadingCompletionTest {
    private lateinit var previousServices: AppServices
    private lateinit var db: InternalDb
    private val progress = mutableMapOf<String, MutableSet<Int>>()

    @Before
    fun setUp() {
        previousServices = App.services
        db = mockk(relaxed = true)
        val storage = mockk<StorageProvider>()
        every { storage.db } returns db
        App.services = AppServices(storage, mockk(), mockk())
        every { db.listReadingPlanNames() } returns listOf("original", "other")
        every { db.getAllReadingCodesByReadingPlanProgressGid(any()) } answers {
            IntArrayList().also { codes -> progress[firstArg<String>()]?.forEach(codes::add) }
        }
        every { db.insertOrUpdateReadingPlanProgress(any(), any(), any()) } answers {
            progress.getOrPut(firstArg()) { mutableSetOf() }.add(secondArg())
            Unit
        }
        every { db.deleteReadingPlanProgress(any(), any()) } answers {
            progress[firstArg<String>()]?.remove(secondArg<Int>())
            Unit
        }
        CurrentReading.clear()
    }

    @After
    fun tearDown() {
        CurrentReading.clear()
        App.services = previousServices
    }

    @Test
    fun `ticking a passage updates only its original plan day and sequence and keeps it active`() {
        CurrentReading.setReadingPlan(0x270106, 0x270110, "original", 12, 3)
        assertEquals(false, CurrentReading.getPlanCompletion())
        assertTrue(CurrentReading.setPlanCompleted(true))
        verify(exactly = 1) { db.insertOrUpdateReadingPlanProgress(ReadingPlan.gidFromName("original"), (12 shl 8) or 3, any()) }
        verify(exactly = 0) { db.insertOrUpdateReadingPlanProgress(ReadingPlan.gidFromName("other"), any(), any()) }
        assertEquals(true, CurrentReading.getPlanCompletion())
        assertArrayEquals(intArrayOf(0x270106, 0x270110), CurrentReading.get())
        assertEquals(CurrentReading.Plan("original", 12, 3), CurrentReading.getPlan())
    }

    @Test
    fun `unticking a completed passage removes only that reading code and keeps it active`() {
        val gid = ReadingPlan.gidFromName("original")
        val code = (12 shl 8) or 3
        progress[gid] = mutableSetOf(code, code + 1)
        CurrentReading.setReadingPlan(0x270106, 0x270110, "original", 12, 3)
        assertEquals(true, CurrentReading.getPlanCompletion())
        assertTrue(CurrentReading.setPlanCompleted(false))
        verify(exactly = 1) { db.deleteReadingPlanProgress(gid, code) }
        assertEquals(setOf(code + 1), progress[gid])
        assertEquals(false, CurrentReading.getPlanCompletion())
        assertArrayEquals(intArrayOf(0x270106, 0x270110), CurrentReading.get())
    }

    @Test
    fun `completion survives reopening the reading and closing it does not erase progress`() {
        CurrentReading.setReadingPlan(0x270106, 0x270110, "original", 12, 3)
        CurrentReading.setPlanCompleted(true)
        CurrentReading.setReadingPlan(0x270106, 0x270110, "original", 12, 3)
        assertEquals(true, CurrentReading.getPlanCompletion())
        CurrentReading.clear()
        assertNull(CurrentReading.get())
        assertNull(CurrentReading.getPlanCompletion())
        verify(exactly = 0) { db.deleteReadingPlanProgress(any(), any()) }
        assertEquals(setOf((12 shl 8) or 3), progress[ReadingPlan.gidFromName("original")])
    }

    @Test
    fun `dismissal and devotional references never mark a reading plan complete`() {
        CurrentReading.setReadingPlan(0x270106, 0x270110, "original", 12, 3)
        CurrentReading.clear()
        assertFalse(CurrentReading.setPlanCompleted(true))
        CurrentReading.setRanges(intArrayOf(0x280905, 0x280906))
        assertFalse(CurrentReading.setPlanCompleted(true))
        assertNull(CurrentReading.getPlanCompletion())
        verify(exactly = 0) { db.insertOrUpdateReadingPlanProgress(any(), any(), any()) }
        verify(exactly = 0) { db.deleteReadingPlanProgress(any(), any()) }
    }

    @Test
    fun `a deleted reading plan cannot acquire new completion records`() {
        every { db.listReadingPlanNames() } returns emptyList()
        CurrentReading.setReadingPlan(0x270106, 0x270110, "deleted", 12, 3)
        assertFalse(CurrentReading.setPlanCompleted(true))
        assertFalse(CurrentReading.setPlanCompleted(false))
        assertNull(CurrentReading.getPlanCompletion())
        verify(exactly = 0) { db.insertOrUpdateReadingPlanProgress(any(), any(), any()) }
        verify(exactly = 0) { db.deleteReadingPlanProgress(any(), any()) }
    }
}
