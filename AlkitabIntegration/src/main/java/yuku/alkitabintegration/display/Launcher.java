package yuku.alkitabintegration.display;

import android.content.Context;
import android.content.Intent;

public class Launcher {
	private static String appPackageName;

	/**
	 * Should be used only if you know the package name of the
	 * target application. Do not use this if you do not.
	 */
	public static void setAppPackageName(final String packageName) {
		appPackageName = packageName;
	}

	/**
	 * Returns an intent that can be used to open the app at the specific book, chapter, and verse. 
	 * Call {@link Context#startActivity(Intent)} with the returned intent from your activity to open it.
	 * @param bookId 0 for Genesis, up to 65 for Revelation
	 * @param chapter_1 Chapter number starting from 1
	 * @param verse_1 Verse number starting from 1
	 */
	public static Intent openAppAtBibleLocation(int bookId, int chapter_1, int verse_1) {
		int ari = (bookId << 16) | (chapter_1 << 8) | verse_1;
		return openAppAtBibleLocation(ari);
	}

	/**
	 * Returns an intent that can be used to open the app at the specific ari. 
	 * Call {@link Context#startActivity(Intent)} with the returned intent from your activity to open it.
	 */
	public static Intent openAppAtBibleLocation(int ari) {
		final Intent res = getBaseViewIntent();
		res.putExtra("ari", ari);
		return res;
	}

	public static Intent getBaseViewIntent() {
		final Intent res = new Intent("yuku.alkitab.action.VIEW");
		if (appPackageName != null) {
			res.setPackage(appPackageName);
		}
		return res;
	}

	/**
	 * Returns an intent that can be used to open a verse dialog at the specified target.
	 * Call {@link Context#startActivity(Intent)} with the returned intent from your activity to open it.
	 *
	 * @param target Can be encoded using any of the following:
	 * a:[ari start]-[ari end],[ari single verse] For example "a:0x000101" opens Gen 1:1, "a:0x021001-0x021002,0x021101" opens Lev 16:1-2, 17:1
	 * o:[osis start]-[osis end],[osis single verse] For example "o:Gen.1.1-Gen.1.20" opens Gen 1:1-20
	 * lid:[lid start]-[lid end],[lid single verse] For example "lid:11-20" opens Gen 1:11-20. Lid can be 1 to 31102.
	 */
	public static Intent openVersesDialogByTarget(String target) {
		Intent res = new Intent("yuku.alkitab.action.SHOW_VERSES_DIALOG");
		res.putExtra("target", target);
		if (appPackageName != null) {
			res.setPackage(appPackageName);
		}
		return res;
	}

	/**
	 * Returns an intent that can be used to open the app at the specific ari.
	 * The verse that is represented in the ari is selected.
	 * Call {@link Context#startActivity(Intent)} with the returned intent from your activity to open it.
	 */
	public static Intent openAppAtBibleLocationWithVerseSelected(int ari) {
		final Intent res = getBaseViewIntent();
		res.putExtra("ari", ari);
		res.putExtra("selectVerse", true);
		return res;
	}

	/**
	 * Returns an intent that can be used to open the app at the specific ari.
	 * The verse that is represented in the ari, and the following verses, as many as verseCount, are selected.
	 * Call {@link Context#startActivity(Intent)} with the returned intent from your activity to open it.
	 */
	public static Intent openAppAtBibleLocationWithVerseSelected(int ari, int verseCount) {
		final Intent res = getBaseViewIntent();
		res.putExtra("ari", ari);
		res.putExtra("selectVerse", true);
		res.putExtra("selectVerseCount", verseCount);
		return res;
	}
}
