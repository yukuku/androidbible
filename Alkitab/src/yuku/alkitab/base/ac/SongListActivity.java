package yuku.alkitab.base.ac;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.AsyncTaskLoader;
import android.support.v4.content.Loader;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.londatiga.android.ActionItem;
import net.londatiga.android.QuickAction;
import yuku.afw.App;
import yuku.afw.V;
import yuku.alkitab.R;
import yuku.alkitab.base.ac.base.BaseActivity;
import yuku.kpri.model.Song;
import yuku.kpriviewer.SongRepo;
import yuku.searchbar.SearchWidget;

public class SongListActivity extends BaseActivity {
	public static final String TAG = SongListActivity.class.getSimpleName();
	
	SearchWidget searchWidget;
	ListView lsSong;
	Button bChangeBook;
	QuickAction qaChangeBook;
	
	SongAdapter adapter;
	SongLoader loader;

	List<SongBookInfo> knownBooks;

	public static Intent createIntent() {
		return new Intent(App.context, SongListActivity.class);
	}

	static class SongBookInfo {
		public String bookName;
		public String bookFile;
		public String url;
		public String description;
	}
	
	static class SongBookRepo {
		static String[] knownBooks = {
			// bookName :: fileName :: url :: bookDescription
			"KJ   :: kj-1.ser   :: http://alkitab-host.appspot.com/addon/songs/v1/data/kj-1.ser.gz   :: Kidung Jemaat, buku himne terbitan YAMUGER (Yayasan Musik Gereja di Indonesia).",
			"NKB  :: nkb-1.ser  :: http://alkitab-host.appspot.com/addon/songs/v1/data/nkb-1.ser.gz  :: Nyanyikanlah Kidung Baru, buku himne terbitan Badan Pengerja Majelis Sinode (BPMS) Gereja Kristen Indonesia.", 
			"PKJ  :: pkj-1.ser  :: http://alkitab-host.appspot.com/addon/songs/v1/data/pkj-1.ser.gz  :: Pelengkap Kidung Jemaat, buku nyanyian untuk melengkapi Kidung Jemaat, terbitan YAMUGER (Yayasan Musik Gereja di Indonesia).",
			"KPRI :: kpri-1.ser :: http://alkitab-host.appspot.com/addon/songs/v1/data/kpri-1.ser.gz :: Kidung Persekutuan Reformed Injili, buku nyanyian terbitan Sinode Gereja Reformed Injili Indonesia (GRII).",
		};
		
		public static File getSongsDir() {
			File res = new File(Environment.getExternalStorageDirectory(), "bible/songs");
			res.mkdirs();
			return res;
		}
		
		public static List<SongBookInfo> getKnownBooks() {
			List<SongBookInfo> res = new ArrayList<SongBookInfo>();
			for (String k: knownBooks) {
				String[] ss = k.split("::");
				SongBookInfo bookInfo = new SongBookInfo();
				bookInfo.bookName = ss[0].trim();
				bookInfo.bookFile = ss[1].trim();
				bookInfo.url = ss[2].trim();
				bookInfo.description = ss[3].trim();
				res.add(bookInfo);
			}
			return res;
		}
		
		public static String getBookFile(SongBookInfo bookInfo) {
			return new File(getSongsDir(), bookInfo.bookFile).getAbsolutePath();
		}
		
		public static boolean available(SongBookInfo bookInfo) {
			File file = new File(getSongsDir(), bookInfo.bookFile);
			return file.exists() && file.isFile() && file.canRead();
		}
	}
	
	@Override protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		setProgressBarIndeterminate(true);
		
		setContentView(R.layout.activity_song_list);
		
		setTitle("Songs");
		
		searchWidget = V.get(this, R.id.searchWidget);
		lsSong = V.get(this, R.id.lsSong);
		bChangeBook = V.get(this, R.id.bChangeBook);
		
		searchWidget.setSubmitButtonEnabled(false);
		searchWidget.setOnQueryTextListener(searchWidget_queryText);
		
