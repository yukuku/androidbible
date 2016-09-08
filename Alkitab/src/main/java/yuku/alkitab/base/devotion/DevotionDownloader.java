package yuku.alkitab.base.devotion;

import android.content.Intent;
import android.net.Uri;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;
import yuku.alkitab.base.App;
import yuku.alkitab.base.S;
import yuku.alkitab.base.ac.DevotionActivity;
import yuku.alkitab.debug.BuildConfig;
import yuku.alkitab.debug.R;

import java.io.IOException;
import java.util.LinkedList;

public class DevotionDownloader extends Thread {
	private static final String TAG = DevotionDownloader.class.getSimpleName();

	public static final String ACTION_DOWNLOAD_STATUS = DevotionDownloader.class.getName() + ".action.DOWNLOAD_STATUS";
	public static final String ACTION_DOWNLOADED = DevotionDownloader.class.getName() + ".action.DOWNLOADED";
	public static final String ACTION_QUEUE_FINISHED = DevotionDownloader.class.getName() + ".action.QUEUE_FINISHED";

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
					broadcastQueueFinished();

					synchronized (queue_) {
						queue_.wait();
					}
					Log.d(TAG, "Downloader is resumed");
				} catch (InterruptedException e) {
					Log.d(TAG, "Queue is interrupted");
				}
			} else {
				final DevotionActivity.DevotionKind kind = article.getKind();
				final String url = BuildConfig.SERVER_HOST + "devotion/get?name=" + kind.name + "&date=" + article.getDate() + "&app_versionCode=" + App.getVersionCode() + "&app_versionName=" + Uri.encode(App.getVersionName());

				Log.d(TAG, "Downloader starts downloading name=" + kind.name + " date=" + article.getDate());
				broadcastDownloadStatus(
					TextUtils.expandTemplate(App.context.getString(R.string.devotion_downloader_downloading_title), kind.title),
					TextUtils.expandTemplate(App.context.getString(R.string.devotion_downloader_downloading_date), article.getDate())
				);

				try {
					final String output = App.downloadString(url);

					// success!
					article.fillIn(output);

					if (output.startsWith("NG")) {
						broadcastDownloadStatus(
							TextUtils.expandTemplate(App.context.getString(R.string.devotion_downloader_downloading_title), kind.title),
							TextUtils.expandTemplate(App.context.getString(R.string.devotion_downloader_error_date), article.getDate(), output)
						);
					} else {
						broadcastDownloadStatus(
							TextUtils.expandTemplate(App.context.getString(R.string.devotion_downloader_downloading_title), kind.title),
							TextUtils.expandTemplate(App.context.getString(R.string.devotion_downloader_success_date), article.getDate())
						);
						broadcastDownloaded(kind.name, article.getDate());
					}

					// let's now store it to db
					S.getDb().storeArticleToDevotions(article);
				} catch (IOException e) {
					Log.w(TAG, "@@run", e);

					broadcastDownloadStatus(
						TextUtils.expandTemplate(App.context.getString(R.string.devotion_downloader_downloading_title), kind.title),
						TextUtils.expandTemplate(App.context.getString(R.string.devotion_downloader_error_date), article.getDate(), e.getMessage())
					);
					Log.d(TAG, "Downloader failed to download");
				}
			}

			SystemClock.sleep(50);
		}
	}

	void broadcastDownloadStatus(final CharSequence title, final CharSequence subtitle) {
		App.getLbm().sendBroadcast(new Intent(ACTION_DOWNLOAD_STATUS).putExtra("title", title).putExtra("subtitle", subtitle));
	}

	void broadcastDownloaded(final String name, final String date) {
		App.getLbm().sendBroadcast(new Intent(ACTION_DOWNLOADED).putExtra("name", name).putExtra("date", date));
	}

	void broadcastQueueFinished() {
		App.getLbm().sendBroadcast(new Intent(ACTION_QUEUE_FINISHED));
	}
}
