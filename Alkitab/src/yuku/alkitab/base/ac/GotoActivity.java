package yuku.alkitab.base.ac;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.FragmentTransaction;

import yuku.afw.App;
import yuku.alkitab.base.ac.base.BaseActivity;
import yuku.alkitab.base.fr.GotoDialerFragment;
import yuku.alkitab.base.fr.GotoDirectFragment;
import yuku.alkitab.base.fr.GotoGridFragment;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.ActionBar.Tab;

public class GotoActivity extends BaseActivity implements ActionBar.TabListener {
	public static final String TAG = GotoActivity.class.getSimpleName();

	public static class Result {
		public int kitab_pos;
		public int pasal_1;
		public int ayat_1;
	}
	
	public static Intent createIntent(int kitab_pos, int pasal_1, int ayat_1) {
		// TODO
		return new Intent(App.context, GotoActivity.class);
	}
	
	public static Result obtainResult(Intent data) {
		Result res = new Result();
		// TODO
		return res;
	}

	private Object tab_dialer = new Object();
	private Object tab_direct = new Object();
	private Object tab_grid = new Object();
	
	GotoDialerFragment fr_dialer;
	GotoDirectFragment fr_direct;
	GotoGridFragment fr_grid;

	@Override public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		ActionBar actionBar = getSupportActionBar();
		actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
		actionBar.addTab(actionBar.newTab().setTag(tab_dialer).setText("Dialer").setTabListener(this));
		actionBar.addTab(actionBar.newTab().setTag(tab_direct).setText("Direct").setTabListener(this));
		actionBar.addTab(actionBar.newTab().setTag(tab_grid).setText("Grid").setTabListener(this));
	}

	@Override public void onTabSelected(Tab tab, FragmentTransaction ft) {
		if (tab.getTag() == tab_dialer) {
			if (fr_dialer == null) {
				fr_dialer = new GotoDialerFragment();
				ft.add(android.R.id.content, fr_dialer);
			} else {
				ft.attach(fr_dialer);
			}
		}
		if (tab.getTag() == tab_direct) {
			if (fr_direct == null) {
				fr_direct = new GotoDirectFragment();
				ft.add(android.R.id.content, fr_direct);
			} else {
				ft.attach(fr_direct);
			}
		}
		if (tab.getTag() == tab_grid) {
			if (fr_grid == null) {
				fr_grid = new GotoGridFragment();
	            ft.add(android.R.id.content, fr_grid);
	        } else {
	            ft.attach(fr_grid);
	        }
		}
	}

	@Override public void onTabReselected(Tab tab, FragmentTransaction ft) {}

	@Override public void onTabUnselected(Tab tab, FragmentTransaction ft) {
		if (tab.getTag() == tab_dialer && fr_dialer != null) ft.detach(fr_dialer); 
		if (tab.getTag() == tab_direct && fr_direct != null) ft.detach(fr_direct); 
		if (tab.getTag() == tab_grid && fr_grid != null) ft.detach(fr_grid); 
	}
}
