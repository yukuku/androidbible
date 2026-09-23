package yuku.alkitab.base.pdbconvert

import org.junit.Assert.assertEquals
import org.junit.Test

class PdbBookNumberToBookIdMappingTest {
    @Test
    fun `protestant canon book numbers map to book ids 0 to 65`() {
        assertEquals(0, PdbBookNumberToBookIdMapping.pdbBookNumberToBookId(10))
        assertEquals(15, PdbBookNumberToBookIdMapping.pdbBookNumberToBookId(160))
        assertEquals(16, PdbBookNumberToBookIdMapping.pdbBookNumberToBookId(190))
        assertEquals(38, PdbBookNumberToBookIdMapping.pdbBookNumberToBookId(460))
        assertEquals(39, PdbBookNumberToBookIdMapping.pdbBookNumberToBookId(470))
        assertEquals(65, PdbBookNumberToBookIdMapping.pdbBookNumberToBookId(730))
    }

    @Test
    fun `deuterocanonical book numbers map to book ids 66 to 87`() {
        assertEquals(66, PdbBookNumberToBookIdMapping.pdbBookNumberToBookId(145))
        assertEquals(70, PdbBookNumberToBookIdMapping.pdbBookNumberToBookId(200))
        assertEquals(80, PdbBookNumberToBookIdMapping.pdbBookNumberToBookId(320))
        assertEquals(87, PdbBookNumberToBookIdMapping.pdbBookNumberToBookId(1802))
    }

    @Test
    fun `alternate book numbers map to the same book ids as their primary numbers`() {
        assertEquals(66, PdbBookNumberToBookIdMapping.pdbBookNumberToBookId(740))
        assertEquals(67, PdbBookNumberToBookIdMapping.pdbBookNumberToBookId(750))
        assertEquals(79, PdbBookNumberToBookIdMapping.pdbBookNumberToBookId(760))
        assertEquals(82, PdbBookNumberToBookIdMapping.pdbBookNumberToBookId(770))
        assertEquals(83, PdbBookNumberToBookIdMapping.pdbBookNumberToBookId(780))
    }

    @Test
    fun `unknown book numbers map to -1`() {
        assertEquals(-1, PdbBookNumberToBookIdMapping.pdbBookNumberToBookId(0))
        assertEquals(-1, PdbBookNumberToBookIdMapping.pdbBookNumberToBookId(15))
        assertEquals(-1, PdbBookNumberToBookIdMapping.pdbBookNumberToBookId(-10))
        assertEquals(-1, PdbBookNumberToBookIdMapping.pdbBookNumberToBookId(9999))
    }
}
