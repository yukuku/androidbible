package yuku.alkitab.base.ac;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
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

import java.util.ArrayList;
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

import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;

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
	private static final String EXTRA_searchState = "searchState"; //$NON-NLS-1$
	
	SearchWidget searchWidget;
	ListView lsSong;
	Button bChangeBook;
	QuickAction qaChangeBook;
	CheckBox cDeepSearch;
	View panelFilter;
	
	SongAdapter adapter;
	SongLoader loader;

	List<SongBookInfo> knownBooks;

	boolean stillUsingInitialSearchState = false;

	public static class Result {
		public String bookName;
		public String code;
		public SearchState last_searchState;
	}
	
	public static class SearchState implements Parcelable {
		public String filter_string;
		public List<SongInfo> result;
		public int selectedPosition;
		public String bookName;
		public boolean deepSearch;
		
		public SearchState(String filter_string, List<SongInfo> result, int selectedPosition, String bookName, boolean deepSearch) {
			this.filter_string = filter_string;
			this.result = result;
			this.selectedPosition = selectedPosition;
			this.bookName = bookName;
			this.deepSearch = deepSearch;
		}
		
		SearchState(Parcel in) {
			filter_string = in.readString();
			in.readList(result = new ArrayList<SongInfo>(), getClass().getClassLoader());
			selectedPosition = in.readInt();
			bookName = in.readString();
			deepSearch = in.readByte() != 0;
		}

		@Override public void writeToParcel(Parcel dest, int flags) {
			dest.writeString(filter_string);
			dest.writeList(result);
			dest.writeInt(selectedPosition);
			dest.writeString(bookName);
			dest.writeByte((byte) (deepSearch? 1: 0));
		}
		
		@Override public int describeContents() {
			return 0;
		}
		
	    public static final Parcelable.Creator<SearchState> CREATOR = new Parcelable.Creator<SearchState>() {
	        @Override public SearchState createFromParcel(Parcel in) {
	            return new SearchState(in);
	        }

	        @Override public SearchState[] newArray(int size) {
	            return new SearchState[size];
	        }
	    };
	}
	
	public static Intent createIntent(SearchState searchState_optional) {
		Intent res = new Intent(App.context, SongListActivity.class);
		if (searchState_optional != null) res.putExtra(EXTRA_searchState, searchState_optional);
		return res;
	}
	
	public static Result obtainResult(Intent data) {
		if (data == null) return null;
		Result res = new Result();
		res.bookName = data.getStringExtra(EXTRA_bookName);
		res.code = data.getStringExtra(EXTRA_code);
		res.last_searchState = data.getParcelableExtra(EXTRA_searchState);
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
		lsSong.setOnItemClickListener(lsSong_itemClick);
		
		qaChangeBook = SongBookUtil.getSongBookQuickAction(this, true);
		qaChangeBook.setOnActionItemClickListener(SongBookUtil.getOnActionItemConverter(songBookSelected)); 
		
		bChangeBook.setOnClickListener(bChangeBook_click);
		cDeepSearch.setOnCheckedChangeListener(cDeepSearch_checkedChange);
		
		// if we're using SearchBar instead of SearchView, move filter panel to 
		// the bottom view of the SearchBar for better appearance
		if (searchWidget.getSearchBarIfUsed() != null) {
			((ViewGroup) panelFilter.getParent()).removeView(panelFilter);
			searchWidget.getSearchBarIfUsed().setBottomView(panelFilter);
			// the background of the search bar is bright, so let's make all text black
			cDeepSearch.setTextColor(0xff000000);
		}
		
		loader = new SongLoader();
        
        SearchState searchState = getIntent().getParcelableExtra(EXTRA_searchState);
        if (searchState != null) {
        	stillUsingInitialSearchState = true; { // prevent triggering
	        	searchWidget.setText(searchState.filter_string);
	        	adapter.setData(searchState.result);
	        	cDeepSearch.setChecked(searchState.deepSearch);
	    		lsSong.setSelection(searchState.selectedPosition);
	    		loader.setSelectedBookName(searchState.bookName);
	    		if (searchState.bookName == null) {
	    			bChangeBook.setText(R.string.sn_bookselector_all);
	    		} else {
	    			bChangeBook.setText(searchState.bookName);
	    		}
        	} stillUsingInitialSearchState = false;
        	setProgressBarIndeterminateVisibility(false); // somehow this is needed.
        } else {
        	startSearch();
        }
        
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
        });
	}
	
	@Override public boolean onCreateOptionsMenu(Menu menu) {
		getSupportMenuInflater().inflate(R.menu.activity_song_list, menu);
		return true;
	}
	
	@Override public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getItemId() == R.id.menuDeleteAll) {
			new AlertDialog.Builder(this)
			.setMessage(R.string.sn_delete_all_songs_explanation)
			.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
				@Override public void onClick(DialogInterface dialog, int which) {
					deleteAllSongs();
				}
			})
			.setNegativeButton(R.string.cancel, null)
			.show();
		}
		return super.onOptionsItemSelected(item);
	}
	
	protected void deleteAllSongs() {
		final ProgressDialog pd = ProgressDialog.show(this, null, getString(R.string.please_wait_titik3), true, false);
		
		new Thread() {
			@Override public void run() {
				final int count = S.getSongDb().deleteAllSongs();
				
				runOnUiThread(new Runnable() {
					@Override public void run() {
						pd.dismiss();
						
						startSearch();
						
						new AlertDialog.Builder(SongListActivity.this)
						.setMessage(getString(R.string.sn_delete_all_songs_result, count))
						.setPositiveButton(R.string.ok, null)
						.show();
					}
				});
			};
		}.start();
	}

	void startSearch() {
		if (stillUsingInitialSearchState) return;
		setProgressBarIndeterminateVisibility(true);
		loader.setFilterString(searchWidget.getText().toString());
		loader.setDeepSearch(cDeepSearch.isChecked());
		loader.forceLoad();
	}
	
	void startSearchSettingBookName(String selectedBookName) {
		loader.setSelectedBookName(selectedBookName);
		startSearch();
	}
	
	OnClickListener bChangeBook_click = new OnClickListener() {
		@Override public void onClick(View v) {
			qaChangeBook.show(v);
		}
	};
	
	OnCheckedChangeListener cDeepSearch_checkedChange = new OnCheckedChangeListener() {
		@Override public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
			startSearch();
		}
	};

	OnSongBookSelectedListener songBookSelected = new OnSongBookSelectedListener() {
		@Override public void onSongBookSelected(boolean all, SongBookInfo songBookInfo) {
			if (all) {
				bChangeBook.setText(R.string.sn_bookselector_all);
				startSearchSettingBookName(null);
			} else if (songBookInfo != null) {
				if (S.getSongDb().getFirstSongFromBook(songBookInfo.bookName) == null) {
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
							startSearchSettingBookName(songBookInfo.bookName);
						}
					});
				} else { // already have, just display
					bChangeBook.setText(songBookInfo.bookName);
					startSearchSettingBookName(songBookInfo.bookName);
				}
			}
		}
	};
	
	private OnItemClickListener lsSong_itemClick = new OnItemClickListener() {
		@Override public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
			SongInfo songInfo = adapter.getItem(position);
			Intent data = new Intent();
			data.putExtra(EXTRA_bookName, songInfo.bookName);
			data.putExtra(EXTRA_code, songInfo.code);
			data.putExtra(EXTRA_searchState, new SearchState(searchWidget.getText().toString(), adapter.getData(), position, loader.getSelectedBookName(), cDeepSearch.isChecked()));
			setResult(RESULT_OK, data);
			finish();
		}
	};

	private SearchWidget.OnQueryTextListener searchWidget_queryText = new SearchWidget.SimpleOnQueryTextListener() {
		@Override public boolean onQueryTextSubmit(SearchWidget searchWidget, String query) {
			startSearch();
			return true;
		};
		
		@Override public boolean onQueryTextChange(SearchWidget searchWidget, String newText) {
			startSearch();
			return true;
		}
	};

	public class SongAdapter extends BaseAdapter {
		List<SongInfo> list;
		
		@Override public int getCount() {
			return list == null? 0: list.size();
		}

		public List<SongInfo> getData() {
			return this.list;
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
		
		public String getSelectedBookName() {
			return selectedBookName;
		}
		
		public void setSelectedBookName(String bookName) {
			this.selectedBookName = bookName;
		}

		@Override public List<SongInfo> loadInBackground() {
			List<SongInfo> res;
			if (!deepSearch) {
				List<SongInfo> songInfos = S.getSongDb().getSongInfosByBookName(getSelectedBookName());
				res = SongFilter.filterSongInfosByString(songInfos, filter_string);
			} else {
				res = S.getSongDb().getSongInfosByBookNameAndDeepFilter(getSelectedBookName(), filter_string);
			}
			return res;
		}
	}
}
