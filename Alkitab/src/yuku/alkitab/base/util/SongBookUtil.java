package yuku.alkitab.base.util;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.os.AsyncTask;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

import net.londatiga.android.ActionItem;
import net.londatiga.android.QuickAction;
import yuku.alkitab.base.S;
import yuku.alkitab.base.rpc.SimpleHttpConnection;
import yuku.kpri.model.Song;

public class SongBookUtil {
	public static final String TAG = SongBookUtil.class.getSimpleName();

	public interface OnSongBookSelectedListener {
		void onSongBookSelected(boolean all, SongBookInfo songBookInfo);
	}
	
	public interface OnDownloadSongBookListener {
		void onDownloadedAndInserted(SongBookInfo songBookInfo);
		void onFailedOrCancelled(SongBookInfo songBookInfo, Exception e);
	}
	
	private static List<SongBookInfo> knownSongBooks;
	
	static {
		knownSongBooks = new ArrayList<SongBookInfo>();
		
		for (String k: new String[] {
			// bookName :: fileName :: downloadUrl :: bookDescription
			"KJ   :: kj-1.ser   :: http://alkitab-host.appspot.com/addon/songs/v1/data/kj-1.ser.gz   :: Kidung Jemaat, buku himne terbitan YAMUGER (Yayasan Musik Gereja di Indonesia).",
			"NKB  :: nkb-1.ser  :: http://alkitab-host.appspot.com/addon/songs/v1/data/nkb-1.ser.gz  :: Nyanyikanlah Kidung Baru, buku himne terbitan Badan Pengerja Majelis Sinode (BPMS) Gereja Kristen Indonesia.", 
			"PKJ  :: pkj-1.ser  :: http://alkitab-host.appspot.com/addon/songs/v1/data/pkj-1.ser.gz  :: Pelengkap Kidung Jemaat, buku nyanyian untuk melengkapi Kidung Jemaat, terbitan YAMUGER (Yayasan Musik Gereja di Indonesia).",
			"KPRI :: kpri-1.ser :: http://alkitab-host.appspot.com/addon/songs/v1/data/kpri-1.ser.gz :: Kidung Persekutuan Reformed Injili, buku nyanyian terbitan Sinode Gereja Reformed Injili Indonesia (GRII).",
		}) {
			String[] ss = k.split("::");
			SongBookInfo bookInfo = new SongBookInfo();
			bookInfo.bookName = ss[0].trim();
			bookInfo.bookFile = ss[1].trim();
			bookInfo.downloadUrl = ss[2].trim();
			bookInfo.description = ss[3].trim();
			knownSongBooks.add(bookInfo);
		}
	}
	
	public static class SongBookInfo {
		public String bookName;
		public String bookFile;
		public String downloadUrl;
		public String description;
	}

	public static QuickAction getSongBookQuickAction(Context context, boolean withAll) {
        QuickAction res = new QuickAction(context, QuickAction.VERTICAL);
        if (withAll) {
        	SpannableStringBuilder sb = new SpannableStringBuilder("All" + "\n");
			int sb_len = sb.length();
			sb.append("Show all books that are installed.");
			sb.setSpan(new RelativeSizeSpan(0.7f), sb_len, sb.length(), 0);
			sb.setSpan(new ForegroundColorSpan(0xff4488bb), sb_len, sb.length(), 0);
        	res.addActionItem(new ActionItem(0, "All"));
        }
		int n = 1;
		for (SongBookInfo bookInfo: knownSongBooks) {
			SpannableStringBuilder sb = new SpannableStringBuilder(bookInfo.bookName + "\n");
			int sb_len = sb.length();
			sb.append(bookInfo.description);
			sb.setSpan(new RelativeSizeSpan(0.7f), sb_len, sb.length(), 0);
			sb.setSpan(new ForegroundColorSpan(0xff4488bb), sb_len, sb.length(), 0);
			res.addActionItem(new ActionItem(n++, sb));
		}
		return res;
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

	public static int getSongDataFormatVersion() {
		return 1;
	}

	public static void downloadSongBook(final Activity activity, final SongBookInfo songBookInfo, final OnDownloadSongBookListener listener) {
		new AsyncTask<Void, Void, List<Song>>() {
			ProgressDialog pd;
			Exception ex;
			
			@Override protected void onPreExecute() {
				pd = ProgressDialog.show(activity, null, "Downloading…", true, true);
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
					S.getSongDb().storeSongs(songBookInfo.bookName, songs, getSongDataFormatVersion());
					
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
