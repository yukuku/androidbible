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
        CurrentReading.setReadingPlan(intArrayOf(0x270106, 0x270110), "original", 12)
        assertEquals(false, CurrentReading.getPlanCompletion(0))
        assertTrue(CurrentReading.setPlanCompleted(true, 0))
        verify(exactly = 1) { db.insertOrUpdateReadingPlanProgress(ReadingPlan.gidFromName("original"), (12 shl 8), any()) }
        verify(exactly = 0) { db.insertOrUpdateReadingPlanProgress(ReadingPlan.gidFromName("other"), any(), any()) }
        assertEquals(true, CurrentReading.getPlanCompletion(0))
        assertArrayEquals(intArrayOf(0x270106, 0x270110), CurrentReading.getRanges())
        assertEquals(CurrentReading.Plan("original", 12), CurrentReading.getPlan())
    }

    @Test
    fun `unticking a completed passage removes only that reading code and keeps it active`() {
        val gid = ReadingPlan.gidFromName("original")
        val code = (12 shl 8)
        progress[gid] = mutableSetOf(code, code + 1)
        CurrentReading.setReadingPlan(intArrayOf(0x270106, 0x270110), "original", 12)
        assertEquals(true, CurrentReading.getPlanCompletion(0))
        assertTrue(CurrentReading.setPlanCompleted(false, 0))
        verify(exactly = 1) { db.deleteReadingPlanProgress(gid, code) }
        assertEquals(setOf(code + 1), progress[gid])
        assertEquals(false, CurrentReading.getPlanCompletion(0))
        assertArrayEquals(intArrayOf(0x270106, 0x270110), CurrentReading.getRanges())
    }

    @Test
    fun `completion survives reopening the reading and closing it does not erase progress`() {
        CurrentReading.setReadingPlan(intArrayOf(0x270106, 0x270110), "original", 12)
        CurrentReading.setPlanCompleted(true, 0)
        CurrentReading.setReadingPlan(intArrayOf(0x270106, 0x270110), "original", 12)
        assertEquals(true, CurrentReading.getPlanCompletion(0))
        CurrentReading.clear()
        assertNull(CurrentReading.getRanges())
        assertNull(CurrentReading.getPlanCompletion(0))
        verify(exactly = 0) { db.deleteReadingPlanProgress(any(), any()) }
        assertEquals(setOf((12 shl 8)), progress[ReadingPlan.gidFromName("original")])
    }

    @Test
    fun `dismissal and devotional references never mark a reading plan complete`() {
        CurrentReading.setReadingPlan(intArrayOf(0x270106, 0x270110), "original", 12)
        CurrentReading.clear()
        assertFalse(CurrentReading.setPlanCompleted(true, 0))
        CurrentReading.setRanges(intArrayOf(0x280905, 0x280906))
        assertFalse(CurrentReading.setPlanCompleted(true, 0))
        assertNull(CurrentReading.getPlanCompletion(0))
        verify(exactly = 0) { db.insertOrUpdateReadingPlanProgress(any(), any(), any()) }
        verify(exactly = 0) { db.deleteReadingPlanProgress(any(), any()) }
    }

    @Test
    fun `a deleted reading plan cannot acquire new completion records`() {
        every { db.listReadingPlanNames() } returns emptyList()
        CurrentReading.setReadingPlan(intArrayOf(0x270106, 0x270110), "deleted", 12)
        assertFalse(CurrentReading.setPlanCompleted(true, 0))
        assertFalse(CurrentReading.setPlanCompleted(false, 0))
        assertNull(CurrentReading.getPlanCompletion(0))
        verify(exactly = 0) { db.insertOrUpdateReadingPlanProgress(any(), any(), any()) }
        verify(exactly = 0) { db.deleteReadingPlanProgress(any(), any()) }
    }

    @Test
    fun `daily passages retain independent saved progress when another plan or day has matching sequences`() {
        val ranges = intArrayOf(0x000100, 0x000300, 0x120101, 0x120106, 0x270101, 0x27010a)
        val gid = ReadingPlan.gidFromName("original")
        progress[gid] = mutableSetOf((12 shl 8) or 1, (13 shl 8) or 2)
        progress[ReadingPlan.gidFromName("other")] = mutableSetOf((12 shl 8) or 0)
        CurrentReading.setReadingPlan(ranges, "original", 12)
        assertArrayEquals(booleanArrayOf(false, true, false), CurrentReading.getPlanCompletions())
        assertTrue(CurrentReading.setPlanCompleted(true, 2))
        assertTrue(CurrentReading.setPlanCompleted(false, 1))
        assertArrayEquals(booleanArrayOf(false, false, true), CurrentReading.getPlanCompletions())
        assertEquals(setOf((12 shl 8) or 2, (13 shl 8) or 2), progress[gid])
        assertEquals(setOf((12 shl 8) or 0), progress[ReadingPlan.gidFromName("other")])
        assertArrayEquals(ranges, CurrentReading.getRanges())
    }

    @Test
    fun `invalid row indices cannot change plan completion`() {
        CurrentReading.setReadingPlan(intArrayOf(0x000100, 0x000300, 0x120101, 0x120106), "original", 12)
        for (index in listOf(-1, 2, Int.MAX_VALUE)) {
            assertFalse(CurrentReading.setPlanCompleted(true, index))
            assertNull(CurrentReading.getPlanCompletion(index))
        }
        verify(exactly = 0) { db.insertOrUpdateReadingPlanProgress(any(), any(), any()) }
    }
}
