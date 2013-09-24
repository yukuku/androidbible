package yuku.alkitab.base.fr.base;

public abstract class BaseGotoFragment extends BaseFragment {
	public static final String TAG = BaseGotoFragment.class.getSimpleName();
	
	public interface GotoFinishListener {
		public static final int GOTO_TAB_dialer = 1;
		public static final int GOTO_TAB_direct = 2;
		public static final int GOTO_TAB_grid = 3;
		
		void onGotoFinished(int gotoTabUsed, int bookId, int chapter_1, int verse_1);
	}
}
