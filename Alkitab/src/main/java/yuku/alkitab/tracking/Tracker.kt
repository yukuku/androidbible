package yuku.alkitab.tracking

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import java.util.concurrent.Executors


object Tracker {
    private lateinit var fa: FirebaseAnalytics

    private val eventSubmitter by lazy { Executors.newSingleThreadExecutor() }

    @JvmStatic
    fun init(context: Context) {
        if (!::fa.isInitialized) {
            fa = FirebaseAnalytics.getInstance(context)
        }
    }

    @JvmStatic
    fun trackEvent(category: String, vararg paramPairs: Any) {
        var bundle: Bundle? = null
        if (paramPairs.isNotEmpty()) {
            if (paramPairs.size % 2 != 0) {
                throw IllegalArgumentException("Param pairs should have even number of elements. Got: $paramPairs")
            }

            bundle = Bundle()
            for (i in 0 until paramPairs.size step 2) {
                val key = paramPairs[i] as String
                val value = paramPairs[i + 1]
                when (value) {
                    is Int -> bundle.putInt(key, value)
                    is Double -> bundle.putDouble(key, value)
                    is Float -> bundle.putDouble(key, value.toDouble())
                    else -> bundle.putString(key, value.toString())
                }
            }
        }

        fa.logEvent(category, bundle)
    }
}