		lsSong.setAdapter(adapter = new SongAdapter());
		lsSong.setOnItemClickListener(lsSong_click);
		
		// load available songs
		File songsDir = SongBookRepo.getSongsDir();
		if (!songsDir.exists() || !songsDir.isDirectory() || !songsDir.canRead()) {
			new AlertDialog.Builder(this)
			.setMessage("Song folder could not be accessed. Please make sure external storage is available.\nPath: " + songsDir)
			.setPositiveButton(R.string.ok, null)
			.show()
			.setOnDismissListener(new DialogInterface.OnDismissListener() {
				@Override public void onDismiss(DialogInterface dialog) {
					finish();
				}
			});
			
			return;
		}
		
		
		knownBooks = SongBookRepo.getKnownBooks();
		
		final List<String> loading = Collections.synchronizedList(new ArrayList<String>());
		for (final SongBookInfo bookInfo: knownBooks) {
			if (!SongBookRepo.available(bookInfo)) continue;
			
			new Thread() {
				@Override public void run() {
					try {
						SongRepo.loadSongData(bookInfo.bookName, new BufferedInputStream(new FileInputStream(SongBookRepo.getBookFile(bookInfo))));
						loading.add(bookInfo.bookName);
					} catch (final Exception e) {
						Log.w(TAG, "Error loading book", e);
						runOnUiThread(new Runnable() {
							@Override public void run() {
								new AlertDialog.Builder(SongListActivity.this)
								.setMessage(getString(R.string.failed_to_load_songbookname_reason, bookInfo.bookName, e.getClass().getSimpleName() + " " + e.getMessage()))
								.setPositiveButton(R.string.ok, null)
								.show();
							}
						});
					}
				};
			}.start();
		}
		
		// popup menu
        qaChangeBook = new QuickAction(this, QuickAction.VERTICAL);
        {
        	SpannableStringBuilder sb = new SpannableStringBuilder("All" + "\n");
			int sb_len = sb.length();
			sb.append("Show all books that are installed.");
			sb.setSpan(new RelativeSizeSpan(0.7f), sb_len, sb.length(), 0);
			sb.setSpan(new ForegroundColorSpan(0xff4488bb), sb_len, sb.length(), 0);
        	qaChangeBook.addActionItem(new ActionItem(0, "All"));
        }
		int n = 1;
		for (SongBookInfo bookInfo: knownBooks) {
			SpannableStringBuilder sb = new SpannableStringBuilder(bookInfo.bookName + "\n");
			int sb_len = sb.length();
			sb.append(bookInfo.description);
			sb.setSpan(new RelativeSizeSpan(0.7f), sb_len, sb.length(), 0);
			sb.setSpan(new ForegroundColorSpan(0xff4488bb), sb_len, sb.length(), 0);
			qaChangeBook.addActionItem(new ActionItem(n++, sb));
		}
		
		qaChangeBook.setOnActionItemClickListener(qaChangeBook_actionItemClick);
		
		bChangeBook.setOnClickListener(bChangeBook_click);
		
		loader = new SongLoader();
		
