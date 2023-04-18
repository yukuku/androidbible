package yuku.alkitab.base.connection

import java.io.File
import java.util.concurrent.TimeUnit
import okhttp3.Cache
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import yuku.alkitab.base.App
import yuku.alkitab.debug.BuildConfig

object Connections {
    private val appContext by lazy { App.context }

    @JvmStatic
    val httpUserAgent by lazy {
        appContext.packageName + "/" + App.getVersionName()
    }

    class UserAgentInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val originalRequest = chain.request()
            val requestWithUserAgent = originalRequest.newBuilder()
                .removeHeader("User-Agent")
                .addHeader("User-Agent", httpUserAgent)
                .build()
            return chain.proceed(requestWithUserAgent)
        }
    }

    private fun newOkHttpClientBuilder(): OkHttpClient.Builder {
        return OkHttpClient.Builder()
    }

    @JvmStatic
    val okHttp: OkHttpClient by lazy {
        val cacheDir = File(appContext.cacheDir, "okhttp-cache")
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
        val builder = newOkHttpClientBuilder()
            .cache(Cache(cacheDir, 50 * 1024 * 1024))
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addNetworkInterceptor(UserAgentInterceptor())
        if (BuildConfig.DEBUG) {
            builder.hostnameVerifier { _, _ -> true }
        }
        builder.build()
    }

    @JvmStatic
    val longTimeoutOkHttpClient: OkHttpClient by lazy {
        val builder = newOkHttpClientBuilder()
        builder
            .addNetworkInterceptor(UserAgentInterceptor())
            .connectTimeout(300, TimeUnit.SECONDS)
            .readTimeout(300, TimeUnit.SECONDS)
            .writeTimeout(600, TimeUnit.SECONDS)
        builder.build()
    }
}
