package yuku.alkitab.base.sync

import java.io.IOException
import java.util.Collections
import java.util.IdentityHashMap
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object SyncDiagnostics {
    const val RETAINED_ROWS = 500
    const val MAX_ROWS = 600
    const val MAX_PARAM_BYTES = 4096
    const val MAX_FIELDS = 16

    @JvmStatic
    fun safeServerPrefix(value: String): String {
        if (value.length > MAX_PARAM_BYTES) return "[invalid URL]"
        val url = value.toHttpUrlOrNull() ?: return "[invalid URL]"
        return HttpUrl.Builder().scheme(url.scheme).host(url.host).port(url.port)
            .build().toString().removeSuffix("/")
    }

    @JvmStatic
    fun causes(error: Throwable): String {
        val seen = Collections.newSetFromMap(IdentityHashMap<Throwable, Boolean>())
        val names = mutableListOf<String>()
        var current: Throwable? = error
        while (current != null && names.size < 6 && seen.add(current)) {
            names += current.javaClass.name.take(80)
            current = current.cause
        }
        return names.joinToString(" > ").take(512)
    }

    @JvmStatic
    fun category(error: Throwable): String {
        var current: Throwable? = error
        repeat(6) {
            val name = current?.javaClass?.name ?: return "UNEXPECTED"
            when {
                name.contains("UnknownHost") -> return "DNS"
                name.contains("Timeout") -> return "TIMEOUT"
                name.contains("SSL") || name.contains("Certificate") -> return "TLS"
                name.contains("ConnectException") -> return "CONNECTION"
                name.contains("SQLite") -> return "DATABASE"
                name.contains("Json") -> return "JSON"
                name.contains("Interrupted") || name.contains("Cancellation") -> return "CANCELLED"
            }
            if (current?.cause == null) return if (current is IOException) "IO" else "UNEXPECTED"
            current = current?.cause
        }
        return "UNEXPECTED"
    }

    @JvmStatic
    fun httpCategory(status: Int): String = when (status) {
        401, 403 -> "AUTH"
        in 200..299 -> "HTTP_OK"
        in 400..499 -> "HTTP_CLIENT"
        in 500..599 -> "HTTP_SERVER"
        else -> "HTTP_OTHER"
    }
}
