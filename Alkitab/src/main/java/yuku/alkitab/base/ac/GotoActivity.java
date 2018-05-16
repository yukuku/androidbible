package yuku.alkitab.base.ac;

import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import yuku.afw.App;
import yuku.afw.V;
import yuku.afw.storage.Preferences;
import yuku.alkitab.base.ac.base.BaseActivity;
import yuku.alkitab.base.fr.GotoDialerFragment;
import yuku.alkitab.base.fr.GotoDirectFragment;
import yuku.alkitab.base.fr.GotoGridFragment;
import yuku.alkitab.base.fr.base.BaseGotoFragment;
import yuku.alkitab.base.storage.Prefkey;
import yuku.alkitab.debug.R;

public class GotoActivity extends BaseActivity implements BaseGotoFragment.GotoFinishListener {
	public static final String TAG = GotoActivity.class.getSimpleName();

	private static final String EXTRA_bookId = "bookId";
	private static final String EXTRA_chapter = "chapter";
	private static final String EXTRA_verse = "verse";

	private static final String INSTANCE_STATE_tab = "tab";

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

	Toolbar toolbar;
	ViewPager viewPager;
	TabLayout tablayout;
	GotoPagerAdapter pagerAdapter;

	boolean okToHideKeyboard = false;

	int bookId;
	int chapter_1;
	int verse_1;

	@Override public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_goto);

		bookId = getIntent().getIntExtra(EXTRA_bookId, -1);
		chapter_1 = getIntent().getIntExtra(EXTRA_chapter, 0);
		verse_1 = getIntent().getIntExtra(EXTRA_verse, 0);

		toolbar = V.get(this, R.id.toolbar);
		setSupportActionBar(toolbar);
		final ActionBar actionBar = getSupportActionBar();
		if (actionBar != null) {
			actionBar.setDisplayShowTitleEnabled(false);
			actionBar.setDisplayHomeAsUpEnabled(true);
			toolbar.setNavigationOnClickListener(v -> navigateUp());
		}

		// ViewPager and its adapters use support library fragments, so use getSupportFragmentManager.
		viewPager = V.get(this, R.id.viewPager);
		viewPager.setAdapter(pagerAdapter = new GotoPagerAdapter(getSupportFragmentManager()));
		viewPager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
			@Override
			public void onPageSelected(int position) {
				if (okToHideKeyboard && position != 1) {
					final View editText = V.get(GotoActivity.this, R.id.tDirectReference);
					if (editText != null) {
						final InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
						imm.hideSoftInputFromWindow(editText.getWindowToken(), InputMethodManager.HIDE_IMPLICIT_ONLY);
						imm.hideSoftInputFromWindow(editText.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
					}
				}
			}
		});

		tablayout = V.get(this, R.id.tablayout);
		tablayout.setTabMode(TabLayout.MODE_SCROLLABLE);
		tablayout.setupWithViewPager(viewPager);

		if (savedInstanceState == null) {
			// get from preferences
			int tabUsed = Preferences.getInt(Prefkey.goto_last_tab, 0);
			if (tabUsed >= 1 && tabUsed <= 3) {
				viewPager.setCurrentItem(tabUsed - 1 /* to make it 0-based */, false);
			}

			if (tabUsed == 2) {
				viewPager.postDelayed(() -> {
					final View editText = V.get(GotoActivity.this, R.id.tDirectReference);
					if (editText != null) {
						InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
						imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT);
					}
					okToHideKeyboard = true;
				}, 100);
			} else {
				okToHideKeyboard = true;
			}
		} else {
			viewPager.setCurrentItem(savedInstanceState.getInt(INSTANCE_STATE_tab, 0), false);
		}
	}

	@Override
	public boolean onCreateOptionsMenu(final Menu menu) {
		getMenuInflater().inflate(R.menu.activity_goto, menu);
		return true;
	}

	@Override
	public boolean onPrepareOptionsMenu(final Menu menu) {
		final MenuItem menuAskForVerse = menu.findItem(R.id.menuAskForVerse);
		final boolean val = Preferences.getBoolean(Prefkey.gotoAskForVerse, Prefkey.GOTO_ASK_FOR_VERSE_DEFAULT);
		menuAskForVerse.setChecked(val);

		return true;
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item) {
		if (item.getItemId() == R.id.menuAskForVerse) {
			final boolean val = Preferences.getBoolean(Prefkey.gotoAskForVerse, Prefkey.GOTO_ASK_FOR_VERSE_DEFAULT);
			Preferences.setBoolean(Prefkey.gotoAskForVerse, !val);
			supportInvalidateOptionsMenu();
			return true;
		}

		return super.onOptionsItemSelected(item);
	}

	@Override protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putInt(INSTANCE_STATE_tab, viewPager.getCurrentItem());
	}

	public class GotoPagerAdapter extends FragmentPagerAdapter {
		final int[] pageTitleResIds = {R.string.goto_tab_dialer_label, R.string.goto_tab_direct_label, R.string.goto_tab_grid_label};

		public GotoPagerAdapter(FragmentManager fm) {
			super(fm);
		}

		@Override
		public Fragment getItem(final int position) {
			final Fragment res;
			if (position == 0) {
				res = Fragment.instantiate(GotoActivity.this, GotoDialerFragment.class.getName(), GotoDialerFragment.createArgs(bookId, chapter_1, verse_1));
			} else if (position == 1) {
				res = Fragment.instantiate(GotoActivity.this, GotoDirectFragment.class.getName(), GotoDirectFragment.createArgs(bookId, chapter_1, verse_1));
			} else {
				res = Fragment.instantiate(GotoActivity.this, GotoGridFragment.class.getName(), GotoGridFragment.createArgs(bookId, chapter_1, verse_1));
			}
			return res;
		}

		@Override
		public int getCount() {
			return pageTitleResIds.length;
		}

		@Override
		public CharSequence getPageTitle(int position) {
			return getString(pageTitleResIds[position]);
		}
	}

	@Override public void onGotoFinished(int gotoTabUsed, int bookId, int chapter_1, int verse_1) {
		// store goto tab used for next time
		Preferences.setInt(Prefkey.goto_last_tab, gotoTabUsed);
		
		Intent data = new Intent();
		data.putExtra(EXTRA_bookId, bookId);
		data.putExtra(EXTRA_chapter, chapter_1);
		data.putExtra(EXTRA_verse, verse_1);
		setResult(RESULT_OK, data);
		finish();
	}
}
