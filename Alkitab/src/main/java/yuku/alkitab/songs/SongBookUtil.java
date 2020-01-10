package yuku.alkitab.songs;

import android.app.Activity;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;
import android.text.Html;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.view.Menu;
import android.view.View;
import android.widget.PopupMenu;
import com.afollestad.materialdialogs.MaterialDialog;
import okhttp3.Call;
import okhttp3.Response;
import yuku.alkitab.base.App;
import yuku.alkitab.base.S;
import yuku.alkitab.base.storage.SongDb;
import yuku.alkitab.base.util.Background;
import yuku.alkitab.base.util.Foreground;
import yuku.alkitab.debug.BuildConfig;
import yuku.alkitab.debug.R;
import yuku.alkitab.io.OptionalGzipInputStream;
import yuku.kpri.model.Song;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class SongBookUtil {
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
		void onDownloadedAndInserted(@NonNull SongBookInfo songBookInfo);
		void onFailedOrCancelled(@NonNull SongBookInfo songBookInfo, @Nullable Exception e);
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
			final SpannableStringBuilder sb = new SpannableStringBuilder(escapeSongBookName(info.name));
			sb.append("\n");
			int sb_len = sb.length();
			if (!TextUtils.isEmpty(info.title)) {
				sb.append(Html.fromHtml(info.title));
			}
			sb.setSpan(new RelativeSizeSpan(0.7f), sb_len, sb.length(), 0);
			sb.setSpan(new ForegroundColorSpan(0xffa0a0a0), sb_len, sb.length(), 0);
			menu.add(0, i + 1, 0, sb);
		}

		if (withMore) {
			final SpannableStringBuilder sb = new SpannableStringBuilder(context.getText(R.string.sn_bookselector_more));
			sb.setSpan(new ForegroundColorSpan(ResourcesCompat.getColor(context.getResources(), R.color.escape, context.getTheme())), 0, sb.length(), 0);
			menu.add(0, POPUP_ID_MORE, 0, sb);
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

	public static class NotOkException extends IOException {
		public int code;

		public NotOkException(final int code) {
			this.code = code;
		}
	}

	public static void downloadSongBook(final Activity activity, final SongBookInfo songBookInfo, final int dataFormatVersion, final OnDownloadSongBookListener listener) {
		final AtomicBoolean cancelled = new AtomicBoolean();

		final MaterialDialog pd = new MaterialDialog.Builder(activity)
			.content(R.string.sn_downloading_ellipsis)
			.progress(true, 0)
			.dismissListener(dialog -> cancelled.set(true))
			.show();

		Background.run(() -> {
			try {
				final Call call = App.downloadCall(BuildConfig.SERVER_HOST + "addon/songs/get_songs?name=" + songBookInfo.name + "&dataFormatVersion=" + dataFormatVersion);

				final Response response = call.execute();
				if (response.code() != 200) {
					throw new NotOkException(response.code());
				}

				final InputStream ogis = new OptionalGzipInputStream(response.body().byteStream());
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

				Foreground.run(() -> listener.onDownloadedAndInserted(songBookInfo));

			} catch (IOException | ClassNotFoundException e) {
				Foreground.run(() -> listener.onFailedOrCancelled(songBookInfo, e));

			} finally {
				pd.dismiss();
			}
		});
	}

	public static CharSequence escapeSongBookName(final String name) {
		if (name != null && name.startsWith("_")) {
			final int color = ResourcesCompat.getColor(App.context.getResources(), R.color.escape, App.context.getTheme());
			final SpannableStringBuilder res = new SpannableStringBuilder(name.substring(1));
			res.setSpan(new ForegroundColorSpan(color), 0, res.length(), 0);
			return res;
		}
		return name;
	}
}
