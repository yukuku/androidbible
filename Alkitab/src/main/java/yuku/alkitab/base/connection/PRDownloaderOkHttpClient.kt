package yuku.alkitab.base.connection

import com.downloader.Constants
import com.downloader.httpclient.HttpClient
import com.downloader.request.DownloadRequest
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.io.InputStream
import java.util.Locale

class PRDownloaderOkHttpClient(private val okHttpClient: OkHttpClient) : HttpClient {
    var response: Response? = null

    override fun clone(): PRDownloaderOkHttpClient {
        return PRDownloaderOkHttpClient(okHttpClient)
    }

    override fun connect(request: DownloadRequest) {
        val range = String.format(Locale.US, "bytes=%d-", request.downloadedBytes)

        val req = Request.Builder()
            .url(request.url)
            .addHeader(Constants.RANGE, range)
            .addHeader(Constants.USER_AGENT, request.userAgent)
            .build()

        response = okHttpClient.newCall(req).execute()
    }

    override fun getResponseCode(): Int {
        return response?.code ?: 0
    }

    override fun getInputStream(): InputStream {
        return response?.body?.byteStream() ?: throw IOException("response or body is null")
    }

    override fun getContentLength(): Long {
        return response?.header("Content-Length")?.toLong() ?: -1
    }

    override fun getResponseHeader(name: String): String? {
        return response?.header(name)
    }

    override fun close() {
        response?.close()
    }
}
