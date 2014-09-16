package yuku.alkitab.base.util;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.os.AsyncTask;
import android.text.Html;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import net.londatiga.android.ActionItem;
import net.londatiga.android.QuickAction;
import yuku.alkitab.base.S;
import yuku.alkitab.base.U;
import yuku.alkitab.base.rpc.SimpleHttpConnection;
import yuku.alkitab.debug.R;
import yuku.alkitab.io.OptionalGzipInputStream;
import yuku.kpri.model.Song;

import java.io.InputStream;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.List;

public class SongBookUtil {
	public static final String TAG = SongBookUtil.class.getSimpleName();

	public interface OnSongBookSelectedListener {
		void onSongBookSelected(boolean all, SongBookInfo songBookInfo);
	}
	
	public interface OnDownloadSongBookListener {
		void onDownloadedAndInserted(SongBookInfo songBookInfo);
		void onFailedOrCancelled(SongBookInfo songBookInfo, Exception e);
	}

	public static class SongBookInfo {
		public String bookName;
		public int dataFormatVersion;
		public String downloadUrl;
		public String description;
		public String copyright;
	}

	static List<SongBookInfo> knownSongBooks;

	static {
		knownSongBooks = new ArrayList<>();

		for (String k: new String[] {
			// bookName :: dataFormatVersion :: downloadUrl :: description :: copyright
			"BE   :: 3 :: https://alkitab-host.appspot.com/addon/songs/v1/data/be-4.ser.gz   :: Buku Ende                             :: (c) Huria Kristen Batak Protestan",
			"KJ   :: 3 :: https://alkitab-host.appspot.com/addon/songs/v1/data/kj-4.ser.gz   :: Kidung Jemaat                         :: (c) Yayasan Musik Gereja di Indonesia (YAMUGER)",
			"KPKA :: 3 :: https://alkitab-host.appspot.com/addon/songs/v1/data/kpka-4.ser.gz :: Kidung Pasamuan Kristen Anyar         :: (c) Badan Musyawarah Gereja-gereja Jawa (BMGJ)",
			"KPKL :: 3 :: https://alkitab-host.appspot.com/addon/songs/v1/data/kpkl-4.ser.gz :: Kidung Pasamuan Kristen Lawas         :: (c) Taman Pustaka Kristen Jogjakarta",
			"KPPK :: 3 :: https://alkitab-host.appspot.com/addon/songs/v1/data/kppk-4.ser.gz :: Kidung Puji-Pujian Kristen            :: (c) Seminari Alkitab Asia Tenggara (SAAT)",
			"KPRI :: 3 :: https://alkitab-host.appspot.com/addon/songs/v1/data/kpri-4.ser.gz :: Kidung Persekutuan Reformed Injili    :: (c) Sinode Gereja Reformed Injili Indonesia (GRII)",
			"NKB  :: 3 :: https://alkitab-host.appspot.com/addon/songs/v1/data/nkb-4.ser.gz  :: Nyanyikanlah Kidung Baru              :: (c) Badan Pengerja Majelis Sinode Gereja Kristen Indonesia (GKI)",
			"NKI  :: 3 :: https://alkitab-host.appspot.com/addon/songs/v1/data/nki-4.ser.gz  :: Nyanyian Kemenangan Iman              :: (c) Kalam Hidup",
			"NP   :: 3 :: https://alkitab-host.appspot.com/addon/songs/v1/data/np-4.ser.gz   :: Nyanyian Pujian                       :: (c) Lembaga Literatur Baptis",
			"NR   :: 3 :: https://alkitab-host.appspot.com/addon/songs/v1/data/nr-4.ser.gz   :: Nafiri Rohani                         :: (c) Sinode Gereja Kristus",
			"PKJ  :: 3 :: https://alkitab-host.appspot.com/addon/songs/v1/data/pkj-4.ser.gz  :: Pelengkap Kidung Jemaat               :: (c) Yayasan Musik Gereja di Indonesia (YAMUGER)",
			"PPK  :: 3 :: https://alkitab-host.appspot.com/addon/songs/v1/data/ppk-4.ser.gz  :: Puji-pujian Kristen                   :: (c) Seminari Alkitab Asia Tenggara (SAAT)",
		}) {
			String[] ss = k.split("::"); //$NON-NLS-1$
			SongBookInfo bookInfo = new SongBookInfo();
			bookInfo.bookName = ss[0].trim();
			bookInfo.dataFormatVersion = Integer.parseInt(ss[1].trim());
			bookInfo.downloadUrl = ss[2].trim();
			bookInfo.description = ss[3].trim();
			bookInfo.copyright = ss[4].trim();
			knownSongBooks.add(bookInfo);
		}
	}


