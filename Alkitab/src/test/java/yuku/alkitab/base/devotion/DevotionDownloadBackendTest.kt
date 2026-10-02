package yuku.alkitab.base.devotion

import com.sun.net.httpserver.HttpServer
import java.io.IOException
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import yuku.alkitab.base.ac.DevotionActivity.DevotionKind
import yuku.alkitab.base.devotion.DevotionDownloader.Key
import yuku.alkitab.base.devotion.DevotionDownloader.State

class DevotionDownloadBackendTest {
    private val key = Key("me-en", "20260929")
    private val cache = mutableMapOf<Key, DevotionArticle>()
    private var output = "<h2>Morning</h2><p>Morning reading</p><h2>Evening</h2><p>Evening reading</p>"
    private var code = 200
    private var offline = false
    private var requests = 0
    private var cacheControl: String? = null
    private val client = OkHttpClient.Builder().addInterceptor { chain ->
        requests++
        cacheControl = chain.request().header("Cache-Control")
        if (offline) throw IOException("offline")
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
            .code(code).message("test").body(output.toResponseBody()).build()
    }.build()
    private val backend = DevotionDownloadBackend(
        client = { client }, url = { "http://localhost/devotion?name=${it.name}&date=${it.date}" },
        cached = { cache[it] }, store = { cache[Key(it.kind.name, it.date)] = it },
    )

    @Test fun `HTTP failure and offline errors are failures and retry stores the complete reading`() {
        code = 503
        assertEquals(State.FAILED, backend.createRequest(key, false).execute())
        assertTrue(cache.isEmpty())
        code = 200
        offline = true
        assertEquals(State.FAILED, backend.createRequest(key, false).execute())
        offline = false
        assertEquals(State.READY, backend.createRequest(key, true).execute())
        assertEquals(output, cache[key]!!.body)
        assertTrue(cache[key]!!.readyToUse)
    }

    @Test fun `NG is unavailable and explicit retry bypasses its cached row`() {
        output = "NG content is not published"
        assertEquals(State.UNAVAILABLE, backend.createRequest(key, false).execute())
        assertFalse(cache[key]!!.readyToUse)
        assertEquals(State.UNAVAILABLE, backend.createRequest(key, false).execute())
        assertEquals(1, requests)
        output = "<p>Published reading</p>"
        assertEquals(State.READY, backend.createRequest(key, true).execute())
        assertEquals(2, requests)
        assertEquals("no-cache", cacheControl)
    }

    @Test fun `network failures and NG refreshes leave a ready offline reading intact`() {
        assertEquals(State.READY, backend.createRequest(key, false).execute())
        val original = cache[key]!!.body
        offline = true
        assertEquals(State.FAILED, backend.createRequest(key, true).execute())
        assertEquals(original, cache[key]!!.body)
        offline = false
        output = "NG"
        assertEquals(State.UNAVAILABLE, backend.createRequest(key, true).execute())
        assertTrue(cache[key]!!.readyToUse)
        assertEquals(original, cache[key]!!.body)
    }

    @Test fun `all sources use cached readings without network access`() {
        DevotionKind.values().forEach { kind ->
            val sourceKey = Key(kind.name, key.date)
            cache[sourceKey] = kind.getArticle(key.date).apply { fillIn("<p>Cached ${kind.name}</p>") }
            assertEquals(State.READY, backend.createRequest(sourceKey, false).execute())
        }
        assertEquals(0, requests)
    }

    @Test fun `persistence failure ends in failed state`() {
        val broken = DevotionDownloadBackend(
            client = { client }, url = { "http://localhost/devotion" }, cached = { null },
            store = { throw IOException("disk full") },
        )
        assertEquals(State.FAILED, broken.createRequest(key, false).execute())
    }

    @Test fun `selecting a new date cancels a stalled OkHttp foreground and starts the new reading`() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val selectedStored = CountDownLatch(1)
        val localCache = ConcurrentHashMap<Key, DevotionArticle>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val serverExecutor = Executors.newCachedThreadPool()
        server.executor = serverExecutor
        server.createContext("/devotion") { exchange ->
            try {
                if (exchange.requestURI.path.endsWith("20260925")) {
                    exchange.sendResponseHeaders(200, 100)
                    exchange.responseBody.write('a'.code)
                    exchange.responseBody.flush()
                    started.countDown()
                    release.await(5, TimeUnit.SECONDS)
                } else {
                    val bytes = output.toByteArray()
                    exchange.sendResponseHeaders(200, bytes.size.toLong())
                    exchange.responseBody.write(bytes)
                }
            } finally { exchange.close() }
        }
        server.start()
        val localClient = OkHttpClient.Builder().callTimeout(10, TimeUnit.SECONDS).build()
        val localBackend = DevotionDownloadBackend(
            client = { localClient }, url = { "http://127.0.0.1:${server.address.port}/devotion/${it.date}" },
            cached = { localCache[it] }, store = {
                localCache[Key(it.kind.name, it.date)] = it
                selectedStored.countDown()
            },
        )
        val downloader = DevotionDownloader(localBackend)
        try {
            downloader.select("me-en", "20260925", true)
            assertTrue(started.await(5, TimeUnit.SECONDS))
            downloader.select(key.name, key.date, true)
            assertTrue(selectedStored.await(5, TimeUnit.SECONDS))
            assertEquals(output, localCache[key]!!.body)
            assertNull(localCache[Key("me-en", "20260925")])
            assertEquals(1L, release.count)
        } finally {
            release.countDown()
            downloader.shutdown()
            server.stop(0)
            serverExecutor.shutdownNow()
            localClient.connectionPool.evictAll()
        }
    }

    @Test fun `a stalled response body reaches the total call timeout without production access`() {
        val release = CountDownLatch(1)
        val started = CountDownLatch(1)
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val serverExecutor = Executors.newSingleThreadExecutor()
        server.executor = serverExecutor
        server.createContext("/devotion") { exchange ->
            try {
                exchange.sendResponseHeaders(200, 100)
                exchange.responseBody.write('a'.code)
                exchange.responseBody.flush()
                started.countDown()
                release.await(5, TimeUnit.SECONDS)
            } finally { exchange.close() }
        }
        server.start()
        val timedClient = OkHttpClient.Builder().callTimeout(1, TimeUnit.SECONDS).build()
        try {
            val local = DevotionDownloadBackend(
                client = { timedClient }, url = { "http://127.0.0.1:${server.address.port}/devotion" },
                cached = { null }, store = { fail("A stalled body must not be persisted") },
            )
            assertEquals(State.FAILED, local.createRequest(key, false).execute())
            assertEquals(0L, started.count)
        } finally {
            release.countDown()
            server.stop(0)
            serverExecutor.shutdownNow()
            timedClient.connectionPool.evictAll()
        }
    }
}
