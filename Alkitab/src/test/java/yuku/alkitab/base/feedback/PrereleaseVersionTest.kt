package yuku.alkitab.base.feedback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PrereleaseVersionTest {
    @Test
    fun `release and unknown stages do not offer prerelease feedback`() {
        listOf("5.0.0", "5.0.0-rc.1", "5.0.0-dev", "5.0.0-dev.x").forEach {
            assertNull(PrereleaseVersion.parse(it))
        }
    }

    @Test
    fun `beta feedback omits the beta number from its label`() {
        assertEquals(PrereleaseVersion("Beta", "Beta"), PrereleaseVersion.parse("5.0.0-beta.12"))
    }

    @Test
    fun `dev feedback includes the full development number`() {
        assertEquals(PrereleaseVersion("Dev", "Dev 123"), PrereleaseVersion.parse("5.0.0-dev.123"))
    }

    @Test
    fun `email subjects identify the channel app full version and commit`() {
        assertEquals(
            "Dev feedback Quick Bible 5.0.0-dev.123 (abc1234)",
            PrereleaseVersion.parse("5.0.0-dev.123")!!.emailSubject("Quick Bible", "5.0.0-dev.123", "abc1234"),
        )
        assertEquals(
            "Beta feedback Alkitab 5.0.0-beta.12 (def5678)",
            PrereleaseVersion.parse("5.0.0-beta.12")!!.emailSubject("Alkitab", "5.0.0-beta.12", "def5678"),
        )
    }
}
