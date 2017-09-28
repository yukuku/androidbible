package yuku.alkitab.base.devotion;

import android.app.Notification;
import android.content.Intent;
import android.net.Uri;
import android.os.SystemClock;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v7.app.NotificationCompat;
import android.text.TextUtils;
import yuku.alkitab.base.App;
import yuku.alkitab.base.S;
import yuku.alkitab.base.ac.DevotionActivity;
import yuku.alkitab.base.util.AppLog;
import yuku.alkitab.base.util.Foreground;
import yuku.alkitab.base.widget.Localized;
import yuku.alkitab.debug.BuildConfig;
import yuku.alkitab.debug.R;

import java.io.IOException;
import java.util.LinkedList;

public class DevotionDownloader extends Thread {
	private static final String TAG = DevotionDownloader.class.getSimpleName();

	public static final String ACTION_DOWNLOADED = DevotionDownloader.class.getName() + ".action.DOWNLOADED";

	private final LinkedList<DevotionArticle> queue_ = new LinkedList<>();

	private static final String NOTIFY_TAG = "devotion_downloader";
	private static final int NOTIFY_ID = 0;
	NotificationManagerCompat nm;

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
					notifyFinished();

					synchronized (queue_) {
						queue_.wait();
					}
					AppLog.d(TAG, "Downloader is resumed");
				} catch (InterruptedException e) {
					AppLog.d(TAG, "Queue is interrupted");
				}
			} else {
				final DevotionActivity.DevotionKind kind = article.getKind();
				final String url = BuildConfig.SERVER_HOST + "devotion/get?name=" + kind.name + "&date=" + article.getDate() + "&app_versionCode=" + App.getVersionCode() + "&app_versionName=" + Uri.encode(App.getVersionName());

				AppLog.d(TAG, "Downloader starts downloading name=" + kind.name + " date=" + article.getDate());
				notifyDownloadStatus(
					TextUtils.expandTemplate(Localized.string(R.string.devotion_downloader_downloading_title), kind.title),
					TextUtils.expandTemplate(Localized.string(R.string.devotion_downloader_downloading_date), article.getDate())
				);

				try {
					final String output = App.downloadString(url);

					// success!
					article.fillIn(output);

					if (output.startsWith("NG")) {
						notifyDownloadStatus(
							TextUtils.expandTemplate(Localized.string(R.string.devotion_downloader_downloading_title), kind.title),
							TextUtils.expandTemplate(Localized.string(R.string.devotion_downloader_error_date), article.getDate(), output)
						);
					} else {
						notifyDownloadStatus(
							TextUtils.expandTemplate(Localized.string(R.string.devotion_downloader_downloading_title), kind.title),
							TextUtils.expandTemplate(Localized.string(R.string.devotion_downloader_success_date), article.getDate())
						);
						broadcastDownloaded(kind.name, article.getDate());
					}

					// let's now store it to db
					S.getDb().storeArticleToDevotions(article);
				} catch (IOException e) {
					AppLog.w(TAG, "@@run", e);

					notifyDownloadStatus(
						TextUtils.expandTemplate(Localized.string(R.string.devotion_downloader_downloading_title), kind.title),
						TextUtils.expandTemplate(Localized.string(R.string.devotion_downloader_error_date), String.valueOf(article.getDate()), String.valueOf(e.getMessage()))
					);
					AppLog.d(TAG, "Downloader failed to download");
				}
			}

			SystemClock.sleep(50);
		}
	}

	void notifyDownloadStatus(final CharSequence title, final CharSequence subtitle) {
		Foreground.run(() -> {
			if (nm == null) {
				nm = NotificationManagerCompat.from(App.context);
			}

			final Notification n = new NotificationCompat.Builder(App.context)
				.setContentTitle(title)
				.setContentText(subtitle)
				.setProgress(0, 0, true)
				.setSmallIcon(android.R.drawable.stat_sys_download)
				.setStyle(new NotificationCompat.BigTextStyle()
					.bigText(subtitle)
				)
				.build();

			nm.notify(NOTIFY_TAG, NOTIFY_ID, n);
		});
	}

	void broadcastDownloaded(final String name, final String date) {
		App.getLbm().sendBroadcast(new Intent(ACTION_DOWNLOADED).putExtra("name", name).putExtra("date", date));
	}

	void notifyFinished() {
		Foreground.run(() -> {
			if (nm == null) {
				nm = NotificationManagerCompat.from(App.context);
			}

			nm.cancel(NOTIFY_TAG, NOTIFY_ID);
		});
	}
}
