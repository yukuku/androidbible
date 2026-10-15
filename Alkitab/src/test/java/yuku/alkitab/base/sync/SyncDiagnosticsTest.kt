package yuku.alkitab.base.sync

import org.junit.Assert.*
import org.junit.Test
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class SyncDiagnosticsTest {
    @Test
    fun `untrusted content and URLs are omitted rather than partially redacted`() {
        val secret = "person@example.org token=secret https://user:password@host/path?token=secret private note"
        val safe = SyncDiagnostics.safeParams(mapOf("email" to secret, "password" to secret,
            "simpleToken" to secret, "Authorization" to secret, "message" to secret,
            "payload" to secret, "stage" to secret, "duration_ms" to secret))
        assertTrue(safe.isEmpty())
        assertEquals("unknown", SyncDiagnostics.safeSet(secret))
    }

    @Test
    fun `cause chains preserve useful types without exception messages`() {
        val error = IllegalStateException("private note", UnknownHostException("secret.example.org"))
        assertEquals("DNS", SyncDiagnostics.category(error))
        assertEquals("java.lang.IllegalStateException > java.net.UnknownHostException", SyncDiagnostics.causes(error))
        assertEquals("TIMEOUT", SyncDiagnostics.category(SocketTimeoutException("secret URL")))
    }

    @Test
    fun `cause cycles and oversized fields have bounded output`() {
        val first = Exception("secret")
        val second = Exception("secret", first)
        first.initCause(second)
        assertTrue(SyncDiagnostics.causes(first).length < 512)
        assertFalse(SyncDiagnostics.safeParams(mapOf("causes" to "A".repeat(513))).containsKey("causes"))
        assertFalse(SyncDiagnostics.safeParams(mapOf("app_version" to "1".repeat(65))).containsKey("app_version"))
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
    fun `account email remains available in copied diagnostic metadata`() {
        assertEquals(mapOf("email" to "person@example.org", "accountName" to "person@example.org"),
            SyncDiagnostics.safeParams(mapOf("email" to "person@example.org", "accountName" to "person@example.org")))
    }

    @Test
    fun `server prefix keeps the origin without URL credentials or user data`() {
        val params = SyncDiagnostics.safeParams(mapOf("serverPrefix" to
            "https://person%40example.org:password@example.org:8443/private-note?token=secret#fragment"))
        assertEquals(mapOf("serverPrefix" to "https://example.org:8443"), params)
        assertEquals("https://api.alkitab.app", SyncDiagnostics.safeServerPrefix("https://api.alkitab.app/"))
        assertEquals("[invalid URL]", SyncDiagnostics.safeServerPrefix("invalid URL secret"))
    }

}
