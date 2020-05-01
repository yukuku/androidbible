package yuku.alkitab.base.util;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.os.Build;
import android.os.SystemClock;
import androidx.annotation.Keep;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import okhttp3.Call;
import okhttp3.FormBody;
import okhttp3.Request;
import yuku.afw.storage.Preferences;
import yuku.alkitab.base.App;
import yuku.alkitab.base.ac.HelpActivity;
import yuku.alkitab.base.connection.Connections;
import yuku.alkitab.base.storage.Prefkey;
import yuku.alkitab.base.widget.Localized;
import yuku.alkitab.debug.BuildConfig;
import yuku.alkitab.debug.R;

public abstract class Announce {
	static final String TAG = Announce.class.getSimpleName();

	public static final int AUTO_CHECK_INTERVAL_SECONDS = BuildConfig.DEBUG ? 60 : 86400 /* 1 days */;
	static final String NOTIFICATION_CHANNEL_ID = "announce";

	public static void checkAnnouncements() {
		final int lastCheck = Preferences.getInt(Prefkey.announce_last_check, 0);
		if (lastCheck != 0 && (Sqlitil.nowDateTime() - lastCheck) < AUTO_CHECK_INTERVAL_SECONDS) {
			AppLog.d(TAG, "@@checkAnnouncements exit because it was recently checked");
			return;
		}

		Background.run(() -> {
			try {
				SystemClock.sleep(10000); // wait 10 seconds
				checkAnnouncements_worker();
			} catch (Exception e) { // handle all exceptions, because we don't want the main app to crash because of this.
				AppLog.d(TAG, "@@checkAnnouncements", e);
			}
		});
	}

	@Keep
	static class Announcement {
		public long id;
		public String title;
		public int createTime;
	}

	@Keep
	static class AnnounceCheckResult {
		public boolean success;
		public String message;
		public Announcement[] announcements;
	}

	private static void checkAnnouncements_worker() throws Exception {
		final List<Announcement> unreadAnnouncements = new ArrayList<>();

		{
			final AnnounceCheckResult result = getAnnouncements();
			if (!result.success) {
				AppLog.d(TAG, "Announce check returns success=false: " + result.message);
				return;
			}

			if (result.announcements != null) {
				final Set<Long> read = getReadAnnouncementIds();
				for (final Announcement announcement : result.announcements) {
					if (!read.contains(announcement.id)) {
						unreadAnnouncements.add(announcement);
					}
				}
			}
		}

		if (unreadAnnouncements.size() == 0) {
			// success, but no new announcements
		} else {
			// sort announcements by createTime desc
			Collections.sort(unreadAnnouncements, (lhs, rhs) -> rhs.createTime - lhs.createTime);

			final long[] announcementIds = new long[unreadAnnouncements.size()];
			for (int i = 0; i < unreadAnnouncements.size(); i++) {
				announcementIds[i] = unreadAnnouncements.get(i).id;
			}

			if (Build.VERSION.SDK_INT >= 26) {
				final NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID, App.context.getString(R.string.notification_channel_announce_name), NotificationManager.IMPORTANCE_LOW);
				final NotificationManager nm = App.context.getSystemService(NotificationManager.class);
				if (nm != null) nm.createNotificationChannel(channel);
			}

			final NotificationCompat.Builder base = new NotificationCompat.Builder(App.context, NOTIFICATION_CHANNEL_ID)
				.setContentTitle(Localized.string(R.string.announce_notif_title, Localized.string(R.string.app_name)))
				.setContentText(unreadAnnouncements.size() == 1 ? unreadAnnouncements.get(0).title : Localized.string(R.string.announce_notif_number_new_announcements, unreadAnnouncements.size()))
				.setSmallIcon(R.drawable.ic_stat_announce)
				.setColor(ContextCompat.getColor(App.context, R.color.secondary))
				.setContentIntent(PendingIntent.getActivity(App.context, Arrays.hashCode(announcementIds), HelpActivity.createViewAnnouncementIntent(announcementIds), PendingIntent.FLAG_UPDATE_CURRENT))
				.setAutoCancel(true);

			final Notification n;
			if (unreadAnnouncements.size() == 1) {
				final NotificationCompat.BigTextStyle builder = new NotificationCompat.BigTextStyle(base)
					.setBigContentTitle(Localized.string(R.string.announce_notif_title, Localized.string(R.string.app_name)))
					.bigText(unreadAnnouncements.get(0).title);

				n = builder.build();
			} else {
				final NotificationCompat.InboxStyle builder = new NotificationCompat.InboxStyle(base)
					.setBigContentTitle(Localized.string(R.string.announce_notif_title, Localized.string(R.string.app_name)))
					.setSummaryText(Localized.string(R.string.announce_notif_number_new_announcements, unreadAnnouncements.size()));

				for (final Announcement announcement : unreadAnnouncements) {
					builder.addLine(announcement.title);
				}

				n = builder.build();
			}

			final NotificationManagerCompat nm = NotificationManagerCompat.from(App.context);
			nm.notify(R.id.NOTIF_announce, n);
		}

		Preferences.setInt(Prefkey.announce_last_check, Sqlitil.nowDateTime());
	}

	private static AnnounceCheckResult getAnnouncements() throws IOException {
		final Call call = Connections.getOkHttp().newCall(
			new Request.Builder()
				.url(BuildConfig.SERVER_HOST + "announce/check")
				.post(
					new FormBody.Builder()
						.add("installation_info", InstallationUtil.getInfoJson())
						.build()
				)
				.build()
		);

		return App.getDefaultGson().fromJson(call.execute().body().charStream(), AnnounceCheckResult.class);
	}

	public static long[] getAnnouncementIds() {
		try {
			final AnnounceCheckResult result = getAnnouncements();
			if (result.announcements == null) {
				AppLog.e(TAG, "@@getAnnouncementIds result.announcements == null");
				return null;
			}

			final long[] res = new long[result.announcements.length];
			for (int i = 0; i < result.announcements.length; i++) {
				res[i] = result.announcements[i].id;
			}

			return res;
		} catch (IOException e) {
			AppLog.e(TAG, "@@getAnnouncementIds", e);
			return null;
		}
	}

	public static Set<Long> getReadAnnouncementIds() {
		final Set<Long> res = new HashSet<>();
		final String s = Preferences.getString(Prefkey.announce_read_ids);
		if (s != null) {
			final long[] ids = App.getDefaultGson().fromJson(s, long[].class);
			for (final long id : ids) {
				res.add(id);
			}
		}
		return res;
	}

	public static void markAsRead(final long[] announcementIds) {
		if (announcementIds == null || announcementIds.length == 0) return;
		final Set<Long> read = getReadAnnouncementIds();
		for (final long id : announcementIds) {
			read.add(id);
		}
		Preferences.setString(Prefkey.announce_read_ids, App.getDefaultGson().toJson(announcementIds));
	}
}
