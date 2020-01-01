package yuku.alkitab.base.connection

import okhttp3.Cache
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.internal.Version
import yuku.alkitab.base.App
import yuku.alkitab.debug.BuildConfig
import yuku.stethoshim.StethoShim
import java.io.File
import java.util.concurrent.TimeUnit

object Connections {
    @JvmStatic
    val httpUserAgent by lazy {
        Version.userAgent() + " " + App.context.packageName + "/" + App.getVersionName()
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

    @JvmStatic
    val okHttp: OkHttpClient by lazy {
        val cacheDir = File(App.context.cacheDir, "okhttp-cache")
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
        val builder = OkHttpClient.Builder()
            .cache(Cache(cacheDir, 50 * 1024 * 1024))
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addNetworkInterceptor(UserAgentInterceptor())
        if (BuildConfig.DEBUG) {
            builder.hostnameVerifier { _, _ -> true }
        }
        StethoShim.addNetworkInterceptor(builder)
        EnableTls12.enableTls12OnPreLollipop(builder).build()
    }

    @JvmStatic
    val longTimeoutOkHttpClient: OkHttpClient by lazy {
        val builder = OkHttpClient.Builder()
        builder
            .addNetworkInterceptor(UserAgentInterceptor())
            .connectTimeout(300, TimeUnit.SECONDS)
            .readTimeout(300, TimeUnit.SECONDS)
            .writeTimeout(600, TimeUnit.SECONDS)
        StethoShim.addNetworkInterceptor(builder)
        EnableTls12.enableTls12OnPreLollipop(builder).build()
    }
}
