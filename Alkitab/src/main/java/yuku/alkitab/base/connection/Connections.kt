package yuku.alkitab.base.connection

import android.graphics.Bitmap
import android.os.Build
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.GooglePlayServicesNotAvailableException
import com.google.android.gms.common.GooglePlayServicesRepairableException
import com.google.android.gms.security.ProviderInstaller
import com.jakewharton.picasso.OkHttp3Downloader
import com.squareup.picasso.Picasso
import okhttp3.Cache
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.internal.Version
import yuku.alkitab.base.App
import yuku.alkitab.base.util.AppLog
import yuku.alkitab.debug.BuildConfig
import yuku.stethoshim.StethoShim
import java.io.File
import java.util.concurrent.TimeUnit

private const val TAG = "Connections"

object Connections {
    private val appContext by lazy { App.context }

    @JvmStatic
    val httpUserAgent by lazy {
        Version.userAgent() + " " + appContext.packageName + "/" + App.getVersionName()
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
        if (Build.VERSION.SDK_INT <= 21) {
            AppLog.d(TAG, "Android version ${Build.VERSION.SDK_INT}, accessing ProviderInstaller to ensure https connections work")
            try {
                ProviderInstaller.installIfNeeded(appContext)

                AppLog.d(TAG, "ProviderInstaller.installIfNeeded returns normally")

            } catch (e: GooglePlayServicesRepairableException) {
                AppLog.e(TAG, "ProviderInstaller.installIfNeeded throws GooglePlayServicesRepairableException", e)

                GoogleApiAvailability.getInstance()
                    .showErrorNotification(appContext, e.connectionStatusCode)

            } catch (e: GooglePlayServicesNotAvailableException) {
                AppLog.e(TAG, "ProviderInstaller.installIfNeeded throws GooglePlayServicesNotAvailableException", e)

                GoogleApiAvailability.getInstance()
                    .showErrorNotification(appContext, e.errorCode)
            }
        }

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
        StethoShim.addNetworkInterceptor(builder)
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
        StethoShim.addNetworkInterceptor(builder)
        builder.build()
    }

    @JvmStatic
    @get:JvmName("picasso")
    val picasso: Picasso by lazy {
        Picasso.Builder(appContext)
            .defaultBitmapConfig(Bitmap.Config.RGB_565)
            .downloader(OkHttp3Downloader(okHttp))
            .build()
    }
}
