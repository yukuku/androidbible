package yuku.alkitab.base.ac;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.view.inputmethod.InputMethodManager;

import yuku.afw.App;
import yuku.afw.storage.Preferences;
import yuku.alkitab.R;
import yuku.alkitab.base.ac.base.BaseActivity;
import yuku.alkitab.base.fr.GotoDialerFragment;
import yuku.alkitab.base.fr.GotoDirectFragment;
import yuku.alkitab.base.fr.GotoGridFragment;
import yuku.alkitab.base.fr.base.BaseGotoFragment.GotoFinishListener;
import yuku.alkitab.base.storage.Prefkey;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.ActionBar.Tab;

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

	private Object tab_dialer = new Object();
	private Object tab_direct = new Object();
	private Object tab_grid = new Object();

	int bookId;
	int chapter_1;
	int verse_1;

	@Override public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		bookId = getIntent().getIntExtra(EXTRA_bookId, -1);
		chapter_1 = getIntent().getIntExtra(EXTRA_chapter, 0);
		verse_1 = getIntent().getIntExtra(EXTRA_verse, 0);

		ActionBar actionBar = getSupportActionBar();

		if (getResources().getBoolean(R.bool.screen_sw_check_min_600dp) == false) {
	        // The following two options trigger the collapsing of the main action bar view.
	        actionBar.setDisplayShowHomeEnabled(false);
	        actionBar.setDisplayShowTitleEnabled(false);
		}
		
        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
		actionBar.addTab(actionBar.newTab().setTag(tab_dialer).setText(R.string.goto_tab_dialer_label).setTabListener(new TabListener<GotoDialerFragment>(this, "dialer", GotoDialerFragment.class, GotoDialerFragment.createArgs(bookId, chapter_1, verse_1)))); //$NON-NLS-1$
		actionBar.addTab(actionBar.newTab().setTag(tab_direct).setText(R.string.goto_tab_direct_label).setTabListener(new TabListener<GotoDirectFragment>(this, "direct", GotoDirectFragment.class, GotoDirectFragment.createArgs(bookId, chapter_1, verse_1)))); //$NON-NLS-1$
		actionBar.addTab(actionBar.newTab().setTag(tab_grid).setText(R.string.goto_tab_grid_label).setTabListener(new TabListener<GotoGridFragment>(this, "grid", GotoGridFragment.class, GotoGridFragment.createArgs(bookId, chapter_1, verse_1)))); //$NON-NLS-1$

		if (savedInstanceState == null) {
			// get from preferences
			int tabUsed = Preferences.getInt(Prefkey.goto_last_tab, 0);
			if (tabUsed >= 1 && tabUsed <= 3) {
				actionBar.setSelectedNavigationItem(tabUsed - 1 /* to make it 0-based */);
			}
		} else {
			actionBar.setSelectedNavigationItem(savedInstanceState.getInt(INSTANCE_STATE_tab, 0));
		}
	}

	@Override protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putInt(INSTANCE_STATE_tab, getSupportActionBar().getSelectedNavigationIndex());
	}

	public static class TabListener<T extends Fragment> implements ActionBar.TabListener {
		private final BaseActivity mActivity;
		private final String mTag;
		private final Class<T> mClass;
		private final Bundle mArgs;
		private Fragment mFragment;

		public TabListener(BaseActivity activity, String tag, Class<T> clz) {
			this(activity, tag, clz, null);
		}

		public TabListener(BaseActivity activity, String tag, Class<T> clz, Bundle args) {
			mActivity = activity;
			mTag = tag;
			mClass = clz;
			mArgs = args;

			// Check to see if we already have a fragment for this tab, probably
			// from a previously saved state. If so, deactivate it, because our
			// initial state is that a tab isn't shown.
			FragmentManager fm = mActivity.getSupportFragmentManager();
			mFragment = fm.findFragmentByTag(mTag);
			if (mFragment != null && !mFragment.isDetached()) {
				FragmentTransaction ft = fm.beginTransaction();
				ft.detach(mFragment);
				ft.commit();
			}
		}

		@Override public void onTabSelected(Tab tab, FragmentTransaction ft) {
			if (mFragment == null) {
				mFragment = Fragment.instantiate(mActivity, mClass.getName(), mArgs);
				ft.add(android.R.id.content, mFragment, mTag);
			} else {
				ft.attach(mFragment);
			}
			
			if (mFragment instanceof GotoDirectFragment) {
				((GotoDirectFragment) mFragment).onTabSelected();
			} else {
				InputMethodManager imm = (InputMethodManager) mActivity.getSystemService(Context.INPUT_METHOD_SERVICE);
				imm.hideSoftInputFromWindow(mActivity.findViewById(android.R.id.content).getWindowToken(), InputMethodManager.HIDE_IMPLICIT_ONLY);
			}
		}

		@Override public void onTabUnselected(Tab tab, FragmentTransaction ft) {
			if (mFragment != null) {
				ft.detach(mFragment);
			}
		}

		@Override public void onTabReselected(Tab tab, FragmentTransaction ft) {
			if (mFragment instanceof GotoDirectFragment) {
				((GotoDirectFragment) mFragment).onTabSelected();
			}
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
