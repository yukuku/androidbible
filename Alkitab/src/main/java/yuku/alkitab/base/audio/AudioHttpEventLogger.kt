package yuku.alkitab.base.audio

import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import okhttp3.Call
import okhttp3.Connection
import okhttp3.EventListener
import okhttp3.Handshake
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response

/**
 * [EventListener] that turns every OkHttp connection-lifecycle callback into a
 * human-readable log line via [onEvent] — DNS lookup, TCP connect, TLS
 * handshake, request/response header and body phases, and every failure mode
 * along the way. Attached to a per-[BibleAudioPlayer] [okhttp3.OkHttpClient]
 * (see [BibleAudioPlayer]) so this never touches the app's shared client.
 *
 * One instance is created per HTTP call (media3's `OkHttpDataSource` issues a
 * new call per byte-range fetch), so a single chapter load can produce several
 * full DNS→body event sequences as the player buffers ahead — that granularity
 * is the point: the audio bar's status line and log sheet are meant to show
 * exactly what the HTTP layer is doing while a load is stuck.
 */
internal class AudioHttpEventLogger(private val onEvent: (String) -> Unit) : EventListener() {

    override fun callStart(call: Call) {
        onEvent("HTTP request started: ${call.request().url}")
    }

    override fun dnsStart(call: Call, domainName: String) {
        onEvent("DNS lookup started ($domainName)")
    }

    override fun dnsEnd(call: Call, domainName: String, inetAddressList: List<java.net.InetAddress>) {
        onEvent("DNS lookup finished: ${inetAddressList.joinToString { it.hostAddress ?: it.toString() }}")
    }

    override fun connectStart(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy) {
        onEvent("Connecting to ${inetSocketAddress.hostString}:${inetSocketAddress.port}")
    }

    override fun secureConnectStart(call: Call) {
        onEvent("TLS handshake starting")
    }

    override fun secureConnectEnd(call: Call, handshake: Handshake?) {
        onEvent("TLS handshake completed" + (handshake?.tlsVersion?.let { " (${it.javaName})" } ?: ""))
    }

    override fun connectEnd(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy, protocol: Protocol?) {
        onEvent("Connected" + (protocol?.let { " ($it)" } ?: ""))
    }

    override fun connectFailed(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy, protocol: Protocol?, ioe: IOException) {
        onEvent("Connect failed: ${ioe.message ?: ioe.javaClass.simpleName}")
    }

    override fun connectionAcquired(call: Call, connection: Connection) {
        onEvent("Connection acquired" + if (connection.newConnection()) " (new)" else " (reused)")
    }

    override fun connectionReleased(call: Call, connection: Connection) {
        onEvent("Connection released")
    }

    override fun requestHeadersStart(call: Call) {
        onEvent("Sending request headers")
    }

    override fun requestHeadersEnd(call: Call, request: Request) {
        val range = request.header("Range")
        onEvent("Request headers sent" + (range?.let { " (Range: $it)" } ?: ""))
    }

    override fun requestFailed(call: Call, ioe: IOException) {
        onEvent("Request failed: ${ioe.message ?: ioe.javaClass.simpleName}")
    }

    override fun responseHeadersStart(call: Call) {
        onEvent("Waiting for response headers")
    }

    override fun responseHeadersEnd(call: Call, response: Response) {
        onEvent("Response headers received: HTTP ${response.code}")
    }

    override fun responseFailed(call: Call, ioe: IOException) {
        onEvent("Response failed: ${ioe.message ?: ioe.javaClass.simpleName}")
    }

    override fun responseBodyStart(call: Call) {
        onEvent("Receiving response body")
    }

    override fun responseBodyEnd(call: Call, byteCount: Long) {
        onEvent("Response body received ($byteCount bytes)")
    }

    override fun callEnd(call: Call) {
        onEvent("HTTP request finished")
    }

    override fun callFailed(call: Call, ioe: IOException) {
        onEvent("HTTP request failed: ${ioe.message ?: ioe.javaClass.simpleName}")
    }
}
