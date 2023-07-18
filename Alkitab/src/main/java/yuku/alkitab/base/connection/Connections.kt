package yuku.alkitab.base.connection

import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.Cache
import okhttp3.Call
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
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

    /**
     * If the response does not indicate successful, [IOException] will be thrown.
     */
    @JvmStatic
    @Throws(IOException::class)
    fun downloadString(url: String): String {
        val response = downloadCall(url).execute()
        if (!response.isSuccessful) throw IOException("response was not successful, code ${response.code}")
        val body = response.body ?: throw IOException("body is null")
        return body.string()
    }

    /**
     * If the response does not indicate successful, [IOException] will be thrown.
     */
    @JvmStatic
    @Throws(IOException::class)
    fun downloadBytes(url: String): ByteArray {
        val response = downloadCall(url).execute()
        if (!response.isSuccessful) throw IOException("response was not successful, code ${response.code}")
        val body = response.body ?: throw IOException("body is null")
        return body.bytes()
    }

    @JvmStatic
    fun downloadCall(url: String): Call {
        return okHttp.newCall(Request.Builder().url(url).build())
    }
}
