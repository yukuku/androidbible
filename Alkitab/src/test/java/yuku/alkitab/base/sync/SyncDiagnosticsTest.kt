package yuku.alkitab.base.sync

import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.junit.Assert.*
import org.junit.Test

class SyncDiagnosticsTest {
    @Test
    fun `cause chains preserve useful types without exception messages`() {
        val error = IllegalStateException("private note", UnknownHostException("secret.example.org"))
        assertEquals("DNS", SyncDiagnostics.category(error))
        assertEquals("java.lang.IllegalStateException > java.net.UnknownHostException", SyncDiagnostics.causes(error))
        assertEquals("TIMEOUT", SyncDiagnostics.category(SocketTimeoutException("secret URL")))
    }

    @Test
    fun `cause cycles have bounded output`() {
        val first = Exception("secret")
        val second = Exception("secret", first)
        first.initCause(second)
        assertTrue(SyncDiagnostics.causes(first).length < 512)
    }

    @Test
    fun `status categories distinguish authentication and server failures`() {
        assertEquals("AUTH", SyncDiagnostics.httpCategory(401))
        assertEquals("AUTH", SyncDiagnostics.httpCategory(403))
        assertEquals("HTTP_SERVER", SyncDiagnostics.httpCategory(503))
        assertEquals("HTTP_CLIENT", SyncDiagnostics.httpCategory(429))
        assertEquals("HTTP_OK", SyncDiagnostics.httpCategory(200))
    }
    @Test
    fun `server prefix keeps the origin without URL credentials or user data`() {
        val origin = SyncDiagnostics.safeServerPrefix(
            "https://person%40example.org:password@example.org:8443/private-note?token=secret#fragment")
        assertEquals("https://example.org:8443", origin)
        assertEquals("https://api.alkitab.app", SyncDiagnostics.safeServerPrefix("https://api.alkitab.app/"))
        assertEquals("[invalid URL]", SyncDiagnostics.safeServerPrefix("invalid URL secret"))
    }

}
