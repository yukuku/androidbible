package yuku.alkitab.base.util;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.os.SystemClock;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import gnu.trove.set.TLongSet;
import gnu.trove.set.hash.TLongHashSet;
import okhttp3.Call;
import okhttp3.FormBody;
import okhttp3.Request;
import yuku.afw.storage.Preferences;
import yuku.alkitab.base.App;
import yuku.alkitab.base.U;
import yuku.alkitab.base.ac.HelpActivity;
import yuku.alkitab.base.storage.Prefkey;
import yuku.alkitab.debug.BuildConfig;
import yuku.alkitab.debug.R;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public abstract class Announce {
	static final String TAG = Announce.class.getSimpleName();

	public static final int AUTO_CHECK_INTERVAL_SECONDS = BuildConfig.DEBUG ? 60 : 86400 /* 1 days */;

	public static void checkAnnouncements() {
		final int lastCheck = Preferences.getInt(Prefkey.announce_last_check, 0);
		if (lastCheck != 0 && (Sqlitil.nowDateTime() - lastCheck) < AUTO_CHECK_INTERVAL_SECONDS) {
			Log.d(TAG, "@@checkAnnouncements exit because it was recently checked");
			return;
		}

		Background.run(() -> {
			try {
				SystemClock.sleep(10000); // wait 10 seconds
				checkAnnouncements_worker();
			} catch (Exception e) { // handle all exceptions, because we don't want the main app to crash because of this.
				Log.d(TAG, "@@checkAnnouncements", e);
			}
		});
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
		final List<Announcement> unreadAnnouncements = new ArrayList<>();

		{
			final AnnounceCheckResult result = getAnnouncements();
			if (!result.success) {
				Log.d(TAG, "Announce check returns success=false: " + result.message);
				return;
			}

			if (result.announcements != null) {
				final TLongSet read = getReadAnnouncementIds();
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

			final NotificationCompat.Builder base = new NotificationCompat.Builder(App.context)
				.setContentTitle(App.context.getString(R.string.announce_notif_title, App.context.getString(R.string.app_name)))
				.setContentText(unreadAnnouncements.size() == 1 ? unreadAnnouncements.get(0).title : App.context.getString(R.string.announce_notif_number_new_announcements, unreadAnnouncements.size()))
				.setSmallIcon(R.drawable.ic_stat_announce)
				.setColor(ContextCompat.getColor(App.context, R.color.accent))
				.setContentIntent(PendingIntent.getActivity(App.context, Arrays.hashCode(announcementIds), HelpActivity.createViewAnnouncementIntent(announcementIds), PendingIntent.FLAG_UPDATE_CURRENT))
				.setAutoCancel(true);

			final Notification n;
			if (unreadAnnouncements.size() == 1) {
				final NotificationCompat.BigTextStyle builder = new NotificationCompat.BigTextStyle(base)
					.setBigContentTitle(App.context.getString(R.string.announce_notif_title, App.context.getString(R.string.app_name)))
					.bigText(unreadAnnouncements.get(0).title);

				n = builder.build();
			} else {
				final NotificationCompat.InboxStyle builder = new NotificationCompat.InboxStyle(base)
					.setBigContentTitle(App.context.getString(R.string.announce_notif_title, App.context.getString(R.string.app_name)))
					.setSummaryText(App.context.getString(R.string.announce_notif_number_new_announcements, unreadAnnouncements.size()));

				for (final Announcement announcement : unreadAnnouncements) {
					builder.addLine(announcement.title);
				}

				n = builder.build();
			}

			final NotificationManager nm = (NotificationManager) App.context.getSystemService(Context.NOTIFICATION_SERVICE);
			nm.notify(R.id.NOTIF_announce, n);
		}

		Preferences.setInt(Prefkey.announce_last_check, Sqlitil.nowDateTime());
	}

	private static AnnounceCheckResult getAnnouncements() throws IOException {
		final Call call = App.okhttp().newCall(
			new Request.Builder()
				.url(BuildConfig.SERVER_HOST + "announce/check")
				.post(
					new FormBody.Builder()
						.add("installation_info", U.getInstallationInfoJson())
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
				Log.e(TAG, "@@getAnnouncementIds result.announcements == null");
				return null;
			}

			final long[] res = new long[result.announcements.length];
			for (int i = 0; i < result.announcements.length; i++) {
				res[i] = result.announcements[i].id;
			}

			return res;
		} catch (IOException e) {
			Log.e(TAG, "@@getAnnouncementIds", e);
			return null;
		}
	}

	public static TLongSet getReadAnnouncementIds() {
		final TLongSet res = new TLongHashSet();
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
		final TLongSet read = getReadAnnouncementIds();
		for (final long id : announcementIds) {
			read.add(id);
		}
		Preferences.setString(Prefkey.announce_read_ids, App.getDefaultGson().toJson(announcementIds));
	}
}
