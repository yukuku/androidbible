package yuku.alkitab.base.sync

import java.util.Collections
import java.util.IdentityHashMap

/** Values from servers, exceptions and accounts are untrusted, even in legacy log rows. */
object SyncDiagnostics {
    const val MAX_ROWS = 500
    const val MAX_FIELDS = 16
    private val numericKeys = setOf("base_revno", "client_delta_operations_size", "client_entities_size",
        "client_delta_operations_size_original", "client_delta_operations_size_chopped", "duration_ms",
        "final_revno", "append_delta_operations_size", "insert_count", "update_count", "delete_count",
        "http_status", "attempt", "sdk", "version_code", "set_count", "io_errors", "parse_errors", "auth_errors")
    private val stages = setOf("request", "schedule", "worker", "prerequisites", "gather", "serialize",
        "http", "parse", "apply", "complete", "fcm")
    private val categories = setOf("DNS", "TIMEOUT", "TLS", "CONNECTION", "IO", "DATABASE", "JSON",
        "CANCELLED", "UNEXPECTED", "AUTH", "HTTP_CLIENT", "HTTP_SERVER", "HTTP_OK", "HTTP_OTHER")

    @JvmStatic
    fun safeSet(value: String?): String? = value?.let {
        if (it in setOf("mabel", "history", "pins", "rp")) it else "unknown"
    }

    @JvmStatic
    fun safeParams(input: Map<String, *>?): Map<String, Any> {
        val result = linkedMapOf<String, Any>()
        input?.entries?.take(MAX_FIELDS)?.forEach { (key, value) ->
            val safe: Any? = when {
                key in numericKeys && value is Number -> value.toLong()
                key in setOf("forced", "network_required", "is_new_registration_id", "custom_server", "is_sync_successful") && value is Boolean -> value
                key == "stage" && value in stages -> value
                key == "category" && value in categories -> value
                key == "reason" && value in setOf("not_logged_in", "updates_ongoing", "unknown_set", "empty_response", "empty_request") -> value
                key == "apply_result" && value in setOf("ok", "unknown_kind", "dirty_entities", "dirty_sync_account", "unsupported_operation") -> value
                key == "state" && value in setOf("ENQUEUED", "RUNNING", "SUCCEEDED", "FAILED", "BLOCKED", "CANCELLED") -> value
                key == "work_id" && value is String && value.matches(Regex("[0-9a-fA-F-]{36}")) -> value
                key in setOf("app_version", "android") && value is String && value.length <= 64 && value.matches(Regex("[0-9][0-9A-Za-z._-]*")) -> value
                key == "causes" && value is String && value.length <= 512 && value.matches(Regex("[A-Za-z0-9_.$ >]+")) -> value
                else -> null
            }
            if (safe != null) result[key] = safe
        }
        return result
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
            if (current?.cause == null) return if (current is java.io.IOException) "IO" else "UNEXPECTED"
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
