package yuku.alkitab.base

import android.content.Intent
import yuku.alkitab.base.util.AppLog
import java.io.IOException
import java.io.InputStream

private const val TAG = "U"

object U {

    @JvmStatic
    fun equals(a: Any?, b: Any): Boolean {
        if (a === b) return true
        return if (a == null) false else a == b
    }

    @JvmStatic
    @Throws(IOException::class)
    fun inputStreamUtf8ToString(input: InputStream): String {
        return input.reader().readText()
    }

    @JvmStatic
    fun dumpIntent(intent: Intent, via: String) {
        AppLog.d(TAG, "Got intent via $via")
        AppLog.d(TAG, "  action: ${intent.action}")
        AppLog.d(TAG, "  data uri: ${intent.data}")
        AppLog.d(TAG, "  component: ${intent.component}")
        AppLog.d(TAG, "  flags: 0x${Integer.toHexString(intent.flags)}")
        AppLog.d(TAG, "  mime: ${intent.type}")
        val extras = intent.extras
        AppLog.d(TAG, "  extras: ${extras?.size() ?: "null"}")
        if (extras != null) {
            for (key in extras.keySet()) {
                AppLog.d(TAG, "    $key = ${extras.get(key)}")
            }
        }
    }

    interface ThrowEverythingRunnable {
        @Throws(Exception::class)
        fun run()
    }

    @JvmStatic
    fun wontThrow(r: ThrowEverythingRunnable) {
        try {
            r.run()
        } catch (e: Exception) {
            throw RuntimeException("ThrowEverythingRunnable is passed but caused exception: $r", e)
        }
    }
}
