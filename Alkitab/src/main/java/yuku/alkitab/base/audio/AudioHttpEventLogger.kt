package yuku.alkitab.base.audio

import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import okhttp3.Call
import okhttp3.Connection
import okhttp3.EventListener
import okhttp3.Handshake
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import yuku.alkitab.debug.R

/**
 * Turns every OkHttp connection-lifecycle callback into a log event via
 * [onEvent].
 *
 * One instance is created per HTTP call, and media3's `OkHttpDataSource`
 * issues a call per byte-range fetch, so a single chapter load produces
 * several full DNS-to-body sequences as the player buffers ahead.
 */
internal class AudioHttpEventLogger(private val onEvent: (AudioLogMessage) -> Unit) : EventListener() {

    /**
     * True once [connectEnd] fires for this call. [Connection] exposes no
     * "is this new" flag, and [connectStart] and [connectEnd] only run when a
     * connection is actually being established, so their absence before
     * [connectionAcquired] is what identifies a pooled connection.
     */
    private var establishedNewConnection = false

    private fun log(resId: Int, vararg args: Any) = onEvent(AudioLogMessage(resId, args.toList()))

    override fun callStart(call: Call) {
        log(R.string.audio_log_http_started, call.request().url.toString())
    }

    override fun dnsStart(call: Call, domainName: String) {
        log(R.string.audio_log_dns_start, domainName)
    }

    override fun dnsEnd(call: Call, domainName: String, inetAddressList: List<InetAddress>) {
        log(R.string.audio_log_dns_end, inetAddressList.joinToString { it.hostAddress ?: it.toString() })
    }

    override fun connectStart(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy) {
        log(R.string.audio_log_connecting, inetSocketAddress.hostString, inetSocketAddress.port)
    }

    override fun secureConnectStart(call: Call) {
        log(R.string.audio_log_tls_start)
    }

    override fun secureConnectEnd(call: Call, handshake: Handshake?) {
        val tlsVersion = handshake?.tlsVersion
        if (tlsVersion != null) {
            log(R.string.audio_log_tls_end_version, tlsVersion.javaName)
        } else {
            log(R.string.audio_log_tls_end)
        }
    }

    override fun connectEnd(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy, protocol: Protocol?) {
        establishedNewConnection = true
        if (protocol != null) {
            log(R.string.audio_log_connected_protocol, protocol.toString())
        } else {
            log(R.string.audio_log_connected)
        }
    }

    override fun connectFailed(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy, protocol: Protocol?, ioe: IOException) {
        log(R.string.audio_log_connect_failed, ioe.describe())
    }

    override fun connectionAcquired(call: Call, connection: Connection) {
        log(
            if (establishedNewConnection) {
                R.string.audio_log_connection_acquired_new
            } else {
                R.string.audio_log_connection_acquired_reused
            }
        )
    }

    override fun connectionReleased(call: Call, connection: Connection) {
        log(R.string.audio_log_connection_released)
    }

    override fun requestHeadersStart(call: Call) {
        log(R.string.audio_log_request_headers_start)
    }

    override fun requestHeadersEnd(call: Call, request: Request) {
        val range = request.header("Range")
        if (range != null) {
            log(R.string.audio_log_request_headers_sent_range, range)
        } else {
            log(R.string.audio_log_request_headers_sent)
        }
    }

    override fun requestFailed(call: Call, ioe: IOException) {
        log(R.string.audio_log_request_failed, ioe.describe())
    }

    override fun responseHeadersStart(call: Call) {
        log(R.string.audio_log_response_headers_start)
    }

    override fun responseHeadersEnd(call: Call, response: Response) {
        log(R.string.audio_log_response_headers_end, response.code)
        if (!response.isSuccessful) logErrorBody(response)
    }

    /**
     * `peekBody` buffers a copy and leaves the original body for the player to
     * consume, since this listener must not drain the stream the data source
     * is about to read. A failed peek is reported rather than thrown: losing a
     * log line must not become a second failure on top of the one being
     * reported.
     */
    private fun logErrorBody(response: Response) {
        val body = try {
            response.peekBody(MAX_ERROR_BODY_BYTES.toLong()).bytes()
        } catch (e: IOException) {
            log(R.string.audio_log_error_body_unreadable, e.describe())
            return
        }
        formatErrorBody(body)?.let { log(R.string.audio_log_response_body_text, it) }
    }

    override fun responseFailed(call: Call, ioe: IOException) {
        log(R.string.audio_log_response_failed, ioe.describe())
    }

    override fun responseBodyStart(call: Call) {
        log(R.string.audio_log_response_body_start)
    }

    override fun responseBodyEnd(call: Call, byteCount: Long) {
        log(R.string.audio_log_response_body_end, byteCount)
    }

    override fun callEnd(call: Call) {
        log(R.string.audio_log_call_end)
    }

    override fun callFailed(call: Call, ioe: IOException) {
        log(R.string.audio_log_call_failed, ioe.describe())
    }
}

/** Message text for a failure, falling back to the class name when there is none. */
private fun IOException.describe(): String = message ?: javaClass.simpleName
