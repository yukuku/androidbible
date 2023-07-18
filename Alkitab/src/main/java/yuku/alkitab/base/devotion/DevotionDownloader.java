package yuku.alkitab.base.devotion;

import android.content.Intent;
import android.os.SystemClock;
import java.io.IOException;
import java.util.LinkedList;
import yuku.alkitab.base.App;
import yuku.alkitab.base.S;
import yuku.alkitab.base.ac.DevotionActivity;
import yuku.alkitab.base.connection.Connections;
import yuku.alkitab.base.util.AppLog;
import yuku.alkitab.debug.BuildConfig;

public class DevotionDownloader extends Thread {
    private static final String TAG = DevotionDownloader.class.getSimpleName();

    public static final String ACTION_DOWNLOADED = DevotionDownloader.class.getName() + ".action.DOWNLOADED";

    private final LinkedList<DevotionArticle> queue_ = new LinkedList<>();

    public synchronized boolean add(DevotionArticle article, boolean prioritize) {
        if (queue_.contains(article)) return false;

        if (prioritize) {
            queue_.addFirst(article);
        } else {
            queue_.add(article);
        }

        if (!isAlive()) {
            start();
        }

        resumeDownloading();

        return true;
    }

    private synchronized DevotionArticle dequeue() {
        while (true) {
            if (queue_.size() == 0) {
                return null;
            }

            DevotionArticle article = queue_.getFirst();
            queue_.removeFirst();

            if (article.getReadyToUse()) {
                continue;
            }

            return article;
        }
    }

    void resumeDownloading() {
        synchronized (queue_) {
            queue_.notify();
        }
    }

    @Override
    public void run() {
        //noinspection InfiniteLoopStatement
        while (true) {
            final DevotionArticle article = dequeue();

            if (article == null) {
                try {
                    synchronized (queue_) {
                        queue_.wait();
                    }
                    AppLog.d(TAG, "Downloader is resumed");
                } catch (InterruptedException e) {
                    AppLog.d(TAG, "Queue is interrupted");
                }
            } else {
                final DevotionActivity.DevotionKind kind = article.getKind();
                final String url = BuildConfig.SERVER_HOST + "devotion/get?name=" + kind.name + "&date=" + article.getDate() + "&" + App.getAppIdentifierParamsEncoded();

                AppLog.d(TAG, "Downloader starts downloading name=" + kind.name + " date=" + article.getDate());

                try {
                    final String output = Connections.downloadString(url);

                    // success!
                    article.fillIn(output);

                    // let's now store it to db
                    S.getDb().storeArticleToDevotions(article);

                    if (!output.startsWith("NG")) {
                        broadcastDownloaded(kind.name, article.getDate());
                    }
                } catch (IOException e) {
                    AppLog.d(TAG, "Downloader failed to download", e);
                }
            }

            SystemClock.sleep(50);
        }
    }

    void broadcastDownloaded(final String name, final String date) {
        final Intent intent = new Intent(ACTION_DOWNLOADED)
            .putExtra("name", name)
            .putExtra("date", date);

        App.getLbm().sendBroadcast(intent);
    }
}
