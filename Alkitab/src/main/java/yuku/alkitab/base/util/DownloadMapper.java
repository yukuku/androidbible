package yuku.alkitab.base.util;

import android.app.DownloadManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import com.downloader.Error;
import com.downloader.OnDownloadListener;
import com.downloader.PRDownloader;
import com.downloader.Status;
import com.downloader.request.DownloadRequest;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import yuku.alkitab.base.App;
import yuku.alkitab.base.ac.AlertDialogActivity;
import yuku.alkitab.base.br.VersionDownloadCompleteReceiver;
import yuku.alkitab.debug.R;
import yuku.alkitab.versionmanager.VersionListFragment;

public enum DownloadMapper {
    instance;

    static final String TAG = DownloadMapper.class.getSimpleName();
    static final String NOTIF_TAG = "DownloadMapper";
    static final String NOTIFICATION_CHANNEL_ID = "download_mapper";

    static class Row {
        public int id;
        public String key;
        public String title;
        public String destPath;
        public long currentBytes;
        public long totalBytes;
        /**
         * When is the last time the progress is notified (in currentTimeMillis).
         * We should not notify about progress too often.
         */
        public long previouslyNotifiedTime;
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

    public float getDownloadProgress(final String downloadKey) {
        final Row row = currentByKey.get(downloadKey);
        return getDownloadProgress(row);
    }

    public float getDownloadProgress(final int id) {
        final Row row = currentById.get(id);
        return getDownloadProgress(row);
    }

    static int prToDmStatus(Status status) {
        return switch (status) {
            case QUEUED -> DownloadManager.STATUS_PENDING;
            case RUNNING -> DownloadManager.STATUS_RUNNING;
            case PAUSED -> DownloadManager.STATUS_PAUSED;
            case COMPLETED -> DownloadManager.STATUS_SUCCESSFUL;
            case CANCELLED -> DownloadManager.STATUS_FAILED;
            default -> 0;
        };
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

    private float getDownloadProgress(final Row row) {
        if (row == null) {
            return -1.f;
        } else {
            return PRDownloader.getDownloadProgress(row.id);
        }
    }

    /**
     * Must be called only after verifying that this id exists.
     */
    public Map<String, String> getAttrs(final int id) {
        final Row row = currentById.get(id);
        return row.attrs;
    }

    /**
     * Must be called only after verifying that this id exists.
     */
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
        // Return value must be filename-safe.
        // So I will remove all non-safe characters and append the hashcode of the downloadKey.
        final StringBuilder safe = new StringBuilder(downloadKey.length() + 40);
        for (char c : downloadKey.toCharArray()) {
            if ('A' <= c && c <= 'Z' || 'a' <= c && c <= 'z' || '0' <= c && c <= '9') {
                safe.append(c);
            }
        }

        return "DownloadMapper-" + safe + downloadKey.hashCode() + ".tmp";
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

            // only notify if last notification is not recent enough.
            final long now = System.currentTimeMillis();
            if (row.previouslyNotifiedTime == 0 || now - row.previouslyNotifiedTime > 1000) {
                row.previouslyNotifiedTime = now;

                displayNotifs();
                App.getLbm().sendBroadcast(new Intent(VersionListFragment.ACTION_RELOAD));
            }
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

                AppLog.e(TAG, "@@onError", error.getException());

                if (error.isConnectionError()) {
                    msg = TextUtils.expandTemplate(App.context.getString(R.string.version_download_network_error), title);
                } else {
                    msg = TextUtils.expandTemplate(App.context.getString(R.string.version_download_server_error), title);
                }

                App.context.startActivity(
                    AlertDialogActivity.createOkIntent(null, msg.toString())
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                );

                remove(id);

                App.getLbm().sendBroadcast(new Intent(VersionListFragment.ACTION_RELOAD));
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

        final NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID, context.getString(R.string.notification_channel_download_mapper_name), NotificationManager.IMPORTANCE_LOW);
        final NotificationManager nm = App.context.getSystemService(NotificationManager.class);
        if (nm != null) nm.createNotificationChannel(channel);

        final NotificationManagerCompat nmc = NotificationManagerCompat.from(context);
        for (final Row row : new ArrayList<>(currentById.values())) {
            final Notification n = new NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
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
