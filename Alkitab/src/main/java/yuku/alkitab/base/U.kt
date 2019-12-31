package yuku.alkitab.base

import java.io.IOException
import java.io.InputStream

object U {

    @JvmStatic
    fun equals(a: Any?, b: Any?): Boolean {
        return a == b
    }

    @JvmStatic
    @Throws(IOException::class)
    fun inputStreamUtf8ToString(input: InputStream): String {
        return input.reader().readText()
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
