package yuku.alkitab.base.ac;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.AsyncTaskLoader;
import android.support.v4.content.Loader;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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
	
	SongAdapter adapter;
	SongLoader loader;

	public static Intent createIntent() {
		return new Intent(App.context, SongListActivity.class);
	}

	@Override protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		setProgressBarIndeterminate(true);
		
		setContentView(R.layout.activity_song_list);
		
		setTitle(getString(R.string.app_name) + " " + App.getVersionName() + "." + App.getVersionCode());
		
		searchWidget = V.get(this, R.id.searchWidget);
		lsSong = V.get(this, R.id.lsSong);
		
		searchWidget.setSubmitButtonEnabled(false);
		searchWidget.setOnQueryTextListener(searchWidget_queryText);
		
		lsSong.setAdapter(adapter = new SongAdapter());
		lsSong.setOnItemClickListener(lsSong_click);
		
		// load songs in bg
		for (final String bookName: "kpri kj nkb pkj".split(" ")) {
			new Thread() {
				@Override public void run() {
					try {
						SongRepo.loadSongData(bookName, getAssets().open(bookName + ".ser"));
					} catch (IOException e) {
						throw new RuntimeException(e);
					}
				};
			}.start();
		}
		
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

		@Override public List<Song> loadInBackground() {
			Log.d(TAG, "@@loadInBackground filter_string=" + filter_string);

			return SongRepo.filterSongByString(SongRepo.getAllSongs(), filter_string);
		}
	}
}
