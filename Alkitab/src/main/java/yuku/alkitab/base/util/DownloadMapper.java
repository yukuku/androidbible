package yuku.alkitab.base.util;

import android.app.DownloadManager;
import android.app.Notification;
import android.content.Context;
import android.content.Intent;
import android.support.annotation.NonNull;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v7.app.NotificationCompat;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.util.Log;
import com.downloader.Error;
import com.downloader.OnDownloadListener;
import com.downloader.PRDownloader;
import com.downloader.Status;
import com.downloader.request.DownloadRequest;
import yuku.alkitab.base.App;
import yuku.alkitab.base.ac.AlertDialogActivity;
import yuku.alkitab.base.ac.VersionsActivity;
import yuku.alkitab.base.br.VersionDownloadCompleteReceiver;
import yuku.alkitab.debug.R;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

public enum DownloadMapper {
	instance;

	static final String TAG = DownloadMapper.class.getSimpleName();
	static final String NOTIF_TAG = "DownloadMapper";

	static class Row {
		public int id;
		public String key;
		public String title;
		public String destPath;
		public long currentBytes;
		public long totalBytes;
		public Map<String, String> attrs;
	}

	final Map<String, Row> currentByKey = new LinkedHashMap<>();
	final Map<Integer, Row> currentById = new LinkedHashMap<>();

	public int getStatus(final String downloadKey) {
		final Row row = currentByKey.get(downloadKey);
		return getStatus(row);
	}

	public int getStatus(final int id) {
		final Row row = currentById.get(id);
		return getStatus(row);
	}

	static int prToDmStatus(Status status) {
		switch (status) {
			case QUEUED:
				return DownloadManager.STATUS_PENDING;
			case RUNNING:
				return DownloadManager.STATUS_RUNNING;
			case PAUSED:
				return DownloadManager.STATUS_PAUSED;
			case COMPLETED:
				return DownloadManager.STATUS_SUCCESSFUL;
			case CANCELLED:
				return DownloadManager.STATUS_FAILED;
		}
		return 0;
	}

	private int getStatus(final Row row) {
		if (row == null) {
			return 0;
		} else {
			final Status status = PRDownloader.getStatus(row.id);
			if (status == Status.UNKNOWN || status == null) {
				// stale data found. Remove immediately
				currentByKey.remove(row.key);
				currentById.remove(row.id);
				return 0;
			} else {
				return prToDmStatus(status);
			}
		}
	}

	/** Must be called only after verifying that this id exists. */
	public Map<String, String> getAttrs(final int id) {
		final Row row = currentById.get(id);
		return row.attrs;
	}

	/** Must be called only after verifying that this id exists. */
	public String getDownloadedFilePath(final int id) {
		final Row row = currentById.get(id);
		return row.destPath;
	}

	static String downloadTempDirPath() {
		final File cacheDir = App.context.getCacheDir();
		final File res = new File(cacheDir, "DownloadMapper-tmp");
		//noinspection ResultOfMethodCallIgnored
		res.mkdirs();
		return res.getAbsolutePath();
	}

	@NonNull
	static String downloadTempBasename(final String downloadKey) {
		return "DownloadMapper-" + downloadKey + ".tmp";
	}

	public void enqueue(final String downloadKey, final String url, final String title, final Map<String, String> attrs) {
		final String downloadTempDirPath = downloadTempDirPath();
		final String downloadTempBasename = downloadTempBasename(downloadKey);

		final DownloadRequest req = PRDownloader.download(url, downloadTempDirPath, downloadTempBasename).build();

		final Row row = new Row();

		req.setOnProgressListener(progress -> {
			Log.d(TAG, "@@onProgress " + progress.currentBytes + "/" + progress.totalBytes);
			row.currentBytes = progress.currentBytes;
			row.totalBytes = progress.totalBytes;

			displayNotifs();
		});

		final int[] p_id = {0};
		p_id[0] = req.start(new OnDownloadListener() {
			@Override
			public void onDownloadComplete() {
				final int id = p_id[0];
				VersionDownloadCompleteReceiver.onReceive(id);
			}

			@Override
			public void onError(final Error error) {
				final int id = p_id[0];

				final CharSequence msg;
				if (error.isConnectionError()) {
					msg = TextUtils.expandTemplate(App.context.getString(R.string.version_download_network_error), title);
				} else {
					msg = TextUtils.expandTemplate(App.context.getString(R.string.version_download_server_error), title);
				}

				App.context.startActivity(
					AlertDialogActivity.createOkIntent(null, msg)
						.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
				);

				remove(id);

				App.getLbm().sendBroadcast(new Intent(VersionsActivity.VersionListFragment.ACTION_RELOAD));
			}
		});

		row.id = p_id[0];
		row.key = downloadKey;
		row.title = title;
		row.destPath = new File(downloadTempDirPath, downloadTempBasename).getAbsolutePath();
		row.attrs = new LinkedHashMap<>(attrs);
		currentByKey.put(downloadKey, row);
		currentById.put(row.id, row);
		displayNotifs();
	}

	void displayNotifs() {
		final Context context = App.context;
		final NotificationManagerCompat nmc = NotificationManagerCompat.from(context);

		for (final Row row : new ArrayList<>(currentById.values())) {
			final Notification n = new NotificationCompat.Builder(context)
				.setSmallIcon(android.R.drawable.stat_sys_download)
				.setContentTitle(row.title)
				.setContentText(row.totalBytes <= 0 ? "" : (Formatter.formatFileSize(context, row.currentBytes) + " / " + Formatter.formatFileSize(context, row.totalBytes)))
				.setShowWhen(false)
				.setProgress(1000, row.totalBytes == 0 ? 0 : (int) (1000 * row.currentBytes / row.totalBytes), false)
				.setOngoing(true)
				.build();

			nmc.notify(NOTIF_TAG, row.id, n);
		}
	}

	/**
	 * Stop and remove from in-memory list and notification.
	 */
	public void remove(final int id) {
		PRDownloader.cancel(id);

		final NotificationManagerCompat nmc = NotificationManagerCompat.from(App.context);
		nmc.cancel(NOTIF_TAG, id);

		final Row row = currentById.get(id);
		if (row != null) {
			currentByKey.remove(row.key);
			currentById.remove(row.id);
		}
	}
}
