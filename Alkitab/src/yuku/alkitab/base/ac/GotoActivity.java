package yuku.alkitab.base.ac;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.FragmentTransaction;
import android.view.inputmethod.InputMethodManager;

import yuku.afw.App;
import yuku.alkitab.base.S;
import yuku.alkitab.base.ac.base.BaseActivity;
import yuku.alkitab.base.fr.GotoDialerFragment;
import yuku.alkitab.base.fr.GotoDirectFragment;
import yuku.alkitab.base.fr.GotoGridFragment;
import yuku.alkitab.base.fr.base.BaseGotoFragment.GotoFinishListener;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.ActionBar.Tab;

public class GotoActivity extends BaseActivity implements ActionBar.TabListener, GotoFinishListener {
	public static final String TAG = GotoActivity.class.getSimpleName();

	private static final String EXTRA_bookId = "bookId";
	private static final String EXTRA_chapter = "chapter";
	private static final String EXTRA_verse = "verse";
	
	public static class Result {
		public int bookId;
		public int chapter_1;
		public int verse_1;
	}
	
	public static Intent createIntent(int bookId, int chapter_1, int verse_1) {
		Intent res = new Intent(App.context, GotoActivity.class);
		res.putExtra(EXTRA_bookId, bookId);
		res.putExtra(EXTRA_chapter, chapter_1);
		res.putExtra(EXTRA_verse, verse_1);
		return res;
	}
	
	public static Result obtainResult(Intent data) {
		Result res = new Result();
		res.bookId = data.getIntExtra(EXTRA_bookId, -1);
		res.chapter_1 = data.getIntExtra(EXTRA_chapter, 0);
		res.verse_1 = data.getIntExtra(EXTRA_verse, 0);
		return res;
	}

	private Object tab_dialer = new Object();
	private Object tab_direct = new Object();
	private Object tab_grid = new Object();
	
	GotoDialerFragment fr_dialer;
	GotoDirectFragment fr_direct;
	GotoGridFragment fr_grid;

	int bookId;
	int chapter_1;
	int verse_1;

	@Override public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		S.prepareBook();

		bookId = getIntent().getIntExtra(EXTRA_bookId, -1);
		chapter_1 = getIntent().getIntExtra(EXTRA_chapter, 0);
		verse_1 = getIntent().getIntExtra(EXTRA_verse, 0);
		
		if (savedInstanceState == null) {
			ActionBar actionBar = getSupportActionBar();
			actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
			actionBar.addTab(actionBar.newTab().setTag(tab_dialer).setText("Dialer").setTabListener(this));
			actionBar.addTab(actionBar.newTab().setTag(tab_direct).setText("Direct").setTabListener(this));
			actionBar.addTab(actionBar.newTab().setTag(tab_grid).setText("Grid").setTabListener(this));
		}
	}

	@Override public void onTabSelected(Tab tab, FragmentTransaction ft) {
		if (tab.getTag() == tab_dialer) {
			if (fr_dialer == null) {
				fr_dialer = GotoDialerFragment.create(bookId, chapter_1, verse_1);
				ft.add(android.R.id.content, fr_dialer);
			} else {
				ft.attach(fr_dialer);
			}
		}
		if (tab.getTag() == tab_direct) {
			if (fr_direct == null) {
				fr_direct = GotoDirectFragment.create(bookId, chapter_1, verse_1);
				ft.add(android.R.id.content, fr_direct);
			} else {
				ft.attach(fr_direct);
			}
		}
		if (tab.getTag() == tab_grid) {
			if (fr_grid == null) {
				fr_grid = GotoGridFragment.create(bookId, chapter_1, verse_1);
				ft.add(android.R.id.content, fr_grid);
			} else {
				ft.attach(fr_grid);
			}
		}

		if (tab.getTag() == tab_direct) {
			fr_direct.onTabSelected();
		} else {
			InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
			imm.hideSoftInputFromWindow(findViewById(android.R.id.content).getWindowToken(), 0);
		}
	}

	@Override public void onTabReselected(Tab tab, FragmentTransaction ft) {}

	@Override public void onTabUnselected(Tab tab, FragmentTransaction ft) {
		if (tab.getTag() == tab_dialer && fr_dialer != null) ft.detach(fr_dialer); 
		if (tab.getTag() == tab_direct && fr_direct != null) ft.detach(fr_direct); 
		if (tab.getTag() == tab_grid && fr_grid != null) ft.detach(fr_grid); 
	}

	@Override public void onGotoFinished(int bookId, int chapter_1, int verse_1) {
		Intent data = new Intent();
		data.putExtra(EXTRA_bookId, bookId);
		data.putExtra(EXTRA_chapter, chapter_1);
		data.putExtra(EXTRA_verse, verse_1);
		setResult(RESULT_OK, data);
		finish();
	}
}
