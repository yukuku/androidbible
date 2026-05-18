package yuku.alkitab.base.fr.base;

public abstract class BaseGotoFragment extends BaseFragment {
	public interface GotoFinishListener {
		int GOTO_TAB_dialer = 1;
		int GOTO_TAB_direct = 2;
		int GOTO_TAB_grid = 3;
		
		void onGotoFinished(int gotoTabUsed, int bookId, int chapter_1, int verse_1);
	}
}
