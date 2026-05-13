package yuku.alkitab.base.devotion

import yuku.alkitab.base.App
import yuku.alkitab.base.ac.DevotionActivity
import yuku.alkitab.base.connection.Connections
import yuku.alkitab.base.events.AppEvents
import yuku.alkitab.base.util.AppLog
import yuku.alkitab.debug.BuildConfig
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingDeque

class DevotionDownloader {
    private val TAG = DevotionDownloader::class.java.simpleName

    private val queue = LinkedBlockingDeque<DevotionArticle>()

    @Volatile
    private var shutdown = false

    private val executor = Executors.newSingleThreadExecutor()

    init {
        executor.submit(::downloadLoop)
    }

    @Synchronized
    fun add(article: DevotionArticle, prioritize: Boolean): Boolean {
        if (shutdown) return false
        if (queue.contains(article)) return false

        if (prioritize) {
            queue.addFirst(article)
        } else {
            queue.addLast(article)
        }

        return true
    }

    fun shutdown() {
        shutdown = true
        executor.shutdownNow()
    }

    private fun downloadLoop() {
        while (!shutdown) {
            try {
                val article = queue.take()

                if (article.readyToUse) continue

                val kind: DevotionActivity.DevotionKind = article.kind
                val url = BuildConfig.SERVER_HOST + "/devotion/get?name=" + kind.name + "&date=" + article.date + "&" + App.getAppIdentifierParamsEncoded()

                AppLog.d(TAG, "Downloader starts downloading name=" + kind.name + " date=" + article.date)

                try {
                    val output = Connections.downloadString(url)
                    article.fillIn(output)
                    App.services.storage.db.storeArticleToDevotions(article)

                    if (!output.startsWith("NG")) {
                        AppEvents.emitDevotionDownloaded(kind.name, article.date)
                    }
                } catch (e: Exception) {
                    AppLog.d(TAG, "Downloader failed to process article", e)
                }
            } catch (e: InterruptedException) {
                AppLog.d(TAG, "Downloader interrupted")
                Thread.currentThread().interrupt()
                break
            }
        }
    }
}
