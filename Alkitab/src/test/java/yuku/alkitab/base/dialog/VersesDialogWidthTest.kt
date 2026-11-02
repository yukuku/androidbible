package yuku.alkitab.base.dialog

import org.junit.Assert.assertEquals
import org.junit.Test

class VersesDialogWidthTest {
    @Test
    fun `dialog width follows phone minimums preferred width and tablet maximum`() {
        val cases = listOf(
            320f to 256f,
            360f to 288f,
            400f to 320f,
            450f to 360f,
            500f to 360f,
            600f to 420f,
            800f to 560f,
            1000f to 640f,
        )
        for ((screenWidth, expected) in cases) {
            assertEquals("screen width $screenWidth", expected, versesDialogWidth(screenWidth), 0.01f)
        }
    }

    @Test
    fun `very narrow windows prioritize the outside margin when the requested bounds overlap`() {
        assertEquals(176f, versesDialogWidth(240f), 0.01f)
    }
}