		setProgressBarIndeterminateVisibility(true);
        getSupportLoaderManager().initLoader(0, null, new LoaderManager.LoaderCallbacks<List<Song>>() {
			@Override public Loader<List<Song>> onCreateLoader(int id, Bundle args) {
				return loader;
			}
			
			@Override public void onLoadFinished(Loader<List<Song>> loader, List<Song> data) { 
				adapter.setData(data);
				setProgressBarIndeterminateVisibility(false);
			}

			@Override public void onLoaderReset(Loader<List<Song>> loader) {
				adapter.setData(null);
				setProgressBarIndeterminateVisibility(false);
			}
		}).forceLoad();
	}
	
	OnClickListener bChangeBook_click = new OnClickListener() {
		@Override public void onClick(View v) {
			qaChangeBook.show(v);
		}
	};
	
	QuickAction.OnActionItemClickListener qaChangeBook_actionItemClick = new QuickAction.OnActionItemClickListener() {
		@Override public void onItemClick(QuickAction source, int pos, int actionId) {
			boolean all = pos == 0;
			SongBookInfo selectedBook = null;
			if (!all) {
				if (pos >= 0 && pos <= knownBooks.size()) {
					selectedBook = knownBooks.get(pos-1);
				}
			}
			
			if (all) {
				bChangeBook.setText("All");
				loader.setSelectedBookName(null);
				loader.forceLoad();
			} else if (selectedBook != null) {
				if (SongBookRepo.available(selectedBook)) {
					bChangeBook.setText(selectedBook.bookName);
					loader.setSelectedBookName(selectedBook.bookName);
					loader.forceLoad();
				} else {
					Toast.makeText(SongListActivity.this, "not yet avail", Toast.LENGTH_SHORT).show();
				}
			}
		}
	};
	
	private OnItemClickListener lsSong_click = new OnItemClickListener() {
		@Override public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
			Pair<List<String>, List<String>> pair = adapter.getBookNamesAndSongCodes();
			startActivity(SongViewActivity.createIntent(pair.first, pair.second, position));
		}
	};

	private SearchWidget.OnQueryTextListener searchWidget_queryText = new SearchWidget.SimpleOnQueryTextListener() {
		@Override public boolean onQueryTextSubmit(SearchWidget searchWidget, String query) {
			search(query);
			return true;
		};
		
		@Override public boolean onQueryTextChange(SearchWidget searchWidget, String newText) {
			search(newText);
			return true;
		}

		private void search(String s) {
			setProgressBarIndeterminateVisibility(true);
			loader.setFilterString(s);
			loader.forceLoad();
		};
	};

	public class SongAdapter extends BaseAdapter {
		List<Song> list;
		
		@Override public int getCount() {
			return list == null? 0: list.size();
		}
		
		public Pair<List<String>, List<String>> getBookNamesAndSongCodes() {
			List<String> bookNames = new ArrayList<String>(getCount());
			List<String> codes = new ArrayList<String>(getCount());
			for (int i = 0, len = getCount(); i < len; i++) {
				Song song = getItem(i);
				bookNames.add(SongRepo.getBookNameBySong(song));
				codes.add(song.code);
			}
			return Pair.create(bookNames, codes);
		}

		public void setData(List<Song> data) {
			this.list = data;
			notifyDataSetChanged();
		}

		@Override public Song getItem(int position) {
			return list.get(position);
		}

		@Override public long getItemId(int position) {
			return position;
		}

		@Override public View getView(int position, View convertView, ViewGroup parent) {
			View res = convertView != null? convertView: getLayoutInflater().inflate(R.layout.item_song, null);
			
			TextView lTitle = V.get(res, R.id.lTitle);
			TextView lTitleOriginal = V.get(res, R.id.lTitleOriginal);
			TextView lBookName = V.get(res, R.id.lBookName);
			
			Song song = getItem(position);
			lTitle.setText(song.code + ". " + song.title);
			if (song.title_original != null) {
				lTitleOriginal.setVisibility(View.VISIBLE);
				lTitleOriginal.setText(song.title_original);
			} else {
				lTitleOriginal.setVisibility(View.GONE);
			}
			lBookName.setText(SongRepo.getBookNameBySong(song).toUpperCase());
			
			return res;
		}
	}
	
	static class SongLoader extends AsyncTaskLoader<List<Song>> {
		private String filter_string;
		private String selectedBookName;

		public SongLoader() {
			super(App.context);
		}

		public void setFilterString(String s) {
			if (TextUtils.isEmpty(s) || s.trim().length() == 0) {
				filter_string = null;
			} else {
				filter_string = s.trim();
			}
		}
		
		public void setSelectedBookName(String bookName) {
			this.selectedBookName = bookName;
		}

		@Override public List<Song> loadInBackground() {
			Log.d(TAG, "@@loadInBackground filter_string=" + filter_string);

			return SongRepo.filterSongByString(selectedBookName == null? SongRepo.getAllSongs(): SongRepo.getSongsByBook(selectedBookName), filter_string);
		}
	}
}
