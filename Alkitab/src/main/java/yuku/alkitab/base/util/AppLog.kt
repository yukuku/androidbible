package yuku.alkitab.base.util

import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics

object AppLog {
    private val crashlytics = FirebaseCrashlytics.getInstance()

    @JvmStatic
    @JvmOverloads
    fun d(tag: String?, msg: String?, ex: Throwable? = null) {
        val t = tag ?: "NO-TAG"
        val m = msg ?: "null"
        if (ex == null) {
            Log.d(t, m)
        } else {
            Log.d(t, m, ex)
        }
        crashlytics.log("D/$t: $m")
        if (ex != null) crashlytics.recordException(ex)
    }

    @JvmStatic
    @JvmOverloads
    fun i(tag: String?, msg: String?, ex: Throwable? = null) {
        val t = tag ?: "NO-TAG"
        val m = msg ?: "null"
        if (ex == null) {
            Log.i(t, m)
        } else {
            Log.i(t, m, ex)
        }
        crashlytics.log("I/$t: $m")
        if (ex != null) crashlytics.recordException(ex)
    }

    @JvmStatic
    @JvmOverloads
    fun w(tag: String?, msg: String?, ex: Throwable? = null) {
        val t = tag ?: "NO-TAG"
        val m = msg ?: "null"
        if (ex == null) {
            Log.w(t, m)
        } else {
            Log.w(t, m, ex)
        }
        crashlytics.log("W/$t: $m")
        if (ex != null) crashlytics.recordException(ex)
    }

    @JvmStatic
    @JvmOverloads
    fun e(tag: String?, msg: String?, ex: Throwable? = null) {
        val t = tag ?: "NO-TAG"
        val m = msg ?: "null"
        if (ex == null) {
            Log.e(t, m)
        } else {
            Log.e(t, m, ex)
        }
        crashlytics.log("E/$t: $m")
        if (ex != null) crashlytics.recordException(ex)
    }
}
