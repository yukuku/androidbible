package yuku.alkitab.base.ac;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v4.view.ViewPager.OnPageChangeListener;

import java.util.ArrayList;
import java.util.List;

import yuku.afw.App;
import yuku.afw.V;
import yuku.alkitab.R;
import yuku.alkitab.base.ac.base.BaseActivity;
import yuku.kpri.model.Song;
import yuku.kpriviewer.SongRepo;
import yuku.kpriviewer.fr.SongFragment;

public class SongViewActivity extends BaseActivity {
	public static final String TAG = SongViewActivity.class.getSimpleName();
	
	private static final String EXTRA_bookNames = "bookNames";
	private static final String EXTRA_codes = "codes";
	private static final String EXTRA_index = "index";
	
	ViewPager viewPager;
	
	SongAdapter adapter;
	
	public static Intent createIntent(List<String> bookNames, List<String> songCodes, int index) {
		Intent res = new Intent(App.context, SongViewActivity.class);
		res.putStringArrayListExtra(EXTRA_bookNames, new ArrayList<String>(bookNames));
		res.putStringArrayListExtra(EXTRA_codes, new ArrayList<String>(songCodes));
		res.putExtra(EXTRA_index, index);
		return res;
	}

	private OnPageChangeListener viewPager_pageChange = new ViewPager.SimpleOnPageChangeListener() {
		@Override public void onPageSelected(int position) {
			updateTitle(position);
		}
	};

	@Override protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_song_view);
		
		viewPager = V.get(this, R.id.viewPager);
		
		List<String> bookNames = getIntent().getStringArrayListExtra(EXTRA_bookNames);
		List<String> codes = getIntent().getStringArrayListExtra(EXTRA_codes);
		
		int index = getIntent().getIntExtra(EXTRA_index, -1);
		if (index == -1) {
			finish();
			return;
		}

		adapter = new SongAdapter(getSupportFragmentManager());
		adapter.setData(SongRepo.getSongsByBookNamesAndCodes(bookNames, codes));
		
		viewPager.setAdapter(adapter);
		viewPager.setCurrentItem(index);
		viewPager.setOnPageChangeListener(viewPager_pageChange);
		updateTitle(index);
	}
	
	void updateTitle(int position) {
		Song song = adapter.getSong(position);
		setTitle(SongRepo.getBookNameBySong(song).toUpperCase() + " " + song.code);
	};
	
	class SongAdapter extends FragmentPagerAdapter {
		List<Song> list;
		
		public SongAdapter(FragmentManager fm) {
			super(fm);
		}

		public void setData(List<Song> data) {
			list = data;
			notifyDataSetChanged();
		}

		public Song getSong(int position) {
			return list.get(position);
		}

		@Override public SongFragment getItem(int position) {
			Song song = getSong(position);
			
			return SongFragment.create(song, "templates/song.html");
		}

		@Override public int getCount() {
			return list == null? 0: list.size();
		}
	}
}
