package yuku.alkitab.base.util;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.os.SystemClock;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;
import com.squareup.okhttp.Call;
import com.squareup.okhttp.FormEncodingBuilder;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import yuku.afw.storage.Preferences;
import yuku.alkitab.base.App;
import yuku.alkitab.base.U;
import yuku.alkitab.base.ac.HelpActivity;
import yuku.alkitab.base.storage.Prefkey;
import yuku.alkitab.debug.BuildConfig;
import yuku.alkitab.debug.R;

import java.util.Arrays;

public abstract class Announce {
	static final String TAG = Announce.class.getSimpleName();

	public static final int AUTO_CHECK_INTERVAL_SECONDS = BuildConfig.DEBUG ? 60 : 86400 /* 1 days */;

	public static void checkAnnouncements() {
		final int lastCheck = Preferences.getInt(Prefkey.announce_last_check, 0);
		if (lastCheck != 0 && (Sqlitil.nowDateTime() - lastCheck) < AUTO_CHECK_INTERVAL_SECONDS) {
			Log.d(TAG, "@@checkAnnouncements exit because it was recently checked");
			return;
		}

		new Thread(() -> {
			try {
				SystemClock.sleep(10000); // wait 10 seconds
				checkAnnouncements_worker();
			} catch (Exception e) { // handle all exceptions, because we don't want the main app to crash because of this.
				Log.d(TAG, "@@checkAnnouncements", e);
			}
		}).start();
	}

	static class Announcement {
		public long id;
		public String title;
		public int createTime;
	}

	static class AnnounceCheckResult {
		public boolean success;
		public String message;
		public Announcement[] announcements;
	}

	private static void checkAnnouncements_worker() throws Exception {
		final OkHttpClient client = App.getOkHttpClient();
		final Call call = client.newCall(
			new Request.Builder()
				.url("https://alkitab-host.appspot.com/announce/check")
				.post(
					new FormEncodingBuilder()
						.add("installation_info", U.getInstallationInfoJson())
						.build()
				)
				.build()
		);

		final AnnounceCheckResult result = App.getDefaultGson().fromJson(call.execute().body().charStream(), AnnounceCheckResult.class);
		if (!result.success) {
			Log.d(TAG, "Announce check returns success=false: " + result.message);
			if (result.message != null) {
				Toast.makeText(App.context, "Announce: " + result.message, Toast.LENGTH_LONG).show();
			}
			return;
		}

		if (result.announcements == null || result.announcements.length == 0) {
			// success, but no new announcements
		} else {
			// sort announcements by createTime desc
			Arrays.sort(result.announcements, (lhs, rhs) -> rhs.createTime - lhs.createTime);

			final long[] announcementIds = new long[result.announcements.length];
			for (int i = 0; i < result.announcements.length; i++) {
				announcementIds[i] = result.announcements[i].id;
			}

			final NotificationCompat.Builder base = new NotificationCompat.Builder(App.context)
				.setContentTitle(App.context.getString(R.string.announce_notif_title, App.context.getString(R.string.app_name)))
				.setContentText(result.announcements.length == 1 ? result.announcements[0].title : App.context.getString(R.string.announce_notif_number_new_announcements, result.announcements.length))
				.setSmallIcon(R.drawable.ic_stat_announce)
				.setColor(App.context.getResources().getColor(R.color.accent))
				.setContentIntent(PendingIntent.getActivity(App.context, Arrays.hashCode(announcementIds), HelpActivity.createViewAnnouncementIntent(announcementIds), PendingIntent.FLAG_UPDATE_CURRENT))
				.setAutoCancel(true);

			final Notification n;
			if (result.announcements.length == 1) {
				final NotificationCompat.BigTextStyle builder = new NotificationCompat.BigTextStyle(base)
					.setBigContentTitle(App.context.getString(R.string.announce_notif_title, App.context.getString(R.string.app_name)))
					.bigText(result.announcements[0].title);

				n = builder.build();
			} else {
				final NotificationCompat.InboxStyle builder = new NotificationCompat.InboxStyle(base)
					.setBigContentTitle(App.context.getString(R.string.announce_notif_title, App.context.getString(R.string.app_name)))
					.setSummaryText(App.context.getString(R.string.announce_notif_number_new_announcements, result.announcements.length));

				for (final Announcement announcement : result.announcements) {
					builder.addLine(announcement.title);
				}

				n = builder.build();
			}

			final NotificationManager nm = (NotificationManager) App.context.getSystemService(Context.NOTIFICATION_SERVICE);
			nm.notify(R.id.NOTIF_announce, n);
		}

		Preferences.setInt(Prefkey.announce_last_check, Sqlitil.nowDateTime());
	}
}
