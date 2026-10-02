package yuku.alkitab.base.util

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertFalse
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
import yuku.alkitab.base.storage.InternalDb

@RunWith(RobolectricTestRunner::class)
@Config(application = yuku.afw.App::class, sdk = [34])
class CurrentReadingCompletionTest {
    private lateinit var previousServices: AppServices
    private lateinit var db: InternalDb

    @Before
    fun setUp() {
        previousServices = App.services
        db = mockk(relaxed = true)
        App.services = AppServices(mockk { every { this@mockk.db } returns this@CurrentReadingCompletionTest.db }, mockk(), mockk())
        CurrentReading.clear()
    }

    @After
    fun tearDown() {
        CurrentReading.clear()
        App.services = previousServices
    }

    @Test
    fun `completing a passage updates only its original plan day and sequence then dismisses it`() {
        every { db.listReadingPlanNames() } returns listOf("original", "other")
        CurrentReading.setReadingPlan(0x270106, 0x270110, "original", 12, 3)
        assertTrue(CurrentReading.completePlan())
        verify(exactly = 1) { db.insertOrUpdateReadingPlanProgress(ReadingPlan.gidFromName("original"), (12 shl 8) or 3, any()) }
        verify(exactly = 0) { db.insertOrUpdateReadingPlanProgress(ReadingPlan.gidFromName("other"), any(), any()) }
        assertNull(CurrentReading.get())
        assertNull(CurrentReading.getPlan())
    }

    @Test
    fun `dismissal and devotional references never mark a reading plan complete`() {
        CurrentReading.setReadingPlan(0x270106, 0x270110, "original", 12, 3)
        CurrentReading.clear()
        assertFalse(CurrentReading.completePlan())
        CurrentReading.setRanges(intArrayOf(0x280905, 0x280906))
        assertFalse(CurrentReading.completePlan())
        verify(exactly = 0) { db.insertOrUpdateReadingPlanProgress(any(), any(), any()) }
    }

    @Test
    fun `a deleted reading plan cannot acquire new completion records`() {
        every { db.listReadingPlanNames() } returns emptyList()
        CurrentReading.setReadingPlan(0x270106, 0x270110, "deleted", 12, 3)
        assertFalse(CurrentReading.completePlan())
        verify(exactly = 0) { db.insertOrUpdateReadingPlanProgress(any(), any(), any()) }
    }
}