	public static QuickAction getSongBookQuickAction(Context context, boolean withAll) {
        QuickAction res = new QuickAction(context, QuickAction.VERTICAL);
        if (withAll) {
        	SpannableStringBuilder sb = new SpannableStringBuilder(context.getString(R.string.sn_bookselector_all) + '\n');
			int sb_len = sb.length();
			sb.append(context.getString(R.string.sn_bookselector_all_desc));
			sb.setSpan(new RelativeSizeSpan(0.7f), sb_len, sb.length(), 0);
			sb.setSpan(new ForegroundColorSpan(0xffa0a0a0), sb_len, sb.length(), 0);
        	res.addActionItem(new ActionItem(0, sb));
        }
		int n = 1;
		for (SongBookInfo bookInfo: knownSongBooks) {
			SpannableStringBuilder sb = new SpannableStringBuilder(bookInfo.bookName + '\n');
			int sb_len = sb.length();
			sb.append(Html.fromHtml(bookInfo.description));
			sb.setSpan(new RelativeSizeSpan(0.7f), sb_len, sb.length(), 0);
			sb.setSpan(new ForegroundColorSpan(0xffa0a0a0), sb_len, sb.length(), 0);
			res.addActionItem(new ActionItem(n++, sb));
		}
		return res;
	}

	public static String getCopyright(final String bookName) {
		for (final SongBookInfo knownSongBook : knownSongBooks) {
			if (U.equals(bookName, knownSongBook.bookName)) {
				return knownSongBook.description + " " + knownSongBook.copyright;
			}
		}
		return "";
	}

	public static QuickAction.OnActionItemClickListener getOnActionItemConverter(final OnSongBookSelectedListener listener) {
		return new QuickAction.OnActionItemClickListener() {
			@Override public void onItemClick(QuickAction source, int pos, int actionId) {
				if (actionId == 0) {
					listener.onSongBookSelected(true, null);
				} else {
					listener.onSongBookSelected(false, knownSongBooks.get(actionId - 1));
				}
			}
		};
	}

	public static void downloadSongBook(final Activity activity, final SongBookInfo songBookInfo, final OnDownloadSongBookListener listener) {
		new AsyncTask<Void, Void, List<Song>>() {
			ProgressDialog pd;
			Exception ex;
			
			@Override protected void onPreExecute() {
				pd = ProgressDialog.show(activity, null, activity.getString(R.string.sn_downloading_ellipsis), true, true);
				pd.setOnDismissListener(new OnDismissListener() {
					@Override public void onDismiss(DialogInterface dialog) {
						cancel(false);
					}
				});
			};
			
			@SuppressWarnings("unchecked") @Override protected List<Song> doInBackground(Void... params) {
				List<Song> songs = null;
				
				SimpleHttpConnection conn = new SimpleHttpConnection(songBookInfo.downloadUrl);
				try {
					final InputStream originalInput = conn.load();
					if (originalInput == null) {
						throw conn.getException();
					}

					final InputStream ogis = new OptionalGzipInputStream(originalInput);
					final ObjectInputStream ois = new ObjectInputStream(ogis);
					songs = (List<Song>) ois.readObject();
					ois.close();
				} catch (Exception e) {
					this.ex = e;
					return null;
				} finally {
					conn.close();
				}

				if (isCancelled()) {
					return null;
				} else {
					// insert songs to db
					S.getSongDb().storeSongs(songBookInfo.bookName, songs, songBookInfo.dataFormatVersion);
					
					return songs;
				}
			}
			
			@Override protected void onPostExecute(List<Song> result) {
				pd.dismiss();
				if (result == null) {
					listener.onFailedOrCancelled(songBookInfo, ex);
				} else {
					listener.onDownloadedAndInserted(songBookInfo);
				}
			};
			
			@Override protected void onCancelled() {
				pd.dismiss();
				listener.onFailedOrCancelled(songBookInfo, null);
			};
		}.execute();
	}
}
