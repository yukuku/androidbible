package yuku.alkitab.base.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GotoButtonBalanceWrapTest {
	private val byLength = GotoButton.WidthMeasurer { _, start, end -> (end - start).toFloat() }

	@Test
	fun `text that fits the available width is returned unchanged on a single line`() {
		assertEquals("Kej 1", GotoButton.balanceWrap("Kej 1", 10f, byLength))
	}

	@Test
	fun `a three token reference that does not fit is split into two balanced lines keeping the chapter number`() {
		val out = GotoButton.balanceWrap("1 Tesalonika 2", 8f, byLength)
		assertEquals("1 Tesal\nonika 2", out)
		val lines = out.split("\n")
		assertEquals(2, lines.size)
		assertTrue(lines.all { it.length <= 8 })
		assertTrue(out.replace("\n", " ").endsWith("2"))
	}

	@Test
	fun `the split breaks mid word rather than only at whitespace when that balances better`() {
		val out = GotoButton.balanceWrap("Wahyu 22", 5f, byLength)
		val lines = out.split("\n")
		assertEquals(2, lines.size)
		assertTrue(lines.all { it.length <= 5 })
	}

	@Test
	fun `a leading space on the second line is trimmed when the split lands on whitespace`() {
		val out = GotoButton.balanceWrap("abc 123", 4f, byLength)
		assertEquals("abc\n123", out)
	}

	@Test
	fun `empty text is returned unchanged`() {
		assertEquals("", GotoButton.balanceWrap("", 5f, byLength))
	}
}
