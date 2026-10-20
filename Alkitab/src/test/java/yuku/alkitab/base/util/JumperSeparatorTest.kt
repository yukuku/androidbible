package yuku.alkitab.base.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JumperSeparatorTest {
    private val logger = Jumper.Logger { }

    @Test
    fun `separator-only input is rejected without throwing`() {
        for (reference in listOf(":", "::", ".", "...", " : . : ", "\t:\t")) {
            val jumper = Jumper(reference, logger)

            assertFalse(reference, jumper.parseSucceeded)
        }
    }

    @Test
    fun `leading and trailing separators preserve a valid reference`() {
        for (reference in listOf(":John 3:16:", ".John 3.16.", " : John 3 : 16 : ")) {
            val jumper = Jumper(reference, logger)

            assertTrue(reference, jumper.parseSucceeded)
            assertEquals(reference, "John", jumper.unparsedBook)
            assertEquals(reference, 3, jumper.chapter)
            assertEquals(reference, 16, jumper.verse)
        }
    }
}
