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
import yuku.kpri.model.Song;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

public class SongBookUtil {
	public static final String TAG = SongBookUtil.class.getSimpleName();

	public interface OnSongBookSelectedListener {
		void onSongBookSelected(boolean all, SongBookInfo songBookInfo);
	}
	
	public interface OnDownloadSongBookListener {
		void onDownloadedAndInserted(SongBookInfo songBookInfo);
		void onFailedOrCancelled(SongBookInfo songBookInfo, Exception e);
	}
	
	static List<SongBookInfo> knownSongBooks;
	
	static {
		knownSongBooks = new ArrayList<SongBookInfo>();
		
		for (String k: new String[] {
			// bookName :: dataFormatVersion :: downloadUrl :: bookDescription
			"KJ   :: 3 :: http://alkitab-host.appspot.com/addon/songs/v1/data/kj-3.ser.gz   :: <b>Kidung Jemaat</b> terbitan Yayasan Musik Gereja di Indonesia (YAMUGER)", //$NON-NLS-1$
			"KPKA :: 3 :: http://alkitab-host.appspot.com/addon/songs/v1/data/kpka-3.ser.gz :: <b>Kidung Pasamuan Kristen</b> (bahasa Jawa) terbitan Badan Musyawarah Gereja-gereja Jawa (BMGJ)", //$NON-NLS-1$
			"KPRI :: 3 :: http://alkitab-host.appspot.com/addon/songs/v1/data/kpri-3.ser.gz :: <b>Kidung Persekutuan Reformed Injili</b> terbitan Sinode Gereja Reformed Injili Indonesia (GRII)", //$NON-NLS-1$
			"NKB  :: 3 :: http://alkitab-host.appspot.com/addon/songs/v1/data/nkb-3.ser.gz  :: <b>Nyanyikanlah Kidung Baru</b> terbitan Badan Pengerja Majelis Sinode Gereja Kristen Indonesia",  //$NON-NLS-1$
			"NP   :: 3 :: http://alkitab-host.appspot.com/addon/songs/v1/data/np-3.ser.gz   :: <b>Nyanyian Pujian</b> terbitan Lembaga Literatur Baptis", //$NON-NLS-1$
			"PKJ  :: 3 :: http://alkitab-host.appspot.com/addon/songs/v1/data/pkj-3.ser.gz  :: <b>Pelengkap Kidung Jemaat</b> terbitan Yayasan Musik Gereja di Indonesia (YAMUGER)", //$NON-NLS-1$
			"PPK  :: 3 :: http://alkitab-host.appspot.com/addon/songs/v1/data/ppk-3.ser.gz  :: <b>Puji-pujian Kristen</b> terbitan Seminari Alkitab Asia Tenggara (SAAT)", //$NON-NLS-1$
		}) {
			String[] ss = k.split("::"); //$NON-NLS-1$
			SongBookInfo bookInfo = new SongBookInfo();
			bookInfo.bookName = ss[0].trim();
			bookInfo.dataFormatVersion = Integer.parseInt(ss[1].trim());
			bookInfo.downloadUrl = ss[2].trim();
			bookInfo.description = ss[3].trim();
			knownSongBooks.add(bookInfo);
		}
	}
	
	public static class SongBookInfo {
		public String bookName;
		public int dataFormatVersion;
		public String downloadUrl;
		public String description;
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
				return knownSongBook.description;
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
					InputStream is = conn.load();
					if (is == null) {
						throw conn.getException();
					}
					
					GZIPInputStream gzis = new GZIPInputStream(new BufferedInputStream(is));
					ObjectInputStream ois = new ObjectInputStream(gzis);
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
