package yuku.alkitab.test;

import junit.framework.TestCase;
import yuku.alkitab.base.S;
import yuku.alkitab.model.Book;
import yuku.alkitab.model.FootnoteEntry;
import yuku.alkitab.model.PericopeBlock;
import yuku.alkitab.model.SingleChapterVerses;
import yuku.alkitab.model.Version;
import yuku.alkitab.model.XrefEntry;
import yuku.alkitab.util.IntArrayList;

import java.util.List;

public class GetVersionInitialsTest extends TestCase {

	Version fakeVersion(final String longName) {
		return new Version() {
			@Override
			public String getShortName() {
				return null;
			}

			@Override
			public String getLongName() {
				return longName;
			}

			@Override
			public String getLocale() {
				return null;
			}

			@Override
			public int getMaxBookIdPlusOne() {
				return 0;
			}

			@Override
			public Book[] getConsecutiveBooks() {
				return new Book[0];
			}

			@Override
			public Book getBook(final int bookId) {
				return null;
			}

			@Override
			public Book getFirstBook() {
				return null;
			}

			@Override
			public String loadVerseText(final int ari) {
				return null;
			}

			@Override
			public String loadVerseText(final Book book, final int chapter_1, final int verse_1) {
				return null;
			}

			@Override
			public int loadVersesByAriRanges(final IntArrayList ariRanges, final IntArrayList result_aris, final List<String> result_verses) {
				return 0;
			}

			@Override
			public int loadPericope(final int bookId, final int chapter_1, final int[] aris, final PericopeBlock[] pericopeBlocks, final int max) {
				return 0;
			}

			@Override
			public SingleChapterVerses loadChapterText(final Book book, final int chapter_1) {
				return null;
			}

			@Override
			public SingleChapterVerses loadChapterTextLowercased(final Book book, final int chapter_1) {
				return null;
			}

			@Override
			public String loadChapterTextLowercasedWithoutSplit(final Book book, final int chapter_1) {
				return null;
			}

			@Override
			public XrefEntry getXrefEntry(final int arif) {
				return null;
			}

			@Override
			public FootnoteEntry getFootnoteEntry(final int arif) {
				return null;
			}
		};
	}

	public void test1() throws Throwable {
		assertEquals(S.getVersionInitials(fakeVersion("King James Version")), "KJV");
		assertEquals(S.getVersionInitials(fakeVersion("king james version")), "KJV");
		assertEquals(S.getVersionInitials(fakeVersion("King-James - Version")), "KJV");
		assertEquals(S.getVersionInitials(fakeVersion("abc 2000")), "A2");
		assertEquals(S.getVersionInitials(fakeVersion("  abc 2000  ")), "A2");
		assertEquals(S.getVersionInitials(fakeVersion("-  abc 2000  -")), "A2");
		assertEquals(S.getVersionInitials(fakeVersion("-  abc 2000  -x")), "A2X");
		assertEquals(S.getVersionInitials(fakeVersion("SHORT")), "SHORT");
		assertEquals(S.getVersionInitials(fakeVersion("short")), "SHORT");
		assertEquals(S.getVersionInitials(fakeVersion("a b c")), "A B C");
	}
}
