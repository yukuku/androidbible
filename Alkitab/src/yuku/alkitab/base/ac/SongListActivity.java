package yuku.alkitab.base.ac;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.AsyncTaskLoader;
import android.support.v4.content.Loader;
import android.text.TextUtils;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ListView;
import android.widget.TextView;

import java.util.List;

import net.londatiga.android.QuickAction;
import yuku.afw.App;
import yuku.afw.V;
import yuku.alkitab.R;
import yuku.alkitab.base.S;
import yuku.alkitab.base.ac.base.BaseActivity;
import yuku.alkitab.base.model.SongInfo;
import yuku.alkitab.base.util.SongBookUtil;
import yuku.alkitab.base.util.SongBookUtil.OnDownloadSongBookListener;
import yuku.alkitab.base.util.SongBookUtil.OnSongBookSelectedListener;
import yuku.alkitab.base.util.SongBookUtil.SongBookInfo;
import yuku.alkitab.base.util.SongFilter;
import yuku.searchbar.SearchWidget;

/*
 * Everytime we want to do a search, make sure 3 things:
 * 1. setProgressBarIndeterminateVisibility(true);
 * 2. set new search params
 * 3. loader.forceLoad()
 */
public class SongListActivity extends BaseActivity {
	public static final String TAG = SongListActivity.class.getSimpleName();
	
	private static final String EXTRA_bookName = "bookName"; //$NON-NLS-1$
	private static final String EXTRA_code = "code"; //$NON-NLS-1$
	
	SearchWidget searchWidget;
	ListView lsSong;
	Button bChangeBook;
	QuickAction qaChangeBook;
	CheckBox cDeepSearch;
	View panelFilter;
	
	SongAdapter adapter;
	SongLoader loader;

	List<SongBookInfo> knownBooks;

	public static class Result {
		public String bookName;
		public String code;
	}
	
	public static Intent createIntent() {
		return new Intent(App.context, SongListActivity.class);
	}
	
	public static Result obtainResult(Intent data) {
		if (data == null) return null;
		Result res = new Result();
		res.bookName = data.getStringExtra(EXTRA_bookName);
		res.code = data.getStringExtra(EXTRA_code);
		return res;
	}
	
	@Override protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		setProgressBarIndeterminate(true);
		
		setContentView(R.layout.activity_song_list);
		
		setTitle(R.string.sn_songs_activity_title);
		
		searchWidget = V.get(this, R.id.searchWidget);
		lsSong = V.get(this, R.id.lsSong);
		bChangeBook = V.get(this, R.id.bChangeBook);
		cDeepSearch = V.get(this, R.id.cDeepSearch);
		panelFilter = V.get(this, R.id.panelFilter);
		
		searchWidget.setSubmitButtonEnabled(false);
		searchWidget.setOnQueryTextListener(searchWidget_queryText);
		
		lsSong.setAdapter(adapter = new SongAdapter());
		lsSong.setOnItemClickListener(lsSong_click);
		
		qaChangeBook = SongBookUtil.getSongBookQuickAction(this, true);
		qaChangeBook.setOnActionItemClickListener(SongBookUtil.getOnActionItemConverter(songBookSelected)); 
		
		bChangeBook.setOnClickListener(bChangeBook_click);
		cDeepSearch.setOnCheckedChangeListener(cDeepSearch_checkedChange);
		
		// if we're using SearchBar instead of SearchView, move filter panel to 
		// the bottom view of the SearchBar for better appearance
		if (searchWidget.getSearchBarIfUsed() != null) {
			((ViewGroup) panelFilter.getParent()).removeView(panelFilter);
			searchWidget.getSearchBarIfUsed().setBottomView(panelFilter);
		}
		
		loader = new SongLoader();
		
