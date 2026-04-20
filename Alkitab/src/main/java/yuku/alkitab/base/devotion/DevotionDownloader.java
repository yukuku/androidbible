package yuku.alkitab.base.devotion;

import android.content.Intent;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import yuku.alkitab.base.App;
import yuku.alkitab.base.S;
import yuku.alkitab.base.ac.DevotionActivity;
import yuku.alkitab.base.connection.Connections;
import yuku.alkitab.base.util.AppLog;
import yuku.alkitab.debug.BuildConfig;

public class DevotionDownloader {
    private static final String TAG = DevotionDownloader.class.getSimpleName();

    public static final String ACTION_DOWNLOADED = DevotionDownloader.class.getName() + ".action.DOWNLOADED";

    private final LinkedBlockingDeque<DevotionArticle> queue_ = new LinkedBlockingDeque<>();
    private volatile boolean shutdown_ = false;
    private final ExecutorService executor_ = Executors.newSingleThreadExecutor();

    public DevotionDownloader() {
        executor_.submit(this::downloadLoop);
    }

    public synchronized boolean add(DevotionArticle article, boolean prioritize) {
        if (shutdown_) return false;
        if (queue_.contains(article)) return false;

        if (prioritize) {
            queue_.addFirst(article);
        } else {
            queue_.addLast(article);
        }

        return true;
    }

    public void shutdown() {
        shutdown_ = true;
        executor_.shutdownNow();
    }

    private void downloadLoop() {
        while (!shutdown_) {
            try {
                final DevotionArticle article = queue_.take();

                if (article.getReadyToUse()) continue;

                final DevotionActivity.DevotionKind kind = article.getKind();
                final String url = BuildConfig.SERVER_HOST + "/devotion/get?name=" + kind.name + "&date=" + article.getDate() + "&" + App.getAppIdentifierParamsEncoded();

                AppLog.d(TAG, "Downloader starts downloading name=" + kind.name + " date=" + article.getDate());

                try {
                    final String output = Connections.downloadString(url);
                    article.fillIn(output);
                    S.getDb().storeArticleToDevotions(article);

                    if (!output.startsWith("NG")) {
                        broadcastDownloaded(kind.name, article.getDate());
                    }
                } catch (IOException e) {
                    AppLog.d(TAG, "Downloader failed to download", e);
                }
            } catch (InterruptedException e) {
                AppLog.d(TAG, "Downloader interrupted");
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void broadcastDownloaded(final String name, final String date) {
        final Intent intent = new Intent(ACTION_DOWNLOADED)
            .putExtra("name", name)
            .putExtra("date", date);

        App.getLbm().sendBroadcast(intent);
    }
}
