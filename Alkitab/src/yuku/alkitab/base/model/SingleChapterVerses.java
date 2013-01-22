package yuku.alkitab.base.model;

public abstract class SingleChapterVerses {
	public static final String TAG = SingleChapterVerses.class.getSimpleName();
	
	public abstract String getVerse(int verse_0);

	public abstract int getVerseCount();
}
