package yuku.alkitab.model;

import androidx.annotation.Nullable;
import java.util.List;
import junit.framework.TestCase;
import yuku.alkitab.util.IntArrayList;

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

			@Nullable
			@Override
			public String loadVerseText(final int ari) {
				return null;
			}

			@Nullable
			@Override
			public String loadVerseText(final Book book, final int chapter_1, final int verse_1) {
				return null;
			}

			@Override
			public int loadVersesByAriRanges(final IntArrayList ariRanges, final IntArrayList result_aris, final List<String> result_verses) {
				return 0;
			}

			@Override
			public int loadPericope(final int bookId, final int chapter_1, final IntArrayList aris, final List<PericopeBlock> pericopeBlocks) {
				return 0;
			}

			@Override
			public SingleChapterVerses loadChapterText(final Book book, final int chapter_1) {
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

	public void test1() {
		assertEquals("KJV", fakeVersion("King James Version").getInitials());
		assertEquals("KJV", fakeVersion("king james version").getInitials());
		assertEquals("KJV", fakeVersion("King-James - Version").getInitials());
		assertEquals("A2", fakeVersion("abc 2000").getInitials());
		assertEquals("A2", fakeVersion("  abc 2000  ").getInitials());
		assertEquals("A2", fakeVersion("-  abc 2000  -").getInitials());
		assertEquals("A2X", fakeVersion("-  abc 2000  -x").getInitials());
		assertEquals("SHORT", fakeVersion("SHORT").getInitials());
		assertEquals("SHORT", fakeVersion("short").getInitials());
		assertEquals("A B C", fakeVersion("a b c").getInitials());
	}
}
