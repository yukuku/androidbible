package yuku.alkitab.base.fr.base;

public class BaseGotoFragment extends BaseFragment {
	public static final String TAG = BaseGotoFragment.class.getSimpleName();
	
	public interface GotoFinishListener {
		void onGotoFinished(int bookId, int chapter_1, int verse_1);
	}
}
