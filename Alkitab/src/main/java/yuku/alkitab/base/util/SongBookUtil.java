package yuku.alkitab.base.util;

import android.app.Activity;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.Html;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.view.Menu;
import android.view.View;
import android.widget.PopupMenu;
import com.afollestad.materialdialogs.MaterialDialog;
import com.squareup.okhttp.Call;
import yuku.alkitab.base.App;
import yuku.alkitab.base.S;
import yuku.alkitab.base.storage.SongDb;
import yuku.alkitab.debug.R;
import yuku.alkitab.io.OptionalGzipInputStream;
import yuku.kpri.model.Song;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class SongBookUtil {
	public static final String TAG = SongBookUtil.class.getSimpleName();

	private static final int POPUP_ID_ALL = -1;
	private static final int POPUP_ID_MORE = -2;

	public interface OnSongBookSelectedListener {
		void onAllSelected();
		void onSongBookSelected(String name);
		void onMoreSelected();
	}

	public static abstract class DefaultOnSongBookSelectedListener implements OnSongBookSelectedListener {
		@Override
		public void onAllSelected() {}

		@Override
		public void onSongBookSelected(final String name) {}

		@Override
		public void onMoreSelected() {}
	}

	public interface OnDownloadSongBookListener {
		void onDownloadedAndInserted(SongBookInfo songBookInfo);
		void onFailedOrCancelled(SongBookInfo songBookInfo, Exception e);
	}

	public static class SongBookInfo {
		public String name;
		public String title;
		public String copyright;
	}

	public static boolean isSupportedDataFormatVersion(final int dataFormatVersion) {
		return dataFormatVersion == 3;
	}

	/**
	 * For migration
	 */
	@Nullable
	public static SongBookInfo getSongBookInfo(@NonNull SQLiteDatabase db, @NonNull final String bookName) {
		final SongBookInfo info = SongDb.getSongBookInfo(db, bookName);

		return fallbackSongBookInfo(bookName, info);
	}

	@Nullable
	public static SongBookInfo getSongBookInfo(@NonNull final String bookName) {
		final SongBookInfo info = S.getSongDb().getSongBookInfo(bookName);

		return fallbackSongBookInfo(bookName, info);
	}

	@Nullable
	static SongBookInfo fallbackSongBookInfo(final @NonNull String bookName, final SongBookInfo info) {
		if (info != null) {
			return info;
		}

		final String title;
		switch (bookName) {
			case "BE": title = "Buku Ende"; break;
			case "KJ": title = "Kidung Jemaat"; break;
			case "KPKA": title = "Kidung Pasamuan Kristen Anyar"; break;
			case "KPKL": title = "Kidung Pasamuan Kristen Lawas"; break;
			case "KPPK": title = "Kidung Puji-Pujian Kristen"; break;
			case "KPRI": title = "Kidung Persekutuan Reformed Injili"; break;
			case "NKB": title = "Nyanyikanlah Kidung Baru"; break;
			case "NKI": title = "Nyanyian Kemenangan Iman"; break;
			case "NP": title = "Nyanyian Pujian"; break;
			case "NR": title = "Nafiri Rohani"; break;
			case "PKJ": title = "Pelengkap Kidung Jemaat"; break;
			case "PPK": title = "Puji-pujian Kristen"; break;
			default: title = null;
		}

		if (title == null) {
			return null;
		} else {
			final SongBookInfo fallback = new SongBookInfo();
			fallback.name = bookName;
			fallback.title = title;
			fallback.copyright = null;
			return fallback;
		}
	}

	public static PopupMenu getSongBookPopupMenu(Context context, boolean withAll, boolean withMore, View anchor) {
		final PopupMenu res = new PopupMenu(context, anchor);
		final Menu menu = res.getMenu();

		if (withAll) {
			final SpannableStringBuilder sb = new SpannableStringBuilder(context.getString(R.string.sn_bookselector_all) + '\n');
			int sb_len = sb.length();
			sb.append(context.getString(R.string.sn_bookselector_all_desc));
			sb.setSpan(new RelativeSizeSpan(0.7f), sb_len, sb.length(), 0);
			sb.setSpan(new ForegroundColorSpan(0xffa0a0a0), sb_len, sb.length(), 0);
			menu.add(0, POPUP_ID_ALL, 0, sb);
		}

		final List<SongBookInfo> infos = S.getSongDb().listSongBookInfos();
		for (int i = 0; i < infos.size(); i++) {
			final SongBookInfo info = infos.get(i);
			final SpannableStringBuilder sb = new SpannableStringBuilder(info.name + '\n');
			int sb_len = sb.length();
			sb.append(Html.fromHtml(info.title));
			sb.setSpan(new RelativeSizeSpan(0.7f), sb_len, sb.length(), 0);
			sb.setSpan(new ForegroundColorSpan(0xffa0a0a0), sb_len, sb.length(), 0);
			menu.add(0, i + 1, 0, sb);
		}

		if (withMore) {
			menu.add(0, POPUP_ID_MORE, 0, R.string.sn_bookselector_more);
		}

		return res;
	}

	public static String getCopyright(final String bookName) {
		final SongBookInfo info = getSongBookInfo(bookName);
		if (info == null) return null;
		return info.copyright;
	}

	public static PopupMenu.OnMenuItemClickListener getSongBookOnMenuItemClickListener(final OnSongBookSelectedListener listener) {
		return item -> {
			final int itemId = item.getItemId();
			switch (itemId) {
				case POPUP_ID_ALL:
					listener.onAllSelected();
					break;
				case POPUP_ID_MORE:
					listener.onMoreSelected();
					break;
				default:
					listener.onSongBookSelected(S.getSongDb().listSongBookInfos().get(itemId - 1).name);
					break;
			}
			return true;
		};
	}

	public static void downloadSongBook(final Activity activity, final SongBookInfo songBookInfo, final int dataFormatVersion, final OnDownloadSongBookListener listener) {
		final AtomicBoolean cancelled = new AtomicBoolean();

		MaterialDialog pd = new MaterialDialog.Builder(activity)
			.content(R.string.sn_downloading_ellipsis)
			.progress(true, 0)
			.dismissListener(dialog -> cancelled.set(true))
			.show();

		new Thread(() -> {
			try {
				final Call call = App.downloadCall("https://alkitab-host.appspot.com/addon/songs/get_songs?name=" + songBookInfo.name + "&dataFormatVersion=" + dataFormatVersion);

				final InputStream ogis = new OptionalGzipInputStream(call.execute().body().byteStream());
				final ObjectInputStream ois = new ObjectInputStream(ogis);
				@SuppressWarnings("unchecked") final List<Song> songs = (List<Song>) ois.readObject();
				ois.close();

				if (cancelled.get()) {
					listener.onFailedOrCancelled(songBookInfo, null);
					return;
				}

				// insert songs to db
				S.getSongDb().insertSongBookInfo(songBookInfo);
				S.getSongDb().storeSongs(songBookInfo.name, songs, dataFormatVersion);

				activity.runOnUiThread(() -> listener.onDownloadedAndInserted(songBookInfo));

			} catch (IOException | ClassNotFoundException e) {
				activity.runOnUiThread(() -> listener.onFailedOrCancelled(songBookInfo, e));

			} finally {
				pd.dismiss();
			}
		}).start();
	}
}