		setProgressBarIndeterminateVisibility(true);
        getSupportLoaderManager().initLoader(0, null, new LoaderManager.LoaderCallbacks<List<SongInfo>>() {
			@Override public Loader<List<SongInfo>> onCreateLoader(int id, Bundle args) {
				return loader;
			}
			
			@Override public void onLoadFinished(Loader<List<SongInfo>> loader, List<SongInfo> data) { 
				adapter.setData(data);
				setProgressBarIndeterminateVisibility(false);
			}

			@Override public void onLoaderReset(Loader<List<SongInfo>> loader) {
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
	
	OnCheckedChangeListener cDeepSearch_checkedChange = new OnCheckedChangeListener() {
		@Override public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
			setProgressBarIndeterminateVisibility(true);
			loader.setDeepSearch(isChecked);
			loader.forceLoad();
		}
	};

	OnSongBookSelectedListener songBookSelected = new OnSongBookSelectedListener() {
		@Override public void onSongBookSelected(boolean all, SongBookInfo songBookInfo) {
			if (all) {
				bChangeBook.setText(R.string.sn_bookselector_all);
				setProgressBarIndeterminateVisibility(true);
				loader.setSelectedBookName(null);
				loader.forceLoad();
			} else if (songBookInfo != null) {
				if (S.getSongDb().getFirstSongFromBook(songBookInfo.bookName, SongBookUtil.getSongDataFormatVersion()) == null) {
					SongBookUtil.downloadSongBook(SongListActivity.this, songBookInfo, new OnDownloadSongBookListener() {
						@Override public void onFailedOrCancelled(SongBookInfo songBookInfo, Exception e) {
							if (e != null) {
								new AlertDialog.Builder(SongListActivity.this)
								.setMessage(e.getClass().getSimpleName() + ' ' + e.getMessage())
								.setPositiveButton(R.string.ok, null)
								.show();
							}
						}
						
						@Override public void onDownloadedAndInserted(SongBookInfo songBookInfo) {
							bChangeBook.setText(songBookInfo.bookName);
							setProgressBarIndeterminateVisibility(true);
							loader.setSelectedBookName(songBookInfo.bookName);
							loader.forceLoad();
						}
					});
				} else { // already have, just display
					bChangeBook.setText(songBookInfo.bookName);
					setProgressBarIndeterminateVisibility(true);
					loader.setSelectedBookName(songBookInfo.bookName);
					loader.forceLoad();
				}
			}
		}
	};
	
	private OnItemClickListener lsSong_click = new OnItemClickListener() {
		@Override public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
			SongInfo songInfo = adapter.getItem(position);
			Intent data = new Intent();
			data.putExtra(EXTRA_bookName, songInfo.bookName);
			data.putExtra(EXTRA_code, songInfo.code);
			setResult(RESULT_OK, data);
			finish();
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
		List<SongInfo> list;
		
		@Override public int getCount() {
			return list == null? 0: list.size();
		}

		public void setData(List<SongInfo> data) {
			this.list = data;
			notifyDataSetChanged();
		}

		@Override public SongInfo getItem(int position) {
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
			
			SongInfo songInfo = getItem(position);
			lTitle.setText(songInfo.code + ". " + songInfo.title); //$NON-NLS-1$
			if (songInfo.title_original != null) {
				lTitleOriginal.setVisibility(View.VISIBLE);
				lTitleOriginal.setText(songInfo.title_original);
			} else {
				lTitleOriginal.setVisibility(View.GONE);
			}
			lBookName.setText(songInfo.bookName);
			
			return res;
		}
	}
	
	static class SongLoader extends AsyncTaskLoader<List<SongInfo>> {
		public static final String TAG = SongLoader.class.getSimpleName();
		
		private String filter_string;
		private String selectedBookName;
		private boolean deepSearch;

		public SongLoader() {
			super(App.context);
		}
		
		public void setDeepSearch(boolean deepSearch) {
			this.deepSearch = deepSearch;
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

		@Override public List<SongInfo> loadInBackground() {
			List<SongInfo> res;
			if (!deepSearch) {
				List<SongInfo> songInfos = S.getSongDb().getSongInfosByBookName(selectedBookName, SongBookUtil.getSongDataFormatVersion());
				res = SongFilter.filterSongInfosByString(songInfos, filter_string);
			} else {
				res = S.getSongDb().getSongInfosByBookNameAndDeepFilter(selectedBookName, filter_string, SongBookUtil.getSongDataFormatVersion());
			}
			return res;
		}
	}
}
