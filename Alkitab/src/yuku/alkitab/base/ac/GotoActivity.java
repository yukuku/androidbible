package yuku.alkitab.base.ac;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBar;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import yuku.afw.App;
import yuku.afw.V;
import yuku.afw.storage.Preferences;
import yuku.alkitab.R;
import yuku.alkitab.base.ac.base.BaseActivity;
import yuku.alkitab.base.fr.GotoDialerFragment;
import yuku.alkitab.base.fr.GotoDirectFragment;
import yuku.alkitab.base.fr.GotoGridFragment;
import yuku.alkitab.base.fr.base.BaseGotoFragment.GotoFinishListener;
import yuku.alkitab.base.storage.Prefkey;

public class GotoActivity extends BaseActivity implements GotoFinishListener {
	public static final String TAG = GotoActivity.class.getSimpleName();

	private static final String EXTRA_bookId = "bookId"; //$NON-NLS-1$
	private static final String EXTRA_chapter = "chapter"; //$NON-NLS-1$
	private static final String EXTRA_verse = "verse"; //$NON-NLS-1$

	private static final String INSTANCE_STATE_tab = "tab"; //$NON-NLS-1$

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

	ViewPager viewPager;
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

		final ActionBar actionBar = getSupportActionBar();

		if (!getResources().getBoolean(R.bool.screen_sw_check_min_600dp)) {
	        // The following two options trigger the collapsing of the main action bar view.
	        actionBar.setDisplayShowHomeEnabled(false);
	        actionBar.setDisplayShowTitleEnabled(false);
		}

		// ViewPager and its adapters use support library fragments, so use getSupportFragmentManager.
		viewPager = V.get(this, R.id.viewPager);
		viewPager.setAdapter(pagerAdapter = new GotoPagerAdapter(getSupportFragmentManager()));
		viewPager.setOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
			@Override
			public void onPageSelected(int position) {
				// When swiping between pages, select the corresponding tab.
				actionBar.setSelectedNavigationItem(position);

				Log.d(TAG, " di sini");
				if (okToHideKeyboard && position != 1) {
					final View editText = findViewById(R.id.tDirectReference);
					if (editText != null) {
						final InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
						imm.hideSoftInputFromWindow(editText.getWindowToken(), InputMethodManager.HIDE_IMPLICIT_ONLY);
						imm.hideSoftInputFromWindow(editText.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
					}
				}
			}
		});

		// Specify that tabs should be displayed in the action bar.
		actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);

		// Create a tab listener that is called when the user changes tabs.
		ActionBar.TabListener tabListener = new ActionBar.TabListener() {
			public void onTabSelected(ActionBar.Tab tab, FragmentTransaction ft) {
				// When the tab is selected, switch to the corresponding page in the ViewPager.
				viewPager.setCurrentItem(tab.getPosition());
			}

			public void onTabUnselected(ActionBar.Tab tab, FragmentTransaction ft) {
				// hide the given tab
			}

			public void onTabReselected(ActionBar.Tab tab, FragmentTransaction ft) {
				// probably ignore this event
			}
		};

		// Add 3 tabs, specifying the tab's text and TabListener
		actionBar.addTab(actionBar.newTab().setText(R.string.goto_tab_dialer_label).setTabListener(tabListener));
		actionBar.addTab(actionBar.newTab().setText(R.string.goto_tab_direct_label).setTabListener(tabListener));
		actionBar.addTab(actionBar.newTab().setText(R.string.goto_tab_grid_label).setTabListener(tabListener));

		if (savedInstanceState == null) {
			// get from preferences
			int tabUsed = Preferences.getInt(Prefkey.goto_last_tab, 0);
			if (tabUsed >= 1 && tabUsed <= 3) {
				actionBar.setSelectedNavigationItem(tabUsed - 1 /* to make it 0-based */);
			}

			if (tabUsed == 2) {
				viewPager.postDelayed(new Runnable() {
					@Override
					public void run() {
						final View editText = V.get(GotoActivity.this, R.id.tDirectReference);
						if (editText != null) {
							InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
							imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT);
						}
						okToHideKeyboard = true;
					}
				}, 100);
			} else {
				okToHideKeyboard = true;
			}
		} else {
			actionBar.setSelectedNavigationItem(savedInstanceState.getInt(INSTANCE_STATE_tab, 0));
		}
	}

	@Override protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putInt(INSTANCE_STATE_tab, getSupportActionBar().getSelectedNavigationIndex());
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
